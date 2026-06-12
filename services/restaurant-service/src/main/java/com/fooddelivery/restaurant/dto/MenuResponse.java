package com.fooddelivery.restaurant.dto;

import java.util.List;

public record MenuResponse(
    List<MenuCategoryResponse> categories
) {}
