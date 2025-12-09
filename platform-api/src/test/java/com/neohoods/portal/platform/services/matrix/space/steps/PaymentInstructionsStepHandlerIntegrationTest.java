package com.neohoods.portal.platform.services.matrix.space.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.space.steps.PaymentInstructionsStepHandler;
import com.neohoods.portal.platform.services.matrix.BaseMatrixAssistantAgentIntegrationTest;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Integration tests for PaymentInstructionsStepHandler.
 * This handler is backend-only (no LLM).
 */
@DisplayName("PaymentInstructionsStepHandler Integration Tests")
@Slf4j
public class PaymentInstructionsStepHandlerIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentInstructionsStepHandlerIntegrationTest.class);

    @Autowired(required = false)
    private PaymentInstructionsStepHandler handler;

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Test
    @Timeout(300)
    @DisplayName("Should generate payment link without LLM and return ANSWER_USER")
    void testGeneratePaymentLink() {
        if (handler == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "PaymentInstructionsStepHandler not available");
        }

        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context and set reservationId
        agentContextService.clearContext(roomId);
        var context = agentContextService.getOrCreateContext(roomId);
        context.updateWorkflowState("reservationStep", SpaceStep.PAYMENT_INSTRUCTIONS.name());
        context.updateWorkflowState("reservationId", UUID.randomUUID().toString());

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        String userMessage = ""; // No user message needed for payment link generation

        // When
        Mono<SpaceStepResponse> responseMono = handler.handle(userMessage, conversationHistory, context, authContext);
        SpaceStepResponse response = responseMono.block();

        // Then
        assertNotNull(response, "Response should not be null");
        assertEquals(SpaceStepResponse.StepStatus.ANSWER_USER, response.getStatus(),
                "Should return ANSWER_USER with payment link");
        assertNotNull(response.getResponse(), "Response message should not be null");
        assertFalse(response.getResponse().isEmpty(), "Response message should not be empty");

        log.info("Response status: {}", response.getStatus());
        log.info("Response message: {}", response.getResponse().substring(0, Math.min(200, response.getResponse().length())));
    }
}

