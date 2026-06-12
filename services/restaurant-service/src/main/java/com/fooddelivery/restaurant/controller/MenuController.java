package com.fooddelivery.restaurant.controller;

import com.fooddelivery.restaurant.common.ApiResponse;
import com.fooddelivery.restaurant.dto.MenuResponse;
import com.fooddelivery.restaurant.dto.UpdateMenuRequest;
import com.fooddelivery.restaurant.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{id}/menu")
@Tag(name = "Menus", description = "Endpoints for restaurant menu management")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    @Operation(summary = "Get restaurant menu", description = "Retrieves all categories and nested menu items of a restaurant.")
    public ResponseEntity<ApiResponse<MenuResponse>> getMenu(@PathVariable UUID id) {
        MenuResponse menu = menuService.getMenu(id);
        return ResponseEntity.ok(ApiResponse.ok(menu));
    }

    @PutMapping
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Update or replace complete menu", description = "Atomically replaces the entire menu of the restaurant. Requires role RESTAURANT_OWNER.")
    @SecurityRequirement(name = "X-User-Id")
    @SecurityRequirement(name = "X-User-Role")
    public ResponseEntity<ApiResponse<MenuResponse>> updateMenu(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMenuRequest request) {

        MenuResponse updated = menuService.updateMenu(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }
}
