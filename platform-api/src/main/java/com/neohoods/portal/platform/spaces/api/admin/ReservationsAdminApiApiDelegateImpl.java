package com.neohoods.portal.platform.spaces.api.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import org.openapitools.jackson.nullable.JsonNullable;

import com.neohoods.portal.platform.api.ReservationsAdminApiApiDelegate;
import com.neohoods.portal.platform.model.AccessCode;
import com.neohoods.portal.platform.model.PaginatedReservations;
import com.neohoods.portal.platform.model.Reservation;
import com.neohoods.portal.platform.model.ReservationAuditLog;
import com.neohoods.portal.platform.model.ReservationStatus;
import com.neohoods.portal.platform.model.Space;
import com.neohoods.portal.platform.model.SpaceStatus;
import com.neohoods.portal.platform.model.SpaceType;
import com.neohoods.portal.platform.model.User;
import com.neohoods.portal.platform.model.UserType;
import com.neohoods.portal.platform.spaces.entities.ReservationAuditLogEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.services.ReservationAuditService;
import com.neohoods.portal.platform.spaces.services.ReservationsService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ReservationsAdminApiApiDelegateImpl implements ReservationsAdminApiApiDelegate {

    @Autowired
    private ReservationsService reservationsService;

    @Autowired
    private ReservationAuditService auditService;

    @Override
    public Mono<ResponseEntity<PaginatedReservations>> getAdminReservations(
            UUID spaceId, UUID userId, ReservationStatus status, LocalDate startDate, LocalDate endDate,
            SpaceType spaceType, String search, Integer page, Integer size, ServerWebExchange exchange) {
        // Create a Pageable for getting reservations with filters
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);

        // Convert API status to entity status
        ReservationStatusForEntity entityStatus = status != null ? convertApiStatusToEntityStatus(status) : null;
        
        // Convert API spaceType to entity spaceType
        SpaceTypeForEntity entitySpaceType = spaceType != null ? convertApiTypeToEntityType(spaceType) : null;

        // Use the new filtered query (search parameter removed - replaced by userId filter)
        Page<ReservationEntity> pageResult = reservationsService.getReservationsWithFilters(
                spaceId, userId, entityStatus, startDate, endDate, entitySpaceType, pageable);

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
        return Mono.just(ResponseEntity.ok(response));
    }

    @Override
    public Mono<ResponseEntity<Reservation>> getAdminReservation(UUID reservationId, ServerWebExchange exchange) {
        ReservationEntity entity = reservationsService.getReservationById(reservationId);
        Reservation reservation = convertToApiModel(entity);
        return Mono.just(ResponseEntity.ok(reservation));
    }

    @Override
    public Mono<ResponseEntity<AccessCode>> getAccessCode(UUID reservationId, ServerWebExchange exchange) {
        ReservationEntity entity = reservationsService.getReservationById(reservationId);

        // Get access code for reservation
        String accessCode = reservationsService.getAccessCodeForReservation(entity);

        AccessCode code = AccessCode.builder()
                .code(accessCode)
                .generatedAt(OffsetDateTime.now())
                .expiresAt(entity.getEndDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC))
                .isActive(true)
                .build();
        return Mono.just(ResponseEntity.ok(code));
    }

    @Override
    public Mono<ResponseEntity<AccessCode>> regenerateAccessCode(UUID reservationId, ServerWebExchange exchange) {
        ReservationEntity entity = reservationsService.getReservationById(reservationId);

        // Regenerate access code for reservation
        String newAccessCode = reservationsService.regenerateAccessCodeForReservation(entity);

        AccessCode code = AccessCode.builder()
                .code(newAccessCode)
                .generatedAt(OffsetDateTime.now())
                .expiresAt(entity.getEndDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC))
                .isActive(true)
                .build();
        return Mono.just(ResponseEntity.ok(code));
    }

    @Override
    public Mono<ResponseEntity<Flux<ReservationAuditLog>>> getReservationAuditLogs(
            UUID reservationId, ServerWebExchange exchange) {

        try {
            List<ReservationAuditLogEntity> auditLogs = auditService.getAuditLogsForReservation(reservationId);

            Flux<ReservationAuditLog> responseLogs = Flux.fromIterable(auditLogs.stream()
                    .map(this::convertAuditLogToResponse)
                    .toList());

            return Mono.just(ResponseEntity.ok(responseLogs));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    private ReservationAuditLog convertAuditLogToResponse(ReservationAuditLogEntity entity) {
        ReservationAuditLog log = new ReservationAuditLog();
        log.setId(entity.getId());
        log.setReservationId(entity.getReservationId());
        log.setEventType(ReservationAuditLog.EventTypeEnum.fromValue(entity.getEventType()));
        log.setOldValue(JsonNullable.of(entity.getOldValue()));
        log.setNewValue(JsonNullable.of(entity.getNewValue()));
        log.setLogMessage(entity.getLogMessage());
        log.setPerformedBy(entity.getPerformedBy());
        log.setCreatedAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        return log;
    }

    // Helper methods for conversion
    private Reservation convertToApiModel(ReservationEntity entity) {
        return Reservation.builder()
                .id(entity.getId())
                .spaceId(entity.getSpace().getId())
                .userId(entity.getUser().getId())
                .space(convertSpaceToApiModel(entity.getSpace()))
                .user(convertUserToApiModel(entity.getUser()))
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(convertEntityStatusToApiStatus(entity.getStatus()))
                .totalPrice(entity.getTotalPrice().floatValue())
                .currency(entity.getSpace().getCurrency())
                .cleaningFee(
                        entity.getSpace().getCleaningFee() != null ? entity.getSpace().getCleaningFee().floatValue()
                                : null)
                .deposit(entity.getSpace().getDeposit() != null ? entity.getSpace().getDeposit().floatValue() : null)
                .platformFeeAmount(
                        entity.getPlatformFeeAmount() != null ? entity.getPlatformFeeAmount().floatValue() : null)
                .platformFixedFeeAmount(
                        entity.getPlatformFixedFeeAmount() != null ? entity.getPlatformFixedFeeAmount().floatValue()
                                : null)
                .stripePaymentIntentId(entity.getStripePaymentIntentId())
                .stripeSessionId(entity.getStripeSessionId())
                .paymentExpiresAt(entity.getPaymentExpiresAt() != null
                        ? entity.getPaymentExpiresAt().atOffset(java.time.ZoneOffset.UTC)
                        : null)
                .createdAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC))
                .build();
    }

    private ReservationStatus convertEntityStatusToApiStatus(
            ReservationStatusForEntity entityStatus) {
        return switch (entityStatus) {
            case EXPIRED -> ReservationStatus.EXPIRED;
            case PENDING_PAYMENT -> ReservationStatus.PENDING_PAYMENT;
            case CONFIRMED -> ReservationStatus.CONFIRMED;
            case ACTIVE -> ReservationStatus.ACTIVE;
            case COMPLETED -> ReservationStatus.COMPLETED;
            case CANCELLED -> ReservationStatus.CANCELLED;
            default -> ReservationStatus.PENDING_PAYMENT;
        };
    }

    private ReservationStatusForEntity convertApiStatusToEntityStatus(ReservationStatus apiStatus) {
        return switch (apiStatus) {
            case EXPIRED -> ReservationStatusForEntity.EXPIRED;
            case PENDING_PAYMENT -> ReservationStatusForEntity.PENDING_PAYMENT;
            case CONFIRMED -> ReservationStatusForEntity.CONFIRMED;
            case ACTIVE -> ReservationStatusForEntity.ACTIVE;
            case COMPLETED -> ReservationStatusForEntity.COMPLETED;
            case CANCELLED -> ReservationStatusForEntity.CANCELLED;
            default -> ReservationStatusForEntity.PENDING_PAYMENT;
        };
    }

    private Space convertSpaceToApiModel(SpaceEntity entity) {
        return Space.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(convertEntityTypeToApiType(entity.getType()))
                .status(convertEntityStatusToApiStatus(entity.getStatus()))
                .instructions(entity.getInstructions())
                .pricing(null) // Will be populated separately if needed
                .rules(null) // Will be populated separately if needed
                .images(new java.util.ArrayList<>())
                .accessCodeEnabled(entity.getAccessCodeEnabled())
                .createdAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC))
                .build();
    }

    private User convertUserToApiModel(com.neohoods.portal.platform.entities.UserEntity entity) {
        return User.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .isEmailVerified(entity.isEmailVerified())
                .type(convertEntityUserTypeToApiType(entity.getType()))
                .username(entity.getUsername())
                .disabled(entity.isDisabled())
                .streetAddress(entity.getStreetAddress())
                .city(entity.getCity())
                .postalCode(entity.getPostalCode())
                .country(entity.getCountry())
                .createdAt(entity.getCreatedAt())
                .properties(new java.util.ArrayList<>())
                .build();
    }

    private SpaceType convertEntityTypeToApiType(SpaceTypeForEntity entityType) {
        return switch (entityType) {
            case GUEST_ROOM -> SpaceType.GUEST_ROOM;
            case COMMON_ROOM -> SpaceType.COMMON_ROOM;
            case COWORKING -> SpaceType.COWORKING;
            case PARKING -> SpaceType.PARKING;
        };
    }

    private SpaceStatus convertEntityStatusToApiStatus(SpaceStatusForEntity entityStatus) {
        return switch (entityStatus) {
            case ACTIVE -> SpaceStatus.ACTIVE;
            case INACTIVE -> SpaceStatus.INACTIVE;
            case MAINTENANCE -> SpaceStatus.MAINTENANCE;
            default -> SpaceStatus.ACTIVE; // Fallback for any missing cases
        };
    }

    private UserType convertEntityUserTypeToApiType(com.neohoods.portal.platform.entities.UserType entityType) {
        return switch (entityType) {
            case ADMIN -> UserType.ADMIN;
            case OWNER -> UserType.OWNER;
            case LANDLORD -> UserType.LANDLORD;
            case TENANT -> UserType.TENANT;
            case SYNDIC -> UserType.SYNDIC;
            case EXTERNAL -> UserType.EXTERNAL;
            case CONTRACTOR -> UserType.CONTRACTOR;
            case COMMERCIAL_PROPERTY_OWNER -> UserType.COMMERCIAL_PROPERTY_OWNER;
            case GUEST -> UserType.GUEST;
            default -> UserType.ADMIN; // Fallback for any missing cases
        };
    }

    private SpaceTypeForEntity convertApiTypeToEntityType(SpaceType apiType) {
        return switch (apiType) {
            case GUEST_ROOM -> SpaceTypeForEntity.GUEST_ROOM;
            case COMMON_ROOM -> SpaceTypeForEntity.COMMON_ROOM;
            case COWORKING -> SpaceTypeForEntity.COWORKING;
            case PARKING -> SpaceTypeForEntity.PARKING;
        };
    }
}
