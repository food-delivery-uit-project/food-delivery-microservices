package com.fooddelivery.restaurant.kafka;

import java.time.Instant;

public class CloudEvent<T> {
    private String id;
    private String source;
    private String specversion = "1.0";
    private String type;
    private String datacontenttype = "application/json";
    private Instant time;
    private T data;

    public CloudEvent() {}

    public CloudEvent(String id, String source, String type, T data) {
        this.id = id;
        this.source = source;
        this.type = type;
        this.time = Instant.now();
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSpecversion() {
        return specversion;
    }

    public void setSpecversion(String specversion) {
        this.specversion = specversion;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDatacontenttype() {
        return datacontenttype;
    }

    public void setDatacontenttype(String datacontenttype) {
        this.datacontenttype = datacontenttype;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
