package com.neohoods.portal.platform.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.SettingsEntity;
import com.neohoods.portal.platform.model.GetPublicSettings200Response;
import com.neohoods.portal.platform.model.GetSecuritySettings200Response;
import com.neohoods.portal.platform.model.SaveSecuritySettingsRequest;
import com.neohoods.portal.platform.repositories.SettingsRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SettingsService {
        private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
        private final SettingsRepository settingsRepository;

        private SettingsEntity getOrCreateDefaultSettings() {
                return settingsRepository.findTopByOrderByIdAsc()
                                .orElseGet(() -> {
                                        // Create default settings if none exist
                                        SettingsEntity defaultSettings = SettingsEntity.builder()
                                                        .id(UUID.fromString("00000000-0000-0000-0000-000000000001")) // Use
                                                                                                                     // the
                                                                                                                     // same
                                                                                                                     // ID
                                                                                                                     // as
                                                                                                                     // in
                                                                                                                     // data.sql
                                                        .isRegistrationEnabled(false)
                                                        .ssoEnabled(false)
                                                        .ssoClientId("")
                                                        .ssoClientSecret("")
                                                        .ssoTokenEndpoint("")
                                                        .ssoAuthorizationEndpoint("")
                                                        .ssoScope("")
                                                        .build();
                                        return settingsRepository.save(defaultSettings);
                                });
        }

        public Mono<GetPublicSettings200Response> getPublicSettings() {
                SettingsEntity setting = getOrCreateDefaultSettings();

                return Mono.just(new GetPublicSettings200Response()
                                .isRegistrationEnabled(setting.isRegistrationEnabled())
                                .ssoEnabled(setting.isSsoEnabled()));
        }

        public Mono<GetSecuritySettings200Response> getSecuritySettings() {
                SettingsEntity setting = getOrCreateDefaultSettings();

                return Mono.just(new GetSecuritySettings200Response()
                                .isRegistrationEnabled(setting.isRegistrationEnabled())
                                .ssoEnabled(setting.isSsoEnabled())
                                .ssoClientId(setting.getSsoClientId())
                                .ssoClientSecret(setting.getSsoClientSecret())
                                .ssoTokenEndpoint(setting.getSsoTokenEndpoint())
                                .ssoAuthorizationEndpoint(setting.getSsoAuthorizationEndpoint())
                                .ssoScope(setting.getSsoScope()));
        }

        public Mono<SettingsEntity> saveSecuritySettings(SaveSecuritySettingsRequest request) {
                SettingsEntity existingSetting = getOrCreateDefaultSettings();

                SettingsEntity updatedSetting = SettingsEntity.builder()
                                .id(existingSetting.getId())
                                .isRegistrationEnabled(request.getIsRegistrationEnabled())
                                .ssoEnabled(request.getSsoEnabled())
                                .ssoClientId(request.getSsoClientId())
                                .ssoClientSecret(request.getSsoClientSecret())
                                .ssoTokenEndpoint(request.getSsoTokenEndpoint())
                                .ssoAuthorizationEndpoint(request.getSsoAuthorizationEndpoint())
                                .ssoScope(request.getSsoScope())
                                .build();
                return Mono.just(settingsRepository.save(updatedSetting));
        }
}