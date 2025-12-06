package com.neohoods.portal.platform.services.matrix.space;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.neohoods.portal.platform.mas.model.SingleResourceForUserEmail;
import com.neohoods.portal.platform.mas.model.IncludeCount;
import com.neohoods.portal.platform.mas.model.SingleResponseForUser;

import com.neohoods.portal.platform.services.Auth0Service;
import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing Matrix users.
 * Handles user creation, lookup, and profile updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixUserService {

    private final MatrixOAuth2Service oauth2Service;
    private final RestTemplate restTemplate;
    private final Auth0Service auth0Service;

    @Value("${neohoods.portal.matrix.disabled}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.server-name}")
    private String serverName;

    @Value("${neohoods.portal.matrix.local-assistant.permanent-token}")
    private String localAssistantPermanentToken;

    @Value("${neohoods.portal.matrix.mas.url}")
    private String masUrl;

    @Value("${neohoods.portal.matrix.mas.auth0-provider-id}")
    private String auth0ProviderId;

    // Cache for Auth0 subject -> MAS user ID mapping (lazy loaded)
    private Map<String, String> auth0SubjectToMasUserIdCache = null;

    /**
     * Get MAS API client configured with admin access token (client credentials flow)
     * Delegates to MatrixOAuth2Service
     */
    private Optional<com.neohoods.portal.platform.mas.ApiClient> getMASAccessToken() {
        if (disabled) {
            log.debug("Matrix bot is disabled");
            return Optional.empty();
        }

        return oauth2Service.getMASApiClient(masUrl);
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
                        // filterProvider must be a ULID, not a string like "auth0"
                        // If auth0ProviderId is not a valid ULID (26 chars), pass null
                        String filterProvider = (auth0ProviderId != null && auth0ProviderId.length() == 26) 
                                ? auth0ProviderId 
                                : null;
                        PaginatedResponseForUpstreamOAuthLink linksResponse = upstreamOauthLinkApi
                                .listUpstreamOAuthLinks(
                                        null, // pageBefore
                                        null, // pageAfter
                                        100, // pageFirst
                                        null, // pageLast
                                        countForLinks, // count - use false to avoid counting total items
                                        masUserId, // filterUser
                                        filterProvider, // filterProvider - must be ULID or null
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
                    // For now, we'll use a simple approach: if we got less than pageSize, we're done
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
                            for (SingleResourceForUserEmail emailResource : emailsResponse
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
                        } catch (Exception e) {
                            // Email might already exist or other error
                            log.debug("Failed to add email {} to Matrix user {}: {}", email, userId, e.getMessage());
                        }
                    } else {
                        log.debug("Email {} already exists for Matrix user {}, skipping", email, userId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to add email {} to Matrix user {}: {}", email, userId, e.getMessage());
                    // Continue even if email addition fails
                }
            }

            // Add upstream OAuth link to Auth0 if subject is available and user was just created
            if (auth0Subject != null && !auth0Subject.isEmpty() && !userAlreadyExists) {
                try {
                    UpstreamOauthLinkApi upstreamOauthLinkApi = new UpstreamOauthLinkApi(masClient);

                    // Check if link already exists
                    boolean linkExists = false;
                    try {
                        IncludeCount countForLinks = new IncludeCount("false");
                        // filterProvider must be a ULID, not a string like "auth0"
                        // If auth0ProviderId is not a valid ULID (26 chars), pass null
                        String filterProvider = (auth0ProviderId != null && auth0ProviderId.length() == 26) 
                                ? auth0ProviderId 
                                : null;
                        PaginatedResponseForUpstreamOAuthLink linksResponse = upstreamOauthLinkApi
                                .listUpstreamOAuthLinks(null, null, 100, null, countForLinks, masUserId,
                                        filterProvider, // filterProvider - must be ULID or null
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
        } catch (Exception e) {
            // User might already exist - try to find the existing user by username
            // Check if it's a 409 conflict error
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("409") || errorMsg.contains("already exists"))) {
                String userId = "@" + username + ":" + serverName;
                log.info("Matrix user {} might already exist in MAS, attempting to retrieve user ID", userId);

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
            }

            log.error("Error creating Matrix user: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Build display name from UserEntity
     * Format: "FirstName LastName [UnitName]"
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

            // Get access token for Authorization header
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

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
     * Normalize homeserver URL (ensure https, remove trailing slash)
     */
    private String normalizeHomeserverUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://chat.neohoods.com"; // default
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            String normalized = url.trim();
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
        return "https://" + url;
    }
}

