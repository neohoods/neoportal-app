package com.neohoods.portal.platform.assistant.services;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.neohoods.portal.platform.assistant.model.WorkflowType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing agent context per room.
 * Stores workflow state for multi-step workflows (e.g., reservation workflow).
 * Context is stored in memory (similar to MatrixConversationContextService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatrixAssistantAgentContextService {

    private final ObjectMapper objectMapper;

    /**
     * Represents the agent context for a room
     */
    public static class AgentContext {
        private String roomId;
        private WorkflowType currentWorkflow;
        private Map<String, Object> workflowState;
        private LocalDateTime lastUpdated;
        private String mistralConversationId;
        private LocalDateTime lastInteractionTime;

        public AgentContext(String roomId) {
            this.roomId = roomId;
            this.workflowState = new HashMap<>();
            this.lastUpdated = LocalDateTime.now();
            this.lastInteractionTime = LocalDateTime.now();
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public WorkflowType getCurrentWorkflow() {
            return currentWorkflow;
        }

        public void setCurrentWorkflow(WorkflowType currentWorkflow) {
            this.currentWorkflow = currentWorkflow;
            this.lastUpdated = LocalDateTime.now();
        }

        public Map<String, Object> getWorkflowState() {
            return workflowState;
        }

        public void setWorkflowState(Map<String, Object> workflowState) {
            this.workflowState = workflowState != null ? new HashMap<>(workflowState) : new HashMap<>();
            this.lastUpdated = LocalDateTime.now();
        }

        public LocalDateTime getLastUpdated() {
            return lastUpdated;
        }

        public void setLastUpdated(LocalDateTime lastUpdated) {
            this.lastUpdated = lastUpdated;
        }

        public String getMistralConversationId() {
            return mistralConversationId;
        }

        public void setMistralConversationId(String mistralConversationId) {
            this.mistralConversationId = mistralConversationId;
            this.lastInteractionTime = LocalDateTime.now();
            this.lastUpdated = LocalDateTime.now();
        }

        public LocalDateTime getLastInteractionTime() {
            return lastInteractionTime;
        }

        public void setLastInteractionTime(LocalDateTime lastInteractionTime) {
            this.lastInteractionTime = lastInteractionTime;
            this.lastUpdated = LocalDateTime.now();
        }

        /**
         * Updates the last interaction time to now
         */
        public void updateLastInteractionTime() {
            this.lastInteractionTime = LocalDateTime.now();
            this.lastUpdated = LocalDateTime.now();
        }

        /**
         * Updates a specific key in the workflow state
         */
        public void updateWorkflowState(String key, Object value) {
            this.workflowState.put(key, value);
            this.lastUpdated = LocalDateTime.now();
        }

        /**
         * Gets a value from the workflow state
         */
        @SuppressWarnings("unchecked")
        public <T> T getWorkflowStateValue(String key, Class<T> type) {
            Object value = workflowState.get(key);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            return null;
        }
    }

    /**
     * In-memory storage of agent context per room
     * Key: roomId, Value: AgentContext
     */
    private final Map<String, AgentContext> roomContexts = new ConcurrentHashMap<>();

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /**
     * Clears all contexts on startup in dev/local environments
     * to avoid stale conversation context after backend restart
     */
    @PostConstruct
    public void clearContextsOnStartupIfDev() {
        if (activeProfiles != null && (activeProfiles.contains("local") || activeProfiles.contains("dev"))) {
            int cleared = clearAllContexts();
            if (cleared > 0) {
                log.info("Cleared {} stale agent contexts on startup (dev/local mode)", cleared);
            }
        }
    }

    /**
     * Gets the agent context for a room
     * 
     * @param roomId Matrix room ID
     * @return AgentContext for the room, or null if no context exists
     */
    public AgentContext getContext(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        return roomContexts.get(roomId);
    }

    /**
     * Gets or creates the agent context for a room
     * 
     * @param roomId Matrix room ID
     * @return AgentContext for the room (created if it doesn't exist)
     */
    public AgentContext getOrCreateContext(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        return roomContexts.computeIfAbsent(roomId, AgentContext::new);
    }

    /**
     * Updates the context for a room
     * 
     * @param roomId   Matrix room ID
     * @param workflow Workflow type
     * @param state    Workflow state (will be copied)
     */
    public void updateContext(String roomId, WorkflowType workflow, Map<String, Object> state) {
        if (roomId == null || roomId.isEmpty()) {
            log.warn("Cannot update context: roomId is null or empty");
            return;
        }

        AgentContext context = getOrCreateContext(roomId);
        context.setCurrentWorkflow(workflow);
        context.setWorkflowState(state);

        log.debug("Updated agent context for room {}: workflow={}, state keys={}",
                roomId, workflow, state != null ? state.keySet() : "null");
    }

    /**
     * Updates a specific key in the workflow state for a room
     * 
     * @param roomId Matrix room ID
     * @param key    State key
     * @param value  State value
     */
    public void updateWorkflowState(String roomId, String key, Object value) {
        if (roomId == null || roomId.isEmpty()) {
            log.warn("Cannot update workflow state: roomId is null or empty");
            return;
        }

        AgentContext context = getOrCreateContext(roomId);
        context.updateWorkflowState(key, value);

        log.debug("Updated workflow state for room {}: {}={}", roomId, key, value);
    }

    /**
     * Clears the context for a room (useful after workflow completion)
     * 
     * @param roomId Room ID
     */
    public void clearContext(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }

        AgentContext removed = roomContexts.remove(roomId);
        if (removed != null) {
            log.info("Cleared agent context for room {}", roomId);
        } else {
            log.debug("No context to clear for room {}", roomId);
        }
    }

    /**
     * Clears all contexts (useful for development/testing after backend restart)
     * 
     * @return Number of contexts cleared
     */
    public int clearAllContexts() {
        int count = roomContexts.size();
        roomContexts.clear();
        log.info("Cleared all {} agent contexts", count);
        return count;
    }

    /**
     * Dumps the context for a room in a human-readable format
     * Used for debugging purposes
     * 
     * @param roomId Room ID
     * @return Formatted string representation of the context
     */
    public String dumpContext(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return "❌ Room ID is null or empty";
        }

        AgentContext context = roomContexts.get(roomId);
        if (context == null) {
            return String.format("📋 **Contexte Agent - Room: %s**\n\nAucun contexte trouvé pour cette room.", roomId);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📋 **Contexte Agent - Room: %s**\n\n", roomId));
        sb.append(String.format("**Workflow actuel:** %s\n",
                context.getCurrentWorkflow() != null ? context.getCurrentWorkflow().name() : "Aucun"));
        sb.append(String.format("**Dernière mise à jour:** %s\n\n", context.getLastUpdated()));

        if (context.getWorkflowState() != null && !context.getWorkflowState().isEmpty()) {
            sb.append("**État du workflow:**\n");
            try {
                String jsonState = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(context.getWorkflowState());
                // Format JSON for Matrix (use code block)
                sb.append("```json\n");
                sb.append(jsonState);
                sb.append("\n```\n");
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize workflow state to JSON: {}", e.getMessage());
                // Fallback to simple key-value format
                sb.append("\n");
                for (Map.Entry<String, Object> entry : context.getWorkflowState().entrySet()) {
                    sb.append(String.format("- **%s:** %s\n", entry.getKey(), entry.getValue()));
                }
            }
        } else {
            sb.append("**État du workflow:** Vide\n");
        }

        return sb.toString();
    }

    /**
     * Checks if a context exists for a room
     * 
     * @param roomId Room ID
     * @return true if context exists
     */
    public boolean hasContext(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return false;
        }
        return roomContexts.containsKey(roomId);
    }
}
