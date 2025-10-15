package com.neohoods.portal.platform.spaces.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.ReservationFeedbackEntity;

@Repository
public interface ReservationFeedbackRepository extends JpaRepository<ReservationFeedbackEntity, UUID> {

    /**
     * Find feedback by reservation ID
     */
    @Query("SELECT f FROM ReservationFeedbackEntity f WHERE f.reservation.id = :reservationId")
    Optional<ReservationFeedbackEntity> findByReservationId(@Param("reservationId") Long reservationId);

    /**
     * Find feedback by user ID
     */
    @Query("SELECT f FROM ReservationFeedbackEntity f WHERE f.user.id = :userId ORDER BY f.submittedAt DESC")
    List<ReservationFeedbackEntity> findByUserId(@Param("userId") UUID userId);

    /**
     * Find feedback by space ID
     */
    @Query("SELECT f FROM ReservationFeedbackEntity f WHERE f.reservation.space.id = :spaceId ORDER BY f.submittedAt DESC")
    List<ReservationFeedbackEntity> findBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * Check if feedback exists for a reservation
     */
    @Query("SELECT COUNT(f) > 0 FROM ReservationFeedbackEntity f WHERE f.reservation.id = :reservationId")
    boolean existsByReservationId(@Param("reservationId") Long reservationId);

    /**
     * Get average rating for a space
     */
    @Query("SELECT AVG(f.rating) FROM ReservationFeedbackEntity f WHERE f.reservation.space.id = :spaceId")
    Double getAverageRatingBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * Get average cleanliness rating for a space
     */
    @Query("SELECT AVG(f.cleanliness) FROM ReservationFeedbackEntity f WHERE f.reservation.space.id = :spaceId AND f.cleanliness IS NOT NULL")
    Double getAverageCleanlinessBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * Get average communication rating for a space
     */
    @Query("SELECT AVG(f.communication) FROM ReservationFeedbackEntity f WHERE f.reservation.space.id = :spaceId AND f.communication IS NOT NULL")
    Double getAverageCommunicationBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * Get average value rating for a space
     */
    @Query("SELECT AVG(f.value) FROM ReservationFeedbackEntity f WHERE f.reservation.space.id = :spaceId AND f.value IS NOT NULL")
    Double getAverageValueBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * Get feedback count for a space
     */
    @Query("SELECT COUNT(f) FROM ReservationFeedbackEntity f WHERE f.reservation.space.id = :spaceId")
    Long getFeedbackCountBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * Get recent feedback for a space (last N feedbacks)
     */
    @Query("SELECT f FROM ReservationFeedbackEntity f WHERE f.reservation.space.id = :spaceId ORDER BY f.submittedAt DESC")
    List<ReservationFeedbackEntity> findRecentFeedbackBySpaceId(@Param("spaceId") UUID spaceId,
            org.springframework.data.domain.Pageable pageable);
}
