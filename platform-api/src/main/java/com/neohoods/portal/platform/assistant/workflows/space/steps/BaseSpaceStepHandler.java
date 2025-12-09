package com.neohoods.portal.platform.assistant.workflows.space.steps;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.MatrixAssistantSpaceAgent;

import reactor.core.publisher.Mono;

/**
 * Base class for space step handlers.
 * Provides common functionality for calling Mistral API and processing
 * responses.
 */
public abstract class BaseSpaceStepHandler implements SpaceStepHandler {

    @Autowired
    protected MatrixAssistantMCPAdapter mcpAdapter;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MessageSource messageSource;

    // Note: spaceAgent is injected lazily to avoid circular dependency
    // MatrixAssistantSpaceAgent needs handlers in constructor, handlers need
    // spaceAgent
    // Using @Lazy ensures the proxy is created after MatrixAssistantSpaceAgent is
    // fully initialized
    @Autowired(required = false)
    @Lazy
    protected MatrixAssistantSpaceAgent spaceAgent;

    protected MatrixAssistantSpaceAgent getSpaceAgent() {
        return spaceAgent;
    }

    /**
     * Calls Mistral API with JSON response format
     * Delegates to MatrixAssistantSpaceAgent which extends BaseMatrixAssistantAgent
     */
    protected Mono<SpaceStepResponse> callMistralAPIWithJSONResponse(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext) {

        MatrixAssistantSpaceAgent agent = getSpaceAgent();
        if (agent == null) {
            return Mono.error(new IllegalStateException("MatrixAssistantSpaceAgent not available"));
        }

        // Use reflection to access protected method, or make it public/protected in
        // BaseMatrixAssistantAgent
        // For now, we'll create a public wrapper method in MatrixAssistantSpaceAgent
        return agent.callMistralAPIWithJSONResponseForStep(userMessage, conversationHistory, systemPrompt, tools,
                authContext);
    }

    /**
     * Builds system prompt for a step
     */
    protected String buildSystemPromptForStep(
            SpaceStep step,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {
        MatrixAssistantSpaceAgent agent = getSpaceAgent();
        if (agent == null) {
            throw new IllegalStateException("MatrixAssistantSpaceAgent not available");
        }
        return agent.buildSystemPromptForStepPublic(step, context, authContext);
    }

    /**
     * Filters tools for a step
     */
    protected java.util.List<Map<String, Object>> filterToolsForStep(
            java.util.List<MCPTool> allTools,
            SpaceStep currentStep) {
        MatrixAssistantSpaceAgent agent = getSpaceAgent();
        if (agent == null) {
            throw new IllegalStateException("MatrixAssistantSpaceAgent not available");
        }
        return agent.filterToolsForStepPublic(allTools, currentStep);
    }
}
