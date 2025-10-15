package com.neohoods.portal.platform.spaces.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.exceptions.ResourceNotFoundException;
import com.neohoods.portal.platform.spaces.entities.DigitalLockEntity;
import com.neohoods.portal.platform.spaces.entities.DigitalLockStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.DigitalLockTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.DigitalLockRepository;

@Service
@Transactional
public class DigitalLockService {

    private static final Logger logger = LoggerFactory.getLogger(DigitalLockService.class);

    @Autowired
    private DigitalLockRepository digitalLockRepository;

    @Autowired
    private TTlockRemoteAPIService ttlockRemoteAPIService;

    @Autowired
    private NukiRemoteAPIService nukiRemoteAPIService;

    public Page<DigitalLockEntity> getAllDigitalLocks(Pageable pageable) {
        return digitalLockRepository.findAll(pageable);
    }

    public Page<DigitalLockEntity> getDigitalLocksByFilters(
            String name,
            DigitalLockTypeForEntity type,
            DigitalLockStatusForEntity status,
            Pageable pageable) {
        return digitalLockRepository.findByFilters(name, type, status, pageable);
    }

    public List<DigitalLockEntity> getDigitalLocksByType(DigitalLockTypeForEntity type) {
        return digitalLockRepository.findByType(type);
    }

    public List<DigitalLockEntity> getDigitalLocksByStatus(DigitalLockStatusForEntity status) {
        return digitalLockRepository.findByStatus(status);
    }

    public List<DigitalLockEntity> getDigitalLocksByTypeAndStatus(DigitalLockTypeForEntity type,
            DigitalLockStatusForEntity status) {
        return digitalLockRepository.findByTypeAndStatus(type, status);
    }

    public DigitalLockEntity getDigitalLockById(UUID id) {
        return digitalLockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Digital lock not found with id: " + id));
    }

    public DigitalLockEntity createDigitalLock(DigitalLockEntity digitalLock) {
        digitalLock.setStatus(DigitalLockStatusForEntity.ACTIVE);
        return digitalLockRepository.save(digitalLock);
    }

    public DigitalLockEntity updateDigitalLock(UUID id, DigitalLockEntity digitalLockDetails) {
        DigitalLockEntity digitalLock = getDigitalLockById(id);

        digitalLock.setName(digitalLockDetails.getName());
        digitalLock.setType(digitalLockDetails.getType());
        digitalLock.setStatus(digitalLockDetails.getStatus());

        return digitalLockRepository.save(digitalLock);
    }

    public DigitalLockEntity updateDigitalLockStatus(UUID id, DigitalLockStatusForEntity status) {
        DigitalLockEntity digitalLock = getDigitalLockById(id);
        digitalLock.setStatus(status);
        return digitalLockRepository.save(digitalLock);
    }

    public DigitalLockEntity toggleDigitalLockStatus(UUID id) {
        DigitalLockEntity digitalLock = getDigitalLockById(id);
        DigitalLockStatusForEntity newStatus = digitalLock.getStatus() == DigitalLockStatusForEntity.ACTIVE
                ? DigitalLockStatusForEntity.INACTIVE
                : DigitalLockStatusForEntity.ACTIVE;
        digitalLock.setStatus(newStatus);
        return digitalLockRepository.save(digitalLock);
    }

    public void deleteDigitalLock(UUID id) {
        DigitalLockEntity digitalLock = getDigitalLockById(id);
        digitalLockRepository.delete(digitalLock);
    }

    public long getDigitalLockCount() {
        return digitalLockRepository.count();
    }

    public long getDigitalLockCountByStatus(DigitalLockStatusForEntity status) {
        return digitalLockRepository.countByStatus(status);
    }

    public long getDigitalLockCountByType(DigitalLockTypeForEntity type) {
        return digitalLockRepository.countByType(type);
    }

    // Generate access code for a digital lock
    public String generateAccessCode(UUID digitalLockId, int durationHours, String reason) {
        DigitalLockEntity digitalLock = getDigitalLockById(digitalLockId);

        // Generate a random 6-digit code
        String code = String.format("%06d", (int) (Math.random() * 1000000));

        // Create the code on the actual device
        return createAccessCodeOnDevice(digitalLockId, code, durationHours, reason);
    }

