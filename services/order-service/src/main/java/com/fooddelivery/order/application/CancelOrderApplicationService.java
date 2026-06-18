package com.fooddelivery.order.application;

import com.fooddelivery.order.domain.event.OrderCancelledEvent;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.port.inbound.CancelOrderUseCase;
import com.fooddelivery.order.domain.port.outbound.EventPublisher;
import com.fooddelivery.order.domain.port.outbound.OrderRepository;
import com.fooddelivery.order.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Application Service: Cancel Order Use Case implementation.
 */
public class CancelOrderApplicationService implements CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderApplicationService.class);
    private static final String TOPIC_ORDER_EVENTS = "order-events";

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public CancelOrderApplicationService(OrderRepository orderRepository, EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(UUID orderId, UUID requesterId) {
        log.info("Cancelling order orderId={} by requester={}", orderId, requesterId);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND",
                "Order not found: " + orderId));

        // Authorization: only owner can cancel (simplified check)
        if (!order.getCustomerId().equals(requesterId)) {
            throw new BusinessException("CANCEL_FORBIDDEN", "You can only cancel your own orders");
        }

        order.cancel();
        orderRepository.save(order);

        OrderCancelledEvent event = new OrderCancelledEvent(
            order.getId(), order.getCustomerId(), order.getRestaurantId(),
            "Customer requested cancellation", "CUSTOMER"
        );
        eventPublisher.publish(TOPIC_ORDER_EVENTS, orderId.toString(), event);

        log.info("Order cancelled orderId={}", orderId);
    }
}
