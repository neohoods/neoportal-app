package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UnitMemberRole;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.PaginatedUnits;
import com.neohoods.portal.platform.model.Unit;
import com.neohoods.portal.platform.model.UnitMember;
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
public class UnitsService {
    private final UnitRepository unitRepository;
    private final UnitMemberRepository unitMemberRepository;
    private final UsersRepository usersRepository;

    @Transactional
    public Mono<Unit> createUnit(String name, UUID initialAdminId) {
        log.info("Creating unit: {} with initial admin: {}", name, initialAdminId);

        UnitEntity unit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        UnitEntity savedUnit = unitRepository.save(unit);

        // Add initial admin
        UUID finalAdminId;
        if (initialAdminId == null) {
            // Determine default admin from users
            List<UserEntity> allUsers = new java.util.ArrayList<>();
            usersRepository.findAll().forEach(allUsers::add);
            UserEntity defaultAdmin = determineDefaultAdmin(allUsers);
            if (defaultAdmin == null) {
                throw new CodedErrorException(CodedError.USER_NOT_FOUND,
                        Map.of("message", "No users available to assign as admin"));
            }
            finalAdminId = defaultAdmin.getId();
        } else {
            finalAdminId = initialAdminId;
        }

        final UUID adminId = finalAdminId;
        UserEntity admin = usersRepository.findById(adminId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", adminId.toString()));

        UnitMemberEntity adminMember = UnitMemberEntity.builder()
                .unit(savedUnit)
                .user(admin)
                .role(UnitMemberRole.ADMIN)
                .joinedAt(OffsetDateTime.now())
                .build();

        unitMemberRepository.save(adminMember);

        log.info("Created unit: {} with ID: {}", name, savedUnit.getId());
        return Mono.just(getUnitById(savedUnit.getId()).block());
    }

    @Transactional(readOnly = true)
    public Mono<Unit> getUnitById(UUID unitId) {
        log.debug("Getting unit by ID: {}", unitId);
        UnitEntity unit = unitRepository.findByIdWithMembers(unitId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));
        return Mono.just(unit.toUnit());
    }

    @Transactional(readOnly = true)
    public Flux<Unit> getUnitsForUser(UUID userId) {
        log.debug("Getting units for user: {}", userId);
        List<UnitEntity> units = unitRepository.findByMembersUserId(userId);
        return Flux.fromIterable(units.stream().map(UnitEntity::toUnit).collect(Collectors.toList()));
    }

    @Transactional
    public Mono<Unit> updateUnit(UUID unitId, String name) {
        log.info("Updating unit: {} with name: {}", unitId, name);
        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        unit.setName(name);
        unit.setUpdatedAt(OffsetDateTime.now());

        UnitEntity saved = unitRepository.save(unit);
        return Mono.just(getUnitById(saved.getId()).block());
    }

    @Transactional
    public Mono<Void> deleteUnit(UUID unitId) {
        log.info("Deleting unit: {}", unitId);
        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        // Get all members before deletion
        List<UnitMemberEntity> members = unitMemberRepository.findByUnitId(unitId);

        // Delete unit (cascade will delete members)
        unitRepository.delete(unit);

        // Disable users who have no other units
        for (UnitMemberEntity member : members) {
            long unitCount = unitMemberRepository.countByUserId(member.getUser().getId());
            if (unitCount == 0) {
                UserEntity user = member.getUser();
                user.setDisabled(true);
                usersRepository.save(user);
                log.info("Disabled user {} as they have no units", user.getId());
            }
        }

        return Mono.empty();
    }

    @Transactional
    public Mono<UnitMember> addMemberToUnit(UUID unitId, UUID userId, UUID addedBy) {
        log.info("Adding member {} to unit {} by {}", userId, unitId, addedBy);

        // Verify addedBy is admin (skip if null for admin operations)
        if (addedBy != null && !isUserAdminOfUnit(addedBy, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", addedBy.toString());
        }

        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(unitId, userId)) {
            throw new CodedErrorException(CodedError.USER_ALREADY_MEMBER, "userId", userId.toString());
        }

        UnitMemberEntity member = UnitMemberEntity.builder()
                .unit(unit)
                .user(user)
                .role(UnitMemberRole.MEMBER)
                .joinedAt(OffsetDateTime.now())
                .build();

        UnitMemberEntity saved = unitMemberRepository.save(member);
        return Mono.just(saved.toUnitMember());
    }

    @Transactional
    public Mono<Void> removeMemberFromUnit(UUID unitId, UUID userId, UUID removedBy) {
        log.info("Removing member {} from unit {} by {}", userId, unitId, removedBy);

        // Verify removedBy is admin (skip if null for admin operations)
        if (removedBy != null && !isUserAdminOfUnit(removedBy, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", removedBy.toString());
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId)
                .orElseThrow(
                        () -> new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", userId.toString()));

        // Don't allow removing the last admin
        if (member.getRole() == UnitMemberRole.ADMIN) {
            List<UnitMemberEntity> admins = unitMemberRepository.findByUnitIdAndRole(unitId, UnitMemberRole.ADMIN);
            if (admins.size() <= 1) {
                throw new CodedErrorException(CodedError.CANNOT_DEMOTE_LAST_ADMIN);
            }
        }

        unitMemberRepository.delete(member);

        // Disable user if they have no other units
        long unitCount = unitMemberRepository.countByUserId(userId);
        if (unitCount == 0) {
            UserEntity user = usersRepository.findById(userId)
                    .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));
            user.setDisabled(true);
            usersRepository.save(user);
            log.info("Disabled user {} as they have no units", userId);
        }

        return Mono.empty();
    }

