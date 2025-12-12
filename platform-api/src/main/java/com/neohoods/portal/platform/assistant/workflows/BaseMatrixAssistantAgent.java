package com.neohoods.portal.platform.assistant.workflows;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPContent;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.ReservationPeriod;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.services.matrix.rag.MatrixAssistantRAGService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Base class for specialized Matrix assistant agents.
 * Provides common functionality for LLM calls, tool filtering, and response
 * generation.
 */
@Slf4j
public abstract class BaseMatrixAssistantAgent {

    protected final WebClient.Builder webClientBuilder;
    protected final ObjectMapper objectMapper;
    protected final MatrixAssistantMCPAdapter mcpAdapter;
    protected final ResourceLoader resourceLoader;
    protected final MatrixAssistantAgentContextService agentContextService;

    @Autowired(required = false)
    protected com.neohoods.portal.platform.assistant.services.MistralAgentsService mistralAgentsService;

    @Autowired(required = false)
    protected com.neohoods.portal.platform.assistant.services.MistralConversationsService mistralConversationsService;

    @Value("${neohoods.portal.matrix.assistant.ai.provider}")
    protected String provider;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key}")
    protected String apiKey;

    @Value("${neohoods.portal.matrix.assistant.ai.model}")
    protected String model;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled}")
    protected boolean ragEnabled;

    @Autowired(required = false)
    protected MatrixAssistantRAGService ragService;

    protected static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";
    protected static final int MAX_TOOL_CALL_CHAIN = 3; // Reduced to prevent infinite loops

    public BaseMatrixAssistantAgent(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            MatrixAssistantMCPAdapter mcpAdapter,
            ResourceLoader resourceLoader,
            MatrixAssistantAgentContextService agentContextService) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.mcpAdapter = mcpAdapter;
        this.resourceLoader = resourceLoader;
        this.agentContextService = agentContextService;
    }

    /**
     * Handles a user message and generates a response
     * 
     * @param userMessage         User message
     * @param conversationHistory Conversation history
     * @param authContext         Authorization context
     * @return Response from the agent
     */
    public abstract Mono<String> handleMessage(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext);

    /**
     * Gets the system prompt for this agent
     */
    protected abstract String getSystemPrompt(MatrixAssistantAuthContext authContext);

    /**
     * Gets the list of tool names available to this agent
     */
    protected abstract Set<String> getAvailableToolNames();

    /**
     * Filters tools based on the agent's available tools
     */
    protected List<Map<String, Object>> filterTools(List<MCPTool> allTools) {
        Set<String> availableToolNames = getAvailableToolNames();
        return allTools.stream()
                .filter(tool -> availableToolNames.contains(tool.getName()))
                .map(this::convertMCPToolToMistralFunction)
                .collect(Collectors.toList());
    }

    /**
     * Converts an MCP tool to Mistral function format
     */
    protected Map<String, Object> convertMCPToolToMistralFunction(MCPTool mcpTool) {
        Map<String, Object> function = new HashMap<>();
        function.put("type", "function");
        function.put("function", Map.of(
                "name", mcpTool.getName(),
                "description", mcpTool.getDescription() != null ? mcpTool.getDescription() : "",
                "parameters", mcpTool.getInputSchema() != null ? mcpTool.getInputSchema() : Map.of()));
        return function;
    }

    /**
     * Calls Mistral API with JSON response format for structured protocol.
     * Used by reservation agent to get structured ReservationStepResponse.
     */
    protected Mono<SpaceStepResponse> callMistralAPIWithJSONResponse(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext) {
        return callMistralAPIWithJSONResponse(null, userMessage, conversationHistory, systemPrompt, tools, authContext);
    }

    /**
     * Calls Mistral API with JSON response format for structured protocol, with an
     * optional context label for logging (e.g., step name).
     */
    protected Mono<SpaceStepResponse> callMistralAPIWithJSONResponse(
            String contextLabel,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext) {

        // Configure HttpClient with longer timeouts for Mistral API
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // Get RAG context if enabled
        Mono<String> ragContextMono = Mono.just("");
        if (ragEnabled && ragService != null && shouldUseRAG()) {
            ragContextMono = ragService.searchRelevantContext(userMessage)
                    .map(context -> context.isEmpty() ? "" : "Documentation context:\n" + context + "\n\n");
        }

        return ragContextMono.flatMap(ragContext -> {
            // Capture current MDC context to propagate across async threads (Netty)
            Map<String, String> mdcContext = org.slf4j.MDC.getCopyOfContextMap();
            // Build messages
            List<Map<String, Object>> messages = new ArrayList<>();

            // System message with RAG context
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt + "\n\n" + ragContext);
            messages.add(systemMsg);

            // Conversation history (clean timestamps before sending to Mistral)
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                for (Map<String, Object> histMsg : conversationHistory) {
                    Map<String, Object> cleanMsg = new HashMap<>();
                    cleanMsg.put("role", histMsg.get("role"));
                    cleanMsg.put("content", histMsg.get("content"));
                    // Preserve tool_calls and tool_call_id if present
                    if (histMsg.containsKey("tool_calls")) {
                        cleanMsg.put("tool_calls", histMsg.get("tool_calls"));
                    }
                    if (histMsg.containsKey("tool_call_id")) {
                        cleanMsg.put("tool_call_id", histMsg.get("tool_call_id"));
                    }
                    messages.add(cleanMsg);
                }
            }

            // User message
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            // Build request body with JSON response format
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
                // Note: response_format cannot be used with tools in Mistral API
                // The prompt must explicitly request JSON format
            } else {
                // Only use response_format when no tools are present
                requestBody.put("response_format", Map.of("type", "json_object"));
            }
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);

            String logLabel = contextLabel != null ? contextLabel : "JSON response format";
            // Log the complete prompt and messages BEFORE sending HTTP request
            // Restore MDC context for logging
            withMdc(mdcContext, () -> logMistralRequest(logLabel, requestBody, messages, mdcContext));

            log.info("🚀 Sending HTTP request to Mistral API [{}]", logLabel);

            return webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnNext(response -> {
                        log.info("✅ Received HTTP response from Mistral API [{}]", logLabel);
                        // Restore MDC context in the async thread
                        Map<String, String> contextToUse = mdcContext != null ? mdcContext : Map.of();
                        withMdc(contextToUse, () -> logMistralResponse(logLabel, response, contextToUse));
                    })
                    .flatMap(response -> processMistralJSONResponse(response, authContext, messages, ragContext, tools))
                    .onErrorResume(e -> {
                        String errorMsg = e instanceof Exception ? ((Exception) e).getMessage() : e.toString();
                        log.error("Error calling Mistral API with JSON response for agent {}: {}",
                                getClass().getSimpleName(), errorMsg, e);
                        return Mono.just(SpaceStepResponse.builder()
                                .status(SpaceStepResponse.StepStatus.ERROR)
                                .response("Désolé, une erreur s'est produite lors de la génération de la réponse.")
                                .build());
                    });
        });
    }

    /**
     * Processes Mistral JSON response and handles tool calls if needed, returning
     * ReservationStepResponse
     */
    @SuppressWarnings("unchecked")
    protected Mono<SpaceStepResponse> processMistralJSONResponse(
            Map<String, Object> response,
            MatrixAssistantAuthContext authContext,
            List<Map<String, Object>> previousMessages,
            String ragContext,
            List<Map<String, Object>> tools) {

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Aucune réponse générée.")
                    .build());
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        String content = (String) message.get("content");

        // Check if Mistral wants to call a function
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            log.info("Agent {} requested {} tool call(s) in JSON response mode", getClass().getSimpleName(),
                    toolCalls.size());

            // Check if Mistral is calling submit_reservation_step (function call for
            // structured output)
            for (Map<String, Object> toolCall : toolCalls) {
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                if (function != null) {
                    String functionName = (String) function.get("name");
                    if ("submit_reservation_step".equals(functionName)) {
                        // This is a function call for structured output - extract arguments directly
                        log.info("Agent {} called submit_reservation_step - extracting structured response",
                                getClass().getSimpleName());
                        String argumentsJson = (String) function.get("arguments");
                        try {
                            return parseSubmitReservationStepArguments(argumentsJson);
                        } catch (Exception e) {
                            log.error("Error parsing submit_reservation_step arguments: {}", e.getMessage(), e);
                            return Mono.just(SpaceStepResponse.builder()
                                    .status(SpaceStepResponse.StepStatus.ERROR)
                                    .response("Erreur lors du traitement de la réponse structurée.")
                                    .build());
                        }
                    }
                }
            }

            // Handle other tool calls recursively, then parse final JSON response
            // Extract contextLabel from MDC if available (e.g., step=CHOOSE_SPACE)
            Map<String, String> currentMdc = org.slf4j.MDC.getCopyOfContextMap();
            String contextLabel = currentMdc != null ? currentMdc.get("step") : null;
            return callMistralWithToolResultsRecursiveForJSON(
                    contextLabel, previousMessages, message, toolCalls, authContext, ragContext, tools, 0)
                    .flatMap(finalResponse -> {
                        // After tool calls, we should get a final JSON response
                        // Try to parse it as ReservationStepResponse
                        return parseJSONResponse(finalResponse);
                    });
        }

        // No tool calls, parse JSON content
        if (content == null || content.trim().isEmpty()) {
            log.warn("Empty JSON response from Mistral for agent {}", getClass().getSimpleName());
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Je n'ai pas pu générer de réponse. Pouvez-vous reformuler votre question ?")
                    .build());
        }

        return parseJSONResponse(content);
    }

    /**
     * Parses submit_reservation_step function call arguments into
     * SpaceStepResponse.
     * This is used when Mistral calls submit_reservation_step as a function call
     * to force structured JSON output.
     */
    @SuppressWarnings("unchecked")
    private Mono<SpaceStepResponse> parseSubmitReservationStepArguments(String argumentsJson) {
        try {
            Map<String, Object> args = objectMapper.readValue(
                    argumentsJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

            SpaceStepResponse.StepStatus status;
            try {
                String statusStr = (String) args.get("status");
                status = SpaceStepResponse.StepStatus.valueOf(statusStr);
            } catch (Exception e) {
                log.warn("Invalid status in submit_reservation_step arguments: {}, defaulting to ASK_USER",
                        args.get("status"));
                status = SpaceStepResponse.StepStatus.ASK_USER;
            }

            String response = (String) args.get("response");
            if (response == null || response.isEmpty()) {
                response = "Réponse vide.";
            }

            String spaceId = (String) args.get("spaceId");
            // Normalize empty string to null
            if (spaceId != null && spaceId.isEmpty()) {
                spaceId = null;
            }

            ReservationPeriod period = null;
            Map<String, Object> periodMap = (Map<String, Object>) args.get("period");
            if (periodMap != null) {
                String startDate = (String) periodMap.get("startDate");
                String endDate = (String) periodMap.get("endDate");
                String startTime = (String) periodMap.get("startTime");
                String endTime = (String) periodMap.get("endTime");

                if (startDate != null && endDate != null) {
                    period = ReservationPeriod.builder()
                            .startDate(startDate)
                            .endDate(endDate)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build();
                }
            }

            SpaceStep nextStep = null;
            String nextStepStr = (String) args.get("nextStep");
            if (nextStepStr != null) {
                try {
                    nextStep = SpaceStep.valueOf(nextStepStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid nextStep in submit_reservation_step arguments: {}", nextStepStr);
                }
            }

            // Parse availableSpaces map (key = space number, value = UUID)
            java.util.Map<String, String> availableSpaces = null;
            Object availableSpacesObj = args.get("availableSpaces");
            if (availableSpacesObj != null) {
                if (availableSpacesObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> spacesMap = (java.util.Map<String, Object>) availableSpacesObj;
                    availableSpaces = new java.util.HashMap<>();
                    for (java.util.Map.Entry<String, Object> entry : spacesMap.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue() != null ? entry.getValue().toString() : null;
                        if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
                            availableSpaces.put(key, value);
                        }
                    }
                } else if (availableSpacesObj instanceof java.util.List) {
                    // Backward compatibility: convert list to map (numbers only, no UUIDs)
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> spacesList = (java.util.List<Object>) availableSpacesObj;
                    availableSpaces = new java.util.HashMap<>();
                    for (Object obj : spacesList) {
                        String number = obj != null ? obj.toString() : null;
                        if (number != null && !number.isEmpty()) {
                            // No UUID available in list format, will need to be resolved later
                            availableSpaces.put(number, null);
                        }
                    }
                }
            }

            return Mono.just(SpaceStepResponse.builder()
                    .status(status)
                    .response(response)
                    .spaceId(spaceId)
                    .period(period)
                    .nextStep(nextStep)
                    .availableSpaces(availableSpaces)
                    .build());
        } catch (Exception e) {
            log.error("Error parsing submit_reservation_step arguments: {}", e.getMessage(), e);
            log.debug("Failed arguments JSON: {}", argumentsJson);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Erreur lors du traitement de la réponse structurée.")
                    .build());
        }
    }

    /**
     * Parses JSON content into ReservationStepResponse
     * Handles cases where Mistral returns text before/after JSON or markdown code
     * blocks
     */
    private Mono<SpaceStepResponse> parseJSONResponse(String jsonContent) {
        try {
            String cleanJson = extractJSONFromText(jsonContent);

            // Map old status "PENDING" to "ASK_USER" for backward compatibility
            cleanJson = cleanJson.replaceAll("\"status\"\\s*:\\s*\"PENDING\"", "\"status\":\"ASK_USER\"");
            cleanJson = cleanJson.replaceAll("\"status\"\\s*:\\s*\"COMPLETED\"", "\"status\":\"SWITCH_STEP\"");

            SpaceStepResponse response = objectMapper.readValue(cleanJson, SpaceStepResponse.class);

            // Validate response
            if (response.getStatus() == null) {
                log.warn("JSON response missing status field, defaulting to ASK_USER");
                response.setStatus(SpaceStepResponse.StepStatus.ASK_USER);
            }
            if (response.getResponse() == null || response.getResponse().isEmpty()) {
                log.warn("JSON response missing response field");
                response.setResponse("Réponse vide.");
            }

            return Mono.just(response);
        } catch (Exception e) {
            log.error("Error parsing JSON response: {}", e.getMessage(), e);
            log.debug("Failed JSON content: {}", jsonContent);
            // Fallback: try to extract response text and return as ASK_USER to keep the
            // flow
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ASK_USER)
                    .response(jsonContent) // Return raw content as fallback
                    .build());
        }
    }

    /**
     * Maps legacy ReservationStepResponse to SpaceStepResponse.
     */
    private SpaceStepResponse mapReservationToSpaceResponse(SpaceStepResponse reservation) {
        if (reservation == null) {
            return SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Réponse vide.")
                    .build();
        }

        SpaceStepResponse.StepStatus status = SpaceStepResponse.StepStatus.ANSWER_USER;
        switch (reservation.getStatus()) {
            case COMPLETED:
                status = SpaceStepResponse.StepStatus.COMPLETED;
                break;
            case CANCEL:
                status = SpaceStepResponse.StepStatus.CANCEL;
                break;
            case ERROR:
                status = SpaceStepResponse.StepStatus.ERROR;
                break;
            case SWITCH_STEP:
                status = SpaceStepResponse.StepStatus.SWITCH_STEP;
                break;
            case ASK_USER:
            default:
                status = SpaceStepResponse.StepStatus.ASK_USER;
                break;
        }

        return SpaceStepResponse.builder()
                .status(status)
                .nextStep(null)
                .internalMessage(reservation.getInternalMessage())
                .response(reservation.getResponse())
                .spaceId(reservation.getSpaceId())
                .period(reservation.getPeriod())
                .locale(reservation.getLocale())
                .build();
    }

    /**
     * Extracts JSON object from text that may contain markdown code blocks or text
     * before/after JSON
     * Handles cases where Mistral returns text like "Je vais vous aider. { ... }"
     */
    private String extractJSONFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty JSON content");
        }

        String cleanText = text.trim();

        // Remove markdown code blocks if present
        if (cleanText.startsWith("```json")) {
            cleanText = cleanText.substring(7);
        } else if (cleanText.startsWith("```")) {
            cleanText = cleanText.substring(3);
        }
        if (cleanText.endsWith("```")) {
            cleanText = cleanText.substring(0, cleanText.length() - 3);
        }
        cleanText = cleanText.trim();

        // Try to find JSON object in the text (handle cases where there's text
        // before/after)
        // Use a more robust approach: find the first { and then find the matching }
        int firstBrace = cleanText.indexOf('{');

        if (firstBrace == -1) {
            // No JSON object found, return as-is and let parser handle error
            log.warn("No JSON object found in response, returning as-is: {}",
                    cleanText.substring(0, Math.min(100, cleanText.length())));
            return cleanText;
        }

        // Find the matching closing brace by counting braces
        int braceCount = 0;
        int lastBrace = -1;
        for (int i = firstBrace; i < cleanText.length(); i++) {
            char c = cleanText.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    lastBrace = i;
                    break;
                }
            }
        }

        if (lastBrace == -1 || firstBrace >= lastBrace) {
            // No matching closing brace found, return as-is
            log.warn("No matching closing brace found, returning as-is: {}",
                    cleanText.substring(0, Math.min(100, cleanText.length())));
            return cleanText;
        }

        // Extract JSON object
        String jsonObject = cleanText.substring(firstBrace, lastBrace + 1);

        // Validate it's valid JSON by trying to parse it
        try {
            objectMapper.readTree(jsonObject);
            log.debug("Successfully extracted JSON object from text");
            return jsonObject;
        } catch (Exception e) {
            // If extraction failed, try the whole cleaned text
            log.debug("Extracted JSON object is invalid, trying full cleaned text: {}", e.getMessage());
            return cleanText;
        }
    }

    /**
     * Recursively handles tool calls and their results for JSON response mode
     * Similar to callMistralWithToolResultsRecursive but returns String for JSON
     * parsing
     */
    @SuppressWarnings("unchecked")
    protected Mono<String> callMistralWithToolResultsRecursiveForJSON(
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            List<Map<String, Object>> toolCalls,
            MatrixAssistantAuthContext authContext,
            String ragContext,
            List<Map<String, Object>> tools,
            int iteration) {
        return callMistralWithToolResultsRecursiveForJSON(null, previousMessages, assistantMessage, toolCalls,
                authContext, ragContext, tools, iteration);
    }

    /**
     * Recursive tool handling with optional context label for logging
     */
    @SuppressWarnings("unchecked")
    protected Mono<String> callMistralWithToolResultsRecursiveForJSON(
            String contextLabel,
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            List<Map<String, Object>> toolCalls,
            MatrixAssistantAuthContext authContext,
            String ragContext,
            List<Map<String, Object>> tools,
            int iteration) {

        if (iteration >= MAX_TOOL_CALL_CHAIN) {
            log.warn("Max tool call chain reached ({}) for agent {} in JSON mode", MAX_TOOL_CALL_CHAIN,
                    getClass().getSimpleName());
            return Mono.just(
                    "{\"status\":\"PENDING\",\"response\":\"J'ai atteint la limite d'appels d'outils. Veuillez reformuler votre demande.\"}");
        }

        // Verify tool calls match expected format
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCallsInAssistantMessage = (List<Map<String, Object>>) assistantMessage
                .get("tool_calls");
        if (toolCallsInAssistantMessage == null || toolCallsInAssistantMessage.size() != toolCalls.size()) {
            log.error("Mismatch: assistant message has {} tool_calls but we're processing {}",
                    toolCallsInAssistantMessage != null ? toolCallsInAssistantMessage.size() : 0, toolCalls.size());
            return Mono.just("{\"status\":\"PENDING\",\"response\":\"Erreur lors du traitement de votre demande.\"}");
        }

        // Capture MDC for propagation in recursive calls
        Map<String, String> mdcContext = org.slf4j.MDC.getCopyOfContextMap();
        String logLabel = contextLabel != null
                ? "step=" + contextLabel + " (recursive iteration " + (iteration + 1) + ")"
                : "JSON response format (recursive iteration " + (iteration + 1) + ")";

        // Execute all tool calls
        List<Map<String, Object>> toolResults = new ArrayList<>();
        for (Map<String, Object> toolCall : toolCalls) {
            String toolCallId = (String) toolCall.get("id");
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String functionName = (String) function.get("name");
            String functionArgumentsJson = (String) function.get("arguments");

            // Special handling for submit_reservation_step: extract arguments and return as
            // JSON
            if ("submit_reservation_step".equals(functionName)) {
                log.info("Agent {} called submit_reservation_step in recursive call - extracting structured response",
                        getClass().getSimpleName());
                // Return the arguments JSON directly as the final response
                return Mono.just(functionArgumentsJson);
            }

            try {
                Map<String, Object> functionArguments = objectMapper.readValue(
                        functionArgumentsJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                // Sanitize known args (e.g., spaceId) using context
                functionArguments = sanitizeToolArguments(functionArguments, authContext);

                // Call MCP tool
                MatrixMCPModels.MCPToolResult toolResult = mcpAdapter.callMCPToolDirect(functionName,
                        functionArguments, authContext);

                // Build tool result text
                String toolResultText = toolResult.getContent().stream()
                        .map(MCPContent::getText)
                        .filter(text -> text != null)
                        .collect(Collectors.joining("\n"));

                // Build tool result in format expected by Mistral API
                Map<String, Object> toolResultMap = new HashMap<>();
                toolResultMap.put("tool_call_id", toolCallId);
                toolResultMap.put("role", "tool");
                toolResultMap.put("content", toolResultText);
                toolResults.add(toolResultMap);
            } catch (Exception e) {
                log.error("Error calling tool {} for agent {} in JSON mode: {}", functionName,
                        getClass().getSimpleName(), e.getMessage(), e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("tool_call_id", toolCallId);
                errorResult.put("role", "tool");
                errorResult.put("content", "Erreur lors de l'appel de l'outil: " + e.getMessage());
                toolResults.add(errorResult);
            }
        }

        // Build messages for next API call
        List<Map<String, Object>> messagesForNextCall = new ArrayList<>(previousMessages);

        // Add assistant message with tool_calls
        Map<String, Object> assistantMsgWithTools = new HashMap<>();
        assistantMsgWithTools.put("role", "assistant");
        assistantMsgWithTools.put("tool_calls", toolCalls);
        messagesForNextCall.add(assistantMsgWithTools);

        // Add tool results
        messagesForNextCall.addAll(toolResults);

        // Check if submit_reservation_step tool is available BEFORE building
        // requestBody
        boolean hasSubmitReservationStepTool = false;
        for (Object toolObj : tools) {
            @SuppressWarnings("unchecked")
            Map<String, Object> toolMap = (Map<String, Object>) toolObj;
            if (toolMap != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> func = (Map<String, Object>) toolMap.get("function");
                if (func != null) {
                    String name = (String) func.get("name");
                    if ("submit_reservation_step".equals(name)) {
                        hasSubmitReservationStepTool = true;
                        break;
                    }
                }
            }
        }

        // Note: We don't add a system message here because Mistral only accepts one
        // system message
        // per conversation. Instead, we rely on tool_choice to force the function call.

        // Call Mistral API again with tool results
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
        requestBody.put("model", model);
        requestBody.put("messages", messagesForNextCall);
        if (!tools.isEmpty()) {
            requestBody.put("tools", tools);
            // If submit_reservation_step is available, force it to be called
            // Mistral format: {"type": "function", "function": {"name": "function_name"}}
            if (hasSubmitReservationStepTool) {
                Map<String, Object> toolChoice = new HashMap<>();
                toolChoice.put("type", "function");
                Map<String, Object> functionChoice = new HashMap<>();
                functionChoice.put("name", "submit_reservation_step");
                toolChoice.put("function", functionChoice);
                requestBody.put("tool_choice", toolChoice);
            } else {
                requestBody.put("tool_choice", "auto");
            }
            // Note: response_format cannot be used with tools in Mistral API
            // The prompt must explicitly request JSON format
        } else {
            // Only use response_format when no tools are present
            requestBody.put("response_format", Map.of("type", "json_object"));
        }
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        // Log the complete prompt and messages BEFORE sending HTTP request recursively
        withMdc(mdcContext, () -> logMistralRequest(logLabel, requestBody, messagesForNextCall, mdcContext));

        log.info("🚀 Sending HTTP request to Mistral API [{}]", logLabel);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(response -> {
                    log.info("✅ Received HTTP response from Mistral API [{}]", logLabel);
                    withMdc(mdcContext, () -> logMistralResponse(logLabel, response, mdcContext));
                })
                .flatMap(response -> {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        return Mono.just("{\"status\":\"PENDING\",\"response\":\"Aucune réponse générée.\"}");
                    }

                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    String content = (String) message.get("content");

                    // Check if Mistral wants to call more functions
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> newToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                    if (newToolCalls != null && !newToolCalls.isEmpty()) {
                        // Recursive call for more tool calls (preserve contextLabel)
                        return callMistralWithToolResultsRecursiveForJSON(
                                contextLabel, messagesForNextCall, message, newToolCalls, authContext, ragContext,
                                tools,
                                iteration + 1);
                    }

                    // No more tool calls, return the JSON content
                    if (content == null || content.trim().isEmpty()) {
                        log.warn("Empty JSON response from Mistral for agent {} at iteration {}",
                                getClass().getSimpleName(),
                                iteration + 1);
                        return Mono
                                .just("{\"status\":\"PENDING\",\"response\":\"Je n'ai pas pu générer de réponse.\"}");
                    }

                    return Mono.just(content.trim());
                })
                .onErrorResume(e -> {
                    String errorMsg = e instanceof Exception ? ((Exception) e).getMessage() : e.toString();
                    log.error("Error in recursive JSON call for agent {} at iteration {}: {}",
                            getClass().getSimpleName(), iteration + 1, errorMsg, e);
                    return Mono.just("{\"status\":\"PENDING\",\"response\":\"Erreur lors du traitement.\"}");
                });
    }

    /**
     * Calls Mistral API with the agent's specific prompt and filtered tools
     */
    protected Mono<String> callMistralAPI(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            String systemPrompt,
            List<Map<String, Object>> tools,
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

        // Get RAG context if enabled
        Mono<String> ragContextMono = Mono.just("");
        if (ragEnabled && ragService != null && shouldUseRAG()) {
            ragContextMono = ragService.searchRelevantContext(userMessage)
                    .map(context -> context.isEmpty() ? "" : "Documentation context:\n" + context + "\n\n");
        }

        return ragContextMono.flatMap(ragContext -> {
            // Build messages
            List<Map<String, Object>> messages = new ArrayList<>();

            // System message with RAG context
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt + "\n\n" + ragContext);
            messages.add(systemMsg);

            // Conversation history (clean timestamps before sending to Mistral)
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                for (Map<String, Object> histMsg : conversationHistory) {
                    Map<String, Object> cleanMsg = new HashMap<>();
                    cleanMsg.put("role", histMsg.get("role"));
                    cleanMsg.put("content", histMsg.get("content"));
                    // Timestamps are only for filtering, not for Mistral API
                    messages.add(cleanMsg);
                }
            }

            // User message
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
            }
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);

            // Log the complete prompt and messages before calling Mistral
            Map<String, String> mdcContext = org.slf4j.MDC.getCopyOfContextMap();
            logMistralRequest("Standard response", requestBody, messages, mdcContext);

            return webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnNext(response -> logMistralResponse("Standard response", response, mdcContext))
                    .flatMap(response -> processMistralResponse(response, authContext, messages, ragContext, tools))
                    .onErrorResume(e -> {
                        String errorMsg = e instanceof Exception ? ((Exception) e).getMessage() : e.toString();
                        log.error("Error calling Mistral API for agent {}: {}", getClass().getSimpleName(), errorMsg,
                                e);
                        return Mono.just("Désolé, une erreur s'est produite lors de la génération de la réponse.");
                    });
        });
    }

    /**
     * Processes Mistral response and handles tool calls if needed
     */
    @SuppressWarnings("unchecked")
    protected Mono<String> processMistralResponse(
            Map<String, Object> response,
            MatrixAssistantAuthContext authContext,
            List<Map<String, Object>> previousMessages,
            String ragContext,
            List<Map<String, Object>> tools) {

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return Mono.just("Aucune réponse générée.");
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        String content = (String) message.get("content");

        // Check if Mistral wants to call a function
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            log.info("Agent {} requested {} tool call(s)", getClass().getSimpleName(), toolCalls.size());
            // previousMessages contains: system, history, user
            // We need to add: assistant (with tool_calls), then tool results
            // But for the recursive call, previousMessages should NOT include the assistant
            // message yet
            // So we pass previousMessages as-is, and add assistant message in iteration 0
            return callMistralWithToolResultsRecursive(
                    previousMessages, message, toolCalls, authContext, ragContext, tools, 0);
        }

        // No tool calls, return the content
        if (content == null || content.trim().isEmpty()) {
            log.warn("Empty response from Mistral for agent {}", getClass().getSimpleName());
            return Mono.just("Je n'ai pas pu générer de réponse. Pouvez-vous reformuler votre question ?");
        }

        return Mono.just(content.trim());
    }

    /**
     * Recursively handles tool calls and their results
     */
    @SuppressWarnings("unchecked")
    protected Mono<String> callMistralWithToolResultsRecursive(
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            List<Map<String, Object>> toolCalls,
            MatrixAssistantAuthContext authContext,
            String ragContext,
            List<Map<String, Object>> tools,
            int iteration) {

        if (iteration >= MAX_TOOL_CALL_CHAIN) {
            log.warn("Max tool call chain reached ({}) for agent {}", MAX_TOOL_CALL_CHAIN, getClass().getSimpleName());
            return Mono.just(
                    "J'ai atteint la limite d'appels d'outils. Veuillez reformuler votre demande de manière plus simple.");
        }

        // Verify tool calls match expected format
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCallsInAssistantMessage = (List<Map<String, Object>>) assistantMessage
                .get("tool_calls");
        if (toolCallsInAssistantMessage == null || toolCallsInAssistantMessage.size() != toolCalls.size()) {
            log.error("Mismatch: assistant message has {} tool_calls but we're processing {}",
                    toolCallsInAssistantMessage != null ? toolCallsInAssistantMessage.size() : 0, toolCalls.size());
            return Mono.just("Erreur lors du traitement de votre demande. Veuillez réessayer.");
        }

        // Execute all tool calls
        // Build tool results in the format expected by MatrixAssistantAIService logic
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

                // Build tool result text
                String toolResultText = toolResult.getContent().stream()
                        .map(MCPContent::getText)
                        .filter(text -> text != null)
                        .collect(Collectors.joining("\n"));

                // Special handling for create_reservation: if it succeeds, mark it for early
                // termination
                // This prevents infinite loops where Mistral keeps calling tools after
                // successful reservation creation
                if ("create_reservation".equals(functionName) && !toolResult.isError() && toolResultText != null) {
                    String lowerText = toolResultText.toLowerCase();
                    if (lowerText.contains("réservation créée") || lowerText.contains("reservation created") ||
                            lowerText.contains("créée avec succès") || lowerText.contains("created successfully") ||
                            (lowerText.contains("réservé") && !lowerText.contains("erreur")) ||
                            (lowerText.contains("reserved") && !lowerText.contains("error"))) {
                        log.info("✅ Reservation created successfully in tool call, will force final response");
                        // Store this in a thread-local or pass it through the recursive call
                        // For now, we'll check in the toolResults list
                    }
                }

                // Build tool result in format expected by MatrixAssistantAIService logic
                // Format: {tool_call_id, function_name, result}
                Map<String, Object> toolResultMap = new HashMap<>();
                toolResultMap.put("tool_call_id", toolCallId);
                toolResultMap.put("function_name", functionName);
                toolResultMap.put("result", toolResultText);
                toolResults.add(toolResultMap);
            } catch (Exception e) {
                log.error("Error calling tool {} for agent {}: {}", functionName, getClass().getSimpleName(),
                        e instanceof Exception ? ((Exception) e).getMessage() : e.toString(), e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("tool_call_id", toolCallId);
                errorResult.put("function_name", functionName);
                errorResult.put("result", "Erreur lors de l'appel de l'outil: "
                        + (e.getMessage() != null ? e.getMessage() : e.toString()));
                toolResults.add(errorResult);
            }
        }

        // Verify tool calls match results
        if (toolCalls.size() != toolResults.size()) {
            log.error("Mismatch: {} tool calls but {} tool results for agent {}",
                    toolCalls.size(), toolResults.size(), getClass().getSimpleName());
            return Mono.just("Erreur lors du traitement de votre demande. Veuillez réessayer.");
        }

        // Allow specialized agents to process tool results and update context
        processToolResults(toolCalls, toolResults, authContext);

        // Special handling for reservation agent: if check_space_availability was
        // called with all info,
        // and space is available, force create_reservation call
        if (getClass().getSimpleName().contains("Reservation")) {
            boolean hasCheckAvailability = false;
            boolean hasCreateReservation = false;
            String spaceId = null;
            String startDate = null;
            String endDate = null;
            String startTime = null;
            String endTime = null;
            boolean spaceAvailable = false;

            // Check what tools were called
            for (Map<String, Object> toolCall : toolCalls) {
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                String functionName = (String) function.get("name");
                if ("check_space_availability".equals(functionName)) {
                    hasCheckAvailability = true;
                    try {
                        String functionArgumentsJson = (String) function.get("arguments");
                        Map<String, Object> functionArguments = objectMapper.readValue(
                                functionArgumentsJson,
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                        spaceId = (String) functionArguments.get("spaceId");
                        startDate = (String) functionArguments.get("startDate");
                        endDate = (String) functionArguments.get("endDate");
                        startTime = (String) functionArguments.getOrDefault("startTime", "00:00");
                        endTime = (String) functionArguments.getOrDefault("endTime", "23:59");
                    } catch (Exception e) {
                        log.warn("Failed to parse check_space_availability arguments: {}", e.getMessage());
                    }
                } else if ("create_reservation".equals(functionName)) {
                    hasCreateReservation = true;
                }
            }

            // Check if space is available from check_space_availability result
            if (hasCheckAvailability && !hasCreateReservation) {
                for (Map<String, Object> toolResult : toolResults) {
                    String functionName = (String) toolResult.get("function_name");
                    if ("check_space_availability".equals(functionName)) {
                        String result = (String) toolResult.get("result");
                        if (result != null) {
                            String lowerResult = result.toLowerCase();
                            // Check if space is available (not "non disponible", "not available", "occupé",
                            // "occupied")
                            spaceAvailable = !lowerResult.contains("non disponible") &&
                                    !lowerResult.contains("not available") &&
                                    !lowerResult.contains("occupé") &&
                                    !lowerResult.contains("occupied") &&
                                    !lowerResult.contains("indisponible") &&
                                    !lowerResult.contains("unavailable");
                        }
                    }
                }

                // If space is available and we have all required info, force create_reservation
                // Use default times if not provided
                String finalStartTime = startTime != null && !startTime.isEmpty() ? startTime : "00:00";
                String finalEndTime = endTime != null && !endTime.isEmpty() ? endTime : "23:59";

                if (spaceAvailable && spaceId != null && startDate != null && endDate != null) {
                    log.info(
                            "🔄 Forcing create_reservation call after check_space_availability (spaceId={}, startDate={}, endDate={}, startTime={}, endTime={})",
                            spaceId, startDate, endDate, finalStartTime, finalEndTime);

                    // Create a synthetic tool call for create_reservation
                    Map<String, Object> createReservationCall = new HashMap<>();
                    createReservationCall.put("id", "forced_" + System.currentTimeMillis());
                    Map<String, Object> createReservationFunction = new HashMap<>();
                    createReservationFunction.put("name", "create_reservation");
                    Map<String, Object> createReservationArgs = new HashMap<>();
                    createReservationArgs.put("spaceId", spaceId);
                    createReservationArgs.put("startDate", startDate);
                    createReservationArgs.put("endDate", endDate);
                    createReservationArgs.put("startTime", finalStartTime);
                    createReservationArgs.put("endTime", finalEndTime);
                    try {
                        String createReservationArgsJson = objectMapper.writeValueAsString(createReservationArgs);
                        createReservationFunction.put("arguments", createReservationArgsJson);
                        createReservationCall.put("function", createReservationFunction);

                        // Execute the forced create_reservation call
                        String toolCallId = (String) createReservationCall.get("id");
                        MatrixMCPModels.MCPToolResult createReservationResult = mcpAdapter.callMCPToolDirect(
                                "create_reservation", createReservationArgs, authContext);

                        String createReservationResultText = createReservationResult.getContent().stream()
                                .map(MCPContent::getText)
                                .filter(text -> text != null)
                                .collect(Collectors.joining("\n"));

                        // Add the result to toolResults
                        Map<String, Object> createReservationResultMap = new HashMap<>();
                        createReservationResultMap.put("tool_call_id", toolCallId);
                        createReservationResultMap.put("function_name", "create_reservation");
                        createReservationResultMap.put("result", createReservationResultText);
                        toolResults.add(createReservationResultMap);

                        log.info("✅ Forced create_reservation call completed, result: {}",
                                createReservationResultText.substring(0,
                                        Math.min(200, createReservationResultText.length())));
                    } catch (Exception e) {
                        log.error("Error forcing create_reservation call: {}", e.getMessage(), e);
                    }
                }
            }
        }

        // Check if any tool result indicates successful reservation creation
        // This allows us to stop early before calling Mistral again
        boolean hasSuccessfulReservationInResults = false;
        log.info("Checking {} tool results for successful reservation creation", toolResults.size());
        for (Map<String, Object> toolResult : toolResults) {
            String functionName = (String) toolResult.get("function_name");
            String result = (String) toolResult.get("result");
            log.info("Tool result: functionName={}, result length={}", functionName,
                    result != null ? result.length() : 0);
            if ("create_reservation".equals(functionName) && result != null) {
                String lowerResult = result.toLowerCase();
                log.info("Checking create_reservation result: {}", result.substring(0, Math.min(100, result.length())));
                if (lowerResult.contains("réservation créée") || lowerResult.contains("reservation created") ||
                        lowerResult.contains("créée avec succès") || lowerResult.contains("created successfully") ||
                        (lowerResult.contains("réservé") && !lowerResult.contains("erreur")) ||
                        (lowerResult.contains("reserved") && !lowerResult.contains("error"))) {
                    hasSuccessfulReservationInResults = true;
                    log.info(
                            "✅ Found successful reservation creation in tool results, will force final response without calling Mistral again");
                    break;
                }
            }
        }

        // If reservation was created successfully, generate a confirmation message and
        // return immediately
        if (hasSuccessfulReservationInResults) {
            log.info("Reservation created successfully, generating confirmation message and returning immediately");
            // Extract reservation details from the tool result
            String confirmationMessage = "✅ Réservation créée avec succès !";
            for (Map<String, Object> toolResult : toolResults) {
                String functionName = (String) toolResult.get("function_name");
                if ("create_reservation".equals(functionName)) {
                    String result = (String) toolResult.get("result");
                    if (result != null && result.contains("Réservation créée")) {
                        // Use the result from create_reservation as the confirmation message
                        confirmationMessage = result;
                        break;
                    }
                }
            }
            return Mono.just(confirmationMessage);
        }

        // Build updated messages list using the same logic as MatrixAssistantAIService
        List<Map<String, Object>> messages = new ArrayList<>();
        // Add previous messages (clean timestamps if present, but keep tool_calls for
        // assistant messages)
        for (Map<String, Object> prevMsg : previousMessages) {
            Map<String, Object> cleanMsg = new HashMap<>();
            cleanMsg.put("role", prevMsg.get("role"));
            cleanMsg.put("content", prevMsg.get("content"));
            // Keep tool_calls for assistant messages (needed for Mistral API format)
            if ("assistant".equals(prevMsg.get("role")) && prevMsg.containsKey("tool_calls")) {
                cleanMsg.put("tool_calls", prevMsg.get("tool_calls"));
            }
            // Keep tool_call_id for tool messages (needed for Mistral API format)
            if ("tool".equals(prevMsg.get("role")) && prevMsg.containsKey("tool_call_id")) {
                cleanMsg.put("tool_call_id", prevMsg.get("tool_call_id"));
            }
            // Remove timestamps (they're only for filtering, not for Mistral API)
            messages.add(cleanMsg);
        }

        // Verify that we have the correct number of tool responses
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCallsInMessage = (List<Map<String, Object>>) assistantMessage.get("tool_calls");
        int expectedToolResponses = toolCallsInMessage != null ? toolCallsInMessage.size() : 0;

        if (expectedToolResponses != toolResults.size()) {
            log.error("⚠️ MISMATCH: Assistant message has {} tool_calls but we have {} tool results! [iteration={}]",
                    expectedToolResponses, toolResults.size(), iteration);
            return Mono.error(new RuntimeException(
                    String.format("Mismatch between tool_calls (%d) and tool results (%d) in iteration %d",
                            expectedToolResponses, toolResults.size(), iteration)));
        }

        // For iteration 0, add assistant message and tool results
        if (iteration == 0) {
            messages.add(assistantMessage);
            // Add ALL tool result messages immediately after the assistant message
            for (Map<String, Object> toolResult : toolResults) {
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolResult.get("tool_call_id"));
                toolMsg.put("content", toolResult.get("result"));
                messages.add(toolMsg);
            }
        } else {
            // For iteration > 0, check if assistant message is already in messages
            boolean assistantMessageAlreadyInMessages = false;
            for (Map<String, Object> msg : messages) {
                if (msg == assistantMessage || (msg.equals(assistantMessage) && "assistant".equals(msg.get("role")))) {
                    assistantMessageAlreadyInMessages = true;
                    break;
                }
            }

            if (!assistantMessageAlreadyInMessages) {
                messages.add(assistantMessage);
            }

            // Add tool results immediately after assistant message
            for (Map<String, Object> toolResult : toolResults) {
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolResult.get("tool_call_id"));
                String toolContent = (String) toolResult.get("result");
                toolMsg.put("content", toolContent);
                messages.add(toolMsg);

                // Check if this is a successful create_reservation call
                if ("create_reservation".equals(toolResult.get("function_name")) && toolContent != null) {
                    String lowerContent = toolContent.toLowerCase();
                    if (lowerContent.contains("réservation créée") ||
                            lowerContent.contains("reservation created") ||
                            lowerContent.contains("créée avec succès") ||
                            lowerContent.contains("created successfully")) {
                        log.info("Detected successful create_reservation in tool result, will force final response");
                    }
                }
            }
        }

        // Verify that the current iteration's tool_calls match tool results
        // (Don't verify entire history - Mistral API allows multiple assistant->tool
        // sequences)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> currentToolCalls = (List<Map<String, Object>>) assistantMessage.get("tool_calls");
        int currentToolCallsCount = currentToolCalls != null ? currentToolCalls.size() : 0;
        int currentToolResultsCount = toolResults.size();

        if (currentToolCallsCount != currentToolResultsCount) {
            log.error(
                    "⚠️ CRITICAL: Current iteration tool_calls ({}) != tool results ({})! [iteration={}]",
                    currentToolCallsCount, currentToolResultsCount, iteration);
            return Mono.error(new RuntimeException(
                    String.format("Current iteration tool_calls (%d) != tool results (%d) at iteration %d",
                            currentToolCallsCount, currentToolResultsCount, iteration)));
        }

        // Optional: Log total counts for debugging (but don't fail on mismatch)
        int totalToolCalls = 0;
        int totalToolResults = 0;
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            if ("assistant".equals(role)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tc = (List<Map<String, Object>>) msg.get("tool_calls");
                if (tc != null) {
                    totalToolCalls += tc.size();
                }
            } else if ("tool".equals(role)) {
                totalToolResults++;
            }
        }
        log.debug("Message history: {} total tool_calls, {} total tool results [iteration={}]",
                totalToolCalls, totalToolResults, iteration);

        // Call Mistral again with tool results
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
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        if (!tools.isEmpty()) {
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto");
        }
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        return Mono.just("Aucune réponse générée.");
                    }

                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> nextMessage = (Map<String, Object>) choice.get("message");
                    String content = (String) nextMessage.get("content");
                    List<Map<String, Object>> nextToolCalls = (List<Map<String, Object>>) nextMessage.get("tool_calls");

                    // Check if previous tool calls included a successful create_reservation
                    // If so, force a final response instead of allowing more tool calls
                    boolean hasSuccessfulReservation = false;
                    log.info("Checking {} messages for successful reservation creation at iteration {}",
                            messages.size(), iteration);
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Map<String, Object> msg = messages.get(i);
                        String role = (String) msg.get("role");
                        log.info("Message {}: role={}", i, role);
                        if ("tool".equals(role)) {
                            String toolContent = (String) msg.get("content");
                            log.info("Found tool message at index {}: content preview = {}", i,
                                    toolContent != null ? toolContent.substring(0, Math.min(100, toolContent.length()))
                                            : "null");
                            if (toolContent != null) {
                                String lowerContent = toolContent.toLowerCase();
                                if (lowerContent.contains("réservation créée") ||
                                        lowerContent.contains("reservation created") ||
                                        lowerContent.contains("créée avec succès") ||
                                        lowerContent.contains("created successfully") ||
                                        (lowerContent.contains("réservé") && !lowerContent.contains("erreur")) ||
                                        (lowerContent.contains("reserved") && !lowerContent.contains("error"))) {
                                    hasSuccessfulReservation = true;
                                    log.info(
                                            "✅ Detected successful reservation creation in tool results (content: {}), forcing final response",
                                            toolContent.substring(0, Math.min(100, toolContent.length())));
                                    break;
                                }
                            }
                        }
                    }
                    if (!hasSuccessfulReservation) {
                        log.info("❌ No successful reservation creation detected in {} messages at iteration {}",
                                messages.size(), iteration);
                    }

                    if (hasSuccessfulReservation) {
                        // Reservation was created successfully - force final response even if Mistral
                        // wants more tool calls
                        log.info(
                                "Reservation created successfully, forcing final response without more tool calls (iteration {})",
                                iteration);
                        if (content == null || content.trim().isEmpty()) {
                            // Generate a confirmation message if Mistral didn't provide one
                            content = "✅ Réservation créée avec succès !";
                        }
                        // Return immediately without allowing more tool calls
                        return Mono.just(content.trim());
                    }

                    if (nextToolCalls != null && !nextToolCalls.isEmpty()) {
                        log.info("Agent {} requested {} more tool call(s) at iteration {}", getClass().getSimpleName(),
                                nextToolCalls.size(), iteration);
                        // More tool calls needed
                        return callMistralWithToolResultsRecursive(
                                messages, nextMessage, nextToolCalls, authContext, ragContext, tools, iteration + 1);
                    }

                    log.debug("Agent {} at iteration {}: no more tool calls, content length: {}",
                            getClass().getSimpleName(), iteration, content != null ? content.length() : 0);

                    // Final response
                    if (content == null || content.trim().isEmpty()) {
                        log.warn("Empty content in final response for agent {} at iteration {}",
                                getClass().getSimpleName(), iteration);
                        return Mono.just("Je n'ai pas pu générer de réponse. Pouvez-vous reformuler votre question ?");
                    }
                    log.info("Agent {} returning final response (iteration {}): {}", getClass().getSimpleName(),
                            iteration,
                            content.length() > 200 ? content.substring(0, 200) + "..." : content);
                    return Mono.just(content.trim());
                });
    }

    /**
     * Whether this agent should use RAG
     */
    protected boolean shouldUseRAG() {
        return true; // Override in subclasses if needed
    }

    /**
     * Loads a prompt file
     */
    protected String loadPromptFile(String promptFile, String defaultPrompt) {
        if (promptFile == null || promptFile.isEmpty() || "null".equals(promptFile)) {
            log.warn("Prompt file is null or empty, using default prompt");
            return defaultPrompt;
        }

        try {
            Resource resource = resourceLoader.getResource(promptFile);
            if (resource.exists() && resource.isReadable()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    String prompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    log.debug("Loaded prompt from {}", promptFile);
                    return prompt;
                }
            } else {
                log.warn("Prompt file not found or not readable: {}, using default", promptFile);
                return defaultPrompt;
            }
        } catch (Exception e) {
            log.error("Error loading prompt from {}: {}", promptFile, e.getMessage(), e);
            return defaultPrompt;
        }
    }

    /**
     * Adds current date information to a prompt
     */
    protected String addDateInformation(String prompt) {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("📅 **CURRENT DATE INFORMATION:**\n");
        promptBuilder.append("- Today's date: ").append(todayStr).append(" (")
                .append(today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))).append(")\n");
        promptBuilder.append("- Tomorrow's date: ").append(tomorrowStr).append(" (")
                .append(today.plusDays(1).format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)))
                .append(")\n");
        promptBuilder.append("- When the user says 'demain' or 'tomorrow', it means: ").append(tomorrowStr)
                .append("\n");
        promptBuilder.append("- When the user says 'aujourd'hui' or 'today', it means: ").append(todayStr).append("\n");
        promptBuilder.append("\n");
        promptBuilder.append(prompt);

        return promptBuilder.toString();
    }

    /**
     * Hook method for specialized agents to process tool results and update
     * context.
     * Called after tool execution but before passing results to Mistral.
     * 
     * @param toolCalls   List of tool calls that were executed
     * @param toolResults List of tool results (same order as toolCalls)
     * @param authContext Authentication context
     */
    @SuppressWarnings("unchecked")
    protected void processToolResults(
            List<Map<String, Object>> toolCalls,
            List<Map<String, Object>> toolResults,
            MatrixAssistantAuthContext authContext) {
        // Default implementation: do nothing
        // Specialized agents can override this to extract information from tool results
        // and update their workflow context
    }

    private Map<String, Object> sanitizeToolArguments(
            Map<String, Object> args,
            MatrixAssistantAuthContext authContext) {
        if (args == null || args.isEmpty()) {
            return args;
        }

        Map<String, Object> sanitized = new HashMap<>(args);
        // Normalize spaceId-like arguments
        resolveSpaceIdAndReplace("spaceId", sanitized, authContext);
        resolveSpaceIdAndReplace("space_id", sanitized, authContext);

        return sanitized;
    }

    private boolean isValidUUID(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves a spaceId argument: if not a valid UUID, attempts to find it by
     * number/name via list_spaces and replaces with the UUID.
     */
    private void resolveSpaceIdAndReplace(String key, Map<String, Object> args,
            MatrixAssistantAuthContext authContext) {
        Object value = args.get(key);
        if (!(value instanceof String)) {
            return;
        }
        String candidate = ((String) value).trim();
        if (isValidUUID(candidate)) {
            return; // already good
        }

        // Try context first
        String roomId = authContext != null ? authContext.getRoomId() : null;
        if (roomId != null) {
            MatrixAssistantAgentContextService.AgentContext ctx = agentContextService.getContext(roomId);
            if (ctx != null) {
                String ctxSpaceId = ctx.getWorkflowStateValue("spaceId", String.class);
                if (isValidUUID(ctxSpaceId)) {
                    args.put(key, ctxSpaceId);
                    return;
                }
            }
        }

        // Try to resolve via list_spaces (synchronous call)
        try {
            String resolved = resolveSpaceIdViaListSpaces(candidate, authContext);
            if (resolved != null) {
                args.put(key, resolved);
            }
        } catch (Exception e) {
            log.warn("Could not resolve spaceId '{}' via list_spaces: {}", candidate, e.getMessage());
        }
    }

    /**
     * Calls list_spaces and attempts to find a matching space by number or name.
     */
    private String resolveSpaceIdViaListSpaces(String candidate, MatrixAssistantAuthContext authContext) {
        Map<String, Object> listArgs = Map.of();
        MatrixMCPModels.MCPToolResult toolResult = mcpAdapter.callMCPToolDirect("list_spaces", listArgs, authContext);
        String text = toolResult.getContent().stream()
                .map(MatrixMCPModels.MCPContent::getText)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));
        if (text.isEmpty()) {
            return null;
        }

        // Extract name -> id mapping
        Map<String, String> nameToId = new HashMap<>();
        Pattern idPattern = Pattern.compile("ID\\s*:\\s*([0-9a-fA-F-]{36})");
        String[] lines = text.split("\\r?\\n");
        String currentName = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("**")) {
                // Name line like "**Place de parking N°7**"
                currentName = trimmed.replace("*", "").trim();
            }
            Matcher m = idPattern.matcher(trimmed);
            if (m.find() && currentName != null) {
                nameToId.put(currentName.toLowerCase(), m.group(1));
                currentName = null;
            }
        }

        // Match by number if candidate is numeric
        String candidateLower = candidate.toLowerCase();
        boolean isNumeric = candidateLower.chars().allMatch(Character::isDigit);
        if (isNumeric) {
            for (Map.Entry<String, String> entry : nameToId.entrySet()) {
                if (entry.getKey().contains(candidateLower)) {
                    return entry.getValue();
                }
            }
        }

        // Match by name contains
        for (Map.Entry<String, String> entry : nameToId.entrySet()) {
            if (entry.getKey().contains(candidateLower)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Logs the complete Mistral API request in a readable format
     */
    @SuppressWarnings("unchecked")
    private void logMistralRequest(String context, Map<String, Object> requestBody,
            List<Map<String, Object>> messages, Map<String, String> mdcContext) {
        logMistralRequest(context, requestBody, messages, mdcContext, null, null);
    }

    /**
     * Logs the complete Mistral API request in a readable format with
     * agent/conversation info
     */
    @SuppressWarnings("unchecked")
    protected void logMistralRequest(String context, Map<String, Object> requestBody,
            List<Map<String, Object>> messages, Map<String, String> mdcContext, String agentId, String conversationId) {
        try {
            withMdc(mdcContext, () -> {
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("🔵 MISTRAL REQUEST [{}] - {}", context, getClass().getSimpleName());
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                // Log agent and conversation IDs if available
                if (agentId != null && !agentId.isEmpty()) {
                    log.info("🤖 Agent ID: {}", agentId);
                }
                if (conversationId != null && !conversationId.isEmpty()) {
                    log.info("💬 Conversation ID: {}", conversationId);
                }

                // Log model and parameters
                log.info("📋 Model: {}", requestBody.get("model"));
                log.info("📋 Temperature: {}", requestBody.get("temperature"));
                log.info("📋 Max tokens: {}", requestBody.get("max_tokens"));
                if (requestBody.containsKey("tools")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tools = (List<Map<String, Object>>) requestBody.get("tools");
                    log.info("📋 Tools: {} tool(s) available", tools != null ? tools.size() : 0);
                    if (tools != null && !tools.isEmpty()) {
                        for (Map<String, Object> tool : tools) {
                            Map<String, Object> function = (Map<String, Object>) tool.get("function");
                            if (function != null) {
                                log.info("   └─ Tool: {}", function.get("name"));
                            }
                        }
                    }
                }
                if (requestBody.containsKey("response_format")) {
                    log.info("📋 Response format: {}", requestBody.get("response_format"));
                }
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

                    // Extract and log locale if present (assistant message with language_iso)
                    if ("assistant".equals(role) && content != null) {
                        try {
                            // Try to parse JSON content for locale
                            if (content.trim().startsWith("{")) {
                                Map<String, Object> contentObj = objectMapper.readValue(content,
                                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class,
                                                Object.class));
                                String locale = (String) contentObj.get("language_iso");
                                if (locale != null && !locale.isEmpty()) {
                                    log.info("   └─ Locale: {}", locale);
                                }
                            }
                        } catch (Exception e) {
                            // Not JSON, ignore
                        }
                    }

                    if (content != null && !content.isEmpty()) {
                        // Truncate very long content for readability
                        String displayContent = content.length() > 2000
                                ? content.substring(0, 2000) + "\n... [TRUNCATED - " + (content.length() - 2000)
                                        + " more characters]"
                                : content;
                        log.info("Content:\n{}", displayContent);
                    }

                    // Log tool calls if present
                    if (msg.containsKey("tool_calls")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
                        log.info("Tool calls: {} call(s)", toolCalls != null ? toolCalls.size() : 0);
                        if (toolCalls != null) {
                            for (Map<String, Object> toolCall : toolCalls) {
                                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                                if (function != null) {
                                    log.info("   └─ Function: {} with args: {}",
                                            function.get("name"),
                                            function.get("arguments"));
                                }
                            }
                        }
                    }

                    // Log tool results if present (truncated for readability)
                    if (msg.containsKey("tool_call_id")) {
                        log.info("Tool result for call_id: {}", msg.get("tool_call_id"));
                        if (content != null) {
                            // For list_spaces results, extract just the numbers for readability
                            if (content.contains("Place de parking N°") || content.contains("**Place de parking")) {
                                String numbers = extractParkingNumbers(content);
                                if (numbers != null && !numbers.isEmpty()) {
                                    log.info("Result (parking numbers): {}", numbers);
                                } else {
                                    String displayContent = content.length() > 300
                                            ? content.substring(0, 300) + "\n... [TRUNCATED]"
                                            : content;
                                    log.info("Result: {}", displayContent);
                                }
                            } else {
                                String displayContent = content.length() > 500
                                        ? content.substring(0, 500) + "\n... [TRUNCATED]"
                                        : content;
                                log.info("Result: {}", displayContent);
                            }
                        }
                    }

                    log.info("");
                }

                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
        } catch (Exception e) {
            log.warn("Error logging Mistral request: {}", e.getMessage(), e);
        }
    }

    /**
     * Logs the complete Mistral API response in a readable format
     */
    @SuppressWarnings("unchecked")
    private void logMistralResponse(String context, Map<String, Object> response, Map<String, String> mdcContext) {
        logMistralResponse(context, response, mdcContext, null, null);
    }

    /**
     * Logs the complete Mistral API response in a readable format with
     * agent/conversation info
     */
    @SuppressWarnings("unchecked")
    protected void logMistralResponse(String context, Map<String, Object> response, Map<String, String> mdcContext,
            String agentId, String conversationId) {
        try {
            withMdc(mdcContext, () -> {
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("🟢 MISTRAL RESPONSE [{}] - {}", context, getClass().getSimpleName());
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                // Log agent and conversation IDs if available
                if (agentId != null && !agentId.isEmpty()) {
                    log.info("🤖 Agent ID: {}", agentId);
                }
                if (conversationId != null && !conversationId.isEmpty()) {
                    log.info("💬 Conversation ID: {}", conversationId);
                }

                // Log conversation ID from response if present (Conversations API)
                if (response.containsKey("id")) {
                    String responseConversationId = (String) response.get("id");
                    if (responseConversationId != null && !responseConversationId.isEmpty()) {
                        log.info("💬 Response Conversation ID: {}", responseConversationId);
                    }
                }

                // Log usage if present
                if (response.containsKey("usage")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                    log.info("📊 Usage:");
                    log.info("   └─ Prompt tokens: {}", usage.get("prompt_tokens"));
                    log.info("   └─ Completion tokens: {}", usage.get("completion_tokens"));
                    log.info("   └─ Total tokens: {}", usage.get("total_tokens"));
                    log.info("");
                }

                // Log entries if present (Conversations API format)
                if (response.containsKey("entries")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
                    log.info("📝 Entries: {} entry/entries", entries != null ? entries.size() : 0);
                    if (entries != null && !entries.isEmpty()) {
                        for (int i = 0; i < entries.size(); i++) {
                            Map<String, Object> entry = entries.get(i);
                            String entryType = (String) entry.get("type");
                            log.info("   └─ Entry {}: type={}, id={}", i + 1, entryType, entry.get("id"));

                            // Log tool.execution entries
                            if ("tool.execution".equals(entryType)) {
                                String toolName = (String) entry.get("name");
                                log.info("      └─ Tool: {}", toolName);
                                if (entry.containsKey("created_at")) {
                                    log.info("      └─ Created at: {}", entry.get("created_at"));
                                }
                                if (entry.containsKey("completed_at")) {
                                    log.info("      └─ Completed at: {}", entry.get("completed_at"));
                                }
                            }

                            // Log message.output entries with tool_reference chunks
                            if ("message.output".equals(entryType)) {
                                List<Map<String, Object>> content = (List<Map<String, Object>>) entry.get("content");
                                if (content != null) {
                                    for (Map<String, Object> chunk : content) {
                                        String chunkType = (String) chunk.get("type");
                                        if ("tool_reference".equals(chunkType)) {
                                            Map<String, Object> toolRef = (Map<String, Object>) chunk
                                                    .get("tool_reference");
                                            if (toolRef != null) {
                                                log.info("      └─ Tool reference: name={}, output={}",
                                                        toolRef.get("name"), toolRef.get("output"));
                                            }
                                        } else if ("text".equals(chunkType)) {
                                            String text = (String) chunk.get("text");
                                            if (text != null && !text.isEmpty()) {
                                                String displayText = text.length() > 500
                                                        ? text.substring(0, 500) + "\n... [TRUNCATED]"
                                                        : text;
                                                log.info("      └─ Text: {}", displayText);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        log.info("");
                    }
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
                            // Truncate very long content for readability
                            String displayContent = content.length() > 2000
                                    ? content.substring(0, 2000) + "\n... [TRUNCATED - " + (content.length() - 2000)
                                            + " more characters]"
                                    : content;
                            log.info("   └─ Content:\n{}", displayContent);
                        } else {
                            log.info("   └─ Content: [EMPTY]");
                        }

                        // Log tool calls if present
                        if (message.containsKey("tool_calls")) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                            log.info("   └─ Tool calls: {} call(s)", toolCalls != null ? toolCalls.size() : 0);
                            if (toolCalls != null) {
                                for (int j = 0; j < toolCalls.size(); j++) {
                                    Map<String, Object> toolCall = toolCalls.get(j);
                                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                                    if (function != null) {
                                        log.info("      └─ [{}] Function: {}", j + 1, function.get("name"));
                                        String args = (String) function.get("arguments");
                                        if (args != null) {
                                            String displayArgs = args.length() > 1000
                                                    ? args.substring(0, 1000) + "\n... [TRUNCATED]"
                                                    : args;
                                            log.info("         Arguments: {}", displayArgs);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    log.info("");
                }

                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
        } catch (Exception e) {
            log.warn("Error logging Mistral response: {}", e.getMessage(), e);
        }
    }

    /**
     * Extract parking numbers from list_spaces tool result for concise logging
     */
    private String extractParkingNumbers(String content) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("N°(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(content);
            java.util.List<String> numbers = new java.util.ArrayList<>();
            while (matcher.find()) {
                numbers.add(matcher.group(1));
            }
            if (!numbers.isEmpty()) {
                return "N°" + String.join(", N°", numbers);
            }
        } catch (Exception e) {
            // Ignore extraction errors
        }
        return null;
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
