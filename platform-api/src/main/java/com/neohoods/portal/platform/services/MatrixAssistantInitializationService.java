package com.neohoods.portal.platform.services;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled",
        "neohoods.portal.matrix.initialization.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixAssistantInitializationService {

    private final MatrixAssistantService matrixAssistantService;
    private final MatrixOAuth2Service oauth2Service;
    private final UsersRepository usersRepository;
    private final ResourceLoader resourceLoader;

    @Value("${neohoods.portal.matrix.space-id:}")
    private String spaceId;

    private static final String IT_ROOM_NAME = "IT";

    @Value("${neohoods.portal.matrix.initialization.rooms-config-file:classpath:matrix-default-rooms.yaml}")
    private String roomsConfigFile;

    @Value("${neohoods.portal.matrix.disabled:false}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url:}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.initialization.admin-users:}")
    private String adminUsersConfig;

    /**
     * Room configuration from YAML
     */
    @SuppressWarnings("unchecked")
    public static class RoomConfig {
        private String name;
        private String description;
        private String image;
        private Boolean autoJoin;

        public RoomConfig(Map<String, Object> map) {
            this.name = (String) map.get("name");
            this.description = (String) map.get("description");
            this.image = (String) map.get("image");
            this.autoJoin = map.get("auto-join") != null ? (Boolean) map.get("auto-join") : true;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getImage() {
            return image;
        }

        public Boolean getAutoJoin() {
            return autoJoin;
        }
    }

    /**
     * Statistics collected during initialization
     */
    public static class InitializationStats {
        private int roomsCreated = 0;
        private int roomsExisting = 0;
        private int roomsErrors = 0;
        private int usersCreated = 0;
        private int usersUpdated = 0;
        private int usersErrors = 0;
        private int spaceInvitationsSent = 0;
        private int roomInvitationsSent = 0;
        private int pendingInvitations = 0;
        private int avatarsUpdated = 0;
        private int avatarsSkipped = 0;
        private int avatarsFailed = 0;

        public int getRoomsCreated() {
            return roomsCreated;
        }

        public void setRoomsCreated(int roomsCreated) {
            this.roomsCreated = roomsCreated;
        }

        public int getRoomsExisting() {
            return roomsExisting;
        }

        public void setRoomsExisting(int roomsExisting) {
            this.roomsExisting = roomsExisting;
        }

        public int getRoomsErrors() {
            return roomsErrors;
        }

        public void setRoomsErrors(int roomsErrors) {
            this.roomsErrors = roomsErrors;
        }

        public int getUsersCreated() {
            return usersCreated;
        }

        public void setUsersCreated(int usersCreated) {
            this.usersCreated = usersCreated;
        }

        public int getUsersUpdated() {
            return usersUpdated;
        }

        public void setUsersUpdated(int usersUpdated) {
            this.usersUpdated = usersUpdated;
        }

        public int getUsersErrors() {
            return usersErrors;
        }

        public void setUsersErrors(int usersErrors) {
            this.usersErrors = usersErrors;
        }

        public int getSpaceInvitationsSent() {
            return spaceInvitationsSent;
        }

        public void setSpaceInvitationsSent(int spaceInvitationsSent) {
            this.spaceInvitationsSent = spaceInvitationsSent;
        }

        public int getRoomInvitationsSent() {
            return roomInvitationsSent;
        }

        public void setRoomInvitationsSent(int roomInvitationsSent) {
            this.roomInvitationsSent = roomInvitationsSent;
        }

        public int getPendingInvitations() {
            return pendingInvitations;
        }

        public void setPendingInvitations(int pendingInvitations) {
            this.pendingInvitations = pendingInvitations;
        }

        public int getAvatarsUpdated() {
            return avatarsUpdated;
        }

        public void setAvatarsUpdated(int avatarsUpdated) {
            this.avatarsUpdated = avatarsUpdated;
        }

        public int getAvatarsSkipped() {
            return avatarsSkipped;
        }

        public void setAvatarsSkipped(int avatarsSkipped) {
            this.avatarsSkipped = avatarsSkipped;
        }

        public int getAvatarsFailed() {
            return avatarsFailed;
        }

        public void setAvatarsFailed(int avatarsFailed) {
            this.avatarsFailed = avatarsFailed;
        }
    }

    /**
     * Result of room invitation process
     */
    public static class RoomInvitationResult {
        private final List<String> newlyInvitedRooms;
        private final List<String> alreadyMemberOrInvitedRooms;

        public RoomInvitationResult() {
            this.newlyInvitedRooms = new ArrayList<>();
            this.alreadyMemberOrInvitedRooms = new ArrayList<>();
        }

        public void addNewlyInvited(String roomName) {
            newlyInvitedRooms.add(roomName);
        }

        public void addAlreadyMemberOrInvited(String roomName) {
            alreadyMemberOrInvitedRooms.add(roomName);
        }

        public List<String> getAllRooms() {
            List<String> all = new ArrayList<>(newlyInvitedRooms);
            all.addAll(alreadyMemberOrInvitedRooms);
            return all;
        }

        public List<String> getNewlyInvitedRooms() {
            return newlyInvitedRooms;
        }

        public List<String> getAlreadyMemberOrInvitedRooms() {
            return alreadyMemberOrInvitedRooms;
        }

        public int getTotalCount() {
            return newlyInvitedRooms.size() + alreadyMemberOrInvitedRooms.size();
        }
    }

    /**
     * Initialize bot on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeBot() {
        doInitializeBot();
    }

    /**
     * Manually trigger bot initialization (for API calls)
     */
    public void initializeBotManually() {
        doInitializeBot();
    }

    /**
     * Internal method that performs the actual initialization
     */
    private void doInitializeBot() {
        if (disabled) {
            log.info("Matrix bot is disabled, skipping initialization");
            return;
        }

        if (spaceId == null || spaceId.isEmpty()) {
            log.warn("No space ID configured, skipping initialization");
            return;
        }

        log.info("Starting Matrix bot initialization...");

        try {
            // 1. Check if space exists
            if (!checkSpace()) {
                log.error("Space {} does not exist, disabling bot", spaceId);
                // Note: We can't actually disable the bot here as it's a config property
                // But we can log the error and skip initialization
                return;
            }

            // 2. Load rooms configuration
            List<RoomConfig> rooms = loadRoomsConfig();

            // 3. Load existing rooms and preload their participants
            Map<String, String> allRooms = matrixAssistantService.getExistingRoomsInSpace(spaceId);
            log.info("Available rooms in space: {}", allRooms);
            if (!allRooms.isEmpty()) {
                log.info("Preloading participants for {} existing rooms...", allRooms.size());
                matrixAssistantService.preloadRoomMemberships(allRooms);
                // Dump room status for debugging
                log.info("=== Initial Chat Status (Before Room Creation) ===");
                matrixAssistantService.dumpRoomStatus(allRooms);
            } else {
                log.info("No existing rooms found in space");
            }

            // Initialize stats
            InitializationStats stats = new InitializationStats();

            // 4. Create default rooms if needed
            if (rooms.isEmpty()) {
                log.warn("No rooms configured, skipping room creation");
            } else {
                createDefaultRooms(rooms, stats);
            }

            // 5. Synchronize users
            synchronizeUsers(rooms, stats);

            // 6. Count pending invitations
            countPendingInvitations(rooms, stats);

            // 7. Update bot avatar and display name if configured
            matrixAssistantService.updateBotAvatar();
            matrixAssistantService.updateBotDisplayName();

            // 8. Create IT room and send summary
            sendInitializationSummary(rooms, stats);

            // 8. Final status dump after synchronization
            if (!allRooms.isEmpty()) {
                log.info("=== Final Status After Synchronization ===");
                // Reload memberships to get updated status
                matrixAssistantService.preloadRoomMemberships(allRooms);
                matrixAssistantService.dumpRoomStatus(allRooms);
            }

            log.info("Matrix bot initialization completed successfully");
        } catch (Exception e) {
            log.error("Error during Matrix bot initialization", e);
            throw e; // Re-throw to allow API to handle error
        }
    }

    /**
     * Check if space exists
     */
    private boolean checkSpace() {
        try {
            boolean exists = matrixAssistantService.checkSpaceExists(spaceId);
            if (!exists) {
                log.error("Space {} does not exist. Bot initialization aborted.", spaceId);
            }
            return exists;
        } catch (Exception e) {
            log.error("Error checking if space exists: {}", spaceId, e);
            return false;
        }
    }

    /**
     * Load rooms configuration from YAML file
     */
    @SuppressWarnings("unchecked")
    private List<RoomConfig> loadRoomsConfig() {
        List<RoomConfig> rooms = new ArrayList<>();
        try {
            Resource resource = resourceLoader.getResource(roomsConfigFile);
            if (!resource.exists()) {
                log.warn("Rooms config file not found: {}", roomsConfigFile);
                return rooms;
            }

            Yaml yaml = new Yaml();
            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> data = yaml.load(inputStream);
                if (data != null && data.containsKey("rooms")) {
                    List<Map<String, Object>> roomsList = (List<Map<String, Object>>) data.get("rooms");
                    for (Map<String, Object> roomMap : roomsList) {
                        rooms.add(new RoomConfig(roomMap));
                    }
                }
            }

            log.info("Loaded {} rooms from configuration", rooms.size());
            return rooms;
        } catch (Exception e) {
            log.error("Error loading rooms configuration from {}", roomsConfigFile, e);
            return rooms;
        }
    }

    /**
     * Create default rooms with rate limiting to avoid 429 errors
     */
    private void createDefaultRooms(List<RoomConfig> rooms, InitializationStats stats) {
        int created = 0;
        int existing = 0;
        int errors = 0;

        // Load all existing rooms in the space once
        Map<String, String> existingRooms = matrixAssistantService.getExistingRoomsInSpace(spaceId);
        log.info("Found {} existing rooms in space", existingRooms.size());

        for (int i = 0; i < rooms.size(); i++) {
            RoomConfig room = rooms.get(i);
            try {
                // Check if room already exists in the pre-loaded map
                String existingRoomId = existingRooms.get(room.getName());
                if (existingRoomId != null) {
                    log.info("Room {} already exists: {}", room.getName(), existingRoomId);
                    existing++;

                    // Update room avatar if image URL is provided and different
                    if (room.getImage() != null && !room.getImage().isEmpty()) {
                        log.info("Updating avatar for existing room {} ({})", room.getName(), existingRoomId);
                        boolean avatarUpdated = matrixAssistantService.updateRoomAvatar(existingRoomId, room.getImage());
                        if (avatarUpdated) {
                            log.info("Successfully updated avatar for room {}", room.getName());
                            stats.setAvatarsUpdated(stats.getAvatarsUpdated() + 1);
                        } else {
                            // Check if it was skipped (already set) or failed
                            // We can't distinguish easily, so we'll log and count as skipped
                            // The updateRoomAvatar method returns false for failures, but true for skipped
                            // Actually, it returns true for skipped, so if false, it's a failure
                            log.warn("Failed to update avatar for room {}", room.getName());
                            stats.setAvatarsFailed(stats.getAvatarsFailed() + 1);
                        }
                    } else {
                        // No image URL provided, count as skipped
                        stats.setAvatarsSkipped(stats.getAvatarsSkipped() + 1);
                    }

                    continue;
                }

                // Create room
                // Rooms with autoJoin=false are restricted (e.g., Proprio,
                // Syndic-de-copropriété)
                // Rooms with autoJoin=true allow guests
                boolean allowGuests = room.getAutoJoin() != null && room.getAutoJoin();
                Optional<String> roomId = matrixAssistantService.createRoomInSpace(
                        room.getName(),
                        room.getDescription(),
                        room.getImage(),
                        spaceId,
                        allowGuests);

                if (roomId.isPresent()) {
                    log.info("Created room: {} ({})", room.getName(), roomId.get());
                    created++;
                    // Avatar is set during room creation, so count as updated if image is provided
                    if (room.getImage() != null && !room.getImage().isEmpty()) {
                        stats.setAvatarsUpdated(stats.getAvatarsUpdated() + 1);
                    } else {
                        stats.setAvatarsSkipped(stats.getAvatarsSkipped() + 1);
                    }
                } else {
                    log.warn("Failed to create room: {}", room.getName());
                    errors++;
                    // Count avatar as failed if image was provided
                    if (room.getImage() != null && !room.getImage().isEmpty()) {
                        stats.setAvatarsFailed(stats.getAvatarsFailed() + 1);
                    }
                }

                // Add delay between room creations to avoid rate limiting (429 errors)
                // Wait 500ms between each room creation, except for the last one
                if (i < rooms.size() - 1) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting between room creations");
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error creating room: {}", room.getName(), e);
                errors++;

                // If we get a 429 error, wait longer before retrying
                // Try to extract retry_after_ms from error response
                long waitTime = 5000; // Default 5 seconds
                if (e instanceof com.neohoods.portal.platform.matrix.ApiException) {
                    com.neohoods.portal.platform.matrix.ApiException apiEx = (com.neohoods.portal.platform.matrix.ApiException) e;
                    if (apiEx.getCode() == 429) {
                        // Try to extract retry_after_ms from response body
                        try {
                            String responseBody = apiEx.getResponseBody();
                            if (responseBody != null && responseBody.contains("retry_after_ms")) {
                                // Parse JSON response: {"errcode":"M_LIMIT_EXCEEDED","error":"Too Many
                                // Requests","retry_after_ms":50237}
                                int retryAfterIndex = responseBody.indexOf("\"retry_after_ms\"");
                                if (retryAfterIndex >= 0) {
                                    int colonIndex = responseBody.indexOf(":", retryAfterIndex);
                                    int commaIndex = responseBody.indexOf(",", colonIndex);
                                    int endIndex = commaIndex > 0 ? commaIndex : responseBody.indexOf("}", colonIndex);
                                    if (colonIndex >= 0 && endIndex > colonIndex) {
                                        String retryAfterStr = responseBody.substring(colonIndex + 1, endIndex).trim();
                                        waitTime = Long.parseLong(retryAfterStr);
                                        log.info("Extracted retry_after_ms from response: {}ms", waitTime);
                                    }
                                }
                            }
                        } catch (Exception parseEx) {
                            log.info("Could not parse retry_after_ms from response body, using default: {}",
                                    parseEx.getMessage());
                        }

                        log.warn("Rate limit hit (429) for room creation, waiting {}ms before continuing...", waitTime);
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Interrupted while waiting after rate limit");
                            break;
                        }
                    }
                } else if (e.getMessage() != null && e.getMessage().contains("429")) {
                    // Fallback: try to extract from error message
                    try {
                        String message = e.getMessage();
                        int retryAfterIndex = message.indexOf("retry_after_ms");
                        if (retryAfterIndex >= 0) {
                            int colonIndex = message.indexOf(":", retryAfterIndex);
                            int commaIndex = message.indexOf(",", colonIndex);
                            int endIndex = commaIndex > 0 ? commaIndex : message.indexOf("}", colonIndex);
                            if (colonIndex >= 0 && endIndex > colonIndex) {
                                String retryAfterStr = message.substring(colonIndex + 1, endIndex).trim();
                                waitTime = Long.parseLong(retryAfterStr);
                                log.info("Extracted retry_after_ms from message: {}ms", waitTime);
                            }
                        }
                    } catch (Exception parseEx) {
                        log.info("Could not parse retry_after_ms from error message, using default");
                    }

                    log.warn("Rate limit hit (429), waiting {}ms before continuing...", waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting after rate limit");
                        break;
                    }
                }
            }
        }

        log.info("Room creation summary: {} created, {} existing, {} errors", created, existing, errors);

        // Update stats
        stats.setRoomsCreated(created);
        stats.setRoomsExisting(existing);
        stats.setRoomsErrors(errors);
    }

    /**
     * Synchronize users with rate limiting to avoid 429 errors
     */
    private void synchronizeUsers(List<RoomConfig> rooms, InitializationStats stats) {
        List<UserEntity> allUsers = usersRepository.findAllWithPrimaryUnit();
        List<String> usernames = allUsers.stream()
                .map(UserEntity::getUsername)
                .collect(Collectors.toList());

        // Improved logging format for large user lists
        if (usernames.size() <= 20) {
            // Small list: show all
            log.info("Synchronizing {} users with Matrix: {}", allUsers.size(), usernames);
        } else {
            // Large list: show summary with first few and last few
            int showCount = 5;
            List<String> firstFew = usernames.subList(0, showCount);
            List<String> lastFew = usernames.subList(usernames.size() - showCount, usernames.size());
            log.info("Synchronizing {} users with Matrix:", allUsers.size());
            log.info("  First {}: {}", showCount, firstFew);
            log.info("  ... ({} more users) ...", usernames.size() - (showCount * 2));
            log.info("  Last {}: {}", showCount, lastFew);

            // Also log in compact format (10 per line) for full visibility
            log.debug("Full user list ({} users):", usernames.size());
            int usersPerLine = 10;
            for (int i = 0; i < usernames.size(); i += usersPerLine) {
                int end = Math.min(i + usersPerLine, usernames.size());
                List<String> batch = usernames.subList(i, end);
                log.debug("  [{}] {}", i + 1, String.join(", ", batch));
            }
        }

        int created = 0;
        int updated = 0;
        int errors = 0;
        Map<String, Integer> roomInvitations = new HashMap<>();

        for (int i = 0; i < allUsers.size(); i++) {
            UserEntity user = allUsers.get(i);
            try {
                // Generate Matrix username (for fallback if user creation fails)
                String matrixUsername = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
                String matrixUserId;

                Optional<String> createdUserId = matrixAssistantService.createMatrixUser(user);

                if (createdUserId.isPresent()) {
                    matrixUserId = createdUserId.get();
                    // Check if user was newly created or already existed
                    // We consider it "created" if the user didn't exist before
                    // For simplicity, we'll count all successful user operations as "created"
                    // (the actual creation logic is in MatrixAssistantService)
                    created++;
                    log.info("Created/found Matrix user: {}", matrixUserId);
                } else {
                    // User creation failed, use generated user ID as fallback
                    matrixUserId = "@" + matrixUsername + ":" + extractServerName();
                    log.warn("Failed to create Matrix user, using generated user ID: {}", matrixUserId);
                }

                // Update user profile
                boolean profileUpdated = matrixAssistantService.updateMatrixUserProfile(matrixUserId, user);
                if (profileUpdated) {
                    updated++;
                }

                // Invite user to space first
                log.info("Inviting user {} ({}) to space {}", user.getUsername(), matrixUserId, spaceId);
                boolean invitedToSpace = matrixAssistantService.inviteUserToSpace(matrixUserId);
                if (invitedToSpace) {
                    log.info("User {} invited to space {}", matrixUserId, spaceId);
                    stats.setSpaceInvitationsSent(stats.getSpaceInvitationsSent() + 1);
                } else {
                    log.warn("Failed to invite user {} to space {}", matrixUserId, spaceId);
                }

                // Invite to appropriate rooms
                log.info("Inviting user {} ({}) to appropriate rooms", user.getUsername(), matrixUserId);
                RoomInvitationResult invitationResult = inviteUserToRooms(user, matrixUserId, rooms);
                List<String> allRooms = invitationResult.getAllRooms();
                if (allRooms.isEmpty()) {
                    log.warn("User {} ({}) was not invited to any rooms", user.getUsername(), matrixUserId);
                } else {
                    int newlyInvited = invitationResult.getNewlyInvitedRooms().size();
                    int alreadyMemberOrInvited = invitationResult.getAlreadyMemberOrInvitedRooms().size();
                    if (alreadyMemberOrInvited > 0 && newlyInvited > 0) {
                        log.info("User {} ({}) invited to {} rooms ({} newly invited, {} already member/invited): {}",
                                user.getUsername(), matrixUserId, allRooms.size(), newlyInvited, alreadyMemberOrInvited,
                                allRooms);
                    } else if (alreadyMemberOrInvited > 0) {
                        log.info("User {} ({}) already member/invited to {} rooms (no new invitations): {}",
                                user.getUsername(), matrixUserId, allRooms.size(), allRooms);
                    } else {
                        log.info("User {} ({}) invited to {} rooms: {}", user.getUsername(), matrixUserId,
                                allRooms.size(), allRooms);
                    }
                }
                for (String roomName : allRooms) {
                    roomInvitations.put(roomName, roomInvitations.getOrDefault(roomName, 0) + 1);
                }

                // Count newly invited rooms
                stats.setRoomInvitationsSent(
                        stats.getRoomInvitationsSent() + invitationResult.getNewlyInvitedRooms().size());

            } catch (Exception e) {
                log.error("Error synchronizing user {} with Matrix", user.getUsername(), e);
                errors++;

                // If we get a 429 error, wait longer before retrying
                // Try to extract retry_after_ms from error message if available
                long waitTime = 3000; // Default 3 seconds
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    // Try to extract retry_after_ms from error message
                    // Format: "retry_after_ms":25562
                    try {
                        String message = e.getMessage();
                        int retryAfterIndex = message.indexOf("retry_after_ms");
                        if (retryAfterIndex >= 0) {
                            int colonIndex = message.indexOf(":", retryAfterIndex);
                            int commaIndex = message.indexOf(",", colonIndex);
                            int endIndex = commaIndex > 0 ? commaIndex : message.indexOf("}", colonIndex);
                            if (colonIndex >= 0 && endIndex > colonIndex) {
                                String retryAfterStr = message.substring(colonIndex + 1, endIndex).trim();
                                waitTime = Long.parseLong(retryAfterStr);
                                log.info("Extracted retry_after_ms: {}ms", waitTime);
                            }
                        }
                    } catch (Exception parseEx) {
                        log.info("Could not parse retry_after_ms from error message, using default");
                    }

                    log.warn("Rate limit hit (429), waiting {}ms before continuing...", waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting after rate limit");
                        break;
                    }
                }
            }
        }

        log.info("User synchronization summary: {} created, {} updated, {} errors", created, updated, errors);
        log.info("Room invitations: {}", roomInvitations);

        // Update stats
        stats.setUsersCreated(created);
        stats.setUsersUpdated(updated);
        stats.setUsersErrors(errors);
    }

    /**
     * Count pending invitations (users with "invite" status in rooms)
     */
    private void countPendingInvitations(List<RoomConfig> rooms, InitializationStats stats) {
        try {
            // Get all rooms in space
            Map<String, String> allRoomsMap = matrixAssistantService.getExistingRoomsInSpace(spaceId);

            // Get all users
            List<UserEntity> allUsers = usersRepository.findAllWithPrimaryUnit();

            int pendingCount = 0;

            for (UserEntity user : allUsers) {
                String matrixUsername = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
                String matrixUserId = "@" + matrixUsername + ":" + extractServerName();

                // Check membership in each room
                for (Map.Entry<String, String> roomEntry : allRoomsMap.entrySet()) {
                    String roomId = roomEntry.getValue();
                    Optional<String> membership = matrixAssistantService.getUserRoomMembership(matrixUserId, roomId);
                    if (membership.isPresent() && "invite".equals(membership.get())) {
                        pendingCount++;
                    }
                }
            }

            stats.setPendingInvitations(pendingCount);
            log.info("Found {} pending invitations", pendingCount);
        } catch (Exception e) {
            log.error("Error counting pending invitations", e);
        }
    }

    /**
     * Invite user to appropriate rooms based on user type and primary unit
     * Includes rate limiting delays between invitations
     */
    private RoomInvitationResult inviteUserToRooms(UserEntity user, String matrixUserId, List<RoomConfig> rooms) {
        // Load all rooms once for efficiency
        Map<String, String> allRoomsMap = matrixAssistantService.getExistingRoomsInSpace(spaceId);
        log.info("Loaded {} rooms from space for user {} invitations", allRoomsMap.size(), matrixUserId);
        RoomInvitationResult result = new RoomInvitationResult();

        // If user is PROPERTY_MANAGEMENT (syndic), invite ONLY to
        // "Syndic-de-copropriété" with notifications
        if (user.getType() == UserType.PROPERTY_MANAGEMENT) {
            String syndicRoomName = "Syndic-de-copropriété";
            // Try to find room in the pre-loaded map first
            String syndicRoomIdStr = allRoomsMap.get(syndicRoomName);
            if (syndicRoomIdStr == null) {
                Optional<String> syndicRoomIdOpt = matrixAssistantService.getRoomIdByName(syndicRoomName, spaceId);
                syndicRoomIdStr = syndicRoomIdOpt.orElse(null);
            }
            if (syndicRoomIdStr != null) {
                // Check if user is already a member or invited before inviting
                Optional<String> membership = matrixAssistantService.getUserRoomMembership(matrixUserId, syndicRoomIdStr);
                if (membership.isPresent()) {
                    if ("join".equals(membership.get())) {
                        log.info("Syndic user {} is already a member (joined) of {} room, skipping invitation",
                                matrixUserId, syndicRoomName);
                    } else if ("invite".equals(membership.get())) {
                        log.info("Syndic user {} is already invited to {} room, skipping invitation",
                                matrixUserId, syndicRoomName);
                    }
                    result.addAlreadyMemberOrInvited(syndicRoomName);
                } else {
                    log.info("Inviting syndic user {} to {} room ({})", matrixUserId, syndicRoomName,
                            syndicRoomIdStr);
                    boolean invited = matrixAssistantService.inviteUserToRoomWithNotifications(
                            matrixUserId,
                            syndicRoomIdStr,
                            true // Enable notifications
                    );
                    if (invited) {
                        result.addNewlyInvited(syndicRoomName);
                        log.info("Successfully invited syndic user {} to {} room", matrixUserId, syndicRoomName);
                    } else {
                        log.warn("Failed to invite syndic user {} to {} room", matrixUserId, syndicRoomName);
                    }
                }
            } else {
                log.warn("Syndic room {} not found in space {}, cannot invite syndic user {}", syndicRoomName,
                        spaceId, matrixUserId);
            }
            return result; // Return early, don't invite to other rooms
        }

        // For other users, invite to auto-join rooms
        for (RoomConfig room : rooms) {
            if (Boolean.TRUE.equals(room.getAutoJoin())) {
                // Try to find room in the pre-loaded map first (more efficient)
                String roomIdStr = allRoomsMap.get(room.getName());
                if (roomIdStr == null) {
                    // Fallback to API call if not found in map
                    Optional<String> roomIdOpt = matrixAssistantService.getRoomIdByName(room.getName(), spaceId);
                    roomIdStr = roomIdOpt.orElse(null);
                }
                if (roomIdStr != null) {
                    // Check if user is already a member or invited before inviting
                    Optional<String> membership = matrixAssistantService.getUserRoomMembership(matrixUserId, roomIdStr);
                    if (membership.isPresent()) {
                        if ("join".equals(membership.get())) {
                            log.info("User {} is already a member (joined) of room {}, skipping invitation",
                                    matrixUserId, room.getName());
                        } else if ("invite".equals(membership.get())) {
                            log.info("User {} is already invited to room {}, skipping invitation", matrixUserId,
                                    room.getName());
                        }
                        result.addAlreadyMemberOrInvited(room.getName());
                    } else {
                        log.info("Inviting user {} to auto-join room {} ({})", matrixUserId, room.getName(),
                                roomIdStr);
                        boolean invited = matrixAssistantService.inviteUserToRoomWithNotifications(
                                matrixUserId,
                                roomIdStr,
                                false // No special notifications
                        );
                        if (invited) {
                            result.addNewlyInvited(room.getName());
                            log.info("Successfully invited user {} to room {}", matrixUserId, room.getName());
                        } else {
                            log.warn("Failed to invite user {} to room {}", matrixUserId, room.getName());
                        }

                        // Add delay between invitations to avoid overwhelming the server
                        // This helps prevent 504 Gateway Timeout errors
                        try {
                            Thread.sleep(200); // 200ms delay between invitations
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Interrupted while waiting between invitations");
                            break;
                        }
                    }
                } else {
                    log.warn("Room {} not found in space {}, cannot invite user {}", room.getName(), spaceId,
                            matrixUserId);
                }
            }
        }

        // Invite to building-specific rooms based on primaryUnit
        // Handle lazy initialization safely
        String unitName = null;
        try {
            if (user.getPrimaryUnit() != null) {
                unitName = user.getPrimaryUnit().getName();
            }
        } catch (org.hibernate.LazyInitializationException e) {
            log.info("Could not access primaryUnit name for user {} due to lazy initialization", matrixUserId);
            unitName = null;
        }
        if (unitName != null) {
            Optional<String> building = matrixAssistantService.parseBuildingFromUnitName(unitName);
            if (building.isPresent()) {
                String buildingRoomName = "Batiment" + building.get();
                // Try to find room in the pre-loaded map first
                String buildingRoomIdStr = allRoomsMap.get(buildingRoomName);
                if (buildingRoomIdStr == null) {
                    Optional<String> buildingRoomIdOpt = matrixAssistantService.getRoomIdByName(buildingRoomName,
                            spaceId);
                    buildingRoomIdStr = buildingRoomIdOpt.orElse(null);
                }
                if (buildingRoomIdStr != null) {
                    // Check if user is already a member or invited before inviting
                    Optional<String> membership = matrixAssistantService.getUserRoomMembership(matrixUserId,
                            buildingRoomIdStr);
                    if (membership.isPresent()) {
                        if ("join".equals(membership.get())) {
                            log.info(
                                    "User {} is already a member (joined) of building room {}, skipping invitation",
                                    matrixUserId, buildingRoomName);
                        } else if ("invite".equals(membership.get())) {
                            log.info("User {} is already invited to building room {}, skipping invitation",
                                    matrixUserId, buildingRoomName);
                        }
                        result.addAlreadyMemberOrInvited(buildingRoomName);
                    } else {
                        log.info("Inviting user {} to building room {} ({})", matrixUserId, buildingRoomName,
                                buildingRoomIdStr);
                        boolean invited = matrixAssistantService.inviteUserToRoomWithNotifications(
                                matrixUserId,
                                buildingRoomIdStr,
                                false);
                        if (invited) {
                            result.addNewlyInvited(buildingRoomName);
                            log.info("Successfully invited user {} to building room {}", matrixUserId,
                                    buildingRoomName);
                        } else {
                            log.warn("Failed to invite user {} to building room {}", matrixUserId,
                                    buildingRoomName);
                        }
                    }
                } else {
                    log.warn("Building room {} not found in space {}, cannot invite user {}", buildingRoomName,
                            spaceId, matrixUserId);
                }
            }
        }

        // Invite to "Proprio" if user is OWNER
        if (user.getType() == UserType.OWNER) {
            // Try to find room in the pre-loaded map first
            String proprioRoomIdStr = allRoomsMap.get("Proprio");
            if (proprioRoomIdStr == null) {
                Optional<String> proprioRoomIdOpt = matrixAssistantService.getRoomIdByName("Proprio", spaceId);
                proprioRoomIdStr = proprioRoomIdOpt.orElse(null);
            }
            if (proprioRoomIdStr != null) {
                // Check if user is already a member or invited before inviting
                Optional<String> membership = matrixAssistantService.getUserRoomMembership(matrixUserId,
                        proprioRoomIdStr);
                if (membership.isPresent()) {
                    if ("join".equals(membership.get())) {
                        log.info("Owner user {} is already a member (joined) of Proprio room, skipping invitation",
                                matrixUserId);
                    } else if ("invite".equals(membership.get())) {
                        log.info("Owner user {} is already invited to Proprio room, skipping invitation",
                                matrixUserId);
                    }
                    result.addAlreadyMemberOrInvited("Proprio");
                } else {
                    log.info("Inviting owner user {} to Proprio room ({})", matrixUserId, proprioRoomIdStr);
                    boolean invited = matrixAssistantService.inviteUserToRoomWithNotifications(
                            matrixUserId,
                            proprioRoomIdStr,
                            false);
                    if (invited) {
                        result.addNewlyInvited("Proprio");
                        log.info("Successfully invited owner user {} to Proprio room", matrixUserId);
                    } else {
                        log.warn("Failed to invite owner user {} to Proprio room", matrixUserId);
                    }
                }
            } else {
                log.warn("Proprio room not found in space {}, cannot invite owner user {}", spaceId, matrixUserId);
            }
        }

        return result;
    }

    /**
     * Build display name for user
     */
    private String buildDisplayName(UserEntity user) {
        String displayName = (user.getFirstName() != null ? user.getFirstName() : "") +
                " " + (user.getLastName() != null ? user.getLastName() : "");
        if (user.getPrimaryUnit() != null && user.getPrimaryUnit().getName() != null) {
            displayName += " [" + user.getPrimaryUnit().getName() + "]";
        }
        return displayName.trim();
    }

    /**
     * Send initialization summary to IT room
     */
    private void sendInitializationSummary(List<RoomConfig> rooms, InitializationStats stats) {
        try {
            // Find or create IT room
            Optional<String> itRoomId = matrixAssistantService.getRoomIdByName(IT_ROOM_NAME, spaceId);
            if (itRoomId.isEmpty()) {
                // Create IT room
                // IT room is a restricted room (allowGuests=false)
                itRoomId = matrixAssistantService.createRoomInSpace(
                        IT_ROOM_NAME,
                        "Room pour les notifications techniques de l'assistant Matrix",
                        null,
                        spaceId,
                        false); // IT room is restricted
            }

            if (itRoomId.isEmpty()) {
                log.error("Could not find or create IT room");
                return;
            }

            // Make sure bot joins the IT room
            log.info("Ensuring bot joins IT room: {}", itRoomId.get());
            boolean botJoined = matrixAssistantService.joinRoomAsBot(itRoomId.get());
            if (botJoined) {
                log.info("Bot successfully joined IT room");
            } else {
                log.warn("Bot failed to join IT room, message sending may fail");
            }

            // Invite all admin users to IT room
            // Use MATRIX_INITIALIZATION_ADMIN_USERS directly instead of finding via MAS/OAuth2
            List<String> adminMatrixUserIds = new ArrayList<>();
            if (adminUsersConfig != null && !adminUsersConfig.isEmpty()) {
                // Parse comma-separated list of Matrix user IDs
                String[] userIds = adminUsersConfig.split(",");
                for (String userId : userIds) {
                    String trimmed = userId.trim();
                    if (!trimmed.isEmpty()) {
                        adminMatrixUserIds.add(trimmed);
                    }
                }
            }

            // Also check database for admin users as fallback
            if (adminMatrixUserIds.isEmpty()) {
                log.debug("No admin users configured in MATRIX_INITIALIZATION_ADMIN_USERS, checking database...");
                usersRepository.findAll().forEach(user -> {
                    if (user.getType() == UserType.ADMIN) {
                        // Try to find Matrix user ID via MAS (may fail if OAuth2 is not available)
                        Optional<String> matrixUserIdOpt = matrixAssistantService.findUserInMatrix(user);
                        if (matrixUserIdOpt.isPresent()) {
                            adminMatrixUserIds.add(matrixUserIdOpt.get());
                        } else {
                            log.warn("Admin {} not found in Matrix via MAS, skipping IT room invitation",
                                    user.getUsername());
                        }
                    }
                });
            }

            if (!adminMatrixUserIds.isEmpty()) {
                log.info("Inviting {} admin users to IT room", adminMatrixUserIds.size());
                for (String matrixUserId : adminMatrixUserIds) {
                    try {
                        // Check if admin is already in room
                        Optional<String> membership = matrixAssistantService.getUserRoomMembership(matrixUserId,
                                itRoomId.get());
                        if (membership.isPresent() && "join".equals(membership.get())) {
                            log.debug("Admin {} is already a member of IT room", matrixUserId);
                            continue;
                        }

                        // Invite admin to IT room using bot permanent token
                        boolean invited = matrixAssistantService.inviteUserToRoomWithNotifications(
                                matrixUserId,
                                itRoomId.get(),
                                false);
                        if (invited) {
                            log.info("Invited admin {} to IT room", matrixUserId);
                        } else {
                            log.warn("Failed to invite admin {} to IT room", matrixUserId);
                        }
                    } catch (Exception e) {
                        log.warn("Error inviting admin {} to IT room: {}", matrixUserId, e.getMessage());
                    }
                }
            } else {
                log.info("No admin users found to invite to IT room");
            }

            // Build summary message
            StringBuilder summary = new StringBuilder();
            summary.append("🎉 **Initialisation de l'assistant Matrix terminée avec succès !**\n\n");

            // Space section
            summary.append("📦 **Space Matrix**\n");
            summary.append("   └─ Space ID: `").append(spaceId).append("`\n");
            summary.append("   └─ Statut: ✅ Vérifié et opérationnel\n\n");

            // Rooms section - Actions during initialization
            summary.append("🏠 **Rooms - Actions effectuées**\n");
            summary.append("   ├─ Créées: **").append(stats.getRoomsCreated()).append("**\n");
            summary.append("   ├─ Déjà existantes: **").append(stats.getRoomsExisting()).append("**\n");
            if (stats.getRoomsErrors() > 0) {
                summary.append("   ├─ Erreurs: **").append(stats.getRoomsErrors()).append("** ⚠️\n");
            }
            summary.append("   └─ Total configurées: **").append(rooms.size()).append(" rooms**\n");

            // Count rooms by auto-join
            long autoJoinCount = rooms.stream().filter(r -> Boolean.TRUE.equals(r.getAutoJoin())).count();
            long manualCount = rooms.size() - autoJoinCount;
            summary.append("      ├─ Auto-join: **").append(autoJoinCount).append("** (ajout automatique)\n");
            summary.append("      └─ Manuelles: **").append(manualCount).append("** (invitation requise)\n\n");

            // Avatars section
            if (stats.getAvatarsUpdated() > 0 || stats.getAvatarsSkipped() > 0 || stats.getAvatarsFailed() > 0) {
                summary.append("🖼️  **Avatars des rooms - Actions effectuées**\n");
                summary.append("   ├─ Mis à jour: **").append(stats.getAvatarsUpdated()).append("**\n");
                if (stats.getAvatarsSkipped() > 0) {
                    summary.append("   ├─ Ignorés: **").append(stats.getAvatarsSkipped()).append("** (pas d'image configurée)\n");
                }
                if (stats.getAvatarsFailed() > 0) {
                    summary.append("   ├─ Échecs: **").append(stats.getAvatarsFailed()).append("** ⚠️\n");
                }
                summary.append("   └─ Total traité: **").append(stats.getAvatarsUpdated() + stats.getAvatarsSkipped() + stats.getAvatarsFailed()).append("**\n\n");
            }

            // Users section - Actions during initialization
            summary.append("👥 **Utilisateurs - Actions effectuées**\n");
            summary.append("   ├─ Créés: **").append(stats.getUsersCreated()).append("**\n");
            summary.append("   ├─ Mis à jour: **").append(stats.getUsersUpdated()).append("**\n");
            if (stats.getUsersErrors() > 0) {
                summary.append("   ├─ Erreurs: **").append(stats.getUsersErrors()).append("** ⚠️\n");
            }

            // Get user stats
            List<UserEntity> allUsers = new ArrayList<>();
            usersRepository.findAll().forEach(allUsers::add);

            // Count by type
            long syndicCount = allUsers.stream().filter(u -> u.getType() == UserType.PROPERTY_MANAGEMENT).count();
            long ownerCount = allUsers.stream().filter(u -> u.getType() == UserType.OWNER).count();
            long otherCount = allUsers.size() - syndicCount - ownerCount;

            summary.append("   └─ Total synchronisés: **").append(allUsers.size()).append(" utilisateurs**\n");
            if (syndicCount > 0 || ownerCount > 0 || otherCount > 0) {
                summary.append("      ├─ 🏢 Syndics: **").append(syndicCount).append("**\n");
                summary.append("      ├─ 🏡 Propriétaires: **").append(ownerCount).append("**\n");
                if (otherCount > 0) {
                    summary.append("      └─ 👤 Autres: **").append(otherCount).append("**\n");
                } else {
                    summary.append("      └─ 👤 Autres: **0**\n");
                }
            }
            summary.append("\n");

            // Invitations section
            summary.append("📨 **Invitations envoyées**\n");
            summary.append("   ├─ Space: **").append(stats.getSpaceInvitationsSent()).append("** invitations\n");
            summary.append("   ├─ Rooms: **").append(stats.getRoomInvitationsSent()).append("** invitations\n");
            summary.append("   └─ En attente: **").append(stats.getPendingInvitations())
                    .append("** invitations non acceptées");
            if (stats.getPendingInvitations() > 0) {
                summary.append(" ⏳");
            }

            summary.append("\n\n✨ **Le bot est maintenant opérationnel et prêt à répondre aux messages !**");

            // Send message
            matrixAssistantService.sendMessage(itRoomId.get(), summary.toString());
            log.info("Sent initialization summary to IT room: {}", itRoomId.get());
        } catch (Exception e) {
            log.error("Error sending initialization summary", e);
        }
    }

    /**
     * Extract server name from homeserver URL
     */
    private String extractServerName() {
        try {
            if (homeserverUrl == null || homeserverUrl.isEmpty()) {
                return "chat.neohoods.com";
            }
            java.net.URI uri = new java.net.URI(homeserverUrl);
            String host = uri.getHost();
            if (host != null) {
                return host;
            }
            return "chat.neohoods.com"; // default
        } catch (Exception e) {
            return "chat.neohoods.com";
        }
    }

    /**
     * Generate random password
     */
    private String generateRandomPassword() {
        return java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString();
    }
}
