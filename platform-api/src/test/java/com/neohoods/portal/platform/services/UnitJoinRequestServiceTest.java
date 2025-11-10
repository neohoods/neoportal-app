package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
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
import com.neohoods.portal.platform.entities.UnitJoinRequestEntity;
import com.neohoods.portal.platform.entities.UnitJoinRequestStatus;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UnitMemberRole;
import com.neohoods.portal.platform.entities.UnitTypeForEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserStatus;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.repositories.UnitJoinRequestRepository;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;

/**
 * Integration tests for UnitJoinRequestService functionality.
 * 
 * Tests cover:
 * - Creating join requests
 * - Auto-adding user as ADMIN when unit is empty
 * - Approving join requests
 * - Rejecting join requests
 * - Permission checks
 * - Duplicate request prevention
 * - Already member validation
 * - Notification sending
 */
@Transactional
public class UnitJoinRequestServiceTest extends BaseIntegrationTest {

    @Autowired
    private UnitJoinRequestService joinRequestService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UnitMemberRepository unitMemberRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UnitJoinRequestRepository joinRequestRepository;

    private UnitEntity testUnit;
    private UnitEntity emptyUnit;
    private UserEntity adminUser;
    private UserEntity requestingUser;
    private UserEntity globalAdminUser;
    private UserEntity regularUser;
    private UnitMemberEntity adminMember;

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

        // Create global admin user
        globalAdminUser = new UserEntity();
        globalAdminUser.setId(UUID.randomUUID());
        globalAdminUser.setEmail("globaladmin@test.com");
        globalAdminUser.setUsername("globaladmin");
        globalAdminUser.setFirstName("Global");
        globalAdminUser.setLastName("Admin");
        globalAdminUser.setPassword("password123");
        globalAdminUser.setType(UserType.ADMIN);
        globalAdminUser.setStatus(UserStatus.ACTIVE);
        globalAdminUser = usersRepository.save(globalAdminUser);

        // Create requesting user
        requestingUser = new UserEntity();
        requestingUser.setId(UUID.randomUUID());
        requestingUser.setEmail("requester@test.com");
        requestingUser.setUsername("requester");
        requestingUser.setFirstName("Requesting");
        requestingUser.setLastName("User");
        requestingUser.setPassword("password123");
        requestingUser.setType(UserType.TENANT);
        requestingUser.setStatus(UserStatus.ACTIVE);
        requestingUser = usersRepository.save(requestingUser);

        // Create regular user
        regularUser = new UserEntity();
        regularUser.setId(UUID.randomUUID());
        regularUser.setEmail("regular@test.com");
        regularUser.setUsername("regular");
        regularUser.setFirstName("Regular");
        regularUser.setLastName("User");
        regularUser.setPassword("password123");
        regularUser.setType(UserType.TENANT);
        regularUser.setStatus(UserStatus.ACTIVE);
        regularUser = usersRepository.save(regularUser);

        // Create test unit with admin
        testUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Test Unit")
                .type(UnitTypeForEntity.FLAT)
                .build();
        testUnit = unitRepository.save(testUnit);

        // Add admin as admin of the unit
        adminMember = UnitMemberEntity.builder()
                .unit(testUnit)
                .user(adminUser)
                .role(UnitMemberRole.ADMIN)
                .residenceRole(ResidenceRole.PROPRIETAIRE)
                .build();
        unitMemberRepository.save(adminMember);

