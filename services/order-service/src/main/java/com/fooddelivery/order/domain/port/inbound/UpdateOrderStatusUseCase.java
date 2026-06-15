package com.fooddelivery.order.domain.port.inbound;

import com.fooddelivery.order.domain.model.OrderStatus;

import java.util.UUID;

/**
 * Inbound port: Use case for updating order status.
 * Used by Kafka consumers (PaymentEventConsumer, RestaurantEventConsumer, DeliveryEventConsumer).
 */
public interface UpdateOrderStatusUseCase {

    record UpdateStatusCommand(UUID orderId, OrderStatus newStatus, UUID driverId) {

        /** For status updates that don't involve driver assignment */
        public static UpdateStatusCommand of(UUID orderId, OrderStatus newStatus) {
            return new UpdateStatusCommand(orderId, newStatus, null);
        }

        /** For driver assignment */
        public static UpdateStatusCommand withDriver(UUID orderId, UUID driverId) {
            return new UpdateStatusCommand(orderId, OrderStatus.DRIVER_ASSIGNED, driverId);
        }
    }

    void execute(UpdateStatusCommand command);
}
