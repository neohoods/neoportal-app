package com.neohoods.portal.platform.assistant.workflows.space.steps;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handler for CONFIRM_RESERVATION_SUMMARY step.
 * Backend-only step that generates reservation summary without LLM.
 */
@Component
@Slf4j
public class ConfirmSummaryStepHandler extends BaseSpaceStepHandler {

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Autowired
    private MessageSource messageSource;

    @Override
    public SpaceStep getStep() {
        return SpaceStep.CONFIRM_RESERVATION_SUMMARY;
    }

    @Override
    public boolean isBackendOnly() {
        return true;
    }

    @Override
    public Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        log.info("🔄 CONFIRM_RESERVATION_SUMMARY handler processing (backend only)");

        if (context == null) {
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Erreur: Contexte de réservation introuvable.")
                    .build());
        }

        // Get data from context
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        Object startDateObj = context.getWorkflowState().get("startDate");
        Object endDateObj = context.getWorkflowState().get("endDate");
        Object startTimeObj = context.getWorkflowState().get("startTime");
        Object endTimeObj = context.getWorkflowState().get("endTime");

        if (spaceId == null || startDateObj == null || endDateObj == null) {
            log.error("❌ CONFIRM_RESERVATION_SUMMARY: Missing required data (spaceId, startDate, or endDate)");
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Une erreur s'est produite : les informations de réservation sont incomplètes.")
                    .build());
        }

        // Get locale
        String localeStr = context.getWorkflowStateValue("locale", String.class);
        Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.FRENCH;

        // Generate summary message
        String summaryMessage = messageSource.getMessage("matrix.reservation.summary", null, locale);
        summaryMessage += "\n\n";
        summaryMessage += messageSource.getMessage("matrix.reservation.spaceId", null, locale) + ": " + spaceId;
        summaryMessage += "\n";
        summaryMessage += messageSource.getMessage("matrix.reservation.startDate", null, locale) + ": " + startDateObj;
        if (startTimeObj != null) {
            summaryMessage += " " + startTimeObj;
        }
        summaryMessage += "\n";
        summaryMessage += messageSource.getMessage("matrix.reservation.endDate", null, locale) + ": " + endDateObj;
        if (endTimeObj != null) {
            summaryMessage += " " + endTimeObj;
        }
        summaryMessage += "\n\n";
        summaryMessage += messageSource.getMessage("matrix.reservation.confirmQuestion", null, locale);

        // Mark summary as shown
        context.updateWorkflowState("summaryShown", true);

        log.info("✅ CONFIRM_RESERVATION_SUMMARY: Summary generated and shown");

        return Mono.just(SpaceStepResponse.builder()
                .status(SpaceStepResponse.StepStatus.ASK_USER)
                .response(summaryMessage)
                .spaceId(spaceId)
                .build());
    }
}
