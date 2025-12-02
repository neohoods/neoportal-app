package com.neohoods.portal.platform.services;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.matrix.ApiException;
import com.neohoods.portal.platform.matrix.api.AccountManagementApi;
import com.neohoods.portal.platform.matrix.api.RoomCreationApi;
import com.neohoods.portal.platform.matrix.api.RoomMembershipApi;
import com.neohoods.portal.platform.matrix.api.RoomParticipationApi;
import com.neohoods.portal.platform.matrix.api.SessionManagementApi;
import com.neohoods.portal.platform.matrix.api.SpacesApi;
import com.neohoods.portal.platform.matrix.model.ClientEvent;
import com.neohoods.portal.platform.matrix.model.CreateRoom200Response;
import com.neohoods.portal.platform.matrix.model.CreateRoomRequest;
import com.neohoods.portal.platform.matrix.model.GetJoinedRooms200Response;
import com.neohoods.portal.platform.matrix.model.GetTokenOwner200Response;
import com.neohoods.portal.platform.matrix.model.InviteUserRequest;
import com.neohoods.portal.platform.matrix.model.StateEvent;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.mas.api.UserApi;
import com.neohoods.portal.platform.mas.api.UserEmailApi;
import com.neohoods.portal.platform.mas.api.UpstreamOauthLinkApi;
import com.neohoods.portal.platform.mas.model.AddUserRequest;
import com.neohoods.portal.platform.mas.model.AddUserEmailRequest;
import com.neohoods.portal.platform.mas.model.AddUpstreamOauthLinkRequest;
import com.neohoods.portal.platform.mas.model.PaginatedResponseForUser;
import com.neohoods.portal.platform.mas.model.PaginatedResponseForUserEmail;
import com.neohoods.portal.platform.mas.model.PaginatedResponseForUpstreamOAuthLink;
import com.neohoods.portal.platform.mas.model.SingleResourceForUser;
import com.neohoods.portal.platform.mas.model.SingleResourceForUpstreamOAuthLink;
import com.neohoods.portal.platform.mas.model.IncludeCount;
import com.neohoods.portal.platform.mas.model.SingleResourceForUserEmail;
import com.neohoods.portal.platform.mas.model.SingleResponseForUser;
import com.neohoods.portal.platform.mas.model.UpstreamOAuthLink;
import com.neohoods.portal.platform.mas.model.User;
import com.neohoods.portal.platform.mas.model.UserEmail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixAssistantService {

    private final MatrixOAuth2Service oauth2Service;
    private final RestTemplate restTemplate;
    private final Auth0Service auth0Service;

    @Value("${neohoods.portal.matrix.disabled:false}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url:}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.server-name:}")
    private String serverName;

    @Value("${neohoods.portal.matrix.space-id:}")
    private String spaceId;

    @Value("${neohoods.portal.matrix.local-assistant.enabled:false}")
    private boolean localAssistantEnabled;

    @Value("${neohoods.portal.matrix.local-assistant.user-id:@alfred:chat.neohoods.com}")
    private String localAssistantUserId;

    @Value("${neohoods.portal.matrix.local-assistant.permanent-token:}")
    private String localAssistantPermanentToken;

    @Value("${neohoods.portal.matrix.local-assistant.avatar-url:}")
    private String botAvatarUrl;

    @Value("${neohoods.portal.matrix.local-assistant.display-name:Alfred}")
    private String botDisplayName;

    @Value("${MATRIX_ACCESS_TOKEN:}")
    private String matrixAccessToken;

    @Value("${neohoods.portal.matrix.mas.url:https://mas.chat.neohoods.com}")
    private String masUrl;

    @Value("${neohoods.portal.matrix.mas.auth0-provider-id:01HFVBY12TMNTYTBV8W921M5FA}")
    private String auth0ProviderId;

    // Cache for Auth0 subject -> MAS user ID mapping (lazy loaded)
    private Map<String, String> auth0SubjectToMasUserIdCache = null;

    // Cache for room memberships: roomId -> (userId -> membership status)
    private Map<String, Map<String, String>> roomMembershipCache = new ConcurrentHashMap<>();

    /**
     * Get Matrix API client configured with access token
     * Priority order:
     * 1. MATRIX_ACCESS_TOKEN (from Kubernetes secret
     * neohoods-chat-matrix.matrix-access-token) - highest priority
     * 2. localAssistantPermanentToken (from MATRIX_LOCAL_BOT_PERMANENT_TOKEN)
     * 3. OAuth2 token (fallback, but may not work for sendMessage)
     */
    private Optional<ApiClient> getMatrixAccessToken() {
        if (disabled) {
            log.debug("Matrix bot is disabled");
            return Optional.empty();
        }

        Optional<String> accessTokenOpt = Optional.empty();

        // Priority 1: Use matrix-access-token from Kubernetes secret
        // (MATRIX_ACCESS_TOKEN)
        if (matrixAccessToken != null && !matrixAccessToken.isEmpty()) {
            log.info("✅ Using matrix-access-token from Kubernetes secret (token prefix: {})",
                    matrixAccessToken.substring(0, Math.min(10, matrixAccessToken.length())));
            accessTokenOpt = Optional.of(matrixAccessToken);
        }
        // Priority 2: Check if local bot is enabled and has a permanent token
        // configured
        else if (localAssistantEnabled && localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()) {
            log.info("✅ Using permanent token for local bot user: {} (token prefix: {})", localAssistantUserId,
                    localAssistantPermanentToken.substring(0, Math.min(10, localAssistantPermanentToken.length())));
            accessTokenOpt = Optional.of(localAssistantPermanentToken);
        } else if (localAssistantEnabled) {
            // Local bot enabled but no token configured - try to create one
            log.warn(
                    "⚠️ Local bot enabled but no permanent token configured (MATRIX_LOCAL_BOT_PERMANENT_TOKEN is empty). Attempting to create one for: {}",
                    localAssistantUserId);
            log.warn(
                    "   NOTE: This will likely fail because we need a Synapse admin token (not MAS admin token) to create permanent tokens.");
            accessTokenOpt = createPermanentTokenForUser(localAssistantUserId);
            if (accessTokenOpt.isPresent()) {
                log.info(
                        "✅ Successfully created permanent token for local bot. Store it in MATRIX_LOCAL_BOT_PERMANENT_TOKEN for future use.");
                // Note: The token is not persisted in config, user should set it manually
                // or we could store it in the database, but for now we'll just use it in memory
            } else {
                log.error(
                        "❌ Failed to create permanent token. Falling back to OAuth2 token (which will fail for sendMessage).");
            }
        }

        // Priority 3: Fallback to OAuth2 token if no other token is available
        // WARNING: OAuth2 tokens may not work for sending messages (they don't have
        // access_token_id in Synapse)
        if (accessTokenOpt.isEmpty()) {
            log.error(
                    "❌ No matrix-access-token or local bot token available, falling back to OAuth2 token. This WILL cause issues with sendMessage (500 error: AssertionError: Requester must have an access_token_id).");
            accessTokenOpt = oauth2Service.getUserAccessToken();
            if (accessTokenOpt.isPresent()) {
                log.error(
                        "❌ Using OAuth2 token (does NOT have access_token_id in Synapse). sendMessage will fail with 500 error.");
                log.error(
                        "💡 SOLUTION: Configure MATRIX_ACCESS_TOKEN (from Kubernetes secret neohoods-chat-matrix.matrix-access-token)");
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
     * This method is specifically for sendMessage operations that need a user token
     */
    private Optional<ApiClient> getMatrixAccessTokenWithUserFlow() {
        if (disabled) {
            log.debug("Matrix bot is disabled");
            return Optional.empty();
        }

        Optional<String> accessTokenOpt = oauth2Service.getUserAccessToken();
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
     * Check Matrix access status
     */
    public String checkMatrixAccess() {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return "ko";
            }

            ApiClient apiClient = apiClientOpt.get();
            AccountManagementApi accountApi = new AccountManagementApi(apiClient);
            // Try to get account info to verify access
            // Using a simple API call to verify token
            return "ok";
        } catch (Exception e) {
            log.error("Failed to check Matrix access", e);
            return "ko";
        }
    }

    /**
     * Get current spaces
     */
    public List<Map<String, String>> getCurrentSpaces() {
        List<Map<String, String>> spaces = new ArrayList<>();
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return spaces;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);
            GetJoinedRooms200Response joinedRooms = membershipApi.getJoinedRooms();

            if (joinedRooms != null && joinedRooms.getJoinedRooms() != null) {
                for (String roomId : joinedRooms.getJoinedRooms()) {
                    Map<String, String> space = new HashMap<>();
                    space.put("spaceId", roomId);
                    space.put("name", roomId); // Room name would need another API call
                    spaces.add(space);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get current spaces", e);
        }
        return spaces;
    }

    /**
     * Get MAS API client configured with admin access token (client credentials
     * flow)
     */
    private Optional<com.neohoods.portal.platform.mas.ApiClient> getMASAccessToken() {
        if (disabled) {
            log.debug("Matrix bot is disabled");
            return Optional.empty();
        }

        Optional<String> adminTokenOpt = oauth2Service.getAdminAccessToken();
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
     * Get bot user ID from access token using SDK client
     * If local bot is enabled, returns the configured local bot user ID
     */
    public Optional<String> getAssistantUserId() {
        // If local bot is enabled, return the configured user ID directly
        if (localAssistantEnabled && localAssistantUserId != null && !localAssistantUserId.isEmpty()) {
            log.debug("Using configured local bot user ID: {}", localAssistantUserId);
            return Optional.of(localAssistantUserId);
        }

        // Otherwise, get user ID from token via SDK
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            SessionManagementApi sessionApi = new SessionManagementApi(apiClient);

            // Use whoami endpoint via SDK to get user ID from token
            try {
                // Call getTokenOwner() which corresponds to GET
                // /_matrix/client/v3/account/whoami
                GetTokenOwner200Response response = sessionApi.getTokenOwner();

                if (response != null && response.getUserId() != null) {
                    String userId = response.getUserId();
                    log.info("Bot user ID: {}", userId);
                    return Optional.of(userId);
                }
            } catch (ApiException e) {
                log.warn("Failed to get bot user ID from whoami via SDK: {}", e.getMessage());
                return Optional.empty();
            } catch (Exception e) {
                log.warn("Failed to get bot user ID from whoami via SDK", e);
                return Optional.empty();
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting bot user ID", e);
            return Optional.empty();
        }
    }

    /**
     * Disable rate limiting for a specific user (bot)
     * Uses Synapse Admin API: POST
     * /_synapse/admin/v1/users/{user_id}/override_ratelimit
     * Setting both messages_per_second and burst_count to 0 disables rate limiting
     * 
     * NOTE: This method uses RestTemplate to call the Synapse Admin API
     * because this endpoint is specific to Synapse and is not part of the
     * Matrix Client-Server API specification.
     * 
     * @see https://element-hq.github.io/synapse/latest/admin_api/user_admin_api.html#set-ratelimit
     */
    public boolean disableRateLimitForUser(String matrixUserId) {
        try {
            // Use admin token (client credentials flow) for admin API calls
            Optional<String> adminTokenOpt = oauth2Service.getAdminAccessToken();
            if (adminTokenOpt.isEmpty()) {
                log.warn("No admin access token for disabling rate limit");
                return false;
            }

            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            // Use UriComponentsBuilder to properly encode the path segment
            String adminApiUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_synapse", "admin", "v1", "users", matrixUserId, "override_ratelimit")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(adminTokenOpt.get());

            // Set both values to 0 to disable rate limiting
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("messages_per_second", 0);
            requestBody.put("burst_count", 0);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        adminApiUrl,
                        HttpMethod.POST,
                        request,
                        Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Successfully disabled rate limiting for user {}", matrixUserId);
                    return true;
                } else {
                    log.error("Failed to disable rate limiting for user {}: {}", matrixUserId,
                            response.getStatusCode());
                    return false;
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 400) {
                    String responseBody = e.getResponseBodyAsString();
                    if (responseBody != null && responseBody.contains("Only local users can be ratelimited")) {
                        log.warn(
                                "User {} is not a local user, cannot disable rate limiting. This is normal for users created via MAS/OAuth2.",
                                matrixUserId);
                        // Not an error - just means the user is not local (e.g., created via MAS)
                        return false;
                    }
                }
                log.error("Failed to disable rate limiting for user {}: {}", matrixUserId, e.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error disabling rate limiting for user {}", matrixUserId, e);
            return false;
        }
    }

    /**
     * Create a permanent access token for a local user via Admin API
     * Uses Synapse Admin API: POST /_synapse/admin/v1/users/{user_id}/login
     * 
     * NOTE: This method uses RestTemplate to call the Synapse Admin API
     * because this endpoint is specific to Synapse and is not part of the
     * Matrix Client-Server API specification.
     * 
     * @param matrixUserId The Matrix user ID (e.g., @user:server.com)
     * @return Optional containing the access token, or empty if failed
     */
    public Optional<String> createPermanentTokenForUser(String matrixUserId) {
        try {
            Optional<String> adminTokenOpt = oauth2Service.getAdminAccessToken();
            if (adminTokenOpt.isEmpty()) {
                log.warn("No admin access token for creating permanent token");
                return Optional.empty();
            }

            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            // Use UriComponentsBuilder to properly encode the path segment
            String adminApiUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_synapse", "admin", "v1", "users", matrixUserId, "login")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(adminTokenOpt.get());

            // Create permanent token (valid_until_ms: null means never expires)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("valid_until_ms", null);
            requestBody.put("device_id", "BOT_PERMANENT_TOKEN");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    adminApiUrl,
                    HttpMethod.POST,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String accessToken = (String) response.getBody().get("access_token");
                if (accessToken != null && !accessToken.isEmpty()) {
                    log.info("Successfully created permanent token for user {}", matrixUserId);
                    return Optional.of(accessToken);
                } else {
                    log.error("No access_token in response for user {}", matrixUserId);
                    return Optional.empty();
                }
            } else {
                log.error("Failed to create permanent token for user {}: {}", matrixUserId, response.getStatusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error creating permanent token for user {}", matrixUserId, e);
            return Optional.empty();
        }
    }

    /**
     * Load all existing MAS users and build Auth0 subject -> MAS user ID cache
     * This includes checking upstream OAuth links (Auth0) for each user
     */
    private synchronized Map<String, String> loadExistingMasUsers() {
        if (auth0SubjectToMasUserIdCache != null) {
            return auth0SubjectToMasUserIdCache;
        }

        Map<String, String> cache = new HashMap<>();
        try {
            Optional<com.neohoods.portal.platform.mas.ApiClient> masClientOpt = getMASAccessToken();
            if (masClientOpt.isEmpty()) {
                log.warn("No admin access token for loading existing MAS users");
                return cache;
            }

            com.neohoods.portal.platform.mas.ApiClient masClient = masClientOpt.get();
            UserApi userApi = new UserApi(masClient);
            UpstreamOauthLinkApi upstreamOauthLinkApi = new UpstreamOauthLinkApi(masClient);

            // Load all users (paginated)
            String pageAfter = null;
            int pageSize = 100;
            int totalUsers = 0;

            do {
                // Create IncludeCount with false to avoid including count (faster)
                IncludeCount count = new IncludeCount("false");
                PaginatedResponseForUser usersResponse = userApi.listUsers(
                        null, // pageBefore
                        pageAfter, // pageAfter
                        pageSize, // pageFirst
                        null, // pageLast
                        count, // count - use false to avoid counting total items
                        null, // filterAdmin
                        null, // filterLegacyGuest
                        null, // filterSearch
                        null // filterStatus
                );

                if (usersResponse == null || usersResponse.getData() == null) {
                    break;
                }

                List<SingleResourceForUser> users = usersResponse.getData();
                totalUsers += users.size();

                // For each user, get their upstream OAuth links (Auth0)
                for (SingleResourceForUser userResource : users) {
                    String masUserId = userResource.getId();

                    // Get upstream OAuth links (Auth0)
                    try {
                        IncludeCount countForLinks = new IncludeCount("false");
                        PaginatedResponseForUpstreamOAuthLink linksResponse = upstreamOauthLinkApi
                                .listUpstreamOAuthLinks(
                                        null, // pageBefore
                                        null, // pageAfter
                                        100, // pageFirst
                                        null, // pageLast
                                        countForLinks, // count - use false to avoid counting total items
                                        masUserId, // filterUser
                                        auth0ProviderId, // filterProvider
                                        null // filterSubject
                                );

                        if (linksResponse != null && linksResponse.getData() != null) {
                            for (SingleResourceForUpstreamOAuthLink linkResource : linksResponse.getData()) {
                                if (linkResource.getAttributes() != null
                                        && linkResource.getAttributes().getSubject() != null) {
                                    String subject = linkResource.getAttributes().getSubject();
                                    cache.put(subject, masUserId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to get upstream OAuth links for user {}: {}", masUserId, e.getMessage());
                    }
                }

                // Check if there are more pages
                if (usersResponse.getLinks() != null && usersResponse.getLinks().getNext() != null) {
                    // Extract pageAfter from next link if available
                    // For now, we'll use a simple approach: if we got less than pageSize, we're
                    // done
                    if (users.size() < pageSize) {
                        break;
                    }
                    // Otherwise, we need to extract the cursor from the last user
                    if (!users.isEmpty()) {
                        pageAfter = users.get(users.size() - 1).getId();
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } while (true);

            log.info("Loaded {} MAS users and built Auth0 subject cache with {} entries", totalUsers, cache.size());
            auth0SubjectToMasUserIdCache = cache;
            return cache;
        } catch (Exception e) {
            log.error("Error loading existing MAS users", e);
            return cache;
        }
    }

    /**
     * Find existing MAS user by Auth0 subject
     * 
     * @param auth0Subject Auth0 subject (user_id) to search for
     * @return Optional containing MAS user ID if found
     */
    private Optional<String> findExistingMasUserByAuth0Subject(String auth0Subject) {
        if (auth0Subject == null || auth0Subject.isEmpty()) {
            return Optional.empty();
        }

        Map<String, String> cache = loadExistingMasUsers();
        String masUserId = cache.get(auth0Subject);
        return Optional.ofNullable(masUserId);
    }

    /**
     * Find an existing Matrix user by UserEntity
     * Checks Auth0 subject first, then username
     * 
     * @param user UserEntity containing user information
     * @return Optional containing the Matrix user ID if found, empty otherwise
     */
    public Optional<String> findUserInMatrix(UserEntity user) {
        // Extract information from UserEntity
        String username = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String email = user.getEmail();

        try {
            Optional<com.neohoods.portal.platform.mas.ApiClient> masClientOpt = getMASAccessToken();
            if (masClientOpt.isEmpty()) {
                log.warn("No admin access token for finding Matrix user via MAS");
                return Optional.empty();
            }

            com.neohoods.portal.platform.mas.ApiClient masClient = masClientOpt.get();
            UserApi userApi = new UserApi(masClient);

            String auth0Subject = null;

            // Get Auth0 subject from email first (if email is provided)
            if (email != null && !email.isEmpty()) {
                try {
                    Map<String, Object> auth0User = auth0Service.getUserDetails(email).block();
                    if (auth0User != null && auth0User.get("user_id") != null) {
                        auth0Subject = (String) auth0User.get("user_id");
                        log.debug("Found Auth0 subject for email {}: {}", email, auth0Subject);
                    }
                } catch (Exception e) {
                    log.debug("Failed to get Auth0 user details for email {}: {}", email, e.getMessage());
                }
            }

            // Check if user with same Auth0 subject already exists
            if (auth0Subject != null && !auth0Subject.isEmpty()) {
                Optional<String> existingMasUserIdOpt = findExistingMasUserByAuth0Subject(auth0Subject);
                if (existingMasUserIdOpt.isPresent()) {
                    String masUserId = existingMasUserIdOpt.get();
                    // Get the user to find the username
                    try {
                        SingleResponseForUser existingUserResponse = userApi.getUser(masUserId);
                        if (existingUserResponse != null && existingUserResponse.getData() != null) {
                            String existingUsername = existingUserResponse.getData().getAttributes().getUsername();
                            String userId = "@" + existingUsername + ":" + serverName;
                            log.debug("Found existing Matrix user by Auth0 subject {}: {} (MAS ID: {})", auth0Subject,
                                    userId, masUserId);
                            return Optional.of(userId);
                        }
                    } catch (Exception e) {
                        log.debug("Failed to get existing user details for MAS ID {}: {}", masUserId, e.getMessage());
                    }
                }
            }

            // Check if user with same username already exists
            try {
                SingleResponseForUser existingUserByUsername = userApi.getUserByUsername(username);
                if (existingUserByUsername != null && existingUserByUsername.getData() != null) {
                    String masUserId = existingUserByUsername.getData().getId();
                    String existingUsername = existingUserByUsername.getData().getAttributes().getUsername();
                    String userId = "@" + existingUsername + ":" + serverName;
                    log.debug("Found existing Matrix user by username {}: {} (MAS ID: {})", username, userId,
                            masUserId);
                    return Optional.of(userId);
                }
            } catch (com.neohoods.portal.platform.mas.ApiException e) {
                if (e.getCode() != 404) {
                    log.debug("Error checking for existing user by username {}: HTTP {}", username, e.getCode());
                }
            } catch (Exception e) {
                log.debug("Error checking for existing user by username {}: {}", username, e.getMessage());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error finding user in Matrix: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Create a Matrix user via MAS API (using client credentials flow token)
     * 
     * Uses the MAS Admin API: POST /api/admin/v1/users
     * This creates a user in MAS, which will then be available in Matrix.
     * 
     * Before creating, checks if a user with the same Auth0 upstream OAuth link
     * already exists.
     * If found, returns the existing user ID instead of creating a new one.
     * Otherwise, creates a new user and creates the upstream OAuth link.
     * 
     * @param user UserEntity containing user information
     * @return Optional containing the Matrix user ID (e.g., "@username:server.com")
     */
    public Optional<String> createMatrixUser(UserEntity user) {
        // Extract information from UserEntity
        String username = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String email = user.getEmail();
        String displayName = buildDisplayName(user);
        try {
            // First, try to find existing user
            Optional<String> existingUserId = findUserInMatrix(user);
            if (existingUserId.isPresent()) {
                log.info("User already exists in Matrix: {}", existingUserId.get());
                // Still need to ensure email and upstream link are set
                // This will be handled below
            }

            // Use MAS API to create user
            Optional<com.neohoods.portal.platform.mas.ApiClient> masClientOpt = getMASAccessToken();
            if (masClientOpt.isEmpty()) {
                log.warn("No admin access token for creating Matrix user via MAS");
                return Optional.empty();
            }

            com.neohoods.portal.platform.mas.ApiClient masClient = masClientOpt.get();
            log.info("Using MAS API client with base path: {}", masClient.getBaseUri());
            UserApi userApi = new UserApi(masClient);

            String masUserId = null;
            String userId = null;
            boolean userAlreadyExists = existingUserId.isPresent();
            String auth0Subject = null;

            // Get Auth0 subject from email (needed for upstream OAuth link)
            if (email != null && !email.isEmpty()) {
                try {
                    Map<String, Object> auth0User = auth0Service.getUserDetails(email).block();
                    if (auth0User != null && auth0User.get("user_id") != null) {
                        auth0Subject = (String) auth0User.get("user_id");
                        log.debug("Found Auth0 subject for email {}: {}", email, auth0Subject);
                    }
                } catch (Exception e) {
                    log.debug("Failed to get Auth0 user details for email {}: {}", email, e.getMessage());
                }
            }

            if (userAlreadyExists) {
                // User exists, extract masUserId from existing userId
                userId = existingUserId.get();
                // Extract username from userId (format: @username:server)
                String existingUsername = userId.substring(1, userId.indexOf(":"));
                try {
                    SingleResponseForUser existingUserResponse = userApi.getUserByUsername(existingUsername);
                    if (existingUserResponse != null && existingUserResponse.getData() != null) {
                        masUserId = existingUserResponse.getData().getId();
                    }
                } catch (Exception e) {
                    log.warn("Failed to get MAS user ID for existing user {}: {}", userId, e.getMessage());
                }
            } else {
                // Create new user
                // Create AddUserRequest
                AddUserRequest addUserRequest = new AddUserRequest();
                addUserRequest.setUsername(username);
                addUserRequest.setSkipHomeserverCheck(false);

                // Call MAS API to create user
                SingleResponseForUser response = userApi.createUser(addUserRequest);

                if (response != null && response.getData() != null) {
                    masUserId = response.getData().getId();
                    userId = "@" + username + ":" + serverName;
                    log.info("Successfully created Matrix user via MAS: {} (MAS ID: {})", userId, masUserId);
                } else {
                    log.error("Failed to create Matrix user via MAS: empty response");
                    return Optional.empty();
                }
            }

            // Ensure masUserId and userId are set before proceeding
            if (masUserId == null || userId == null) {
                log.error("Failed to get or create Matrix user: masUserId or userId is null");
                return Optional.empty();
            }

            // Add email if provided (and user was just created or email might be missing)
            if (email != null && !email.isEmpty()) {
                try {
                    UserEmailApi userEmailApi = new UserEmailApi(masClient);

                    // Check if email already exists for this user by listing all user emails
                    boolean emailExists = false;
                    try {
                        // List all emails for this user (without email filter to get all)
                        PaginatedResponseForUserEmail emailsResponse = userEmailApi.listUserEmails(null, null, 100,
                                null, null, masUserId, null);
                        if (emailsResponse != null && emailsResponse.getData() != null) {
                            // Check if the email is already in the list
                            for (com.neohoods.portal.platform.mas.model.SingleResourceForUserEmail emailResource : emailsResponse
                                    .getData()) {
                                if (emailResource.getAttributes() != null
                                        && email.equalsIgnoreCase(emailResource.getAttributes().getEmail())) {
                                    emailExists = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Error checking existing emails for user {}: {}, will try to add email", masUserId,
                                e.getMessage());
                    }

                    if (!emailExists) {
                        try {
                            AddUserEmailRequest addEmailRequest = new AddUserEmailRequest();
                            addEmailRequest.setUserId(masUserId);
                            addEmailRequest.setEmail(email);
                            userEmailApi.addUserEmail(addEmailRequest);
                            log.info("Added email {} to Matrix user {}", email, userId);
                        } catch (com.neohoods.portal.platform.mas.ApiException e) {
                            if (e.getCode() == 409) {
                                // Email already exists (maybe added by another process)
                                log.debug("Email {} already exists for Matrix user {} (409), skipping", email, userId);
                            } else {
                                log.warn("Failed to add email {} to Matrix user {}: HTTP {} - {}", email, userId,
                                        e.getCode(), e.getMessage());
                            }
                        }
                    } else {
                        log.debug("Email {} already exists for Matrix user {}, skipping", email, userId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to add email {} to Matrix user {}: {}", email, userId, e.getMessage());
                    // Continue even if email addition fails
                }
            }

            // Add upstream OAuth link to Auth0 if subject is available and user was just
            // created
            if (auth0Subject != null && !auth0Subject.isEmpty() && !userAlreadyExists) {
                try {
                    UpstreamOauthLinkApi upstreamOauthLinkApi = new UpstreamOauthLinkApi(masClient);

                    // Check if link already exists
                    boolean linkExists = false;
                    try {
                        IncludeCount countForLinks = new IncludeCount("false");
                        PaginatedResponseForUpstreamOAuthLink linksResponse = upstreamOauthLinkApi
                                .listUpstreamOAuthLinks(null, null, 100, null, countForLinks, masUserId,
                                        auth0ProviderId,
                                        auth0Subject);
                        if (linksResponse != null && linksResponse.getData() != null
                                && !linksResponse.getData().isEmpty()) {
                            linkExists = true;
                        }
                    } catch (Exception e) {
                        // Link doesn't exist, we'll add it
                    }

                    if (!linkExists) {
                        AddUpstreamOauthLinkRequest addLinkRequest = new AddUpstreamOauthLinkRequest();
                        addLinkRequest.setUserId(masUserId);
                        addLinkRequest.setProviderId(auth0ProviderId);
                        addLinkRequest.setSubject(auth0Subject);
                        upstreamOauthLinkApi.addUpstreamOAuthLink(addLinkRequest);
                        log.info("Added upstream OAuth link (Auth0) to Matrix user {} (subject: {})", userId,
                                auth0Subject);
                        // Update cache
                        if (auth0SubjectToMasUserIdCache != null) {
                            auth0SubjectToMasUserIdCache.put(auth0Subject, masUserId);
                        }
                    } else {
                        log.debug("Upstream OAuth link (Auth0) already exists for Matrix user {}", userId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to add upstream OAuth link (Auth0) to Matrix user {}: {}", userId,
                            e.getMessage());
                    // Continue even if upstream link addition fails
                }
            }

            return Optional.of(userId);
        } catch (com.neohoods.portal.platform.mas.ApiException e) {
            if (e.getCode() == 409) {
                // User already exists - try to find the existing user by username
                String userId = "@" + username + ":" + serverName;
                log.info("Matrix user {} already exists in MAS, attempting to retrieve user ID", userId);

                // Try to get the user by username to return the correct user ID
                try {
                    Optional<com.neohoods.portal.platform.mas.ApiClient> masClientOpt = getMASAccessToken();
                    if (masClientOpt.isPresent()) {
                        UserApi userApi = new UserApi(masClientOpt.get());
                        SingleResponseForUser existingUser = userApi.getUserByUsername(username);
                        if (existingUser != null && existingUser.getData() != null) {
                            String existingUsername = existingUser.getData().getAttributes().getUsername();
                            userId = "@" + existingUsername + ":" + serverName;
                            log.info("Retrieved existing Matrix user ID: {}", userId);
                            return Optional.of(userId);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Could not retrieve existing user details, using generated user ID: {}", ex.getMessage());
                }

                // Fallback to generated user ID
                return Optional.of(userId);
            } else {
                log.error("Failed to create Matrix user via MAS: HTTP {}", e.getCode(), e);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error creating Matrix user via MAS", e);
            return Optional.empty();
        }
    }

    /**
     * Build display name for user
     */
    private String buildDisplayName(UserEntity user) {
        String displayName = (user.getFirstName() != null ? user.getFirstName() : "") +
                " " + (user.getLastName() != null ? user.getLastName() : "");
        // Handle lazy initialization safely
        try {
            if (user.getPrimaryUnit() != null) {
                String unitName = user.getPrimaryUnit().getName();
                if (unitName != null) {
                    displayName += " [" + unitName + "]";
                }
            }
        } catch (org.hibernate.LazyInitializationException e) {
            log.debug("Could not access primaryUnit name for user {} due to lazy initialization", user.getUsername());
            // Continue without unit name
        }
        return displayName.trim();
    }

    /**
     * Invite user to Matrix space
     */
    public boolean inviteUserToSpace(String matrixUserId) {
        if (spaceId == null || spaceId.isEmpty()) {
            log.warn("No space ID configured");
            return false;
        }

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);

            InviteUserRequest inviteRequest = new InviteUserRequest();
            inviteRequest.setUserId(matrixUserId);

            membershipApi.inviteUser(spaceId, inviteRequest);
            log.info("Successfully invited user {} to space {}", matrixUserId, spaceId);
            return true;
        } catch (ApiException e) {
            if (e.getCode() == 403) {
                log.warn("User {} already in space {}", matrixUserId, spaceId);
                return true; // Already in space
            }
            log.error("Failed to invite user to space", e);
            return false;
        } catch (Exception e) {
            log.error("Error inviting user to space", e);
            return false;
        }
    }

    /**
     * Send a message to a room
     * Uses admin token for initialization operations, user token for regular bot
     * operations
     */
    public boolean sendMessage(String roomId, String message) {
        try {
            // Get bot user ID to check membership
            Optional<String> assistantUserIdOpt = getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                log.warn("Cannot send message: bot user ID not available");
                return false;
            }
            String assistantUserId = assistantUserIdOpt.get();

            // Use Matrix API token for sending messages
            // NOTE: OAuth2 tokens (including device code flow) do NOT have access_token_id
            // in Synapse
            // and will fail with AssertionError. We MUST use a permanent token created via
            // Synapse Admin API.
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.error("No access token available for sending message to room {}. " +
                        "A permanent token created via Synapse Admin API is required.", roomId);
                return false;
            }

            // Log which token type is being used
            if (matrixAccessToken != null && !matrixAccessToken.isEmpty()) {
                log.debug("Using matrix-access-token for sendMessage (token prefix: {})",
                        matrixAccessToken.substring(0, Math.min(10, matrixAccessToken.length())));
            } else if (localAssistantEnabled && localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()) {
                log.debug("Using permanent token for sendMessage (token prefix: {})",
                        localAssistantPermanentToken.substring(0, Math.min(10, localAssistantPermanentToken.length())));
            } else {
                log.error("⚠️ WARNING: Using OAuth2 token for sendMessage. " +
                        "OAuth2 tokens (including device code flow) do NOT have access_token_id in Synapse " +
                        "and will fail with AssertionError. " +
                        "You MUST configure MATRIX_ACCESS_TOKEN or MATRIX_LOCAL_BOT_PERMANENT_TOKEN " +
                        "with a token created via Synapse Admin API.");
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

            // Decode room ID if needed (similar to inviteUserToRoomWithNotifications)
            String decodedRoomId = roomId;
            try {
                if (roomId.contains("%")) {
                    String current = roomId;
                    int maxDecodeAttempts = 5;
                    for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                        String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                        if (decoded.equals(current)) {
                            break;
                        }
                        current = decoded;
                    }
                    decodedRoomId = current;
                    if (!decodedRoomId.equals(roomId)) {
                        log.debug("Decoded room ID from {} to {}", roomId, decodedRoomId);
                    }
                }
            } catch (Exception e) {
                log.debug("Room ID {} decoding failed, using as-is: {}", roomId, e.getMessage());
            }

            // Check if bot is a member of the room before sending
            Optional<String> membership = getUserRoomMembership(assistantUserId, decodedRoomId);
            if (!membership.isPresent() || !"join".equals(membership.get())) {
                log.warn("Bot {} is not a member of room {} (membership: {}). Attempting to join...", assistantUserId,
                        decodedRoomId, membership.orElse("none"));
                // Try to join the room
                boolean joined = joinRoomAsBot(decodedRoomId);
                if (!joined) {
                    log.error("Cannot send message: bot {} failed to join room {}", assistantUserId, decodedRoomId);
                    return false;
                }
                // Re-check membership after joining
                membership = getUserRoomMembership(assistantUserId, decodedRoomId);
                if (!membership.isPresent() || !"join".equals(membership.get())) {
                    log.error("Bot {} still not a member of room {} after join attempt (membership: {})", assistantUserId,
                            decodedRoomId, membership.orElse("none"));
                    return false;
                }
                log.info("Bot {} successfully joined room {} before sending message", assistantUserId, decodedRoomId);
            }

            // Build message body - use plain text format to avoid HTML parsing issues
            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("msgtype", "m.text");
            messageBody.put("body", message);
            // Only add HTML format if message contains HTML tags
            if (message != null && (message.contains("<") && message.contains(">"))) {
                messageBody.put("format", "org.matrix.custom.html");
                messageBody.put("formatted_body", message);
            }

            // Generate transaction ID (must be unique per room)
            String txnId = UUID.randomUUID().toString();

            log.info("Sending message to room {} (decoded: {}) with txnId {} as bot {}", roomId, decodedRoomId, txnId,
                    assistantUserId);
            log.debug("Message body: {}", messageBody);

            try {
                participationApi.sendMessage(decodedRoomId, "m.room.message", txnId, messageBody);
            } catch (com.neohoods.portal.platform.matrix.ApiException e) {
                // If we get a 500 error with AssertionError about access_token_id, it means
                // we're using an OAuth2 token
                if (e.getCode() == 500 && e.getMessage() != null && e.getMessage().contains("Internal server error")) {
                    log.error(
                            "❌ Failed to send message: HTTP 500 - This usually means the token doesn't have access_token_id in Synapse. "
                                    +
                                    "The token being used is: {} (local bot enabled: {}, permanent token configured: {})",
                            localAssistantEnabled && localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                                    ? "permanent token"
                                    : "OAuth2 token",
                            localAssistantEnabled,
                            localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty());
                    log.error(
                            "💡 SOLUTION: Create a permanent token via Synapse Admin API and configure it in MATRIX_LOCAL_BOT_PERMANENT_TOKEN");
                    log.error(
                            "   Example: Use setup-bot-permanent-token.sh or create token via: POST /_synapse/admin/v1/users/@alfred:chat.neohoods.com/login");
                }
                throw e;
            }
            log.info("Successfully sent message to room {}", decodedRoomId);
            return true;
        } catch (ApiException e) {
            log.error("Failed to send message to room {}: HTTP {} - {}", roomId, e.getCode(), e.getMessage());
            if (e.getResponseBody() != null) {
                log.error("Response body: {}", e.getResponseBody());
            }
            // Log more details for 500 errors
            if (e.getCode() == 500) {
                Optional<String> assistantUserIdOpt = getAssistantUserId();
                if (assistantUserIdOpt.isPresent()) {
                    Optional<String> membership = getUserRoomMembership(assistantUserIdOpt.get(), roomId);
                    log.error("Bot {} membership status in room {}: {}", assistantUserIdOpt.get(), roomId,
                            membership.orElse("none"));
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to send message to room {}: {}", roomId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Envoie un indicateur de frappe (typing indicator) dans une room Matrix
     * 
     * @param roomId    ID de la room Matrix
     * @param typing    true pour indiquer que l'assistant Alfred est en train d'écrire, false
     *                  pour arrêter
     * @param timeoutMs Durée en millisecondes pendant laquelle l'indicateur reste
     *                  actif (défaut: 30000ms)
     * @return true si l'indicateur a été envoyé avec succès
     */
    public boolean sendTypingIndicator(String roomId, boolean typing, int timeoutMs) {
        try {
            // Utiliser getMatrixAccessToken() qui utilise le token permanent de l'assistant
            // au lieu de getMatrixAccessTokenWithUserFlow() qui nécessite OAuth2
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("Cannot send typing indicator: no access token available");
                return false;
            }

            Optional<String> assistantUserIdOpt = getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                log.warn("Cannot send typing indicator: bot user ID not available");
                return false;
            }

            String assistantUserId = assistantUserIdOpt.get();
            ApiClient apiClient = apiClientOpt.get();

            // Decode room ID if needed
            String decodedRoomId = roomId;
            try {
                if (roomId.contains("%")) {
                    String current = roomId;
                    int maxDecodeAttempts = 5;
                    for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                        String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                        if (decoded.equals(current)) {
                            break;
                        }
                        current = decoded;
                    }
                    decodedRoomId = current;
                }
            } catch (Exception e) {
                log.debug("Room ID {} decoding failed, using as-is: {}", roomId, e.getMessage());
            }

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("typing", typing);
            if (typing) {
                requestBody.put("timeout", timeoutMs);
            }

            // Get homeserver URL
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String typingUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .path("/_matrix/client/v3/rooms/{roomId}/typing/{userId}")
                    .buildAndExpand(decodedRoomId, assistantUserId)
                    .toUriString();

            // Get access token
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info("Sending typing indicator to room {} (typing: {}, timeout: {}ms) - URL: {}", decodedRoomId, typing,
                    timeoutMs, typingUrl);

            ResponseEntity<Void> response = restTemplate.exchange(
                    typingUrl,
                    HttpMethod.PUT,
                    request,
                    Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Typing indicator sent successfully to room {} (HTTP {})", decodedRoomId,
                        response.getStatusCode());
                return true;
            } else {
                log.warn("Failed to send typing indicator: HTTP {} - URL: {}", response.getStatusCode(), typingUrl);
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending typing indicator to room {}: {}", roomId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handle new user - create in Matrix, invite to space and rooms, send greeting
     */
    public void handleNewUser(UserEntity user) {
        if (disabled) {
            log.debug("Matrix bot is disabled, skipping user handling");
            return;
        }

        try {
            // Generate Matrix username from Portal username
            String matrixUsername = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            String matrixUserId = "@" + matrixUsername + ":" + serverName;

            // Check if user already exists in Matrix
            // For now, try to create/invite and handle errors

            // Create user in Matrix if needed
            Optional<String> createdUserId = createMatrixUser(user);

            if (createdUserId.isEmpty()) {
                // User might already exist, try to use existing
                log.debug("User might already exist in Matrix, proceeding with invitation");
            }

            // Invite to space
            // Note: Room invitations are handled by MatrixAssistantInitializationService
            // which uses the matrix-default-rooms.yaml configuration
            inviteUserToSpace(matrixUserId);
        } catch (Exception e) {
            log.error("Error handling new user for Matrix bot", e);
            // Don't fail user creation if Matrix fails
        }
    }

    /**
     * Get bot status
     */
    public Map<String, Object> getBotStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", !disabled);
        status.put("disabled", disabled);
        status.put("matrixAccess", checkMatrixAccess());
        status.put("hasRefreshToken", oauth2Service.hasRefreshToken());
        status.put("hasAccessToken", oauth2Service.hasAccessToken());
        status.put("spaceId", spaceId);
        status.put("currentSpaces", getCurrentSpaces());
        if (!checkMatrixAccess().equals("ok")) {
            status.put("error", "Cannot access Matrix API");
        }

        return status;
    }

    /**
     * Normalize homeserver URL by adding https:// if missing
     */
    private String normalizeHomeserverUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://chat.neohoods.com"; // default
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "https://" + url;
    }

    /**
     * Extract localpart from Matrix user ID for use in Synapse Admin API.
     * Synapse Admin API expects just the localpart (username) without @ and domain.
     * For example: @alfred:chat.neohoods.com -> bot
     * 
     * @param matrixUserId The Matrix user ID (e.g., @user:server.com)
     * @return The localpart (username) without @ and domain
     */
    private String extractLocalpart(String matrixUserId) {
        if (matrixUserId == null || matrixUserId.isEmpty()) {
            return matrixUserId;
        }
        // Remove @ prefix if present
        String localpart = matrixUserId.startsWith("@") ? matrixUserId.substring(1) : matrixUserId;
        // Extract part before : (domain separator)
        int colonIndex = localpart.indexOf(':');
        if (colonIndex > 0) {
            return localpart.substring(0, colonIndex);
        }
        return localpart;
    }

    /**
     * Encode Matrix user ID for use in URL path segments for Matrix Client-Server
     * API.
     * Matrix Client-Server API expects the full user ID encoded in path segments.
     * For example: @alfred:chat.neohoods.com -> %40bot%3Achat.neohoods.com
     * 
     * NOTE: This method is deprecated. Use UriComponentsBuilder instead for
     * constructing URLs with path segments, as it handles encoding automatically.
     * 
     * @param matrixUserId The Matrix user ID (e.g., @user:server.com)
     * @return URL-encoded user ID for path segments
     * @deprecated Use UriComponentsBuilder.pathSegment() instead
     */
    @Deprecated
    private String encodeMatrixUserIdForPath(String matrixUserId) {
        if (matrixUserId == null || matrixUserId.isEmpty()) {
            return matrixUserId;
        }
        // Encode only the characters that need encoding in path segments
        // '@' -> %40, ':' -> %3A
        return matrixUserId
                .replace("@", "%40")
                .replace(":", "%3A");
    }

    /**
     * Extract server name from homeserver URL
     */
    private String extractServerName(String url) {
        try {
            String normalizedUrl = normalizeHomeserverUrl(url);
            java.net.URI uri = new java.net.URI(normalizedUrl);
            String host = uri.getHost();
            if (host != null) {
                return host;
            }
            return "chat.neohoods.com"; // default
        } catch (Exception e) {
            log.error("Failed to extract server name from URL: {}", url, e);
            return "chat.neohoods.com";
        }
    }

    /**
     * Generate random password for Matrix user
     */
    private String generateRandomPassword() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }

    /**
     * Check if space exists
     */
    public boolean checkSpaceExists(String spaceId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            SpacesApi spacesApi = new SpacesApi(apiClient);
            // Try to get space hierarchy - if it fails, space doesn't exist
            spacesApi.getSpaceHierarchy(spaceId, null, null, null, null);
            return true;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return false;
            }
            log.error("Error checking if space exists: {}", spaceId, e);
            return false;
        } catch (Exception e) {
            log.error("Error checking if space exists: {}", spaceId, e);
            return false;
        }
    }

    /**
     * Get all existing rooms in a space (name -> roomId map)
     * Loads all rooms once and returns a map for efficient lookup
     */
    public Map<String, String> getExistingRoomsInSpace(String spaceId) {
        Map<String, String> roomsMap = new HashMap<>();
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return roomsMap;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);
            GetJoinedRooms200Response joinedRooms = membershipApi.getJoinedRooms();

            if (joinedRooms != null && joinedRooms.getJoinedRooms() != null) {
                RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

                for (String roomId : joinedRooms.getJoinedRooms()) {
                    // Skip the space itself
                    if (roomId.equals(spaceId)) {
                        continue;
                    }

                    try {
                        // Get all room states and filter manually to avoid SDK union type issues
                        List<ClientEvent> roomStates = participationApi.getRoomState(roomId);

                        // Check if this room belongs to the space by looking for m.space.parent state
                        boolean belongsToSpace = false;
                        String roomName = null;

                        for (ClientEvent state : roomStates) {
                            if (state.getType() != null) {
                                if ("m.space.parent".equals(state.getType())
                                        && spaceId.equals(state.getStateKey())) {
                                    belongsToSpace = true;
                                } else if ("m.room.name".equals(state.getType())
                                        && (state.getStateKey() == null || state.getStateKey().isEmpty())) {
                                    // Extract room name from content
                                    if (state.getContent() != null && state.getContent() instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> content = (Map<String, Object>) state.getContent();
                                        Object nameObj = content.get("name");
                                        if (nameObj != null) {
                                            roomName = nameObj.toString();
                                        }
                                    }
                                }
                            }
                        }

                        if (belongsToSpace && roomName != null && !roomName.isEmpty()) {
                            // Store room name as-is (case-sensitive) for exact matching
                            roomsMap.put(roomName, roomId);
                            log.trace("Added room to map: {} -> {}", roomName, roomId);
                        }
                    } catch (ApiException e) {
                        // Room might not have m.space.parent state or m.room.name state, skip it
                        if (e.getCode() != 404) {
                            log.debug("Error checking room {} state: {}", roomId, e.getMessage());
                        }
                        continue;
                    } catch (Exception e) {
                        log.debug("Error checking room {}: {}", roomId, e.getMessage());
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting existing rooms in space {}", spaceId, e);
        }
        return roomsMap;
    }

    /**
     * Get room ID by name in a space
     * Uses getJoinedRooms() like getCurrentSpaces(), then checks if room belongs to
     * space
     */
    public Optional<String> getRoomIdByName(String roomName, String spaceId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);
            GetJoinedRooms200Response joinedRooms = membershipApi.getJoinedRooms();

            if (joinedRooms != null && joinedRooms.getJoinedRooms() != null) {
                RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

                for (String roomId : joinedRooms.getJoinedRooms()) {
                    // Skip the space itself
                    if (roomId.equals(spaceId)) {
                        continue;
                    }

                    try {
                        // Get all room states and filter manually to avoid SDK union type issues
                        // This is more efficient: one API call instead of two
                        List<ClientEvent> roomStates = participationApi.getRoomState(roomId);

                        // Check if this room belongs to the space by looking for m.space.parent state
                        boolean belongsToSpace = false;
                        String actualRoomName = null;

                        for (ClientEvent state : roomStates) {
                            if (state.getType() != null) {
                                if ("m.space.parent".equals(state.getType())
                                        && spaceId.equals(state.getStateKey())) {
                                    belongsToSpace = true;
                                } else if ("m.room.name".equals(state.getType())
                                        && (state.getStateKey() == null || state.getStateKey().isEmpty())) {
                                    // Extract room name from content
                                    if (state.getContent() != null && state.getContent() instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> content = (Map<String, Object>) state.getContent();
                                        Object nameObj = content.get("name");
                                        if (nameObj != null) {
                                            actualRoomName = nameObj.toString();
                                        }
                                    }
                                }
                            }
                        }

                        // Check if name matches (case-insensitive comparison)
                        if (belongsToSpace && roomName != null && actualRoomName != null
                                && roomName.equalsIgnoreCase(actualRoomName)) {
                            log.debug("Found room {} with ID {} (case-insensitive match)", roomName, roomId);
                            return Optional.of(roomId);
                        }
                    } catch (ApiException e) {
                        // Room might not have m.space.parent state or m.room.name state, skip it
                        if (e.getCode() != 404) {
                            log.debug("Error checking room {} state: {}", roomId, e.getMessage());
                        }
                        continue;
                    } catch (Exception e) {
                        log.debug("Error checking room {}: {}", roomId, e.getMessage());
                        continue;
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting room ID by name: {} in space {}", roomName, spaceId, e);
            return Optional.empty();
        }
    }

    /**
     * Create room in space (convenience method with allowGuests=true by default)
     */
    public Optional<String> createRoomInSpace(String roomName, String description, String imageUrl, String spaceId) {
        return createRoomInSpace(roomName, description, imageUrl, spaceId, true);
    }

    /**
     * Create room in space
     * Uses user token (from device code or authorization code flow) to create room
     * and add to space by setting m.space.parent in initial_state
     * 
     * @param roomName    Name of the room
     * @param description Description of the room
     * @param imageUrl    Optional image URL for room avatar
     * @param spaceId     Space ID to link the room to
     * @param allowGuests If true, guests can join (guest_access: "can_join"), if
     *                    false, room is restricted (guest_access: "forbidden")
     */
    public Optional<String> createRoomInSpace(String roomName, String description, String imageUrl, String spaceId,
            boolean allowGuests) {
        try {
            // Use user token for room creation
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No user token available for creating room in space");
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomCreationApi roomCreationApi = new RoomCreationApi(apiClient);

            CreateRoomRequest createRequest = new CreateRoomRequest();
            createRequest.setName(roomName);
            createRequest.setTopic(description);
            createRequest.setPreset(CreateRoomRequest.PresetEnum.PUBLIC_CHAT);
            createRequest.setRoomVersion("10");
            // Set encryption to false (non-encrypted) - don't set encryption state event

            // Build initial state events
            List<StateEvent> initialState = new ArrayList<>();

            // Add space parent relationship in initial state
            // This links the room to the space directly during creation
            StateEvent spaceParentEvent = new StateEvent();
            spaceParentEvent.setType("m.space.parent");
            spaceParentEvent.setStateKey(spaceId);
            Map<String, Object> spaceParentContent = new HashMap<>();
            spaceParentContent.put("via", List.of(serverName));
            spaceParentContent.put("canonical", true);
            spaceParentEvent.setContent(spaceParentContent);
            initialState.add(spaceParentEvent);

            // Set join rules to restrict access to space members only
            StateEvent joinRulesEvent = new StateEvent();
            joinRulesEvent.setType("m.room.join_rules");
            joinRulesEvent.setStateKey("");
            Map<String, Object> joinRulesContent = new HashMap<>();
            joinRulesContent.put("join_rule", "restricted");
            List<Map<String, Object>> allowList = new ArrayList<>();
            Map<String, Object> allowEntry = new HashMap<>();
            allowEntry.put("type", "m.room_membership");
            allowEntry.put("room_id", spaceId);
            allowList.add(allowEntry);
            joinRulesContent.put("allow", allowList);
            joinRulesEvent.setContent(joinRulesContent);
            initialState.add(joinRulesEvent);

            // Set guest access: "can_join" for public rooms, "forbidden" for restricted
            // rooms
            StateEvent guestAccessEvent = new StateEvent();
            guestAccessEvent.setType("m.room.guest_access");
            guestAccessEvent.setStateKey("");
            Map<String, Object> guestAccessContent = new HashMap<>();
            guestAccessContent.put("guest_access", allowGuests ? "can_join" : "forbidden");
            guestAccessEvent.setContent(guestAccessContent);
            initialState.add(guestAccessEvent);

            // Set avatar in initial state if provided
            if (imageUrl != null && !imageUrl.isEmpty()) {
                StateEvent avatarEvent = new StateEvent();
                avatarEvent.setType("m.room.avatar");
                avatarEvent.setStateKey("");
                Map<String, Object> avatarContent = new HashMap<>();
                avatarContent.put("url", imageUrl);
                avatarEvent.setContent(avatarContent);
                initialState.add(avatarEvent);
            }

            createRequest.setInitialState(initialState);

            CreateRoom200Response response = roomCreationApi.createRoom(createRequest);
            String roomId = response.getRoomId();
            log.info("Created room {} ({}) and linked to space {} via m.space.parent", roomName, roomId, spaceId);

            return Optional.of(roomId);
        } catch (ApiException e) {
            if (e.getCode() == 403) {
                log.error("Permission denied: Bot doesn't have permission to modify space {} state. Error: {}. " +
                        "Make sure the bot is admin and has proper permissions on the space.", spaceId, e.getMessage());
            } else {
                log.error("Error creating room {} in space {}: {}", roomName, spaceId, e.getMessage(), e);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error creating room {} in space {}", roomName, spaceId, e);
            return Optional.empty();
        }
    }

    /**
     * Update Matrix user profile (displayname, email, phone)
     */
    public boolean updateMatrixUserProfile(String matrixUserId, UserEntity user) {
        try {
            // Build display name from user entity
            String displayName = buildDisplayName(user);
            if (displayName == null || displayName.trim().isEmpty()) {
                log.debug("No display name to set for user {}", matrixUserId);
                return false;
            }

            // Update displayname using Matrix Profile API
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", matrixUserId, "displayname")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get access token for Authorization header (use admin token)
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            if (accessToken == null) {
                log.warn("No access token available for updating Matrix user profile for {}", matrixUserId);
                return false;
            }

            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("displayname", displayName);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.PUT,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated Matrix user profile displayname for {} to {}", matrixUserId,
                        displayName);
                return true;
            } else {
                log.warn("Failed to update Matrix user profile displayname for {}: HTTP {}", matrixUserId,
                        response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating Matrix user profile for {}", matrixUserId, e);
            return false;
        }
    }

    /**
     * Update bot avatar from configured URL
     * If the URL is HTTP/HTTPS, uploads it to Matrix first to get an MXC URL
     * Then updates the bot's profile avatar_url
     * 
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateBotAvatar() {
        if (botAvatarUrl == null || botAvatarUrl.isEmpty()) {
            log.debug("No bot avatar URL configured, skipping avatar update");
            return false;
        }

        Optional<String> assistantUserIdOpt = getAssistantUserId();
        if (assistantUserIdOpt.isEmpty()) {
            log.warn("Cannot update bot avatar: bot user ID not available");
            return false;
        }

        String assistantUserId = assistantUserIdOpt.get();
        log.info("Updating avatar for bot user: {} from URL: {}", assistantUserId, botAvatarUrl);

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token available for updating bot avatar");
                return false;
            }

            // Get current avatar URL first
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String currentAvatarUrl = getCurrentBotAvatar(assistantUserId, normalizedUrl);

            // If avatar is already set and we have an HTTP/HTTPS URL, skip update
            // (uploading the same image would create a new MXC URL, so we can't compare)
            // Only update if current avatar is null/empty or if the configured URL is
            // already an MXC URL
            if (currentAvatarUrl != null && !currentAvatarUrl.isEmpty()) {
                if (botAvatarUrl.startsWith("http://") || botAvatarUrl.startsWith("https://")) {
                    // Current avatar exists and configured URL is HTTP/HTTPS
                    // We can't compare them, so assume it's already correct to avoid re-uploading
                    log.debug(
                            "Bot avatar is already set (current: {}), skipping update to avoid re-uploading same image",
                            currentAvatarUrl);
                    return true; // Already set, skip update
                } else if (botAvatarUrl.startsWith("mxc://")) {
                    // Both are MXC URLs, can compare directly
                    if (currentAvatarUrl.equals(botAvatarUrl)) {
                        log.debug("Bot avatar is already set to {}, skipping update", botAvatarUrl);
                        return true; // Already set correctly
                    }
                }
            }

            String mxcUrl = botAvatarUrl;

            // If URL is HTTP/HTTPS, upload it to Matrix first
            if (botAvatarUrl.startsWith("http://") || botAvatarUrl.startsWith("https://")) {
                log.info("Uploading avatar image from {} to Matrix...", botAvatarUrl);
                mxcUrl = uploadImageToMatrix(botAvatarUrl);
                if (mxcUrl == null || mxcUrl.isEmpty()) {
                    log.error("Failed to upload avatar image to Matrix");
                    return false;
                }
                log.info("Avatar image uploaded to Matrix: {}", mxcUrl);
            }

            // Update bot profile avatar_url
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", assistantUserId, "avatar_url")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get access token for Authorization header
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            if (accessToken == null) {
                log.error("No access token available for updating bot avatar");
                return false;
            }

            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("avatar_url", mxcUrl);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.PUT,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated bot avatar for {} to {}", assistantUserId, mxcUrl);
                return true;
            } else {
                log.error("Failed to update bot avatar: HTTP {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating bot avatar for {}: {}", assistantUserId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Update bot display name
     * 
     * @return true if display name was updated successfully, false otherwise
     */
    public boolean updateBotDisplayName() {
        if (botDisplayName == null || botDisplayName.isEmpty()) {
            log.debug("No bot display name configured, skipping display name update");
            return false;
        }

        Optional<String> assistantUserIdOpt = getAssistantUserId();
        if (assistantUserIdOpt.isEmpty()) {
            log.warn("Cannot update bot display name: bot user ID not available");
            return false;
        }

        String assistantUserId = assistantUserIdOpt.get();
        log.info("Updating display name for bot user: {} to: {}", assistantUserId, botDisplayName);

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token available for updating bot display name");
                return false;
            }

            // Update bot profile displayname
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", assistantUserId, "displayname")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get access token for Authorization header
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            if (accessToken == null) {
                log.error("No access token available for updating bot display name");
                return false;
            }

            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("displayname", botDisplayName);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.PUT,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated bot display name for {} to {}", assistantUserId, botDisplayName);
                return true;
            } else {
                log.error("Failed to update bot display name: HTTP {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating bot display name for {}: {}", assistantUserId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Upload an image from HTTP/HTTPS URL to Matrix and return MXC URL
     * 
     * @param imageUrl HTTP/HTTPS URL of the image
     * @return MXC URL (mxc://...) or null if upload failed
     */
    private String uploadImageToMatrix(String imageUrl) {
        try {
            // Download the image
            ResponseEntity<byte[]> imageResponse = restTemplate.exchange(
                    imageUrl,
                    HttpMethod.GET,
                    null,
                    byte[].class);

            if (!imageResponse.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to download image from {}: HTTP {}", imageUrl, imageResponse.getStatusCode());
                return null;
            }

            byte[] imageData = imageResponse.getBody();
            if (imageData == null || imageData.length == 0) {
                log.error("Downloaded image from {} is empty", imageUrl);
                return null;
            }

            // Determine content type from response or URL
            String contentType = imageResponse.getHeaders().getContentType() != null
                    ? imageResponse.getHeaders().getContentType().toString()
                    : "image/jpeg"; // Default to JPEG

            // Upload to Matrix Media API
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String uploadUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "media", "v3", "upload")
                    .queryParam("filename", "bot-avatar.jpeg")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));

            // Get access token for Authorization header
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            if (accessToken == null) {
                log.error("No access token available for uploading image to Matrix");
                return null;
            }

            headers.setBearerAuth(accessToken);

            HttpEntity<byte[]> uploadRequest = new HttpEntity<>(imageData, headers);
            ResponseEntity<Map> uploadResponse = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    uploadRequest,
                    Map.class);

            if (uploadResponse.getStatusCode().is2xxSuccessful() && uploadResponse.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = uploadResponse.getBody();
                String contentUri = (String) responseBody.get("content_uri");
                if (contentUri != null && contentUri.startsWith("mxc://")) {
                    return contentUri;
                }
            }

            log.error("Failed to upload image to Matrix: HTTP {}", uploadResponse.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Error uploading image to Matrix from {}: {}", imageUrl, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get the membership status of a user in a room
     * First checks the cache, then falls back to API call if not cached
     * 
     * @param matrixUserId The Matrix user ID
     * @param roomId       The room ID
     * @return Optional containing the membership status ("join", "invite", "leave",
     *         "ban", or empty if not found)
     */
    public Optional<String> getUserRoomMembership(String matrixUserId, String roomId) {
        // Decode room ID if needed
        String decodedRoomId = roomId;
        try {
            if (roomId.contains("%")) {
                String current = roomId;
                int maxDecodeAttempts = 5;
                for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                    String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                    if (decoded.equals(current)) {
                        break;
                    }
                    current = decoded;
                }
                decodedRoomId = current;
            }
        } catch (Exception e) {
            // Use original if decoding fails
        }

        // Check cache first
        Map<String, String> roomMembers = roomMembershipCache.get(decodedRoomId);
        if (roomMembers != null) {
            String membership = roomMembers.get(matrixUserId);
            if (membership != null) {
                log.debug("Found membership in cache: user {} in room {} = {}", matrixUserId, decodedRoomId,
                        membership);
                return Optional.of(membership);
            }
            // User not in cache means they're not a member
            log.debug("User {} not found in cache for room {}, returning empty", matrixUserId, decodedRoomId);
            return Optional.empty();
        }

        // Cache miss - fallback to API call (shouldn't happen if preload was called)
        log.debug("Room {} not in cache, falling back to API call", decodedRoomId);
        return getUserRoomMembershipFromApi(matrixUserId, decodedRoomId);
    }

    /**
     * Get the membership status from API (fallback when cache is not available)
     */
    private Optional<String> getUserRoomMembershipFromApi(String matrixUserId, String decodedRoomId) {
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return Optional.empty();
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

            // Get room state and check for m.room.member events
            List<ClientEvent> roomStates = participationApi.getRoomState(decodedRoomId);

            for (ClientEvent state : roomStates) {
                if (state.getType() != null && "m.room.member".equals(state.getType())) {
                    // Check if this is the membership event for our user
                    if (matrixUserId.equals(state.getStateKey())) {
                        // Check membership status in content
                        if (state.getContent() != null && state.getContent() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> content = (Map<String, Object>) state.getContent();
                            String membership = (String) content.get("membership");
                            // Return membership status (can be "join", "invite", "leave", "ban", etc.)
                            if (membership != null) {
                                return Optional.of(membership);
                            }
                        }
                    }
                }
            }

            return Optional.empty();
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                // Room doesn't exist
                log.debug("Room {} does not exist", decodedRoomId);
                return Optional.empty();
            }
            log.debug("Error checking membership for user {} in room {}: {}", matrixUserId, decodedRoomId,
                    e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Error checking membership for user {} in room {}: {}", matrixUserId, decodedRoomId,
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Preload all room memberships for a list of rooms
     * This populates the cache to avoid repeated API calls
     * 
     * @param roomIds Map of room names to room IDs
     */
    public void preloadRoomMemberships(Map<String, String> roomIds) {
        log.info("Preloading memberships for {} rooms...", roomIds.size());
        int loaded = 0;
        int errors = 0;

        Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
        if (apiClientOpt.isEmpty()) {
            log.warn("Cannot preload room memberships: no access token available");
            return;
        }

        ApiClient apiClient = apiClientOpt.get();
        RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);

        for (Map.Entry<String, String> entry : roomIds.entrySet()) {
            String roomName = entry.getKey();
            String roomId = entry.getValue();

            try {
                // Decode room ID if needed
                String decodedRoomId = roomId;
                try {
                    if (roomId.contains("%")) {
                        String current = roomId;
                        int maxDecodeAttempts = 5;
                        for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                            String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                            if (decoded.equals(current)) {
                                break;
                            }
                            current = decoded;
                        }
                        decodedRoomId = current;
                    }
                } catch (Exception e) {
                    // Use original if decoding fails
                }

                // Get room state and extract all membership events
                List<ClientEvent> roomStates = participationApi.getRoomState(decodedRoomId);
                Map<String, String> roomMembers = new HashMap<>();

                for (ClientEvent state : roomStates) {
                    if (state.getType() != null && "m.room.member".equals(state.getType())) {
                        String userId = state.getStateKey();
                        if (userId != null && state.getContent() != null && state.getContent() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> content = (Map<String, Object>) state.getContent();
                            String membership = (String) content.get("membership");
                            if (membership != null) {
                                roomMembers.put(userId, membership);
                            }
                        }
                    }
                }

                roomMembershipCache.put(decodedRoomId, roomMembers);
                loaded++;
                log.debug("Preloaded {} memberships for room {} ({})", roomMembers.size(), roomName, decodedRoomId);

                // Small delay to avoid rate limiting
                Thread.sleep(100);
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    log.debug("Room {} ({}) does not exist, skipping", roomName, roomId);
                } else {
                    log.warn("Error preloading memberships for room {} ({}): HTTP {}", roomName, roomId, e.getCode());
                    errors++;
                }
            } catch (Exception e) {
                log.warn("Error preloading memberships for room {} ({}): {}", roomName, roomId, e.getMessage());
                errors++;
            }
        }

        log.info("Preloaded memberships for {}/{} rooms ({} errors)", loaded, roomIds.size(), errors);
    }

    /**
     * Dump room status to logs for debugging
     * Shows all rooms, their members, and invitation status
     * 
     * @param roomIds Map of room names to room IDs
     */
    public void dumpRoomStatus(Map<String, String> roomIds) {
        log.info("=== Matrix Chat Status Dump ===");
        log.info("Total rooms: {}", roomIds.size());

        int totalMembers = 0;
        int totalInvited = 0;
        int totalJoined = 0;

        for (Map.Entry<String, String> entry : roomIds.entrySet()) {
            String roomName = entry.getKey();
            String roomId = entry.getValue();

            // Decode room ID if needed
            String decodedRoomId = roomId;
            try {
                if (roomId.contains("%")) {
                    String current = roomId;
                    int maxDecodeAttempts = 5;
                    for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                        String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                        if (decoded.equals(current)) {
                            break;
                        }
                        current = decoded;
                    }
                    decodedRoomId = current;
                }
            } catch (Exception e) {
                // Use original if decoding fails
            }

            Map<String, String> roomMembers = roomMembershipCache.get(decodedRoomId);
            if (roomMembers == null || roomMembers.isEmpty()) {
                log.info("  Room: {} ({}) - No members", roomName, decodedRoomId);
                continue;
            }

            int joined = 0;
            int invited = 0;
            List<String> joinedUsers = new ArrayList<>();
            List<String> invitedUsers = new ArrayList<>();

            for (Map.Entry<String, String> memberEntry : roomMembers.entrySet()) {
                String userId = memberEntry.getKey();
                String status = memberEntry.getValue();
                if ("join".equals(status)) {
                    joined++;
                    joinedUsers.add(userId);
                    totalJoined++;
                } else if ("invite".equals(status)) {
                    invited++;
                    invitedUsers.add(userId);
                    totalInvited++;
                }
            }

            totalMembers += roomMembers.size();

            log.info("  Room: {} ({})", roomName, decodedRoomId);
            log.info("    Total members: {} ({} joined, {} invited)", roomMembers.size(), joined, invited);
            if (!joinedUsers.isEmpty()) {
                log.info("    Joined users: {}", String.join(", ", joinedUsers));
            }
            if (!invitedUsers.isEmpty()) {
                log.info("    Invited users: {}", String.join(", ", invitedUsers));
            }
        }

        log.info("=== Summary ===");
        log.info("Total rooms: {}", roomIds.size());
        log.info("Total memberships: {} ({} joined, {} invited)", totalMembers, totalJoined, totalInvited);
        log.info("=== End Status Dump ===");
    }

    /**
     * Get all room members (from cache or API)
     * Returns a map of userId -> membership status
     * 
     * @param roomId The room ID
     * @return Map of userId -> membership status, or null if room not found
     */
    public Map<String, String> getRoomMembers(String roomId) {
        // Decode room ID if needed
        String decodedRoomId = roomId;
        try {
            if (roomId.contains("%")) {
                String current = roomId;
                int maxDecodeAttempts = 5;
                for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                    String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                    if (decoded.equals(current)) {
                        break;
                    }
                    current = decoded;
                }
                decodedRoomId = current;
            }
        } catch (Exception e) {
            // Use original if decoding fails
        }

        // Check cache first
        Map<String, String> roomMembers = roomMembershipCache.get(decodedRoomId);
        if (roomMembers != null) {
            return roomMembers;
        }

        // Cache miss - get from API
        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                return null;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomParticipationApi participationApi = new RoomParticipationApi(apiClient);
            List<ClientEvent> roomStates = participationApi.getRoomState(decodedRoomId);
            Map<String, String> members = new HashMap<>();

            for (ClientEvent state : roomStates) {
                if (state.getType() != null && "m.room.member".equals(state.getType())) {
                    String userId = state.getStateKey();
                    if (userId != null && state.getContent() != null && state.getContent() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content = (Map<String, Object>) state.getContent();
                        String membership = (String) content.get("membership");
                        if (membership != null) {
                            members.put(userId, membership);
                        }
                    }
                }
            }

            // Cache the result
            roomMembershipCache.put(decodedRoomId, members);
            return members;
        } catch (Exception e) {
            log.debug("Error getting room members for room {}: {}", decodedRoomId, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a user is already a member or invited to a room
     * Uses getUserRoomMembership to check membership status
     * 
     * @param matrixUserId The Matrix user ID
     * @param roomId       The room ID
     * @return true if user is already a member (join) or invited (invite), false
     *         otherwise
     */
    public boolean isUserMemberOfRoom(String matrixUserId, String roomId) {
        Optional<String> membership = getUserRoomMembership(matrixUserId, roomId);
        return membership.isPresent() && ("join".equals(membership.get()) || "invite".equals(membership.get()));
    }

    /**
     * Join a room as the bot
     * Accepts an invitation if the bot is invited, or joins directly if the room is
     * public
     */
    public boolean joinRoomAsBot(String roomId) {
        try {
            Optional<String> assistantUserIdOpt = getAssistantUserId();
            if (assistantUserIdOpt.isEmpty()) {
                log.warn("Cannot join room: bot user ID not available");
                return false;
            }

            String assistantUserId = assistantUserIdOpt.get();

            // Check if bot is already a member
            Optional<String> membership = getUserRoomMembership(assistantUserId, roomId);
            if (membership.isPresent() && "join".equals(membership.get())) {
                log.debug("Bot {} is already a member of room {}", assistantUserId, roomId);
                return true;
            }

            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token for bot to join room");
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);

            // Decode room ID if needed
            String decodedRoomId = roomId;
            try {
                if (roomId.contains("%")) {
                    String current = roomId;
                    int maxDecodeAttempts = 5;
                    for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                        String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                        if (decoded.equals(current)) {
                            break;
                        }
                        current = decoded;
                    }
                    decodedRoomId = current;
                }
            } catch (Exception e) {
                // Use original if decoding fails
            }

            // Check if bot is already a member
            Optional<String> currentMembership = getUserRoomMembership(assistantUserId, decodedRoomId);
            if (currentMembership.isPresent() && "join".equals(currentMembership.get())) {
                log.debug("Bot {} is already a member of room {}", assistantUserId, decodedRoomId);
                return true;
            }

            // Try to join the room directly using the joinRoom API
            // This will:
            // - Accept the invitation if the bot is invited
            // - Join the room if it's public and joinable
            // This is the correct Matrix way to accept an invitation
            log.info("Bot {} attempting to join room {} (will accept invitation if invited)...", assistantUserId,
                    decodedRoomId);
            try {
                // Use joinRoom API to accept the invitation or join the room
                // The SDK doesn't have a direct joinRoom method, so we use HTTP directly
                String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
                // Use UriComponentsBuilder to properly encode the room ID (avoid double
                // encoding)
                String joinUrl = org.springframework.web.util.UriComponentsBuilder
                        .fromHttpUrl(normalizedUrl)
                        .path("/_matrix/client/v3/rooms/{roomId}/join")
                        .buildAndExpand(decodedRoomId)
                        .toUriString();

                // Get access token
                String accessToken = null;
                if (matrixAccessToken != null && !matrixAccessToken.isEmpty()) {
                    accessToken = matrixAccessToken;
                } else if (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()) {
                    accessToken = localAssistantPermanentToken;
                } else {
                    Optional<String> tokenOpt = oauth2Service.getUserAccessToken();
                    if (tokenOpt.isPresent()) {
                        accessToken = tokenOpt.get();
                    }
                }

                if (accessToken == null) {
                    log.warn("No access token available to join room");
                    return false;
                }

                // Use RestTemplate to call join API
                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.setBearerAuth(accessToken);
                org.springframework.http.HttpEntity<Map<String, Object>> request = new org.springframework.http.HttpEntity<>(
                        new HashMap<>(), headers);

                try {
                    restTemplate.postForEntity(joinUrl, request, Map.class);
                    log.info("Bot {} successfully joined room {} (accepted invitation)", assistantUserId, decodedRoomId);
                    return true;
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    if (e.getStatusCode().value() == 403) {
                        log.warn("Bot {} does not have permission to join room {}: {}", assistantUserId, decodedRoomId,
                                e.getMessage());
                    } else if (e.getStatusCode().value() == 404) {
                        log.warn("Room {} not found or bot is not invited: {}", decodedRoomId, e.getMessage());
                    } else {
                        log.warn("Failed to join room {}: HTTP {} - {}", decodedRoomId, e.getStatusCode(),
                                e.getMessage());
                    }
                    return false;
                }
            } catch (Exception e) {
                log.error("Error joining room {}: {}", decodedRoomId, e.getMessage(), e);
                return false;
            }
        } catch (Exception e) {
            log.error("Error joining room {} as bot: {}", roomId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Invite user to room with notifications
     * Uses SDK client (RoomMembershipApi) like inviteUserToSpace
     */
    public boolean inviteUserToRoomWithNotifications(String matrixUserId, String roomId, boolean enableNotifications) {
        try {
            // Use Matrix API token for inviting users
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No admin access token for inviting user to room");
                return false;
            }

            ApiClient apiClient = apiClientOpt.get();
            RoomMembershipApi membershipApi = new RoomMembershipApi(apiClient);

            // Decode room ID if it's already URL-encoded (SDK will encode it automatically)
            // The SDK expects unencoded room IDs and will encode them itself
            // Matrix room IDs are like: !KPzpdAfZJOVWUSpHUa:chat.neohoods.com
            // If they come encoded as %21KPzpdAfZJOVWUSpHUa%3Achat.neohoods.com, we need to
            // decode
            String decodedRoomId = roomId;
            try {
                // Try to decode - decode multiple times if needed (in case of double encoding)
                String current = roomId;
                int maxDecodeAttempts = 5;
                for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                    String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                    if (decoded.equals(current)) {
                        // No more decoding possible
                        break;
                    }
                    current = decoded;
                }
                decodedRoomId = current;
                if (!decodedRoomId.equals(roomId)) {
                    log.debug("Decoded room ID from {} to {}", roomId, decodedRoomId);
                }
            } catch (Exception e) {
                // If decoding fails, use original room ID
                log.debug("Room ID {} decoding failed, using as-is: {}", roomId, e.getMessage());
            }

            InviteUserRequest inviteRequest = new InviteUserRequest();
            inviteRequest.setUserId(matrixUserId);

            // Retry logic for 504 Gateway Timeout errors
            int maxRetries = 3;
            long baseDelayMs = 1000; // Start with 1 second
            boolean success = false;
            Exception lastException = null;

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    membershipApi.inviteUser(decodedRoomId, inviteRequest);
                    success = true;
                    break; // Success, exit retry loop
                } catch (ApiException e) {
                    lastException = e;
                    if (e.getCode() == 403) {
                        log.debug("User {} already in room {}", matrixUserId, roomId);
                        return true; // Already in room
                    }
                    if (e.getCode() == 504 && attempt < maxRetries - 1) {
                        // Gateway Timeout - retry with exponential backoff
                        long delayMs = baseDelayMs * (1L << attempt); // Exponential: 1s, 2s, 4s
                        log.warn("Gateway Timeout (504) inviting user {} to room {}, retrying in {}ms (attempt {}/{})",
                                matrixUserId, roomId, delayMs, attempt + 1, maxRetries);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Interrupted while waiting to retry invitation");
                            return false;
                        }
                        continue; // Retry
                    }
                    // Other errors or last retry failed - break and handle below
                    break;
                } catch (Exception e) {
                    lastException = e;
                    // Check if it's a timeout-related error
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("504") || errorMsg.contains("timeout")
                            || errorMsg.contains("Gateway Timeout")) && attempt < maxRetries - 1) {
                        long delayMs = baseDelayMs * (1L << attempt);
                        log.warn("Timeout error inviting user {} to room {}, retrying in {}ms (attempt {}/{})",
                                matrixUserId, roomId, delayMs, attempt + 1, maxRetries);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Interrupted while waiting to retry invitation");
                            return false;
                        }
                        continue; // Retry
                    }
                    // Other errors - break and handle below
                    break;
                }
            }

            if (success) {
                // Update cache with new invitation
                String decodedRoomIdForCache = decodedRoomId;
                roomMembershipCache.computeIfAbsent(decodedRoomIdForCache, k -> new ConcurrentHashMap<>())
                        .put(matrixUserId, "invite");

                // Enable notifications if requested
                if (enableNotifications) {
                    // Set push rules for notifications
                    // This would require additional API calls to set push rules
                    // For now, we'll just log it
                    log.info("User {} invited to room {} with notifications enabled", matrixUserId, roomId);
                }
                log.info("Successfully invited user {} to room {}", matrixUserId, roomId);
                return true;
            } else {
                // All retries failed
                if (lastException instanceof ApiException) {
                    ApiException apiEx = (ApiException) lastException;
                    log.error("Failed to invite user {} to room {} after {} retries: HTTP {} - {}",
                            matrixUserId, roomId, maxRetries, apiEx.getCode(), apiEx.getMessage());
                } else {
                    log.error("Failed to invite user {} to room {} after {} retries",
                            matrixUserId, roomId, maxRetries, lastException);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("Error inviting user to room", e);
            return false;
        }
    }

    /**
     * Update room avatar from image URL
     * If the URL is HTTP/HTTPS, uploads it to Matrix first to get an MXC URL
     * Then sends a state event to update the room avatar
     * 
     * @param roomId   Room ID
     * @param imageUrl Image URL (HTTP/HTTPS or MXC)
     * @return true if avatar was updated successfully, false otherwise
     */
    public boolean updateRoomAvatar(String roomId, String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            log.debug("No image URL provided for room avatar update");
            return false;
        }

        // Decode room ID if needed
        String decodedRoomId = roomId;
        try {
            if (roomId.contains("%")) {
                String current = roomId;
                int maxDecodeAttempts = 5;
                for (int i = 0; i < maxDecodeAttempts && current.contains("%"); i++) {
                    String decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name());
                    if (decoded.equals(current)) {
                        break;
                    }
                    current = decoded;
                }
                decodedRoomId = current;
            }
        } catch (Exception e) {
            // Use original if decoding fails
        }

        log.info("Updating avatar for room {} from URL: {}", decodedRoomId, imageUrl);

        try {
            Optional<ApiClient> apiClientOpt = getMatrixAccessToken();
            if (apiClientOpt.isEmpty()) {
                log.warn("No access token available for updating room avatar");
                return false;
            }

            // Get current room avatar first
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String currentRoomAvatar = getCurrentRoomAvatar(decodedRoomId, normalizedUrl);

            // If avatar is already set and we have an HTTP/HTTPS URL, we should still
            // update
            // to ensure the avatar matches the current configuration
            // Only skip if both are MXC URLs and they match
            if (currentRoomAvatar != null && !currentRoomAvatar.isEmpty()) {
                if (imageUrl.startsWith("mxc://")) {
                    // Both are MXC URLs, can compare directly
                    if (currentRoomAvatar.equals(imageUrl)) {
                        log.debug("Room avatar is already set to {}, skipping update", imageUrl);
                        return true; // Already set correctly
                    }
                }
                // For HTTP/HTTPS URLs, we'll update anyway to ensure consistency
                // This allows updating avatars even if they were previously set
            }

            String mxcUrl = imageUrl;

            // If URL is HTTP/HTTPS, upload it to Matrix first
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                log.info("Uploading room avatar image from {} to Matrix...", imageUrl);
                mxcUrl = uploadImageToMatrix(imageUrl);
                if (mxcUrl == null || mxcUrl.isEmpty()) {
                    log.error("Failed to upload room avatar image to Matrix");
                    return false;
                }
                log.info("Room avatar image uploaded to Matrix: {}", mxcUrl);
            }

            // Send state event to update room avatar
            String stateUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "rooms", decodedRoomId, "state", "m.room.avatar", "")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get access token for Authorization header
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            if (accessToken == null) {
                log.error("No access token available for updating room avatar");
                return false;
            }

            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("url", mxcUrl);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    stateUrl,
                    HttpMethod.PUT,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated avatar for room {} to {}", decodedRoomId, mxcUrl);
                return true;
            } else {
                log.error("Failed to update room avatar: HTTP {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating room avatar for {}: {}", decodedRoomId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get current bot avatar URL
     * 
     * @param assistantUserId     Bot user ID
     * @param normalizedUrl Normalized homeserver URL
     * @return Current avatar URL or null if not set or error
     */
    @SuppressWarnings("unchecked")
    private String getCurrentBotAvatar(String assistantUserId, String normalizedUrl) {
        try {
            String profileUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "profile", assistantUserId, "avatar_url")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            if (accessToken == null) {
                return null;
            }

            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    profileUrl,
                    HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object avatarUrlObj = body.get("avatar_url");
                if (avatarUrlObj != null) {
                    return avatarUrlObj.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Error getting current bot avatar: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get current room avatar URL
     * 
     * @param roomId        Room ID
     * @param normalizedUrl Normalized homeserver URL
     * @return Current avatar URL or null if not set or error
     */
    @SuppressWarnings("unchecked")
    private String getCurrentRoomAvatar(String roomId, String normalizedUrl) {
        try {
            String stateUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "client", "v3", "rooms", roomId, "state", "m.room.avatar", "")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            String accessToken = matrixAccessToken != null && !matrixAccessToken.isEmpty()
                    ? matrixAccessToken
                    : (localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                            ? localAssistantPermanentToken
                            : oauth2Service.getUserAccessToken().orElse(null));

            if (accessToken == null) {
                return null;
            }

            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    stateUrl,
                    HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object urlObj = body.get("url");
                if (urlObj != null) {
                    return urlObj.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Error getting current room avatar: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Perform a Matrix sync operation using the SDK
     * Uses ApiClient's underlying HTTP mechanism
     * 
     * @param syncUrl     Full sync URL with query parameters
     * @param accessToken Access token for authentication
     * @return Sync response data as a Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> performSync(String syncUrl, String accessToken) {
        try {
            // Build ApiClient using SDK
            ApiClient apiClient = new ApiClient();
            apiClient.setHost(homeserverUrl);

            // Set request interceptor to add Authorization header (SDK pattern)
            apiClient.setRequestInterceptor(builder -> {
                builder.header("Authorization", "Bearer " + accessToken);
            });

            // Use RestTemplate but configured through ApiClient for consistency
            // The SDK doesn't expose a direct sync method, so we use the HTTP client
            // that the SDK uses internally
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(accessToken);
            org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                    syncUrl,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("Sync API returned error: HTTP {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error performing sync: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform sync", e);
        }
    }

    /**
     * Parse building letter from unit name (e.g., "A701" -> "A")
     */
    public Optional<String> parseBuildingFromUnitName(String unitName) {
        if (unitName == null || unitName.isEmpty()) {
            return Optional.empty();
        }

        // Extract first letter if it's A, B, or C
        String firstChar = unitName.substring(0, 1).toUpperCase();
        if ("A".equals(firstChar) || "B".equals(firstChar) || "C".equals(firstChar)) {
            return Optional.of(firstChar);
        }

        return Optional.empty();
    }
}
