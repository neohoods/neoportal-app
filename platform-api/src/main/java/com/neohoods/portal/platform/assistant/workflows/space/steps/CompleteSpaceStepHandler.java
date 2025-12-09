package com.neohoods.portal.platform.assistant.workflows.space.steps;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

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
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response(messageSource.getMessage("matrix.reservation.error.contextNotFound", null, locale))
                    .build());
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
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response(messageSource.getMessage("matrix.reservation.error.incompleteInfo", null, locale))
                    .build());
        }

        try {
            // Get user from repository
            UserEntity user = usersRepository.findByMatrixUserId(authContext.getMatrixUserId());
            if (user == null) {
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.ERROR)
                        .response(messageSource.getMessage("matrix.reservation.error.userNotFound", null, locale))
                        .build());
            }

            // Get space
            UUID spaceUuid = UUID.fromString(spaceId);
            SpaceEntity space = spacesService.getSpaceById(spaceUuid);
            if (space == null) {
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.ERROR)
                        .response(messageSource.getMessage("matrix.reservation.error.spaceNotFound", null, locale))
                        .build());
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

            // Generate confirmation message
            String confirmationMessage = messageSource.getMessage("matrix.reservation.createdSuccess", null, locale);
            confirmationMessage += "\n\n" + messageSource.getMessage("matrix.reservation.id", null, locale) + ": "
                    + reservation.getId();

            log.info("✅ COMPLETE_RESERVATION: Reservation created successfully (id={})", reservation.getId());

            // If payment required, switch to payment instructions
            if (reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT) {
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
        } catch (Exception e) {
            log.error("Exception creating reservation: {}", e.getMessage(), e);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response(messageSource.getMessage("matrix.reservation.error.createFailed",
                            new Object[] { e.getMessage() }, locale))
                    .build());
        }
    }
}
