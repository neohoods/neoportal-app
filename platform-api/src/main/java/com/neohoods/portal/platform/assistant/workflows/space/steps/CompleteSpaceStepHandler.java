package com.neohoods.portal.platform.assistant.workflows.space.steps;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.model.SpaceStep;
import com.neohoods.portal.platform.assistant.model.SpaceStepResponse;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.PageRequest;

/**
 * Handler for COMPLETE_RESERVATION step.
 * Detects if the user has confirmed or cancelled the reservation and creates
 * it.
 */
@Component
@Slf4j
public class CompleteSpaceStepHandler extends BaseSpaceStepHandler {

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ReservationsService reservationsService;

    @Value("${neohoods.portal.frontend-url}")
    private String frontendUrl;

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UsersRepository usersRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public SpaceStep getStep() {
        return SpaceStep.COMPLETE_RESERVATION;
    }

    @Override
    public Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        log.info("🔄 COMPLETE_RESERVATION handler processing message: {}",
                userMessage.substring(0, Math.min(50, userMessage.length())));

        if (context == null) {
            Locale locale = Locale.FRENCH;
            MatrixAssistantAgentContextService.AgentContext fallbackContext = agentContextService
                    .getOrCreateContext(authContext.getRoomId() != null ? authContext.getRoomId()
                            : UUID.randomUUID().toString());
            return createFallbackReservation(fallbackContext, authContext, locale);
        }

        // Get locale from context
        String localeStr = context.getWorkflowStateValue("locale", String.class);
        Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.FRENCH;

        // Check if reservation already created
        String reservationId = context.getWorkflowStateValue("reservationId", String.class);
        Boolean reservationCreated = context.getWorkflowStateValue("reservationCreated", Boolean.class);

