package com.fooddelivery.restaurant.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fooddelivery.restaurant.common.ApiResponse;
import com.fooddelivery.restaurant.dto.CreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantDto;
import com.fooddelivery.restaurant.kafka.RestaurantEventProducer;
import com.fooddelivery.restaurant.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants")
@Tag(name = "Restaurants", description = "Endpoints for restaurant profile and status management")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final RestaurantEventProducer eventProducer;

    public RestaurantController(RestaurantService restaurantService, RestaurantEventProducer eventProducer) {
        this.restaurantService = restaurantService;
        this.eventProducer = eventProducer;
    }

    @GetMapping
    @Operation(summary = "Search and list active restaurants", description = "Filter by cuisine type and query search term with pagination.")
    public ResponseEntity<ApiResponse<List<RestaurantDto>>> searchRestaurants(
            @RequestParam(required = false) String cuisine,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<RestaurantDto> pageResult = restaurantService.searchRestaurants(cuisine, search, PageRequest.of(page, size));
        ApiResponse.Pagination pagination = new ApiResponse.Pagination(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.ok(pageResult.getContent(), pagination));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get restaurant by ID", description = "Retrieve complete details of a single restaurant.")
    public ResponseEntity<ApiResponse<RestaurantDto>> getRestaurant(@PathVariable UUID id) {
        RestaurantDto restaurant = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(ApiResponse.ok(restaurant));
    }

    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Create a new restaurant", description = "Allows restaurant owners to register a restaurant profile. Requires role RESTAURANT_OWNER.")
    @SecurityRequirement(name = "X-User-Id")
    @SecurityRequirement(name = "X-User-Role")
    public ResponseEntity<ApiResponse<RestaurantDto>> createRestaurant(
            @Parameter(hidden = true) @AuthenticationPrincipal String ownerIdStr,
            @Valid @RequestBody CreateRestaurantRequest request) {

        UUID ownerId = UUID.fromString(ownerIdStr);
        RestaurantDto created = restaurantService.createRestaurant(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Open or close restaurant", description = "Allows restaurant owners to modify open/close status. Triggers Kafka event.")
    @SecurityRequirement(name = "X-User-Id")
    @SecurityRequirement(name = "X-User-Role")
    public ResponseEntity<ApiResponse<RestaurantDto>> patchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdatePayload payload) {

        RestaurantDto updated = restaurantService.updateStatus(id, payload.isActive());
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @PostMapping("/{id}/orders/{orderId}/accept")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Confirm/Accept order", description = "Confirm and accept a customer's order. Publishes OrderAccepted to Kafka.")
    @SecurityRequirement(name = "X-User-Id")
    @SecurityRequirement(name = "X-User-Role")
    public ResponseEntity<ApiResponse<Void>> acceptOrder(
            @PathVariable UUID id,
            @PathVariable UUID orderId) {

        eventProducer.publishOrderAccepted(orderId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/orders/{orderId}/ready")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Mark order ready", description = "Mark order as prepared and ready for rider pickup. Publishes OrderReadyForPickup to Kafka.")
    @SecurityRequirement(name = "X-User-Id")
    @SecurityRequirement(name = "X-User-Role")
    public ResponseEntity<ApiResponse<Void>> readyOrder(
            @PathVariable UUID id,
            @PathVariable UUID orderId) {

        eventProducer.publishOrderReadyForPickup(orderId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public static class StatusUpdatePayload {
        @JsonAlias({"isActive", "is_active"})
        private Boolean isActive;

        public StatusUpdatePayload() {}

        public StatusUpdatePayload(Boolean isActive) {
            this.isActive = isActive;
        }

        public Boolean isActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }
    }
}
