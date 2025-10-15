package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.spaces.entities.PaymentStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

import jakarta.annotation.PostConstruct;

/**
 * Service for handling Stripe payment operations
 * Real implementation with Stripe Java SDK
 */
@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    @Value("${stripe.publishable-key:pk_test_...}")
    private String stripePublishableKey;

    @Value("${stripe.secret-key:sk_test_...}")
    private String stripeSecretKey;

    @Value("${neohoods.portal.frontend-url}")
    private String baseUrl;

    @Value("${stripe.webhook-secret:whsec_...}")
    private String webhookSecret;

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private SpacesService spacesService;

    @PostConstruct
    public void initializeStripe() {
        // Initialize Stripe with secret key after dependency injection
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Create a Stripe PaymentIntent for a reservation
     */
    public String createPaymentIntent(ReservationEntity reservation, UserEntity user, SpaceEntity space) {
        try {
            // Calculate amount in cents
            long amountCents = reservation.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();

            // Create metadata for tracking
            Map<String, String> metadata = new HashMap<>();
            metadata.put("reservation_id", reservation.getId().toString());
            metadata.put("user_id", user.getId().toString());
            metadata.put("space_id", space.getId().toString());
            metadata.put("space_name", space.getName());

            // Create PaymentIntent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(space.getCurrency().toLowerCase())
                    .setDescription(String.format("Reservation for %s from %s to %s",
                            space.getName(),
                            reservation.getStartDate(),
                            reservation.getEndDate()))
                    .putAllMetadata(metadata)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Store PaymentIntent ID in reservation
            reservation.setStripePaymentIntentId(paymentIntent.getId());

            logger.info("Created PaymentIntent {} for reservation {}",
                    paymentIntent.getId(), reservation.getId());

            return paymentIntent.getId();

        } catch (StripeException e) {
            logger.error("Failed to create PaymentIntent for reservation {}",
                    reservation.getId(), e);
            throw new CodedErrorException(CodedError.PAYMENT_INTENT_CREATION_FAILED,
                    Map.of("reservationId", reservation.getId()), e);
        }
    }

    /**
     * Create a Stripe Checkout Session for a reservation
     * Note: CheckoutSession creates its own PaymentIntent, but we track the
     * original for reference
     */
    public String createCheckoutSession(ReservationEntity reservation, UserEntity user, SpaceEntity space) {
        try {
            // Verify PaymentIntent was created first
            String originalPaymentIntentId = reservation.getStripePaymentIntentId();
            if (originalPaymentIntentId == null) {
                throw new CodedErrorException(CodedError.PAYMENT_INTENT_REQUIRED, "reservationId", reservation.getId());
            }

            // Calculate amount in cents
            long amountCents = reservation.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();

            // Create line items for the checkout session
            List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();
            lineItems.add(SessionCreateParams.LineItem.builder()
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(space.getCurrency().toLowerCase())
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(String.format("Reservation: %s", space.getName()))
                                    .setDescription(String.format("Reservation from %s to %s",
                                            reservation.getStartDate(),
                                            reservation.getEndDate()))
                                    .build())
                            .setUnitAmount(amountCents)
                            .build())
                    .setQuantity(1L)
                    .build());

            // Create metadata for tracking (include original PaymentIntent for reference)
            Map<String, String> metadata = new HashMap<>();
            metadata.put("reservation_id", reservation.getId().toString());
            metadata.put("user_id", user.getId().toString());
            metadata.put("space_id", space.getId().toString());
            metadata.put("space_name", space.getName());
            metadata.put("original_payment_intent", originalPaymentIntentId);

            // Create checkout session
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(user.getEmail())
                    .setSuccessUrl(
                            baseUrl + "/spaces/reservations/success?session_id={CHECKOUT_SESSION_ID}&reservation_id="
                                    + reservation.getId())
                    .setCancelUrl(baseUrl + "/spaces/reservations/cancel?reservation_id=" + reservation.getId())
                    .addAllLineItem(lineItems)
                    .putAllMetadata(metadata)
                    .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                            .setDescription(String.format("Reservation for %s from %s to %s",
                                    space.getName(),
                                    reservation.getStartDate(),
                                    reservation.getEndDate()))
                            .putAllMetadata(metadata)
                            .build())
                    .build();

            Session session = Session.create(params);

            // Store Session ID in reservation
            reservation.setStripeSessionId(session.getId());

            logger.info("Created Checkout Session {} for reservation {} (original PaymentIntent: {})",
                    session.getId(), reservation.getId(), originalPaymentIntentId);

            return session.getUrl();

        } catch (StripeException e) {
            logger.error("Failed to create Checkout Session for reservation {}",
                    reservation.getId(), e);
            throw new CodedErrorException(CodedError.CHECKOUT_SESSION_CREATION_FAILED,
                    Map.of("reservationId", reservation.getId()), e);
        }
    }

    /**
     * Handle Stripe webhook events
     */
    public void handleWebhook(String webhookPayload, String signature) {
        Event event = null;
        try {
            // Verify webhook signature
            Webhook.constructEvent(webhookPayload, signature, webhookSecret);

            // Parse the event
            event = Webhook.constructEvent(
                    webhookPayload, signature, webhookSecret);

            logger.info("Received Stripe webhook event: {}", event.getType());

            // Handle different event types
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                case "checkout.session.expired":
                    handleCheckoutSessionExpired(event);
                    break;
                default:
                    logger.info("Unhandled webhook event type: {}", event.getType());
            }

        } catch (Exception e) {
            logger.error("Error processing Stripe webhook", e);
            throw new CodedErrorException(CodedError.WEBHOOK_PROCESSING_FAILED,
                    Map.of("webhookId", event != null ? event.getId() : "unknown"), e);
        }
    }

    /**
     * Handle successful payment intent
     */
    private void handlePaymentIntentSucceeded(com.stripe.model.Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (paymentIntent != null) {
            String reservationId = paymentIntent.getMetadata().get("reservation_id");
            logger.info("Payment succeeded for reservation: {}", reservationId);

            try {
                // Update reservation status to CONFIRMED
                UUID reservationUuid = UUID.fromString(reservationId);
                reservationsService.confirmReservation(reservationUuid, paymentIntent.getId(), null);
                logger.info("Reservation {} confirmed after successful payment", reservationId);

                // Send confirmation email
                sendConfirmationEmail(reservationUuid);
                logger.info("Confirmation email sent for reservation: {}", reservationId);

                // Update space availability
                updateSpaceAvailability(reservationUuid);
                logger.info("Space availability updated for reservation: {}", reservationId);

            } catch (Exception e) {
                logger.error("Failed to process successful payment for reservation {}: {}",
                        reservationId, e.getMessage(), e);
            }
        }
    }

    /**
     * Handle failed payment intent
     */
    private void handlePaymentIntentFailed(com.stripe.model.Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (paymentIntent != null) {
            String reservationId = paymentIntent.getMetadata().get("reservation_id");
            logger.warn("Payment failed for reservation: {}", reservationId);

            try {
                // Update reservation status to PAYMENT_FAILED
                UUID reservationUuid = UUID.fromString(reservationId);
                ReservationEntity reservation = reservationsService.getReservationById(reservationUuid);
                reservation.setStatus(ReservationStatusForEntity.PAYMENT_FAILED);
                reservation.setPaymentStatus(PaymentStatusForEntity.FAILED);
                reservationsService.updateReservation(reservation);
                logger.info("Reservation {} marked as payment failed", reservationId);

                // Send failure notification
                sendFailureNotification(reservationUuid);
                logger.info("Failure notification sent for reservation: {}", reservationId);

            } catch (Exception e) {
                logger.error("Failed to process payment failure for reservation {}: {}",
                        reservationId, e.getMessage(), e);
            }
        }
    }

    /**
     * Handle completed checkout session
     */
    private void handleCheckoutSessionCompleted(com.stripe.model.Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session != null) {
            String reservationId = session.getMetadata().get("reservation_id");
            logger.info("Checkout session completed for reservation: {}", reservationId);

            try {
                // Update reservation status to CONFIRMED
                UUID reservationUuid = UUID.fromString(reservationId);
                reservationsService.confirmReservation(reservationUuid, null, session.getId());
                logger.info("Reservation {} confirmed after checkout session completion", reservationId);

                // Send confirmation email
                sendConfirmationEmail(reservationUuid);
                logger.info("Confirmation email sent for reservation: {}", reservationId);

            } catch (Exception e) {
                logger.error("Failed to process checkout session completion for reservation {}: {}",
                        reservationId, e.getMessage(), e);
            }
        }
    }

    /**
     * Handle expired checkout session
     */
    private void handleCheckoutSessionExpired(com.stripe.model.Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session != null) {
            String reservationId = session.getMetadata().get("reservation_id");
            logger.info("Checkout session expired for reservation: {}", reservationId);

            try {
                // Update reservation status to EXPIRED
                UUID reservationUuid = UUID.fromString(reservationId);
                reservationsService.expireReservation(reservationUuid, "Checkout session expired");
                logger.info("Reservation {} expired due to checkout session timeout", reservationId);

                // Clean up expired reservation
                cleanupExpiredReservation(reservationUuid);
                logger.info("Additional cleanup performed for expired reservation: {}", reservationId);

            } catch (Exception e) {
                logger.error("Failed to process expired checkout session for reservation {}: {}",
                        reservationId, e.getMessage(), e);
            }
        }
    }

    /**
     * Calculate total amount for reservation
     */
    public BigDecimal calculateTotalAmount(SpaceEntity space, ReservationEntity reservation) {
        // This would typically be done by the SpacesService
        // but we can add additional Stripe-specific calculations here
        return reservation.getTotalPrice();
    }

    /**
     * Get Stripe publishable key for frontend
     */
    public String getPublishableKey() {
        return stripePublishableKey;
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Webhook.constructEvent(payload, signature, webhookSecret);
            return true;
        } catch (Exception e) {
            logger.warn("Invalid webhook signature", e);
            return false;
        }
    }

    /**
     * Send confirmation email for successful reservation
     */
    private void sendConfirmationEmail(UUID reservationId) {
        try {
            // Get reservation details
            ReservationEntity reservation = reservationsService.getReservationById(reservationId);

            // TODO: Implement actual email service integration
            // This would typically involve:
            // 1. Getting user email from reservation
            // 2. Creating email template with reservation details
            // 3. Sending email via email service (SendGrid, AWS SES, etc.)

            logger.info("Confirmation email would be sent to user for reservation: {}", reservationId);

        } catch (Exception e) {
            logger.error("Failed to send confirmation email for reservation {}: {}",
                    reservationId, e.getMessage(), e);
        }
    }

    /**
     * Send failure notification for failed payment
     */
    private void sendFailureNotification(UUID reservationId) {
        try {
            // Get reservation details
            ReservationEntity reservation = reservationsService.getReservationById(reservationId);

            // TODO: Implement actual email service integration
            // This would typically involve:
            // 1. Getting user email from reservation
            // 2. Creating failure notification template
            // 3. Sending email via email service

            logger.info("Failure notification would be sent to user for reservation: {}", reservationId);

        } catch (Exception e) {
            logger.error("Failed to send failure notification for reservation {}: {}",
                    reservationId, e.getMessage(), e);
        }
    }

    /**
     * Update space availability after successful reservation
     */
    private void updateSpaceAvailability(UUID reservationId) {
        try {
            // Get reservation details
            ReservationEntity reservation = reservationsService.getReservationById(reservationId);

            // Increment used annual reservations for the space
            spacesService.incrementUsedAnnualReservations(reservation.getSpace().getId());

            logger.info("Space availability updated for reservation: {}", reservationId);

        } catch (Exception e) {
            logger.error("Failed to update space availability for reservation {}: {}",
                    reservationId, e.getMessage(), e);
        }
    }

    /**
     * Clean up expired reservation
     */
    private void cleanupExpiredReservation(UUID reservationId) {
        try {
            // Get reservation details
            ReservationEntity reservation = reservationsService.getReservationById(reservationId);

            // Decrement used annual reservations for the space
            spacesService.decrementUsedAnnualReservations(reservation.getSpace().getId());

            // TODO: Additional cleanup tasks could include:
            // - Removing access codes
            // - Cleaning up temporary files
            // - Updating analytics
            // - Sending cleanup notifications

            logger.info("Cleanup completed for expired reservation: {}", reservationId);

        } catch (Exception e) {
            logger.error("Failed to cleanup expired reservation {}: {}",
                    reservationId, e.getMessage(), e);
        }
    }

    /**
     * Verify that a payment was successful with Stripe
     */
    public boolean verifyPaymentSuccess(ReservationEntity reservation) {
        try {
            // Try to verify using Checkout Session (preferred for Checkout sessions)
            if (reservation.getStripeSessionId() != null) {
                try {
                    Session session = Session.retrieve(reservation.getStripeSessionId());
                    boolean isSuccessful = "complete".equals(session.getPaymentStatus())
                            || "paid".equals(session.getPaymentStatus());

                    logger.info(
                            "Payment verification via Checkout Session for reservation {}: sessionId={}, paymentStatus={}, successful={}",
                            reservation.getId(), session.getId(), session.getPaymentStatus(), isSuccessful);

                    return isSuccessful;
                } catch (Exception e) {
                    logger.warn("Failed to verify via Checkout Session for reservation {}: {}", reservation.getId(),
                            e.getMessage());
                }
            }

            // Fallback: verify using PaymentIntent
            if (reservation.getStripePaymentIntentId() != null) {
                PaymentIntent paymentIntent = PaymentIntent.retrieve(reservation.getStripePaymentIntentId());
                boolean isSuccessful = "succeeded".equals(paymentIntent.getStatus());

                logger.info("Payment verification via PaymentIntent for reservation {}: status={}, successful={}",
                        reservation.getId(), paymentIntent.getStatus(), isSuccessful);

                return isSuccessful;
            }

            logger.warn("No payment ID found for reservation: {}", reservation.getId());
            return false;

        } catch (StripeException e) {
            logger.error("Failed to verify payment for reservation {}: {}",
                    reservation.getId(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error verifying payment for reservation {}: {}",
                    reservation.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Process Stripe webhook event
     */
    public boolean processWebhookEvent(String payload) {
        try {
            // Parse the webhook event
           Event event = Webhook.constructEvent(
                    payload, "", webhookSecret);

            logger.info("Processing Stripe webhook event: {}", event.getType());

            // Handle different event types
            switch (event.getType()) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                case "checkout.session.expired":
                    handleCheckoutSessionExpired(event);
                    break;
                default:
                    logger.info("Unhandled webhook event type: {}", event.getType());
                    break;
            }

            return true;

        } catch (Exception e) {
            logger.error("Failed to process webhook event: {}", e.getMessage(), e);
            return false;
        }
    }

}