package com.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fooddelivery.restaurant.model.MenuItemOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record MenuItemInput(
    @NotBlank(message = "Item name must not be blank")
    String name,

    String description,

    @NotNull(message = "Price must not be null")
    BigDecimal price,

    @JsonAlias("imageUrl")
    String imageUrl,

    @JsonAlias("isAvailable")
    Boolean isAvailable,

    List<MenuItemOption> options
) {}
