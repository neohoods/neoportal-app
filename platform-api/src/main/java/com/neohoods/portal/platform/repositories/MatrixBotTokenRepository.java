package com.neohoods.portal.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import com.neohoods.portal.platform.entities.MatrixBotTokenEntity;

public interface MatrixBotTokenRepository extends CrudRepository<MatrixBotTokenEntity, UUID> {
    Optional<MatrixBotTokenEntity> findFirstByOrderByCreatedAtDesc();
}




























