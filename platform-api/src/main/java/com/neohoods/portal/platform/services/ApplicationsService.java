package com.neohoods.portal.platform.services;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.ApplicationEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.Application;
import com.neohoods.portal.platform.repositories.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationsService {
    private final ApplicationRepository applicationRepository;

    public Mono<Application> createApplication(Application application) {
        log.info("Creating application: {}", application.getName());
        ApplicationEntity entity = ApplicationEntity.fromApplication(application);
        ApplicationEntity savedEntity = applicationRepository.save(entity);
        log.info("Created application: {} with ID: {}", savedEntity.getName(), savedEntity.getId());
        return Mono.just(savedEntity.toApplication());
    }

    public Flux<Application> getApplications() {
        log.info("Retrieving all applications");
        return Flux.fromIterable(applicationRepository.findAllByOrderByName())
                .map(ApplicationEntity::toApplication)
                .doOnComplete(() -> log.info("Retrieved all applications"));
    }

    public Mono<Application> getApplication(UUID applicationId) {
        log.info("Retrieving application: {}", applicationId);
        return Mono.justOrEmpty(applicationRepository.findById(applicationId))
                .map(entity -> {
                    log.info("Found application: {}", entity.getName());
                    return entity.toApplication();
                })
                .switchIfEmpty(Mono.error(new CodedException(
                        CodedError.APPLICATION_NOT_FOUND.getCode(),
                        CodedError.APPLICATION_NOT_FOUND.getDefaultMessage(),
                        Map.of("applicationId", applicationId),
                        CodedError.APPLICATION_NOT_FOUND.getDocumentationUrl())));
    }

    public Mono<Application> updateApplication(UUID applicationId, Application application) {
        log.info("Updating application: {}", applicationId);
        ApplicationEntity existingEntity = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CodedException(
                        CodedError.APPLICATION_NOT_FOUND.getCode(),
                        CodedError.APPLICATION_NOT_FOUND.getDefaultMessage(),
                        Map.of("applicationId", applicationId),
                        CodedError.APPLICATION_NOT_FOUND.getDocumentationUrl()));

        existingEntity.setName(application.getName());
        existingEntity.setUrl(application.getUrl());
        existingEntity.setIcon(application.getIcon());
        existingEntity.setHelpText(application.getHelpText());
        existingEntity.setDisabled(application.getDisabled() != null ? application.getDisabled() : false);

        ApplicationEntity updatedEntity = applicationRepository.save(existingEntity);
        log.info("Updated application: {}", updatedEntity.getName());
        return Mono.just(updatedEntity.toApplication());
    }

    public Mono<Void> deleteApplication(UUID applicationId) {
        log.info("Deleting application: {}", applicationId);
        ApplicationEntity entity = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CodedException(
                        CodedError.APPLICATION_NOT_FOUND.getCode(),
                        CodedError.APPLICATION_NOT_FOUND.getDefaultMessage(),
                        Map.of("applicationId", applicationId),
                        CodedError.APPLICATION_NOT_FOUND.getDocumentationUrl()));

        applicationRepository.delete(entity);
        log.info("Deleted application: {}", entity.getName());
        return Mono.empty();
    }
}
