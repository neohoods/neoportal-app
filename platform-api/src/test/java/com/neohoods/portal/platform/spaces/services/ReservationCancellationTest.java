package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for reservation cancellation functionality.
 */
@Transactional
public class ReservationCancellationTest extends BaseIntegrationTest {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private UsersRepository usersRepository;

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
    @DisplayName("Cancel reservation - cancellation reason stored")
    public void testCancelReservation_WithReason() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, startDate.plusDays(3));

        // Act
        reservationsService.cancelReservation(reservation.getId(), "Change of plans", ownerUser.getEmail());

        // Assert
        ReservationEntity cancelled = reservationsService.getReservationById(reservation.getId());
        assertEquals(ReservationStatusForEntity.CANCELLED, cancelled.getStatus());
        assertEquals("Change of plans", cancelled.getCancellationReason());
        assertNotNull(cancelled.getCancelledAt());
    }

    @Test
    @DisplayName("Cancel reservation - already cancelled should error")
    public void testCancelReservation_AlreadyCancelled() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(10);
        ReservationEntity reservation = reservationsService.createReservation(
                guestRoomSpace, ownerUser, startDate, startDate.plusDays(3));
        reservationsService.cancelReservation(reservation.getId(), "First reason", ownerUser.getEmail());

        // Act & Assert
        assertThrows(Exception.class, () -> {
            reservationsService.cancelReservation(reservation.getId(), "Second reason", ownerUser.getEmail());
        });
    }
}
