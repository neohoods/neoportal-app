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
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import java.util.UUID;

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

    @Autowired
    private SpacesService spacesService;

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

        // Get space name from spaceId
        String spaceName = spaceId; // Fallback to UUID if space not found
        try {
            UUID spaceUuid = UUID.fromString(spaceId);
            SpaceEntity space = spacesService.getSpaceById(spaceUuid);
            if (space != null && space.getName() != null) {
                spaceName = space.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch space name for spaceId {}: {}", spaceId, e.getMessage());
        }

        // Format dates nicely
        String startDateStr = startDateObj != null ? startDateObj.toString() : "";
        String endDateStr = endDateObj != null ? endDateObj.toString() : "";
        String startTimeStr = startTimeObj != null ? startTimeObj.toString() : "";
        String endTimeStr = endTimeObj != null ? endTimeObj.toString() : "";

        // Generate summary message with Markdown formatting (backend will convert to HTML)
        StringBuilder summaryMessage = new StringBuilder();
        try {
            summaryMessage.append("**").append(messageSource.getMessage("matrix.reservation.summary.title", null, locale))
                    .append("**");
        } catch (Exception e) {
            summaryMessage.append("**Récapitulatif de votre réservation**");
        }
        summaryMessage.append("\n\n");

        // Space name
        try {
            summaryMessage.append("**").append(messageSource.getMessage("matrix.reservation.space", null, locale))
                    .append(":** ").append(spaceName);
        } catch (Exception e) {
            summaryMessage.append("**Espace:** ").append(spaceName);
        }
        summaryMessage.append("\n");

        // Start date/time
        try {
            summaryMessage.append("**").append(messageSource.getMessage("matrix.reservation.from", null, locale))
                    .append(":** ").append(startDateStr);
            if (!startTimeStr.isEmpty()) {
                summaryMessage.append(" ").append(messageSource.getMessage("matrix.reservation.at", null, locale))
                        .append(" ").append(startTimeStr);
            }
        } catch (Exception e) {
            summaryMessage.append("**Du:** ").append(startDateStr);
            if (!startTimeStr.isEmpty()) {
                summaryMessage.append(" à ").append(startTimeStr);
            }
        }
        summaryMessage.append("\n");

        // End date/time
        try {
            summaryMessage.append("**").append(messageSource.getMessage("matrix.reservation.to", null, locale))
                    .append(":** ").append(endDateStr);
            if (!endTimeStr.isEmpty()) {
                summaryMessage.append(" ").append(messageSource.getMessage("matrix.reservation.at", null, locale))
                        .append(" ").append(endTimeStr);
            }
        } catch (Exception e) {
            summaryMessage.append("**Au:** ").append(endDateStr);
            if (!endTimeStr.isEmpty()) {
                summaryMessage.append(" à ").append(endTimeStr);
            }
        }
        summaryMessage.append("\n\n");

        // Confirmation question
        try {
            summaryMessage.append(messageSource.getMessage("matrix.reservation.summary.confirmQuestion", null, locale));
        } catch (Exception e) {
            summaryMessage.append("Voulez-vous confirmer cette réservation ?");
        }

        // Mark summary as shown
        context.updateWorkflowState("summaryShown", true);

        log.info("✅ CONFIRM_RESERVATION_SUMMARY: Summary generated and shown");

        return Mono.just(SpaceStepResponse.builder()
                .status(SpaceStepResponse.StepStatus.ASK_USER)
                .response(summaryMessage.toString())
                .spaceId(spaceId)
                .build());
    }
}
