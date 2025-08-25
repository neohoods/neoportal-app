package com.neohoods.portal.platform.api.hub;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.HelloWorldApiApiDelegate;
import com.neohoods.portal.platform.model.ResponseHelloWorld;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class HelloWorldApi implements HelloWorldApiApiDelegate {


    @Override
    public Mono<ResponseEntity<ResponseHelloWorld>> getHelloWorld(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(ResponseHelloWorld.builder()
                .message("hello test!")
                .build()));
    }
}
