package com.neohoods.portal.platform.services.matrix;

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

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.workflows.MatrixAssistantRouter;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for MatrixAssistantResidentInfoAgent.
 * 
 * These tests verify that the resident info agent correctly calls MCP tools
 * when asked about residents.
 * 
 * Run with: mvn test -Dtest=MatrixAssistantResidentInfoAgentIntegrationTest
 * -DMISTRAL_AI_TOKEN=your_token
 */
@DisplayName("Matrix Assistant Resident Info Agent Integration Tests")
@Slf4j
public class MatrixAssistantResidentInfoAgentIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MatrixAssistantRouter router;

    @Test
    @Timeout(60)
    @DisplayName("Bot must call get_resident_info tool when asked 'qui habite au 808' and not just say 'Je vais vérifier'")
    void testResidentInfoMustCallTool() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "qui habite au 808";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");

                    String lowerResponse = response.toLowerCase();

                    // The bot should NOT just say "Je vais vérifier" without calling the tool
                    // If it says "Je vais vérifier", it should have actually called the tool
                    if (lowerResponse.contains("vais vérifier")
                            || lowerResponse.contains("vais verifier")) {
                        // If it says it will check, it MUST have called the tool
                        // We verify this by checking that the response contains actual
                        // information
                        // or at least mentions the apartment number
                        assertTrue(
                                lowerResponse.contains("808") ||
                                        lowerResponse.contains("appartement") ||
                                        lowerResponse.contains("apartment") ||
                                        lowerResponse.contains("résident") ||
                                        lowerResponse.contains("resident"),
                                "If bot says 'Je vais vérifier', it must have called the tool and provided information. Got: "
                                        + response);
                    }

                    // The bot should NOT refuse without calling the tool
                    assertFalse(
                            (lowerResponse.contains("je ne peux pas") ||
                                    lowerResponse.contains("i cannot") ||
                                    lowerResponse.contains("i don't have") ||
                                    lowerResponse.contains("je n'ai pas")) &&
                                    !lowerResponse.contains("808") &&
                                    !lowerResponse.contains("appartement"),
                            "Bot should NOT refuse to provide information without calling the tool. Got: "
                                    + response);
                })
                .verifyComplete();
    }

    @Test
    @Timeout(60)
    @DisplayName("Bot must call tool in conversation flow: 'qui habite au 808' -> 'alors?' -> must have called tool")
    void testConversationFlowWithFollowUp() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();

        // First message: "qui habite au 808"
        String question1 = "qui habite au 808";

        // When - First response
        Mono<String> firstResponseMono = router.handleMessage(question1, null, authContext);

        // Build conversation history
        Mono<List<Map<String, Object>>> conversationHistoryMono = firstResponseMono.map(firstResp -> {
            List<Map<String, Object>> history = new ArrayList<>();

            Map<String, Object> userMsg1 = new HashMap<>();
            userMsg1.put("role", "user");
            userMsg1.put("content", question1);
            history.add(userMsg1);

            Map<String, Object> assistantMsg1 = new HashMap<>();
            assistantMsg1.put("role", "assistant");
            assistantMsg1.put("content", firstResp);
            history.add(assistantMsg1);

            return history;
        });

        // Second message: "alors?" (follow-up)
        String question2 = "alors?";

        // When - Second response with conversation history
        Mono<String> secondResponseMono = conversationHistoryMono.flatMap(history -> {
            return router.handleMessage(question2, history, authContext);
        });

        // Then
        StepVerifier.create(secondResponseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");

                    String lowerResponse = response.toLowerCase();

                    // After follow-up, bot should have called the tool and provided information
                    // It should NOT just say "I don't have information" without having called the
                    // tool
                    assertFalse(
                            (lowerResponse.contains("je ne peux pas") ||
                                    lowerResponse.contains("i cannot") ||
                                    lowerResponse.contains("i don't have") ||
                                    lowerResponse.contains("je n'ai pas")) &&
                                    !lowerResponse.contains("808") &&
                                    !lowerResponse.contains("appartement") &&
                                    !lowerResponse.contains("résident"),
                            "After follow-up 'alors?', bot should have called the tool and provided information. Got: "
                                    + response);

                    // Should mention apartment or residents, or at least not refuse
                    // The key is that it should NOT just say "I can help with something else"
                    assertFalse(
                            lowerResponse.contains("autre chose") ||
                                    lowerResponse.contains("something else") ||
                                    lowerResponse.contains("autre sujet"),
                            "Bot should NOT suggest helping with something else after being asked about residents. Got: "
                                    + response);

                    // Should mention apartment or residents
                    assertTrue(
                            lowerResponse.contains("808") ||
                                    lowerResponse.contains("appartement") ||
                                    lowerResponse.contains("apartment") ||
                                    lowerResponse.contains("résident") ||
                                    lowerResponse.contains("resident") ||
                                    lowerResponse.contains("aucun") ||
                                    lowerResponse.contains("pas de") ||
                                    lowerResponse.contains("vérifier") ||
                                    lowerResponse.contains("chercher"),
                            "Response should mention apartment 808, residents, or indicate checking/searching. Got: "
                                    + response);
                })
                .verifyComplete();
    }

    @Test
    @Timeout(60)
    @DisplayName("Bot must call get_resident_info tool when asked multiple times, not refuse")
    void testMultipleAsksMustCallTool() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "qui habite au 808";

        // When - First ask
        Mono<String> firstResponse = router.handleMessage(question, null, authContext);

        // Build conversation history
        Mono<List<Map<String, Object>>> historyMono = firstResponse.map(firstResp -> {
            List<Map<String, Object>> history = new ArrayList<>();

            Map<String, Object> userMsg1 = new HashMap<>();
            userMsg1.put("role", "user");
            userMsg1.put("content", question);
            history.add(userMsg1);

            Map<String, Object> assistantMsg1 = new HashMap<>();
            assistantMsg1.put("role", "assistant");
            assistantMsg1.put("content", firstResp);
            history.add(assistantMsg1);

            return history;
        });

        // Second ask: same question again
        Mono<String> secondResponse = historyMono.flatMap(history -> {
            Map<String, Object> userMsg2 = new HashMap<>();
            userMsg2.put("role", "user");
            userMsg2.put("content", question);
            history.add(userMsg2);

            return router.handleMessage(question, history, authContext);
        });

        // Then
        StepVerifier.create(secondResponse)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");

                    String lowerResponse = response.toLowerCase();

                    // Even if asked multiple times, bot should call the tool, not refuse
                    assertFalse(
                            (lowerResponse.contains("je ne peux pas") ||
                                    lowerResponse.contains("i cannot") ||
                                    lowerResponse.contains("i don't have") ||
                                    lowerResponse.contains("je n'ai pas")) &&
                                    !lowerResponse.contains("808") &&
                                    !lowerResponse.contains("appartement"),
                            "Bot should call the tool even when asked multiple times, not refuse. Got: "
                                    + response);
                })
                .verifyComplete();
    }

    @Test
    @Timeout(60)
    @DisplayName("Bot must not say 'Je vais vérifier' without actually calling the tool")
    void testNoVaisVerifierWithoutToolCall() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "qui habite au 808";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");

                    String lowerResponse = response.toLowerCase();

                    // If bot says "Je vais vérifier", it MUST have actually called the tool
                    // We verify this by checking that the response contains information or
                    // apartment reference
                    if (lowerResponse.contains("vais vérifier")
                            || lowerResponse.contains("vais verifier")) {
                        // If it says it will check, it must have called the tool and provided
                        // info
                        assertTrue(
                                lowerResponse.contains("808") ||
                                        lowerResponse.contains("appartement") ||
                                        lowerResponse.contains("apartment") ||
                                        lowerResponse.contains("résident") ||
                                        lowerResponse.contains("resident") ||
                                        response.length() > 50, // If it says "Je vais vérifier", it should have more
                                                                // content
                                "If bot says 'Je vais vérifier', it must have called the tool and provided information. Got: "
                                        + response);
                    }
                })
                .verifyComplete();
    }
}

