package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
public class ReservationCleanupJob {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationsService reservationsService;

    /**
     * Scheduled task to expire stale reservations
     * Runs every 60 seconds to check for expired reservations
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void expireStaleReservations() {
        log.debug("Checking for expired reservations");

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nowMinus24Hours = now.minusHours(24);
        List<ReservationEntity> expiredReservations = reservationRepository
                .findExpiredPendingPaymentReservations(now, nowMinus24Hours,
                        ReservationStatusForEntity.PENDING_PAYMENT);

        if (expiredReservations.isEmpty()) {
            log.debug("No expired reservations found");
            return;
        }

        log.info("Found {} expired reservations to process", expiredReservations.size());

        for (ReservationEntity reservation : expiredReservations) {
            try {
                log.info("Expiring reservation {} (expired at: {})",
                        reservation.getId(), reservation.getPaymentExpiresAt());

                // Expire the reservation
                reservationsService.expireReservation(reservation.getId(), "Payment timeout reached");

                log.info("Successfully expired reservation: {}", reservation.getId());

            } catch (Exception e) {
                log.error("Error expiring reservation: {}", reservation.getId(), e);
            }
        }
    }
}
