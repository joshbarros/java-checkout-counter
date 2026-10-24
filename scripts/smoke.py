#!/usr/bin/env python3
"""Order, authorize, allocate, release, and contend for the last units."""

import base64
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

ORDER = "http://localhost:8111"
PAY = "http://localhost:8112"
STOCK = "http://localhost:8113"
MGMT = "http://localhost:15676"
AUTH = "platform:platform"
failures = []


def check(name, ok, detail=""):
    if ok:
        print(f"PASS  {name}")
    else:
        print(f"FAIL  {name} {detail}")
        failures.append(f"{name}: {detail}")


def call(method, url, body=None, auth=None, timeout=15):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        request.add_header("content-type", "application/json")
    if auth:
        request.add_header("Authorization", "Basic " + base64.b64encode(auth.encode()).decode())
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return ex.code, parsed
    except Exception as ex:
        return 0, str(ex)


def wait_ok(url, seconds=180, auth=None):
    deadline = time.time() + seconds
    while time.time() < deadline:
        status, _ = call("GET", url, auth=auth)
        if status == 200:
            return True
        time.sleep(1)
    return False


def until(label, fn, seconds=45):
    deadline = time.time() + seconds
    last = None
    while time.time() < deadline:
        last = fn()
        if last is not None:
            return last
        time.sleep(0.4)
    check(label, False, f"timed out last={last!r}")
    return None


def depth(name):
    status, body = call("GET", f"{MGMT}/api/queues/%2F/{name}", auth=AUTH)
    if status != 200 or not isinstance(body, dict):
        return None
    return body.get("messages", 0)


def grew(name, before, amount=1):
    def ready():
        now = depth(name)
        if now is not None and now >= before + amount:
            return now
        return None
    return until(f"dlq {name}", ready, 20)


def order(sku, qty, price, card):
    return call("POST", f"{ORDER}/orders", {
        "sku": sku, "quantity": qty, "unitPriceCents": price, "cardToken": card,
    })


