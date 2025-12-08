package com.neohoods.portal.platform.services.matrix.space;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.neohoods.portal.platform.assistant.services.MatrixAssistantLLMJudgeService;
import com.neohoods.portal.platform.assistant.services.MatrixReactionEvaluationService;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAuthContextService;
import com.neohoods.portal.platform.assistant.MatrixAssistantMessageHandler;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantService;
import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixSyncService {

    private final MatrixOAuth2Service oauth2Service;
    private final MatrixAssistantService matrixAssistantService;
    private final MessageSource messageSource;
    private final MatrixAssistantAuthContextService authContextService;

    // Optional: AI message handler (only available if AI is enabled)
    @Autowired(required = false)
    private MatrixAssistantMessageHandler messageHandler;

    // Optional: Conversation context service (only available if conversation is
    // enabled)
    @Autowired(required = false)
    private MatrixConversationContextService conversationContextService;

    // Optional: Admin command service
    @Autowired(required = false)
    private MatrixAssistantAdminCommandService adminCommandService;

    // Optional: LLM-as-a-Judge service
    @Autowired(required = false)
    private MatrixAssistantLLMJudgeService llmJudgeService;

    // Optional: Reaction evaluation service
    @Autowired(required = false)
    private MatrixReactionEvaluationService reactionEvaluationService;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.local-assistant.user-id}")
    private String assistantUserId;

    @Value("${neohoods.portal.matrix.local-assistant.permanent-token}")
    private String assistantPermanentToken;

    @Value("${neohoods.portal.matrix.local-assistant.enabled}")
    private boolean localAssistantEnabled;

    @Value("${neohoods.portal.matrix.space-id}")
    private String spaceId;

    private String nextBatchToken = null;
    private long podStartupTimestampMs; // Timestamp when pod started (in milliseconds)

    /**
     * Initialize pod startup timestamp and accept pending invitations
     * This ensures we only process messages received after pod startup
     */
    @PostConstruct
    public void initializeStartupTimestamp() {
        podStartupTimestampMs = System.currentTimeMillis();
        log.info("Matrix sync service initialized. Pod startup timestamp: {} (will ignore messages before this)",
                podStartupTimestampMs);

        // Accept all pending invitations on startup
        acceptPendingInvitations();
    }

    /**
     * Accept all pending invitations for the bot
     * Called on startup to ensure the bot joins all rooms it was invited to
     * Uses Matrix SDK client (ApiClient) for consistency
     * 
     * @return number of invitations accepted
     */
    public int acceptPendingInvitations() {
        try {
            // Use MatrixAssistantService to get ApiClient (uses SDK)
            // We need to access the private method, so we'll use reflection or create a
            // public method
            // For now, let's use the same approach as pollMatrixSync but ensure we use SDK
            // patterns
            Optional<String> accessTokenOpt = getAssistantAccessToken();
            if (accessTokenOpt.isEmpty()) {
                log.warn("Cannot accept pending invitations: no bot access token available");
                return 0;
            }

            log.info("Checking for pending invitations on startup using Matrix SDK...");

            // Use MatrixAssistantService to perform sync (uses SDK internally)
            // Create a helper method in MatrixAssistantService or use existing sync method
            String syncUrl = buildSyncUrlForInitialSync();
            @SuppressWarnings("unchecked")
            Map<String, Object> syncData = matrixAssistantService.performSync(syncUrl, accessTokenOpt.get());

            // Extract invited rooms and accept them
            @SuppressWarnings("unchecked")
            Map<String, Object> rooms = (Map<String, Object>) syncData.get("rooms");
            if (rooms != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> invitedRooms = (Map<String, Object>) rooms.get("invite");
                if (invitedRooms != null && !invitedRooms.isEmpty()) {
                    int count = invitedRooms.size();
                    log.info("Found {} pending invitation(s) on startup, accepting them...", count);
                    processRooms(invitedRooms, "invite", true); // Accept all during initialization
                    return count;
                } else {
                    log.info("No pending invitations found on startup");
                    return 0;
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("Error accepting pending invitations on startup", e);
            return 0;
        }
    }

    /**
     * Build sync URL for initial sync (without nextBatchToken to get all current
     * state)
     */
    private String buildSyncUrlForInitialSync() {
        String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                .path("/_matrix/client/v3/sync")
                .queryParam("timeout", "0"); // No timeout for initial sync

        // Don't include nextBatchToken for initial sync to get all current state
        return builder.toUriString();
    }

    /**
     * Poll Matrix sync API to listen for messages
     * Runs every 30 seconds
     */
    @Scheduled(fixedDelay = 5000)
    public void pollMatrixSync() {
        try {
            Optional<String> accessTokenOpt = getAssistantAccessToken();
            if (accessTokenOpt.isEmpty()) {
                log.debug("No bot access token available for sync");
                return;
            }

            // Use MatrixAssistantService to perform sync (uses SDK internally)
            String syncUrl = buildSyncUrl();
            log.debug("Polling Matrix sync API (nextBatch: {})", nextBatchToken);

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> syncData = matrixAssistantService.performSync(syncUrl, accessTokenOpt.get());
                if (syncData != null) {
                    processSyncResponse(syncData);
                }
            } catch (Exception e) {
                log.warn("Sync API returned error: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error polling Matrix sync", e);
        }
    }

    /**
     * Build the sync API URL with query parameters
     */
    private String buildSyncUrl() {
        String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                .path("/_matrix/client/v3/sync")
                .queryParam("timeout", "30000"); // 30 seconds timeout

        if (nextBatchToken != null && !nextBatchToken.isEmpty()) {
            builder.queryParam("since", nextBatchToken);
        }

        return builder.toUriString();
    }

    /**
     * Normalize homeserver URL by adding https:// if missing
     */
    private String normalizeHomeserverUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://matrix.neohoods.com"; // default
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://" + url;
    }

    /**
     * Process the sync response and extract messages
     */
    @SuppressWarnings("unchecked")
    private void processSyncResponse(Map<String, Object> syncResponse) {
        // Update next batch token (only in memory, not persisted)
        // Bot only processes messages from startup time
        Object nextBatchObj = syncResponse.get("next_batch");
        if (nextBatchObj != null) {
            nextBatchToken = nextBatchObj.toString();
            log.debug("Updated next batch token: {}", nextBatchToken);
        }

        // Extract rooms data - handle cases where rooms might be null or missing
        Object roomsObj = syncResponse.get("rooms");
        if (roomsObj == null) {
            log.debug("No rooms in sync response (might be a DM or empty sync)");
            return;
        }

        if (!(roomsObj instanceof Map)) {
            log.warn("Rooms object is not a Map, got: {}", roomsObj.getClass().getName());
            return;
        }

        Map<String, Object> rooms = (Map<String, Object>) roomsObj;

        // Process joined rooms
        Object joinObj = rooms.get("join");
        if (joinObj instanceof Map) {
            Map<String, Object> joinedRooms = (Map<String, Object>) joinObj;
            if (!joinedRooms.isEmpty()) {
                processRooms(joinedRooms, "join", false); // Normal operation: check space membership
            }
        }

        // Process invited rooms (optional, for future use)
        Object inviteObj = rooms.get("invite");
        if (inviteObj instanceof Map) {
            Map<String, Object> invitedRooms = (Map<String, Object>) inviteObj;
            if (!invitedRooms.isEmpty()) {
                processRooms(invitedRooms, "invite", false); // Normal operation: check space membership
            }
        }

        // Process left rooms (for cleanup if needed)
        Object leaveObj = rooms.get("leave");
        if (leaveObj instanceof Map) {
            // Could handle left rooms here if needed
            log.debug("Found left rooms in sync response");
        }
    }

    /**
     * Process rooms and extract timeline events (messages)
     * Also handles automatic invitation acceptance for invited rooms
     * 
     * @param rooms                Map of rooms to process
     * @param membershipType       Type of membership ("invite", "join", "leave")
     * @param acceptAllInvitations If true, accept all invitations regardless of
     *                             space membership (for initialization)
     */
    @SuppressWarnings("unchecked")
    private void processRooms(Map<String, Object> rooms, String membershipType, boolean acceptAllInvitations) {
        for (Map.Entry<String, Object> roomEntry : rooms.entrySet()) {
            String roomId = roomEntry.getKey();
            Object roomDataObj = roomEntry.getValue();

            if (!(roomDataObj instanceof Map)) {
                continue;
            }

            Map<String, Object> roomData = (Map<String, Object>) roomDataObj;

            // Handle invitations: automatically accept them
            // During initialization, accept all invitations (including space invitations)
            // During normal operation, accept invitations to rooms in the configured space
            // OR DMs (2 people)
            if ("invite".equals(membershipType)) {
                log.info("Bot received invitation to room: {}", roomId);

                boolean shouldAccept = acceptAllInvitations;

                if (!acceptAllInvitations) {
                    // Normal operation: check if room belongs to configured space OR is a DM
                    String spaceIdToCheck = spaceId;
                    if (spaceIdToCheck != null && !spaceIdToCheck.isEmpty()) {
                        try {
                            boolean belongsToSpace = matrixAssistantService.roomBelongsToSpace(roomId, spaceIdToCheck);
                            if (!belongsToSpace) {
                                // Room doesn't belong to space - check if it's a DM
                                // Accept invitation first to check member count (we can't check before joining)
                                log.info(
                                        "Room {} does not belong to configured space {}, accepting invitation to check if it's a DM",
                                        roomId, spaceIdToCheck);
                                boolean joined = matrixAssistantService.joinRoomAsBot(roomId);
                                if (joined) {
                                    // Wait a bit for Matrix to update room state
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    // Now check if it's a DM (2 members: bot + user)
                                    boolean isDM = isDirectMessage(roomId);
                                    if (isDM) {
                                        log.info("Room {} is a DM (2 members), keeping invitation", roomId);
                                        shouldAccept = true;
                                    } else {
                                        log.info(
                                                "Room {} is not a DM and doesn't belong to space, will ignore messages from this room",
                                                roomId);
                                        // Don't leave the room, but we'll ignore messages from it
                                        continue; // Skip processing this room
                                    }
                                } else {
                                    log.warn("Failed to join room {} to check if it's a DM", roomId);
                                    continue; // Skip this invitation
                                }
                            } else {
                                shouldAccept = true; // Room belongs to space
                            }
                        } catch (Exception e) {
                            // If check fails (e.g., bot not in room yet), try to accept and check if DM
                            log.debug("Could not verify if room {} belongs to space {}, accepting to check if DM: {}",
                                    roomId, spaceIdToCheck, e.getMessage());
                            boolean joined = matrixAssistantService.joinRoomAsBot(roomId);
                            if (joined) {
                                // Wait a bit for Matrix to update room state
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                boolean isDM = isDirectMessage(roomId);
                                if (isDM) {
                                    log.info("Room {} is a DM (2 members), keeping invitation", roomId);
                                    shouldAccept = true;
                                } else {
                                    log.info(
                                            "Room {} is not a DM and space check failed, will ignore messages from this room",
                                            roomId);
                                    // Don't leave the room, but we'll ignore messages from it
                                    continue;
                                }
                            } else {
                                continue; // Skip this invitation
                            }
                        }
                    } else {
                        shouldAccept = true; // No space configured, accept all
                    }
                } else {
                    // Initialization mode: accept all invitations
                    log.info(
                            "Initialization mode: accepting invitation to room {} (will verify space membership later)",
                            roomId);
                }

                if (shouldAccept) {
                    // Only join if we haven't already joined (in the DM check above)
                    Optional<String> assistantUserIdOpt = matrixAssistantService.getAssistantUserId();
                    boolean alreadyJoined = false;
                    if (assistantUserIdOpt.isPresent()) {
                        Optional<String> membership = matrixAssistantService
                                .getUserRoomMembership(assistantUserIdOpt.get(), roomId);
                        alreadyJoined = membership.isPresent() && "join".equals(membership.get());
                    }

                    if (!alreadyJoined) {
                        boolean joined = matrixAssistantService.joinRoomAsBot(roomId);
                        if (joined) {
                            log.info("Bot successfully accepted invitation and joined room: {}", roomId);
                        } else {
                            log.warn("Bot failed to accept invitation to room: {}", roomId);
                        }
                    } else {
                        log.info("Bot already in room {}, skipping join", roomId);
                    }
                }
                // Don't process timeline events for invited rooms (we're not in the room yet)
                continue;
            }

            // For joined rooms, extract timeline events (messages)
            Object timelineObj = roomData.get("timeline");
            if (timelineObj instanceof Map) {
                Map<String, Object> timeline = (Map<String, Object>) timelineObj;
                Object eventsObj = timeline.get("events");

                if (eventsObj instanceof List) {
                    List<Map<String, Object>> events = (List<Map<String, Object>>) eventsObj;
                    processTimelineEvents(roomId, events);
                }
            }
        }
    }

    /**
     * Process timeline events and extract messages
     */
    @SuppressWarnings("unchecked")
    private void processTimelineEvents(String roomId, List<Map<String, Object>> events) {
        for (Map<String, Object> event : events) {
            String eventType = (String) event.get("type");

            // Process reactions
            if ("m.reaction".equals(eventType)) {
                processReactionEvent(roomId, event);
                continue;
            }

            if (!"m.room.message".equals(eventType)) {
                continue; // Only process message events
            }

            String sender = (String) event.get("sender");
            if (sender == null || sender.equals(assistantUserId)) {
                continue; // Skip messages from the bot itself
            }

            Object contentObj = event.get("content");
            if (!(contentObj instanceof Map)) {
                continue;
            }

            Map<String, Object> content = (Map<String, Object>) contentObj;
            String msgtype = (String) content.get("msgtype");
            String body = (String) content.get("body");

            // Only process text messages
            if ("m.text".equals(msgtype) && body != null && !body.isEmpty()) {
                // Check message timestamp - only process messages after pod startup
                if (!isMessageAfterStartup(event)) {
                    log.debug("Ignoring message from {} (timestamp before pod startup): {}", sender, body);
                    continue;
                }

                // Store all messages in conversation context (for full room context)
                if (conversationContextService != null && !sender.equals(assistantUserId)) {
                    conversationContextService.addUserMessage(roomId, body, sender);
                }

                log.info("Received message in room {} from {}: {}", roomId, sender, body);
                processMessage(roomId, sender, body, event);
            }
        }
    }

    /**
     * Process reaction events (m.reaction)
     */
    @SuppressWarnings("unchecked")
    private void processReactionEvent(String roomId, Map<String, Object> event) {
        try {
            String sender = (String) event.get("sender");
            if (sender == null || sender.equals(assistantUserId)) {
                return; // Skip reactions from the bot itself
            }

            Object contentObj = event.get("content");
            if (!(contentObj instanceof Map)) {
                return;
            }

            Map<String, Object> content = (Map<String, Object>) contentObj;

            // Get the event ID that this reaction relates to
            Map<String, Object> relatesTo = (Map<String, Object>) content.get("m.relates_to");
            if (relatesTo == null) {
                return;
            }

            String relType = (String) relatesTo.get("rel_type");
            if (!"m.annotation".equals(relType)) {
                return; // Only process annotation reactions
            }

            String eventId = (String) relatesTo.get("event_id");
            String key = (String) relatesTo.get("key"); // The emoji

            if (eventId == null || key == null) {
                return;
            }

            log.info("Received reaction {} on message {} from {} in room {}", key, eventId, sender, roomId);

            // Evaluate the reaction
            if (reactionEvaluationService != null) {
                reactionEvaluationService.evaluateReaction(eventId, key, sender);
            }
        } catch (Exception e) {
            log.error("Error processing reaction event", e);
        }
    }

    /**
     * Get bot access token for sync API
     * Uses permanent token if available, otherwise falls back to OAuth2 token
     */
    private Optional<String> getAssistantAccessToken() {
        // Prefer permanent token for local bot
        if (localAssistantEnabled && assistantPermanentToken != null && !assistantPermanentToken.isEmpty()) {
            return Optional.of(assistantPermanentToken);
        }

        // Fallback to OAuth2 token
        return oauth2Service.getUserAccessToken();
    }

    /**
     * Process incoming message and respond if needed
     * Only responds to:
     * - Direct messages (DMs - rooms with exactly 2 members)
     * - Messages that mention the bot (@alfred-local:chat.neohoods.com)
     */
    @SuppressWarnings("unchecked")
    private void processMessage(String roomId, String sender, String messageBody, Map<String, Object> event) {
        if (messageBody == null) {
            return;
        }

        // Check if message mentions the bot using the m.mentions field from the event
        // Matrix events contain a "m.mentions" field in content with a "user_ids" array
        boolean isMention = false;
        try {
            Object contentObj = event.get("content");
            if (contentObj instanceof Map) {
                Map<String, Object> content = (Map<String, Object>) contentObj;
                Object mentionsObj = content.get("m.mentions");
                if (mentionsObj instanceof Map) {
                    Map<String, Object> mentions = (Map<String, Object>) mentionsObj;
                    Object userIdsObj = mentions.get("user_ids");
                    if (userIdsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> userIds = (List<String>) userIdsObj;
                        isMention = userIds.contains(assistantUserId);
                        log.debug("Found mentions in event: {} (bot mentioned: {})", userIds, isMention);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting mentions from event: {}", e.getMessage());
            // Fallback: check in message body if mentions extraction fails
            String normalizedMessage = messageBody.toLowerCase();
            isMention = normalizedMessage.contains(assistantUserId.toLowerCase()) ||
                    normalizedMessage.contains("@bot");
        }

        // Check if it's a direct message (DM) first - DMs don't belong to spaces but
        // should be allowed
        boolean isDirectMessage = isDirectMessage(roomId);

        // Check if the room belongs to the configured space (if space-id is configured)
        // Skip this check for DMs as they don't belong to spaces
        String spaceIdToCheck = spaceId;

        // Verify that the room belongs to the configured space (skip for DMs)
        if (!isDirectMessage && spaceIdToCheck != null && !spaceIdToCheck.isEmpty()) {
            boolean belongsToSpace = matrixAssistantService.roomBelongsToSpace(roomId, spaceIdToCheck);
            if (!belongsToSpace) {
                log.info(
                        "Ignoring message in room {} that does not belong to configured space {} (sender: {}, local assistant: {}, DM: {})",
                        roomId, spaceIdToCheck, sender, localAssistantEnabled, isDirectMessage);
                return;
            }
        }

        // Only respond to:
        // - DMs (direct messages) - always respond in a DM
        // - Messages in a public room that explicitly mention the assistant
        // Alfred
        if (!isDirectMessage && !isMention) {
            log.debug("Ignoring message in public room {} without bot mention (DM: {}, Mention: {}): {}",
                    roomId, isDirectMessage, isMention, messageBody);
            return;
        }

        log.info("Processing message in room {} from {} (DM: {}, Mention: {}): {}",
                roomId, sender, isDirectMessage, isMention, messageBody);

        // Check for admin commands first (before AI handler)
        if (adminCommandService != null) {
            String adminCommandResponse = adminCommandService.handleAdminCommand(sender, messageBody);
            if (adminCommandResponse != null) {
                try {
                    sendMessage(roomId, adminCommandResponse);
                    log.info("Sent admin command response to room {}", roomId);
                } catch (Exception e) {
                    log.error("Failed to send admin command response", e);
                }
                return; // Don't process with AI handler
            }
        }

        // Use AI message handler if available
        if (messageHandler != null) {
            // Send typing indicator immediately to show that assistant Alfred
            // is processing the request
            // Multiple messages can be processed in parallel, each with its own
            // typing indicators
            boolean typingSent = matrixAssistantService.sendTypingIndicator(roomId, true, 30000);
            if (typingSent) {
                log.debug("Typing indicator sent for room {}", roomId);
            } else {
                log.warn("Failed to send typing indicator for room {}", roomId);
            }

            // Store user question for LLM-as-a-Judge evaluation
            final String userQuestionForEvaluation = messageBody;
            final String userIdForEvaluation = sender;
            final String roomIdForEvaluation = roomId;

            messageHandler.handleMessage(roomId, sender, messageBody, isDirectMessage)
                    .subscribe(
                            response -> {
                                // Stop typing indicator before sending response
                                matrixAssistantService.sendTypingIndicator(roomId, false, 0);
                                if (response != null && !response.isEmpty()) {
                                    try {
                                        sendMessage(roomId, response);
                                        log.info("Sent AI response to room {}", roomId);

                                        // Trigger LLM-as-a-Judge evaluation asynchronously
                                        if (llmJudgeService != null) {
                                            // Use a generated message ID (will be updated when we get the real one)
                                            String messageId = java.util.UUID.randomUUID().toString();
                                            llmJudgeService.evaluateResponseAsync(
                                                    roomIdForEvaluation,
                                                    userIdForEvaluation,
                                                    messageId,
                                                    userQuestionForEvaluation,
                                                    response).subscribe(
                                                            null,
                                                            error -> log.error("Error in LLM-as-a-Judge evaluation",
                                                                    error));
                                        }
                                    } catch (Exception e) {
                                        log.error("Failed to send AI response message", e);
                                    }
                                }
                            },
                            error -> {
                                // Stop typing indicator on error
                                matrixAssistantService.sendTypingIndicator(roomId, false, 0);
                                log.error("Error in AI message handler", error);
                                // Fallback to simple response
                                try {
                                    Locale locale = getLocaleForUser(sender);
                                    String errorMessage = messageSource.getMessage("matrix.error.generic", null,
                                            locale);
                                    sendMessage(roomId, errorMessage);
                                } catch (Exception e) {
                                    log.error("Failed to send error response", e);
                                }
                            });
        } else {
            // Fallback: simple hello response if AI handler not available
            if (messageBody.toLowerCase().contains("hello")) {
                try {
                    sendMessage(roomId, "Hello! How can I help you?");
                    log.info("Responded to hello message in room {}", roomId);
                } catch (Exception e) {
                    log.error("Failed to send response message", e);
                }
            }
        }
    }

    /**
     * Check if a room is a direct message (DM)
     * A DM is a room with exactly 2 members (the bot and one other user)
     */
    private boolean isDirectMessage(String roomId) {
        try {
            Optional<String> assistantUserIdOpt = matrixAssistantService.getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                return false;
            }

            String assistantUserId = assistantUserIdOpt.get();

            // Get room members from cache or API
            Map<String, String> roomMembers = matrixAssistantService.getRoomMembers(roomId);
            if (roomMembers == null) {
                return false;
            }

            // Count joined members (exclude invited/left/banned)
            long joinedMembers = roomMembers.entrySet().stream()
                    .filter(entry -> "join".equals(entry.getValue()))
                    .count();

            // A DM has exactly 2 joined members (bot + one user)
            boolean isDM = joinedMembers == 2 && roomMembers.containsKey(assistantUserId);

            if (isDM) {
                log.debug("Room {} is a direct message (2 members: bot + user)", roomId);
            }

            return isDM;
        } catch (Exception e) {
            log.debug("Error checking if room {} is a DM: {}", roomId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if message timestamp is after pod startup
     * Matrix events have an 'origin_server_ts' field (timestamp in milliseconds)
     * 
     * @param event Matrix event
     * @return true if message was received after pod startup, false otherwise
     */
    @SuppressWarnings("unchecked")
    private boolean isMessageAfterStartup(Map<String, Object> event) {
        try {
            Object originServerTsObj = event.get("origin_server_ts");
            if (originServerTsObj == null) {
                // If no timestamp, assume it's old and skip it
                log.debug("Message has no origin_server_ts, skipping");
                return false;
            }

            long messageTimestampMs;
            if (originServerTsObj instanceof Number) {
                messageTimestampMs = ((Number) originServerTsObj).longValue();
            } else {
                // Try to parse as string
                try {
                    messageTimestampMs = Long.parseLong(originServerTsObj.toString());
                } catch (NumberFormatException e) {
                    log.debug("Cannot parse origin_server_ts: {}, skipping message", originServerTsObj);
                    return false;
                }
            }

            // Message must be after pod startup (with small margin for clock skew)
            // Allow 5 seconds margin for potential clock differences
            long marginMs = 5000;
            boolean isAfterStartup = messageTimestampMs >= (podStartupTimestampMs - marginMs);

            if (!isAfterStartup) {
                log.debug("Message timestamp {} is before pod startup {} (margin: {}ms)",
                        messageTimestampMs, podStartupTimestampMs, marginMs);
            }

            return isAfterStartup;
        } catch (Exception e) {
            log.warn("Error checking message timestamp, skipping message: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send a message to a room
     */
    private void sendMessage(String roomId, String message) {
        matrixAssistantService.sendMessage(roomId, message);

        // Store bot's message in conversation context for full room context
        if (conversationContextService != null) {
            conversationContextService.addAssistantResponse(roomId, message);
        }
    }

    /**
     * Gets locale for a Matrix user (defaults to English)
     */
    private Locale getLocaleForUser(String matrixUserId) {
        try {
            MatrixAssistantAuthContext authContext = authContextService.createAuthContext(
                    matrixUserId, null, false);
            if (authContext.hasUser() && authContext.getAuthenticatedUser().getLocale() != null) {
                return authContext.getAuthenticatedUser().getLocale();
            }
        } catch (Exception e) {
            log.debug("Could not get locale for user {}, using default", matrixUserId);
        }
        return Locale.ENGLISH;
    }

}
