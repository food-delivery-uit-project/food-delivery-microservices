package com.fooddelivery.restaurant.model;

import java.io.Serializable;
import java.util.List;

public class MenuItemOption implements Serializable {
    private String name;
    private boolean required;
    private List<MenuItemOptionChoice> choices;

    public MenuItemOption() {}

    public MenuItemOption(String name, boolean required, List<MenuItemOptionChoice> choices) {
        this.name = name;
        this.required = required;
        this.choices = choices;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public List<MenuItemOptionChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<MenuItemOptionChoice> choices) {
        this.choices = choices;
    }
}
