package com.neohoods.portal.platform.spaces.api.publicapi;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.PublicSpacesApiApiDelegate;
import com.neohoods.portal.platform.spaces.services.CleaningCalendarService;
import com.neohoods.portal.platform.spaces.services.CleaningCalendarTokenService;
import com.nimbusds.jose.JOSEException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class PublicSpacesApiApiDelegateImpl implements PublicSpacesApiApiDelegate {

    @Autowired
    private CleaningCalendarTokenService cleaningCalendarTokenService;

    @Autowired
    private CleaningCalendarService cleaningCalendarService;

    @Override
    public Mono<ResponseEntity<String>> getSpaceCleaningCalendar(
            UUID spaceId, String token, String type, ServerWebExchange exchange) {
        try {
            // Verify token and extract claims
            CleaningCalendarTokenService.TokenVerificationResult tokenResult = cleaningCalendarTokenService
                    .verifyTokenWithClaims(token);

            // Verify that the token's spaceId matches the path parameter
            if (tokenResult.getSpaceId() == null || !tokenResult.getSpaceId().equals(spaceId)) {
                log.warn("Token spaceId {} does not match path spaceId {}", tokenResult.getSpaceId(), spaceId);
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
            }

            // Determine calendar type (default to "cleaning" if not provided)
            String calendarType = (type != null && !type.isEmpty()) ? type : "cleaning";

            // Verify token type matches requested type (if type is provided in query)
            if (type != null && !type.isEmpty() && !calendarType.equals(tokenResult.getType())) {
                log.warn("Token type {} does not match requested type {}", tokenResult.getType(), calendarType);
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
            }

            // Generate calendar based on type
            String calendarContent;
            String filename;
            if ("reservation".equals(calendarType)) {
                // For reservation type, use userId from token if present
                UUID userId = tokenResult.getUserId();
                calendarContent = cleaningCalendarService.generateReservationCalendarIcs(spaceId, userId);
                filename = "reservation-calendar.ics";
            } else {
                // Default to cleaning calendar
                calendarContent = cleaningCalendarService.generateCalendarIcs(spaceId);
                filename = "cleaning-calendar.ics";
            }

            // Set headers for iCalendar
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/calendar; charset=utf-8"));
            headers.setContentDispositionFormData("attachment", filename);

            return Mono.just(ResponseEntity.ok()
                    .headers(headers)
                    .body(calendarContent));

        } catch (JOSEException e) {
            log.warn("Invalid or expired token for space {}", spaceId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        } catch (IllegalArgumentException e) {
            log.warn("Space not found: {}", spaceId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (IllegalStateException e) {
            log.warn("Cleaning calendar not enabled for space: {}", spaceId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            log.error("Error generating calendar for space {}", spaceId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }
    }
}
