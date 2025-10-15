package com.neohoods.portal.platform.spaces.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "space_settings")
public class SpaceSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @DecimalMin(value = "0.0", message = "Platform fee percentage must be non-negative")
    @DecimalMax(value = "100.0", message = "Platform fee percentage must not exceed 100%")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal platformFeePercentage = BigDecimal.valueOf(2.00);

    @NotNull
    @DecimalMin(value = "0.0", message = "Platform fixed fee must be non-negative")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFixedFee = BigDecimal.valueOf(0.25);

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public SpaceSettingsEntity() {
    }

    public SpaceSettingsEntity(BigDecimal platformFeePercentage, BigDecimal platformFixedFee) {
        this.platformFeePercentage = platformFeePercentage;
        this.platformFixedFee = platformFixedFee;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getPlatformFeePercentage() {
        return platformFeePercentage;
    }

    public void setPlatformFeePercentage(BigDecimal platformFeePercentage) {
        this.platformFeePercentage = platformFeePercentage;
    }

    public BigDecimal getPlatformFixedFee() {
        return platformFixedFee;
    }

    public void setPlatformFixedFee(BigDecimal platformFixedFee) {
        this.platformFixedFee = platformFixedFee;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "SpaceSettingsEntity{" +
                "id=" + id +
                ", platformFeePercentage=" + platformFeePercentage +
                ", platformFixedFee=" + platformFixedFee +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
