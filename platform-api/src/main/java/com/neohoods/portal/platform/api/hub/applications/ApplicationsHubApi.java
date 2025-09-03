package com.neohoods.portal.platform.api.hub.applications;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.ApplicationsHubApiApiDelegate;
import com.neohoods.portal.platform.model.Application;
import com.neohoods.portal.platform.services.ApplicationsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationsHubApi implements ApplicationsHubApiApiDelegate {

    private final ApplicationsService applicationsService;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> createApplication(Mono<Application> application, ServerWebExchange exchange) {
        return (Mono) application
                .flatMap(applicationsService::createApplication)
                .then(Mono.fromSupplier(() -> {
                    log.info("Application created successfully");
                    return ResponseEntity.status(HttpStatus.CREATED).build();
                }))
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    @Override
    public Mono<ResponseEntity<Flux<Application>>> getApplications(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(applicationsService.getApplications()));
    }

    @Override
    public Mono<ResponseEntity<Application>> getApplication(UUID applicationId, ServerWebExchange exchange) {
        return applicationsService.getApplication(applicationId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving application: {}", applicationId, e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> updateApplication(UUID applicationId, Mono<Application> application,
            ServerWebExchange exchange) {
        return (Mono) application
                .flatMap(app -> applicationsService.updateApplication(applicationId, app))
                .then(Mono.fromSupplier(() -> {
                    log.info("Application updated successfully: {}", applicationId);
                    return ResponseEntity.ok().build();
                }))
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> deleteApplication(UUID applicationId, ServerWebExchange exchange) {
        return (Mono) applicationsService.deleteApplication(applicationId)
                .then(Mono.fromSupplier(() -> {
                    log.info("Application deleted successfully: {}", applicationId);
                    return ResponseEntity.noContent().build();
                }))
                .onErrorReturn(ResponseEntity.notFound().build());
    }
}
