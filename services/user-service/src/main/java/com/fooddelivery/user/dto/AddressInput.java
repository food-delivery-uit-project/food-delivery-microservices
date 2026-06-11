package com.fooddelivery.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddressInput(
    String label,

    @NotBlank(message = "Address line must not be blank")
    @JsonAlias("addressLine")
    String addressLine,

    @NotNull(message = "Latitude must not be null")
    Double lat,

    @NotNull(message = "Longitude must not be null")
    Double lng,

    @JsonAlias("isDefault")
    Boolean isDefault
) {}
