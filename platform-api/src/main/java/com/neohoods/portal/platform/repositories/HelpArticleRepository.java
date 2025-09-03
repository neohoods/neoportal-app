package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.HelpArticleEntity;

@Repository
public interface HelpArticleRepository extends CrudRepository<HelpArticleEntity, UUID> {
    List<HelpArticleEntity> findAllByCategoryId(UUID categoryId);
}