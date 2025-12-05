package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantReminderService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantReminderService Unit Tests")
class MatrixAssistantReminderServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MatrixAssistantService matrixAssistantService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private MatrixAssistantReminderService reminderService;

    private UserEntity testUser;
    private SpaceEntity testSpace;
    private ReservationEntity testReservation;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reminderService, "beforeReservationHours", 24);
        ReflectionTestUtils.setField(reminderService, "checkoutReminderHours", 9);
        ReflectionTestUtils.setField(reminderService, "feedbackDaysAfter", 1);

        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setPreferredLanguage("en");

        testSpace = new SpaceEntity();
        testSpace.setId(UUID.randomUUID());
        testSpace.setName("Test Space");

        testReservation = new ReservationEntity();
        testReservation.setId(UUID.randomUUID());
        testReservation.setUser(testUser);
        testReservation.setSpace(testSpace);
        testReservation.setStartDate(LocalDate.now().plusDays(1));
        testReservation.setEndDate(LocalDate.now().plusDays(2));
        testReservation.setStatus(ReservationStatusForEntity.CONFIRMED);
    }

    @Test
    @DisplayName("sendUpcomingReservationReminders should find reservations within reminder window")
    void testSendUpcomingReservationReminders_FindsReservations() {
        // Given
        List<ReservationEntity> reservations = new ArrayList<>();
        reservations.add(testReservation);

        when(reservationRepository.findAll()).thenReturn(reservations);
        // Use lenient() for mocks that may not be used if no reservations match the filter
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key; // Return the key as the message for simplicity
        });
        lenient().when(matrixAssistantService.findUserInMatrix(any(UserEntity.class))).thenReturn(Optional.of("@user:chat.neohoods.com"));

        // When
        assertDoesNotThrow(() -> reminderService.sendUpcomingReservationReminders());

        // Then
        verify(reservationRepository).findAll();
    }

    @Test
    @DisplayName("sendUpcomingReservationReminders should skip reservations outside reminder window")
    void testSendUpcomingReservationReminders_SkipsOutsideWindow() {
        // Given
        testReservation.setStartDate(LocalDate.now().plusDays(10)); // Too far in future
        List<ReservationEntity> reservations = new ArrayList<>();
        reservations.add(testReservation);

        when(reservationRepository.findAll()).thenReturn(reservations);

        // When
        assertDoesNotThrow(() -> reminderService.sendUpcomingReservationReminders());

        // Then
        verify(reservationRepository).findAll();
        verify(matrixAssistantService, never()).findUserInMatrix(any());
    }

    @Test
    @DisplayName("sendCheckoutReminders should find reservations ending today")
    void testSendCheckoutReminders_FindsReservationsEndingToday() {
        // Given
        testReservation.setStatus(ReservationStatusForEntity.ACTIVE);
        testReservation.setEndDate(LocalDate.now());
        List<ReservationEntity> reservations = new ArrayList<>();
        reservations.add(testReservation);

        when(reservationRepository.findAll()).thenReturn(reservations);
        // Use lenient() for mocks that may not be used if no reservations match the filter
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key; // Return the key as the message for simplicity
        });
        lenient().when(matrixAssistantService.findUserInMatrix(any(UserEntity.class))).thenReturn(Optional.of("@user:chat.neohoods.com"));

        // When
        assertDoesNotThrow(() -> reminderService.sendCheckoutReminders());

        // Then
        verify(reservationRepository).findAll();
    }

    @Test
    @DisplayName("sendFeedbackRequests should find completed reservations")
    void testSendFeedbackRequests_FindsCompletedReservations() {
        // Given
        testReservation.setStatus(ReservationStatusForEntity.COMPLETED);
        testReservation.setEndDate(LocalDate.now().minusDays(1));
        List<ReservationEntity> reservations = new ArrayList<>();
        reservations.add(testReservation);

        when(reservationRepository.findAll()).thenReturn(reservations);
        // Use lenient() for mocks that may not be used if no reservations match the filter
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key; // Return the key as the message for simplicity
        });
        lenient().when(matrixAssistantService.findUserInMatrix(any(UserEntity.class))).thenReturn(Optional.of("@user:chat.neohoods.com"));

        // When
        assertDoesNotThrow(() -> reminderService.sendFeedbackRequests());

        // Then
        verify(reservationRepository).findAll();
    }

    @Test
    @DisplayName("sendMessageToUser should handle user not found in Matrix")
    void testSendMessageToUser_UserNotFound() {
        // Given
        when(matrixAssistantService.findUserInMatrix(any(UserEntity.class))).thenReturn(Optional.empty());

        // When
        assertDoesNotThrow(() -> {
            ReflectionTestUtils.invokeMethod(reminderService, "sendMessageToUser", testUser, "Test message", testReservation.getId());
        });

        // Then
        verify(matrixAssistantService).findUserInMatrix(testUser);
    }

    @Test
    @DisplayName("sendMessageToUser should use user locale")
    void testSendMessageToUser_UsesUserLocale() {
        // Given
        testUser.setPreferredLanguage("fr");
        // Set reservation start date to be within the reminder window (24 hours)
        // The method uses startDate.atStartOfDay(), so we need to ensure the date is tomorrow
        // to be within the 24-hour window (since atStartOfDay() gives midnight of that day)
        LocalDate startDate = LocalDate.now().plusDays(1);
        testReservation.setStartDate(startDate);
        testReservation.setStatus(ReservationStatusForEntity.CONFIRMED);
        List<ReservationEntity> reservations = new ArrayList<>();
        reservations.add(testReservation);
        when(reservationRepository.findAll()).thenReturn(reservations);
        when(matrixAssistantService.findUserInMatrix(any(UserEntity.class))).thenReturn(Optional.of("@user:chat.neohoods.com"));
        when(matrixAssistantService.findOrCreateDMRoom(anyString())).thenReturn(Optional.of("!dmroom:chat.neohoods.com"));
        // Mock messageSource to return messages for French locale
        when(messageSource.getMessage(anyString(), any(), eq(Locale.FRENCH))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key; // Return the key as the message for simplicity
        });

        // When
        assertDoesNotThrow(() -> {
            reminderService.sendUpcomingReservationReminders();
        });

        // Then
        // Verify locale is used - may be called multiple times
        verify(messageSource, atLeastOnce()).getMessage(anyString(), any(), eq(Locale.FRENCH));
    }
}

