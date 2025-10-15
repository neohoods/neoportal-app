package com.neohoods.portal.platform.spaces.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "reservation_audit_log")
public class ReservationAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @NotNull
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "log_message")
    private String logMessage;

    @NotNull
    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public ReservationAuditLogEntity() {
    }

    public ReservationAuditLogEntity(UUID id, UUID reservationId, String eventType,
            String oldValue, String newValue, String logMessage,
            String performedBy, LocalDateTime createdAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.eventType = eventType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.logMessage = logMessage;
        this.performedBy = performedBy;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Define common event types as constants
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String CODE_GENERATED = "CODE_GENERATED";
    public static final String CODE_REGENERATED = "CODE_REGENERATED";
    public static final String PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String CANCELLED = "CANCELLED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String ENTRY_LOGGED = "ENTRY_LOGGED";
    public static final String EXIT_LOGGED = "EXIT_LOGGED";
}