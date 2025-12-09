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
 * Handler for CHOOSE_PERIOD step.
 * Extracts period (dates and times) from user message or conversation history.
 */
@Component
@Slf4j
public class ChoosePeriodStepHandler extends BaseSpaceStepHandler {

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Override
    public SpaceStep getStep() {
        return SpaceStep.CHOOSE_PERIOD;
    }

    @Override
    public Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        log.info("🔄 CHOOSE_PERIOD handler processing message: {}",
                userMessage.substring(0, Math.min(50, userMessage.length())));

        // Get spaceId from context (required for this step)
        String spaceId = context != null ? context.getWorkflowStateValue("spaceId", String.class) : null;
        if (spaceId == null || spaceId.isEmpty()) {
            log.error("❌ CHOOSE_PERIOD: No spaceId in context, cannot proceed");
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Une erreur s'est produite : l'espace n'a pas été sélectionné.")
                    .build());
        }

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(SpaceStep.CHOOSE_PERIOD, context, authContext);

        // Get filtered tools for this step
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterToolsForStep(allTools, SpaceStep.CHOOSE_PERIOD);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse);
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        log.info("❌ CHOOSE_PERIOD: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse);
                    }

                    if (stepResponse.isAskingUser()) {
                        // Need more conversation - return response
                        log.info("🔄 CHOOSE_PERIOD: Status=ASK_USER, continuing conversation");
                        return Mono.just(stepResponse);
                    }

                    if (stepResponse.isCompleted() || stepResponse.isSwitchingStep()) {
                        // Validate period is present (required for COMPLETED)
                        if (!stepResponse.hasCompletePeriod()) {
                            log.error("❌ CHOOSE_PERIOD: COMPLETED/SWITCH_STEP status but period is incomplete!");
                            return Mono.just(SpaceStepResponse.builder()
                                    .status(SpaceStepResponse.StepStatus.ERROR)
                                    .response("Une erreur s'est produite : la période de réservation est incomplète.")
                                    .build());
                        }

                        // Store period
                        if (context != null) {
                            context.updateWorkflowState("startDate", stepResponse.getPeriod().getStartDate());
                            context.updateWorkflowState("endDate", stepResponse.getPeriod().getEndDate());
                            if (stepResponse.getPeriod().getStartTime() != null) {
                                context.updateWorkflowState("startTime", stepResponse.getPeriod().getStartTime());
                            }
                            if (stepResponse.getPeriod().getEndTime() != null) {
                                context.updateWorkflowState("endTime", stepResponse.getPeriod().getEndTime());
                            }
                            log.info("✅ CHOOSE_PERIOD: Stored period information");
                        }

                        // If switching step, ensure nextStep is set to CONFIRM_RESERVATION_SUMMARY
                        if (stepResponse.isSwitchingStep() && stepResponse.getNextStep() == null) {
                            return Mono.just(SpaceStepResponse.builder()
                                    .status(SpaceStepResponse.StepStatus.SWITCH_STEP)
                                    .nextStep(SpaceStep.CONFIRM_RESERVATION_SUMMARY)
                                    .response(stepResponse.getResponse())
                                    .spaceId(spaceId)
                                    .period(stepResponse.getPeriod())
                                    .build());
                        }
                    }

                    return Mono.just(stepResponse);
                });
    }
}
