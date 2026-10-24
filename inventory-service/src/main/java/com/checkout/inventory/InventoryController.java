package com.checkout.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InventoryController {

    private final SkuRepository skus;
    private final AllocationRepository allocations;

    public InventoryController(SkuRepository skus, AllocationRepository allocations) {
        this.skus = skus;
        this.allocations = allocations;
    }

    @PostMapping("/skus")
    public ResponseEntity<SkuEntity> create(@RequestBody CreateSkuRequest request) {
        if (request == null || request.sku() == null || request.sku().isBlank() || request.onHand() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sku and onHand are required");
        }
        String sku = request.sku().trim();
        if (skus.existsById(sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sku exists");
        }
        SkuEntity row = new SkuEntity();
        row.setSku(sku);
        row.setOnHand(request.onHand());
        return ResponseEntity.status(HttpStatus.CREATED).body(skus.save(row));
    }

    @GetMapping("/skus/{sku}")
    public SkuEntity sku(@PathVariable String sku) {
        return skus.findById(sku).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
    }

    @GetMapping("/allocations/{orderId}")
    public AllocationEntity allocation(@PathVariable String orderId) {
        return allocations.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "allocation not found"));
    }

    public record CreateSkuRequest(String sku, int onHand) {
    }
}
