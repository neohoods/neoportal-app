package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitJoinRequestEntity;
import com.neohoods.portal.platform.entities.UnitJoinRequestStatus;
import com.neohoods.portal.platform.entities.UserEntity;
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

        UnitJoinRequestEntity request = UnitJoinRequestEntity.builder()
                .id(UUID.randomUUID())
                .unit(unit)
                .requestedBy(requestedBy)
                .status(UnitJoinRequestStatus.PENDING)
                .message(message)
                .createdAt(OffsetDateTime.now())
                .build();

        return joinRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<UnitJoinRequestEntity> getPendingRequestsForUnit(UUID unitId) {
        return joinRequestRepository.findByUnitIdAndStatus(unitId, UnitJoinRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public boolean canUserApproveRequest(UUID userId, UUID unitId) {
        // Check if user is admin of the unit
        if (unitsService.isUserAdminOfUnit(userId, unitId)) {
            return true;
        }

        // Check if unit has no members (admin global can approve)
        List<com.neohoods.portal.platform.entities.UnitMemberEntity> members = unitMemberRepository.findByUnitId(unitId);
        if (members.isEmpty()) {
            // Check if user is global admin (PROPERTY_MANAGEMENT or OWNER)
            UserEntity user = usersRepository.findById(userId)
                    .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));
            return user.getType() == com.neohoods.portal.platform.entities.UserType.PROPERTY_MANAGEMENT 
                    || user.getType() == com.neohoods.portal.platform.entities.UserType.OWNER;
        }

        return false;
    }

    @Transactional
    public UnitJoinRequestEntity approveRequest(UUID requestId, UUID approvedBy) {
        log.info("Approving join request {} by user {}", requestId, approvedBy);

        UnitJoinRequestEntity request = joinRequestRepository.findById(requestId)
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

        return joinRequestRepository.save(request);
    }

    @Transactional
    public UnitJoinRequestEntity rejectRequest(UUID requestId, UUID rejectedBy) {
        log.info("Rejecting join request {} by user {}", requestId, rejectedBy);

        UnitJoinRequestEntity request = joinRequestRepository.findById(requestId)
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

        return joinRequestRepository.save(request);
    }
}