        // Create empty unit (no members)
        emptyUnit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name("Empty Unit")
                .type(UnitTypeForEntity.FLAT)
                .build();
        emptyUnit = unitRepository.save(emptyUnit);
    }

    @Test
    @DisplayName("Successfully create join request")
    public void testCreateJoinRequest_Success() {
        // Act
        UnitJoinRequestEntity request = joinRequestService.createJoinRequest(
                testUnit.getId(),
                requestingUser.getId(),
                "Please let me join"
        );

        // Assert
        assertNotNull(request);
        assertNotNull(request.getId());
        assertEquals(testUnit.getId(), request.getUnit().getId());
        assertEquals(requestingUser.getId(), request.getRequestedBy().getId());
        assertEquals(UnitJoinRequestStatus.PENDING, request.getStatus());
        assertEquals("Please let me join", request.getMessage());
        assertNotNull(request.getCreatedAt());

        // Verify persistence
        UnitJoinRequestEntity saved = joinRequestRepository.findById(request.getId()).orElseThrow();
        assertEquals(UnitJoinRequestStatus.PENDING, saved.getStatus());
    }

    @Test
    @DisplayName("Create join request with empty unit should add user directly as ADMIN")
    public void testCreateJoinRequest_EmptyUnit_AddsUserAsAdmin() {
        // Act
        UnitJoinRequestEntity request = joinRequestService.createJoinRequest(
                emptyUnit.getId(),
                requestingUser.getId(),
                null
        );

        // Assert - should return null (user was added directly)
        assertNull(request);

        // Verify user was added as ADMIN
        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(
                emptyUnit.getId(), requestingUser.getId()).orElseThrow();
        assertEquals(UnitMemberRole.ADMIN, member.getRole());
        assertEquals(ResidenceRole.PROPRIETAIRE, member.getResidenceRole());

        // Verify primary unit was set
        UserEntity reloadedUser = usersRepository.findById(requestingUser.getId()).orElseThrow();
        assertEquals(emptyUnit.getId(), reloadedUser.getPrimaryUnit().getId());
    }

    @Test
    @DisplayName("Create join request should fail when user is already a member")
    public void testCreateJoinRequest_UserAlreadyMember_ThrowsException() {
        // Arrange - add requesting user as member
        UnitMemberEntity existingMember = UnitMemberEntity.builder()
                .unit(testUnit)
                .user(requestingUser)
                .role(UnitMemberRole.MEMBER)
                .residenceRole(ResidenceRole.TENANT)
                .build();
        unitMemberRepository.save(existingMember);

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.createJoinRequest(
                    testUnit.getId(),
                    requestingUser.getId(),
                    "Test"
            );
        });

        assertEquals(CodedError.USER_ALREADY_MEMBER, exception.getError());
    }

    @Test
    @DisplayName("Create join request should fail when pending request already exists")
    public void testCreateJoinRequest_DuplicateRequest_ThrowsException() {
        // Arrange - create existing pending request
        UnitJoinRequestEntity existingRequest = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("First request")
                .createdAt(OffsetDateTime.now())
                .build();
        joinRequestRepository.save(existingRequest);

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.createJoinRequest(
                    testUnit.getId(),
                    requestingUser.getId(),
                    "Second request"
            );
        });

        assertEquals(CodedError.INVALID_INPUT, exception.getError());
    }

    @Test
    @DisplayName("Create join request should fail when unit not found")
    public void testCreateJoinRequest_UnitNotFound_ThrowsException() {
        // Arrange
        UUID nonExistentUnitId = UUID.randomUUID();

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.createJoinRequest(
                    nonExistentUnitId,
                    requestingUser.getId(),
                    "Test"
            );
        });

        assertEquals(CodedError.UNIT_NOT_FOUND, exception.getError());
    }

    @Test
    @DisplayName("Create join request should fail when user not found")
    public void testCreateJoinRequest_UserNotFound_ThrowsException() {
        // Arrange
        UUID nonExistentUserId = UUID.randomUUID();

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.createJoinRequest(
                    testUnit.getId(),
                    nonExistentUserId,
                    "Test"
            );
        });

        assertEquals(CodedError.USER_NOT_FOUND, exception.getError());
    }

    @Test
    @DisplayName("Successfully approve join request as unit admin")
    public void testApproveRequest_AsUnitAdmin_Success() {
        // Arrange - create pending request
        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Please approve")
                .createdAt(OffsetDateTime.now())
                .build();
        request = joinRequestRepository.save(request);

        // Act
        UnitJoinRequestEntity approved = joinRequestService.approveRequest(request.getId(), adminUser.getId());

        // Assert
        assertNotNull(approved);
        assertEquals(UnitJoinRequestStatus.APPROVED, approved.getStatus());
        assertNotNull(approved.getRespondedAt());
        assertEquals(adminUser.getId(), approved.getRespondedBy().getId());

        // Verify user was added to unit
        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(
                testUnit.getId(), requestingUser.getId()).orElseThrow();
        assertNotNull(member);
    }

    @Test
    @DisplayName("Successfully approve join request as global admin")
    public void testApproveRequest_AsGlobalAdmin_Success() {
        // Arrange - create pending request
        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Please approve")
                .createdAt(OffsetDateTime.now())
                .build();
        request = joinRequestRepository.save(request);

        // Act
        UnitJoinRequestEntity approved = joinRequestService.approveRequest(request.getId(), globalAdminUser.getId());

        // Assert
        assertNotNull(approved);
        assertEquals(UnitJoinRequestStatus.APPROVED, approved.getStatus());
        assertEquals(globalAdminUser.getId(), approved.getRespondedBy().getId());
    }

    @Test
    @DisplayName("Approve request should fail when request not found")
    public void testApproveRequest_RequestNotFound_ThrowsException() {
        // Arrange
        UUID nonExistentRequestId = UUID.randomUUID();

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.approveRequest(nonExistentRequestId, adminUser.getId());
        });

        assertEquals(CodedError.UNIT_NOT_FOUND, exception.getError());
    }

    @Test
    @DisplayName("Approve request should fail when request is not pending")
    public void testApproveRequest_NotPending_ThrowsException() {
        // Arrange - create already approved request
        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.APPROVED)
                .message("Already approved")
                .createdAt(OffsetDateTime.now())
                .build();
        final UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.approveRequest(savedRequest.getId(), adminUser.getId());
        });

        assertEquals(CodedError.INVALID_INPUT, exception.getError());
    }

    @Test
    @DisplayName("Approve request should fail when user cannot approve")
    public void testApproveRequest_UnauthorizedUser_ThrowsException() {
        // Arrange - create pending request
        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Please approve")
                .createdAt(OffsetDateTime.now())
                .build();
        final UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);

        // Act & Assert - regular user cannot approve
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.approveRequest(savedRequest.getId(), regularUser.getId());
        });

        assertEquals(CodedError.USER_NOT_ADMIN_OF_UNIT, exception.getError());
    }

    @Test
    @DisplayName("Successfully reject join request")
    public void testRejectRequest_Success() {
        // Arrange - create pending request
        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Please approve")
                .createdAt(OffsetDateTime.now())
                .build();
        request = joinRequestRepository.save(request);

        // Act
        UnitJoinRequestEntity rejected = joinRequestService.rejectRequest(request.getId(), adminUser.getId());

        // Assert
        assertNotNull(rejected);
        assertEquals(UnitJoinRequestStatus.REJECTED, rejected.getStatus());
        assertNotNull(rejected.getRespondedAt());
        assertEquals(adminUser.getId(), rejected.getRespondedBy().getId());

        // Verify user was NOT added to unit
        assertTrue(unitMemberRepository.findByUnitIdAndUserId(
                testUnit.getId(), requestingUser.getId()).isEmpty());
    }

    @Test
    @DisplayName("Reject request should fail when request not found")
    public void testRejectRequest_RequestNotFound_ThrowsException() {
        // Arrange
        UUID nonExistentRequestId = UUID.randomUUID();

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.rejectRequest(nonExistentRequestId, adminUser.getId());
        });

        assertEquals(CodedError.UNIT_NOT_FOUND, exception.getError());
    }

    @Test
    @DisplayName("Reject request should fail when request is not pending")
    public void testRejectRequest_NotPending_ThrowsException() {
        // Arrange - create already rejected request
        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.REJECTED)
                .message("Already rejected")
                .createdAt(OffsetDateTime.now())
                .build();
        final UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);

        // Act & Assert
        CodedErrorException exception = assertThrows(CodedErrorException.class, () -> {
            joinRequestService.rejectRequest(savedRequest.getId(), adminUser.getId());
        });

        assertEquals(CodedError.INVALID_INPUT, exception.getError());
    }

    @Test
    @DisplayName("Get pending requests for unit")
    public void testGetPendingRequestsForUnit_Success() {
        // Arrange - create multiple requests
        UnitJoinRequestEntity request1 = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Request 1")
                .createdAt(OffsetDateTime.now())
                .build();
        joinRequestRepository.save(request1);

        UnitJoinRequestEntity request2 = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(regularUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Request 2")
                .createdAt(OffsetDateTime.now())
                .build();
        joinRequestRepository.save(request2);

        // Create approved request (should not be returned)
        UnitJoinRequestEntity approvedRequest = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(globalAdminUser)
                .status(UnitJoinRequestStatus.APPROVED)
                .message("Approved")
                .createdAt(OffsetDateTime.now())
                .build();
        joinRequestRepository.save(approvedRequest);

        // Act
        List<UnitJoinRequestEntity> pending = joinRequestService.getPendingRequestsForUnit(testUnit.getId());

        // Assert
        assertEquals(2, pending.size());
        assertTrue(pending.stream().allMatch(r -> r.getStatus() == UnitJoinRequestStatus.PENDING));
    }

    @Test
    @DisplayName("Get all pending requests")
    public void testGetAllPendingRequests_Success() {
        // Arrange - create requests for different units
        UnitJoinRequestEntity request1 = UnitJoinRequestEntity.builder()
                .unit(testUnit)
                .requestedBy(requestingUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Request 1")
                .createdAt(OffsetDateTime.now())
                .build();
        joinRequestRepository.save(request1);

        UnitJoinRequestEntity request2 = UnitJoinRequestEntity.builder()
                .unit(emptyUnit)
                .requestedBy(regularUser)
                .status(UnitJoinRequestStatus.PENDING)
                .message("Request 2")
                .createdAt(OffsetDateTime.now())
                .build();
        joinRequestRepository.save(request2);

        // Act
        List<UnitJoinRequestEntity> allPending = joinRequestService.getAllPendingRequests();

        // Assert
        assertEquals(2, allPending.size());
        assertTrue(allPending.stream().allMatch(r -> r.getStatus() == UnitJoinRequestStatus.PENDING));
    }

    @Test
    @DisplayName("Can user approve request - unit admin")
    public void testCanUserApproveRequest_UnitAdmin_ReturnsTrue() {
        // Act
        boolean canApprove = joinRequestService.canUserApproveRequest(adminUser.getId(), testUnit.getId());

        // Assert
        assertTrue(canApprove);
    }

    @Test
    @DisplayName("Can user approve request - global admin")
    public void testCanUserApproveRequest_GlobalAdmin_ReturnsTrue() {
        // Act
        boolean canApprove = joinRequestService.canUserApproveRequest(globalAdminUser.getId(), testUnit.getId());

        // Assert
        assertTrue(canApprove);
    }

    @Test
    @DisplayName("Can user approve request - regular user returns false")
    public void testCanUserApproveRequest_RegularUser_ReturnsFalse() {
        // Act
        boolean canApprove = joinRequestService.canUserApproveRequest(regularUser.getId(), testUnit.getId());

        // Assert
        assertTrue(!canApprove);
    }
}

