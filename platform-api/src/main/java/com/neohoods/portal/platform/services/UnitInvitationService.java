package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.NotificationType;
import com.neohoods.portal.platform.entities.ResidenceRole;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitInvitationEntity;
import com.neohoods.portal.platform.entities.UnitInvitationStatus;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UnitMemberRole;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.UnitInvitation;
import com.neohoods.portal.platform.repositories.UnitInvitationRepository;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitInvitationService {
    private final UnitInvitationRepository invitationRepository;
    private final UnitRepository unitRepository;
    private final UsersRepository usersRepository;
    private final UnitMemberRepository unitMemberRepository;
    private final NotificationsService notificationsService;
    private final UnitsService unitsService;

    @Transactional
    public Mono<UnitInvitation> inviteUser(UUID unitId, UUID userId, String email, UUID invitedBy) {
        log.info("Inviting user {} (email: {}) to unit {} by {}", userId, email, unitId, invitedBy);

        // Verify invitedBy is admin
        if (!unitsService.isUserAdminOfUnit(invitedBy, unitId)) {
            log.warn("User {} attempted to invite to unit {} but is not an admin", invitedBy, unitId);
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", invitedBy.toString());
        }
        log.debug("Verified that user {} is admin of unit {}", invitedBy, unitId);

        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> {
                    log.error("Unit {} not found when creating invitation", unitId);
                    return new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString());
                });
        log.debug("Found unit: {} ({})", unit.getName(), unitId);

        UserEntity invitedByUser = usersRepository.findById(invitedBy)
                .orElseThrow(() -> {
                    log.error("Inviter user {} not found", invitedBy);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", invitedBy.toString());
                });
        log.debug("Found inviter: {} ({})", invitedByUser.getEmail(), invitedBy);

        UserEntity invitedUser = null;
        if (userId != null) {
            invitedUser = usersRepository.findById(userId)
                    .orElseThrow(() -> {
                        log.error("Invited user {} not found", userId);
                        return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                    });
            log.debug("Found invited user: {} ({})", invitedUser.getEmail(), userId);

            // Check if already a member
            if (unitMemberRepository.existsByUnitIdAndUserId(unitId, userId)) {
                log.warn("User {} is already a member of unit {}", userId, unitId);
                throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", userId.toString());
            }

            // Check if there's already a pending invitation
            List<UnitInvitationEntity> existingInvitations = invitationRepository
                    .findByInvitedUserIdAndStatus(userId, UnitInvitationStatus.PENDING);
            if (!existingInvitations.isEmpty()) {
                log.warn("User {} already has {} pending invitation(s) for unit {}", userId, existingInvitations.size(),
                        unitId);
                throw new CodedErrorException(CodedError.INVALID_INPUT, "message",
                        "User already has a pending invitation");
            }
        } else if (email != null && !email.trim().isEmpty()) {
            log.debug("Invitation by email: {}", email);
            // Check if there's already a pending invitation for this email
            List<UnitInvitationEntity> existingInvitations = invitationRepository
                    .findByInvitedEmailAndStatus(email, UnitInvitationStatus.PENDING);
            if (!existingInvitations.isEmpty()) {
                log.warn("Email {} already has {} pending invitation(s)", email, existingInvitations.size());
                throw new CodedErrorException(CodedError.INVALID_INPUT, "message",
                        "Email already has a pending invitation");
            }
        } else {
            log.error("Missing required field: both userId and email are null");
            throw new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "userId or email");
        }

        UnitInvitationEntity invitation = UnitInvitationEntity.builder()
                .id(UUID.randomUUID())
                .unit(unit)
                .invitedUser(invitedUser)
                .invitedEmail(email)
                .invitedBy(invitedByUser)
                .status(UnitInvitationStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        UnitInvitationEntity saved = invitationRepository.save(invitation);
        log.info("Created invitation {} for user {} (email: {}) to unit {} by {}",
                saved.getId(), userId, email, unitId, invitedBy);

        // Send notification
        sendInvitationNotification(saved);

        log.debug("Successfully created and saved invitation {}", saved.getId());
        return Mono.just(saved.toUnitInvitation());
    }

    @Transactional
    public Mono<UnitMemberEntity> acceptInvitation(UUID invitationId, UUID userId) {
        log.info("Accepting invitation {} by user {}", invitationId, userId);

        UnitInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> {
                    log.error("Invitation {} not found when accepting", invitationId);
                    return new CodedErrorException(CodedError.UNIT_INVITATION_NOT_FOUND, "invitationId",
                            invitationId.toString());
                });
        log.debug("Found invitation {} with status {} for unit {}", invitationId, invitation.getStatus(),
                invitation.getUnit().getId());

        if (invitation.getStatus() != UnitInvitationStatus.PENDING) {
            log.warn("Attempted to accept invitation {} with status {} (expected PENDING)", invitationId,
                    invitation.getStatus());
            throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation is not pending");
        }

        // Verify the invitation is for this user
        if (invitation.getInvitedUser() != null && !invitation.getInvitedUser().getId().equals(userId)) {
            log.warn("User {} attempted to accept invitation {} intended for user {}",
                    userId, invitationId, invitation.getInvitedUser().getId());
            throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
        }

        // If invitation is by email, verify the user's email matches
        if (invitation.getInvitedUser() == null && invitation.getInvitedEmail() != null) {
            UserEntity user = usersRepository.findById(userId)
                    .orElseThrow(() -> {
                        log.error("User {} not found when accepting invitation by email", userId);
                        return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                    });
            if (!invitation.getInvitedEmail().equals(user.getEmail())) {
                log.warn("User {} (email: {}) attempted to accept invitation for email {}",
                        userId, user.getEmail(), invitation.getInvitedEmail());
                throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
            }
            log.debug("Verified email match for invitation {}: {} == {}", invitationId, user.getEmail(),
                    invitation.getInvitedEmail());
        }

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User {} not found when accepting invitation", userId);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                });

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(invitation.getUnit().getId(), userId)) {
            log.warn("User {} is already a member of unit {}, cannot accept invitation {}",
                    userId, invitation.getUnit().getId(), invitationId);
            throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", userId.toString());
        }

        // Add user to unit
        UnitMemberEntity member = UnitMemberEntity.builder()
                .unit(invitation.getUnit())
                .user(user)
                .role(UnitMemberRole.MEMBER)
                .residenceRole(ResidenceRole.TENANT) // Default to TENANT for invited members
                .joinedAt(OffsetDateTime.now())
                .build();

        UnitMemberEntity savedMember = unitMemberRepository.save(member);
        log.info("Added user {} as member to unit {} via invitation {}", userId, invitation.getUnit().getId(),
                invitationId);

        // Update invitation status
        invitation.setStatus(UnitInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);
        log.debug("Updated invitation {} status to ACCEPTED", invitationId);

        return Mono.just(savedMember);
    }

    @Transactional
    public Mono<Void> rejectInvitation(UUID invitationId, UUID userId) {
        log.info("Rejecting invitation {} by user {}", invitationId, userId);

        UnitInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> {
                    log.error("Invitation {} not found when rejecting", invitationId);
                    return new CodedErrorException(CodedError.UNIT_INVITATION_NOT_FOUND, "invitationId",
                            invitationId.toString());
                });
        log.debug("Found invitation {} with status {} for unit {}", invitationId, invitation.getStatus(),
                invitation.getUnit().getId());

        if (invitation.getStatus() != UnitInvitationStatus.PENDING) {
            log.warn("Attempted to reject invitation {} with status {} (expected PENDING)", invitationId,
                    invitation.getStatus());
            throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation is not pending");
        }

        // Verify the invitation is for this user
        if (invitation.getInvitedUser() != null && !invitation.getInvitedUser().getId().equals(userId)) {
            log.warn("User {} attempted to reject invitation {} intended for user {}",
                    userId, invitationId, invitation.getInvitedUser().getId());
            throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
        }

        // If invitation is by email, verify the user's email matches
        if (invitation.getInvitedUser() == null && invitation.getInvitedEmail() != null) {
            UserEntity user = usersRepository.findById(userId)
                    .orElseThrow(() -> {
                        log.error("User {} not found when rejecting invitation by email", userId);
                        return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                    });
            if (!invitation.getInvitedEmail().equals(user.getEmail())) {
                log.warn("User {} (email: {}) attempted to reject invitation for email {}",
                        userId, user.getEmail(), invitation.getInvitedEmail());
                throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
            }
            log.debug("Verified email match for invitation {}: {} == {}", invitationId, user.getEmail(),
                    invitation.getInvitedEmail());
        }

        // Update invitation status
        invitation.setStatus(UnitInvitationStatus.REJECTED);
        invitation.setRespondedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);
        log.info("Rejected invitation {} by user {} for unit {}", invitationId, userId, invitation.getUnit().getId());

        return Mono.empty();
    }

    @Transactional
    public Mono<UnitMemberEntity> approveInvitation(UUID invitationId, UUID adminId) {
        log.info("Approving invitation {} by admin {}", invitationId, adminId);

        UnitInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> {
                    log.error("Invitation {} not found when approving", invitationId);
                    return new CodedErrorException(CodedError.UNIT_INVITATION_NOT_FOUND, "invitationId",
                            invitationId.toString());
                });
        log.debug("Found invitation {} with status {} for unit {}", invitationId, invitation.getStatus(),
                invitation.getUnit().getId());

        // Verify admin is admin of unit
        if (!unitsService.isUserAdminOfUnit(adminId, invitation.getUnit().getId())) {
            log.warn("User {} attempted to approve invitation {} but is not admin of unit {}",
                    adminId, invitationId, invitation.getUnit().getId());
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", adminId.toString());
        }
        log.debug("Verified that admin {} is admin of unit {}", adminId, invitation.getUnit().getId());

        if (invitation.getStatus() != UnitInvitationStatus.PENDING) {
            log.warn("Attempted to approve invitation {} with status {} (expected PENDING)", invitationId,
                    invitation.getStatus());
            throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation is not pending");
        }

        UUID userId;
        if (invitation.getInvitedUser() != null) {
            userId = invitation.getInvitedUser().getId();
            log.debug("Invitation {} is for user {}", invitationId, userId);
        } else {
            // If invitation is by email, we need to find the user
            if (invitation.getInvitedEmail() == null) {
                log.error("Invitation {} has neither user nor email", invitationId);
                throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation has no user or email");
            }
            UserEntity user = usersRepository.findByEmail(invitation.getInvitedEmail());
            if (user == null) {
                log.error("User with email {} not found for invitation {}", invitation.getInvitedEmail(), invitationId);
                throw new CodedErrorException(CodedError.USER_NOT_FOUND, "email", invitation.getInvitedEmail());
            }
            userId = user.getId();
            log.debug("Found user {} for email {} in invitation {}", userId, invitation.getInvitedEmail(),
                    invitationId);
        }

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(invitation.getUnit().getId(), userId)) {
            log.warn("User {} is already a member of unit {}, cannot approve invitation {}",
                    userId, invitation.getUnit().getId(), invitationId);
            throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", userId.toString());
        }

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User {} not found when approving invitation", userId);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                });

        // Add user to unit
        UnitMemberEntity member = UnitMemberEntity.builder()
                .unit(invitation.getUnit())
                .user(user)
                .role(UnitMemberRole.MEMBER)
                .residenceRole(ResidenceRole.TENANT) // Default to TENANT for invited members
                .joinedAt(OffsetDateTime.now())
                .build();

        UnitMemberEntity savedMember = unitMemberRepository.save(member);
        log.info("Added user {} as member to unit {} via admin approval of invitation {}",
                userId, invitation.getUnit().getId(), invitationId);

        // Update invitation status
        invitation.setStatus(UnitInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);
        log.debug("Updated invitation {} status to ACCEPTED by admin {}", invitationId, adminId);

        return Mono.just(savedMember);
    }

    @Transactional(readOnly = true)
    public Flux<UnitInvitation> getPendingInvitationsForUnit(UUID unitId) {
        log.debug("Getting pending invitations for unit {}", unitId);
        List<UnitInvitationEntity> invitations = invitationRepository.findByUnitId(unitId).stream()
                .filter(inv -> inv.getStatus() == UnitInvitationStatus.PENDING)
                .toList();
        log.debug("Found {} pending invitation(s) for unit {}", invitations.size(), unitId);
        return Flux.fromIterable(invitations.stream().map(UnitInvitationEntity::toUnitInvitation).toList());
    }

    @Transactional(readOnly = true)
    public Flux<UnitInvitation> getPendingInvitationsForUser(UUID userId) {
        log.debug("Getting pending invitations for user {}", userId);
        List<UnitInvitationEntity> invitations = invitationRepository
                .findByInvitedUserIdAndStatus(userId, UnitInvitationStatus.PENDING);
        log.debug("Found {} pending invitation(s) for user {}", invitations.size(), userId);
        return Flux.fromIterable(invitations.stream().map(UnitInvitationEntity::toUnitInvitation).toList());
    }

    private void sendInvitationNotification(UnitInvitationEntity invitation) {
        UUID targetUserId = invitation.getInvitedUser() != null ? invitation.getInvitedUser().getId() : null;
        if (targetUserId == null) {
            // If invitation is by email, we can't send notification yet
            log.debug("Invitation {} is by email {}, notification will be sent when user signs up",
                    invitation.getId(), invitation.getInvitedEmail());
            return;
        }

        String unitName = invitation.getUnit().getName();
        String inviterName = invitation.getInvitedBy().getFirstName() + " " + invitation.getInvitedBy().getLastName();

        log.debug("Sending invitation notification to user {} for invitation {} to unit {}",
                targetUserId, invitation.getId(), unitName);
        notificationsService.createNotification(
                targetUserId,
                NotificationType.UNIT_INVITATION,
                "Invitation to join unit: " + unitName,
                inviterName + " has invited you to join the unit: " + unitName,
                Map.of(
                        "unitId", invitation.getUnit().getId().toString(),
                        "unitName", unitName,
                        "invitationId", invitation.getId().toString(),
                        "invitedBy", inviterName));
        log.debug("Sent invitation notification to user {} for invitation {}", targetUserId, invitation.getId());
    }
}
