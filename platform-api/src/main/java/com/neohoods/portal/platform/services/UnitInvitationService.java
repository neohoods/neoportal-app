package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.NotificationType;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitInvitationEntity;
import com.neohoods.portal.platform.entities.UnitInvitationStatus;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.ResidenceRole;
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
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", invitedBy.toString());
        }

        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        UserEntity invitedByUser = usersRepository.findById(invitedBy)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", invitedBy.toString()));

        UserEntity invitedUser = null;
        if (userId != null) {
            invitedUser = usersRepository.findById(userId)
                    .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

            // Check if already a member
            if (unitMemberRepository.existsByUnitIdAndUserId(unitId, userId)) {
                throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", userId.toString());
            }

            // Check if there's already a pending invitation
            List<UnitInvitationEntity> existingInvitations = invitationRepository
                    .findByInvitedUserIdAndStatus(userId, UnitInvitationStatus.PENDING);
            if (!existingInvitations.isEmpty()) {
                throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "User already has a pending invitation");
            }
        } else if (email != null && !email.trim().isEmpty()) {
            // Check if there's already a pending invitation for this email
            List<UnitInvitationEntity> existingInvitations = invitationRepository
                    .findByInvitedEmailAndStatus(email, UnitInvitationStatus.PENDING);
            if (!existingInvitations.isEmpty()) {
                throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Email already has a pending invitation");
            }
        } else {
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

        // Send notification
        sendInvitationNotification(saved);

        return Mono.just(saved.toUnitInvitation());
    }

    @Transactional
    public Mono<UnitMemberEntity> acceptInvitation(UUID invitationId, UUID userId) {
        log.info("Accepting invitation {} by user {}", invitationId, userId);

        UnitInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_INVITATION_NOT_FOUND, "invitationId",
                        invitationId.toString()));

        if (invitation.getStatus() != UnitInvitationStatus.PENDING) {
            throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation is not pending");
        }

        // Verify the invitation is for this user
        if (invitation.getInvitedUser() != null && !invitation.getInvitedUser().getId().equals(userId)) {
            throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
        }

        // If invitation is by email, verify the user's email matches
        if (invitation.getInvitedUser() == null && invitation.getInvitedEmail() != null) {
            UserEntity user = usersRepository.findById(userId)
                    .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));
            if (!invitation.getInvitedEmail().equals(user.getEmail())) {
                throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
            }
        }

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(invitation.getUnit().getId(), userId)) {
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

        // Update invitation status
        invitation.setStatus(UnitInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);

        return Mono.just(savedMember);
    }

    @Transactional
    public Mono<Void> rejectInvitation(UUID invitationId, UUID userId) {
        log.info("Rejecting invitation {} by user {}", invitationId, userId);

        UnitInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_INVITATION_NOT_FOUND, "invitationId",
                        invitationId.toString()));

        if (invitation.getStatus() != UnitInvitationStatus.PENDING) {
            throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation is not pending");
        }

        // Verify the invitation is for this user
        if (invitation.getInvitedUser() != null && !invitation.getInvitedUser().getId().equals(userId)) {
            throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
        }

        // If invitation is by email, verify the user's email matches
        if (invitation.getInvitedUser() == null && invitation.getInvitedEmail() != null) {
            UserEntity user = usersRepository.findById(userId)
                    .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));
            if (!invitation.getInvitedEmail().equals(user.getEmail())) {
                throw new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS);
            }
        }

        // Update invitation status
        invitation.setStatus(UnitInvitationStatus.REJECTED);
        invitation.setRespondedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);

        return Mono.empty();
    }

    @Transactional
    public Mono<UnitMemberEntity> approveInvitation(UUID invitationId, UUID adminId) {
        log.info("Approving invitation {} by admin {}", invitationId, adminId);

        UnitInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_INVITATION_NOT_FOUND, "invitationId",
                        invitationId.toString()));

        // Verify admin is admin of unit
        if (!unitsService.isUserAdminOfUnit(adminId, invitation.getUnit().getId())) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", adminId.toString());
        }

        if (invitation.getStatus() != UnitInvitationStatus.PENDING) {
            throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation is not pending");
        }

        UUID userId;
        if (invitation.getInvitedUser() != null) {
            userId = invitation.getInvitedUser().getId();
        } else {
            // If invitation is by email, we need to find the user
            if (invitation.getInvitedEmail() == null) {
                throw new CodedErrorException(CodedError.INVALID_INPUT, "message", "Invitation has no user or email");
            }
            UserEntity user = usersRepository.findByEmail(invitation.getInvitedEmail());
            if (user == null) {
                throw new CodedErrorException(CodedError.USER_NOT_FOUND, "email", invitation.getInvitedEmail());
            }
            userId = user.getId();
        }

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(invitation.getUnit().getId(), userId)) {
            throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", userId.toString());
        }

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

        // Add user to unit
        UnitMemberEntity member = UnitMemberEntity.builder()
                .unit(invitation.getUnit())
                .user(user)
                .role(UnitMemberRole.MEMBER)
                .residenceRole(ResidenceRole.TENANT) // Default to TENANT for invited members
                .joinedAt(OffsetDateTime.now())
                .build();

        UnitMemberEntity savedMember = unitMemberRepository.save(member);

        // Update invitation status
        invitation.setStatus(UnitInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);

        return Mono.just(savedMember);
    }

    @Transactional(readOnly = true)
    public Flux<UnitInvitation> getPendingInvitationsForUnit(UUID unitId) {
        List<UnitInvitationEntity> invitations = invitationRepository.findByUnitId(unitId).stream()
                .filter(inv -> inv.getStatus() == UnitInvitationStatus.PENDING)
                .toList();
        return Flux.fromIterable(invitations.stream().map(UnitInvitationEntity::toUnitInvitation).toList());
    }

    @Transactional(readOnly = true)
    public Flux<UnitInvitation> getPendingInvitationsForUser(UUID userId) {
        List<UnitInvitationEntity> invitations = invitationRepository
                .findByInvitedUserIdAndStatus(userId, UnitInvitationStatus.PENDING);
        return Flux.fromIterable(invitations.stream().map(UnitInvitationEntity::toUnitInvitation).toList());
    }

    private void sendInvitationNotification(UnitInvitationEntity invitation) {
        UUID targetUserId = invitation.getInvitedUser() != null ? invitation.getInvitedUser().getId() : null;
        if (targetUserId == null) {
            // If invitation is by email, we can't send notification yet
            log.debug("Invitation is by email, notification will be sent when user signs up");
            return;
        }

        String unitName = invitation.getUnit().getName();
        String inviterName = invitation.getInvitedBy().getFirstName() + " " + invitation.getInvitedBy().getLastName();

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
    }
}

