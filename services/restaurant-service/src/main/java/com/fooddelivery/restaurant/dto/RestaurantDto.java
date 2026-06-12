package com.fooddelivery.restaurant.dto;

import com.fooddelivery.restaurant.model.DayHours;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RestaurantDto(
    UUID id,
    UUID ownerId,
    String name,
    String description,
    String addressLine,
    Double lat,
    Double lng,
    List<String> cuisineTypes,
    Map<String, DayHours> operatingHours,
    Boolean isActive,
    BigDecimal avgRating,
    Integer totalReviews,
    String imageUrl,
    Instant createdAt,
    Instant updatedAt
) {}
