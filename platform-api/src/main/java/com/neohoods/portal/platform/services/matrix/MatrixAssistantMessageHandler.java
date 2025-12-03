package com.neohoods.portal.platform.services.matrix;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Gestionnaire de messages IA pour l'assistant Alfred Matrix.
 * Orchestre RAG, MCP et LLM pour générer des réponses contextuelles.
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

    @Value("${neohoods.portal.matrix.assistant.ai.enabled:false}")
    private boolean aiEnabled;

    /**
     * Traite un message Matrix et génère une réponse IA
     * 
     * @param roomId ID de la room Matrix
     * @param sender Matrix user ID du sender
     * @param messageBody Contenu du message
     * @param isDirectMessage true si c'est un DM
     * @return Réponse à envoyer (ou null si pas de réponse)
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
            // Le typing indicator est déjà envoyé dans MatrixSyncService avant l'appel à handleMessage
            // On peut le renouveler ici si le traitement prend du temps (optionnel)
            
            // Créer le contexte d'autorisation
            MatrixAssistantAuthContext authContext = authContextService.createAuthContext(
                    sender, roomId, isDirectMessage);

            // Ajouter le message utilisateur à l'historique de conversation
            conversationContextService.addUserMessage(roomId, messageBody);

            // Récupérer l'historique de conversation pour cette room
            List<Map<String, Object>> conversationHistory = conversationContextService.getConversationHistory(roomId);

            // Générer la réponse via l'IA avec l'historique de conversation
            // Le typing indicator est géré dans MatrixSyncService
            return aiService.generateResponse(messageBody, null, conversationHistory, authContext)
                    .map(response -> {
                        if (response == null || response.isEmpty()) {
                            return null;
                        }
                        // Ajouter la réponse de l'assistant à l'historique
                        conversationContextService.addAssistantResponse(roomId, response);
                        return response;
                    })
                    .onErrorResume(e -> {
                        log.error("Error generating AI response: {}", e.getMessage(), e);
                        return Mono.just("Désolé, une erreur s'est produite. Veuillez réessayer.");
                    });
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            return Mono.just("Désolé, une erreur s'est produite lors du traitement de votre message.");
        }
    }
}





