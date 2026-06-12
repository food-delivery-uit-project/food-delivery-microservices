package com.fooddelivery.restaurant.kafka;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentEvent {
    private String eventId;
    private String type;
    private UUID orderId;
    private UUID userId;
    private BigDecimal amount;

    public PaymentEvent() {}

    public PaymentEvent(String eventId, String type, UUID orderId, UUID userId, BigDecimal amount) {
        this.eventId = eventId;
        this.type = type;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "PaymentEvent{" +
                "eventId='" + eventId + '\'' +
                ", type='" + type + '\'' +
                ", orderId=" + orderId +
                ", userId=" + userId +
                ", amount=" + amount +
                '}';
    }
}
