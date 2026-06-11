package com.fooddelivery.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fooddelivery.user.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address")
    String email,

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    String password,

    @NotBlank(message = "Full name must not be blank")
    @JsonAlias("fullName")
    String fullName,

    String phone,

    @NotNull(message = "Role must not be null")
    UserRole role
) {}
