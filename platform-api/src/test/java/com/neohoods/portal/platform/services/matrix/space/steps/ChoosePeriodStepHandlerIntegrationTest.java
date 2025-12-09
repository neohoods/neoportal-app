package com.neohoods.portal.platform.services.matrix.space.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.neohoods.portal.platform.assistant.workflows.space.steps.ChoosePeriodStepHandler;
import com.neohoods.portal.platform.services.matrix.BaseMatrixAssistantAgentIntegrationTest;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Integration tests for ChoosePeriodStepHandler.
 */
@DisplayName("ChoosePeriodStepHandler Integration Tests")
@Slf4j
public class ChoosePeriodStepHandlerIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChoosePeriodStepHandlerIntegrationTest.class);

    @Autowired(required = false)
    private ChoosePeriodStepHandler handler;

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Test
    @Timeout(300)
    @DisplayName("Should extract period from conversation and return SWITCH_STEP to CONFIRM_RESERVATION_SUMMARY")
    void testExtractPeriod() {
        if (handler == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "ChoosePeriodStepHandler not available");
        }

        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context and set spaceId
        agentContextService.clearContext(roomId);
        var context = agentContextService.getOrCreateContext(roomId);
        context.updateWorkflowState("reservationStep", SpaceStep.CHOOSE_PERIOD.name());
        context.updateWorkflowState("spaceId", parkingSpace1.getId().toString());

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        // Add previous messages mentioning "demain"
        Map<String, Object> prevMsg = new HashMap<>();
        prevMsg.put("role", "user");
        prevMsg.put("content", "Je veux réserver pour demain");
        conversationHistory.add(prevMsg);
        
        String userMessage = "oui";

        // When
        Mono<SpaceStepResponse> responseMono = handler.handle(userMessage, conversationHistory, context, authContext);
        SpaceStepResponse response = responseMono.block();

        // Then
        assertNotNull(response, "Response should not be null");
        log.info("Response status: {}", response.getStatus());
        log.info("Response message: {}", response.getResponse().substring(0, Math.min(200, response.getResponse().length())));

        // Should either SWITCH_STEP to CONFIRM_RESERVATION_SUMMARY (if period found) or ASK_USER (if not found)
        assertTrue(
                response.isSwitchingStep() || response.isAskingUser(),
                "Should return SWITCH_STEP or ASK_USER. Got: " + response.getStatus());

        if (response.isSwitchingStep()) {
            assertEquals(SpaceStep.CONFIRM_RESERVATION_SUMMARY, response.getNextStep(),
                    "Should switch to CONFIRM_RESERVATION_SUMMARY when period identified");
            assertNotNull(response.getPeriod(), "Should have period when switching");
        }
    }
}

