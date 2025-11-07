package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.exceptions.ResourceNotFoundException;
import com.neohoods.portal.platform.services.MailService;
import com.neohoods.portal.platform.services.NotificationsService;
import com.neohoods.portal.platform.services.UnitsService;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.spaces.entities.AccessCodeEntity;
import com.neohoods.portal.platform.spaces.entities.PaymentStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationAuditLogEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

@Service
@Transactional
public class ReservationsService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationsService.class);

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private AccessCodeService accessCodeService;

    @Autowired
    private DigitalLockService digitalLockService;

    @Autowired
    private ReservationFeedbackService feedbackService;

    @Autowired
    private ReservationAuditService auditService;

    @Autowired
    private MailService mailService;

    @Autowired
    private NotificationsService notificationsService;

    @Autowired
    private CleaningNotificationService cleaningNotificationService;

    @Autowired
    private UnitsService unitsService;

    /**
     * Get all reservations for a user
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getUserReservations(UserEntity user) {
        return reservationRepository.findByUser(user);
    }

    /**
     * Get all reservations (for admin)
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getAllReservations() {
        return reservationRepository.findAll();
    }

    /**
     * Get reservations with filters and pagination for admin
     */
    @Transactional(readOnly = true)
    public Page<ReservationEntity> getReservationsWithFilters(UUID spaceId, UUID userId,
            ReservationStatusForEntity status, LocalDate startDate, LocalDate endDate,
            SpaceTypeForEntity spaceType, Pageable pageable) {

        // Handle null parameters to avoid PostgreSQL type determination issues
        // When all parameters are null, PostgreSQL can't determine the parameter types
        if (spaceId == null && userId == null && status == null && startDate == null && endDate == null
                && spaceType == null) {
            // If all filters are null, return all reservations with pagination
            return reservationRepository.findAll(pageable);
        }

        // Use specific method for space + date range queries (common case for calendar)
        // Only if no spaceType filter is applied
        if (spaceId != null && startDate != null && endDate != null && userId == null && status == null
                && spaceType == null) {
            return reservationRepository.findReservationsBySpaceAndDateRange(spaceId, startDate, endDate, pageable);
        }

        return reservationRepository.findReservationsWithFilters(spaceId, userId, status, startDate, endDate, spaceType,
                pageable);
    }

    /**
     * Get user reservations with filters and pagination
     */
    @Transactional(readOnly = true)
    public Page<ReservationEntity> getUserReservationsWithFilters(UUID userId, ReservationStatusForEntity status,
            SpaceTypeForEntity spaceType, Pageable pageable) {
        return reservationRepository.findUserReservationsWithFilters(userId, status, spaceType, pageable);
    }

    /**
     * Get all reservations for a user with pagination
     */
    @Transactional(readOnly = true)
    public Page<ReservationEntity> getUserReservations(UserEntity user, Pageable pageable) {
        return reservationRepository.findByUser(user, pageable);
    }

    /**
     * Get all reservations by status
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getReservationsByStatus(ReservationStatusForEntity status) {
        return reservationRepository.findByStatus(status);
    }

    /**
     * Get all reservations by status with pagination
     */
    @Transactional(readOnly = true)
    public Page<ReservationEntity> getReservationsByStatus(ReservationStatusForEntity status, Pageable pageable) {
        return reservationRepository.findByStatus(status, pageable);
    }

    /**
     * Get reservations by status for a user
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getUserReservationsByStatus(UserEntity user, ReservationStatusForEntity status) {
        return reservationRepository.findByUserAndStatus(user, status);
    }

    /**
     * Get reservations by status for a user with pagination
     */
    @Transactional(readOnly = true)
    public Page<ReservationEntity> getUserReservationsByStatus(UserEntity user, ReservationStatusForEntity status,
            Pageable pageable) {
        return reservationRepository.findByUserAndStatus(user, status, pageable);
    }

    /**
     * Get all reservations for a space
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getSpaceReservations(SpaceEntity space) {
        return reservationRepository.findBySpace(space);
    }

    /**
     * Get all reservations for a space with pagination
     */
    @Transactional(readOnly = true)
    public Page<ReservationEntity> getSpaceReservations(SpaceEntity space, Pageable pageable) {
        return reservationRepository.findBySpace(space, pageable);
    }

    /**
     * Get reservation by ID
     */
    @Transactional(readOnly = true)
    public ReservationEntity getReservationById(UUID id) {
        return reservationRepository.findByIdWithSpaceAndAccessCode(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    /**
     * Get active reservations (currently ongoing)
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getActiveReservations() {
        return reservationRepository.findActiveReservations(LocalDate.now());
    }

    /**
     * Get upcoming reservations
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getUpcomingReservations() {
        return reservationRepository.findUpcomingReservations(LocalDate.now());
    }

    /**
     * Get past reservations
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getPastReservations() {
        return reservationRepository.findPastReservations(LocalDate.now());
    }

    /**
     * Create a new reservation
     */
    public ReservationEntity createReservation(SpaceEntity space, UserEntity user, LocalDate startDate,
            LocalDate endDate) {
        // Validate the reservation - this will throw specific coded exceptions with
        // context
        spacesService.validateUserCanReserveSpace(space.getId(), user.getId(), startDate, endDate);

        // Determine if user is an owner (you may need to adjust this logic based on
        // your user types)
        boolean isOwner = user.getType() != null && "OWNER".equals(user.getType().toString());

        // Calculate detailed price breakdown with platform fees
        PriceCalculationResult priceBreakdown = spacesService.calculatePriceBreakdown(space.getId(), startDate, endDate,
                isOwner);

        // Get primary unit for user (if user is TENANT or OWNER)
        // For spaces that require unit (COMMON_ROOM, COWORKING), unit will be set
        // For other spaces, unit may be null
        UnitEntity unit = null;
        boolean requiresUnit = space.getType() == SpaceTypeForEntity.COMMON_ROOM ||
                space.getType() == SpaceTypeForEntity.COWORKING;
        
        if (requiresUnit) {
            try {
                unit = unitsService.getPrimaryUnitForUser(user.getId()).block();
            } catch (Exception e) {
                // If unit is required but not found, validation should have caught this
                // But if it's not required, we can continue with null unit
                logger.warn("Could not get primary unit for user {}: {}", user.getId(), e.getMessage());
            }
        } else {
            // For spaces that don't require unit, try to get it anyway if user has one
            try {
                unit = unitsService.getPrimaryUnitForUser(user.getId()).block();
            } catch (Exception e) {
                // User doesn't have a primary unit, that's ok for non-required spaces
                logger.debug("User {} does not have a primary unit, continuing without unit", user.getId());
            }
        }

        // Create the reservation
        ReservationEntity reservation;
        if (unit != null) {
            reservation = new ReservationEntity(space, user, unit, startDate, endDate,
                    priceBreakdown.getTotalPrice());
        } else {
            reservation = new ReservationEntity(space, user, startDate, endDate,
                    priceBreakdown.getTotalPrice());
        }
        reservation.setStatus(ReservationStatusForEntity.PENDING_PAYMENT);
        reservation.setPaymentStatus(PaymentStatusForEntity.PENDING);
        reservation.setPaymentExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15));

        // Store platform fee amounts
        reservation.setPlatformFeeAmount(priceBreakdown.getPlatformFeeAmount());
        reservation.setPlatformFixedFeeAmount(priceBreakdown.getPlatformFixedFeeAmount());

        // Save the reservation
        reservation = reservationRepository.save(reservation);

        // Log audit event
        String performedBy = user.getUsername() != null ? user.getUsername()
                : (user.getEmail() != null ? user.getEmail() : "system");
        auditService.logEvent(reservation.getId(), ReservationAuditLogEntity.STATUS_CHANGE,
                null, ReservationStatusForEntity.PENDING_PAYMENT.toString(),
                "Reservation created", performedBy);

        // Increment used annual reservations for the space
        spacesService.incrementUsedAnnualReservations(space.getId());

        return reservation;
    }

    /**
     * Confirm a reservation (after successful payment)
     */
    public ReservationEntity confirmReservation(UUID reservationId, String stripePaymentIntentId,
            String stripeSessionId) {
        ReservationEntity reservation = getReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatusForEntity.PENDING_PAYMENT) {
            throw new CodedErrorException(CodedError.RESERVATION_NOT_PENDING_PAYMENT, "reservationId", reservationId);
        }

        String oldStatus = reservation.getStatus().toString();
        reservation.setStatus(ReservationStatusForEntity.CONFIRMED);
        reservation.setPaymentStatus(PaymentStatusForEntity.SUCCEEDED);
        reservation.setStripePaymentIntentId(stripePaymentIntentId);
        reservation.setStripeSessionId(stripeSessionId);

        // Generate access code for all confirmed reservations
        // For future reservations, the code will be sent by scheduled job on day of
        accessCodeService.generateAccessCode(reservation);

        reservation = reservationRepository.save(reservation);

        // Get user and space for audit logging
        UserEntity user = reservation.getUser();
        SpaceEntity space = reservation.getSpace();

        // Log audit events
        String performedBy = user.getUsername() != null ? user.getUsername()
                : (user.getEmail() != null ? user.getEmail() : "system");
        auditService.logStatusChange(reservation.getId(), oldStatus,
                ReservationStatusForEntity.CONFIRMED.toString(), performedBy);
        auditService.logConfirmation(reservation.getId(), performedBy);
        if (stripePaymentIntentId != null) {
            auditService.logPaymentReceived(reservation.getId(), stripePaymentIntentId, performedBy);
        }

        // Get user and space for emails and notifications

        // Send confirmation email
        try {
            String accessCode = null;
            if (reservation.getAccessCode() != null) {
                accessCode = reservation.getAccessCode().getCode();
            }

            // Format dates in French format
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH);
            String startDate = reservation.getStartDate().format(formatter);
            String endDate = reservation.getEndDate().format(formatter);

            mailService.sendReservationConfirmationEmail(user, space.getName(),
                    startDate, endDate, accessCode, reservation.getId(), space.getId(), java.util.Locale.FRENCH);
        } catch (Exception e) {
            logger.error("Failed to send confirmation email for reservation {}", reservationId, e);
            // Don't fail the reservation confirmation if email fails
        }

        // Send notifications
        try {
            // Check if notifications are enabled for this space
            if (space.getEnableNotifications() != null && space.getEnableNotifications()) {
                // Find admin user (assuming first admin user for now)
                UserEntity adminUser = findAdminUser();
                notificationsService.notifyReservationConfirmed(reservation.getId(), space.getName(), user, adminUser);
            }
        } catch (Exception e) {
            logger.error("Failed to send notification for reservation {}", reservationId, e);
            // Don't fail the reservation confirmation if notification fails
        }

        // Send cleaning company notification
        try {
            cleaningNotificationService.sendBookingConfirmationEmail(reservation);
        } catch (Exception e) {
            logger.error("Failed to send cleaning notification for reservation {}", reservationId, e);
            // Don't fail the reservation confirmation if cleaning notification fails
        }

        return reservation;
    }

    /**
     * Find an admin user for notifications
     */
    private UserEntity findAdminUser() {
        // For now, return null - in a real implementation, you would query for admin
        // users
        // This could be enhanced to find the appropriate admin based on space or other
        // criteria
        return null;
    }

    /**
     * Activate a reservation (when start date is reached)
     */
    public ReservationEntity activateReservation(UUID reservationId) {
        ReservationEntity reservation = getReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatusForEntity.CONFIRMED) {
            throw new CodedErrorException(CodedError.RESERVATION_NOT_CONFIRMED, "reservationId", reservationId);
        }

        if (!reservation.getStartDate().equals(LocalDate.now())) {
            throw new CodedErrorException(CodedError.RESERVATION_START_DATE_NOT_TODAY, "reservationId", reservationId);
        }

        String oldStatus = reservation.getStatus().toString();
        reservation.setStatus(ReservationStatusForEntity.ACTIVE);

        // Generate access code if not already generated
        if (reservation.getAccessCode() == null) {
            accessCodeService.generateAccessCode(reservation);
        }

        reservation = reservationRepository.save(reservation);

        // Log audit event
        String performedBy = reservation.getUser().getUsername() != null ? reservation.getUser().getUsername()
                : (reservation.getUser().getEmail() != null ? reservation.getUser().getEmail() : "system");
        auditService.logStatusChange(reservation.getId(), oldStatus,
                ReservationStatusForEntity.ACTIVE.toString(), performedBy);

        return reservation;
    }

    /**
     * Complete a reservation (when end date is reached)
     */
    public ReservationEntity completeReservation(UUID reservationId) {
        ReservationEntity reservation = getReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatusForEntity.ACTIVE) {
            throw new CodedErrorException(CodedError.RESERVATION_NOT_ACTIVE, "reservationId", reservationId);
        }

        if (!reservation.getEndDate().isBefore(LocalDate.now())) {
            throw new CodedErrorException(CodedError.RESERVATION_END_DATE_NOT_REACHED, "reservationId", reservationId);
        }

        String oldStatus = reservation.getStatus().toString();
        reservation.setStatus(ReservationStatusForEntity.COMPLETED);

        // Deactivate access code
        if (reservation.getAccessCode() != null) {
            accessCodeService.deactivateAccessCode(reservation.getAccessCode());
        }

        reservation = reservationRepository.save(reservation);

        // Log audit event
        String performedBy = reservation.getUser().getUsername() != null ? reservation.getUser().getUsername()
                : (reservation.getUser().getEmail() != null ? reservation.getUser().getEmail() : "system");
        auditService.logStatusChange(reservation.getId(), oldStatus,
                ReservationStatusForEntity.COMPLETED.toString(), performedBy);

        return reservation;
    }

    /**
     * Cancel a reservation
     */
    public ReservationEntity cancelReservation(UUID reservationId, String reason, String cancelledBy) {
        ReservationEntity reservation = getReservationById(reservationId);

        if (reservation.getStatus() == ReservationStatusForEntity.CANCELLED) {
            throw new CodedErrorException(CodedError.RESERVATION_ALREADY_CANCELLED, "reservationId", reservationId);
        }

        if (reservation.getStatus() == ReservationStatusForEntity.COMPLETED) {
            throw new CodedErrorException(CodedError.RESERVATION_ALREADY_COMPLETED, "reservationId", reservationId);
        }

        String oldStatus = reservation.getStatus().toString();
        reservation.setStatus(ReservationStatusForEntity.CANCELLED);
        reservation.setCancellationReason(reason);
        reservation.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC));
        reservation.setCancelledBy(cancelledBy);

        // Deactivate access code if exists
        if (reservation.getAccessCode() != null) {
            accessCodeService.deactivateAccessCode(reservation.getAccessCode());
        }

        // Decrement used annual reservations for the space
        spacesService.decrementUsedAnnualReservations(reservation.getSpace().getId());

        reservation = reservationRepository.save(reservation);

        // Log audit events
        auditService.logStatusChange(reservation.getId(), oldStatus,
                ReservationStatusForEntity.CANCELLED.toString(), cancelledBy);
        auditService.logCancellation(reservation.getId(), reason, cancelledBy);

        // Send cleaning company cancellation notification
        try {
            cleaningNotificationService.sendCancellationEmail(reservation);
        } catch (Exception e) {
            logger.error("Failed to send cleaning cancellation notification for reservation {}", reservationId, e);
            // Don't fail the cancellation if cleaning notification fails
        }

        return reservation;
    }

    /**
     * Expire a reservation (when payment timeout is reached)
     */
    public ReservationEntity expireReservation(UUID reservationId, String reason) {
        ReservationEntity reservation = getReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatusForEntity.PENDING_PAYMENT) {
            throw new CodedErrorException(CodedError.RESERVATION_NOT_PENDING_PAYMENT, "reservationId", reservationId);
        }

        String oldStatus = reservation.getStatus().toString();
        reservation.setStatus(ReservationStatusForEntity.EXPIRED);
        reservation.setCancellationReason(reason);
        reservation.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC));
        reservation.setCancelledBy("system");

        // Deactivate access code if exists
        if (reservation.getAccessCode() != null) {
            accessCodeService.deactivateAccessCode(reservation.getAccessCode());
        }

        // Decrement used annual reservations for the space
        spacesService.decrementUsedAnnualReservations(reservation.getSpace().getId());

        reservation = reservationRepository.save(reservation);

        // Log audit events
        auditService.logStatusChange(reservation.getId(), oldStatus,
                ReservationStatusForEntity.EXPIRED.toString(), "system");
        auditService.logCancellation(reservation.getId(), reason, "system");

        return reservation;
    }

    /**
     * Retry payment for a failed reservation
     */
    public ReservationEntity retryPayment(UUID reservationId) {
        ReservationEntity reservation = getReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatusForEntity.PAYMENT_FAILED &&
                reservation.getStatus() != ReservationStatusForEntity.PENDING_PAYMENT) {
            throw new CodedErrorException(CodedError.RESERVATION_CANNOT_RETRY_PAYMENT, "reservationId", reservationId);
        }

        // Reset payment expiration time
        reservation.setPaymentExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15));

        // Keep current status (don't change from PAYMENT_FAILED to PENDING_PAYMENT)
        // The Stripe service will create a new PaymentIntent

        return reservationRepository.save(reservation);
    }

    /**
     * Update reservation payment status
     */
    public ReservationEntity updatePaymentStatus(UUID reservationId, PaymentStatusForEntity paymentStatus,
            String stripePaymentIntentId) {
        ReservationEntity reservation = getReservationById(reservationId);

        reservation.setPaymentStatus(paymentStatus);
        if (stripePaymentIntentId != null) {
            reservation.setStripePaymentIntentId(stripePaymentIntentId);
        }

        return reservationRepository.save(reservation);
    }

    /**
     * Get reservation by Stripe payment intent ID
     */
    @Transactional(readOnly = true)
    public Optional<ReservationEntity> getReservationByStripePaymentIntentId(String stripePaymentIntentId) {
        return reservationRepository.findByStripePaymentIntentId(stripePaymentIntentId);
    }

    /**
     * Get reservation by Stripe session ID
     */
    @Transactional(readOnly = true)
    public Optional<ReservationEntity> getReservationByStripeSessionId(String stripeSessionId) {
        return reservationRepository.findByStripeSessionId(stripeSessionId);
    }

    /**
     * Process reservations that need to be activated (start date reached)
     */
    public void processReservationsToActivate() {
        List<ReservationEntity> reservationsToActivate = reservationRepository
                .findReservationsToActivate(LocalDate.now());

        for (ReservationEntity reservation : reservationsToActivate) {
            try {
                activateReservation(reservation.getId());
            } catch (Exception e) {
                logger.error("Error activating reservation {}: {}", reservation.getId(), e.getMessage(), e);
                // Continue processing other reservations
            }
        }
    }

    /**
     * Process reservations that need to be completed (end date reached)
     */
    public void processReservationsToComplete() {
        List<ReservationEntity> reservationsToComplete = reservationRepository
                .findReservationsToComplete(LocalDate.now());

        for (ReservationEntity reservation : reservationsToComplete) {
            try {
                completeReservation(reservation.getId());
            } catch (Exception e) {
                logger.error("Error completing reservation {}: {}", reservation.getId(), e.getMessage(), e);
                // Continue processing other reservations
            }
        }
    }

    /**
     * Check if a user has reached their annual reservation quota for a space
     * @deprecated Use hasUnitReachedAnnualQuota instead
     */
    @Deprecated
    @Transactional(readOnly = true)
    public boolean hasUserReachedAnnualQuota(UserEntity user, UUID spaceId, int year) {
        SpaceEntity space = spacesService.getSpaceById(spaceId);

        if (space.getMaxAnnualReservations() == 0) {
            return false; // No quota limit
        }

        Long userReservationsCount = reservationRepository.countReservationsByUserAndYear(user, year);
        return userReservationsCount >= space.getMaxAnnualReservations();
    }

    /**
     * Check if a unit has reached its annual reservation quota for a space
     */
    @Transactional(readOnly = true)
    public boolean hasUnitReachedAnnualQuota(UUID unitId, UUID spaceId, int year) {
        SpaceEntity space = spacesService.getSpaceById(spaceId);

        if (space.getMaxAnnualReservations() == 0) {
            return false; // No quota limit
        }

        Long unitReservationsCount = reservationRepository.countReservationsByUnitAndYear(unitId, year);
        return unitReservationsCount >= space.getMaxAnnualReservations();
    }

    /**
     * Get reservations for a unit
     */
    @Transactional(readOnly = true)
    public Page<ReservationEntity> getReservationsByUnit(UUID unitId, Pageable pageable) {
        return reservationRepository.findByUnitId(unitId, pageable);
    }

    /**
     * Get reservations in a date range
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getReservationsInDateRange(LocalDate startDate, LocalDate endDate) {
        return reservationRepository.findReservationsInDateRange(startDate, endDate);
    }

    /**
     * Get reservations for a specific space in a date range
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getReservationsForSpaceInDateRange(UUID spaceId, LocalDate startDate,
            LocalDate endDate) {
        return reservationRepository.findReservationsForSpaceInDateRange(spaceId, startDate, endDate);
    }

    /**
     * Get a user's reservation by ID
     */
    @Transactional(readOnly = true)
    public ReservationEntity getUserReservationById(UserEntity user, UUID reservationId) {
        return reservationRepository.findByUserAndId(user, reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
    }

    /**
     * Get user's active reservations
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getUserActiveReservations(UserEntity user) {
        return reservationRepository.findByUserAndStatus(user, ReservationStatusForEntity.CONFIRMED);
    }

    /**
     * Get user's upcoming reservations
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getUserUpcomingReservations(UserEntity user) {
        return reservationRepository.findByUserAndStartDateAfterAndStatus(user, LocalDate.now(),
                ReservationStatusForEntity.CONFIRMED);
    }

    /**
     * Get user's past reservations
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getUserPastReservations(UserEntity user) {
        return reservationRepository.findByUserAndEndDateBeforeAndStatus(user, LocalDate.now(),
                ReservationStatusForEntity.COMPLETED);
    }

    /**
     * Get user's reservations in date range
     */
    @Transactional(readOnly = true)
    public List<ReservationEntity> getUserReservationsInDateRange(UserEntity user, LocalDate startDate,
            LocalDate endDate) {
        return reservationRepository.findByUserAndStartDateBetweenOrEndDateBetween(user, startDate, endDate, startDate,
                endDate);
    }

    /**
     * Cancel a user's reservation
     */
    @Transactional
    public ReservationEntity cancelUserReservation(UserEntity user, UUID reservationId, String reason) {
        ReservationEntity reservation = getUserReservationById(user, reservationId);
        reservation.setStatus(ReservationStatusForEntity.CANCELLED);
        reservation.setCancellationReason(reason);
        reservation.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC));
        return reservationRepository.save(reservation);
    }

    /**
     * Submit feedback for a reservation
     */
    @Transactional
    public void submitReservationFeedback(UserEntity user, UUID reservationId, Integer rating, String comment) {
        ReservationEntity reservation = getUserReservationById(user, reservationId);

        // Validate that the reservation is completed or active (user can only give
        // feedback after using the space)
        if (reservation.getStatus() != ReservationStatusForEntity.COMPLETED &&
                reservation.getStatus() != ReservationStatusForEntity.ACTIVE) {
            throw new CodedErrorException(CodedError.RESERVATION_NOT_COMPLETED_OR_ACTIVE, "reservationId",
                    reservationId);
        }

        // Check if feedback already exists
        Long reservationIdLong = Long.valueOf(reservation.getId().toString().replaceAll("-", "").substring(0, 8), 16);
        if (feedbackService.hasFeedbackForReservation(reservationIdLong)) {
            logger.warn("User {} attempted to submit duplicate feedback for reservation {}",
                    user.getId(), reservationId);
            throw new CodedErrorException(CodedError.FEEDBACK_ALREADY_SUBMITTED, "reservationId", reservationId);
        }

        // Submit feedback using the feedback service
        try {
            feedbackService.submitFeedback(reservationIdLong, user.getId(), rating, comment, null, null, null);
            logger.info("Feedback submitted successfully for reservation {} by user {}",
                    reservationId, user.getId());
        } catch (Exception e) {
            logger.error("Failed to submit feedback for reservation {} by user {}: {}",
                    reservationId, user.getId(), e.getMessage(), e);
            throw new CodedErrorException(CodedError.FEEDBACK_SUBMISSION_FAILED, Map.of("reservationId", reservationId),
                    e);
        }
    }

    /**
     * Update a reservation
     */
    public ReservationEntity updateReservation(ReservationEntity reservation) {
        return reservationRepository.save(reservation);
    }

    /**
     * Get access code for a reservation
     */
    @Transactional(readOnly = true)
    public String getAccessCodeForReservation(ReservationEntity reservation) {
        // Check if reservation has an access code
        if (reservation.getAccessCode() != null) {
            return reservation.getAccessCode().getCode();
        }

        // If no access code exists, generate one
        AccessCodeEntity accessCode = accessCodeService.generateAccessCode(reservation);
        return accessCode.getCode();
    }

    /**
     * Regenerate access code for a reservation
     */
    @Transactional
    public String regenerateAccessCodeForReservation(ReservationEntity reservation) {
        // Deactivate existing access code if it exists
        if (reservation.getAccessCode() != null) {
            accessCodeService.deactivateAccessCode(reservation.getAccessCode());
        }

        // Generate new access code
        AccessCodeEntity newAccessCode = accessCodeService.generateAccessCode(reservation);
        return newAccessCode.getCode();
    }

    /**
     * Get reservation by Stripe payment intent ID
     */
    @Transactional(readOnly = true)
    public ReservationEntity getReservationByPaymentIntentId(String paymentIntentId) {
        Optional<ReservationEntity> reservation = reservationRepository.findByStripePaymentIntentId(paymentIntentId);
        if (reservation.isEmpty()) {
            throw new ResourceNotFoundException("Reservation not found with payment intent ID: " + paymentIntentId);
        }
        return reservation.get();
    }

    /**
     * Debug method to find reservations for a space between specific dates
     * Usage: debugReservationsForSpace(spaceId, "2024-10-25", "2024-10-30")
     */
    public List<ReservationEntity> debugReservationsForSpace(UUID spaceId, String startDateStr, String endDateStr) {
        try {
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);

            logger.info("=== DEBUG RESERVATIONS ===");
            logger.info("Space ID: {}", spaceId);
            logger.info("Date range: {} to {}", startDate, endDate);

            // Utiliser la méthode existante du repository
            List<ReservationEntity> reservations = reservationRepository
                    .findReservationsForSpaceInDateRange(spaceId, startDate, endDate);

            logger.info("Found {} reservations in date range", reservations.size());

            // Afficher les détails de chaque réservation
            for (ReservationEntity reservation : reservations) {
                logger.info("Reservation ID: {}, Status: {}, Start: {}, End: {}, User: {}, Price: {}",
                        reservation.getId(),
                        reservation.getStatus(),
                        reservation.getStartDate(),
                        reservation.getEndDate(),
                        reservation.getUser().getUsername(),
                        reservation.getTotalPrice());
            }

            // Alternative: récupérer toutes les réservations et filtrer avec stream
            List<ReservationEntity> allReservations = reservationRepository.findBySpace(
                    spacesService.getSpaceById(spaceId));

            logger.info("Total reservations for this space: {}", allReservations.size());

            List<ReservationEntity> streamFiltered = allReservations.stream()
                    .filter(reservation -> {
                        LocalDate resStart = reservation.getStartDate();
                        LocalDate resEnd = reservation.getEndDate();

                        // Vérifier si la réservation chevauche avec la période demandée
                        return (resStart.isBefore(endDate.plusDays(1)) &&
                                resEnd.isAfter(startDate.minusDays(1)));
                    })
                    .filter(reservation -> {
                        // Filtrer seulement les réservations actives
                        return reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT ||
                                reservation.getStatus() == ReservationStatusForEntity.CONFIRMED ||
                                reservation.getStatus() == ReservationStatusForEntity.ACTIVE;
                    })
                    .collect(Collectors.toList());

            logger.info("Stream filtered reservations: {}", streamFiltered.size());

            return reservations;

        } catch (Exception e) {
            logger.error("Error in debugReservationsForSpace: {}", e.getMessage(), e);
            throw new CodedErrorException(CodedError.SPACE_NOT_FOUND, "spaceId", spaceId);
        }
    }
}
