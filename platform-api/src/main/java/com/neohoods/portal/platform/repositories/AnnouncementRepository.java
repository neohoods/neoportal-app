package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.AnnouncementCategory;
import com.neohoods.portal.platform.entities.AnnouncementEntity;

@Repository
public interface AnnouncementRepository extends CrudRepository<AnnouncementEntity, UUID> {

    List<AnnouncementEntity> findAllByOrderByCreatedAtDesc();

    List<AnnouncementEntity> findByCategoryOrderByCreatedAtDesc(AnnouncementCategory category);
}
