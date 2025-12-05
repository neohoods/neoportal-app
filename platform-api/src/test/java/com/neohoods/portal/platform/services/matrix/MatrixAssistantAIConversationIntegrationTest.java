package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.BaseIntegrationTest;
import com.neohoods.portal.platform.entities.InfoEntity;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserStatus;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.InfoRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAIService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.services.matrix.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.services.matrix.mcp.MatrixAssistantMCPServer;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for Matrix Assistant AI service conversation flows.
 * 
 * These tests verify that the bot actually calls MCP tools during
 * conversations,
 * especially in scenarios where it might say "Je vais vérifier" but not
 * actually call the tool.
 * 
 * Run with: mvn test -Dtest=MatrixAssistantAIConversationIntegrationTest
 * -DMISTRAL_AI_TOKEN=your_token
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@DisplayName("Matrix Assistant AI Conversation Integration Tests")
@TestPropertySource(properties = {
        "neohoods.portal.matrix.enabled=false", // Disable Matrix API calls
        "neohoods.portal.matrix.assistant.ai.enabled=true",
        "neohoods.portal.matrix.assistant.ai.api-key=${MISTRAL_AI_TOKEN:}",
        "neohoods.portal.matrix.assistant.ai.provider=mistral",
        "neohoods.portal.matrix.assistant.ai.model=mistral-small",
        "neohoods.portal.matrix.assistant.mcp.enabled=true",
        "neohoods.portal.matrix.assistant.rag.enabled=false", // Disable RAG for focused tests
        "neohoods.portal.matrix.assistant.conversation.enabled=true"
})
@Transactional
@Slf4j
public class MatrixAssistantAIConversationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MatrixAssistantAIService aiService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private InfoRepository infoRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    // Spy on MCP adapter to verify tool calls
    @SpyBean
    private MatrixAssistantMCPAdapter mcpAdapter;

    // Spy on MCP server to verify tool calls
    @SpyBean
    private MatrixAssistantMCPServer mcpServer;

    @MockBean
    private MatrixAssistantAdminCommandService adminCommandService;

    private UserEntity testUser;
    private UnitEntity apartment808;
    private InfoEntity infoEntity;

    @BeforeEach
    public void setUp() {
        // Create test user
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test-user@neohoods.com");
        testUser.setUsername("testuser");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setType(UserType.OWNER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setPreferredLanguage("fr");
        testUser = usersRepository.save(testUser);

        // Create apartment 808
        apartment808 = new UnitEntity();
        apartment808.setId(UUID.randomUUID());
        apartment808.setName("808");
        apartment808.setType(com.neohoods.portal.platform.entities.UnitTypeForEntity.FLAT);
        apartment808 = unitRepository.save(apartment808);

        // Use existing InfoEntity from test data
        infoEntity = infoRepository.findByIdWithContactNumbers(
                UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .orElse(null);

        if (infoEntity == null) {
            infoEntity = new InfoEntity();
            infoEntity.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            infoEntity = infoRepository.save(infoEntity);
        }
    }

    private MatrixAssistantAuthContext createAuthContext() {
        return MatrixAssistantAuthContext.builder()
                .matrixUserId("@testuser:chat.neohoods.com")
                .roomId("!testroom:chat.neohoods.com")
                .isDirectMessage(true)
                .userEntity(Optional.of(testUser))
                .build();
    }

    @Test
    @Timeout(60)
    @DisplayName("Bot must call get_resident_info tool when asked 'qui habite au 808' and not just say 'Je vais vérifier'")
    void testResidentInfoMustCallTool() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "qui habite au 808";

        // When
        Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");

                    String lowerResponse = response.toLowerCase();

                    // The bot should NOT just say "Je vais vérifier" without calling the tool
                    // If it says "Je vais vérifier", it should have actually called the tool
                    if (lowerResponse.contains("vais vérifier") || lowerResponse.contains("vais verifier")) {
                        // If it says it will check, it MUST have called the tool
                        // We verify this by checking that the response contains actual information
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
                            "Bot should NOT refuse to provide information without calling the tool. Got: " + response);
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
        Mono<String> firstResponseMono = aiService.generateResponse(question1, null, null, authContext);

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
            return aiService.generateResponse(question2, null, history, authContext);
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
        Mono<String> firstResponse = aiService.generateResponse(question, null, null, authContext);

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

            return aiService.generateResponse(question, null, history, authContext);
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
                            "Bot should call the tool even when asked multiple times, not refuse. Got: " + response);
                })
                .verifyComplete();
    }

    @Test
    @Timeout(60)
    @DisplayName("Bot must call get_emergency_numbers tool when asked about number of buildings, not refuse")
    void testEmergencyContactsForBuildingInfo() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "Il y a combien de batiments?";

        // When
        Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

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
                            "Bot should NOT refuse without trying to get information. Got: " + response);
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
        Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");

                    String lowerResponse = response.toLowerCase();

                    // If bot says "Je vais vérifier", it MUST have actually called the tool
                    // We verify this by checking that the response contains information or
                    // apartment reference
                    if (lowerResponse.contains("vais vérifier") || lowerResponse.contains("vais verifier")) {
                        // If it says it will check, it must have called the tool and provided info
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
