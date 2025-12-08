package com.neohoods.portal.platform.assistant.services;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.matrix.ApiException;
import com.neohoods.portal.platform.matrix.api.AccountManagementApi;
import com.neohoods.portal.platform.matrix.api.RoomCreationApi;
import com.neohoods.portal.platform.matrix.api.SessionManagementApi;
import com.neohoods.portal.platform.matrix.model.CreateRoom200Response;
import com.neohoods.portal.platform.matrix.model.CreateRoomRequest;
import com.neohoods.portal.platform.matrix.model.GetTokenOwner200Response;
import com.neohoods.portal.platform.matrix.model.StateEvent;
import com.neohoods.portal.platform.entities.UserEntity;

import com.neohoods.portal.platform.services.Auth0Service;

import com.neohoods.portal.platform.services.matrix.space.MatrixAvatarService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMediaService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMembershipService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMessageService;
import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import com.neohoods.portal.platform.services.matrix.space.MatrixRoomService;
import com.neohoods.portal.platform.services.matrix.space.MatrixUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixAssistantService {

    private final MatrixOAuth2Service oauth2Service;
    private final RestTemplate restTemplate;
    private final Auth0Service auth0Service;
    private final MatrixUserService matrixUserService;
    private final MatrixRoomService matrixRoomService;
    private final MatrixMembershipService matrixMembershipService;
    private final MatrixMessageService matrixMessageService;
    private final MatrixAvatarService matrixAvatarService;
    private final MatrixMediaService matrixMediaService;

    @Value("${neohoods.portal.matrix.disabled}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.server-name}")
    private String serverName;

    @Value("${neohoods.portal.matrix.space-id}")
    private String spaceId;

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

    @Value("${neohoods.portal.matrix.mas.url}")
    private String masUrl;

    /**
     * Get Matrix API client configured with access token
     * Delegates to MatrixOAuth2Service which will automatically use OAuth2 token
     * service as fallback
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
     * Get Matrix API client configured with OAuth2 user access token
     * Delegates to MatrixOAuth2Service
     */
    private Optional<ApiClient> getMatrixAccessTokenWithUserFlow() {
        if (disabled) {
            log.debug("Matrix bot is disabled");
            return Optional.empty();
        }

        return oauth2Service.getMatrixApiClientWithUserToken(homeserverUrl);
    }

    /**
     * Check Matrix access status
     */
    public String checkMatrixAccess() {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return "ko";
            }

            ApiClient apiClient = apiClientOpt.get();
            AccountManagementApi accountApi = new AccountManagementApi(apiClient);
            // Try to get account info to verify access
            // Using a simple API call to verify token
            return "ok";
        } catch (Exception e) {
            log.error("Failed to check Matrix access", e);
            return "ko";
        }
    }

    /**
     * Get current spaces
     * Delegates to MatrixRoomService
     */
    public List<Map<String, String>> getCurrentSpaces() {
        return matrixRoomService.getCurrentSpaces();
    }

    /**
     * Get MAS API client configured with admin access token (client credentials
     * flow)
     * Delegates to MatrixOAuth2Service
     */
    private Optional<com.neohoods.portal.platform.mas.ApiClient> getMASAccessToken() {
        if (disabled) {
            log.debug("Matrix bot is disabled");
            return Optional.empty();
        }

        return oauth2Service.getMASApiClient(masUrl);
    }

    /**
     * Get bot user ID from access token using SDK client
     * If local bot is enabled, returns the configured local bot user ID
     */
    public Optional<String> getAssistantUserId() {
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
                // Call getTokenOwner() which corresponds to GET
                // /_matrix/client/v3/account/whoami
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
     * Find an existing Matrix user by UserEntity
     * Delegates to MatrixUserService
     * 
     * @param user UserEntity containing user information
     * @return Optional containing the Matrix user ID if found, empty otherwise
     */
    public Optional<String> findUserInMatrix(UserEntity user) {
        return matrixUserService.findUserInMatrix(user);
    }

    /**
     * Create a Matrix user via MAS API
     * Delegates to MatrixUserService
     * 
     * @param user UserEntity containing user information
     * @return Optional containing the Matrix user ID (e.g., "@username:server.com")
     */
    public Optional<String> createMatrixUser(UserEntity user) {
        return matrixUserService.createMatrixUser(user);
    }

    /**
     * Invite user to Matrix space
     * Delegates to MatrixMembershipService
     */
    public boolean inviteUserToSpace(String matrixUserId) {
        return matrixMembershipService.inviteUserToSpace(matrixUserId);
    }

    /**
     * Send a message to a room
     * Delegates to MatrixMessageService
     */
    public boolean sendMessage(String roomId, String message) {
        return matrixMessageService.sendMessage(roomId, message);
    }

    /**
     * Sends a typing indicator in a Matrix room
     * Delegates to MatrixMessageService
     * 
     * @param roomId    Matrix room ID
     * @param typing    true to indicate that assistant Alfred is typing, false to
     *                  stop
     * @param timeoutMs Duration in milliseconds during which the indicator remains
     *                  active (default: 30000ms)
     * @return true if the indicator was sent successfully
     */
    public boolean sendTypingIndicator(String roomId, boolean typing, int timeoutMs) {
        return matrixMessageService.sendTypingIndicator(roomId, typing, timeoutMs);
    }

    /**
     * Handle new user - create in Matrix, invite to space and rooms, send greeting
     */
    public void handleNewUser(UserEntity user) {
        if (disabled) {
            log.debug("Matrix bot is disabled, skipping user handling");
            return;
        }

        try {
            // Generate Matrix username from Portal username
            String matrixUsername = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            String matrixUserId = "@" + matrixUsername + ":" + serverName;

            // Check if user already exists in Matrix
            // For now, try to create/invite and handle errors

            // Create user in Matrix if needed
            Optional<String> createdUserId = matrixUserService.createMatrixUser(user);

            if (createdUserId.isEmpty()) {
                // User might already exist, try to use existing
                log.debug("User might already exist in Matrix, proceeding with invitation");
            }

            // Invite to space
            // Note: Room invitations are handled by MatrixAssistantInitializationService
            // which uses the matrix-default-rooms.yaml configuration
            inviteUserToSpace(matrixUserId);
        } catch (Exception e) {
            log.error("Error handling new user for Matrix bot", e);
            // Don't fail user creation if Matrix fails
        }
    }

    /**
     * Get bot status
     */
    public Map<String, Object> getBotStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", !disabled);
        status.put("disabled", disabled);
        status.put("matrixAccess", checkMatrixAccess());
        status.put("hasRefreshToken", oauth2Service.hasRefreshToken());
        status.put("hasAccessToken", oauth2Service.hasAccessToken());
        status.put("spaceId", spaceId);
        status.put("currentSpaces", getCurrentSpaces());
        if (!checkMatrixAccess().equals("ok")) {
            status.put("error", "Cannot access Matrix API");
        }

        return status;
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
     * Extract localpart from Matrix user ID for use in Synapse Admin API.
     * Synapse Admin API expects just the localpart (username) without @ and domain.
     * For example: @alfred-local:chat.neohoods.com -> bot
     * 
     * @param matrixUserId The Matrix user ID (e.g., @user:server.com)
     * @return The localpart (username) without @ and domain
     */
    private String extractLocalpart(String matrixUserId) {
        if (matrixUserId == null || matrixUserId.isEmpty()) {
            return matrixUserId;
        }
        // Remove @ prefix if present
        String localpart = matrixUserId.startsWith("@") ? matrixUserId.substring(1) : matrixUserId;
        // Extract part before : (domain separator)
        int colonIndex = localpart.indexOf(':');
        if (colonIndex > 0) {
            return localpart.substring(0, colonIndex);
        }
        return localpart;
    }

    /**
     * Encode Matrix user ID for use in URL path segments for Matrix Client-Server
     * API.
     * Matrix Client-Server API expects the full user ID encoded in path segments.
     * For example: @alfred-local:chat.neohoods.com -> %40bot%3Achat.neohoods.com
     * 
     * NOTE: This method is deprecated. Use UriComponentsBuilder instead for
     * constructing URLs with path segments, as it handles encoding automatically.
     * 
     * @param matrixUserId The Matrix user ID (e.g., @user:server.com)
     * @return URL-encoded user ID for path segments
     * @deprecated Use UriComponentsBuilder.pathSegment() instead
     */
    @Deprecated
    private String encodeMatrixUserIdForPath(String matrixUserId) {
        if (matrixUserId == null || matrixUserId.isEmpty()) {
            return matrixUserId;
        }
        // Encode only the characters that need encoding in path segments
        // '@' -> %40, ':' -> %3A
        return matrixUserId
                .replace("@", "%40")
                .replace(":", "%3A");
    }

    /**
     * Extract server name from homeserver URL
     */
    private String extractServerName(String url) {
        try {
            String normalizedUrl = normalizeHomeserverUrl(url);
            URI uri = new URI(normalizedUrl);
            String host = uri.getHost();
            if (host != null) {
                return host;
            }
            return "chat.neohoods.com"; // default
        } catch (Exception e) {
            log.error("Failed to extract server name from URL: {}", url, e);
            return "chat.neohoods.com";
        }
    }

    /**
     * Generate random password for Matrix user
     */
    private String generateRandomPassword() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }

    /**
     * Check if space exists
     * Delegates to MatrixRoomService
     */
    public boolean checkSpaceExists(String spaceId) {
        return matrixRoomService.checkSpaceExists(spaceId);
    }

    /**
     * Get all existing rooms in a space (name -> roomId map)
     * Delegates to MatrixRoomService
     */
    public Map<String, String> getExistingRoomsInSpace(String spaceId) {
        return matrixRoomService.getExistingRoomsInSpace(spaceId);
    }

    /**
     * Get room ID by name in a space
     * Delegates to MatrixRoomService
     */
    public Optional<String> getRoomIdByName(String roomName, String spaceId) {
        return matrixRoomService.getRoomIdByName(roomName, spaceId);
    }

    /**
     * Check if a room belongs to a specific space
     * Delegates to MatrixRoomService
     * 
     * @param roomId  The room ID to check
     * @param spaceId The space ID to check against
     * @return true if the room belongs to the space, false otherwise
     */
    public boolean roomBelongsToSpace(String roomId, String spaceId) {
        return matrixRoomService.roomBelongsToSpace(roomId, spaceId);
    }

    /**
     * Find or create a DM room with a specific user
     * Delegates to MatrixRoomService
     * 
     * @param matrixUserId Matrix user ID of the user to create DM with
     * @return Optional containing the DM room ID if found or created successfully
     */
    public Optional<String> findOrCreateDMRoom(String matrixUserId) {
        return matrixRoomService.findOrCreateDMRoom(matrixUserId);
    }

    /**
     * Create room in space (convenience method with allowGuests=true by default)
     */
    public Optional<String> createRoomInSpace(String roomName, String description, String imageUrl, String spaceId) {
        return createRoomInSpace(roomName, description, imageUrl, spaceId, true);
    }

    /**
     * Create room in space
     * Uses user token (from device code or authorization code flow) to create room
     * and add to space by setting m.space.parent in initial_state
     * 
     * @param roomName    Name of the room
     * @param description Description of the room
     * @param imageUrl    Optional image URL for room avatar
     * @param spaceId     Space ID to link the room to
     * @param allowGuests If true, guests can join (guest_access: "can_join"), if
     *                    false, room is restricted (guest_access: "forbidden")
     */
    public Optional<String> createRoomInSpace(String roomName, String description, String imageUrl, String spaceId,
            boolean allowGuests) {
        try {
            // Use user token for room creation
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No user token available for creating room in space");
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomCreationApi roomCreationApi = new RoomCreationApi(apiClient);

            CreateRoomRequest createRequest = new CreateRoomRequest();
            createRequest.setName(roomName);
            createRequest.setTopic(description);
            createRequest.setPreset(CreateRoomRequest.PresetEnum.PUBLIC_CHAT);
            createRequest.setRoomVersion("10");
            // Set encryption to false (non-encrypted) - don't set encryption state event

            // Build initial state events
            List<StateEvent> initialState = new ArrayList<>();

            // Add space parent relationship in initial state
            // This links the room to the space directly during creation
            StateEvent spaceParentEvent = new StateEvent();
            spaceParentEvent.setType("m.space.parent");
            spaceParentEvent.setStateKey(spaceId);
            Map<String, Object> spaceParentContent = new HashMap<>();
            spaceParentContent.put("via", List.of(serverName));
            spaceParentContent.put("canonical", true);
            spaceParentEvent.setContent(spaceParentContent);
            initialState.add(spaceParentEvent);

            // Set join rules to restrict access to space members only
            StateEvent joinRulesEvent = new StateEvent();
            joinRulesEvent.setType("m.room.join_rules");
            joinRulesEvent.setStateKey("");
            Map<String, Object> joinRulesContent = new HashMap<>();
            joinRulesContent.put("join_rule", "restricted");
            List<Map<String, Object>> allowList = new ArrayList<>();
            Map<String, Object> allowEntry = new HashMap<>();
            allowEntry.put("type", "m.room_membership");
            allowEntry.put("room_id", spaceId);
            allowList.add(allowEntry);
            joinRulesContent.put("allow", allowList);
            joinRulesEvent.setContent(joinRulesContent);
            initialState.add(joinRulesEvent);

            // Set guest access: "can_join" for public rooms, "forbidden" for restricted
            // rooms
            StateEvent guestAccessEvent = new StateEvent();
            guestAccessEvent.setType("m.room.guest_access");
            guestAccessEvent.setStateKey("");
            Map<String, Object> guestAccessContent = new HashMap<>();
            guestAccessContent.put("guest_access", allowGuests ? "can_join" : "forbidden");
            guestAccessEvent.setContent(guestAccessContent);
            initialState.add(guestAccessEvent);

            // Set avatar in initial state if provided
            if (imageUrl != null && !imageUrl.isEmpty()) {
                StateEvent avatarEvent = new StateEvent();
                avatarEvent.setType("m.room.avatar");
                avatarEvent.setStateKey("");
                Map<String, Object> avatarContent = new HashMap<>();
                avatarContent.put("url", imageUrl);
                avatarEvent.setContent(avatarContent);
                initialState.add(avatarEvent);
            }

            createRequest.setInitialState(initialState);

            CreateRoom200Response response = roomCreationApi.createRoom(createRequest);
            String roomId = response.getRoomId();
            log.info("Created room {} ({}) and linked to space {} via m.space.parent", roomName, roomId, spaceId);

            return Optional.of(roomId);
        } catch (ApiException e) {
            if (e.getCode() == 403) {
                log.error("Permission denied: Bot doesn't have permission to modify space {} state. Error: {}. " +
                        "Make sure the bot is admin and has proper permissions on the space.", spaceId, e.getMessage());
            } else {
                log.error("Error creating room {} in space {}: {}", roomName, spaceId, e.getMessage(), e);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error creating room {} in space {}", roomName, spaceId, e);
            return Optional.empty();
        }
    }

    /**
     * Update Matrix user profile (displayname, email, phone)
     * Delegates to MatrixUserService
     */
    public boolean updateMatrixUserProfile(String matrixUserId, UserEntity user) {
        return matrixUserService.updateMatrixUserProfile(matrixUserId, user);
    }

    /**
     * Update bot avatar from configured URL
     * Delegates to MatrixAvatarService
     * 
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateBotAvatar() {
        return matrixAvatarService.updateBotAvatar();
    }

    /**
     * Update bot avatar from configured URL
     * Delegates to MatrixAvatarService
     * 
     * @param force If true, force update even if avatar is already set
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateBotAvatar(boolean force) {
        return matrixAvatarService.updateBotAvatar(force);
    }

    /**
     * Update bot display name
     * Delegates to MatrixAvatarService
     * 
     * @return true if display name was updated successfully, false otherwise
     */
    public boolean updateBotDisplayName() {
        return matrixAvatarService.updateBotDisplayName();
    }

    /**
     * Get the membership status of a user in a room
     * Delegates to MatrixMembershipService
     * 
     * @param matrixUserId The Matrix user ID
     * @param roomId       The room ID
     * @return Optional containing the membership status ("join", "invite", "leave",
     *         "ban", or empty if not found)
     */
    public Optional<String> getUserRoomMembership(String matrixUserId, String roomId) {
        return matrixMembershipService.getUserRoomMembership(matrixUserId, roomId);
    }

    /**
     * Preload all room memberships for a list of rooms
     * Delegates to MatrixMembershipService
     * 
     * @param roomIds Map of room names to room IDs
     */
    public void preloadRoomMemberships(Map<String, String> roomIds) {
        matrixMembershipService.preloadRoomMemberships(roomIds);
    }

    /**
     * Dump room status to logs for debugging
     * Delegates to MatrixMembershipService
     * 
     * @param roomIds Map of room names to room IDs
     */
    public void dumpRoomStatus(Map<String, String> roomIds) {
        matrixMembershipService.dumpRoomStatus(roomIds);
    }

    /**
     * Get all room members (from cache or API)
     * Delegates to MatrixMembershipService
     * 
     * @param roomId The room ID
     * @return Map of userId -> membership status, or null if room not found
     */
    public Map<String, String> getRoomMembers(String roomId) {
        return matrixMembershipService.getRoomMembers(roomId);
    }

    /**
     * Check if a user is already a member or invited to a room
     * Delegates to MatrixMembershipService
     * 
     * @param matrixUserId The Matrix user ID
     * @param roomId       The room ID
     * @return true if user is already a member (join) or invited (invite), false
     *         otherwise
     */
    public boolean isUserMemberOfRoom(String matrixUserId, String roomId) {
        return matrixMembershipService.isUserMemberOfRoom(matrixUserId, roomId);
    }

    /**
     * Join a room as the bot
     * Delegates to MatrixMembershipService
     */
    public boolean joinRoomAsBot(String roomId) {
        return matrixMembershipService.joinRoomAsBot(roomId);
    }

    /**
     * Invite user to room with notifications
     * Delegates to MatrixMembershipService
     */
    public boolean inviteUserToRoomWithNotifications(String matrixUserId, String roomId, boolean enableNotifications) {
        return matrixMembershipService.inviteUserToRoomWithNotifications(matrixUserId, roomId, enableNotifications);
    }

    /**
     * Update room avatar from image URL
     * Delegates to MatrixAvatarService
     * 
     * @param roomId   Room ID
     * @param imageUrl Image URL (HTTP/HTTPS or MXC)
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateRoomAvatar(String roomId, String imageUrl) {
        return matrixAvatarService.updateRoomAvatar(roomId, imageUrl);
    }

    /**
     * Perform a Matrix sync operation using the SDK
     * Uses ApiClient's underlying HTTP mechanism
     * 
     * @param syncUrl     Full sync URL with query parameters
     * @param accessToken Access token for authentication
     * @return Sync response data as a Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> performSync(String syncUrl, String accessToken) {
        try {
            // Build ApiClient using SDK
            ApiClient apiClient = new ApiClient();
            apiClient.setHost(homeserverUrl);

            // Set request interceptor to add Authorization header (SDK pattern)
            apiClient.setRequestInterceptor(builder -> {
                builder.header("Authorization", "Bearer " + accessToken);
            });

            // Use RestTemplate but configured through ApiClient for consistency
            // The SDK doesn't expose a direct sync method, so we use the HTTP client
            // that the SDK uses internally
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(accessToken);
            org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                    syncUrl,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("Sync API returned error: HTTP {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error performing sync: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform sync", e);
        }
    }

    /**
     * Parse building letter from unit name (e.g., "A701" -> "A")
     */
    public Optional<String> parseBuildingFromUnitName(String unitName) {
        if (unitName == null || unitName.isEmpty()) {
            return Optional.empty();
        }

        // Extract first letter if it's A, B, or C
        String firstChar = unitName.substring(0, 1).toUpperCase();
        if ("A".equals(firstChar) || "B".equals(firstChar) || "C".equals(firstChar)) {
            return Optional.of(firstChar);
        }

        return Optional.empty();
    }
}
