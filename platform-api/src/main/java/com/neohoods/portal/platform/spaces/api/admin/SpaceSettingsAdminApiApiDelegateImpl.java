package com.neohoods.portal.platform.spaces.api.admin;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.SpaceSettingsAdminApiApiDelegate;
import com.neohoods.portal.platform.model.PlatformFeeSettings;
import com.neohoods.portal.platform.spaces.entities.SpaceSettingsEntity;
import com.neohoods.portal.platform.spaces.services.SpaceSettingsService;

import reactor.core.publisher.Mono;

@Service
public class SpaceSettingsAdminApiApiDelegateImpl implements SpaceSettingsAdminApiApiDelegate {

    @Autowired
    private SpaceSettingsService spaceSettingsService;

    @Override
    public Mono<ResponseEntity<PlatformFeeSettings>> getSpaceSettings(ServerWebExchange exchange) {
        try {
            SpaceSettingsEntity entity = spaceSettingsService.getSpaceSettings();
            PlatformFeeSettings settings = convertToApiModel(entity);
            return Mono.just(ResponseEntity.ok(settings));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }
    }

    @Override
    public Mono<ResponseEntity<PlatformFeeSettings>> saveSpaceSettings(Mono<PlatformFeeSettings> platformFeeSettings,
            ServerWebExchange exchange) {
        return platformFeeSettings.flatMap(dto -> {
            try {
                SpaceSettingsEntity entity = convertRequestToEntity(dto);
                SpaceSettingsEntity savedEntity = spaceSettingsService.saveSpaceSettings(entity);
                PlatformFeeSettings savedSettings = convertToApiModel(savedEntity);
                return Mono.just(ResponseEntity.ok(savedSettings));
            } catch (Exception e) {
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
            }
        });
    }

    // Helper methods for conversion
    private PlatformFeeSettings convertToApiModel(SpaceSettingsEntity entity) {
        PlatformFeeSettings settings = new PlatformFeeSettings();
        settings.setPlatformFeePercentage(entity.getPlatformFeePercentage().floatValue());
        settings.setPlatformFixedFee(entity.getPlatformFixedFee().floatValue());
        return settings;
    }

    private SpaceSettingsEntity convertRequestToEntity(PlatformFeeSettings settings) {
        SpaceSettingsEntity entity = spaceSettingsService.getSpaceSettings(); // Get existing to preserve ID
        entity.setPlatformFeePercentage(BigDecimal.valueOf(settings.getPlatformFeePercentage()));
        entity.setPlatformFixedFee(BigDecimal.valueOf(settings.getPlatformFixedFee()));
        return entity;
    }
}
