package com.neohoods.portal.platform.assistant.workflows.space.steps;

import java.util.List;
import java.util.Map;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import reactor.core.publisher.Mono;

/**
 * Interface for space step handlers.
 * Each step in the space workflow is handled by a dedicated handler class.
 */
public interface SpaceStepHandler {

    /**
     * Handles a user message for this step
     * 
     * @param userMessage         User message
     * @param conversationHistory Full conversation history
     * @param context             Agent context
     * @param authContext         Auth context
     * @return Step response with status and next action
     */
    Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext);

    /**
     * Returns the step this handler manages
     */
    SpaceStep getStep();

    /**
     * Returns true if this step is handled entirely by backend (no LLM)
     */
    default boolean isBackendOnly() {
        return false;
    }
}
