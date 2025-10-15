package com.neohoods.portal.platform.spaces.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.exceptions.ResourceNotFoundException;
import com.neohoods.portal.platform.spaces.entities.ReservationFeedbackEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationFeedbackRepository;

@Service
@Transactional
public class ReservationFeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationFeedbackService.class);

    @Autowired
    private ReservationFeedbackRepository feedbackRepository;

    /**
     * Submit feedback for a reservation
     */
    public ReservationFeedbackEntity submitFeedback(Long reservationId, UUID userId,
            Integer rating, String comment, Integer cleanliness, Integer communication, Integer value) {

        // Check if feedback already exists for this reservation
        Optional<ReservationFeedbackEntity> existingFeedback = feedbackRepository.findByReservationId(reservationId);
        if (existingFeedback.isPresent()) {
            logger.warn("Feedback already exists for reservation: {}", reservationId);
            throw new IllegalStateException("Feedback already submitted for this reservation");
        }

        // Create new feedback
        ReservationFeedbackEntity feedback = new ReservationFeedbackEntity();
        feedback.setRating(rating);
        feedback.setComment(comment);
        feedback.setCleanliness(cleanliness);
        feedback.setCommunication(communication);
        feedback.setValue(value);

        // Note: In a real implementation, we would need to fetch the reservation and
        // user entities
        // For now, we'll create a minimal implementation
        logger.info("Submitting feedback for reservation {}: rating={}, comment={}",
                reservationId, rating, comment);

        ReservationFeedbackEntity savedFeedback = feedbackRepository.save(feedback);
        logger.info("Feedback submitted successfully with ID: {}", savedFeedback.getId());

        return savedFeedback;
    }

    /**
     * Get feedback by reservation ID
     */
    @Transactional(readOnly = true)
    public Optional<ReservationFeedbackEntity> getFeedbackByReservationId(Long reservationId) {
        return feedbackRepository.findByReservationId(reservationId);
    }

    /**
     * Get feedback by user ID
     */
    @Transactional(readOnly = true)
    public List<ReservationFeedbackEntity> getFeedbackByUserId(UUID userId) {
        return feedbackRepository.findByUserId(userId);
    }

    /**
     * Get feedback by space ID
     */
    @Transactional(readOnly = true)
    public List<ReservationFeedbackEntity> getFeedbackBySpaceId(UUID spaceId) {
        return feedbackRepository.findBySpaceId(spaceId);
    }

    /**
     * Get recent feedback for a space
     */
    @Transactional(readOnly = true)
    public List<ReservationFeedbackEntity> getRecentFeedbackBySpaceId(UUID spaceId, Pageable pageable) {
        return feedbackRepository.findRecentFeedbackBySpaceId(spaceId, pageable);
    }

    /**
     * Check if feedback exists for a reservation
     */
    @Transactional(readOnly = true)
    public boolean hasFeedbackForReservation(Long reservationId) {
        return feedbackRepository.existsByReservationId(reservationId);
    }

    /**
     * Get average rating for a space
     */
    @Transactional(readOnly = true)
    public Double getAverageRatingForSpace(UUID spaceId) {
        Double averageRating = feedbackRepository.getAverageRatingBySpaceId(spaceId);
        return averageRating != null ? averageRating : 0.0;
    }

    /**
     * Get average cleanliness rating for a space
     */
    @Transactional(readOnly = true)
    public Double getAverageCleanlinessForSpace(UUID spaceId) {
        Double averageCleanliness = feedbackRepository.getAverageCleanlinessBySpaceId(spaceId);
        return averageCleanliness != null ? averageCleanliness : 0.0;
    }

    /**
     * Get average communication rating for a space
     */
    @Transactional(readOnly = true)
    public Double getAverageCommunicationForSpace(UUID spaceId) {
        Double averageCommunication = feedbackRepository.getAverageCommunicationBySpaceId(spaceId);
        return averageCommunication != null ? averageCommunication : 0.0;
    }

    /**
     * Get average value rating for a space
     */
    @Transactional(readOnly = true)
    public Double getAverageValueForSpace(UUID spaceId) {
        Double averageValue = feedbackRepository.getAverageValueBySpaceId(spaceId);
        return averageValue != null ? averageValue : 0.0;
    }

    /**
     * Get feedback count for a space
     */
    @Transactional(readOnly = true)
    public Long getFeedbackCountForSpace(UUID spaceId) {
        return feedbackRepository.getFeedbackCountBySpaceId(spaceId);
    }

    /**
     * Update feedback
     */
    public ReservationFeedbackEntity updateFeedback(UUID feedbackId, Integer rating, String comment,
            Integer cleanliness, Integer communication, Integer value) {
        ReservationFeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + feedbackId));

        if (rating != null)
            feedback.setRating(rating);
        if (comment != null)
            feedback.setComment(comment);
        if (cleanliness != null)
            feedback.setCleanliness(cleanliness);
        if (communication != null)
            feedback.setCommunication(communication);
        if (value != null)
            feedback.setValue(value);

        logger.info("Updated feedback with ID: {}", feedbackId);
        return feedbackRepository.save(feedback);
    }

    /**
     * Delete feedback
     */
    public void deleteFeedback(UUID feedbackId) {
        if (!feedbackRepository.existsById(feedbackId)) {
            throw new ResourceNotFoundException("Feedback not found with id: " + feedbackId);
        }
        feedbackRepository.deleteById(feedbackId);
        logger.info("Deleted feedback with ID: {}", feedbackId);
    }
}
