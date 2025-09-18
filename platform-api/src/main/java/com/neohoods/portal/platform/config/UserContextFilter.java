package com.neohoods.portal.platform.config;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.neohoods.portal.platform.services.UserContextService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1) // After authentication filters
@RequiredArgsConstructor
public class UserContextFilter implements WebFilter {
    private static final String USER_ID_MDC_KEY = "userId";
    private static final String USERNAME_MDC_KEY = "username";

    private final UserContextService userContextService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .flatMap(principal -> {
                    if (principal != null) {
                        String principalName = principal.getName();
                        MDC.put(USER_ID_MDC_KEY, principalName);

                        // Try to get username from cache/database
                        try {
                            UUID userId = UUID.fromString(principalName);
                            return userContextService.getUsername(userId)
                                    .doOnNext(username -> {
                                        MDC.put(USERNAME_MDC_KEY, username);
                                        System.out.println("DEBUG: Username resolved: " + username);
                                    })
                                    .then(Mono.just(principal));
                        } catch (IllegalArgumentException e) {
                            // Not a UUID, use principal name as username
                            MDC.put(USERNAME_MDC_KEY, principalName);
                            return Mono.just(principal);
                        }
                    }
                    return Mono.just(principal);
                })
                .then(chain.filter(exchange))
                .doFinally(signalType -> {
                    // Clean up MDC after request
                    MDC.remove(USER_ID_MDC_KEY);
                    MDC.remove(USERNAME_MDC_KEY);
                });
    }
}
