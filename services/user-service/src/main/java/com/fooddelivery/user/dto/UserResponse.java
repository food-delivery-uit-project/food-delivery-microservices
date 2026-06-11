package com.fooddelivery.user.dto;

import com.fooddelivery.user.model.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    UserRole role,
    boolean isActive,
    Instant createdAt
) {}
