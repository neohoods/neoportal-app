package com.neohoods.portal.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Order(-100) // Execute before other security filters
@Slf4j
public class BotJwtAuthenticationFilter implements WebFilter {

    @Value("${neohoods.portal.bot.jwt-secret}")
    private String jwtSecret;

    @Value("${neohoods.portal.bot.jwt-issuer:matrix-sync-bot}")
    private String jwtIssuer;

    @Value("${neohoods.portal.bot.jwt-audience:neohoods-portal}")
    private String jwtAudience;

    private final MACVerifier verifier;

    public BotJwtAuthenticationFilter(@Value("${neohoods.portal.bot.jwt-secret}") String jwtSecret)
            throws NoSuchAlgorithmException, JOSEException {
        // Derive a 256-bit (32-byte) key from the secret using SHA-256
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(secretBytes);
        this.verifier = new MACVerifier(keyBytes);
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Only process /api/bot/** paths
        if (!path.startsWith("/api/bot/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for bot endpoint: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        try {
            // Parse and validate JWT
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                log.warn("JWT signature verification failed for bot endpoint: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Check expiration
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                log.warn("Expired JWT token for bot endpoint: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Validate issuer
            String tokenIssuer = claims.getIssuer();
            if (tokenIssuer == null || !jwtIssuer.equals(tokenIssuer)) {
                log.warn("Invalid JWT issuer: expected {}, got {}", jwtIssuer, tokenIssuer);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Validate audience
            List<String> tokenAudiences = claims.getAudience();
            if (tokenAudiences == null || tokenAudiences.isEmpty() || !tokenAudiences.contains(jwtAudience)) {
                log.warn("Invalid JWT audience: expected {}, got {}", jwtAudience, tokenAudiences);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Validate subject
            String subject = claims.getSubject();
            if (subject == null || !"sync-bot".equals(subject)) {
                log.warn("Invalid JWT subject: expected sync-bot, got {}", subject);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Create authentication with ROLE_BOT
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_BOT"));
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    "sync-bot",
                    null,
                    authorities);

            // Set authentication in security context
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (java.text.ParseException e) {
            log.warn("Failed to parse JWT token for bot endpoint: {} - {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } catch (JOSEException e) {
            log.warn("JWT verification failed for bot endpoint: {} - {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } catch (Exception e) {
            log.error("Error processing JWT token for bot endpoint: {}", path, e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }
}

