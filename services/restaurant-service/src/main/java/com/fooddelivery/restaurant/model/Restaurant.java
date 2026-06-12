package com.fooddelivery.restaurant.model;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Type(StringArrayType.class)
    @Column(name = "cuisine_types", columnDefinition = "_varchar")
    private String[] cuisineTypes;

    @Type(JsonBinaryType.class)
    @Column(name = "operating_hours", columnDefinition = "jsonb")
    private Map<String, DayHours> operatingHours;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "avg_rating", precision = 2, scale = 1)
    private BigDecimal avgRating = BigDecimal.valueOf(0.0);

    @Column(name = "total_reviews")
    private Integer totalReviews = 0;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Restaurant() {}

    public Restaurant(UUID id, UUID ownerId, String name, String description, String addressLine,
                      Double lat, Double lng, String[] cuisineTypes, Map<String, DayHours> operatingHours,
                      Boolean isActive, BigDecimal avgRating, Integer totalReviews, String imageUrl) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.addressLine = addressLine;
        this.lat = lat;
        this.lng = lng;
        this.cuisineTypes = cuisineTypes;
        this.operatingHours = operatingHours;
        this.isActive = isActive;
        this.avgRating = avgRating;
        this.totalReviews = totalReviews;
        this.imageUrl = imageUrl;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String[] getCuisineTypes() {
        return cuisineTypes;
    }

    public void setCuisineTypes(String[] cuisineTypes) {
        this.cuisineTypes = cuisineTypes;
    }

    public Map<String, DayHours> getOperatingHours() {
        return operatingHours;
    }

    public void setOperatingHours(Map<String, DayHours> operatingHours) {
        this.operatingHours = operatingHours;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
