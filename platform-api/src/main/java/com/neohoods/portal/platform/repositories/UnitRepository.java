package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitTypeForEntity;

@Repository
public interface UnitRepository extends JpaRepository<UnitEntity, UUID> {

    @Query("SELECT DISTINCT u FROM UnitEntity u LEFT JOIN FETCH u.members m LEFT JOIN FETCH m.user WHERE u.id = :id")
    Optional<UnitEntity> findByIdWithMembers(@Param("id") UUID id);

    @Query("SELECT DISTINCT u FROM UnitEntity u LEFT JOIN FETCH u.members m LEFT JOIN FETCH m.user WHERE u.id IN (SELECT DISTINCT um.unit.id FROM UnitMemberEntity um WHERE um.user.id = :userId)")
    List<UnitEntity> findByMembersUserId(@Param("userId") UUID userId);

    List<UnitEntity> findByNameContainingIgnoreCase(String name);

    List<UnitEntity> findByType(UnitTypeForEntity type);

    List<UnitEntity> findByTypeAndNameContainingIgnoreCase(UnitTypeForEntity type, String name);

    @Query("SELECT DISTINCT u FROM UnitEntity u LEFT JOIN FETCH u.members m LEFT JOIN FETCH m.user WHERE u.id IN (SELECT DISTINCT um.unit.id FROM UnitMemberEntity um WHERE um.user.id = :userId) AND u.type = :type")
    List<UnitEntity> findByMembersUserIdAndType(@Param("userId") UUID userId, @Param("type") UnitTypeForEntity type);

    /**
     * Find units with filtering and sorting for directory pagination.
     * Sorting order:
     * 1. By type: FLAT > COMMERCIAL > GARAGE > PARKING > OTHER
     * 2. By name (alphanumeric)
     */
    @Query(value = """
            SELECT u.id, u.name, u.type, u.created_at, u.updated_at FROM units u
            WHERE
                (:userId IS NULL OR u.id IN (SELECT DISTINCT um.unit_id FROM unit_members um WHERE um.user_id = CAST(:userId AS uuid)))
                AND (:typeStr IS NULL OR u.type = :typeStr)
                AND (:search IS NULL OR LOWER(u.name) LIKE LOWER('%' || :search || '%'))
                AND (:onlyOccupied IS NULL OR :onlyOccupied = false OR u.id IN (SELECT DISTINCT um2.unit_id FROM unit_members um2))
            ORDER BY
                CASE u.type
                    WHEN 'FLAT' THEN 1
                    WHEN 'COMMERCIAL' THEN 2
                    WHEN 'GARAGE' THEN 3
                    WHEN 'PARKING' THEN 4
                    WHEN 'OTHER' THEN 5
                    ELSE 99
                END,
                u.name ASC
            """, countQuery = """
            SELECT COUNT(DISTINCT u.id) FROM units u
            WHERE
                (:userId IS NULL OR u.id IN (SELECT DISTINCT um.unit_id FROM unit_members um WHERE um.user_id = CAST(:userId AS uuid)))
                AND (:typeStr IS NULL OR u.type = :typeStr)
                AND (:search IS NULL OR LOWER(u.name) LIKE LOWER('%' || :search || '%'))
                AND (:onlyOccupied IS NULL OR :onlyOccupied = false OR u.id IN (SELECT DISTINCT um2.unit_id FROM unit_members um2))
            """, nativeQuery = true)
    Page<UnitEntity> findUnitsDirectoryPaginated(
            @Param("userId") UUID userId,
            @Param("typeStr") String typeStr,
            @Param("search") String search,
            @Param("onlyOccupied") Boolean onlyOccupied,
            Pageable pageable);
}
