package com.neohoods.portal.platform.spaces.api.spaces;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.ReservationsApiApiDelegate;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.model.CancelReservation200Response;
import com.neohoods.portal.platform.model.ConfirmReservationPayment200Response;
import com.neohoods.portal.platform.model.ConfirmReservationPaymentRequest;
import com.neohoods.portal.platform.model.CreateReservationRequest;
import com.neohoods.portal.platform.model.CreateReservationResponse;
import com.neohoods.portal.platform.model.PaginatedReservations;
import com.neohoods.portal.platform.model.PaymentSessionResponse;
import com.neohoods.portal.platform.model.PriceBreakdown;
import com.neohoods.portal.platform.model.Reservation;
import com.neohoods.portal.platform.model.ReservationFeedbackRequest;
import com.neohoods.portal.platform.model.ReservationStatus;
import com.neohoods.portal.platform.model.SpaceType;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.UsersService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.services.ReservationMapper;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;
import com.neohoods.portal.platform.spaces.services.StripeService;

import reactor.core.publisher.Mono;

@Service
public class ReservationsApiApiDelegateImpl implements ReservationsApiApiDelegate {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private UsersService usersService;
    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private StripeService stripeService;

    @Override
    public Mono<ResponseEntity<PaginatedReservations>> getMyReservations(
            ReservationStatus status, SpaceType spaceType, Integer page, Integer size, ServerWebExchange exchange) {
        return getCurrentUser(exchange)
                .map(user -> {
                    // Create a Pageable for getting user reservations with filters
                    Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);

                    // Convert API types to entity types
                    ReservationStatusForEntity entityStatus = status != null
                            ? convertApiStatusToEntityStatus(status)
                            : null;
                    SpaceTypeForEntity entitySpaceType = spaceType != null
                            ? convertApiTypeToEntityType(spaceType)
                            : null;

                    // Use the new filtered query
                    Page<ReservationEntity> pageResult = reservationsService.getUserReservationsWithFilters(
                            user.getId(), entityStatus, entitySpaceType, pageable);

                    // Convert to API models
                    List<Reservation> reservations = pageResult.getContent().stream()
                            .map(this::convertToApiModel)
                            .toList();

                    PaginatedReservations response = PaginatedReservations.builder()
                            .content(reservations)
                            .totalElements(BigDecimal.valueOf(pageResult.getTotalElements()))
                            .number(pageResult.getNumber())
                            .size(pageResult.getSize())
                            .build();
                    return ResponseEntity.ok(response);

                });
    }

    @Override
    public Mono<ResponseEntity<CreateReservationResponse>> createReservation(
            Mono<CreateReservationRequest> createReservationRequest, ServerWebExchange exchange) {
        return createReservationRequest.flatMap(request -> getCurrentUser(exchange).map(user -> {
            // Get space entity from request
            SpaceEntity space = spacesService.getSpaceById(request.getSpaceId());

            // Create reservation using service with correct parameters
            ReservationEntity savedEntity = reservationsService.createReservation(
                    space,
                    user,
                    request.getStartDate(),
                    request.getEndDate());

            // Return reservation without Stripe session (will be created on payment
            // initiation)
            CreateReservationResponse response = CreateReservationResponse.builder()
                    .reservation(convertToApiModel(savedEntity))
                    .build();
            return ResponseEntity.status(201).body(response);
        }));
    }

    @Override
    public Mono<ResponseEntity<Reservation>> getReservation(UUID reservationId, ServerWebExchange exchange) {
        return getCurrentUser(exchange)
                .map(user -> {
                    // Get user-specific reservation
                    ReservationEntity entity = reservationsService.getUserReservationById(user, reservationId);
                    Reservation reservation = convertToApiModel(entity);
                    return ResponseEntity.ok(reservation);
                });
    }

    @Override
    public Mono<ResponseEntity<CancelReservation200Response>> cancelReservation(UUID reservationId,
            ServerWebExchange exchange) {
        return getCurrentUser(exchange)
                .map(user -> {
                    // Cancel user reservation using service
                    reservationsService.cancelUserReservation(user, reservationId, "User cancelled");

                    CancelReservation200Response response = CancelReservation200Response.builder()
                            .message("Reservation cancelled successfully")
                            .refundAmount(0.0f)
                            .build();
                    return ResponseEntity.ok(response);
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> submitReservationFeedback(
            UUID reservationId, Mono<ReservationFeedbackRequest> reservationFeedbackRequest,
            ServerWebExchange exchange) {
        return getCurrentUser(exchange)
                .flatMap(user -> reservationFeedbackRequest.map(request -> {
                    // Submit feedback using service
                    reservationsService.submitReservationFeedback(user, reservationId, request.getRating(),
                            request.getComment());
                    return ResponseEntity.status(201).<Void>build();
                }));
    }

    @Override
    public Mono<ResponseEntity<PaymentSessionResponse>> initiatePayment(
            UUID reservationId, ServerWebExchange exchange) {
        return getCurrentUser(exchange)
                .map(user -> {
                    // Get reservation and space
                    ReservationEntity reservation = reservationsService.getUserReservationById(user, reservationId);
                    SpaceEntity space = spacesService.getSpaceById(reservation.getSpace().getId());

                    // Check if this is a retry for a failed payment
                    if (reservation.getStatus() == ReservationStatusForEntity.PAYMENT_FAILED) {
                        // Retry payment - reset expiration time
                        reservationsService.retryPayment(reservationId);
                        reservation = reservationsService.getUserReservationById(user, reservationId);
                    }

                    // Create Stripe PaymentIntent and Checkout Session
                    String paymentIntentId = stripeService.createPaymentIntent(reservation, user, space);
                    String stripeCheckoutUrl = stripeService.createCheckoutSession(reservation, user, space);

                    // Update reservation with payment intent ID
                    reservation.setStripePaymentIntentId(paymentIntentId);
                    reservationsService.updateReservation(reservation);

                    // Return payment session response
                    PaymentSessionResponse response = PaymentSessionResponse
                            .builder()
                            .stripeCheckoutUrl(java.net.URI.create(stripeCheckoutUrl))
                            .paymentIntentId(paymentIntentId)
                            .build();
                    return ResponseEntity.ok(response);
                });
    }

    @Override
    public Mono<ResponseEntity<ConfirmReservationPayment200Response>> confirmReservationPayment(
            UUID reservationId, Mono<ConfirmReservationPaymentRequest> confirmReservationPaymentRequest,
            ServerWebExchange exchange) {
        return confirmReservationPaymentRequest.flatMap(request -> {
            // Get reservation by ID (public endpoint, no user authentication required)
            ReservationEntity reservation = reservationsService.getReservationById(reservationId);

            if (reservation == null) {
                return Mono.just(ResponseEntity.notFound().build());
            }

            // For paid reservations, verify the session ID matches
            if (reservation.getTotalPrice().floatValue() > 0) {
                if (!request.getSessionId().equals(reservation.getStripeSessionId())) {
                    return Mono.just(ResponseEntity.badRequest().build());
                }
            }

            // Update reservation status based on frontend confirmation
            ReservationStatusForEntity newStatus;
            String message;

            if (request.getStatus() == ConfirmReservationPaymentRequest.StatusEnum.SUCCESS) {
                // Check if reservation requires payment
                if (reservation.getTotalPrice().floatValue() > 0) {
                    // Verify with Stripe that payment was actually successful
                    boolean paymentSuccessful = stripeService.verifyPaymentSuccess(reservation);

                    if (paymentSuccessful) {
                        // Use confirmReservation() instead of manual status update
                        // This properly generates access code, sends email, and updates payment status
                        try {
                            reservationsService.confirmReservation(
                                    reservationId,
                                    reservation.getStripePaymentIntentId(),
                                    reservation.getStripeSessionId());
                            newStatus = ReservationStatusForEntity.CONFIRMED;
                            message = "Payment confirmed successfully";
                        } catch (Exception e) {
                            // If confirmation fails (e.g., already confirmed), just update status
                            newStatus = ReservationStatusForEntity.CONFIRMED;
                            message = "Payment confirmed successfully";
                        }
                    } else {
                        newStatus = ReservationStatusForEntity.PENDING_PAYMENT;
                        message = "Payment verification failed";
                    }
                } else {
                    // Free reservation - no payment required
                    try {
                        reservationsService.confirmReservation(reservationId, null, null);
                        newStatus = ReservationStatusForEntity.CONFIRMED;
                        message = "Reservation confirmed successfully";
                    } catch (Exception e) {
                        // If confirmation fails (e.g., already confirmed), just update status
                        newStatus = ReservationStatusForEntity.CONFIRMED;
                        message = "Reservation confirmed successfully";
                    }
                }
            } else {
                // Payment was cancelled
                newStatus = ReservationStatusForEntity.CANCELLED;
                message = "Payment was cancelled";
            }
            reservation.setStatus(newStatus);
            reservationsService.updateReservation(reservation);
            // Get updated reservation to return correct status
            ReservationEntity updatedReservation = reservationsService.getReservationById(reservationId);

            // Return confirmation response
            ConfirmReservationPayment200Response response = ConfirmReservationPayment200Response.builder()
                    .reservationId(reservationId)
                    .status(convertEntityStatusToApiStatus(updatedReservation.getStatus()))
                    .message(message)
                    .build();

            return Mono.just(ResponseEntity.ok(response));
        });
    }

    // Helper methods
    private Reservation convertToApiModel(ReservationEntity entity) {
        // Calculate pricing details using utility method
        ReservationMapper.PricingDetails pricingDetails = ReservationMapper.calculatePricingDetails(entity);

        // Create PriceBreakdown object
        PriceBreakdown priceBreakdown = new PriceBreakdown();
        priceBreakdown.setUnitPrice(pricingDetails.unitPrice.floatValue());
        priceBreakdown.setNumberOfDays((int) pricingDetails.numberOfDays);
        priceBreakdown.setTotalDaysPrice(pricingDetails.totalDaysPrice.floatValue());
        priceBreakdown.setCleaningFee(pricingDetails.cleaningFee.floatValue());
        priceBreakdown.setSubtotal(pricingDetails.subtotal.floatValue());
        priceBreakdown.setDeposit(pricingDetails.deposit.floatValue());
        priceBreakdown.setPlatformFeeAmount(pricingDetails.platformFeeAmount.floatValue());
        priceBreakdown.setPlatformFixedFeeAmount(pricingDetails.platformFixedFeeAmount.floatValue());
        priceBreakdown.setTotalPrice(pricingDetails.totalPrice.floatValue());

        return Reservation.builder()
                .id(entity.getId())
                .spaceId(entity.getSpace().getId())
                .userId(entity.getUser().getId())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(convertEntityStatusToApiStatus(entity.getStatus()))
                .totalPrice(entity.getTotalPrice().floatValue())
                .currency(entity.getSpace().getCurrency())
                .cleaningFee(pricingDetails.cleaningFee.floatValue())
                .deposit(pricingDetails.deposit.floatValue())
                .platformFeeAmount(pricingDetails.platformFeeAmount.floatValue())
                .platformFixedFeeAmount(pricingDetails.platformFixedFeeAmount.floatValue())
                .priceBreakdown(priceBreakdown)
                .stripePaymentIntentId(entity.getStripePaymentIntentId())
                .stripeSessionId(entity.getStripeSessionId())
                .paymentExpiresAt(
                        entity.getPaymentExpiresAt() != null ? entity.getPaymentExpiresAt().atOffset(ZoneOffset.UTC)
                                : null)
                .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .build();
    }

    private ReservationStatus convertEntityStatusToApiStatus(
            ReservationStatusForEntity entityStatus) {
        return switch (entityStatus) {
            case PENDING_PAYMENT -> ReservationStatus.PENDING_PAYMENT;
            case PAYMENT_FAILED -> ReservationStatus.PAYMENT_FAILED;
            case EXPIRED -> ReservationStatus.EXPIRED;
            case CONFIRMED -> ReservationStatus.CONFIRMED;
            case ACTIVE -> ReservationStatus.ACTIVE;
            case COMPLETED -> ReservationStatus.COMPLETED;
            case CANCELLED -> ReservationStatus.CANCELLED;
            case REFUNDED -> ReservationStatus.REFUNDED;
        };
    }

    private SpaceType convertEntityTypeToApiType(SpaceTypeForEntity entityType) {
        return switch (entityType) {
            case GUEST_ROOM -> SpaceType.GUEST_ROOM;
            case COMMON_ROOM -> SpaceType.COMMON_ROOM;
            case COWORKING -> SpaceType.COWORKING;
            case PARKING -> SpaceType.PARKING;
        };
    }

    private com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity convertApiTypeToEntityType(
            SpaceType apiType) {
        return switch (apiType) {
            case GUEST_ROOM -> SpaceTypeForEntity.GUEST_ROOM;
            case COMMON_ROOM -> SpaceTypeForEntity.COMMON_ROOM;
            case COWORKING -> SpaceTypeForEntity.COWORKING;
            case PARKING -> SpaceTypeForEntity.PARKING;
        };
    }

    private ReservationStatusForEntity convertApiStatusToEntityStatus(ReservationStatus apiStatus) {
        return switch (apiStatus) {
            case PENDING_PAYMENT -> ReservationStatusForEntity.PENDING_PAYMENT;
            case CONFIRMED -> ReservationStatusForEntity.CONFIRMED;
            case ACTIVE -> ReservationStatusForEntity.ACTIVE;
            case COMPLETED -> ReservationStatusForEntity.COMPLETED;
            case CANCELLED -> ReservationStatusForEntity.CANCELLED;
            default -> ReservationStatusForEntity.PENDING_PAYMENT;
        };
    }

    // Helper method to get current user from security context
    private Mono<UserEntity> getCurrentUser(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(principal -> UUID.fromString(principal.getName()))
                .map(userId -> usersRepository.findById(userId).orElseThrow());
    }

    /**
     * Debug method to test reservation queries
     * Usage: Call this method directly from your service or controller
     */
    public List<Reservation> debugReservationsForSpace(UUID spaceId, String startDateStr, String endDateStr) {
        List<ReservationEntity> reservations = reservationsService.debugReservationsForSpace(
                spaceId, startDateStr, endDateStr);

        return reservations.stream()
                .map(this::convertToApiModel)
                .collect(java.util.stream.Collectors.toList());
    }
}
