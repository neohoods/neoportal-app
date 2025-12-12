package com.neohoods.portal.platform.assistant.services;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.model.SpaceStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for initializing Mistral Agents and Document Library at startup.
 * Creates all agents with their prompts and tools, and initializes the Document
 * Library.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MistralAgentsInitializationService {

    private final MistralAgentsService agentsService;
    private final MistralDocumentLibraryService documentLibraryService;
    private final MatrixAssistantMCPAdapter mcpAdapter;
    private final ResourceLoader resourceLoader;

    @Value("${neohoods.portal.matrix.assistant.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${neohoods.portal.matrix.assistant.ai.agents.enabled:true}")
    private boolean agentsEnabled;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-prompt.txt}")
    private String reservationAgentPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.router-prompt-file:classpath:matrix-assistant/prompts/router/matrix-assistant-router-prompt.txt}")
    private String routerPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-request-space-info.txt}")
    private String spaceInfoPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-choose-space.txt}")
    private String chooseSpacePromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-confirm-summary.txt}")
    private String confirmSummaryPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-complete-reservation.txt}")
    private String completeReservationPromptFile;

    @Value("${neohoods.portal.matrix.assistant.ai.reservation-agent-prompt-file:classpath:matrix-assistant/prompts/reservation/matrix-assistant-reservation-agent-payment-instructions.txt}")
    private String paymentInstructionsPromptFile;

    /**
     * Initializes all Mistral agents and Document Library at startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAgents() {
        if (!aiEnabled || !agentsEnabled) {
            log.info("Mistral Agents API is disabled, skipping initialization");
            return;
        }

        log.info("Initializing Mistral Agents and Document Library...");

        // Step 1: Initialize Document Library
        Mono<String> libraryIdMono = documentLibraryService.initializeLibrary()
                .doOnSuccess(libId -> {
                    if (libId != null && !libId.isEmpty()) {
                        log.info("✓ Document Library initialized with ID: {}", libId);
                    } else {
                        log.info("Document Library initialization skipped (RAG disabled or no documents)");
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Document Library initialization failed: {}", e.getMessage());
                    return Mono.just(""); // Continue even if library fails
                });

        // Step 2: Get library ID and create agents
        libraryIdMono
                .flatMap(libraryId -> {
                    List<String> libraryIds = new ArrayList<>();
                    if (libraryId != null && !libraryId.isEmpty()) {
                        libraryIds.add(libraryId);
                    }

                    // Get all MCP tools
                    List<MCPTool> allMCPTools = mcpAdapter.listTools();

                    // Create agents sequentially
                    return createRoutingAgent(allMCPTools, libraryIds)
                            .then(createSpaceStepAgents(allMCPTools, libraryIds))
                            .then(Mono.fromRunnable(() -> {
                                log.info("✓ All Mistral agents initialized successfully");
                            }));
                })
                .subscribe(
                        null,
                        error -> {
                            log.error("Failed to initialize Mistral agents: {}", error.getMessage(), error);
                        },
                        () -> {
                            log.info("Mistral Agents initialization completed");
                        });
    }

    /**
     * Creates the routing agent
     */
    private Mono<Void> createRoutingAgent(List<MCPTool> allMCPTools, List<String> libraryIds) {
        String prompt = loadPromptFile(routerPromptFile,
                "You are a workflow identifier for the NeoHoods Matrix assistant.");

        // Routing agent needs a function calling tool for structured output
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> routeTool = createRouteToWorkflowTool();
        tools.add(routeTool);

        MistralAgentsService.AgentConfig config = new MistralAgentsService.AgentConfig(
                MistralAgentsService.getRoutingAgentName(),
                "Routes user messages to the appropriate workflow (GENERAL, RESIDENT_INFO, SPACE, HELP, SUPPORT)",
                prompt);
        config.setTools(tools);
        config.setLibraryIds(libraryIds);
        config.setCompletionArgs(Map.of("temperature", 0.1, "top_p", 0.95));

        return agentsService.createAgent(config)
                .then()
                .doOnSuccess(v -> log.info("✓ Created routing agent"))
                .onErrorResume(e -> {
                    log.error("Failed to create routing agent: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Creates agents for all space steps
     */
    private Mono<Void> createSpaceStepAgents(List<MCPTool> allMCPTools, List<String> libraryIds) {
        List<Mono<Void>> agentCreations = new ArrayList<>();

        // REQUEST_SPACE_INFO agent
        agentCreations.add(createSpaceStepAgent(
                SpaceStep.REQUEST_SPACE_INFO,
                spaceInfoPromptFile,
                "Helps users find information about available spaces",
                getToolsForStep(SpaceStep.REQUEST_SPACE_INFO, allMCPTools),
                libraryIds));

        // CHOOSE_SPACE agent
        agentCreations.add(createSpaceStepAgent(
                SpaceStep.CHOOSE_SPACE,
                chooseSpacePromptFile,
                "Helps users choose a specific space and reservation period",
                getToolsForStep(SpaceStep.CHOOSE_SPACE, allMCPTools),
                libraryIds));

        // CONFIRM_RESERVATION_SUMMARY agent
        agentCreations.add(createSpaceStepAgent(
                SpaceStep.CONFIRM_RESERVATION_SUMMARY,
                confirmSummaryPromptFile,
                "Shows reservation summary and waits for user confirmation",
                getToolsForStep(SpaceStep.CONFIRM_RESERVATION_SUMMARY, allMCPTools),
                libraryIds));

        // COMPLETE_RESERVATION agent
        agentCreations.add(createSpaceStepAgent(
                SpaceStep.COMPLETE_RESERVATION,
                completeReservationPromptFile,
                "Detects user confirmation and creates the reservation",
                getToolsForStep(SpaceStep.COMPLETE_RESERVATION, allMCPTools),
                libraryIds));

        // PAYMENT_INSTRUCTIONS agent
        agentCreations.add(createSpaceStepAgent(
                SpaceStep.PAYMENT_INSTRUCTIONS,
                paymentInstructionsPromptFile,
                "Generates payment link and shows payment instructions",
                getToolsForStep(SpaceStep.PAYMENT_INSTRUCTIONS, allMCPTools),
                libraryIds));

        return Mono.when(agentCreations);
    }

    /**
     * Creates an agent for a specific space step
     */
    private Mono<Void> createSpaceStepAgent(
            SpaceStep step,
            String promptFile,
            String description,
            List<Map<String, Object>> tools,
            List<String> libraryIds) {

        String prompt = loadPromptFile(promptFile, "Space reservation step: " + step.name());

        String agentName = MistralAgentsService.getAgentNameForStep(step);
        MistralAgentsService.AgentConfig config = new MistralAgentsService.AgentConfig(
                agentName,
                description,
                prompt);
        config.setTools(tools);
        config.setLibraryIds(libraryIds);
        config.setCompletionArgs(Map.of("temperature", 0.7, "top_p", 0.95));

        return agentsService.createAgent(config)
                .then()
                .doOnSuccess(v -> log.info("✓ Created agent: {}", agentName))
                .onErrorResume(e -> {
                    log.error("Failed to create agent {}: {}", agentName, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Gets tools for a specific step
     */
    private List<Map<String, Object>> getToolsForStep(SpaceStep step, List<MCPTool> allMCPTools) {
        Set<String> availableToolNames = getAvailableToolNamesForStep(step);

        List<Map<String, Object>> tools = allMCPTools.stream()
                .filter(tool -> availableToolNames.contains(tool.getName()))
                .map(this::convertMCPToolToMistralFunction)
                .collect(Collectors.toList());

        // Add submit_reservation_step tool for CHOOSE_SPACE
        if (step == SpaceStep.CHOOSE_SPACE) {
            tools.add(createSubmitReservationStepTool());
        }

        return tools;
    }

    /**
     * Gets available tool names for a step
     */
    private Set<String> getAvailableToolNamesForStep(SpaceStep step) {
        Set<String> tools = new HashSet<>();
        tools.add("list_spaces");
        tools.add("get_space_info");
        tools.add("check_space_availability");

        // Add reservation tools only for later steps
        if (step == SpaceStep.CONFIRM_RESERVATION_SUMMARY ||
                step == SpaceStep.COMPLETE_RESERVATION ||
                step == SpaceStep.PAYMENT_INSTRUCTIONS) {
            tools.add("create_reservation");
            tools.add("generate_payment_link");
        }

        return tools;
    }

    /**
     * Converts an MCP tool to Mistral function format
     */
    private Map<String, Object> convertMCPToolToMistralFunction(MCPTool mcpTool) {
        Map<String, Object> function = new HashMap<>();
        function.put("type", "function");
        function.put("function", Map.of(
                "name", mcpTool.getName(),
                "description", mcpTool.getDescription() != null ? mcpTool.getDescription() : "",
                "parameters", mcpTool.getInputSchema() != null ? mcpTool.getInputSchema() : Map.of()));
        return function;
    }

    /**
     * Creates the route_to_workflow tool for routing agent
     */
    private Map<String, Object> createRouteToWorkflowTool() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");

        Map<String, Object> function = new HashMap<>();
        function.put("name", "route_to_workflow");
        function.put("description",
                "Route the user's message to the appropriate workflow. You MUST call this function with the workflow type.");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> workflowTypeField = new HashMap<>();
        workflowTypeField.put("type", "string");
        workflowTypeField.put("enum", java.util.Arrays.asList("GENERAL", "RESIDENT_INFO", "SPACE", "HELP", "SUPPORT"));
        workflowTypeField.put("description", "The workflow type to route to");
        properties.put("workflowType", workflowTypeField);

        parameters.put("properties", properties);
        parameters.put("required", java.util.Arrays.asList("workflowType"));
        function.put("parameters", parameters);

        tool.put("function", function);
        return tool;
    }

    /**
     * Creates the submit_reservation_step tool for CHOOSE_SPACE agent
     */
    private Map<String, Object> createSubmitReservationStepTool() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");

        Map<String, Object> function = new HashMap<>();
        function.put("name", "submit_reservation_step");
        function.put("description",
                "Submit the current reservation step result. Use this to return structured JSON response with status, spaceId, period, etc.");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();

        // Status field
        Map<String, Object> statusField = new HashMap<>();
        statusField.put("type", "string");
        statusField.put("enum", java.util.Arrays.asList("PENDING", "COMPLETED", "CANCELED", "ASK_USER", "SWITCH_STEP"));
        statusField.put("description", "Step status");
        properties.put("status", statusField);

        // Response field
        Map<String, Object> responseField = new HashMap<>();
        responseField.put("type", "string");
        responseField.put("description", "Response message to user");
        properties.put("response", responseField);

        // SpaceId field
        Map<String, Object> spaceIdField = new HashMap<>();
        spaceIdField.put("type", "string");
        spaceIdField.put("description", "Space UUID (required if COMPLETED or SWITCH_STEP)");
        properties.put("spaceId", spaceIdField);

        // Period field
        Map<String, Object> periodField = new HashMap<>();
        periodField.put("type", "object");
        Map<String, Object> periodProperties = new HashMap<>();
        periodProperties.put("startDate", Map.of("type", "string", "description", "Start date (YYYY-MM-DD)"));
        periodProperties.put("endDate", Map.of("type", "string", "description", "End date (YYYY-MM-DD)"));
        periodProperties.put("startTime", Map.of("type", "string", "description", "Start time (HH:mm)"));
        periodProperties.put("endTime", Map.of("type", "string", "description", "End time (HH:mm)"));
        periodField.put("properties", periodProperties);
        properties.put("period", periodField);

        // NextStep field (for SWITCH_STEP)
        Map<String, Object> nextStepField = new HashMap<>();
        nextStepField.put("type", "string");
        nextStepField.put("enum", java.util.Arrays.asList("CONFIRM_RESERVATION_SUMMARY", "COMPLETE_RESERVATION"));
        nextStepField.put("description", "Next step to transition to (required if SWITCH_STEP)");
        properties.put("nextStep", nextStepField);

        // AvailableSpaces field (for ASK_USER)
        Map<String, Object> availableSpacesField = new HashMap<>();
        availableSpacesField.put("type", "object");
        availableSpacesField.put("description",
                "Map of space numbers to UUIDs (e.g., {\"7\": \"uuid-7\", \"23\": \"uuid-23\"})");
        properties.put("availableSpaces", availableSpacesField);

        parameters.put("properties", properties);
        parameters.put("required", java.util.Arrays.asList("status", "response"));
        function.put("parameters", parameters);

        tool.put("function", function);
        return tool;
    }

    /**
     * Loads a prompt file
     */
    private String loadPromptFile(String filePath, String defaultValue) {
        try {
            Resource resource = resourceLoader.getResource(filePath);
            if (resource.exists() && resource.isReadable()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    if (content != null && !content.trim().isEmpty()) {
                        return content.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load prompt file {}: {}, using default", filePath, e.getMessage());
        }
        return defaultValue;
    }
}


