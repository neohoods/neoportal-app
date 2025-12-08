package com.neohoods.portal.platform.assistant.services;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for sending automatic reminders for reservations
 * - Reminder before reservation starts (e.g., 24h before)
 * - Checkout reminder (on checkout day)
 * - Feedback request (after checkout)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.reminders.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantReminderService {

    private final ReservationRepository reservationRepository;
    private final MatrixAssistantService matrixAssistantService;
    private final MessageSource messageSource;

    @Value("${neohoods.portal.matrix.assistant.reminders.before-reservation-hours}")
    private int beforeReservationHours;

    @Value("${neohoods.portal.matrix.assistant.reminders.checkout-reminder-hours}")
    private int checkoutReminderHours;

    @Value("${neohoods.portal.matrix.assistant.reminders.feedback-days-after}")
    private int feedbackDaysAfter;

    /**
     * Send reminders for upcoming reservations (runs every hour)
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void sendUpcomingReservationReminders() {
        log.debug("Checking for upcoming reservations to remind...");

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime reminderTime = now.plusHours(beforeReservationHours);

        // Find reservations starting within the reminder window
        List<ReservationEntity> upcomingReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatusForEntity.CONFIRMED ||
                        r.getStatus() == ReservationStatusForEntity.ACTIVE)
                .filter(r -> {
                    LocalDateTime startDateTime = r.getStartDate().atStartOfDay();
                    // Check if reservation starts within the reminder window
                    // Note: In production, you'd track reminderSent in a separate table or field
                    return !startDateTime.isBefore(now) &&
                            !startDateTime.isAfter(reminderTime);
                })
                .collect(Collectors.toList());

        for (ReservationEntity reservation : upcomingReservations) {
            try {
                sendUpcomingReservationReminder(reservation);
                // Note: In production, track reminderSent in a separate table or add field to
                // ReservationEntity
            } catch (Exception e) {
                log.error("Error sending reminder for reservation {}: {}", reservation.getId(), e.getMessage(), e);
            }
        }

        if (!upcomingReservations.isEmpty()) {
            log.info("Sent {} upcoming reservation reminders", upcomingReservations.size());
        }
    }

    /**
     * Send checkout reminders (runs every hour)
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void sendCheckoutReminders() {
        log.debug("Checking for checkout reminders...");

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime reminderTime = now.plusHours(checkoutReminderHours);

        // Find reservations ending today
        List<ReservationEntity> checkoutReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatusForEntity.ACTIVE)
                .filter(r -> r.getEndDate().equals(today))
                .collect(Collectors.toList());

        for (ReservationEntity reservation : checkoutReservations) {
            try {
                sendCheckoutReminder(reservation);
                // Note: In production, track checkoutReminderSent in a separate table or add
                // field to ReservationEntity
            } catch (Exception e) {
                log.error("Error sending checkout reminder for reservation {}: {}", reservation.getId(), e.getMessage(),
                        e);
            }
        }

        if (!checkoutReservations.isEmpty()) {
            log.info("Sent {} checkout reminders", checkoutReservations.size());
        }
    }

    /**
     * Send feedback requests (runs daily)
     */
    @Scheduled(cron = "0 0 10 * * *") // Every day at 10:00
    public void sendFeedbackRequests() {
        log.debug("Checking for feedback requests...");

        LocalDate cutoffDate = LocalDate.now().minusDays(feedbackDaysAfter);

        // Find completed reservations
        List<ReservationEntity> completedReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatusForEntity.COMPLETED)
                .filter(r -> r.getEndDate().equals(cutoffDate))
                .collect(Collectors.toList());

        for (ReservationEntity reservation : completedReservations) {
            try {
                sendFeedbackRequest(reservation);
                // Note: In production, track feedbackRequestSent in a separate table or add
                // field to ReservationEntity
            } catch (Exception e) {
                log.error("Error sending feedback request for reservation {}: {}", reservation.getId(), e.getMessage(),
                        e);
            }
        }

        if (!completedReservations.isEmpty()) {
            log.info("Sent {} feedback requests", completedReservations.size());
        }
    }

    private void sendUpcomingReservationReminder(ReservationEntity reservation) {
        UserEntity user = reservation.getUser();
        Locale locale = user.getLocale();

        String title = messageSource.getMessage("matrix.reminder.upcoming.title", null, locale);
        String reservationLabel = messageSource.getMessage("matrix.reminder.upcoming.reservation", null, locale);
        String spaceLabel = messageSource.getMessage("matrix.reminder.upcoming.space", null, locale);
        String dateLabel = messageSource.getMessage("matrix.reminder.upcoming.date", null, locale);
        String reminderNote = messageSource.getMessage("matrix.reminder.upcoming.note", null, locale);
        String accessCodeNote = messageSource.getMessage("matrix.reminder.upcoming.accessCodeNote", null, locale);
        String commandNote = messageSource.getMessage("matrix.reminder.upcoming.commandNote",
                new Object[] { reservation.getId() }, locale);
        String closing = messageSource.getMessage("matrix.reminder.upcoming.closing", null, locale);

        String message = String.format(
                "🔔 **%s**\n\n" +
                        "📋 **%s:** %s\n" +
                        "🏠 **%s:** %s\n" +
                        "📅 **%s:** %s - %s\n\n" +
                        "💡 **%s:**\n" +
                        "- %s\n" +
                        "- %s\n\n" +
                        "%s 🎉",
                title,
                reservationLabel, reservation.getId(),
                spaceLabel, reservation.getSpace().getName(),
                dateLabel, reservation.getStartDate(), reservation.getEndDate(),
                reminderNote,
                accessCodeNote,
                commandNote,
                closing);

        sendMessageToUser(user, message, reservation.getId());
    }

    private void sendCheckoutReminder(ReservationEntity reservation) {
        UserEntity user = reservation.getUser();
        Locale locale = user.getLocale();

        String title = messageSource.getMessage("matrix.reminder.checkout.title", null, locale);
        String reservationLabel = messageSource.getMessage("matrix.reminder.checkout.reservation", null, locale);
        String spaceLabel = messageSource.getMessage("matrix.reminder.checkout.space", null, locale);
        String checkoutDateLabel = messageSource.getMessage("matrix.reminder.checkout.checkoutDate", null, locale);
        String reminderNote = messageSource.getMessage("matrix.reminder.checkout.note", null, locale);
        String timeNote = messageSource.getMessage("matrix.reminder.checkout.timeNote", null, locale);
        String keysNote = messageSource.getMessage("matrix.reminder.checkout.keysNote", null, locale);
        String cleanNote = messageSource.getMessage("matrix.reminder.checkout.cleanNote", null, locale);
        String closing = messageSource.getMessage("matrix.reminder.checkout.closing", null, locale);

        String message = String.format(
                "🔔 **%s**\n\n" +
                        "📋 **%s:** %s\n" +
                        "🏠 **%s:** %s\n" +
                        "📅 **%s:** %s\n\n" +
                        "⏰ **%s:**\n" +
                        "- %s\n" +
                        "- %s\n" +
                        "- %s\n\n" +
                        "%s 👋",
                title,
                reservationLabel, reservation.getId(),
                spaceLabel, reservation.getSpace().getName(),
                checkoutDateLabel, reservation.getEndDate(),
                reminderNote,
                timeNote,
                keysNote,
                cleanNote,
                closing);

        sendMessageToUser(user, message, reservation.getId());
    }

    private void sendFeedbackRequest(ReservationEntity reservation) {
        UserEntity user = reservation.getUser();
        Locale locale = user.getLocale();

        String title = messageSource.getMessage("matrix.reminder.feedback.title", null, locale);
        String reservationLabel = messageSource.getMessage("matrix.reminder.feedback.reservation", null, locale);
        String spaceLabel = messageSource.getMessage("matrix.reminder.feedback.space", null, locale);
        String dateLabel = messageSource.getMessage("matrix.reminder.feedback.date", null, locale);
        String messageText = messageSource.getMessage("matrix.reminder.feedback.message", null, locale);
        String closing = messageSource.getMessage("matrix.reminder.feedback.closing", null, locale);

        String message = String.format(
                "📝 **%s**\n\n" +
                        "📋 **%s:** %s\n" +
                        "🏠 **%s:** %s\n" +
                        "📅 **%s:** %s - %s\n\n" +
                        "%s\n\n" +
                        "%s 🙏",
                title,
                reservationLabel, reservation.getId(),
                spaceLabel, reservation.getSpace().getName(),
                dateLabel, reservation.getStartDate(), reservation.getEndDate(),
                messageText,
                closing);

        sendMessageToUser(user, message, reservation.getId());
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
                log.warn("Cannot send reminder: user {} not found in Matrix for reservation {}",
                        user.getId(), reservationId);
                return;
            }

            String matrixUserId = matrixUserIdOpt.get();

            // Find or create DM room with the user
            Optional<String> dmRoomIdOpt = matrixAssistantService.findOrCreateDMRoom(matrixUserId);
            if (dmRoomIdOpt.isEmpty()) {
                log.warn("Cannot send reminder: failed to find or create DM room with user {} for reservation {}",
                        matrixUserId, reservationId);
                return;
            }

            String dmRoomId = dmRoomIdOpt.get();
            boolean sent = matrixAssistantService.sendMessage(dmRoomId, message);
            if (sent) {
                log.info("Successfully sent reminder message to Matrix user {} (reservation {})",
                        matrixUserId, reservationId);
            } else {
                log.error("Failed to send reminder message to Matrix user {} (reservation {})",
                        matrixUserId, reservationId);
            }
        } catch (Exception e) {
            log.error("Error sending reminder message to user {} for reservation {}: {}",
                    user.getId(), reservationId, e.getMessage(), e);
        }
    }
}
