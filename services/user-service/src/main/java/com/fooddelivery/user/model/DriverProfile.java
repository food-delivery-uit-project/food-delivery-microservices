package com.fooddelivery.user.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "driver_profiles")
public class DriverProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "vehicle_type", length = 20)
    private String vehicleType;

    @Column(name = "license_plate", length = 20)
    private String licensePlate;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "avg_rating")
    private BigDecimal avgRating = BigDecimal.valueOf(5.0);

    public DriverProfile() {
    }

    public DriverProfile(UUID userId, User user, String vehicleType, String licensePlate, Boolean isVerified, BigDecimal avgRating) {
        this.userId = userId;
        this.user = user;
        this.vehicleType = vehicleType;
        this.licensePlate = licensePlate;
        this.isVerified = isVerified;
        this.avgRating = avgRating;
    }

    // Getters and Setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }

    // Custom Builder Pattern
    public static class DriverProfileBuilder {
        private UUID userId;
        private User user;
        private String vehicleType;
        private String licensePlate;
        private Boolean isVerified = false;
        private BigDecimal avgRating = BigDecimal.valueOf(5.0);

        public DriverProfileBuilder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public DriverProfileBuilder user(User user) {
            this.user = user;
            return this;
        }

        public DriverProfileBuilder vehicleType(String vehicleType) {
            this.vehicleType = vehicleType;
            return this;
        }

        public DriverProfileBuilder licensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
            return this;
        }

        public DriverProfileBuilder isVerified(Boolean isVerified) {
            this.isVerified = isVerified;
            return this;
        }

        public DriverProfileBuilder avgRating(BigDecimal avgRating) {
            this.avgRating = avgRating;
            return this;
        }

        public DriverProfile build() {
            return new DriverProfile(userId, user, vehicleType, licensePlate, isVerified, avgRating);
        }
    }

    public static DriverProfileBuilder builder() {
        return new DriverProfileBuilder();
    }
}
