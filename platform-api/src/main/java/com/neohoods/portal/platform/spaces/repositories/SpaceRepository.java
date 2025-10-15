package com.neohoods.portal.platform.spaces.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;

@Repository
public interface SpaceRepository extends JpaRepository<SpaceEntity, UUID>, JpaSpecificationExecutor<SpaceEntity> {

        /**
         * Find all active spaces
         */
        List<SpaceEntity> findByStatus(SpaceStatusForEntity status);

        /**
         * Find spaces by type
         */
        List<SpaceEntity> findByTypeAndStatus(SpaceTypeForEntity type, SpaceStatusForEntity status);

        /**
         * Find spaces with pagination
         */
        Page<SpaceEntity> findByStatus(SpaceStatusForEntity status, Pageable pageable);

        /**
         * Find spaces by type with pagination
         */
        Page<SpaceEntity> findByTypeAndStatus(SpaceTypeForEntity type, SpaceStatusForEntity status, Pageable pageable);

        /**
         * Find available spaces for a date range
         * A space is available if it has no conflicting reservations
         */
        @Query("SELECT s FROM SpaceEntity s WHERE s.status = :status " +
                        "AND s.id NOT IN (" +
                        "SELECT DISTINCT r.space.id FROM ReservationEntity r " +
                        "WHERE r.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))" +
                        ")")
        List<SpaceEntity> findAvailableSpaces(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("status") SpaceStatusForEntity status);

        /**
         * Find available spaces by type for a date range
         */
        @Query("SELECT s FROM SpaceEntity s WHERE s.status = :status AND s.type = :type " +
                        "AND s.id NOT IN (" +
                        "SELECT DISTINCT r.space.id FROM ReservationEntity r " +
                        "WHERE r.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))" +
                        ")")
        List<SpaceEntity> findAvailableSpacesByType(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("type") SpaceTypeForEntity type,
                        @Param("status") SpaceStatusForEntity status);

