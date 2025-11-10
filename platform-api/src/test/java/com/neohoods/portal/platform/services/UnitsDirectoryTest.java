package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.ResidenceRole;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UnitMemberRole;
import com.neohoods.portal.platform.entities.UnitTypeForEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserStatus;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.model.PaginatedUnits;
import com.neohoods.portal.platform.model.Unit;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;

/**
 * Integration tests for Units directory and related parking/garages functionality.
 * 
 * Tests cover:
 * - Directory pagination
 * - Filtering by unit type
 * - Filtering by search term
 * - Filtering by user membership
 * - Related parking/garages retrieval
 */
@Transactional
public class UnitsDirectoryTest extends BaseIntegrationTest {

    @Autowired
    private UnitsService unitsService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UnitMemberRepository unitMemberRepository;

    @Autowired
    private UsersRepository usersRepository;

    private UnitEntity flatUnit1;
    private UnitEntity flatUnit2;
    private UnitEntity garageUnit;
    private UnitEntity parkingUnit;
    private UnitEntity commercialUnit;
    private UserEntity testUser;
    private UserEntity testUser2;

    @BeforeEach
    public void setUp() {
        // Create test users
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("testuser@test.com");
        testUser.setUsername("testuser");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setPassword("password123");
        testUser.setType(UserType.TENANT);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = usersRepository.save(testUser);

        testUser2 = new UserEntity();
        testUser2.setId(UUID.randomUUID());
        testUser2.setEmail("testuser2@test.com");
        testUser2.setUsername("testuser2");
        testUser2.setFirstName("Test");
        testUser2.setLastName("User2");
        testUser2.setPassword("password123");
        testUser2.setType(UserType.TENANT);
        testUser2.setStatus(UserStatus.ACTIVE);
        testUser2 = usersRepository.save(testUser2);

        // Create units of different types
        flatUnit1 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement 101")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatUnit1 = unitRepository.save(flatUnit1);

        flatUnit2 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement 202")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatUnit2 = unitRepository.save(flatUnit2);

        garageUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Garage A1")
                .type(UnitTypeForEntity.GARAGE)
                .build();
        garageUnit = unitRepository.save(garageUnit);

        parkingUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Parking 12")
                .type(UnitTypeForEntity.PARKING)
                .build();
        parkingUnit = unitRepository.save(parkingUnit);

        commercialUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Boutique 1")
                .type(UnitTypeForEntity.COMMERCIAL)
                .build();
        commercialUnit = unitRepository.save(commercialUnit);

        // Add testUser as member of flatUnit1 and garageUnit (as PROPRIETAIRE)
        UnitMemberEntity member1 = UnitMemberEntity.builder()
                .unit(flatUnit1)
                .user(testUser)
                .role(UnitMemberRole.MEMBER)
                .residenceRole(ResidenceRole.TENANT)
                .build();
        unitMemberRepository.save(member1);

        UnitMemberEntity member2 = UnitMemberEntity.builder()
                .unit(garageUnit)
                .user(testUser)
                .role(UnitMemberRole.ADMIN)
                .residenceRole(ResidenceRole.PROPRIETAIRE)
                .build();
        unitMemberRepository.save(member2);

        // Set flatUnit1 as primary unit for testUser
        testUser.setPrimaryUnit(flatUnit1);
        usersRepository.save(testUser);
    }

