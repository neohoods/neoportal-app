package com.neohoods.portal.platform.services.matrix.space;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import com.neohoods.portal.platform.matrix.api.RoomParticipationApi;
import com.neohoods.portal.platform.matrix.api.SessionManagementApi;
import com.neohoods.portal.platform.matrix.model.GetTokenOwner200Response;

import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing Matrix messages.
 * Handles message sending, typing indicators, and Markdown conversion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixMessageService {

    private final MatrixOAuth2Service oauth2Service;
    private final MatrixMembershipService membershipService;
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

    /**
     * Get Matrix API client configured with access token
     * Uses MatrixOAuth2Service which will automatically use OAuth2 token service as fallback
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
     * Convert Markdown to HTML for Matrix formatted messages
     * Supports: **bold**, *italic*, line breaks
     * 
     * @param markdown Markdown text
     * @return HTML formatted text, or original text if no formatting detected
     */
    public String convertMarkdownToMatrixHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }

        // Check if message contains Markdown formatting
        boolean hasMarkdown = markdown.contains("**") || markdown.contains("*") || markdown.contains("\n");
        if (!hasMarkdown) {
            return markdown; // No formatting, return as-is
        }

        // Escape HTML to prevent injection
        String html = escapeHtml(markdown);

        // Convert Markdown to HTML
        // **bold** -> <strong>bold</strong>
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");

        // *italic* -> <em>italic</em> (but not if it's part of **bold**)
        // We need to be careful here - only match single * that are not part of **
        html = html.replaceAll("(?<!\\*)\\*([^*]+?)\\*(?!\\*)", "<em>$1</em>");

        // Convert line breaks to <br />
        html = html.replace("\n", "<br />");

        return html;
    }

    /**
     * Escape HTML special characters
     * 
     * @param text Text to escape
     * @return Escaped HTML text
     */
    public String escapeHtml(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Send a message to a room
     * Uses admin token for initialization operations, user token for regular bot
     * operations
     */
    public boolean sendMessage(String roomId, String message) {
        try {
            // Get bot user ID to check membership
            Optional<String> assistantUserIdOpt = getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                log.warn("Cannot send message: bot user ID not available");
                return false;
            }
            String assistantUserId = assistantUserIdOpt.get();

            // Use Matrix API token for sending messages
            // NOTE: OAuth2 tokens (including device code flow) do NOT have access_token_id
            // in Synapse
            // and will fail with AssertionError. We MUST use a permanent token created via
            // Synapse Admin API.
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.error("No access token available for sending message to room {}. " +
                        "A permanent token created via Synapse Admin API is required.", roomId);
                return false;
            }

            // Log which token type is being used
            if (localAssistantEnabled && localAssistantPermanentToken != null
                    && !localAssistantPermanentToken.isEmpty()) {
                log.debug("Using permanent token for sendMessage (token prefix: {})",
                        localAssistantPermanentToken.substring(0, Math.min(10, localAssistantPermanentToken.length())));
            } else {
                log.error("⚠️ WARNING: Using OAuth2 token for sendMessage. " +
                        "OAuth2 tokens (including device code flow) do NOT have access_token_id in Synapse " +
                        "and will fail with AssertionError. " +
                        "You MUST configure MATRIX_LOCAL_BOT_PERMANENT_TOKEN " +
                        "with a token created via Synapse Admin API (OAuth2 token service is used as fallback).");
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

            // Decode room ID if needed (similar to inviteUserToRoomWithNotifications)
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
                    if (!decodedRoomId.equals(roomId)) {
                        log.debug("Decoded room ID from {} to {}", roomId, decodedRoomId);
                    }
                }
            } catch (Exception e) {
                log.debug("Room ID {} decoding failed, using as-is: {}", roomId, e.getMessage());
            }

            // Check if bot is a member of the room before sending
            Optional<String> membership = membershipService.getUserRoomMembership(assistantUserId, decodedRoomId);
            if (!membership.isPresent() || !"join".equals(membership.get())) {
                log.warn("Bot {} is not a member of room {} (membership: {}). Attempting to join...", assistantUserId,
                        decodedRoomId, membership.orElse("none"));
                // Try to join the room
                boolean joined = membershipService.joinRoomAsBot(decodedRoomId);
                if (!joined) {
                    log.error("Cannot send message: bot {} failed to join room {}", assistantUserId, decodedRoomId);
                    return false;
                }
                // Re-check membership after joining
                membership = membershipService.getUserRoomMembership(assistantUserId, decodedRoomId);
                if (!membership.isPresent() || !"join".equals(membership.get())) {
                    log.error("Bot {} still not a member of room {} after join attempt (membership: {})",
                            assistantUserId,
                            decodedRoomId, membership.orElse("none"));
                    return false;
                }
                log.info("Bot {} successfully joined room {} before sending message", assistantUserId, decodedRoomId);
            }

            // Build message body - convert Markdown to HTML for Matrix
            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("msgtype", "m.text");
            messageBody.put("body", message);

            // Check if message already contains HTML tags (don't convert Markdown if HTML
            // is present)
            boolean hasHtmlTags = message != null && message.contains("<") && message.contains(">");

            if (hasHtmlTags) {
                // Message already contains HTML tags, use as-is
                messageBody.put("format", "org.matrix.custom.html");
                messageBody.put("formatted_body", message);
            } else {
                // Convert Markdown to HTML for Matrix formatted messages
                String htmlBody = convertMarkdownToMatrixHtml(message);
                if (htmlBody != null && !htmlBody.equals(message)) {
                    // Message contains formatting, add HTML format
                    messageBody.put("format", "org.matrix.custom.html");
                    messageBody.put("formatted_body", htmlBody);
                }
            }

            // Generate transaction ID (must be unique per room)
            String txnId = UUID.randomUUID().toString();

            log.info("Sending message to room {} (decoded: {}) with txnId {} as bot {}", roomId, decodedRoomId, txnId,
                    assistantUserId);
            log.debug("Message body: {}", messageBody);

            try {
                participationApi.sendMessage(decodedRoomId, "m.room.message", txnId, messageBody);
            } catch (ApiException e) {
                // If we get a 500 error with AssertionError about access_token_id, it means
                // we're using an OAuth2 token
                if (e.getCode() == 500 && e.getMessage() != null && e.getMessage().contains("Internal server error")) {
                    log.error(
                            "❌ Failed to send message: HTTP 500 - This usually means the token doesn't have access_token_id in Synapse. "
                                    +
                                    "The token being used is: {} (local bot enabled: {}, permanent token configured: {})",
                            localAssistantEnabled && localAssistantPermanentToken != null
                                    && !localAssistantPermanentToken.isEmpty()
                                            ? "permanent token"
                                            : "OAuth2 token",
                            localAssistantEnabled,
                            localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty());
                    log.error(
                            "💡 SOLUTION: Create a permanent token via Synapse Admin API and configure it in MATRIX_LOCAL_BOT_PERMANENT_TOKEN");
                    log.error(
                            "   Example: Use setup-bot-permanent-token.sh or create token via: POST /_synapse/admin/v1/users/@alfred-local:chat.neohoods.com/login");
                }
                throw e;
            }
            log.info("Successfully sent message to room {}", decodedRoomId);
            return true;
        } catch (ApiException e) {
            log.error("Failed to send message to room {}: HTTP {} - {}", roomId, e.getCode(), e.getMessage());
            if (e.getResponseBody() != null) {
                log.error("Response body: {}", e.getResponseBody());
            }
            // Log more details for 500 errors
            if (e.getCode() == 500) {
                Optional<String> assistantUserIdOpt = getAssistantUserId();
                if (assistantUserIdOpt.isPresent()) {
                    Optional<String> membership = membershipService.getUserRoomMembership(assistantUserIdOpt.get(), roomId);
                    log.error("Bot {} membership status in room {}: {}", assistantUserIdOpt.get(), roomId,
                            membership.orElse("none"));
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to send message to room {}: {}", roomId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a typing indicator in a Matrix room
     * 
     * @param roomId    Matrix room ID
     * @param typing    true to indicate that assistant Alfred is typing, false to
     *                  stop
     * @param timeoutMs Duration in milliseconds during which the indicator remains
     *                  active (default: 30000ms)
     * @return true if the indicator was sent successfully
     */
    public boolean sendTypingIndicator(String roomId, boolean typing, int timeoutMs) {
        try {
            // Use getMatrixAccessToken() which uses the assistant's permanent token
            // instead of getMatrixAccessTokenWithUserFlow() which requires OAuth2
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("Cannot send typing indicator: no access token available");
                return false;
            }

            Optional<String> assistantUserIdOpt = getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                log.warn("Cannot send typing indicator: bot user ID not available");
                return false;
            }

            String assistantUserId = assistantUserIdOpt.get();
            ApiClient apiClient = apiClientOpt.get();

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
                log.debug("Room ID {} decoding failed, using as-is: {}", roomId, e.getMessage());
            }

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("typing", typing);
            if (typing) {
                requestBody.put("timeout", timeoutMs);
            }

            // Get homeserver URL
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String typingUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .path("/_matrix/client/v3/rooms/{roomId}/typing/{userId}")
                    .buildAndExpand(decodedRoomId, assistantUserId)
                    .toUriString();

            // Get access token
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info("Sending typing indicator to room {} (typing: {}, timeout: {}ms) - URL: {}", decodedRoomId, typing,
                    timeoutMs, typingUrl);

            ResponseEntity<Void> response = restTemplate.exchange(
                    typingUrl,
                    HttpMethod.PUT,
                    request,
                    Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Typing indicator sent successfully to room {} (HTTP {})", decodedRoomId,
                        response.getStatusCode());
                return true;
            } else {
                log.warn("Failed to send typing indicator: HTTP {} - URL: {}", response.getStatusCode(), typingUrl);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending typing indicator to room {}: {}", roomId, e.getMessage(), e);
            return false;
        }
    }
}

