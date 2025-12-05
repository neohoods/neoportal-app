package com.neohoods.portal.platform.services.matrix.assistant;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.services.matrix.space.MatrixConversationContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * AI message handler for Alfred Matrix assistant.
 * Orchestrates RAG, MCP and LLM to generate contextual responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantMessageHandler {

    private final MatrixAssistantAIService aiService;
    private final MatrixAssistantAuthContextService authContextService;
    private final MatrixAssistantService matrixAssistantService;
    private final MatrixConversationContextService conversationContextService;
    private final MessageSource messageSource;

    @Value("${neohoods.portal.matrix.assistant.ai.enabled}")
    private boolean aiEnabled;

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

            // Generate response via AI with conversation history
            // Typing indicator is managed in MatrixSyncService
            // Note: Response will be stored in context by sendMessage() in
            // MatrixSyncService
            return aiService.generateResponse(messageBody, null, conversationHistory, authContext)
                    .filter(response -> response != null && !response.isEmpty())
                    .onErrorResume(e -> {
                        log.error("Error generating AI response: {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(sender);
                        return Mono.just(messageSource.getMessage("matrix.error.generic", null, locale));
                    });
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            Locale locale = getLocaleFromAuthContext(sender);
            return Mono.just(messageSource.getMessage("matrix.error.processing", null, locale));
        }
    }

    /**
     * Gets locale from auth context for a Matrix user
     */
    private Locale getLocaleFromAuthContext(String matrixUserId) {
        try {
            MatrixAssistantAuthContext authContext = authContextService.createAuthContext(
                    matrixUserId, null, false);
            if (authContext.getUserEntity().isPresent()) {
                return authContext.getUserEntity().get().getLocale();
            }
        } catch (Exception e) {
            log.debug("Could not get locale for user {}, using default", matrixUserId);
        }
        return Locale.ENGLISH;
    }
}