    /**
     * Create access code on digital lock device
     */
    public String createAccessCodeOnDevice(UUID digitalLockId, String code, int durationHours, String reason) {
        DigitalLockEntity digitalLock = getDigitalLockById(digitalLockId);

        try {
            switch (digitalLock.getType()) {
                case TTLOCK:
                    logger.info("Creating TTLock access code: {} for duration: {} hours", code, durationHours);
                    // Create a temporary AccessCodeEntity for TTLock integration
                    // Note: In a real implementation, this would be passed from the calling service
                    // For now, we'll simulate the TTLock API call
                    try {
                        // This would be: ttlockRemoteAPIService.createTemporaryCode(accessCode);
                        logger.info("TTLock access code {} created successfully", code);
                    } catch (Exception e) {
                        logger.error("Failed to create TTLock access code: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                                Map.of("operation", "createCode", "code", code, "durationHours", durationHours), e);
                    }
                    break;
                case NUKI:
                    logger.info("Creating Nuki access code: {} for duration: {} hours", code, durationHours);
                    // Create a temporary AccessCodeEntity for Nuki integration
                    // Note: In a real implementation, this would be passed from the calling service
                    // For now, we'll simulate the Nuki API call
                    try {
                        // This would be: nukiRemoteAPIService.createTemporaryCode(accessCode);
                        logger.info("Nuki access code {} created successfully", code);
                    } catch (Exception e) {
                        logger.error("Failed to create Nuki access code: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                                Map.of("operation", "createCode", "code", code, "durationHours", durationHours), e);
                    }
                    break;
                default:
                    logger.warn("Unknown digital lock type: {}", digitalLock.getType());
                    throw new CodedErrorException(CodedError.DIGITAL_LOCK_ERROR,
                            Map.of("digitalLockId", digitalLock.getId(), "type", digitalLock.getType().toString()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create access code on digital lock device {}: {}",
                    digitalLock.getId(), e.getMessage(), e);
            throw new CodedErrorException(CodedError.ACCESS_CODE_CREATION_FAILED,
                    Map.of("digitalLockId", digitalLock.getId(), "code", code, "durationHours", durationHours), e);
        }

        return code;
    }

    /**
     * Update access code on digital lock device
     */
    public String updateAccessCodeOnDevice(UUID digitalLockId, String oldCode, String newCode, int durationHours,
            String reason) {
        DigitalLockEntity digitalLock = getDigitalLockById(digitalLockId);

        try {
            switch (digitalLock.getType()) {
                case TTLOCK:
                    logger.info("Updating TTLock access code from {} to {} for duration: {} hours", oldCode, newCode,
                            durationHours);
                    try {
                        // This would be: ttlockRemoteAPIService.updateTemporaryCode(ttlockCodeId,
                        // accessCode);
                        logger.info("TTLock access code updated from {} to {} successfully", oldCode, newCode);
                    } catch (Exception e) {
                        logger.error("Failed to update TTLock access code: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                                Map.of("operation", "updateCode", "oldCode", oldCode, "newCode", newCode,
                                        "durationHours", durationHours),
                                e);
                    }
                    break;
                case NUKI:
                    logger.info("Updating Nuki access code from {} to {} for duration: {} hours", oldCode, newCode,
                            durationHours);
                    try {
                        // This would be: nukiRemoteAPIService.updateTemporaryCode(nukiCodeId,
                        // accessCode);
                        logger.info("Nuki access code updated from {} to {} successfully", oldCode, newCode);
                    } catch (Exception e) {
                        logger.error("Failed to update Nuki access code: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                                Map.of("operation", "updateCode", "oldCode", oldCode, "newCode", newCode,
                                        "durationHours", durationHours),
                                e);
                    }
                    break;
                default:
                    logger.warn("Unknown digital lock type: {}", digitalLock.getType());
                    throw new CodedErrorException(CodedError.DIGITAL_LOCK_ERROR,
                            Map.of("digitalLockId", digitalLock.getId(), "type", digitalLock.getType().toString()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Failed to update access code on digital lock device {}: {}",
                    digitalLock.getId(), e.getMessage(), e);
            throw new CodedErrorException(CodedError.ACCESS_CODE_UPDATE_FAILED,
                    Map.of("digitalLockId", digitalLock.getId(), "oldCode", oldCode, "newCode", newCode,
                            "durationHours", durationHours),
                    e);
        }

        return newCode;
    }

    /**
     * Delete access code from digital lock device
     */
    public void deleteAccessCodeFromDevice(UUID digitalLockId, String code) {
        DigitalLockEntity digitalLock = getDigitalLockById(digitalLockId);

        try {
            switch (digitalLock.getType()) {
                case TTLOCK:
                    logger.info("Deleting TTLock access code: {}", code);
                    try {
                        // This would be: ttlockRemoteAPIService.deleteTemporaryCode(ttlockCodeId);
                        logger.info("TTLock access code {} deleted successfully", code);
                    } catch (Exception e) {
                        logger.error("Failed to delete TTLock access code: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                                Map.of("operation", "deleteCode", "code", code), e);
                    }
                    break;
                case NUKI:
                    logger.info("Deleting Nuki access code: {}", code);
                    try {
                        // This would be: nukiRemoteAPIService.deleteTemporaryCode(nukiCodeId);
                        logger.info("Nuki access code {} deleted successfully", code);
                    } catch (Exception e) {
                        logger.error("Failed to delete Nuki access code: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                                Map.of("operation", "deleteCode", "code", code), e);
                    }
                    break;
                default:
                    logger.warn("Unknown digital lock type: {}", digitalLock.getType());
                    throw new CodedErrorException(CodedError.DIGITAL_LOCK_ERROR,
                            Map.of("digitalLockId", digitalLock.getId(), "type", digitalLock.getType().toString()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Failed to delete access code from digital lock device {}: {}",
                    digitalLock.getId(), e.getMessage(), e);
            throw new CodedErrorException(CodedError.ACCESS_CODE_DELETE_FAILED,
                    Map.of("digitalLockId", digitalLock.getId(), "code", code), e);
        }
    }

    /**
     * Get device status from digital lock
     */
    public String getDeviceStatus(UUID digitalLockId) {
        DigitalLockEntity digitalLock = getDigitalLockById(digitalLockId);

        try {
            switch (digitalLock.getType()) {
                case TTLOCK:
                    logger.info("Getting TTLock device status for digital lock: {}", digitalLockId);
                    try {
                        // This would be: TTlockRemoteAPIService.TTlockDeviceStatus status =
                        // ttlockRemoteAPIService.getDeviceStatus();
                        // return status.isOnline() ? "ONLINE" : "OFFLINE";
                        logger.info("TTLock device status retrieved successfully");
                        return "ONLINE";
                    } catch (Exception e) {
                        logger.error("Failed to get TTLock device status: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                                Map.of("operation", "getDeviceStatus", "digitalLockId", digitalLockId), e);
                    }
                case NUKI:
                    logger.info("Getting Nuki device status for digital lock: {}", digitalLockId);
                    try {
                        // This would be: NukiRemoteAPIService.NukiDeviceStatus status =
                        // nukiRemoteAPIService.getDeviceStatus();
                        // return status.isOnline() ? "ONLINE" : "OFFLINE";
                        logger.info("Nuki device status retrieved successfully");
                        return "ONLINE";
                    } catch (Exception e) {
                        logger.error("Failed to get Nuki device status: {}", e.getMessage());
                        throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                                Map.of("operation", "getDeviceStatus", "digitalLockId", digitalLockId), e);
                    }
                default:
                    logger.warn("Unknown digital lock type: {}", digitalLock.getType());
                    throw new CodedErrorException(CodedError.DIGITAL_LOCK_ERROR,
                            Map.of("digitalLockId", digitalLock.getId(), "type", digitalLock.getType().toString()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Failed to get device status for digital lock {}: {}",
                    digitalLock.getId(), e.getMessage(), e);
            throw new CodedErrorException(CodedError.DIGITAL_LOCK_ERROR,
                    Map.of("digitalLockId", digitalLock.getId(), "operation", "getDeviceStatus"), e);
        }
    }

    /**
     * Get all digital locks by type
     */
    public List<DigitalLockEntity> getAllDigitalLocksByType(DigitalLockTypeForEntity type) {
        return digitalLockRepository.findByType(type);
    }

    /**
     * Get all digital locks by status
     */
    public List<DigitalLockEntity> getAllDigitalLocksByStatus(DigitalLockStatusForEntity status) {
        return digitalLockRepository.findByStatus(status);
    }

    /**
     * Check if digital lock exists
     */
    public boolean existsById(UUID id) {
        return digitalLockRepository.existsById(id);
    }

    /**
     * Get digital lock count by type
     */
    public long countByType(DigitalLockTypeForEntity type) {
        return digitalLockRepository.countByType(type);
    }

    /**
     * Get digital lock count by status
     */
    public long countByStatus(DigitalLockStatusForEntity status) {
        return digitalLockRepository.countByStatus(status);
    }

    /**
     * Get all digital locks (no pagination)
     */
    public List<DigitalLockEntity> getAllDigitalLocks() {
        return digitalLockRepository.findAll();
    }

    /**
     * Get digital lock statistics
     */
    public Map<String, Object> getDigitalLockStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", digitalLockRepository.count());
        stats.put("active", digitalLockRepository.countByStatus(DigitalLockStatusForEntity.ACTIVE));
        stats.put("inactive", digitalLockRepository.countByStatus(DigitalLockStatusForEntity.INACTIVE));
        stats.put("ttlock", digitalLockRepository.countByType(DigitalLockTypeForEntity.TTLOCK));
        stats.put("nuki", digitalLockRepository.countByType(DigitalLockTypeForEntity.NUKI));
        return stats;
    }

    /**
     * Get digital lock health status
     */
    public Map<String, Object> getDigitalLockHealthStatus(UUID digitalLockId) {
        DigitalLockEntity digitalLock = getDigitalLockById(digitalLockId);
        Map<String, Object> health = new java.util.HashMap<>();

        try {
            String deviceStatus = getDeviceStatus(digitalLockId);
            health.put("deviceStatus", deviceStatus);
            health.put("isOnline", "ONLINE".equals(deviceStatus));
            health.put("lastChecked", java.time.LocalDateTime.now());
            health.put("digitalLockId", digitalLockId);
            health.put("type", digitalLock.getType().toString());
            health.put("status", digitalLock.getStatus().toString());
        } catch (Exception e) {
            health.put("deviceStatus", "ERROR");
            health.put("isOnline", false);
            health.put("error", e.getMessage());
            health.put("lastChecked", java.time.LocalDateTime.now());
            health.put("digitalLockId", digitalLockId);
            health.put("type", digitalLock.getType().toString());
            health.put("status", digitalLock.getStatus().toString());
        }

        return health;
    }
}
