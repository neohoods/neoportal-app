package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                        && day.getReservationId().equals(pendingReservation.getId().toString()))
                .orElse(false);

        assertTrue(hasReservationId,
                "Occupancy calendar should include the reservation ID for the pending payment reservation");
    }
}
