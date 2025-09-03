package com.neohoods.portal.platform.api.admin;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.UsersAdminApiApiDelegate;
import com.neohoods.portal.platform.model.SetUserPasswordRequest;
import com.neohoods.portal.platform.model.User;
import com.neohoods.portal.platform.services.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsersAdminApi implements UsersAdminApiApiDelegate {
    private final UsersService usersService;

    @Override
    public Mono<ResponseEntity<User>> getUser(UUID userId, ServerWebExchange exchange) {
        return usersService.getUserById(userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Flux<User>>> getUsers(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(usersService.getUsers()));
    }

    @Override
    public Mono<ResponseEntity<User>> saveUser(Mono<User> user, ServerWebExchange exchange) {
        return user.flatMap(usersService::saveUser)
                .map(ResponseEntity::ok);

    }

    @Override
    public Mono<ResponseEntity<Void>> setUserPassword(UUID userId, Mono<SetUserPasswordRequest> setUserPasswordRequest,
            ServerWebExchange exchange) {
        return setUserPasswordRequest.doOnNext(request -> usersService.setUserPassword(userId, request.getNewPassword()))
                .map(r -> ResponseEntity.noContent().build());
    }
}