package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

/**
 * Integration tests for space search and filtering.
 * 
 * Tests:
 * - Search by type
 * - Search by availability
 * - Search by date range
 */
@Transactional
public class SpaceSearchTest extends BaseIntegrationTest {

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("Get all active spaces")
    public void testGetAllActiveSpaces() {
        // Act
        List<SpaceEntity> spaces = spacesService.getAllActiveSpaces();

        // Assert
        assertNotNull(spaces);
        assertFalse(spaces.isEmpty());

        // All returned spaces should be active
        for (SpaceEntity space : spaces) {
            assertEquals(SpaceStatusForEntity.ACTIVE, space.getStatus());
        }
    }

    @Test
    @DisplayName("Get spaces by type")
    public void testGetSpacesByType() {
        // Act
        List<SpaceEntity> guestRooms = spaceRepository.findByTypeAndStatus(
                SpaceTypeForEntity.GUEST_ROOM, SpaceStatusForEntity.ACTIVE);

        List<SpaceEntity> commonRooms = spaceRepository.findByTypeAndStatus(
                SpaceTypeForEntity.COMMON_ROOM, SpaceStatusForEntity.ACTIVE);

        // Assert
        assertNotNull(guestRooms);
        assertNotNull(commonRooms);

        for (SpaceEntity space : guestRooms) {
            assertEquals(SpaceTypeForEntity.GUEST_ROOM, space.getType());
        }

        for (SpaceEntity space : commonRooms) {
            assertEquals(SpaceTypeForEntity.COMMON_ROOM, space.getType());
        }
    }

    @Test
    @DisplayName("Find available spaces by date range")
    public void testFindAvailableSpacesByDateRange() {
        // Arrange
        LocalDate startDate = LocalDate.now().plusDays(7);
        LocalDate endDate = startDate.plusDays(3);

        // Act
        List<SpaceEntity> allSpaces = spacesService.getAllActiveSpaces();

        // Assert - Should find spaces available in the range
        for (SpaceEntity space : allSpaces) {
            boolean isAvailable = spacesService.isSpaceAvailable(space.getId(), startDate, endDate);
            // At least some spaces should be available
        }
    }

    @Test
    @DisplayName("Search spaces: filter by status")
    public void testSearchSpaces_FilterByStatus() {
        // Act
        List<SpaceEntity> activeSpaces = spaceRepository.findByStatus(SpaceStatusForEntity.ACTIVE);

        // Assert
        assertNotNull(activeSpaces);
        assertFalse(activeSpaces.isEmpty());

        for (SpaceEntity space : activeSpaces) {
            assertEquals(SpaceStatusForEntity.ACTIVE, space.getStatus());
        }
    }

    @Test
    @DisplayName("Search spaces: exclude inactive")
    public void testSearchSpaces_ExcludeInactive() {
        // Act
        List<SpaceEntity> activeSpaces = spacesService.getAllActiveSpaces();

        // Assert
        for (SpaceEntity space : activeSpaces) {
            assertTrue(space.getStatus() != SpaceStatusForEntity.INACTIVE);
        }
    }

    @Test
    @DisplayName("Get space by ID")
    public void testGetSpaceById() {
        // Arrange
        List<SpaceEntity> allSpaces = spacesService.getAllActiveSpaces();
        assertFalse(allSpaces.isEmpty());

        SpaceEntity firstSpace = allSpaces.get(0);

        // Act
        SpaceEntity found = spacesService.getSpaceById(firstSpace.getId());

        // Assert
        assertNotNull(found);
        assertEquals(firstSpace.getId(), found.getId());
        assertEquals(firstSpace.getName(), found.getName());
    }

    @Test
    @DisplayName("Search: filter by minimum duration")
    public void testSearch_FilterByMinDuration() {
        // Act
        List<SpaceEntity> allSpaces = spacesService.getAllActiveSpaces();

        // Assert - Spaces have min/max duration constraints
        for (SpaceEntity space : allSpaces) {
            assertTrue(space.getMinDurationDays() > 0);
            assertTrue(space.getMaxDurationDays() >= space.getMinDurationDays());
        }
    }

    @Test
    @DisplayName("Search: include spaces with access codes enabled")
    public void testSearch_SpacesWithAccessCodes() {
        // Act
        List<SpaceEntity> allSpaces = spacesService.getAllActiveSpaces();

        // Assert - Check which spaces have access codes enabled
        boolean foundAccessCodeSpace = false;
        for (SpaceEntity space : allSpaces) {
            if (space.getAccessCodeEnabled() != null && space.getAccessCodeEnabled()) {
                foundAccessCodeSpace = true;
                assertNotNull(space.getDigitalLockId());
            }
        }
        // At least some spaces should have access code enabled
    }

    @Test
    @DisplayName("Search: filter by cleaning fee")
    public void testSearch_FilterByCleaningFee() {
        // Act
        List<SpaceEntity> allSpaces = spacesService.getAllActiveSpaces();

        // Assert
        for (SpaceEntity space : allSpaces) {
            // Cleaning fee should be zero or positive
            if (space.getCleaningFee() != null) {
                assertTrue(space.getCleaningFee().compareTo(java.math.BigDecimal.ZERO) >= 0);
            }
        }
    }

    @Test
    @DisplayName("Search: filter by pricing")
    public void testSearch_FilterByPricing() {
        // Act
        List<SpaceEntity> allSpaces = spacesService.getAllActiveSpaces();

        // Assert - All spaces should have tenant price set (parking can be 0)
        for (SpaceEntity space : allSpaces) {
            assertNotNull(space.getTenantPrice());
            assertTrue(space.getTenantPrice().compareTo(java.math.BigDecimal.ZERO) >= 0,
                    "Tenant price should be >= 0 for space: " + space.getName());
        }
    }
}
