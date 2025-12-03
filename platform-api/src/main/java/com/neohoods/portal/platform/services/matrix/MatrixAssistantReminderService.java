package com.neohoods.portal.platform.services.matrix;

import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

    @Value("${neohoods.portal.matrix.assistant.reminders.before-reservation-hours:24}")
    private int beforeReservationHours;

    @Value("${neohoods.portal.matrix.assistant.reminders.checkout-reminder-hours:9}")
    private int checkoutReminderHours;

    @Value("${neohoods.portal.matrix.assistant.reminders.feedback-days-after:1}")
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
                // Note: In production, track reminderSent in a separate table or add field to ReservationEntity
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
                // Note: In production, track checkoutReminderSent in a separate table or add field to ReservationEntity
            } catch (Exception e) {
                log.error("Error sending checkout reminder for reservation {}: {}", reservation.getId(), e.getMessage(), e);
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
                // Note: In production, track feedbackRequestSent in a separate table or add field to ReservationEntity
            } catch (Exception e) {
                log.error("Error sending feedback request for reservation {}: {}", reservation.getId(), e.getMessage(), e);
            }
        }

        if (!completedReservations.isEmpty()) {
            log.info("Sent {} feedback requests", completedReservations.size());
        }
    }

    private void sendUpcomingReservationReminder(ReservationEntity reservation) {
        String message = String.format(
                "🔔 **Rappel: Votre réservation approche!**\n\n" +
                "📋 **Réservation:** %s\n" +
                "🏠 **Espace:** %s\n" +
                "📅 **Date:** Du %s au %s\n\n" +
                "💡 **N'oubliez pas:**\n" +
                "- Votre code d'accès sera disponible le jour de l'arrivée\n" +
                "- Utilisez `get_reservation_access_code` avec l'ID de votre réservation pour obtenir le code\n\n" +
                "Bon séjour! 🎉",
                reservation.getId(),
                reservation.getSpace().getName(),
                reservation.getStartDate(),
                reservation.getEndDate()
        );

        // Find user's Matrix DM room or send to a notification room
        // For now, we'll need to implement a way to find the user's Matrix room
        // This is a placeholder - you'll need to implement MatrixAssistantService.sendMessageToUser()
        log.info("Would send upcoming reminder to user {} for reservation {}", 
                reservation.getUser().getId(), reservation.getId());
        // TODO: Implement actual Matrix message sending
    }

    private void sendCheckoutReminder(ReservationEntity reservation) {
        String message = String.format(
                "🔔 **Rappel de départ**\n\n" +
                "📋 **Réservation:** %s\n" +
                "🏠 **Espace:** %s\n" +
                "📅 **Date de départ:** %s\n\n" +
                "⏰ **N'oubliez pas:**\n" +
                "- Départ avant 11h00\n" +
                "- Remettez les clés dans la boîte prévue\n" +
                "- Laissez l'espace propre\n\n" +
                "Merci et à bientôt! 👋",
                reservation.getId(),
                reservation.getSpace().getName(),
                reservation.getEndDate()
        );

        log.info("Would send checkout reminder to user {} for reservation {}", 
                reservation.getUser().getId(), reservation.getId());
        // TODO: Implement actual Matrix message sending
    }

    private void sendFeedbackRequest(ReservationEntity reservation) {
        String message = String.format(
                "📝 **Votre avis nous intéresse!**\n\n" +
                "📋 **Réservation:** %s\n" +
                "🏠 **Espace:** %s\n" +
                "📅 **Date:** Du %s au %s\n\n" +
                "Votre retour est précieux pour nous améliorer! " +
                "N'hésitez pas à partager vos impressions sur votre séjour.\n\n" +
                "Merci! 🙏",
                reservation.getId(),
                reservation.getSpace().getName(),
                reservation.getStartDate(),
                reservation.getEndDate()
        );

        log.info("Would send feedback request to user {} for reservation {}", 
                reservation.getUser().getId(), reservation.getId());
        // TODO: Implement actual Matrix message sending
    }
}

