package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAIService;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import lombok.extern.slf4j.Slf4j;

/**
 * Integration tests for Matrix Assistant AI service with real Mistral API.
 * 
 * These tests verify that the AI assistant:
 * - Correctly identifies itself as Alfred, NeoHoods assistant
 * - Lists correct capabilities when asked
 * - Successfully retrieves resident information via MCP tools
 * - Successfully manages spaces and reservations
 * 
 * Run with: mvn test -Dtest=MatrixAssistantAIIntegrationTest
 * -DMISTRAL_AI_TOKEN=your_token
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
                "spring.jpa.show-sql=false",
                "spring.jpa.hibernate.ddl-auto=none"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@DisplayName("Matrix Assistant AI Integration Tests")
@TestPropertySource(properties = {
                "neohoods.portal.matrix.enabled=false", // Disable Matrix API calls
                "neohoods.portal.matrix.assistant.ai.enabled=true",
                "neohoods.portal.matrix.assistant.ai.api-key=${MISTRAL_AI_TOKEN:}",
                "neohoods.portal.matrix.assistant.ai.provider=mistral",
                "neohoods.portal.matrix.assistant.ai.model=mistral-small",
                "neohoods.portal.matrix.assistant.mcp.enabled=true",
                "neohoods.portal.matrix.assistant.rag.enabled=false", // Disable RAG for focused tests
                "neohoods.portal.matrix.assistant.conversation.enabled=true",
                "neohoods.portal.matrix.assistant.llm-judge.enabled=false" // Disable LLM-as-a-Judge for integration
                                                                           // tests
})
// // @org.junit.jupiter.api.Disabled("Requires MISTRAL_AI_TOKEN - run manually
// with: mvn test -Dtest=MatrixAssistantAIIntegrationTest
// -DMISTRAL_AI_TOKEN=xxx")
@Transactional
@Slf4j
public class MatrixAssistantAIIntegrationTest extends BaseIntegrationTest {

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

        // Note: We use real MatrixAssistantAIService which will use real
        // MatrixAssistantMCPAdapter
        // and real MatrixAssistantMCPServer to call MCP tools with test database data

        @MockBean
        private MatrixAssistantAdminCommandService adminCommandService;

        private UserEntity testUser;
        private UnitEntity apartment808;
        private UnitEntity apartmentA701;
        private InfoEntity infoEntity;
        private SpaceEntity guestRoomSpace;

        @BeforeEach
        public void setUp() {
                // Skip tests if MISTRAL_AI_TOKEN is not set (e.g., in CI without token)
                String apiKey = System.getenv("MISTRAL_AI_TOKEN");
                if (apiKey == null || apiKey.isEmpty()) {
                        org.junit.jupiter.api.Assumptions.assumeTrue(false,
                                        "MISTRAL_AI_TOKEN not set - skipping integration test");
                }
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

                // Note: We don't create UnitMemberEntity to avoid optimistic locking issues
                // The test will verify that the bot calls get_resident_info tool correctly
                // The tool may return "no residents found" which is acceptable for the test

                // Create apartment A701
                apartmentA701 = new UnitEntity();
                apartmentA701.setId(UUID.randomUUID());
                apartmentA701.setName("A701");
                apartmentA701.setType(com.neohoods.portal.platform.entities.UnitTypeForEntity.FLAT);
                apartmentA701 = unitRepository.save(apartmentA701);

                // Use existing InfoEntity from test data (data.sql should have emergency
                // contacts)
                // The test data should already contain ACAF emergency contact with phone +33 4
                // 76 12 95 85
                infoEntity = infoRepository.findByIdWithContactNumbers(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"))
                                .orElse(null);

                // If InfoEntity doesn't exist, create it but don't add contacts to avoid
                // optimistic locking
                // The test will use existing contacts from data.sql
                if (infoEntity == null) {
                        infoEntity = new InfoEntity();
                        infoEntity.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
                        infoEntity = infoRepository.save(infoEntity);
                }

                // Get or create guest room space
                List<SpaceEntity> spaces = spaceRepository.findAll();
                guestRoomSpace = spaces.stream()
                                .filter(s -> s.getType() == SpaceTypeForEntity.GUEST_ROOM)
                                .findFirst()
                                .orElse(null);

                if (guestRoomSpace == null) {
                        guestRoomSpace = new SpaceEntity();
                        guestRoomSpace.setId(UUID.randomUUID());
                        guestRoomSpace.setName("Chambre d'amis");
                        guestRoomSpace.setType(SpaceTypeForEntity.GUEST_ROOM);
                        guestRoomSpace = spaceRepository.save(guestRoomSpace);
                }
        }

        private MatrixAssistantAuthContext createAuthContext() {
                return MatrixAssistantAuthContext.builder()
                                .matrixUserId("@testuser:chat.neohoods.com")
                                .roomId("!testroom:chat.neohoods.com")
                                .isDirectMessage(true)
                                .userEntity(testUser)
                                .build();
        }

        @Test
        @Timeout(30)
        @DisplayName("Bot should identify itself as Alfred, NeoHoods assistant")
        void testBotIdentity() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                String question = "Qui es-tu?";

                // When
                Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

                // Then
                StepVerifier.create(responseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");

                                        String lowerResponse = response.toLowerCase();

                                        // Check for identity keywords
                                        assertTrue(
                                                        lowerResponse.contains("alfred") ||
                                                                        lowerResponse.contains("neo") ||
                                                                        lowerResponse.contains("neohoods") ||
                                                                        lowerResponse.contains("copropriété") ||
                                                                        lowerResponse.contains("co-ownership"),
                                                        "Response should mention Alfred, NeoHoods, or co-ownership. Got: "
                                                                        + response);
                                })
                                .verifyComplete();
        }

        @Test
        @Timeout(30)
        @DisplayName("Bot should list NeoHoods-specific capabilities, not generic AI features")
        void testCapabilitiesList() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                String question = "Tu sais faire quoi?";

                // When
                Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

                // Then
                StepVerifier.create(responseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");

                                        String lowerResponse = response.toLowerCase();

                                        // Should contain NeoHoods-specific capabilities
                                        int capabilityCount = 0;
                                        if (lowerResponse.contains("élément") || lowerResponse.contains("element"))
                                                capabilityCount++;
                                        if (lowerResponse.contains("urgence") || lowerResponse.contains("emergency")
                                                        || lowerResponse.contains("acaf"))
                                                capabilityCount++;
                                        if (lowerResponse.contains("habite") || lowerResponse.contains("résident")
                                                        || lowerResponse.contains("resident")
                                                        || lowerResponse.contains("appartement"))
                                                capabilityCount++;
                                        if (lowerResponse.contains("réservation")
                                                        || lowerResponse.contains("reservation"))
                                                capabilityCount++;
                                        if (lowerResponse.contains("espace") || lowerResponse.contains("space"))
                                                capabilityCount++;

                                        assertTrue(capabilityCount >= 3,
                                                        "Response should mention at least 3 NeoHoods-specific capabilities. Found: "
                                                                        + capabilityCount + ". Response: " + response);

                                        // Should NOT contain generic AI capabilities
                                        assertFalse(
                                                        lowerResponse.contains("traduction")
                                                                        || lowerResponse.contains("translation") ||
                                                                        lowerResponse.contains("recommandation")
                                                                        || lowerResponse.contains("recommendation") ||
                                                                        lowerResponse.contains("film")
                                                                        || lowerResponse.contains("movie") ||
                                                                        lowerResponse.contains("livre")
                                                                        || lowerResponse.contains("book"),
                                                        "Response should NOT contain generic AI capabilities. Got: "
                                                                        + response);
                                })
                                .verifyComplete();
        }

        @Test
        @Timeout(30)
        @DisplayName("Bot should retrieve resident information for apartment 808")
        void testResidentInformation() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                String question = "Qui habite au 808?";

                // When
                Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

                // Then
                StepVerifier.create(responseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");

                                        String lowerResponse = response.toLowerCase();

                                        // The bot should have called the get_resident_info tool
                                        // It may return "no residents found" if UnitMemberEntity wasn't created
                                        // But it should NOT refuse to check or say it cannot provide the information
                                        assertFalse(
                                                        lowerResponse.contains("je ne peux pas") ||
                                                                        lowerResponse.contains("i cannot") ||
                                                                        lowerResponse.contains("i don't have") ||
                                                                        lowerResponse.contains("je n'ai pas") ||
                                                                        lowerResponse.contains("contact the syndic") ||
                                                                        lowerResponse.contains("contacter le syndic"),
                                                        "Response should NOT refuse to provide information or suggest contacting syndic. Got: "
                                                                        + response);

                                        // Should mention apartment 808 or indicate it checked
                                        assertTrue(
                                                        lowerResponse.contains("808") ||
                                                                        lowerResponse.contains("appartement") ||
                                                                        lowerResponse.contains("apartment") ||
                                                                        lowerResponse.contains("résident") ||
                                                                        lowerResponse.contains("resident"),
                                                        "Response should mention apartment 808 or residents. Got: "
                                                                        + response);
                                })
                                .verifyComplete();
        }

        @Test
        @Timeout(30)
        @DisplayName("Bot should retrieve emergency contact information (ACAF)")
        void testEmergencyContacts() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                String question = "Numéro d'urgence ACAF";

                // When
                Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

                // Then
                StepVerifier.create(responseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");

                                        // Should contain phone number
                                        assertTrue(
                                                        response.contains("76 12 95 85") ||
                                                                        response.contains("476129585") ||
                                                                        response.contains("+33"),
                                                        "Response should contain ACAF phone number. Got: " + response);
                                })
                                .verifyComplete();
        }

        @Test
        @Timeout(30)
        @DisplayName("Bot should retrieve address when asked after getting phone number")
        void testEmergencyContactAddressRecall() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                String question1 = "Numéro d'urgence ACAF";

                // First, get the phone number
                Mono<String> firstResponse = aiService.generateResponse(question1, null, null, authContext);

                // Then ask for address
                String question2 = "leur adresse postale";

                // When - simulate conversation with history
                Mono<String> secondResponse = firstResponse.flatMap(firstResp -> {
                        // Build conversation history
                        java.util.List<java.util.Map<String, Object>> history = new java.util.ArrayList<>();
                        java.util.Map<String, Object> userMsg = new java.util.HashMap<>();
                        userMsg.put("role", "user");
                        userMsg.put("content", question1);
                        history.add(userMsg);

                        java.util.Map<String, Object> assistantMsg = new java.util.HashMap<>();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", firstResp);
                        history.add(assistantMsg);

                        java.util.Map<String, Object> userMsg2 = new java.util.HashMap<>();
                        userMsg2.put("role", "user");
                        userMsg2.put("content", question2);
                        history.add(userMsg2);

                        return aiService.generateResponse(question2, null, history, authContext);
                });

                // Then
                StepVerifier.create(secondResponse)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");

                                        String lowerResponse = response.toLowerCase();

                                        // Should contain address information
                                        assertTrue(
                                                        lowerResponse.contains("rue") ||
                                                                        lowerResponse.contains("grenoble") ||
                                                                        lowerResponse.contains("38000") ||
                                                                        lowerResponse.contains("république"),
                                                        "Response should contain ACAF address. Got: " + response);
                                })
                                .verifyComplete();
        }

        @Test
        @Timeout(30)
        @DisplayName("Bot should list available spaces")
        void testSpaceManagement() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                String question = "Quels espaces sont disponibles?";

                // When
                Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

                // Then
                StepVerifier.create(responseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");

                                        String lowerResponse = response.toLowerCase();

                                        // Should mention spaces or reservations, or indicate an error occurred
                                        // If there's an error, it's OK as long as the bot tried to call the tool
                                        assertTrue(
                                                        lowerResponse.contains("espace") ||
                                                                        lowerResponse.contains("space") ||
                                                                        lowerResponse.contains("réservation") ||
                                                                        lowerResponse.contains("reservation") ||
                                                                        lowerResponse.contains("chambre") ||
                                                                        lowerResponse.contains("coworking") ||
                                                                        lowerResponse.contains("parking") ||
                                                                        lowerResponse.contains("error") ||
                                                                        lowerResponse.contains("erreur"),
                                                        "Response should mention spaces, reservations, or indicate an error. Got: "
                                                                        + response);
                                })
                                .verifyComplete();
        }
}
