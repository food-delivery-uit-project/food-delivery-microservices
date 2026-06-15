package com.fooddelivery.order.domain.port.outbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port: Client for querying Restaurant Service.
 * Implemented by REST adapter (RestaurantRestClient).
 *
 * NOTE: Pure Java interface — no framework imports.
 */
public interface RestaurantClient {

    /**
     * Validate and retrieve item details from the restaurant.
     * Returns a list of validated items with current prices.
     */
    List<ValidatedItem> validateItems(UUID restaurantId, List<ItemRequest> items);

    record ItemRequest(UUID itemId, int quantity) {}

    record ValidatedItem(UUID itemId, String name, int quantity, BigDecimal unitPrice) {}
}
