package com.checkout.order;

import java.util.UUID;

import com.checkout.common.AuthorizeCommand;
import com.checkout.common.Channels;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrderController {

    private final OrderRepository orders;
    private final RabbitTemplate rabbit;

    public OrderController(OrderRepository orders, RabbitTemplate rabbit) {
        this.orders = orders;
        this.rabbit = rabbit;
    }

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<OrderEntity> create(@RequestBody CreateOrderRequest request) {
        if (request == null || blank(request.sku()) || blank(request.cardToken())
                || request.quantity() <= 0 || request.unitPriceCents() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sku, quantity, unitPriceCents, and cardToken are required");
        }
        int amount;
        try {
            amount = Math.multiplyExact(request.quantity(), request.unitPriceCents());
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is too large");
        }
        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID().toString());
        order.setSku(request.sku().trim());
        order.setQuantity(request.quantity());
        order.setAmountCents(amount);
        order.setStatus("PENDING");
        orders.save(order);
        String card = request.cardToken().trim();
        Publish.afterCommit(() -> rabbit.convertAndSend(Channels.AUTHORIZE, new AuthorizeCommand(
                order.getId(), order.getSku(), order.getQuantity(), order.getAmountCents(), card)));
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/orders/{id}")
    public OrderEntity order(@PathVariable String id) {
        return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record CreateOrderRequest(String sku, int quantity, int unitPriceCents, String cardToken) {
    }
}
