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
import com.neohoods.portal.platform.entities.ResidenceRole;
import com.neohoods.portal.platform.entities.UnitMemberRole;
import com.neohoods.portal.platform.entities.UnitTypeForEntity;
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
    public Mono<Unit> createUnit(String name, UnitTypeForEntity type, UUID initialAdminId) {
        log.info("Creating unit: {} of type {} with initial admin: {}", name, type, initialAdminId);

        UnitEntity unit = UnitEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .type(type != null ? type : UnitTypeForEntity.FLAT)
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
                .residenceRole(ResidenceRole.PROPRIETAIRE) // Default to PROPRIETAIRE for admin
                .joinedAt(OffsetDateTime.now())
                .build();

        unitMemberRepository.save(adminMember);
        
        // If this is the user's first unit, set it as primary
        long unitCount = unitMemberRepository.countByUserId(adminId);
        if (unitCount == 1 && admin.getPrimaryUnit() == null) {
            admin.setPrimaryUnit(savedUnit);
            usersRepository.save(admin);
            log.info("Set unit {} as primary unit for user {} (first unit)", savedUnit.getId(), adminId);
        }

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
            // Check if user is global admin (ADMIN type)
            UserEntity addedByUser = usersRepository.findById(addedBy)
                    .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", addedBy.toString()));
            if (addedByUser.getType() != com.neohoods.portal.platform.entities.UserType.ADMIN) {
                throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", addedBy.toString());
            }
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
                .residenceRole(ResidenceRole.TENANT) // Default to TENANT for new members
                .joinedAt(OffsetDateTime.now())
                .build();

        UnitMemberEntity saved = unitMemberRepository.save(member);
        
        // If this is the user's first unit, set it as primary
        long unitCount = unitMemberRepository.countByUserId(userId);
        if (unitCount == 1 && user.getPrimaryUnit() == null) {
            user.setPrimaryUnit(unit);
            usersRepository.save(user);
            log.info("Set unit {} as primary unit for user {} (first unit)", unitId, userId);
        }
        
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

        // Update primary unit if needed
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));
        
        // If the removed unit was the primary unit, clear it or set a new one
        if (user.getPrimaryUnit() != null && user.getPrimaryUnit().getId().equals(unitId)) {
            long remainingUnitCount = unitMemberRepository.countByUserId(userId);
            if (remainingUnitCount == 0) {
                // No units left, clear primary unit
                user.setPrimaryUnit(null);
                user.setDisabled(true);
                log.info("Cleared primary unit and disabled user {} as they have no units", userId);
            } else {
                // Set first remaining unit as primary
                List<UnitEntity> remainingUnits = unitRepository.findByMembersUserId(userId);
                if (!remainingUnits.isEmpty()) {
                    user.setPrimaryUnit(remainingUnits.get(0));
                    log.info("Set unit {} as new primary unit for user {} after removal", remainingUnits.get(0).getId(), userId);
                }
            }
            usersRepository.save(user);
        } else if (unitMemberRepository.countByUserId(userId) == 0) {
            // Disable user if they have no other units
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
     * Get the primary unit for a user (explicit primary_unit_id field)
     * Returns the unit explicitly set as primary, or throws exception if not set
     */
    @Transactional(readOnly = true)
    public Mono<UnitEntity> getPrimaryUnitForUser(UUID userId) {
        log.debug("Getting primary unit for user: {}", userId);

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

        if (user.getPrimaryUnit() == null) {
            throw new CodedErrorException(CodedError.USER_NO_PRIMARY_UNIT,
                    Map.of("userId", userId.toString()));
        }

        return Mono.just(user.getPrimaryUnit());
    }

    /**
     * Set the primary unit for a user
     * @param userId The user ID
     * @param unitId The unit ID to set as primary
     * @param setBy The user ID who is setting this (must be admin of the unit or global admin, null for admin operations)
     */
    @Transactional
    public Mono<Void> setPrimaryUnitForUser(UUID userId, UUID unitId, UUID setBy) {
        log.info("Setting primary unit {} for user {} by {}", unitId, userId, setBy);

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        // Verify user is a member of the unit
        if (!isUserMemberOfUnit(userId, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_MEMBER_OF_UNIT,
                    Map.of("userId", userId.toString(), "unitId", unitId.toString()));
        }

        // Verify setBy is admin of the unit (skip if null for admin operations)
        if (setBy != null && !isUserAdminOfUnit(setBy, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", setBy.toString());
        }

        user.setPrimaryUnit(unit);
        usersRepository.save(user);

        log.info("Set unit {} as primary unit for user {}", unitId, userId);
        return Mono.empty();
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

    @Transactional(readOnly = true)
    public Mono<PaginatedUnits> getUnitsDirectoryPaginated(int page, int size, UnitTypeForEntity type, String search, UUID userId) {
        Pageable pageable = PageRequest.of(page, size);
        List<UnitEntity> filteredUnits;

        // Apply filters
        if (userId != null) {
            // Filter by user membership
            if (type != null) {
                filteredUnits = unitRepository.findByMembersUserIdAndType(userId, type);
            } else {
                filteredUnits = unitRepository.findByMembersUserId(userId);
            }
        } else if (type != null) {
            // Filter by type only
            if (search != null && !search.trim().isEmpty()) {
                filteredUnits = unitRepository.findByTypeAndNameContainingIgnoreCase(type, search);
            } else {
                filteredUnits = unitRepository.findByType(type);
            }
        } else if (search != null && !search.trim().isEmpty()) {
            // Filter by search only
            filteredUnits = unitRepository.findByNameContainingIgnoreCase(search);
        } else {
            // No filters - get all
            filteredUnits = unitRepository.findAll();
        }

        // Manual pagination
        int start = page * size;
        int end = Math.min(start + size, filteredUnits.size());
        List<UnitEntity> pagedUnits = start < filteredUnits.size() ? filteredUnits.subList(start, end) : List.of();
        Page<UnitEntity> pageResult = new org.springframework.data.domain.PageImpl<>(pagedUnits, pageable, filteredUnits.size());

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

    @Transactional(readOnly = true)
    public Mono<List<Unit>> getRelatedParkingGaragesForUser(UUID userId) {
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString()));

        // Check if user has a primary unit of type FLAT
        if (user.getPrimaryUnit() == null || user.getPrimaryUnit().getType() != UnitTypeForEntity.FLAT) {
            return Mono.just(List.of());
        }

        // Find GARAGE and PARKING units where user is PROPRIETAIRE
        List<UnitMemberEntity> proprietaireMembers = unitMemberRepository.findByUserIdAndResidenceRole(
                userId, ResidenceRole.PROPRIETAIRE);

        List<Unit> relatedUnits = proprietaireMembers.stream()
                .map(UnitMemberEntity::getUnit)
                .filter(unit -> unit.getType() == UnitTypeForEntity.GARAGE || unit.getType() == UnitTypeForEntity.PARKING)
                .map(UnitEntity::toUnit)
                .collect(Collectors.toList());

        return Mono.just(relatedUnits);
    }

    @Transactional
    public Mono<UnitMember> updateMemberResidenceRole(UUID unitId, UUID memberUserId, ResidenceRole residenceRole, UUID updatedBy) {
        log.info("Updating residence role for member {} in unit {} by {}", memberUserId, unitId, updatedBy);

        // Residence role is now required
        if (residenceRole == null) {
            throw new CodedErrorException(CodedError.INVALID_INPUT, "residenceRole", "Residence role is required");
        }

        // Check unit exists first
        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        // Verify updatedBy is admin (only if provided)
        if (updatedBy != null && !isUserAdminOfUnit(updatedBy, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", updatedBy.toString());
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, memberUserId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", memberUserId.toString()));

        member.setResidenceRole(residenceRole);
        UnitMemberEntity saved = unitMemberRepository.save(member);
        
        // Refresh to ensure we have the latest data
        unitMemberRepository.flush();
        saved = unitMemberRepository.findByUnitIdAndUserId(unitId, memberUserId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", memberUserId.toString()));

        log.info("Updated residence role for member {} in unit {} to {}", memberUserId, unitId, residenceRole);
        log.debug("Saved member residenceRole: {}", saved.getResidenceRole());
        UnitMember result = saved.toUnitMember();
        log.debug("Result member residenceRole: {}", result.getResidenceRole());
        return Mono.just(result);
    }
}
