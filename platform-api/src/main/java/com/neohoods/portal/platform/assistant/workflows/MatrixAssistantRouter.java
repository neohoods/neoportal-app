package com.neohoods.portal.platform.assistant.workflows;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.time.Duration;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.WorkflowType;
import com.neohoods.portal.platform.assistant.services.ApplicationStartupTimeService;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.exceptions.CodedException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Router service that identifies the workflow type and delegates to
 * specialized agents.
 * 
 * This service:
 * 1. Identifies the workflow via a lightweight LLM call
 * 2. Delegates to the appropriate specialized agent
 * 3. Filters tools according to the workflow
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantRouter {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final MatrixAssistantAgentContextService agentContextService;
    private final ApplicationStartupTimeService startupTimeService;

    @Autowired(required = false)
    private com.neohoods.portal.platform.assistant.services.MistralAgentsService mistralAgentsService;

    @Autowired(required = false)
    private com.neohoods.portal.platform.assistant.services.MistralConversationsService mistralConversationsService;

    @Value("${neohoods.portal.matrix.assistant.ai.provider}")
    private String provider;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.ai.model}")
    private String model;

    @Value("${neohoods.portal.matrix.assistant.ai.router-prompt-file:classpath:matrix-assistant/prompts/router/matrix-assistant-router-prompt.txt}")
    private String routerPromptFile;

    private static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";

    // Agents (will be injected as they are created)
    @Autowired(required = false)
    private MatrixAssistantGeneralAgent generalAgent;

    @Autowired(required = false)
    private MatrixAssistantResidentInfoAgent residentInfoAgent;

    @Autowired(required = false)
    private MatrixAssistantSpaceAgent spaceAgent;

    @Autowired(required = false)
    private MatrixAssistantHelpAgent helpAgent;

    private String routerPrompt;

    @PostConstruct
    public void loadRouterPrompt() {
        try {
            Resource resource = resourceLoader.getResource(routerPromptFile);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    routerPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    log.info("Loaded router prompt from {}", routerPromptFile);
                }
            } else {
                log.warn("Router prompt file not found: {}", routerPromptFile);
                routerPrompt = getDefaultRouterPrompt();
            }
        } catch (Exception e) {
            log.error("Error loading router prompt from {}: {}", routerPromptFile, e.getMessage(), e);
            routerPrompt = getDefaultRouterPrompt();
        }
    }

    private String getDefaultRouterPrompt() {
        return "You are a workflow identifier. Respond with ONLY one word: GENERAL, RESIDENT_INFO, RESERVATION, SUPPORT, or HELP.";
    }

    /**
     * Builds an enhanced router prompt that includes current workflow
     * information
     */
    private String buildEnhancedRouterPrompt(WorkflowType currentWorkflow) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(routerPrompt);

        if (currentWorkflow != null) {
            promptBuilder.append("\n\n");
            promptBuilder.append("**CURRENT WORKFLOW CONTEXT:**\n");
            promptBuilder.append("The user is currently in a ").append(currentWorkflow.name()).append(" workflow.\n");
            promptBuilder.append("\n");
            promptBuilder.append("**IMPORTANT:**\n");
            promptBuilder.append("- If the user's latest message is still related to the current workflow (")
                    .append(currentWorkflow.name()).append("), respond with ").append(currentWorkflow.name())
                    .append(".\n");
            promptBuilder.append(
                    "- If the user's latest message starts a NEW topic or workflow, respond with the appropriate new workflow type.\n");
            promptBuilder.append(
                    "- Consider the conversation history to understand if the user is continuing the current workflow or starting a new one.\n");
            promptBuilder.append(
                    "- Short responses like 'oui', 'yes', 'ok', 'd'accord' typically continue the current workflow.\n");
        }

        return promptBuilder.toString();
    }

    /**
     * Removes timestamps from conversation history before passing to agents
     * (timestamps are only used for pre-filtering in router)
     */
    private List<Map<String, Object>> cleanTimestampsFromHistory(List<Map<String, Object>> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return conversationHistory;
        }

        List<Map<String, Object>> cleanHistory = new ArrayList<>();
        for (Map<String, Object> msg : conversationHistory) {
            Map<String, Object> cleanMsg = new HashMap<>();
            cleanMsg.put("role", msg.get("role"));
            cleanMsg.put("content", msg.get("content"));
            // Remove timestamps - they're only for router pre-filtering
            cleanHistory.add(cleanMsg);
        }
        return cleanHistory;
    }

    /**
     * Filters conversation history to get only recent messages (last 10, within 15
     * minutes).
     * In local/dev environments, also excludes messages from before application
     * startup
     * to avoid stale conversation context after backend restart.
     */
    private List<Map<String, Object>> filterRecentMessages(List<Map<String, Object>> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return new ArrayList<>();
        }

        long currentTime = System.currentTimeMillis();
        long fifteenMinutesAgo = currentTime - (15 * 60 * 1000); // 15 minutes in milliseconds
        long startupTime = startupTimeService.getApplicationStartTimeMillis();
        boolean filterByStartup = startupTimeService.shouldFilterByStartupTime();

        // Filter messages within last 15 minutes AND after application startup (in
        // dev/local)
        List<Map<String, Object>> recentMessages = new ArrayList<>();
        int skippedBeforeStartup = 0;
        for (Map<String, Object> message : conversationHistory) {
            Object timestampObj = message.get("timestamp");
            if (timestampObj instanceof Number) {
                long timestamp = ((Number) timestampObj).longValue();

                // Check if message is within 15 minutes
                if (timestamp < fifteenMinutesAgo) {
                    continue; // Too old
                }

                // In dev/local, also check if message is after application startup
                if (filterByStartup && timestamp < startupTime) {
                    skippedBeforeStartup++;
                    continue; // Message from before restart
                }

                recentMessages.add(message);
            } else {
                // If no timestamp, assume it's recent (for backward compatibility)
                // But in dev/local, exclude if we're filtering by startup time
                if (!filterByStartup) {
                    recentMessages.add(message);
                } else {
                    skippedBeforeStartup++;
                }
            }
        }

        if (skippedBeforeStartup > 0) {
            log.info("Filtered out {} messages from before application startup (dev/local mode)",
                    skippedBeforeStartup);
        }

        // Take last 10 messages (or all if less than 10)
        int startIndex = Math.max(0, recentMessages.size() - 10);
        List<Map<String, Object>> lastTenMessages = new ArrayList<>(
                recentMessages.subList(startIndex, recentMessages.size()));

        log.debug("Filtered to {} recent messages (within 15 min, after startup, last 10) out of {} total messages",
                lastTenMessages.size(), conversationHistory.size());

        return lastTenMessages;
    }

    /**
     * Determines if we should include current workflow based on last user message
     * timestamp
     * Returns true if last user message is less than 15 minutes old
     */
    private boolean shouldIncludeCurrentWorkflow(List<Map<String, Object>> recentHistory) {
        if (recentHistory == null || recentHistory.isEmpty()) {
            return false;
        }

        // Find last user message
        for (int i = recentHistory.size() - 1; i >= 0; i--) {
            Map<String, Object> message = recentHistory.get(i);
            if ("user".equals(message.get("role"))) {
                Object timestampObj = message.get("timestamp");
                if (timestampObj instanceof Number) {
                    long timestamp = ((Number) timestampObj).longValue();
                    long currentTime = System.currentTimeMillis();
                    long fifteenMinutesAgo = currentTime - (15 * 60 * 1000);
                    boolean isRecent = timestamp >= fifteenMinutesAgo;
                    log.debug("Last user message timestamp: {} ({}), should include workflow: {}",
                            timestamp, new java.util.Date(timestamp), isRecent);
                    return isRecent;
                } else {
                    // If no timestamp, assume it's recent (for backward compatibility)
                    return true;
                }
            }
        }

        // No user message found in recent history
        return false;
    }

    /**
     * Main entry point for handling messages.
     * Identifies the workflow and delegates to the appropriate agent.
     * 
     * @param userMessage         User message
     * @param conversationHistory Conversation history (list format)
     * @param authContext         Authorization context
     * @return Response from the appropriate agent
     */
    public Mono<String> handleMessage(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {

        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.just("Je n'ai pas compris votre message. Pouvez-vous reformuler ?");
        }

        log.info("Router handling message: {} (user: {})",
                userMessage.substring(0, Math.min(50, userMessage.length())),
                authContext.getMatrixUserId());

        // Step 1: Identify workflow using LLM (with conversation history and current
        // workflow)
        return identifyWorkflow(userMessage, conversationHistory, authContext)
                // Step 1bis: Heuristic override to avoid obvious misclassifications (e.g.
                // reservations)
                .map(workflowType -> maybeOverrideWorkflow(userMessage, workflowType))
                .flatMap(workflowType -> {
                    log.info("Identified workflow (after override if any): {} for message: {}", workflowType,
                            userMessage.substring(0, Math.min(50, userMessage.length())));

                    // Step 2: Delegate to appropriate agent
                    // Clean timestamps from conversationHistory before passing to agents
                    // (timestamps are only used for pre-filtering in router)
                    List<Map<String, Object>> cleanHistory = cleanTimestampsFromHistory(conversationHistory);
                    return delegateToAgent(workflowType, userMessage, cleanHistory, authContext);
                })
                .onErrorResume(e -> {
                    // Convert errors to CodedException and propagate
                    if (e instanceof CodedException) {
                        return Mono.error(e);
                    }
                    // Check if it's a 5xx error (server error)
                    boolean isServerError = isServerError(e);
                    String errorCode = isServerError ? "MATRIX_ROUTER_SERVER_ERROR" : "MATRIX_ROUTER_ERROR";
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("error", e.getClass().getSimpleName());
                    variables.put("message", e.getMessage());
                    return Mono.error(new CodedException(errorCode, "Error in router workflow", variables, null, e));
                });
    }

    /**
     * Identifies the workflow type using ROUTING_AGENT with Conversations API
     * Falls back to Chat Completions API if Agents API is not available
     */
    private Mono<WorkflowType> identifyWorkflow(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {

        // Check if Mistral Agents API is available
        if (mistralAgentsService != null && mistralConversationsService != null) {
            String routingAgentName = com.neohoods.portal.platform.assistant.services.MistralAgentsService
                    .getRoutingAgentName();
            String routingAgentId = mistralAgentsService.getAgentId(routingAgentName);
            if (routingAgentId != null && !routingAgentId.isEmpty()) {
                return identifyWorkflowWithConversationsAPI(userMessage, conversationHistory, authContext,
                        routingAgentId);
            }
        }

        // Fallback to Chat Completions API
        return identifyWorkflowWithChatCompletions(userMessage, conversationHistory, authContext);
    }

    /**
     * Identifies workflow using Conversations API with ROUTING_AGENT
     */
    private Mono<WorkflowType> identifyWorkflowWithConversationsAPI(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext,
            String routingAgentId) {

        String roomId = authContext.getRoomId();
        WorkflowType currentWorkflow = null;
        if (roomId != null) {
            MatrixAssistantAgentContextService.AgentContext context = agentContextService.getContext(roomId);
            if (context != null) {
                currentWorkflow = context.getCurrentWorkflow();
            }
        }

        // Pre-filter: Get recent conversation history
        List<Map<String, Object>> recentHistory = filterRecentMessages(conversationHistory);
        boolean shouldIncludeCurrentWorkflow = shouldIncludeCurrentWorkflow(recentHistory);
        if (!shouldIncludeCurrentWorkflow) {
            currentWorkflow = null;
            log.debug("Last user message is older than 15 minutes, ignoring current workflow");
        }

        // Short message optimization
        if (currentWorkflow != null && userMessage != null && userMessage.trim().length() <= 18) {
            log.debug("Short message with existing workflow {}, reusing it without LLM", currentWorkflow);
            return Mono.just(currentWorkflow);
        }

        final WorkflowType finalCurrentWorkflow = currentWorkflow;

        // Build inputs: include recent history + current message
        List<Map<String, Object>> inputs = new ArrayList<>();
        if (!recentHistory.isEmpty()) {
            for (Map<String, Object> msg : recentHistory) {
                Map<String, Object> cleanMsg = new HashMap<>();
                cleanMsg.put("role", msg.get("role"));
                cleanMsg.put("content", msg.get("content"));
                inputs.add(cleanMsg);
            }
        }
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        inputs.add(userMsg);

        // Check if we should create a new conversation or append
        boolean shouldCreateNew = mistralConversationsService.shouldCreateNewConversation(roomId, routingAgentId);

        Mono<Map<String, Object>> responseMono;
        if (shouldCreateNew) {
            log.debug("Starting new routing conversation for room {}", roomId);
            responseMono = mistralConversationsService.startConversation(roomId, routingAgentId, inputs, true)
                    .flatMap(conversationId -> {
                        // Update context
                        if (roomId != null) {
                            MatrixAssistantAgentContextService.AgentContext context = agentContextService
                                    .getOrCreateContext(roomId);
                            context.setMistralConversationId(conversationId);
                            context.updateLastInteractionTime();
                        }
                        // Retrieve conversation to get entries
                        return retrieveConversation(conversationId);
                    });
        } else {
            log.debug("Appending to existing routing conversation for room {}", roomId);
            responseMono = mistralConversationsService.appendToConversation(roomId, userMessage)
                    .flatMap(conversationId -> {
                        // Update context
                        if (roomId != null) {
                            MatrixAssistantAgentContextService.AgentContext context = agentContextService
                                    .getOrCreateContext(roomId);
                            context.setMistralConversationId(conversationId);
                            context.updateLastInteractionTime();
                        }
                        // Retrieve conversation to get entries
                        return retrieveConversation(conversationId);
                    });
        }

        return responseMono
                .map(response -> parseWorkflowFromConversationEntries(response, finalCurrentWorkflow))
                .onErrorResume(e -> {
                    log.warn("Failed to use Conversations API for routing, falling back to Chat Completions: {}",
                            e.getMessage());
                    return identifyWorkflowWithChatCompletions(userMessage, conversationHistory, authContext);
                });
    }

    /**
     * Retrieves a conversation by ID
     */
    private Mono<Map<String, Object>> retrieveConversation(String conversationId) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.get()
                .uri("/conversations/{conversationId}", conversationId)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(java.util.Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedResponse = (Map<String, Object>) response;
                    Map<String, String> mdcContext = org.slf4j.MDC.getCopyOfContextMap();
                    logMistralResponse("Routing conversation", typedResponse, mdcContext);
                    return typedResponse;
                });
    }

    /**
     * Parses workflow type from conversation entries
     */
    @SuppressWarnings("unchecked")
    private WorkflowType parseWorkflowFromConversationEntries(Map<String, Object> conversationResponse,
            WorkflowType fallbackWorkflow) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) conversationResponse.get("entries");
        if (entries == null || entries.isEmpty()) {
            log.warn("No entries in conversation response, using fallback workflow");
            return fallbackWorkflow != null ? fallbackWorkflow : WorkflowType.GENERAL;
        }

        // Find the latest message.output entry with tool_reference chunks
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map<String, Object> entry = entries.get(i);
            String entryType = (String) entry.get("type");

            if ("message.output".equals(entryType)) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) entry.get("content");
                if (content != null) {
                    for (Map<String, Object> chunk : content) {
                        String chunkType = (String) chunk.get("type");
                        if ("tool_reference".equals(chunkType)) {
                            Map<String, Object> toolRef = (Map<String, Object>) chunk.get("tool_reference");
                            if (toolRef != null) {
                                String toolName = (String) toolRef.get("name");
                                if ("route_to_workflow".equals(toolName)) {
                                    Map<String, Object> output = (Map<String, Object>) toolRef.get("output");
                                    if (output != null) {
                                        String workflowTypeStr = (String) output.get("workflowType");
                                        if (workflowTypeStr != null) {
                                            try {
                                                WorkflowType workflow = WorkflowType.valueOf(workflowTypeStr);
                                                log.debug("Parsed workflow type from conversation: {}", workflow);
                                                return workflow;
                                            } catch (IllegalArgumentException e) {
                                                log.warn("Invalid workflow type '{}' in conversation", workflowTypeStr);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        log.warn("Could not parse workflow type from conversation entries, using fallback");
        return fallbackWorkflow != null ? fallbackWorkflow : WorkflowType.GENERAL;
    }

    /**
     * Fallback: Identifies workflow using Chat Completions API (original
     * implementation)
     */
    private Mono<WorkflowType> identifyWorkflowWithChatCompletions(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // Get current workflow from context
        String roomId = authContext.getRoomId();
        WorkflowType currentWorkflow = null;
        if (roomId != null) {
            MatrixAssistantAgentContextService.AgentContext context = agentContextService.getContext(roomId);
            if (context != null) {
                currentWorkflow = context.getCurrentWorkflow();
            }
        }

        // Pre-filter: Get recent conversation history (last 10 messages within 15
        // minutes)
        List<Map<String, Object>> recentHistory = filterRecentMessages(conversationHistory);

        // Check if we should include current workflow based on last user message
        // timestamp
        boolean shouldIncludeCurrentWorkflow = shouldIncludeCurrentWorkflow(recentHistory);
        if (!shouldIncludeCurrentWorkflow) {
            currentWorkflow = null; // Don't use current workflow if last message is too old
            log.debug("Last user message is older than 15 minutes, ignoring current workflow");
        }

        // If message is very short/elliptic and we already have a current workflow,
        // keep it to avoid LLM chatter
        if (currentWorkflow != null && userMessage != null && userMessage.trim().length() <= 18) {
            log.debug("Short message with existing workflow {}, reusing it without LLM", currentWorkflow);
            return Mono.just(currentWorkflow);
        }

        // Snapshot for lambda
        final WorkflowType finalCurrentWorkflow = currentWorkflow;

        // Build enhanced prompt with current workflow information
        String enhancedPrompt = buildEnhancedRouterPrompt(currentWorkflow);

        // Build messages for workflow identification
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", enhancedPrompt);
        messages.add(systemMsg);

        // Add recent conversation history (if available)
        // Remove timestamps before sending to LLM (they're only for filtering)
        if (!recentHistory.isEmpty()) {
            for (Map<String, Object> msg : recentHistory) {
                Map<String, Object> cleanMsg = new HashMap<>();
                cleanMsg.put("role", msg.get("role"));
                cleanMsg.put("content", msg.get("content"));
                messages.add(cleanMsg);
            }
            log.debug("Added {} messages from recent conversation history (timestamps removed)", recentHistory.size());
        }

        // Add current user message
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // Create router function call tool to force structured output
        List<Map<String, Object>> routerTools = new ArrayList<>();
        Map<String, Object> routerTool = new HashMap<>();
        routerTool.put("type", "function");

        Map<String, Object> routerFunction = new HashMap<>();
        routerFunction.put("name", "route_to_workflow");
        routerFunction.put("description",
                "Route the user's message to the appropriate workflow. You MUST call this function with the workflow type.");

        Map<String, Object> routerParameters = new HashMap<>();
        routerParameters.put("type", "object");
        Map<String, Object> routerProperties = new HashMap<>();

        Map<String, Object> workflowTypeField = new HashMap<>();
        workflowTypeField.put("type", "string");
        workflowTypeField.put("enum", java.util.Arrays.asList("GENERAL", "RESIDENT_INFO", "SPACE", "HELP", "SUPPORT"));
        workflowTypeField.put("description", "The workflow type to route to");
        routerProperties.put("workflowType", workflowTypeField);

        routerParameters.put("properties", routerProperties);
        routerParameters.put("required", java.util.Arrays.asList("workflowType"));
        routerFunction.put("parameters", routerParameters);
        routerTool.put("function", routerFunction);
        routerTools.add(routerTool);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.1); // very deterministic
        requestBody.put("tools", routerTools);
        // Force function call - Mistral format: {"type": "function", "function":
        // {"name": "function_name"}}
        Map<String, Object> toolChoice = new HashMap<>();
        toolChoice.put("type", "function");
        Map<String, Object> functionChoice = new HashMap<>();
        functionChoice.put("name", "route_to_workflow");
        toolChoice.put("function", functionChoice);
        requestBody.put("tool_choice", toolChoice);

        Map<String, String> mdcContext = org.slf4j.MDC.getCopyOfContextMap();
        // Log the complete prompt and messages before calling Mistral
        logMistralRequest("Workflow identification", requestBody, messages, mdcContext);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(response -> logMistralResponse("Workflow identification", response, mdcContext))
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        log.warn("No choices in workflow identification response, defaulting to GENERAL");
                        return WorkflowType.GENERAL;
                    }

                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");

                    // Check for function call (router now uses function calls)
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        Map<String, Object> toolCall = toolCalls.get(0);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                        if (function != null && "route_to_workflow".equals(function.get("name"))) {
                            String argumentsJson = (String) function.get("arguments");
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> args = objectMapper.readValue(
                                        argumentsJson,
                                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class,
                                                Object.class));
                                String workflowTypeStr = (String) args.get("workflowType");
                                if (workflowTypeStr != null) {
                                    try {
                                        WorkflowType workflow = WorkflowType.valueOf(workflowTypeStr);
                                        log.debug("Parsed workflow type from function call: {}", workflow);
                                        return workflow;
                                    } catch (IllegalArgumentException e) {
                                        log.warn("Invalid workflow type '{}' in function call arguments",
                                                workflowTypeStr);
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Error parsing router function call arguments: {}", e.getMessage());
                            }
                        }
                    }

                    // Fallback: try to parse from content (should not happen with function calls)
                    String content = (String) message.get("content");
                    if (content != null && !content.trim().isEmpty()) {
                        String workflowStr = content.trim().split("\\s+")[0].toUpperCase().replaceAll("[^A-Z_]", "");
                        try {
                            WorkflowType workflow = WorkflowType.valueOf(workflowStr);
                            log.debug("Parsed workflow type from content: {}", workflow);
                            return workflow;
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid workflow type '{}' in response content", workflowStr);
                        }
                    }

                    // Final fallback
                    log.warn("Could not parse workflow type from function call or content, using fallback");
                    if (finalCurrentWorkflow != null) {
                        return finalCurrentWorkflow;
                    }
                    return WorkflowType.GENERAL;
                })
                .onErrorResume(e -> {
                    // Convert errors to CodedException and propagate
                    if (e instanceof CodedException) {
                        return Mono.error(e);
                    }
                    // Check if it's a 5xx error (server error)
                    boolean isServerError = isServerError(e);
                    String errorCode = isServerError ? "MATRIX_ROUTER_WORKFLOW_IDENTIFICATION_SERVER_ERROR"
                            : "MATRIX_ROUTER_WORKFLOW_IDENTIFICATION_ERROR";
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("error", e.getClass().getSimpleName());
                    variables.put("message", e.getMessage());
                    return Mono.error(new CodedException(errorCode, "Error identifying workflow", variables, null, e));
                });
    }

    /**
     * Checks if a workflow requires a DM (Direct Message)
     */
    private boolean requiresDM(WorkflowType workflowType) {
        switch (workflowType) {
            case SPACE:
                return true; // Reservations must be in DM
            case GENERAL:
            case RESIDENT_INFO:
            case SUPPORT:
                return false; // These can work in public or DM
            default:
                return false;
        }
    }

    /**
     * Heuristic override to avoid obvious LLM routing mistakes
     * If the message clearly mentions reservations/parking, force RESERVATION
     */
    private WorkflowType maybeOverrideWorkflow(String userMessage, WorkflowType workflowType) {
        if (userMessage == null || userMessage.isEmpty()) {
            return workflowType;
        }

        String lower = userMessage.toLowerCase();
        boolean mentionsReservation = lower.contains("réserv") || lower.contains("reserv")
                || lower.contains("booking") || lower.contains("book");
        boolean mentionsParking = lower.contains("parking") || lower.contains("place de parking");

        if ((mentionsReservation || mentionsParking) && workflowType != WorkflowType.SPACE) {
            log.warn("Heuristic override: forcing workflow to RESERVATION (previous: {}) for message: {}",
                    workflowType, userMessage);
            return WorkflowType.SPACE;
        }

        return workflowType;
    }

    /**
     * Delegates to the appropriate agent based on workflow type
     */
    private Mono<String> delegateToAgent(
            WorkflowType workflowType,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {

        // Check if workflow requires DM and we're in a public room
        if (requiresDM(workflowType) && !authContext.isDirectMessage()) {
            log.warn("Workflow {} requires DM but conversation is in public room (roomId={}, isDirectMessage={})",
                    workflowType, authContext.getRoomId(), authContext.isDirectMessage());
            Map<String, Object> variables = new HashMap<>();
            variables.put("workflow", workflowType.name());
            variables.put("roomId", authContext.getRoomId());
            throw new CodedException("MATRIX_ROUTER_DM_REQUIRED",
                    "Workflow requires DM for privacy", variables);
        }

        // Log for debugging
        if (requiresDM(workflowType)) {
            log.debug("Workflow {} requires DM - verified: isDirectMessage={}, roomId={}",
                    workflowType, authContext.isDirectMessage(), authContext.getRoomId());
        }

        // Update agent context with current workflow
        String roomId = authContext.getRoomId();
        if (roomId != null) {
            agentContextService.getOrCreateContext(roomId).setCurrentWorkflow(workflowType);
        }

        switch (workflowType) {
            case GENERAL:
                if (generalAgent != null) {
                    return generalAgent.handleMessage(userMessage, conversationHistory, authContext);
                } else {
                    log.warn("General agent not available, returning default message");
                    return Mono.just("Je peux vous aider avec des questions générales sur la copropriété.");
                }

            case RESIDENT_INFO:
                if (residentInfoAgent != null) {
                    return residentInfoAgent.handleMessage(userMessage, conversationHistory, authContext);
                } else {
                    log.warn("Resident info agent not available, falling back to general agent");
                    if (generalAgent != null) {
                        return generalAgent.handleMessage(userMessage, conversationHistory, authContext);
                    }
                    return Mono.just("Je peux vous aider à trouver des informations sur les résidents.");
                }

            case SPACE:
                if (spaceAgent != null) {
                    return spaceAgent.handleMessage(userMessage, conversationHistory, authContext);
                } else {
                    log.warn("Reservation agent not available, falling back to general agent");
                    if (generalAgent != null) {
                        return generalAgent.handleMessage(userMessage, conversationHistory, authContext);
                    }
                    return Mono.just("Je peux vous aider à gérer les réservations d'espaces.");
                }

            case SUPPORT:
                // Support agent not yet implemented, fallback to general
                log.debug("Support workflow not yet implemented, falling back to general agent");
                if (generalAgent != null) {
                    return generalAgent.handleMessage(userMessage, conversationHistory, authContext);
                }
                return Mono.just("Le support n'est pas encore disponible. Veuillez contacter l'administrateur.");

            case HELP:
                if (helpAgent != null) {
                    return helpAgent.handleMessage(userMessage, authContext);
                } else {
                    log.warn("Help agent not available, falling back to general agent");
                    if (generalAgent != null) {
                        return generalAgent.handleMessage(userMessage, conversationHistory, authContext);
                    }
                    return Mono.just(
                            "Je peux vous aider avec plusieurs fonctionnalités. Demandez-moi ce que vous souhaitez faire.");
                }

            default:
                log.warn("Unknown workflow type: {}, falling back to general agent", workflowType);
                if (generalAgent != null) {
                    return generalAgent.handleMessage(userMessage, conversationHistory, authContext);
                }
                throw new CodedException("MATRIX_ROUTER_UNKNOWN_WORKFLOW", "Unknown workflow type: " + workflowType);
        }
    }

    /**
     * Checks if an exception represents a server error (5xx)
     */
    private boolean isServerError(Throwable e) {
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            org.springframework.web.reactive.function.client.WebClientResponseException webEx = (org.springframework.web.reactive.function.client.WebClientResponseException) e;
            int statusCode = webEx.getStatusCode().value();
            return statusCode >= 500 && statusCode < 600;
        }
        // For other exceptions, consider them as server errors if they're not
        // CodedException
        return !(e instanceof CodedException);
    }

    /**
     * Logs the complete Mistral API request in a readable format
     */
    @SuppressWarnings("unchecked")
    private void logMistralRequest(String context, Map<String, Object> requestBody, List<Map<String, Object>> messages,
            Map<String, String> mdcContext) {
        try {
            withMdc(mdcContext, () -> {
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("🔵 MISTRAL REQUEST [{}] - Router", context);
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                // Log model and parameters
                log.info("📋 Model: {}", requestBody.get("model"));
                log.info("📋 Temperature: {}", requestBody.get("temperature"));
                log.info("📋 Max tokens: {}", requestBody.get("max_tokens"));
                if (requestBody.containsKey("tool_choice")) {
                    log.info("📋 Tool choice: {}", requestBody.get("tool_choice"));
                }

                log.info("");
                log.info("💬 MESSAGES:");
                log.info("────────────────────────────────────────────────────────────────────────────────");

                // Log all messages
                for (int i = 0; i < messages.size(); i++) {
                    Map<String, Object> msg = messages.get(i);
                    String role = (String) msg.get("role");
                    String content = (String) msg.get("content");

                    log.info("[Message {}] Role: {}", i + 1, role.toUpperCase());

                    if (content != null && !content.isEmpty()) {
                        // Truncate very long content for readability
                        String displayContent = content.length() > 2000
                                ? content.substring(0, 2000) + "\n... [TRUNCATED - " + (content.length() - 2000)
                                        + " more characters]"
                                : content;
                        log.info("Content:\n{}", displayContent);
                    }

                    log.info("");
                }

                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
        } catch (Exception e) {
            log.warn("Error logging Mistral request: {}", e.getMessage());
        }
    }

    /**
     * Logs the complete Mistral API response in a readable format
     */
    @SuppressWarnings("unchecked")
    private void logMistralResponse(String context, Map<String, Object> response, Map<String, String> mdcContext) {
        try {
            withMdc(mdcContext, () -> {
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("🟢 MISTRAL RESPONSE [{}] - Router", context);
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                // Log usage if present
                if (response.containsKey("usage")) {
                    Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                    log.info("📊 Usage:");
                    log.info("   └─ Prompt tokens: {}", usage.get("prompt_tokens"));
                    log.info("   └─ Completion tokens: {}", usage.get("completion_tokens"));
                    log.info("   └─ Total tokens: {}", usage.get("total_tokens"));
                    log.info("");
                }

                // Log choices
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices == null || choices.isEmpty()) {
                    log.warn("⚠️  No choices in response!");
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    return;
                }

                for (int i = 0; i < choices.size(); i++) {
                    Map<String, Object> choice = choices.get(i);
                    log.info("📦 Choice {}:", i + 1);
                    log.info("   └─ Finish reason: {}", choice.get("finish_reason"));

                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null) {
                        String role = (String) message.get("role");
                        String content = (String) message.get("content");

                        log.info("   └─ Role: {}", role);

                        if (content != null && !content.isEmpty()) {
                            log.info("   └─ Content: {}", content);
                        } else {
                            log.info("   └─ Content: [EMPTY]");
                        }
                    }

                    log.info("");
                }

                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
        } catch (Exception e) {
            log.warn("Error logging Mistral response: {}", e.getMessage());
        }
    }

    /**
     * Execute an action with a specific MDC context, restoring the previous MDC
     * afterwards.
     */
    private void withMdc(Map<String, String> mdcContext, Runnable action) {
        Map<String, String> previous = org.slf4j.MDC.getCopyOfContextMap();
        try {
            if (mdcContext != null) {
                org.slf4j.MDC.setContextMap(mdcContext);
            }
            action.run();
        } finally {
            if (previous != null) {
                org.slf4j.MDC.setContextMap(previous);
            } else {
                org.slf4j.MDC.clear();
            }
        }
    }
}
