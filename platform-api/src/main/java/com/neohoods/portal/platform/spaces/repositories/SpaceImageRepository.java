package com.neohoods.portal.platform.spaces.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.spaces.entities.SpaceImageEntity;

@Repository
public interface SpaceImageRepository extends JpaRepository<SpaceImageEntity, UUID> {

    List<SpaceImageEntity> findBySpaceId(UUID spaceId);

    void deleteBySpaceId(UUID spaceId);
}