package com.neohoods.portal.platform.services.matrix.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.matrix.ApiException;
import com.neohoods.portal.platform.matrix.api.RoomCreationApi;
import com.neohoods.portal.platform.matrix.api.RoomMembershipApi;
import com.neohoods.portal.platform.matrix.api.RoomParticipationApi;
import com.neohoods.portal.platform.matrix.api.SpacesApi;
import com.neohoods.portal.platform.matrix.model.ClientEvent;
import com.neohoods.portal.platform.matrix.model.CreateRoom200Response;
import com.neohoods.portal.platform.matrix.model.CreateRoomRequest;
import com.neohoods.portal.platform.matrix.model.GetJoinedRooms200Response;
import com.neohoods.portal.platform.matrix.api.SessionManagementApi;
import com.neohoods.portal.platform.matrix.model.GetTokenOwner200Response;
import com.neohoods.portal.platform.matrix.model.StateEvent;

import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing Matrix rooms and spaces.
 * Handles room creation, space management, and DM room operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixRoomService {

    private final MatrixOAuth2Service oauth2Service;
    private final MatrixMembershipService membershipService;

    @Value("${neohoods.portal.matrix.disabled}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.server-name}")
    private String serverName;

    @Value("${neohoods.portal.matrix.local-assistant.enabled}")
    private boolean localAssistantEnabled;

    @Value("${neohoods.portal.matrix.local-assistant.user-id}")
    private String localAssistantUserId;

    @Value("${neohoods.portal.matrix.local-assistant.permanent-token}")
    private String localAssistantPermanentToken;

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
     * Get current spaces
     */
    public List<Map<String, String>> getCurrentSpaces() {
        List<Map<String, String>> spaces = new ArrayList<>();
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return spaces;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);
            GetJoinedRooms200Response joinedRooms = membershipApi.getJoinedRooms();

            if (joinedRooms != null && joinedRooms.getJoinedRooms() != null) {
                for (String roomId : joinedRooms.getJoinedRooms()) {
                    Map<String, String> space = new HashMap<>();
                    space.put("spaceId", roomId);
                    space.put("name", roomId); // Room name would need another API call
                    spaces.add(space);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get current spaces", e);
        }
        return spaces;
    }

    /**
     * Check if space exists
     */
    public boolean checkSpaceExists(String spaceId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            SpacesApi spacesApi = new SpacesApi(apiClient);
            // Try to get space hierarchy - if it fails, space doesn't exist
            spacesApi.getSpaceHierarchy(spaceId, null, null, null, null);
            return true;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return false;
            }
            log.error("Error checking if space exists: {}", spaceId, e);
            return false;
        } catch (Exception e) {
            log.error("Error checking if space exists: {}", spaceId, e);
            return false;
        }
    }

    /**
     * Get all existing rooms in a space (name -> roomId map)
     * Loads all rooms once and returns a map for efficient lookup
     */
    public Map<String, String> getExistingRoomsInSpace(String spaceId) {
        Map<String, String> roomsMap = new HashMap<>();
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return roomsMap;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);
            GetJoinedRooms200Response joinedRooms = membershipApi.getJoinedRooms();

            if (joinedRooms != null && joinedRooms.getJoinedRooms() != null) {
                RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

                for (String roomId : joinedRooms.getJoinedRooms()) {
                    // Skip the space itself
                    if (roomId.equals(spaceId)) {
                        continue;
                    }

                    try {
                        // Get all room states and filter manually to avoid SDK union type issues
                        List<ClientEvent> roomStates = participationApi.getRoomState(roomId);

                        // Check if this room belongs to the space by looking for m.space.parent state
                        boolean belongsToSpace = false;
                        String roomName = null;

                        for (ClientEvent state : roomStates) {
                            if (state.getType() != null) {
                                if ("m.space.parent".equals(state.getType())
                                        && spaceId.equals(state.getStateKey())) {
                                    belongsToSpace = true;
                                } else if ("m.room.name".equals(state.getType())
                                        && (state.getStateKey() == null || state.getStateKey().isEmpty())) {
                                    // Extract room name from content
                                    if (state.getContent() != null && state.getContent() instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> content = (Map<String, Object>) state.getContent();
                                        Object nameObj = content.get("name");
                                        if (nameObj != null) {
                                            roomName = nameObj.toString();
                                        }
                                    }
                                }
                            }
                        }

                        if (belongsToSpace && roomName != null && !roomName.isEmpty()) {
                            // Store room name as-is (case-sensitive) for exact matching
                            roomsMap.put(roomName, roomId);
                            log.trace("Added room to map: {} -> {}", roomName, roomId);
                        }
                    } catch (ApiException e) {
                        // Room might not have m.space.parent state or m.room.name state, skip it
                        if (e.getCode() != 404) {
                            log.debug("Error checking room {} state: {}", roomId, e.getMessage());
                        }
                        continue;
                    } catch (Exception e) {
                        log.debug("Error checking room {}: {}", roomId, e.getMessage());
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting existing rooms in space {}", spaceId, e);
        }
        return roomsMap;
    }

    /**
     * Get room ID by name in a space
     * Uses getJoinedRooms() like getCurrentSpaces(), then checks if room belongs to
     * space
     */
    public Optional<String> getRoomIdByName(String roomName, String spaceId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);
            GetJoinedRooms200Response joinedRooms = membershipApi.getJoinedRooms();

            if (joinedRooms != null && joinedRooms.getJoinedRooms() != null) {
                RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

                for (String roomId : joinedRooms.getJoinedRooms()) {
                    // Skip the space itself
                    if (roomId.equals(spaceId)) {
                        continue;
                    }

                    try {
                        // Get all room states and filter manually to avoid SDK union type issues
                        // This is more efficient: one API call instead of two
                        List<ClientEvent> roomStates = participationApi.getRoomState(roomId);

                        // Check if this room belongs to the space by looking for m.space.parent state
                        boolean belongsToSpace = false;
                        String actualRoomName = null;

                        for (ClientEvent state : roomStates) {
                            if (state.getType() != null) {
                                if ("m.space.parent".equals(state.getType())
                                        && spaceId.equals(state.getStateKey())) {
                                    belongsToSpace = true;
                                } else if ("m.room.name".equals(state.getType())
                                        && (state.getStateKey() == null || state.getStateKey().isEmpty())) {
                                    // Extract room name from content
                                    if (state.getContent() != null && state.getContent() instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> content = (Map<String, Object>) state.getContent();
                                        Object nameObj = content.get("name");
                                        if (nameObj != null) {
                                            actualRoomName = nameObj.toString();
                                        }
                                    }
                                }
                            }
                        }

                        // Check if name matches (case-insensitive comparison)
                        if (belongsToSpace && roomName != null && actualRoomName != null
                                && roomName.equalsIgnoreCase(actualRoomName)) {
                            log.debug("Found room {} with ID {} (case-insensitive match)", roomName, roomId);
                            return Optional.of(roomId);
                        }
                    } catch (ApiException e) {
                        // Room might not have m.space.parent state or m.room.name state, skip it
                        if (e.getCode() != 404) {
                            log.debug("Error checking room {} state: {}", roomId, e.getMessage());
                        }
                        continue;
                    } catch (Exception e) {
                        log.debug("Error checking room {}: {}", roomId, e.getMessage());
                        continue;
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting room ID by name: {} in space {}", roomName, spaceId, e);
            return Optional.empty();
        }
    }

    /**
     * Check if a room belongs to a specific space
     * 
     * @param roomId  The room ID to check
     * @param spaceId The space ID to check against
     * @return true if the room belongs to the space, false otherwise
     */
    public boolean roomBelongsToSpace(String roomId, String spaceId) {
        if (roomId == null || spaceId == null || roomId.isEmpty() || spaceId.isEmpty()) {
            return false;
        }

        // If the room is the space itself, it belongs to it
        if (roomId.equals(spaceId)) {
            return true;
        }

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("Cannot check room space membership: no access token available");
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

            try {
                // Get all room states
                List<ClientEvent> roomStates = participationApi.getRoomState(roomId);

                // Check if this room belongs to the space by looking for m.space.parent state
                for (ClientEvent state : roomStates) {
                    if (state.getType() != null && "m.space.parent".equals(state.getType())
                            && spaceId.equals(state.getStateKey())) {
                        log.debug("Room {} belongs to space {}", roomId, spaceId);
                        return true;
                    }
                }
            } catch (ApiException e) {
                // Room might not have m.space.parent state or might not exist
                if (e.getCode() != 404) {
                    log.debug("Error checking room {} state: {}", roomId, e.getMessage());
                }
                return false;
            } catch (Exception e) {
                log.debug("Error checking room {}: {}", roomId, e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking if room {} belongs to space {}", roomId, spaceId, e);
            return false;
        }

        return false;
    }

    /**
     * Find or create a DM room with a specific user
     * 
     * @param matrixUserId Matrix user ID of the user to create DM with
     * @return Optional containing the DM room ID if found or created successfully
     */
    public Optional<String> findOrCreateDMRoom(String matrixUserId) {
        try {
            Optional<String> assistantUserIdOpt = getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                log.warn("Cannot find or create DM room: bot user ID not available");
                return Optional.empty();
            }

            String assistantUserId = assistantUserIdOpt.get();

            // First, try to find existing DM room
            Optional<String> existingDMRoom = findExistingDMRoom(assistantUserId, matrixUserId);
            if (existingDMRoom.isPresent()) {
                log.debug("Found existing DM room {} between {} and {}", existingDMRoom.get(), assistantUserId,
                        matrixUserId);
                return existingDMRoom;
            }

            // No existing DM room found, create a new one
            log.info("No existing DM room found, creating new DM room between {} and {}", assistantUserId,
                    matrixUserId);
            return createDMRoom(matrixUserId);
        } catch (Exception e) {
            log.error("Error finding or creating DM room with user {}: {}", matrixUserId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find existing DM room between bot and a user
     * 
     * @param assistantUserId Bot user ID
     * @param matrixUserId    User ID to find DM with
     * @return Optional containing room ID if found
     */
    private Optional<String> findExistingDMRoom(String assistantUserId, String matrixUserId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);
            GetJoinedRooms200Response joinedRooms = membershipApi.getJoinedRooms();

            if (joinedRooms != null && joinedRooms.getJoinedRooms() != null) {
                for (String roomId : joinedRooms.getJoinedRooms()) {
                    // Check if this is a DM room (exactly 2 members: bot + user)
                    Map<String, String> roomMembers = membershipService.getRoomMembers(roomId);
                    if (roomMembers != null) {
                        long joinedCount = roomMembers.entrySet().stream()
                                .filter(entry -> "join".equals(entry.getValue()))
                                .count();

                        // DM has exactly 2 joined members and both bot and user are in it
                        if (joinedCount == 2 &&
                                roomMembers.containsKey(assistantUserId) &&
                                roomMembers.containsKey(matrixUserId) &&
                                "join".equals(roomMembers.get(assistantUserId)) &&
                                "join".equals(roomMembers.get(matrixUserId))) {
                            log.debug("Found existing DM room {} between {} and {}", roomId, assistantUserId,
                                    matrixUserId);
                            return Optional.of(roomId);
                        }
                    }
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            log.debug("Error finding existing DM room: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Create a new DM room with a user
     * 
     * @param matrixUserId User ID to create DM with
     * @return Optional containing room ID if created successfully
     */
    private Optional<String> createDMRoom(String matrixUserId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token available for creating DM room");
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomCreationApi roomCreationApi = new RoomCreationApi(apiClient);

            CreateRoomRequest createRequest = new CreateRoomRequest();
            createRequest.setPreset(CreateRoomRequest.PresetEnum.TRUSTED_PRIVATE_CHAT);
            createRequest.setRoomVersion("10");
            // Note: DM rooms are identified by having exactly 2 members (bot + user)
            // The TRUSTED_PRIVATE_CHAT preset with invitation creates a private room

            // Invite the user to the DM room
            List<String> inviteList = new ArrayList<>();
            inviteList.add(matrixUserId);
            createRequest.setInvite(inviteList);

            CreateRoom200Response response = roomCreationApi.createRoom(createRequest);
            String roomId = response.getRoomId();
            log.info("Created DM room {} with user {}", roomId, matrixUserId);

            return Optional.of(roomId);
        } catch (ApiException e) {
            log.error("Error creating DM room with user {}: HTTP {} - {}", matrixUserId, e.getCode(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error creating DM room with user {}: {}", matrixUserId, e.getMessage(), e);
            return Optional.empty();
        }
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
}