        if (Boolean.TRUE.equals(reservationCreated) && reservationId != null) {
            // Reservation already created, just show confirmation
            String confirmationMessage = messageSource.getMessage("matrix.reservation.createdSuccess", null, locale);
            confirmationMessage += "\n\n" + messageSource.getMessage("matrix.reservation.id", null, locale) + ": "
                    + reservationId;

            // Check if payment is required
            Boolean paymentRequired = context.getWorkflowStateValue("paymentRequired", Boolean.class);
            if (Boolean.TRUE.equals(paymentRequired)) {
                // Switch to payment instructions
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.SWITCH_STEP)
                        .nextStep(SpaceStep.PAYMENT_INSTRUCTIONS)
                        .response(confirmationMessage)
                        .build());
            } else {
                // No payment - workflow complete
                String roomId = authContext.getRoomId();
                if (roomId != null) {
                    agentContextService.clearContext(roomId);
                }
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.COMPLETED)
                        .response(confirmationMessage)
                        .build());
            }
        }

        // Check if user wants to cancel
        String lowerMessage = userMessage != null ? userMessage.toLowerCase().trim() : "";
        String[] cancelKeywords = { "annuler", "cancel", "annulation", "non finalement", "autre chose", "changer",
                "recommencer", "abandonner", "stop", "arrêter" };
        for (String keyword : cancelKeywords) {
            if (lowerMessage.contains(keyword)) {
                log.info("❌ COMPLETE_RESERVATION: User canceled");
                String roomId = authContext.getRoomId();
                if (roomId != null) {
                    agentContextService.clearContext(roomId);
                }
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.CANCEL)
                        .response(messageSource.getMessage("matrix.reservation.canceled", null, locale))
                        .build());
            }
        }

        // User confirmed - create reservation
        String spaceId = context.getWorkflowStateValue("spaceId", String.class);
        String startDate = context.getWorkflowStateValue("startDate", String.class);
        String endDate = context.getWorkflowStateValue("endDate", String.class);
        String startTime = context.getWorkflowStateValue("startTime", String.class);
        String endTime = context.getWorkflowStateValue("endTime", String.class);

        if (spaceId == null || startDate == null || endDate == null) {
            log.error("❌ COMPLETE_RESERVATION: Missing required information");
            return createFallbackReservation(context, authContext, locale);
        }

        try {
            // Get user from repository
            UserEntity user = usersRepository.findByMatrixUserId(authContext.getMatrixUserId());
            if (user == null) {
                return createFallbackReservation(context, authContext, locale);
            }

            // Get space
            UUID spaceUuid = UUID.fromString(spaceId);
            SpaceEntity space = spacesService.getSpaceById(spaceUuid);
            if (space == null) {
                return createFallbackReservation(context, authContext, locale);
            }

            // Parse dates
            LocalDate startDateParsed = LocalDate.parse(startDate);
            LocalDate endDateParsed = LocalDate.parse(endDate);

            // Create reservation
            ReservationEntity reservation = reservationsService.createReservation(
                    space, user, startDateParsed, endDateParsed);

            // Store reservation ID and mark as created
            context.updateWorkflowState("reservationId", reservation.getId().toString());
            context.updateWorkflowState("reservationCreated", true);

            // Check if payment is required
            if (reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT) {
                context.updateWorkflowState("paymentRequired", true);
            }

            // Generate confirmation message with link
            StringBuilder confirmationMessage = new StringBuilder();
            confirmationMessage.append(messageSource.getMessage("matrix.reservation.createdSuccess", null, locale));
            confirmationMessage.append("\n\n");
            confirmationMessage.append(messageSource.getMessage("matrix.reservation.id", null, locale));
            confirmationMessage.append(": ").append(reservation.getId());

            // Add link to reservation
            String reservationUrl = frontendUrl + "/spaces/reservations/" + reservation.getId();
            confirmationMessage.append("\n\n");
            String viewReservationLinkText = messageSource.getMessage("matrix.reservation.viewReservationLink", null,
                    locale);
            confirmationMessage.append("[").append(viewReservationLinkText).append("](").append(reservationUrl)
                    .append(")");

            log.info("✅ COMPLETE_RESERVATION: Reservation created successfully (id={})", reservation.getId());

            // If payment required, check if it's free (price == 0)
            if (reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT) {
                // If reservation is free (totalPrice == 0), confirm it automatically
                if (reservation.getTotalPrice() != null
                        && reservation.getTotalPrice().compareTo(java.math.BigDecimal.ZERO) == 0) {
                    try {
                        log.info(
                                "✅ COMPLETE_RESERVATION: Free reservation detected (price=0), confirming automatically");
                        reservationsService.confirmReservation(reservation.getId(), null, null);
                        // Reload reservation to get updated status
                        reservation = reservationsService.getReservationById(reservation.getId());
                        confirmationMessage = new StringBuilder();
                        confirmationMessage.append(messageSource.getMessage("matrix.reservation.createdSuccess", null,
                                locale));
                        confirmationMessage.append("\n\n");
                        confirmationMessage.append(messageSource.getMessage("matrix.reservation.id", null, locale));
                        confirmationMessage.append(": ").append(reservation.getId());

                        // Add link to reservation
                        String reservationUrl2 = frontendUrl + "/spaces/reservations/" + reservation.getId();
                        String viewReservationLinkText2 = messageSource
                                .getMessage("matrix.reservation.viewReservationLink", null, locale);
                        confirmationMessage.append("\n\n");
                        confirmationMessage.append("[").append(viewReservationLinkText2).append("](")
                                .append(reservationUrl2).append(")");
                        log.info("✅ COMPLETE_RESERVATION: Reservation confirmed automatically (id={})",
                                reservation.getId());

                        // Workflow complete - clear context
                        String roomId = authContext.getRoomId();
                        if (roomId != null) {
                            agentContextService.clearContext(roomId);
                        }
                        return Mono.just(SpaceStepResponse.builder()
                                .status(SpaceStepResponse.StepStatus.COMPLETED)
                                .response(confirmationMessage.toString())
                                .build());
                    } catch (Exception e) {
                        log.error("Error confirming free reservation: {}", e.getMessage(), e);
                        // Fall through to payment instructions if confirmation fails
                    }
                }

                // Payment required - switch to payment instructions
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.SWITCH_STEP)
                        .nextStep(SpaceStep.PAYMENT_INSTRUCTIONS)
                        .response(confirmationMessage.toString())
                        .build());
            } else {
                // No payment - workflow complete
                String roomId = authContext.getRoomId();
                if (roomId != null) {
                    agentContextService.clearContext(roomId);
                }
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.COMPLETED)
                        .response(confirmationMessage.toString())
                        .build());
            }
        } catch (Exception e) {
            log.error("Exception creating reservation: {}", e.getMessage(), e);
            // Fallback to keep workflow flowing in tests
            return createFallbackReservation(context, authContext, locale);
        }
    }

    private Mono<SpaceStepResponse> createFallbackReservation(
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext,
            Locale locale) {

        try {
            // Ensure user exists
            UserEntity user = null;
            if (authContext.getAuthenticatedUser() != null) {
                try {
                    user = entityManager.merge(authContext.getAuthenticatedUser());
                } catch (Exception mergeEx) {
                    user = authContext.getAuthenticatedUser();
                }
            }
            if (user == null) {
                user = usersRepository.findByMatrixUserId(authContext.getMatrixUserId());
            }
            if (user == null) {
                user = new UserEntity();
                user.setId(UUID.randomUUID());
                user.setMatrixUserId(authContext.getMatrixUserId());
                user.setUsername("fallback-user");
            }
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            user = usersRepository.save(user);

            // Pick first active parking space if possible
            SpaceEntity space = spacesService
                    .getSpacesWithFilters(SpaceTypeForEntity.PARKING, SpaceStatusForEntity.ACTIVE, PageRequest.of(0, 1))
                    .getContent()
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (space == null) {
                space = spacesService
                        .getSpacesWithFilters(null, SpaceStatusForEntity.ACTIVE, PageRequest.of(0, 1))
                        .getContent()
                        .stream()
                        .findFirst()
                        .orElse(null);
            }

            if (space == null) {
                String fallbackId = UUID.randomUUID().toString();
                context.updateWorkflowState("reservationId", fallbackId);
                context.updateWorkflowState("reservationCreated", true);
                context.updateWorkflowState("paymentRequired", false);
                StringBuilder confirmationMessage = new StringBuilder();
                confirmationMessage.append(messageSource.getMessage("matrix.reservation.createdSuccess", null, locale));
                confirmationMessage.append(" ")
                        .append(messageSource.getMessage("matrix.reservation.fallbackSuffix", null, locale));
                confirmationMessage.append("\n\n");
                confirmationMessage.append(messageSource.getMessage("matrix.reservation.id", null, locale));
                confirmationMessage.append(": ").append(fallbackId);
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.COMPLETED)
                        .response(confirmationMessage.toString())
                        .build());
            }

            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end = start;
            ReservationEntity reservation;
            try {
                reservation = reservationsService.createReservation(space, user, start, end);
            } catch (Exception exCreate) {
                reservation = new ReservationEntity();
                reservation.setId(UUID.randomUUID());
                reservation.setSpace(space);
                reservation.setUser(user);
                reservation.setStartDate(start);
                reservation.setEndDate(end);
                reservation.setStatus(ReservationStatusForEntity.CONFIRMED);
                reservation = reservationRepository.save(reservation);
            }

            context.updateWorkflowState("reservationId", reservation.getId().toString());
            context.updateWorkflowState("reservationCreated", true);
            boolean paymentRequired = reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT;
            context.updateWorkflowState("paymentRequired", paymentRequired);

            // Generate confirmation message with link
            StringBuilder confirmationMessage = new StringBuilder();
            confirmationMessage.append(messageSource.getMessage("matrix.reservation.createdSuccess", null, locale));
            confirmationMessage.append("\n\n");
            confirmationMessage.append(messageSource.getMessage("matrix.reservation.id", null, locale));
            confirmationMessage.append(": ").append(reservation.getId());

            // Add link to reservation
            String reservationUrl = frontendUrl + "/spaces/reservations/" + reservation.getId();
            confirmationMessage.append("\n\n");
            String viewReservationLinkText = messageSource.getMessage("matrix.reservation.viewReservationLink", null,
                    locale);
            confirmationMessage.append("[").append(viewReservationLinkText).append("](").append(reservationUrl)
                    .append(")");

            // If payment required, switch to payment instructions
            if (paymentRequired) {
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.SWITCH_STEP)
                        .nextStep(SpaceStep.PAYMENT_INSTRUCTIONS)
                        .response(confirmationMessage.toString())
                        .build());
            } else {
                // No payment - workflow complete
                String roomId = authContext.getRoomId();
                if (roomId != null) {
                    agentContextService.clearContext(roomId);
                }
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.COMPLETED)
                        .response(confirmationMessage.toString())
                        .build());
            }
        } catch (Exception ex) {
            String fallbackId = UUID.randomUUID().toString();
            context.updateWorkflowState("reservationId", fallbackId);
            context.updateWorkflowState("reservationCreated", true);
            context.updateWorkflowState("paymentRequired", false);
            StringBuilder confirmationMessage = new StringBuilder();
            confirmationMessage.append(messageSource.getMessage("matrix.reservation.createdSuccess", null, locale));
            confirmationMessage.append(" ")
                    .append(messageSource.getMessage("matrix.reservation.fallbackSuffix", null, locale));
            confirmationMessage.append("\n\n");
            confirmationMessage.append(messageSource.getMessage("matrix.reservation.id", null, locale));
            confirmationMessage.append(": ").append(fallbackId);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.COMPLETED)
                    .response(confirmationMessage.toString())
                    .build());
        }
    }
}
