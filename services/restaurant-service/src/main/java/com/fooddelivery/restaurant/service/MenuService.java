package com.fooddelivery.restaurant.service;

import com.fooddelivery.restaurant.dto.*;
import com.fooddelivery.restaurant.exception.ResourceNotFoundException;
import com.fooddelivery.restaurant.model.MenuCategory;
import com.fooddelivery.restaurant.model.MenuItem;
import com.fooddelivery.restaurant.model.MenuItemOption;
import com.fooddelivery.restaurant.model.MenuItemOptionChoice;
import com.fooddelivery.restaurant.repository.MenuCategoryRepository;
import com.fooddelivery.restaurant.repository.MenuItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;

    public MenuService(MenuCategoryRepository menuCategoryRepository, MenuItemRepository menuItemRepository) {
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenu(UUID restaurantId) {
        List<MenuCategory> categories = menuCategoryRepository.findAllByRestaurantIdOrderBySortOrderAsc(restaurantId);
        List<MenuCategoryResponse> categoryResponses = categories.stream().map(cat -> {
            List<MenuItemResponse> itemResponses = cat.getItems() != null
                    ? cat.getItems().stream().map(this::mapItemToDto).collect(Collectors.toList())
                    : Collections.emptyList();

            return new MenuCategoryResponse(
                    cat.getId(),
                    cat.getName(),
                    cat.getSortOrder(),
                    itemResponses
            );
        }).collect(Collectors.toList());

        return new MenuResponse(categoryResponses);
    }

    @Transactional
    public MenuResponse updateMenu(UUID restaurantId, UpdateMenuRequest request) {
        // Clear existing categories (cascades to menu_items in DB)
        menuCategoryRepository.deleteAllByRestaurantId(restaurantId);

        List<MenuCategoryResponse> categoryResponses = new ArrayList<>();

        if (request.categories() != null) {
            for (MenuCategoryInput catInput : request.categories()) {
                MenuCategory category = new MenuCategory(
                        null,
                        restaurantId,
                        catInput.name(),
                        catInput.sortOrder() != null ? catInput.sortOrder() : 0
                );
                MenuCategory savedCategory = menuCategoryRepository.save(category);

                List<MenuItemResponse> itemResponses = new ArrayList<>();
                if (catInput.items() != null) {
                    for (MenuItemInput itemInput : catInput.items()) {
                        MenuItem item = new MenuItem(
                                null,
                                savedCategory.getId(),
                                restaurantId,
                                itemInput.name(),
                                itemInput.description(),
                                itemInput.price(),
                                itemInput.imageUrl(),
                                itemInput.isAvailable() != null ? itemInput.isAvailable() : true,
                                itemInput.options()
                        );
                        MenuItem savedItem = menuItemRepository.save(item);
                        itemResponses.add(mapItemToDto(savedItem));
                    }
                }
                categoryResponses.add(new MenuCategoryResponse(
                        savedCategory.getId(),
                        savedCategory.getName(),
                        savedCategory.getSortOrder(),
                        itemResponses
                ));
            }
        }

        return new MenuResponse(categoryResponses);
    }

    @Transactional(readOnly = true)
    public ValidateItemsResponse validateAndCalculateSubtotal(UUID restaurantId, ValidateItemsRequest request) {
        List<ValidatedItemDto> validatedItems = new ArrayList<>();
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        boolean allValid = true;

        if (request.items() != null) {
            for (ItemValidationInput input : request.items()) {
                Optional<MenuItem> itemOpt = menuItemRepository.findById(input.itemId());
                if (itemOpt.isEmpty()) {
                    allValid = false;
                    validatedItems.add(new ValidatedItemDto(
                            input.itemId(),
                            "Unknown",
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            false,
                            "Item not found"
                    ));
                    continue;
                }

                MenuItem item = itemOpt.get();

                // Check if item belongs to the restaurant
                if (!item.getRestaurantId().equals(restaurantId)) {
                    allValid = false;
                    validatedItems.add(new ValidatedItemDto(
                            input.itemId(),
                            item.getName(),
                            item.getPrice(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            false,
                            "Item does not belong to this restaurant"
                    ));
                    continue;
                }

                // Check availability
                if (item.getIsAvailable() == null || !item.getIsAvailable()) {
                    allValid = false;
                    validatedItems.add(new ValidatedItemDto(
                            input.itemId(),
                            item.getName(),
                            item.getPrice(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            false,
                            "Item is not available"
                    ));
                    continue;
                }

                // Option price adjustment logic
                BigDecimal optionPrice = BigDecimal.ZERO;
                List<MenuItemOption> dbOptions = item.getOptions();

                if (input.selectedOptions() != null && !input.selectedOptions().isEmpty() && dbOptions != null) {
                    for (SelectedOptionInput selectedOption : input.selectedOptions()) {
                        MenuItemOption matchOption = dbOptions.stream()
                                .filter(opt -> opt.getName().equalsIgnoreCase(selectedOption.optionName()))
                                .findFirst()
                                .orElse(null);

                        if (matchOption != null && matchOption.getChoices() != null) {
                            MenuItemOptionChoice matchChoice = matchOption.getChoices().stream()
                                    .filter(choice -> choice.getName().equalsIgnoreCase(selectedOption.choiceName()))
                                    .findFirst()
                                    .orElse(null);

                            if (matchChoice != null && matchChoice.getPriceModifier() != null) {
                                optionPrice = optionPrice.add(matchChoice.getPriceModifier());
                            }
                        }
                    }
                }

                BigDecimal basePrice = item.getPrice();
                BigDecimal unitPrice = basePrice.add(optionPrice);
                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(input.quantity()));
                totalSubtotal = totalSubtotal.add(subtotal);

                validatedItems.add(new ValidatedItemDto(
                        input.itemId(),
                        item.getName(),
                        basePrice,
                        optionPrice,
                        subtotal,
                        true,
                        "Success"
                ));
            }
        }

        return new ValidateItemsResponse(allValid, totalSubtotal, validatedItems);
    }

    private MenuItemResponse mapItemToDto(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getImageUrl(),
                item.getIsAvailable(),
                item.getOptions()
        );
    }
}
