package com.neohoods.portal.platform.assistant.workflows.space.steps;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handler for REQUEST_SPACE_INFO step.
 * Helps user identify which space they want to reserve by providing information
 * about available spaces.
 */
@Component
@Slf4j
public class RequestSpaceInfoStepHandler extends BaseSpaceStepHandler {

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Override
    public SpaceStep getStep() {
        return SpaceStep.REQUEST_SPACE_INFO;
    }

    @Override
    public Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        log.info("🔄 REQUEST_SPACE_INFO handler processing message: {}",
                userMessage.substring(0, Math.min(50, userMessage.length())));

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(SpaceStep.REQUEST_SPACE_INFO, context, authContext);

        // Get filtered tools for this step (exclude create_reservation and
        // generate_payment_link)
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterToolsForStep(allTools, SpaceStep.REQUEST_SPACE_INFO);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse);
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        // User canceled - clear context
                        log.info("❌ REQUEST_SPACE_INFO: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse);
                    }

                    // Store locale - use user's preferred language from userEntity, or
                    // LLM-detected, or default to "fr"
                    String userLocale = null;
                    try {
                        // Try to get user from authContext
                        // Note: This requires access to UsersRepository, which we don't have here
                        // We'll rely on LLM-detected locale or default
                        if (stepResponse.getLocale() != null && !stepResponse.getLocale().isEmpty()) {
                            userLocale = stepResponse.getLocale();
                            log.info("🌐 REQUEST_SPACE_INFO: Using locale detected by LLM: {}", userLocale);
                        } else {
                            userLocale = "fr"; // Default fallback
                            log.info("🌐 REQUEST_SPACE_INFO: No locale found, defaulting to 'fr'");
                        }
                    } catch (Exception e) {
                        log.debug("Could not get user locale: {}", e.getMessage());
                        userLocale = "fr";
                    }

                    if (context != null) {
                        context.updateWorkflowState("locale", userLocale);
                    }

                    // Guardrail: if response is missing or generic error, provide a clear prompt-like message
                    String resp = stepResponse.getResponse();
                    if (resp == null || resp.isBlank()
                            || resp.toLowerCase().contains("erreur lors du traitement")) {
                        String fallback = "Voici les places de parking disponibles. Quelle place souhaitez-vous réserver ?";
                        return Mono.just(SpaceStepResponse.builder()
                                .status(SpaceStepResponse.StepStatus.ASK_USER)
                                .response(fallback)
                                .build());
                    }

                    // REQUEST_SPACE_INFO is purely informational - we never store spaceId here
                    // If user has chosen a space, the response should have SWITCH_STEP to
                    // CHOOSE_SPACE
                    // or ASK_USER if more info is needed
                    log.info("🔄 REQUEST_SPACE_INFO: Status={}, continuing workflow", stepResponse.getStatus());
                    return Mono.just(stepResponse);
                });
    }
}
