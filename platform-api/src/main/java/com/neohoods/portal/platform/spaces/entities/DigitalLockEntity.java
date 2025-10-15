package com.neohoods.portal.platform.spaces.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "digital_locks")
public class DigitalLockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DigitalLockTypeForEntity type = DigitalLockTypeForEntity.TTLOCK;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DigitalLockStatusForEntity status = DigitalLockStatusForEntity.ACTIVE;

    // Config is now handled by specific config entities (TtlockConfigEntity,
    // NukiConfigEntity, etc.)

    @OneToOne(mappedBy = "digitalLock", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TtlockConfigEntity ttlockConfig;

    @OneToOne(mappedBy = "digitalLock", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NukiConfigEntity nukiConfig;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public DigitalLockEntity() {
    }

    public DigitalLockEntity(String name, DigitalLockTypeForEntity type) {
        this.name = name;
        this.type = type;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DigitalLockTypeForEntity getType() {
        return type;
    }

    public void setType(DigitalLockTypeForEntity type) {
        this.type = type;
    }

    public DigitalLockStatusForEntity getStatus() {
        return status;
    }

    public void setStatus(DigitalLockStatusForEntity status) {
        this.status = status;
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

    // Config getters and setters
    public TtlockConfigEntity getTtlockConfig() {
        return ttlockConfig;
    }

    public void setTtlockConfig(TtlockConfigEntity ttlockConfig) {
        this.ttlockConfig = ttlockConfig;
    }

    public NukiConfigEntity getNukiConfig() {
        return nukiConfig;
    }

    public void setNukiConfig(NukiConfigEntity nukiConfig) {
        this.nukiConfig = nukiConfig;
    }
}