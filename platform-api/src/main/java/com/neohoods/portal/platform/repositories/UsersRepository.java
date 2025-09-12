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

    // Methods to fetch users with their properties using LEFT JOIN FETCH
    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.properties WHERE u.id = :id")
    Optional<UserEntity> findByIdWithProperties(@Param("id") UUID id);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.properties WHERE u.username = :username")
    Optional<UserEntity> findByUsernameWithProperties(@Param("username") String username);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.properties WHERE u.email = :email")
    Optional<UserEntity> findByEmailWithProperties(@Param("email") String email);

    @Query("SELECT DISTINCT u FROM UserEntity u LEFT JOIN FETCH u.properties")
    List<UserEntity> findAllWithProperties();

    List<UserEntity> findByType(UserType type);
}
