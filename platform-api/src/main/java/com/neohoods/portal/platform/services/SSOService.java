package com.neohoods.portal.platform.services;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class SSOService {

    @Value("${neohoods.portal.frontend-url}")
    private String frontendUrl;
    
    @Value("${neohoods.portal.sso.enabled:false}")
    private boolean ssoEnabled;
    
    @Value("${neohoods.portal.sso.client-id:}")
    private String ssoClientId;
    
    @Value("${neohoods.portal.sso.client-secret:}")
    private String ssoClientSecret;
    
    @Value("${neohoods.portal.sso.token-endpoint:}")
    private String ssoTokenEndpoint;
    
    @Value("${neohoods.portal.sso.authorization-endpoint:}")
    private String ssoAuthorizationEndpoint;
    
    @Value("${neohoods.portal.sso.scope:openid profile email}")
    private String ssoScope;
    
    private final ServerSecurityContextRepository serverSecurityContextRepository;
    private final UsersRepository usersRepository;
    private final Auth0Service auth0Service;
    
    public SSOService(ServerSecurityContextRepository serverSecurityContextRepository,
                      UsersRepository usersRepository,
                      Auth0Service auth0Service) {
        this.serverSecurityContextRepository = serverSecurityContextRepository;
        this.usersRepository = usersRepository;
        this.auth0Service = auth0Service;
    }

    public URI generateSSOLoginUrl() {
        State state = new State();
        Nonce nonce = new Nonce();
        AuthenticationRequest request = null;
        try {
            // Split the SSO scope string by space and create Scope object
            String[] scopeArray = ssoScope.split("\\s+");
            Scope scope = new Scope(scopeArray);

            request = new AuthenticationRequest.Builder(
                    new ResponseType("code"),
                    scope,
                    new ClientID(ssoClientId),
                    new URI(frontendUrl + "/token-exchange"))
                    .endpointURI(new URI(ssoAuthorizationEndpoint))
                    .state(state)
                    .nonce(nonce)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return request.toURI();
    }

    public Mono<Boolean> tokenExchange(ServerWebExchange exchange, String state, String authorizationCode) {
        return Mono.fromCallable(() -> {
            // TODO state and PKCE
            AuthorizationCode code = new AuthorizationCode(authorizationCode);

            URI callback;
            URI tokenEndpoint;
            try {
                callback = new URI(frontendUrl + "/sso/callback");
                tokenEndpoint = new URI(ssoTokenEndpoint);
            } catch (URISyntaxException e) {
                log.error("Invalid URI configuration", e);
                throw new CodedErrorException(CodedError.INTERNAL_ERROR,
                        Map.of("error", "Invalid URI configuration", "details", e.getMessage()));
            }

            AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, callback);

            ClientID clientID = new ClientID(ssoClientId);
            Secret clientSecret = new Secret(ssoClientSecret);
            ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);

            TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant, null);

            TokenResponse tokenResponse = null;
            try {
                tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());
            } catch (ParseException e) {
                log.error("Failed to parse token response", e);
                throw new CodedErrorException(CodedError.EXTERNAL_SERVICE_ERROR,
                        Map.of("service", "SSO Token Exchange", "error", e.getMessage()));
            }

            if (!tokenResponse.indicatesSuccess()) {
                return false;
            }

            OIDCTokenResponse successResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
            JWT idToken = successResponse.getOIDCTokens().getIDToken();

            try {
                String email = idToken.getJWTClaimsSet().getStringClaim("email");
                log.info("Looking up user by email: {}", email);

                UserEntity user = usersRepository.findByEmail(email);

                if (user == null) {
                    // User not found, create a new user or handle appropriately
                    log.error("User not found for email: {}", email);
                    throw new CodedErrorException(CodedError.USER_NOT_FOUND_SSO, Map.of("email", email));
                }

                log.info("Found user: {} with roles: {}", user.getUsername(), user.getRoles());

                // Perform account linking if email is verified
                Boolean emailVerified = (Boolean) idToken.getJWTClaimsSet().getClaim("email_verified");
                if (Boolean.TRUE.equals(emailVerified)) {
                    String currentUserId = idToken.getJWTClaimsSet().getStringClaim("sub");
                    // Get the connection/provider from the JWT claims
                    String provider = idToken.getJWTClaimsSet().getStringClaim("connection");
                    if (provider == null) {
                        // Fallback to 'auth0' if connection is not available
                        provider = "auth0";
                    }
                    log.debug("Using provider '{}' for account linking of user: {}", provider, email);

                    // Perform account linking asynchronously (don't block the login process)
                    auth0Service.performAccountLinking(currentUserId, email, provider)
                            .subscribe(
                                    result -> log.info("Account linking completed for user: {}", email),
                                    error -> log.warn("Account linking failed for user: {}, error: {}", email,
                                            error.getMessage()));
                } else {
                    log.debug("Email not verified for user: {}, skipping account linking", email);
                }

                // Convert user roles to authorities
                Collection<GrantedAuthority> authorities = user.getRoles() != null
                        ? user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                                .collect(Collectors.toList())
                        : java.util.Collections.emptyList();

                log.info("Created authorities: {}", authorities);

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(
                        user.getId().toString(),
                        null,
                        authorities));

                log.info("Created security context for user: {} with authorities: {}", user.getId(), authorities);

                // Return the context for saving
                return new SecurityContext[] { context };

            } catch (java.text.ParseException e) {
                log.error("Failed to parse JWT claims", e);
                throw new CodedErrorException(CodedError.EXTERNAL_SERVICE_ERROR,
                        Map.of("service", "JWT Parsing", "error", e.getMessage()));
            }
        }).flatMap(contextArray -> {
            if (contextArray == null) {
                log.warn("Context array is null, returning false");
                return Mono.just(false);
            }

            SecurityContext[] contexts = (SecurityContext[]) contextArray;
            SecurityContext context = contexts[0];

            log.info("Saving security context to session");
            return serverSecurityContextRepository.save(exchange, context)
                    .doOnSuccess(v -> log.info("Security context saved successfully"))
                    .doOnError(e -> log.error("Failed to save security context", e))
                    .then(Mono.just(true));
        });
    }
}
