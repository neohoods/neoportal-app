package com.neohoods.portal.platform.services.matrix.space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing conversation context per room.
 * Stores message history for each room to maintain conversation context between
 * messages.
 */
@Service
@Slf4j
public class MatrixConversationContextService {

    @Value("${neohoods.portal.matrix.assistant.conversation.max-history}")
    private int maxHistoryPerRoom;

    @Value("${neohoods.portal.matrix.assistant.conversation.enabled}")
    private boolean conversationContextEnabled;

    /**
     * In-memory storage of history per room
     * Key: roomId, Value: list of messages (user/assistant)
     */
    private final Map<String, List<ConversationMessage>> roomHistory = new ConcurrentHashMap<>();

    /**
     * In-memory storage of conversation trace IDs per room
     * Key: roomId, Value: conversation_trace_id (UUID)
     * The conversation_trace_id persists for the entire conversation in a room
     */
    private final Map<String, String> conversationTraceIds = new ConcurrentHashMap<>();

    /**
     * Represents a message in the conversation
     */
    public static class ConversationMessage {
        private final String role; // "user" or "assistant"
        private final String content;
        private final long timestamp; // Timestamp in milliseconds (epoch)

        public ConversationMessage(String role, String content) {
            this(role, content, System.currentTimeMillis());
        }

        public ConversationMessage(String role, String content, long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Gets conversation history for a room
     * 
     * @param roomId Matrix room ID
     * @return List of conversation messages (format for Mistral API)
     */
    public List<Map<String, Object>> getConversationHistory(String roomId) {
        if (!conversationContextEnabled) {
            return Collections.emptyList();
        }

        List<ConversationMessage> history = roomHistory.get(roomId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        // Convert to Mistral API format (include timestamp for filtering)
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ConversationMessage msg : history) {
            Map<String, Object> message = new HashMap<>();
            message.put("role", msg.getRole());
            message.put("content", msg.getContent());
            message.put("timestamp", msg.getTimestamp()); // Include timestamp for filtering
            messages.add(message);
        }

        log.debug("Retrieved {} messages from conversation history for room {}", messages.size(), roomId);
        return messages;
    }

    /**
     * Gets or creates a conversation trace ID for a room
     * The trace ID persists for the entire conversation
     * 
     * @param roomId Matrix room ID
     * @return conversation_trace_id (UUID)
     */
    public String getOrCreateConversationTraceId(String roomId) {
        return conversationTraceIds.computeIfAbsent(roomId, k -> java.util.UUID.randomUUID().toString());
    }

    /**
     * Gets the conversation trace ID for a room (if exists)
     * 
     * @param roomId Matrix room ID
     * @return conversation_trace_id or null if not exists
     */
    public String getConversationTraceId(String roomId) {
        return conversationTraceIds.get(roomId);
    }

    /**
     * Adds a user message to the room history
     * 
     * @param roomId  Matrix room ID
     * @param message User message content
     */
    public void addUserMessage(String roomId, String message) {
        addUserMessage(roomId, message, null);
    }

    /**
     * Adds a user message to the room history with sender
     * 
     * @param roomId  Matrix room ID
     * @param message User message content
     * @param sender  Matrix user ID of sender (optional, for context - not included
     *                in message to avoid LLM reproducing the format)
     */
    public void addUserMessage(String roomId, String message, String sender) {
        if (!conversationContextEnabled) {
            return;
        }

        // Don't format with sender to avoid LLM reproducing Matrix user ID format
        // Sender is kept only for logs
        roomHistory.computeIfAbsent(roomId, k -> new ArrayList<>())
                .add(new ConversationMessage("user", message));

        // Limit history size
        trimHistory(roomId);

        log.debug("Added user message to conversation history for room {} (sender: {}, total: {})", roomId, sender,
                roomHistory.get(roomId).size());
    }

    /**
     * Adds an assistant response to the room history
     * 
     * @param roomId   Matrix room ID
     * @param response Assistant response
     */
    public void addAssistantResponse(String roomId, String response) {
        if (!conversationContextEnabled) {
            return;
        }

        roomHistory.computeIfAbsent(roomId, k -> new ArrayList<>())
                .add(new ConversationMessage("assistant", response));

        // Limit history size
        trimHistory(roomId);

        log.debug("Added assistant response to conversation history for room {} (total: {})", roomId,
                roomHistory.get(roomId).size());
    }

    /**
     * Limits history size by keeping the last N messages
     * 
     * @param roomId Room ID
     */
    private void trimHistory(String roomId) {
        List<ConversationMessage> history = roomHistory.get(roomId);
        if (history == null) {
            return;
        }

        if (history.size() > maxHistoryPerRoom) {
            // Keep the last N messages
            int toRemove = history.size() - maxHistoryPerRoom;
            history.subList(0, toRemove).clear();
            log.debug("Trimmed conversation history for room {} (removed {} messages)", roomId, toRemove);
        }
    }

    /**
     * Clears history for a room (useful for tests or reset)
     * Also clears the conversation trace ID
     * 
     * @param roomId Room ID
     */
    public void clearHistory(String roomId) {
        roomHistory.remove(roomId);
        conversationTraceIds.remove(roomId);
        log.info("Cleared conversation history and trace ID for room {}", roomId);
    }

    /**
     * Returns the number of messages in a room's history
     * 
     * @param roomId Room ID
     * @return Number of messages
     */
    public int getHistorySize(String roomId) {
        List<ConversationMessage> history = roomHistory.get(roomId);
        return history != null ? history.size() : 0;
    }

    /**
     * Checks if conversation context is enabled
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return conversationContextEnabled;
    }
}
