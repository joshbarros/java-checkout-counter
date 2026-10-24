package com.checkout.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PaymentController {

    private final HoldRepository holds;

    public PaymentController(HoldRepository holds) {
        this.holds = holds;
    }

    @GetMapping("/holds/{orderId}")
    public HoldEntity hold(@PathVariable String orderId) {
        return holds.findById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "hold not found"));
    }
}
