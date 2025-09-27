package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.EmailTemplateEntity;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplateEntity, UUID> {

    List<EmailTemplateEntity> findByType(String type);

    List<EmailTemplateEntity> findByTypeAndIsActive(String type, Boolean isActive);

    @Query("SELECT e FROM EmailTemplateEntity e WHERE e.type = :type AND e.isActive = true")
    Optional<EmailTemplateEntity> findActiveByType(@Param("type") String type);

    @Query("SELECT e FROM EmailTemplateEntity e WHERE e.type = :type ORDER BY e.createdAt DESC")
    List<EmailTemplateEntity> findByTypeOrderByCreatedAtDesc(@Param("type") String type);

    @Query("SELECT e FROM EmailTemplateEntity e WHERE e.isActive = true")
    List<EmailTemplateEntity> findAllActive();

    @Query("SELECT e FROM EmailTemplateEntity e WHERE e.type = :type AND e.id != :excludeId")
    List<EmailTemplateEntity> findByTypeExcludingId(@Param("type") String type, @Param("excludeId") UUID excludeId);
}
