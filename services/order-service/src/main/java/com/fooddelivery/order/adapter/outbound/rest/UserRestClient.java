package com.fooddelivery.order.adapter.outbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.domain.port.outbound.UserClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * Outbound Adapter: REST client for User Service.
 * Implements UserClient port with Circuit Breaker + Retry.
 */
@Component
public class UserRestClient implements UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserRestClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public UserRestClient(
        WebClient.Builder webClientBuilder,
        @Value("${internal.user-service-url}") String userServiceUrl,
        ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.baseUrl(userServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserByIdFallback")
    @Retry(name = "user-service")
    public UserInfo getUserById(UUID userId) {
        log.debug("Fetching user from user-service userId={}", userId);

        try {
            String responseBody = webClient.get()
                .uri("/api/internal/users/{userId}", userId)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");

            return new UserInfo(
                UUID.fromString(data.path("id").asText()),
                data.path("email").asText(),
                data.path("fullName").asText(),
                data.path("role").asText(),
                data.path("isActive").asBoolean(true)
            );
        } catch (Exception e) {
            log.error("Failed to fetch user from user-service userId={}", userId, e);
            return null;
        }
    }

    public UserInfo getUserByIdFallback(UUID userId, Exception ex) {
        log.warn("Circuit breaker fallback for user-service userId={}: {}", userId, ex.getMessage());
        return null;
    }
}
