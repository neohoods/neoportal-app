package com.neohoods.portal.platform.api.admin.units;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.UnitsAdminApiApiDelegate;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.InviteUserRequest;
import com.neohoods.portal.platform.model.PaginatedUnits;
import com.neohoods.portal.platform.model.Unit;
import com.neohoods.portal.platform.model.UnitMember;
import com.neohoods.portal.platform.model.UnitRequest;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.UnitInvitationService;
import com.neohoods.portal.platform.services.UnitsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitsAdminApiApiDelegateImpl implements UnitsAdminApiApiDelegate {
    private final UnitsService unitsService;
    private final UnitInvitationService invitationService;
    private final UsersRepository usersRepository;

    @Override
    public Mono<ResponseEntity<PaginatedUnits>> getAdminUnits(Integer page, Integer size, String search, ServerWebExchange exchange) {
        int pageNum = page != null ? Math.max(0, page - 1) : 0;
        int pageSize = size != null ? size : 20;
        return unitsService.getUnitsPaginated(pageNum, pageSize, search)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to get units", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Unit>> createUnit(Mono<UnitRequest> unitRequest, ServerWebExchange exchange) {
        return unitRequest
                .flatMap(request -> {
                    UUID initialAdminId = request.getInitialAdminId();
                    return unitsService.createUnit(request.getName(), initialAdminId);
                })
                .map(ResponseEntity.status(HttpStatus.CREATED)::body)
                .onErrorResume(e -> {
                    log.error("Failed to create unit", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Unit>> getAdminUnit(UUID unitId, ServerWebExchange exchange) {
        return unitsService.getUnitById(unitId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.UNIT_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to get unit", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Unit>> updateUnit(UUID unitId, Mono<UnitRequest> unitRequest, ServerWebExchange exchange) {
        return unitRequest
                .flatMap(request -> unitsService.updateUnit(unitId, request.getName()))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.UNIT_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to update unit", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteUnit(UUID unitId, ServerWebExchange exchange) {
        return unitsService.deleteUnit(unitId)
                .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).<Void>build()))
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.UNIT_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to delete unit", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Void>build());
                });
    }

    @Override
    public Mono<ResponseEntity<UnitMember>> addMember(UUID unitId, Mono<InviteUserRequest> inviteUserRequest, ServerWebExchange exchange) {
        return inviteUserRequest
                .flatMap(request -> {
                    UUID userId = request.getUserId();
                    String email = request.getEmail();
                    
                    if (userId != null) {
                        // Directly add member if userId is provided
                        return unitsService.addMemberToUnit(unitId, userId, null)
                                .map(member -> {
                                    UnitMember apiMember = new UnitMember();
                                    apiMember.setUserId(member.getUserId());
                                    apiMember.setUser(member.getUser());
                                    apiMember.setRole(member.getRole());
                                    apiMember.setJoinedAt(member.getJoinedAt());
                                    return apiMember;
                                });
                    } else if (email != null && !email.trim().isEmpty()) {
                        // Find user by email and add directly
                        com.neohoods.portal.platform.entities.UserEntity user = usersRepository.findByEmail(email);
                        if (user == null) {
                            return Mono.error(new CodedErrorException(CodedError.USER_NOT_FOUND, "email", email));
                        }
                        return unitsService.addMemberToUnit(unitId, user.getId(), null)
                                .map(member -> {
                                    UnitMember apiMember = new UnitMember();
                                    apiMember.setUserId(member.getUserId());
                                    apiMember.setUser(member.getUser());
                                    apiMember.setRole(member.getRole());
                                    apiMember.setJoinedAt(member.getJoinedAt());
                                    return apiMember;
                                });
                    } else {
                        return Mono.error(new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "userId or email"));
                    }
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.UNIT_NOT_FOUND || ex.getError() == CodedError.USER_NOT_FOUND) {
                            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                        }
                        return Mono.error(e);
                    }
                    log.error("Failed to add member", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> revokeAdminMember(UUID unitId, UUID userId, ServerWebExchange exchange) {
        // For admin API, we use a system user ID (null) as the removedBy parameter
        // The service will need to handle this case - we'll need to update the service to allow null for admin operations
        return unitsService.removeMemberFromUnit(unitId, userId, null)
                .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).<Void>build()))
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
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
    public Mono<ResponseEntity<UnitMember>> promoteAdminMember(UUID unitId, UUID userId, ServerWebExchange exchange) {
        // For admin API, we use a system user ID (null) as the promotedBy parameter
        return unitsService.promoteToAdmin(unitId, userId, null)
                .map(member -> {
                    UnitMember apiMember = new UnitMember();
                    apiMember.setUserId(member.getUserId());
                    apiMember.setUser(member.getUser());
                    apiMember.setRole(member.getRole());
                    apiMember.setJoinedAt(member.getJoinedAt());
                    return apiMember;
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
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
    public Mono<ResponseEntity<UnitMember>> demoteAdminMember(UUID unitId, UUID userId, ServerWebExchange exchange) {
        // For admin API, we use a system user ID (null) as the demotedBy parameter
        return unitsService.demoteFromAdmin(unitId, userId, null)
                .map(member -> {
                    UnitMember apiMember = new UnitMember();
                    apiMember.setUserId(member.getUserId());
                    apiMember.setUser(member.getUser());
                    apiMember.setRole(member.getRole());
                    apiMember.setJoinedAt(member.getJoinedAt());
                    return apiMember;
                })
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof CodedErrorException) {
                        CodedErrorException ex = (CodedErrorException) e;
                        if (ex.getError() == CodedError.CANNOT_DEMOTE_LAST_ADMIN) {
                            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
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
}

