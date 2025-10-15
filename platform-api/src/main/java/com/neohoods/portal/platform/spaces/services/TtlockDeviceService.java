package com.neohoods.portal.platform.spaces.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.exceptions.ResourceNotFoundException;
import com.neohoods.portal.platform.spaces.entities.TtlockDeviceEntity;
import com.neohoods.portal.platform.spaces.entities.TtlockDeviceStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.TtlockDeviceRepository;

@Service
@Transactional
public class TtlockDeviceService {

    private static final Logger logger = LoggerFactory.getLogger(TtlockDeviceService.class);

    @Autowired
    private TtlockDeviceRepository ttlockDeviceRepository;

    @Transactional(readOnly = true)
    public List<TtlockDeviceEntity> getAllTtlockDevices() {
        List<TtlockDeviceEntity> devices = ttlockDeviceRepository.findAll();
        logger.info("Retrieved {} TTLock devices", devices.size());
        return devices;
    }

    @Transactional(readOnly = true)
    public Optional<TtlockDeviceEntity> getTtlockDeviceById(UUID id) {
        return ttlockDeviceRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public TtlockDeviceEntity getTtlockDeviceByIdOrThrow(UUID id) {
        return ttlockDeviceRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("TTLock device not found with id: {}", id);
                    return new ResourceNotFoundException("TTLock device not found with id: " + id);
                });
    }

    @Transactional(readOnly = true)
    public TtlockDeviceEntity getTtlockDeviceByDeviceId(String deviceId) {
        return ttlockDeviceRepository.findByDeviceId(deviceId);
    }

    @Transactional(readOnly = true)
    public List<TtlockDeviceEntity> getTtlockDevicesByStatus(TtlockDeviceStatusForEntity status) {
        return ttlockDeviceRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public long getTtlockDeviceCountByStatus(TtlockDeviceStatusForEntity status) {
        return ttlockDeviceRepository.countByStatus(status);
    }

    @Transactional
    public TtlockDeviceEntity createTtlockDevice(TtlockDeviceEntity device) {
        TtlockDeviceEntity savedDevice = ttlockDeviceRepository.save(device);
        logger.info("Created TTLock device with ID: {} and device ID: {}",
                savedDevice.getId(), savedDevice.getDeviceId());
        return savedDevice;
    }

    @Transactional
    public TtlockDeviceEntity updateTtlockDevice(TtlockDeviceEntity device) {
        TtlockDeviceEntity updatedDevice = ttlockDeviceRepository.save(device);
        logger.info("Updated TTLock device with ID: {} and device ID: {}",
                updatedDevice.getId(), updatedDevice.getDeviceId());
        return updatedDevice;
    }

    @Transactional
    public void deleteTtlockDevice(UUID id) {
        if (!ttlockDeviceRepository.existsById(id)) {
            logger.warn("Attempted to delete non-existent TTLock device with id: {}", id);
            throw new ResourceNotFoundException("TTLock device not found with id: " + id);
        }
        ttlockDeviceRepository.deleteById(id);
        logger.info("Deleted TTLock device with ID: {}", id);
    }

    @Transactional
    public TtlockDeviceEntity updateTtlockDeviceStatus(UUID id, TtlockDeviceStatusForEntity status) {
        TtlockDeviceEntity device = getTtlockDeviceByIdOrThrow(id);
        TtlockDeviceStatusForEntity oldStatus = device.getStatus();
        device.setStatus(status);
        TtlockDeviceEntity updatedDevice = ttlockDeviceRepository.save(device);
        logger.info("Updated TTLock device {} status from {} to {}",
                id, oldStatus, status);
        return updatedDevice;
    }

    @Transactional
    public TtlockDeviceEntity updateTtlockDeviceBatteryLevel(UUID id, Integer batteryLevel) {
        TtlockDeviceEntity device = getTtlockDeviceByIdOrThrow(id);
        Integer oldBatteryLevel = device.getBatteryLevel();
        device.setBatteryLevel(batteryLevel);
        TtlockDeviceEntity updatedDevice = ttlockDeviceRepository.save(device);
        logger.info("Updated TTLock device {} battery level from {}% to {}%",
                id, oldBatteryLevel, batteryLevel);
        return updatedDevice;
    }

    @Transactional
    public TtlockDeviceEntity updateTtlockDeviceSignalStrength(UUID id, Integer signalStrength) {
        TtlockDeviceEntity device = getTtlockDeviceByIdOrThrow(id);
        Integer oldSignalStrength = device.getSignalStrength();
        device.setSignalStrength(signalStrength);
        TtlockDeviceEntity updatedDevice = ttlockDeviceRepository.save(device);
        logger.info("Updated TTLock device {} signal strength from {} to {}",
                id, oldSignalStrength, signalStrength);
        return updatedDevice;
    }

    /**
     * Get all active TTLock devices
     */
    @Transactional(readOnly = true)
    public List<TtlockDeviceEntity> getActiveTtlockDevices() {
        List<TtlockDeviceEntity> activeDevices = ttlockDeviceRepository
                .findByStatus(TtlockDeviceStatusForEntity.ACTIVE);
        logger.info("Retrieved {} active TTLock devices", activeDevices.size());
        return activeDevices;
    }

    /**
     * Get all inactive TTLock devices
     */
    @Transactional(readOnly = true)
    public List<TtlockDeviceEntity> getInactiveTtlockDevices() {
        List<TtlockDeviceEntity> inactiveDevices = ttlockDeviceRepository
                .findByStatus(TtlockDeviceStatusForEntity.INACTIVE);
        logger.info("Retrieved {} inactive TTLock devices", inactiveDevices.size());
        return inactiveDevices;
    }

    /**
     * Get devices with low battery level (below threshold)
     */
    @Transactional(readOnly = true)
    public List<TtlockDeviceEntity> getTtlockDevicesWithLowBattery(int batteryThreshold) {
        List<TtlockDeviceEntity> allDevices = ttlockDeviceRepository.findAll();
        List<TtlockDeviceEntity> lowBatteryDevices = allDevices.stream()
                .filter(device -> device.getBatteryLevel() != null && device.getBatteryLevel() < batteryThreshold)
                .toList();
        logger.info("Found {} TTLock devices with battery level below {}%",
                lowBatteryDevices.size(), batteryThreshold);
        return lowBatteryDevices;
    }

    /**
     * Get devices with weak signal strength (below threshold)
     */
    @Transactional(readOnly = true)
    public List<TtlockDeviceEntity> getTtlockDevicesWithWeakSignal(int signalThreshold) {
        List<TtlockDeviceEntity> allDevices = ttlockDeviceRepository.findAll();
        List<TtlockDeviceEntity> weakSignalDevices = allDevices.stream()
                .filter(device -> device.getSignalStrength() != null && device.getSignalStrength() < signalThreshold)
                .toList();
        logger.info("Found {} TTLock devices with signal strength below {}",
                weakSignalDevices.size(), signalThreshold);
        return weakSignalDevices;
    }

    /**
     * Check if a device exists by device ID
     */
    @Transactional(readOnly = true)
    public boolean existsByDeviceId(String deviceId) {
        boolean exists = ttlockDeviceRepository.findByDeviceId(deviceId) != null;
        logger.debug("Device with ID {} exists: {}", deviceId, exists);
        return exists;
    }

    /**
     * Get device count by status
     */
    @Transactional(readOnly = true)
    public long getDeviceCountByStatus(TtlockDeviceStatusForEntity status) {
        long count = ttlockDeviceRepository.countByStatus(status);
        logger.info("Found {} TTLock devices with status: {}", count, status);
        return count;
    }
}
