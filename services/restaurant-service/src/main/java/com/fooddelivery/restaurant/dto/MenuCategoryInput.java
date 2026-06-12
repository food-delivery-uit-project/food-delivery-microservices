package com.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record MenuCategoryInput(
    @NotBlank(message = "Category name must not be blank")
    String name,

    @JsonAlias("sortOrder")
    Integer sortOrder,

    @Valid
    List<MenuItemInput> items
) {}
