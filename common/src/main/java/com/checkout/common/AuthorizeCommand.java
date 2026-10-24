package com.checkout.common;

public record AuthorizeCommand(String orderId, String sku, int quantity, int amountCents, String cardToken) {
}
