package com.neohoods.portal.platform.services.matrix.assistant;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending notifications when reservations are completed
 * This service can be called from webhooks or scheduled tasks
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.notifications.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantNotificationService {

    private final ReservationRepository reservationRepository;
    private final MatrixAssistantService matrixAssistantService;
    private final MessageSource messageSource;

    /**
     * Send notification when a reservation is completed
     * Called from Stripe webhook or when reservation status changes to COMPLETED
     */
    @Transactional
    public void notifyReservationCompleted(ReservationEntity reservation) {
        if (reservation.getStatus() != ReservationStatusForEntity.COMPLETED) {
            log.warn("Reservation {} is not completed, skipping notification", reservation.getId());
            return;
        }

        try {
            UserEntity user = reservation.getUser();
            Locale locale = user.getLocale();

            String title = messageSource.getMessage("matrix.notification.reservation.completed.title", null, locale);
            String reservationLabel = messageSource.getMessage("matrix.notification.reservation.completed.reservation",
                    null, locale);
            String spaceLabel = messageSource.getMessage("matrix.notification.reservation.completed.space", null,
                    locale);
            String dateLabel = messageSource.getMessage("matrix.notification.reservation.completed.date", null, locale);
            String thanks = messageSource.getMessage("matrix.notification.reservation.completed.thanks", null, locale);
            String nextSteps = messageSource.getMessage("matrix.notification.reservation.completed.nextSteps", null,
                    locale);
            String accessCode = messageSource.getMessage("matrix.notification.reservation.completed.accessCode", null,
                    locale);
            String feedback = messageSource.getMessage("matrix.notification.reservation.completed.feedback", null,
                    locale);
            String closing = messageSource.getMessage("matrix.notification.reservation.completed.closing", null,
                    locale);

            String message = String.format(
                    "✅ **%s**\n\n" +
                            "📋 **%s:** %s\n" +
                            "🏠 **%s:** %s\n" +
                            "📅 **%s:** %s - %s\n\n" +
                            "%s\n\n" +
                            "💡 **%s:**\n" +
                            "- %s\n" +
                            "- %s\n\n" +
                            "%s 🎉",
                    title,
                    reservationLabel, reservation.getId(),
                    spaceLabel, reservation.getSpace().getName(),
                    dateLabel, reservation.getStartDate(), reservation.getEndDate(),
                    thanks,
                    nextSteps,
                    accessCode,
                    feedback,
                    closing);

            sendMessageToUser(user, message, reservation.getId());
        } catch (Exception e) {
            log.error("Error sending completion notification for reservation {}: {}",
                    reservation.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send notification when payment is successful
     */
    @Transactional
    public void notifyPaymentSuccessful(ReservationEntity reservation) {
        try {
            UserEntity user = reservation.getUser();
            Locale locale = user.getLocale();

            String title = messageSource.getMessage("matrix.notification.payment.successful.title", null, locale);
            String reservationLabel = messageSource.getMessage("matrix.notification.payment.successful.reservation",
                    null, locale);
            String spaceLabel = messageSource.getMessage("matrix.notification.payment.successful.space", null, locale);
            String dateLabel = messageSource.getMessage("matrix.notification.payment.successful.date", null, locale);
            String amountLabel = messageSource.getMessage("matrix.notification.payment.successful.amount", null,
                    locale);
            String confirmed = messageSource.getMessage("matrix.notification.payment.successful.confirmed", null,
                    locale);
            String nextSteps = messageSource.getMessage("matrix.notification.payment.successful.nextSteps", null,
                    locale);
            String reminder = messageSource.getMessage("matrix.notification.payment.successful.reminder", null, locale);
            String accessCode = messageSource.getMessage("matrix.notification.payment.successful.accessCode", null,
                    locale);
            String command = messageSource.getMessage("matrix.notification.payment.successful.command", null, locale);
            String closing = messageSource.getMessage("matrix.notification.payment.successful.closing", null, locale);

            String message = String.format(
                    "💳 **%s**\n\n" +
                            "📋 **%s:** %s\n" +
                            "🏠 **%s:** %s\n" +
                            "📅 **%s:** %s - %s\n" +
                            "💰 **%s:** %s€\n\n" +
                            "%s\n\n" +
                            "💡 **%s:**\n" +
                            "- %s\n" +
                            "- %s\n" +
                            "- %s\n\n" +
                            "%s 🎉",
                    title,
                    reservationLabel, reservation.getId(),
                    spaceLabel, reservation.getSpace().getName(),
                    dateLabel, reservation.getStartDate(), reservation.getEndDate(),
                    amountLabel, reservation.getTotalPrice(),
                    confirmed,
                    nextSteps,
                    reminder,
                    accessCode,
                    command,
                    closing);

            sendMessageToUser(user, message, reservation.getId());
        } catch (Exception e) {
            log.error("Error sending payment success notification for reservation {}: {}",
                    reservation.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send notification when payment fails
     */
    @Transactional
    public void notifyPaymentFailed(ReservationEntity reservation) {
        try {
            UserEntity user = reservation.getUser();
            Locale locale = user.getLocale();

            String title = messageSource.getMessage("matrix.notification.payment.failed.title", null, locale);
            String reservationLabel = messageSource.getMessage("matrix.notification.payment.failed.reservation", null,
                    locale);
            String spaceLabel = messageSource.getMessage("matrix.notification.payment.failed.space", null, locale);
            String messageText = messageSource.getMessage("matrix.notification.payment.failed.message", null, locale);
            String whatToDo = messageSource.getMessage("matrix.notification.payment.failed.whatToDo", null, locale);
            String checkPayment = messageSource.getMessage("matrix.notification.payment.failed.checkPayment", null,
                    locale);
            String generateLink = messageSource.getMessage("matrix.notification.payment.failed.generateLink", null,
                    locale);
            String contactSupport = messageSource.getMessage("matrix.notification.payment.failed.contactSupport", null,
                    locale);
            String validTime = messageSource.getMessage("matrix.notification.payment.failed.validTime", null, locale);

            String message = String.format(
                    "❌ **%s**\n\n" +
                            "📋 **%s:** %s\n" +
                            "🏠 **%s:** %s\n\n" +
                            "%s\n\n" +
                            "💡 **%s?**\n" +
                            "- %s\n" +
                            "- %s\n" +
                            "- %s\n\n" +
                            "%s",
                    title,
                    reservationLabel, reservation.getId(),
                    spaceLabel, reservation.getSpace().getName(),
                    messageText,
                    whatToDo,
                    checkPayment,
                    generateLink,
                    contactSupport,
                    validTime);

            sendMessageToUser(user, message, reservation.getId());
        } catch (Exception e) {
            log.error("Error sending payment failed notification for reservation {}: {}",
                    reservation.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send a message to a user via Matrix
     * Finds or creates a DM room with the user and sends the message
     */
    private void sendMessageToUser(UserEntity user, String message, UUID reservationId) {
        try {
            // Find user's Matrix user ID
            Optional<String> matrixUserIdOpt = matrixAssistantService.findUserInMatrix(user);
            if (matrixUserIdOpt.isEmpty()) {
                log.warn("Cannot send notification: user {} not found in Matrix for reservation {}",
                        user.getId(), reservationId);
                return;
            }

            String matrixUserId = matrixUserIdOpt.get();

            // Find or create DM room with the user
            Optional<String> dmRoomIdOpt = matrixAssistantService.findOrCreateDMRoom(matrixUserId);
            if (dmRoomIdOpt.isEmpty()) {
                log.warn("Cannot send notification: failed to find or create DM room with user {} for reservation {}",
                        matrixUserId, reservationId);
                return;
            }

            String dmRoomId = dmRoomIdOpt.get();
            boolean sent = matrixAssistantService.sendMessage(dmRoomId, message);
            if (sent) {
                log.info("Successfully sent notification to Matrix user {} (reservation {})",
                        matrixUserId, reservationId);
            } else {
                log.error("Failed to send notification to Matrix user {} (reservation {})",
                        matrixUserId, reservationId);
            }
        } catch (Exception e) {
            log.error("Error sending notification message to user {} for reservation {}: {}",
                    user.getId(), reservationId, e.getMessage(), e);
        }
    }
}
