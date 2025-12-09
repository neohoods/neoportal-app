package com.neohoods.portal.platform.assistant.workflows;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPReservationHandler;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.model.WorkflowType;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.space.SpaceStateMachine;
import com.neohoods.portal.platform.assistant.workflows.space.steps.SpaceStepHandler;
import com.neohoods.portal.platform.entities.UserEntity;
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
    private String choosePeriodPrompt;
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
        choosePeriodPrompt = loadPromptFile(choosePeriodPromptFile, "Choose period");
        confirmSummaryPrompt = loadPromptFile(confirmSummaryPromptFile, "Confirm summary");
        completeReservationPrompt = loadPromptFile(completeReservationPromptFile, "Complete reservation");
        paymentInstructionsPrompt = loadPromptFile(paymentInstructionsPromptFile, "Payment instructions");
        // Step 7 is handled by payment webhook in background, no prompt needed
    }

    /**
     * Public wrapper for step handlers to call Mistral API
     */
    public Mono<SpaceStepResponse> callMistralAPIWithJSONResponseForStep(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext) {
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext);
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
                    if (stepResponse.getResponse() != null && !stepResponse.getResponse().isEmpty()) {
                        return stepResponse.getResponse();
                    }
                    return "Désolé, je n'ai pas pu générer de réponse.";
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
            return SpaceStep.REQUEST_SPACE_INFO;
        }

        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
        if (startDateObj == null || endDateObj == null) {
            return SpaceStep.CHOOSE_PERIOD;
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
        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
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
        if (spaceId != null && startDateObj != null && endDateObj != null &&
                (summaryShown == null || !summaryShown)) {
            if (storedStep != SpaceStep.CONFIRM_RESERVATION_SUMMARY) {
                log.info("Upgrading step from {} to CONFIRM_RESERVATION_SUMMARY (all info collected)", storedStep);
                return SpaceStep.CONFIRM_RESERVATION_SUMMARY;
            }
            return SpaceStep.CONFIRM_RESERVATION_SUMMARY;
        }

        // Step 3: Choose period (spaceId exists but dates missing)
        if (spaceId != null && (startDateObj == null || endDateObj == null)) {
            if (storedStep.ordinal() > SpaceStep.CHOOSE_PERIOD.ordinal()) {
                log.warn("Downgrading step from {} to CHOOSE_PERIOD (missing dates)", storedStep);
                return SpaceStep.CHOOSE_PERIOD;
            }
            return SpaceStep.CHOOSE_PERIOD;
        }

        // Step 2: Choose space (spaceId exists)
        if (spaceId != null) {
            if (storedStep == SpaceStep.REQUEST_SPACE_INFO) {
                log.info("Upgrading step from REQUEST_SPACE_INFO to CHOOSE_SPACE (spaceId found)");
                return SpaceStep.CHOOSE_SPACE;
            }
            // If we're at CHOOSE_SPACE and have spaceId, stay there or move to
            // CHOOSE_PERIOD
            if (storedStep == SpaceStep.CHOOSE_SPACE) {
                if (startDateObj != null && endDateObj != null) {
                    return SpaceStep.CHOOSE_PERIOD;
                }
                return SpaceStep.CHOOSE_SPACE;
            }
        }

        // Step 1: Request space info (no spaceId)
        if (spaceId == null) {
            if (storedStep != SpaceStep.REQUEST_SPACE_INFO) {
                log.warn("Resetting step to REQUEST_SPACE_INFO (no spaceId)");
                return SpaceStep.REQUEST_SPACE_INFO;
            }
            return SpaceStep.REQUEST_SPACE_INFO;
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
            case CHOOSE_PERIOD:
                stepPrompt = choosePeriodPrompt;
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
        promptBuilder.append(filledPrompt);

        return promptBuilder.toString();
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
     */
    protected List<Map<String, Object>> filterToolsForStep(List<MCPTool> allTools, SpaceStep currentStep) {
        Set<String> availableToolNames = getAvailableToolNames();

        // For steps 1, 2, and 3, exclude reservation creation tools
        if (currentStep == SpaceStep.REQUEST_SPACE_INFO ||
                currentStep == SpaceStep.CHOOSE_SPACE ||
                currentStep == SpaceStep.CHOOSE_PERIOD) {
            availableToolNames = new HashSet<>(availableToolNames);
            availableToolNames.remove("create_reservation");
            availableToolNames.remove("generate_payment_link");
            log.debug("Filtered tools for step {}: excluded create_reservation and generate_payment_link", currentStep);
        }

        Set<String> finalAvailableToolNames = availableToolNames;
        return allTools.stream()
                .filter(tool -> finalAvailableToolNames.contains(tool.getName()))
                .map(this::convertMCPToolToMistralFunction)
                .collect(Collectors.toList());
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
                throw new IllegalStateException(
                        "User not found in database and cannot be attached to session: " +
                                userFromContext.getId() + ". Error: " + e.getMessage());
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
