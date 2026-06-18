package com.fooddelivery.order.application;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderStatus;
import com.fooddelivery.order.domain.port.inbound.UpdateOrderStatusUseCase;
import com.fooddelivery.order.domain.port.outbound.OrderRepository;
import com.fooddelivery.order.domain.port.outbound.EventPublisher;
import com.fooddelivery.order.domain.event.OrderCancelledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application Service: Update Order Status Use Case implementation.
 * Used by Kafka consumers to update order state from external events.
 */
public class UpdateOrderStatusApplicationService implements UpdateOrderStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateOrderStatusApplicationService.class);

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public UpdateOrderStatusApplicationService(OrderRepository orderRepository, EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(UpdateStatusCommand command) {
        log.info("Updating order status orderId={} newStatus={}", command.orderId(), command.newStatus());

        Order order = orderRepository.findById(command.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND",
                "Order not found: " + command.orderId()));

        if (command.newStatus() == OrderStatus.DRIVER_ASSIGNED && command.driverId() != null) {
            order.assignDriver(command.driverId());
        } else {
            order.transitionTo(command.newStatus());
        }

        Order saved = orderRepository.save(order);
        
        // Saga Orchestration: If cancelled, trigger compensating transactions
        if (command.newStatus() == OrderStatus.CANCELLED) {
            log.info("Saga Orchestration: Order {} cancelled. Emitting OrderCancelledEvent for compensations.", order.getId());
            OrderCancelledEvent event = new OrderCancelledEvent(
                saved.getId(),
                saved.getCustomerId(),
                saved.getRestaurantId(),
                "Order cancelled by Saga Orchestrator due to failure.",
                saved.getStatus().name()
            );
            eventPublisher.publish("order-events", saved.getId().toString(), event);
        }

        log.info("Order status updated orderId={} status={}", order.getId(), order.getStatus());
    }
}
