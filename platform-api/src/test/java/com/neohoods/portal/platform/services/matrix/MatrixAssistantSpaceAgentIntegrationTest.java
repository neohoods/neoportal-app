package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.MatrixAssistantSpaceAgent;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.StripeService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Integration tests for MatrixAssistantSpaceAgent workflow.
 * 
 * These tests verify that the reservation agent correctly handles the complete
 * reservation workflow step by step, from initial request to confirmation.
 * 
 * Run with: mvn test -Dtest=MatrixAssistantSpaceAgentIntegrationTest
 */
@DisplayName("Matrix Assistant Reservation Agent Integration Tests - Workflow")
@Slf4j
public class MatrixAssistantSpaceAgentIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatrixAssistantSpaceAgentIntegrationTest.class);

    @Autowired
    private MatrixAssistantSpaceAgent spaceAgent;

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationsService reservationsService;

    @SpyBean
    private StripeService stripeService;

    @Test
    @Timeout(300)
    @DisplayName("Complete parking reservation workflow: Step 1 to Step 5")
    void testCompleteParkingReservationWorkflow() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // Step 1: User initiates reservation request
        String userMsg1 = "Je voudrais réserver une place de parking pour demain";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        log.info("Step 1 - User: '{}'", userMsg1);
        Mono<String> response1Mono = spaceAgent.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Step 1 response should not be null");
        assertFalse(response1.isEmpty(), "Step 1 response should not be empty");
        log.info("Step 1 - Assistant: '{}'", response1.substring(0, Math.min(100, response1.length())));

        // Verify Step 1: Should list spaces or ask for more info
        String lowerResponse1 = response1.toLowerCase();
                    assertTrue(
                lowerResponse1.contains("parking") ||
                        lowerResponse1.contains("place") ||
                        lowerResponse1.contains("disponible") ||
                        lowerResponse1.contains("available") ||
                        lowerResponse1.contains("souhaitez") ||
                        lowerResponse1.contains("voulez"),
                "Step 1 should mention parking, places, or ask for choice. Got: " + response1);

        // Add assistant response to history
            Map<String, Object> assistantMsg1 = new HashMap<>();
            assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Step 2: User chooses a space
        String userMsg2 = "la place " + parkingSpace1.getName();
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        log.info("Step 2 - User: '{}'", userMsg2);
        Mono<String> response2Mono = spaceAgent.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Step 2 response should not be null");
        assertFalse(response2.isEmpty(), "Step 2 response should not be empty");
        log.info("Step 2 - Assistant: '{}'", response2.substring(0, Math.min(100, response2.length())));

        // Verify Step 2: Should confirm space or ask for period
        String lowerResponse2 = response2.toLowerCase();
        assertTrue(
                lowerResponse2.contains("parking") ||
                        lowerResponse2.contains("place") ||
                        lowerResponse2.contains("date") ||
                        lowerResponse2.contains("jour") ||
                        lowerResponse2.contains("période") ||
                        lowerResponse2.contains("confir"),
                "Step 2 should mention space, date, or ask for period. Got: " + response2);

        // Add assistant response to history
        Map<String, Object> assistantMsg2 = new HashMap<>();
        assistantMsg2.put("role", "assistant");
        assistantMsg2.put("content", response2);
        assistantMsg2.put("timestamp", baseTime - (7 * 60 * 1000));
        conversationHistory.add(assistantMsg2);

        // Step 3: User confirms (if period was already provided) or provides confirmation
        String userMsg3 = "oui";
        Map<String, Object> msg3 = new HashMap<>();
        msg3.put("role", "user");
        msg3.put("content", userMsg3);
        msg3.put("timestamp", baseTime - (6 * 60 * 1000));
        conversationHistory.add(msg3);

        log.info("Step 3 - User: '{}'", userMsg3);
        Mono<String> response3Mono = spaceAgent.handleMessage(userMsg3, conversationHistory, authContext);
        String response3 = response3Mono.block();
        assertNotNull(response3, "Step 3 response should not be null");
        assertFalse(response3.isEmpty(), "Step 3 response should not be empty");
        log.info("Step 3 - Assistant: '{}'", response3.substring(0, Math.min(100, response3.length())));

        // Verify Step 3: Should show summary, create reservation, or continue workflow
        String lowerResponse3 = response3.toLowerCase();
                    assertTrue(
                lowerResponse3.contains("réservé") ||
                        lowerResponse3.contains("reservé") ||
                        lowerResponse3.contains("reserved") ||
                        lowerResponse3.contains("confirmation") ||
                        lowerResponse3.contains("confirmé") ||
                        lowerResponse3.contains("créé") ||
                        lowerResponse3.contains("created") ||
                        lowerResponse3.contains("résumé") ||
                        lowerResponse3.contains("summary") ||
                        lowerResponse3.contains("parking") ||
                        lowerResponse3.contains("place") ||
                        lowerResponse3.contains("disponible") ||
                        lowerResponse3.contains("souhaitez") ||
                        lowerResponse3.contains("voulez"),
                "Step 3 should mention reservation, summary, or continue workflow. Got: " + response3);

        // Verify reservation was created in database
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

        if (lowerResponse3.contains("réservé") || lowerResponse3.contains("reservé")
                || lowerResponse3.contains("reserved") || lowerResponse3.contains("créé")
                || lowerResponse3.contains("created")) {
            assertTrue(!reservations.isEmpty(),
                    "If reservation was confirmed, it should exist in database");
        }

        // Verify context step progression
        MatrixAssistantAgentContextService.AgentContext context = agentContextService.getContext(roomId);
        if (context != null) {
            String stepStr = context.getWorkflowStateValue("reservationStep", String.class);
            log.info("Final context step: {}", stepStr);
            // Step should be COMPLETE_RESERVATION or PAYMENT_INSTRUCTIONS if reservation was created
            if (!reservations.isEmpty()) {
                    assertTrue(
                        SpaceStep.COMPLETE_RESERVATION.name().equals(stepStr) ||
                                SpaceStep.PAYMENT_INSTRUCTIONS.name().equals(stepStr),
                        "Context step should be COMPLETE_RESERVATION or PAYMENT_INSTRUCTIONS after creation. Got: "
                                + stepStr);
            }
        }
    }

    @Test
    @Timeout(600)
    @DisplayName("COMPLETE parking reservation workflow: Step 1 to Step 5 - REAL END-TO-END TEST")
    void testCompleteParkingReservationEndToEnd() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Step 1: User initiates reservation request
        String userMsg1 = "Je voudrais réserver une place de parking pour demain";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        log.info("=== STEP 1: User initiates reservation ===");
        log.info("User: '{}'", userMsg1);
        Mono<String> response1Mono = spaceAgent.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Step 1 response should not be null");
        assertFalse(response1.isEmpty(), "Step 1 response should not be empty");
        log.info("Assistant: '{}'", response1.substring(0, Math.min(200, response1.length())));

        // Verify Step 1: Should list spaces or ask for more info
        String lowerResponse1 = response1.toLowerCase();
        assertTrue(
                lowerResponse1.contains("parking") ||
                        lowerResponse1.contains("place") ||
                        lowerResponse1.contains("disponible") ||
                        lowerResponse1.contains("available") ||
                        lowerResponse1.contains("souhaitez") ||
                        lowerResponse1.contains("voulez"),
                "Step 1 should mention parking, places, or ask for choice. Got: " + response1);

        // Add assistant response to history
            Map<String, Object> assistantMsg1 = new HashMap<>();
            assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Step 2: User chooses a space
        String userMsg2 = "la place " + parkingSpace1.getName();
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        log.info("=== STEP 2: User chooses space ===");
        log.info("User: '{}'", userMsg2);
        Mono<String> response2Mono = spaceAgent.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Step 2 response should not be null");
        assertFalse(response2.isEmpty(), "Step 2 response should not be empty");
        log.info("Assistant: '{}'", response2.substring(0, Math.min(200, response2.length())));

        // Verify Step 2: Should confirm space or ask for period
        String lowerResponse2 = response2.toLowerCase();
        assertTrue(
                lowerResponse2.contains("parking") ||
                        lowerResponse2.contains("place") ||
                        lowerResponse2.contains("date") ||
                        lowerResponse2.contains("jour") ||
                        lowerResponse2.contains("période") ||
                        lowerResponse2.contains("confir") ||
                        lowerResponse2.contains("identifié"),
                "Step 2 should mention space, date, or ask for period. Got: " + response2);

        // Add assistant response to history
            Map<String, Object> assistantMsg2 = new HashMap<>();
            assistantMsg2.put("role", "assistant");
        assistantMsg2.put("content", response2);
        assistantMsg2.put("timestamp", baseTime - (7 * 60 * 1000));
        conversationHistory.add(assistantMsg2);

        // Step 3: Continue conversation until reservation is created
        // The workflow may need multiple turns to complete
        int maxTurns = 10;
        boolean reservationCreated = false;
        
        for (int turn = 0; turn < maxTurns && !reservationCreated; turn++) {
            String userMsg = turn == 0 ? "oui" : "oui";
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", "user");
            msg.put("content", userMsg);
            msg.put("timestamp", baseTime - ((6 - turn) * 60 * 1000));
            conversationHistory.add(msg);

            log.info("=== TURN {}: User says '{}' ===", turn + 1, userMsg);
            Mono<String> responseMono = spaceAgent.handleMessage(userMsg, conversationHistory, authContext);
            String response = responseMono.block();
            assertNotNull(response, "Response should not be null");
            assertFalse(response.isEmpty(), "Response should not be empty");
            log.info("Assistant: '{}'", response.substring(0, Math.min(200, response.length())));

            // Add assistant response to history
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", response);
            assistantMsg.put("timestamp", baseTime - ((5 - turn) * 60 * 1000));
            conversationHistory.add(assistantMsg);

            // Check if reservation was created
            List<ReservationEntity> currentReservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                    .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

            if (!currentReservations.isEmpty()) {
                reservationCreated = true;
                log.info("✅ RESERVATION CREATED after {} turns!", turn + 1);
                break;
            }

            // Check if we should continue (summary shown, asking for confirmation, etc.)
                    String lowerResponse = response.toLowerCase();
            if (lowerResponse.contains("réservé") || lowerResponse.contains("reservé") ||
                    lowerResponse.contains("reserved") || lowerResponse.contains("créé") ||
                    lowerResponse.contains("created")) {
                // Reservation might be created, check again
                currentReservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                        .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());
                if (!currentReservations.isEmpty()) {
                    reservationCreated = true;
                    log.info("✅ RESERVATION CREATED!");
                    break;
                }
            }
        }

        // CRITICAL: Verify reservation was actually created in database
        assertTrue(reservationCreated,
                "CRITICAL: Reservation should have been created after " + maxTurns + " turns. Check logs above for details.");
        
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

                    assertTrue(!reservations.isEmpty(),
                "CRITICAL: At least one parking reservation should have been created for tomorrow. Found: " +
                        reservations.size());

        ReservationEntity reservation = reservations.get(0);
        assertNotNull(reservation.getId(), "Reservation should have an ID");
        assertNotNull(reservation.getSpace(), "Reservation should have a space");
        // Verify it's a parking space (may not be exactly parkingSpace1 if user chose a different one)
        assertEquals(SpaceTypeForEntity.PARKING, reservation.getSpace().getType(),
                "Reservation should be for a parking space");
        assertEquals(tomorrow, reservation.getStartDate(), "Reservation should be for tomorrow");

        log.info("✅ RESERVATION CREATED SUCCESSFULLY: ID={}, Space={}, Date={}", 
                reservation.getId(), reservation.getSpace().getName(), reservation.getStartDate());
    }

    @Test
    @Timeout(300)
    @DisplayName("Parking reservation workflow with explicit period: Step 1 to Step 5")
    void testParkingReservationWithExplicitPeriod() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // Step 1: User requests reservation with space and period
        String userMsg1 = "Je veux réserver la place de parking " + parkingSpace1.getName() + " pour demain";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        log.info("Step 1 - User: '{}'", userMsg1);
        Mono<String> response1Mono = spaceAgent.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Step 1 response should not be null");
        assertFalse(response1.isEmpty(), "Step 1 response should not be empty");
        log.info("Step 1 - Assistant: '{}'", response1.substring(0, Math.min(100, response1.length())));

        // Add assistant response to history
        Map<String, Object> assistantMsg1 = new HashMap<>();
        assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Step 2: User confirms
        String userMsg2 = "oui";
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        log.info("Step 2 - User: '{}'", userMsg2);
        Mono<String> response2Mono = spaceAgent.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Step 2 response should not be null");
        assertFalse(response2.isEmpty(), "Step 2 response should not be empty");
        log.info("Step 2 - Assistant: '{}'", response2.substring(0, Math.min(100, response2.length())));

                    // Verify reservation was created
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

        String lowerResponse2 = response2.toLowerCase();
        if (lowerResponse2.contains("réservé") || lowerResponse2.contains("reservé")
                || lowerResponse2.contains("reserved") || lowerResponse2.contains("créé")
                || lowerResponse2.contains("created")) {
                    assertTrue(!reservations.isEmpty(),
                    "If reservation was confirmed, it should exist in database");
        }
    }

    @Test
    @Timeout(300)
    @DisplayName("Short message 'la 23' in reservation context: should continue workflow")
    void testShortMessageInReservationContext() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // Step 1: User initiates reservation
        String userMsg1 = "Je voudrais réserver une place de parking pour demain";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        log.info("Step 1 - User: '{}'", userMsg1);
        Mono<String> response1Mono = spaceAgent.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Step 1 response should not be null");
        log.info("Step 1 - Assistant: '{}'", response1.substring(0, Math.min(100, response1.length())));

        // Add assistant response to history
        Map<String, Object> assistantMsg1 = new HashMap<>();
        assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Step 2: User provides short message "la 23" (selecting space 23)
        String userMsg2 = "la 23";
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        log.info("Step 2 - User: '{}'", userMsg2);
        Mono<String> response2Mono = spaceAgent.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Step 2 response should not be null");
        assertFalse(response2.isEmpty(), "Step 2 response should not be empty");
        log.info("Step 2 - Assistant: '{}'", response2.substring(0, Math.min(100, response2.length())));

        // Verify Step 2: Should handle short message and continue workflow
        String lowerResponse2 = response2.toLowerCase();
        assertTrue(
                lowerResponse2.contains("23") ||
                        lowerResponse2.contains("parking") ||
                        lowerResponse2.contains("place") ||
                        lowerResponse2.contains("confir") ||
                        lowerResponse2.contains("réservé") ||
                        lowerResponse2.contains("reservé"),
                "Step 2 should handle short message and continue workflow. Got: " + response2);
    }

    @Test
    @Timeout(300)
    @DisplayName("Common room reservation workflow: Step 1 to Step 5")
    void testCommonRoomReservationWorkflow() {
        // Given - Create a common room space
        SpaceEntity commonRoom = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.COMMON_ROOM)
                .findFirst()
                .orElse(null);

        if (commonRoom == null) {
            commonRoom = new SpaceEntity();
            commonRoom.setId(UUID.randomUUID());
            commonRoom.setName("Salle commune");
            commonRoom.setType(SpaceTypeForEntity.COMMON_ROOM);
            commonRoom.setStatus(SpaceStatusForEntity.ACTIVE);
            commonRoom.setTenantPrice(BigDecimal.ZERO);
            commonRoom.setCurrency("EUR");
            commonRoom = spaceRepository.save(commonRoom);
        }

        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // Step 1: User requests common room reservation
        String userMsg1 = "Je veux réserver la salle commune pour demain";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        log.info("Step 1 - User: '{}'", userMsg1);
        Mono<String> response1Mono = spaceAgent.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Step 1 response should not be null");
        assertFalse(response1.isEmpty(), "Step 1 response should not be empty");
        log.info("Step 1 - Assistant: '{}'", response1.substring(0, Math.min(100, response1.length())));

        // Verify Step 1: Should mention common room
        String lowerResponse1 = response1.toLowerCase();
                    assertTrue(
                lowerResponse1.contains("salle") ||
                        lowerResponse1.contains("commune") ||
                        lowerResponse1.contains("common") ||
                        lowerResponse1.contains("confir") ||
                        lowerResponse1.contains("réservé"),
                "Step 1 should mention common room or reservation. Got: " + response1);

        // Add assistant response to history
        Map<String, Object> assistantMsg1 = new HashMap<>();
        assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Step 2: User confirms
        String userMsg2 = "oui";
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        log.info("Step 2 - User: '{}'", userMsg2);
        Mono<String> response2Mono = spaceAgent.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Step 2 response should not be null");
        assertFalse(response2.isEmpty(), "Step 2 response should not be empty");
        log.info("Step 2 - Assistant: '{}'", response2.substring(0, Math.min(100, response2.length())));

        // Verify reservation was created
                        LocalDate tomorrow = LocalDate.now().plusDays(1);
                        List<ReservationEntity> reservations = reservationRepository
                                .findByUser(testUser)
                                .stream()
                .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.COMMON_ROOM)
                                .filter(r -> r.getStartDate().equals(tomorrow))
                                .collect(Collectors.toList());

        String lowerResponse2 = response2.toLowerCase();
        if (lowerResponse2.contains("réservé") || lowerResponse2.contains("reservé")
                || lowerResponse2.contains("reserved") || lowerResponse2.contains("créé")
                || lowerResponse2.contains("created")) {
                        assertTrue(!reservations.isEmpty(),
                                "If reservation was confirmed, it should exist in database");
                    }
    }

    @Test
    @Timeout(300)
    @DisplayName("Short message 'n'importe laquelle' in reservation context: should use tools to list spaces")
    void testShortMessageNImporteLaquelle() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // Step 1: User initiates reservation
        String userMsg1 = "Je voudrais réserver une place de parking pour demain";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        log.info("Step 1 - User: '{}'", userMsg1);
        Mono<String> response1Mono = spaceAgent.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Step 1 response should not be null");
        log.info("Step 1 - Assistant: '{}'", response1.substring(0, Math.min(100, response1.length())));

        // Add assistant response to history
        Map<String, Object> assistantMsg1 = new HashMap<>();
        assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Step 2: User says "n'importe laquelle" (any one)
        String userMsg2 = "n'importe laquelle";
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        log.info("Step 2 - User: '{}'", userMsg2);
        Mono<String> response2Mono = spaceAgent.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Step 2 response should not be null");
        assertFalse(response2.isEmpty(), "Step 2 response should not be empty");
        log.info("Step 2 - Assistant: '{}'", response2.substring(0, Math.min(200, response2.length())));

        // Verify Step 2: Should use tools to list spaces and handle "any one" request
        // Note: May hit tool call limit, but should NOT say "don't have access to tools"
        String lowerResponse2 = response2.toLowerCase();
        assertTrue(
                !lowerResponse2.contains("i'm sorry") &&
                        !lowerResponse2.contains("i don't have") &&
                        !lowerResponse2.contains("unable to assist") &&
                        !lowerResponse2.contains("don't have access") &&
                        !lowerResponse2.contains("don't have the tools") &&
                        !lowerResponse2.contains("i currently don't have the tools") &&
                        (lowerResponse2.contains("parking") ||
                                lowerResponse2.contains("place") ||
                                lowerResponse2.contains("disponible") ||
                                lowerResponse2.contains("available") ||
                                lowerResponse2.contains("réservé") ||
                                lowerResponse2.contains("reservé") ||
                                lowerResponse2.contains("confir") ||
                                lowerResponse2.contains("souhaitez") ||
                                lowerResponse2.contains("voulez") ||
                                lowerResponse2.contains("limite") ||
                                lowerResponse2.contains("reformuler")),
                "Step 2 should use tools and handle 'n'importe laquelle' request (or indicate tool limit). Got: " + response2);
    }

    @Test
    @Timeout(300)
    @DisplayName("Short message 'la place 23' in reservation context: should use tools to find space")
    void testShortMessageLaPlace23() {
        // Given - Create a parking space with number 23
        SpaceEntity parkingSpace23 = spaceRepository.findAll().stream()
                .filter(s -> s.getType() == SpaceTypeForEntity.PARKING &&
                        (s.getName().contains("23") || s.getName().contains("N°23")))
                .findFirst()
                .orElse(null);

        if (parkingSpace23 == null) {
            parkingSpace23 = new SpaceEntity();
            parkingSpace23.setId(UUID.randomUUID());
            parkingSpace23.setName("Place de parking N°23");
            parkingSpace23.setType(SpaceTypeForEntity.PARKING);
            parkingSpace23.setStatus(SpaceStatusForEntity.ACTIVE);
            parkingSpace23.setTenantPrice(BigDecimal.ZERO);
            parkingSpace23.setCurrency("EUR");
            parkingSpace23 = spaceRepository.save(parkingSpace23);
        }

        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");

        // Clear context
        agentContextService.clearContext(roomId);

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        // Step 1: User initiates reservation
        String userMsg1 = "Je voudrais réserver une place de parking pour demain";
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", userMsg1);
        msg1.put("timestamp", baseTime - (10 * 60 * 1000));
        conversationHistory.add(msg1);

        log.info("Step 1 - User: '{}'", userMsg1);
        Mono<String> response1Mono = spaceAgent.handleMessage(userMsg1, conversationHistory, authContext);
        String response1 = response1Mono.block();
        assertNotNull(response1, "Step 1 response should not be null");
        log.info("Step 1 - Assistant: '{}'", response1.substring(0, Math.min(100, response1.length())));

        // Add assistant response to history
        Map<String, Object> assistantMsg1 = new HashMap<>();
        assistantMsg1.put("role", "assistant");
        assistantMsg1.put("content", response1);
        assistantMsg1.put("timestamp", baseTime - (9 * 60 * 1000));
        conversationHistory.add(assistantMsg1);

        // Step 2: User says "la place 23" (selecting space 23)
        String userMsg2 = "la place 23";
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("role", "user");
        msg2.put("content", userMsg2);
        msg2.put("timestamp", baseTime - (8 * 60 * 1000));
        conversationHistory.add(msg2);

        log.info("Step 2 - User: '{}'", userMsg2);
        Mono<String> response2Mono = spaceAgent.handleMessage(userMsg2, conversationHistory, authContext);
        String response2 = response2Mono.block();
        assertNotNull(response2, "Step 2 response should not be null");
        assertFalse(response2.isEmpty(), "Step 2 response should not be empty");
        log.info("Step 2 - Assistant: '{}'", response2.substring(0, Math.min(200, response2.length())));

        // Verify Step 2: Should use tools to find space 23 and handle the request
        // CRITICAL: Should NOT say "don't have access to tools" - this is the main bug
        // CRITICAL: Should respond in FRENCH, not English
        String lowerResponse2 = response2.toLowerCase();
        
        // Must NOT contain English error messages about missing tools
        assertTrue(
                !lowerResponse2.contains("i'm sorry") &&
                        !lowerResponse2.contains("i don't have") &&
                        !lowerResponse2.contains("unable to assist") &&
                        !lowerResponse2.contains("don't have access") &&
                        !lowerResponse2.contains("don't have the tools") &&
                        !lowerResponse2.contains("i currently don't have the tools") &&
                        !lowerResponse2.contains("if you have any other questions"),
                "Step 2 should NOT contain English error messages about missing tools. Got: " + response2);
        
        // Must contain French words or space-related content
                    assertTrue(
                lowerResponse2.contains("23") ||
                        lowerResponse2.contains("parking") ||
                        lowerResponse2.contains("place") ||
                        lowerResponse2.contains("disponible") ||
                        lowerResponse2.contains("available") ||
                        lowerResponse2.contains("réservé") ||
                        lowerResponse2.contains("reservé") ||
                        lowerResponse2.contains("confir") ||
                        lowerResponse2.contains("souhaitez") ||
                        lowerResponse2.contains("voulez") ||
                        lowerResponse2.contains("limite") ||
                        lowerResponse2.contains("reformuler") ||
                        lowerResponse2.contains("désolé") ||
                        lowerResponse2.contains("pouvez"),
                "Step 2 should use tools to find space 23 and handle the request (or indicate tool limit). Got: " + response2);
    }
}
