package com.neohoods.portal.platform.api.admin.matrix;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.MatrixBotAdminApiApiDelegate;
import com.neohoods.portal.platform.model.GetMatrixBotDeviceCode200Response;
import com.neohoods.portal.platform.model.GetMatrixBotOAuth2RedirectUri200Response;
import com.neohoods.portal.platform.model.HandleMatrixBotOAuth2Callback200Response;
import com.neohoods.portal.platform.model.InitializeMatrixBot200Response;
import com.neohoods.portal.platform.model.MatrixBotStatus;
import com.neohoods.portal.platform.model.MatrixBotStatusCurrentSpacesInner;
import com.neohoods.portal.platform.model.MatrixBotStatus.MatrixAccessEnum;
import com.neohoods.portal.platform.model.PollMatrixBotDeviceCode200Response;
import com.neohoods.portal.platform.model.PollMatrixBotDeviceCodeRequest;
import com.neohoods.portal.platform.services.MatrixAssistantService;
import com.neohoods.portal.platform.services.MatrixAssistantInitializationService;
import com.neohoods.portal.platform.services.MatrixOAuth2Service;
import com.neohoods.portal.platform.services.MatrixOAuth2Service.DeviceCodeInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatrixBotAdminApi implements MatrixBotAdminApiApiDelegate {

    private final MatrixAssistantService matrixAssistantService;
    private final MatrixOAuth2Service oauth2Service;
    private final MatrixAssistantInitializationService matrixBotInitializationService;

    /**
     * Get Matrix bot status
     * Implements GET /admin/matrix-bot/status
     */
    @Override
    public Mono<ResponseEntity<MatrixBotStatus>> getMatrixBotStatus(ServerWebExchange exchange) {
        try {
            Map<String, Object> statusMap = matrixAssistantService.getBotStatus();
            MatrixBotStatus status = convertToMatrixBotStatus(statusMap);
            return Mono.just(ResponseEntity.ok(status));
        } catch (Exception e) {
            log.error("Error getting Matrix bot status", e);
            MatrixBotStatus errorStatus = MatrixBotStatus.builder()
                    .enabled(false)
                    .disabled(true)
                    .matrixAccess(MatrixAccessEnum.KO)
                    .hasRefreshToken(false)
                    .hasAccessToken(false)
                    .error("Error getting status: " + e.getMessage())
                    .build();
            return Mono.just(ResponseEntity.ok(errorStatus));
        }
    }

    /**
     * Get OAuth2 redirect URI
     * Implements GET /admin/matrix-bot/oauth2/redirect-uri
     */
    @Override
    public Mono<ResponseEntity<GetMatrixBotOAuth2RedirectUri200Response>> getMatrixBotOAuth2RedirectUri(
            ServerWebExchange exchange) {
        try {
            String state = java.util.UUID.randomUUID().toString();
            String redirectUriString = oauth2Service.generateRedirectUri(state);
            URI redirectUri = URI.create(redirectUriString);
            GetMatrixBotOAuth2RedirectUri200Response response = GetMatrixBotOAuth2RedirectUri200Response.builder()
                    .redirectUri(redirectUri)
                    .build();
            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception e) {
            log.error("Error getting OAuth2 redirect URI", e);
            GetMatrixBotOAuth2RedirectUri200Response error = GetMatrixBotOAuth2RedirectUri200Response.builder()
                    .redirectUri(URI.create(""))
                    .build();
            return Mono.just(ResponseEntity.ok(error));
        }
    }

    /**
     * Handle OAuth2 callback
     * Implements GET /admin/matrix-bot/oauth2/callback
     */
    @Override
    public Mono<ResponseEntity<HandleMatrixBotOAuth2Callback200Response>> handleMatrixBotOAuth2Callback(
            String code,
            String state,
            ServerWebExchange exchange) {
        try {
            // Exchange code for tokens using stored PKCE code verifier
            oauth2Service.exchangeCodeForTokens(code, state);
            HandleMatrixBotOAuth2Callback200Response response = HandleMatrixBotOAuth2Callback200Response.builder()
                    .success(true)
                    .message("OAuth2 tokens saved successfully")
                    .build();
            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception e) {
            log.error("Error handling OAuth2 callback", e);
            HandleMatrixBotOAuth2Callback200Response error = HandleMatrixBotOAuth2Callback200Response.builder()
                    .success(false)
                    .message("Error processing callback: " + e.getMessage())
                    .build();
            return Mono.just(ResponseEntity.ok(error));
        }
    }

    /**
     * Get device code for OAuth2 device code flow
     * Implements GET /admin/matrix-bot/oauth2/device-code
     */
    @Override
    public Mono<ResponseEntity<GetMatrixBotDeviceCode200Response>> getMatrixBotDeviceCode(
            ServerWebExchange exchange) {
        try {
            DeviceCodeInfo deviceCodeInfo = oauth2Service.initiateDeviceCodeFlow();

            GetMatrixBotDeviceCode200Response response = GetMatrixBotDeviceCode200Response
                    .builder()
                    .deviceCode(deviceCodeInfo.getDeviceCode())
                    .userCode(deviceCodeInfo.getUserCode())
                    .verificationUri(URI.create(deviceCodeInfo.getVerificationUri()))
                    .verificationUriComplete(URI.create(deviceCodeInfo.getVerificationUriComplete()))
                    .expiresIn((int) deviceCodeInfo.getExpiresIn())
                    .interval((int) deviceCodeInfo.getInterval())
                    .build();

            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception e) {
            log.error("Error getting device code", e);
            GetMatrixBotDeviceCode200Response error = GetMatrixBotDeviceCode200Response
                    .builder()
                    .deviceCode("")
                    .userCode("")
                    .verificationUri(URI.create(""))
                    .verificationUriComplete(URI.create(""))
                    .expiresIn(0)
                    .interval(0)
                    .build();
            return Mono.just(ResponseEntity.ok(error));
        }
    }

    /**
     * Poll device code status (backend handles polling internally)
     * Implements POST /admin/matrix-bot/oauth2/device-code/poll
     */
    @Override
    public Mono<ResponseEntity<PollMatrixBotDeviceCode200Response>> pollMatrixBotDeviceCode(
            Mono<PollMatrixBotDeviceCodeRequest> pollMatrixBotDeviceCodeRequestMono,
            ServerWebExchange exchange) {
        return pollMatrixBotDeviceCodeRequestMono.flatMap(pollMatrixBotDeviceCodeRequest -> {
            try {
                String deviceCode = pollMatrixBotDeviceCodeRequest.getDeviceCode();
                if (deviceCode == null || deviceCode.isEmpty()) {
                    PollMatrixBotDeviceCode200Response error = PollMatrixBotDeviceCode200Response
                            .builder()
                            .status(PollMatrixBotDeviceCode200Response.StatusEnum.ERROR)
                            .message("Device code is required")
                            .build();
                    return Mono.just(ResponseEntity.ok(error));
                }

                // Get device code entry to retrieve interval and expiration
                com.neohoods.portal.platform.services.MatrixOAuth2Service.DeviceCodeEntry deviceCodeEntry = oauth2Service
                        .getDeviceCodeEntry(deviceCode);

                if (deviceCodeEntry == null) {
                    PollMatrixBotDeviceCode200Response error = PollMatrixBotDeviceCode200Response
                            .builder()
                            .status(PollMatrixBotDeviceCode200Response.StatusEnum.ERROR)
                            .message("Device code not found or expired")
                            .build();
                    return Mono.just(ResponseEntity.ok(error));
                }

                long intervalSeconds = deviceCodeEntry.interval;
                long timeoutSeconds = deviceCodeEntry.expiresIn;

                // Poll with retry in a non-blocking way using Mono.fromCallable
                return Mono.fromCallable(() -> {
                    return oauth2Service.pollDeviceCodeTokenWithRetry(deviceCode, intervalSeconds, timeoutSeconds);
                })
                        .subscribeOn(Schedulers.boundedElastic())
                        .timeout(java.time.Duration.ofSeconds(timeoutSeconds + 10))
                        .map(accessTokenOpt -> {
                            if (accessTokenOpt.isPresent()) {
                                return PollMatrixBotDeviceCode200Response
                                        .builder()
                                        .status(PollMatrixBotDeviceCode200Response.StatusEnum.SUCCESS)
                                        .message("OAuth2 tokens saved successfully")
                                        .accessToken(accessTokenOpt.get())
                                        .build();
                            } else {
                                return PollMatrixBotDeviceCode200Response
                                        .builder()
                                        .status(PollMatrixBotDeviceCode200Response.StatusEnum.ERROR)
                                        .message("Authorization timeout or device code expired")
                                        .build();
                            }
                        })
                        .onErrorResume(e -> {
                            log.error("Error polling device code", e);
                            PollMatrixBotDeviceCode200Response error = PollMatrixBotDeviceCode200Response
                                    .builder()
                                    .status(PollMatrixBotDeviceCode200Response.StatusEnum.ERROR)
                                    .message("Error polling device code: " + e.getMessage())
                                    .build();
                            return Mono.just(error);
                        })
                        .map(ResponseEntity::ok);
            } catch (Exception e) {
                log.error("Error starting device code polling", e);
                PollMatrixBotDeviceCode200Response error = PollMatrixBotDeviceCode200Response
                        .builder()
                        .status(PollMatrixBotDeviceCode200Response.StatusEnum.ERROR)
                        .message("Error starting device code polling: " + e.getMessage())
                        .build();
                return Mono.just(ResponseEntity.ok(error));
            }
        });
    }

    /**
     * Manually trigger Matrix bot initialization
     * Implements POST /admin/matrix-bot/initialize
     */
    @Override
    public Mono<ResponseEntity<InitializeMatrixBot200Response>> initializeMatrixBot(
            ServerWebExchange exchange) {
        try {
            // Run initialization asynchronously to avoid blocking the request
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    matrixBotInitializationService.initializeBotManually();
                } catch (Exception e) {
                    log.error("Error during async Matrix bot initialization", e);
                }
            });

            InitializeMatrixBot200Response response = InitializeMatrixBot200Response
                    .builder()
                    .success(true)
                    .message("Matrix bot initialization started successfully")
                    .build();
            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception e) {
            log.error("Error starting Matrix bot initialization", e);
            InitializeMatrixBot200Response error = InitializeMatrixBot200Response
                    .builder()
                    .success(false)
                    .message("Error starting Matrix bot initialization: " + e.getMessage())
                    .build();
            return Mono.just(ResponseEntity.ok(error));
        }
    }

    /**
     * Convert Map status to MatrixBotStatus model
     */
    private MatrixBotStatus convertToMatrixBotStatus(Map<String, Object> statusMap) {
        MatrixBotStatus.MatrixBotStatusBuilder builder = MatrixBotStatus.builder();

        if (statusMap.containsKey("enabled")) {
            builder.enabled((Boolean) statusMap.get("enabled"));
        }
        if (statusMap.containsKey("disabled")) {
            builder.disabled((Boolean) statusMap.get("disabled"));
        }
        if (statusMap.containsKey("matrixAccess")) {
            String access = (String) statusMap.get("matrixAccess");
            builder.matrixAccess("ok".equals(access) ? MatrixAccessEnum.OK : MatrixAccessEnum.KO);
        }
        if (statusMap.containsKey("hasRefreshToken")) {
            builder.hasRefreshToken((Boolean) statusMap.get("hasRefreshToken"));
        }
        if (statusMap.containsKey("hasAccessToken")) {
            builder.hasAccessToken((Boolean) statusMap.get("hasAccessToken"));
        }
        if (statusMap.containsKey("spaceId")) {
            builder.spaceId((String) statusMap.get("spaceId"));
        }
        if (statusMap.containsKey("error")) {
            builder.error((String) statusMap.get("error"));
        }
        if (statusMap.containsKey("currentSpaces")) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> spacesList = (List<Map<String, String>>) statusMap.get("currentSpaces");
            List<MatrixBotStatusCurrentSpacesInner> currentSpaces = new ArrayList<>();
            if (spacesList != null) {
                for (Map<String, String> spaceMap : spacesList) {
                    MatrixBotStatusCurrentSpacesInner space = MatrixBotStatusCurrentSpacesInner.builder()
                            .spaceId(spaceMap.get("spaceId"))
                            .name(spaceMap.get("name"))
                            .build();
                    currentSpaces.add(space);
                }
            }
            builder.currentSpaces(currentSpaces);
        }

        return builder.build();
    }
}
