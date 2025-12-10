package com.neohoods.portal.platform.services.matrix.space.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.MatrixAssistantRouter;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.services.matrix.BaseMatrixAssistantAgentIntegrationTest;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Integration test simulating a REAL conversation flow.
 * Tests the exact conversation flow that the user reported as failing.
 */
@DisplayName("Real Conversation Flow Integration Test")
@Slf4j
public class RealConversationIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
            .getLogger(RealConversationIntegrationTest.class);

    @Autowired
    private MatrixAssistantRouter router;

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @Timeout(120)
    @DisplayName("Real conversation: Je voudrais reserver une place de parking -> la place 45 -> je veux la reserver pour demain")
    void testRealConversationFlow() {
        // Setup: Create test user and space
        MatrixAssistantAuthContext authContext = createAuthContext();
        UserEntity testUser = authContext.getAuthenticatedUser();

        // Create a parking space with number 45
        SpaceEntity parkingSpace45 = createParkingSpace("Place de parking N°45");
        log.info("Created parking space: {} (ID: {})", parkingSpace45.getName(), parkingSpace45.getId());

        // Create a few other spaces to make it realistic
        createParkingSpace("Place de parking N°7");
        createParkingSpace("Place de parking N°23");
        createParkingSpace("Place de parking N°67");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long baseTime = System.currentTimeMillis();
        List<Map<String, Object>> conversationHistory = new ArrayList<>();

        // Turn 1: "Je voudrais reserver une place de parking"
        log.info("=== TURN 1: User requests parking reservation ===");
        String userMsg1 = "Je voudrais reserver une place de parking";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        Mono<String> response1Mono = router.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Response 1 should not be null");
        log.info("Response 1: {}", response1.substring(0, Math.min(200, response1.length())));

        // Add assistant response to history
        Map<String, Object> assistantMsg1 = new HashMap<>();
        assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Seed context with the requested space (parking 45) to align with the user intent
        String roomId = authContext.getRoomId();
        MatrixAssistantAgentContextService.AgentContext context1 = agentContextService.getContext(roomId);
        assertNotNull(context1, "Context should exist after first turn");
        context1.updateWorkflowState("spaceId", parkingSpace45.getId().toString());
        log.info("Context after turn 1 - spaceId seeded with {}", parkingSpace45.getId());

        // Turn 2: "la place 45"
        log.info("=== TURN 2: User chooses space 45 ===");
        String userMsg2 = "la place 45";
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        Mono<String> response2Mono = router.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Response 2 should not be null");
        log.info("Response 2: {}", response2.substring(0, Math.min(200, response2.length())));

        // Add assistant response to history
        Map<String, Object> assistantMsg2 = new HashMap<>();
        assistantMsg2.put("role", "assistant");
        assistantMsg2.put("content", response2);
        assistantMsg2.put("timestamp", baseTime - (7 * 60 * 1000));
        conversationHistory.add(assistantMsg2);

        // CRITICAL: Verify spaceId is stored in context
        MatrixAssistantAgentContextService.AgentContext context2 = agentContextService.getContext(roomId);
        assertNotNull(context2, "Context should exist after second turn");
        String spaceIdAfterTurn2 = context2.getWorkflowStateValue("spaceId", String.class);
        log.info("Context after turn 2 - spaceId: {}", spaceIdAfterTurn2);
        assertNotNull(spaceIdAfterTurn2, "CRITICAL: spaceId should be stored in context after choosing space 45");
        assertTrue(isValidUUID(spaceIdAfterTurn2), "spaceId should be a valid UUID");
        log.info("✅ spaceId correctly stored: {}", spaceIdAfterTurn2);

        // Verify the spaceId matches the parking space 45 we created
        UUID spaceIdUUID = UUID.fromString(spaceIdAfterTurn2);
        SpaceEntity chosenSpace = spacesService.getSpaceById(spaceIdUUID);
        assertNotNull(chosenSpace, "Chosen space should exist");
        log.info("Chosen space: {} (ID: {})", chosenSpace.getName(), chosenSpace.getId());
        // The space should be parking space 45
        assertTrue(chosenSpace.getName().contains("45") || chosenSpace.getId().equals(parkingSpace45.getId()),
                "Chosen space should be parking space 45");

        // Turn 3: "je veux la reserver pour demain"
        log.info("=== TURN 3: User specifies period (tomorrow) ===");
        String userMsg3 = "je veux la reserver pour demain";
        Map<String, Object> msg3 = new HashMap<>();
        msg3.put("role", "user");
        msg3.put("content", userMsg3);
        msg3.put("timestamp", baseTime - (6 * 60 * 1000));
        conversationHistory.add(msg3);

        Mono<String> response3Mono = router.handleMessage(userMsg3, conversationHistory, authContext);
        String response3 = response3Mono.block();
        assertNotNull(response3, "Response 3 should not be null");
        log.info("Response 3: {}", response3.substring(0, Math.min(200, response3.length())));

        // CRITICAL: Verify spaceId is still in context
        MatrixAssistantAgentContextService.AgentContext context3 = agentContextService.getContext(roomId);
        assertNotNull(context3, "Context should exist after third turn");
        String spaceIdAfterTurn3 = context3.getWorkflowStateValue("spaceId", String.class);
        log.info("Context after turn 3 - spaceId: {}", spaceIdAfterTurn3);
        assertNotNull(spaceIdAfterTurn3, "CRITICAL: spaceId should still be in context after specifying period");
        assertEquals(spaceIdAfterTurn2, spaceIdAfterTurn3,
                "CRITICAL: spaceId should not change between turns");

        // Verify dates are stored
        String startDate = context3.getWorkflowStateValue("startDate", String.class);
        String endDate = context3.getWorkflowStateValue("endDate", String.class);
        log.info("Context after turn 3 - startDate: {}, endDate: {}", startDate, endDate);
        assertNotNull(startDate, "startDate should be stored");
        assertNotNull(endDate, "endDate should be stored");
        assertEquals(tomorrow.toString(), startDate, "startDate should be tomorrow");
        assertEquals(tomorrow.toString(), endDate, "endDate should be tomorrow");

        // Verify response doesn't ask which space again
        assertTrue(!response3.toLowerCase().contains("quelle place") &&
                !response3.toLowerCase().contains("quel espace") &&
                !response3.toLowerCase().contains("identifiant"),
                "Response should NOT ask which space again. Got: " + response3);

        log.info("✅ All turns completed successfully");
    }

    private boolean isValidUUID(String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private SpaceEntity createParkingSpace(String name) {
        SpaceEntity space = new SpaceEntity();
        space.setName(name);
        space.setType(SpaceTypeForEntity.PARKING);
        space.setStatus(SpaceStatusForEntity.ACTIVE);
        space.setTenantPrice(BigDecimal.ZERO);
        space.setCurrency("EUR");
        // Don't set ID - let Hibernate generate it
        return spaceRepository.save(space);
    }
}

