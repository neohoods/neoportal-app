package com.neohoods.portal.platform.spaces.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.TtlockDeviceEntity;
import com.neohoods.portal.platform.spaces.entities.TtlockDeviceStatusForEntity;

@Repository
public interface TtlockDeviceRepository extends JpaRepository<TtlockDeviceEntity, UUID> {

    List<TtlockDeviceEntity> findByStatus(TtlockDeviceStatusForEntity status);

    TtlockDeviceEntity findByDeviceId(String deviceId);

    @Query("SELECT t FROM TtlockDeviceEntity t WHERE t.status = :status")
    List<TtlockDeviceEntity> findActiveDevices(@Param("status") TtlockDeviceStatusForEntity status);

    @Query("SELECT COUNT(t) FROM TtlockDeviceEntity t WHERE t.status = :status")
    long countByStatus(@Param("status") TtlockDeviceStatusForEntity status);
}
