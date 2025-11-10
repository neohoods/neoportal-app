package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.UnitMember;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;

/**
 * Integration tests for updateMemberResidenceRole functionality.
 * 
 * Tests cover:
 * - Successful update with valid data
 * - Unit not found error
 * - Member not found error
 * - Non-admin user trying to update (when updatedBy is provided)
 * - Null residence role validation
 * - All residence role values (PROPRIETAIRE, BAILLEUR, MANAGER, TENANT)
 * - Residence role persistence and retrieval
 */
@Transactional
public class UpdateMemberResidenceRoleTest extends BaseIntegrationTest {

    @Autowired
    private UnitsService unitsService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UnitMemberRepository unitMemberRepository;

    @Autowired
    private UsersRepository usersRepository;

    private UnitEntity testUnit;
    private UserEntity adminUser;
    private UserEntity memberUser;
    private UserEntity nonMemberUser;
    private UnitMemberEntity memberEntity;

    @BeforeEach
    public void setUp() {
        // Create admin user
        adminUser = new UserEntity();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin@test.com");
        adminUser.setUsername("admin");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setPassword("password123");
        adminUser.setType(UserType.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser = usersRepository.save(adminUser);

        // Create member user
        memberUser = new UserEntity();
        memberUser.setId(UUID.randomUUID());
        memberUser.setEmail("member@test.com");
        memberUser.setUsername("member");
        memberUser.setFirstName("Member");
        memberUser.setLastName("User");
        memberUser.setPassword("password123");
        memberUser.setType(UserType.TENANT);
        memberUser.setStatus(UserStatus.ACTIVE);
        memberUser = usersRepository.save(memberUser);

        // Create non-member user
        nonMemberUser = new UserEntity();
        nonMemberUser.setId(UUID.randomUUID());
        nonMemberUser.setEmail("nonmember@test.com");
        nonMemberUser.setUsername("nonmember");
        nonMemberUser.setFirstName("NonMember");
        nonMemberUser.setLastName("User");
        nonMemberUser.setPassword("password123");
        nonMemberUser.setType(UserType.TENANT);
        nonMemberUser.setStatus(UserStatus.ACTIVE);
        nonMemberUser = usersRepository.save(nonMemberUser);

        // Create test unit
        testUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Test Unit")
                .type(UnitTypeForEntity.FLAT)
                .build();
        testUnit = unitRepository.save(testUnit);

        // Add admin as admin of the unit
        UnitMemberEntity adminMember = UnitMemberEntity.builder()
                .unit(testUnit)
                .user(adminUser)
                .role(UnitMemberRole.ADMIN)
                .residenceRole(ResidenceRole.PROPRIETAIRE)
                .build();
        unitMemberRepository.save(adminMember);

        // Add member as regular member with TENANT role
        memberEntity = UnitMemberEntity.builder()
                .unit(testUnit)
                .user(memberUser)
                .role(UnitMemberRole.MEMBER)
                .residenceRole(ResidenceRole.TENANT)
                .build();
        memberEntity = unitMemberRepository.save(memberEntity);
    }

    @Test
    @DisplayName("Successfully update residence role to PROPRIETAIRE")
    public void testUpdateResidenceRole_ToProprietaire_Success() {
        // Act
        UnitMember result = unitsService.updateMemberResidenceRole(
                testUnit.getId(),
                memberUser.getId(),
                ResidenceRole.PROPRIETAIRE,
                adminUser.getId()
        ).block();

        // Assert
        assertNotNull(result);
        assertEquals(ResidenceRole.PROPRIETAIRE.name(), result.getResidenceRole().getValue());

        // Verify persistence
        UnitMemberEntity saved = unitMemberRepository.findByUnitIdAndUserId(
                testUnit.getId(), memberUser.getId()).orElseThrow();
        assertEquals(ResidenceRole.PROPRIETAIRE, saved.getResidenceRole());
    }

    @Test
    @DisplayName("Successfully update residence role to BAILLEUR")
    public void testUpdateResidenceRole_ToBailleur_Success() {
        // Act
        UnitMember result = unitsService.updateMemberResidenceRole(
                testUnit.getId(),
                memberUser.getId(),
                ResidenceRole.BAILLEUR,
                adminUser.getId()
        ).block();

        // Assert
        assertNotNull(result);
        assertEquals(ResidenceRole.BAILLEUR.name(), result.getResidenceRole().getValue());

        // Verify persistence
        UnitMemberEntity saved = unitMemberRepository.findByUnitIdAndUserId(
                testUnit.getId(), memberUser.getId()).orElseThrow();
        assertEquals(ResidenceRole.BAILLEUR, saved.getResidenceRole());
    }

    @Test
    @DisplayName("Successfully update residence role to MANAGER")
    public void testUpdateResidenceRole_ToManager_Success() {
        // Act
        UnitMember result = unitsService.updateMemberResidenceRole(
                testUnit.getId(),
                memberUser.getId(),
                ResidenceRole.MANAGER,
                adminUser.getId()
        ).block();

        // Assert
        assertNotNull(result);
        assertEquals(ResidenceRole.MANAGER.name(), result.getResidenceRole().getValue());

        // Verify persistence
        UnitMemberEntity saved = unitMemberRepository.findByUnitIdAndUserId(
                testUnit.getId(), memberUser.getId()).orElseThrow();
        assertEquals(ResidenceRole.MANAGER, saved.getResidenceRole());
    }

    @Test
    @DisplayName("Successfully update residence role to TENANT")
    public void testUpdateResidenceRole_ToTenant_Success() {
        // First set to PROPRIETAIRE
        memberEntity.setResidenceRole(ResidenceRole.PROPRIETAIRE);
        unitMemberRepository.save(memberEntity);

        // Act - update back to TENANT
        UnitMember result = unitsService.updateMemberResidenceRole(
                testUnit.getId(),
                memberUser.getId(),
                ResidenceRole.TENANT,
                adminUser.getId()
        ).block();

        // Assert
        assertNotNull(result);
        assertEquals(ResidenceRole.TENANT.name(), result.getResidenceRole().getValue());

        // Verify persistence
        UnitMemberEntity saved = unitMemberRepository.findByUnitIdAndUserId(
                testUnit.getId(), memberUser.getId()).orElseThrow();
        assertEquals(ResidenceRole.TENANT, saved.getResidenceRole());
    }

    @Test
    @DisplayName("Update with null updatedBy (admin API) should succeed")
    public void testUpdateResidenceRole_WithNullUpdatedBy_Success() {
        // Act - null updatedBy means admin API call
        UnitMember result = unitsService.updateMemberResidenceRole(
                testUnit.getId(),
                memberUser.getId(),
                ResidenceRole.MANAGER,
                null
        ).block();

        // Assert
        assertNotNull(result);
        assertEquals(ResidenceRole.MANAGER.name(), result.getResidenceRole().getValue());
    }

    @Test
    @DisplayName("Update should fail when unit not found")
    public void testUpdateResidenceRole_UnitNotFound_ThrowsException() {
        // Arrange
        UUID nonExistentUnitId = UUID.randomUUID();

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            unitsService.updateMemberResidenceRole(
                    nonExistentUnitId,
                    memberUser.getId(),
                    ResidenceRole.PROPRIETAIRE,
                    adminUser.getId()
            ).block();
        });

