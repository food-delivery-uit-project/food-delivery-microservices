package com.fooddelivery.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
    @NotBlank(message = "Full name must not be blank")
    @JsonAlias("fullName")
    String fullName,

    String phone
) {}
