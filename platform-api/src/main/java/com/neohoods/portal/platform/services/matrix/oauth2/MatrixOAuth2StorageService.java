package com.neohoods.portal.platform.services.matrix.oauth2;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing OAuth2 PKCE and Device Code storage.
 * Handles storage, cleanup, and retrieval of PKCE entries and device codes.
 */
@Service
@Slf4j
public class MatrixOAuth2StorageService {

    // Store PKCE code verifiers by state with timestamp (expires after 10 minutes)
    private static class PkceEntry {
        final CodeVerifier codeVerifier;
        final State state;
        final OffsetDateTime createdAt;

        PkceEntry(CodeVerifier codeVerifier, State state) {
            this.codeVerifier = codeVerifier;
            this.state = state;
            this.createdAt = OffsetDateTime.now();
        }
    }

    // Store device codes with timestamp (expires after 10 minutes)
    public static class DeviceCodeEntry {
        public final String deviceCode;
        public final OffsetDateTime createdAt;
        public final long expiresIn;
        public final long interval;

        DeviceCodeEntry(String deviceCode, long expiresIn, long interval) {
            this.deviceCode = deviceCode;
            this.createdAt = OffsetDateTime.now();
            this.expiresIn = expiresIn;
            this.interval = interval;
        }

        public boolean isExpired() {
            return createdAt.plusSeconds(expiresIn).isBefore(OffsetDateTime.now());
        }
    }

    // Store admin access token with expiration
    private static class AdminTokenEntry {
        final String accessToken;
        final OffsetDateTime expiresAt;

        AdminTokenEntry(String accessToken, long expiresIn) {
            this.accessToken = accessToken;
            this.expiresAt = OffsetDateTime.now().plusSeconds(expiresIn);
        }

        boolean isExpired() {
            return expiresAt.isBefore(OffsetDateTime.now());
        }
    }

    private final Map<String, PkceEntry> pkceStorage = new ConcurrentHashMap<>();
    private final Map<String, DeviceCodeEntry> deviceCodeStorage = new ConcurrentHashMap<>();
    private volatile AdminTokenEntry adminTokenEntry = null;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "matrix-oauth2-pkce-cleanup");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        // Schedule cleanup of expired PKCE entries every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredPkce, 5, 5, TimeUnit.MINUTES);
        // Schedule cleanup of expired device codes every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredDeviceCodes, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Cleanup expired PKCE entries (older than 10 minutes)
     */
    private void cleanupExpiredPkce() {
        OffsetDateTime expireTime = OffsetDateTime.now().minusMinutes(10);
        int sizeBefore = pkceStorage.size();

        pkceStorage.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().createdAt.isBefore(expireTime);
            if (expired) {
                log.debug("Removing expired PKCE entry for state: {}", entry.getKey());
            }
            return expired;
        });

        int removed = sizeBefore - pkceStorage.size();
        if (removed > 0) {
            log.debug("Cleaned up {} expired PKCE entries ({} remaining)", removed, pkceStorage.size());
        }
    }

    /**
     * Cleanup expired device code entries
     */
    private void cleanupExpiredDeviceCodes() {
        int sizeBefore = deviceCodeStorage.size();

        deviceCodeStorage.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                log.debug("Removing expired device code entry: {}", entry.getKey());
            }
            return expired;
        });

        int removed = sizeBefore - deviceCodeStorage.size();
        if (removed > 0) {
            log.debug("Cleaned up {} expired device code entries ({} remaining)", removed, deviceCodeStorage.size());
        }
    }

    /**
     * Store PKCE entry
     */
    public void storePkceEntry(String state, CodeVerifier codeVerifier, State stateObj) {
        pkceStorage.put(state, new PkceEntry(codeVerifier, stateObj));
    }

    /**
     * Retrieve and remove PKCE entry
     */
    public PkceEntry retrievePkceEntry(String state) {
        return pkceStorage.remove(state);
    }

    /**
     * Store device code entry
     */
    public void storeDeviceCodeEntry(String deviceCode, long expiresIn, long interval) {
        deviceCodeStorage.put(deviceCode, new DeviceCodeEntry(deviceCode, expiresIn, interval));
    }

    /**
     * Get device code entry from storage
     */
    public DeviceCodeEntry getDeviceCodeEntry(String deviceCode) {
        return deviceCodeStorage.get(deviceCode);
    }

    /**
     * Remove device code entry
     */
    public void removeDeviceCodeEntry(String deviceCode) {
        deviceCodeStorage.remove(deviceCode);
    }

    /**
     * Store admin token entry
     */
    public void storeAdminTokenEntry(String accessToken, long expiresIn) {
        adminTokenEntry = new AdminTokenEntry(accessToken, expiresIn);
    }

    /**
     * Get admin token entry if valid
     */
    public String getAdminTokenEntry() {
        if (adminTokenEntry != null && !adminTokenEntry.isExpired()) {
            return adminTokenEntry.accessToken;
        }
        return null;
    }
}

