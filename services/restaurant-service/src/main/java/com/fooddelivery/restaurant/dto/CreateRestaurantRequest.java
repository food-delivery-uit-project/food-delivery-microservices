package com.fooddelivery.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fooddelivery.restaurant.model.DayHours;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record CreateRestaurantRequest(
    @NotBlank(message = "Restaurant name must not be blank")
    String name,

    String description,

    @NotBlank(message = "Address line must not be blank")
    @JsonAlias("addressLine")
    String addressLine,

    @NotNull(message = "Latitude must not be null")
    Double lat,

    @NotNull(message = "Longitude must not be null")
    Double lng,

    @JsonAlias("cuisineTypes")
    List<String> cuisineTypes,

    @JsonAlias("operatingHours")
    Map<String, DayHours> operatingHours,

    @JsonAlias("imageUrl")
    String imageUrl
) {}
