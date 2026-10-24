package com.checkout.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "card_holds")
public class HoldEntity {

    @Id
    @Column(name = "order_id")
    private String orderId;
    @Column(name = "amount_cents")
    private int amountCents;
    private String status;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getAmountCents() { return amountCents; }
    public void setAmountCents(int amountCents) { this.amountCents = amountCents; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
