package com.neohoods.portal.platform.services.matrix.oauth2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.neohoods.portal.platform.services.MailService;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationRequest;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationResponse;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationSuccessResponse;
import com.nimbusds.oauth2.sdk.device.DeviceAuthorizationErrorResponse;
import com.nimbusds.oauth2.sdk.device.DeviceCode;
import com.nimbusds.oauth2.sdk.device.DeviceCodeGrant;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;

import com.nimbusds.oauth2.sdk.auth.ClientSecretPost;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.MatrixBotErrorNotificationEntity;
import com.neohoods.portal.platform.entities.MatrixBotTokenEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.MatrixBotErrorNotificationRepository;
import com.neohoods.portal.platform.repositories.MatrixBotTokenRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;

import com.neohoods.portal.platform.matrix.ApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatrixOAuth2Service {

    private final MatrixBotTokenRepository tokenRepository;
    private final MatrixBotErrorNotificationRepository errorNotificationRepository;
    private final UsersRepository usersRepository;
    private final MailService mailService;

    @Value("${neohoods.portal.matrix.oauth2.client-id}")
    private String clientId;

    @Value("${neohoods.portal.matrix.oauth2.client-secret}")
    private String clientSecret;

    @Value("${neohoods.portal.matrix.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${neohoods.portal.matrix.oauth2.authorization-endpoint}")
    private String authorizationEndpoint;

    @Value("${neohoods.portal.matrix.oauth2.token-endpoint}")
    private String tokenEndpoint;

    @Value("${neohoods.portal.matrix.oauth2.scope}")
    private String scope;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.base-url}")
    private String baseUrl;

    @Value("${neohoods.portal.matrix.oauth2.device-authorization-endpoint}")
    private String deviceAuthorizationEndpoint;

    @PostConstruct
    public void init() {
        // Auto-deduce redirect-uri if not provided
        if (redirectUri == null || redirectUri.isEmpty()) {
            redirectUri = baseUrl + "/api/admin/matrix-bot/oauth2/callback";
            log.info("Auto-deduced Matrix OAuth2 redirect-uri: {}", redirectUri);
        }

        // Auto-deduce authorization-endpoint if not provided
        if (authorizationEndpoint == null || authorizationEndpoint.isEmpty()) {
            String normalizedHomeserverUrl = normalizeHomeserverUrl(homeserverUrl);
            authorizationEndpoint = normalizedHomeserverUrl + "/_matrix/client/v3/oauth2/authorize";
            log.info("Auto-deduced Matrix OAuth2 authorization-endpoint: {}", authorizationEndpoint);
        }

        // Auto-deduce token-endpoint if not provided
        if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
            String normalizedHomeserverUrl = normalizeHomeserverUrl(homeserverUrl);
            tokenEndpoint = normalizedHomeserverUrl + "/_matrix/client/v3/oauth2/token";
            log.info("Auto-deduced Matrix OAuth2 token-endpoint: {}", tokenEndpoint);
        }

        // Auto-deduce device-authorization-endpoint if not provided
        if (deviceAuthorizationEndpoint == null || deviceAuthorizationEndpoint.isEmpty()) {
            deviceAuthorizationEndpoint = "https://mas.chat.neohoods.com/oauth2/device";
            log.info("Auto-deduced Matrix OAuth2 device-authorization-endpoint: {}", deviceAuthorizationEndpoint);
        }

        // Schedule cleanup of expired PKCE entries every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredPkce, 5, 5, TimeUnit.MINUTES);
        // Schedule cleanup of expired device codes every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredDeviceCodes, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Normalize homeserver URL by adding https:// if missing
     */
    private String normalizeHomeserverUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://matrix.neohoods.com"; // default
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://" + url;
    }

    /**
     * Cleanup expired PKCE entries (older than 10 minutes)
     */
    private void cleanupExpiredPkce() {
        OffsetDateTime expireTime = OffsetDateTime.now().minusMinutes(10);
        int sizeBefore = pkceStorage.size();

        pkceStorage.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().createdAt.isBefore(expireTime);
            if (expired) {
                log.debug("Removing expired PKCE entry for state: {}", entry.getKey());
            }
            return expired;
        });

        int removed = sizeBefore - pkceStorage.size();
        if (removed > 0) {
            log.debug("Cleaned up {} expired PKCE entries ({} remaining)", removed, pkceStorage.size());
        }
    }

    /**
     * Cleanup expired device code entries
     */
    private void cleanupExpiredDeviceCodes() {
        int sizeBefore = deviceCodeStorage.size();

        deviceCodeStorage.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                log.debug("Removing expired device code entry: {}", entry.getKey());
            }
            return expired;
        });

        int removed = sizeBefore - deviceCodeStorage.size();
        if (removed > 0) {
            log.debug("Cleaned up {} expired device code entries ({} remaining)", removed, deviceCodeStorage.size());
        }
    }

    // Store PKCE code verifiers by state with timestamp (expires after 10 minutes)
    private static class PkceEntry {
        final CodeVerifier codeVerifier;
        final State state;
        final OffsetDateTime createdAt;

        PkceEntry(CodeVerifier codeVerifier, State state) {
            this.codeVerifier = codeVerifier;
            this.state = state;
            this.createdAt = OffsetDateTime.now();
        }
    }

    private final Map<String, PkceEntry> pkceStorage = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "matrix-oauth2-pkce-cleanup");
        t.setDaemon(true);
        return t;
    });

    // Store device codes with timestamp (expires after 10 minutes)
    public static class DeviceCodeEntry {
        public final String deviceCode;
        public final OffsetDateTime createdAt;
        public final long expiresIn;
        public final long interval;

        DeviceCodeEntry(String deviceCode, long expiresIn, long interval) {
            this.deviceCode = deviceCode;
            this.createdAt = OffsetDateTime.now();
            this.expiresIn = expiresIn;
            this.interval = interval;
        }

        public boolean isExpired() {
            return createdAt.plusSeconds(expiresIn).isBefore(OffsetDateTime.now());
        }
    }

    private final Map<String, DeviceCodeEntry> deviceCodeStorage = new ConcurrentHashMap<>();

    /**
     * Exception thrown when slow_down is received from token endpoint
     */
    public static class SlowDownException extends RuntimeException {
        public SlowDownException(String message) {
            super(message);
        }
    }

    /**
     * Get device code entry from storage (for retrieving interval and expiration)
     */
    public DeviceCodeEntry getDeviceCodeEntry(String deviceCode) {
        return deviceCodeStorage.get(deviceCode);
    }

    // Store admin access token with expiration
    private static class AdminTokenEntry {
        final String accessToken;
        final OffsetDateTime expiresAt;

        AdminTokenEntry(String accessToken, long expiresIn) {
            this.accessToken = accessToken;
            this.expiresAt = OffsetDateTime.now().plusSeconds(expiresIn);
        }

        boolean isExpired() {
            return expiresAt.isBefore(OffsetDateTime.now());
        }
    }

    private volatile AdminTokenEntry adminTokenEntry = null;

    /**
     * Generate OAuth2 redirect URI with PKCE using Nimbus OAuth SDK
     * Uses scopes 'urn:synapse:admin:*' and 'urn:matrix:client:api:*' for admin
     * operations
     */
    public String generateRedirectUri(String stateString) {
        try {
            // Generate new random state
            State state = new State(stateString);

            // Generate PKCE code verifier
            CodeVerifier codeVerifier = new CodeVerifier();

            // Store code verifier with state for later use in token exchange
            pkceStorage.put(state.getValue(), new PkceEntry(codeVerifier, state));
            log.debug("Stored PKCE code verifier for state: {} (length: {})", state.getValue(),
                    codeVerifier.getValue().length());

            // Use admin scopes for authorization code flow
            // According to Synapse docs: urn:synapse:admin:* requires
            // urn:matrix:client:api:* as well
            String[] adminScopeArray = {
                    "urn:synapse:admin:*",
                    "urn:matrix:client:api:*"
            };
            Scope adminScope = new Scope(adminScopeArray);

            // Build authorization request using Nimbus SDK
            AuthorizationRequest request = new AuthorizationRequest.Builder(
                    new ResponseType("code"),
                    new ClientID(clientId))
                    .endpointURI(new URI(authorizationEndpoint))
                    .redirectionURI(new URI(redirectUri))
                    .scope(adminScope)
                    .state(state)
                    .codeChallenge(codeVerifier, CodeChallengeMethod.S256)
                    .build();

            URI authUri = request.toURI();
            log.debug("Generated authorization URI: {}", authUri);
            return authUri.toString();
        } catch (URISyntaxException e) {
            log.error("Failed to generate redirect URI", e);
            throw new RuntimeException("Failed to generate redirect URI", e);
        }
    }

    /**
     * Exchange authorization code for tokens using stored PKCE code verifier and
     * Nimbus SDK
     */
    public void exchangeCodeForTokens(String codeString, String stateString) {
        try {
            // Retrieve stored code verifier
            PkceEntry entry = pkceStorage.remove(stateString);
            if (entry == null) {
                log.error("No PKCE code verifier found for state: {}", stateString);
                handleOAuth2Error("No PKCE code verifier found for state: " + stateString);
                throw new RuntimeException("No PKCE code verifier found for state: " + stateString);
            }

            // Check if entry is expired (shouldn't happen due to cleanup, but double-check)
            if (entry.createdAt.isBefore(OffsetDateTime.now().minusMinutes(10))) {
                log.warn("PKCE entry expired for state: {} (created at: {})", stateString, entry.createdAt);
                handleOAuth2Error("PKCE entry expired for state: " + stateString);
                throw new RuntimeException("PKCE entry expired for state: " + stateString);
            }

            CodeVerifier codeVerifier = entry.codeVerifier;
            log.debug("Retrieved PKCE code verifier for state: {} (length: {})", stateString,
                    codeVerifier.getValue().length());

            // Create authorization code and grant with PKCE
            AuthorizationCode code = new AuthorizationCode(codeString);
            URI redirectURI = new URI(redirectUri);
            AuthorizationCodeGrant codeGrant = new AuthorizationCodeGrant(code, redirectURI, codeVerifier);

            // Create client authentication
            ClientID clientID = new ClientID(clientId);
            Secret clientSecret = new Secret(this.clientSecret);
            ClientAuthentication clientAuth = new ClientSecretPost(clientID, clientSecret);

            // Build token request using Nimbus SDK
            TokenRequest tokenRequest = new TokenRequest(
                    new URI(tokenEndpoint),
                    clientAuth,
                    codeGrant,
                    null // no additional parameters
            );

            // Send token request
            TokenResponse tokenResponse = TokenResponse.parse(tokenRequest.toHTTPRequest().send());

            if (tokenResponse.indicatesSuccess()) {
                AccessTokenResponse successResponse = tokenResponse.toSuccessResponse();
                AccessToken accessToken = successResponse.getTokens().getAccessToken();
                RefreshToken refreshToken = successResponse.getTokens().getRefreshToken();

                // Get expiration time if available
                Integer expiresIn = null;
                if (accessToken.getLifetime() > 0) {
                    expiresIn = (int) accessToken.getLifetime();
                }

                saveTokens(accessToken.getValue(), refreshToken != null ? refreshToken.getValue() : null, expiresIn);
                log.info("Successfully exchanged code for tokens");
            } else {
                com.nimbusds.oauth2.sdk.ErrorObject error = tokenResponse.toErrorResponse().getErrorObject();
                log.error("Failed to exchange code for tokens: {} - {}", error.getCode(), error.getDescription());
                handleOAuth2Error(
                        "Failed to exchange code for tokens: " + error.getCode() + " - " + error.getDescription());
                throw new RuntimeException(
                        "Failed to exchange code for tokens: " + error.getCode() + " - " + error.getDescription());
            }
        } catch (URISyntaxException | ParseException e) {
            log.error("Error exchanging code for tokens", e);
            handleOAuth2Error("Error exchanging code for tokens: " + e.getMessage());
            throw new RuntimeException("Failed to exchange code for tokens", e);
        } catch (IOException e) {
            log.error("IO error exchanging code for tokens", e);
            handleOAuth2Error("IO error exchanging code for tokens: " + e.getMessage());
            throw new RuntimeException("Failed to exchange code for tokens", e);
        }
    }

    /**
     * Refresh access token using refresh token with Nimbus OAuth SDK
     */
    public Optional<String> refreshAccessToken() {
        Optional<MatrixBotTokenEntity> tokenOpt = tokenRepository.findFirstByOrderByCreatedAtDesc();
        if (tokenOpt.isEmpty() || tokenOpt.get().getRefreshToken() == null) {
            log.warn("No refresh token available");
            return Optional.empty();
        }

        MatrixBotTokenEntity tokenEntity = tokenOpt.get();
        try {
            // Construct the grant from the saved refresh token
            RefreshToken refreshToken = new RefreshToken(tokenEntity.getRefreshToken());
            AuthorizationGrant refreshTokenGrant = new RefreshTokenGrant(refreshToken);

            // The credentials to authenticate the client at the token endpoint
            ClientID clientID = new ClientID(clientId);
            Secret clientSecret = new Secret(this.clientSecret);
            ClientAuthentication clientAuth = new ClientSecretPost(clientID, clientSecret);

            // The token endpoint
            URI tokenEndpointURI = new URI(tokenEndpoint);

            // Make the token request
            TokenRequest request = new TokenRequest(tokenEndpointURI, clientAuth, refreshTokenGrant);

            // Send token request
            TokenResponse response = TokenResponse.parse(request.toHTTPRequest().send());

            if (!response.indicatesSuccess()) {
                // We got an error response...
                com.nimbusds.oauth2.sdk.TokenErrorResponse errorResponse = response.toErrorResponse();
                com.nimbusds.oauth2.sdk.ErrorObject error = errorResponse.getErrorObject();
                log.error("Failed to refresh token: {} - {}", error.getCode(), error.getDescription());
                handleOAuth2Error("Failed to refresh token: " + error.getCode() + " - " + error.getDescription());
                return Optional.empty();
            }

            // Get the access token, the refresh token may be updated
            AccessTokenResponse successResponse = response.toSuccessResponse();
            AccessToken accessToken = successResponse.getTokens().getAccessToken();
            RefreshToken newRefreshToken = successResponse.getTokens().getRefreshToken();

            // Get expiration time if available
            Integer expiresIn = null;
            if (accessToken.getLifetime() > 0) {
                expiresIn = (int) accessToken.getLifetime();
            }

            // Use new refresh token if provided and not empty, otherwise keep the old one
            String refreshTokenValue;
            if (newRefreshToken != null && newRefreshToken.getValue() != null
                    && !newRefreshToken.getValue().isEmpty()) {
                refreshTokenValue = newRefreshToken.getValue();
                log.debug("New refresh token received, replacing old one in database");
            } else {
                refreshTokenValue = tokenEntity.getRefreshToken();
                log.debug("No new refresh token provided, keeping existing one");
            }

            saveTokens(accessToken.getValue(), refreshTokenValue, expiresIn);
            log.info("Successfully refreshed access token");
            return Optional.of(accessToken.getValue());
        } catch (URISyntaxException | ParseException e) {
            log.error("Error refreshing access token", e);
            handleOAuth2Error("Error refreshing access token: " + e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            log.error("IO error refreshing access token", e);
            handleOAuth2Error("IO error refreshing access token: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get current access token, refreshing if needed
     * If local bot is enabled, returns the permanent token for the local bot user
     */
    public Optional<String> getUserAccessToken() {
        // Check if local bot is enabled and has a permanent token
        // This is handled by MatrixAssistantService which calls this method
        // The local bot token check should be done in
        // MatrixAssistantService.getMatrixApiClientWithUserToken()

        Optional<MatrixBotTokenEntity> tokenOpt = tokenRepository.findFirstByOrderByCreatedAtDesc();
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        MatrixBotTokenEntity tokenEntity = tokenOpt.get();
        if (tokenEntity.getAccessToken() != null &&
                (tokenEntity.getExpiresAt() == null || tokenEntity.getExpiresAt().isAfter(OffsetDateTime.now()))) {
            return Optional.of(tokenEntity.getAccessToken());
        }

        // Token expired or missing, try to refresh
        return refreshAccessToken();
    }

    /**
     * Check if refresh token exists
     */
    public boolean hasRefreshToken() {
        return tokenRepository.findFirstByOrderByCreatedAtDesc()
                .map(token -> token.getRefreshToken() != null)
                .orElse(false);
    }

    /**
     * Check if access token exists and is valid
     */
    public boolean hasAccessToken() {
        Optional<MatrixBotTokenEntity> tokenOpt = tokenRepository.findFirstByOrderByCreatedAtDesc();
        if (tokenOpt.isEmpty()) {
            return false;
        }
        MatrixBotTokenEntity tokenEntity = tokenOpt.get();
        return tokenEntity.getAccessToken() != null &&
                (tokenEntity.getExpiresAt() == null || tokenEntity.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    /**
     * Save tokens to database
     */
    private void saveTokens(String accessToken, String refreshToken, Integer expiresIn) {
        Optional<MatrixBotTokenEntity> existingOpt = tokenRepository.findFirstByOrderByCreatedAtDesc();
        MatrixBotTokenEntity tokenEntity;

        if (existingOpt.isPresent()) {
            tokenEntity = existingOpt.get();
            tokenEntity.setAccessToken(accessToken);
            tokenEntity.setRefreshToken(refreshToken);
            if (expiresIn != null) {
                tokenEntity.setExpiresAt(OffsetDateTime.now().plusSeconds(expiresIn));
            }
        } else {
            tokenEntity = MatrixBotTokenEntity.builder()
                    .id(UUID.randomUUID())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresAt(expiresIn != null ? OffsetDateTime.now().plusSeconds(expiresIn) : null)
                    .build();
        }

        tokenRepository.save(tokenEntity);
    }

    /**
     * Initiate Device Code Grant Flow using Nimbus OAuth SDK
     * Returns device code information for user to authorize
     * Uses scopes 'urn:synapse:admin:*' and 'urn:matrix:client:api:*' for admin
     * operations
     */
    public DeviceCodeInfo initiateDeviceCodeFlow() {
        try {
            // Build device authorization endpoint URI
            URI deviceAuthEndpoint = new URI(deviceAuthorizationEndpoint);

            // Client authentication (optional for device flow, but some providers require
            // it)
            ClientID clientID = new ClientID(clientId);
            Secret clientSecret = new Secret(this.clientSecret);
            ClientAuthentication clientAuth = new ClientSecretPost(clientID, clientSecret);

            // Use admin scopes for device code flow
            // According to Synapse docs: urn:synapse:admin:* requires
            // urn:matrix:client:api:* as well
            String[] adminScopeArray = {
                    "urn:synapse:admin:*",
                    "urn:matrix:client:api:*"
            };
            Scope requestScope = new Scope(adminScopeArray);

            // Create device authorization request using Nimbus SDK
            DeviceAuthorizationRequest deviceAuthRequest = new DeviceAuthorizationRequest.Builder(clientAuth)
                    .endpointURI(deviceAuthEndpoint).scope(requestScope).build();

            // Send request
            HTTPResponse httpResponse = deviceAuthRequest.toHTTPRequest().send();
            DeviceAuthorizationResponse deviceAuthResponse = DeviceAuthorizationResponse.parse(httpResponse);

            if (deviceAuthResponse instanceof DeviceAuthorizationSuccessResponse) {
                DeviceAuthorizationSuccessResponse successResponse = (DeviceAuthorizationSuccessResponse) deviceAuthResponse;

                // Extract device code information
                String deviceCode = successResponse.getDeviceCode().getValue();
                String userCode = successResponse.getUserCode().getValue();
                URI verificationURI = successResponse.getVerificationURI();
                URI verificationURIComplete = successResponse.getVerificationURIComplete();
                long expiresIn = successResponse.getLifetime() > 0 ? successResponse.getLifetime() : 900; // Default 15
                                                                                                          // minutes
                long interval = successResponse.getInterval() > 0 ? successResponse.getInterval() : 5; // Default 5
                                                                                                       // seconds

                // Store device code for polling
                deviceCodeStorage.put(deviceCode, new DeviceCodeEntry(deviceCode, expiresIn, interval));

                log.info("Device code flow initiated, device code: {}, expires in: {}s, interval: {}s", deviceCode,
                        expiresIn, interval);

                return new DeviceCodeInfo(
                        deviceCode,
                        userCode,
                        verificationURI != null ? verificationURI.toString() : null,
                        verificationURIComplete != null ? verificationURIComplete.toString() : null,
                        expiresIn,
                        interval);
            } else {
                DeviceAuthorizationErrorResponse errorResponse = (DeviceAuthorizationErrorResponse) deviceAuthResponse;
                com.nimbusds.oauth2.sdk.ErrorObject error = errorResponse.getErrorObject();
                log.error("Failed to initiate device code flow: {} - {}", error.getCode(), error.getDescription());
                throw new RuntimeException(
                        "Failed to initiate device code flow: " + error.getCode() + " - " + error.getDescription());
            }
        } catch (URISyntaxException | ParseException e) {
            log.error("Error initiating device code flow", e);
            throw new RuntimeException("Failed to initiate device code flow", e);
        } catch (IOException e) {
            log.error("IO error initiating device code flow", e);
            throw new RuntimeException("Failed to initiate device code flow", e);
        }
    }

    /**
     * Poll for device code token exchange with automatic retry
     * This method polls the token endpoint until authorization is complete or
     * timeout
     * Returns Optional with access token if authorized, empty if timeout or error
     */
    public Optional<String> pollDeviceCodeTokenWithRetry(String deviceCodeString, long intervalSeconds,
            long timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long currentInterval = intervalSeconds;
        int consecutiveSlowDowns = 0;

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            try {
                Optional<String> result = pollDeviceCodeToken(deviceCodeString);
                if (result.isPresent()) {
                    return result; // Success!
                }
                // Still pending, wait before next poll
                // If we got slow_down, increase interval progressively
                if (consecutiveSlowDowns > 0) {
                    currentInterval = Math.min(currentInterval * 2, 60); // Max 60 seconds
                    log.debug("Increased polling interval to {}s due to slow_down", currentInterval);
                }
                consecutiveSlowDowns = 0; // Reset on successful poll (even if pending)

                try {
                    Thread.sleep(currentInterval * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Polling interrupted for device code: {}", deviceCodeString);
                    return Optional.empty();
                }
            } catch (SlowDownException e) {
                // Slow down requested, increase interval
                consecutiveSlowDowns++;
                currentInterval = Math.min(currentInterval * 2, 60); // Max 60 seconds
                log.debug("Received slow_down, increasing interval to {}s", currentInterval);
                try {
                    Thread.sleep(currentInterval * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            } catch (RuntimeException e) {
                // Check if it's an expired token error
                if (e.getMessage() != null && e.getMessage().contains("expired")) {
                    log.error("Device code expired during polling: {}", deviceCodeString);
                    throw e;
                }
                // Other errors, retry after interval
                log.debug("Error during polling, will retry: {}", e.getMessage());
                try {
                    Thread.sleep(currentInterval * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }

        log.warn("Polling timeout for device code: {} (timeout: {}s)", deviceCodeString, timeoutSeconds);
        return Optional.empty();
    }

    /**
     * Poll for device code token exchange (single attempt) using Nimbus OAuth SDK
     * Returns Optional with access token if authorized, empty if still pending
     */
    public Optional<String> pollDeviceCodeToken(String deviceCodeString) {
        try {
            DeviceCodeEntry entry = deviceCodeStorage.get(deviceCodeString);
            if (entry == null) {
                log.error("No device code entry found for: {}", deviceCodeString);
                throw new RuntimeException("Device code not found or expired");
            }

            if (entry.isExpired()) {
                log.warn("Device code expired: {}", deviceCodeString);
                deviceCodeStorage.remove(deviceCodeString);
                throw new RuntimeException("Device code expired");
            }

            // Create device code grant using Nimbus SDK
            DeviceCode deviceCode = new DeviceCode(deviceCodeString);
            DeviceCodeGrant deviceCodeGrant = new DeviceCodeGrant(deviceCode);

            // Create client authentication
            ClientID clientID = new ClientID(clientId);
            Secret clientSecret = new Secret(getClientSecret());
            ClientAuthentication clientAuth = new ClientSecretPost(clientID, clientSecret);

            // Build token request using Nimbus SDK
            TokenRequest tokenRequest = new TokenRequest(
                    new URI(tokenEndpoint),
                    clientAuth,
                    deviceCodeGrant,
                    null // no additional parameters
            );

            // Send token request
            TokenResponse tokenResponse = TokenResponse.parse(tokenRequest.toHTTPRequest().send());

            if (tokenResponse.indicatesSuccess()) {
                AccessTokenResponse successResponse = tokenResponse.toSuccessResponse();
                AccessToken accessToken = successResponse.getTokens().getAccessToken();
                RefreshToken refreshToken = successResponse.getTokens().getRefreshToken();

                // Get expiration time if available
                Integer expiresIn = null;
                if (accessToken.getLifetime() > 0) {
                    expiresIn = (int) accessToken.getLifetime();
                }

                // Remove device code from storage
                deviceCodeStorage.remove(deviceCodeString);

                // Save tokens
                saveTokens(accessToken.getValue(), refreshToken != null ? refreshToken.getValue() : null, expiresIn);
                log.info("Successfully exchanged device code for tokens");
                return Optional.of(accessToken.getValue());
            } else {
                // Handle error response
                com.nimbusds.oauth2.sdk.TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
                com.nimbusds.oauth2.sdk.ErrorObject error = errorResponse.getErrorObject();
                String errorCode = error.getCode();

                if ("authorization_pending".equals(errorCode)) {
                    log.debug("Authorization still pending for device code: {}", deviceCodeString);
                    return Optional.empty(); // Still pending
                } else if ("slow_down".equals(errorCode)) {
                    log.debug("Slow down requested for device code: {}", deviceCodeString);
                    throw new SlowDownException("Slow down requested for device code: " + deviceCodeString);
                } else if ("expired_token".equals(errorCode)) {
                    log.warn("Device code expired: {}", deviceCodeString);
                    deviceCodeStorage.remove(deviceCodeString);
                    throw new RuntimeException("Device code expired");
                } else {
                    log.error("Failed to exchange device code for tokens: {} - {}", error.getCode(),
                            error.getDescription());
                    throw new RuntimeException("Failed to exchange device code for tokens: " + error.getCode() + " - "
                            + error.getDescription());
                }
            }
        } catch (URISyntaxException | ParseException e) {
            log.error("Error polling device code token", e);
            throw new RuntimeException("Failed to poll device code token", e);
        } catch (IOException e) {
            log.error("IO error polling device code token", e);
            throw new RuntimeException("Failed to poll device code token", e);
        } catch (RuntimeException e) {
            // Re-throw runtime exceptions (including SlowDownException)
            throw e;
        }
    }

    /**
     * Get admin access token using Client Credentials Grant Flow
     * Uses scope 'urn:mas:admin' for admin operations via client credentials flow
     * 
     * NOTE: For client credentials flow, only 'urn:mas:admin' is needed.
     * For device code and authorization code flows, use 'urn:synapse:admin:*' and
     * 'urn:matrix:client:api:*' scopes.
     */
    public Optional<String> getAdminAccessToken() {
        // Check if we have a cached valid token
        if (adminTokenEntry != null && !adminTokenEntry.isExpired()) {
            log.debug("Using cached admin access token");
            return Optional.of(adminTokenEntry.accessToken);
        }

        try {
            ClientID clientID = new ClientID(clientId);
            Secret clientSecret = new Secret(this.clientSecret);
            ClientAuthentication clientAuth = new ClientSecretPost(clientID, clientSecret);
            URI tokenEndpointURI = new URI(tokenEndpoint);

            // Use admin scope for client credentials flow
            Scope adminScope = new Scope("urn:mas:admin");
            ClientCredentialsGrant clientCredentialsGrant = new ClientCredentialsGrant();

            // Build token request
            TokenRequest tokenRequest = new TokenRequest(tokenEndpointURI, clientAuth, clientCredentialsGrant,
                    adminScope);

            // Send token request
            TokenResponse tokenResponse = TokenResponse.parse(tokenRequest.toHTTPRequest().send());

            if (tokenResponse.indicatesSuccess()) {
                AccessTokenResponse successResponse = tokenResponse.toSuccessResponse();
                AccessToken accessToken = successResponse.getTokens().getAccessToken();
                long expiresIn = accessToken.getLifetime() > 0 ? accessToken.getLifetime() : 3600; // Default 1 hour

                // Cache the token
                adminTokenEntry = new AdminTokenEntry(accessToken.getValue(), expiresIn);
                log.info("Successfully obtained admin access token (expires in {}s)", expiresIn);
                return Optional.of(accessToken.getValue());
            } else {
                com.nimbusds.oauth2.sdk.TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
                com.nimbusds.oauth2.sdk.ErrorObject error = errorResponse.getErrorObject();
                log.error("Failed to get admin access token: {} - {}", error.getCode(), error.getDescription());
                return Optional.empty();
            }
        } catch (URISyntaxException | ParseException e) {
            log.error("Error getting admin access token", e);
            return Optional.empty();
        } catch (IOException e) {
            log.error("IO error getting admin access token", e);
            return Optional.empty();
        }
    }

    /**
     * Get Matrix API client configured with access token
     * Priority order:
     * 1. matrixAccessToken (from Kubernetes secret) - highest priority
     * 2. localAssistantPermanentToken (from MATRIX_LOCAL_BOT_PERMANENT_TOKEN)
     * 3. OAuth2 token (fallback, but may not work for sendMessage)
     * 
     * @param homeserverUrl Matrix homeserver URL
     * @param matrixAccessToken Optional Kubernetes secret token
     * @param localAssistantEnabled Whether local assistant is enabled
     * @param localAssistantPermanentToken Optional permanent token for local assistant
     * @param localAssistantUserId Local assistant user ID (for logging)
     * @param createPermanentTokenCallback Optional callback to create permanent token if needed
     * @return Optional Matrix API client configured with access token
     */
    public Optional<ApiClient> getMatrixApiClient(
            String homeserverUrl,
            String matrixAccessToken,
            boolean localAssistantEnabled,
            String localAssistantPermanentToken,
            String localAssistantUserId,
            Function<String, Optional<String>> createPermanentTokenCallback) {
        
        Optional<String> accessTokenOpt = Optional.empty();

        // Priority 1: Use matrix-access-token from Kubernetes secret
        if (matrixAccessToken != null && !matrixAccessToken.isEmpty()) {
            log.info("Using matrix-access-token from Kubernetes secret (token prefix: {})",
                    matrixAccessToken.substring(0, Math.min(10, matrixAccessToken.length())));
            accessTokenOpt = Optional.of(matrixAccessToken);
        }
        // Priority 2: Check if local bot is enabled and has a permanent token configured
        else if (localAssistantEnabled && localAssistantPermanentToken != null
                && !localAssistantPermanentToken.isEmpty()) {
            log.info("Using permanent token for local bot user: {} (token prefix: {})", localAssistantUserId,
                    localAssistantPermanentToken.substring(0, Math.min(10, localAssistantPermanentToken.length())));
            accessTokenOpt = Optional.of(localAssistantPermanentToken);
        } else if (localAssistantEnabled && createPermanentTokenCallback != null) {
            // Local bot enabled but no token configured - try to create one
            log.warn(
                    "Local bot enabled but no permanent token configured. Attempting to create one for: {}",
                    localAssistantUserId);
            log.warn(
                    "   NOTE: This will likely fail because we need a Synapse admin token (not MAS admin token) to create permanent tokens.");
            accessTokenOpt = createPermanentTokenCallback.apply(localAssistantUserId);
            if (accessTokenOpt.isPresent()) {
                log.info(
                        "Successfully created permanent token for local bot. Store it in MATRIX_LOCAL_BOT_PERMANENT_TOKEN for future use.");
            } else {
                log.error(
                        "Failed to create permanent token. Falling back to OAuth2 token (which will fail for sendMessage).");
            }
        }

        // Priority 3: Fallback to OAuth2 token if no other token is available
        // WARNING: OAuth2 tokens may not work for sending messages (they don't have access_token_id in Synapse)
        if (accessTokenOpt.isEmpty()) {
            log.error(
                    "No matrix-access-token or local bot token available, falling back to OAuth2 token. This WILL cause issues with sendMessage (500 error: AssertionError: Requester must have an access_token_id).");
            accessTokenOpt = getUserAccessToken();
            if (accessTokenOpt.isPresent()) {
                log.error(
                        "Using OAuth2 token (does NOT have access_token_id in Synapse). sendMessage will fail with 500 error.");
                log.error(
                        "SOLUTION: Configure MATRIX_ACCESS_TOKEN (from Kubernetes secret neohoods-chat-matrix.matrix-access-token)");
                log.error("   OR create a permanent token via Synapse Admin API:");
                log.error(
                        "   1. Get a Synapse admin token (from an admin user like @quentincastel86:chat.neohoods.com)");
                log.error("   2. POST https://matrix.neohoods.com/_synapse/admin/v1/users/{}/login",
                        localAssistantUserId != null ? localAssistantUserId : "@alfred:chat.neohoods.com");
                log.error(
                        "   3. Configure the returned token in MATRIX_LOCAL_BOT_PERMANENT_TOKEN or MATRIX_ACCESS_TOKEN");
            }
        }

        if (accessTokenOpt.isEmpty()) {
            log.warn("No access token available for Matrix API");
            return Optional.empty();
        }

        ApiClient apiClient = new ApiClient();
        apiClient.setHost(homeserverUrl);

        Optional<String> finalAccessTokenOpt = accessTokenOpt;
        apiClient.setRequestInterceptor(builder -> {
            builder.header("Authorization", "Bearer " + finalAccessTokenOpt.get());
        });

        return Optional.of(apiClient);
    }

    /**
     * Get Matrix API client configured with OAuth2 user access token
     * Uses the user OAuth2 token flow (device code or authorization code)
     * 
     * @param homeserverUrl Matrix homeserver URL
     * @return Optional Matrix API client configured with OAuth2 user access token
     */
    public Optional<ApiClient> getMatrixApiClientWithUserToken(String homeserverUrl) {
        Optional<String> accessTokenOpt = getUserAccessToken();
        if (accessTokenOpt.isEmpty()) {
            log.warn("No OAuth2 user access token available for Matrix API");
            return Optional.empty();
        }

        log.debug("Using OAuth2 user access token for Matrix API (token prefix: {})",
                accessTokenOpt.get().substring(0, Math.min(10, accessTokenOpt.get().length())));

        ApiClient apiClient = new ApiClient();
        apiClient.setHost(homeserverUrl);

        Optional<String> finalAccessTokenOpt = accessTokenOpt;
        apiClient.setRequestInterceptor(builder -> {
            builder.header("Authorization", "Bearer " + finalAccessTokenOpt.get());
        });

        return Optional.of(apiClient);
    }

    /**
     * Get MAS API client configured with admin access token (client credentials flow)
     * 
     * @param masUrl MAS API URL
     * @return Optional MAS API client configured with admin access token
     */
    public Optional<com.neohoods.portal.platform.mas.ApiClient> getMASApiClient(String masUrl) {
        Optional<String> adminTokenOpt = getAdminAccessToken();
        if (adminTokenOpt.isEmpty()) {
            log.warn("No admin access token available for MAS API");
            return Optional.empty();
        }

        com.neohoods.portal.platform.mas.ApiClient apiClient = new com.neohoods.portal.platform.mas.ApiClient();
        String normalizedMasUrl = masUrl;
        if (!normalizedMasUrl.startsWith("http://") && !normalizedMasUrl.startsWith("https://")) {
            normalizedMasUrl = "https://" + normalizedMasUrl;
        }
        apiClient.updateBaseUri(normalizedMasUrl);
        log.info("Configured MAS API client with base path: {}", normalizedMasUrl);

        apiClient.setRequestInterceptor(builder -> {
            builder.header("Authorization", "Bearer " + adminTokenOpt.get());
        });

        return Optional.of(apiClient);
    }

    /**
     * Device Code Info DTO
     */
    public static class DeviceCodeInfo {
        private final String deviceCode;
        private final String userCode;
        private final String verificationUri;
        private final String verificationUriComplete;
        private final long expiresIn;
        private final long interval;

        public DeviceCodeInfo(String deviceCode, String userCode, String verificationUri,
                String verificationUriComplete, long expiresIn, long interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.verificationUriComplete = verificationUriComplete;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }

        public String getDeviceCode() {
            return deviceCode;
        }

        public String getUserCode() {
            return userCode;
        }

        public String getVerificationUri() {
            return verificationUri;
        }

        public String getVerificationUriComplete() {
            return verificationUriComplete;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public long getInterval() {
            return interval;
        }
    }

    /**
     * Get client secret, handling URL decoding if needed
     */
    private String getClientSecret() {
        try {
            // Try to decode in case it's URL-encoded
            String decoded = URLDecoder.decode(clientSecret, StandardCharsets.UTF_8.name());
            // If decoding changed something, use decoded version
            if (!decoded.equals(clientSecret)) {
                log.debug("Client secret was URL-encoded, using decoded version");
                return decoded;
            }
            return clientSecret;
        } catch (Exception e) {
            // If decoding fails, return original
            return clientSecret;
        }
    }

    /**
     * Handle OAuth2 errors and send email to admin with device code flow (max once
     * per day)
     */
    private void handleOAuth2Error(String errorMessage) {
        LocalDate today = LocalDate.now();
        Optional<MatrixBotErrorNotificationEntity> notificationOpt = errorNotificationRepository
                .findByLastNotificationDate(today);

        if (notificationOpt.isPresent()) {
            log.debug("Error notification already sent today, skipping email");
            return;
        }

        // Initiate device code flow
        DeviceCodeInfo deviceCodeInfo;
        try {
            deviceCodeInfo = initiateDeviceCodeFlow();
            log.info("Device code flow initiated for OAuth2 error notification");
        } catch (Exception e) {
            log.error("Failed to initiate device code flow for OAuth2 error notification", e);
            // Fallback: send simple error email without device code
            sendSimpleErrorEmail(errorMessage, today);
            return;
        }

        // Send email to admins with device code
        try {
            List<UserEntity> admins = usersRepository.findByType(UserType.ADMIN);
            if (admins.isEmpty()) {
                log.warn("No admin users found to notify about Matrix OAuth2 error");
                return;
            }

            String subject = "matrixBot.oauth2.deviceCode.email.title";
            int expiresInMinutes = (int) (deviceCodeInfo.getExpiresIn() / 60);

            for (UserEntity admin : admins) {
                if (admin.getEmail() != null) {
                    List<MailService.TemplateVariable> variables = new ArrayList<>();
                    variables.add(MailService.TemplateVariable.builder()
                            .type(MailService.TemplateVariableType.RAW)
                            .ref("username")
                            .value(admin.getUsername())
                            .build());
                    variables.add(MailService.TemplateVariable.builder()
                            .type(MailService.TemplateVariableType.RAW)
                            .ref("userCode")
                            .value(deviceCodeInfo.getUserCode())
                            .build());
                    variables.add(MailService.TemplateVariable.builder()
                            .type(MailService.TemplateVariableType.RAW)
                            .ref("verificationUri")
                            .value(deviceCodeInfo.getVerificationUriComplete() != null
                                    ? deviceCodeInfo.getVerificationUriComplete()
                                    : deviceCodeInfo.getVerificationUri())
                            .build());
                    variables.add(MailService.TemplateVariable.builder()
                            .type(MailService.TemplateVariableType.RAW)
                            .ref("expiresInMinutes")
                            .value(expiresInMinutes)
                            .build());
                    variables.add(MailService.TemplateVariable.builder()
                            .type(MailService.TemplateVariableType.RAW)
                            .ref("errorMessage")
                            .value(errorMessage)
                            .build());

                    log.info("Sending OAuth2 device code email to admin: {} with user code: {}",
                            admin.getEmail(), deviceCodeInfo.getUserCode());

                    // Since sendTemplatedEmail doesn't throw exceptions and fails silently,
                    // we'll send the fallback email directly which is guaranteed to work
                    // This ensures the admin always receives the device code information
                    sendFallbackEmailWithDeviceCode(admin, errorMessage, deviceCodeInfo);
                }
            }

            // Save notification record
            MatrixBotErrorNotificationEntity notification = MatrixBotErrorNotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .lastNotificationDate(today)
                    .errorMessage(errorMessage)
                    .build();
            errorNotificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Failed to send OAuth2 error notification email with device code", e);
            // Fallback: send simple error email
            sendSimpleErrorEmail(errorMessage, today);
        }
    }

    /**
     * Send fallback email with device code information when template fails
     */
    private void sendFallbackEmailWithDeviceCode(UserEntity admin, String errorMessage, DeviceCodeInfo deviceCodeInfo) {
        try {
            String subject = "Matrix Bot OAuth2 Error - Device Code Required";
            String verificationUri = deviceCodeInfo.getVerificationUriComplete() != null
                    ? deviceCodeInfo.getVerificationUriComplete()
                    : deviceCodeInfo.getVerificationUri();
            int expiresInMinutes = (int) (deviceCodeInfo.getExpiresIn() / 60);

            String htmlContent = String.format(
                    "<html><body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                            "<h2 style='color: #1b1f3b;'>Matrix Bot OAuth2 Authorization Required</h2>" +
                            "<p>Hello %s,</p>" +
                            "<p>An error occurred with the Matrix bot OAuth2 authentication. To re-authorize the bot, please use the device code flow.</p>"
                            +
                            "<div style='background-color: #f8f9fa; border: 1px solid #dee2e6; border-radius: 8px; padding: 20px; margin: 20px 0; text-align: center;'>"
                            +
                            "<div style='font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #1b1f3b; font-family: monospace; margin: 20px 0;'>%s</div>"
                            +
                            "<p style='color: #6c757d;'>Enter this code on the verification page</p>" +
                            "</div>" +
                            "<p style='text-align: center; margin: 30px 0;'>" +
                            "<a href='%s' style='background-color: #FB8C02; color: white; padding: 12px 24px; text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;'>Authorize Bot</a>"
                            +
                            "</p>" +
                            "<p style='color: #6c757d; font-size: 14px;'>This code will expire in %d minutes.</p>" +
                            "<p style='color: #6c757d; font-size: 14px;'><strong>Original error:</strong> %s</p>" +
                            "<p style='margin-top: 30px; color: #6c757d;'>Best regards,<br>NeoHoods Team</p>" +
                            "</body></html>",
                    admin.getUsername(),
                    deviceCodeInfo.getUserCode(),
                    verificationUri,
                    expiresInMinutes,
                    errorMessage);

            mailService.sendMail(admin.getEmail(), subject, htmlContent);
            log.info("Sent fallback OAuth2 device code email to admin: {}", admin.getEmail());
        } catch (Exception e) {
            log.error("Failed to send fallback email with device code to admin: {}", admin.getEmail(), e);
        }
    }

    /**
     * Fallback method to send simple error email if device code flow fails
     */
    private void sendSimpleErrorEmail(String errorMessage, LocalDate today) {
        try {
            List<UserEntity> admins = usersRepository.findByType(UserType.ADMIN);
            if (admins.isEmpty()) {
                log.warn("No admin users found to notify about Matrix OAuth2 error");
                return;
            }

            String subject = "Matrix Bot OAuth2 Error";
            String htmlContent = String.format(
                    "<h2>Matrix Bot OAuth2 Error</h2>" +
                            "<p>An error occurred with the Matrix bot OAuth2 authentication:</p>" +
                            "<p><strong>Error:</strong> %s</p>" +
                            "<p>Please check the Matrix bot configuration and OAuth2 settings.</p>",
                    errorMessage);

            for (UserEntity admin : admins) {
                if (admin.getEmail() != null) {
                    mailService.sendMail(admin.getEmail(), subject, htmlContent);
                    log.info("Sent simple OAuth2 error notification email to admin: {}", admin.getEmail());
                }
            }

            // Save notification record
            MatrixBotErrorNotificationEntity notification = MatrixBotErrorNotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .lastNotificationDate(today)
                    .errorMessage(errorMessage)
                    .build();
            errorNotificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Failed to send simple OAuth2 error notification email", e);
        }
    }
}
