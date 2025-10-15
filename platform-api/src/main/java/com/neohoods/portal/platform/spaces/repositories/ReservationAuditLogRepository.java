package com.neohoods.portal.platform.spaces.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.ReservationAuditLogEntity;

@Repository
public interface ReservationAuditLogRepository extends JpaRepository<ReservationAuditLogEntity, UUID> {

    /**
     * Find all audit logs for a specific reservation, ordered by creation date
     * (newest first)
     */
    @Query("SELECT ral FROM ReservationAuditLogEntity ral WHERE ral.reservationId = :reservationId ORDER BY ral.createdAt DESC")
    List<ReservationAuditLogEntity> findByReservationIdOrderByCreatedAtDesc(@Param("reservationId") UUID reservationId);

    /**
     * Find audit logs by event type
     */
    List<ReservationAuditLogEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);

    /**
     * Find audit logs by performer
     */
    List<ReservationAuditLogEntity> findByPerformedByOrderByCreatedAtDesc(String performedBy);

    /**
     * Count audit logs for a reservation
     */
    long countByReservationId(UUID reservationId);
}
