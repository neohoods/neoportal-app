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
 * Handler for CHOOSE_SPACE step.
 * Identifies the spaceId UUID from user's choice and may also extract period.
 */
@Component
@Slf4j
public class ChooseSpaceStepHandler extends BaseSpaceStepHandler {

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Override
    public SpaceStep getStep() {
        return SpaceStep.CHOOSE_SPACE;
    }

    @Override
    public Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        log.info("🔄 CHOOSE_SPACE handler processing message: {}",
                userMessage.substring(0, Math.min(50, userMessage.length())));

        // Build step-specific system prompt
        String systemPrompt = buildSystemPromptForStep(SpaceStep.CHOOSE_SPACE, context, authContext);

        // Get filtered tools for this step
        List<MCPTool> allTools = mcpAdapter.listTools();
        List<Map<String, Object>> tools = filterToolsForStep(allTools, SpaceStep.CHOOSE_SPACE);

        // Call Mistral API with JSON response format
        return callMistralAPIWithJSONResponse(userMessage, conversationHistory, systemPrompt, tools, authContext)
                .flatMap(stepResponse -> {
                    String roomId = authContext.getRoomId();
                    if (roomId == null) {
                        return Mono.just(stepResponse);
                    }

                    // Handle different statuses
                    if (stepResponse.isCanceled()) {
                        log.info("❌ CHOOSE_SPACE: User canceled, clearing context");
                        agentContextService.clearContext(roomId);
                        return Mono.just(stepResponse);
                    }

                    if (stepResponse.isAskingUser()) {
                        // Need more conversation - return response
                        log.info("🔄 CHOOSE_SPACE: Status=ASK_USER, continuing conversation");
                        return Mono.just(stepResponse);
                    }

                    if (stepResponse.isCompleted() || stepResponse.isSwitchingStep()) {
                        // Validate spaceId is present (required for COMPLETED)
                        if (!stepResponse.hasSpaceId()) {
                            log.error("❌ CHOOSE_SPACE: COMPLETED/SWITCH_STEP status but spaceId is missing!");
                            return Mono.just(SpaceStepResponse.builder()
                                    .status(SpaceStepResponse.StepStatus.ERROR)
                                    .response("Une erreur s'est produite : l'identifiant de l'espace est manquant.")
                                    .build());
                        }

                        // Store spaceId
                        if (context != null) {
                            context.updateWorkflowState("spaceId", stepResponse.getSpaceId());
                            log.info("✅ CHOOSE_SPACE: Stored spaceId={}", stepResponse.getSpaceId());
                        }

                        // If period is also present, we can switch directly to CHOOSE_PERIOD or
                        // CONFIRM_RESERVATION_SUMMARY
                        if (stepResponse.hasCompletePeriod() && stepResponse.getPeriod() != null) {
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
                                log.info("✅ CHOOSE_SPACE: Also stored period information");
                            }

                            // If switching step, ensure nextStep is set
                            if (stepResponse.isSwitchingStep() && stepResponse.getNextStep() == null) {
                                // Auto-determine next step: if period is complete, go to
                                // CONFIRM_RESERVATION_SUMMARY
                                return Mono.just(SpaceStepResponse.builder()
                                        .status(SpaceStepResponse.StepStatus.SWITCH_STEP)
                                        .nextStep(SpaceStep.CONFIRM_RESERVATION_SUMMARY)
                                        .response(stepResponse.getResponse())
                                        .spaceId(stepResponse.getSpaceId())
                                        .period(stepResponse.getPeriod())
                                        .build());
                            }
                        } else if (stepResponse.isSwitchingStep() && stepResponse.getNextStep() == null) {
                            // If switching but no period, go to CHOOSE_PERIOD
                            return Mono.just(SpaceStepResponse.builder()
                                    .status(SpaceStepResponse.StepStatus.SWITCH_STEP)
                                    .nextStep(SpaceStep.CHOOSE_PERIOD)
                                    .response(stepResponse.getResponse())
                                    .spaceId(stepResponse.getSpaceId())
                                    .build());
                        }
                    }

                    return Mono.just(stepResponse);
                });
    }
}
