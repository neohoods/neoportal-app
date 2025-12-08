package com.neohoods.portal.platform.assistant.workflows;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.WorkflowType;
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
    private MatrixAssistantReservationAgent reservationAgent;

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
     * minutes)
     */
    private List<Map<String, Object>> filterRecentMessages(List<Map<String, Object>> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return new ArrayList<>();
        }

        long currentTime = System.currentTimeMillis();
        long fifteenMinutesAgo = currentTime - (15 * 60 * 1000); // 15 minutes in milliseconds

        // Filter messages within last 15 minutes
        List<Map<String, Object>> recentMessages = new ArrayList<>();
        for (Map<String, Object> message : conversationHistory) {
            Object timestampObj = message.get("timestamp");
            if (timestampObj instanceof Number) {
                long timestamp = ((Number) timestampObj).longValue();
                if (timestamp >= fifteenMinutesAgo) {
                    recentMessages.add(message);
                }
            } else {
                // If no timestamp, assume it's recent (for backward compatibility)
                recentMessages.add(message);
            }
        }

        // Take last 10 messages (or all if less than 10)
        int startIndex = Math.max(0, recentMessages.size() - 10);
        List<Map<String, Object>> lastTenMessages = new ArrayList<>(
                recentMessages.subList(startIndex, recentMessages.size()));

        log.debug("Filtered to {} recent messages (within 15 min, last 10) out of {} total messages",
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
     * Identifies the workflow type using a lightweight LLM call
     * Takes into account conversation history and current workflow
     * This implements the Routing workflow pattern from Anthropic's best practices
     */
    private Mono<WorkflowType> identifyWorkflow(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {
        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
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

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3); // Lower temperature for more consistent classification
        requestBody.put("max_tokens", 10); // Very short response (just the workflow type)

        log.debug("Calling Mistral API for workflow identification");

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        log.warn("No choices in workflow identification response, defaulting to GENERAL");
                        return WorkflowType.GENERAL;
                    }

                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    String content = (String) message.get("content");

                    if (content == null || content.trim().isEmpty()) {
                        log.warn("Empty content in workflow identification response, defaulting to GENERAL");
                        return WorkflowType.GENERAL;
                    }

                    // Parse workflow type from response
                    String workflowStr = content.trim().toUpperCase();
                    try {
                        WorkflowType workflow = WorkflowType.valueOf(workflowStr);
                        log.debug("Parsed workflow type: {}", workflow);
                        return workflow;
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid workflow type '{}' in response, defaulting to GENERAL", workflowStr);
                        return WorkflowType.GENERAL;
                    }
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
            case RESERVATION:
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

        if ((mentionsReservation || mentionsParking) && workflowType != WorkflowType.RESERVATION) {
            log.warn("Heuristic override: forcing workflow to RESERVATION (previous: {}) for message: {}",
                    workflowType, userMessage);
            return WorkflowType.RESERVATION;
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

            case RESERVATION:
                if (reservationAgent != null) {
                    return reservationAgent.handleMessage(userMessage, conversationHistory, authContext);
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
}