        /**
         * Check if a space is available for a specific date range
         */
        @Query("SELECT COUNT(r) = 0 FROM ReservationEntity r " +
                        "WHERE r.space.id = :spaceId " +
                        "AND r.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))")
        boolean isSpaceAvailable(
                        @Param("spaceId") UUID spaceId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Check if a space is available for a specific date range considering shared
         * spaces
         * A space is not available if it or any of its shared spaces have conflicting
         * reservations
         */
        // TODO: Implémenter la logique de partage d'espaces de manière plus robuste
        // @Query("SELECT COUNT(r) = 0 FROM ReservationEntity r " +
        // "WHERE r.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE') " +
        // "AND ((r.startDate < :endDate AND r.endDate > :startDate)) " +
        // "AND (r.space.id = :spaceId " +
        // "OR (SIZE(:sharedSpaceIds) > 0 AND r.space.id IN :sharedSpaceIds))")
        // boolean isSpaceAvailableWithSharing(
        // @Param("spaceId") UUID spaceId,
        // @Param("sharedSpaceIds") List<UUID> sharedSpaceIds,
        // @Param("startDate") LocalDate startDate,
        // @Param("endDate") LocalDate endDate);

        /**
         * Find spaces with images
         */
        @Query("SELECT DISTINCT s FROM SpaceEntity s LEFT JOIN FETCH s.images WHERE s.status = :status " +
                        "ORDER BY s.type")
        List<SpaceEntity> findByStatusWithImages(@Param("status") SpaceStatusForEntity status);

        /**
         * Find spaces by type and status with images
         */
        @Query("SELECT DISTINCT s FROM SpaceEntity s LEFT JOIN FETCH s.images WHERE s.type = :type AND s.status = :status "
                        +
                        "ORDER BY s.type")
        List<SpaceEntity> findByTypeAndStatusWithImages(@Param("type") SpaceTypeForEntity type,
                        @Param("status") SpaceStatusForEntity status);

        /**
         * Find space by ID with images
         */
        @Query("SELECT s FROM SpaceEntity s LEFT JOIN FETCH s.images WHERE s.id = :id")
        Optional<SpaceEntity> findByIdWithImages(@Param("id") UUID id);

        /**
         * Find space by ID with allowed days
         */
        @Query("SELECT s FROM SpaceEntity s LEFT JOIN FETCH s.allowedDays WHERE s.id = :id")
        Optional<SpaceEntity> findByIdWithAllowedDays(@Param("id") UUID id);

        /**
         * Find space by ID with cleaning days
         */
        @Query("SELECT s FROM SpaceEntity s LEFT JOIN FETCH s.cleaningDays WHERE s.id = :id")
        Optional<SpaceEntity> findByIdWithCleaningDays(@Param("id") UUID id);

        /**
         * Find spaces with reservations
         */
        @Query("SELECT DISTINCT s FROM SpaceEntity s LEFT JOIN FETCH s.reservations WHERE s.status = :status")
        List<SpaceEntity> findByStatusWithReservations(@Param("status") SpaceStatusForEntity status);

        /**
         * Find space by ID with reservations
         */
        @Query("SELECT s FROM SpaceEntity s LEFT JOIN FETCH s.reservations WHERE s.id = :id")
        Optional<SpaceEntity> findByIdWithReservations(@Param("id") UUID id);

        // Removed findByStatusWithImagesAndReservations - causes
        // MultipleBagFetchException

        /**
         * Find active spaces with images and filters using JOIN FETCH
         * Note: Can only fetch one collection at a time due to Hibernate limitations
         */
        @Query("SELECT DISTINCT s FROM SpaceEntity s LEFT JOIN FETCH s.images WHERE s.status = 'ACTIVE' " +
                        "AND (:type IS NULL OR s.type = :type) " +
                        "ORDER BY s.type")
        List<SpaceEntity> findActiveSpacesWithImagesAndFilters(
                        @Param("type") SpaceTypeForEntity type);

        /**
         * Find active spaces with allowed days using JOIN FETCH
         */
        @Query("SELECT DISTINCT s FROM SpaceEntity s LEFT JOIN FETCH s.allowedDays WHERE s.status = 'ACTIVE' " +
                        "AND (:type IS NULL OR s.type = :type) " +
                        "ORDER BY s.type")
        List<SpaceEntity> findActiveSpacesWithAllowedDaysAndFilters(
                        @Param("type") SpaceTypeForEntity type);

        // Removed findByIdWithImagesAndReservations - causes MultipleBagFetchException

        /**
         * Find spaces with filters and pagination for admin (without search)
         */
        @Query("SELECT s FROM SpaceEntity s WHERE " +
                        "(:type IS NULL OR s.type = :type) AND " +
                        "(:status IS NULL OR s.status = :status) " +
                        "ORDER BY s.type")
        Page<SpaceEntity> findSpacesWithFilters(
                        @Param("type") SpaceTypeForEntity type,
                        @Param("status") SpaceStatusForEntity status,
                        Pageable pageable);

        /**
         * Find spaces with filters and pagination for public API
         */
        @Query("SELECT s FROM SpaceEntity s WHERE s.status = 'ACTIVE' AND " +
                        "(:type IS NULL OR s.type = :type) AND " +
                        "(:searchText IS NULL OR s.name LIKE %:searchText% OR s.description LIKE %:searchText%) " +
                        "ORDER BY s.type")
        Page<SpaceEntity> findActiveSpacesWithFilters(
                        @Param("type") SpaceTypeForEntity type,
                        @Param("searchText") String search,
                        Pageable pageable);

        /**
         * Find available spaces for a date range with type filter
         * Excludes spaces that have reservations on themselves or their shared spaces
         */
        @Query("SELECT s FROM SpaceEntity s WHERE s.status = 'ACTIVE' AND " +
                        "(:type IS NULL OR s.type = :type) AND " +
                        "s.id NOT IN (" +
                        "SELECT DISTINCT r.space.id FROM ReservationEntity r " +
                        "WHERE r.status IN ('CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))" +
                        ") AND " +
                        "s.id NOT IN (" +
                        "SELECT DISTINCT s2.id FROM SpaceEntity s2 " +
                        "JOIN s2.shareSpaceWith sharedId " +
                        "WHERE sharedId IN (" +
                        "SELECT DISTINCT r.space.id FROM ReservationEntity r " +
                        "WHERE r.status IN ('CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))" +
                        ")" +
                        ") " +
                        "ORDER BY s.type")
        Page<SpaceEntity> findAvailableSpacesWithFilters(
                        @Param("type") SpaceTypeForEntity type,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        Pageable pageable);

        /**
         * Find available spaces with images for a date range with type filter
         * Uses fetch join to load images to avoid LazyInitializationException
         */
        @Query("SELECT DISTINCT s FROM SpaceEntity s LEFT JOIN FETCH s.images WHERE s.status = 'ACTIVE' AND " +
                        "(:type IS NULL OR s.type = :type) AND " +
                        "s.id NOT IN (" +
                        "SELECT DISTINCT r.space.id FROM ReservationEntity r " +
                        "WHERE r.status IN ('CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))" +
                        ") AND " +
                        "s.id NOT IN (" +
                        "SELECT DISTINCT s2.id FROM SpaceEntity s2 " +
                        "JOIN s2.shareSpaceWith sharedId " +
                        "WHERE sharedId IN (" +
                        "SELECT DISTINCT r.space.id FROM ReservationEntity r " +
                        "WHERE r.status IN ('CONFIRMED', 'ACTIVE') " +
                        "AND ((r.startDate < :endDate AND r.endDate > :startDate))" +
                        ")" +
                        ") " +
                        "ORDER BY s.type")
        List<SpaceEntity> findAvailableSpacesWithImages(
                        @Param("type") SpaceTypeForEntity type,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}
