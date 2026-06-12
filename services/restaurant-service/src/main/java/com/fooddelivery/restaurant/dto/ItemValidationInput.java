package com.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ItemValidationInput(
    @NotNull(message = "Item ID must not be null")
    @JsonAlias({"itemId", "item_id"})
    UUID itemId,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity,

    @Valid
    @JsonAlias({"selectedOptions", "selected_options"})
    List<SelectedOptionInput> selectedOptions
) {}
