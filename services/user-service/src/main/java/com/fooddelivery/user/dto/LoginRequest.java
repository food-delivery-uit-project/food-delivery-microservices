package com.fooddelivery.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address")
    @Schema(defaultValue = "customer@example.com")    
    String email,

    @NotBlank(message = "Password must not be blank")
    @Schema(defaultValue = "strongpassword123")
    String password
) {}
