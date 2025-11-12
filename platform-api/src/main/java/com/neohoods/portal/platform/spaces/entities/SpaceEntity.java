package com.neohoods.portal.platform.spaces.entities;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(name = "spaces")
public class SpaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpaceTypeForEntity type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpaceStatusForEntity status = SpaceStatusForEntity.ACTIVE;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tenantPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal ownerPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal cleaningFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal deposit;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @PositiveOrZero
    @Column(nullable = false)
    private Integer minDurationDays = 1;

    @PositiveOrZero
    @Column(nullable = false)
    private Integer maxDurationDays = 365;

    @Positive
    @Column
    private Integer capacity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "space_shared_with", joinColumns = @JoinColumn(name = "space_id"))
    @Column(name = "shared_space_id")
    private List<UUID> shareSpaceWith = new ArrayList<>();

    @PositiveOrZero
    @Column(nullable = false)
    private Integer maxAnnualReservations = 0; // 0 = unlimited

    @PositiveOrZero
    @Column(nullable = false)
    private Integer usedAnnualReservations = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "space_allowed_days", joinColumns = @JoinColumn(name = "space_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private List<DayOfWeek> allowedDays = new ArrayList<>();

    @Column(length = 5)
    private String allowedHoursStart = "08:00";

    @Column(length = 5)
    private String allowedHoursEnd = "20:00";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "space_cleaning_days", joinColumns = @JoinColumn(name = "space_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private List<DayOfWeek> cleaningDays = new ArrayList<>();

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SpaceImageEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationEntity> reservations = new ArrayList<>();

    @Column
    private UUID digitalLockId;

    @Column(nullable = false)
    private Boolean accessCodeEnabled = true;

    @Column(nullable = false)
    private Boolean enableNotifications = true;

    @Column(nullable = false)
    private Boolean cleaningEnabled = false;

    @Column
    private String cleaningEmail;

    @Column(nullable = false)
    private Boolean cleaningNotificationsEnabled = false;

    @Column(nullable = false)
    private Boolean cleaningCalendarEnabled = false;

    @Column(nullable = false)
    private Integer cleaningDaysAfterCheckout = 0;

    @Column(nullable = false, length = 5)
    private String cleaningHour = "10:00";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public SpaceEntity() {
    }

    public SpaceEntity(String name, String description, SpaceTypeForEntity type, BigDecimal tenantPrice) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.tenantPrice = tenantPrice;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public SpaceTypeForEntity getType() {
        return type;
    }

    public void setType(SpaceTypeForEntity type) {
        this.type = type;
    }

    public SpaceStatusForEntity getStatus() {
        return status;
    }

    public void setStatus(SpaceStatusForEntity status) {
        this.status = status;
    }

    public BigDecimal getTenantPrice() {
        return tenantPrice;
    }

    public void setTenantPrice(BigDecimal tenantPrice) {
        this.tenantPrice = tenantPrice;
    }

    public BigDecimal getOwnerPrice() {
        return ownerPrice;
    }

    public void setOwnerPrice(BigDecimal ownerPrice) {
        this.ownerPrice = ownerPrice;
    }

    public BigDecimal getCleaningFee() {
        return cleaningFee;
    }

    public void setCleaningFee(BigDecimal cleaningFee) {
        this.cleaningFee = cleaningFee;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getMinDurationDays() {
        return minDurationDays;
    }

    public void setMinDurationDays(Integer minDurationDays) {
        this.minDurationDays = minDurationDays;
    }

    public Integer getMaxDurationDays() {
        return maxDurationDays;
    }

    public void setMaxDurationDays(Integer maxDurationDays) {
        this.maxDurationDays = maxDurationDays;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public List<UUID> getShareSpaceWith() {
        return shareSpaceWith;
    }

    public void setShareSpaceWith(List<UUID> shareSpaceWith) {
        this.shareSpaceWith = shareSpaceWith;
    }

    public Integer getMaxAnnualReservations() {
        return maxAnnualReservations;
    }

    public void setMaxAnnualReservations(Integer maxAnnualReservations) {
        this.maxAnnualReservations = maxAnnualReservations;
    }

    public Integer getUsedAnnualReservations() {
        return usedAnnualReservations;
    }

    public void setUsedAnnualReservations(Integer usedAnnualReservations) {
        this.usedAnnualReservations = usedAnnualReservations;
    }

    public List<DayOfWeek> getAllowedDays() {
        return allowedDays;
    }

    public void setAllowedDays(List<DayOfWeek> allowedDays) {
        this.allowedDays = allowedDays;
    }

    public String getAllowedHoursStart() {
        return allowedHoursStart;
    }

    public void setAllowedHoursStart(String allowedHoursStart) {
        this.allowedHoursStart = allowedHoursStart;
    }

    public String getAllowedHoursEnd() {
        return allowedHoursEnd;
    }

    public void setAllowedHoursEnd(String allowedHoursEnd) {
        this.allowedHoursEnd = allowedHoursEnd;
    }

    public List<DayOfWeek> getCleaningDays() {
        return cleaningDays;
    }

    public void setCleaningDays(List<DayOfWeek> cleaningDays) {
        this.cleaningDays = cleaningDays;
    }

    public List<SpaceImageEntity> getImages() {
        return images;
    }

    public void setImages(List<SpaceImageEntity> images) {
        this.images = images;
    }

    public List<ReservationEntity> getReservations() {
        return reservations;
    }

    public void setReservations(List<ReservationEntity> reservations) {
        this.reservations = reservations;
    }

    public UUID getDigitalLockId() {
        return digitalLockId;
    }

    public void setDigitalLockId(UUID digitalLockId) {
        this.digitalLockId = digitalLockId;
    }

    public Boolean getAccessCodeEnabled() {
        return accessCodeEnabled;
    }

    public void setAccessCodeEnabled(Boolean accessCodeEnabled) {
        this.accessCodeEnabled = accessCodeEnabled;
    }

    public Boolean getEnableNotifications() {
        return enableNotifications;
    }

    public void setEnableNotifications(Boolean enableNotifications) {
        this.enableNotifications = enableNotifications;
    }

    public Boolean getCleaningEnabled() {
        return cleaningEnabled;
    }

    public void setCleaningEnabled(Boolean cleaningEnabled) {
        this.cleaningEnabled = cleaningEnabled;
    }

    public String getCleaningEmail() {
        return cleaningEmail;
    }

    public void setCleaningEmail(String cleaningEmail) {
        this.cleaningEmail = cleaningEmail;
    }

    public Boolean getCleaningNotificationsEnabled() {
        return cleaningNotificationsEnabled;
    }

    public void setCleaningNotificationsEnabled(Boolean cleaningNotificationsEnabled) {
        this.cleaningNotificationsEnabled = cleaningNotificationsEnabled;
    }

    public Boolean getCleaningCalendarEnabled() {
        return cleaningCalendarEnabled;
    }

    public void setCleaningCalendarEnabled(Boolean cleaningCalendarEnabled) {
        this.cleaningCalendarEnabled = cleaningCalendarEnabled;
    }

    public Integer getCleaningDaysAfterCheckout() {
        return cleaningDaysAfterCheckout;
    }

    public void setCleaningDaysAfterCheckout(Integer cleaningDaysAfterCheckout) {
        this.cleaningDaysAfterCheckout = cleaningDaysAfterCheckout;
    }

    public String getCleaningHour() {
        return cleaningHour;
    }

    public void setCleaningHour(String cleaningHour) {
        this.cleaningHour = cleaningHour;
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
