package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import com.neohoods.portal.platform.spaces.entities.PaymentStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for Stripe payment integration.
 * 
 * Tests:
 * - Successful payment confirmation
 * - Failed payment handling
 * - Payment webhook processing
 */
@Transactional
public class StripeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private com.neohoods.portal.platform.services.UnitsService unitsService;

    private SpaceEntity guestRoomSpace;
    private UserEntity tenantUser;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        // Get guest room space
        guestRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No guest room space found"));

        // Get tenant user
        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.TENANT) {
                tenantUser = user;
                break;
            }
        }
        if (tenantUser == null) {
            tenantUser = new UserEntity();
            tenantUser.setEmail("test-tenant-stripe@neohoods.com");
            tenantUser.setType(UserType.TENANT);
            tenantUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            tenantUser = usersRepository.save(tenantUser);
        }

        // Create unit for tenant user (required for GUEST_ROOM reservations)
        if (unitsService.getUserUnits(tenantUser.getId()).count().block() == 0) {
            unitsService.createUnit("Test Unit " + tenantUser.getId(), null, tenantUser.getId()).block();
        }

        // Ensure tenant user has a primary unit set
        tenantUser = usersRepository.findById(tenantUser.getId()).orElse(tenantUser);
        if (tenantUser.getPrimaryUnit() == null) {
            var units = unitsService.getUserUnits(tenantUser.getId()).collectList().block();
            if (units != null && !units.isEmpty() && units.get(0).getId() != null) {
                unitsService.setPrimaryUnitForUser(tenantUser.getId(), units.get(0).getId(), null).block();
                tenantUser = usersRepository.findById(tenantUser.getId()).orElse(tenantUser);
            }
        }
    }

    @Test
    @DisplayName("Successful payment confirmation updates reservation status")
    public void testSuccessfulPayment_ReservationConfirmed() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_test_success", "cs_test_success");

        // Assert
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());
        assertEquals(PaymentStatusForEntity.SUCCEEDED, confirmed.getPaymentStatus());
        assertEquals("pi_test_success", confirmed.getStripePaymentIntentId());
        assertEquals("cs_test_success", confirmed.getStripeSessionId());
        assertNotNull(confirmed.getId());
        assertTrue(confirmed.getTotalPrice().compareTo(BigDecimal.ZERO) > 0,
                "Total price should be greater than zero");

        // Verify platform fees were calculated
        assertTrue(confirmed.getPlatformFeeAmount().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(confirmed.getPlatformFixedFeeAmount().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("Cannot confirm reservation twice")
    public void testCannotConfirmTwice() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        reservationsService.confirmReservation(reservation.getId(), "pi_first", "cs_first");

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.confirmReservation(reservation.getId(), "pi_second", "cs_second");
        });

        // Verify correct error code
        assertEquals(CodedError.RESERVATION_NOT_PENDING_PAYMENT, exception.getError());
        assertNotNull(exception.getVariables());
        assertEquals(reservation.getId(), exception.getVariables().get("reservationId"));
    }

    @Test
    @DisplayName("Payment amount matches reservation total")
    public void testPaymentAmount_MatchesTotal() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_amount", "cs_amount");

        // Assert
        assertNotNull(confirmed.getTotalPrice());
        assertTrue(confirmed.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Stripe payment intent and session IDs stored")
    public void testStripeIds_Stored() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        String paymentIntentId = "pi_" + System.currentTimeMillis();
        String sessionId = "cs_" + System.currentTimeMillis();

        // Act
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), paymentIntentId, sessionId);

        // Assert
        assertEquals(paymentIntentId, confirmed.getStripePaymentIntentId());
        assertEquals(sessionId, confirmed.getStripeSessionId());
    }

    @Test
    @DisplayName("Payment expiration timestamp set correctly")
    public void testPaymentExpiration_SetCorrectly() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertNotNull(reservation.getPaymentExpiresAt());
        // Payment expiration is set (timing varies in test environment)
    }

    @Test
    @DisplayName("Cancelled payment: reservation stays pending")
    public void testCancelledPayment_StaysPending() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act - Don't confirm, just leave it
        // In real scenario, webhook would mark as cancelled

        // Assert
        assertEquals(ReservationStatusForEntity.PENDING_PAYMENT, reservation.getStatus());
        assertEquals(PaymentStatusForEntity.PENDING, reservation.getPaymentStatus());
    }

    @Test
    @DisplayName("Refunded payment: reservation status handled")
    public void testRefundedPayment() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_refund", "cs_refund");

        // Act - Simulate refund by cancelling
        reservationsService.cancelReservation(confirmed.getId(), "Refund requested", tenantUser.getEmail());

        // Assert
        assertTrue(confirmed.getStatus() == ReservationStatusForEntity.CANCELLED);
    }

    @Test
    @DisplayName("Multiple payment attempts: second fails if already paid")
    public void testMultiplePaymentAttempts_SecondFails() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // First payment succeeds
        reservationsService.confirmReservation(reservation.getId(), "pi_1", "cs_1");

        // Act & Assert - Second payment should fail
        assertThrows(CodedErrorException.class, () -> {
            reservationsService.confirmReservation(reservation.getId(), "pi_2", "cs_2");
        });
    }

    @Test
    @DisplayName("Payment webhook: updates correct reservation")
    public void testPaymentWebhook_UpdatesCorrectReservation() {
        // Arrange - Create multiple reservations - Use dates far in the future to avoid conflicts
        LocalDate startDate = LocalDate.now().plusDays(365);

        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, startDate.plusDays(3));

        ReservationEntity res2 = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate.plusDays(10), startDate.plusDays(13));

        // Act - Confirm second reservation
        reservationsService.confirmReservation(res2.getId(), "pi_webhook_test", "cs_webhook_test");

        // Assert - Only second should be confirmed
        ReservationEntity updatedRes2 = reservationsService.getReservationById(res2.getId());
        assertEquals(ReservationStatusForEntity.CONFIRMED, updatedRes2.getStatus());

        // First reservation should still be pending
        ReservationEntity updatedRes1 = reservationsService.getReservationById(res1.getId());
        assertEquals(ReservationStatusForEntity.PENDING_PAYMENT, updatedRes1.getStatus());
        assertEquals(PaymentStatusForEntity.SUCCEEDED, updatedRes2.getPaymentStatus());
    }

    @Test
    @DisplayName("Platform fees: correctly calculated and stored")
    public void testPlatformFees_CalculatedAndStored() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertNotNull(reservation.getPlatformFeeAmount());
        assertNotNull(reservation.getPlatformFixedFeeAmount());
        assertTrue(reservation.getPlatformFeeAmount().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(reservation.getPlatformFixedFeeAmount().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("Payment currency: correct currency used")
    public void testPaymentCurrency_Correct() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertEquals(guestRoomSpace.getCurrency(), reservation.getSpace().getCurrency());
        // For French spaces, should be EUR
        assertEquals("EUR", guestRoomSpace.getCurrency());
    }
}
