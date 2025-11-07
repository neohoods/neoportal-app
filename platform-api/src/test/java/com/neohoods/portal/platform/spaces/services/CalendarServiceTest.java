package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.context.MessageSource;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Unit tests for CalendarService.
 * 
 * Tests:
 * - Calendar generation with various reservations
 * - Event formatting
 * - Date/time calculation
 * - Text escaping
 * - Error handling
 */
@ExtendWith(MockitoExtension.class)
public class CalendarServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private CalendarService calendarService;

    private SpaceEntity space;
    private UserEntity user;
    private UUID spaceId;

    @BeforeEach
    public void setUp() {
        // Set baseUrl and appName using reflection
        ReflectionTestUtils.setField(calendarService, "baseUrl", "https://local.portal.neohoods.com:4200");
        ReflectionTestUtils.setField(calendarService, "appName", "Terres de Laya");
        
        spaceId = UUID.randomUUID();
        space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("Test Guest Room");
        space.setType(SpaceTypeForEntity.GUEST_ROOM);
        space.setCleaningEnabled(true);
        space.setCleaningCalendarEnabled(true);
        space.setCleaningDaysAfterCheckout(0);
        space.setCleaningHour("10:00");

        user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");

        // Mock MessageSource to return translation keys as-is (lenient to avoid unnecessary stubbing errors)
        lenient().when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            // Return a simple default value based on the key
            if (key.contains("cleaning")) {
                return "Cleaning";
            } else if (key.contains("scheduleFor")) {
                return "Cleaning schedule for";
            } else if (key.contains("cleaningForReservation")) {
                return "Cleaning for reservation:";
            } else if (key.contains("guest")) {
                return "Guest:";
            } else if (key.contains("checkout")) {
                return "Checkout:";
            } else if (key.contains("reservation")) {
                return "Reservation:";
            } else if (key.contains("to")) {
                return "to";
            } else if (key.contains("reservationId")) {
                return "Reservation ID:";
            } else if (key.contains("spaceId")) {
                return "Space ID:";
            } else if (key.contains("from")) {
                return "From:";
            }
            return key;
        });
    }

    @Test
    @DisplayName("Generate calendar with single confirmed reservation")
    public void testGenerateCalendar_SingleReservation() {
        // Arrange
        ReservationEntity reservation = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.CONFIRMED,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3));

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(reservationRepository.findBySpace(space)).thenReturn(List.of(reservation));

        // Act
        String calendar = calendarService.generateCalendarIcs(spaceId);

        // Assert
        assertNotNull(calendar);
        assertTrue(calendar.contains("BEGIN:VCALENDAR"));
        assertTrue(calendar.contains("END:VCALENDAR"));
        assertTrue(calendar.contains("BEGIN:VEVENT"));
        assertTrue(calendar.contains("END:VEVENT"));
        assertTrue(calendar.contains("Cleaning - Test Guest Room - John Doe"));
        assertTrue(calendar.contains("john.doe@example.com"));
    }

    @Test
    @DisplayName("Generate calendar with multiple reservations")
    public void testGenerateCalendar_MultipleReservations() {
        // Arrange
        ReservationEntity reservation1 = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.CONFIRMED,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3));

        ReservationEntity reservation2 = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.ACTIVE,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7));

        ReservationEntity reservation3 = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.COMPLETED,
                LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(3));

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(reservationRepository.findBySpace(space))
                .thenReturn(List.of(reservation1, reservation2, reservation3));

        // Act
        String calendar = calendarService.generateCalendarIcs(spaceId);

        // Assert
        assertNotNull(calendar);
        // Count VEVENT blocks
        long eventCount = calendar.split("BEGIN:VEVENT").length - 1;
        assertEquals(3, eventCount);
    }

    @Test
    @DisplayName("Generate calendar with days after checkout offset")
    public void testGenerateCalendar_DaysAfterCheckout() {
        // Arrange
        space.setCleaningDaysAfterCheckout(1);
        LocalDate checkoutDate = LocalDate.now().plusDays(3);
        ReservationEntity reservation = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.CONFIRMED,
                LocalDate.now().plusDays(1),
                checkoutDate);

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(reservationRepository.findBySpace(space)).thenReturn(List.of(reservation));

        // Act
        String calendar = calendarService.generateCalendarIcs(spaceId);

        // Assert
        assertNotNull(calendar);
        // Calendar should contain the cleaning date (checkout + 1 day)
        LocalDate cleaningDate = checkoutDate.plusDays(1);
        assertTrue(calendar.contains(cleaningDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))));
    }

    @Test
    @DisplayName("Generate calendar with custom cleaning hour")
    public void testGenerateCalendar_CustomCleaningHour() {
        // Arrange
        space.setCleaningHour("14:30");
        ReservationEntity reservation = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.CONFIRMED,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3));

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(reservationRepository.findBySpace(space)).thenReturn(List.of(reservation));

        // Act
        String calendar = calendarService.generateCalendarIcs(spaceId);

        // Assert
        assertNotNull(calendar);
        assertTrue(calendar.contains("143000")); // HHmmss format
    }

    @Test
    @DisplayName("Filter out non-completed reservations")
    public void testGenerateCalendar_FiltersReservations() {
        // Arrange
        ReservationEntity confirmed = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.CONFIRMED,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3));

        ReservationEntity pending = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.PENDING_PAYMENT,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7));

        ReservationEntity cancelled = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.CANCELLED,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12));

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(reservationRepository.findBySpace(space))
                .thenReturn(List.of(confirmed, pending, cancelled));

        // Act
        String calendar = calendarService.generateCalendarIcs(spaceId);

        // Assert
        assertNotNull(calendar);
        // Only CONFIRMED should be included
        long eventCount = calendar.split("BEGIN:VEVENT").length - 1;
        assertEquals(1, eventCount);
    }

    @Test
    @DisplayName("Generate calendar with empty reservations list")
    public void testGenerateCalendar_EmptyReservations() {
        // Arrange
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(reservationRepository.findBySpace(space)).thenReturn(new ArrayList<>());

        // Act
        String calendar = calendarService.generateCalendarIcs(spaceId);

        // Assert
        assertNotNull(calendar);
        assertTrue(calendar.contains("BEGIN:VCALENDAR"));
        assertTrue(calendar.contains("END:VCALENDAR"));
        // No VEVENT blocks
        assertTrue(!calendar.contains("BEGIN:VEVENT") || calendar.split("BEGIN:VEVENT").length == 1);
    }

    @Test
    @DisplayName("Throw exception when space not found")
    public void testGenerateCalendar_SpaceNotFound() {
        // Arrange
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            calendarService.generateCalendarIcs(spaceId);
        });
    }

    @Test
    @DisplayName("Throw exception when cleaning not enabled")
    public void testGenerateCalendar_CleaningNotEnabled() {
        // Arrange
        space.setCleaningEnabled(false);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            calendarService.generateCalendarIcs(spaceId);
        });
    }

    @Test
    @DisplayName("Throw exception when calendar not enabled")
    public void testGenerateCalendar_CalendarNotEnabled() {
        // Arrange
        space.setCleaningCalendarEnabled(false);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            calendarService.generateCalendarIcs(spaceId);
        });
    }

    @Test
    @DisplayName("Escape special characters in text")
    public void testGenerateCalendar_EscapeSpecialCharacters() {
        // Arrange
        space.setName("Test; Room, With\\Special\nChars");
        user.setFirstName("John;");
        user.setLastName("Doe,");
        user.setEmail("john\\doe@example.com");

        ReservationEntity reservation = createReservation(
                UUID.randomUUID(),
                ReservationStatusForEntity.CONFIRMED,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3));

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(reservationRepository.findBySpace(space)).thenReturn(List.of(reservation));

        // Act
        String calendar = calendarService.generateCalendarIcs(spaceId);

        // Assert
        assertNotNull(calendar);
        // Check that special characters are escaped
        assertTrue(calendar.contains("Test\\; Room\\, With\\\\Special\\nChars"));
    }

    private ReservationEntity createReservation(UUID id, ReservationStatusForEntity status,
            LocalDate startDate, LocalDate endDate) {
        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(id);
        reservation.setSpace(space);
        reservation.setUser(user);
        reservation.setStatus(status);
        reservation.setStartDate(startDate);
        reservation.setEndDate(endDate);
        reservation.setTotalPrice(java.math.BigDecimal.valueOf(100.0));
        return reservation;
    }
}


