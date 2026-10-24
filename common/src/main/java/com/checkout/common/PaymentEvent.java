package com.checkout.common;

public record PaymentEvent(String orderId, String status, int amountCents) {
}
