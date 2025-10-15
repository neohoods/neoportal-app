package com.neohoods.portal.platform.spaces.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.SpaceSettingsEntity;

@Repository
public interface SpaceSettingsRepository extends JpaRepository<SpaceSettingsEntity, UUID> {

    /**
     * Find the most recent space settings (there should only be one global settings
     * record)
     * 
     * @return Optional containing the latest space settings
     */
    Optional<SpaceSettingsEntity> findFirstByOrderByCreatedAtDesc();
}
