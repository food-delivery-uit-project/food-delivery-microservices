package com.fooddelivery.user.dto;

import java.util.UUID;

public record AddressResponse(
    UUID id,
    String label,
    String addressLine,
    Double lat,
    Double lng,
    boolean isDefault
) {}
