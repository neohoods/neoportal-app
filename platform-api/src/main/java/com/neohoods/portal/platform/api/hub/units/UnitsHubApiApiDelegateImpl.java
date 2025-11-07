package com.neohoods.portal.platform.api.hub.units;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.UnitsHubApiApiDelegate;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.InviteUserRequest;
import com.neohoods.portal.platform.model.Unit;
import com.neohoods.portal.platform.model.UnitInvitation;
import com.neohoods.portal.platform.model.UnitMember;
import com.neohoods.portal.platform.services.UnitInvitationService;
import com.neohoods.portal.platform.services.UnitsService;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.UnitCalendarService;
import com.neohoods.portal.platform.model.Reservation;
import com.neohoods.portal.platform.model.PaginatedReservations;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitsHubApiApiDelegateImpl implements UnitsHubApiApiDelegate {
    private final UnitsService unitsService;
    private final UnitInvitationService invitationService;
    private final ReservationsService reservationsService;
    private final UnitCalendarService unitCalendarService;

    private Mono<UUID> getCurrentUserId(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(principal -> UUID.fromString(principal.getName()));
    }

    @Override
    public Mono<ResponseEntity<Flux<Unit>>> getUnits(ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .doOnNext(userId -> log.debug("Getting units for user: {}", userId))
                .flatMap(userId -> {
                    Flux<Unit> units = unitsService.getUnitsForUser(userId);
                    return units.collectList()
                            .doOnNext(unitsList -> log.debug("Found {} units for user {}", unitsList.size(), userId))
                            .map(unitsList -> ResponseEntity.ok(Flux.fromIterable(unitsList)));
                })
                .onErrorResume(e -> {
                    log.error("Failed to get units", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Unit>> getUnit(UUID unitId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(userId -> {
                    // Verify user is member of unit
                    if (!unitsService.isUserMemberOfUnit(userId, unitId)) {
                        return Mono.error(new CodedErrorException(CodedError.USER_NOT_MEMBER_OF_UNIT, "unitId", unitId.toString()));
                    }
                    return unitsService.getUnitById(unitId);
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        return Mono.error(e);
                    }
                    log.error("Failed to get unit", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<UnitInvitation>> inviteUser(UUID unitId, Mono<InviteUserRequest> inviteUserRequest, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(currentUserId -> inviteUserRequest.flatMap(request -> {
                    UUID userId = request.getUserId();
                    String email = request.getEmail();
                    return invitationService.inviteUser(unitId, userId, email, currentUserId);
                }))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.USER_NOT_ADMIN_OF_UNIT) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                        }
                        if (ex.getError() == CodedError.UNIT_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to invite user", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<UnitMember>> approveInvitation(UUID unitId, UUID userId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(currentUserId -> {
                    // Find pending invitation for this user in this unit
                    return invitationService.getPendingInvitationsForUnit(unitId)
                            .filter(inv -> {
                                UUID invitedUserId = inv.getInvitedUserId() != null ? inv.getInvitedUserId().get() : null;
                                return invitedUserId != null && invitedUserId.equals(userId);
                            })
                            .next()
                            .switchIfEmpty(Mono.error(new CodedErrorException(CodedError.UNIT_INVITATION_NOT_FOUND, "userId", userId.toString())))
                            .flatMap(inv -> invitationService.approveInvitation(inv.getId(), currentUserId))
                            .map(member -> {
                                UnitMember apiMember = new UnitMember();
                                apiMember.setUserId(member.getUser().getId());
                                apiMember.setUser(member.getUser().toUser().build());
                                apiMember.setRole(UnitMember.RoleEnum.fromValue(member.getRole().name()));
                                apiMember.setJoinedAt(member.getJoinedAt());
                                return apiMember;
                            })
                            .map(ResponseEntity::ok);
                })
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.USER_NOT_ADMIN_OF_UNIT) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                        }
                        if (ex.getError() == CodedError.UNIT_INVITATION_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to approve invitation", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> revokeMember(UUID unitId, UUID userId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(currentUserId -> unitsService.removeMemberFromUnit(unitId, userId, currentUserId))
                .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).<Void>build()))
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.USER_NOT_ADMIN_OF_UNIT) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build());
                        }
                        if (ex.getError() == CodedError.UNIT_MEMBER_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to revoke member", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Void>build());
                });
    }

    @Override
    public Mono<ResponseEntity<UnitMember>> promoteAdmin(UUID unitId, UUID userId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(currentUserId -> unitsService.promoteToAdmin(unitId, userId, currentUserId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.USER_NOT_ADMIN_OF_UNIT) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                        }
                        if (ex.getError() == CodedError.UNIT_MEMBER_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to promote admin", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<UnitMember>> demoteAdmin(UUID unitId, UUID userId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(currentUserId -> unitsService.demoteFromAdmin(unitId, userId, currentUserId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.CANNOT_DEMOTE_LAST_ADMIN) {
                            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                        }
                        if (ex.getError() == CodedError.USER_NOT_ADMIN_OF_UNIT) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                        }
                        if (ex.getError() == CodedError.UNIT_MEMBER_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to demote admin", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<UnitMember>> acceptInvitation(UUID invitationId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(userId -> invitationService.acceptInvitation(invitationId, userId))
                .map(member -> {
                    UnitMember apiMember = new UnitMember();
                    apiMember.setUserId(member.getUser().getId());
                    apiMember.setUser(member.getUser().toUser().build());
                    apiMember.setRole(UnitMember.RoleEnum.fromValue(member.getRole().name()));
                    apiMember.setJoinedAt(member.getJoinedAt());
                    return apiMember;
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.UNIT_INVITATION_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to accept invitation", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> rejectInvitation(UUID invitationId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(userId -> invitationService.rejectInvitation(invitationId, userId))
                .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).<Void>build()))
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.UNIT_INVITATION_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to reject invitation", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Void>build());
                });
    }

    // Note: These methods will be added to the interface when OpenAPI is regenerated
    // For now, we implement them here and they'll be available after regeneration
    
    public Mono<ResponseEntity<PaginatedReservations>> getUnitReservations(
            UUID unitId, Integer page, Integer size, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(userId -> {
                    // Verify user is member of unit
                    if (!unitsService.isUserMemberOfUnit(userId, unitId)) {
                        return Mono.error(new CodedErrorException(CodedError.USER_NOT_MEMBER_OF_UNIT, "unitId", unitId.toString()));
                    }
                    
                    Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
                    Page<ReservationEntity> pageResult = reservationsService.getReservationsByUnit(unitId, pageable);
                    
                    // Convert to API models
                    List<Reservation> reservations = pageResult.getContent().stream()
                            .map(this::convertToApiModel)
                            .collect(Collectors.toList());
                    
                    PaginatedReservations response = PaginatedReservations.builder()
                            .content(reservations)
                            .totalElements(BigDecimal.valueOf(pageResult.getTotalElements()))
                            .number(pageResult.getNumber())
                            .size(pageResult.getSize())
                            .build();
                    
                    return Mono.just(ResponseEntity.ok(response));
                })
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.USER_NOT_MEMBER_OF_UNIT) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to get unit reservations", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    public Mono<ResponseEntity<String>> getUnitCalendar(UUID unitId, ServerWebExchange exchange) {
        return getCurrentUserId(exchange)
                .flatMap(userId -> {
                    // Verify user is member of unit
                    if (!unitsService.isUserMemberOfUnit(userId, unitId)) {
                        return Mono.error(new CodedErrorException(CodedError.USER_NOT_MEMBER_OF_UNIT, "unitId", unitId.toString()));
                    }
                    
                    String icsContent = unitCalendarService.generateICSForUnit(unitId);
                    return Mono.just(ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("text/calendar"))
                            .body(icsContent));
                })
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.USER_NOT_MEMBER_OF_UNIT) {
                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to get unit calendar", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    private Reservation convertToApiModel(ReservationEntity entity) {
        Reservation.ReservationBuilder builder = Reservation.builder()
                .id(entity.getId())
                .spaceId(entity.getSpace().getId())
                .userId(entity.getUser().getId())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(convertEntityStatusToApiStatus(entity.getStatus()))
                .totalPrice(entity.getTotalPrice().floatValue())
                .currency(entity.getSpace().getCurrency())
                .cleaningFee(entity.getSpace().getCleaningFee() != null 
                        ? entity.getSpace().getCleaningFee().floatValue() : null)
                .deposit(entity.getSpace().getDeposit() != null 
                        ? entity.getSpace().getDeposit().floatValue() : null)
                .platformFeeAmount(entity.getPlatformFeeAmount() != null 
                        ? entity.getPlatformFeeAmount().floatValue() : null)
                .platformFixedFeeAmount(entity.getPlatformFixedFeeAmount() != null 
                        ? entity.getPlatformFixedFeeAmount().floatValue() : null)
                .stripePaymentIntentId(entity.getStripePaymentIntentId())
                .stripeSessionId(entity.getStripeSessionId())
                .paymentExpiresAt(entity.getPaymentExpiresAt() != null
                        ? entity.getPaymentExpiresAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .createdAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC));
        
        // Add unitId if present
        if (entity.getUnit() != null) {
            builder.unitId(entity.getUnit().getId());
        }
        
        return builder.build();
    }

    private com.neohoods.portal.platform.model.ReservationStatus convertEntityStatusToApiStatus(
            com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity status) {
        return switch (status) {
            case PENDING_PAYMENT -> com.neohoods.portal.platform.model.ReservationStatus.PENDING_PAYMENT;
            case PAYMENT_FAILED -> com.neohoods.portal.platform.model.ReservationStatus.PAYMENT_FAILED;
            case EXPIRED -> com.neohoods.portal.platform.model.ReservationStatus.EXPIRED;
            case CONFIRMED -> com.neohoods.portal.platform.model.ReservationStatus.CONFIRMED;
            case ACTIVE -> com.neohoods.portal.platform.model.ReservationStatus.ACTIVE;
            case CANCELLED -> com.neohoods.portal.platform.model.ReservationStatus.CANCELLED;
            case COMPLETED -> com.neohoods.portal.platform.model.ReservationStatus.COMPLETED;
            case REFUNDED -> com.neohoods.portal.platform.model.ReservationStatus.REFUNDED;
        };
    }
}

