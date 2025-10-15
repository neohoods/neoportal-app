package com.neohoods.portal.platform.spaces.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.DigitalLockEntity;
import com.neohoods.portal.platform.spaces.entities.DigitalLockStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.DigitalLockTypeForEntity;

@Repository
public interface DigitalLockRepository extends JpaRepository<DigitalLockEntity, UUID> {

    List<DigitalLockEntity> findByType(DigitalLockTypeForEntity type);

    List<DigitalLockEntity> findByStatus(DigitalLockStatusForEntity status);

    List<DigitalLockEntity> findByTypeAndStatus(DigitalLockTypeForEntity type, DigitalLockStatusForEntity status);

    @Query("SELECT dl FROM DigitalLockEntity dl WHERE " +
            "(:name IS NULL OR LOWER(dl.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:type IS NULL OR dl.type = :type) AND " +
            "(:status IS NULL OR dl.status = :status)")
    Page<DigitalLockEntity> findByFilters(
            @Param("name") String name,
            @Param("type") DigitalLockTypeForEntity type,
            @Param("status") DigitalLockStatusForEntity status,
            Pageable pageable);

    long countByStatus(DigitalLockStatusForEntity status);

    long countByType(DigitalLockTypeForEntity type);
}
