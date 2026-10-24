package com.checkout.common;

public record AllocateCommand(String orderId, String sku, int quantity, int amountCents) {
}
