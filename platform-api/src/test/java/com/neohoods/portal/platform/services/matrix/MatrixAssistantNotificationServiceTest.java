package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantNotificationService;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantNotificationService Unit Tests")
class MatrixAssistantNotificationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MatrixAssistantService matrixAssistantService;

    @InjectMocks
    private MatrixAssistantNotificationService notificationService;

    private UserEntity testUser;
    private SpaceEntity testSpace;
    private ReservationEntity testReservation;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());

        testSpace = new SpaceEntity();
        testSpace.setId(UUID.randomUUID());
        testSpace.setName("Test Space");

        testReservation = new ReservationEntity();
        testReservation.setId(UUID.randomUUID());
        testReservation.setUser(testUser);
        testReservation.setSpace(testSpace);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setEndDate(LocalDate.now().plusDays(1));
        testReservation.setTotalPrice(new java.math.BigDecimal("100.00"));
    }

    @Test
    @DisplayName("notifyReservationCompleted should skip when reservation is not completed")
    void testNotifyReservationCompleted_SkipsWhenNotCompleted() {
        // Given
        testReservation.setStatus(ReservationStatusForEntity.CONFIRMED);

        // When
        notificationService.notifyReservationCompleted(testReservation);

        // Then
        verify(matrixAssistantService, never()).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyReservationCompleted should process completed reservation")
    void testNotifyReservationCompleted_ProcessesCompleted() {
        // Given
        testReservation.setStatus(ReservationStatusForEntity.COMPLETED);

        // When
        assertDoesNotThrow(() -> notificationService.notifyReservationCompleted(testReservation));

        // Then
        // Currently just logs, TODO will be implemented later
        // verify(matrixAssistantService).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyPaymentSuccessful should process payment success")
    void testNotifyPaymentSuccessful_ProcessesSuccess() {
        // When
        assertDoesNotThrow(() -> notificationService.notifyPaymentSuccessful(testReservation));

        // Then
        // Currently just logs, TODO will be implemented later
        // verify(matrixAssistantService).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyPaymentFailed should process payment failure")
    void testNotifyPaymentFailed_ProcessesFailure() {
        // When
        assertDoesNotThrow(() -> notificationService.notifyPaymentFailed(testReservation));

        // Then
        // Currently just logs, TODO will be implemented later
        // verify(matrixAssistantService).sendMessage(anyString(), anyString());
    }
}

