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
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Agent specialized in resident information questions.
 * Uses get_resident_info and get_emergency_numbers tools.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantResidentInfoAgent extends BaseMatrixAssistantAgent {

    @Value("${neohoods.portal.matrix.assistant.ai.resident-info-agent-prompt-file:classpath:matrix-assistant/prompts/resident-info/matrix-assistant-resident-info-agent-prompt.txt}")
    private String residentInfoAgentPromptFile;

    private String baseSystemPrompt;

    public MatrixAssistantResidentInfoAgent(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            MatrixAssistantMCPAdapter mcpAdapter,
            ResourceLoader resourceLoader,
            MatrixAssistantAgentContextService agentContextService) {
        super(webClientBuilder, objectMapper, mcpAdapter, resourceLoader, agentContextService);
        loadSystemPrompt();
    }

    private void loadSystemPrompt() {
        baseSystemPrompt = loadPromptFile(residentInfoAgentPromptFile,
                "You are Alfred, the AI assistant for NeoHoods. You answer questions about residents.");
    }

    @Override
    public Mono<String> handleMessage(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {

        log.info("Resident info agent handling message: {} (user: {})",
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
        String prompt = addDateInformation(baseSystemPrompt);
        return prompt;
    }

    @Override
    protected Set<String> getAvailableToolNames() {
        Set<String> tools = new HashSet<>();
        tools.add("get_resident_info");
        tools.add("get_emergency_numbers");
        return tools;
    }

    @Override
    protected boolean shouldUseRAG() {
        return false; // Resident info agent doesn't need RAG
    }
}
