package com.neohoods.portal.platform.api.admin.custompages;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.CustomPagesAdminApiApiDelegate;
import com.neohoods.portal.platform.model.CustomPage;
import com.neohoods.portal.platform.services.CustomPagesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomPagesAdminApi implements CustomPagesAdminApiApiDelegate {

    private final CustomPagesService customPagesService;

    @Override
    public Mono<ResponseEntity<CustomPage>> createCustomPage(Mono<CustomPage> customPage, ServerWebExchange exchange) {
        return customPage
                .flatMap(customPagesService::createCustomPage)
                .map(createdPage -> {
                    log.info("Custom page created successfully: {}", createdPage.getRef());
                    return ResponseEntity.status(HttpStatus.CREATED).body(createdPage);
                })
                .onErrorResume(e -> {
                    log.error("Error creating custom page", e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Override
    public Mono<ResponseEntity<Flux<CustomPage>>> getAdminCustomPages(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(customPagesService.getCustomPages()));
    }

    @Override
    public Mono<ResponseEntity<CustomPage>> getAdminCustomPage(String pageRef, ServerWebExchange exchange) {
        return customPagesService.getCustomPage(pageRef)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving custom page: {}", pageRef, e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    public Mono<ResponseEntity<CustomPage>> updateCustomPage(String pageRef, Mono<CustomPage> customPage,
            ServerWebExchange exchange) {
        return customPage
                .flatMap(page -> customPagesService.updateCustomPage(pageRef, page))
                .map(updatedPage -> {
                    log.info("Custom page updated successfully: {}", pageRef);
                    return ResponseEntity.ok(updatedPage);
                })
                .onErrorResume(e -> {
                    log.error("Error updating custom page: {}", pageRef, e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteCustomPage(String pageRef, ServerWebExchange exchange) {
        return customPagesService.deleteCustomPage(pageRef)
                .then(Mono.fromSupplier(() -> {
                    log.info("Custom page deleted successfully: {}", pageRef);
                    return ResponseEntity.noContent().<Void>build();
                }))
                .onErrorResume(e -> {
                    log.error("Error deleting custom page: {}", pageRef, e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
}
