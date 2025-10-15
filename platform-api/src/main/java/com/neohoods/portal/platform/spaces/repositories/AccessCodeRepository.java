package com.neohoods.portal.platform.spaces.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.AccessCodeEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;

@Repository
public interface AccessCodeRepository extends JpaRepository<AccessCodeEntity, UUID> {

    /**
     * Find access code by reservation
     */
    Optional<AccessCodeEntity> findByReservation(ReservationEntity reservation);

    /**
     * Find access code by code string
     */
    Optional<AccessCodeEntity> findByCode(String code);

    /**
     * Find active access codes
     */
    List<AccessCodeEntity> findByIsActiveTrue();

    /**
     * Find expired access codes
     */
    @Query("SELECT ac FROM AccessCodeEntity ac WHERE ac.expiresAt < :currentTime")
    List<AccessCodeEntity> findExpiredAccessCodes(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find access codes expiring soon (within specified hours)
     */
    @Query("SELECT ac FROM AccessCodeEntity ac WHERE " +
            "ac.expiresAt BETWEEN :currentTime AND :expiryTime " +
            "AND ac.isActive = true")
    List<AccessCodeEntity> findAccessCodesExpiringSoon(
            @Param("currentTime") LocalDateTime currentTime,
            @Param("expiryTime") LocalDateTime expiryTime);

    /**
     * Find access codes by digital lock ID
     */
    List<AccessCodeEntity> findByDigitalLockId(UUID digitalLockId);

    /**
     * Find access codes by digital lock code ID
     */
    Optional<AccessCodeEntity> findByDigitalLockCodeId(String digitalLockCodeId);

    /**
     * Find access codes that need to be deactivated (expired)
     */
    @Query("SELECT ac FROM AccessCodeEntity ac WHERE " +
            "ac.expiresAt < :currentTime " +
            "AND ac.isActive = true")
    List<AccessCodeEntity> findAccessCodesToDeactivate(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Check if a code already exists
     */
    boolean existsByCode(String code);

    /**
     * Find access codes by reservation with reservation details
     */
    @Query("SELECT ac FROM AccessCodeEntity ac " +
            "LEFT JOIN FETCH ac.reservation r " +
            "LEFT JOIN FETCH r.space " +
            "LEFT JOIN FETCH r.user " +
            "WHERE ac.reservation = :reservation")
    Optional<AccessCodeEntity> findByReservationWithDetails(@Param("reservation") ReservationEntity reservation);

    /**
     * Find all access codes with reservation details
     */
    @Query("SELECT ac FROM AccessCodeEntity ac " +
            "LEFT JOIN FETCH ac.reservation r " +
            "LEFT JOIN FETCH r.space " +
            "LEFT JOIN FETCH r.user")
    List<AccessCodeEntity> findAllWithReservationDetails();
}
