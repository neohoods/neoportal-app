package com.neohoods.portal.platform.services.matrix.space.steps;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.space.steps.ConfirmSummaryStepHandler;
import com.neohoods.portal.platform.services.matrix.BaseMatrixAssistantAgentIntegrationTest;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Integration tests for ConfirmSummaryStepHandler.
 * This handler is backend-only (no LLM).
 */
@DisplayName("ConfirmSummaryStepHandler Integration Tests")
@Slf4j
public class ConfirmSummaryStepHandlerIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConfirmSummaryStepHandlerIntegrationTest.class);

    @Autowired(required = false)
    private ConfirmSummaryStepHandler handler;

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Test
    @Timeout(300)
    @DisplayName("Should generate summary without LLM and return ASK_USER")
    void testGenerateSummary() {
        if (handler == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "ConfirmSummaryStepHandler not available");
        }

        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context and set spaceId and period
        agentContextService.clearContext(roomId);
        var context = agentContextService.getOrCreateContext(roomId);
        context.updateWorkflowState("reservationStep", SpaceStep.CONFIRM_RESERVATION_SUMMARY.name());
        context.updateWorkflowState("spaceId", parkingSpace1.getId().toString());
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        context.updateWorkflowState("startDate", tomorrow.toString());
        context.updateWorkflowState("endDate", tomorrow.toString());

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        String userMessage = ""; // No user message needed for summary generation

        // When
        Mono<SpaceStepResponse> responseMono = handler.handle(userMessage, conversationHistory, context, authContext);
        SpaceStepResponse response = responseMono.block();

        // Then
        assertNotNull(response, "Response should not be null");
        assertTrue(response.isAskingUser(), "Should return ASK_USER with summary");
        assertNotNull(response.getResponse(), "Response message should not be null");
        assertFalse(response.getResponse().isEmpty(), "Response message should not be empty");

        log.info("Response status: {}", response.getStatus());
        log.info("Response message: {}", response.getResponse().substring(0, Math.min(200, response.getResponse().length())));

        // Summary should mention space and date
        String lowerResponse = response.getResponse().toLowerCase();
        assertTrue(
                lowerResponse.contains("parking") ||
                        lowerResponse.contains("place") ||
                        lowerResponse.contains("résumé") ||
                        lowerResponse.contains("summary") ||
                        lowerResponse.contains("confirmer") ||
                        lowerResponse.contains("confirm"),
                "Summary should mention space or ask for confirmation. Got: " + response.getResponse());
    }
}

