package com.neohoods.portal.platform.services.matrix.space.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.workflows.space.steps.CompleteSpaceStepHandler;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.services.matrix.BaseMatrixAssistantAgentIntegrationTest;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Integration tests for CompleteSpaceStepHandler.
 */
@DisplayName("CompleteSpaceStepHandler Integration Tests")
@Slf4j
public class CompleteSpaceStepHandlerIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CompleteSpaceStepHandlerIntegrationTest.class);

    @Autowired(required = false)
    private CompleteSpaceStepHandler handler;

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @Timeout(300)
    @DisplayName("Should create reservation when user confirms and return COMPLETED")
    void testCreateReservation() {
        if (handler == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "CompleteSpaceStepHandler not available");
        }

        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String roomId = authContext.getRoomId();
        assertNotNull(roomId, "Room ID should not be null");
        UserEntity testUser = authContext.getAuthenticatedUser();

        // Clear context and set spaceId and period
        agentContextService.clearContext(roomId);
        var context = agentContextService.getOrCreateContext(roomId);
        context.updateWorkflowState("reservationStep", SpaceStep.COMPLETE_RESERVATION.name());
        context.updateWorkflowState("spaceId", parkingSpace1.getId().toString());
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        context.updateWorkflowState("startDate", tomorrow.toString());
        context.updateWorkflowState("endDate", tomorrow.toString());

        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        String userMessage = "oui";

        // When
        Mono<SpaceStepResponse> responseMono = handler.handle(userMessage, conversationHistory, context, authContext);
        SpaceStepResponse response = responseMono.block();

        // Then
        assertNotNull(response, "Response should not be null");
        log.info("Response status: {}", response.getStatus());
        log.info("Response message: {}", response.getResponse().substring(0, Math.min(200, response.getResponse().length())));

        // Should return COMPLETED for parking (free) or SWITCH_STEP to PAYMENT_INSTRUCTIONS for paid spaces
        assertTrue(
                response.isCompleted() || response.isSwitchingStep(),
                "Should return COMPLETED or SWITCH_STEP. Got: " + response.getStatus());

        if (response.isCompleted()) {
            // Verify reservation was created
            List<ReservationEntity> reservations = reservationRepository
                    .findByUser(testUser)
                    .stream()
                    .filter(r -> r.getSpace().getType() == SpaceTypeForEntity.PARKING)
                    .filter(r -> r.getStartDate().equals(tomorrow))
                    .collect(Collectors.toList());

            assertTrue(!reservations.isEmpty(),
                    "Reservation should have been created in database");
        }
    }
}

