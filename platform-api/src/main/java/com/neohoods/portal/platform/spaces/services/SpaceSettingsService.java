package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.spaces.entities.SpaceSettingsEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceSettingsRepository;

@Service
@Transactional
public class SpaceSettingsService {

    @Autowired
    private SpaceSettingsRepository spaceSettingsRepository;

    /**
     * Get the current space settings, creating default settings if none exist
     * 
     * @return SpaceSettingsEntity with current platform fee configuration
     */
    @Transactional(readOnly = true)
    public SpaceSettingsEntity getSpaceSettings() {
        Optional<SpaceSettingsEntity> settings = spaceSettingsRepository.findFirstByOrderByCreatedAtDesc();

        if (settings.isEmpty()) {
            // Create default settings if none exist
            SpaceSettingsEntity defaultSettings = new SpaceSettingsEntity(
                    BigDecimal.valueOf(2.00), // 2% platform fee
                    BigDecimal.valueOf(0.25) // 0.25€ fixed fee
            );
            return spaceSettingsRepository.save(defaultSettings);
        }

        return settings.get();
    }

    /**
     * Save space settings
     * 
     * @param settings SpaceSettingsEntity to save
     * @return Saved SpaceSettingsEntity
     */
    public SpaceSettingsEntity saveSpaceSettings(SpaceSettingsEntity settings) {
        return spaceSettingsRepository.save(settings);
    }

    /**
     * Update space settings with new values
     * 
     * @param platformFeePercentage New platform fee percentage
     * @param platformFixedFee      New platform fixed fee
     * @return Updated SpaceSettingsEntity
     */
    public SpaceSettingsEntity updateSpaceSettings(BigDecimal platformFeePercentage, BigDecimal platformFixedFee) {
        SpaceSettingsEntity settings = getSpaceSettings();
        settings.setPlatformFeePercentage(platformFeePercentage);
        settings.setPlatformFixedFee(platformFixedFee);
        return spaceSettingsRepository.save(settings);
    }

    /**
     * Get platform fee percentage
     * 
     * @return Current platform fee percentage
     */
    @Transactional(readOnly = true)
    public BigDecimal getPlatformFeePercentage() {
        return getSpaceSettings().getPlatformFeePercentage();
    }

    /**
     * Get platform fixed fee
     * 
     * @return Current platform fixed fee
     */
    @Transactional(readOnly = true)
    public BigDecimal getPlatformFixedFee() {
        return getSpaceSettings().getPlatformFixedFee();
    }
}
