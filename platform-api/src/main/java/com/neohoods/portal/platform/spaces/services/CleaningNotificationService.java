package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.services.MailService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleaningNotificationService {

    private final MailService mailService;
    private final CleaningCalendarTokenService tokenService;

    @Value("${neohoods.portal.base-url}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Send booking confirmation email to cleaning company
     */
    public void sendBookingConfirmationEmail(ReservationEntity reservation) {
        SpaceEntity space = reservation.getSpace();

        if (!space.getCleaningEnabled() || !space.getCleaningNotificationsEnabled()
                || space.getCleaningEmail() == null || space.getCleaningEmail().isEmpty()) {
            log.debug("Cleaning notifications not enabled for space {}", space.getId());
            return;
        }

        try {
            LocalDate checkoutDate = reservation.getEndDate();
            LocalDate cleaningDate = checkoutDate.plusDays(space.getCleaningDaysAfterCheckout());
            LocalTime cleaningTime = parseTime(space.getCleaningHour());

            String guestName = reservation.getUser().getFirstName() + " " + reservation.getUser().getLastName();
            String guestEmail = reservation.getUser().getEmail();

            String calendarUrl = null;
            if (space.getCleaningCalendarEnabled()) {
                String token = tokenService.generateToken(space.getId());
                calendarUrl = baseUrl + "/api/public/spaces/" + space.getId() + "/calendar.ics?token=" + token;
            }

            mailService.sendCleaningBookingConfirmationEmail(
                    space.getCleaningEmail(),
                    space.getName(),
                    checkoutDate.format(DATE_FORMAT),
                    cleaningDate.format(DATE_FORMAT),
                    cleaningTime.format(TIME_FORMAT),
                    guestName,
                    guestEmail,
                    calendarUrl,
                    Locale.FRENCH);

            log.info("Sent cleaning booking confirmation email for reservation {} to {}", 
                    reservation.getId(), space.getCleaningEmail());
        } catch (Exception e) {
            log.error("Failed to send cleaning booking confirmation email for reservation {}", 
                    reservation.getId(), e);
        }
    }

    /**
     * Send reminder email 1 day before cleaning
     */
    public void sendCleaningReminderEmail(ReservationEntity reservation) {
        SpaceEntity space = reservation.getSpace();

        if (!space.getCleaningEnabled() || !space.getCleaningNotificationsEnabled()
                || space.getCleaningEmail() == null || space.getCleaningEmail().isEmpty()) {
            log.debug("Cleaning notifications not enabled for space {}", space.getId());
            return;
        }

        try {
            LocalDate checkoutDate = reservation.getEndDate();
            LocalDate cleaningDate = checkoutDate.plusDays(space.getCleaningDaysAfterCheckout());
            LocalTime cleaningTime = parseTime(space.getCleaningHour());

            String guestName = reservation.getUser().getFirstName() + " " + reservation.getUser().getLastName();

            mailService.sendCleaningReminderEmail(
                    space.getCleaningEmail(),
                    space.getName(),
                    cleaningDate.format(DATE_FORMAT),
                    cleaningTime.format(TIME_FORMAT),
                    guestName,
                    Locale.FRENCH);

            log.info("Sent cleaning reminder email for reservation {} to {}", 
                    reservation.getId(), space.getCleaningEmail());
        } catch (Exception e) {
            log.error("Failed to send cleaning reminder email for reservation {}", 
                    reservation.getId(), e);
        }
    }

    /**
     * Send cancellation email to cleaning company
     */
    public void sendCancellationEmail(ReservationEntity reservation) {
        SpaceEntity space = reservation.getSpace();

        if (!space.getCleaningEnabled() || !space.getCleaningNotificationsEnabled()
                || space.getCleaningEmail() == null || space.getCleaningEmail().isEmpty()) {
            log.debug("Cleaning notifications not enabled for space {}", space.getId());
            return;
        }

        try {
            LocalDate checkoutDate = reservation.getEndDate();
            LocalDate cleaningDate = checkoutDate.plusDays(space.getCleaningDaysAfterCheckout());
            LocalTime cleaningTime = parseTime(space.getCleaningHour());

            String guestName = reservation.getUser().getFirstName() + " " + reservation.getUser().getLastName();
            String guestEmail = reservation.getUser().getEmail();

            mailService.sendCleaningCancellationEmail(
                    space.getCleaningEmail(),
                    space.getName(),
                    checkoutDate.format(DATE_FORMAT),
                    cleaningDate.format(DATE_FORMAT),
                    cleaningTime.format(TIME_FORMAT),
                    guestName,
                    guestEmail,
                    Locale.FRENCH);

            log.info("Sent cleaning cancellation email for reservation {} to {}", 
                    reservation.getId(), space.getCleaningEmail());
        } catch (Exception e) {
            log.error("Failed to send cleaning cancellation email for reservation {}", 
                    reservation.getId(), e);
        }
    }

    private LocalTime parseTime(String timeStr) {
        try {
            return LocalTime.parse(timeStr, TIME_FORMAT);
        } catch (Exception e) {
            log.warn("Invalid time format: {}, using default 10:00", timeStr);
            return LocalTime.of(10, 0);
        }
    }
}

