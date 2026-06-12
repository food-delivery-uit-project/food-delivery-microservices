package com.fooddelivery.restaurant.repository;

import com.fooddelivery.restaurant.model.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    @Query(value = "SELECT * FROM restaurants r WHERE r.is_active = true " +
            "AND (:cuisine IS NULL OR :cuisine = ANY(r.cuisine_types)) " +
            "AND (:search IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%')))",
            countQuery = "SELECT count(*) FROM restaurants r WHERE r.is_active = true " +
                    "AND (:cuisine IS NULL OR :cuisine = ANY(r.cuisine_types)) " +
                    "AND (:search IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%')))",
            nativeQuery = true)
    Page<Restaurant> searchRestaurants(
            @Param("cuisine") String cuisine,
            @Param("search") String search,
            Pageable pageable);
}
