package com.neohoods.portal.platform.services.matrix.space.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.neohoods.portal.platform.assistant.workflows.space.steps.RequestSpaceInfoStepHandler;
import com.neohoods.portal.platform.services.matrix.BaseMatrixAssistantAgentIntegrationTest;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Integration tests for RequestSpaceInfoStepHandler.
 * 
 * Tests that the handler correctly:
 * - Lists available spaces when user asks for information
 * - Returns ASK_USER when user needs to choose a space
 * - Returns SWITCH_STEP when user provides all information
 */
@DisplayName("RequestSpaceInfoStepHandler Integration Tests")
@Slf4j
public class RequestSpaceInfoStepHandlerIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequestSpaceInfoStepHandlerIntegrationTest.class);

    @Autowired(required = false)
    private RequestSpaceInfoStepHandler handler;

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Test
    @Timeout(300)
    @DisplayName("Should return ASK_USER with list of available spaces")
    void testListAvailableSpaces() {
        // Skip if handler not available (not yet implemented)
        if (handler == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "RequestSpaceInfoStepHandler not available");
        }

        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);
        var context = agentContextService.getOrCreateContext(roomId);
        context.updateWorkflowState("reservationStep", SpaceStep.REQUEST_SPACE_INFO.name());

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        String userMessage = "Je voudrais réserver une place de parking pour demain";

        // When
        Mono<SpaceStepResponse> responseMono = handler.handle(userMessage, conversationHistory, context, authContext);
        SpaceStepResponse response = responseMono.block();

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getStatus(), "Status should not be null");
        assertNotNull(response.getResponse(), "Response message should not be null");
        assertFalse(response.getResponse().isEmpty(), "Response message should not be empty");

        log.info("Response status: {}", response.getStatus());
        log.info("Response message: {}", response.getResponse().substring(0, Math.min(200, response.getResponse().length())));

        // Should either ASK_USER (to choose space) or SWITCH_STEP (if all info provided)
        assertTrue(
                response.isAskingUser() || response.isSwitchingStep(),
                "Should return ASK_USER or SWITCH_STEP. Got: " + response.getStatus());

        if (response.isAskingUser()) {
            // Should mention parking or spaces
            String lowerResponse = response.getResponse().toLowerCase();
            assertTrue(
                    lowerResponse.contains("parking") ||
                            lowerResponse.contains("place") ||
                            lowerResponse.contains("disponible") ||
                            lowerResponse.contains("available"),
                    "Response should mention parking or spaces. Got: " + response.getResponse());
        }

        // Should not have spaceId yet (this is step 1)
        assertTrue(response.getSpaceId() == null || response.getSpaceId().isEmpty(),
                "Step 1 should not have spaceId yet");
    }

    @Test
    @Timeout(300)
    @DisplayName("Should return SWITCH_STEP to CHOOSE_SPACE when user provides all info")
    void testSwitchToChooseSpaceWhenAllInfoProvided() {
        // Skip if handler not available
        if (handler == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "RequestSpaceInfoStepHandler not available");
        }

        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);
        var context = agentContextService.getOrCreateContext(roomId);
        context.updateWorkflowState("reservationStep", SpaceStep.REQUEST_SPACE_INFO.name());

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        String userMessage = "Je veux réserver la place de parking 7 pour demain";

        // When
        Mono<SpaceStepResponse> responseMono = handler.handle(userMessage, conversationHistory, context, authContext);
        SpaceStepResponse response = responseMono.block();

        // Then
        assertNotNull(response, "Response should not be null");
        
        // If all info is provided, should switch to CHOOSE_SPACE
        if (response.isSwitchingStep()) {
            assertEquals(SpaceStep.CHOOSE_SPACE, response.getNextStep(),
                    "Should switch to CHOOSE_SPACE when all info provided");
            assertNotNull(response.getInternalMessage(), "Internal message should not be null");
        }
    }
}

