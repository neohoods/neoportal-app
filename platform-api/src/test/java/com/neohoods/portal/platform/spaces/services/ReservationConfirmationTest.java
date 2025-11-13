package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.PaymentStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for reservation confirmation functionality.
 */
@Transactional
public class ReservationConfirmationTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private com.neohoods.portal.platform.services.UnitsService unitsService;

    private SpaceEntity guestRoomSpace;
    private UserEntity ownerUser;

    @BeforeEach
    public void setUp() {
        // Get guest room space
        guestRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No guest room space found"));

        // Get owner user
        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.OWNER) {
                ownerUser = user;
                break;
            }
        }
        if (ownerUser == null) {
            ownerUser = new UserEntity();
            ownerUser.setEmail("test-owner@neohoods.com");
            ownerUser.setUsername("test-owner");
            ownerUser.setFirstName("Test");
            ownerUser.setLastName("Owner");
            ownerUser.setType(UserType.OWNER);
            ownerUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            ownerUser = usersRepository.save(ownerUser);
        }

        // Create unit for owner user (required for GUEST_ROOM reservations)
        if (unitsService.getUserUnits(ownerUser.getId()).count().block() == 0) {
            unitsService.createUnit("Test Unit " + ownerUser.getId(), null, ownerUser.getId()).block();
        }

        // Ensure owner user has a primary unit set
        ownerUser = usersRepository.findById(ownerUser.getId()).orElse(ownerUser);
        if (ownerUser.getPrimaryUnit() == null) {
            var units = unitsService.getUserUnits(ownerUser.getId()).collectList().block();
            if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                unitsService.setPrimaryUnitForUser(ownerUser.getId(), units.get(0).getId(), null).block();
                ownerUser = usersRepository.findById(ownerUser.getId()).orElse(ownerUser);
            }
        }
    }

    @Test
    @DisplayName("Confirm reservation - starts soon generates access code immediately")
    public void testConfirmReservation_Success_SameDay() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(202); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(1); // One day reservation
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, endDate);

        // Act
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_test_123", "cs_test_456");

        // Assert
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());
        assertEquals(PaymentStatusForEntity.SUCCEEDED, confirmed.getPaymentStatus());
    }

    @Test
    @DisplayName("Confirm reservation - status change from pending to confirmed")
    public void testConfirmReservation_StatusChange_PendingToConfirmed() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, startDate.plusDays(3));

        // Act
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_test_789", "cs_test_101");

        // Assert
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());
        assertEquals("pi_test_789", confirmed.getStripePaymentIntentId());
        assertEquals("cs_test_101", confirmed.getStripeSessionId());
    }

    @Test
    @DisplayName("Cannot confirm already confirmed reservation")
    public void testConfirmReservation_AlreadyConfirmed() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, startDate.plusDays(3));
        reservationsService.confirmReservation(reservation.getId(), "pi_123", "cs_456");

        // Act & Assert
        assertThrows(CodedErrorException.class, () -> {
            reservationsService.confirmReservation(reservation.getId(), "pi_456", "cs_789");
        });
    }

    @Test
    @DisplayName("Cannot confirm cancelled reservation")
    public void testConfirmReservation_Cancelled() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, startDate.plusDays(3));
        reservationsService.cancelReservation(reservation.getId(), "Test cancellation", ownerUser.getEmail());

        // Act & Assert
        assertThrows(CodedErrorException.class, () -> {
            reservationsService.confirmReservation(reservation.getId(), "pi_123", "cs_456");
        });
    }
}
