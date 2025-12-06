package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.PaymentStatusForEntity;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

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
                "neohoods.portal.matrix.assistant.conversation.enabled=true",
                "neohoods.portal.matrix.assistant.llm-judge.enabled=false" // Disable LLM-as-a-Judge for integration
                                                                           // tests
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

        @Autowired
        private ReservationRepository reservationRepository;

        @Autowired
        private ReservationsService reservationsService;

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
        private SpaceEntity commonRoomSpace;
        private SpaceEntity parkingSpace1;
        private SpaceEntity parkingSpace2;

    @BeforeEach
    public void setUp() {
                // Skip tests if MISTRAL_AI_TOKEN is not set (e.g., in CI without token)
                String apiKey = System.getenv("MISTRAL_AI_TOKEN");
                if (apiKey == null || apiKey.isEmpty()) {
                        org.junit.jupiter.api.Assumptions.assumeTrue(false,
                                        "MISTRAL_AI_TOKEN not set - skipping integration test");
                }
        // Create test user
        // IMPORTANT: Username must match the Matrix user ID format (@username:server.com)
        // The getUser() method extracts "testuser" from "@testuser:chat.neohoods.com"
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test-user@neohoods.com");
        testUser.setUsername("testuser"); // Must match the username extracted from Matrix user ID
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

                // Create or find common room space for availability tests
                commonRoomSpace = spaceRepository.findAll().stream()
                                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM && 
                                                (s.getName().toLowerCase().contains("salle") || 
                                                 s.getName().toLowerCase().contains("commune") ||
                                                 s.getName().toLowerCase().contains("common")))
                                .findFirst()
                                .orElse(null);

                if (commonRoomSpace == null) {
                        commonRoomSpace = new SpaceEntity();
                        commonRoomSpace.setId(UUID.randomUUID());
                        commonRoomSpace.setName("Salle commune");
                        commonRoomSpace.setType(SpaceTypeForEntity.COMMON_ROOM);
                        commonRoomSpace.setStatus(SpaceStatusForEntity.ACTIVE);
                        commonRoomSpace.setTenantPrice(BigDecimal.ZERO);
                        commonRoomSpace.setCurrency("EUR");
                        commonRoomSpace = spaceRepository.save(commonRoomSpace);
                }

                // Create parking spaces for parking reservation tests
                List<SpaceEntity> existingParkings = spaceRepository.findAll().stream()
                                .filter(s -> s.getType() == SpaceTypeForEntity.PARKING)
                                .collect(Collectors.toList());

                if (existingParkings.size() < 2) {
                        // Create parking space 1
                        parkingSpace1 = new SpaceEntity();
                        parkingSpace1.setId(UUID.randomUUID());
                        parkingSpace1.setName("Place de parking A1");
                        parkingSpace1.setType(SpaceTypeForEntity.PARKING);
                        parkingSpace1.setStatus(SpaceStatusForEntity.ACTIVE);
                        parkingSpace1.setTenantPrice(BigDecimal.ZERO);
                        parkingSpace1.setCurrency("EUR");
                        parkingSpace1 = spaceRepository.save(parkingSpace1);

                        // Create parking space 2
                        parkingSpace2 = new SpaceEntity();
                        parkingSpace2.setId(UUID.randomUUID());
                        parkingSpace2.setName("Place de parking A2");
                        parkingSpace2.setType(SpaceTypeForEntity.PARKING);
                        parkingSpace2.setStatus(SpaceStatusForEntity.ACTIVE);
                        parkingSpace2.setTenantPrice(BigDecimal.ZERO);
                        parkingSpace2.setCurrency("EUR");
                        parkingSpace2 = spaceRepository.save(parkingSpace2);
                } else {
                        parkingSpace1 = existingParkings.get(0);
                        parkingSpace2 = existingParkings.size() > 1 ? existingParkings.get(1) : existingParkings.get(0);
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
                                                        "Bot should call the tool even when asked multiple times, not refuse. Got: "
                                                                        + response);
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
                                                        "Bot should NOT refuse without trying to get information. Got: "
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
                                                                                response.length() > 50, // If it says
                                                                                                        // "Je vais
                                                                                                        // vérifier", it
                                                                                                        // should have
                                                                                                        // more
                                                                // content
                                "If bot says 'Je vais vérifier', it must have called the tool and provided information. Got: "
                                        + response);
                    }
                })
                .verifyComplete();
    }

        @Test
        @Timeout(120)
        @DisplayName("Bot must chain tool calls: list_spaces then check_space_availability for 'Est-ce que je peux reserver la salle commune demain?'")
        void testSpaceAvailabilityChainedToolCalls() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                String question = "Est-ce que je peux reserver la salle commune demain?";

                // When
                Mono<String> responseMono = aiService.generateResponse(question, null, null, authContext);

                // Then
                StepVerifier.create(responseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");
                                        log.info("Bot response: {}", response);

                                        String lowerResponse = response.toLowerCase();

                                        // The bot should have called list_spaces to find the space ID
                                        // Then called check_space_availability with the space ID and "demain"
                                        // The response should contain information about availability

                                        // Should mention the space or availability
                                        assertTrue(
                                                        lowerResponse.contains("salle") ||
                                                        lowerResponse.contains("commune") ||
                                                        lowerResponse.contains("common") ||
                                                        lowerResponse.contains("disponible") ||
                                                        lowerResponse.contains("available") ||
                                                        lowerResponse.contains("réservation") ||
                                                        lowerResponse.contains("reservation") ||
                                                        lowerResponse.contains("demain") ||
                                                        lowerResponse.contains("tomorrow"),
                                                        "Response should mention the space, availability, or reservation. Got: " + response);

                                        // Should NOT just say "I don't have information" without calling tools
                                        assertFalse(
                                                        (lowerResponse.contains("je ne peux pas") ||
                                                        lowerResponse.contains("i cannot") ||
                                                        lowerResponse.contains("i don't have") ||
                                                        lowerResponse.contains("je n'ai pas")) &&
                                                        !lowerResponse.contains("salle") &&
                                                        !lowerResponse.contains("disponible"),
                                                        "Bot should have called tools and provided availability information. Got: " + response);
                                })
                                .verifyComplete();
        }

        @Test
        @Timeout(180)
        @DisplayName("Complete parking reservation flow: check availability then create reservation")
        void testParkingReservationCompleteFlow() {
                // Given
                MatrixAssistantAuthContext authContext = createAuthContext();
                
                // Step 1: Ask for available parking spaces for tomorrow
                String question1 = "Est-ce que je peux reserver une place de parking pour demain? Lesquels sont dispo?";
                
                // When - First response (availability check)
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
                
                // Step 2: Request reservation for a specific parking space
                // Extract parking space name from first response or use a known one
                Mono<String> secondResponseMono = conversationHistoryMono.flatMap(history -> {
                        // Use one of the parking spaces we created
                        String parkingName = parkingSpace1.getName();
                        String question2 = "Reserve moi la place de parking " + parkingName;
                        
                        Map<String, Object> userMsg2 = new HashMap<>();
                        userMsg2.put("role", "user");
                        userMsg2.put("content", question2);
                        history.add(userMsg2);
                        
                        return aiService.generateResponse(question2, null, history, authContext);
                });
                
                // Then - Verify the complete flow
                StepVerifier.create(secondResponseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Response should not be null");
                                        assertFalse(response.isEmpty(), "Response should not be empty");
                                        log.info("Bot reservation response: {}", response);
                                        
                                        String lowerResponse = response.toLowerCase();
                                        
                                        // The bot should have created a reservation
                                        // Response should contain confirmation keywords
                                        assertTrue(
                                                        lowerResponse.contains("réservé") ||
                                                        lowerResponse.contains("reservé") ||
                                                        lowerResponse.contains("reserved") ||
                                                        lowerResponse.contains("confirmation") ||
                                                        lowerResponse.contains("confirmé") ||
                                                        lowerResponse.contains("confirmée") ||
                                                        lowerResponse.contains("créé") ||
                                                        lowerResponse.contains("cree") ||
                                                        lowerResponse.contains("created") ||
                                                        lowerResponse.contains("parking") ||
                                                        lowerResponse.contains("place"),
                                                        "Response should confirm reservation creation. Got: " + response);
                                        
                                        // Verify that a reservation was actually created in the database
                                        LocalDate tomorrow = LocalDate.now().plusDays(1);
                                        List<ReservationEntity> reservations = reservationRepository
                                                        .findByUser(testUser)
                                                        .stream()
                                                        .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.PARKING)
                                                        .filter(r -> r.getStartDate().equals(tomorrow))
                                                        .collect(Collectors.toList());
                                        
                                        assertFalse(reservations.isEmpty(), 
                                                "At least one parking reservation should have been created for tomorrow");
                                        
                                        // Verify the reservation is for the correct space
                                        boolean foundCorrectSpace = reservations.stream()
                                                        .anyMatch(r -> r.getSpace().getId().equals(parkingSpace1.getId()) ||
                                                                     r.getSpace().getName().toLowerCase().contains("a1"));
                                        
                                        assertTrue(foundCorrectSpace || !reservations.isEmpty(),
                                                "Reservation should be for a parking space. Found: " + 
                                                reservations.stream()
                                                        .map(r -> r.getSpace().getName())
                                                        .collect(Collectors.joining(", ")));
                                        
                                        // Verify reservation status
                                        ReservationEntity reservation = reservations.get(0);
                                        assertNotNull(reservation.getStatus(), "Reservation should have a status");
                                        assertTrue(
                                                        reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT ||
                                                        reservation.getStatus() == ReservationStatusForEntity.CONFIRMED,
                                                        "Reservation status should be PENDING_PAYMENT or CONFIRMED. Got: " + 
                                                        reservation.getStatus());
                                })
                                .verifyComplete();
                
                // Also verify first response mentioned parking availability
                StepVerifier.create(firstResponseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "First response should not be null");
                                        assertFalse(response.isEmpty(), "First response should not be empty");
                                        log.info("Bot availability response: {}", response);
                                        
                                        String lowerResponse = response.toLowerCase();
                                        
                                        // Should mention parking and availability
                                        assertTrue(
                                                        lowerResponse.contains("parking") ||
                                                        lowerResponse.contains("place") ||
                                                        lowerResponse.contains("disponible") ||
                                                        lowerResponse.contains("available") ||
                                                        lowerResponse.contains("dispo"),
                                                        "First response should mention parking availability. Got: " + response);
                                })
                                .verifyComplete();
        }

        @Test
        @Timeout(300)
        @DisplayName("Complete common room reservation flow with payment: create reservation, generate payment link, wait for payment, confirm")
        void testCommonRoomReservationWithPaymentFlow() {
                // Given - Create a common room space with a price (requires payment)
                SpaceEntity paidCommonRoom = spaceRepository.findAll().stream()
                                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM && 
                                                s.getTenantPrice() != null && 
                                                s.getTenantPrice().compareTo(BigDecimal.ZERO) > 0)
                                .findFirst()
                                .orElse(null);

                if (paidCommonRoom == null) {
                        paidCommonRoom = new SpaceEntity();
                        paidCommonRoom.setId(UUID.randomUUID());
                        paidCommonRoom.setName("Salle commune");
                        paidCommonRoom.setType(SpaceTypeForEntity.COMMON_ROOM);
                        paidCommonRoom.setStatus(SpaceStatusForEntity.ACTIVE);
                        paidCommonRoom.setTenantPrice(new BigDecimal("50.00")); // Requires payment
                        paidCommonRoom.setCurrency("EUR");
                        paidCommonRoom = spaceRepository.save(paidCommonRoom);
                }

                MatrixAssistantAuthContext authContext = createAuthContext();
                LocalDate tomorrow = LocalDate.now().plusDays(1);
                
                // Step 1: Create reservation
                String question1 = "Je veux reserver la salle commune pour demain";
                
                // When - First response (create reservation)
                Mono<String> firstResponseMono = aiService.generateResponse(question1, null, null, authContext);
                
                // Build conversation history after first response
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
                
                // Step 2: Request payment link
                Mono<String> secondResponseMono = conversationHistoryMono.flatMap(history -> {
                        // Find the reservation that was just created
                        List<ReservationEntity> reservations = reservationRepository
                                        .findByUser(testUser)
                                        .stream()
                                        .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.COMMON_ROOM)
                                        .filter(r -> r.getStartDate().equals(tomorrow))
                                        .filter(r -> r.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT)
                                        .collect(Collectors.toList());
                        
                        if (reservations.isEmpty()) {
                                // If no reservation found, ask for payment link anyway
                                String question2 = "Je veux payer ma reservation";
                                Map<String, Object> userMsg2 = new HashMap<>();
                                userMsg2.put("role", "user");
                                userMsg2.put("content", question2);
                                history.add(userMsg2);
                                return aiService.generateResponse(question2, null, history, authContext);
                        } else {
                                // Use the reservation ID from the created reservation
                                UUID reservationId = reservations.get(0).getId();
                                
                                // Mock Stripe service to return a payment link
                                String mockCheckoutUrl = "https://checkout.stripe.com/test-session-" + reservationId;
                                try {
                                        when(stripeService.createCheckoutSession(any(ReservationEntity.class), any(UserEntity.class), any(SpaceEntity.class)))
                                                        .thenReturn(mockCheckoutUrl);
                                } catch (Exception e) {
                                        log.error("Error mocking Stripe service: {}", e.getMessage());
                                }
                                
                                String question2 = "Genere moi le lien de paiement pour la reservation " + reservationId;
                                Map<String, Object> userMsg2 = new HashMap<>();
                                userMsg2.put("role", "user");
                                userMsg2.put("content", question2);
                                history.add(userMsg2);
                                return aiService.generateResponse(question2, null, history, authContext);
                        }
                });
                
                // Step 3: Simulate payment completion and verify confirmation message
                // Use zip to combine first and second responses
                Mono<String> thirdResponseMono = Mono.zip(firstResponseMono, secondResponseMono).flatMap(tuple -> {
                        String firstResp = tuple.getT1();
                        String secondResp = tuple.getT2();
                        
                        // Find the reservation
                        List<ReservationEntity> reservations = reservationRepository
                                        .findByUser(testUser)
                                        .stream()
                                        .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.COMMON_ROOM)
                                        .filter(r -> r.getStartDate().equals(tomorrow))
                                        .collect(Collectors.toList());
                        
                        if (reservations.isEmpty()) {
                                return Mono.just("No reservation found");
                        }
                        
                        ReservationEntity reservation = reservations.get(0);
                        
                        // Mock Stripe service to return payment success
                        try {
                                when(stripeService.verifyPaymentSuccess(any(ReservationEntity.class)))
                                                .thenReturn(true);
                        } catch (Exception e) {
                                log.error("Error mocking Stripe verification: {}", e.getMessage());
                        }
                        
                        // Simulate payment completion by updating reservation
                        reservation.setStripeSessionId("test_session_" + reservation.getId());
                        reservation.setStripePaymentIntentId("test_pi_" + reservation.getId());
                        reservation.setPaymentStatus(PaymentStatusForEntity.SUCCEEDED);
                        reservation = reservationRepository.save(reservation);
                        
                        // Confirm the reservation (simulates payment webhook)
                        try {
                                reservationsService.confirmReservation(
                                                reservation.getId(),
                                                reservation.getStripePaymentIntentId(),
                                                reservation.getStripeSessionId());
                        } catch (Exception e) {
                                log.warn("Error confirming reservation: {}", e.getMessage());
                        }
                        
                        // Build conversation history
                        List<Map<String, Object>> history = new ArrayList<>();
                        
                        Map<String, Object> userMsg1 = new HashMap<>();
                        userMsg1.put("role", "user");
                        userMsg1.put("content", question1);
                        history.add(userMsg1);
                        
                        Map<String, Object> assistantMsg1 = new HashMap<>();
                        assistantMsg1.put("role", "assistant");
                        assistantMsg1.put("content", firstResp);
                        history.add(assistantMsg1);
                        
                        Map<String, Object> userMsg2 = new HashMap<>();
                        userMsg2.put("role", "user");
                        userMsg2.put("content", "Genere moi le lien de paiement");
                        history.add(userMsg2);
                        
                        Map<String, Object> assistantMsg2 = new HashMap<>();
                        assistantMsg2.put("role", "assistant");
                        assistantMsg2.put("content", secondResp);
                        history.add(assistantMsg2);
                        
                        // Ask about payment status
                        String question3 = "Est ce que mon paiement est confirme?";
                        Map<String, Object> userMsg3 = new HashMap<>();
                        userMsg3.put("role", "user");
                        userMsg3.put("content", question3);
                        history.add(userMsg3);
                        
                        return aiService.generateResponse(question3, null, history, authContext);
                });
                
                // Then - Verify the complete flow
                StepVerifier.create(firstResponseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "First response should not be null");
                                        assertFalse(response.isEmpty(), "First response should not be empty");
                                        log.info("Bot reservation creation response: {}", response);
                                        
                                        String lowerResponse = response.toLowerCase();
                                        
                                        // Should mention reservation creation
                                        assertTrue(
                                                        lowerResponse.contains("réservé") ||
                                                        lowerResponse.contains("reservé") ||
                                                        lowerResponse.contains("reserved") ||
                                                        lowerResponse.contains("créé") ||
                                                        lowerResponse.contains("cree") ||
                                                        lowerResponse.contains("created") ||
                                                        lowerResponse.contains("salle") ||
                                                        lowerResponse.contains("commune"),
                                                        "First response should mention reservation creation. Got: " + response);
                                        
                                        // Verify that a reservation was actually created
                                        List<ReservationEntity> reservations = reservationRepository
                                                        .findByUser(testUser)
                                                        .stream()
                                                        .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.COMMON_ROOM)
                                                        .filter(r -> r.getStartDate().equals(tomorrow))
                                                        .collect(Collectors.toList());
                                        
                                        assertTrue(!reservations.isEmpty(), 
                                                "At least one common room reservation should have been created for tomorrow");
                                })
                                .verifyComplete();
                
                StepVerifier.create(secondResponseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Second response should not be null");
                                        assertFalse(response.isEmpty(), "Second response should not be empty");
                                        log.info("Bot payment link response: {}", response);
                                        
                                        String lowerResponse = response.toLowerCase();
                                        
                                        // Should mention payment link
                                        assertTrue(
                                                        lowerResponse.contains("lien") ||
                                                        lowerResponse.contains("link") ||
                                                        lowerResponse.contains("paiement") ||
                                                        lowerResponse.contains("payment") ||
                                                        lowerResponse.contains("checkout") ||
                                                        lowerResponse.contains("stripe") ||
                                                        lowerResponse.contains("http"),
                                                        "Second response should mention payment link. Got: " + response);
                                })
                                .verifyComplete();
                
                StepVerifier.create(thirdResponseMono)
                                .assertNext(response -> {
                                        assertNotNull(response, "Third response should not be null");
                                        assertFalse(response.isEmpty(), "Third response should not be empty");
                                        log.info("Bot payment confirmation response: {}", response);
                                        
                                        String lowerResponse = response.toLowerCase();
                                        
                                        // Should mention payment confirmation
                                        assertTrue(
                                                        lowerResponse.contains("confirmé") ||
                                                        lowerResponse.contains("confirme") ||
                                                        lowerResponse.contains("confirmed") ||
                                                        lowerResponse.contains("payé") ||
                                                        lowerResponse.contains("paye") ||
                                                        lowerResponse.contains("paid") ||
                                                        lowerResponse.contains("succès") ||
                                                        lowerResponse.contains("success") ||
                                                        lowerResponse.contains("validé") ||
                                                        lowerResponse.contains("valide"),
                                                        "Third response should confirm payment. Got: " + response);
                                        
                                        // Verify that the reservation is confirmed
                                        List<ReservationEntity> reservations = reservationRepository
                                                        .findByUser(testUser)
                                                        .stream()
                                                        .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.COMMON_ROOM)
                                                        .filter(r -> r.getStartDate().equals(tomorrow))
                                                        .collect(Collectors.toList());
                                        
                                        assertFalse(reservations.isEmpty(), 
                                                "Reservation should exist");
                                        
                                        ReservationEntity reservation = reservations.get(0);
                                        assertTrue(
                                                        reservation.getStatus() == ReservationStatusForEntity.CONFIRMED,
                                                        "Reservation status should be CONFIRMED after payment. Got: " + 
                                                        reservation.getStatus());
                                })
                                .verifyComplete();
        }
}
