package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleaningReminderScheduler {

    private final ReservationRepository reservationRepository;
    private final CleaningNotificationService cleaningNotificationService;

    /**
     * Send reminder emails for cleaning scheduled tomorrow
     * Runs daily at 8:00 AM
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendCleaningReminders() {
        log.info("Starting scheduled task to send cleaning reminders");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        
        // Find all CONFIRMED and ACTIVE reservations
        List<ReservationEntity> confirmedReservations = reservationRepository
                .findByStatus(ReservationStatusForEntity.CONFIRMED);
        List<ReservationEntity> activeReservations = reservationRepository
                .findByStatus(ReservationStatusForEntity.ACTIVE);
        
        List<ReservationEntity> reservations = new java.util.ArrayList<>();
        reservations.addAll(confirmedReservations);
        reservations.addAll(activeReservations);

        int count = 0;
        for (ReservationEntity reservation : reservations) {
            try {
                // Check if cleaning is enabled for this space
                if (reservation.getSpace().getCleaningEnabled() == null
                        || !reservation.getSpace().getCleaningEnabled()) {
                    continue;
                }

                // Calculate cleaning date
                LocalDate checkoutDate = reservation.getEndDate();
                LocalDate cleaningDate = checkoutDate
                        .plusDays(reservation.getSpace().getCleaningDaysAfterCheckout());

                // Send reminder if cleaning is tomorrow
                if (cleaningDate.equals(tomorrow)) {
                    cleaningNotificationService.sendCleaningReminderEmail(reservation);
                    count++;
                }
            } catch (Exception e) {
                log.error("Failed to send cleaning reminder for reservation {}", reservation.getId(), e);
            }
        }

        log.info("Completed scheduled task: sent {} cleaning reminder emails", count);
    }
}

