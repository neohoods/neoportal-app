package com.neohoods.portal.platform.repositories;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.NewsletterEntity;
import com.neohoods.portal.platform.entities.NewsletterStatus;

@Repository
public interface NewsletterRepository extends CrudRepository<NewsletterEntity, UUID> {

    List<NewsletterEntity> findAllByOrderByCreatedAtDesc();

    List<NewsletterEntity> findByStatusOrderByCreatedAtDesc(NewsletterStatus status);

    Page<NewsletterEntity> findAll(Pageable pageable);

    Page<NewsletterEntity> findByStatus(NewsletterStatus status, Pageable pageable);

    List<NewsletterEntity> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    @Query("SELECT n FROM NewsletterEntity n WHERE n.status = :status AND n.scheduledAt <= :now")
    List<NewsletterEntity> findScheduledNewslettersReadyToSend(@Param("status") NewsletterStatus status,
            @Param("now") OffsetDateTime now);

    long countByStatus(NewsletterStatus status);
}
