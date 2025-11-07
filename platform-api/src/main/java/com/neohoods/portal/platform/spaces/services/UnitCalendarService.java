package com.neohoods.portal.platform.spaces.services;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitCalendarService {
    private final ReservationRepository reservationRepository;

    private static final DateTimeFormatter ICS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /**
     * Generate ICS calendar content for a unit's confirmed/active reservations
     */
    public String generateICSForUnit(UUID unitId) {
        log.debug("Generating ICS calendar for unit: {}", unitId);
        
        // Get confirmed and active reservations for the unit
        List<ReservationEntity> confirmedReservations = reservationRepository
                .findByUnitIdAndStatus(unitId, ReservationStatusForEntity.CONFIRMED);
        List<ReservationEntity> activeReservations = reservationRepository
                .findByUnitIdAndStatus(unitId, ReservationStatusForEntity.ACTIVE);
        
        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//NeoHoods//Unit Reservations//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        
        // Add confirmed reservations
        for (ReservationEntity reservation : confirmedReservations) {
            addReservationToICS(ics, reservation);
        }
        
        // Add active reservations
        for (ReservationEntity reservation : activeReservations) {
            addReservationToICS(ics, reservation);
        }
        
        ics.append("END:VCALENDAR\r\n");
        
        return ics.toString();
    }

    private void addReservationToICS(StringBuilder ics, ReservationEntity reservation) {
        String uid = reservation.getId().toString();
        String summary = escapeText("Réservation: " + (reservation.getSpace() != null ? reservation.getSpace().getName() : "Espace"));
        String description = escapeText(
                "Réservation du " + reservation.getStartDate() + " au " + reservation.getEndDate() +
                (reservation.getSpace() != null ? "\nEspace: " + reservation.getSpace().getName() : "") +
                (reservation.getUser() != null ? "\nRéservé par: " + reservation.getUser().getFirstName() + " " + reservation.getUser().getLastName() : "")
        );
        
        // Convert dates to UTC ZonedDateTime for ICS format
        ZonedDateTime startDateTime = reservation.getStartDate().atStartOfDay(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"));
        ZonedDateTime endDateTime = reservation.getEndDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"));
        
        String dtStart = startDateTime.format(ICS_DATE_FORMAT);
        String dtEnd = endDateTime.format(ICS_DATE_FORMAT);
        String dtStamp = ZonedDateTime.now(ZoneId.of("UTC")).format(ICS_DATE_FORMAT);
        
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("@neohoods.com\r\n");
        ics.append("DTSTAMP:").append(dtStamp).append("\r\n");
        ics.append("DTSTART:").append(dtStart).append("\r\n");
        ics.append("DTEND:").append(dtEnd).append("\r\n");
        ics.append("SUMMARY:").append(summary).append("\r\n");
        ics.append("DESCRIPTION:").append(description).append("\r\n");
        ics.append("STATUS:CONFIRMED\r\n");
        ics.append("END:VEVENT\r\n");
    }

    private String escapeText(String text) {
        if (text == null) {
            return "";
        }
        // Escape special characters for ICS format
        return text.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}

