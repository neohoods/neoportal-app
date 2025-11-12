package com.neohoods.portal.platform.api.hub.users;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.UsersHubApiApiDelegate;
import com.neohoods.portal.platform.model.UserBasic;
import com.neohoods.portal.platform.services.UsersService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsersHubApi implements UsersHubApiApiDelegate {

    private final UsersService usersService;

    @Override
    public Mono<ResponseEntity<Flux<UserBasic>>> getHubUsers(ServerWebExchange exchange) {
        log.info("Retrieving all users for hub (basic information only)");
        return Mono.just(ResponseEntity.ok(usersService.getUsersBasic()));
    }
}
