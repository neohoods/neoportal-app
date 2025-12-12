package com.neohoods.portal.platform.assistant.workflows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPReservationHandler;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.ReservationPeriod;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.model.WorkflowType;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.space.SpaceStateMachine;
import com.neohoods.portal.platform.assistant.workflows.space.steps.SpaceStepHandler;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;
import com.neohoods.portal.platform.spaces.services.StripeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Agent specialized in space reservations.
 * Implements a 3-step workflow:
 * 1. Identify the space (get spaceId UUID)
 * 2. Choose the period (get dates and times)
 * 3. Create the reservation
 * 
 * Uses agent context to persist state between steps.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantSpaceAgent extends BaseMatrixAssistantAgent {

    @Value("${neohoods.portal.matrix.assistant.debug.context:false}")
    private boolean debugContextAppend;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/space/matrix-assistant-reservation-agent-prompt.txt}")
    private String reservationAgentPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/space/matrix-assistant-reservation-agent-request-space-info.txt}")
    private String spaceInfoPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/space/matrix-assistant-reservation-agent-choose-space.txt}")
    private String chooseSpacePromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/space/matrix-assistant-reservation-agent-choose-period.txt}")
    private String choosePeriodPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/space/matrix-assistant-reservation-agent-confirm-summary.txt}")
    private String confirmSummaryPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/space/matrix-assistant-reservation-agent-complete-reservation.txt}")
    private String completeReservationPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/space/matrix-assistant-reservation-agent-step6-payment-instructions.txt}")
    private String paymentInstructionsPromptFile;

    private String baseSystemPrompt;
    private String spaceInfoPrompt;
    private String chooseSpacePrompt;
    private String confirmSummaryPrompt;
    private String completeReservationPrompt;
    private String paymentInstructionsPrompt;

    private final MatrixMCPReservationHandler reservationHandler;
    private final SpacesService spacesService;
    private final ReservationsService reservationsService;
    private final StripeService stripeService;
    private final ReservationRepository reservationRepository;
    private final UsersRepository usersRepository;
    private final MessageSource messageSource;
    private final SpaceStateMachine stateMachine;

    @PersistenceContext
    private EntityManager entityManager;

    // Step handlers - injected via constructor
    private final Map<SpaceStep, SpaceStepHandler> stepHandlers;

    public MatrixAssistantSpaceAgent(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            MatrixAssistantMCPAdapter mcpAdapter,
            ResourceLoader resourceLoader,
            MatrixAssistantAgentContextService agentContextService,
            MatrixMCPReservationHandler reservationHandler,
            SpacesService spacesService,
            ReservationsService reservationsService,
            StripeService stripeService,
            ReservationRepository reservationRepository,
            UsersRepository usersRepository,
            MessageSource messageSource,
            List<SpaceStepHandler> stepHandlers,
            SpaceStateMachine stateMachine) {
        super(webClientBuilder, objectMapper, mcpAdapter, resourceLoader, agentContextService);
        this.reservationHandler = reservationHandler;
        this.spacesService = spacesService;
        this.reservationsService = reservationsService;
        this.stripeService = stripeService;
        this.reservationRepository = reservationRepository;
        this.usersRepository = usersRepository;
        this.messageSource = messageSource;
        this.stateMachine = stateMachine;

        // Initialize step handlers map
        this.stepHandlers = new HashMap<>();
        if (stepHandlers != null) {
            for (SpaceStepHandler handler : stepHandlers) {
                this.stepHandlers.put(handler.getStep(), handler);
                // Note: spaceAgent is injected via @Lazy @Autowired in BaseSpaceStepHandler
                // No need to manually set it here anymore
            }
        }

        loadSystemPrompt();
    }

    private void loadSystemPrompt() {
        baseSystemPrompt = loadPromptFile(reservationAgentPromptFile,
                "You are Alfred, the AI assistant for NeoHoods. You handle space reservations.");
        spaceInfoPrompt = loadPromptFile(spaceInfoPromptFile, "Request space info");
        chooseSpacePrompt = loadPromptFile(chooseSpacePromptFile, "Choose space");
        confirmSummaryPrompt = loadPromptFile(confirmSummaryPromptFile, "Confirm summary");
        completeReservationPrompt = loadPromptFile(completeReservationPromptFile, "Complete reservation");
        paymentInstructionsPrompt = loadPromptFile(paymentInstructionsPromptFile, "Payment instructions");
        // Step 7 is handled by payment webhook in background, no prompt needed
    }

    /**
     * Public wrapper for step handlers to call Mistral API
     * Uses Conversations API with step-specific agents if available, otherwise
     * falls back to Chat Completions
     */
    public Mono<SpaceStepResponse> callMistralAPIWithJSONResponseForStep(
            String stepLabel,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext) {
        String label = stepLabel != null ? "step=" + stepLabel : null;

        // Try to use Conversations API with step-specific agent
        if (mistralAgentsService != null && mistralConversationsService != null && stepLabel != null) {
            try {
                SpaceStep step = SpaceStep.valueOf(stepLabel);
                String agentName = com.neohoods.portal.platform.assistant.services.MistralAgentsService
                        .getAgentNameForStep(step);
                String agentId = mistralAgentsService.getAgentId(agentName);
                if (agentId != null && !agentId.isEmpty()) {
                    log.debug("Using Conversations API with agent {} for step {}", agentName, stepLabel);
                    return callMistralAPIWithJSONResponseUsingConversations(
                            label, userMessage, conversationHistory, systemPrompt, tools, authContext, agentId, step);
                }
            } catch (IllegalArgumentException e) {
                log.debug("Invalid step label {}, falling back to Chat Completions", stepLabel);
            }
        }

        // Fallback to Chat Completions API
        return callMistralAPIWithJSONResponse(label, userMessage, conversationHistory, systemPrompt, tools,
                authContext);
    }

    /**
     * Calls Mistral API using Conversations API with a step-specific agent
     */
    private Mono<SpaceStepResponse> callMistralAPIWithJSONResponseUsingConversations(
            String contextLabel,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext,
            String agentId,
            SpaceStep step) {

        String roomId = authContext.getRoomId();
        String logLabel = contextLabel != null ? contextLabel : "step=" + step.name();

        // Build inputs: include conversation history + current message
        List<Map<String, Object>> inputs = new ArrayList<>();
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (Map<String, Object> histMsg : conversationHistory) {
                Map<String, Object> cleanMsg = new HashMap<>();
                cleanMsg.put("role", histMsg.get("role"));
                cleanMsg.put("content", histMsg.get("content"));
                inputs.add(cleanMsg);
            }
        }
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        inputs.add(userMsg);

        // Check if we should create a new conversation or append
        boolean shouldCreateNew = mistralConversationsService.shouldCreateNewConversation(roomId, agentId);

        Mono<Map<String, Object>> responseMono;
        if (shouldCreateNew) {
            log.debug("Starting new conversation for step {} with agent {}", step, agentId);
            responseMono = mistralConversationsService.startConversation(roomId, agentId, inputs, true)
                    .flatMap(conversationId -> {
                        // Update context
                        if (roomId != null) {
                            MatrixAssistantAgentContextService.AgentContext context = agentContextService
                                    .getOrCreateContext(roomId);
                            context.setMistralConversationId(conversationId);
                            context.updateLastInteractionTime();
                        }
                        // Retrieve conversation to get entries
                        return retrieveConversationForStep(conversationId, agentId);
                    });
        } else {
            log.debug("Appending to existing conversation for step {} with agent {}", step, agentId);
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
                        return retrieveConversationForStep(conversationId, agentId);
                    });
        }

        return responseMono
                .flatMap(response -> parseStepResponseFromConversation(response, authContext, tools, step))
                .onErrorResume(e -> {
                    log.warn("Failed to use Conversations API for step {}, falling back to Chat Completions: {}",
                            step, e.getMessage());
                    return callMistralAPIWithJSONResponse(logLabel, userMessage, conversationHistory, systemPrompt,
                            tools,
                            authContext);
                });
    }

    /**
     * Retrieves a conversation by ID for step processing
     */
    private Mono<Map<String, Object>> retrieveConversationForStep(String conversationId, String agentId) {
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
                    logMistralResponse("Step conversation", typedResponse, mdcContext, agentId, conversationId);
                    return typedResponse;
                });
    }

    /**
     * Parses SpaceStepResponse from conversation entries
     */
    @SuppressWarnings("unchecked")
    private Mono<SpaceStepResponse> parseStepResponseFromConversation(
            Map<String, Object> conversationResponse,
            MatrixAssistantAuthContext authContext,
            List<Map<String, Object>> tools,
            SpaceStep step) {

        List<Map<String, Object>> entries = (List<Map<String, Object>>) conversationResponse.get("entries");
        if (entries == null || entries.isEmpty()) {
            log.warn("No entries in conversation response for step {}", step);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Erreur lors du traitement de votre demande.")
                    .build());
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
                                if ("submit_reservation_step".equals(toolName)) {
                                    Map<String, Object> output = (Map<String, Object>) toolRef.get("output");
                                    if (output != null) {
                                        return Mono.just(parseStepResponseFromToolOutput(output));
                                    }
                                }
                            }
                        } else if ("text".equals(chunkType)) {
                            // Try to parse JSON from text content
                            String text = (String) chunk.get("text");
                            if (text != null && text.trim().startsWith("{")) {
                                try {
                                    Map<String, Object> jsonResponse = objectMapper.readValue(text,
                                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class,
                                                    Object.class));
                                    return Mono.just(parseStepResponseFromToolOutput(jsonResponse));
                                } catch (Exception e) {
                                    log.debug("Could not parse JSON from text chunk: {}", e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fallback: try to process tool executions if any
        return processToolExecutionsFromConversation(entries, authContext, tools, step);
    }

    /**
     * Parses SpaceStepResponse from tool output map
     */
    @SuppressWarnings("unchecked")
    private SpaceStepResponse parseStepResponseFromToolOutput(Map<String, Object> output) {
        SpaceStepResponse.SpaceStepResponseBuilder builder = SpaceStepResponse.builder();

        String statusStr = (String) output.get("status");
        if (statusStr != null) {
            try {
                builder.status(SpaceStepResponse.StepStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status '{}' in tool output", statusStr);
                builder.status(SpaceStepResponse.StepStatus.ASK_USER);
            }
        }

        builder.response((String) output.get("response"));
        builder.spaceId((String) output.get("spaceId"));

        Map<String, Object> periodMap = (Map<String, Object>) output.get("period");
        if (periodMap != null) {
            ReservationPeriod period = ReservationPeriod.builder()
                    .startDate((String) periodMap.get("startDate"))
                    .endDate((String) periodMap.get("endDate"))
                    .startTime((String) periodMap.get("startTime"))
                    .endTime((String) periodMap.get("endTime"))
                    .build();
            builder.period(period);
        }

        String nextStepStr = (String) output.get("nextStep");
        if (nextStepStr != null) {
            try {
                builder.nextStep(SpaceStep.valueOf(nextStepStr));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid nextStep '{}' in tool output", nextStepStr);
            }
        }

        Map<String, String> availableSpaces = (Map<String, String>) output.get("availableSpaces");
        if (availableSpaces != null) {
            builder.availableSpaces(availableSpaces);
        }

        return builder.build();
    }

    /**
     * Processes tool executions from conversation entries (for tool calls that need
     * execution)
     */
    @SuppressWarnings("unchecked")
    private Mono<SpaceStepResponse> processToolExecutionsFromConversation(
            List<Map<String, Object>> entries,
            MatrixAssistantAuthContext authContext,
            List<Map<String, Object>> tools,
            SpaceStep step) {

        // Find tool.execution entries and execute them
        List<Map<String, Object>> toolExecutions = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            String entryType = (String) entry.get("type");
            if ("tool.execution".equals(entryType)) {
                toolExecutions.add(entry);
            }
        }

        if (toolExecutions.isEmpty()) {
            // No tool executions, return asking user response
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ASK_USER)
                    .response("En attente de votre réponse...")
                    .build());
        }

        // Execute tools and continue conversation
        // This is complex - for now, fallback to Chat Completions
        log.debug("Found {} tool executions in conversation, falling back to Chat Completions for processing",
                toolExecutions.size());
        return Mono.error(new RuntimeException("Tool executions need processing - fallback to Chat Completions"));
    }

    /**
     * Public wrapper for step handlers to build system prompt
     */
    public String buildSystemPromptForStepPublic(
            SpaceStep step,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {
        return buildSystemPromptForStep(step, context, authContext);
    }

    @Override
    public Mono<String> handleMessage(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAuthContext authContext) {

        log.info("Reservation agent handling message: {} (user: {})",
                userMessage.substring(0, Math.min(50, userMessage.length())),
                authContext.getMatrixUserId());

        String roomId = authContext.getRoomId();
        MatrixAssistantAgentContextService.AgentContext context = null;
        if (roomId != null) {
            context = agentContextService.getOrCreateContext(roomId);
            context.setCurrentWorkflow(WorkflowType.SPACE);
        }

        // Determine current step based on context
        SpaceStep currentStep = determineCurrentStep(context);

        // Log current step for debugging
        log.info("🔄 Current space step: {} (user: {})", currentStep, authContext.getMatrixUserId());

        // Update context with current step
        if (context != null) {
            context.updateWorkflowState("reservationStep", currentStep.name());
        }

        // Use handler - all steps should have handlers
        SpaceStepHandler handler = stepHandlers.get(currentStep);
        if (handler == null) {
            log.error("No handler found for step {}, this should not happen", currentStep);
            return Mono.just("Erreur: étape non gérée.");
        }
        return processWithHandler(handler, currentStep, userMessage, conversationHistory, context, authContext);
    }

    /**
     * Process message with handler and implement SWITCH_STEP loop
     * Delegates to SpaceStateMachine for state management
     */
    private Mono<String> processWithHandler(
            SpaceStepHandler handler,
            SpaceStep currentStep,
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        return stateMachine.processStepWithSwitchLoop(
                handler, currentStep, userMessage, conversationHistory, context, authContext, stepHandlers)
                .map(stepResponse -> {
                    // Format response for user
                    String baseResponse = stepResponse.getResponse();
                    if (baseResponse == null || baseResponse.isEmpty()) {
                        throw new CodedException("MATRIX_ASSISTANT_EMPTY_RESPONSE",
                                "Generated response is empty");
                    }

                    if (debugContextAppend && context != null) {
                        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
                        String startDate = context.getWorkflowStateValue("startDate", String.class);
                        String endDate = context.getWorkflowStateValue("endDate", String.class);
                        String startTime = context.getWorkflowStateValue("startTime", String.class);
                        String endTime = context.getWorkflowStateValue("endTime", String.class);

                        // Get the actual current step from context (may have been updated by state
                        // machine)
                        // Fallback to the original currentStep if not found in context
                        String stepStr = context.getWorkflowStateValue("reservationStep", String.class);
                        SpaceStep actualStep = currentStep;
                        if (stepStr != null) {
                            try {
                                actualStep = SpaceStep.valueOf(stepStr);
                            } catch (IllegalArgumentException e) {
                                // Keep currentStep if invalid
                            }
                        }

                        // Format debug block with proper null handling
                        // Use HTML format for Matrix - <pre><code> tags will be preserved by
                        // convertMarkdownToMatrixHtml
                        String stateStr = actualStep != null ? actualStep.name() : "null";
                        String spaceIdStr = spaceId != null ? spaceId : "null";
                        String startDateStr = startDate != null ? startDate : "null";
                        String endDateStr = endDate != null ? endDate : "null";
                        String startTimeStr = startTime != null ? startTime : "null";
                        String endTimeStr = endTime != null ? endTime : "null";

                        // Format as HTML code block - escape only the content values to prevent
                        // injection
                        // The <pre><code> tags themselves will be preserved by
                        // convertMarkdownToMatrixHtml
                        String debugContent = String.format(
                                "[state=%s, spaceId=%s, startDate=%s, endDate=%s, startTime=%s, endTime=%s]",
                                stateStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"),
                                spaceIdStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"),
                                startDateStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"),
                                endDateStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"),
                                startTimeStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"),
                                endTimeStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
                        // Use HTML format - tags will be preserved by convertMarkdownToMatrixHtml
                        String debugBlock = "\n\n<pre><code>" + debugContent + "</code></pre>";
                        return baseResponse + debugBlock;
                    }
                    return baseResponse;
                });
    }

    // Note: processStepWithSwitchLoop logic has been moved to SpaceStateMachine
    // All state transitions are now handled by
    // SpaceStateMachine.processStepWithSwitchLoop()

    /**
     * Determines the current step based on context state
     */
    private SpaceStep determineCurrentStep(MatrixAssistantAgentContextService.AgentContext context) {
        if (context == null) {
            return SpaceStep.REQUEST_SPACE_INFO;
        }

        // Check if we have a stored step
        String stepStr = context.getWorkflowStateValue("reservationStep", String.class);
        if (stepStr != null) {
            try {
                SpaceStep storedStep = SpaceStep.valueOf(stepStr);
                // Validate that stored step is still valid based on collected data
                return validateAndUpdateStep(storedStep, context);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid reservation step in context: {}, resetting to REQUEST_SPACE_INFO", stepStr);
            }
        }

        // Determine step based on collected data
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        if (spaceId == null || spaceId.isEmpty()) {
            // After the initial info, move to CHOOSE_SPACE to let the user pick
            return SpaceStep.CHOOSE_SPACE;
        }

        // Check if we have both spaceId and period - if not, stay in CHOOSE_SPACE
        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
        if (startDateObj == null || endDateObj == null) {
            return SpaceStep.CHOOSE_SPACE;
        }

        // Check if reservation has been created
        Boolean reservationCreated = context.getWorkflowStateValue("reservationCreated", Boolean.class);
        if (reservationCreated != null && reservationCreated) {
            // Check if payment link has been generated
            Boolean paymentLinkGenerated = context.getWorkflowStateValue("paymentLinkGenerated", Boolean.class);
            if (paymentLinkGenerated != null && paymentLinkGenerated) {
                return SpaceStep.PAYMENT_INSTRUCTIONS;
            }
            return SpaceStep.COMPLETE_RESERVATION;
        }

        // Check if summary has been shown
        Boolean summaryShown = context.getWorkflowStateValue("summaryShown", Boolean.class);
        if (summaryShown != null && summaryShown) {
            return SpaceStep.COMPLETE_RESERVATION;
        }

        // We have all info but haven't shown summary yet
        return SpaceStep.CONFIRM_RESERVATION_SUMMARY;
    }

    /**
     * Validates that the stored step matches the actual state and updates if needed
     */
    private SpaceStep validateAndUpdateStep(SpaceStep storedStep,
            MatrixAssistantAgentContextService.AgentContext context) {
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        // Check for LocalDateTime first (preferred), then fallback to date strings
        Object startDateTimeObj = context.getWorkflowState().get("startDateTime");
        Object endDateTimeObj = context.getWorkflowState().get("endDateTime");
        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
        boolean hasPeriod = (startDateTimeObj != null && endDateTimeObj != null) ||
                (startDateObj != null && endDateObj != null);
        Boolean reservationCreated = context.getWorkflowStateValue("reservationCreated", Boolean.class);
        Boolean summaryShown = context.getWorkflowStateValue("summaryShown", Boolean.class);
        Boolean paymentLinkGenerated = context.getWorkflowStateValue("paymentLinkGenerated", Boolean.class);

        // Step 7: Payment confirmed (background, not managed here)
        if (storedStep == SpaceStep.PAYMENT_CONFIRMED) {
            return storedStep;
        }

        // Step 6: Payment instructions (after reservation created and payment link
        // generated)
        if (reservationCreated != null && reservationCreated &&
                paymentLinkGenerated != null && paymentLinkGenerated) {
            if (storedStep != SpaceStep.PAYMENT_INSTRUCTIONS) {
                log.info("Upgrading step from {} to PAYMENT_INSTRUCTIONS", storedStep);
                return SpaceStep.PAYMENT_INSTRUCTIONS;
            }
            return SpaceStep.PAYMENT_INSTRUCTIONS;
        }

        // Step 5: Complete reservation (after summary shown, reservation not yet
        // created)
        if (summaryShown != null && summaryShown &&
                (reservationCreated == null || !reservationCreated)) {
            if (storedStep != SpaceStep.COMPLETE_RESERVATION) {
                log.info("Upgrading step from {} to COMPLETE_RESERVATION", storedStep);
                return SpaceStep.COMPLETE_RESERVATION;
            }
            return SpaceStep.COMPLETE_RESERVATION;
        }

        // Step 4: Confirm reservation summary (all info collected, summary not shown)
        if (spaceId != null && hasPeriod &&
                (summaryShown == null || !summaryShown)) {
            if (storedStep != SpaceStep.CONFIRM_RESERVATION_SUMMARY) {
                log.info("Upgrading step from {} to CONFIRM_RESERVATION_SUMMARY (all info collected)", storedStep);
                return SpaceStep.CONFIRM_RESERVATION_SUMMARY;
            }
            return SpaceStep.CONFIRM_RESERVATION_SUMMARY;
        }

        // Step 2: Choose space and period (spaceId exists but dates may be missing)
        if (spaceId != null) {
            if (storedStep == SpaceStep.REQUEST_SPACE_INFO) {
                log.info("Upgrading step from REQUEST_SPACE_INFO to CHOOSE_SPACE (spaceId found)");
                return SpaceStep.CHOOSE_SPACE;
            }
            // If we're at CHOOSE_SPACE, check if we have both spaceId and period
            if (storedStep == SpaceStep.CHOOSE_SPACE) {
                if (hasPeriod) {
                    // Both spaceId and period are present, automatically move to
                    // CONFIRM_RESERVATION_SUMMARY
                    log.info(
                            "Upgrading step from CHOOSE_SPACE to CONFIRM_RESERVATION_SUMMARY (spaceId and period collected)");
                    return SpaceStep.CONFIRM_RESERVATION_SUMMARY;
                }
                return SpaceStep.CHOOSE_SPACE;
            }
            // If we're at CONFIRM_RESERVATION_SUMMARY or later but missing data, don't
            // downgrade
            // This prevents resetting when context is temporarily unavailable
            if (storedStep == SpaceStep.CONFIRM_RESERVATION_SUMMARY ||
                    storedStep == SpaceStep.COMPLETE_RESERVATION ||
                    storedStep == SpaceStep.PAYMENT_INSTRUCTIONS) {
                // Don't downgrade - keep the current step even if data seems missing
                // This handles cases where data is stored but not yet loaded
                log.debug("Keeping step {} even though some data appears missing (may be loading)", storedStep);
                return storedStep;
            }
        }

        // Step 1: Request space info (no spaceId)
        // CRITICAL: If we're at CHOOSE_SPACE without spaceId, that's NORMAL - we're
        // actively collecting it
        // Only reset to REQUEST_SPACE_INFO if we're already at REQUEST_SPACE_INFO
        if (spaceId == null) {
            // If we're at CHOOSE_SPACE, it's normal to not have spaceId yet - we're
            // processing it
            if (storedStep == SpaceStep.CHOOSE_SPACE) {
                return SpaceStep.CHOOSE_SPACE;
            }
            // If we're at REQUEST_SPACE_INFO, stay there
            if (storedStep == SpaceStep.REQUEST_SPACE_INFO) {
                return SpaceStep.REQUEST_SPACE_INFO;
            }
            // If we're at a later step, keep it (never downgrade - might be loading)
            return storedStep;
        }

        return storedStep;
    }

    /**
     * Builds a step-specific system prompt optimized for the current step
     */
    private String buildSystemPromptForStep(
            SpaceStep step,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        StringBuilder promptBuilder = new StringBuilder();

        // Add user locale information to the prompt
        String userLocale = "fr"; // Default
        try {
            UserEntity user = getUserFromAuthContext(authContext);
            if (user != null && user.getPreferredLanguage() != null && !user.getPreferredLanguage().isEmpty()) {
                userLocale = user.getPreferredLanguage();
            }
        } catch (Exception e) {
            log.debug("Could not get user locale for prompt: {}", e.getMessage());
        }
        promptBuilder.append("**USER LOCALE**: ").append(userLocale).append("\n");
        promptBuilder.append("**IMPORTANT**: Always respond in the user's preferred language (").append(userLocale)
                .append("). Do NOT hardcode French.\n\n");

        promptBuilder.append(addDateInformation(baseSystemPrompt));
        promptBuilder.append("\n\n");

        String stepPrompt = null;
        switch (step) {
            case REQUEST_SPACE_INFO:
                stepPrompt = spaceInfoPrompt;
                break;
            case CHOOSE_SPACE:
                stepPrompt = chooseSpacePrompt;
                break;
            case CONFIRM_RESERVATION_SUMMARY:
                stepPrompt = confirmSummaryPrompt;
                break;
            case COMPLETE_RESERVATION:
                stepPrompt = completeReservationPrompt;
                break;
            case PAYMENT_INSTRUCTIONS:
                // Step 6 is handled entirely by backend, no prompt needed
                // This should not be reached as Step 6 is handled before
                // buildSystemPromptForStep
                stepPrompt = "";
                break;
            case PAYMENT_CONFIRMED:
                // Step 7 is handled by payment webhook in background, should not reach here
                stepPrompt = "";
                break;
            default:
                stepPrompt = spaceInfoPrompt;
        }

        // Replace placeholders with actual values from context
        String filledPrompt = fillStepPromptPlaceholders(stepPrompt, context);

        // Add preventive validation: inject current state and rules based on state
        String preventiveValidation = buildPreventiveValidation(step, context);
        if (!preventiveValidation.isEmpty()) {
            promptBuilder.append("\n\n");
            promptBuilder.append(preventiveValidation);
        }

        promptBuilder.append(filledPrompt);

        return promptBuilder.toString();
    }

    /**
     * Builds preventive validation rules based on current step and context state.
     * This helps guide the LLM before it makes mistakes, rather than correcting
     * after.
     */
    private String buildPreventiveValidation(SpaceStep step, MatrixAssistantAgentContextService.AgentContext context) {
        if (context == null) {
            return "";
        }

        StringBuilder validation = new StringBuilder();
        validation.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        validation.append("📊 **CURRENT STATE & VALIDATION RULES**\n");
        validation.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // Get current state values
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
        Object startTimeObj = context.getWorkflowState().get("startTime");
        Object endTimeObj = context.getWorkflowState().get("endTime");
        Object startDateTimeObj = context.getWorkflowState().get("startDateTime");
        Object endDateTimeObj = context.getWorkflowState().get("endDateTime");

        boolean hasSpaceId = spaceId != null && !spaceId.isEmpty();
        boolean hasStartDate = startDateObj != null || startDateTimeObj != null;
        boolean hasEndDate = endDateObj != null || endDateTimeObj != null;
        boolean hasPeriod = hasStartDate && hasEndDate;

        // Display current state
        validation.append("**Current State:**\n");
        validation.append("- spaceId: ").append(hasSpaceId ? "✅ " + spaceId : "❌ NOT SET").append("\n");
        validation.append("- startDate: ")
                .append(hasStartDate ? "✅ " + (startDateObj != null ? startDateObj : startDateTimeObj) : "❌ NOT SET")
                .append("\n");
        validation.append("- endDate: ")
                .append(hasEndDate ? "✅ " + (endDateObj != null ? endDateObj : endDateTimeObj) : "❌ NOT SET")
                .append("\n");
        if (startTimeObj != null) {
            validation.append("- startTime: ✅ ").append(startTimeObj).append("\n");
        }
        if (endTimeObj != null) {
            validation.append("- endTime: ✅ ").append(endTimeObj).append("\n");
        }
        validation.append("\n");

        // Step-specific validation rules
        if (step == SpaceStep.CHOOSE_SPACE) {
            validation.append("**🚨 VALIDATION RULES FOR CHOOSE_SPACE:**\n\n");

            if (hasSpaceId && hasPeriod) {
                validation.append("✅ **BOTH spaceId AND period are present!**\n");
                validation.append(
                        "→ **YOU MUST use:** `status: \"SWITCH_STEP\"` with `nextStep: \"CONFIRM_RESERVATION_SUMMARY\"`\n");
                validation.append("→ **FORBIDDEN:** Do NOT use `status: \"ASK_USER\"` or `status: \"COMPLETED\"`\n");
                validation.append("→ **FORBIDDEN:** Do NOT ask for confirmation - switch immediately!\n\n");
            } else if (hasSpaceId && !hasPeriod) {
                validation.append("⚠️ **spaceId is present but period is missing!**\n");
                validation.append("→ **YOU MUST use:** `status: \"ASK_USER\"` to request the reservation period\n");
                validation
                        .append("→ **Include:** Ask for dates and times (startDate, endDate, startTime, endTime)\n\n");
            } else if (!hasSpaceId && hasPeriod) {
                validation.append("⚠️ **period is present but spaceId is missing!**\n");
                validation.append("→ **YOU MUST use:** `status: \"ASK_USER\"` to request the space choice\n");
                validation.append("→ **Include:** Call `list_spaces` and provide `availableSpaces` map\n\n");
            } else {
                validation.append("❌ **BOTH spaceId AND period are missing!**\n");
                validation.append("→ **YOU MUST use:** `status: \"ASK_USER\"` to request both space and period\n");
                validation.append("→ **Include:** Call `list_spaces` and provide `availableSpaces` map\n");
                validation.append("→ **Include:** Ask for reservation period (dates and times)\n\n");
            }

            validation.append("**CRITICAL:**\n");
            validation.append(
                    "- ❌ **NEVER use** `status: \"COMPLETED\"` in CHOOSE_SPACE - only COMPLETE_RESERVATION can use COMPLETED\n");
            validation.append(
                    "- ❌ **NEVER create reservations** in this step - you can only identify spaceId and period\n");
            validation.append("- ✅ **ALWAYS call** `submit_reservation_step` function - never return plain text\n");
        } else if (step == SpaceStep.CONFIRM_RESERVATION_SUMMARY) {
            validation.append("**🚨 VALIDATION RULES FOR CONFIRM_RESERVATION_SUMMARY:**\n\n");

            if (!hasSpaceId || !hasPeriod) {
                validation.append("⚠️ **Missing required data!**\n");
                validation.append("→ This step requires both spaceId and period\n");
                validation.append("→ If missing, this indicates a workflow error\n\n");
            } else {
                validation.append("✅ **All required data is present!**\n");
                validation.append("→ Display the summary and wait for user confirmation\n");
                validation.append("→ Use `status: \"ASK_USER\"` to wait for confirmation\n\n");
            }
        } else if (step == SpaceStep.COMPLETE_RESERVATION) {
            validation.append("**🚨 VALIDATION RULES FOR COMPLETE_RESERVATION:**\n\n");

            if (!hasSpaceId || !hasPeriod) {
                validation.append("⚠️ **Missing required data!**\n");
                validation.append("→ Cannot create reservation without spaceId and period\n");
                validation.append("→ This indicates a workflow error\n\n");
            } else {
                validation.append("✅ **All required data is present!**\n");
                validation.append("→ If user confirms, call `create_reservation` tool\n");
                validation.append("→ After successful creation, use `status: \"COMPLETED\"`\n\n");
            }
        }

        validation.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        return validation.toString();
    }

    /**
     * Fills placeholders in step prompts with actual values from context
     */
    private String fillStepPromptPlaceholders(String prompt, MatrixAssistantAgentContextService.AgentContext context) {
        if (prompt == null || context == null) {
            return prompt != null ? prompt : "";
        }

        String result = prompt;

        // Replace common placeholders
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        result = result.replace("{{SPACE_ID}}", spaceId != null ? spaceId : "UNKNOWN");

        Object startDateObj = context.getWorkflowState().get("startDate");
        String startDate = startDateObj != null ? startDateObj.toString() : "UNKNOWN";
        result = result.replace("{{START_DATE}}", startDate);

        Object endDateObj = context.getWorkflowState().get("endDate");
        String endDate = endDateObj != null ? endDateObj.toString() : "UNKNOWN";
        result = result.replace("{{END_DATE}}", endDate);

        String startTime = context.getWorkflowStateValue("startTime", String.class);
        if (startTime == null || startTime.isEmpty()) {
            startTime = "00:00";
        }
        result = result.replace("{{START_TIME}}", startTime);

        String endTime = context.getWorkflowStateValue("endTime", String.class);
        if (endTime == null || endTime.isEmpty()) {
            endTime = "23:59";
        }
        result = result.replace("{{END_TIME}}", endTime);

        String reservationId = context.getWorkflowStateValue("reservationId", String.class);
        if (reservationId != null && !reservationId.isEmpty()) {
            result = result.replace("{{#RESERVATION_ID}}", "");
            result = result.replace("{{/RESERVATION_ID}}", "");
        } else {
            // Remove conditional blocks if reservationId is not available
            result = result.replaceAll("\\{\\{#RESERVATION_ID\\}\\}.*?\\{\\{/RESERVATION_ID\\}\\}", "");
        }

        // Handle conditional blocks for startTime/endTime
        if (startTime != null && !startTime.equals("00:00") && endTime != null && !endTime.equals("23:59")) {
            result = result.replace("{{#START_TIME}}", "");
            result = result.replace("{{/START_TIME}}", "");
        } else {
            // Remove conditional blocks if times are default
            result = result.replaceAll("\\{\\{#START_TIME\\}\\}.*?\\{\\{/START_TIME\\}\\}", "");
        }

        return result;
    }

    @Override
    protected String getSystemPrompt(MatrixAssistantAuthContext authContext) {
        return addDateInformation(baseSystemPrompt);
    }

    @Override
    protected Set<String> getAvailableToolNames() {
        Set<String> tools = new HashSet<>();
        tools.add("list_spaces");
        tools.add("check_space_availability");
        tools.add("create_reservation");
        tools.add("generate_payment_link");
        return tools;
    }

    /**
     * Public wrapper for step handlers to filter tools
     */
    public List<Map<String, Object>> filterToolsForStepPublic(List<MCPTool> allTools, SpaceStep currentStep) {
        return filterToolsForStep(allTools, currentStep);
    }

    /**
     * Filters tools based on the current reservation step.
     * For steps 1, 2, and 3, excludes create_reservation and generate_payment_link
     * to prevent the LLM from creating reservations prematurely.
     * Step 4 (CONFIRM_RESERVATION_SUMMARY) can use all tools as the backend handles
     * creation.
     * 
     * For CHOOSE_SPACE, adds the submit_reservation_step tool
     * to force structured JSON output via function calls.
     */
    protected List<Map<String, Object>> filterToolsForStep(List<MCPTool> allTools, SpaceStep currentStep) {
        Set<String> availableToolNames = getAvailableToolNames();

        // For steps 1, 2, and 3, exclude reservation creation tools
        if (currentStep == SpaceStep.REQUEST_SPACE_INFO ||
                currentStep == SpaceStep.CHOOSE_SPACE) {
            availableToolNames = new HashSet<>(availableToolNames);
            availableToolNames.remove("create_reservation");
            availableToolNames.remove("generate_payment_link");
            log.debug("Filtered tools for step {}: excluded create_reservation and generate_payment_link", currentStep);
        }

        Set<String> finalAvailableToolNames = availableToolNames;
        List<Map<String, Object>> tools = allTools.stream()
                .filter(tool -> finalAvailableToolNames.contains(tool.getName()))
                .map(this::convertMCPToolToMistralFunction)
                .collect(Collectors.toList());

        // Add submit_reservation_step tool for CHOOSE_SPACE
        if (currentStep == SpaceStep.CHOOSE_SPACE) {
            tools.add(createSubmitReservationStepTool());
        }

        return tools;
    }

    /**
     * Creates the submit_reservation_step tool definition for Mistral function
     * calls.
     * This tool forces Mistral to return structured JSON via function calls instead
     * of
     * plain text or JSON in content.
     */
    private Map<String, Object> createSubmitReservationStepTool() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");

        Map<String, Object> function = new HashMap<>();
        function.put("name", "submit_reservation_step");
        function.put("description",
                "Soumettre les informations de l'étape de réservation (espace, période, statut). Vous DEVEZ appeler cette fonction pour soumettre votre réponse structurée.");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // status field
        Map<String, Object> statusEnum = new HashMap<>();
        statusEnum.put("type", "string");
        statusEnum.put("enum",
                java.util.Arrays.asList("ANSWER_USER", "ASK_USER", "SWITCH_STEP", "CANCEL", "COMPLETED", "ERROR"));
        properties.put("status", statusEnum);

        // response field
        Map<String, Object> responseField = new HashMap<>();
        responseField.put("type", "string");
        responseField.put("description", "Message de réponse à l'utilisateur");
        properties.put("response", responseField);

        // spaceId field (optional)
        Map<String, Object> spaceIdField = new HashMap<>();
        spaceIdField.put("type", "string");
        spaceIdField.put("description", "UUID de l'espace sélectionné (requis si identifié)");
        properties.put("spaceId", spaceIdField);

        // period field (optional)
        Map<String, Object> periodField = new HashMap<>();
        periodField.put("type", "object");
        Map<String, Object> periodProperties = new HashMap<>();

        Map<String, Object> startDateField = new HashMap<>();
        startDateField.put("type", "string");
        startDateField.put("description", "Date de début au format YYYY-MM-DD");
        periodProperties.put("startDate", startDateField);

        Map<String, Object> endDateField = new HashMap<>();
        endDateField.put("type", "string");
        endDateField.put("description", "Date de fin au format YYYY-MM-DD");
        periodProperties.put("endDate", endDateField);

        Map<String, Object> startTimeField = new HashMap<>();
        startTimeField.put("type", "string");
        startTimeField.put("description", "Heure de début au format HH:mm (optionnel)");
        periodProperties.put("startTime", startTimeField);

        Map<String, Object> endTimeField = new HashMap<>();
        endTimeField.put("type", "string");
        endTimeField.put("description", "Heure de fin au format HH:mm (optionnel)");
        periodProperties.put("endTime", endTimeField);

        periodField.put("properties", periodProperties);
        periodField.put("required", java.util.Arrays.asList("startDate", "endDate"));
        properties.put("period", periodField);

        // nextStep field (optional)
        Map<String, Object> nextStepField = new HashMap<>();
        nextStepField.put("type", "string");
        nextStepField.put("enum", java.util.Arrays.asList("CHOOSE_SPACE",
                "CONFIRM_RESERVATION_SUMMARY", "COMPLETE_RESERVATION"));
        nextStepField.put("description", "Étape suivante si status est SWITCH_STEP");
        properties.put("nextStep", nextStepField);

        // availableSpaces field (optional, but REQUIRED when status == ASK_USER and
        // user needs to choose)
        // Map: key is space number (e.g., "7", "23"), value is UUID
        Map<String, Object> availableSpacesField = new HashMap<>();
        availableSpacesField.put("type", "object");
        availableSpacesField.put("description",
                "Map des espaces disponibles: clé = numéro d'espace (ex: \"7\", \"23\"), valeur = UUID (ex: {\"7\": \"550e8400-...\", \"23\": \"550e8400-...\"}). REQUIS si status == ASK_USER et que l'utilisateur doit choisir parmi des options. Permet au LLM de mapper le choix de l'utilisateur (ex: \"23\") au bon UUID.");
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("type", "string");
        availableSpacesField.put("additionalProperties", additionalProperties);
        properties.put("availableSpaces", availableSpacesField);

        parameters.put("properties", properties);
        parameters.put("required", java.util.Arrays.asList("status", "response"));

        function.put("parameters", parameters);
        tool.put("function", function);

        return tool;
    }

    @Override
    protected boolean shouldUseRAG() {
        return false; // Reservation agent doesn't need RAG
    }

    /**
     * Gets user from auth context with proper persistence context handling
     * (similar to MatrixMCPReservationHandler logic)
     */
    private UserEntity getUserFromAuthContext(MatrixAssistantAuthContext authContext) {
        UserEntity userFromContext = authContext.getAuthenticatedUser();
        UserEntity user = null;

        // First, try to get user from current persistence context (fastest)
        try {
            user = entityManager.find(UserEntity.class, userFromContext.getId());
        } catch (Exception e) {
            log.debug("User {} not found in current persistence context: {}", userFromContext.getId(),
                    e.getMessage());
        }

        // If not found, try to reload from database
        if (user == null) {
            user = usersRepository.findById(userFromContext.getId()).orElse(null);
        }

        // If still not found, try by username
        if (user == null && userFromContext.getUsername() != null) {
            user = usersRepository.findByUsername(userFromContext.getUsername());
        }

        // If still not found, use merge() to attach the detached entity to the current
        // session
        if (user == null) {
            log.debug("User {} not found in database, merging detached entity into current session",
                    userFromContext.getId());
            try {
                user = entityManager.merge(userFromContext);
                log.debug("Successfully merged user {} into current session", userFromContext.getId());
            } catch (Exception e) {
                log.error("Error merging user into persistence context: {}", e.getMessage(), e);
                // Fallback: return detached user to keep flow alive
                user = userFromContext;
            }
        }

        return user;
    }

    /**
     * Gets locale from context, userEntity, or defaults to "fr"
     */
    private Locale getLocaleFromContext(MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {
        // First try context
        if (context != null) {
            String localeStr = context.getWorkflowStateValue("locale", String.class);
            if (localeStr != null && !localeStr.isEmpty()) {
                return Locale.forLanguageTag(localeStr);
            }
        }
        // Then try userEntity
        try {
            UserEntity user = getUserFromAuthContext(authContext);
            if (user != null && user.getPreferredLanguage() != null && !user.getPreferredLanguage().isEmpty()) {
                return Locale.forLanguageTag(user.getPreferredLanguage());
            }
        } catch (Exception e) {
            log.debug("Could not get user locale from userEntity: {}", e.getMessage());
        }
        // Default to French
        return Locale.FRENCH;
    }

    /**
     * Gets locale from context or defaults to "fr" (backward compatibility)
     */
    private Locale getLocaleFromContext(MatrixAssistantAgentContextService.AgentContext context) {
        if (context == null) {
            return Locale.FRENCH;
        }
        String localeStr = context.getWorkflowStateValue("locale", String.class);
        if (localeStr == null || localeStr.isEmpty()) {
            return Locale.FRENCH;
        }
        return Locale.forLanguageTag(localeStr);
    }

}
