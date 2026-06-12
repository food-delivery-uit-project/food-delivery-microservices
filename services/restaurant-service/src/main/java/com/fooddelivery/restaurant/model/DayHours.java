package com.fooddelivery.restaurant.model;

import java.io.Serializable;

public class DayHours implements Serializable {
    private String open;
    private String close;

    public DayHours() {}

    public DayHours(String open, String close) {
        this.open = open;
        this.close = close;
    }

    public String getOpen() {
        return open;
    }

    public void setOpen(String open) {
        this.open = open;
    }

    public String getClose() {
        return close;
    }

    public void setClose(String close) {
        this.close = close;
    }
}
