package com.neohoods.portal.platform.spaces.api.publicapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.nimbusds.jose.JOSEException;
import com.neohoods.portal.platform.spaces.services.CleaningCalendarService;
import com.neohoods.portal.platform.spaces.services.CleaningCalendarTokenService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for PublicSpacesApiApiDelegateImpl.
 * 
 * Tests:
 * - Calendar endpoint with valid token
 * - Calendar endpoint with invalid token
 * - Calendar endpoint with expired token
 * - Calendar endpoint with non-existent space
 * - Calendar endpoint with mismatched space ID
 */
@ExtendWith(MockitoExtension.class)
public class PublicSpacesCalendarControllerTest {

    @Mock
    private CleaningCalendarTokenService tokenService;

    @Mock
    private CleaningCalendarService calendarService;

    @InjectMocks
    private PublicSpacesApiApiDelegateImpl delegate;

    private UUID spaceId;
    private String validToken;
    private String calendarContent;

    @BeforeEach
    public void setUp() {
        spaceId = UUID.randomUUID();
        validToken = "valid-token";
        calendarContent = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";
    }

    @Test
    @DisplayName("Get calendar with valid token")
    public void testGetSpaceCleaningCalendar_ValidToken() throws JOSEException {
        // Arrange
        CleaningCalendarTokenService.TokenVerificationResult tokenResult = 
                new CleaningCalendarTokenService.TokenVerificationResult(spaceId, null, "cleaning");
        when(tokenService.verifyTokenWithClaims(validToken)).thenReturn(tokenResult);
        when(calendarService.generateCalendarIcs(spaceId)).thenReturn(calendarContent);

        // Act
        Mono<ResponseEntity<String>> result = delegate.getSpaceCleaningCalendar(spaceId, validToken, "cleaning", null);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals(calendarContent, response.getBody());
                    assertEquals(MediaType.parseMediaType("text/calendar; charset=utf-8"),
                            response.getHeaders().getContentType());
                    assertNotNull(response.getHeaders().getContentDisposition());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Return 401 when token is invalid")
    public void testGetSpaceCleaningCalendar_InvalidToken() throws JOSEException {
        // Arrange
        String invalidToken = "invalid-token";
        when(tokenService.verifyTokenWithClaims(invalidToken))
                .thenThrow(new JOSEException("Invalid token"));

        // Act
        Mono<ResponseEntity<String>> result = delegate.getSpaceCleaningCalendar(spaceId, invalidToken, "cleaning", null);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Return 401 when token space ID does not match path")
    public void testGetSpaceCleaningCalendar_MismatchedSpaceId() throws JOSEException {
        // Arrange
        UUID tokenSpaceId = UUID.randomUUID();
        CleaningCalendarTokenService.TokenVerificationResult tokenResult = 
                new CleaningCalendarTokenService.TokenVerificationResult(tokenSpaceId, null, "cleaning");
        when(tokenService.verifyTokenWithClaims(validToken)).thenReturn(tokenResult);

        // Act
        Mono<ResponseEntity<String>> result = delegate.getSpaceCleaningCalendar(spaceId, validToken, "cleaning", null);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Return 404 when space not found")
    public void testGetSpaceCleaningCalendar_SpaceNotFound() throws JOSEException {
        // Arrange
        CleaningCalendarTokenService.TokenVerificationResult tokenResult = 
                new CleaningCalendarTokenService.TokenVerificationResult(spaceId, null, "cleaning");
        when(tokenService.verifyTokenWithClaims(validToken)).thenReturn(tokenResult);
        when(calendarService.generateCalendarIcs(spaceId))
                .thenThrow(new IllegalArgumentException("Space not found: " + spaceId));

        // Act
        Mono<ResponseEntity<String>> result = delegate.getSpaceCleaningCalendar(spaceId, validToken, "cleaning", null);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Return 404 when cleaning calendar not enabled")
    public void testGetSpaceCleaningCalendar_CalendarNotEnabled() throws JOSEException {
        // Arrange
        CleaningCalendarTokenService.TokenVerificationResult tokenResult = 
                new CleaningCalendarTokenService.TokenVerificationResult(spaceId, null, "cleaning");
        when(tokenService.verifyTokenWithClaims(validToken)).thenReturn(tokenResult);
        when(calendarService.generateCalendarIcs(spaceId))
                .thenThrow(new IllegalStateException("Cleaning calendar is not enabled for this space"));

        // Act
        Mono<ResponseEntity<String>> result = delegate.getSpaceCleaningCalendar(spaceId, validToken, "cleaning", null);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Return 500 when unexpected error occurs")
    public void testGetSpaceCleaningCalendar_UnexpectedError() throws JOSEException {
        // Arrange
        CleaningCalendarTokenService.TokenVerificationResult tokenResult = 
                new CleaningCalendarTokenService.TokenVerificationResult(spaceId, null, "cleaning");
        when(tokenService.verifyTokenWithClaims(validToken)).thenReturn(tokenResult);
        when(calendarService.generateCalendarIcs(spaceId))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        Mono<ResponseEntity<String>> result = delegate.getSpaceCleaningCalendar(spaceId, validToken, "cleaning", null);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Calendar response has correct headers")
    public void testGetSpaceCleaningCalendar_ResponseHeaders() throws JOSEException {
        // Arrange
        CleaningCalendarTokenService.TokenVerificationResult tokenResult = 
                new CleaningCalendarTokenService.TokenVerificationResult(spaceId, null, "cleaning");
        when(tokenService.verifyTokenWithClaims(validToken)).thenReturn(tokenResult);
        when(calendarService.generateCalendarIcs(spaceId)).thenReturn(calendarContent);

        // Act
        Mono<ResponseEntity<String>> result = delegate.getSpaceCleaningCalendar(spaceId, validToken, "cleaning", null);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(MediaType.parseMediaType("text/calendar; charset=utf-8"),
                            response.getHeaders().getContentType());
                    assertNotNull(response.getHeaders().getContentDisposition());
                    assertEquals("form-data", response.getHeaders().getContentDisposition().getType());
                    assertEquals("cleaning-calendar.ics",
                            response.getHeaders().getContentDisposition().getFilename());
                })
                .verifyComplete();
    }
}

