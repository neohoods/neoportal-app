package com.neohoods.portal.platform.services;

import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
            String message = String.format(
                    "✅ **Réservation complétée!**\n\n" +
                    "📋 **Réservation:** %s\n" +
                    "🏠 **Espace:** %s\n" +
                    "📅 **Date:** Du %s au %s\n\n" +
                    "Merci d'avoir utilisé nos services! " +
                    "Nous espérons que vous avez passé un agréable séjour.\n\n" +
                    "💡 **Prochaines étapes:**\n" +
                    "- Vous pouvez demander votre code d'accès avec `get_reservation_access_code`\n" +
                    "- N'hésitez pas à partager vos impressions!\n\n" +
                    "À bientôt! 🎉",
                    reservation.getId(),
                    reservation.getSpace().getName(),
                    reservation.getStartDate(),
                    reservation.getEndDate()
            );

            // Find user's Matrix DM room or send to a notification room
            log.info("Would send completion notification to user {} for reservation {}", 
                    reservation.getUser().getId(), reservation.getId());
            // TODO: Implement actual Matrix message sending
            // matrixAssistantService.sendMessageToUser(reservation.getUser(), message);
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
            String message = String.format(
                    "💳 **Paiement confirmé!**\n\n" +
                    "📋 **Réservation:** %s\n" +
                    "🏠 **Espace:** %s\n" +
                    "📅 **Date:** Du %s au %s\n" +
                    "💰 **Montant:** %s€\n\n" +
                    "Votre réservation est maintenant confirmée!\n\n" +
                    "💡 **Prochaines étapes:**\n" +
                    "- Vous recevrez un rappel avant votre arrivée\n" +
                    "- Le code d'accès sera disponible le jour de l'arrivée\n" +
                    "- Utilisez `get_reservation_access_code` avec l'ID de votre réservation\n\n" +
                    "Bon séjour! 🎉",
                    reservation.getId(),
                    reservation.getSpace().getName(),
                    reservation.getStartDate(),
                    reservation.getEndDate(),
                    reservation.getTotalPrice()
            );

            log.info("Would send payment success notification to user {} for reservation {}", 
                    reservation.getUser().getId(), reservation.getId());
            // TODO: Implement actual Matrix message sending
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
            String message = String.format(
                    "❌ **Paiement échoué**\n\n" +
                    "📋 **Réservation:** %s\n" +
                    "🏠 **Espace:** %s\n\n" +
                    "Le paiement pour votre réservation n'a pas pu être traité.\n\n" +
                    "💡 **Que faire?**\n" +
                    "- Vérifiez vos informations de paiement\n" +
                    "- Utilisez `generate_payment_link` avec l'ID de votre réservation pour générer un nouveau lien\n" +
                    "- Contactez le support si le problème persiste\n\n" +
                    "Votre réservation reste valide pendant 15 minutes.",
                    reservation.getId(),
                    reservation.getSpace().getName()
            );

            log.info("Would send payment failed notification to user {} for reservation {}", 
                    reservation.getUser().getId(), reservation.getId());
            // TODO: Implement actual Matrix message sending
        } catch (Exception e) {
            log.error("Error sending payment failed notification for reservation {}: {}", 
                    reservation.getId(), e.getMessage(), e);
        }
    }
}

