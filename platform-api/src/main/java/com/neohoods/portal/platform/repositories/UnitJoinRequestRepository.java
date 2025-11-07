package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.UnitJoinRequestEntity;
import com.neohoods.portal.platform.entities.UnitJoinRequestStatus;

@Repository
public interface UnitJoinRequestRepository extends JpaRepository<UnitJoinRequestEntity, UUID> {

    List<UnitJoinRequestEntity> findByUnitId(UUID unitId);

    List<UnitJoinRequestEntity> findByRequestedById(UUID requestedById);

    List<UnitJoinRequestEntity> findByUnitIdAndStatus(UUID unitId, UnitJoinRequestStatus status);

    List<UnitJoinRequestEntity> findByRequestedByIdAndStatus(UUID requestedById, UnitJoinRequestStatus status);

    Optional<UnitJoinRequestEntity> findByIdAndStatus(UUID id, UnitJoinRequestStatus status);

    boolean existsByUnitIdAndRequestedByIdAndStatus(UUID unitId, UUID requestedById, UnitJoinRequestStatus status);
}

