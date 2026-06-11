package com.fooddelivery.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token must not be blank")
    @JsonAlias("refreshToken")
    String refreshToken
) {}
