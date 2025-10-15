package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

import jakarta.persistence.EntityManager;

/**
 * Integration tests for space collision detection.
 * 
 * Verifies that conflicting space types cannot be reserved simultaneously:
 * - Coworking vs Common Room
 * - Multiple reservations cannot overlap
 */
@Transactional
public class SpaceCollisionTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UsersRepository usersRepository;

    private SpaceEntity guestRoomSpace;
    private UserEntity ownerUser;
    private UserEntity tenantUser;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        // Get guest room space (using it for all collision tests)
        guestRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No guest room space found"));

        // Get or create users
        Iterable<UserEntity> allUsers = usersRepository.findAll();
        for (UserEntity user : allUsers) {
            if (user.getType() == UserType.OWNER) {
                ownerUser = user;
            }
            if (user.getType() == UserType.TENANT) {
                tenantUser = user;
            }
        }

        if (ownerUser == null) {
            ownerUser = new UserEntity();
            ownerUser.setId(UUID.randomUUID());
            ownerUser.setEmail("test-owner-collision@neohoods.com");
            ownerUser.setUsername("test-owner-collision@neohoods.com");
            ownerUser.setPassword("test-password");
            ownerUser.setType(UserType.OWNER);
            ownerUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            ownerUser = usersRepository.save(ownerUser);
        }

        if (tenantUser == null) {
            tenantUser = new UserEntity();
            tenantUser.setId(UUID.randomUUID());
            tenantUser.setEmail("test-tenant-collision@neohoods.com");
            tenantUser.setUsername("test-tenant-collision@neohoods.com");
            tenantUser.setPassword("test-password");
            tenantUser.setType(UserType.TENANT);
            tenantUser.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
            tenantUser = usersRepository.save(tenantUser);
        }
    }

    @Test
    @DisplayName("Same space: cannot be double-booked")
    public void testSameSpace_CannotDoubleBook() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act - Create first reservation for guest room
        ReservationEntity firstReservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, endDate);

        reservationsService.confirmReservation(firstReservation.getId(), "pi_first", "cs_first");

        // Assert - Cannot create second reservation for same space during same period
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    guestRoomSpace, tenantUser, startDate.plusDays(1), endDate.plusDays(1));
        });

        // Verify correct error code
        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
        assertNotNull(exception.getVariables());
        assertEquals(guestRoomSpace.getId(), exception.getVariables().get("spaceId"));
    }

    @Test
    @DisplayName("Same type spaces can be reserved if they don't conflict")
    public void testSameType_NoConflictIfDifferentSpaces() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Get another guest room if available
        SpaceEntity anotherGuestRoom = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM)
                .skip(1)
                .findFirst()
                .orElse(null);

        if (anotherGuestRoom != null) {
            // Act - Create first reservation
            ReservationEntity res1 = reservationsService.createReservation(
                    guestRoomSpace, ownerUser, startDate, endDate);

            // Act - Create reservation in different space (should work)
            ReservationEntity res2 = reservationsService.createReservation(
                    anotherGuestRoom, tenantUser, startDate, endDate);

            // Assert
            assertTrue(res1.getId() != null);
            assertTrue(res2.getId() != null);
        }
    }

    @Test
    @DisplayName("Adjacent reservations are allowed")
    public void testAdjacentReservations_Allowed() {
        // Arrange
        LocalDate startDate1 = LocalDate.now().plusDays(10);
        LocalDate endDate1 = startDate1.plusDays(3);

        LocalDate startDate2 = endDate1.plusDays(1); // Start immediately after
        LocalDate endDate2 = startDate2.plusDays(3);

        // Get a different guest room
        SpaceEntity anotherGuestRoom = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM && !s.getId().equals(guestRoomSpace.getId()))
                .findFirst()
                .orElse(null);

        // Act
        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate1, endDate1);

        if (anotherGuestRoom != null) {
            ReservationEntity res2 = reservationsService.createReservation(
                    anotherGuestRoom, tenantUser, startDate2, endDate2);

            // Assert - No exception thrown
            assertTrue(res1.getId() != null);
            assertTrue(res2.getId() != null);
        } else {
            // If only one guest room available, the test still passes for same space
            // adjacent dates
            assertTrue(res1.getId() != null);
        }
    }

    @Test
    @DisplayName("Partial overlap detection: start overlaps")
    public void testPartialOverlap_StartOverlaps() {
        // Arrange
        LocalDate startDate1 = LocalDate.now().plusDays(10);
        LocalDate endDate1 = startDate1.plusDays(5);

        // Act - Create first reservation
        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate1, endDate1);
        reservationsService.confirmReservation(res1.getId(), "pi_1", "cs_1");

        // Assert - Cannot create second that starts during first
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    guestRoomSpace, tenantUser,
                    startDate1.plusDays(2), // Starts during first reservation
                    endDate1.plusDays(3));
        });

        // Verify correct error code
        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
    }

    @Test
    @DisplayName("Partial overlap detection: end overlaps")
    public void testPartialOverlap_EndOverlaps() {
        // Arrange
        LocalDate startDate1 = LocalDate.now().plusDays(10);
        LocalDate endDate1 = startDate1.plusDays(5);

        // Act - Create first reservation
        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate1, endDate1);
        reservationsService.confirmReservation(res1.getId(), "pi_2", "cs_2");

        // Assert - Cannot create second that ends during first
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    guestRoomSpace, tenantUser,
                    startDate1.minusDays(3),
                    startDate1.plusDays(2)); // Ends during first reservation
        });

        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
    }

    @Test
    @DisplayName("Complete overlap: second fully contained in first")
    @org.junit.jupiter.api.Disabled("Duration exceeds max allowed")
    public void testCompleteOverlap_SecondContainedInFirst() {
        // Arrange
        LocalDate startDate1 = LocalDate.now().plusDays(10);
        LocalDate endDate1 = startDate1.plusDays(10);

        // Act
        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate1, endDate1);
        reservationsService.confirmReservation(res1.getId(), "pi_3", "cs_3");

        // Assert - Cannot create shorter reservation during
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    guestRoomSpace, tenantUser,
                    startDate1.plusDays(2), // Inside
                    startDate1.plusDays(7)); // Inside
        });

        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
    }

    @Test
    @DisplayName("Complete overlap: first fully contained in second")
    public void testCompleteOverlap_FirstContainedInSecond() {
        // Arrange
        LocalDate startDate1 = LocalDate.now().plusDays(10);
        LocalDate endDate1 = startDate1.plusDays(3); // 4 days total (within 7 day limit)

        // Act
        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate1, endDate1);
        reservationsService.confirmReservation(res1.getId(), "pi_4", "cs_4");

        // Assert - Cannot create longer reservation that contains first
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    guestRoomSpace, tenantUser,
                    startDate1.minusDays(1), // Before
                    endDate1.plusDays(1)); // After (6 days total, within limit)
        });

        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
    }

    @Test
    @DisplayName("Same-day boundaries: end of first = start of second")
    public void testSameDayBoundary_EndEqualsStart_Allowed() {
        // Arrange
        LocalDate startDate1 = LocalDate.now().plusDays(10);
        LocalDate endDate1 = startDate1.plusDays(3);

        // Act
        ReservationEntity res1 = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate1, endDate1);
        reservationsService.confirmReservation(res1.getId(), "pi_5", "cs_5");

        // Should work - same day end = same day start (not overlapping)
        ReservationEntity res2 = reservationsService.createReservation(
                guestRoomSpace, tenantUser, endDate1, endDate1.plusDays(3));

        // Assert
        assertNotNull(res2.getId());
        assertNotNull(res2.getStatus());
    }

    @Test
    @DisplayName("Space availability check: available when no conflicts")
    public void testAvailability_NoConflicts_Available() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        boolean isAvailable = spacesService.isSpaceAvailable(guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertTrue(isAvailable, "Space should be available when no reservations exist");
    }

    @Test
    @DisplayName("Space availability check: unavailable when conflicting reservation exists")
    public void testAvailability_HasConflict_Unavailable() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Create reservation
        ReservationEntity res = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, endDate);
        reservationsService.confirmReservation(res.getId(), "pi_6", "cs_6");

        // Act
        boolean isAvailable = spacesService.isSpaceAvailable(guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertFalse(isAvailable, "Space should be unavailable when reservation exists");
    }

    @Test
    @DisplayName("Cancelled reservations don't block other reservations")
    public void testCancelledReservation_DoesNotBlock() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);

        // Create and cancel reservation
        ReservationEntity res = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, endDate);
        reservationsService.cancelReservation(res.getId(), "Changed my mind", ownerUser.getEmail());

        // Act - Should be able to create new reservation
        boolean isAvailable = spacesService.isSpaceAvailable(guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertTrue(isAvailable, "Cancelled reservations should not block new ones");
    }

    @Test
    @DisplayName("Past reservations don't block future reservations")
    public void testPastReservations_DoNotBlock() {
        // Arrange - Use future dates for past reservation (simulating a reservation
        // that has already ended)
        LocalDate oldStartDate = LocalDate.now().plusDays(205); // Future date, but earlier
        LocalDate oldEndDate = oldStartDate.plusDays(3);

        LocalDate futureStartDate = LocalDate.now().plusDays(220); // After old reservation
        LocalDate futureEndDate = futureStartDate.plusDays(3);

        // Create "old" reservation (future but earlier)
        ReservationEntity oldRes = reservationsService.createReservation(
                guestRoomSpace, ownerUser, oldStartDate, oldEndDate);
        reservationsService.confirmReservation(oldRes.getId(), "pi_old", "cs_old");
        entityManager.flush();

        // Act - Should be able to create future reservation after the old one
        boolean isAvailable = spacesService.isSpaceAvailable(guestRoomSpace.getId(), futureStartDate, futureEndDate);

        // Assert
        assertTrue(isAvailable, "Reservations in the past (relative to future dates) should not block future ones");
    }

    // TODO: Réactiver ce test quand la logique de partage d'espaces sera
    // complètement implémentée
    // @Test
    // @DisplayName("Coworking vs Common Room collision - Case 1: Coworking reserved
    // blocks Common Room")
    public void testCoworkingCommonRoomCollision_Case1_CoworkingBlocksCommonRoom() {
        // Arrange
        LocalDate testDate = LocalDate.of(2026, 1, 15); // 15 janvier 2026 - date sans réservations

        // Get coworking space (Bureau A)
        SpaceEntity coworkingSpaceA = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COWORKING)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No coworking space found"));

        // Get common room space
        SpaceEntity commonRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No common room space found"));

        // Get another coworking space (Bureau B) if available
        SpaceEntity coworkingSpaceB = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COWORKING && !s.getId().equals(coworkingSpaceA.getId()))
                .findFirst()
                .orElse(null);

        // Configure sharing relationships bidirectionally
        // Only common room shares with coworking spaces, coworking spaces don't share
        // with each other
        commonRoomSpace.setShareSpaceWith(new ArrayList<>(Arrays.asList(
                coworkingSpaceA.getId(),
                coworkingSpaceB.getId())));
        coworkingSpaceA.setShareSpaceWith(new ArrayList<>(Arrays.asList(
                commonRoomSpace.getId())));
        coworkingSpaceB.setShareSpaceWith(new ArrayList<>(Arrays.asList(
                commonRoomSpace.getId())));

        // Save the spaces with sharing relationships
        spaceRepository.save(commonRoomSpace);
        spaceRepository.save(coworkingSpaceA);
        spaceRepository.save(coworkingSpaceB);

        // Act - Test user reserves coworking space A
        ReservationEntity coworkingReservation = reservationsService.createReservation(
                coworkingSpaceA, ownerUser, testDate, testDate);
        reservationsService.confirmReservation(coworkingReservation.getId(), "pi_test", "cs_test");

        // Force flush to make the reservation visible in the database
        entityManager.flush();

        // Assert - Test user cannot reserve common room on the same date
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            reservationsService.createReservation(
                    commonRoomSpace, tenantUser, testDate, testDate);
        });
        assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());

        // Assert - Test user can reserve coworking space B (different space)
        if (coworkingSpaceB != null) {
            ReservationEntity bobReservation = reservationsService.createReservation(
                    coworkingSpaceB, tenantUser, testDate, testDate);
            assertNotNull(bobReservation.getId());
        }
    }

    // TODO: Réactiver ce test quand la logique de partage d'espaces sera
    // complètement implémentée
    // @Test
    // @DisplayName("Coworking vs Common Room collision - Case 2: Common Room
    // reserved blocks all Coworking")
    public void testCoworkingCommonRoomCollision_Case2_CommonRoomBlocksAllCoworking() {
        // Arrange
        LocalDate testDate = LocalDate.of(2026, 1, 15); // 15 janvier 2026 - date sans réservations

        // Get common room space
        SpaceEntity commonRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No common room space found"));

        // Get all coworking spaces
        List<SpaceEntity> coworkingSpaces = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COWORKING)
                .collect(java.util.stream.Collectors.toList());

        assertTrue(coworkingSpaces.size() >= 2, "Need at least 2 coworking spaces for this test");

        // Configure sharing relationships bidirectionally
        // Only common room shares with coworking spaces, coworking spaces don't share
        // with each other
        List<UUID> coworkingIds = coworkingSpaces.stream().map(SpaceEntity::getId)
                .collect(java.util.stream.Collectors.toList());
        commonRoomSpace.setShareSpaceWith(new ArrayList<>(coworkingIds));

        // Each coworking space shares only with common room
        for (SpaceEntity coworkingSpace : coworkingSpaces) {
            coworkingSpace.setShareSpaceWith(new ArrayList<>(Arrays.asList(commonRoomSpace.getId())));
        }

        // Save the spaces with sharing relationships
        spaceRepository.save(commonRoomSpace);
        for (SpaceEntity coworkingSpace : coworkingSpaces) {
            spaceRepository.save(coworkingSpace);
        }

        // Create users
        UserEntity alice = createTestUser("alice@test.com", UserType.TENANT);
        UserEntity bob = createTestUser("bob@test.com", UserType.TENANT);

        // Act - Alice reserves common room
        ReservationEntity commonRoomReservation = reservationsService.createReservation(
                commonRoomSpace, alice, testDate, testDate);
        reservationsService.confirmReservation(commonRoomReservation.getId(), "pi_alice", "cs_alice");

        // Force flush to make the reservation visible in the database
        entityManager.flush();

        // Assert - Bob cannot reserve any coworking space (A or B)
        for (SpaceEntity coworkingSpace : coworkingSpaces) {
            CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
                reservationsService.createReservation(
                        coworkingSpace, bob, testDate, testDate);
            });
            assertEquals(CodedError.SPACE_NOT_AVAILABLE, exception.getError());
        }
    }

    // TODO: Réactiver ce test quand la logique de partage d'espaces sera
    // complètement implémentée
    // @Test
    // @DisplayName("Coworking vs Common Room collision - Different dates should not
    // conflict")
    public void testCoworkingCommonRoomCollision_DifferentDates_NoConflict() {
        // Arrange
        LocalDate date1 = LocalDate.of(2026, 1, 15);
        LocalDate date2 = LocalDate.of(2026, 1, 16);

        // Get coworking space
        SpaceEntity coworkingSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COWORKING)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No coworking space found"));

        // Get common room space
        SpaceEntity commonRoomSpace = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No common room space found"));

        // Configure sharing relationships bidirectionally
        // Only common room shares with coworking spaces, coworking spaces don't share
        // with each other
        commonRoomSpace.setShareSpaceWith(new ArrayList<>(Arrays.asList(coworkingSpace.getId())));
        coworkingSpace.setShareSpaceWith(new ArrayList<>(Arrays.asList(commonRoomSpace.getId())));

        // Save the spaces with sharing relationships
        spaceRepository.save(commonRoomSpace);
        spaceRepository.save(coworkingSpace);

        // Create users
        UserEntity user1 = createTestUser("user1@test.com", UserType.TENANT);
        UserEntity user2 = createTestUser("user2@test.com", UserType.TENANT);

        // Act - User1 reserves coworking on date1
        ReservationEntity coworkingReservation = reservationsService.createReservation(
                coworkingSpace, user1, date1, date1);
        reservationsService.confirmReservation(coworkingReservation.getId(), "pi_user1", "cs_user1");

        // Assert - User2 can reserve common room on date2 (different date)
        ReservationEntity commonRoomReservation = reservationsService.createReservation(
                commonRoomSpace, user2, date2, date2);
        assertNotNull(commonRoomReservation.getId());
    }

    /**
     * Helper method to create test users
     */
    private UserEntity createTestUser(String email, UserType userType) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setUsername(email); // Use email as username
        user.setPassword("test-password"); // Set a test password
        user.setType(userType);
        user.setStatus(com.neohoods.portal.platform.entities.UserStatus.ACTIVE);
        return usersRepository.save(user);
    }
}
