package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.spaces.entities.ReservationAuditLogEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationAuditLogRepository;

@Service
@Transactional
public class ReservationAuditService {

    private static final Logger log = LoggerFactory.getLogger(ReservationAuditService.class);

    @Autowired
    private final ReservationAuditLogRepository auditLogRepository;

    public ReservationAuditService(ReservationAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log an event for a reservation
     */
    public void logEvent(UUID reservationId, String eventType, String oldValue, String newValue, String logMessage,
            String performedBy) {
        try {
            ReservationAuditLogEntity auditLog = new ReservationAuditLogEntity();
            auditLog.setReservationId(reservationId);
            auditLog.setEventType(eventType);
            auditLog.setOldValue(oldValue);
            auditLog.setNewValue(newValue);
            auditLog.setLogMessage(logMessage);
            auditLog.setPerformedBy(performedBy);
            auditLog.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

            auditLogRepository.save(auditLog);
            log.debug("Audit log created for reservation {}: {} - {}", reservationId, eventType, logMessage);
        } catch (Exception e) {
            log.error("Failed to create audit log for reservation {}: {}", reservationId, e.getMessage(), e);
        }
    }

    /**
     * Log a status change event
     */
    public void logStatusChange(UUID reservationId, String oldStatus, String newStatus, String performedBy) {
        String logMessage = String.format("Reservation status changed from %s to %s", oldStatus, newStatus);
        logEvent(reservationId, ReservationAuditLogEntity.STATUS_CHANGE, oldStatus, newStatus, logMessage, performedBy);
    }

    /**
     * Log an access code generation event
     */
    public void logCodeGenerated(UUID reservationId, String newCode, String performedBy) {
        String logMessage = String.format("Access code generated: %s", newCode);
        logEvent(reservationId, ReservationAuditLogEntity.CODE_GENERATED, null, newCode, logMessage, performedBy);
    }

    /**
     * Log an access code regeneration event
     */
    public void logCodeRegenerated(UUID reservationId, String oldCode, String newCode, String performedBy) {
        String logMessage = String.format("Access code regenerated from %s to %s", oldCode, newCode);
        logEvent(reservationId, ReservationAuditLogEntity.CODE_REGENERATED, oldCode, newCode, logMessage, performedBy);
    }

    /**
     * Log a payment received event
     */
    public void logPaymentReceived(UUID reservationId, String paymentIntentId, String performedBy) {
        String logMessage = String.format("Payment received. Payment Intent ID: %s", paymentIntentId);
        logEvent(reservationId, ReservationAuditLogEntity.PAYMENT_RECEIVED, null, paymentIntentId, logMessage,
                performedBy);
    }

    /**
     * Log a cancellation event
     */
    public void logCancellation(UUID reservationId, String reason, String performedBy) {
        String logMessage = String.format("Reservation cancelled. Reason: %s", reason);
        logEvent(reservationId, ReservationAuditLogEntity.CANCELLED, null, reason, logMessage, performedBy);
    }

    /**
     * Log a confirmation event
     */
    public void logConfirmation(UUID reservationId, String performedBy) {
        String logMessage = "Reservation confirmed.";
        logEvent(reservationId, ReservationAuditLogEntity.CONFIRMED, null, null, logMessage, performedBy);
    }

    /**
     * Get all audit logs for a reservation
     */
    public List<ReservationAuditLogEntity> getAuditLogsForReservation(UUID reservationId) {
        return auditLogRepository.findByReservationIdOrderByCreatedAtDesc(reservationId);
    }
}