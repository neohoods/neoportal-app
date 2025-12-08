package com.neohoods.portal.platform.assistant.workflows;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Agent specialized in general questions about the copropriété (copro).
 * Uses RAG + info tools (get_infos, get_emergency_numbers, get_announcements,
 * etc.)
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantGeneralAgent extends BaseMatrixAssistantAgent {

    @Value("${neohoods.portal.matrix.assistant.ai.general-agent-prompt-file:classpath:matrix-assistant/prompts/general/matrix-assistant-general-agent-prompt.txt}")
    private String generalAgentPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.context-public-file:classpath:matrix-assistant/prompts/context/matrix-assistant-context-public.txt}")
    private String contextPublicFile;

    private String baseSystemPrompt;
    private String contextPublic;

    public MatrixAssistantGeneralAgent(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            MatrixAssistantMCPAdapter mcpAdapter,
            ResourceLoader resourceLoader,
            MatrixAssistantAgentContextService agentContextService) {
        super(webClientBuilder, objectMapper, mcpAdapter, resourceLoader, agentContextService);
        loadSystemPrompt();
    }

    private void loadSystemPrompt() {
        baseSystemPrompt = loadPromptFile(generalAgentPromptFile,
                "You are Alfred, the AI assistant for NeoHoods. You answer general questions about the copropriété.");
        contextPublic = loadPromptFile(contextPublicFile, "WARNING: This conversation is in a public room.");
    }

    @Override
    public Mono<String> handleMessage(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {

        log.info("General agent handling message: {} (user: {})",
                userMessage.substring(0, Math.min(50, userMessage.length())),
                authContext.getMatrixUserId());

        // Get filtered tools for this agent
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterTools(allTools);

        // Build system prompt
        String systemPrompt = getSystemPrompt(authContext);

        // Call Mistral API
        return callMistralAPI(userMessage, conversationHistory, systemPrompt, tools, authContext);
    }

    @Override
    protected String getSystemPrompt(MatrixAssistantAuthContext authContext) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(addDateInformation(baseSystemPrompt));

        // Add public room context only if conversation is public (GENERAL workflow can
        // be public or DM)
        if (!authContext.isDirectMessage() && contextPublic != null && !contextPublic.isEmpty()) {
            promptBuilder.append("\n\n").append(contextPublic);
        }

        return promptBuilder.toString();
    }

    @Override
    protected Set<String> getAvailableToolNames() {
        Set<String> tools = new HashSet<>();
        tools.add("get_emergency_numbers");
        tools.add("get_infos");
        tools.add("get_announcements");
        tools.add("get_applications");
        tools.add("get_notifications");
        tools.add("get_unread_notifications_count");
        return tools;
    }

    @Override
    protected boolean shouldUseRAG() {
        return true; // General agent uses RAG
    }
}
