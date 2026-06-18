package com.fooddelivery.order.adapter.outbound.persistence;

import com.fooddelivery.order.domain.model.DeliveryAddress;
import com.fooddelivery.order.domain.model.Money;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.*;

/**
 * Mapper between domain Order model and JPA entities.
 * Handles the JSONB serialization/deserialization of items and deliveryAddress.
 */
public class OrderMapper {

    public static OrderJpaEntity toJpaEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setId(order.getId());
        entity.setCustomerId(order.getCustomerId());
        entity.setRestaurantId(order.getRestaurantId());
        entity.setSubtotal(order.getSubtotal().amount());
        entity.setDeliveryFee(order.getDeliveryFee().amount());
        entity.setTotalAmount(order.getTotalAmount().amount());
        entity.setStatus(order.getStatus());
        entity.setDriverId(order.getDriverId());
        entity.setSpecialInstructions(order.getSpecialInstructions());
        entity.setCreatedAt(order.getCreatedAt());
        entity.setUpdatedAt(order.getUpdatedAt());

        // Serialize delivery address to map for JSONB
        DeliveryAddress addr = order.getDeliveryAddress();
        entity.setDeliveryAddress(Map.of(
            "addressLine", addr.addressLine(),
            "lat", addr.lat(),
            "lng", addr.lng()
        ));

        // Serialize items to list of maps for JSONB
        List<Map<String, Object>> itemMaps = order.getItems().stream()
            .map(item -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemId", item.itemId().toString());
                m.put("name", item.name());
                m.put("quantity", item.quantity());
                m.put("unitPrice", item.unitPrice().amount().toPlainString());
                m.put("currency", item.unitPrice().currency());
                return m;
            })
            .toList();
        entity.setItems(itemMaps);

        return entity;
    }

    public static Order toDomain(OrderJpaEntity entity) {
        // Deserialize delivery address from JSONB map
        Map<String, Object> addrMap = entity.getDeliveryAddress();
        DeliveryAddress deliveryAddress = new DeliveryAddress(
            (String) addrMap.get("addressLine"),
            toDouble(addrMap.get("lat")),
            toDouble(addrMap.get("lng"))
        );

        // Deserialize items from JSONB list
        List<OrderItem> items = entity.getItems().stream()
            .map(m -> new OrderItem(
                UUID.fromString((String) m.get("itemId")),
                (String) m.get("name"),
                toInt(m.get("quantity")),
                Money.of(new BigDecimal((String) m.get("unitPrice")), (String) m.getOrDefault("currency", "VND"))
            ))
            .toList();

        return Order.reconstitute(
            entity.getId(),
            entity.getCustomerId(),
            entity.getRestaurantId(),
            items,
            deliveryAddress,
            Money.of(entity.getSubtotal(), "VND"),
            Money.of(entity.getDeliveryFee(), "VND"),
            Money.of(entity.getTotalAmount(), "VND"),
            entity.getStatus(),
            entity.getDriverId(),
            entity.getSpecialInstructions(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private static double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(val));
    }

    private static int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(val));
    }
}
