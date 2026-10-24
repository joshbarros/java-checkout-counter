package com.checkout.inventory;

import com.checkout.common.AllocateCommand;
import com.checkout.common.Channels;
import com.checkout.common.StockEvent;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryWorker {

    private final SkuRepository skus;
    private final AllocationRepository allocations;
    private final KafkaTemplate<String, Object> kafka;

    public InventoryWorker(SkuRepository skus, AllocationRepository allocations, KafkaTemplate<String, Object> kafka) {
        this.skus = skus;
        this.allocations = allocations;
        this.kafka = kafka;
    }

    @RabbitListener(queues = Channels.ALLOCATE, concurrency = "4")
    @Transactional
    public void onAllocate(AllocateCommand command) {
        if (allocations.existsById(command.orderId())) {
            return;
        }
        if (skus.decrement(command.sku(), command.quantity()) == 0) {
            Publish.kafka(kafka, Channels.STOCK, command.orderId(), new StockEvent(command.orderId(), "RELEASED"));
            throw new AmqpRejectAndDontRequeueException("out of stock");
        }
        AllocationEntity row = new AllocationEntity();
        row.setOrderId(command.orderId());
        row.setSku(command.sku());
        row.setQuantity(command.quantity());
        row.setStatus("ALLOCATED");
        allocations.save(row);
        Publish.kafka(kafka, Channels.STOCK, command.orderId(), new StockEvent(command.orderId(), "ALLOCATED"));
    }
}
