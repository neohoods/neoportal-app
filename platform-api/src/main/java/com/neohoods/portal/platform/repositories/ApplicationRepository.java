package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.ApplicationEntity;

@Repository
public interface ApplicationRepository extends CrudRepository<ApplicationEntity, UUID> {
    List<ApplicationEntity> findAllByOrderByName();

    List<ApplicationEntity> findByDisabledFalseOrderByName();
}
