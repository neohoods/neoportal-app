package com.neohoods.portal.platform.assistant.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Duration;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for managing Mistral Conversations API.
 * Handles conversation lifecycle with expiration (10 minutes of inactivity).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MistralConversationsService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.conversation.expiration-minutes:10}")
    private int expirationMinutes;

    private static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";

    /**
     * Represents a conversation mapping between room_id and conversation_id
     */
    public static class ConversationMapping {
        private String roomId;
        private String conversationId;
        private String agentId;
        private LocalDateTime lastInteractionTime;
        private boolean store;

        public ConversationMapping(String roomId, String conversationId, String agentId, boolean store) {
            this.roomId = roomId;
            this.conversationId = conversationId;
            this.agentId = agentId;
            this.store = store;
            this.lastInteractionTime = LocalDateTime.now();
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public LocalDateTime getLastInteractionTime() {
            return lastInteractionTime;
        }

        public void setLastInteractionTime(LocalDateTime lastInteractionTime) {
            this.lastInteractionTime = lastInteractionTime;
        }

        public boolean isStore() {
            return store;
        }

        public void setStore(boolean store) {
            this.store = store;
        }

        public boolean isExpired(int expirationMinutes) {
            return LocalDateTime.now().isAfter(lastInteractionTime.plusMinutes(expirationMinutes));
        }
    }

    // Map: roomId -> ConversationMapping
    private final Map<String, ConversationMapping> conversationsByRoom = new ConcurrentHashMap<>();

    /**
     * Starts a new conversation with an agent
     * 
     * @param roomId  Matrix room ID
     * @param agentId Mistral agent ID
     * @param inputs  Initial message(s) - can be String or List of messages
     * @param store   Whether to store the conversation on Mistral cloud
     * @return Conversation ID
     */
    public Mono<String> startConversation(String roomId, String agentId, Object inputs, boolean store) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("agent_id", agentId);
        requestBody.put("inputs", inputs);
        requestBody.put("store", store);

        log.debug("Starting Mistral conversation for room {} with agent {}", roomId, agentId);

        return webClient.post()
                .uri("/conversations/start")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    String conversationId = (String) response.get("id");
                    if (conversationId == null || conversationId.isEmpty()) {
                        throw new RuntimeException("Conversation start failed: no ID returned for room " + roomId);
                    }

                    // Store mapping
                    ConversationMapping mapping = new ConversationMapping(roomId, conversationId, agentId, store);
                    conversationsByRoom.put(roomId, mapping);

                    log.info("✓ Started Mistral conversation for room {}: conversationId={}, agentId={}",
                            roomId, conversationId, agentId);
                    return conversationId;
                })
                .onErrorResume(e -> {
                    log.error("Failed to start Mistral conversation for room {}: {}", roomId, e.getMessage(), e);
                    return Mono
                            .error(new RuntimeException("Failed to start Mistral conversation for room " + roomId, e));
                });
    }

    /**
     * Appends a message to an existing conversation
     * 
     * @param roomId Matrix room ID
     * @param inputs Message(s) to append - can be String or List of messages
     * @return New conversation ID (conversations.append returns a new ID)
     */
    public Mono<String> appendToConversation(String roomId, Object inputs) {
        ConversationMapping mapping = conversationsByRoom.get(roomId);
        if (mapping == null) {
            return Mono.error(new IllegalStateException("No conversation found for room " + roomId));
        }

        // Check if conversation is expired
        if (mapping.isExpired(expirationMinutes)) {
            log.info("Conversation expired for room {} (last interaction: {}), starting new conversation",
                    roomId, mapping.getLastInteractionTime());
            // Start a new conversation with the same agent
            return startConversation(roomId, mapping.getAgentId(), inputs, mapping.isStore());
        }

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("conversation_id", mapping.getConversationId());
        requestBody.put("inputs", inputs);
        requestBody.put("store", mapping.isStore());

        log.debug("Appending to Mistral conversation for room {}: conversationId={}", roomId,
                mapping.getConversationId());

        return webClient.post()
                .uri("/conversations/append")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    String newConversationId = (String) response.get("id");
                    if (newConversationId == null || newConversationId.isEmpty()) {
                        throw new RuntimeException("Conversation append failed: no ID returned for room " + roomId);
                    }

                    // Update mapping with new conversation ID and timestamp
                    mapping.setConversationId(newConversationId);
                    mapping.setLastInteractionTime(LocalDateTime.now());

                    log.debug("✓ Appended to Mistral conversation for room {}: new conversationId={}",
                            roomId, newConversationId);
                    return newConversationId;
                })
                .onErrorResume(e -> {
                    log.error("Failed to append to Mistral conversation for room {}: {}", roomId, e.getMessage(), e);
                    return Mono.error(
                            new RuntimeException("Failed to append to Mistral conversation for room " + roomId, e));
                });
    }

    /**
     * Gets the conversation ID for a room
     * 
     * @param roomId Matrix room ID
     * @return Conversation ID or null if not found
     */
    public String getConversationId(String roomId) {
        ConversationMapping mapping = conversationsByRoom.get(roomId);
        return mapping != null ? mapping.getConversationId() : null;
    }

    /**
     * Gets the agent ID for a room's conversation
     * 
     * @param roomId Matrix room ID
     * @return Agent ID or null if not found
     */
    public String getAgentId(String roomId) {
        ConversationMapping mapping = conversationsByRoom.get(roomId);
        return mapping != null ? mapping.getAgentId() : null;
    }

    /**
     * Checks if a conversation exists and is not expired
     * 
     * @param roomId Matrix room ID
     * @return true if conversation exists and is not expired
     */
    public boolean hasActiveConversation(String roomId) {
        ConversationMapping mapping = conversationsByRoom.get(roomId);
        return mapping != null && !mapping.isExpired(expirationMinutes);
    }

    /**
     * Checks if a conversation should be recreated (expired or different agent)
     * 
     * @param roomId  Matrix room ID
     * @param agentId Desired agent ID
     * @return true if conversation should be recreated
     */
    public boolean shouldCreateNewConversation(String roomId, String agentId) {
        ConversationMapping mapping = conversationsByRoom.get(roomId);
        if (mapping == null) {
            return true; // No conversation exists
        }
        if (mapping.isExpired(expirationMinutes)) {
            return true; // Conversation expired
        }
        if (!mapping.getAgentId().equals(agentId)) {
            return true; // Different agent needed
        }
        return false;
    }

    /**
     * Clears a conversation mapping (useful when workflow completes)
     * 
     * @param roomId Matrix room ID
     */
    public void clearConversation(String roomId) {
        ConversationMapping removed = conversationsByRoom.remove(roomId);
        if (removed != null) {
            log.info("Cleared Mistral conversation mapping for room {}", roomId);
        }
    }

    /**
     * Expires old conversations (called periodically)
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void expireOldConversations() {
        LocalDateTime now = LocalDateTime.now();
        List<String> expiredRooms = new ArrayList<>();

        for (Map.Entry<String, ConversationMapping> entry : conversationsByRoom.entrySet()) {
            ConversationMapping mapping = entry.getValue();
            if (mapping.isExpired(expirationMinutes)) {
                expiredRooms.add(entry.getKey());
            }
        }

        if (!expiredRooms.isEmpty()) {
            log.debug("Found {} expired conversations, clearing mappings", expiredRooms.size());
            for (String roomId : expiredRooms) {
                conversationsByRoom.remove(roomId);
            }
        }
    }

    /**
     * Updates the last interaction time for a conversation
     * 
     * @param roomId Matrix room ID
     */
    public void updateLastInteractionTime(String roomId) {
        ConversationMapping mapping = conversationsByRoom.get(roomId);
        if (mapping != null) {
            mapping.setLastInteractionTime(LocalDateTime.now());
        }
    }
}
