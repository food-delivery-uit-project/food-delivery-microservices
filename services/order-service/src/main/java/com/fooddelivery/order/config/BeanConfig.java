package com.fooddelivery.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.adapter.outbound.messaging.KafkaEventPublisher;
import com.fooddelivery.order.adapter.outbound.persistence.JpaOrderRepository;
import com.fooddelivery.order.adapter.outbound.persistence.SpringDataOrderRepository;
import com.fooddelivery.order.adapter.outbound.persistence.SpringDataOutboxRepository;
import com.fooddelivery.order.adapter.outbound.rest.RestaurantRestClient;
import com.fooddelivery.order.application.CancelOrderApplicationService;
import com.fooddelivery.order.application.CreateOrderApplicationService;
import com.fooddelivery.order.application.GetOrderQueryService;
import com.fooddelivery.order.application.UpdateOrderStatusApplicationService;
import com.fooddelivery.order.domain.port.inbound.CancelOrderUseCase;
import com.fooddelivery.order.domain.port.inbound.CreateOrderUseCase;
import com.fooddelivery.order.domain.port.inbound.GetOrderQuery;
import com.fooddelivery.order.domain.port.inbound.UpdateOrderStatusUseCase;
import com.fooddelivery.order.domain.port.outbound.EventPublisher;
import com.fooddelivery.order.domain.port.outbound.OrderRepository;
import com.fooddelivery.order.domain.port.outbound.RestaurantClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot wiring configuration: connects domain ports to their adapters.
 *
 * This is the "glue" that makes Hexagonal Architecture work with Spring.
 * Domain knows NOTHING about this class.
 */
@Configuration
public class BeanConfig {

    // ===== Outbound Adapters =====

    @Bean
    public OrderRepository orderRepository(SpringDataOrderRepository springDataOrderRepository) {
        return new JpaOrderRepository(springDataOrderRepository);
    }

    @Bean
    public EventPublisher eventPublisher(
        SpringDataOutboxRepository outboxRepository,
        ObjectMapper objectMapper
    ) {
        return new KafkaEventPublisher(outboxRepository, objectMapper);
    }

    // ===== Application Use Cases (wired with ports) =====

    @Bean
    public CreateOrderUseCase createOrderUseCase(
        OrderRepository orderRepository,
        EventPublisher eventPublisher,
        RestaurantClient restaurantClient
    ) {
        return new CreateOrderApplicationService(orderRepository, eventPublisher, restaurantClient);
    }

    @Bean
    public CancelOrderUseCase cancelOrderUseCase(
        OrderRepository orderRepository,
        EventPublisher eventPublisher
    ) {
        return new CancelOrderApplicationService(orderRepository, eventPublisher);
    }

    @Bean
    public UpdateOrderStatusUseCase updateOrderStatusUseCase(OrderRepository orderRepository, EventPublisher eventPublisher) {
        return new UpdateOrderStatusApplicationService(orderRepository, eventPublisher);
    }

    @Bean
    public GetOrderQuery getOrderQuery(OrderRepository orderRepository) {
        return new GetOrderQueryService(orderRepository);
    }
}
