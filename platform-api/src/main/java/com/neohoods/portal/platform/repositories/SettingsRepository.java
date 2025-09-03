package com.neohoods.portal.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.SettingsEntity;

@Repository
public interface SettingsRepository extends CrudRepository<SettingsEntity, UUID> {
    Optional<SettingsEntity> findTopByOrderByIdAsc();
}