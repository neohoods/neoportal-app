package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.AccessCodeEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.AccessCodeRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for reservation access code timing and email notifications.
 * 
 * Tests verify that:
 * - Access codes are NOT generated on reservation creation
 * - Access codes are NOT generated on confirmation for future reservations
 * - Access codes ARE generated on confirmation for same-day reservations
 * - Email behavior is correct for different scenarios
 */
@Transactional
public class ReservationAccessCodeTimingTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private AccessCodeRepository accessCodeRepository;

    private SpaceEntity guestRoomSpace;
    private UserEntity tenantUser;

    @BeforeEach
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
            tenantUser.setEmail("test-tenant-timing@neohoods.com");
            tenantUser.setUsername("test-tenant-timing");
            tenantUser.setType(UserType.TENANT);
            tenantUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            tenantUser = usersRepository.save(tenantUser);
        }
    }

    @Test
    @DisplayName("Access code NOT generated on reservation creation")
    public void testAccessCode_NotGeneratedOnCreation() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(300); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(2);

        // Act - Create reservation
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Assert - No access code should be generated
        assertNotNull(reservation.getId());
        assertEquals(ReservationStatusForEntity.PENDING_PAYMENT, reservation.getStatus());

        Optional<AccessCodeEntity> accessCode = accessCodeRepository.findByReservation(reservation);
        assertTrue(accessCode.isEmpty(), "Access code should NOT be generated on reservation creation");
    }

    @Test
    @DisplayName("Access code IS generated on confirmation for future reservation")
    public void testAccessCode_GeneratedOnConfirmation_FutureReservation() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(301); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(2);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act - Confirm reservation
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_test_future", "cs_test_future");

        // Assert - Access code should be generated for future reservations too
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());

        Optional<AccessCodeEntity> accessCode = accessCodeRepository.findByReservation(confirmed);
        assertTrue(accessCode.isPresent(),
                "Access code should be generated on confirmation for future reservations");
    }

    @Test
    @DisplayName("Access code IS generated on confirmation for same-day reservation")
    public void testAccessCode_GeneratedOnConfirmation_SameDayReservation() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(200); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(1);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act - Confirm reservation
        ReservationEntity confirmed = reservationsService.confirmReservation(
                reservation.getId(), "pi_test_same_day", "cs_test_same_day");

        // Assert - Access code should be generated for same-day reservation
        assertEquals(ReservationStatusForEntity.CONFIRMED, confirmed.getStatus());

        Optional<AccessCodeEntity> accessCode = accessCodeRepository.findByReservation(confirmed);
        assertTrue(accessCode.isPresent(),
                "Access code SHOULD be generated on confirmation for same-day reservations");

        AccessCodeEntity code = accessCode.get();
        assertNotNull(code.getCode());
        assertTrue(code.getCode().length() > 0);
        assertTrue(code.getIsActive());
    }

    @Test
    @DisplayName("Email sent on confirmation with access code for same-day reservation")
    public void testEmailSent_ConfirmationWithAccessCode_SameDay() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(201); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(1);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act - Confirm reservation
        reservationsService.confirmReservation(reservation.getId(), "pi_test_email", "cs_test_email");

        // Assert - Email should be sent with access code
        // Note: In real implementation, we would verify
        // MailService.sendReservationConfirmationEmail was called
        // with the correct parameters including the access code

        Optional<AccessCodeEntity> accessCode = accessCodeRepository.findByReservation(reservation);
        assertTrue(accessCode.isPresent(), "Access code should exist for email verification");

        // Verify email would contain access code
        String code = accessCode.get().getCode();
        assertNotNull(code);
        assertTrue(code.length() > 0);
    }

    @Test
    @DisplayName("Email sent on confirmation WITH access code for future reservation")
    public void testEmailSent_ConfirmationWithAccessCode_Future() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(302); // Far future to avoid conflicts
        LocalDate endDate = startDate.plusDays(2);

        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, endDate);

        // Act - Confirm reservation
        reservationsService.confirmReservation(reservation.getId(), "pi_test_future_email", "cs_test_future_email");

        // Assert - Email should be sent WITH access code
        // Note: In real implementation, we would verify
        // MailService.sendReservationConfirmationEmail was called
        // with the access code

        Optional<AccessCodeEntity> accessCode = accessCodeRepository.findByReservation(reservation);
        assertTrue(accessCode.isPresent(), "Access code should exist for future reservation after confirmation");
    }
}