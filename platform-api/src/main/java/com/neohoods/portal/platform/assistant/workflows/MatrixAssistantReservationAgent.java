package com.neohoods.portal.platform.assistant.workflows;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.ReservationPeriod;
import com.neohoods.portal.platform.assistant.model.ReservationStep;
import com.neohoods.portal.platform.assistant.model.ReservationStepResponse;
import com.neohoods.portal.platform.assistant.model.WorkflowType;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPContent;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPReservationHandler;
import com.neohoods.portal.platform.spaces.services.SpacesService;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.StripeService;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.MessageSource;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

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
public class MatrixAssistantReservationAgent extends BaseMatrixAssistantAgent {

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-prompt.txt}")
    private String reservationAgentPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-step1-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-step1-request-space-info.txt}")
    private String step1PromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-step2-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-step2-choose-space.txt}")
    private String step2PromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-step3-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-step3-choose-period.txt}")
    private String step3PromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-step4-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-step4-confirm-summary.txt}")
    private String step4PromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-step5-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-step5-complete-reservation.txt}")
    private String step5PromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-step6-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-step6-payment-instructions.txt}")
    private String step6PromptFile;

    private String baseSystemPrompt;
    private String step1Prompt;
    private String step2Prompt;
    private String step3Prompt;
    private String step4Prompt;
    private String step5Prompt;
    private String step6Prompt;

    private final MatrixMCPReservationHandler reservationHandler;
    private final SpacesService spacesService;
    private final ReservationsService reservationsService;
    private final StripeService stripeService;
    private final ReservationRepository reservationRepository;
    private final UsersRepository usersRepository;
    private final MessageSource messageSource;

    @PersistenceContext
    private EntityManager entityManager;

    public MatrixAssistantReservationAgent(
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
            MessageSource messageSource) {
        super(webClientBuilder, objectMapper, mcpAdapter, resourceLoader, agentContextService);
        this.reservationHandler = reservationHandler;
        this.spacesService = spacesService;
        this.reservationsService = reservationsService;
        this.stripeService = stripeService;
        this.reservationRepository = reservationRepository;
        this.usersRepository = usersRepository;
        this.messageSource = messageSource;
        loadSystemPrompt();
    }

    private void loadSystemPrompt() {
        baseSystemPrompt = loadPromptFile(reservationAgentPromptFile,
                "You are Alfred, the AI assistant for NeoHoods. You handle space reservations.");
        step1Prompt = loadPromptFile(step1PromptFile, "Step 1: Request space info");
        step2Prompt = loadPromptFile(step2PromptFile, "Step 2: Choose space");
        step3Prompt = loadPromptFile(step3PromptFile, "Step 3: Choose period");
        step4Prompt = loadPromptFile(step4PromptFile, "Step 4: Confirm summary");
        step5Prompt = loadPromptFile(step5PromptFile, "Step 5: Complete reservation");
        step6Prompt = loadPromptFile(step6PromptFile, "Step 6: Payment instructions");
        // Step 7 is handled by payment webhook in background, no prompt needed
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
            context.setCurrentWorkflow(WorkflowType.RESERVATION);
        }

        // Determine current step based on context
        ReservationStep currentStep = determineCurrentStep(context);

        // Update context with current step
        if (context != null) {
            context.updateWorkflowState("reservationStep", currentStep.name());
        }

        // Handle each step with a switch case for better readability
        return switch (currentStep) {
            case REQUEST_SPACE_INFO -> handleStep1RequestSpaceInfo(userMessage, conversationHistory, context,
                    authContext);
            case CHOOSE_SPACE -> handleStep2ChooseSpace(userMessage, conversationHistory, context, authContext);
            case CHOOSE_PERIOD -> handleStep3ChoosePeriod(userMessage, conversationHistory, context, authContext);
            case CONFIRM_RESERVATION_SUMMARY -> handleStep4ConfirmSummary(userMessage, conversationHistory, context,
                    authContext);
            case PAYMENT_INSTRUCTIONS -> handleStep6PaymentInstructions(context, authContext, roomId);
            case COMPLETE_RESERVATION -> handleStep5CompleteReservation(userMessage, conversationHistory, context,
                    authContext);
            case PAYMENT_CONFIRMED -> {
                Locale locale = context != null ? getLocaleFromContext(context) : Locale.FRENCH;
                yield Mono.just(messageSource.getMessage("matrix.reservation.payment.confirmed", null, locale));
            }
        };
    }

    /**
     * Step 6: Payment instructions - handled entirely by backend, no LLM
     * Generates payment link using StripeService directly
     */
    private Mono<String> handleStep6PaymentInstructions(
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext,
            String roomId) {

        if (context == null) {
            return Mono.just("Erreur: Contexte de réservation introuvable.");
        }

        String reservationIdStr = context.getWorkflowStateValue("reservationId", String.class);
        if (reservationIdStr == null || reservationIdStr.isEmpty()) {
            log.warn("Step 6: No reservationId found in context, cannot generate payment link");
            Locale locale = getLocaleFromContext(context);
            return Mono
                    .just(messageSource.getMessage("matrix.reservation.payment.reservationIdRequired", null, locale));
        }

        log.info("✅ Step 6: Generating payment link automatically (reservationId={})", reservationIdStr);

        try {
            UUID reservationId = UUID.fromString(reservationIdStr);
            ReservationEntity reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationIdStr));

            // Verify the reservation belongs to the authenticated user
            UserEntity user = getUserFromAuthContext(authContext);
            if (!reservation.getUser().getId().equals(user.getId())) {
                Locale locale = getLocaleFromContext(context);
                return Mono.just(messageSource.getMessage("matrix.reservation.payment.noAccess", null, locale));
            }

            // Create PaymentIntent first if not exists
            if (reservation.getStripePaymentIntentId() == null || reservation.getStripePaymentIntentId().isEmpty()) {
                try {
                    String paymentIntentId = stripeService.createPaymentIntent(reservation, user,
                            reservation.getSpace());
                    reservation.setStripePaymentIntentId(paymentIntentId);
                    reservationRepository.save(reservation);
                } catch (Exception e) {
                    log.error("Error creating payment intent: {}", e.getMessage(), e);
                    Locale locale = getLocaleFromContext(context);
                    return Mono.just(messageSource.getMessage("matrix.reservation.payment.createIntentError",
                            new Object[] { e.getMessage() }, locale));
                }
            }

            // Create checkout session
            String checkoutUrl;
            try {
                checkoutUrl = stripeService.createCheckoutSession(reservation, user, reservation.getSpace());
                reservationRepository.save(reservation);
            } catch (Exception e) {
                log.error("Error creating checkout session: {}", e.getMessage(), e);
                Locale locale = getLocaleFromContext(context);
                return Mono.just(messageSource.getMessage("matrix.reservation.payment.createLinkError",
                        new Object[] { e.getMessage() }, locale));
            }

            // Generate templated response with locale
            String paymentMessage = generatePaymentInstructionsMessage(reservation, checkoutUrl, context);

            // Mark payment link as generated and clear context
            context.updateWorkflowState("paymentLinkGenerated", true);
            agentContextService.clearContext(roomId);
            log.info("✅ Step 6: Payment link generated successfully");

            return Mono.just(paymentMessage);
        } catch (Exception e) {
            log.error("Exception generating payment link: {}", e.getMessage(), e);
            Locale locale = getLocaleFromContext(context);
            return Mono.just(messageSource.getMessage("matrix.reservation.payment.error",
                    new Object[] { e.getMessage() }, locale));
        }
    }

    /**
     * Step 1: Request space info - LLM helps user identify which space to reserve
     */
    private Mono<String> handleStep1RequestSpaceInfo(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(ReservationStep.REQUEST_SPACE_INFO, context, authContext);

        // Get filtered tools for this agent
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterTools(allTools);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse.getResponse());
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        // User canceled - clear context
                        log.info("❌ Step 1: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse.getResponse());
                    }

                    // Step 1: Store locale if provided (should be identified by LLM)
                    if (stepResponse.getLocale() != null && !stepResponse.getLocale().isEmpty()) {
                        if (context != null) {
                            context.updateWorkflowState("locale", stepResponse.getLocale());
                            log.info("🌐 Step 1: Locale identified and stored: {}", stepResponse.getLocale());
                        }
                    } else {
                        // Default to French if not identified
                        if (context != null) {
                            context.updateWorkflowState("locale", "fr");
                            log.info("🌐 Step 1: No locale identified, defaulting to 'fr'");
                        }
                    }

                    // Step 1 is purely informational - we never store spaceId here
                    // Step 2 will handle space identification and confirmation
                    // If user confirms their choice in next message, Step 2 will be called
                    log.info(
                            "🔄 Step 1: Status={}, continuing conversation (Step 2 will handle space confirmation when user confirms choice)",
                            stepResponse.getStatus());
                    return Mono.just(stepResponse.getResponse());
                });
    }

    /**
     * Step 2: Choose space - LLM identifies spaceId UUID, may also extract period
     */
    private Mono<String> handleStep2ChooseSpace(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(ReservationStep.CHOOSE_SPACE, context, authContext);

        // Get filtered tools for this agent
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterTools(allTools);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse.getResponse());
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        // User canceled - clear context
                        log.info("❌ Step 2: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse.getResponse());
                    }

                    if (stepResponse.isPending()) {
                        // Need more conversation - return response
                        log.info("🔄 Step 2: Status=PENDING, continuing conversation");
                        return Mono.just(stepResponse.getResponse());
                    }

                    if (stepResponse.isCompleted()) {
                        // Validate spaceId is present (required for COMPLETED)
                        if (!stepResponse.hasSpaceId()) {
                            log.error("❌ Step 2: COMPLETED status but spaceId is missing!");
                            Locale locale = getLocaleFromContext(context);
                            return Mono.just(
                                    messageSource.getMessage("matrix.reservation.error.spaceIdMissing", null, locale));
                        }

                        // Store spaceId
                        if (context != null) {
                            context.updateWorkflowState("spaceId", stepResponse.getSpaceId());
                            log.info("✅ Step 2: Space identified (spaceId={})", stepResponse.getSpaceId());

                            // Check if period is also provided
                            if (stepResponse.hasCompletePeriod()) {
                                // User provided both space and period - store period and generate summary
                                ReservationPeriod period = stepResponse.getPeriod();
                                context.updateWorkflowState("startDate", period.getStartDate());
                                context.updateWorkflowState("endDate", period.getEndDate());
                                if (period.getStartTime() != null) {
                                    context.updateWorkflowState("startTime", period.getStartTime());
                                }
                                if (period.getEndTime() != null) {
                                    context.updateWorkflowState("endTime", period.getEndTime());
                                }
                                context.updateWorkflowState("reservationStep",
                                        ReservationStep.CONFIRM_RESERVATION_SUMMARY.name());
                                log.info(
                                        "✅ Step 2: Space + period identified, generating summary and moving to Step 4");

                                // Generate formatted reservation summary
                                return generateReservationSummary(stepResponse.getSpaceId(), period, authContext)
                                        .map(summary -> summary);
                            } else {
                                // Only spaceId - move to Step 3 to get period
                                context.updateWorkflowState("reservationStep", ReservationStep.CHOOSE_PERIOD.name());
                                log.info("✅ Step 2: Space identified, moving to Step 3");
                            }
                        }

                        return Mono.just(stepResponse.getResponse());
                    }

                    // Fallback
                    return Mono.just(stepResponse.getResponse());
                });
    }

    /**
     * Step 3: Choose period - LLM gets reservation dates and times from user
     */
    private Mono<String> handleStep3ChoosePeriod(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(ReservationStep.CHOOSE_PERIOD, context, authContext);

        // Get filtered tools for this agent
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterTools(allTools);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse.getResponse());
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        // User canceled - clear context
                        log.info("❌ Step 3: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse.getResponse());
                    }

                    if (stepResponse.isPending()) {
                        // Need more conversation - return response
                        log.info("🔄 Step 3: Status=PENDING, continuing conversation");
                        return Mono.just(stepResponse.getResponse());
                    }

                    if (stepResponse.isCompleted()) {
                        // Validate spaceId and period are present (required for COMPLETED)
                        if (!stepResponse.hasSpaceId()) {
                            log.error("❌ Step 3: COMPLETED status but spaceId is missing!");
                            Locale locale = getLocaleFromContext(context);
                            return Mono.just(
                                    messageSource.getMessage("matrix.reservation.error.spaceIdMissing", null, locale));
                        }

                        if (!stepResponse.hasCompletePeriod()) {
                            log.error("❌ Step 3: COMPLETED status but period is incomplete!");
                            Locale locale = getLocaleFromContext(context);
                            return Mono.just(messageSource.getMessage("matrix.reservation.error.periodIncomplete", null,
                                    locale));
                        }

                        // Store period and generate summary
                        if (context != null) {
                            ReservationPeriod period = stepResponse.getPeriod();
                            context.updateWorkflowState("spaceId", stepResponse.getSpaceId());
                            context.updateWorkflowState("startDate", period.getStartDate());
                            context.updateWorkflowState("endDate", period.getEndDate());
                            if (period.getStartTime() != null && !period.getStartTime().isEmpty()) {
                                context.updateWorkflowState("startTime", period.getStartTime());
                            }
                            if (period.getEndTime() != null && !period.getEndTime().isEmpty()) {
                                context.updateWorkflowState("endTime", period.getEndTime());
                            }
                            context.updateWorkflowState("reservationStep",
                                    ReservationStep.CONFIRM_RESERVATION_SUMMARY.name());
                            log.info("✅ Step 3: Period identified, generating summary and moving to Step 4");

                            // Generate formatted reservation summary
                            return generateReservationSummary(stepResponse.getSpaceId(), period, authContext)
                                    .map(summary -> summary);
                        }

                        return Mono.just(stepResponse.getResponse());
                    }

                    // Fallback
                    return Mono.just(stepResponse.getResponse());
                });
    }

    /**
     * Step 4: Confirm reservation summary - LLM shows summary and detects user
     * confirmation
     */
    private Mono<String> handleStep4ConfirmSummary(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(ReservationStep.CONFIRM_RESERVATION_SUMMARY, context,
                authContext);

        // Get filtered tools for this agent
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterTools(allTools);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse.getResponse());
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        // User canceled - clear context
                        log.info("❌ Step 4: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse.getResponse());
                    }

                    if (stepResponse.isPending()) {
                        // Waiting for confirmation - return response
                        log.info("🔄 Step 4: Status=PENDING, waiting for user confirmation");
                        return Mono.just(stepResponse.getResponse());
                    }

                    if (stepResponse.isCompleted()) {
                        // User confirmed - create reservation directly (backend)
                        log.info("✅ Step 4: User confirmed, creating reservation");

                        // Validate we have all required information
                        Locale locale;
                        if (context == null) {
                            locale = Locale.FRENCH; // Default if no context
                            return Mono.just(
                                    messageSource.getMessage("matrix.reservation.error.contextNotFound", null, locale));
                        }

                        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
                        String startDate = context.getWorkflowStateValue("startDate", String.class);
                        String endDate = context.getWorkflowStateValue("endDate", String.class);
                        String startTime = context.getWorkflowStateValue("startTime", String.class);
                        String endTime = context.getWorkflowStateValue("endTime", String.class);

                        locale = getLocaleFromContext(context);
                        if (spaceId == null || startDate == null || endDate == null) {
                            log.error("❌ Step 4: Missing required information for reservation creation");
                            return Mono.just(
                                    messageSource.getMessage("matrix.reservation.error.incompleteInfo", null, locale));
                        }

                        // Create reservation directly via service (backend)
                        try {
                            // Get user from auth context (with proper persistence context handling)
                            UserEntity user = getUserFromAuthContext(authContext);
                            if (user == null) {
                                return Mono.just(messageSource.getMessage("matrix.reservation.error.userNotFound", null,
                                        locale));
                            }

                            // Get space
                            UUID spaceUuid = UUID.fromString(spaceId);
                            SpaceEntity space = spacesService.getSpaceById(spaceUuid);
                            if (space == null) {
                                return Mono.just(messageSource.getMessage("matrix.reservation.error.spaceNotFound",
                                        null, locale));
                            }

                            // Parse dates
                            LocalDate startDateParsed = LocalDate.parse(startDate);
                            LocalDate endDateParsed = LocalDate.parse(endDate);

                            // Create reservation via service
                            ReservationEntity reservation = reservationsService.createReservation(
                                    space, user, startDateParsed, endDateParsed);

                            // Generate templated response with locale
                            String confirmationMessage = generateReservationConfirmationMessage(reservation, space,
                                    startDateParsed, endDateParsed, startTime, endTime, context);

                            // Store reservation ID and mark as created
                            context.updateWorkflowState("reservationId", reservation.getId().toString());
                            context.updateWorkflowState("reservationCreated", true);

                            // Check if payment is required (reservation status is PENDING_PAYMENT)
                            if (reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT) {
                                context.updateWorkflowState("paymentRequired", true);
                            }

                            // Move to Step 5 (show confirmation)
                            context.updateWorkflowState("reservationStep",
                                    ReservationStep.COMPLETE_RESERVATION.name());
                            log.info("✅ Step 4: Reservation created successfully (id={}), moving to Step 5",
                                    reservation.getId());

                            return Mono.just(confirmationMessage);
                        } catch (Exception e) {
                            log.error("Exception creating reservation: {}", e.getMessage(), e);
                            return Mono.just(messageSource.getMessage("matrix.reservation.error.createFailed",
                                    new Object[] { e.getMessage() }, locale));
                        }
                    }

                    // Fallback
                    return Mono.just(stepResponse.getResponse());
                });
    }

    /**
     * Extracts reservation ID from reservation creation result text
     */
    private String extractReservationId(String resultText) {
        if (resultText == null) {
            return null;
        }
        // Look for pattern: "ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        java.util.regex.Pattern pattern = java.util.regex.Pattern
                .compile("ID:\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(resultText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Detects if user wants to cancel or change their mind
     * Checks for keywords and LLM response status
     */
    private boolean detectUserChangeOfMind(String userMessage, ReservationStepResponse stepResponse) {
        if (userMessage == null) {
            return false;
        }

        String lowerMessage = userMessage.toLowerCase().trim();

        // Check for cancellation keywords
        String[] cancelKeywords = { "annuler", "cancel", "annulation", "non finalement", "autre chose", "changer",
                "recommencer", "abandonner", "stop", "arrêter" };
        for (String keyword : cancelKeywords) {
            if (lowerMessage.contains(keyword)) {
                log.info("🔍 User change of mind detected via keyword: {}", keyword);
                return true;
            }
        }

        // Check LLM response status
        if (stepResponse != null && stepResponse.isCanceled()) {
            log.info("🔍 User change of mind detected via LLM status: CANCELED");
            return true;
        }

        return false;
    }

    /**
     * Step 5: Complete reservation - Backend only, triggered automatically after
     * Step 4
     * Shows reservation confirmation message
     */
    private Mono<String> handleStep5CompleteReservation(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        String roomId = authContext.getRoomId();
        Locale locale = context != null ? getLocaleFromContext(context) : Locale.FRENCH;
        if (roomId == null) {
            return Mono.just(messageSource.getMessage("matrix.reservation.error.contextNotFound", null, locale));
        }

        if (context == null) {
            return Mono.just(messageSource.getMessage("matrix.reservation.error.contextNotFound", null, locale));
        }

        // Reservation was already created in Step 4
        // Just show confirmation message - use templated message from Step 4
        // Step 5 is just a pass-through, the confirmation was already shown in Step 4
        String reservationId = context.getWorkflowStateValue("reservationId", String.class);
        Boolean paymentRequired = context.getWorkflowStateValue("paymentRequired", Boolean.class);

        // If we have reservationId, we can generate a simple confirmation
        // But ideally Step 4 already showed the full confirmation
        String confirmationMessage = messageSource.getMessage("matrix.reservation.createdSuccess", null, locale);
        if (reservationId != null) {
            confirmationMessage += "\n\n" + messageSource.getMessage("matrix.reservation.id", null, locale) + ": "
                    + reservationId;
        }

        // If payment is required, move to Step 6
        if (Boolean.TRUE.equals(paymentRequired)) {
            context.updateWorkflowState("reservationStep", ReservationStep.PAYMENT_INSTRUCTIONS.name());
            log.info("✅ Step 5: Payment required, moving to Step 6");
        } else {
            // No payment - clear context and end workflow
            agentContextService.clearContext(roomId);
            log.info("✅ Step 5: No payment required, workflow complete");
        }

        return Mono.just(confirmationMessage);
    }

    /**
     * Determines the current step based on context state
     */
    private ReservationStep determineCurrentStep(MatrixAssistantAgentContextService.AgentContext context) {
        if (context == null) {
            return ReservationStep.REQUEST_SPACE_INFO;
        }

        // Check if we have a stored step
        String stepStr = context.getWorkflowStateValue("reservationStep", String.class);
        if (stepStr != null) {
            try {
                ReservationStep storedStep = ReservationStep.valueOf(stepStr);
                // Validate that stored step is still valid based on collected data
                return validateAndUpdateStep(storedStep, context);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid reservation step in context: {}, resetting to REQUEST_SPACE_INFO", stepStr);
            }
        }

        // Determine step based on collected data
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        if (spaceId == null || spaceId.isEmpty()) {
            return ReservationStep.REQUEST_SPACE_INFO;
        }

        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
        if (startDateObj == null || endDateObj == null) {
            return ReservationStep.CHOOSE_PERIOD;
        }

        // Check if reservation has been created
        Boolean reservationCreated = context.getWorkflowStateValue("reservationCreated", Boolean.class);
        if (reservationCreated != null && reservationCreated) {
            // Check if payment link has been generated
            Boolean paymentLinkGenerated = context.getWorkflowStateValue("paymentLinkGenerated", Boolean.class);
            if (paymentLinkGenerated != null && paymentLinkGenerated) {
                return ReservationStep.PAYMENT_INSTRUCTIONS;
            }
            return ReservationStep.COMPLETE_RESERVATION;
        }

        // Check if summary has been shown
        Boolean summaryShown = context.getWorkflowStateValue("summaryShown", Boolean.class);
        if (summaryShown != null && summaryShown) {
            return ReservationStep.COMPLETE_RESERVATION;
        }

        // We have all info but haven't shown summary yet
        return ReservationStep.CONFIRM_RESERVATION_SUMMARY;
    }

    /**
     * Validates that the stored step matches the actual state and updates if needed
     */
    private ReservationStep validateAndUpdateStep(ReservationStep storedStep,
            MatrixAssistantAgentContextService.AgentContext context) {
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
        Boolean reservationCreated = context.getWorkflowStateValue("reservationCreated", Boolean.class);
        Boolean summaryShown = context.getWorkflowStateValue("summaryShown", Boolean.class);
        Boolean paymentLinkGenerated = context.getWorkflowStateValue("paymentLinkGenerated", Boolean.class);

        // Step 7: Payment confirmed (background, not managed here)
        if (storedStep == ReservationStep.PAYMENT_CONFIRMED) {
            return storedStep;
        }

        // Step 6: Payment instructions (after reservation created and payment link
        // generated)
        if (reservationCreated != null && reservationCreated &&
                paymentLinkGenerated != null && paymentLinkGenerated) {
            if (storedStep != ReservationStep.PAYMENT_INSTRUCTIONS) {
                log.info("Upgrading step from {} to PAYMENT_INSTRUCTIONS", storedStep);
                return ReservationStep.PAYMENT_INSTRUCTIONS;
            }
            return ReservationStep.PAYMENT_INSTRUCTIONS;
        }

        // Step 5: Complete reservation (after summary shown, reservation not yet
        // created)
        if (summaryShown != null && summaryShown &&
                (reservationCreated == null || !reservationCreated)) {
            if (storedStep != ReservationStep.COMPLETE_RESERVATION) {
                log.info("Upgrading step from {} to COMPLETE_RESERVATION", storedStep);
                return ReservationStep.COMPLETE_RESERVATION;
            }
            return ReservationStep.COMPLETE_RESERVATION;
        }

        // Step 4: Confirm reservation summary (all info collected, summary not shown)
        if (spaceId != null && startDateObj != null && endDateObj != null &&
                (summaryShown == null || !summaryShown)) {
            if (storedStep != ReservationStep.CONFIRM_RESERVATION_SUMMARY) {
                log.info("Upgrading step from {} to CONFIRM_RESERVATION_SUMMARY (all info collected)", storedStep);
                return ReservationStep.CONFIRM_RESERVATION_SUMMARY;
            }
            return ReservationStep.CONFIRM_RESERVATION_SUMMARY;
        }

        // Step 3: Choose period (spaceId exists but dates missing)
        if (spaceId != null && (startDateObj == null || endDateObj == null)) {
            if (storedStep.ordinal() > ReservationStep.CHOOSE_PERIOD.ordinal()) {
                log.warn("Downgrading step from {} to CHOOSE_PERIOD (missing dates)", storedStep);
                return ReservationStep.CHOOSE_PERIOD;
            }
            return ReservationStep.CHOOSE_PERIOD;
        }

        // Step 2: Choose space (spaceId exists)
        if (spaceId != null) {
            if (storedStep == ReservationStep.REQUEST_SPACE_INFO) {
                log.info("Upgrading step from REQUEST_SPACE_INFO to CHOOSE_SPACE (spaceId found)");
                return ReservationStep.CHOOSE_SPACE;
            }
            // If we're at CHOOSE_SPACE and have spaceId, stay there or move to
            // CHOOSE_PERIOD
            if (storedStep == ReservationStep.CHOOSE_SPACE) {
                if (startDateObj != null && endDateObj != null) {
                    return ReservationStep.CHOOSE_PERIOD;
                }
                return ReservationStep.CHOOSE_SPACE;
            }
        }

        // Step 1: Request space info (no spaceId)
        if (spaceId == null) {
            if (storedStep != ReservationStep.REQUEST_SPACE_INFO) {
                log.warn("Resetting step to REQUEST_SPACE_INFO (no spaceId)");
                return ReservationStep.REQUEST_SPACE_INFO;
            }
            return ReservationStep.REQUEST_SPACE_INFO;
        }

        return storedStep;
    }

    /**
     * Builds a step-specific system prompt optimized for the current step
     */
    private String buildSystemPromptForStep(
            ReservationStep step,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(addDateInformation(baseSystemPrompt));
        promptBuilder.append("\n\n");

        String stepPrompt = null;
        switch (step) {
            case REQUEST_SPACE_INFO:
                stepPrompt = step1Prompt;
                break;
            case CHOOSE_SPACE:
                stepPrompt = step2Prompt;
                break;
            case CHOOSE_PERIOD:
                stepPrompt = step3Prompt;
                break;
            case CONFIRM_RESERVATION_SUMMARY:
                stepPrompt = step4Prompt;
                break;
            case COMPLETE_RESERVATION:
                stepPrompt = step5Prompt;
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
                stepPrompt = step1Prompt;
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

    @Override
    protected boolean shouldUseRAG() {
        return false; // Reservation agent doesn't need RAG
    }

    /**
     * Override processMistralResponse to update context based on tool results
     */
    @Override
    protected Mono<String> processMistralResponse(
            Map<String, Object> response,
            MatrixAssistantAuthContext authContext,
            List<Map<String, Object>> previousMessages,
            String ragContext,
            List<Map<String, Object>> tools) {

        // Process tool calls and update context
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return Mono.just("Aucune réponse générée.");
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

        if (toolCalls != null && !toolCalls.isEmpty()) {
            // Update context based on tool calls before processing
            updateContextFromToolCalls(toolCalls, authContext);
        }

        return super.processMistralResponse(response, authContext, previousMessages, ragContext, tools)
                .flatMap(finalResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null || finalResponse == null) {
                        return Mono.just(finalResponse);
                    }

                    MatrixAssistantAgentContextService.AgentContext context = agentContextService
                            .getOrCreateContext(roomId);
                    String currentStepStr = context.getWorkflowStateValue("reservationStep", String.class);

                    // Step 4: Automatically mark summaryShown=true after LLM displays the summary
                    if (ReservationStep.CONFIRM_RESERVATION_SUMMARY.name().equals(currentStepStr)) {
                        // Check if we have all info (spaceId, dates) - if so, the LLM just showed the
                        // summary
                        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
                        Object startDateObj = context.getWorkflowState().get("startDate");
                        Object endDateObj = context.getWorkflowState().get("endDate");

                        if (spaceId != null && startDateObj != null && endDateObj != null) {
                            // Mark summary as shown and move to Step 5
                            context.updateWorkflowState("summaryShown", true);
                            context.updateWorkflowState("reservationStep", ReservationStep.COMPLETE_RESERVATION.name());
                            log.info("✅ Step 4: Summary automatically shown, moving to Step 5: COMPLETE_RESERVATION");
                        }
                    }

                    // Step 5: Detect confirmation in LLM response and create reservation
                    // automatically
                    if (ReservationStep.COMPLETE_RESERVATION.name().equals(currentStepStr)) {
                        String lowerResponse = finalResponse.toLowerCase();
                        // Check if LLM detected confirmation (positive response)
                        boolean isConfirmed = lowerResponse.contains("parfait") ||
                                lowerResponse.contains("procède") ||
                                lowerResponse.contains("création") ||
                                lowerResponse.contains("réservation créée") ||
                                lowerResponse.contains("reservation created") ||
                                (lowerResponse.contains("confir") && !lowerResponse.contains("annul"));

                        // Check if LLM detected cancellation (negative response)
                        boolean isCancelled = lowerResponse.contains("annul") ||
                                lowerResponse.contains("cancel") ||
                                (lowerResponse.contains("non") && !lowerResponse.contains("confir"));

                        if (isConfirmed && !isCancelled) {
                            // User confirmed - create reservation automatically
                            String spaceId = context.getWorkflowStateValue("spaceId", String.class);
                            Object startDateObj = context.getWorkflowState().get("startDate");
                            Object endDateObj = context.getWorkflowState().get("endDate");
                            String startTime = context.getWorkflowStateValue("startTime", String.class);
                            String endTime = context.getWorkflowStateValue("endTime", String.class);

                            if (spaceId != null && startDateObj != null && endDateObj != null) {
                                log.info(
                                        "✅ Step 5: Confirmation detected, creating reservation automatically (spaceId={}, startDate={}, endDate={})",
                                        spaceId, startDateObj, endDateObj);

                                // Build arguments for create_reservation
                                Map<String, Object> createReservationArgs = new java.util.HashMap<>();
                                createReservationArgs.put("spaceId", spaceId);
                                createReservationArgs.put("startDate", startDateObj.toString());
                                createReservationArgs.put("endDate", endDateObj.toString());
                                if (startTime != null && !startTime.isEmpty()) {
                                    createReservationArgs.put("startTime", startTime);
                                }
                                if (endTime != null && !endTime.isEmpty()) {
                                    createReservationArgs.put("endTime", endTime);
                                }

                                // Call reservation handler directly (no MCP adapter, no HTTP)
                                try {
                                    MatrixMCPModels.MCPToolResult result = reservationHandler.createReservation(
                                            createReservationArgs,
                                            authContext);

                                    // Extract result text
                                    String resultText = result.getContent().stream()
                                            .map(MCPContent::getText)
                                            .filter(text -> text != null)
                                            .collect(java.util.stream.Collectors.joining("\n"));

                                    // Update context and move to Step 6
                                    if (!result.isError() && resultText != null) {
                                        String lowerText = resultText.toLowerCase();
                                        if (lowerText.contains("réservation créée") ||
                                                lowerText.contains("reservation created") ||
                                                lowerText.contains("créée avec succès") ||
                                                lowerText.contains("created successfully")) {
                                            log.info("✅ Step 5: Reservation created successfully, moving to Step 6");
                                            context.updateWorkflowState("reservationCreated", true);

                                            // Extract reservation ID from response
                                            String extractedReservationId = extractReservationIdFromResponse(
                                                    resultText);
                                            if (extractedReservationId != null) {
                                                context.updateWorkflowState("reservationId", extractedReservationId);
                                                log.info("📋 Step 5: Extracted reservationId {} from response",
                                                        extractedReservationId);
                                            }

                                            // Move to Step 6 - will be handled automatically in next call
                                            context.updateWorkflowState("reservationStep",
                                                    ReservationStep.PAYMENT_INSTRUCTIONS.name());

                                            // Return confirmation message - Step 6 will be triggered on next user
                                            // message
                                            // or we can trigger it immediately
                                            return Mono.just(resultText);
                                        }
                                    }

                                    // If error, return error message
                                    if (result.isError()) {
                                        log.warn("Error creating reservation: {}", resultText);
                                        return Mono.just(
                                                resultText != null ? resultText
                                                        : "Erreur lors de la création de la réservation.");
                                    }

                                    // Return result
                                    return Mono
                                            .just(resultText != null ? resultText : "Réservation créée avec succès.");
                                } catch (Exception e) {
                                    log.error("Exception creating reservation: {}", e.getMessage(), e);
                                    return Mono.just("Erreur lors de la création de la réservation: " + e.getMessage());
                                }
                            }
                        } else if (isCancelled) {
                            // User cancelled - clear context
                            log.info("❌ Step 5: Cancellation detected, clearing context");
                            agentContextService.clearContext(roomId);
                        }
                    }

                    // After final response, check if reservation was created successfully
                    // If so, clear the context
                    if (finalResponse.contains("réservation créée") ||
                            finalResponse.contains("reservation created") ||
                            finalResponse.contains("réservation confirmée") ||
                            finalResponse.contains("reservation confirmed")) {
                        log.info("Reservation completed, clearing context for room {}", roomId);
                        agentContextService.clearContext(roomId);
                    }

                    return Mono.just(finalResponse);
                });
    }

    /**
     * Override to update context from tool results (not just tool calls)
     * This allows us to extract spaceId from list_spaces response
     */
    @Override
    protected void processToolResults(
            List<Map<String, Object>> toolCalls,
            List<Map<String, Object>> toolResults,
            MatrixAssistantAuthContext authContext) {

        String roomId = authContext.getRoomId();
        if (roomId == null) {
            return;
        }

        MatrixAssistantAgentContextService.AgentContext context = agentContextService.getOrCreateContext(roomId);

        // Process each tool result to extract information
        for (int i = 0; i < toolCalls.size() && i < toolResults.size(); i++) {
            Map<String, Object> toolCall = toolCalls.get(i);
            Map<String, Object> toolResult = toolResults.get(i);

            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String functionName = (String) function.get("name");
            String resultText = (String) toolResult.get("result");

            if (resultText == null) {
                continue;
            }

            // Step 2: Extract spaceId from list_spaces response (user chose a space)
            // Note: User may also provide period in the same message, which will be
            // extracted
            // in check_space_availability call (if made in the same turn)
            if ("list_spaces".equals(functionName)) {
                String extractedSpaceId = extractSpaceIdFromListSpacesResponse(resultText);
                if (extractedSpaceId != null) {
                    log.info("📋 Step 2: Extracted spaceId {} from list_spaces response", extractedSpaceId);
                    context.updateWorkflowState("spaceId", extractedSpaceId);
                    // Check if we already have dates (user provided space + period together)
                    // This will be checked after check_space_availability processes the dates
                    // For now, if check_space_availability was also called, it will handle the
                    // transition
                }
            }

            // Step 3: Extract dates/times from check_space_availability arguments
            // Note: This can be called in Step 2 if user provides space + period together
            if ("check_space_availability".equals(functionName)) {
                try {
                    String functionArgumentsJson = (String) function.get("arguments");
                    if (functionArgumentsJson != null) {
                        Map<String, Object> functionArguments = objectMapper.readValue(
                                functionArgumentsJson,
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                        Object spaceIdObj = functionArguments.get("spaceId");
                        if (spaceIdObj != null) {
                            log.info("📅 Step 3: Extracted spaceId {} from check_space_availability",
                                    spaceIdObj.toString());
                            context.updateWorkflowState("spaceId", spaceIdObj.toString());
                        }
                        Object startDateObj = functionArguments.get("startDate");
                        if (startDateObj != null) {
                            log.info("📅 Step 3: Extracted startDate {} from check_space_availability",
                                    startDateObj.toString());
                            context.updateWorkflowState("startDate", startDateObj.toString());
                        }
                        Object endDateObj = functionArguments.get("endDate");
                        if (endDateObj != null) {
                            log.info("📅 Step 3: Extracted endDate {} from check_space_availability",
                                    endDateObj.toString());
                            context.updateWorkflowState("endDate", endDateObj.toString());
                        }
                        Object startTimeObj = functionArguments.get("startTime");
                        if (startTimeObj != null) {
                            log.info("📅 Step 3: Extracted startTime {} from check_space_availability",
                                    startTimeObj.toString());
                            context.updateWorkflowState("startTime", startTimeObj.toString());
                        }
                        Object endTimeObj = functionArguments.get("endTime");
                        if (endTimeObj != null) {
                            log.info("📅 Step 3: Extracted endTime {} from check_space_availability",
                                    endTimeObj.toString());
                            context.updateWorkflowState("endTime", endTimeObj.toString());
                        }

                        // Check if we have all required info for Step 4 (summary)
                        if (spaceIdObj != null && startDateObj != null && endDateObj != null) {
                            // We have all info, move to Step 4 (summary will be shown automatically)
                            context.updateWorkflowState("reservationStep",
                                    ReservationStep.CONFIRM_RESERVATION_SUMMARY.name());
                            log.info("✅ Step 3 completed, moving to Step 4: CONFIRM_RESERVATION_SUMMARY");
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse check_space_availability arguments: {}", e.getMessage());
                }
            }

            // Step 5: Mark reservation as created after successful creation
            if ("create_reservation".equals(functionName)) {
                String lowerResult = resultText.toLowerCase();
                if (lowerResult.contains("réservation créée") ||
                        lowerResult.contains("reservation created") ||
                        lowerResult.contains("créée avec succès") ||
                        lowerResult.contains("created successfully")) {
                    log.info("✅ Step 5: Reservation created successfully");
                    context.updateWorkflowState("reservationCreated", true);
                    context.updateWorkflowState("reservationStep", ReservationStep.PAYMENT_INSTRUCTIONS.name());
                    // Don't clear context yet - we need to show payment instructions
                }
            }

            // Step 6: Mark payment link as generated
            if ("generate_payment_link".equals(functionName)) {
                if (!resultText.toLowerCase().contains("error")) {
                    log.info("✅ Step 6: Payment link generated");
                    context.updateWorkflowState("paymentLinkGenerated", true);
                    // Clear context after payment instructions are shown
                    agentContextService.clearContext(roomId);
                }
            }
        }
    }

    /**
     * Extracts reservation ID from create_reservation response text
     * Format: "ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" or "Réservation ID: ..."
     */
    private String extractReservationIdFromResponse(String responseText) {
        if (responseText == null || responseText.isEmpty()) {
            return null;
        }

        // Look for "ID: " or "Réservation ID:" followed by a UUID pattern
        java.util.regex.Pattern uuidPattern = java.util.regex.Pattern.compile(
                "(?i)(?:réservation\\s+)?id[:\\s]+([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = uuidPattern.matcher(responseText);

        if (matcher.find()) {
            String uuid = matcher.group(1);
            // Validate it's a proper UUID
            try {
                UUID.fromString(uuid);
                return uuid;
            } catch (IllegalArgumentException e) {
                log.warn("Extracted invalid UUID from reservation response: {}", uuid);
            }
        }

        return null;
    }

    /**
     * Extracts spaceId UUID from list_spaces response text
     * Format: " - ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
     */
    private String extractSpaceIdFromListSpacesResponse(String responseText) {
        if (responseText == null || responseText.isEmpty()) {
            return null;
        }

        // Look for "ID: " followed by a UUID pattern
        java.util.regex.Pattern uuidPattern = java.util.regex.Pattern.compile(
                "(?i)(?:id|identifiant)[:\\s]+([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = uuidPattern.matcher(responseText);

        if (matcher.find()) {
            String uuid = matcher.group(1);
            // Validate it's a proper UUID
            try {
                UUID.fromString(uuid);
                return uuid;
            } catch (IllegalArgumentException e) {
                log.warn("Extracted invalid UUID from list_spaces response: {}", uuid);
            }
        }

        return null;
    }

    /**
     * Updates agent context based on tool calls
     */
    @SuppressWarnings("unchecked")
    private void updateContextFromToolCalls(List<Map<String, Object>> toolCalls,
            MatrixAssistantAuthContext authContext) {
        String roomId = authContext.getRoomId();
        if (roomId == null) {
            return;
        }

        MatrixAssistantAgentContextService.AgentContext context = agentContextService.getOrCreateContext(roomId);

        for (Map<String, Object> toolCall : toolCalls) {
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String functionName = (String) function.get("name");
            String functionArgumentsJson = (String) function.get("arguments");

            try {
                Map<String, Object> functionArguments = objectMapper.readValue(
                        functionArgumentsJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                // Update context based on tool call
                if ("list_spaces".equals(functionName)) {
                    // Context will be updated after we get the result and identify the space
                    // This is handled by the LLM in its response
                } else if ("create_reservation".equals(functionName)) {
                    // Extract spaceId, dates, times from arguments
                    Object spaceIdObj = functionArguments.get("spaceId");
                    if (spaceIdObj != null) {
                        context.updateWorkflowState("spaceId", spaceIdObj.toString());
                    }
                    Object startDateObj = functionArguments.get("startDate");
                    if (startDateObj != null) {
                        context.updateWorkflowState("startDate", startDateObj.toString());
                    }
                    Object endDateObj = functionArguments.get("endDate");
                    if (endDateObj != null) {
                        context.updateWorkflowState("endDate", endDateObj.toString());
                    }
                    Object startTimeObj = functionArguments.get("startTime");
                    if (startTimeObj != null) {
                        context.updateWorkflowState("startTime", startTimeObj.toString());
                    }
                    Object endTimeObj = functionArguments.get("endTime");
                    if (endTimeObj != null) {
                        context.updateWorkflowState("endTime", endTimeObj.toString());
                    }
                } else if ("check_space_availability".equals(functionName)) {
                    // Extract spaceId and dates from arguments
                    Object spaceIdObj = functionArguments.get("spaceId");
                    if (spaceIdObj != null) {
                        context.updateWorkflowState("spaceId", spaceIdObj.toString());
                    }
                    Object startDateObj = functionArguments.get("startDate");
                    if (startDateObj != null) {
                        context.updateWorkflowState("startDate", startDateObj.toString());
                    }
                    Object endDateObj = functionArguments.get("endDate");
                    if (endDateObj != null) {
                        context.updateWorkflowState("endDate", endDateObj.toString());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to update context from tool call {}: {}", functionName, e.getMessage());
            }
        }
    }

    /**
     * Generates a formatted reservation summary to display to the user
     * before asking for confirmation in Step 4
     */
    private Mono<String> generateReservationSummary(
            String spaceId,
            ReservationPeriod period,
            MatrixAssistantAuthContext authContext) {

        try {
            UUID spaceUuid = UUID.fromString(spaceId);
            SpaceEntity space = spacesService.getSpaceById(spaceUuid);

            if (space == null) {
                log.warn("Space not found for spaceId: {}", spaceId);
                Locale locale = Locale.FRENCH; // Default if no context available
                return Mono.just(messageSource.getMessage("matrix.reservation.error.spaceNotFound", null, locale));
            }

            LocalDate startDate = LocalDate.parse(period.getStartDate());
            LocalDate endDate = LocalDate.parse(period.getEndDate());
            long nights = ChronoUnit.DAYS.between(startDate, endDate);

            // Get locale from context (default to French)
            Locale locale = Locale.FRENCH;
            String roomId = authContext.getRoomId();
            if (roomId != null) {
                MatrixAssistantAgentContextService.AgentContext context = agentContextService.getContext(roomId);
                if (context != null) {
                    String localeStr = context.getWorkflowStateValue("locale", String.class);
                    if (localeStr != null && !localeStr.isEmpty()) {
                        locale = Locale.forLanguageTag(localeStr);
                    }
                }
            }

            StringBuilder summary = new StringBuilder();
            summary.append("📋 **").append(messageSource.getMessage("matrix.reservation.summary.title", null, locale))
                    .append("**\n\n");
            summary.append("🏠 **").append(messageSource.getMessage("matrix.reservation.space", null, locale))
                    .append(":** ").append(space.getName()).append("\n");
            summary.append("📅 **").append(messageSource.getMessage("matrix.reservation.from", null, locale))
                    .append(":** ").append(startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

            if (period.getStartTime() != null && !period.getStartTime().isEmpty()) {
                summary.append(" ").append(messageSource.getMessage("matrix.reservation.at", null, locale))
                        .append(" ").append(period.getStartTime());
            }
            summary.append("\n");

            summary.append("📅 **").append(messageSource.getMessage("matrix.reservation.to", null, locale))
                    .append(":** ").append(endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            if (period.getEndTime() != null && !period.getEndTime().isEmpty()) {
                summary.append(" ").append(messageSource.getMessage("matrix.reservation.at", null, locale))
                        .append(" ").append(period.getEndTime());
            }
            summary.append("\n");

            if (nights > 0) {
                summary.append("🌙 **").append(messageSource.getMessage("matrix.reservation.duration", null, locale))
                        .append(":** ").append(nights).append(" ")
                        .append(messageSource.getMessage("matrix.reservation.night" + (nights > 1 ? "s" : ""), null,
                                locale))
                        .append("\n");
            }

            summary.append("\n");
            summary.append(messageSource.getMessage("matrix.reservation.summary.confirmQuestion", null, locale));

            return Mono.just(summary.toString());

        } catch (Exception e) {
            log.error("Error generating reservation summary: {}", e.getMessage(), e);
            return Mono.just("Erreur lors de la génération du résumé de réservation.");
        }
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
     * Gets locale from context or defaults to "fr"
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

    /**
     * Generates a templated confirmation message after reservation creation
     * Uses MessageSource for internationalization based on user's locale
     */
    private String generateReservationConfirmationMessage(
            ReservationEntity reservation,
            SpaceEntity space,
            LocalDate startDate,
            LocalDate endDate,
            String startTime,
            String endTime,
            MatrixAssistantAgentContextService.AgentContext context) {

        Locale locale = getLocaleFromContext(context);

        StringBuilder message = new StringBuilder();
        message.append("✅ **").append(messageSource.getMessage("matrix.reservation.createdSuccess", null, locale))
                .append("**\n\n");
        message.append("📋 **").append(messageSource.getMessage("matrix.reservation.details", null, locale))
                .append(":**\n");
        message.append("- **").append(messageSource.getMessage("matrix.reservation.space", null, locale))
                .append(":** ").append(space.getName()).append("\n");
        message.append("- **").append(messageSource.getMessage("matrix.reservation.from", null, locale))
                .append(":** ").append(startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        if (startTime != null && !startTime.isEmpty() && !startTime.equals("00:00")) {
            message.append(" ").append(messageSource.getMessage("matrix.reservation.at", null, locale))
                    .append(" ").append(startTime);
        }
        message.append("\n");
        message.append("- **").append(messageSource.getMessage("matrix.reservation.to", null, locale))
                .append(":** ").append(endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        if (endTime != null && !endTime.isEmpty() && !endTime.equals("23:59")) {
            message.append(" ").append(messageSource.getMessage("matrix.reservation.at", null, locale))
                    .append(" ").append(endTime);
        }
        message.append("\n");

        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        if (nights > 0) {
            message.append("- **").append(messageSource.getMessage("matrix.reservation.duration", null, locale))
                    .append(":** ").append(nights).append(" ")
                    .append(messageSource.getMessage("matrix.reservation.night" + (nights > 1 ? "s" : ""), null,
                            locale))
                    .append("\n");
        }

        message.append("- **").append(messageSource.getMessage("matrix.reservation.id", null, locale))
                .append(":** ").append(reservation.getId()).append("\n");
        message.append("- **").append(messageSource.getMessage("matrix.reservation.status", null, locale))
                .append(":** ").append(getStatusDescription(reservation.getStatus(), locale)).append("\n");

        if (reservation.getTotalPrice() != null) {
            message.append("- **").append(messageSource.getMessage("matrix.reservation.totalPrice", null, locale))
                    .append(":** ").append(reservation.getTotalPrice()).append("€\n");
        }

        message.append("\n");

        // Check if payment is required
        if (reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT) {
            message.append("💳 **").append(messageSource.getMessage("matrix.reservation.paymentRequired", null, locale))
                    .append(":** ")
                    .append(messageSource.getMessage("matrix.reservation.paymentLinkWillBeGenerated", null, locale))
                    .append("\n");
        } else {
            message.append("✅ ").append(messageSource.getMessage("matrix.reservation.confirmed", null, locale))
                    .append("\n");
        }

        return message.toString();
    }

    /**
     * Generates a templated payment instructions message with payment link
     */
    private String generatePaymentInstructionsMessage(
            ReservationEntity reservation,
            String checkoutUrl,
            MatrixAssistantAgentContextService.AgentContext context) {

        Locale locale = getLocaleFromContext(context);

        StringBuilder message = new StringBuilder();
        message.append("💳 **")
                .append(messageSource.getMessage("matrix.reservation.payment.linkGenerated", null, locale))
                .append("**\n\n");
        message.append("📋 **").append(messageSource.getMessage("matrix.reservation.id", null, locale))
                .append(":** ").append(reservation.getId()).append("\n");
        message.append("🏠 **").append(messageSource.getMessage("matrix.reservation.space", null, locale))
                .append(":** ").append(reservation.getSpace().getName()).append("\n");

        if (reservation.getTotalPrice() != null) {
            message.append("💰 **").append(messageSource.getMessage("matrix.reservation.payment.amount", null, locale))
                    .append(":** ").append(reservation.getTotalPrice()).append("€\n");
        }

        message.append("\n");
        message.append("🔗 **").append(messageSource.getMessage("matrix.reservation.payment.link", null, locale))
                .append(":**\n");
        message.append(checkoutUrl).append("\n\n");
        message.append(messageSource.getMessage("matrix.reservation.payment.instructions", null, locale));

        return message.toString();
    }

    /**
     * Gets a human-readable description of reservation status
     * Uses MessageSource for internationalization
     */
    private String getStatusDescription(ReservationStatusForEntity status, Locale locale) {
        if (status == null) {
            return messageSource.getMessage("matrix.reservation.status.unknown", null, locale);
        }
        switch (status) {
            case PENDING_PAYMENT:
                return messageSource.getMessage("matrix.reservation.status.pendingPayment", null, locale);
            case CONFIRMED:
                return messageSource.getMessage("matrix.reservation.status.confirmed", null, locale);
            case CANCELLED:
                return messageSource.getMessage("matrix.reservation.status.cancelled", null, locale);
            case COMPLETED:
                return messageSource.getMessage("matrix.reservation.status.completed", null, locale);
            default:
                return status.toString();
        }
    }
}
