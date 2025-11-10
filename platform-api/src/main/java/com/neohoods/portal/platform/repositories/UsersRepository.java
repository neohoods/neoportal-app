package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;

public interface UsersRepository extends CrudRepository<UserEntity, UUID> {
    UserEntity findByUsername(String login);

    UserEntity findByEmail(String email);

    List<UserEntity> findByType(UserType type);
}
