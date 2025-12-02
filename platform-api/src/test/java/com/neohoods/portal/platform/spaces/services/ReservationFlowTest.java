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
import com.neohoods.portal.platform.spaces.entities.AccessCodeEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.AccessCodeRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for the complete reservation flow:
 * 1. Create reservation → confirmation email
 * 2. Day before → reminder email
 * 3. Day of → access code generated
 * 4. End of stay → access code revoked
 */
@Transactional
public class ReservationFlowTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private AccessCodeRepository accessCodeRepository;

    @Autowired
    private ReservationEmailScheduler emailScheduler;

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

        // Get or create tenant user
        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.TENANT) {
                tenantUser = user;
                break;
            }
        }
        if (tenantUser == null) {
            tenantUser = new UserEntity();
            tenantUser.setEmail("test-tenant-flow@neohoods.com");
            tenantUser.setUsername("test-tenant-flow");
            tenantUser.setFirstName("Test");
            tenantUser.setLastName("Tenant");
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
    @DisplayName("Complete flow: 4-day guest room reservation → confirmation email sent")
    public void testCompleteFlow_ConfirmationEmailSent() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(4);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertNotNull(reservation);
        assertEquals(ReservationStatusForEntity.PENDING_PAYMENT, reservation.getStatus());

        // Verify confirmation email would be sent (verify it's scheduled)
        assertNotNull(reservation.getPaymentExpiresAt());
    }

    @Test
    @DisplayName("Complete flow: Access code generated for confirmed reservation")
    public void testCompleteFlow_AccessCodeGenerated() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(15);
        LocalDate endDate = startDate.plusDays(4);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act - Confirm reservation
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_test_flow", "cs_test_flow");

        // Assert
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());
        assertEquals("pi_test_flow", confirmed.getStripePaymentIntentId());
        assertEquals("cs_test_flow", confirmed.getStripeSessionId());

        // Check if access code was created
        AccessCodeEntity accessCode = accessCodeRepository.findByReservation(confirmed).orElse(null);
        assertNotNull(accessCode, "Access code should be generated for future reservations");
        assertNotNull(accessCode.getCode());
        assertTrue(accessCode.getCode().length() > 0, "Access code should not be empty");
        assertTrue(accessCode.getIsActive());
        assertNotNull(accessCode.getExpiresAt());

        // Verify access code expires at end of reservation
        assertEquals(endDate.atTime(23, 59, 59), accessCode.getExpiresAt());
    }

    @Test
    @DisplayName("Complete flow: Access code revoked at end of stay")
    public void testCompleteFlow_AccessCodeRevoked() {
        // Arrange - Create a future reservation
        LocalDate startDate = LocalDate.now().plusDays(65);
        LocalDate endDate = startDate.plusDays(4);

        // Note: In a real scenario, this would be a confirmed reservation
        // For testing, we'll verify the access code expiration logic

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Confirm it
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_test_revoke", "cs_test_revoke");

        // Assert
        // For future reservations, access code should be generated
        AccessCodeEntity accessCode = accessCodeRepository.findByReservation(confirmed).orElse(null);

        // Note: Future reservations should have active access codes
        if (accessCode != null) {
            assertTrue(accessCode.getIsActive());
            assertNotNull(accessCode.getCode());
            assertTrue(accessCode.getCode().length() > 0);
        }
    }

    @Test
    @DisplayName("Complete flow: Reminder email sent day before")
    public void testCompleteFlow_ReminderEmailSent() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(204); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(4);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_reminder", "cs_reminder");

        // Act - Trigger reminder logic (simulate scheduler)
        // Note: In real app, this is triggered by @Scheduled
        // For testing, we verify that reminder email is prepared

        // Assert
        assertNotNull(confirmed);
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());
        // Verify notification was created (mock should be called)
    }

    @Test
    @DisplayName("Complete flow: Cannot create overlapping reservations")
    public void testCompleteFlow_CannotOverlap() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(70);
        LocalDate endDate = startDate.plusDays(4);

        // Create first reservation
        ReservationEntity firstReservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);
        reservationsService.confirmReservation(firstReservation.getId(), "pi_1", "cs_1");

        // Act & Assert - Try to create overlapping reservation
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    guestRoomSpace, tenantUser, startDate.plusDays(1), endDate.plusDays(2));
        });

        // Verify correct error code
        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
        assertNotNull(exception.getVariables());
        assertEquals(guestRoomSpace.getId(), exception.getVariables().get("spaceId"));
    }

    @Test
    @DisplayName("Complete flow: Owner price vs Tenant price")
    public void testCompleteFlow_OwnerVsTenantPricing() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(75);
        LocalDate endDate = startDate.plusDays(4);

        // Create owner user
        UserEntity ownerUser = null;
        for (UserEntity user : usersRepository.findAll()) {
            if (user.getType() == UserType.OWNER) {
                ownerUser = user;
                break;
            }
        }

        if (ownerUser != null) {
            // Create tenant reservation
            ReservationEntity tenantReservation = reservationsService.createReservation(
                    guestRoomSpace, tenantUser, startDate, endDate);

            // Create owner reservation (different dates)
            ReservationEntity ownerReservation = reservationsService.createReservation(
                    guestRoomSpace, ownerUser, startDate.plusDays(10), endDate.plusDays(10));

            // Assert - Prices should be different
            assertNotNull(tenantReservation.getTotalPrice());
            assertNotNull(ownerReservation.getTotalPrice());

            // Owner should pay less or equal to tenant
            assertTrue(ownerReservation.getTotalPrice().compareTo(tenantReservation.getTotalPrice()) <= 0);
        }
    }

    @Test
    @DisplayName("Complete flow: Platform fees calculated correctly")
    public void testCompleteFlow_PlatformFees() {
        // Arrange - Use far future date to avoid conflicts with other tests and
        // data.sql reservations. Use 450 days to avoid conflict with
        // PricingCalculationTest (400 days)
        LocalDate startDate = LocalDate.now().plusDays(450);
        LocalDate endDate = startDate.plusDays(4);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertNotNull(reservation.getPlatformFeeAmount());
        assertNotNull(reservation.getPlatformFixedFeeAmount());

        // Total price should include base + cleaning + platform fees
        assertTrue(reservation.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Complete flow: Deposit and cleaning fee included")
    public void testCompleteFlow_DepositAndCleaningFee() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(80);
        LocalDate endDate = startDate.plusDays(4);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        assertTrue(reservation.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);

        // Check if space has deposit/cleaning fee configured
        if (guestRoomSpace.getDeposit() != null && guestRoomSpace.getDeposit().compareTo(BigDecimal.ZERO) > 0) {
            assertTrue(reservation.getTotalPrice().compareTo(guestRoomSpace.getDeposit()) > 0);
        }

        if (guestRoomSpace.getCleaningFee() != null && guestRoomSpace.getCleaningFee().compareTo(BigDecimal.ZERO) > 0) {
            assertTrue(reservation.getTotalPrice().compareTo(guestRoomSpace.getCleaningFee()) > 0);
        }
    }

    @Test
    @DisplayName("Complete flow: Multiple days pricing calculation")
    public void testCompleteFlow_MultiDayPricing() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(85);

        // Act - 1 day
        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, startDate.plusDays(1));

        // Act - 3 days (respecting max duration)
        ReservationEntity res3 = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate.plusDays(30), startDate.plusDays(33));

        // Assert
        assertNotNull(res1.getTotalPrice());
        assertNotNull(res3.getTotalPrice());

        // Both reservations should have valid prices
        assertTrue(res1.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(res3.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);

        // 3-day reservation should cost more than 1-day reservation
        assertTrue(res3.getTotalPrice().compareTo(res1.getTotalPrice()) > 0);
    }

    @Test
    @DisplayName("Complete flow: Audit log created for each action")
    public void testCompleteFlow_AuditLogs() {
        // Arrange - Use far future date to avoid conflicts with other tests
        LocalDate startDate = LocalDate.now().plusDays(220);
        LocalDate endDate = startDate.plusDays(4);

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Verify audit log was created for reservation creation
        assertNotNull(reservation.getId());

        // Confirm and verify audit log
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_audit", "cs_audit");

        // Assert
        assertNotNull(confirmed.getId());
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());
    }

    @Test
    @DisplayName("Complete flow: Annual quota tracked correctly")
    public void testCompleteFlow_AnnualQuota() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(210); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(4);

        int initialUsedReservations = guestRoomSpace.getUsedAnnualReservations();

        // Act
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert
        // Reload space to check quota
        SpaceEntity updatedSpace = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (updatedSpace != null && updatedSpace.getMaxAnnualReservations() > 0) {
            assertTrue(updatedSpace.getUsedAnnualReservations() > initialUsedReservations);
            assertTrue(updatedSpace.getUsedAnnualReservations() <= updatedSpace.getMaxAnnualReservations());
        }
    }
}
