package com.fooddelivery.restaurant.dto;

import com.fooddelivery.restaurant.model.MenuItemOption;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MenuItemResponse(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    Boolean isAvailable,
    List<MenuItemOption> options
) {}
