package com.neohoods.portal.platform.services.matrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service de gestion du contexte de conversation par room.
 * Stocke l'historique des messages pour chaque room afin de maintenir le contexte
 * de conversation entre les messages.
 */
@Service
@Slf4j
public class MatrixConversationContextService {

    @Value("${neohoods.portal.matrix.assistant.conversation.max-history:20}")
    private int maxHistoryPerRoom;

    @Value("${neohoods.portal.matrix.assistant.conversation.enabled:true}")
    private boolean conversationContextEnabled;

    /**
     * Stockage en mémoire de l'historique par room
     * Key: roomId, Value: liste des messages (user/assistant)
     */
    private final Map<String, List<ConversationMessage>> roomHistory = new ConcurrentHashMap<>();

    /**
     * Représente un message dans la conversation
     */
    public static class ConversationMessage {
        private final String role; // "user" ou "assistant"
        private final String content;

        public ConversationMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * Récupère l'historique de conversation pour une room
     * 
     * @param roomId ID de la room Matrix
     * @return Liste des messages de conversation (format pour Mistral API)
     */
    public List<Map<String, Object>> getConversationHistory(String roomId) {
        if (!conversationContextEnabled) {
            return Collections.emptyList();
        }

        List<ConversationMessage> history = roomHistory.get(roomId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        // Convertir en format Mistral API
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ConversationMessage msg : history) {
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("role", msg.getRole());
            message.put("content", msg.getContent());
            messages.add(message);
        }

        log.debug("Retrieved {} messages from conversation history for room {}", messages.size(), roomId);
        return messages;
    }

    /**
     * Ajoute un message utilisateur à l'historique de la room
     * 
     * @param roomId ID de la room Matrix
     * @param message Contenu du message utilisateur
     */
    public void addUserMessage(String roomId, String message) {
        addUserMessage(roomId, message, null);
    }

    /**
     * Ajoute un message utilisateur à l'historique de la room avec le sender
     * 
     * @param roomId ID de la room Matrix
     * @param message Contenu du message utilisateur
     * @param sender Matrix user ID du sender (optionnel, pour le contexte)
     */
    public void addUserMessage(String roomId, String message, String sender) {
        if (!conversationContextEnabled) {
            return;
        }

        // Formater le message avec le sender si disponible
        String formattedMessage = sender != null && !sender.isEmpty()
                ? String.format("[%s]: %s", sender, message)
                : message;

        roomHistory.computeIfAbsent(roomId, k -> new ArrayList<>())
                .add(new ConversationMessage("user", formattedMessage));

        // Limiter la taille de l'historique
        trimHistory(roomId);

        log.debug("Added user message to conversation history for room {} (sender: {}, total: {})", roomId, sender,
                roomHistory.get(roomId).size());
    }

    /**
     * Ajoute une réponse de l'assistant à l'historique de la room
     * 
     * @param roomId ID de la room Matrix
     * @param response Réponse de l'assistant
     */
    public void addAssistantResponse(String roomId, String response) {
        if (!conversationContextEnabled) {
            return;
        }

        roomHistory.computeIfAbsent(roomId, k -> new ArrayList<>())
                .add(new ConversationMessage("assistant", response));

        // Limiter la taille de l'historique
        trimHistory(roomId);

        log.debug("Added assistant response to conversation history for room {} (total: {})", roomId,
                roomHistory.get(roomId).size());
    }

    /**
     * Limite la taille de l'historique en gardant les N derniers messages
     * 
     * @param roomId ID de la room
     */
    private void trimHistory(String roomId) {
        List<ConversationMessage> history = roomHistory.get(roomId);
        if (history == null) {
            return;
        }

        if (history.size() > maxHistoryPerRoom) {
            // Garder les N derniers messages
            int toRemove = history.size() - maxHistoryPerRoom;
            history.subList(0, toRemove).clear();
            log.debug("Trimmed conversation history for room {} (removed {} messages)", roomId, toRemove);
        }
    }

    /**
     * Efface l'historique d'une room (utile pour les tests ou reset)
     * 
     * @param roomId ID de la room
     */
    public void clearHistory(String roomId) {
        roomHistory.remove(roomId);
        log.info("Cleared conversation history for room {}", roomId);
    }

    /**
     * Retourne le nombre de messages dans l'historique d'une room
     * 
     * @param roomId ID de la room
     * @return Nombre de messages
     */
    public int getHistorySize(String roomId) {
        List<ConversationMessage> history = roomHistory.get(roomId);
        return history != null ? history.size() : 0;
    }

    /**
     * Vérifie si le contexte de conversation est activé
     * 
     * @return true si activé
     */
    public boolean isEnabled() {
        return conversationContextEnabled;
    }
}



