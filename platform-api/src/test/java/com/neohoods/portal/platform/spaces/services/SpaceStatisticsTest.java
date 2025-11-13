package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.model.OccupancyCalendarDay;
import com.neohoods.portal.platform.model.SpaceStatistics;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for space statistics.
 * 
 * Tests calculation of:
 * - Total reservations
 * - Total revenue
 * - Occupancy rate
 * - Average duration
 */
@Transactional
public class SpaceStatisticsTest extends BaseIntegrationTest {

    @Autowired
    private SpaceStatisticsService statisticsService;

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private com.neohoods.portal.platform.services.UnitsService unitsService;

    private SpaceEntity guestRoomSpace;
    private UserEntity ownerUser;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        guestRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No guest room space found"));

        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.OWNER) {
                ownerUser = user;
                break;
            }
        }

        // Create unit for owner user (required for GUEST_ROOM reservations)
        if (ownerUser != null) {
            if (unitsService.getUserUnits(ownerUser.getId()).count().block() == 0) {
                unitsService.createUnit("Test Unit " + ownerUser.getId(), null, ownerUser.getId()).block();
            }
            ownerUser = usersRepository.findById(ownerUser.getId()).orElse(ownerUser);
            if (ownerUser.getPrimaryUnit() == null) {
                var units = unitsService.getUserUnits(ownerUser.getId()).collectList().block();
                if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                    unitsService.setPrimaryUnitForUser(ownerUser.getId(), units.get(0).getId(), null).block();
                    ownerUser = usersRepository.findById(ownerUser.getId()).orElse(ownerUser);
                }
            }
        }
    }

    @Test
    @DisplayName("Calculate statistics for date range")
    public void testCalculateStatistics_DateRange() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now().plusDays(30);

        // Act
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertNotNull(stats);
        assertNotNull(stats.getTotalReservations());
        assertNotNull(stats.getTotalRevenue());
    }

    @Test
    @DisplayName("Statistics: total reservations count")
    public void testStatistics_TotalReservations() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(60);
        LocalDate endDate = LocalDate.now().plusDays(60);

        // Act
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertTrue(stats.getTotalReservations() >= 0);
    }

    @Test
    @DisplayName("Statistics: total revenue calculation")
    public void testStatistics_TotalRevenue() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(60);
        LocalDate endDate = LocalDate.now().plusDays(60);

        // Act
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertTrue(stats.getTotalRevenue() >= 0.0);
    }

    @Test
    @DisplayName("Statistics: occupancy rate")
    public void testStatistics_OccupancyRate() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(60);
        LocalDate endDate = LocalDate.now().plusDays(60);

        // Act
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertTrue(stats.getOccupancyRate() >= 0.0);
        assertTrue(stats.getOccupancyRate() <= 100.0);
    }

    @Test
    @DisplayName("Statistics: average reservation duration")
    public void testStatistics_AverageDuration() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(60);
        LocalDate endDate = LocalDate.now().plusDays(60);

        // Act
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertTrue(stats.getAverageReservationDuration() >= 0.0);
    }

    @Test
    @DisplayName("Statistics: monthly occupancy data")
    public void testStatistics_MonthlyOccupancy() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(90);
        LocalDate endDate = LocalDate.now().plusDays(90);

        // Act
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertNotNull(stats.getMonthlyOccupancy());
        assertTrue(stats.getOccupancyRate() >= 0.0);
    }

    @Test
    @DisplayName("Statistics: revenue breakdown")
    public void testStatistics_RevenueBreakdown() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(60);
        LocalDate endDate = LocalDate.now().plusDays(60);

        // Act
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertNotNull(stats.getTotalRevenue());
        assertTrue(stats.getTotalRevenue() >= 0.0);
    }

    @Test
    @DisplayName("Occupancy calendar includes PENDING_PAYMENT reservations")
    public void testOccupancyCalendar_IncludesPendingPaymentReservations() {
        // Arrange
        LocalDate testStartDate = LocalDate.now().plusDays(500); // Far future to avoid conflicts
        LocalDate testEndDate = testStartDate.plusDays(2); // 3 days total
        LocalDate periodStartDate = testStartDate.minusDays(1);
        LocalDate periodEndDate = testEndDate.plusDays(1);

        // Get a tenant user
        UserEntity tenantUser = null;
        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.TENANT) {
                tenantUser = user;
                break;
            }
        }
        if (tenantUser == null) {
            tenantUser = new UserEntity();
            tenantUser.setEmail("test-tenant-stats@neohoods.com");
            tenantUser.setType(UserType.TENANT);
            tenantUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            tenantUser = usersRepository.save(tenantUser);
        }

        // Create unit for tenant user (required for GUEST_ROOM reservations)
        if (unitsService.getUserUnits(tenantUser.getId()).count().block() == 0) {
            unitsService.createUnit("Test Unit " + tenantUser.getId(), null, tenantUser.getId()).block();
        }
        tenantUser = usersRepository.findById(tenantUser.getId()).orElse(tenantUser);
        if (tenantUser.getPrimaryUnit() == null) {
            var units = unitsService.getUserUnits(tenantUser.getId()).collectList().block();
            if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                unitsService.setPrimaryUnitForUser(tenantUser.getId(), units.get(0).getId(), null).block();
                tenantUser = usersRepository.findById(tenantUser.getId()).orElse(tenantUser);
            }
        }

        // Create a reservation in PENDING_PAYMENT status
        ReservationEntity pendingReservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, testStartDate, testEndDate);

        // Verify it's in PENDING_PAYMENT status
        assertEquals(ReservationStatusForEntity.PENDING_PAYMENT, pendingReservation.getStatus(),
                "Reservation should be in PENDING_PAYMENT status");

        // Act - Calculate statistics for the period
        SpaceStatistics stats = statisticsService.calculateSpaceStatistics(
                guestRoomSpace.getId(), periodStartDate, periodEndDate);

        // Assert - The occupancy calendar should show the pending reservation as
        // occupied
        assertNotNull(stats.getOccupancyCalendar(), "Occupancy calendar should not be null");

        // Check that the dates of the pending reservation are marked as occupied
        long occupiedDaysCount = stats.getOccupancyCalendar().stream()
                .filter(day -> day.getDate().isEqual(testStartDate)
                        || (day.getDate().isAfter(testStartDate) && day.getDate().isBefore(testEndDate))
                        || day.getDate().isEqual(testEndDate))
                .filter(OccupancyCalendarDay::getIsOccupied)
                .count();

        assertTrue(occupiedDaysCount >= 3,
                "At least 3 days (testStartDate, testStartDate+1, testEndDate) should be marked as occupied in the calendar. "
                        +
                        "Found: " + occupiedDaysCount);

        // Verify specific days are marked as occupied
        boolean testStartDateOccupied = stats.getOccupancyCalendar().stream()
                .filter(day -> day.getDate().isEqual(testStartDate))
                .findFirst()
                .map(OccupancyCalendarDay::getIsOccupied)
                .orElse(false);

        assertTrue(testStartDateOccupied,
                "Test start date (" + testStartDate + ") should be marked as occupied in the calendar");

        // Verify reservation ID is present
        boolean hasReservationId = stats.getOccupancyCalendar().stream()
                .filter(day -> day.getDate().isEqual(testStartDate))
                .findFirst()
                .map(day -> day.getReservationId() != null
                        && day.getReservationId().equals(pendingReservation.getId()))
                .orElse(false);

        assertTrue(hasReservationId,
                "Occupancy calendar should include the reservation ID for the pending payment reservation");
    }

    @Test
    @DisplayName("calculateOccupancyCalendarForUser: returns reservationId only for current user's reservations")
    public void testCalculateOccupancyCalendarForUser_ReservationIdFiltering() {
        // Arrange
        LocalDate testStartDate = LocalDate.now().plusDays(600); // Far future to avoid conflicts
        LocalDate testEndDate = testStartDate.plusDays(2); // 3 days total
        LocalDate periodStartDate = testStartDate.minusDays(1);
        LocalDate periodEndDate = testEndDate.plusDays(1);

        // Get two different users
        UserEntity currentUser = null;
        UserEntity otherUser = null;
        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.TENANT) {
                if (currentUser == null) {
                    currentUser = user;
                } else if (otherUser == null) {
                    otherUser = user;
                    break;
                }
            }
        }
        if (currentUser == null) {
            currentUser = new UserEntity();
            currentUser.setEmail("test-current-user-stats@neohoods.com");
            currentUser.setType(UserType.TENANT);
            currentUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            currentUser = usersRepository.save(currentUser);
        }
        if (otherUser == null) {
            otherUser = new UserEntity();
            otherUser.setEmail("test-other-user-stats@neohoods.com");
            otherUser.setType(UserType.TENANT);
            otherUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            otherUser = usersRepository.save(otherUser);
        }

        // Create units for users (required for GUEST_ROOM reservations)
        if (unitsService.getUserUnits(currentUser.getId()).count().block() == 0) {
            unitsService.createUnit("Test Unit " + currentUser.getId(), null, currentUser.getId()).block();
        }
        currentUser = usersRepository.findById(currentUser.getId()).orElse(currentUser);
        if (currentUser.getPrimaryUnit() == null) {
            var units = unitsService.getUserUnits(currentUser.getId()).collectList().block();
            if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                unitsService.setPrimaryUnitForUser(currentUser.getId(), units.get(0).getId(), null).block();
                currentUser = usersRepository.findById(currentUser.getId()).orElse(currentUser);
            }
        }

        if (unitsService.getUserUnits(otherUser.getId()).count().block() == 0) {
            unitsService.createUnit("Test Unit " + otherUser.getId(), null, otherUser.getId()).block();
        }
        otherUser = usersRepository.findById(otherUser.getId()).orElse(otherUser);
        if (otherUser.getPrimaryUnit() == null) {
            var units = unitsService.getUserUnits(otherUser.getId()).collectList().block();
            if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                unitsService.setPrimaryUnitForUser(otherUser.getId(), units.get(0).getId(), null).block();
                otherUser = usersRepository.findById(otherUser.getId()).orElse(otherUser);
            }
        }

        // Create reservation for current user
        ReservationEntity currentUserReservation = reservationsService.createReservation(
                guestRoomSpace, currentUser, testStartDate, testEndDate);

        // Create reservation for other user (different dates)
        LocalDate otherUserStartDate = testEndDate.plusDays(5);
        LocalDate otherUserEndDate = otherUserStartDate.plusDays(2);
        ReservationEntity otherUserReservation = reservationsService.createReservation(
                guestRoomSpace, otherUser, otherUserStartDate, otherUserEndDate);

        // Act - Calculate occupancy calendar for current user
        LocalDate calendarStartDate = testStartDate.minusDays(1);
        LocalDate calendarEndDate = otherUserEndDate.plusDays(1);
        java.util.List<OccupancyCalendarDay> calendar = statisticsService.calculateOccupancyCalendarForUser(
                guestRoomSpace.getId(), currentUser.getId(), calendarStartDate, calendarEndDate);

        // Assert
        assertNotNull(calendar, "Calendar should not be null");

        // Check current user's reservation dates - should have reservationId
        OccupancyCalendarDay currentUserDay = calendar.stream()
                .filter(day -> day.getDate().isEqual(testStartDate))
                .findFirst()
                .orElse(null);

        assertNotNull(currentUserDay, "Current user's reservation date should be in calendar");
        assertTrue(currentUserDay.getIsOccupied(), "Current user's reservation date should be marked as occupied");
        assertNotNull(currentUserDay.getReservationId(), "Current user's reservation should have reservationId");
        assertEquals(currentUserReservation.getId(), currentUserDay.getReservationId(),
                "Current user's reservation should have correct reservationId");

        // Check other user's reservation dates - should NOT have reservationId
        OccupancyCalendarDay otherUserDay = calendar.stream()
                .filter(day -> day.getDate().isEqual(otherUserStartDate))
                .findFirst()
                .orElse(null);

        assertNotNull(otherUserDay, "Other user's reservation date should be in calendar");
        assertTrue(otherUserDay.getIsOccupied(), "Other user's reservation date should be marked as occupied");
        assertNull(otherUserDay.getReservationId(),
                "Other user's reservation should NOT have reservationId (only current user's reservations should)");

        // Verify userName is never exposed
        assertNull(currentUserDay.getUserName(), "userName should never be exposed in public API");
        assertNull(otherUserDay.getUserName(), "userName should never be exposed in public API");
    }

    @Test
    @DisplayName("calculateOccupancyCalendarForUser: throws exception for invalid space ID")
    public void testCalculateOccupancyCalendarForUser_InvalidSpaceId() {
        // Arrange
        java.util.UUID invalidSpaceId = java.util.UUID.randomUUID();
        java.util.UUID currentUserId = java.util.UUID.randomUUID();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(30);

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                com.neohoods.portal.platform.exceptions.CodedErrorException.class,
                () -> statisticsService.calculateOccupancyCalendarForUser(invalidSpaceId, currentUserId, startDate,
                        endDate),
                "Should throw CodedErrorException for invalid space ID");
    }

    @Test
    @DisplayName("calculateOccupancyCalendarForUser: returns empty calendar for space with no reservations")
    public void testCalculateOccupancyCalendarForUser_NoReservations() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(800); // Far future
        LocalDate endDate = startDate.plusDays(10);
        java.util.UUID currentUserId = ownerUser.getId();

        // Act
        java.util.List<OccupancyCalendarDay> calendar = statisticsService.calculateOccupancyCalendarForUser(
                guestRoomSpace.getId(), currentUserId, startDate, endDate);

        // Assert
        assertNotNull(calendar, "Calendar should not be null");
        assertEquals(11, calendar.size(), "Calendar should have 11 days (startDate + 10 days)");
        assertTrue(calendar.stream().noneMatch(OccupancyCalendarDay::getIsOccupied),
                "No days should be marked as occupied");
        assertTrue(calendar.stream().allMatch(day -> day.getReservationId() == null),
                "No reservation IDs should be present");
        assertTrue(calendar.stream().allMatch(day -> day.getUserName() == null),
                "No user names should be present");
    }
}
