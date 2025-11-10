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
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        UserEntity requestedBy = usersRepository.findById(requestedById)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", requestedById.toString()));

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(unitId, requestedById)) {
            throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", requestedById.toString());
        }

        // Check if there's already a pending request
        if (joinRequestRepository.existsByUnitIdAndRequestedByIdAndStatus(unitId, requestedById, UnitJoinRequestStatus.PENDING)) {
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
            
            // Set as primary unit if user has no primary unit
            if (requestedBy.getPrimaryUnit() == null) {
                requestedBy.setPrimaryUnit(unit);
                usersRepository.save(requestedBy);
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
        
        // Reload with fetches to avoid lazy initialization issues
        savedRequest = joinRequestRepository.findByIdWithFetches(savedRequest.getId())
                .orElse(savedRequest);

        // Send notifications to unit admins
        List<com.neohoods.portal.platform.entities.UnitMemberEntity> unitAdmins = unitMemberRepository.findByUnitIdAndRole(unitId, UnitMemberRole.ADMIN);
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
        }

        // Send notifications to global admins
        List<UserEntity> globalAdmins = usersRepository.findByType(UserType.ADMIN);
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
            }
        }

        return savedRequest;
    }

    @Transactional(readOnly = true)
    public List<UnitJoinRequestEntity> getPendingRequestsForUnit(UUID unitId) {
        return joinRequestRepository.findByUnitIdAndStatusWithFetches(unitId, UnitJoinRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<UnitJoinRequestEntity> getAllPendingRequests() {
        return joinRequestRepository.findByStatusWithFetches(UnitJoinRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<UnitJoinRequestEntity> getPendingRequestsForUser(UUID userId) {
        return joinRequestRepository.findByRequestedByIdAndStatusWithFetches(userId, UnitJoinRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public boolean hasPendingRequestForUnit(UUID userId, UUID unitId) {
        return joinRequestRepository.existsByUnitIdAndRequestedByIdAndStatus(unitId, userId, UnitJoinRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public boolean canUserApproveRequest(UUID userId, UUID unitId) {
        // Check if user is admin of the unit
        if (unitsService.isUserAdminOfUnit(userId, unitId)) {
            return true;
        }

        // Check if user is global admin (ADMIN type)
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));
        if (user.getType() == com.neohoods.portal.platform.entities.UserType.ADMIN) {
            return true;
        }

        // Check if unit has no members (admin global can approve)
        List<com.neohoods.portal.platform.entities.UnitMemberEntity> members = unitMemberRepository.findByUnitId(unitId);
        if (members.isEmpty()) {
            // Check if user is global admin (PROPERTY_MANAGEMENT or OWNER)
            return user.getType() == com.neohoods.portal.platform.entities.UserType.PROPERTY_MANAGEMENT 
                    || user.getType() == com.neohoods.portal.platform.entities.UserType.OWNER;
        }

        return false;
    }

    @Transactional
    public UnitJoinRequestEntity approveRequest(UUID requestId, UUID approvedBy) {
        log.info("Approving join request {} by user {}", requestId, approvedBy);

        UnitJoinRequestEntity request = joinRequestRepository.findByIdWithFetches(requestId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "requestId", requestId.toString()));

        if (request.getStatus() != UnitJoinRequestStatus.PENDING) {
            throw new CodedErrorException(CodedError.INVALID_INPUT, Map.of("message", "Request is not pending"));
        }

        // Check if user can approve
        if (!canUserApproveRequest(approvedBy, request.getUnit().getId())) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", approvedBy.toString());
        }

        UserEntity responder = usersRepository.findById(approvedBy)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", approvedBy.toString()));

        // Add user to unit
        unitsService.addMemberToUnit(request.getUnit().getId(), request.getRequestedBy().getId(), approvedBy).block();

        // Update request status
        request.setStatus(UnitJoinRequestStatus.APPROVED);
        request.setRespondedAt(OffsetDateTime.now());
        request.setRespondedBy(responder);

        UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);
        
        // Reload with fetches to avoid lazy initialization issues
        return joinRequestRepository.findByIdWithFetches(savedRequest.getId())
                .orElse(savedRequest);
    }

    @Transactional
    public UnitJoinRequestEntity rejectRequest(UUID requestId, UUID rejectedBy) {
        log.info("Rejecting join request {} by user {}", requestId, rejectedBy);

        UnitJoinRequestEntity request = joinRequestRepository.findByIdWithFetches(requestId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "requestId", requestId.toString()));

        if (request.getStatus() != UnitJoinRequestStatus.PENDING) {
            throw new CodedErrorException(CodedError.INVALID_INPUT, Map.of("message", "Request is not pending"));
        }

        // Check if user can reject
        if (!canUserApproveRequest(rejectedBy, request.getUnit().getId())) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", rejectedBy.toString());
        }

        UserEntity responder = usersRepository.findById(rejectedBy)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", rejectedBy.toString()));

        // Update request status
        request.setStatus(UnitJoinRequestStatus.REJECTED);
        request.setRespondedAt(OffsetDateTime.now());
        request.setRespondedBy(responder);

        UnitJoinRequestEntity savedRequest = joinRequestRepository.save(request);
        
        // Reload with fetches to avoid lazy initialization issues
        return joinRequestRepository.findByIdWithFetches(savedRequest.getId())
                .orElse(savedRequest);
    }
}

