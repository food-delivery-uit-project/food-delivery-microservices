package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.common.ApiResponse;
import com.fooddelivery.restaurant.dto.ValidateItemsRequest;
import com.fooddelivery.restaurant.dto.ValidateItemsResponse;
import com.fooddelivery.restaurant.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/restaurants")
@Tag(name = "Internal Restaurants", description = "Service-to-service internal endpoints for validation and billing calculations")
public class InternalRestaurantController {

    private final MenuService menuService;

    public InternalRestaurantController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping("/{id}/validate-items")
    @Operation(summary = "[Internal] Validate menu items and calculate total cost", 
               description = "Accepts list of items, quantities, and selected options to calculate total subtotal with topping price adjustments. Open to internal callers.")
    public ResponseEntity<ApiResponse<ValidateItemsResponse>> validateItems(
            @PathVariable UUID id,
            @Valid @RequestBody ValidateItemsRequest request) {

        ValidateItemsResponse response = menuService.validateAndCalculateSubtotal(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
