package com.neohoods.portal.platform.api.hub.profile;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.ProfileHubApiApiDelegate;
import com.neohoods.portal.platform.model.User;
import com.neohoods.portal.platform.services.UsersService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCommunityApi implements ProfileHubApiApiDelegate {

    private final UsersService usersService;

    @Override
    public Mono<ResponseEntity<User>> updateProfile(Mono<User> userMono, ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(principal -> UUID.fromString(principal.getName()))
                .flatMap(userId -> userMono.flatMap(user -> {
                    if (!userId.equals(user.getId())) {
                        log.warn("User {} is not allowed to update user {}", userId, user.getId());
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<User>build());
                    }
                    return usersService.updateProfile(userId, user)
                            .map(ResponseEntity::ok);
                }))
                .onErrorResume(e -> {
                    // Let CodedErrorException pass through to GlobalExceptionHandler
                    if (e instanceof com.neohoods.portal.platform.exceptions.CodedErrorException) {
                        return Mono.error(e);
                    }
                    log.error("Failed to update user profile", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @Override
    public Mono<ResponseEntity<User>> getProfile(ServerWebExchange exchange) {
        log.info("Getting profile - Principal: {}", exchange.getPrincipal());
        return exchange.getPrincipal()
                .doOnNext(principal -> log.info("Principal found: {}, name: {}", principal, principal.getName()))
                .map(principal -> UUID.fromString(principal.getName()))
                .doOnNext(userId -> log.info("User ID extracted: {}", userId))
                .flatMap(userId -> usersService.getProfile(userId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to get user profile", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}