package com.fooddelivery.restaurant.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ValidatedItemDto(
    UUID itemId,
    String name,
    BigDecimal price,
    BigDecimal optionPrice,
    BigDecimal subtotal,
    Boolean available,
    String message
) {}
