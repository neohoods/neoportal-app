package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
