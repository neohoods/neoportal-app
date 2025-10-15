package com.neohoods.portal.platform.spaces.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "access_codes")
public class AccessCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @NotNull
    private ReservationEntity reservation;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String code;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "digital_lock_id")
    private UUID digitalLockId;

    @Column(name = "digital_lock_code_id")
    private String digitalLockCodeId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "regenerated_at")
    private LocalDateTime regeneratedAt;

    @Column(name = "regenerated_by")
    private String regeneratedBy; // user ID or "system"

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public AccessCodeEntity() {
    }

    public AccessCodeEntity(ReservationEntity reservation, String code, LocalDateTime expiresAt) {
        this.reservation = reservation;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReservationEntity getReservation() {
        return reservation;
    }

    public void setReservation(ReservationEntity reservation) {
        this.reservation = reservation;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getDigitalLockId() {
        return digitalLockId;
    }

    public void setDigitalLockId(UUID digitalLockId) {
        this.digitalLockId = digitalLockId;
    }

    public String getDigitalLockCodeId() {
        return digitalLockCodeId;
    }

    public void setDigitalLockCodeId(String digitalLockCodeId) {
        this.digitalLockCodeId = digitalLockCodeId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public LocalDateTime getRegeneratedAt() {
        return regeneratedAt;
    }

    public void setRegeneratedAt(LocalDateTime regeneratedAt) {
        this.regeneratedAt = regeneratedAt;
    }

    public String getRegeneratedBy() {
        return regeneratedBy;
    }

    public void setRegeneratedBy(String regeneratedBy) {
        this.regeneratedBy = regeneratedBy;
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
}
