package com.checkout.common;

public final class CardRules {
    private CardRules() {
    }

    public static boolean declined(String cardToken) {
        return cardToken != null && cardToken.endsWith("0000");
    }
}
