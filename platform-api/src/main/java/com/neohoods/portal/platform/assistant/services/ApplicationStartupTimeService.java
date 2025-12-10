package com.neohoods.portal.platform.assistant.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that tracks application startup time.
 * Used to filter out conversation messages from before the application restart
 * in local development environments.
 */
@Service
@Slf4j
public class ApplicationStartupTimeService {

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Getter
    private long applicationStartTimeMillis;

    @PostConstruct
    public void init() {
        applicationStartTimeMillis = System.currentTimeMillis();
        log.info("Application started at timestamp: {} ({})", applicationStartTimeMillis,
                new java.util.Date(applicationStartTimeMillis));
    }

    /**
     * Checks if we should filter messages by startup time.
     * Only in local/dev profiles to avoid stale conversation context.
     * 
     * @return true if messages should be filtered by startup time
     */
    public boolean shouldFilterByStartupTime() {
        // Only filter in local/dev profiles, not in production
        return activeProfiles != null && (activeProfiles.contains("local") || activeProfiles.contains("dev"));
    }

    /**
     * Checks if a timestamp is after application startup.
     * 
     * @param timestampMillis Timestamp to check
     * @return true if timestamp is after startup (or if filtering is disabled)
     */
    public boolean isAfterStartup(long timestampMillis) {
        if (!shouldFilterByStartupTime()) {
            // In production, don't filter by startup time
            return true;
        }
        return timestampMillis >= applicationStartTimeMillis;
    }
}

