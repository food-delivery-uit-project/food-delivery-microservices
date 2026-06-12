package com.fooddelivery.restaurant.service;

import com.fooddelivery.restaurant.dto.CreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantDto;
import com.fooddelivery.restaurant.exception.ResourceNotFoundException;
import com.fooddelivery.restaurant.kafka.RestaurantEventProducer;
import com.fooddelivery.restaurant.model.Restaurant;
import com.fooddelivery.restaurant.repository.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantEventProducer eventProducer;

    public RestaurantService(RestaurantRepository restaurantRepository, RestaurantEventProducer eventProducer) {
        this.restaurantRepository = restaurantRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public RestaurantDto createRestaurant(UUID ownerId, CreateRestaurantRequest request) {
        String[] cuisineArray = null;
        if (request.cuisineTypes() != null) {
            cuisineArray = request.cuisineTypes().toArray(new String[0]);
        } else {
            cuisineArray = new String[0];
        }

        Restaurant restaurant = new Restaurant(
                null,
                ownerId,
                request.name(),
                request.description(),
                request.addressLine(),
                request.lat(),
                request.lng(),
                cuisineArray,
                request.operatingHours(),
                true,
                BigDecimal.valueOf(0.0),
                0,
                request.imageUrl()
        );

        Restaurant saved = restaurantRepository.save(restaurant);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public RestaurantDto getRestaurantById(UUID id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RESTAURANT_NOT_FOUND", "Restaurant with id '" + id + "' not found"));
        return mapToDto(restaurant);
    }

    @Transactional(readOnly = true)
    public Page<RestaurantDto> searchRestaurants(String cuisine, String search, Pageable pageable) {
        Page<Restaurant> page = restaurantRepository.searchRestaurants(cuisine, search, pageable);
        return page.map(this::mapToDto);
    }

    @Transactional
    public RestaurantDto updateStatus(UUID id, boolean isActive) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RESTAURANT_NOT_FOUND", "Restaurant with id '" + id + "' not found"));
        
        restaurant.setIsActive(isActive);
        Restaurant saved = restaurantRepository.save(restaurant);

        // Publish event to Kafka
        eventProducer.publishRestaurantStatusChanged(id, isActive);

        return mapToDto(saved);
    }

    public RestaurantDto mapToDto(Restaurant restaurant) {
        List<String> cuisines = restaurant.getCuisineTypes() != null 
                ? Arrays.asList(restaurant.getCuisineTypes()) 
                : Collections.emptyList();

        return new RestaurantDto(
                restaurant.getId(),
                restaurant.getOwnerId(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getAddressLine(),
                restaurant.getLat(),
                restaurant.getLng(),
                cuisines, // Wait: typo in name! cuisines
                restaurant.getOperatingHours(),
                restaurant.getIsActive(),
                restaurant.getAvgRating(),
                restaurant.getTotalReviews(),
                restaurant.getImageUrl(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt()
        );
    }
}
