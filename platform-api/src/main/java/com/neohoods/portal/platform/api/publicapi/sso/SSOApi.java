package com.neohoods.portal.platform.api.publicapi.sso;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.SsoPublicApiApiDelegate;
import com.neohoods.portal.platform.model.ExchangeSSOTokenRequest;
import com.neohoods.portal.platform.model.GenerateSSOLoginUrl200Response;
import com.neohoods.portal.platform.services.SSOService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class SSOApi implements SsoPublicApiApiDelegate {

    private final SSOService ssoService;

    @Override
    public Mono<ResponseEntity<GenerateSSOLoginUrl200Response>> generateSSOLoginUrl(ServerWebExchange exchange) {
        return Mono.just(
                ResponseEntity.ok(
                        GenerateSSOLoginUrl200Response.builder().loginUrl(ssoService.generateSSOLoginUrl().toString())
                                .build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> exchangeSSOToken(Mono<ExchangeSSOTokenRequest> request,
            ServerWebExchange exchange) {
        return request.flatMap(r -> {
            return ssoService.tokenExchange(exchange, r.getState(), r.getAuthorizationCode())
                    .map(success -> {
                        if (success) {
                            return ResponseEntity.ok().<Void>build();
                        } else {
                            return ResponseEntity.badRequest().<Void>build();
                        }
                    });
        });
    }
}