package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleaningCalendarService {

    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final UsersRepository usersRepository;
    private final MessageSource messageSource;

    @org.springframework.beans.factory.annotation.Value("${neohoods.portal.base-url}")
    private String baseUrl;

    private static final DateTimeFormatter ICAL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    /**
     * Generate iCalendar content for a space's cleaning schedule
     * 
     * @param spaceId The space ID
     * @return iCalendar content as string
     */
    public String generateCalendarIcs(UUID spaceId) {
        SpaceEntity space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));

        if (!space.getCleaningEnabled() || !space.getCleaningCalendarEnabled()) {
            throw new IllegalStateException("Cleaning calendar is not enabled for this space");
        }

        // Fetch reservations with status CONFIRMED, ACTIVE, or COMPLETED
        List<ReservationEntity> reservations = reservationRepository.findBySpace(space).stream()
                .filter(r -> r.getStatus() == ReservationStatusForEntity.CONFIRMED
                        || r.getStatus() == ReservationStatusForEntity.ACTIVE
                        || r.getStatus() == ReservationStatusForEntity.COMPLETED)
                .toList();

        String cleaningLabel = messageSource.getMessage("cleaning.calendar.cleaning", null, DEFAULT_LOCALE);
        String scheduleForLabel = messageSource.getMessage("cleaning.calendar.scheduleFor", null, DEFAULT_LOCALE);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//NeoHoods Portal//Cleaning Calendar//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("X-WR-CALNAME:").append(escapeText(cleaningLabel + " - " + space.getName())).append("\r\n");
        ics.append("X-WR-CALDESC:").append(escapeText(scheduleForLabel + " " + space.getName())).append("\r\n");

        for (ReservationEntity reservation : reservations) {
            LocalDate checkoutDate = reservation.getEndDate();
            LocalDate cleaningDate = checkoutDate.plusDays(space.getCleaningDaysAfterCheckout());
            LocalTime cleaningTime = parseTime(space.getCleaningHour());

            LocalDateTime cleaningDateTime = LocalDateTime.of(cleaningDate, cleaningTime);
            ZonedDateTime startZoned = cleaningDateTime.atZone(ZoneId.systemDefault());
            ZonedDateTime endZoned = startZoned.plusHours(1); // 1 hour duration

            String guestName = reservation.getUser().getFirstName() + " " + reservation.getUser().getLastName();
            String guestEmail = reservation.getUser().getEmail();

            String cleaningForReservationLabel = messageSource.getMessage("cleaning.calendar.cleaningForReservation",
                    null, DEFAULT_LOCALE);
            String guestLabel = messageSource.getMessage("cleaning.calendar.guest", null, DEFAULT_LOCALE);
            String checkoutLabel = messageSource.getMessage("cleaning.calendar.checkout", null, DEFAULT_LOCALE);
            String reservationLabel = messageSource.getMessage("cleaning.calendar.reservation", null, DEFAULT_LOCALE);
            String toLabel = messageSource.getMessage("cleaning.calendar.to", null, DEFAULT_LOCALE);

            String eventTitle = cleaningLabel + " - " + space.getName() + " - " + guestName + " (" + guestEmail + ")";

            String reservationIdLabel = messageSource.getMessage("cleaning.calendar.reservationId", null,
                    DEFAULT_LOCALE);
            String spaceIdLabel = messageSource.getMessage("cleaning.calendar.spaceId", null, DEFAULT_LOCALE);

            String description = cleaningForReservationLabel + "\n"
                    + guestLabel + " " + guestName + " (" + guestEmail + ")\n"
                    + checkoutLabel + " " + checkoutDate.format(DateTimeFormatter.ISO_DATE) + "\n"
                    + reservationLabel + " " + reservation.getStartDate().format(DateTimeFormatter.ISO_DATE)
                    + " " + toLabel + " " + reservation.getEndDate().format(DateTimeFormatter.ISO_DATE) + "\n"
                    + "\n"
                    + reservationIdLabel + " " + reservation.getId() + "\n"
                    + spaceIdLabel + " " + spaceId;

            ics.append("BEGIN:VEVENT\r\n");
            ics.append("UID:").append(reservation.getId()).append("@neohoods-portal\r\n");
            ics.append("DTSTART:").append(startZoned.format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("DTEND:").append(endZoned.format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("DTSTAMP:").append(ZonedDateTime.now().format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("SUMMARY:").append(escapeText(eventTitle)).append("\r\n");
            ics.append("DESCRIPTION:").append(escapeText(description)).append("\r\n");
            ics.append("LOCATION:").append(escapeText(space.getName())).append("\r\n");
            ics.append("STATUS:CONFIRMED\r\n");
            ics.append("SEQUENCE:0\r\n");
            // Add URL links to reservation and space
            ics.append("URL:").append(baseUrl).append("/spaces/reservations/").append(reservation.getId())
                    .append("\r\n");
            ics.append("URL:").append(baseUrl).append("/spaces/detail/").append(spaceId).append("\r\n");
            ics.append("END:VEVENT\r\n");
        }

        ics.append("END:VCALENDAR\r\n");

        log.debug("Generated cleaning calendar for space {} with {} events", spaceId, reservations.size());
        return ics.toString();
    }

    /**
     * Generate iCalendar content for a space's reservation schedule
     * 
     * @param spaceId The space ID
     * @param userId  Optional user ID to filter reservations (null for all users)
     * @return iCalendar content as string
     */
    public String generateReservationCalendarIcs(UUID spaceId, UUID userId) {
        SpaceEntity space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));

        // Fetch reservations with status CONFIRMED, ACTIVE, or COMPLETED
        List<ReservationEntity> reservations = reservationRepository.findBySpace(space).stream()
                .filter(r -> r.getStatus() == ReservationStatusForEntity.CONFIRMED
                        || r.getStatus() == ReservationStatusForEntity.ACTIVE
                        || r.getStatus() == ReservationStatusForEntity.COMPLETED)
                .filter(r -> userId == null || r.getUser().getId().equals(userId))
                .toList();

        String reservationLabel = messageSource.getMessage("calendar.reservation.title", null, DEFAULT_LOCALE);
        String scheduleForLabel = messageSource.getMessage("calendar.reservation.scheduleFor", null, DEFAULT_LOCALE);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//NeoHoods Portal//Reservation Calendar//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("X-WR-CALNAME:").append(escapeText(reservationLabel + " - " + space.getName())).append("\r\n");
        ics.append("X-WR-CALDESC:").append(escapeText(scheduleForLabel + " " + space.getName())).append("\r\n");

        String reservationForLabel = messageSource.getMessage("calendar.reservation.reservationFor", null,
                DEFAULT_LOCALE);
        String spaceLabel = messageSource.getMessage("calendar.reservation.space", null, DEFAULT_LOCALE);
        String guestLabel = messageSource.getMessage("calendar.reservation.guest", null, DEFAULT_LOCALE);
        String fromLabel = messageSource.getMessage("calendar.reservation.from", null, DEFAULT_LOCALE);
        String toLabel = messageSource.getMessage("calendar.reservation.to", null, DEFAULT_LOCALE);
        String reservationIdLabel = messageSource.getMessage("calendar.reservation.reservationId", null,
                DEFAULT_LOCALE);
        String spaceIdLabel = messageSource.getMessage("calendar.reservation.spaceId", null, DEFAULT_LOCALE);

        for (ReservationEntity reservation : reservations) {
            LocalDate startDate = reservation.getStartDate();
            LocalDate endDate = reservation.getEndDate();

            // Use start date at 00:00 and end date at 23:59
            ZonedDateTime startZoned = startDate.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime endZoned = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault());

            String guestName = reservation.getUser().getFirstName() + " " + reservation.getUser().getLastName();
            String guestEmail = reservation.getUser().getEmail();

            String eventTitle = reservationLabel + " - " + space.getName() + " - " + guestName + " (" + guestEmail
                    + ")";

            String description = reservationForLabel + "\n"
                    + spaceLabel + " " + space.getName() + "\n"
                    + guestLabel + " " + guestName + " (" + guestEmail + ")\n"
                    + fromLabel + " " + startDate.format(DateTimeFormatter.ISO_DATE) + "\n"
                    + toLabel + " " + endDate.format(DateTimeFormatter.ISO_DATE) + "\n"
                    + "\n"
                    + reservationIdLabel + " " + reservation.getId() + "\n"
                    + spaceIdLabel + " " + spaceId;

            ics.append("BEGIN:VEVENT\r\n");
            ics.append("UID:").append(reservation.getId()).append("@neohoods-portal-reservation\r\n");
            ics.append("DTSTART:").append(startZoned.format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("DTEND:").append(endZoned.format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("DTSTAMP:").append(ZonedDateTime.now().format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("SUMMARY:").append(escapeText(eventTitle)).append("\r\n");
            ics.append("DESCRIPTION:").append(escapeText(description)).append("\r\n");
            ics.append("LOCATION:").append(escapeText(space.getName())).append("\r\n");
            ics.append("STATUS:CONFIRMED\r\n");
            ics.append("SEQUENCE:0\r\n");
            // Add URL links to reservation and space
            ics.append("URL:").append(baseUrl).append("/spaces/reservations/").append(reservation.getId())
                    .append("\r\n");
            ics.append("URL:").append(baseUrl).append("/spaces/detail/").append(spaceId).append("\r\n");
            ics.append("END:VEVENT\r\n");
        }

        ics.append("END:VCALENDAR\r\n");

        log.debug("Generated reservation calendar for space {} with {} events (userId filter: {})", spaceId,
                reservations.size(), userId);
        return ics.toString();
    }

    /**
     * Generate iCalendar content for a user's personal reservation calendar
     * 
     * @param userId The user ID
     * @return iCalendar content as string
     */
    public String generateUserCalendarIcs(UUID userId) {
        // Verify user exists
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Fetch all reservations for user across all spaces
        List<ReservationEntity> reservations = reservationRepository.findByUser(user).stream()
                .filter(r -> r.getStatus() == ReservationStatusForEntity.CONFIRMED
                        || r.getStatus() == ReservationStatusForEntity.ACTIVE
                        || r.getStatus() == ReservationStatusForEntity.COMPLETED)
                .toList();

        String reservationLabel = messageSource.getMessage("calendar.reservation.title", null, DEFAULT_LOCALE);
        String scheduleForLabel = messageSource.getMessage("calendar.user.scheduleFor", null, DEFAULT_LOCALE);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//NeoHoods Portal//User Calendar//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("X-WR-CALNAME:").append(escapeText(scheduleForLabel)).append("\r\n");
        ics.append("X-WR-CALDESC:").append(escapeText(scheduleForLabel)).append("\r\n");

        String reservationForLabel = messageSource.getMessage("calendar.reservation.reservationFor", null,
                DEFAULT_LOCALE);
        String spaceLabel = messageSource.getMessage("calendar.reservation.space", null, DEFAULT_LOCALE);
        String fromLabel = messageSource.getMessage("calendar.reservation.from", null, DEFAULT_LOCALE);
        String toLabel = messageSource.getMessage("calendar.reservation.to", null, DEFAULT_LOCALE);
        String reservationIdLabel = messageSource.getMessage("calendar.reservation.reservationId", null,
                DEFAULT_LOCALE);
        String spaceIdLabel = messageSource.getMessage("calendar.reservation.spaceId", null, DEFAULT_LOCALE);

        for (ReservationEntity reservation : reservations) {
            SpaceEntity space = reservation.getSpace();
            LocalDate startDate = reservation.getStartDate();
            LocalDate endDate = reservation.getEndDate();

            // Use start date at 00:00 and end date at 23:59
            ZonedDateTime startZoned = startDate.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime endZoned = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault());

            String eventTitle = reservationLabel + " - " + space.getName();

            String description = reservationForLabel + "\n"
                    + spaceLabel + " " + space.getName() + "\n"
                    + fromLabel + " " + startDate.format(DateTimeFormatter.ISO_DATE) + "\n"
                    + toLabel + " " + endDate.format(DateTimeFormatter.ISO_DATE) + "\n"
                    + "\n"
                    + reservationIdLabel + " " + reservation.getId() + "\n"
                    + spaceIdLabel + " " + space.getId();

            ics.append("BEGIN:VEVENT\r\n");
            ics.append("UID:").append(reservation.getId()).append("@neohoods-portal-user\r\n");
            ics.append("DTSTART:").append(startZoned.format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("DTEND:").append(endZoned.format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("DTSTAMP:").append(ZonedDateTime.now().format(ICAL_DATE_FORMAT)).append("\r\n");
            ics.append("SUMMARY:").append(escapeText(eventTitle)).append("\r\n");
            ics.append("DESCRIPTION:").append(escapeText(description)).append("\r\n");
            ics.append("LOCATION:").append(escapeText(space.getName())).append("\r\n");
            ics.append("STATUS:CONFIRMED\r\n");
            ics.append("SEQUENCE:0\r\n");
            // Add URL links to reservation and space
            ics.append("URL:").append(baseUrl).append("/spaces/reservations/").append(reservation.getId())
                    .append("\r\n");
            ics.append("URL:").append(baseUrl).append("/spaces/detail/").append(space.getId()).append("\r\n");
            ics.append("END:VEVENT\r\n");
        }

        ics.append("END:VCALENDAR\r\n");

        log.debug("Generated user calendar for user {} with {} events", userId, reservations.size());
        return ics.toString();
    }

    private LocalTime parseTime(String timeStr) {
        try {
            return LocalTime.parse(timeStr, TIME_FORMAT);
        } catch (Exception e) {
            log.warn("Invalid time format: {}, using default 10:00", timeStr);
            return LocalTime.of(10, 0);
        }
    }

    private String escapeText(String text) {
        if (text == null) {
            return "";
        }
        // iCalendar text escaping: backslash, semicolon, comma, newline
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
