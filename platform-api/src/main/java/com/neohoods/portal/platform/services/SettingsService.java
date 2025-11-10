package com.neohoods.portal.platform.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

        @Value("${neohoods.portal.sso.enabled:false}")
        private boolean ssoEnabled;

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
                                                        .build();
                                        return settingsRepository.save(defaultSettings);
                                });
        }

        public Mono<GetPublicSettings200Response> getPublicSettings() {
                SettingsEntity setting = getOrCreateDefaultSettings();

                return Mono.just(new GetPublicSettings200Response()
                                .isRegistrationEnabled(setting.isRegistrationEnabled())
                                .ssoEnabled(ssoEnabled)); // SSO settings are now in application config, not in DB
        }

        public Mono<GetSecuritySettings200Response> getSecuritySettings() {
                SettingsEntity setting = getOrCreateDefaultSettings();

                return Mono.just(new GetSecuritySettings200Response()
                                .isRegistrationEnabled(setting.isRegistrationEnabled()));
        }

        public Mono<SettingsEntity> saveSecuritySettings(SaveSecuritySettingsRequest request) {
                SettingsEntity existingSetting = getOrCreateDefaultSettings();

                SettingsEntity updatedSetting = SettingsEntity.builder()
                                .id(existingSetting.getId())
                                .isRegistrationEnabled(request.getIsRegistrationEnabled())
                                .build();
                return Mono.just(settingsRepository.save(updatedSetting));
        }
}