package com.fooddelivery.restaurant.dto;

import java.util.List;
import java.util.UUID;

public record MenuCategoryResponse(
    UUID id,
    String name,
    Integer sortOrder,
    List<MenuItemResponse> items
) {}
