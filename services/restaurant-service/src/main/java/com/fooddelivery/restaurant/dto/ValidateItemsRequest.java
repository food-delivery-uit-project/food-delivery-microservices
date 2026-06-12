package com.fooddelivery.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ValidateItemsRequest(
    @NotEmpty(message = "Items list must not be empty")
    @Valid
    List<ItemValidationInput> items
) {}
