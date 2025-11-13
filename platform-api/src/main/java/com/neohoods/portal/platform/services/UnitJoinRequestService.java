package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.NotificationType;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitJoinRequestEntity;
import com.neohoods.portal.platform.entities.UnitJoinRequestStatus;
import com.neohoods.portal.platform.entities.UnitMemberRole;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.repositories.UnitJoinRequestRepository;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitJoinRequestService {
    private final UnitJoinRequestRepository joinRequestRepository;
    private final UnitRepository unitRepository;
    private final UnitMemberRepository unitMemberRepository;
    private final UsersRepository usersRepository;
    private final UnitsService unitsService;
    private final NotificationsService notificationsService;

    @Transactional
    public UnitJoinRequestEntity createJoinRequest(UUID unitId, UUID requestedById, String message) {
        log.info("Creating join request for unit {} by user {}", unitId, requestedById);

        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> {
                    log.error("Unit {} not found when creating join request", unitId);
                    return new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString());
                });
        log.debug("Found unit: {} ({})", unit.getName(), unitId);

        UserEntity requestedBy = usersRepository.findById(requestedById)
                .orElseThrow(() -> {
                    log.error("User {} not found when creating join request", requestedById);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", requestedById.toString());
                });
        log.debug("Found requesting user: {} ({})", requestedBy.getEmail(), requestedById);

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(unitId, requestedById)) {
            log.warn("User {} is already a member of unit {}", requestedById, unitId);
            throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", requestedById.toString());
        }

        // Check if there's already a pending request
        if (joinRequestRepository.existsByUnitIdAndRequestedByIdAndStatus(unitId, requestedById, UnitJoinRequestStatus.PENDING)) {
            log.warn("User {} already has a pending join request for unit {}", requestedById, unitId);
            throw new CodedErrorException(CodedError.INVALID_INPUT, Map.of("message", "User already has a pending join request for this unit"));
        }

        // Check if unit has no members - if so, add user directly as ADMIN
        List<com.neohoods.portal.platform.entities.UnitMemberEntity> members = unitMemberRepository.findByUnitId(unitId);
        if (members.isEmpty()) {
            log.info("Unit {} has no members, adding user {} directly as ADMIN", unitId, requestedById);
            // Add user directly as ADMIN
            com.neohoods.portal.platform.entities.UnitMemberEntity adminMember = com.neohoods.portal.platform.entities.UnitMemberEntity.builder()
                    .unit(unit)
                    .user(requestedBy)
                    .role(com.neohoods.portal.platform.entities.UnitMemberRole.ADMIN)
                    .residenceRole(com.neohoods.portal.platform.entities.ResidenceRole.PROPRIETAIRE)
                    .joinedAt(OffsetDateTime.now())
                    .build();
            unitMemberRepository.save(adminMember);
            log.info("Added user {} as ADMIN to empty unit {}", requestedById, unitId);

            // Set as primary unit if user has no primary unit
            if (requestedBy.getPrimaryUnit() == null) {
                requestedBy.setPrimaryUnit(unit);
                usersRepository.save(requestedBy);
                log.debug("Set unit {} as primary unit for user {}", unitId, requestedById);
            }

            // Return null to indicate no request was created (user was added directly)
            return null;
        }

        // Create join request
        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .unit(unit)
                .requestedBy(requestedBy)
                .status(UnitJoinRequestStatus.PENDING)
                .message(message)
                .createdAt(OffsetDateTime.now())
                .build();

        UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);
        log.info("Created join request {} for user {} to unit {}", savedRequest.getId(), requestedById, unitId);

        // Reload with fetches to avoid lazy initialization issues
        savedRequest = joinRequestRepository.findByIdWithFetches(savedRequest.getId())
                .orElse(savedRequest);

        // Send notifications to unit admins
        List<com.neohoods.portal.platform.entities.UnitMemberEntity> unitAdmins = unitMemberRepository.findByUnitIdAndRole(unitId, UnitMemberRole.ADMIN);
        log.debug("Found {} unit admin(s) for unit {} to notify about join request {}", unitAdmins.size(), unitId, savedRequest.getId());
        for (com.neohoods.portal.platform.entities.UnitMemberEntity adminMember : unitAdmins) {
            UserEntity admin = adminMember.getUser();
            Map<String, Object> payload = Map.of(
                    "requestId", savedRequest.getId().toString(),
                    "unitId", unitId.toString(),
                    "unitName", unit.getName(),
                    "requestedById", requestedById.toString(),
                    "requestedByUsername", requestedBy.getUsername(),
                    "requestedByFirstName", requestedBy.getFirstName() != null ? requestedBy.getFirstName() : "",
                    "requestedByLastName", requestedBy.getLastName() != null ? requestedBy.getLastName() : "",
                    "message", message != null ? message : ""
            );
            notificationsService.createNotification(
                    admin.getId(),
                    NotificationType.UNIT_JOIN_REQUEST,
                    "Nouvelle demande de rejoindre le logement",
                    String.format("%s %s souhaite rejoindre le logement %s",
                            requestedBy.getFirstName() != null ? requestedBy.getFirstName() : "",
                            requestedBy.getLastName() != null ? requestedBy.getLastName() : "",
                            unit.getName()),
                    payload
            );
            log.debug("Sent join request notification to unit admin {} for request {}", admin.getId(), savedRequest.getId());
        }

        // Send notifications to global admins
        List<UserEntity> globalAdmins = usersRepository.findByType(UserType.ADMIN);
        log.debug("Found {} global admin(s) to notify about join request {}", globalAdmins.size(), savedRequest.getId());
        int notifiedGlobalAdmins = 0;
        for (UserEntity admin : globalAdmins) {
            // Skip if admin is already notified (they might be a unit admin too)
            boolean alreadyNotified = unitAdmins.stream()
                    .anyMatch(m -> m.getUser().getId().equals(admin.getId()));
            if (!alreadyNotified) {
                Map<String, Object> payload = Map.of(
                        "requestId", savedRequest.getId().toString(),
                        "unitId", unitId.toString(),
                        "unitName", unit.getName(),
                        "requestedById", requestedById.toString(),
                        "requestedByUsername", requestedBy.getUsername(),
                        "requestedByFirstName", requestedBy.getFirstName() != null ? requestedBy.getFirstName() : "",
                        "requestedByLastName", requestedBy.getLastName() != null ? requestedBy.getLastName() : "",
                        "message", message != null ? message : ""
                );
                notificationsService.createNotification(
                        admin.getId(),
                        NotificationType.UNIT_JOIN_REQUEST,
                        "Nouvelle demande de rejoindre un logement",
                        String.format("%s %s souhaite rejoindre le logement %s",
                                requestedBy.getFirstName() != null ? requestedBy.getFirstName() : "",
                                requestedBy.getLastName() != null ? requestedBy.getLastName() : "",
                                unit.getName()),
                        payload
                );
                notifiedGlobalAdmins++;
                log.debug("Sent join request notification to global admin {} for request {}", admin.getId(), savedRequest.getId());
            }
        }
        log.info("Sent notifications for join request {}: {} unit admin(s), {} global admin(s)", 
                savedRequest.getId(), unitAdmins.size(), notifiedGlobalAdmins);

        return savedRequest;
    }

    @Transactional(readOnly = true)
    public List<UnitJoinRequestEntity> getPendingRequestsForUnit(UUID unitId) {
        log.debug("Getting pending join requests for unit {}", unitId);
        List<UnitJoinRequestEntity> requests = joinRequestRepository.findByUnitIdAndStatusWithFetches(unitId, UnitJoinRequestStatus.PENDING);
        log.debug("Found {} pending join request(s) for unit {}", requests.size(), unitId);
        return requests;
    }

    @Transactional(readOnly = true)
    public List<UnitJoinRequestEntity> getAllPendingRequests() {
        log.debug("Getting all pending join requests");
        List<UnitJoinRequestEntity> requests = joinRequestRepository.findByStatusWithFetches(UnitJoinRequestStatus.PENDING);
        log.debug("Found {} total pending join request(s)", requests.size());
        return requests;
    }

    @Transactional(readOnly = true)
    public List<UnitJoinRequestEntity> getPendingRequestsForUser(UUID userId) {
        log.debug("Getting pending join requests for user {}", userId);
        List<UnitJoinRequestEntity> requests = joinRequestRepository.findByRequestedByIdAndStatusWithFetches(userId, UnitJoinRequestStatus.PENDING);
        log.debug("Found {} pending join request(s) for user {}", requests.size(), userId);
        return requests;
    }

    @Transactional(readOnly = true)
    public boolean hasPendingRequestForUnit(UUID userId, UUID unitId) {
        log.debug("Checking if user {} has pending request for unit {}", userId, unitId);
        boolean hasPending = joinRequestRepository.existsByUnitIdAndRequestedByIdAndStatus(unitId, userId, UnitJoinRequestStatus.PENDING);
        log.debug("User {} {} a pending request for unit {}", userId, hasPending ? "has" : "does not have", unitId);
        return hasPending;
    }

    @Transactional(readOnly = true)
    public boolean canUserApproveRequest(UUID userId, UUID unitId) {
        log.debug("Checking if user {} can approve requests for unit {}", userId, unitId);
        
        // Check if user is admin of the unit
        if (unitsService.isUserAdminOfUnit(userId, unitId)) {
            log.debug("User {} is admin of unit {}, can approve requests", userId, unitId);
            return true;
        }

        // Check if user is global admin (ADMIN type)
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User {} not found when checking approval permissions", userId);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                });
        if (user.getType() == com.neohoods.portal.platform.entities.UserType.ADMIN) {
            log.debug("User {} is global ADMIN, can approve requests for unit {}", userId, unitId);
            return true;
        }

        // Check if unit has no members (admin global can approve)
        List<com.neohoods.portal.platform.entities.UnitMemberEntity> members = unitMemberRepository.findByUnitId(unitId);
        if (members.isEmpty()) {
            // Check if user is global admin (PROPERTY_MANAGEMENT or OWNER)
            boolean canApprove = user.getType() == com.neohoods.portal.platform.entities.UserType.PROPERTY_MANAGEMENT
                    || user.getType() == com.neohoods.portal.platform.entities.UserType.OWNER;
            log.debug("Unit {} has no members, user {} (type: {}) {} approve requests", 
                    unitId, userId, user.getType(), canApprove ? "can" : "cannot");
            return canApprove;
        }

        log.debug("User {} cannot approve requests for unit {}", userId, unitId);
        return false;
    }

    @Transactional
    public UnitJoinRequestEntity approveRequest(UUID requestId, UUID approvedBy) {
        log.info("Approving join request {} by user {}", requestId, approvedBy);

        UnitJoinRequestEntity request = joinRequestRepository.findByIdWithFetches(requestId)
                .orElseThrow(() -> {
                    log.error("Join request {} not found when approving", requestId);
                    return new CodedErrorException(CodedError.UNIT_NOT_FOUND, "requestId", requestId.toString());
                });
        log.debug("Found join request {} with status {} for unit {}", requestId, request.getStatus(), request.getUnit().getId());

        if (request.getStatus() != UnitJoinRequestStatus.PENDING) {
            log.warn("Attempted to approve join request {} with status {} (expected PENDING)", requestId, request.getStatus());
            throw new CodedErrorException(CodedError.INVALID_INPUT, Map.of("message", "Request is not pending"));
        }

        // Check if user can approve
        if (!canUserApproveRequest(approvedBy, request.getUnit().getId())) {
            log.warn("User {} attempted to approve request {} but lacks permission", approvedBy, requestId);
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", approvedBy.toString());
        }
        log.debug("Verified that user {} can approve request {}", approvedBy, requestId);

        UserEntity responder = usersRepository.findById(approvedBy)
                .orElseThrow(() -> {
                    log.error("Approver user {} not found", approvedBy);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", approvedBy.toString());
                });

        // Add user to unit
        log.debug("Adding user {} to unit {} via approved join request {}", 
                request.getRequestedBy().getId(), request.getUnit().getId(), requestId);
        unitsService.addMemberToUnit(request.getUnit().getId(), request.getRequestedBy().getId(), approvedBy).block();
        log.info("Added user {} to unit {} via approved join request {}", 
                request.getRequestedBy().getId(), request.getUnit().getId(), requestId);

        // Update request status
        request.setStatus(UnitJoinRequestStatus.APPROVED);
        request.setRespondedAt(OffsetDateTime.now());
        request.setRespondedBy(responder);

        UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);
        log.info("Updated join request {} status to APPROVED by user {}", requestId, approvedBy);

        // Reload with fetches to avoid lazy initialization issues
        return joinRequestRepository.findByIdWithFetches(savedRequest.getId())
                .orElse(savedRequest);
    }

    @Transactional
    public UnitJoinRequestEntity rejectRequest(UUID requestId, UUID rejectedBy) {
        log.info("Rejecting join request {} by user {}", requestId, rejectedBy);

        UnitJoinRequestEntity request = joinRequestRepository.findByIdWithFetches(requestId)
                .orElseThrow(() -> {
                    log.error("Join request {} not found when rejecting", requestId);
                    return new CodedErrorException(CodedError.UNIT_NOT_FOUND, "requestId", requestId.toString());
                });
        log.debug("Found join request {} with status {} for unit {}", requestId, request.getStatus(), request.getUnit().getId());

        if (request.getStatus() != UnitJoinRequestStatus.PENDING) {
            log.warn("Attempted to reject join request {} with status {} (expected PENDING)", requestId, request.getStatus());
            throw new CodedErrorException(CodedError.INVALID_INPUT, Map.of("message", "Request is not pending"));
        }

        // Check if user can reject
        if (!canUserApproveRequest(rejectedBy, request.getUnit().getId())) {
            log.warn("User {} attempted to reject request {} but lacks permission", rejectedBy, requestId);
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", rejectedBy.toString());
        }
        log.debug("Verified that user {} can reject request {}", rejectedBy, requestId);

        UserEntity responder = usersRepository.findById(rejectedBy)
                .orElseThrow(() -> {
                    log.error("Rejecter user {} not found", rejectedBy);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", rejectedBy.toString());
                });

        // Update request status
        request.setStatus(UnitJoinRequestStatus.REJECTED);
        request.setRespondedAt(OffsetDateTime.now());
        request.setRespondedBy(responder);

        UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);
        log.info("Rejected join request {} by user {} for unit {}", requestId, rejectedBy, request.getUnit().getId());

        // Reload with fetches to avoid lazy initialization issues
        return joinRequestRepository.findByIdWithFetches(savedRequest.getId())
                .orElse(savedRequest);
    }
}

