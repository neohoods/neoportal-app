package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.NewsletterLogEntity;
import com.neohoods.portal.platform.entities.NewsletterLogEntity.NewsletterLogStatus;

@Repository
public interface NewsletterLogRepository extends CrudRepository<NewsletterLogEntity, UUID> {

    List<NewsletterLogEntity> findByNewsletterIdOrderByCreatedAtDesc(UUID newsletterId);

    Page<NewsletterLogEntity> findByNewsletterIdOrderByCreatedAtDesc(UUID newsletterId, Pageable pageable);

    List<NewsletterLogEntity> findByNewsletterIdAndStatusOrderByCreatedAtDesc(UUID newsletterId, NewsletterLogStatus status);

    long countByNewsletterId(UUID newsletterId);

    long countByNewsletterIdAndStatus(UUID newsletterId, NewsletterLogStatus status);
}
