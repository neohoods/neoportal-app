package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.CustomPageEntity;
import com.neohoods.portal.platform.entities.CustomPagePosition;

@Repository
public interface CustomPageRepository extends CrudRepository<CustomPageEntity, UUID> {

    List<CustomPageEntity> findAllByOrderByOrderAsc();

    List<CustomPageEntity> findByPositionOrderByOrderAsc(CustomPagePosition position);

    Optional<CustomPageEntity> findByRef(String ref);

    void deleteByRef(String ref);
}