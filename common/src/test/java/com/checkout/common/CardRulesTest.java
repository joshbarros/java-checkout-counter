package com.checkout.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CardRulesTest {

    @Test
    void declinesOnlyATokenThatEndsInFourZeros() {
        assertTrue(CardRules.declined("42420000"));
        assertTrue(CardRules.declined("0000"));
        assertFalse(CardRules.declined("4242"));
        assertFalse(CardRules.declined("424200001"));
        assertFalse(CardRules.declined(null));
    }
}
