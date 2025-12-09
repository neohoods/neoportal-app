package com.neohoods.portal.platform.assistant.workflows.space.steps;

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
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.services.StripeService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handler for PAYMENT_INSTRUCTIONS step.
 * Generates payment link and shows instructions to the user.
 * This step is handled entirely by backend without LLM.
 */
@Component
@Slf4j
public class PaymentInstructionsStepHandler extends BaseSpaceStepHandler {

    @Autowired
    private MatrixAssistantAgentContextService agentContextService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Override
    public SpaceStep getStep() {
        return SpaceStep.PAYMENT_INSTRUCTIONS;
    }

    @Override
    public boolean isBackendOnly() {
        return true;
    }

    @Override
    public Mono<SpaceStepResponse> handle(
            String userMessage,
            List<Map<String, Object>> conversationHistory,
            MatrixAssistantAgentContextService.AgentContext context,
            MatrixAssistantAuthContext authContext) {

        log.info("🔄 PAYMENT_INSTRUCTIONS handler processing (backend only)");

        if (context == null) {
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response("Erreur: Contexte de réservation introuvable.")
                    .build());
        }

        String reservationIdStr = context.getWorkflowStateValue("reservationId", String.class);
        if (reservationIdStr == null || reservationIdStr.isEmpty()) {
            log.warn("PAYMENT_INSTRUCTIONS: No reservationId found in context");
            Locale locale = getLocaleFromContext(context);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response(
                            messageSource.getMessage("matrix.reservation.payment.reservationIdRequired", null, locale))
                    .build());
        }

        log.info("✅ PAYMENT_INSTRUCTIONS: Generating payment link (reservationId={})", reservationIdStr);

        try {
            UUID reservationId = UUID.fromString(reservationIdStr);
            ReservationEntity reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationIdStr));

            // Verify the reservation belongs to the authenticated user
            UserEntity user = usersRepository.findByMatrixUserId(authContext.getMatrixUserId());
            if (user == null) {
                Locale locale = getLocaleFromContext(context);
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.ERROR)
                        .response(messageSource.getMessage("matrix.reservation.error.userNotFound", null, locale))
                        .build());
            }

            if (!reservation.getUser().getId().equals(user.getId())) {
                Locale locale = getLocaleFromContext(context);
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.ERROR)
                        .response(messageSource.getMessage("matrix.reservation.payment.noAccess", null, locale))
                        .build());
            }

            // Create PaymentIntent first if not exists
            if (reservation.getStripePaymentIntentId() == null || reservation.getStripePaymentIntentId().isEmpty()) {
                try {
                    String paymentIntentId = stripeService.createPaymentIntent(reservation, user,
                            reservation.getSpace());
                    reservation.setStripePaymentIntentId(paymentIntentId);
                    reservationRepository.save(reservation);
                } catch (Exception e) {
                    log.error("Error creating payment intent: {}", e.getMessage(), e);
                    Locale locale = getLocaleFromContext(context);
                    return Mono.just(SpaceStepResponse.builder()
                            .status(SpaceStepResponse.StepStatus.ERROR)
                            .response(messageSource.getMessage("matrix.reservation.payment.createIntentError",
                                    new Object[] { e.getMessage() }, locale))
                            .build());
                }
            }

            // Create checkout session
            String checkoutUrl;
            try {
                checkoutUrl = stripeService.createCheckoutSession(reservation, user, reservation.getSpace());
                reservationRepository.save(reservation);
            } catch (Exception e) {
                log.error("Error creating checkout session: {}", e.getMessage(), e);
                Locale locale = getLocaleFromContext(context);
                return Mono.just(SpaceStepResponse.builder()
                        .status(SpaceStepResponse.StepStatus.ERROR)
                        .response(messageSource.getMessage("matrix.reservation.payment.createLinkError",
                                new Object[] { e.getMessage() }, locale))
                        .build());
            }

            // Generate payment message
            Locale locale = getLocaleFromContext(context);
            String paymentMessage = messageSource.getMessage("matrix.reservation.payment.instructions", null, locale);
            paymentMessage += "\n\n" + checkoutUrl;
            paymentMessage += "\n\n" + messageSource.getMessage("matrix.reservation.payment.note", null, locale);

            // Mark payment link as generated and clear context
            context.updateWorkflowState("paymentLinkGenerated", true);
            String roomId = authContext.getRoomId();
            if (roomId != null) {
                agentContextService.clearContext(roomId);
            }
            log.info("✅ PAYMENT_INSTRUCTIONS: Payment link generated successfully");

            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ANSWER_USER)
                    .response(paymentMessage)
                    .build());
        } catch (Exception e) {
            log.error("Exception generating payment link: {}", e.getMessage(), e);
            Locale locale = getLocaleFromContext(context);
            return Mono.just(SpaceStepResponse.builder()
                    .status(SpaceStepResponse.StepStatus.ERROR)
                    .response(messageSource.getMessage("matrix.reservation.payment.error",
                            new Object[] { e.getMessage() }, locale))
                    .build());
        }
    }

    private Locale getLocaleFromContext(MatrixAssistantAgentContextService.AgentContext context) {
        String localeStr = context.getWorkflowStateValue("locale", String.class);
        return localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.FRENCH;
    }
}
