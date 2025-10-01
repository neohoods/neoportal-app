package com.neohoods.portal.platform.services;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.PropertyEntity;
import com.neohoods.portal.platform.entities.PropertyType;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.User;
import com.neohoods.portal.platform.repositories.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsersService {
        private final UsersRepository usersRepository;
        private final PasswordEncoder passwordEncoder;

        public Mono<User> getUserById(UUID id) {
                return Mono.justOrEmpty(usersRepository.findByIdWithProperties(id)
                                .map(UserEntity::toUser)
                                .map(User.UserBuilder::build)
                                .orElse(null));
        }

        public Flux<User> getUsers() {
                return Flux.fromIterable(usersRepository.findAllWithProperties())
                                .map(UserEntity::toUser)
                                .map(User.UserBuilder::build);
        }

        public Mono<User> updateProfile(UUID userId, User user) {
                log.info("Updating profile for user: {}", userId);

                UserEntity userEntity = usersRepository.findByIdWithProperties(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Check if email is already taken by another user
                if (user.getEmail() != null && !user.getEmail().equals(userEntity.getEmail())) {
                        UserEntity existingUserWithEmail = usersRepository.findByEmail(user.getEmail());
                        if (existingUserWithEmail != null && !existingUserWithEmail.getId().equals(userId)) {
                                log.warn("Email {} is already taken by user {}", user.getEmail(),
                                                existingUserWithEmail.getId());
                                throw new CodedErrorException(
                                                com.neohoods.portal.platform.exceptions.CodedError.EMAIL_ALREADY_EXISTS,
                                                "email", user.getEmail());
                        }
                }

                userEntity.setFirstName(user.getFirstName());
                userEntity.setLastName(user.getLastName());
                userEntity.setEmail(user.getEmail());
                userEntity.setFlatNumber(user.getFlatNumber());
                userEntity.setStreetAddress(user.getStreetAddress());
                userEntity.setCity(user.getCity());
                userEntity.setPostalCode(user.getPostalCode());
                userEntity.setCountry(user.getCountry());
                userEntity.setPreferredLanguage(user.getPreferredLanguage());
                userEntity.setAvatarUrl(user.getAvatarUrl());

                // Handle user type
                if (user.getType() != null) {
                        userEntity.setType(UserType.fromOpenApiUserType(user.getType()));
                }

                // Handle properties - replace entire collection
                if (user.getProperties() != null) {
                        // Create new properties list
                        List<PropertyEntity> newPropertyEntities = user.getProperties().stream()
                                        .map(property -> PropertyEntity.builder()
                                                        .type(PropertyType.fromOpenApiPropertyType(property.getType()))
                                                        .name(property.getName())
                                                        .user(userEntity)
                                                        .build())
                                        .collect(Collectors.toList());

                        // Replace the entire collection to ensure proper cascade handling
                        userEntity.setProperties(newPropertyEntities);
                }

                UserEntity savedEntity = usersRepository.save(userEntity);
                log.info("Profile updated successfully for user: {}", userId);
                // Reload the entity with properties to avoid lazy loading issues
                UserEntity reloadedEntity = usersRepository.findByIdWithProperties(savedEntity.getId())
                                .orElseThrow(() -> new RuntimeException("User not found after save"));
                return Mono.just(reloadedEntity.toUser().build());
        }

        public Mono<User> getProfile(UUID userId) {
                log.info("Get profile for user: {}", userId);
                UserEntity userEntity = usersRepository.findByIdWithProperties(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return Mono.just(userEntity.toUser().build());
        }

        public Mono<User> saveUser(User user) {
                UserEntity entity = user.getId() != null ? usersRepository.findByIdWithProperties(user.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"))
                                : UserEntity.builder()
                                                .id(UUID.randomUUID())
                                                .build();

                // Check if email is already taken by another user
                if (user.getEmail() != null) {
                        UserEntity existingUserWithEmail = usersRepository.findByEmail(user.getEmail());
                        if (existingUserWithEmail != null && !existingUserWithEmail.getId().equals(entity.getId())) {
                                log.warn("Email {} is already taken by user {}", user.getEmail(),
                                                existingUserWithEmail.getId());
                                throw new CodedErrorException(
                                                com.neohoods.portal.platform.exceptions.CodedError.EMAIL_ALREADY_EXISTS,
                                                "email", user.getEmail());
                        }
                }

                entity.setUsername(user.getUsername());
                entity.setFirstName(user.getFirstName());
                entity.setLastName(user.getLastName());
                entity.setEmail(user.getEmail());
                entity.setFlatNumber(user.getFlatNumber());
                entity.setStreetAddress(user.getStreetAddress());
                entity.setCity(user.getCity());
                entity.setPostalCode(user.getPostalCode());
                entity.setCountry(user.getCountry());
                entity.setPreferredLanguage(user.getPreferredLanguage());
                entity.setAvatarUrl(user.getAvatarUrl());
                entity.setDisabled(user.getDisabled());
                entity.setEmailVerified(user.getIsEmailVerified());
                entity.setRoles(new HashSet<>(user.getRoles()));

                // Handle user type
                if (user.getType() != null) {
                        entity.setType(UserType.fromOpenApiUserType(user.getType()));
                }

                // Handle properties
                if (user.getProperties() != null) {
                        List<PropertyEntity> propertyEntities = user.getProperties().stream()
                                        .map(property -> PropertyEntity.builder()
                                                        .type(PropertyType.fromOpenApiPropertyType(property.getType()))
                                                        .name(property.getName())
                                                        .user(entity)
                                                        .build())
                                        .collect(Collectors.toList());
                        entity.setProperties(propertyEntities);
                }

                UserEntity savedEntity = usersRepository.save(entity);
                return getUserById(savedEntity.getId());
        }

        public Mono<Void> setUserPassword(UUID userId, String password) {
                UserEntity user = usersRepository.findByIdWithProperties(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                user.setPassword(passwordEncoder.encode(password));
                usersRepository.save(user);
                return Mono.empty();
        }
}