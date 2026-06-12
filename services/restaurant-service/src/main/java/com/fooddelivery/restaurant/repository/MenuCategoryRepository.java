package com.fooddelivery.restaurant.repository;

import com.fooddelivery.restaurant.model.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {
    List<MenuCategory> findAllByRestaurantIdOrderBySortOrderAsc(UUID restaurantId);
    void deleteAllByRestaurantId(UUID restaurantId);
}
