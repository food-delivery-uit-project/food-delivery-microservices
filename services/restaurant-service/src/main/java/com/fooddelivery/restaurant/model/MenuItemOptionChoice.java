package com.fooddelivery.restaurant.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;

public class MenuItemOptionChoice implements Serializable {
    @JsonProperty("label")
    @JsonAlias({"name"})
    private String name;
    
    @JsonProperty("price_modifier")
    @JsonAlias({"price_adjustment", "priceModifier", "priceAdjustment"})
    private BigDecimal priceModifier;

    public MenuItemOptionChoice() {}

    public MenuItemOptionChoice(String name, BigDecimal priceModifier) {
        this.name = name;
        this.priceModifier = priceModifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPriceModifier() {
        return priceModifier;
    }

    public void setPriceModifier(BigDecimal priceModifier) {
        this.priceModifier = priceModifier;
    }
}
