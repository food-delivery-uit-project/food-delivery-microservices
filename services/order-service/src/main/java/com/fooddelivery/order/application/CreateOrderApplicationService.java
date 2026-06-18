package com.fooddelivery.order.application;

import com.fooddelivery.order.domain.event.OrderCreatedEvent;
import com.fooddelivery.order.domain.model.DeliveryAddress;
import com.fooddelivery.order.domain.model.Money;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderItem;
import com.fooddelivery.order.domain.port.inbound.CreateOrderUseCase;
import com.fooddelivery.order.domain.port.outbound.EventPublisher;
import com.fooddelivery.order.domain.port.outbound.OrderRepository;
import com.fooddelivery.order.domain.port.outbound.RestaurantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Application Service: Create Order Use Case implementation.
 *
 * Orchestrates:
 * 1. Validate items via RestaurantClient (outbound port)
 * 2. Create Order aggregate (domain model)
 * 3. Save via OrderRepository (outbound port)
 * 4. Publish OrderCreatedEvent (outbound port)
 */
public class CreateOrderApplicationService implements CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderApplicationService.class);

    private static final String TOPIC_ORDER_EVENTS = "order-events";

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final RestaurantClient restaurantClient;

    public CreateOrderApplicationService(
        OrderRepository orderRepository,
        EventPublisher eventPublisher,
        RestaurantClient restaurantClient
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.restaurantClient = restaurantClient;
    }

    @Override
    public UUID execute(CreateOrderCommand command) {
        log.info("Creating order for customer={} restaurant={}",
            command.customerId(), command.restaurantId());

        // 1. Validate items with Restaurant Service
        List<RestaurantClient.ItemRequest> itemRequests = command.items().stream()
            .map(i -> new RestaurantClient.ItemRequest(i.itemId(), i.quantity()))
            .toList();

        List<RestaurantClient.ValidatedItem> validatedItems =
            restaurantClient.validateItems(command.restaurantId(), itemRequests);

        // 2. Map validated items to domain model
        List<OrderItem> orderItems = validatedItems.stream()
            .map(v -> new OrderItem(
                v.itemId(),
                v.name(),
                v.quantity(),
                Money.of(v.unitPrice(), "VND")
            ))
            .toList();

        // 3. Create Order aggregate (domain business logic)
        Order order = Order.create(
            command.customerId(),
            command.restaurantId(),
            orderItems,
            command.deliveryAddress(),
            command.specialInstructions()
        );
        order.transitionTo(com.fooddelivery.order.domain.model.OrderStatus.PAYMENT_PENDING);

        // 4. Persist (via outbox pattern inside JpaOrderRepository)
        Order saved = orderRepository.save(order);

        // 5. Publish OrderCreated event (via outbox relay)
        OrderCreatedEvent event = new OrderCreatedEvent(
            saved.getId(),
            saved.getCustomerId(),
            saved.getRestaurantId(),
            saved.getTotalAmount().amount(),
            "COD",           // default payment method for demo
            saved.getItems(),
            saved.getDeliveryAddress()
        );
        eventPublisher.publish(TOPIC_ORDER_EVENTS, saved.getId().toString(), event);

        log.info("Order created successfully orderId={}", saved.getId());
        return saved.getId();
    }
}
