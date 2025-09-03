package com.neohoods.portal.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.InfoEntity;

@Repository
public interface InfoRepository extends CrudRepository<InfoEntity, UUID> {

    @Query("SELECT DISTINCT i FROM InfoEntity i LEFT JOIN FETCH i.delegates WHERE i.id = :id")
    Optional<InfoEntity> findByIdWithDelegates(@Param("id") UUID id);

    @Query("SELECT DISTINCT i FROM InfoEntity i LEFT JOIN FETCH i.contactNumbers WHERE i.id = :id")
    Optional<InfoEntity> findByIdWithContactNumbers(@Param("id") UUID id);
}
