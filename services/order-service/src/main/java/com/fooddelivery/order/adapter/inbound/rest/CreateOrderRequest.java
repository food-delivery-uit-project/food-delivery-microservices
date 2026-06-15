package com.fooddelivery.order.adapter.inbound.rest;

import com.fooddelivery.order.domain.model.DeliveryAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating an order.
 */
public record CreateOrderRequest(
    @NotNull UUID restaurantId,
    @NotNull @NotEmpty List<OrderItemInput> items,
    @NotNull @Valid DeliveryAddressInput deliveryAddress,
    String specialInstructions
) {
    public record OrderItemInput(
        @NotNull UUID itemId,
        @Positive int quantity
    ) {}

    public record DeliveryAddressInput(
        @NotNull String addressLine,
        @NotNull Double lat,
        @NotNull Double lng
    ) {
        public DeliveryAddress toDomain() {
            return new DeliveryAddress(addressLine, lat, lng);
        }
    }
}
