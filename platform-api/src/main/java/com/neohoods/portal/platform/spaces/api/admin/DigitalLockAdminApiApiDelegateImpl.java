package com.neohoods.portal.platform.spaces.api.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.DigitalLockAdminApiApiDelegate;
import com.neohoods.portal.platform.model.AccessCode;
import com.neohoods.portal.platform.model.DigitalLock;
import com.neohoods.portal.platform.model.DigitalLockStatus;
import com.neohoods.portal.platform.model.DigitalLockType;
import com.neohoods.portal.platform.model.GenerateDigitalLockAccessCodeRequest;
import com.neohoods.portal.platform.model.GetDigitalLockStats200Response;
import com.neohoods.portal.platform.model.GetDigitalLockStats200ResponseByStatus;
import com.neohoods.portal.platform.model.GetDigitalLockStats200ResponseByType;
import com.neohoods.portal.platform.model.NukiConfig;
import com.neohoods.portal.platform.model.TtlockConfig;
import com.neohoods.portal.platform.model.UpdateDigitalLockStatusRequest;
import com.neohoods.portal.platform.spaces.entities.DigitalLockEntity;
import com.neohoods.portal.platform.spaces.entities.DigitalLockStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.DigitalLockTypeForEntity;
import com.neohoods.portal.platform.spaces.entities.NukiConfigEntity;
import com.neohoods.portal.platform.spaces.entities.TtlockConfigEntity;
import com.neohoods.portal.platform.spaces.services.DigitalLockService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DigitalLockAdminApiApiDelegateImpl implements DigitalLockAdminApiApiDelegate {

    @Autowired
    private DigitalLockService digitalLockService;

    @Override
    public Mono<ResponseEntity<Flux<DigitalLock>>> getDigitalLocks(
            Integer page, Integer size, String name, DigitalLockType type,
            DigitalLockStatus status, ServerWebExchange exchange) {
        // Create a Pageable for getting all digital locks
        Pageable pageable = PageRequest.of(0, 1000);
        Page<DigitalLockEntity> pageResult = digitalLockService.getAllDigitalLocks(pageable);

        List<DigitalLock> locks = pageResult.getContent().stream()
                .map(this::convertToApiModel)
                .toList();

        return Mono.just(ResponseEntity.ok(Flux.fromIterable(locks)));
    }

    @Override
    public Mono<ResponseEntity<DigitalLock>> createDigitalLock(
            Mono<DigitalLock> digitalLock, ServerWebExchange exchange) {
        return digitalLock.flatMap(lock -> {
            DigitalLockEntity entity = convertToEntity(lock);
            DigitalLockEntity saved = digitalLockService.createDigitalLock(entity);
            DigitalLock response = convertToApiModel(saved);
            return Mono.just(ResponseEntity.ok(response));
        });
    }

    @Override
    public Mono<ResponseEntity<DigitalLock>> getDigitalLock(UUID lockId, ServerWebExchange exchange) {
        DigitalLockEntity entity = digitalLockService.getDigitalLockById(lockId);
        if (entity == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        DigitalLock lock = convertToApiModel(entity);
        return Mono.just(ResponseEntity.ok(lock));
    }

    @Override
    public Mono<ResponseEntity<DigitalLock>> updateDigitalLock(UUID lockId, Mono<DigitalLock> digitalLock,
            ServerWebExchange exchange) {
        return digitalLock.flatMap(lock -> {
            DigitalLockEntity entity = convertToEntity(lock);
            entity.setId(lockId);
            DigitalLockEntity updated = digitalLockService.updateDigitalLock(lockId, entity);
            DigitalLock response = convertToApiModel(updated);
            return Mono.just(ResponseEntity.ok(response));
        });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteDigitalLock(UUID lockId, ServerWebExchange exchange) {
        digitalLockService.deleteDigitalLock(lockId);
        return Mono.just(ResponseEntity.noContent().<Void>build());
    }

    @Override
    public Mono<ResponseEntity<DigitalLock>> updateDigitalLockStatus(
            UUID lockId, Mono<UpdateDigitalLockStatusRequest> updateDigitalLockStatusRequest,
            ServerWebExchange exchange) {
        return updateDigitalLockStatusRequest.flatMap(request -> {
            DigitalLockEntity entity = digitalLockService.getDigitalLockById(lockId);
            if (entity == null) {
                return Mono.just(ResponseEntity.notFound().build());
            }

            // Convert API status to entity status
            DigitalLockStatusForEntity entityStatus = convertApiStatusToEntityStatus(request.getStatus());
            entity.setStatus(entityStatus);

            DigitalLockEntity updated = digitalLockService.updateDigitalLock(lockId, entity);
            DigitalLock response = convertToApiModel(updated);
            return Mono.just(ResponseEntity.ok(response));
        });
    }

    @Override
    public Mono<ResponseEntity<AccessCode>> generateDigitalLockAccessCode(
            UUID lockId, Mono<GenerateDigitalLockAccessCodeRequest> generateDigitalLockAccessCodeRequest,
            ServerWebExchange exchange) {
        return generateDigitalLockAccessCodeRequest.flatMap(request -> {
            DigitalLockEntity entity = digitalLockService.getDigitalLockById(lockId);
            if (entity == null) {
                return Mono.just(ResponseEntity.notFound().build());
            }

            // Generate access code using the service
            String generatedCode = digitalLockService.generateAccessCode(lockId, 24, "Admin generated");

            AccessCode code = new AccessCode();
            code.setCode(generatedCode);
            code.setGeneratedAt(OffsetDateTime.now());
            code.setExpiresAt(OffsetDateTime.now().plusHours(24));
            code.setIsActive(true);
            return Mono.just(ResponseEntity.ok(code));
        });
    }

    @Override
    public Mono<ResponseEntity<GetDigitalLockStats200Response>> getDigitalLockStats(ServerWebExchange exchange) {
        // Create a Pageable for getting all digital locks
        Pageable pageable = PageRequest.of(0, 1000);
        Page<DigitalLockEntity> page = digitalLockService.getAllDigitalLocks(pageable);
        List<DigitalLockEntity> entities = page.getContent();

        GetDigitalLockStats200Response stats = new GetDigitalLockStats200Response();
        stats.setTotal(entities.size());

        // Count by status
        GetDigitalLockStats200ResponseByStatus byStatus = new GetDigitalLockStats200ResponseByStatus();
        byStatus.setActive((int) entities.stream()
                .filter(e -> e.getStatus() == DigitalLockStatusForEntity.ACTIVE)
                .count());
        byStatus.setInactive((int) entities.stream().filter(
                e -> e.getStatus() == DigitalLockStatusForEntity.INACTIVE)
                .count());
        // Maintenance status not available in API model, skip for now
        byStatus.setError((int) entities.stream()
                .filter(e -> e.getStatus() == DigitalLockStatusForEntity.ERROR)
                .count());
        stats.setByStatus(byStatus);

        // Count by type
        GetDigitalLockStats200ResponseByType byType = new GetDigitalLockStats200ResponseByType();
        byType.setTtlock((int) entities.stream()
                .filter(e -> e.getType() == DigitalLockTypeForEntity.TTLOCK)
                .count());
        byType.setNuki((int) entities.stream()
                .filter(e -> e.getType() == DigitalLockTypeForEntity.NUKI)
                .count());
        byType.setYale((int) entities.stream()
                .filter(e -> e.getType() == DigitalLockTypeForEntity.YALE)
                .count());
        stats.setByType(byType);

        return Mono.just(ResponseEntity.ok(stats));
    }

    // Helper methods for conversion
    private DigitalLock convertToApiModel(DigitalLockEntity entity) {
        DigitalLock lock = new DigitalLock();
        lock.setId(entity.getId());
        lock.setName(entity.getName());
        lock.setType(convertEntityTypeToApiType(entity.getType()));
        lock.setStatus(convertEntityStatusToApiStatus(entity.getStatus()));
        lock.setCreatedAt(
                entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null);
        lock.setUpdatedAt(
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC) : null);

        // Set configuration based on type
        if (entity.getTtlockConfig() != null) {
            lock.setTtlockConfig(convertTtlockConfig(entity.getTtlockConfig()));
        }
        if (entity.getNukiConfig() != null) {
            lock.setNukiConfig(convertNukiConfig(entity.getNukiConfig()));
        }

        return lock;
    }

    private DigitalLockEntity convertToEntity(DigitalLock lock) {
        DigitalLockEntity entity = new DigitalLockEntity();
        entity.setId(lock.getId());
        entity.setName(lock.getName());
        entity.setType(convertApiTypeToEntityType(lock.getType()));
        entity.setStatus(convertApiStatusToEntityStatus(lock.getStatus()));
        entity.setCreatedAt(lock.getCreatedAt() != null ? lock.getCreatedAt().toLocalDateTime() : null);
        entity.setUpdatedAt(lock.getUpdatedAt() != null ? lock.getUpdatedAt().toLocalDateTime() : null);
        return entity;
    }

    private DigitalLockType convertEntityTypeToApiType(
            DigitalLockTypeForEntity entityType) {
        return switch (entityType) {
            case TTLOCK -> DigitalLockType.TTLOCK;
            case NUKI -> DigitalLockType.NUKI;
            case YALE -> DigitalLockType.YALE;
        };
    }

    private DigitalLockTypeForEntity convertApiTypeToEntityType(
            DigitalLockType apiType) {
        return switch (apiType) {
            case TTLOCK -> DigitalLockTypeForEntity.TTLOCK;
            case NUKI -> DigitalLockTypeForEntity.NUKI;
            case YALE -> DigitalLockTypeForEntity.YALE;
        };
    }

    private DigitalLockStatus convertEntityStatusToApiStatus(
            DigitalLockStatusForEntity entityStatus) {
        return switch (entityStatus) {
            case ACTIVE -> DigitalLockStatus.ACTIVE;
            case INACTIVE -> DigitalLockStatus.INACTIVE;
            case MAINTENANCE -> DigitalLockStatus.ACTIVE; // Map to ACTIVE as MAINTENANCE not available
            case ERROR -> DigitalLockStatus.ERROR;
        };
    }

    private DigitalLockStatusForEntity convertApiStatusToEntityStatus(
            DigitalLockStatus apiStatus) {
        return switch (apiStatus) {
            case ACTIVE -> DigitalLockStatusForEntity.ACTIVE;
            case INACTIVE -> DigitalLockStatusForEntity.INACTIVE;
            case ERROR -> DigitalLockStatusForEntity.ERROR;
        };
    }

    private TtlockConfig convertTtlockConfig(
            TtlockConfigEntity entity) {
        TtlockConfig config = new TtlockConfig();
        config.setId(entity.getId());
        config.setDeviceId(entity.getDeviceId());
        config.setLocation(entity.getLocation());
        config.setBatteryLevel(entity.getBatteryLevel());
        config.setSignalStrength(entity.getSignalStrength());
        config.setLastSeen(
                entity.getLastSeen() != null ? entity.getLastSeen().atOffset(java.time.ZoneOffset.UTC) : null);
        return config;
    }

    private NukiConfig convertNukiConfig(
            NukiConfigEntity entity) {
        NukiConfig config = new NukiConfig();
        config.setId(entity.getId());
        config.setDeviceId(entity.getDeviceId());
        config.setToken(entity.getToken());
        config.setBatteryLevel(entity.getBatteryLevel());
        config.setLastSeen(
                entity.getLastSeen() != null ? entity.getLastSeen().atOffset(java.time.ZoneOffset.UTC) : null);
        return config;
    }
}
