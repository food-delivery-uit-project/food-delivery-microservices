package com.fooddelivery.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateMenuRequest(
    @NotEmpty(message = "Categories list must not be empty")
    @Valid
    List<MenuCategoryInput> categories
) {}
