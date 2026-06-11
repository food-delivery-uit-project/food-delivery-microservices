package com.fooddelivery.user.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "address_line", length = 500, nullable = false)
    private String addressLine;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lng", nullable = false)
    private Double lng;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    public Address() {
    }

    public Address(UUID id, User user, String label, String addressLine, Double lat, Double lng, Boolean isDefault) {
        this.id = id;
        this.user = user;
        this.label = label;
        this.addressLine = addressLine;
        this.lat = lat;
        this.lng = lng;
        this.isDefault = isDefault;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    // Custom Builder Pattern
    public static class AddressBuilder {
        private UUID id;
        private User user;
        private String label;
        private String addressLine;
        private Double lat;
        private Double lng;
        private Boolean isDefault = false;

        public AddressBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public AddressBuilder user(User user) {
            this.user = user;
            return this;
        }

        public AddressBuilder label(String label) {
            this.label = label;
            return this;
        }

        public AddressBuilder addressLine(String addressLine) {
            this.addressLine = addressLine;
            return this;
        }

        public AddressBuilder lat(Double lat) {
            this.lat = lat;
            return this;
        }

        public AddressBuilder lng(Double lng) {
            this.lng = lng;
            return this;
        }

        public AddressBuilder isDefault(Boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        public Address build() {
            return new Address(id, user, label, addressLine, lat, lng, isDefault);
        }
    }

    public static AddressBuilder builder() {
        return new AddressBuilder();
    }
}
