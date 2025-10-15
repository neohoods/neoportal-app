package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for space availability checks.
 */
@Transactional
public class SpaceAvailabilityTest extends BaseIntegrationTest {

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private SpaceEntity guestRoomSpace;
    private UserEntity ownerUser;

    @BeforeEach
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
    }

    @Test
    @DisplayName("Space available when no overlapping reservations")
    public void testCheckAvailability_Available() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(20);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        boolean isAvailable = spacesService.isSpaceAvailable(guestRoomSpace.getId(), startDate, endDate);

        // Assert
        assertTrue(isAvailable);
    }

    @Test
    @DisplayName("Space unavailable when overlapping reservation exists")
    public void testCheckAvailability_Unavailable_Overlapping() {
        // Arrange - Create a reservation
        LocalDate existingStart = LocalDate.now().plusDays(10);
        LocalDate existingEnd = LocalDate.now().plusDays(13);
        reservationsService.createReservation(guestRoomSpace, ownerUser, existingStart, existingEnd);

        // Try to reserve overlapping dates
        LocalDate newStart = LocalDate.now().plusDays(11);
        LocalDate newEnd = LocalDate.now().plusDays(14);

        // Act
        boolean isAvailable = spacesService.isSpaceAvailable(guestRoomSpace.getId(), newStart, newEnd);

        // Assert
        assertFalse(isAvailable);
    }

    @Test
    @DisplayName("Adjacent reservations are OK")
    public void testCheckAvailability_Available_AdjacentReservations() {
        // Arrange - Create a reservation
        LocalDate existingStart = LocalDate.now().plusDays(10);
        LocalDate existingEnd = LocalDate.now().plusDays(13);
        reservationsService.createReservation(guestRoomSpace, ownerUser, existingStart, existingEnd);

        // Try to reserve adjacent dates (immediately after)
        LocalDate newStart = LocalDate.now().plusDays(14);
        LocalDate newEnd = LocalDate.now().plusDays(16);

        // Act
        boolean isAvailable = spacesService.isSpaceAvailable(guestRoomSpace.getId(), newStart, newEnd);

        // Assert
        assertTrue(isAvailable);
    }
}









