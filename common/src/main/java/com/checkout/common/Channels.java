package com.checkout.common;

public final class Channels {
    public static final String AUTHORIZE = "checkout.payment.authorize";
    public static final String AUTHORIZE_DLQ = "checkout.payment.authorize.dlq";
    public static final String ALLOCATE = "checkout.stock.allocate";
    public static final String ALLOCATE_DLQ = "checkout.stock.allocate.dlq";
    public static final String PAYMENTS = "checkout.payments";
    public static final String STOCK = "checkout.stock";

    private Channels() {
    }
}