    @Test
    @DisplayName("Get all units directory without filters")
    public void testGetUnitsDirectory_NoFilters_ReturnsAllUnits() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, null, null, null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 5); // At least our 5 test units
        assertTrue(result.getContent().size() <= 20);
    }

    @Test
    @DisplayName("Get units directory filtered by type FLAT")
    public void testGetUnitsDirectory_FilterByTypeFlat_ReturnsOnlyFlats() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, UnitTypeForEntity.FLAT, null, null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 2); // At least our 2 flat units
        assertTrue(result.getContent().stream().allMatch(u -> u.getType().getValue().equals("FLAT")));
    }

    @Test
    @DisplayName("Get units directory filtered by type GARAGE")
    public void testGetUnitsDirectory_FilterByTypeGarage_ReturnsOnlyGarages() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, UnitTypeForEntity.GARAGE, null, null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1); // At least our 1 garage unit
        assertTrue(result.getContent().stream().allMatch(u -> u.getType().getValue().equals("GARAGE")));
    }

    @Test
    @DisplayName("Get units directory filtered by search term")
    public void testGetUnitsDirectory_FilterBySearch_ReturnsMatchingUnits() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, null, "Appartement", null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 2); // At least our 2 "Appartement" units
        assertTrue(result.getContent().stream().allMatch(u -> u.getName().contains("Appartement")));
    }

    @Test
    @DisplayName("Get units directory filtered by user membership")
    public void testGetUnitsDirectory_FilterByUserId_ReturnsUserUnits() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, null, null, testUser.getId()).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 2); // testUser is member of flatUnit1 and garageUnit
        List<Unit> units = result.getContent();
        assertTrue(units.stream().anyMatch(u -> u.getId().equals(flatUnit1.getId())));
        assertTrue(units.stream().anyMatch(u -> u.getId().equals(garageUnit.getId())));
    }

    @Test
    @DisplayName("Get units directory filtered by type and search")
    public void testGetUnitsDirectory_FilterByTypeAndSearch_ReturnsMatchingUnits() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, UnitTypeForEntity.FLAT, "101", null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1); // At least flatUnit1
        assertTrue(result.getContent().stream().allMatch(u -> 
            u.getType().getValue().equals("FLAT") && u.getName().contains("101")));
    }

    @Test
    @DisplayName("Get units directory with pagination")
    public void testGetUnitsDirectory_WithPagination_ReturnsCorrectPage() {
        // Act - first page
        PaginatedUnits page1 = unitsService.getUnitsDirectoryPaginated(0, 2, null, null, null).block();
        
        // Act - second page
        PaginatedUnits page2 = unitsService.getUnitsDirectoryPaginated(1, 2, null, null, null).block();

        // Assert
        assertNotNull(page1);
        assertNotNull(page2);
        assertEquals(2, page1.getContent().size());
        assertTrue(page2.getContent().size() >= 0);
        assertTrue(page1.getNumber() == 0);
        assertTrue(page2.getNumber() == 1);
    }

    @Test
    @DisplayName("Get related parking/garages for user with primary FLAT")
    public void testGetRelatedParkingGarages_UserWithPrimaryFlat_ReturnsRelatedUnits() {
        // Act
        List<Unit> related = unitsService.getRelatedParkingGaragesForUser(testUser.getId()).block();

        // Assert
        assertNotNull(related);
        assertTrue(related.stream().anyMatch(u -> u.getId().equals(garageUnit.getId())));
        // Should only return GARAGE or PARKING units where user is PROPRIETAIRE
        assertTrue(related.stream().allMatch(u -> 
            (u.getType().getValue().equals("GARAGE") || u.getType().getValue().equals("PARKING"))));
    }

    @Test
    @DisplayName("Get related parking/garages for user without primary FLAT returns empty")
    public void testGetRelatedParkingGarages_UserWithoutPrimaryFlat_ReturnsEmpty() {
        // Arrange - user without primary unit
        // Act
        List<Unit> related = unitsService.getRelatedParkingGaragesForUser(testUser2.getId()).block();

        // Assert
        assertNotNull(related);
        assertTrue(related.isEmpty());
    }

    @Test
    @DisplayName("Get related parking/garages only returns PROPRIETAIRE units")
    public void testGetRelatedParkingGarages_OnlyProprietaire_ReturnsCorrectUnits() {
        // Arrange - add testUser as TENANT to parkingUnit (should not be returned)
        UnitMemberEntity tenantMember = UnitMemberEntity.builder()
                .unit(parkingUnit)
                .user(testUser)
                .role(UnitMemberRole.MEMBER)
                .residenceRole(ResidenceRole.TENANT)
                .build();
        unitMemberRepository.save(tenantMember);

        // Act
        List<Unit> related = unitsService.getRelatedParkingGaragesForUser(testUser.getId()).block();

        // Assert
        assertNotNull(related);
        // Should only return garageUnit (PROPRIETAIRE), not parkingUnit (TENANT)
        assertTrue(related.stream().anyMatch(u -> u.getId().equals(garageUnit.getId())));
        assertTrue(related.stream().noneMatch(u -> u.getId().equals(parkingUnit.getId())));
    }

    @Test
    @DisplayName("Get related parking/garages includes both GARAGE and PARKING types")
    public void testGetRelatedParkingGarages_IncludesBothTypes_ReturnsBoth() {
        // Arrange - add testUser as PROPRIETAIRE to parkingUnit
        UnitMemberEntity parkingMember = UnitMemberEntity.builder()
                .unit(parkingUnit)
                .user(testUser)
                .role(UnitMemberRole.ADMIN)
                .residenceRole(ResidenceRole.PROPRIETAIRE)
                .build();
        unitMemberRepository.save(parkingMember);

        // Act
        List<Unit> related = unitsService.getRelatedParkingGaragesForUser(testUser.getId()).block();

        // Assert
        assertNotNull(related);
        assertTrue(related.stream().anyMatch(u -> u.getId().equals(garageUnit.getId())));
        assertTrue(related.stream().anyMatch(u -> u.getId().equals(parkingUnit.getId())));
        assertTrue(related.stream().allMatch(u -> 
            u.getType().getValue().equals("GARAGE") || u.getType().getValue().equals("PARKING")));
    }
}

