package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.NotificationEntity;

@Repository
public interface NotificationRepository extends CrudRepository<NotificationEntity, UUID> {
    @Query("SELECT n FROM NotificationEntity n WHERE n.userId = :userId")
    List<NotificationEntity> findAllByUserId(UUID userId);

    long countByUserIdAndAlreadyReadFalse(UUID userId);
}