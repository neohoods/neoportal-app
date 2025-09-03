package com.neohoods.portal.platform.services;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.CustomPageEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.CustomPage;
import com.neohoods.portal.platform.model.GetCustomPageRefs200ResponseInner;
import com.neohoods.portal.platform.repositories.CustomPageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomPagesService {
    private final CustomPageRepository customPageRepository;

    public Mono<CustomPage> createCustomPage(CustomPage customPage) {
        log.info("Creating custom page: {}", customPage.getRef());

        CustomPageEntity entity = CustomPageEntity.fromCustomPage(customPage);
        CustomPageEntity savedEntity = customPageRepository.save(entity);
        log.info("Created custom page: {} with ID: {}", savedEntity.getRef(), savedEntity.getId());
        return Mono.just(savedEntity.toCustomPage());
    }

    public Flux<CustomPage> getCustomPages() {
        log.info("Retrieving all custom pages");
        return Flux.fromIterable(customPageRepository.findAllByOrderByOrderAsc())
                .map(CustomPageEntity::toCustomPage)
                .doOnComplete(() -> log.info("Retrieved all custom pages"));
    }

    public Flux<GetCustomPageRefs200ResponseInner> getCustomPageRefs() {
        log.info("Retrieving custom page references");
        return Flux.fromIterable(customPageRepository.findAllByOrderByOrderAsc())
                .map(entity -> GetCustomPageRefs200ResponseInner.builder()
                        .ref(entity.getRef())
                        .position(
                                entity.getPosition() != null
                                        ? GetCustomPageRefs200ResponseInner.PositionEnum
                                                .fromValue(entity.getPosition().getValue())
                                        : null)
                        .title(entity.getTitle())
                        .build())
                .doOnComplete(() -> log.info("Retrieved custom page references"));
    }

    public Mono<CustomPage> getCustomPage(String pageRef) {
        log.info("Retrieving custom page: {}", pageRef);
        return Mono.justOrEmpty(customPageRepository.findByRef(pageRef))
                .map(entity -> {
                    log.info("Found custom page: {}", entity.getTitle());
                    return entity.toCustomPage();
                })
                .switchIfEmpty(Mono.error(new CodedException(
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getCode(),
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getDefaultMessage(),
                        Map.of("pageRef", pageRef),
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getDocumentationUrl())));
    }

    public Mono<CustomPage> updateCustomPage(String pageRef, CustomPage customPage) {
        log.info("Updating custom page: {}", pageRef);

        CustomPageEntity existingEntity = customPageRepository.findByRef(pageRef)
                .orElseThrow(() -> new CodedException(
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getCode(),
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getDefaultMessage(),
                        Map.of("pageRef", pageRef),
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getDocumentationUrl()));

        // Update fields
        existingEntity.setRef(customPage.getRef());
        existingEntity.setOrder(customPage.getOrder());
        existingEntity.setPosition(customPage.getPosition() != null
                ? com.neohoods.portal.platform.entities.CustomPagePosition.fromOpenApiPosition(customPage.getPosition())
                : null);
        existingEntity.setTitle(customPage.getTitle());
        existingEntity.setContent(customPage.getContent());

        CustomPageEntity updatedEntity = customPageRepository.save(existingEntity);
        log.info("Updated custom page: {}", updatedEntity.getRef());
        return Mono.just(updatedEntity.toCustomPage());
    }

    @Transactional
    public Mono<Void> deleteCustomPage(String pageRef) {
        log.info("Deleting custom page: {}", pageRef);

        CustomPageEntity entity = customPageRepository.findByRef(pageRef)
                .orElseThrow(() -> new CodedException(
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getCode(),
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getDefaultMessage(),
                        Map.of("pageRef", pageRef),
                        CodedError.CUSTOM_PAGE_NOT_FOUND.getDocumentationUrl()));

        customPageRepository.deleteByRef(pageRef);
        log.info("Deleted custom page: {}", entity.getTitle());
        return Mono.empty();
    }
}