    @Transactional
    public Mono<UnitMember> promoteToAdmin(UUID unitId, UUID userId, UUID promotedBy) {
        log.info("Promoting member {} to admin in unit {} by {}", userId, unitId, promotedBy);

        // Verify promotedBy is admin (skip if null for admin operations)
        if (promotedBy != null && !isUserAdminOfUnit(promotedBy, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", promotedBy.toString());
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId)
                .orElseThrow(
                        () -> new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", userId.toString()));

        member.setRole(UnitMemberRole.ADMIN);
        UnitMemberEntity saved = unitMemberRepository.save(member);
        return Mono.just(saved.toUnitMember());
    }

    @Transactional
    public Mono<UnitMember> demoteFromAdmin(UUID unitId, UUID userId, UUID demotedBy) {
        log.info("Demoting admin {} from unit {} by {}", userId, unitId, demotedBy);

        // Verify demotedBy is admin (skip if null for admin operations)
        if (demotedBy != null && !isUserAdminOfUnit(demotedBy, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", demotedBy.toString());
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId)
                .orElseThrow(
                        () -> new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", userId.toString()));

        // Don't allow demoting the last admin
        List<UnitMemberEntity> admins = unitMemberRepository.findByUnitIdAndRole(unitId, UnitMemberRole.ADMIN);
        if (admins.size() <= 1) {
            throw new CodedErrorException(CodedError.CANNOT_DEMOTE_LAST_ADMIN);
        }

        member.setRole(UnitMemberRole.MEMBER);
        UnitMemberEntity saved = unitMemberRepository.save(member);
        return Mono.just(saved.toUnitMember());
    }

    @Transactional(readOnly = true)
    public boolean isUserAdminOfUnit(UUID userId, UUID unitId) {
        Optional<UnitMemberEntity> member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId);
        return member.isPresent() && member.get().getRole() == UnitMemberRole.ADMIN;
    }

    @Transactional(readOnly = true)
    public boolean isUserMemberOfUnit(UUID userId, UUID unitId) {
        return unitMemberRepository.existsByUnitIdAndUserId(unitId, userId);
    }

    @Transactional(readOnly = true)
    public UserEntity determineDefaultAdmin(List<UserEntity> users) {
        if (users == null || users.isEmpty()) {
            return null;
        }

        // Priority: PROPERTY_MANAGEMENT > OWNER > LANDLORD > TENANT
        for (UserType type : List.of(UserType.PROPERTY_MANAGEMENT, UserType.OWNER, UserType.LANDLORD,
                UserType.TENANT)) {
            for (UserEntity user : users) {
                if (user.getType() == type) {
                    return user;
                }
            }
        }

        // If no match, return first user
        return users.get(0);
    }

    @Transactional(readOnly = true)
    public Flux<Unit> getUserUnits(UUID userId) {
        return getUnitsForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<UnitMemberEntity> getUnitAdmins(UUID unitId) {
        return unitMemberRepository.findByUnitIdAndRole(unitId, UnitMemberRole.ADMIN);
    }

    /**
     * Get the primary unit for a user (where user is TENANT or OWNER)
     * A user should normally only have one unit where they are TENANT or OWNER
     * If multiple exist (bug), returns the first one
     */
    @Transactional(readOnly = true)
    public Mono<UnitEntity> getPrimaryUnitForUser(UUID userId) {
        log.debug("Getting primary unit for user: {}", userId);

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

        // Get all units where user is a member
        List<UnitEntity> userUnits = unitRepository.findByMembersUserId(userId);

        // Filter to find units where user's type is OWNER or TENANT
        Optional<UnitEntity> primaryUnit = userUnits.stream()
                .filter(unit -> {
                    // Check if user is a member of this unit
                    List<UnitMemberEntity> members = unitMemberRepository.findByUnitId(unit.getId());
                    return members.stream()
                            .anyMatch(member -> member.getUser().getId().equals(userId)
                                    && (user.getType() == UserType.OWNER || user.getType() == UserType.TENANT));
                })
                .findFirst();

        if (primaryUnit.isEmpty()) {
            throw new CodedErrorException(CodedError.USER_NOT_TENANT_OR_OWNER,
                    Map.of("userId", userId.toString()));
        }

        return Mono.just(primaryUnit.get());
    }

    @Transactional(readOnly = true)
    public Mono<PaginatedUnits> getUnitsPaginated(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UnitEntity> pageResult;

        if (search != null && !search.trim().isEmpty()) {
            List<UnitEntity> units = unitRepository.findByNameContainingIgnoreCase(search);
            // Manual pagination for search results
            int start = page * size;
            int end = Math.min(start + size, units.size());
            List<UnitEntity> pagedUnits = start < units.size() ? units.subList(start, end) : List.of();
            pageResult = new org.springframework.data.domain.PageImpl<>(pagedUnits, pageable, units.size());
        } else {
            pageResult = unitRepository.findAll(pageable);
        }

        List<Unit> units = pageResult.getContent().stream()
                .map(UnitEntity::toUnit)
                .collect(Collectors.toList());

        PaginatedUnits paginatedUnits = PaginatedUnits.builder()
                .content(units)
                .totalElements((int) pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .number(pageResult.getNumber())
                .size(pageResult.getSize())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .numberOfElements((int) pageResult.getNumberOfElements())
                .build();

        return Mono.just(paginatedUnits);
    }
}
