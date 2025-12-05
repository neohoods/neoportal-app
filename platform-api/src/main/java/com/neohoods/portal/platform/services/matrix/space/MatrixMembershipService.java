package com.neohoods.portal.platform.services.matrix.space;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.matrix.ApiException;
import com.neohoods.portal.platform.matrix.api.RoomMembershipApi;
import com.neohoods.portal.platform.matrix.api.RoomParticipationApi;
import com.neohoods.portal.platform.matrix.model.ClientEvent;
import com.neohoods.portal.platform.matrix.model.InviteUserRequest;
import com.neohoods.portal.platform.matrix.api.SessionManagementApi;
import com.neohoods.portal.platform.matrix.model.GetTokenOwner200Response;

import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing Matrix room memberships.
 * Handles membership queries, invitations, and room joining operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixMembershipService {

    private final MatrixOAuth2Service oauth2Service;
    private final RestTemplate restTemplate;

    @Value("${neohoods.portal.matrix.disabled}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.space-id}")
    private String spaceId;

    @Value("${neohoods.portal.matrix.local-assistant.enabled}")
    private boolean localAssistantEnabled;

    @Value("${neohoods.portal.matrix.local-assistant.user-id}")
    private String localAssistantUserId;

    @Value("${neohoods.portal.matrix.local-assistant.permanent-token}")
    private String localAssistantPermanentToken;

    // Cache for room memberships: roomId -> (userId -> membership status)
    private Map<String, Map<String, String>> roomMembershipCache = new ConcurrentHashMap<>();

    /**
     * Get Matrix API client configured with access token
     * Delegates to MatrixOAuth2Service which will automatically use OAuth2 token service as fallback
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
     * Get the membership status of a user in a room
     * First checks the cache, then falls back to API call if not cached
     * 
     * @param matrixUserId The Matrix user ID
     * @param roomId       The room ID
     * @return Optional containing the membership status ("join", "invite", "leave",
     *         "ban", or empty if not found)
     */
    public Optional<String> getUserRoomMembership(String matrixUserId, String roomId) {
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

        // Check cache first
        Map<String, String> roomMembers = roomMembershipCache.get(decodedRoomId);
        if (roomMembers != null) {
            String membership = roomMembers.get(matrixUserId);
            if (membership != null) {
                log.debug("Found membership in cache: user {} in room {} = {}", matrixUserId, decodedRoomId,
                        membership);
                return Optional.of(membership);
            }
            // User not in cache means they're not a member
            log.debug("User {} not found in cache for room {}, returning empty", matrixUserId, decodedRoomId);
            return Optional.empty();
        }

        // Cache miss - fallback to API call (shouldn't happen if preload was called)
        log.debug("Room {} not in cache, falling back to API call", decodedRoomId);
        return getUserRoomMembershipFromApi(matrixUserId, decodedRoomId);
    }

    /**
     * Get the membership status from API (fallback when cache is not available)
     */
    private Optional<String> getUserRoomMembershipFromApi(String matrixUserId, String decodedRoomId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

            // Get room state and check for m.room.member events
            List<ClientEvent> roomStates = participationApi.getRoomState(decodedRoomId);

            for (ClientEvent state : roomStates) {
                if (state.getType() != null && "m.room.member".equals(state.getType())) {
                    // Check if this is the membership event for our user
                    if (matrixUserId.equals(state.getStateKey())) {
                        // Check membership status in content
                        if (state.getContent() != null && state.getContent() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> content = (Map<String, Object>) state.getContent();
                            String membership = (String) content.get("membership");
                            // Return membership status (can be "join", "invite", "leave", "ban", etc.)
                            if (membership != null) {
                                return Optional.of(membership);
                            }
                        }
                    }
                }
            }

            return Optional.empty();
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                // Room doesn't exist
                log.debug("Room {} does not exist", decodedRoomId);
                return Optional.empty();
            }
            log.debug("Error checking membership for user {} in room {}: {}", matrixUserId, decodedRoomId,
                    e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Error checking membership for user {} in room {}: {}", matrixUserId, decodedRoomId,
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Preload all room memberships for a list of rooms
     * This populates the cache to avoid repeated API calls
     * 
     * @param roomIds Map of room names to room IDs
     */
    public void preloadRoomMemberships(Map<String, String> roomIds) {
        log.info("Preloading memberships for {} rooms...", roomIds.size());
        int loaded = 0;
        int errors = 0;

        Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
        if (apiClientOpt.isEmpty()) {
            log.warn("Cannot preload room memberships: no access token available");
            return;
        }

        ApiClient apiClient = apiClientOpt.get();
        RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

        for (Map.Entry<String, String> entry : roomIds.entrySet()) {
            String roomName = entry.getKey();
            String roomId = entry.getValue();

            try {
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

                // Get room state and extract all membership events
                List<ClientEvent> roomStates = participationApi.getRoomState(decodedRoomId);
                Map<String, String> roomMembers = new HashMap<>();

                for (ClientEvent state : roomStates) {
                    if (state.getType() != null && "m.room.member".equals(state.getType())) {
                        String userId = state.getStateKey();
                        if (userId != null && state.getContent() != null && state.getContent() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> content = (Map<String, Object>) state.getContent();
                            String membership = (String) content.get("membership");
                            if (membership != null) {
                                roomMembers.put(userId, membership);
                            }
                        }
                    }
                }

                roomMembershipCache.put(decodedRoomId, roomMembers);
                loaded++;
                log.debug("Preloaded {} memberships for room {} ({})", roomMembers.size(), roomName, decodedRoomId);

                // Small delay to avoid rate limiting
                Thread.sleep(100);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    log.debug("Room {} ({}) does not exist, skipping", roomName, roomId);
                } else {
                    log.warn("Error preloading memberships for room {} ({}): HTTP {}", roomName, roomId, e.getCode());
                    errors++;
                }
            } catch (Exception e) {
                log.warn("Error preloading memberships for room {} ({}): {}", roomName, roomId, e.getMessage());
                errors++;
            }
        }

        log.info("Preloaded memberships for {}/{} rooms ({} errors)", loaded, roomIds.size(), errors);
    }

    /**
     * Dump room status to logs for debugging
     * Shows all rooms, their members, and invitation status
     * 
     * @param roomIds Map of room names to room IDs
     */
    public void dumpRoomStatus(Map<String, String> roomIds) {
        log.info("=== Matrix Chat Status Dump ===");
        log.info("Total rooms: {}", roomIds.size());

        int totalMembers = 0;
        int totalInvited = 0;
        int totalJoined = 0;

        for (Map.Entry<String, String> entry : roomIds.entrySet()) {
            String roomName = entry.getKey();
            String roomId = entry.getValue();

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

            Map<String, String> roomMembers = roomMembershipCache.get(decodedRoomId);
            if (roomMembers == null || roomMembers.isEmpty()) {
                log.info("  Room: {} ({}) - No members", roomName, decodedRoomId);
                continue;
            }

            int joined = 0;
            int invited = 0;
            List<String> joinedUsers = new ArrayList<>();
            List<String> invitedUsers = new ArrayList<>();

            for (Map.Entry<String, String> memberEntry : roomMembers.entrySet()) {
                String userId = memberEntry.getKey();
                String status = memberEntry.getValue();
                if ("join".equals(status)) {
                    joined++;
                    joinedUsers.add(userId);
                    totalJoined++;
                } else if ("invite".equals(status)) {
                    invited++;
                    invitedUsers.add(userId);
                    totalInvited++;
                }
            }

            totalMembers += roomMembers.size();

            log.info("  Room: {} ({})", roomName, decodedRoomId);
            log.info("    Total members: {} ({} joined, {} invited)", roomMembers.size(), joined, invited);
            if (!joinedUsers.isEmpty()) {
                log.info("    Joined users: {}", String.join(", ", joinedUsers));
            }
            if (!invitedUsers.isEmpty()) {
                log.info("    Invited users: {}", String.join(", ", invitedUsers));
            }
        }

        log.info("=== Summary ===");
        log.info("Total rooms: {}", roomIds.size());
        log.info("Total memberships: {} ({} joined, {} invited)", totalMembers, totalJoined, totalInvited);
        log.info("=== End Status Dump ===");
    }

    /**
     * Get all room members (from cache or API)
     * Returns a map of userId -> membership status
     * 
     * @param roomId The room ID
     * @return Map of userId -> membership status, or null if room not found
     */
    public Map<String, String> getRoomMembers(String roomId) {
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

        // Check cache first
        Map<String, String> roomMembers = roomMembershipCache.get(decodedRoomId);
        if (roomMembers != null) {
            return roomMembers;
        }

        // Cache miss - get from API
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return null;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);
            List<ClientEvent> roomStates = participationApi.getRoomState(decodedRoomId);
            Map<String, String> members = new HashMap<>();

            for (ClientEvent state : roomStates) {
                if (state.getType() != null && "m.room.member".equals(state.getType())) {
                    String userId = state.getStateKey();
                    if (userId != null && state.getContent() != null && state.getContent() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content = (Map<String, Object>) state.getContent();
                        String membership = (String) content.get("membership");
                        if (membership != null) {
                            members.put(userId, membership);
                        }
                    }
                }
            }

            // Cache the result
            roomMembershipCache.put(decodedRoomId, members);
            return members;
        } catch (Exception e) {
            log.debug("Error getting room members for room {}: {}", decodedRoomId, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a user is already a member or invited to a room
     * Uses getUserRoomMembership to check membership status
     * 
     * @param matrixUserId The Matrix user ID
     * @param roomId       The room ID
     * @return true if user is already a member (join) or invited (invite), false
     *         otherwise
     */
    public boolean isUserMemberOfRoom(String matrixUserId, String roomId) {
        Optional<String> membership = getUserRoomMembership(matrixUserId, roomId);
        return membership.isPresent() && ("join".equals(membership.get()) || "invite".equals(membership.get()));
    }

    /**
     * Join a room as the bot
     * Accepts an invitation if the bot is invited, or joins directly if the room is
     * public
     */
    public boolean joinRoomAsBot(String roomId) {
        try {
            Optional<String> assistantUserIdOpt = getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                log.warn("Cannot join room: bot user ID not available");
                return false;
            }

            String assistantUserId = assistantUserIdOpt.get();

            // Check if bot is already a member
            Optional<String> membership = getUserRoomMembership(assistantUserId, roomId);
            if (membership.isPresent() && "join".equals(membership.get())) {
                log.debug("Bot {} is already a member of room {}", assistantUserId, roomId);
                return true;
            }

            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token for bot to join room");
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

            // Check if bot is already a member
            Optional<String> currentMembership = getUserRoomMembership(assistantUserId, decodedRoomId);
            if (currentMembership.isPresent() && "join".equals(currentMembership.get())) {
                log.debug("Bot {} is already a member of room {}", assistantUserId, decodedRoomId);
                return true;
            }

            // Try to join the room directly using the joinRoom API
            // This will:
            // - Accept the invitation if the bot is invited
            // - Join the room if it's public and joinable
            // This is the correct Matrix way to accept an invitation
            log.info("Bot {} attempting to join room {} (will accept invitation if invited)...", assistantUserId,
                    decodedRoomId);
            try {
                // Use joinRoom API to accept the invitation or join the room
                // The SDK doesn't have a direct joinRoom method, so we use HTTP directly
                String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
                // Use UriComponentsBuilder to properly encode the room ID (avoid double
                // encoding)
                String joinUrl = UriComponentsBuilder
                        .fromHttpUrl(normalizedUrl)
                        .path("/_matrix/client/v3/rooms/{roomId}/join")
                        .buildAndExpand(decodedRoomId)
                        .toUriString();

                // Get access token
                // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
                String accessToken = null;
                if (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()) {
                    accessToken = localAssistantPermanentToken;
                } else {
                    Optional<String> tokenOpt = oauth2Service.getUserAccessToken();
                    if (tokenOpt.isPresent()) {
                        accessToken = tokenOpt.get();
                    }
                }

                if (accessToken == null) {
                    log.warn("No access token available to join room");
                    return false;
                }

                // Use RestTemplate to call join API
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(accessToken);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                        new HashMap<>(), headers);

                try {
                    restTemplate.postForEntity(joinUrl, request, Map.class);
                    log.info("Bot {} successfully joined room {} (accepted invitation)", assistantUserId,
                            decodedRoomId);
                    return true;
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    if (e.getStatusCode().value() == 403) {
                        log.warn("Bot {} does not have permission to join room {}: {}", assistantUserId, decodedRoomId,
                                e.getMessage());
                    } else if (e.getStatusCode().value() == 404) {
                        log.warn("Room {} not found or bot is not invited: {}", decodedRoomId, e.getMessage());
                    } else {
                        log.warn("Failed to join room {}: HTTP {} - {}", decodedRoomId, e.getStatusCode(),
                                e.getMessage());
                    }
                    return false;
                }
            } catch (Exception e) {
                log.error("Error joining room {}: {}", decodedRoomId, e.getMessage(), e);
                return false;
            }
        } catch (Exception e) {
            log.error("Error joining room {} as bot: {}", roomId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Invite user to room with notifications
     * Uses SDK client (RoomMembershipApi) like inviteUserToSpace
     */
    public boolean inviteUserToRoomWithNotifications(String matrixUserId, String roomId, boolean enableNotifications) {
        try {
            // Use Matrix API token for inviting users
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No admin access token for inviting user to room");
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);

            // Decode room ID if it's already URL-encoded (SDK will encode it automatically)
            // The SDK expects unencoded room IDs and will encode them itself
            // Matrix room IDs are like: !KPzpdAfZJOVWUSpHUa:chat.neohoods.com
            // If they come encoded as %21KPzpdAfZJOVWUSpHUa%3Achat.neohoods.com, we need to
            // decode
            String decodedRoomId = roomId;
            try {
                // Try to decode - decode multiple times if needed (in case of double encoding)
                String current = roomId;
                int maxDecodeAttempts = 5;
                for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                    String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                    if (decoded.equals(current)) {
                        // No more decoding possible
                        break;
                    }
                    current = decoded;
                }
                decodedRoomId = current;
                if (!decodedRoomId.equals(roomId)) {
                    log.debug("Decoded room ID from {} to {}", roomId, decodedRoomId);
                }
            } catch (Exception e) {
                // If decoding fails, use original room ID
                log.debug("Room ID {} decoding failed, using as-is: {}", roomId, e.getMessage());
            }

            InviteUserRequest inviteRequest = new InviteUserRequest();
            inviteRequest.setUserId(matrixUserId);

            // Retry logic for 504 Gateway Timeout errors
            int maxRetries = 3;
            long baseDelayMs = 1000; // Start with 1 second
            boolean success = false;
            Exception lastException = null;

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    membershipApi.inviteUser(decodedRoomId, inviteRequest);
                    success = true;
                    break; // Success, exit retry loop
                } catch (ApiException e) {
                    lastException = e;
                    if (e.getCode() == 403) {
                        log.debug("User {} already in room {}", matrixUserId, roomId);
                        return true; // Already in room
                    }
                    if (e.getCode() == 504 && attempt < maxRetries - 1) {
                        // Gateway Timeout - retry with exponential backoff
                        long delayMs = baseDelayMs * (1L << attempt); // Exponential: 1s, 2s, 4s
                        log.warn("Gateway Timeout (504) inviting user {} to room {}, retrying in {}ms (attempt {}/{})",
                                matrixUserId, roomId, delayMs, attempt + 1, maxRetries);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Interrupted while waiting to retry invitation");
                            return false;
                        }
                        continue; // Retry
                    }
                    // Other errors or last retry failed - break and handle below
                    break;
                } catch (Exception e) {
                    lastException = e;
                    // Check if it's a timeout-related error
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("504") || errorMsg.contains("timeout")
                            || errorMsg.contains("Gateway Timeout")) && attempt < maxRetries - 1) {
                        long delayMs = baseDelayMs * (1L << attempt);
                        log.warn("Timeout error inviting user {} to room {}, retrying in {}ms (attempt {}/{})",
                                matrixUserId, roomId, delayMs, attempt + 1, maxRetries);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Interrupted while waiting to retry invitation");
                            return false;
                        }
                        continue; // Retry
                    }
                    // Other errors - break and handle below
                    break;
                }
            }

            if (success) {
                // Update cache with new invitation
                String decodedRoomIdForCache = decodedRoomId;
                roomMembershipCache.computeIfAbsent(decodedRoomIdForCache, k -> new ConcurrentHashMap<>())
                        .put(matrixUserId, "invite");

                // Enable notifications if requested
                if (enableNotifications) {
                    // Set push rules for notifications
                    // This would require additional API calls to set push rules
                    // For now, we'll just log it
                    log.info("User {} invited to room {} with notifications enabled", matrixUserId, roomId);
                }
                log.info("Successfully invited user {} to room {}", matrixUserId, roomId);
                return true;
            } else {
                // All retries failed
                if (lastException instanceof ApiException) {
                    ApiException apiEx = (ApiException) lastException;
                    log.error("Failed to invite user {} to room {} after {} retries: HTTP {} - {}",
                            matrixUserId, roomId, maxRetries, apiEx.getCode(), apiEx.getMessage());
                } else {
                    log.error("Failed to invite user {} to room {} after {} retries",
                            matrixUserId, roomId, maxRetries, lastException);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("Error inviting user to room", e);
            return false;
        }
    }

    /**
     * Invite user to Matrix space
     */
    public boolean inviteUserToSpace(String matrixUserId) {
        if (spaceId == null || spaceId.isEmpty()) {
            log.warn("No space ID configured");
            return false;
        }

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);

            InviteUserRequest inviteRequest = new InviteUserRequest();
            inviteRequest.setUserId(matrixUserId);

            membershipApi.inviteUser(spaceId, inviteRequest);
            log.info("Successfully invited user {} to space {}", matrixUserId, spaceId);
            return true;
        } catch (ApiException e) {
            if (e.getCode() == 403) {
                log.warn("User {} already in space {}", matrixUserId, spaceId);
                return true; // Already in space
            }
            log.error("Failed to invite user to space", e);
            return false;
        } catch (Exception e) {
            log.error("Error inviting user to space", e);
            return false;
        }
    }
}

