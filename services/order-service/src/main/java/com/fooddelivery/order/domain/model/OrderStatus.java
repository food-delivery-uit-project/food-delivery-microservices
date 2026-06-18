package com.fooddelivery.order.domain.model;

/**
 * Order status enum with allowed transitions.
 * Represents the state machine for order lifecycle.
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    RESTAURANT_ACCEPTED,
    PREPARING,
    READY_FOR_PICKUP,
    DRIVER_ASSIGNED,
    PICKED_UP,
    DELIVERED,
    CANCELLED;

    /**
     * Check if transition from current status to target status is allowed.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PAYMENT_PENDING || target == CANCELLED;
            case PAYMENT_PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == RESTAURANT_ACCEPTED || target == CANCELLED;
            case RESTAURANT_ACCEPTED -> target == PREPARING || target == READY_FOR_PICKUP;
            case PREPARING -> target == READY_FOR_PICKUP;
            case READY_FOR_PICKUP -> target == DRIVER_ASSIGNED;
            case DRIVER_ASSIGNED -> target == PICKED_UP;
            case PICKED_UP -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /**
     * Check if order can be cancelled from current status.
     */
    public boolean isCancellable() {
        return this == CREATED || this == PAYMENT_PENDING || this == PAID;
    }
}
