package com.neohoods.portal.platform.assistant;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAuthContextService;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantService;
import com.neohoods.portal.platform.assistant.workflows.MatrixAssistantRouter;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.services.matrix.space.MatrixConversationContextService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMessageService;
import com.neohoods.portal.platform.services.matrix.space.MatrixRoomService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * AI message handler for Alfred Matrix assistant.
 * Routes messages to appropriate agents using RAG, MCP and LLM to generate
 * contextual responses.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantMessageHandler {

    private final MatrixAssistantRouter router;
    private final MatrixAssistantAuthContextService authContextService;
    private final MatrixAssistantService matrixAssistantService;
    private final MatrixConversationContextService conversationContextService;
    private final MessageSource messageSource;
    private final MatrixMessageService matrixMessageService;
    private final MatrixRoomService matrixRoomService;

    @Autowired
    public MatrixAssistantMessageHandler(
            MatrixAssistantRouter router,
            MatrixAssistantAuthContextService authContextService,
            MatrixAssistantService matrixAssistantService,
            MatrixConversationContextService conversationContextService,
            MessageSource messageSource,
            @Autowired(required = false) MatrixMessageService matrixMessageService,
            @Autowired(required = false) MatrixRoomService matrixRoomService) {
        this.router = router;
        this.authContextService = authContextService;
        this.matrixAssistantService = matrixAssistantService;
        this.conversationContextService = conversationContextService;
        this.messageSource = messageSource;
        this.matrixMessageService = matrixMessageService;
        this.matrixRoomService = matrixRoomService;
    }

    @Value("${neohoods.portal.matrix.assistant.ai.enabled}")
    private boolean aiEnabled;

    @Value("${neohoods.portal.matrix.space-id}")
    private String spaceId;

    /**
     * Processes a Matrix message and generates an AI response
     * 
     * @param roomId          Matrix room ID
     * @param sender          Matrix user ID of the sender
     * @param messageBody     Message content
     * @param isDirectMessage true if it's a DM
     * @return Response to send (or null if no response)
     */
    public Mono<String> handleMessage(
            String roomId,
            String sender,
            String messageBody,
            boolean isDirectMessage) {

        if (!aiEnabled) {
            log.debug("AI bot is disabled, skipping message handling");
            return Mono.empty();
        }

        log.info("Handling message from {} in room {} (DM: {}): {}",
                sender, roomId, isDirectMessage, messageBody.substring(0, Math.min(100, messageBody.length())));

        // Get or create conversation trace ID and set in MDC
        String conversationTraceId = conversationContextService.getOrCreateConversationTraceId(roomId);
        MDC.put("conversationTraceId", conversationTraceId);
        MDC.put("roomId", roomId != null ? roomId : "unknown");

        try {
            // Typing indicator is already sent in MatrixSyncService before calling
            // handleMessage
            // Can be renewed here if processing takes time (optional)

            // Create authorization context
            MatrixAssistantAuthContext authContext = authContextService.createAuthContext(
                    sender, roomId, isDirectMessage);

            // Note: User message has already been added to context in
            // processTimelineEvents
            // Don't add it again here to avoid duplicates

            // Get conversation history for this room
            List<Map<String, Object>> conversationHistory = conversationContextService.getConversationHistory(roomId);

            // Generate response via router with conversation history
            // Typing indicator is managed in MatrixSyncService
            // Note: Response will be stored in context by sendMessage() in
            // MatrixSyncService
            return router.handleMessage(messageBody, conversationHistory, authContext)
                    .filter(response -> response != null && !response.isEmpty())
                    .onErrorResume(e -> {
                        return handleError(e, sender, roomId, messageBody, conversationHistory, authContext);
                    });
        } catch (Exception e) {
            return handleError(e, sender, roomId, messageBody, null, null);
        } finally {
            // Clean up MDC
            MDC.remove("conversationTraceId");
            MDC.remove("roomId");
        }
    }

    /**
     * Handles errors by translating CodedException and sending 5xx errors to IT
     * room
     */
    private Mono<String> handleError(Throwable e, String sender, String roomId, String messageBody,
            List<Map<String, Object>> conversationHistory, MatrixAssistantAuthContext authContext) {
        log.error("Error generating AI response: {}", e.getMessage(), e);

        Locale locale = getLocaleFromAuthContext(sender);
        String errorMessage;

        // Check if it's an UnauthorizedException (user sync issue)
        if (e instanceof MatrixAssistantAuthContext.UnauthorizedException) {
            MatrixAssistantAuthContext.UnauthorizedException unauthorizedEx = 
                    (MatrixAssistantAuthContext.UnauthorizedException) e;
            String errorMsg = unauthorizedEx.getMessage();
            
            // If it's a "User not found" error, it's likely a sync issue - notify IT
            if (errorMsg != null && errorMsg.contains("User not found")) {
                log.error("User synchronization issue detected for Matrix user: {} in room: {}. " +
                        "The user exists in Matrix but not in the local database. " +
                        "This indicates the Matrix user sync bot may not have run successfully.",
                        sender, roomId);
                // Send to IT room as this is a configuration/sync issue
                sendErrorToITRoom(unauthorizedEx, sender, roomId, messageBody, conversationHistory);
                // Return user-friendly message
                errorMessage = messageSource.getMessage("matrix.error.user_not_synced", 
                        new Object[]{sender}, locale);
            } else {
                // Other unauthorized errors
                errorMessage = messageSource.getMessage("matrix.error.unauthorized", null, locale);
            }
        } else if (e instanceof CodedException) {
            CodedException codedEx = (CodedException) e;
            String errorCode = codedEx.getCode();

            // Try to translate the error code
            String translationKey = "matrix.error." + errorCode.toLowerCase();
            try {
                errorMessage = messageSource.getMessage(translationKey, null, locale);
            } catch (Exception ex) {
                // Fallback to generic error message
                errorMessage = messageSource.getMessage("matrix.error.generic", null, locale);
            }

            // Check if it's a server error (5xx) - error codes starting with SERVER_ERROR
            boolean isServerError = errorCode.contains("SERVER_ERROR");
            if (isServerError) {
                sendErrorToITRoom(codedEx, sender, roomId, messageBody, conversationHistory);
            }
        } else {
            // Generic error
            errorMessage = messageSource.getMessage("matrix.error.generic", null, locale);

            // Check if it's a 5xx error
            if (isServerError(e)) {
                sendErrorToITRoom(e, sender, roomId, messageBody, conversationHistory);
            }
        }

        return Mono.just(errorMessage);
    }

    /**
     * Checks if an exception represents a server error (5xx)
     */
    private boolean isServerError(Throwable e) {
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            org.springframework.web.reactive.function.client.WebClientResponseException webEx = (org.springframework.web.reactive.function.client.WebClientResponseException) e;
            int statusCode = webEx.getStatusCode().value();
            return statusCode >= 500 && statusCode < 600;
        }
        // For other exceptions, consider them as server errors if they're not
        // CodedException
        return !(e instanceof CodedException);
    }

    /**
     * Sends error summary to IT room for 5xx errors
     */
    private void sendErrorToITRoom(Throwable e, String sender, String roomId, String messageBody,
            List<Map<String, Object>> conversationHistory) {
        try {
            if (matrixRoomService == null) {
                log.warn("MatrixRoomService not available, cannot send error to IT room");
                return;
            }

            // Get IT room ID
            Optional<String> itRoomIdOpt = matrixRoomService.getRoomIdByName("IT", spaceId);
            if (itRoomIdOpt.isEmpty()) {
                log.warn("Cannot send error to IT room: IT room not found in space {}", spaceId);
                return;
            }

            if (matrixMessageService == null) {
                log.warn("MatrixMessageService not available, cannot send error to IT room");
                return;
            }

            String conversationTraceId = conversationContextService.getConversationTraceId(roomId);
            String traceId = MDC.get("traceId");

            // Build error summary message
            StringBuilder errorSummary = new StringBuilder();
            errorSummary.append("🚨 **Erreur serveur dans l'assistant Matrix**\n\n");
            errorSummary.append("**Utilisateur:** ").append(sender).append("\n");
            errorSummary.append("**Room ID:** ").append(roomId != null ? roomId : "unknown").append("\n");
            if (conversationTraceId != null) {
                errorSummary.append("**Conversation Trace ID:** ").append(conversationTraceId).append("\n");
            }
            if (traceId != null) {
                errorSummary.append("**Trace ID:** ").append(traceId).append("\n");
            }
            errorSummary.append("**Erreur:** ").append(e.getClass().getSimpleName()).append("\n");
            errorSummary.append("**Message:** ").append(e.getMessage()).append("\n\n");

            if (e instanceof CodedException) {
                CodedException codedEx = (CodedException) e;
                errorSummary.append("**Code d'erreur:** ").append(codedEx.getCode()).append("\n");
                if (codedEx.getVariables() != null && !codedEx.getVariables().isEmpty()) {
                    errorSummary.append("**Variables:** ").append(codedEx.getVariables()).append("\n");
                }
            }

            errorSummary.append("\n**Message utilisateur:**\n");
            errorSummary.append("```\n").append(messageBody).append("\n```\n\n");

            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                errorSummary.append("**Historique de conversation (derniers messages):**\n");
                int historySize = Math.min(conversationHistory.size(), 5);
                for (int i = Math.max(0, conversationHistory.size() - historySize); i < conversationHistory
                        .size(); i++) {
                    Map<String, Object> msg = conversationHistory.get(i);
                    String role = (String) msg.get("role");
                    String content = (String) msg.get("content");
                    errorSummary.append("- **").append(role).append(":** ")
                            .append(content.substring(0, Math.min(200, content.length()))).append("\n");
                }
            }

            // Send to IT room
            boolean sent = matrixMessageService.sendMessage(itRoomIdOpt.get(), errorSummary.toString());
            if (sent) {
                log.info("Sent error summary to IT room for server error");
            } else {
                log.warn("Failed to send error summary to IT room");
            }
        } catch (Exception ex) {
            log.error("Error sending error summary to IT room: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Gets locale from auth context for a Matrix user
     */
    private Locale getLocaleFromAuthContext(String matrixUserId) {
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
