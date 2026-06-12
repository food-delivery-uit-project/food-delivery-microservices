package com.fooddelivery.restaurant.dto;

import java.math.BigDecimal;
import java.util.List;

public record ValidateItemsResponse(
    Boolean valid,
    BigDecimal subtotal,
    List<ValidatedItemDto> items
) {}
