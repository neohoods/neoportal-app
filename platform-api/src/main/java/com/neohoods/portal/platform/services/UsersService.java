package com.neohoods.portal.platform.services;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.User;
import com.neohoods.portal.platform.model.UserBasic;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UsersService {
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final java.util.Optional<MatrixAssistantService> matrixAssistantService;

    public UsersService(UsersRepository usersRepository, PasswordEncoder passwordEncoder, 
                        java.util.Optional<MatrixAssistantService> matrixAssistantService) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
        this.matrixAssistantService = matrixAssistantService;
    }

    public Mono<User> getUserById(UUID id) {
        return Mono.justOrEmpty(usersRepository.findById(id)
                .map(UserEntity::toUser)
                .map(User.UserBuilder::build)
                .orElse(null));
    }

    public Flux<User> getUsers() {
        return Flux.fromIterable(usersRepository.findAll())
                .map(UserEntity::toUser)
                .map(User.UserBuilder::build);
    }

    public Flux<UserBasic> getUsersBasic() {
        return Flux.fromIterable(usersRepository.findAll())
                .map(userEntity -> {
                    UserBasic.UserBasicBuilder builder = UserBasic.builder();
                    if (userEntity.getId() != null) {
                        builder.id(userEntity.getId());
                    }
                    if (userEntity.getFirstName() != null) {
                        builder.firstName(userEntity.getFirstName());
                    }
                    if (userEntity.getLastName() != null) {
                        builder.lastName(userEntity.getLastName());
                    }
                    if (userEntity.getEmail() != null) {
                        builder.email(userEntity.getEmail());
                    }
                    return builder.build();
                });
    }

    public Flux<User> getUsersByIds(List<UUID> userIds) {
        return Flux.fromIterable(usersRepository.findAllById(userIds))
                .map(UserEntity::toUser)
                .map(User.UserBuilder::build);
    }

    public Mono<User> updateProfile(UUID userId, User user) {
        log.info("Updating profile for user: {}", userId);

        UserEntity userEntity = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if email is already taken by another user
        if (user.getEmail() != null && !user.getEmail().equals(userEntity.getEmail())) {
            UserEntity existingUserWithEmail = usersRepository.findByEmail(user.getEmail());
            if (existingUserWithEmail != null && !existingUserWithEmail.getId().equals(userId)) {
                log.warn("Email {} is already taken by user {}", user.getEmail(), existingUserWithEmail.getId());
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
        userEntity.setProfileSharingConsent(user.getProfileSharingConsent());

        // Handle user type - only set if not already defined (immutable once set)
        if (user.getType() != null && userEntity.getType() == null) {
            userEntity.setType(UserType.fromOpenApiUserType(user.getType()));
        }

        UserEntity savedEntity = usersRepository.save(userEntity);
        log.info("Profile updated successfully for user: {}", userId);
        UserEntity reloadedEntity = usersRepository.findById(savedEntity.getId())
                .orElseThrow(() -> new RuntimeException("User not found after save"));
        return Mono.just(reloadedEntity.toUser().build());
    }

    public Mono<User> getProfile(UUID userId) {
        log.info("Get profile for user: {}", userId);
        UserEntity userEntity = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return Mono.just(userEntity.toUser().build());
    }

    public Mono<User> saveUser(User user) {
        UserEntity entity = user.getId() != null ? usersRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"))
                : UserEntity.builder()
                .id(UUID.randomUUID())
                .build();

        // Check if email is already taken by another user
        if (user.getEmail() != null) {
            UserEntity existingUserWithEmail = usersRepository.findByEmail(user.getEmail());
            if (existingUserWithEmail != null && !existingUserWithEmail.getId().equals(entity.getId())) {
                log.warn("Email {} is already taken by user {}", user.getEmail(), existingUserWithEmail.getId());
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
        entity.setProfileSharingConsent(user.getProfileSharingConsent());
        entity.setDisabled(user.getDisabled());
        entity.setEmailVerified(user.getIsEmailVerified());
        entity.setRoles(new HashSet<>(user.getRoles()));

        if (user.getId() == null) {
            entity.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        }
        // Handle user type
        if (user.getType() != null) {
            entity.setType(UserType.fromOpenApiUserType(user.getType()));
        }

        UserEntity savedEntity = usersRepository.save(entity);
        
        // Handle new user in Matrix bot if enabled
        if (user.getId() == null && matrixAssistantService.isPresent()) {
            try {
                matrixAssistantService.get().handleNewUser(savedEntity);
            } catch (Exception e) {
                log.error("Failed to handle new user in Matrix bot", e);
                // Don't fail user creation if Matrix fails
            }
        }
        
        return getUserById(savedEntity.getId());
    }

    public void setUserPassword(UUID userId, String password) {
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(password));
        usersRepository.save(user);
    }

    public Mono<Boolean> deleteUser(UUID userId) {
        log.info("Deleting user: {}", userId);

        Optional<UserEntity> userOpt = usersRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found for deletion: {}", userId);
            return Mono.just(false);
        }

        UserEntity user = userOpt.get();
        usersRepository.delete(user);
        log.info("User deleted successfully: {}", userId);
        return Mono.just(true);
    }
}