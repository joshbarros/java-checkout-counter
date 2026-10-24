package com.checkout.order;

import com.checkout.common.Channels;
import com.checkout.common.PaymentEvent;
import com.checkout.common.StockEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderProjection {

    private final OrderRepository orders;

    public OrderProjection(OrderRepository orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = Channels.PAYMENTS, groupId = "order-payments")
    @Transactional
    public void onPayment(PaymentEvent event) {
        if ("DECLINED".equals(event.status())) {
            orders.markDeclined(event.orderId());
        } else if ("AUTHORIZED".equals(event.status())) {
            orders.markAuthorized(event.orderId());
        }
    }

    @KafkaListener(topics = Channels.STOCK, groupId = "order-stock")
    @Transactional
    public void onStock(StockEvent event) {
        if ("ALLOCATED".equals(event.status())) {
            orders.markAllocated(event.orderId());
        } else if ("RELEASED".equals(event.status())) {
            orders.markCancelled(event.orderId());
        }
    }
}
