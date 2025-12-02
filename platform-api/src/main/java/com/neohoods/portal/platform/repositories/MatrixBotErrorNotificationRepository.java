package com.neohoods.portal.platform.repositories;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import com.neohoods.portal.platform.entities.MatrixBotErrorNotificationEntity;

public interface MatrixBotErrorNotificationRepository extends CrudRepository<MatrixBotErrorNotificationEntity, UUID> {
    Optional<MatrixBotErrorNotificationEntity> findByLastNotificationDate(LocalDate date);
}




















