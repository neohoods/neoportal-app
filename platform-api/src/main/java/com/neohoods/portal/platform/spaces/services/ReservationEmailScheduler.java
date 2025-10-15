package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.services.MailService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

@Component
public class ReservationEmailScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReservationEmailScheduler.class);

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private AccessCodeService accessCodeService;

    /**
     * Send access codes for reservations starting today
     * Runs daily at 6:00 AM
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void sendDayOfAccessCodes() {
        logger.info("Starting scheduled task to send day-of access codes");

        LocalDate today = LocalDate.now();
        List<ReservationEntity> reservations = reservationRepository.findByStartDateAndStatus(
                today, ReservationStatusForEntity.CONFIRMED);

        int count = 0;
        for (ReservationEntity reservation : reservations) {
            try {
                // Generate access code if not already generated
                if (reservation.getAccessCode() == null) {
                    accessCodeService.generateAccessCode(reservation);
                    reservation = reservationRepository.findById(reservation.getId()).orElse(reservation);
                }

                // Send email with access code
                sendAccessCodeEmail(reservation);
                count++;
            } catch (Exception e) {
                logger.error("Failed to send day-of access code for reservation {}", reservation.getId(), e);
            }
        }

        logger.info("Completed scheduled task: sent {} day-of access code emails", count);
    }

    /**
     * Send reminder emails for reservations starting tomorrow
     * Runs daily at 6:00 PM
     */
    @Scheduled(cron = "0 0 18 * * *")
    public void sendDayBeforeReminders() {
        logger.info("Starting scheduled task to send day-before reminders");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<ReservationEntity> reservations = reservationRepository.findByStartDateAndStatus(
                tomorrow, ReservationStatusForEntity.CONFIRMED);

        int count = 0;
        for (ReservationEntity reservation : reservations) {
            try {
                sendReminderEmail(reservation);
                count++;
            } catch (Exception e) {
                logger.error("Failed to send reminder email for reservation {}", reservation.getId(), e);
            }
        }

        logger.info("Completed scheduled task: sent {} reminder emails", count);
    }

    private void sendAccessCodeEmail(ReservationEntity reservation) {
        UserEntity user = reservation.getUser();
        String spaceName = reservation.getSpace().getName();
        String accessCode = reservation.getAccessCode() != null ? reservation.getAccessCode().getCode() : null;

        // Format dates in French format
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                .ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH);
        String startDate = reservation.getStartDate().format(formatter);
        String endDate = reservation.getEndDate().format(formatter);

        mailService.sendReservationConfirmationEmail(user, spaceName, startDate, endDate, accessCode,
                reservation.getId(), reservation.getSpace().getId(), Locale.FRENCH);
    }

    private void sendReminderEmail(ReservationEntity reservation) {
        UserEntity user = reservation.getUser();
        String spaceName = reservation.getSpace().getName();
        String startDate = reservation.getStartDate().toString();
        String endDate = reservation.getEndDate().toString();

        String accessCode = null;
        if (reservation.getAccessCode() != null) {
            accessCode = reservation.getAccessCode().getCode();
        }

        // Get space instructions
        String spaceInstructions = reservation.getSpace().getInstructions();

        mailService.sendReservationReminderEmail(user, spaceName, startDate, endDate,
                accessCode, null, spaceInstructions, Locale.FRENCH);
    }
}
