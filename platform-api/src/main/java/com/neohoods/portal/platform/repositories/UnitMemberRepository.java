package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UnitMemberRole;

@Repository
public interface UnitMemberRepository extends JpaRepository<UnitMemberEntity, UUID> {

    List<UnitMemberEntity> findByUnitId(UUID unitId);

    List<UnitMemberEntity> findByUserId(UUID userId);

    Optional<UnitMemberEntity> findByUnitIdAndUserId(@Param("unitId") UUID unitId, @Param("userId") UUID userId);

    boolean existsByUnitIdAndUserId(UUID unitId, UUID userId);

    long countByUserId(UUID userId);

    List<UnitMemberEntity> findByUnitIdAndRole(UUID unitId, UnitMemberRole role);
}


