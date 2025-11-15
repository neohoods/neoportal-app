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
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, null, null, null, null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 5); // At least our 5 test units
        assertTrue(result.getContent().size() <= 20);
    }

    @Test
    @DisplayName("Get units directory filtered by type FLAT")
    public void testGetUnitsDirectory_FilterByTypeFlat_ReturnsOnlyFlats() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, UnitTypeForEntity.FLAT, null, null, null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 2); // At least our 2 flat units
        assertTrue(result.getContent().stream().allMatch(u -> u.getType().getValue().equals("FLAT")));
    }

    @Test
    @DisplayName("Get units directory filtered by type GARAGE")
    public void testGetUnitsDirectory_FilterByTypeGarage_ReturnsOnlyGarages() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, UnitTypeForEntity.GARAGE, null, null, null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1); // At least our 1 garage unit
        assertTrue(result.getContent().stream().allMatch(u -> u.getType().getValue().equals("GARAGE")));
    }

    @Test
    @DisplayName("Get units directory filtered by search term")
    public void testGetUnitsDirectory_FilterBySearch_ReturnsMatchingUnits() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, null, "Appartement", null, null).block();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 2); // At least our 2 "Appartement" units
        assertTrue(result.getContent().stream().allMatch(u -> u.getName().contains("Appartement")));
    }

    @Test
    @DisplayName("Get units directory filtered by user membership")
    public void testGetUnitsDirectory_FilterByUserId_ReturnsUserUnits() {
        // Act
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, null, null, testUser.getId(), null).block();

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
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 20, UnitTypeForEntity.FLAT, "101", null, null).block();

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
        PaginatedUnits page1 = unitsService.getUnitsDirectoryPaginated(0, 2, null, null, null, null).block();
        
        // Act - second page
        PaginatedUnits page2 = unitsService.getUnitsDirectoryPaginated(1, 2, null, null, null, null).block();

        // Assert
        assertNotNull(page1);
        assertNotNull(page2);
        assertEquals(2, page1.getContent().size());
        assertTrue(page2.getContent().size() >= 0);
        assertTrue(page1.getNumber() == 0);
        assertTrue(page2.getNumber() == 1);
    }

    @Test
    @DisplayName("Get units directory with onlyOccupied filter returns only units with members")
    public void testGetUnitsDirectory_OnlyOccupiedFilter_ReturnsOnlyOccupiedUnits() {
        // Arrange - Create an empty unit (no members)
        UnitEntity emptyUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement Vide")
                .type(UnitTypeForEntity.FLAT)
                .build();
        final UnitEntity savedEmptyUnit = unitRepository.save(emptyUnit);
        final UUID emptyUnitId = savedEmptyUnit.getId();
        final UUID flatUnit1Id = flatUnit1.getId();

        // flatUnit1 already has members (from setUp), so it should be included
        // emptyUnit has no members, so it should be excluded when onlyOccupied=true

        // Act - Get all units without filter
        PaginatedUnits allUnits = unitsService.getUnitsDirectoryPaginated(0, 100, null, null, null, null).block();
        
        // Act - Get units with onlyOccupied=true
        PaginatedUnits occupiedUnits = unitsService.getUnitsDirectoryPaginated(0, 100, null, null, null, true).block();

        // Assert
        assertNotNull(allUnits);
        assertNotNull(occupiedUnits);
        
        // All units should include both occupied and empty units
        assertTrue(allUnits.getContent().stream()
                .anyMatch(u -> u.getId().equals(flatUnit1Id)));
        assertTrue(allUnits.getContent().stream()
                .anyMatch(u -> u.getId().equals(emptyUnitId)));
        
        // Only occupied units should exclude empty unit
        assertTrue(occupiedUnits.getContent().stream()
                .anyMatch(u -> u.getId().equals(flatUnit1Id)));
        assertTrue(occupiedUnits.getContent().stream()
                .noneMatch(u -> u.getId().equals(emptyUnitId)));
        
        // Total count should be less when filtering
        assertTrue(occupiedUnits.getTotalElements() < allUnits.getTotalElements());
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

    @Test
    @DisplayName("Units are sorted correctly: FLAT > COMMERCIAL > GARAGE > PARKING > OTHER, then by name")
    public void testGetUnitsDirectory_SortingOrder_IsCorrect() {
        // Arrange - Create units with specific names to test sorting
        UnitEntity flatA001 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement A001")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatA001 = unitRepository.save(flatA001);

        UnitEntity flatA002 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement A002")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatA002 = unitRepository.save(flatA002);

        UnitEntity flatA101 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement A101")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatA101 = unitRepository.save(flatA101);

        UnitEntity flatA102 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement A102")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatA102 = unitRepository.save(flatA102);

        UnitEntity flatB001 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement B001")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatB001 = unitRepository.save(flatB001);

        UnitEntity flatB002 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement B002")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatB002 = unitRepository.save(flatB002);

        UnitEntity flatB101 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement B101")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatB101 = unitRepository.save(flatB101);

        UnitEntity flatB102 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement B102")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatB102 = unitRepository.save(flatB102);

        UnitEntity flatC001 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement C001")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatC001 = unitRepository.save(flatC001);

        UnitEntity flatC002 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement C002")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatC002 = unitRepository.save(flatC002);

        UnitEntity flatC101 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement C101")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatC101 = unitRepository.save(flatC101);

        UnitEntity flatC102 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Appartement C102")
                .type(UnitTypeForEntity.FLAT)
                .build();
        flatC102 = unitRepository.save(flatC102);

        UnitEntity garage001 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Garage 001")
                .type(UnitTypeForEntity.GARAGE)
                .build();
        garage001 = unitRepository.save(garage001);

        UnitEntity garage009 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Garage 009")
                .type(UnitTypeForEntity.GARAGE)
                .build();
        garage009 = unitRepository.save(garage009);

        UnitEntity garage101 = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Garage 101")
                .type(UnitTypeForEntity.GARAGE)
                .build();
        garage101 = unitRepository.save(garage101);

        UnitEntity commercialUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Local Commercial 1")
                .type(UnitTypeForEntity.COMMERCIAL)
                .build();
        commercialUnit = unitRepository.save(commercialUnit);

        // Act - Get all units
        PaginatedUnits result = unitsService.getUnitsDirectoryPaginated(0, 100, null, null, null, null).block();

        // Assert
        assertNotNull(result);
        List<Unit> units = result.getContent();
        
        // Find indices of our test units
        int flatA001Idx = findUnitIndex(units, flatA001.getId());
        int flatA002Idx = findUnitIndex(units, flatA002.getId());
        int flatA101Idx = findUnitIndex(units, flatA101.getId());
        int flatA102Idx = findUnitIndex(units, flatA102.getId());
        int flatB001Idx = findUnitIndex(units, flatB001.getId());
        int flatB002Idx = findUnitIndex(units, flatB002.getId());
        int flatB101Idx = findUnitIndex(units, flatB101.getId());
        int flatB102Idx = findUnitIndex(units, flatB102.getId());
        int flatC001Idx = findUnitIndex(units, flatC001.getId());
        int flatC002Idx = findUnitIndex(units, flatC002.getId());
        int flatC101Idx = findUnitIndex(units, flatC101.getId());
        int flatC102Idx = findUnitIndex(units, flatC102.getId());
        int commercialIdx = findUnitIndex(units, commercialUnit.getId());
        int garage001Idx = findUnitIndex(units, garage001.getId());
        int garage009Idx = findUnitIndex(units, garage009.getId());
        int garage101Idx = findUnitIndex(units, garage101.getId());

        // Verify type order: FLAT units come before COMMERCIAL, which come before GARAGE
        assertTrue(flatA001Idx < commercialIdx, "FLAT should come before COMMERCIAL");
        assertTrue(commercialIdx < garage001Idx, "COMMERCIAL should come before GARAGE");
        
        // Verify FLAT units are sorted by name: A001 < A002 < A101 < A102 < B001 < B002 < B101 < B102 < C001 < C002 < C101 < C102
        assertTrue(flatA001Idx < flatA002Idx, "A001 should come before A002");
        assertTrue(flatA002Idx < flatA101Idx, "A002 should come before A101");
        assertTrue(flatA101Idx < flatA102Idx, "A101 should come before A102");
        assertTrue(flatA102Idx < flatB001Idx, "A102 should come before B001");
        assertTrue(flatB001Idx < flatB002Idx, "B001 should come before B002");
        assertTrue(flatB002Idx < flatB101Idx, "B002 should come before B101");
        assertTrue(flatB101Idx < flatB102Idx, "B101 should come before B102");
        assertTrue(flatB102Idx < flatC001Idx, "B102 should come before C001");
        assertTrue(flatC001Idx < flatC002Idx, "C001 should come before C002");
        assertTrue(flatC002Idx < flatC101Idx, "C002 should come before C101");
        assertTrue(flatC101Idx < flatC102Idx, "C101 should come before C102");
        
        // Verify GARAGE units are sorted by name: 001 < 009 < 101
        assertTrue(garage001Idx < garage009Idx, "Garage 001 should come before Garage 009");
        assertTrue(garage009Idx < garage101Idx, "Garage 009 should come before Garage 101");
    }

    private int findUnitIndex(List<Unit> units, UUID unitId) {
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).getId().equals(unitId)) {
                return i;
            }
        }
        return -1;
    }
}

