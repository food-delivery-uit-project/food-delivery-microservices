package com.fooddelivery.user.dto;

import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    int expiresIn,
    UUID userId
) {}
