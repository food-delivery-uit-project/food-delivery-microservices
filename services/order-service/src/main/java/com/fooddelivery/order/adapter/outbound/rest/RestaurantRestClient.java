package com.fooddelivery.order.adapter.outbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.exception.BusinessException;
import com.fooddelivery.order.domain.port.outbound.RestaurantClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Outbound Adapter: REST client for Restaurant Service.
 * Implements RestaurantClient port with Circuit Breaker + Retry.
 */
@Component
public class RestaurantRestClient implements RestaurantClient {

    private static final Logger log = LoggerFactory.getLogger(RestaurantRestClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public RestaurantRestClient(
        WebClient.Builder webClientBuilder,
        @Value("${internal.restaurant-service-url}") String restaurantServiceUrl,
        ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.baseUrl(restaurantServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "restaurant-service", fallbackMethod = "validateItemsFallback")
    @Retry(name = "restaurant-service")
    public List<ValidatedItem> validateItems(UUID restaurantId, List<ItemRequest> items) {
        log.info("Validating items with restaurant-service restaurantId={}", restaurantId);

        try {
            // Build request body
            List<java.util.Map<String, Object>> itemList = items.stream()
                .map(i -> java.util.Map.<String, Object>of(
                    "item_id", i.itemId().toString(),
                    "quantity", i.quantity()
                ))
                .toList();

            String responseBody = webClient.post()
                .uri("/api/internal/restaurants/{restaurantId}/validate-items", restaurantId)
                .bodyValue(java.util.Map.of("items", itemList))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            // ValidateItemsResponse: { valid, subtotal, items: [{itemId, name, price, quantity, available}] }
            JsonNode itemsNode = data.path("items");

            List<ValidatedItem> result = new ArrayList<>();
            // Need to match items back to requested quantities (response doesn't include quantity)
            java.util.Map<String, ItemRequest> requestMap = items.stream()
                .collect(java.util.stream.Collectors.toMap(i -> i.itemId().toString(), i -> i));

            for (JsonNode item : itemsNode) {
                String itemIdStr = item.path("item_id").asText();
                ItemRequest req = requestMap.get(itemIdStr);
                int quantity = req != null ? req.quantity() : 1;

                // Check availability
                if (!item.path("available").asBoolean(true)) {
                    throw new BusinessException("ITEM_UNAVAILABLE",
                        "Item " + item.path("name").asText() + " is not available");
                }

                result.add(new ValidatedItem(
                    UUID.fromString(itemIdStr),
                    item.path("name").asText(),
                    quantity,
                    new BigDecimal(item.path("price").asText("0"))
                ));
            }
            return result;

        } catch (WebClientResponseException.NotFound e) {
            throw new BusinessException("RESTAURANT_NOT_FOUND", "Restaurant or items not found");
        } catch (Exception e) {
            log.error("Failed to validate items with restaurant-service", e);
            throw new RuntimeException("Failed to validate items", e);
        }
    }

    public List<ValidatedItem> validateItemsFallback(UUID restaurantId, List<ItemRequest> items, Exception ex) {
        log.warn("Circuit breaker fallback for restaurant-service: {}", ex.getMessage());
        throw new BusinessException("RESTAURANT_SERVICE_UNAVAILABLE",
            "Restaurant service is temporarily unavailable. Please try again.");
    }
}
