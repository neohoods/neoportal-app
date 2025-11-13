package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.Comparator;
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

import com.neohoods.portal.platform.entities.ResidenceRole;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
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
        log.debug("Saved unit entity {} with ID: {}", name, savedUnit.getId());

        // Add initial admin
        UUID finalAdminId;
        if (initialAdminId == null) {
            log.debug("No initial admin specified, determining default admin");
            // Determine default admin from users
            List<UserEntity> allUsers = new java.util.ArrayList<>();
            usersRepository.findAll().forEach(allUsers::add);
            UserEntity defaultAdmin = determineDefaultAdmin(allUsers);
            if (defaultAdmin == null) {
                log.error("No users available to assign as admin for unit {}", savedUnit.getId());
                throw new CodedErrorException(CodedError.USER_NOT_FOUND,
                        Map.of("message", "No users available to assign as admin"));
            }
            finalAdminId = defaultAdmin.getId();
            log.debug("Determined default admin: {} ({})", defaultAdmin.getEmail(), finalAdminId);
        } else {
            finalAdminId = initialAdminId;
            log.debug("Using specified initial admin: {}", finalAdminId);
        }

        final UUID adminId = finalAdminId;
        UserEntity admin = usersRepository.findById(adminId)
                .orElseThrow(() -> {
                    log.error("Admin user {} not found when creating unit", adminId);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", adminId.toString());
                });
        log.debug("Found admin user: {} ({})", admin.getEmail(), adminId);

        UnitMemberEntity adminMember = UnitMemberEntity.builder()
                .unit(savedUnit)
                .user(admin)
                .role(UnitMemberRole.ADMIN)
                .residenceRole(ResidenceRole.PROPRIETAIRE) // Default to PROPRIETAIRE for admin
                .joinedAt(OffsetDateTime.now())
                .build();

        unitMemberRepository.save(adminMember);
        log.debug("Added user {} as ADMIN to unit {}", adminId, savedUnit.getId());

        // If this is the user's first unit, set it as primary
        long unitCount = unitMemberRepository.countByUserId(adminId);
        if (unitCount == 1 && admin.getPrimaryUnit() == null) {
            admin.setPrimaryUnit(savedUnit);
            usersRepository.save(admin);
            log.info("Set unit {} as primary unit for user {} (first unit)", savedUnit.getId(), adminId);
        }

        log.info("Created unit: {} with ID: {} and admin: {}", name, savedUnit.getId(), adminId);
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
                .orElseThrow(() -> {
                    log.error("Unit {} not found when updating", unitId);
                    return new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString());
                });
        log.debug("Found unit: {} (current name: {})", unitId, unit.getName());

        String oldName = unit.getName();
        unit.setName(name);
        unit.setUpdatedAt(OffsetDateTime.now());

        UnitEntity saved = unitRepository.save(unit);
        log.info("Updated unit {}: name changed from '{}' to '{}'", unitId, oldName, name);
        return Mono.just(getUnitById(saved.getId()).block());
    }

    @Transactional
    public Mono<Void> deleteUnit(UUID unitId) {
        log.info("Deleting unit: {}", unitId);
        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> {
                    log.error("Unit {} not found when deleting", unitId);
                    return new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString());
                });
        log.debug("Found unit to delete: {} ({})", unit.getName(), unitId);

        // Get all members before deletion
        List<UnitMemberEntity> members = unitMemberRepository.findByUnitId(unitId);
        log.debug("Unit {} has {} member(s) that will be affected", unitId, members.size());

        // Delete unit (cascade will delete members)
        unitRepository.delete(unit);
        log.info("Deleted unit {} ({})", unit.getName(), unitId);

        // Disable users who have no other units
        int disabledCount = 0;
        for (UnitMemberEntity member : members) {
            long unitCount = unitMemberRepository.countByUserId(member.getUser().getId());
            if (unitCount == 0) {
                UserEntity user = member.getUser();
                user.setDisabled(true);
                usersRepository.save(user);
                disabledCount++;
                log.info("Disabled user {} as they have no units after deletion of unit {}", user.getId(), unitId);
            }
        }
        if (disabledCount > 0) {
            log.info("Disabled {} user(s) after deletion of unit {}", disabledCount, unitId);
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
                    .orElseThrow(() -> {
                        log.error("User {} not found when adding member", addedBy);
                        return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", addedBy.toString());
                    });
            if (addedByUser.getType() != UserType.ADMIN) {
                log.warn("User {} attempted to add member {} to unit {} but is not admin", addedBy, userId, unitId);
                throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", addedBy.toString());
            }
            log.debug("User {} is global ADMIN, allowed to add member to unit {}", addedBy, unitId);
        } else if (addedBy != null) {
            log.debug("Verified that user {} is admin of unit {}", addedBy, unitId);
        }

        UnitEntity unit = unitRepository.findById(unitId)
                .orElseThrow(() -> {
                    log.error("Unit {} not found when adding member", unitId);
                    return new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString());
                });
        log.debug("Found unit: {} ({})", unit.getName(), unitId);

        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User {} not found when adding to unit", userId);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                });
        log.debug("Found user to add: {} ({})", user.getEmail(), userId);

        // Check if already a member
        if (unitMemberRepository.existsByUnitIdAndUserId(unitId, userId)) {
            log.warn("User {} is already a member of unit {}", userId, unitId);
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
        log.info("Added user {} as MEMBER to unit {} with residence role TENANT", userId, unitId);

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
            log.warn("User {} attempted to remove member {} from unit {} but is not admin", removedBy, userId, unitId);
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", removedBy.toString());
        }
        if (removedBy != null) {
            log.debug("Verified that user {} is admin of unit {}", removedBy, unitId);
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId)
                .orElseThrow(() -> {
                    log.error("Member {} not found in unit {}", userId, unitId);
                    return new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", userId.toString());
                });
        log.debug("Found member {} with role {} in unit {}", userId, member.getRole(), unitId);

        // Don't allow removing the last admin
        if (member.getRole() == UnitMemberRole.ADMIN) {
            List<UnitMemberEntity> admins = unitMemberRepository.findByUnitIdAndRole(unitId, UnitMemberRole.ADMIN);
            if (admins.size() <= 1) {
                log.warn("Attempted to remove last admin {} from unit {}", userId, unitId);
                throw new CodedErrorException(CodedError.CANNOT_DEMOTE_LAST_ADMIN);
            }
            log.debug("Unit {} has {} admin(s), removing admin {} is allowed", unitId, admins.size(), userId);
        }

        unitMemberRepository.delete(member);
        log.info("Removed member {} from unit {}", userId, unitId);

        // Update primary unit if needed
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User {} not found after removing from unit", userId);
                    return new CodedErrorException(CodedError.USER_NOT_FOUND, "userId", userId.toString());
                });

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
            log.warn("User {} attempted to promote member {} in unit {} but is not admin", promotedBy, userId, unitId);
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", promotedBy.toString());
        }
        if (promotedBy != null) {
            log.debug("Verified that user {} is admin of unit {}", promotedBy, unitId);
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId)
                .orElseThrow(() -> {
                    log.error("Member {} not found in unit {} when promoting", userId, unitId);
                    return new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", userId.toString());
                });
        log.debug("Found member {} with current role {} in unit {}", userId, member.getRole(), unitId);

        member.setRole(UnitMemberRole.ADMIN);
        UnitMemberEntity saved = unitMemberRepository.save(member);
        log.info("Promoted member {} to ADMIN in unit {}", userId, unitId);
        return Mono.just(saved.toUnitMember());
    }

    @Transactional
    public Mono<UnitMember> demoteFromAdmin(UUID unitId, UUID userId, UUID demotedBy) {
        log.info("Demoting admin {} from unit {} by {}", userId, unitId, demotedBy);

        // Verify demotedBy is admin (skip if null for admin operations)
        if (demotedBy != null && !isUserAdminOfUnit(demotedBy, unitId)) {
            log.warn("User {} attempted to demote admin {} in unit {} but is not admin", demotedBy, userId, unitId);
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", demotedBy.toString());
        }
        if (demotedBy != null) {
            log.debug("Verified that user {} is admin of unit {}", demotedBy, unitId);
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId)
                .orElseThrow(() -> {
                    log.error("Member {} not found in unit {} when demoting", userId, unitId);
                    return new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", userId.toString());
                });
        log.debug("Found member {} with current role {} in unit {}", userId, member.getRole(), unitId);

        // Don't allow demoting the last admin
        List<UnitMemberEntity> admins = unitMemberRepository.findByUnitIdAndRole(unitId, UnitMemberRole.ADMIN);
        if (admins.size() <= 1) {
            log.warn("Attempted to demote last admin {} from unit {}", userId, unitId);
            throw new CodedErrorException(CodedError.CANNOT_DEMOTE_LAST_ADMIN);
        }
        log.debug("Unit {} has {} admin(s), demoting admin {} is allowed", unitId, admins.size(), userId);

        member.setRole(UnitMemberRole.MEMBER);
        UnitMemberEntity saved = unitMemberRepository.save(member);
        log.info("Demoted admin {} to MEMBER in unit {}", userId, unitId);
        return Mono.just(saved.toUnitMember());
    }

    @Transactional(readOnly = true)
    public boolean isUserAdminOfUnit(UUID userId, UUID unitId) {
        log.debug("Checking if user {} is admin of unit {}", userId, unitId);
        Optional<UnitMemberEntity> member = unitMemberRepository.findByUnitIdAndUserId(unitId, userId);
        boolean isAdmin = member.isPresent() && member.get().getRole() == UnitMemberRole.ADMIN;
        log.debug("User {} {} admin of unit {}", userId, isAdmin ? "is" : "is not", unitId);
        return isAdmin;
    }

    @Transactional(readOnly = true)
    public boolean isUserMemberOfUnit(UUID userId, UUID unitId) {
        log.debug("Checking if user {} is member of unit {}", userId, unitId);
        boolean isMember = unitMemberRepository.existsByUnitIdAndUserId(unitId, userId);
        log.debug("User {} {} member of unit {}", userId, isMember ? "is" : "is not", unitId);
        return isMember;
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
        return users.getFirst();
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
     *
     * @param userId The user ID
     * @param unitId The unit ID to set as primary
     * @param setBy  The user ID who is setting this (must be admin of the unit or global admin, null for admin operations)
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
                .numberOfElements(pageResult.getNumberOfElements())
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

        // Sort by unit type: FLAT first, then COMMERCIAL, then GARAGE, then others
        Map<UnitTypeForEntity, Integer> typeOrder = Map.of(
                UnitTypeForEntity.FLAT, 1,
                UnitTypeForEntity.COMMERCIAL, 2,
                UnitTypeForEntity.GARAGE, 3,
                UnitTypeForEntity.PARKING, 4,
                UnitTypeForEntity.OTHER, 5
        );
        filteredUnits.sort(Comparator.comparing(
                unit -> typeOrder.getOrDefault(unit.getType(), 99),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

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
                .numberOfElements(pageResult.getNumberOfElements())
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
        unitRepository.findById(unitId).orElseThrow(() -> new CodedErrorException(CodedError.UNIT_NOT_FOUND, "unitId", unitId.toString()));

        // Verify updatedBy is admin (only if provided)
        if (updatedBy != null && !isUserAdminOfUnit(updatedBy, unitId)) {
            throw new CodedErrorException(CodedError.USER_NOT_ADMIN_OF_UNIT, "userId", updatedBy.toString());
        }

        UnitMemberEntity member = unitMemberRepository.findByUnitIdAndUserId(unitId, memberUserId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", memberUserId.toString()));

        member.setResidenceRole(residenceRole);
        unitMemberRepository.save(member);

        // Refresh to ensure we have the latest data
        unitMemberRepository.flush();
        UnitMemberEntity saved = unitMemberRepository.findByUnitIdAndUserId(unitId, memberUserId)
                .orElseThrow(() -> new CodedErrorException(CodedError.UNIT_MEMBER_NOT_FOUND, "userId", memberUserId.toString()));

        log.info("Updated residence role for member {} in unit {} to {}", memberUserId, unitId, residenceRole);
        log.debug("Saved member residenceRole: {}", saved.getResidenceRole());
        UnitMember result = saved.toUnitMember();
        log.debug("Result member residenceRole: {}", result.getResidenceRole());
        return Mono.just(result);
    }
}
