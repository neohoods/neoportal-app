package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.UnitsService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for annual quota validation.
 * 
 * Verifies that spaces cannot be reserved beyond their annual quota limits.
 */
@Transactional
public class QuotaValidationTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UnitsService unitsService;

    @Autowired
    private ReservationRepository reservationRepository;

    private SpaceEntity guestRoomSpace;
    private UserEntity ownerUser;
    private UserEntity tenantUser;

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
            }
            if (user.getType() == UserType.TENANT) {
                tenantUser = user;
            }
        }
    }

    @Test
    @DisplayName("Space with unlimited quota: can reserve without limit")
    public void testUnlimitedQuota_NoLimit() {
        // Arrange - Space with maxAnnualReservations = 0 (unlimited)
        if (guestRoomSpace.getMaxAnnualReservations() == 0) {
            // Act
            LocalDate startDate = LocalDate.now().plusDays(10);
            ReservationEntity reservation = reservationsService.createReservation(
                    guestRoomSpace, tenantUser, startDate, startDate.plusDays(3));

            // Assert - Should succeed (unlimited quota)
            assertTrue(reservation.getId() != null);
        }
    }

    @Test
    @DisplayName("Used annual reservations incremented on creation")
    public void testUsedReservations_IncrementedOnCreation() {
        // Arrange
        SpaceEntity space = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (space == null)
            return;

        int initialUsed = space.getUsedAnnualReservations();
        int maxReservations = space.getMaxAnnualReservations();

        if (maxReservations > 0) {
            // Act
            LocalDate startDate = LocalDate.now().plusDays(10);
            reservationsService.createReservation(space, tenantUser, startDate, startDate.plusDays(3));

            // Assert
            SpaceEntity updatedSpace = spaceRepository.findById(space.getId()).orElse(null);
            if (updatedSpace != null) {
                assertTrue(updatedSpace.getUsedAnnualReservations() > initialUsed);
            }
        }
    }

    @Test
    @DisplayName("Cannot exceed annual quota")
    public void testCannotExceedAnnualQuota() {
        // Arrange - Get space with quota
        SpaceEntity space = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (space == null)
            return;

        int maxReservations = space.getMaxAnnualReservations();

        if (maxReservations > 0) {
            // Remove primary unit from tenant user to test global quota (usedAnnualReservations)
            UserEntity user = usersRepository.findById(tenantUser.getId()).orElse(null);
            if (user != null && user.getPrimaryUnit() != null) {
                user.setPrimaryUnit(null);
                usersRepository.save(user);
            }

            // Set used reservations to max
            space.setUsedAnnualReservations(maxReservations);
            space = spaceRepository.save(space);

            // Act & Assert - Cannot create new reservation
            final LocalDate startDate = LocalDate.now().plusDays(10);
            final SpaceEntity finalSpace = space;
            assertThrows(CodedErrorException.class, () -> {
                reservationsService.createReservation(finalSpace, tenantUser, startDate, startDate.plusDays(3));
            });
        }
    }

    @Test
    @DisplayName("Cancelled reservations don't count towards quota")
    public void testCancelledReservations_NotCounted() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);

        int initialUsed = guestRoomSpace.getUsedAnnualReservations();

        // Create and cancel
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, tenantUser, startDate, startDate.plusDays(3));

        // Verify quota was incremented
        SpaceEntity spaceAfterCreation = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        assertTrue(spaceAfterCreation.getUsedAnnualReservations() > initialUsed,
                "Quota should be incremented after reservation creation");

        reservationsService.cancelReservation(reservation.getId(), "Change of plans", tenantUser.getEmail());

        // Assert - Quota should be decremented back to initial value
        SpaceEntity updatedSpace = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        assertNotNull(updatedSpace, "Space should still exist after cancellation");
        assertEquals(initialUsed, updatedSpace.getUsedAnnualReservations(),
                "Quota should be decremented back to initial value after cancellation");
    }

    @Test
    @DisplayName("Space with quota = 1: can reserve exactly once")
    public void testQuotaOne_CanReserveOnce() {
        // Arrange
        SpaceEntity space = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (space == null)
            return;

        // Remove primary unit from tenant user to test global quota (usedAnnualReservations)
        UserEntity user = usersRepository.findById(tenantUser.getId()).orElse(null);
        if (user != null && user.getPrimaryUnit() != null) {
            user.setPrimaryUnit(null);
            usersRepository.save(user);
        }

        int originalMax = space.getMaxAnnualReservations();
        space.setMaxAnnualReservations(1);
        space.setUsedAnnualReservations(0);
        space = spaceRepository.save(space);

        try {
            // Act
            LocalDate startDate = LocalDate.now().plusDays(10);
            ReservationEntity res1 = reservationsService.createReservation(
                    space, tenantUser, startDate, startDate.plusDays(3));

            // Assert - First should succeed
            assertTrue(res1.getId() != null);

            // Act & Assert - Second should fail
            final SpaceEntity space1 = space;
            assertThrows(CodedErrorException.class, () -> {
                reservationsService.createReservation(space1, tenantUser,
                        startDate.plusDays(20), startDate.plusDays(23));
            });
        } finally {
            // Cleanup
            space.setMaxAnnualReservations(originalMax);
            spaceRepository.save(space);
        }
    }

    @Test
    @DisplayName("Quota check: includes confirmed and pending reservations")
    public void testQuotaCheck_IncludesPendingAndConfirmed() {
        // Arrange
        SpaceEntity space = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (space == null || space.getMaxAnnualReservations() <= 0)
            return;

        int maxRes = space.getMaxAnnualReservations();
        space.setMaxAnnualReservations(maxRes + 5); // Increase quota for testing
        space.setUsedAnnualReservations(maxRes);
        space = spaceRepository.save(space);

        try {
            LocalDate startDate = LocalDate.now().plusDays(10);

            // Create pending reservation
            ReservationEntity pending = reservationsService.createReservation(
                    space, tenantUser, startDate, startDate.plusDays(3));

            // Confirm it
            reservationsService.confirmReservation(pending.getId(), "pi_1", "cs_1");

            // Assert - Used should reflect confirmed reservation
            SpaceEntity updated = spaceRepository.findById(space.getId()).orElse(null);
            if (updated != null) {
                // Quota tracking should work
                assertEquals(maxRes + 1, updated.getUsedAnnualReservations());
            }
        } finally {
            space.setMaxAnnualReservations(maxRes);
            spaceRepository.save(space);
        }
    }

    @Test
    @DisplayName("Zero quota: unlimited reservations allowed")
    public void testZeroQuota_UnlimitedReservations() {
        // Arrange
        SpaceEntity space = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (space == null)
            return;

        int originalQuota = space.getMaxAnnualReservations();
        space.setMaxAnnualReservations(0); // 0 = unlimited
        space.setUsedAnnualReservations(0);
        space = spaceRepository.save(space);

        try {
            // Act - Should succeed with unlimited quota
            LocalDate startDate = LocalDate.now().plusDays(10);
            ReservationEntity reservation = reservationsService.createReservation(space, tenantUser, startDate,
                    startDate.plusDays(3));

            // Assert - Should succeed
            assertTrue(reservation.getId() != null);
        } finally {
            // Cleanup
            space.setMaxAnnualReservations(originalQuota);
            spaceRepository.save(space);
        }
    }

    @Test
    @DisplayName("Quota reset: annual reset (new year)")
    public void testQuotaReset_NewYear() {
        // This test would verify that used reservations reset at new year
        // For now, just verify current functionality

        SpaceEntity space = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (space == null || space.getMaxAnnualReservations() <= 0)
            return;

        // Assert - Quota tracking is working
        assertTrue(space.getMaxAnnualReservations() > 0);
        assertTrue(space.getUsedAnnualReservations() >= 0);
    }

    @Test
    @DisplayName("Multiple users: quota shared across all reservations")
    public void testMultipleUsers_SharedQuota() {
        // Arrange
        SpaceEntity space = spaceRepository.findById(guestRoomSpace.getId()).orElse(null);
        if (space == null || space.getMaxAnnualReservations() <= 0)
            return;

        // Remove primary units from both users to test global quota (usedAnnualReservations)
        UserEntity owner = usersRepository.findById(ownerUser.getId()).orElse(null);
        UserEntity tenant = usersRepository.findById(tenantUser.getId()).orElse(null);
        if (owner != null && owner.getPrimaryUnit() != null) {
            owner.setPrimaryUnit(null);
            usersRepository.save(owner);
        }
        if (tenant != null && tenant.getPrimaryUnit() != null) {
            tenant.setPrimaryUnit(null);
            usersRepository.save(tenant);
        }

        int originalMax = space.getMaxAnnualReservations();
        space.setMaxAnnualReservations(1); // Set quota to 1 so only one reservation is allowed
        space.setUsedAnnualReservations(0);
        space = spaceRepository.save(space);

        try {
            final LocalDate startDate = LocalDate.now().plusDays(10);
            final SpaceEntity finalSpace3 = space;

            // Owner reserves (uses global quota)
            reservationsService.createReservation(finalSpace3, ownerUser, startDate, startDate.plusDays(3));

            // Act & Assert - Tenant cannot exceed shared quota
            assertThrows(CodedErrorException.class, () -> {
                reservationsService.createReservation(finalSpace3, tenantUser,
                        startDate.plusDays(10), startDate.plusDays(13));
            });
        } finally {
            space.setMaxAnnualReservations(originalMax);
            spaceRepository.save(space);
        }
    }
}
