package com.neohoods.portal.platform.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.SettingsAdminApiApiDelegate;
import com.neohoods.portal.platform.model.GetSecuritySettings200Response;
import com.neohoods.portal.platform.model.SaveSecuritySettingsRequest;
import com.neohoods.portal.platform.services.SettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsAdminApi implements SettingsAdminApiApiDelegate {

    private final SettingsService settingsService;

    @Override
    public Mono<ResponseEntity<GetSecuritySettings200Response>> getSecuritySettings(ServerWebExchange exchange) {
        return settingsService.getSecuritySettings()
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<SaveSecuritySettingsRequest>> saveSecuritySettings(
            Mono<SaveSecuritySettingsRequest> request,
            ServerWebExchange exchange) {
        return request
                .doOnNext(req -> log.info("Received security settings save request: {}", req))
                .switchIfEmpty(Mono
                        .error(new IllegalArgumentException("Request body is required for saving security settings")))
                .flatMap(req -> {
                    return settingsService.saveSecuritySettings(req)
                            .map(savedEntity -> {
                                log.info("Security settings saved successfully");
                                // Convert saved entity back to request format for response
                                return SaveSecuritySettingsRequest.builder()
                                        .isRegistrationEnabled(savedEntity.isRegistrationEnabled())
                                        .build();
                            });
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error saving security settings", e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}