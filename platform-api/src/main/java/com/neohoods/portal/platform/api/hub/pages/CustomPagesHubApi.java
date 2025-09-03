package com.neohoods.portal.platform.api.hub.pages;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.CustomPagesHubApiApiDelegate;
import com.neohoods.portal.platform.model.CustomPage;
import com.neohoods.portal.platform.model.GetCustomPageRefs200ResponseInner;
import com.neohoods.portal.platform.services.CustomPagesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomPagesHubApi implements CustomPagesHubApiApiDelegate {

    private final CustomPagesService customPagesService;

    @Override
    public Mono<ResponseEntity<CustomPage>> getCustomPage(String pageRef, ServerWebExchange exchange) {
        return customPagesService.getCustomPage(pageRef)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Flux<GetCustomPageRefs200ResponseInner>>> getCustomPageRefs(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(customPagesService.getCustomPageRefs()));
    }

}