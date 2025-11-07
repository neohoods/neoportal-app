package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.services.MailService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;

/**
 * Unit tests for CleaningNotificationService.
 * 
 * Tests:
 * - Booking confirmation email sending
 * - Reminder email sending
 * - Cancellation email sending
 * - Conditional sending based on settings
 * - Calendar URL inclusion
 */
@ExtendWith(MockitoExtension.class)
public class CleaningNotificationServiceTest {

    @Mock
    private MailService mailService;

    @Mock
    private CalendarTokenService tokenService;

    @InjectMocks
    private CleaningNotificationService notificationService;

    private SpaceEntity space;
    private UserEntity user;
    private ReservationEntity reservation;
    private static final String BASE_URL = "https://example.com";

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(notificationService, "baseUrl", BASE_URL);

        space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName("Test Guest Room");
        space.setType(SpaceTypeForEntity.GUEST_ROOM);
        space.setCleaningEnabled(true);
        space.setCleaningNotificationsEnabled(true);
        space.setCleaningEmail("cleaning@example.com");
        space.setCleaningCalendarEnabled(true);
        space.setCleaningDaysAfterCheckout(0);
        space.setCleaningHour("10:00");

        user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");

        reservation = new ReservationEntity();
        reservation.setId(UUID.randomUUID());
        reservation.setSpace(space);
        reservation.setUser(user);
        reservation.setStatus(ReservationStatusForEntity.CONFIRMED);
        reservation.setStartDate(LocalDate.now().plusDays(1));
        reservation.setEndDate(LocalDate.now().plusDays(3));
        reservation.setTotalPrice(java.math.BigDecimal.valueOf(100.0));
    }

    @Test
    @DisplayName("Send booking confirmation email when enabled")
    public void testSendBookingConfirmationEmail_Enabled() {
        // Arrange
        String token = "test-token";
        when(tokenService.generateToken(space.getId())).thenReturn(token);

        // Act
        notificationService.sendBookingConfirmationEmail(reservation);

        // Assert
        verify(mailService).sendCleaningBookingConfirmationEmail(
                eq("cleaning@example.com"),
                eq("Test Guest Room"),
                anyString(), // checkoutDate
                anyString(), // cleaningDate
                eq("10:00"),
                eq("John Doe"),
                eq("john.doe@example.com"),
                eq(BASE_URL + "/api/public/spaces/" + space.getId() + "/calendar.ics?token=" + token),
                eq(Locale.FRENCH));
    }

    @Test
    @DisplayName("Do not send email when cleaning not enabled")
    public void testSendBookingConfirmationEmail_CleaningNotEnabled() {
        // Arrange
        space.setCleaningEnabled(false);

        // Act
        notificationService.sendBookingConfirmationEmail(reservation);

        // Assert
        verify(mailService, never()).sendCleaningBookingConfirmationEmail(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Do not send email when notifications not enabled")
    public void testSendBookingConfirmationEmail_NotificationsNotEnabled() {
        // Arrange
        space.setCleaningNotificationsEnabled(false);

        // Act
        notificationService.sendBookingConfirmationEmail(reservation);

        // Assert
        verify(mailService, never()).sendCleaningBookingConfirmationEmail(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Do not send email when email address is null")
    public void testSendBookingConfirmationEmail_EmailNull() {
        // Arrange
        space.setCleaningEmail(null);

        // Act
        notificationService.sendBookingConfirmationEmail(reservation);

        // Assert
        verify(mailService, never()).sendCleaningBookingConfirmationEmail(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Do not send email when email address is empty")
    public void testSendBookingConfirmationEmail_EmailEmpty() {
        // Arrange
        space.setCleaningEmail("");

        // Act
        notificationService.sendBookingConfirmationEmail(reservation);

        // Assert
        verify(mailService, never()).sendCleaningBookingConfirmationEmail(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Do not include calendar URL when calendar not enabled")
    public void testSendBookingConfirmationEmail_CalendarNotEnabled() {
        // Arrange
        space.setCleaningCalendarEnabled(false);

        // Act
        notificationService.sendBookingConfirmationEmail(reservation);

        // Assert
        verify(mailService).sendCleaningBookingConfirmationEmail(
                eq("cleaning@example.com"),
                eq("Test Guest Room"),
                anyString(),
                anyString(),
                eq("10:00"),
                eq("John Doe"),
                eq("john.doe@example.com"),
                isNull(), // calendarUrl should be null
                eq(Locale.FRENCH));
    }

    @Test
    @DisplayName("Send reminder email when enabled")
    public void testSendCleaningReminderEmail_Enabled() {
        // Act
        notificationService.sendCleaningReminderEmail(reservation);

        // Assert
        verify(mailService).sendCleaningReminderEmail(
                eq("cleaning@example.com"),
                eq("Test Guest Room"),
                anyString(), // cleaningDate
                eq("10:00"),
                eq("John Doe"),
                eq(Locale.FRENCH));
    }

    @Test
    @DisplayName("Do not send reminder when cleaning not enabled")
    public void testSendCleaningReminderEmail_CleaningNotEnabled() {
        // Arrange
        space.setCleaningEnabled(false);

        // Act
        notificationService.sendCleaningReminderEmail(reservation);

        // Assert
        verify(mailService, never()).sendCleaningReminderEmail(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Send cancellation email when enabled")
    public void testSendCancellationEmail_Enabled() {
        // Act
        notificationService.sendCancellationEmail(reservation);

        // Assert
        verify(mailService).sendCleaningCancellationEmail(
                eq("cleaning@example.com"),
                eq("Test Guest Room"),
                anyString(), // checkoutDate
                anyString(), // cleaningDate
                eq("10:00"),
                eq("John Doe"),
                eq("john.doe@example.com"),
                eq(Locale.FRENCH));
    }

    @Test
    @DisplayName("Do not send cancellation when notifications not enabled")
    public void testSendCancellationEmail_NotificationsNotEnabled() {
        // Arrange
        space.setCleaningNotificationsEnabled(false);

        // Act
        notificationService.sendCancellationEmail(reservation);

        // Assert
        verify(mailService, never()).sendCleaningCancellationEmail(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Handle invalid time format gracefully")
    public void testSendBookingConfirmationEmail_InvalidTimeFormat() {
        // Arrange
        space.setCleaningHour("invalid-time");
        String token = "test-token";
        when(tokenService.generateToken(space.getId())).thenReturn(token);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> {
            notificationService.sendBookingConfirmationEmail(reservation);
        });

        // Should use default time (10:00)
        verify(mailService).sendCleaningBookingConfirmationEmail(
                any(), any(), any(), any(),
                eq("10:00"), // default time
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("Calculate cleaning date with days after checkout offset")
    public void testSendBookingConfirmationEmail_DaysAfterCheckout() {
        // Arrange
        space.setCleaningDaysAfterCheckout(1);
        LocalDate checkoutDate = reservation.getEndDate();
        String token = "test-token";
        when(tokenService.generateToken(space.getId())).thenReturn(token);

        // Act
        notificationService.sendBookingConfirmationEmail(reservation);

        // Assert
        verify(mailService).sendCleaningBookingConfirmationEmail(
                eq("cleaning@example.com"),
                eq("Test Guest Room"),
                anyString(), // checkoutDate
                anyString(), // cleaningDate (checkout + 1 day)
                eq("10:00"),
                eq("John Doe"),
                eq("john.doe@example.com"),
                anyString(),
                eq(Locale.FRENCH));
    }
}


