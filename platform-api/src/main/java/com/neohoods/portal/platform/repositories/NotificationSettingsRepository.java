package com.neohoods.portal.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.NotificationSettingsEntity;
import com.neohoods.portal.platform.entities.UserEntity;

@Repository
public interface NotificationSettingsRepository extends CrudRepository<NotificationSettingsEntity, UUID> {
    Optional<NotificationSettingsEntity> findByUser(UserEntity user);
} 