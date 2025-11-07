package com.neohoods.portal.platform.spaces.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

        /**
         * Find reservations by user
         */
        List<ReservationEntity> findByUser(UserEntity user);

        /**
         * Find reservations by user with pagination
         */
        Page<ReservationEntity> findByUser(UserEntity user, Pageable pageable);

        /**
         * Find reservations by user and status
         */
        List<ReservationEntity> findByUserAndStatus(UserEntity user, ReservationStatusForEntity status);

        /**
         * Find reservations by user and status with pagination
         */
        Page<ReservationEntity> findByUserAndStatus(UserEntity user, ReservationStatusForEntity status,
                        Pageable pageable);

        /**
         * Find reservations by space
         */
        List<ReservationEntity> findBySpace(SpaceEntity space);

        /**
         * Find reservations by space with pagination
         */
        Page<ReservationEntity> findBySpace(SpaceEntity space, Pageable pageable);

        /**
         * Find reservations by space and status
         */
        List<ReservationEntity> findBySpaceAndStatus(SpaceEntity space, ReservationStatusForEntity status);

        /**
         * Find reservations by status
         */
        List<ReservationEntity> findByStatus(ReservationStatusForEntity status);

        /**
         * Find reservations by status with pagination
         */
        Page<ReservationEntity> findByStatus(ReservationStatusForEntity status, Pageable pageable);

        /**
         * Find reservations for a specific date range
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.status IN ('CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))")
        List<ReservationEntity> findReservationsInDateRange(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Find reservations for a specific space and date range
         */
        @Query("SELECT r FROM ReservationEntity r WHERE r.space.id = :spaceId " +
                        "AND r.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))")
        List<ReservationEntity> findReservationsForSpaceInDateRange(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Find active reservations (currently ongoing)
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.status = 'ACTIVE' " +
                        "AND r.startDate <= :currentDate " +
                        "AND r.endDate >= :currentDate")
        List<ReservationEntity> findActiveReservations(@Param("currentDate") LocalDate currentDate);

        /**
         * Find upcoming reservations
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.status = 'CONFIRMED' " +
                        "AND r.startDate > :currentDate")
        List<ReservationEntity> findUpcomingReservations(@Param("currentDate") LocalDate currentDate);

        /**
         * Find past reservations
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.status = 'COMPLETED' " +
                        "AND r.endDate < :currentDate")
        List<ReservationEntity> findPastReservations(@Param("currentDate") LocalDate currentDate);

        /**
         * Find reservations by user with space and access code
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.accessCode " +
                        "WHERE r.user.id = :userId")
        List<ReservationEntity> findByUserIdWithSpaceAndAccessCode(@Param("userId") UUID userId);

        /**
         * Find reservation by ID with space and access code
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.accessCode " +
                        "WHERE r.id = :id")
        Optional<ReservationEntity> findByIdWithSpaceAndAccessCode(@Param("id") UUID id);

        /**
         * Find reservations by Stripe payment intent ID
         */
        Optional<ReservationEntity> findByStripePaymentIntentId(String stripePaymentIntentId);

        /**
         * Find reservations by Stripe session ID
         */
        Optional<ReservationEntity> findByStripeSessionId(String stripeSessionId);

        /**
         * Count reservations by user and year
         */
        @Query("SELECT COUNT(r) FROM ReservationEntity r WHERE " +
                        "r.user = :user " +
                        "AND YEAR(r.startDate) = :year " +
                        "AND r.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED')")
        Long countReservationsByUserAndYear(@Param("user") UserEntity user, @Param("year") int year);

        /**
         * Count reservations by unit and year
         */
        @Query("SELECT COUNT(r) FROM ReservationEntity r WHERE " +
                        "r.unit.id = :unitId " +
                        "AND YEAR(r.startDate) = :year " +
                        "AND r.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED')")
        Long countReservationsByUnitAndYear(@Param("unitId") UUID unitId, @Param("year") int year);

        /**
         * Find reservations by unit
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.user " +
                        "WHERE r.unit.id = :unitId " +
                        "ORDER BY r.startDate DESC")
        List<ReservationEntity> findByUnitId(@Param("unitId") UUID unitId);

        /**
         * Find reservations by unit with pagination
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.user " +
                        "WHERE r.unit.id = :unitId " +
                        "ORDER BY r.startDate DESC")
        Page<ReservationEntity> findByUnitId(@Param("unitId") UUID unitId, Pageable pageable);

        /**
         * Find reservations by unit and status
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.user " +
                        "WHERE r.unit.id = :unitId " +
                        "AND r.status = :status " +
                        "ORDER BY r.startDate DESC")
        List<ReservationEntity> findByUnitIdAndStatus(@Param("unitId") UUID unitId, 
                        @Param("status") ReservationStatusForEntity status);

        /**
         * Find reservations that need to be activated (start date reached)
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.status = 'CONFIRMED' " +
                        "AND r.startDate <= :currentDate")
        List<ReservationEntity> findReservationsToActivate(@Param("currentDate") LocalDate currentDate);

        /**
         * Find reservations that need to be completed (end date reached)
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.status = 'ACTIVE' " +
                        "AND r.endDate < :currentDate")
        List<ReservationEntity> findReservationsToComplete(@Param("currentDate") LocalDate currentDate);

        /**
         * Find reservation by user and ID
         */
        Optional<ReservationEntity> findByUserAndId(UserEntity user, UUID id);

        /**
         * Find reservations by user and start date after and status
         */
        List<ReservationEntity> findByUserAndStartDateAfterAndStatus(UserEntity user, LocalDate startDate,
                        ReservationStatusForEntity status);

        /**
         * Find reservations by user and end date before and status
         */
        List<ReservationEntity> findByUserAndEndDateBeforeAndStatus(UserEntity user, LocalDate endDate,
                        ReservationStatusForEntity status);

        /**
         * Find reservations by user and date range
         */
        @Query("SELECT r FROM ReservationEntity r WHERE r.user = :user AND " +
                        "(r.startDate BETWEEN :startDate AND :endDate OR r.endDate BETWEEN :startDate AND :endDate)")
        List<ReservationEntity> findByUserAndStartDateBetweenOrEndDateBetween(
                        @Param("user") UserEntity user,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("startDate2") LocalDate startDate2,
                        @Param("endDate2") LocalDate endDate2);

        /**
         * Find reservations with filters and pagination for admin
         * Using proper null handling for PostgreSQL compatibility
         * Includes JOIN FETCH to load user and space relations
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.user " +
                        "LEFT JOIN FETCH r.accessCode " +
                        "WHERE (:spaceId IS NULL OR r.space.id = :spaceId) AND " +
                        "(:userId IS NULL OR r.user.id = :userId) AND " +
                        "(:status IS NULL OR r.status = :status) AND " +
                        "(:startDate IS NULL OR r.startDate >= :startDate) AND " +
                        "(:endDate IS NULL OR r.endDate <= :endDate) AND " +
                        "(:spaceType IS NULL OR r.space.type = :spaceType)")
        Page<ReservationEntity> findReservationsWithFilters(
                        @Param("spaceId") UUID spaceId,
                        @Param("userId") UUID userId,
                        @Param("status") ReservationStatusForEntity status,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("spaceType") SpaceTypeForEntity spaceType,
                        Pageable pageable);

        /**
         * Find reservations by space and date range (for calendar view)
         * This method is more specific and avoids null parameter issues
         * Includes JOIN FETCH to load user and space relations
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.user " +
                        "LEFT JOIN FETCH r.accessCode " +
                        "WHERE r.space.id = :spaceId " +
                        "AND r.startDate <= :startDate AND r.endDate >= :endDate")
        Page<ReservationEntity> findReservationsBySpaceAndDateRange(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        Pageable pageable);

        /**
         * Find user reservations with filters and pagination
         */
        @Query("SELECT r FROM ReservationEntity r WHERE r.user.id = :userId AND " +
                        "(:status IS NULL OR r.status = :status) AND " +
                        "(:spaceType IS NULL OR r.space.type = :spaceType)")
        Page<ReservationEntity> findUserReservationsWithFilters(
                        @Param("userId") UUID userId,
                        @Param("status") ReservationStatusForEntity status,
                        @Param("spaceType") SpaceTypeForEntity spaceType,
                        Pageable pageable);

        // Statistics methods for space analytics - using aggregate functions to avoid
        // lazy loading

        /**
         * Count total reservations for a space in date range
         */
        @Query("SELECT COUNT(r) FROM ReservationEntity r WHERE " +
                        "r.space.id = :spaceId " +
                        "AND r.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED') " +
                        "AND r.startDate >= :startDate " +
                        "AND r.endDate <= :endDate")
        Long countReservationsBySpaceAndDateRange(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Calculate total revenue for a space in date range
         */
        @Query("SELECT COALESCE(SUM(r.totalPrice), 0) FROM ReservationEntity r WHERE " +
                        "r.space.id = :spaceId " +
                        "AND r.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED') " +
                        "AND r.startDate >= :startDate " +
                        "AND r.endDate <= :endDate")
        java.math.BigDecimal calculateTotalRevenueBySpaceAndDateRange(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Get reservations for a space in date range (for manual calculation)
         * Includes PENDING_PAYMENT for occupancy calendar display
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.space.id = :spaceId " +
                        "AND r.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE', 'COMPLETED') " +
                        "AND r.startDate >= :startDate " +
                        "AND r.endDate <= :endDate")
        List<ReservationEntity> findReservationsForStatistics(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Get top users by reservation count for a space in date range
         */
        @Query("SELECT r.user.id, r.user.firstName, r.user.lastName, COUNT(r) as reservationCount " +
                        "FROM ReservationEntity r WHERE " +
                        "r.space.id = :spaceId " +
                        "AND r.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED') " +
                        "AND r.startDate >= :startDate " +
                        "AND r.endDate <= :endDate " +
                        "GROUP BY r.user.id, r.user.firstName, r.user.lastName " +
                        "ORDER BY reservationCount DESC")
        List<Object[]> getTopUsersByReservationCount(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        Pageable pageable);

        /**
         * Get top users with reservation count and total spent for a space in date
         * range
         */
        @Query("SELECT r.user.id, r.user.firstName, r.user.lastName, r.user.email, " +
                        "COUNT(r) as reservationCount, " +
                        "SUM(r.totalPrice) as totalSpent " +
                        "FROM ReservationEntity r WHERE " +
                        "r.space.id = :spaceId " +
                        "AND r.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED') " +
                        "AND r.startDate >= :startDate " +
                        "AND r.endDate <= :endDate " +
                        "GROUP BY r.user.id, r.user.firstName, r.user.lastName, r.user.email " +
                        "ORDER BY reservationCount DESC")
        List<Object[]> getTopUsersWithDetails(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        Pageable pageable);

        /**
         * Get all reservations for a specific user and space in date range (for manual
         * calculation)
         */
        @Query("SELECT r FROM ReservationEntity r WHERE " +
                        "r.space.id = :spaceId " +
                        "AND r.user.id = :userId " +
                        "AND r.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED') " +
                        "AND r.startDate >= :startDate " +
                        "AND r.endDate <= :endDate")
        List<ReservationEntity> findReservationsByUserAndSpace(
                        @Param("spaceId") UUID spaceId,
                        @Param("userId") UUID userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Find expired pending payment reservations
         * Also includes reservations without paymentExpiresAt that are older than 24
         * hours
         * (for cleanup of old data that may have been created before paymentExpiresAt
         * was mandatory)
         */
        @Query("SELECT r FROM ReservationEntity r WHERE r.status = :status " +
                        "AND ((r.paymentExpiresAt IS NOT NULL AND r.paymentExpiresAt < :now) " +
                        "OR (r.paymentExpiresAt IS NULL AND r.createdAt < :nowMinus24Hours))")
        List<ReservationEntity> findExpiredPendingPaymentReservations(
                        @Param("now") LocalDateTime now,
                        @Param("nowMinus24Hours") LocalDateTime nowMinus24Hours,
                        @Param("status") ReservationStatusForEntity status);

        /**
         * Find reservations by start date and status
         */
        List<ReservationEntity> findByStartDateAndStatus(LocalDate startDate, ReservationStatusForEntity status);

        /**
         * Find reservations for shared spaces in a date range
         * Returns reservations from spaces that share the same physical space
         */
        @Query("SELECT r FROM ReservationEntity r " +
                        "LEFT JOIN FETCH r.space " +
                        "LEFT JOIN FETCH r.user " +
                        "LEFT JOIN FETCH r.accessCode " +
                        "WHERE r.space.id IN :sharedSpaceIds " +
                        "AND r.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE') " +
                        "AND r.startDate < :endDate AND r.endDate > :startDate")
        List<ReservationEntity> findSharedSpaceReservations(
                        @Param("sharedSpaceIds") List<UUID> sharedSpaceIds,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}