        // The exception might be wrapped in a RuntimeException
        CodedErrorException codedException = null;
        if (exception instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception;
        } else if (exception.getCause() instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception.getCause();
        }
        assertNotNull(codedException, "Expected CodedErrorException but got: " + exception.getClass());
        assertEquals(CodedError.UNIT_NOT_FOUND, codedException.getError());
    }

    @Test
    @DisplayName("Update should fail when member not found")
    public void testUpdateResidenceRole_MemberNotFound_ThrowsException() {
        // Arrange
        UUID nonExistentUserId = UUID.randomUUID();

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            unitsService.updateMemberResidenceRole(
                    testUnit.getId(),
                    nonExistentUserId,
                    ResidenceRole.PROPRIETAIRE,
                    adminUser.getId()
            ).block();
        });

        // The exception might be wrapped
        CodedErrorException codedException = null;
        if (exception instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception;
        } else if (exception.getCause() instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception.getCause();
        }
        assertNotNull(codedException, "Expected CodedErrorException but got: " + exception.getClass());
        assertEquals(CodedError.UNIT_MEMBER_NOT_FOUND, codedException.getError());
    }

    @Test
    @DisplayName("Update should fail when non-admin user tries to update")
    public void testUpdateResidenceRole_NonAdminUser_ThrowsException() {
        // Arrange - memberUser is not admin
        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            unitsService.updateMemberResidenceRole(
                    testUnit.getId(),
                    memberUser.getId(),
                    ResidenceRole.PROPRIETAIRE,
                    memberUser.getId() // memberUser trying to update
            ).block();
        });

        // The exception might be wrapped
        CodedErrorException codedException = null;
        if (exception instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception;
        } else if (exception.getCause() instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception.getCause();
        }
        assertNotNull(codedException, "Expected CodedErrorException but got: " + exception.getClass());
        assertEquals(CodedError.USER_NOT_ADMIN_OF_UNIT, codedException.getError());
    }

    @Test
    @DisplayName("Update should fail when residence role is null")
    public void testUpdateResidenceRole_NullResidenceRole_ThrowsException() {
        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            unitsService.updateMemberResidenceRole(
                    testUnit.getId(),
                    memberUser.getId(),
                    null,
                    adminUser.getId()
            ).block();
        });

        // The exception might be wrapped
        CodedErrorException codedException = null;
        if (exception instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception;
        } else if (exception.getCause() instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception.getCause();
        }
        assertNotNull(codedException, "Expected CodedErrorException but got: " + exception.getClass());
        assertEquals(CodedError.INVALID_INPUT, codedException.getError());
    }

    @Test
    @DisplayName("Update should persist correctly after refresh")
    public void testUpdateResidenceRole_PersistenceAfterRefresh() {
        // Act
        UnitMember result = unitsService.updateMemberResidenceRole(
                testUnit.getId(),
                memberUser.getId(),
                ResidenceRole.BAILLEUR,
                adminUser.getId()
        ).block();

        // Assert - verify the result
        assertNotNull(result);
        assertEquals(ResidenceRole.BAILLEUR.name(), result.getResidenceRole().getValue());

        // Verify by reloading from database
        unitMemberRepository.flush();
        UnitMemberEntity reloaded = unitMemberRepository.findByUnitIdAndUserId(
                testUnit.getId(), memberUser.getId()).orElseThrow();
        assertEquals(ResidenceRole.BAILLEUR, reloaded.getResidenceRole());

        // Verify the toUnitMember conversion works correctly
        UnitMember converted = reloaded.toUnitMember();
        assertNotNull(converted.getResidenceRole());
        assertEquals(ResidenceRole.BAILLEUR.name(), converted.getResidenceRole().getValue());
    }

    @Test
    @DisplayName("Update should fail when user is not a member of the unit")
    public void testUpdateResidenceRole_NonMemberUser_ThrowsException() {
        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            unitsService.updateMemberResidenceRole(
                    testUnit.getId(),
                    nonMemberUser.getId(),
                    ResidenceRole.PROPRIETAIRE,
                    adminUser.getId()
            ).block();
        });

        // The exception might be wrapped
        CodedErrorException codedException = null;
        if (exception instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception;
        } else if (exception.getCause() instanceof CodedErrorException) {
            codedException = (CodedErrorException) exception.getCause();
        }
        assertNotNull(codedException, "Expected CodedErrorException but got: " + exception.getClass());
        assertEquals(CodedError.UNIT_MEMBER_NOT_FOUND, codedException.getError());
    }
}

