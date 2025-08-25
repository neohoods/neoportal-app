package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.SettingsEntity;

@Repository
public interface SettingsRepository extends CrudRepository<SettingsEntity, String> {
    Optional<SettingsEntity> findTopByOrderByIdAsc();
}