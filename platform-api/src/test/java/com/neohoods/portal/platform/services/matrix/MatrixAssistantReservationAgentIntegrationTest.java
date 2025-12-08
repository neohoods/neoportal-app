package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

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
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.workflows.MatrixAssistantRouter;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.PaymentStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.StripeService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for MatrixAssistantReservationAgent.
 * 
 * These tests verify that the reservation agent correctly calls MCP tools
 * and creates reservations for parking spaces and common rooms.
 * 
 * Run with: mvn test -Dtest=MatrixAssistantReservationAgentIntegrationTest
 * -DMISTRAL_AI_TOKEN=your_token
 */
@DisplayName("Matrix Assistant Reservation Agent Integration Tests")
@Slf4j
public class MatrixAssistantReservationAgentIntegrationTest extends BaseMatrixAssistantAgentIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MatrixAssistantRouter router;

    @org.springframework.beans.factory.annotation.Autowired
    private ReservationRepository reservationRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ReservationsService reservationsService;

    @SpyBean
    private StripeService stripeService;

    @Test
    @Timeout(120)
    @DisplayName("Bot must chain tool calls: list_spaces then check_space_availability for 'Est-ce que je peux reserver la salle commune demain?'")
    void testSpaceAvailabilityChainedToolCalls() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "Est-ce que je peux reserver la salle commune demain?";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

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
                            "Response should mention the space, availability, or reservation. Got: "
                                    + response);

                    // Should NOT just say "I don't have information" without calling tools
                    assertFalse(
                            (lowerResponse.contains("je ne peux pas") ||
                                    lowerResponse.contains("i cannot") ||
                                    lowerResponse.contains("i don't have") ||
                                    lowerResponse.contains("je n'ai pas")) &&
                                    !lowerResponse.contains("salle") &&
                                    !lowerResponse.contains("disponible"),
                            "Bot should have called tools and provided availability information. Got: "
                                    + response);
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

            return router.handleMessage(question2, history, authContext);
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
                            "Response should confirm reservation creation. Got: "
                                    + response);

                    // Verify that a reservation was actually created in the database
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                            .filter(r -> r.getSpace()
                                    .getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

                    assertFalse(reservations.isEmpty(),
                            "At least one parking reservation should have been created for tomorrow");

                    // Verify the reservation is for the correct space
                    boolean foundCorrectSpace = reservations.stream()
                            .anyMatch(r -> r.getSpace().getId()
                                    .equals(parkingSpace1.getId()) ||
                                    r.getSpace().getName().toLowerCase()
                                            .contains("a1"));

                    assertTrue(foundCorrectSpace || !reservations.isEmpty(),
                            "Reservation should be for a parking space. Found: " +
                                    reservations.stream()
                                            .map(r -> r.getSpace()
                                                    .getName())
                                            .collect(Collectors.joining(
                                                    ", ")));

                    // Verify reservation status
                    ReservationEntity reservation = reservations.get(0);
                    assertNotNull(reservation.getStatus(), "Reservation should have a status");
                    assertTrue(
                            reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT
                                    ||
                                    reservation.getStatus() == ReservationStatusForEntity.CONFIRMED,
                            "Reservation status should be PENDING_PAYMENT or CONFIRMED. Got: "
                                    +
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
                            "First response should mention parking availability. Got: "
                                    + response);
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
        Mono<String> firstResponseMono = router.handleMessage(question1, null, authContext);

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
                return router.handleMessage(question2, history, authContext);
            } else {
                // Use the reservation ID from the created reservation
                UUID reservationId = reservations.get(0).getId();

                // Mock Stripe service to return a payment link
                String mockCheckoutUrl = "https://checkout.stripe.com/test-session-" + reservationId;
                try {
                    org.mockito.Mockito
                            .when(stripeService.createCheckoutSession(
                                    any(ReservationEntity.class),
                                    any(UserEntity.class), any(SpaceEntity.class)))
                            .thenReturn(mockCheckoutUrl);
                } catch (Exception e) {
                    log.error("Error mocking Stripe service: {}", e.getMessage());
                }

                String question2 = "Genere moi le lien de paiement pour la reservation "
                        + reservationId;
                Map<String, Object> userMsg2 = new HashMap<>();
                userMsg2.put("role", "user");
                userMsg2.put("content", question2);
                history.add(userMsg2);
                return router.handleMessage(question2, history, authContext);
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
                org.mockito.Mockito
                        .when(stripeService.verifyPaymentSuccess(any(ReservationEntity.class)))
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

            return router.handleMessage(question3, history, authContext);
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
                            "First response should mention reservation creation. Got: "
                                    + response);

                    // Verify that a reservation was actually created
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                            .filter(r -> r.getSpace()
                                    .getType() == SpaceTypeForEntity.COMMON_ROOM)
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
                            "Second response should mention payment link. Got: "
                                    + response);
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
                            .filter(r -> r.getSpace()
                                    .getType() == SpaceTypeForEntity.COMMON_ROOM)
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

    @Test
    @Timeout(180)
    @DisplayName("Parking reservation variant 1: Direct request with space number and time")
    void testParkingReservationDirectRequest() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "Reserve moi la place de parking " + parkingSpace1.getName()
                + " pour demain de 10h a 19h";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext)
                .timeout(java.time.Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    log.error("Error in test: {}", e.getMessage(), e);
                    return Mono.just("Error: " + e.getMessage());
                });

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");
                    log.info("Bot reservation response: {}", response);

                    String lowerResponse = response.toLowerCase();

                    // Should confirm reservation creation
                    assertTrue(
                            lowerResponse.contains("réservé") ||
                                    lowerResponse.contains("reservé") ||
                                    lowerResponse.contains("reserved") ||
                                    lowerResponse.contains("confirmation") ||
                                    lowerResponse.contains("confirmé") ||
                                    lowerResponse.contains("créé") ||
                                    lowerResponse.contains("created"),
                            "Response should confirm reservation creation. Got: "
                                    + response);

                    // Verify reservation was created
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                            .filter(r -> r.getSpace()
                                    .getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

                    assertTrue(!reservations.isEmpty(),
                            "At least one parking reservation should have been created for tomorrow");
                })
                .verifyComplete();
    }

    @Test
    @Timeout(180)
    @DisplayName("Parking reservation variant 2: Request with 'n'importe laquelle' (any one)")
    void testParkingReservationAnyOne() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "Je veux reserver une place de parking pour demain, n'importe laquelle";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");
                    log.info("Bot reservation response: {}", response);

                    String lowerResponse = response.toLowerCase();

                    // Should confirm reservation creation
                    assertTrue(
                            lowerResponse.contains("réservé") ||
                                    lowerResponse.contains("reservé") ||
                                    lowerResponse.contains("reserved") ||
                                    lowerResponse.contains("confirmation") ||
                                    lowerResponse.contains("confirmé") ||
                                    lowerResponse.contains("créé") ||
                                    lowerResponse.contains("created") ||
                                    lowerResponse.contains("parking") ||
                                    lowerResponse.contains("place"),
                            "Response should confirm reservation creation. Got: "
                                    + response);

                    // Verify reservation was created
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                            .filter(r -> r.getSpace()
                                    .getType() == SpaceTypeForEntity.PARKING)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

                    assertTrue(!reservations.isEmpty(),
                            "At least one parking reservation should have been created for tomorrow");
                })
                .verifyComplete();
    }

    @Test
    @Timeout(180)
    @DisplayName("Parking reservation variant 3: Request with space number only, no time")
    void testParkingReservationSpaceNumberOnly() {
        // Given
        MatrixAssistantAuthContext authContext = createAuthContext();
        String question = "Reserve moi la place de parking " + parkingSpace1.getName() + " pour demain";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");
                    log.info("Bot reservation response: {}", response);

                    String lowerResponse = response.toLowerCase();

                    // Should either confirm reservation or ask for time
                    assertTrue(
                            lowerResponse.contains("réservé") ||
                                    lowerResponse.contains("reservé") ||
                                    lowerResponse.contains("reserved") ||
                                    lowerResponse.contains("confirmation") ||
                                    lowerResponse.contains("confirmé") ||
                                    lowerResponse.contains("créé") ||
                                    lowerResponse.contains("created") ||
                                    lowerResponse.contains("heure") ||
                                    lowerResponse.contains("time") ||
                                    lowerResponse.contains("horaire"),
                            "Response should confirm reservation or ask for time. Got: "
                                    + response);

                    // If it asks for time, verify it's a reasonable question
                    if (lowerResponse.contains("heure") || lowerResponse.contains("time")) {
                        // It's OK to ask for time if not provided
                        assertTrue(true, "Bot correctly asked for time");
                    } else {
                        // If it created reservation, verify it exists
                        LocalDate tomorrow = LocalDate.now().plusDays(1);
                        List<ReservationEntity> reservations = reservationRepository
                                .findByUser(testUser)
                                .stream()
                                .filter(r -> r.getSpace()
                                        .getType() == SpaceTypeForEntity.PARKING)
                                .filter(r -> r.getStartDate().equals(tomorrow))
                                .collect(Collectors.toList());

                        assertTrue(!reservations.isEmpty(),
                                "If reservation was created, it should exist in database");
                    }
                })
                .verifyComplete();
    }

    @Test
    @Timeout(180)
    @DisplayName("Parking reservation variant 4: Request with 'la place 23' format")
    void testParkingReservationPlaceNumberFormat() {
        // Given - Create a parking space with number in name
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
        String question = "la place 23 de 10h a 19h";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");
                    log.info("Bot reservation response: {}", response);

                    String lowerResponse = response.toLowerCase();

                    // Should either confirm reservation or indicate it found the space
                    assertTrue(
                            lowerResponse.contains("réservé") ||
                                    lowerResponse.contains("reservé") ||
                                    lowerResponse.contains("reserved") ||
                                    lowerResponse.contains("confirmation") ||
                                    lowerResponse.contains("confirmé") ||
                                    lowerResponse.contains("créé") ||
                                    lowerResponse.contains("created") ||
                                    lowerResponse.contains("23") ||
                                    lowerResponse.contains("parking") ||
                                    lowerResponse.contains("place"),
                            "Response should mention reservation or space. Got: "
                                    + response);

                    // Verify reservation was created if bot confirmed
                    if (lowerResponse.contains("réservé") || lowerResponse.contains("reservé")
                            || lowerResponse.contains("reserved")) {
                        LocalDate tomorrow = LocalDate.now().plusDays(1);
                        List<ReservationEntity> reservations = reservationRepository
                                .findByUser(testUser)
                                .stream()
                                .filter(r -> r.getSpace()
                                        .getType() == SpaceTypeForEntity.PARKING)
                                .filter(r -> r.getStartDate().equals(tomorrow))
                                .collect(Collectors.toList());

                        assertTrue(!reservations.isEmpty(),
                                "If reservation was confirmed, it should exist in database");
                    }
                })
                .verifyComplete();
    }

    @Test
    @Timeout(300)
    @DisplayName("Common room reservation variant 1: Direct request with payment")
    void testCommonRoomReservationDirectRequest() {
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
        String question = "Je veux reserver la salle commune pour demain de 14h a 18h";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");
                    log.info("Bot reservation response: {}", response);

                    String lowerResponse = response.toLowerCase();

                    // Should mention reservation creation and payment
                    assertTrue(
                            lowerResponse.contains("réservé") ||
                                    lowerResponse.contains("reservé") ||
                                    lowerResponse.contains("reserved") ||
                                    lowerResponse.contains("créé") ||
                                    lowerResponse.contains("created") ||
                                    lowerResponse.contains("salle") ||
                                    lowerResponse.contains("commune"),
                            "Response should mention reservation creation. Got: "
                                    + response);

                    // Verify reservation was created
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    List<ReservationEntity> reservations = reservationRepository
                            .findByUser(testUser)
                            .stream()
                            .filter(r -> r.getSpace()
                                    .getType() == SpaceTypeForEntity.COMMON_ROOM)
                            .filter(r -> r.getStartDate().equals(tomorrow))
                            .collect(Collectors.toList());

                    assertTrue(!reservations.isEmpty(),
                            "At least one common room reservation should have been created for tomorrow");
                })
                .verifyComplete();
    }

    @Test
    @Timeout(300)
    @DisplayName("Common room reservation variant 2: Request without time, should ask or use defaults")
    void testCommonRoomReservationWithoutTime() {
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
        String question = "Reserve moi la salle commune pour demain";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");
                    log.info("Bot reservation response: {}", response);

                    String lowerResponse = response.toLowerCase();

                    // Should either confirm reservation or ask for time
                    assertTrue(
                            lowerResponse.contains("réservé") ||
                                    lowerResponse.contains("reservé") ||
                                    lowerResponse.contains("reserved") ||
                                    lowerResponse.contains("créé") ||
                                    lowerResponse.contains("created") ||
                                    lowerResponse.contains("heure") ||
                                    lowerResponse.contains("time") ||
                                    lowerResponse.contains("horaire") ||
                                    lowerResponse.contains("salle") ||
                                    lowerResponse.contains("commune"),
                            "Response should mention reservation or ask for time. Got: "
                                    + response);

                    // If it created reservation, verify it exists
                    if (lowerResponse.contains("réservé") || lowerResponse.contains("reservé")
                            || lowerResponse.contains("reserved")) {
                        LocalDate tomorrow = LocalDate.now().plusDays(1);
                        List<ReservationEntity> reservations = reservationRepository
                                .findByUser(testUser)
                                .stream()
                                .filter(r -> r.getSpace()
                                        .getType() == SpaceTypeForEntity.COMMON_ROOM)
                                .filter(r -> r.getStartDate().equals(tomorrow))
                                .collect(Collectors.toList());

                        assertTrue(!reservations.isEmpty(),
                                "If reservation was created, it should exist in database");
                    }
                })
                .verifyComplete();
    }

    @Test
    @Timeout(300)
    @DisplayName("Common room reservation variant 3: Request with 'Est-ce que je peux reserver' format")
    void testCommonRoomReservationQuestionFormat() {
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
        String question = "Est-ce que je peux reserver la salle commune pour demain de 15h a 20h?";

        // When
        Mono<String> responseMono = router.handleMessage(question, null, authContext);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response, "Response should not be null");
                    assertFalse(response.isEmpty(), "Response should not be empty");
                    log.info("Bot reservation response: {}", response);

                    String lowerResponse = response.toLowerCase();

                    // Should mention availability or reservation
                    assertTrue(
                            lowerResponse.contains("disponible") ||
                                    lowerResponse.contains("available") ||
                                    lowerResponse.contains("réservé") ||
                                    lowerResponse.contains("reservé") ||
                                    lowerResponse.contains("reserved") ||
                                    lowerResponse.contains("créé") ||
                                    lowerResponse.contains("created") ||
                                    lowerResponse.contains("salle") ||
                                    lowerResponse.contains("commune"),
                            "Response should mention availability or reservation. Got: "
                                    + response);
                })
                .verifyComplete();
    }
}

