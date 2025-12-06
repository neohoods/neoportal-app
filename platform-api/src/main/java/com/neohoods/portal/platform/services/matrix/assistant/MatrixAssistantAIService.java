package com.neohoods.portal.platform.services.matrix.assistant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.neohoods.portal.platform.services.matrix.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.services.matrix.mcp.MatrixMCPModels;
import com.neohoods.portal.platform.services.matrix.rag.MatrixAssistantRAGService;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.services.matrix.mcp.MatrixMCPModels.MCPContent;
import com.neohoods.portal.platform.services.matrix.mcp.MatrixMCPModels.MCPTool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for integration with Mistral AI.
 * Manages LLM calls, function calls, and orchestration with RAG and MCP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantAIService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final MatrixAssistantMCPAdapter mcpAdapter;
    private final ResourceLoader resourceLoader;
    private final MessageSource messageSource;

    @Value("${neohoods.portal.matrix.assistant.ai.system-prompt-file}")
    private String systemPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.context-public-file}")
    private String contextPublicFile;

    @Value("${neohoods.portal.matrix.assistant.ai.context-private-file}")
    private String contextPrivateFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-flow-file}")
    private String reservationFlowFile;

    @Value("${neohoods.portal.matrix.assistant.ai.minimal-prompt-file}")
    private String minimalPromptFile;

    private String baseSystemPrompt;
    private String contextPublic;
    private String contextPrivate;
    private String reservationFlow;
    private String minimalPrompt;

    // Optional: RAG service (only available if RAG is enabled)
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MatrixAssistantRAGService ragService;

    // Optional: Admin command service
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MatrixAssistantAdminCommandService adminCommandService;

    @Value("${neohoods.portal.matrix.assistant.ai.provider}")
    private String provider;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.ai.model}")
    private String model;

    @Value("${neohoods.portal.matrix.assistant.ai.enabled}")
    private boolean aiEnabled;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled}")
    private boolean ragEnabled;

    @Value("${neohoods.portal.matrix.assistant.mcp.enabled}")
    private boolean mcpEnabled;

    private static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";

    /**
     * Loads prompts and contexts from resource files at startup
     */
    @PostConstruct
    public void loadSystemPrompt() {
        try {
            // Load base system prompt
            Resource resource = resourceLoader.getResource(systemPromptFile);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    baseSystemPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    log.info("System prompt loaded from: {}", systemPromptFile);
                }
            } else {
                log.warn("System prompt file not found: {}, using default", systemPromptFile);
                baseSystemPrompt = getDefaultSystemPrompt();
            }

            // Load context files
            contextPublic = loadResourceFile(contextPublicFile, "Context public");
            contextPrivate = loadResourceFile(contextPrivateFile, "Context private");
            reservationFlow = loadResourceFile(reservationFlowFile, "Reservation flow");
            minimalPrompt = loadResourceFile(minimalPromptFile, "Minimal prompt");
        } catch (Exception e) {
            log.error("Error loading system prompt from file: {}", systemPromptFile, e);
            baseSystemPrompt = getDefaultSystemPrompt();
        }
    }

    /**
     * Loads a resource file and returns its content as a string
     */
    private String loadResourceFile(String filePath, String description) {
        try {
            Resource resource = resourceLoader.getResource(filePath);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    log.info("{} loaded from: {}", description, filePath);
                    return content;
                }
            } else {
                log.warn("{} file not found: {}, using empty string", description, filePath);
                return "";
            }
        } catch (Exception e) {
            log.error("Error loading {} from file: {}", description, filePath, e);
            return "";
        }
    }

    /**
     * Default system prompt (fallback if file is not found)
     */
    private String getDefaultSystemPrompt() {
        return "You are Alfred, the AI assistant for NeoHoods, a co-ownership management platform.\n" +
                "You answer residents' questions via Matrix (Element).\n\n" +
                "⚠️ ABSOLUTE RULE - NEVER INVENT INFORMATION:\n" +
                "- If you do NOT have the information in the conversation context OR via MCP tools, " +
                "you must respond: \"I don't have this information. Let me search for it for you.\" " +
                "then use the appropriate MCP tool.\n";
    }

    /**
     * Generates a response to a user message using Mistral AI
     * with support for RAG and MCP
     */
    public Mono<String> generateResponse(
            String userMessage,
            String conversationHistory,
            MatrixAssistantAuthContext authContext) {
        return generateResponse(userMessage, conversationHistory, null, authContext);
    }

    /**
     * Generates a response to a user message using Mistral AI
     * with support for RAG and MCP
     * 
     * @param userMessage             User message
     * @param conversationHistory     Conversation history (string format, for
     *                                compatibility)
     * @param conversationHistoryList Conversation history (list format, preferred)
     * @param authContext             Authorization context
     */
    public Mono<String> generateResponse(
            String userMessage,
            String conversationHistory,
            List<Map<String, Object>> conversationHistoryList,
            MatrixAssistantAuthContext authContext) {

        if (!aiEnabled) {
            return Mono.just("AI assistant is not enabled.");
        }

        log.info("Generating AI response for message: {} (user: {})",
                userMessage.substring(0, Math.min(50, userMessage.length())),
                authContext.getMatrixUserId());

        // 1. Get RAG context if needed
        Mono<String> ragContextMono = Mono.just("");
        if (ragEnabled && ragService != null) {
            ragContextMono = ragService.searchRelevantContext(userMessage)
                    .map(context -> context.isEmpty() ? "" : "Documentation context:\n" + context + "\n\n");
        }

        // 2. Build list of available MCP tools
        List<Map<String, Object>> tools = new ArrayList<>();
        if (mcpEnabled) {
            List<MCPTool> mcpTools = mcpAdapter.listTools();
            tools = mcpTools.stream()
                    .map(this::convertMCPToolToMistralFunction)
                    .collect(Collectors.toList());
        }

        // 3. Build system prompt
        final String systemPrompt = buildSystemPrompt(authContext);
        final List<Map<String, Object>> finalTools = tools;

        // 4. Call Mistral API with function calling
        return ragContextMono.flatMap(ragContext -> {
            return callMistralAPI(userMessage, conversationHistory, conversationHistoryList, ragContext, systemPrompt,
                    finalTools, authContext);
        });
    }

    /**
     * Calls Mistral API with function calling
     */
    private Mono<String> callMistralAPI(
            String userMessage,
            String conversationHistory,
            List<Map<String, Object>> conversationHistoryList,
            String ragContext,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext) {

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // Build messages
        List<Map<String, Object>> messages = new ArrayList<>();

        // Optimization: If this is the first message in the conversation, send the
        // full system prompt
        // Otherwise, use a minimal system prompt (the model already has context in
        // the history)
        boolean isFirstMessage = (conversationHistoryList == null || conversationHistoryList.isEmpty()) &&
                (conversationHistory == null || conversationHistory.isEmpty());

        String systemPromptToUse;
        if (isFirstMessage) {
            // First message: full system prompt
            systemPromptToUse = systemPrompt + "\n\n" + ragContext;
            log.debug("Using full system prompt (first message in conversation)");
        } else {
            // Subsequent messages: minimal system prompt (only critical rules)
            systemPromptToUse = buildMinimalSystemPrompt(authContext) + "\n\n" + ragContext;
            log.debug("Using minimal system prompt (conversation in progress)");
        }

        // System message
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPromptToUse);
        messages.add(systemMsg);

        // Conversation history (if available)
        // Prefer list if available, otherwise parse the string
        if (conversationHistoryList != null && !conversationHistoryList.isEmpty()) {
            // Use list directly (preferred format)
            messages.addAll(conversationHistoryList);
            log.debug("Added {} messages from conversation history (list format)", conversationHistoryList.size());
        } else if (conversationHistory != null && !conversationHistory.isEmpty()) {
            // Parse history string (legacy format)
            // Expected format: "role: content\nrole: content\n..."
            String[] lines = conversationHistory.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                int colonIndex = line.indexOf(":");
                if (colonIndex > 0) {
                    String role = line.substring(0, colonIndex).trim();
                    String content = line.substring(colonIndex + 1).trim();
                    if (("user".equals(role) || "assistant".equals(role)) && !content.isEmpty()) {
                        Map<String, Object> histMsg = new HashMap<>();
                        histMsg.put("role", role);
                        histMsg.put("content", content);
                        messages.add(histMsg);
                    }
                }
            }
            log.debug("Added {} messages from conversation history (string format)", messages.size() - 1); // -1 for
                                                                                                           // system
        }

        // User message
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // Determine if we should force tool usage based on the question
        String toolChoice = determineToolChoice(userMessage, tools);

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        if (!tools.isEmpty()) {
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", toolChoice);
            log.debug("Using tool_choice: {} for message: {}", toolChoice,
                    userMessage.substring(0, Math.min(50, userMessage.length())));
        }
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        log.debug("Calling Mistral API with model: {}, tools: {}", model, tools.size());

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    // Process Mistral response
                    return processMistralResponse(response, authContext, messages, ragContext, tools);
                })
                .onErrorResume(e -> {
                    log.error("Error calling Mistral API: {}",
                            e instanceof Exception ? ((Exception) e).getMessage() : e.toString(), e);
                    return Mono.just("Sorry, an error occurred while generating the response.");
                });
    }

    /**
     * Processes Mistral response and handles function calls if needed
     */
    @SuppressWarnings("unchecked")
    private Mono<String> processMistralResponse(Map<String, Object> response, MatrixAssistantAuthContext authContext,
            List<Map<String, Object>> previousMessages, String ragContext, List<Map<String, Object>> tools) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return Mono.just("Aucune réponse générée.");
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        // Check if Mistral wants to call a function
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            // Mistral wants to call one or more tools
            log.info("Mistral requested {} tool call(s)", toolCalls.size());

            // Process ALL tool calls and collect all results
            List<Map<String, Object>> toolResults = new ArrayList<>();
            
            for (Map<String, Object> toolCall : toolCalls) {
                String toolCallId = (String) toolCall.get("id");
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                String functionName = (String) function.get("name");
                String functionArgumentsJson = (String) function.get("arguments");

                try {
                    Map<String, Object> functionArguments = objectMapper.readValue(
                            functionArgumentsJson,
                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                    // Call MCP tool
                    MatrixMCPModels.MCPToolResult toolResult = mcpAdapter.callMCPToolDirect(functionName,
                            functionArguments, authContext);

                    // Build a message with the tool result
                    String toolResultText = toolResult.getContent().stream()
                            .map(MCPContent::getText)
                            .filter(text -> text != null)
                            .collect(Collectors.joining("\n"));

                    // Store tool result with its ID
                    Map<String, Object> toolResultMsg = new HashMap<>();
                    toolResultMsg.put("tool_call_id", toolCallId);
                    toolResultMsg.put("function_name", functionName);
                    toolResultMsg.put("result", toolResultText);
                    toolResults.add(toolResultMsg);
                } catch (Exception e) {
                    log.error("Error calling tool {}: {}", functionName, e.getMessage(), e);
                    // Add error result for this tool call
                    Map<String, Object> toolResultMsg = new HashMap<>();
                    toolResultMsg.put("tool_call_id", toolCallId);
                    toolResultMsg.put("function_name", functionName);
                    toolResultMsg.put("result", "Error: " + e.getMessage());
                    toolResults.add(toolResultMsg);
                }
            }

            // Call Mistral again with ALL tool results
            // Pass the original assistant message with tool_calls and all tool results
            // Also pass tools so Mistral can make additional tool calls if needed (reasoning chain)
            return callMistralWithToolResults(previousMessages, message, toolResults,
                    authContext, ragContext, tools);
        }

        // No function call, return direct response
        // BUT: If the question requires data (addresses, numbers, etc.),
        // we should not have a direct response without tool call
        String content = (String) message.get("content");

        // LOG: All final bot responses for audit
        if (content != null && !content.isEmpty()) {
            log.info("🤖 BOT FINAL RESPONSE (no tool call) [user={}, room={}]: {}",
                    authContext.getMatrixUserId(),
                    authContext.getRoomId(),
                    content.length() > 500 ? content.substring(0, 500) + "..." : content);
        } else {
            log.warn("⚠️ BOT FINAL RESPONSE (no tool call) IS EMPTY OR NULL [user={}, room={}], content={}",
                    authContext.getMatrixUserId(),
                    authContext.getRoomId(),
                    content == null ? "null" : "empty string");
        }

        // CRITICAL: If bot says "Je vais chercher" or similar but didn't call a tool,
        // we MUST force a tool call
        if (content != null && !content.trim().isEmpty() && !tools.isEmpty()) {
            String lowerContent = content.toLowerCase();
            boolean saysWillSearch = lowerContent.contains("vais chercher") ||
                    lowerContent.contains("vais verifier") ||
                    lowerContent.contains("je vais chercher") ||
                    lowerContent.contains("je vais vérifier") ||
                    lowerContent.contains("i'll search") ||
                    lowerContent.contains("i will search") ||
                    lowerContent.contains("chercher les informations") ||
                    lowerContent.contains("searching for") ||
                    (lowerContent.contains("chercher") && lowerContent.contains("informations"));

            // Get the last user message to check if it requires a tool
            String lastUserMessage = "";
            for (int i = previousMessages.size() - 1; i >= 0; i--) {
                Map<String, Object> msg = previousMessages.get(i);
                if ("user".equals(msg.get("role"))) {
                    lastUserMessage = (String) msg.get("content");
                    break;
                }
            }

            if (saysWillSearch && requiresToolCall(lastUserMessage)) {
                log.warn("🚨 Bot said 'Je vais chercher' but didn't call tool! Forcing tool call for: {}",
                        lastUserMessage);
                // Force tool call by calling Mistral again with tool_choice="required"
                return forceToolCall(lastUserMessage, previousMessages, tools, authContext, ragContext);
            }
        }

        // Check if the response seems to contain invented information
        // (addresses, phone numbers, opening hours, etc. that are not in the
        // RAG context or toolResult)
        if (content != null && !content.trim().isEmpty() && containsPotentiallyInventedInfo(content)) {
            // Check if suspicious information is in RAG
            boolean infoInRag = ragContext != null && !ragContext.isEmpty() &&
                    ragContext.toLowerCase()
                            .contains(content.toLowerCase().substring(0, Math.min(100, content.length())));

            if (!infoInRag) {
                log.warn(
                        "🚨 Bot response may contain invented information (no tool call, not in RAG). Forcing tool usage. Content: {}",
                        content.substring(0, Math.min(200, content.length())));
                // Don't send this response, return a message indicating we don't have
                // the info
                return Mono.just("I don't have this information available. Let me search for it for you.");
            } else {
                log.debug("Bot response contains potentially invented info but it's in RAG context, allowing it.");
            }
        }

        // Return content if not null and not empty, otherwise return a default message
        if (content != null && !content.trim().isEmpty()) {
            return Mono.just(content);
        } else {
            log.warn("⚠️ Mistral returned empty response (no tool call), returning default message");
            return Mono.just("Je n'ai pas pu générer de réponse. Pouvez-vous reformuler votre question ?");
        }
    }

    /**
     * Maximum number of tool calls in a chain to prevent infinite loops
     */
    private static final int MAX_TOOL_CALL_CHAIN = 5;
    
    /**
     * Calls Mistral with multiple tool results (when Mistral requested multiple tools)
     * 
     * @param previousMessages Previous messages (system + user)
     * @param assistantMessage Original assistant message with tool_calls
     * @param toolResults      List of tool results, each containing tool_call_id, function_name, and result
     * @param authContext      Authorization context
     * @param ragContext       RAG context (may contain valid information)
     * @param tools            Available tools (needed for chained tool calls)
     */
    private Mono<String> callMistralWithToolResults(
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            List<Map<String, Object>> toolResults,
            MatrixAssistantAuthContext authContext,
            String ragContext,
            List<Map<String, Object>> tools) {
        
        return callMistralWithToolResultsRecursive(previousMessages, assistantMessage, toolResults,
                authContext, ragContext, tools, 0);
    }
    
    /**
     * Calls Mistral with the result of a single tool call (backward compatibility)
     * Supports chained tool calls: if Mistral wants to call another tool after receiving the result,
     * it will do so automatically (up to MAX_TOOL_CALL_CHAIN iterations)
     * 
     * @param previousMessages Previous messages (system + user)
     * @param assistantMessage Original assistant message with tool_calls
     * @param toolCallId       Tool call ID (needed to associate the result)
     * @param functionName     Name of the called function
     * @param toolResult       Tool call result
     * @param authContext      Authorization context
     * @param ragContext       RAG context (may contain valid information)
     * @param tools            Available tools (needed for chained tool calls)
     */
    private Mono<String> callMistralWithToolResult(
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            String toolCallId,
            String functionName,
            String toolResult,
            MatrixAssistantAuthContext authContext,
            String ragContext,
            List<Map<String, Object>> tools) {
        
        // Convert single tool result to list format
        List<Map<String, Object>> toolResults = new ArrayList<>();
        Map<String, Object> singleResult = new HashMap<>();
        singleResult.put("tool_call_id", toolCallId);
        singleResult.put("function_name", functionName);
        singleResult.put("result", toolResult);
        toolResults.add(singleResult);
        
        return callMistralWithToolResultsRecursive(previousMessages, assistantMessage, toolResults,
                authContext, ragContext, tools, 0);
    }
    
    /**
     * Recursive helper for chained tool calls with multiple tool results
     * Handles the case where Mistral requested multiple tools and we need to respond to all of them
     */
    private Mono<String> callMistralWithToolResultsRecursive(
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            List<Map<String, Object>> toolResults,
            MatrixAssistantAuthContext authContext,
            String ragContext,
            List<Map<String, Object>> tools,
            int iteration) {
        
        // Prevent infinite loops
        if (iteration >= MAX_TOOL_CALL_CHAIN) {
            log.warn("⚠️ Maximum tool call chain reached ({}), returning current result", MAX_TOOL_CALL_CHAIN);
            return Mono.just("J'ai effectué plusieurs vérifications mais je n'ai pas pu obtenir toutes les informations nécessaires. Pouvez-vous reformuler votre question ?");
        }
        
        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        List<Map<String, Object>> messages = new ArrayList<>();

        // Include all previous messages (system + user + previous assistant messages + tool results)
        messages.addAll(previousMessages);

        // If this is the first iteration, add the assistant message and all tool results
        // For recursive calls (iteration > 0), previousMessages already contains the assistant message
        // (it was added in the calling code before the recursive call)
        if (iteration == 0) {
            // Original assistant message with tool_calls (needed for context)
            messages.add(assistantMessage);
        }
        // For iteration > 0, assistantMessage is already in previousMessages (added in updatedMessages),
        // so we don't add it again to avoid duplication

        // Add ALL tool result messages (one for each tool call in the assistant message)
        // CRITICAL: The number of tool results MUST match the number of tool_calls in assistantMessage
        for (Map<String, Object> toolResult : toolResults) {
            Map<String, Object> toolMsg = new HashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", toolResult.get("tool_call_id"));
            toolMsg.put("content", toolResult.get("result"));
            messages.add(toolMsg);
        }
        
        // Verify that we have the correct number of tool responses
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCallsInMessage = (List<Map<String, Object>>) assistantMessage.get("tool_calls");
        int expectedToolResponses = toolCallsInMessage != null ? toolCallsInMessage.size() : 0;
        if (expectedToolResponses != toolResults.size()) {
            log.error("⚠️ MISMATCH: Assistant message has {} tool_calls but we have {} tool results! [iteration={}]", 
                    expectedToolResponses, toolResults.size(), iteration);
            log.error("  Tool calls in message: {}", toolCallsInMessage != null ? 
                    toolCallsInMessage.stream()
                            .map(tc -> {
                                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                                return func != null ? (String) func.get("name") : "unknown";
                            })
                            .collect(Collectors.joining(", ")) : "null");
            log.error("  Tool results: {}", toolResults.stream()
                    .map(tr -> (String) tr.get("function_name"))
                    .collect(Collectors.joining(", ")));
            // If mismatch, we cannot proceed - return error to prevent invalid API call
            return Mono.error(new RuntimeException(
                    String.format("Mismatch between tool_calls (%d) and tool results (%d) in iteration %d", 
                            expectedToolResponses, toolResults.size(), iteration)));
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        // Pass tools so Mistral can make additional tool calls if needed
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto"); // Allow Mistral to decide if it needs more tools
        }
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        // Debug: Log message structure to verify tool_calls and tool results match
        int toolCallsCount = expectedToolResponses;
        log.debug("Calling Mistral with tool result (iteration {}): {} messages, {} tools, {} tool_calls in assistant message, {} tool results", 
                iteration, messages.size(), tools != null ? tools.size() : 0, toolCallsCount, toolResults.size());
        
        // Log the last few messages to verify structure
        int lastMessagesToLog = Math.min(5, messages.size());
        for (int i = messages.size() - lastMessagesToLog; i < messages.size(); i++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");
            if ("assistant".equals(role)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tc = (List<Map<String, Object>>) msg.get("tool_calls");
                log.debug("  Message {}: role={}, tool_calls={}", i, role, tc != null ? tc.size() : 0);
            } else if ("tool".equals(role)) {
                log.debug("  Message {}: role={}, tool_call_id={}", i, role, msg.get("tool_call_id"));
            } else {
                log.debug("  Message {}: role={}", i, role);
            }
        }

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                        response -> {
                            log.error("Mistral API error (iteration {}): {} - Request had {} messages, {} tools", 
                                    iteration, response.statusCode(), messages.size(), tools != null ? tools.size() : 0);
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Mistral API error body: {}", body);
                                        return Mono.error(new RuntimeException("Mistral API error: " + response.statusCode() + " - " + body));
                                    });
                        })
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    // Check if Mistral wants to make another tool call (chained reasoning)
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                        
                        if (toolCalls != null && !toolCalls.isEmpty()) {
                            // Mistral wants to make another tool call - chain it!
                            log.info("🔗 Chained tool call #{}: Mistral wants to call {} tool(s)",
                                    iteration + 1, toolCalls.size());
                            
                            // Process ALL tool calls and collect all results
                            List<Map<String, Object>> newToolResults = new ArrayList<>();
                            
                            for (Map<String, Object> toolCall : toolCalls) {
                                String newToolCallId = (String) toolCall.get("id");
                                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                                String newFunctionName = (String) function.get("name");
                                String newFunctionArgumentsJson = (String) function.get("arguments");
                                
                                try {
                                    Map<String, Object> newFunctionArguments = objectMapper.readValue(
                                            newFunctionArgumentsJson,
                                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                                    
                                    // Call the chained MCP tool
                                    MatrixMCPModels.MCPToolResult newToolResult = mcpAdapter.callMCPToolDirect(newFunctionName,
                                            newFunctionArguments, authContext);
                                    
                                    String newToolResultText = newToolResult.getContent().stream()
                                            .map(MCPContent::getText)
                                            .filter(text -> text != null)
                                            .collect(Collectors.joining("\n"));
                                    
                                    // Store tool result with its ID
                                    Map<String, Object> toolResultMsg = new HashMap<>();
                                    toolResultMsg.put("tool_call_id", newToolCallId);
                                    toolResultMsg.put("function_name", newFunctionName);
                                    toolResultMsg.put("result", newToolResultText);
                                    newToolResults.add(toolResultMsg);
                                } catch (Exception e) {
                                    log.error("Error in chained tool call {}: {}", newFunctionName, e.getMessage(), e);
                                    // Add error result for this tool call
                                    Map<String, Object> toolResultMsg = new HashMap<>();
                                    toolResultMsg.put("tool_call_id", newToolCallId);
                                    toolResultMsg.put("function_name", newFunctionName);
                                    toolResultMsg.put("result", "Error: " + e.getMessage());
                                    newToolResults.add(toolResultMsg);
                                }
                            }
                            
                            // Build updated messages: previousMessages already contains all previous messages
                            // including the assistant message and tool results from the previous iteration
                            List<Map<String, Object>> updatedMessages = new ArrayList<>(previousMessages);
                            
                            // Add the new assistant message with tool_calls (this is the response from Mistral
                            // asking for another tool call)
                            updatedMessages.add(message);
                            
                            // Recursively call with ALL new tool results
                            // Note: updatedMessages now contains: system + user + previous assistant + previous tool results + new assistant message
                            // The recursive call will add the new tool results
                            return callMistralWithToolResultsRecursive(updatedMessages, message, newToolResults,
                                    authContext, ragContext, tools, iteration + 1);
                        }
                    }
                    
                    // No more tool calls, process the final response
                    choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        String content = (String) message.get("content");

                        // LOG: All final bot responses for audit
                        if (content != null && !content.isEmpty()) {
                            log.info("🤖 BOT FINAL RESPONSE [user={}, room={}]: {}",
                                    authContext.getMatrixUserId(),
                                    authContext.getRoomId(),
                                    content.length() > 500 ? content.substring(0, 500) + "..." : content);
                        } else {
                            log.warn("⚠️ BOT FINAL RESPONSE IS EMPTY OR NULL [user={}, room={}], content={}",
                                    authContext.getMatrixUserId(),
                                    authContext.getRoomId(),
                                    content == null ? "null" : "empty string");
                        }

                        // Check if the response contains invented information
                        // If toolResults did not contain the requested info (e.g., address, opening
                        // hours)
                        // but the response contains it, it's suspicious
                        // Combine all tool results into a single string for validation
                        String combinedToolResults = toolResults != null && !toolResults.isEmpty() 
                                ? toolResults.stream()
                                        .map(tr -> (String) tr.get("result"))
                                        .filter(r -> r != null)
                                        .collect(Collectors.joining("\n"))
                                : null;
                        
                        if (content != null && !content.trim().isEmpty() && combinedToolResults != null) {
                            String lowerContent = content.toLowerCase();
                            String lowerToolResult = combinedToolResults.toLowerCase();

                            // Check if the question asked for opening hours
                            // (can detect this in previous context or in the response)
                            boolean askingForHours = lowerContent.contains("horaires") ||
                                    lowerContent.contains("horaire") ||
                                    lowerContent.contains("ouverture") ||
                                    lowerContent.contains("ouvert");

                            // Check if the response contains opening hours
                            boolean responseHasHours = lowerContent
                                    .matches(".*(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*") ||
                                    lowerContent.matches(".*\\d+h\\d+.*\\d+h\\d+.*") ||
                                    lowerContent.matches(
                                            ".*(du|de)\\s+(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*");

                            // Check if toolResult contains opening hours
                            boolean toolResultHasHours = lowerToolResult.contains("horaire") ||
                                    lowerToolResult.contains("ouverture") ||
                                    lowerToolResult.matches(".*\\d+h.*\\d+h.*");

                            // If we asked for opening hours, the response contains them, but toolResult
                            // doesn't, check if it's in RAG
                            if (askingForHours && responseHasHours && !toolResultHasHours) {
                                // Check if opening hours are in RAG
                                boolean hoursInRag = ragContext != null && !ragContext.isEmpty() &&
                                        (ragContext.toLowerCase().contains("horaire") ||
                                                ragContext.toLowerCase().contains("ouverture") ||
                                                ragContext.toLowerCase().matches(".*\\d+h.*\\d+h.*"));

                                if (!hoursInRag) {
                                    log.warn("🚨 BOT INVENTED OPENING HOURS! Tool result: {}, Response: {}, RAG: {}",
                                            combinedToolResults.substring(0, Math.min(300, combinedToolResults.length())),
                                            content.substring(0, Math.min(300, content.length())),
                                            ragContext != null
                                                    ? ragContext.substring(0, Math.min(200, ragContext.length()))
                                                    : "null");
                                    return Mono.just("I don't have opening hours available in my data.");
                                } else {
                                    log.debug("Opening hours found in RAG context, allowing response.");
                                }
                            }

                            // If we were looking for an address but toolResult doesn't contain it
                            boolean askingForAddress = lowerToolResult.contains("adresse") ||
                                    lowerToolResult.contains("postal");
                            boolean responseHasAddress = lowerContent
                                    .matches(".*\\d+\\s+(rue|avenue|boulevard|place).*") ||
                                    lowerContent.matches(".*\\d{5}\\s+paris.*");

                            if (askingForAddress && responseHasAddress &&
                                    !lowerToolResult.contains("adresse:")) {
                                // Check if address is in RAG
                                boolean addressInRag = ragContext != null && !ragContext.isEmpty() &&
                                        (ragContext.toLowerCase().contains("adresse") ||
                                                ragContext.toLowerCase()
                                                        .matches(".*\\d+\\s+(rue|avenue|boulevard|place).*"));

                                if (!addressInRag) {
                                    log.warn("🚨 BOT INVENTED ADDRESS! Tool result: {}, Response: {}, RAG: {}",
                                            combinedToolResults.substring(0, Math.min(300, combinedToolResults.length())),
                                            content.substring(0, Math.min(300, content.length())),
                                            ragContext != null
                                                    ? ragContext.substring(0, Math.min(200, ragContext.length()))
                                                    : "null");
                                    return Mono.just("I don't have this information available in my data.");
                                } else {
                                    log.debug("Address found in RAG context, allowing response.");
                                }
                            }

                            // General check for invented information
                            // If the response contains suspicious patterns but toolResult doesn't
                            // contain them
                            if (containsPotentiallyInventedInfo(content)) {
                                // Check if toolResult contains at least part of this info
                                boolean toolResultHasInfo = lowerToolResult.contains("adresse") ||
                                        lowerToolResult.contains("téléphone") ||
                                        lowerToolResult.contains("phone") ||
                                        lowerToolResult.contains("horaire") ||
                                        lowerToolResult.contains("ouverture");

                                if (!toolResultHasInfo) {
                                    // Check if the information is in RAG
                                    boolean infoInRag = ragContext != null && !ragContext.isEmpty() &&
                                            (ragContext.toLowerCase().contains("adresse") ||
                                                    ragContext.toLowerCase().contains("téléphone") ||
                                                    ragContext.toLowerCase().contains("phone") ||
                                                    ragContext.toLowerCase().contains("horaire") ||
                                                    ragContext.toLowerCase().contains("ouverture"));

                                    if (!infoInRag) {
                                        log.warn("🚨 BOT INVENTED INFORMATION! Tool result: {}, Response: {}, RAG: {}",
                                                combinedToolResults.substring(0, Math.min(300, combinedToolResults.length())),
                                                content.substring(0, Math.min(300, content.length())),
                                                ragContext != null
                                                        ? ragContext.substring(0, Math.min(200, ragContext.length()))
                                                        : "null");
                                        return Mono.just("I don't have this information available in my data.");
                                    } else {
                                        log.debug("Information found in RAG context, allowing response.");
                                    }
                                }
                            }
                        }

                        // Return content if not null and not empty, otherwise return a default message
                        if (content != null && !content.trim().isEmpty()) {
                            return Mono.just(content);
                        } else {
                            log.warn("⚠️ Mistral returned empty response, returning default message");
                            return Mono.just("Je n'ai pas pu générer de réponse. Pouvez-vous reformuler votre question ?");
                        }
                    }
                    log.warn("⚠️ Mistral API returned no choices in response");
                    return Mono.just("Je n'ai pas reçu de réponse de l'API. Pouvez-vous réessayer ?");
                })
                .onErrorResume(e -> {
                    log.error("Error calling Mistral with tool result: {}", e.getMessage(), e);
                    return Mono.just("Sorry, an error occurred while generating the final response.");
                });
    }

    /**
     * Checks if a response potentially contains invented information
     * (addresses, phone numbers, opening hours, etc. that look like real data)
     */
    private boolean containsPotentiallyInventedInfo(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase();

        // Detect patterns that suggest invented information:
        // - Addresses with street numbers (e.g., "1 Rue", "75010", etc.)
        // - French phone numbers (format +33 or 0X XX XX XX XX)
        // - French postal codes (5 digits)
        // - Opening hours (e.g., "8h30", "Monday to Friday", "9h-17h", etc.)
        boolean hasAddressPattern = lowerContent.matches(".*\\d+\\s+(rue|avenue|boulevard|place|chemin|allée).*") ||
                lowerContent.matches(".*\\d{5}\\s+paris.*") ||
                lowerContent.matches(".*adresse.*\\d+.*");

        boolean hasPhonePattern = lowerContent
                .matches(".*\\+33\\s*[0-9]\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}.*") ||
                lowerContent.matches(".*0[1-9]\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}.*");

        boolean hasPostalCode = lowerContent.matches(".*\\d{5}.*");

        // Detect opening hours (common patterns)
        boolean hasOpeningHours = lowerContent
                .matches(".*(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*") ||
                lowerContent.matches(".*\\d+h\\d+.*\\d+h\\d+.*") ||
                lowerContent.matches(
                        ".*(horaires|horaire|ouverture).*(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*")
                ||
                lowerContent.matches(".*(du|de)\\s+(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*");

        // If we have an address, phone number, or opening hours AND the
        // context doesn't explicitly mention
        // that we used a tool, it's suspicious
        return (hasAddressPattern || hasPhonePattern) && hasPostalCode || hasOpeningHours;
    }

    /**
     * Builds a minimal system prompt for subsequent messages in a conversation
     * (the full prompt is already in the first message history)
     */
    private String buildMinimalSystemPrompt(MatrixAssistantAuthContext authContext) {
        StringBuilder prompt = new StringBuilder();
        if (minimalPrompt != null && !minimalPrompt.isEmpty()) {
            prompt.append(minimalPrompt).append("\n");
        } else {
            prompt.append("You are Alfred, the AI assistant for NeoHoods.\n");
            prompt.append(
                    "⚠️ ABSOLUTE RULE: NEVER invent information. Use MCP tools if necessary.\n");
        }

        if (authContext.isPublicResponse()) {
            if (contextPublic != null && !contextPublic.isEmpty()) {
                prompt.append(contextPublic).append("\n");
            } else {
                prompt.append("WARNING: Public room - do not share sensitive information.\n");
            }
        }

        // Add admin commands only if necessary (dynamic part)
        if (adminCommandService != null && adminCommandService.isAdminUser(authContext.getMatrixUserId())) {
            Locale locale = Locale.ENGLISH;
            if (authContext.getUserEntity().isPresent()) {
                locale = authContext.getUserEntity().get().getLocale();
            }
            String adminCommands = adminCommandService.getAdminCommandsForPrompt(locale);
            if (adminCommands != null && !adminCommands.isEmpty()) {
                prompt.append("\n").append(adminCommands);
            }
        }

        return prompt.toString();
    }

    /**
     * Builds the complete system prompt for Mistral by loading the template
     * from resources
     * et en remplaçant les placeholders dynamiques
     */
    private String buildSystemPrompt(MatrixAssistantAuthContext authContext) {
        if (baseSystemPrompt == null || baseSystemPrompt.isEmpty()) {
            log.warn("Base system prompt not loaded, using default");
            baseSystemPrompt = getDefaultSystemPrompt();
        }

        String prompt = baseSystemPrompt;

        // Replace {CONTEXT_PUBLIC_OR_PRIVATE}
        String contextMessage;
        if (authContext.isPublicResponse()) {
            contextMessage = (contextPublic != null && !contextPublic.isEmpty())
                    ? contextPublic + "\n\n"
                    : "";
        } else {
            contextMessage = (contextPrivate != null && !contextPrivate.isEmpty())
                    ? contextPrivate + "\n\n"
                    : "";
        }
        prompt = prompt.replace("{CONTEXT_PUBLIC_OR_PRIVATE}", contextMessage);

        // Replace {RESERVATION_FLOW}
        // Reservations require portal authentication (userEntity)
        String reservationFlowText = "";
        if (authContext.getUserEntity().isPresent()) {
            reservationFlowText = (reservationFlow != null && !reservationFlow.isEmpty())
                    ? reservationFlow + "\n\n"
                    : "";
        }
        prompt = prompt.replace("{RESERVATION_FLOW}", reservationFlowText);

        // Replace {ADMIN_COMMANDS}
        String adminCommandsSection = "";
        if (adminCommandService != null && adminCommandService.isAdminUser(authContext.getMatrixUserId())) {
            Locale locale = Locale.ENGLISH;
            if (authContext.getUserEntity().isPresent()) {
                locale = authContext.getUserEntity().get().getLocale();
            }
            String adminCommands = adminCommandService.getAdminCommandsForPrompt(locale);
            if (adminCommands != null && !adminCommands.isEmpty()) {
                String warning = messageSource.getMessage("matrix.admin.commands.warning", null, locale);
                adminCommandsSection = "\n" + adminCommands + "\n" + warning + "\n";
            }
        }
        prompt = prompt.replace("{ADMIN_COMMANDS}", adminCommandsSection);

        // Remplacer {NO_ADMIN_WARNING}
        String noAdminWarning = "";
        if (adminCommandService == null || !adminCommandService.isAdminUser(authContext.getMatrixUserId())) {
            noAdminWarning = "\n**IMPORTANT**: Si un utilisateur te demande ce que tu peux faire, " +
                    "liste uniquement les capacités générales (informations, réservations, etc.). " +
                    "NE mentionne JAMAIS de commandes admin ou de fonctionnalités réservées aux administrateurs.\n";
        }
        prompt = prompt.replace("{NO_ADMIN_WARNING}", noAdminWarning);

        return prompt;
    }

    /**
     * Converts an MCP tool to Mistral function format
     */
    private Map<String, Object> convertMCPToolToMistralFunction(MCPTool mcpTool) {
        Map<String, Object> function = new HashMap<>();
        function.put("type", "function");

        Map<String, Object> functionDef = new HashMap<>();
        functionDef.put("name", mcpTool.getName());
        functionDef.put("description", mcpTool.getDescription());
        functionDef.put("parameters", mcpTool.getInputSchema());

        function.put("function", functionDef);
        return function;
    }

    /**
     * Determines the appropriate tool_choice value based on the user message.
     * For questions that clearly require MCP tools, we force tool usage.
     * 
     * @param userMessage The user's message
     * @param tools       Available tools
     * @return "required" to force tool usage, "auto" otherwise
     */
    private String determineToolChoice(String userMessage, List<Map<String, Object>> tools) {
        if (userMessage == null || userMessage.isEmpty() || tools.isEmpty()) {
            return "auto";
        }

        String lowerMessage = userMessage.toLowerCase().trim();

        // Patterns that REQUIRE tool usage:
        // 1. Resident information questions
        boolean requiresResidentInfo = lowerMessage.matches(".*(qui habite|who lives|résident|resident).*") ||
                lowerMessage.matches(".*(appartement|apartment|étage|floor).*\\d+.*") ||
                lowerMessage.matches(".*\\d{3,4}.*")
                        && (lowerMessage.contains("habite") || lowerMessage.contains("lives"));

        // 2. Emergency contacts / ACAF questions
        boolean requiresEmergencyContacts = lowerMessage
                .matches(".*(acaf|urgence|emergency|syndic|numéro|number|téléphone|phone|adresse|address).*") ||
                lowerMessage.matches(".*(combien.*bâtiment|how many.*building).*");

        // 3. Space/reservation questions
        boolean requiresSpaceInfo = lowerMessage
                .matches(".*(espace|space|réservation|reservation|disponible|available).*") ||
                lowerMessage.matches(".*(liste.*espace|list.*space).*");

        // 4. Info questions
        boolean requiresInfo = lowerMessage.matches(".*(info|information|description|service).*");

        if (requiresResidentInfo || requiresEmergencyContacts || requiresSpaceInfo || requiresInfo) {
            log.info("🔧 Forcing tool usage for message: {}",
                    userMessage.substring(0, Math.min(100, userMessage.length())));
            return "required"; // Force Mistral to use at least one tool
        }

        return "auto"; // Let Mistral decide
    }

    /**
     * Checks if a message requires a tool call based on patterns
     */
    private boolean requiresToolCall(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String lowerMessage = message.toLowerCase().trim();
        return lowerMessage.matches(".*(qui habite|who lives|résident|resident).*") ||
                lowerMessage
                        .matches(".*(acaf|urgence|emergency|syndic|numéro|number|téléphone|phone|adresse|address).*")
                ||
                lowerMessage.matches(".*(espace|space|réservation|reservation|disponible|available).*") ||
                lowerMessage.matches(".*(info|information|description|service).*") ||
                (lowerMessage.matches(".*\\d{3,4}.*")
                        && (lowerMessage.contains("habite") || lowerMessage.contains("lives")))
                ||
                lowerMessage.matches(".*(combien.*bâtiment|how many.*building).*");
    }

    /**
     * Forces a tool call by calling Mistral again with tool_choice="required"
     */
    private Mono<String> forceToolCall(
            String userMessage,
            List<Map<String, Object>> previousMessages,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext,
            String ragContext) {

        log.info("🔄 Forcing tool call for message: {}", userMessage);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        List<Map<String, Object>> messages = new ArrayList<>();

        // Add system prompt
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildMinimalSystemPrompt(authContext) + "\n\n" + ragContext);
        messages.add(systemMsg);

        // Add conversation history (skip the last assistant message that said "Je vais
        // chercher")
        for (int i = 0; i < previousMessages.size() - 1; i++) {
            Map<String, Object> msg = previousMessages.get(i);
            if (!"system".equals(msg.get("role"))) {
                messages.add(msg);
            }
        }

        // Add user message again
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("tools", tools);
        requestBody.put("tool_choice", "required"); // FORCE tool usage
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    return processMistralResponse(response, authContext, messages, ragContext, tools);
                })
                .onErrorResume(e -> {
                    log.error("Error forcing tool call: {}",
                            e instanceof Exception ? ((Exception) e).getMessage() : e.toString(), e);
                    return Mono.just("Sorry, an error occurred while searching for information.");
                });
    }
}
