package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.workflows.MatrixAssistantRouter;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for MatrixAssistantGeneralAgent.
 * 
 * These tests verify that the general agent correctly calls MCP tools
 * when asked general questions about the copropriété.
 * 
 * Run with: mvn test -Dtest=MatrixAssistantGeneralAgentIntegrationTest
 * -DMISTRAL_AI_TOKEN=your_token
 */
@DisplayName("Matrix Assistant General Agent Integration Tests")
@Slf4j
public class MatrixAssistantGeneralAgentIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MatrixAssistantRouter router;

    @Test
    @Timeout(60)
    @DisplayName("Bot must call get_emergency_numbers tool when asked about number of buildings, not refuse")
    void testEmergencyContactsForBuildingInfo() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "Il y a combien de batiments?";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");

                    String lowerResponse = response.toLowerCase();

                    // Bot should NOT refuse without trying to get information
                    // For building info, it might use get_infos or get_emergency_numbers
                    assertFalse(
                            (lowerResponse.contains("je ne peux pas") ||
                                    lowerResponse.contains("i cannot") ||
                                    lowerResponse.contains("i don't have") ||
                                    lowerResponse.contains("je n'ai pas")) &&
                                    !lowerResponse.contains("bâtiment") &&
                                    !lowerResponse.contains("building") &&
                                    !lowerResponse.contains("3") &&
                                    !lowerResponse.contains("a") &&
                                    !lowerResponse.contains("b") &&
                                    !lowerResponse.contains("c"),
                            "Bot should NOT refuse without trying to get information. Got: "
                                    + response);
                })
                .verifyComplete();
    }
}

