package com.checkout.order;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class Publish {

    private Publish() {
    }

    static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    static void kafka(KafkaTemplate<String, Object> template, String topic, String key, Object event) {
        try {
            template.send(topic, key, event).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("event log unavailable", ex);
        }
    }
}
