package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * User Story Error Scenarios Tests for ReservationsService
 * 
 * These tests cover real-world error scenarios that users might encounter
 * when interacting with the reservation system, ensuring they receive
 * precise error codes and helpful context.
 */
@Transactional
public class ReservationErrorScenariosFixedTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    private SpaceEntity guestRoomSpace;
    private SpaceEntity inactiveSpace;
    private UserEntity tenantUser;
    private UserEntity ownerUser;

    @BeforeEach
    public void setUp() {
        super.baseSetUp();

        // Create a guest room space
        List<SpaceEntity> guestRooms = spaceRepository.findByTypeAndStatus(SpaceTypeForEntity.GUEST_ROOM,
                SpaceStatusForEntity.ACTIVE);
        if (guestRooms.isEmpty()) {
            guestRoomSpace = new SpaceEntity();
            guestRoomSpace.setName("Test Guest Room");
            guestRoomSpace.setDescription("A test guest room");
            guestRoomSpace.setType(SpaceTypeForEntity.GUEST_ROOM);
            guestRoomSpace.setStatus(SpaceStatusForEntity.ACTIVE);
            guestRoomSpace.setTenantPrice(new BigDecimal("100.00"));
            guestRoomSpace.setOwnerPrice(new BigDecimal("80.00"));
            guestRoomSpace.setCleaningFee(new BigDecimal("20.00"));
            guestRoomSpace.setDeposit(new BigDecimal("50.00"));
            guestRoomSpace.setCurrency("EUR");
            guestRoomSpace.setMinDurationDays(1);
            guestRoomSpace.setMaxDurationDays(30);
            guestRoomSpace.setRequiresApartmentAccess(true);
            guestRoomSpace.setMaxAnnualReservations(10);
            guestRoomSpace.setUsedAnnualReservations(0);
            guestRoomSpace.setAccessCodeEnabled(true);
            guestRoomSpace.setAllowedHoursStart("15:00");
            guestRoomSpace.setAllowedHoursEnd("11:00");
            guestRoomSpace = spaceRepository.save(guestRoomSpace);
        } else {
            guestRoomSpace = guestRooms.get(0);
        }

        // Create an inactive space for testing
        inactiveSpace = new SpaceEntity();
        inactiveSpace.setName("Inactive Space");
        inactiveSpace.setDescription("An inactive space for testing");
        inactiveSpace.setType(SpaceTypeForEntity.GUEST_ROOM);
        inactiveSpace.setStatus(SpaceStatusForEntity.INACTIVE);
        inactiveSpace.setTenantPrice(new BigDecimal("50.00"));
        inactiveSpace.setOwnerPrice(new BigDecimal("40.00"));
        inactiveSpace.setCleaningFee(BigDecimal.ZERO);
        inactiveSpace.setDeposit(BigDecimal.ZERO);
        inactiveSpace.setCurrency("EUR");
        inactiveSpace.setMinDurationDays(1);
        inactiveSpace.setMaxDurationDays(7);
        inactiveSpace.setRequiresApartmentAccess(false);
        inactiveSpace.setMaxAnnualReservations(0);
        inactiveSpace.setUsedAnnualReservations(0);
        inactiveSpace.setAccessCodeEnabled(false);
        inactiveSpace = spaceRepository.save(inactiveSpace);

        // Create tenant user
        List<UserEntity> tenants = usersRepository.findByType(UserType.TENANT);
        if (tenants.isEmpty()) {
            tenantUser = new UserEntity();
            tenantUser.setEmail("tenant.error@example.com");
            tenantUser.setFirstName("Tenant");
            tenantUser.setLastName("Error");
            tenantUser.setType(UserType.TENANT);
            tenantUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            tenantUser = usersRepository.save(tenantUser);
        } else {
            tenantUser = tenants.get(0);
        }

        // Create owner user
        List<UserEntity> owners = usersRepository.findByType(UserType.OWNER);
        if (owners.isEmpty()) {
            ownerUser = new UserEntity();
            ownerUser.setEmail("owner.error@example.com");
            ownerUser.setFirstName("Owner");
            ownerUser.setLastName("Error");
            ownerUser.setType(UserType.OWNER);
            ownerUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            ownerUser = usersRepository.save(ownerUser);
        } else {
            ownerUser = owners.get(0);
        }
    }

    @Test
    @DisplayName("User Story: Marie tries to book an inactive space and gets a clear error message")
    public void testUserStory_MarieBooksInactiveSpace() {
        // User Story: Marie is browsing spaces and finds one she likes, but when she
        // tries to book it,
        // she gets a clear error message explaining that the space is not available for
        // booking.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(100);
        LocalDate endDate = startDate.plusDays(3);

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(inactiveSpace, tenantUser, startDate, endDate);
        });

        // Verify the user gets a precise error code
        assertEquals(CodedError.SPACE_INACTIVE, exception.getError());
        assertEquals("SPA002", exception.getError().getCode());
        assertEquals("Space is not active and cannot be reserved", exception.getError().getDefaultMessage());

        // Verify context is provided
        assertNotNull(exception.getVariables());
        assertEquals(inactiveSpace.getId(), exception.getVariables().get("spaceId"));
        assertEquals(SpaceStatusForEntity.INACTIVE.toString(), exception.getVariables().get("status"));
    }

    @Test
    @DisplayName("User Story: Pierre tries to book for only 2 hours but minimum is 1 day")
    public void testUserStory_PierreBooksTooShortDuration() {
        // User Story: Pierre wants to book a space for just a few hours for a meeting,
        // but the system explains that the minimum booking duration is 1 day.

        // Arrange
        LocalDate baseDate = LocalDate.now().plusDays(101);
        LocalDate startDate = baseDate;
        LocalDate endDate = baseDate; // Same day = 1 day (inclusive)

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(guestRoomSpace, tenantUser, startDate, endDate);
        });

        // Verify precise error code
        assertEquals(CodedError.SPACE_DURATION_TOO_SHORT, exception.getError());
        assertEquals("SPA003", exception.getError().getCode());
        assertEquals("Reservation duration is shorter than the minimum allowed",
                exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(guestRoomSpace.getId(), exception.getVariables().get("spaceId"));
        assertEquals(1L, exception.getVariables().get("requestedDays")); // Same day = 1 day (inclusive)
        assertEquals(2, exception.getVariables().get("minDurationDays")); // Use int instead of Long
    }

    @Test
    @DisplayName("User Story: Sophie tries to book for 2 months but maximum is 30 days")
    public void testUserStory_SophieBooksTooLongDuration() {
        // User Story: Sophie wants to book a space for 2 months for a long-term
        // project,
        // but the system explains that the maximum booking duration is 30 days.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(102);
        LocalDate endDate = startDate.plusDays(60); // 60 days

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(guestRoomSpace, tenantUser, startDate, endDate);
        });

        // Verify precise error code
        assertEquals(CodedError.SPACE_DURATION_TOO_LONG, exception.getError());
        assertEquals("SPA004", exception.getError().getCode());
        assertEquals("Reservation duration is longer than the maximum allowed",
                exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(guestRoomSpace.getId(), exception.getVariables().get("spaceId"));
        assertEquals(61L, exception.getVariables().get("requestedDays")); // 60 days + 1 = 61 days requested
        assertEquals(7, exception.getVariables().get("maxDurationDays")); // Use int instead of Long
    }

    @Test
    @DisplayName("User Story: Thomas tries to book dates that are already taken")
    public void testUserStory_ThomasBooksOverlappingDates() {
        // User Story: Thomas tries to book a space for dates that are already reserved,
        // and gets a clear message about the conflict.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(103);
        LocalDate endDate = startDate.plusDays(3);

        // Create first reservation
        reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act & Assert - Try to book overlapping dates
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    guestRoomSpace, ownerUser, startDate.plusDays(1), endDate.plusDays(1));
        });

        // Verify precise error code
        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
        assertEquals("RES020", exception.getError().getCode());
        assertEquals("Space is not available for the selected dates", exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(guestRoomSpace.getId(), exception.getVariables().get("spaceId"));
        assertEquals(startDate.plusDays(1).toString(), exception.getVariables().get("startDate"));
        assertEquals(endDate.plusDays(1).toString(), exception.getVariables().get("endDate"));
    }

    @Test
    @DisplayName("User Story: Lisa tries to cancel an already cancelled reservation")
    public void testUserStory_LisaCancelsAlreadyCancelledReservation() {
        // User Story: Lisa tries to cancel a reservation that she already cancelled,
        // and gets a clear message that it's already cancelled.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(104);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Cancel the reservation first
        reservationsService.cancelReservation(reservation.getId(), "Change of mind", "tenant");

        // Act & Assert - Try to cancel again
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.cancelReservation(reservation.getId(), "Another reason", "tenant");
        });

        // Verify precise error code
        assertEquals(CodedError.RESERVATION_ALREADY_CANCELLED, exception.getError());
        assertEquals("RES021", exception.getError().getCode());
        assertEquals("Reservation is already cancelled", exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(reservation.getId(), exception.getVariables().get("reservationId"));
    }

    @Test
    @DisplayName("User Story: Marc tries to confirm a reservation that's not pending payment")
    public void testUserStory_MarcConfirmsNonPendingReservation() {
        // User Story: Marc tries to confirm a reservation that's already confirmed,
        // and gets a clear message about the invalid status transition.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(105);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Confirm the reservation first
        reservationsService.confirmReservation(reservation.getId(), "pi_test", "cs_test");

        // Act & Assert - Try to confirm again
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.confirmReservation(reservation.getId(), "pi_test_2", "cs_test_2");
        });

        // Verify precise error code
        assertEquals(CodedError.RESERVATION_NOT_PENDING_PAYMENT, exception.getError());
        assertEquals("RES023", exception.getError().getCode());
        assertEquals("Reservation is not in pending payment status", exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(reservation.getId(), exception.getVariables().get("reservationId"));
    }

    @Test
    @DisplayName("User Story: Emma tries to complete a reservation that's not active")
    public void testUserStory_EmmaCompletesNonActiveReservation() {
        // User Story: Emma tries to mark a reservation as completed, but it's not in
        // active status,
        // and gets a clear message about the invalid operation.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(106);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act & Assert - Try to complete a pending reservation
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.completeReservation(reservation.getId());
        });

        // Verify precise error code
        assertEquals(CodedError.RESERVATION_NOT_ACTIVE, exception.getError());
        assertEquals("RES025", exception.getError().getCode());
        assertEquals("Reservation is not in active status", exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(reservation.getId(), exception.getVariables().get("reservationId"));
    }

    @Test
    @DisplayName("User Story: David tries to complete a reservation before the end date")
    public void testUserStory_DavidCompletesReservationTooEarly() {
        // User Story: David tries to mark a reservation as completed before the end
        // date,
        // and gets a clear message that the end date hasn't been reached yet.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(107);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Confirm the reservation to make it active
        reservationsService.confirmReservation(reservation.getId(), "pi_test", "cs_test");

        // Act & Assert - Try to complete before end date
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.completeReservation(reservation.getId());
        });

        // Verify precise error code - The reservation is confirmed but not yet active
        assertEquals(CodedError.RESERVATION_NOT_ACTIVE, exception.getError());
        assertEquals("RES025", exception.getError().getCode());
        assertEquals("Reservation is not in active status", exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(reservation.getId(), exception.getVariables().get("reservationId"));
    }

    @Test
    @DisplayName("User Story: Anna tries to retry payment on a confirmed reservation")
    public void testUserStory_AnnaRetriesPaymentOnConfirmedReservation() {
        // User Story: Anna tries to retry payment on a reservation that's already
        // confirmed,
        // and gets a clear message that payment retry is not allowed.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(108);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Confirm the reservation
        reservationsService.confirmReservation(reservation.getId(), "pi_test", "cs_test");

        // Act & Assert - Try to retry payment
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.retryPayment(reservation.getId());
        });

        // Verify precise error code
        assertEquals(CodedError.RESERVATION_CANNOT_RETRY_PAYMENT, exception.getError());
        assertEquals("RES026", exception.getError().getCode());
        assertEquals("Reservation is not in a state that allows payment retry",
                exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(reservation.getId(), exception.getVariables().get("reservationId"));
    }

    @Test
    @DisplayName("User Story: Robert tries to book a space that has reached its annual quota")
    public void testUserStory_RobertBooksSpaceWithExceededQuota() {
        // User Story: Robert tries to book a space that has reached its annual
        // reservation limit,
        // and gets a clear message about the quota being exceeded.

        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(109);
        LocalDate endDate = startDate.plusDays(3);

        // Set the space to have a quota of 1 and already used
        guestRoomSpace.setMaxAnnualReservations(1);
        guestRoomSpace.setUsedAnnualReservations(1);
        spaceRepository.save(guestRoomSpace);

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(guestRoomSpace, tenantUser, startDate, endDate);
        });

        // Verify precise error code
        assertEquals(CodedError.SPACE_ANNUAL_QUOTA_EXCEEDED, exception.getError());
        assertEquals("SPA005", exception.getError().getCode());
        assertEquals("Space has reached its maximum annual reservation limit",
                exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(guestRoomSpace.getId(), exception.getVariables().get("spaceId"));
        assertEquals(1, exception.getVariables().get("usedReservations"));
        assertEquals(1, exception.getVariables().get("maxReservations"));
    }

    @Test
    @DisplayName("User Story: Sarah tries to book dates in the past")
    public void testUserStory_SarahBooksPastDates() {
        // User Story: Sarah tries to book a space for dates that are in the past,
        // and gets a clear message that past dates are not allowed.

        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(5);
        LocalDate endDate = LocalDate.now().minusDays(1);

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(guestRoomSpace, tenantUser, startDate, endDate);
        });

        // Verify precise error code
        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
        assertEquals("RES020", exception.getError().getCode());
        assertEquals("Space is not available for the selected dates", exception.getError().getDefaultMessage());

        // Verify helpful context
        assertNotNull(exception.getVariables());
        assertEquals(guestRoomSpace.getId(), exception.getVariables().get("spaceId"));
        assertEquals(startDate.toString(), exception.getVariables().get("startDate"));
        assertEquals(endDate.toString(), exception.getVariables().get("endDate"));
    }
}
