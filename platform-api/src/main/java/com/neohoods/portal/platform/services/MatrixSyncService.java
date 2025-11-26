package com.neohoods.portal.platform.services;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.neohoods.portal.platform.matrix.ApiClient;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixSyncService {

    private final MatrixOAuth2Service oauth2Service;
    private final MatrixBotService matrixBotService;
    private final RestTemplate restTemplate;

    @Value("${neohoods.portal.matrix.homeserver-url:}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.local-bot.user-id:@bot:chat.neohoods.com}")
    private String botUserId;

    @Value("${neohoods.portal.matrix.local-bot.permanent-token:}")
    private String botPermanentToken;

    @Value("${neohoods.portal.matrix.local-bot.enabled:false}")
    private boolean localBotEnabled;

    private String nextBatchToken = null;
    private long podStartupTimestampMs; // Timestamp when pod started (in milliseconds)

    /**
     * Initialize pod startup timestamp
     * This ensures we only process messages received after pod startup
     */
    @PostConstruct
    public void initializeStartupTimestamp() {
        podStartupTimestampMs = System.currentTimeMillis();
        log.info("Matrix sync service initialized. Pod startup timestamp: {} (will ignore messages before this)", 
                podStartupTimestampMs);
    }

    /**
     * Poll Matrix sync API to listen for messages
     * Runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000)
    public void pollMatrixSync() {
        try {
            Optional<String> accessTokenOpt = getBotAccessToken();
            if (accessTokenOpt.isEmpty()) {
                log.debug("No bot access token available for sync");
                return;
            }

            String syncUrl = buildSyncUrl();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessTokenOpt.get());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            log.debug("Polling Matrix sync API (nextBatch: {})", nextBatchToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    syncUrl,
                    HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> syncData = (Map<String, Object>) response.getBody();
                processSyncResponse(syncData);
            } else {
                log.warn("Sync API returned status: {}", response.getStatusCode());
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
                processRooms(joinedRooms, "join");
            }
        }

        // Process invited rooms (optional, for future use)
        Object inviteObj = rooms.get("invite");
        if (inviteObj instanceof Map) {
            Map<String, Object> invitedRooms = (Map<String, Object>) inviteObj;
            if (!invitedRooms.isEmpty()) {
                processRooms(invitedRooms, "invite");
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
     */
    @SuppressWarnings("unchecked")
    private void processRooms(Map<String, Object> rooms, String membershipType) {
        for (Map.Entry<String, Object> roomEntry : rooms.entrySet()) {
            String roomId = roomEntry.getKey();
            Object roomDataObj = roomEntry.getValue();

            if (!(roomDataObj instanceof Map)) {
                continue;
            }

            Map<String, Object> roomData = (Map<String, Object>) roomDataObj;

            // Extract timeline events
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
            if (!"m.room.message".equals(eventType)) {
                continue; // Only process message events
            }

            String sender = (String) event.get("sender");
            if (sender == null || sender.equals(botUserId)) {
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
                log.info("Received message in room {} from {}: {}", roomId, sender, body);
                processMessage(roomId, sender, body, event);
            }
        }
    }

    /**
     * Get bot access token for sync API
     * Uses permanent token if available, otherwise falls back to OAuth2 token
     */
    private Optional<String> getBotAccessToken() {
        // Prefer permanent token for local bot
        if (localBotEnabled && botPermanentToken != null && !botPermanentToken.isEmpty()) {
            return Optional.of(botPermanentToken);
        }

        // Fallback to OAuth2 token
        return oauth2Service.getUserAccessToken();
    }

    /**
     * Process incoming message and respond if needed
     * Only responds to:
     * - Direct messages (DMs - rooms with exactly 2 members)
     * - Messages that mention the bot (@bot:chat.neohoods.com)
     */
    @SuppressWarnings("unchecked")
    private void processMessage(String roomId, String sender, String messageBody, Map<String, Object> event) {
        if (messageBody == null) {
            return;
        }

        // Check if message mentions the bot
        boolean isMention = messageBody.contains(botUserId) || messageBody.contains("@bot");
        
        // Check if it's a direct message (DM)
        boolean isDirectMessage = isDirectMessage(roomId);

        // Only respond to DMs or mentions
        if (!isDirectMessage && !isMention) {
            log.debug("Ignoring message in room {} (not a DM and no mention): {}", roomId, messageBody);
            return;
        }

        log.info("Processing message in room {} from {} (DM: {}, Mention: {}): {}", 
                roomId, sender, isDirectMessage, isMention, messageBody);

        // Hello world test: respond if message contains "hello"
        if (messageBody.toLowerCase().contains("hello")) {
            try {
                sendMessage(roomId, "Hello! How can I help you?");
                log.info("Responded to hello message in room {}", roomId);
            } catch (Exception e) {
                log.error("Failed to send response message", e);
            }
        }
    }

    /**
     * Check if a room is a direct message (DM)
     * A DM is a room with exactly 2 members (the bot and one other user)
     */
    private boolean isDirectMessage(String roomId) {
        try {
            Optional<String> botUserIdOpt = matrixBotService.getBotUserId();
            if (botUserIdOpt.isEmpty()) {
                return false;
            }

            String botUserId = botUserIdOpt.get();
            
            // Get room members from cache or API
            Map<String, String> roomMembers = matrixBotService.getRoomMembers(roomId);
            if (roomMembers == null) {
                return false;
            }

            // Count joined members (exclude invited/left/banned)
            long joinedMembers = roomMembers.entrySet().stream()
                    .filter(entry -> "join".equals(entry.getValue()))
                    .count();

            // A DM has exactly 2 joined members (bot + one user)
            boolean isDM = joinedMembers == 2 && roomMembers.containsKey(botUserId);
            
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
        matrixBotService.sendMessage(roomId, message);
    }

}
