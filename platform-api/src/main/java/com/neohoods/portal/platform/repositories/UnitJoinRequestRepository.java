package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.UnitJoinRequestEntity;
import com.neohoods.portal.platform.entities.UnitJoinRequestStatus;

@Repository
public interface UnitJoinRequestRepository extends JpaRepository<UnitJoinRequestEntity, UUID> {

    List<UnitJoinRequestEntity> findByUnitId(UUID unitId);

    List<UnitJoinRequestEntity> findByRequestedById(UUID requestedById);

    List<UnitJoinRequestEntity> findByUnitIdAndStatus(UUID unitId, UnitJoinRequestStatus status);

    List<UnitJoinRequestEntity> findByRequestedByIdAndStatus(UUID requestedById, UnitJoinRequestStatus status);

    @Query("SELECT DISTINCT r FROM UnitJoinRequestEntity r " +
           "LEFT JOIN FETCH r.unit u " +
           "LEFT JOIN FETCH u.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH r.requestedBy " +
           "LEFT JOIN FETCH r.respondedBy " +
           "WHERE r.status = :status")
    List<UnitJoinRequestEntity> findByStatusWithFetches(@Param("status") UnitJoinRequestStatus status);

    @Query("SELECT DISTINCT r FROM UnitJoinRequestEntity r " +
           "LEFT JOIN FETCH r.unit u " +
           "LEFT JOIN FETCH u.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH r.requestedBy " +
           "LEFT JOIN FETCH r.respondedBy " +
           "WHERE r.unit.id = :unitId AND r.status = :status")
    List<UnitJoinRequestEntity> findByUnitIdAndStatusWithFetches(@Param("unitId") UUID unitId, @Param("status") UnitJoinRequestStatus status);

    @Query("SELECT DISTINCT r FROM UnitJoinRequestEntity r " +
           "LEFT JOIN FETCH r.unit u " +
           "LEFT JOIN FETCH u.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH r.requestedBy " +
           "LEFT JOIN FETCH r.respondedBy " +
           "WHERE r.requestedBy.id = :requestedById AND r.status = :status")
    List<UnitJoinRequestEntity> findByRequestedByIdAndStatusWithFetches(@Param("requestedById") UUID requestedById, @Param("status") UnitJoinRequestStatus status);

    List<UnitJoinRequestEntity> findByStatus(UnitJoinRequestStatus status);

    @Query("SELECT DISTINCT r FROM UnitJoinRequestEntity r " +
           "LEFT JOIN FETCH r.unit u " +
           "LEFT JOIN FETCH u.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH r.requestedBy " +
           "LEFT JOIN FETCH r.respondedBy " +
           "WHERE r.id = :id")
    Optional<UnitJoinRequestEntity> findByIdWithFetches(@Param("id") UUID id);

    @Query("SELECT DISTINCT r FROM UnitJoinRequestEntity r " +
           "LEFT JOIN FETCH r.unit u " +
           "LEFT JOIN FETCH u.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH r.requestedBy " +
           "LEFT JOIN FETCH r.respondedBy " +
           "WHERE r.id = :id AND r.status = :status")
    Optional<UnitJoinRequestEntity> findByIdAndStatusWithFetches(@Param("id") UUID id, @Param("status") UnitJoinRequestStatus status);

    Optional<UnitJoinRequestEntity> findByIdAndStatus(UUID id, UnitJoinRequestStatus status);

    boolean existsByUnitIdAndRequestedByIdAndStatus(UUID unitId, UUID requestedById, UnitJoinRequestStatus status);
}

