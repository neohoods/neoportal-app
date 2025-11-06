package com.neohoods.portal.platform.users.api.publicapi;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.PublicUsersApiApiDelegate;
import com.neohoods.portal.platform.spaces.services.CleaningCalendarService;
import com.neohoods.portal.platform.spaces.services.CleaningCalendarTokenService;
import com.nimbusds.jose.JOSEException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class PublicUsersApiApiDelegateImpl implements PublicUsersApiApiDelegate {

    @Autowired
    private CleaningCalendarTokenService cleaningCalendarTokenService;

    @Autowired
    private CleaningCalendarService cleaningCalendarService;

    @Override
    public Mono<ResponseEntity<String>> getUserCalendar(
            UUID userId, String token, ServerWebExchange exchange) {
        try {
            // Verify token and check userId matches
            cleaningCalendarTokenService.verifyTokenForUser(token, userId);

            // Generate user calendar
            String calendarContent = cleaningCalendarService.generateUserCalendarIcs(userId);

            // Set headers for iCalendar
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/calendar; charset=utf-8"));
            headers.setContentDispositionFormData("attachment", "user-calendar.ics");

            return Mono.just(ResponseEntity.ok()
                    .headers(headers)
                    .body(calendarContent));

        } catch (JOSEException e) {
            log.warn("Invalid or expired token for user {}, or userId mismatch", userId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        } catch (IllegalArgumentException e) {
            log.warn("User not found: {}", userId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            log.error("Error generating calendar for user {}", userId, e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }
    }
}

