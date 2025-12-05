package com.neohoods.portal.platform.services.matrix.space;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.matrix.ApiException;
import com.neohoods.portal.platform.matrix.api.SessionManagementApi;
import com.neohoods.portal.platform.matrix.model.GetTokenOwner200Response;

import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing Matrix avatars and display names.
 * Handles bot avatar, room avatar, and bot display name updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixAvatarService {

    private final MatrixOAuth2Service oauth2Service;
    private final MatrixMediaService mediaService;
    private final RestTemplate restTemplate;

    @Value("${neohoods.portal.matrix.disabled}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.local-assistant.enabled}")
    private boolean localAssistantEnabled;

    @Value("${neohoods.portal.matrix.local-assistant.user-id}")
    private String localAssistantUserId;

    @Value("${neohoods.portal.matrix.local-assistant.permanent-token}")
    private String localAssistantPermanentToken;

    @Value("${neohoods.portal.matrix.local-assistant.avatar-url}")
    private String botAvatarUrl;

    @Value("${neohoods.portal.matrix.local-assistant.display-name}")
    private String botDisplayName;

    /**
     * Get Matrix API client configured with access token
     * Uses MatrixOAuth2Service which will automatically use OAuth2 token service as
     * fallback
     */
    private Optional<ApiClient> getMatrixAccessToken() {
        if (disabled) {
            log.debug("Matrix bot is disabled");
            return Optional.empty();
        }

        return oauth2Service.getMatrixApiClient(
                homeserverUrl,
                null, // No hardcoded token - will use OAuth2 token service as fallback
                localAssistantEnabled,
                localAssistantPermanentToken,
                localAssistantUserId,
                null);
    }

    /**
     * Get bot user ID from access token using SDK client
     * If local bot is enabled, returns the configured local bot user ID
     */
    private Optional<String> getAssistantUserId() {
        // If local bot is enabled, return the configured user ID directly
        if (localAssistantEnabled && localAssistantUserId != null && !localAssistantUserId.isEmpty()) {
            log.debug("Using configured local bot user ID: {}", localAssistantUserId);
            return Optional.of(localAssistantUserId);
        }

        // Otherwise, get user ID from token via SDK
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            SessionManagementApi sessionApi = new SessionManagementApi(apiClient);

            // Use whoami endpoint via SDK to get user ID from token
            try {
                GetTokenOwner200Response response = sessionApi.getTokenOwner();

                if (response != null && response.getUserId() != null) {
                    String userId = response.getUserId();
                    log.info("Bot user ID: {}", userId);
                    return Optional.of(userId);
                }
            } catch (ApiException e) {
                log.warn("Failed to get bot user ID from whoami via SDK: {}", e.getMessage());
                return Optional.empty();
            } catch (Exception e) {
                log.warn("Failed to get bot user ID from whoami via SDK", e);
                return Optional.empty();
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting bot user ID", e);
            return Optional.empty();
        }
    }

    /**
     * Normalize homeserver URL by adding https:// if missing
     */
    private String normalizeHomeserverUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://chat.neohoods.com"; // default
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://" + url;
    }

    /**
     * Get current bot avatar URL
     * 
     * @param assistantUserId Bot user ID
     * @return Current avatar URL or null if not set or error
     */
    @SuppressWarnings("unchecked")
    private String getCurrentBotAvatar(String assistantUserId) {
        try {
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", assistantUserId, "avatar_url")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

            if (accessToken == null) {
                return null;
            }

            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object avatarUrlObj = body.get("avatar_url");
                if (avatarUrlObj != null) {
                    return avatarUrlObj.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Error getting current bot avatar: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get current room avatar URL
     * 
     * @param roomId Room ID
     * @return Current avatar URL or null if not set or error
     */
    @SuppressWarnings("unchecked")
    private String getCurrentRoomAvatar(String roomId) {
        try {
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String stateUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "rooms", roomId, "state", "m.room.avatar", "")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

            if (accessToken == null) {
                return null;
            }

            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    stateUrl,
                    HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object urlObj = body.get("url");
                if (urlObj != null) {
                    return urlObj.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Error getting current room avatar: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get current bot display name
     * 
     * @param assistantUserId Bot user ID
     * @return Current display name or null if not set or error
     */
    @SuppressWarnings("unchecked")
    private String getCurrentBotDisplayName(String assistantUserId) {
        try {
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", assistantUserId, "displayname")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

            if (accessToken == null) {
                return null;
            }

            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object displayNameObj = body.get("displayname");
                if (displayNameObj != null) {
                    return displayNameObj.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Error getting current bot display name: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update bot avatar from configured URL
     * If the URL is HTTP/HTTPS, uploads it to Matrix first to get an MXC URL
     * Then updates the bot's profile avatar_url
     * 
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateBotAvatar() {
        return updateBotAvatar(false);
    }

    /**
     * Update bot avatar from configured URL
     * If the URL is HTTP/HTTPS, uploads it to Matrix first to get an MXC URL
     * Then updates the bot's profile avatar_url
     * 
     * @param force If true, force update even if avatar is already set
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateBotAvatar(boolean force) {
        if (botAvatarUrl == null || botAvatarUrl.isEmpty()) {
            log.debug("No bot avatar URL configured, skipping avatar update");
            return false;
        }

        Optional<String> assistantUserIdOpt = getAssistantUserId();
        if (assistantUserIdOpt.isEmpty()) {
            log.warn("Cannot update bot avatar: bot user ID not available");
            return false;
        }

        String assistantUserId = assistantUserIdOpt.get();
        log.info("Updating avatar for bot user: {} from URL: {} (force: {})", assistantUserId, botAvatarUrl, force);

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token available for updating bot avatar");
                return false;
            }

            // Get current avatar URL first
            String currentAvatarUrl = getCurrentBotAvatar(assistantUserId);

            // Check if update is needed by comparing images (not just URLs)
            if (!force && currentAvatarUrl != null && !currentAvatarUrl.isEmpty()) {
                if (botAvatarUrl.startsWith("mxc://")) {
                    // Both are MXC URLs, can compare directly
                    if (currentAvatarUrl.equals(botAvatarUrl)) {
                        log.info("Bot avatar is already set to {}, skipping update", botAvatarUrl);
                        return true; // Already set correctly
                    }
                } else if (botAvatarUrl.startsWith("http://") || botAvatarUrl.startsWith("https://")) {
                    // Compare images by hash to avoid unnecessary uploads
                    log.info("Comparing bot avatar images (source: {}, current: {})...", botAvatarUrl,
                            currentAvatarUrl);
                    Boolean identical = mediaService.imagesAreIdentical(botAvatarUrl, currentAvatarUrl);
                    if (identical == null) {
                        // Comparison failed - could be due to network issues, cache, or image change
                        // Force update to ensure avatar is up to date (URL might point to new image)
                        log.warn(
                                "Could not compare bot avatar images (comparison failed), will update to ensure avatar is current");
                        // Continue to update (don't return)
                    } else if (identical) {
                        log.info("Bot avatar image is identical to current, skipping update");
                        return true; // Already set correctly
                    } else {
                        log.info("Bot avatar image differs from current (hash mismatch), will update");
                    }
                }
            }

            // Convert configured URL to MXC if needed
            String mxcUrl = botAvatarUrl;
            if (botAvatarUrl.startsWith("http://") || botAvatarUrl.startsWith("https://")) {
                log.info("Uploading avatar image from {} to Matrix...", botAvatarUrl);
                mxcUrl = mediaService.uploadImageToMatrix(botAvatarUrl);
                if (mxcUrl == null || mxcUrl.isEmpty()) {
                    log.error("Failed to upload avatar image to Matrix");
                    return false;
                }
                log.info("Avatar image uploaded to Matrix: {}", mxcUrl);
            }

            if (currentAvatarUrl == null || currentAvatarUrl.isEmpty()) {
                log.info("Bot avatar not set, setting to {}", mxcUrl);
            } else {
                log.info("Bot avatar differs, updating from {} to {}", currentAvatarUrl, mxcUrl);
            }

            // Update bot profile avatar_url
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", assistantUserId, "avatar_url")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get access token for Authorization header
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

            if (accessToken == null) {
                log.error("No access token available for updating bot avatar");
                return false;
            }

            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("avatar_url", mxcUrl);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.PUT,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated bot avatar for {} to {}", assistantUserId, mxcUrl);
                return true;
            } else {
                log.error("Failed to update bot avatar: HTTP {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating bot avatar for {}: {}", assistantUserId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Update bot display name
     * 
     * @return true if display name was updated successfully, false otherwise
     */
    public boolean updateBotDisplayName() {
        if (botDisplayName == null || botDisplayName.isEmpty()) {
            log.debug("No bot display name configured, skipping display name update");
            return false;
        }

        Optional<String> assistantUserIdOpt = getAssistantUserId();
        if (assistantUserIdOpt.isEmpty()) {
            log.warn("Cannot update bot display name: bot user ID not available");
            return false;
        }

        String assistantUserId = assistantUserIdOpt.get();

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token available for updating bot display name");
                return false;
            }

            // Get current display name first
            String currentDisplayName = getCurrentBotDisplayName(assistantUserId);

            // Check if update is needed
            if (currentDisplayName != null && currentDisplayName.equals(botDisplayName)) {
                log.debug("Bot display name is already set to {}, skipping update", botDisplayName);
                return true; // Already set correctly
            } else if (currentDisplayName != null) {
                log.info("Bot display name differs (current: {}, configured: {}), updating...", currentDisplayName,
                        botDisplayName);
            } else {
                log.info("Bot display name not set, setting to {}", botDisplayName);
            }

            // Update bot profile displayname
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", assistantUserId, "displayname")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get access token for Authorization header
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

            if (accessToken == null) {
                log.error("No access token available for updating bot display name");
                return false;
            }

            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("displayname", botDisplayName);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.PUT,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated bot display name for {} to {}", assistantUserId, botDisplayName);
                return true;
            } else {
                log.error("Failed to update bot display name: HTTP {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating bot display name for {}: {}", assistantUserId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Update room avatar from image URL
     * If the URL is HTTP/HTTPS, uploads it to Matrix first to get an MXC URL
     * Then sends a state event to update the room avatar
     * 
     * @param roomId   Room ID
     * @param imageUrl Image URL (HTTP/HTTPS or MXC)
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateRoomAvatar(String roomId, String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            log.debug("No image URL provided for room avatar update");
            return false;
        }

        // Decode room ID if needed
        String decodedRoomId = roomId;
        try {
            if (roomId.contains("%")) {
                String current = roomId;
                int maxDecodeAttempts = 5;
                for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                    String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                    if (decoded.equals(current)) {
                        break;
                    }
                    current = decoded;
                }
                decodedRoomId = current;
            }
        } catch (Exception e) {
            // Use original if decoding fails
        }

        log.info("Updating avatar for room {} from URL: {}", decodedRoomId, imageUrl);

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token available for updating room avatar");
                return false;
            }

            // Get current room avatar first
            String currentRoomAvatar = getCurrentRoomAvatar(decodedRoomId);

            // Check if update is needed by comparing images (not just URLs)
            if (currentRoomAvatar != null && !currentRoomAvatar.isEmpty()) {
                if (imageUrl.startsWith("mxc://")) {
                    // Both are MXC URLs, can compare directly
                    if (currentRoomAvatar.equals(imageUrl)) {
                        log.info("Room avatar is already set to {}, skipping update", imageUrl);
                        return true; // Already set correctly
                    }
                } else if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                    // Compare images by hash to avoid unnecessary uploads
                    log.info("Comparing room avatar images (source: {}, current: {})...", imageUrl, currentRoomAvatar);
                    Boolean identical = mediaService.imagesAreIdentical(imageUrl, currentRoomAvatar);
                    if (identical == null) {
                        // Comparison failed - could be due to network issues, cache, or image change
                        // Force update to ensure avatar is up to date (URL might point to new image)
                        log.warn(
                                "Could not compare room avatar images (comparison failed), will update to ensure avatar is current");
                        // Continue to update (don't return)
                    } else if (identical) {
                        log.info("Room avatar image is identical to current, skipping update");
                        return true; // Already set correctly
                    } else {
                        log.info("Room avatar image differs from current (hash mismatch), will update");
                    }
                }
            }

            // Convert configured URL to MXC if needed
            String mxcUrl = imageUrl;
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                log.info("Uploading room avatar image from {} to Matrix...", imageUrl);
                mxcUrl = mediaService.uploadImageToMatrix(imageUrl);
                if (mxcUrl == null || mxcUrl.isEmpty()) {
                    log.error("Failed to upload room avatar image to Matrix");
                    return false;
                }
                log.info("Room avatar image uploaded to Matrix: {}", mxcUrl);
            }

            if (currentRoomAvatar == null || currentRoomAvatar.isEmpty()) {
                log.info("Room avatar not set, setting to {}", mxcUrl);
            } else {
                log.info("Room avatar differs, updating from {} to {}", currentRoomAvatar, mxcUrl);
            }

            // Send state event to update room avatar
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String stateUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "rooms", decodedRoomId, "state", "m.room.avatar", "")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get access token for Authorization header
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

            if (accessToken == null) {
                log.error("No access token available for updating room avatar");
                return false;
            }

            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("url", mxcUrl);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    stateUrl,
                    HttpMethod.PUT,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated avatar for room {} to {}", decodedRoomId, mxcUrl);
                return true;
            } else {
                log.error("Failed to update room avatar: HTTP {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating room avatar for {}: {}", decodedRoomId, e.getMessage(), e);
            return false;
        }
    }
}