def main():
    check("order health", wait_ok(f"{ORDER}/actuator/health"))
    check("payment health", wait_ok(f"{PAY}/actuator/health"))
    check("inventory health", wait_ok(f"{STOCK}/actuator/health"))
    check("rabbit health", wait_ok(f"{MGMT}/api/overview", auth=AUTH))

    status, _ = call("POST", f"{STOCK}/skus", {"sku": "  ", "onHand": 1})
    check("blank sku is 400", status == 400, status)
    status, _ = order("", 1, 100, "4242")
    check("blank order sku is 400", status == 400, status)
    status, _ = order("MUG", 0, 100, "4242")
    check("zero quantity is 400", status == 400, status)
    status, missing = call("GET", f"{ORDER}/orders/missing")
    check("unknown order is 404", status == 404, status)

    mug = "MUG-" + uuid.uuid4().hex[:8]
    status, sku = call("POST", f"{STOCK}/skus", {"sku": mug, "onHand": 5})
    check("create sku", status == 201 and sku.get("onHand") == 5, sku)
    status, _ = call("POST", f"{STOCK}/skus", {"sku": mug, "onHand": 1})
    check("duplicate sku is 409", status == 409, status)

    status, placed = order(mug, 2, 1500, "4242")
    check("place order", status == 201 and placed.get("status") == "PENDING" and placed.get("amountCents") == 3000, placed)
    oid = placed["id"]

    def allocated():
        code, body = call("GET", f"{ORDER}/orders/{oid}")
        hold_code, hold = call("GET", f"{PAY}/holds/{oid}")
        alloc_code, alloc = call("GET", f"{STOCK}/allocations/{oid}")
        if code == 200 and body.get("status") == "ALLOCATED" and hold_code == 200 and hold.get("status") == "AUTHORIZED" and alloc_code == 200:
            return body
        return None

    check("two units allocate", until("allocate mug", allocated) is not None)
    _, shelf = call("GET", f"{STOCK}/skus/{mug}")
    check("shelf is 3 after the first order", shelf.get("onHand") == 3, shelf)

    stock_dlq = depth("checkout.stock.allocate.dlq") or 0
    pay_dlq = depth("checkout.payment.authorize.dlq") or 0
    status, short = order(mug, 10, 100, "4242")
    short_id = short["id"]

    def cancelled():
        _, body = call("GET", f"{ORDER}/orders/{short_id}")
        hold_code, hold = call("GET", f"{PAY}/holds/{short_id}")
        alloc_code, _ = call("GET", f"{STOCK}/allocations/{short_id}")
        if isinstance(body, dict) and body.get("status") == "CANCELLED" and hold_code == 200 and hold.get("status") == "RELEASED" and alloc_code == 404:
            return body
        return None

    check("short shelf cancels and releases the hold", until("cancel short", cancelled, 50) is not None)
    _, shelf = call("GET", f"{STOCK}/skus/{mug}")
    check("short order does not change the shelf", shelf.get("onHand") == 3, shelf)
    check("short order dead-letters allocation", grew("checkout.stock.allocate.dlq", stock_dlq) == stock_dlq + 1)

    status, declined = order(mug, 1, 100, "42420000")
    declined_id = declined["id"]

    def declined_ok():
        _, body = call("GET", f"{ORDER}/orders/{declined_id}")
        hold_code, _ = call("GET", f"{PAY}/holds/{declined_id}")
        if isinstance(body, dict) and body.get("status") == "DECLINED" and hold_code == 404:
            return body
        return None

    check("token ending in 0000 declines with no hold", until("decline", declined_ok) is not None)
    _, shelf = call("GET", f"{STOCK}/skus/{mug}")
    check("declined card does not change the shelf", shelf.get("onHand") == 3, shelf)
    check("declined card dead-letters authorization", grew("checkout.payment.authorize.dlq", pay_dlq) == pay_dlq + 1)

    status, ghost = order("GHOST-" + uuid.uuid4().hex[:8], 1, 100, "4242")
    ghost_id = ghost["id"]

    def ghost_done():
        _, body = call("GET", f"{ORDER}/orders/{ghost_id}")
        _, hold = call("GET", f"{PAY}/holds/{ghost_id}")
        if isinstance(body, dict) and body.get("status") == "CANCELLED" and isinstance(hold, dict) and hold.get("status") == "RELEASED":
            return body
        return None

    check("unknown sku cancels after the hold", until("ghost sku", ghost_done) is not None)

    last = "LAST-" + uuid.uuid4().hex[:8]
    status, _ = call("POST", f"{STOCK}/skus", {"sku": last, "onHand": 2})
    check("create a shelf of two", status == 201, status)

    def one(_):
        return order(last, 1, 100, "4242")

    with ThreadPoolExecutor(max_workers=5) as pool:
        raced = list(pool.map(one, range(5)))
    ids = [body["id"] for code, body in raced if code == 201 and isinstance(body, dict)]
    check("five overlapping orders are accepted", len(ids) == 5, [code for code, _ in raced])

    def outcome(oid):
        _, body = call("GET", f"{ORDER}/orders/{oid}")
        if isinstance(body, dict) and body.get("status") in ("ALLOCATED", "CANCELLED"):
            return body.get("status")
        return None

    statuses = []
    for oid in ids:
        statuses.append(until(f"race {oid[:8]}", lambda oid=oid: outcome(oid), 50))
    check("exactly two of five orders allocate", statuses.count("ALLOCATED") == 2 and statuses.count("CANCELLED") == 3, statuses)
    _, shelf = call("GET", f"{STOCK}/skus/{last}")
    check("contended shelf ends at zero", shelf.get("onHand") == 0, shelf)

    print("\n== summary ==")
    if failures:
        print(f"{len(failures)} failed")
        for item in failures:
            print(f" - {item}")
        sys.exit(1)
    print("checkout counter smoke passed")


if __name__ == "__main__":
    main()
