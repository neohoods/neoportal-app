package com.neohoods.portal.platform.services;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class Auth0Service {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${neohoods.portal.auth0.domain}")
    private String auth0Domain;

    @Value("${neohoods.portal.auth0.client-id}")
    private String clientId;

    @Value("${neohoods.portal.auth0.client-secret}")
    private String clientSecret;

    @Value("${neohoods.portal.auth0.audience}")
    private String audience;

    @Value("${neohoods.portal.auth0.connection}")
    private String connection;

    /**
     * Get an access token from Auth0 Management API
     */
    public String getAccessToken() {
        try {
            String tokenUrl = "https://" + auth0Domain + "/oauth/token";

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("client_id", clientId);
            requestBody.put("client_secret", clientSecret);
            requestBody.put("audience", audience);
            requestBody.put("grant_type", "client_credentials");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            } else {
                log.error("Failed to get access token from Auth0. Status: {}", response.getStatusCode());
                throw new CodedErrorException(CodedError.AUTH0_TOKEN_ERROR, "status",
                        response.getStatusCode().toString());
            }
        } catch (CodedErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting access token from Auth0", e);
            throw new CodedErrorException(CodedError.AUTH0_TOKEN_ERROR, e);
        }
    }

    /**
     * Register a new user in Auth0
     */
    public void registerUser(String email, String password, String username, Map<String, Object> userMetadata) {
        try {
            String accessToken = getAccessToken();
            String usersUrl = "https://" + auth0Domain + "/api/v2/users";

            Map<String, Object> userData = new HashMap<>();
            userData.put("email", email);
            userData.put("password", password);
            userData.put("username", username);
            userData.put("user_metadata", userMetadata);
            userData.put("connection", connection);

            // Note: returnTo is not supported in Auth0 user creation API
            // Email verification redirect URL should be configured in Auth0 Dashboard
            // under Applications > Settings > Advanced Settings > URLs

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(userData, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(usersUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully registered user in Auth0: {}", email);
            } else {
                log.error("Failed to register user in Auth0. Status: {}, Response: {}",
                        response.getStatusCode(), response.getBody());
                throw new CodedErrorException(CodedError.AUTH0_REGISTRATION_FAILED,
                        Map.of("email", email, "status", response.getStatusCode().toString()));
            }
        } catch (CodedErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error registering user in Auth0: {}", email, e);
            throw new CodedErrorException(CodedError.AUTH0_REGISTRATION_FAILED,
                    Map.of("email", email), e);
        }
    }

    /**
     * Check if a user exists in Auth0 by email
     */
    public Mono<Boolean> userExists(String email) {
        return Mono.fromCallable(() -> {
            try {
                String accessToken = getAccessToken();
                String searchUrl = "https://" + auth0Domain + "/api/v2/users-by-email?email=" + email;

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(accessToken);

                HttpEntity<String> request = new HttpEntity<>(headers);

                ResponseEntity<Map[]> response = restTemplate.exchange(
                        searchUrl,
                        HttpMethod.GET,
                        request,
                        Map[].class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return response.getBody().length > 0;
                }
                return false;
            } catch (Exception e) {
                log.error("Error checking if user exists in Auth0: {}", email, e);
                throw new CodedErrorException(CodedError.INTERNAL_ERROR,
                        Map.of("email", email), e);
            }
        });
    }

    /**
     * Get user details from Auth0 by email to check verification status
     */
    public Mono<Map<String, Object>> getUserDetails(String email) {
        return Mono.fromCallable(() -> {
            try {
                String accessToken = getAccessToken();
                String searchUrl = "https://" + auth0Domain + "/api/v2/users-by-email?email=" + email;

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(accessToken);

                HttpEntity<String> request = new HttpEntity<>(headers);

                ResponseEntity<Map[]> response = restTemplate.exchange(
                        searchUrl,
                        HttpMethod.GET,
                        request,
                        Map[].class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                        && response.getBody().length > 0) {
                    return response.getBody()[0];
                }
                return null;
            } catch (Exception e) {
                log.error("Error getting user details from Auth0: {}", email, e);
                throw new CodedErrorException(CodedError.INTERNAL_ERROR,
                        Map.of("email", email), e);
            }
        });
    }

    /**
     * Delete a user from Auth0 (for rollback purposes)
     */
    public void deleteUser(String email) {
        try {
            String accessToken = getAccessToken();

            // First, find the user by email
            String searchUrl = "https://" + auth0Domain + "/api/v2/users-by-email?email=" + email;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map[]> response = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    request,
                    Map[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && response.getBody().length > 0) {
                String userId = (String) response.getBody()[0].get("user_id");

                // Delete the user
                String deleteUrl = "https://" + auth0Domain + "/api/v2/users/" + userId;
                restTemplate.exchange(deleteUrl, HttpMethod.DELETE, request, Void.class);

                log.info("Successfully deleted user from Auth0: {}", email);
            } else {
                log.warn("User not found in Auth0 for deletion: {}", email);
            }
        } catch (Exception e) {
            log.error("Error deleting user from Auth0: {}", email, e);
            throw new CodedErrorException(CodedError.AUTH0_USER_DELETE_ERROR,
                    Map.of("email", email), e);
        }
    }

    /**
     * Get all users with the same email from Auth0
     */
    public Mono<List<Map<String, Object>>> getUsersByEmail(String email) {
        return Mono.fromCallable(() -> {
            try {
                String accessToken = getAccessToken();
                String searchUrl = "https://" + auth0Domain + "/api/v2/users-by-email?email=" + email;

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(accessToken);

                HttpEntity<String> request = new HttpEntity<>(headers);

                ResponseEntity<Map[]> response = restTemplate.exchange(
                        searchUrl,
                        HttpMethod.GET,
                        request,
                        Map[].class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return Arrays.asList(response.getBody());
                }
                return List.of();
            } catch (Exception e) {
                log.error("Error getting users by email from Auth0: {}", email, e);
                throw new CodedErrorException(CodedError.INTERNAL_ERROR,
                        Map.of("email", email), e);
            }
        });
    }

    /**
     * Link two Auth0 users (secondary user to primary user)
     */
    public void linkUsers(String primaryUserId, String secondaryUserId, String provider) {
        try {
            log.debug("Attempting to link user {} to primary user {} with provider {}",
                    secondaryUserId, primaryUserId, provider);

            String accessToken = getAccessToken();
            String linkUrl = "https://" + auth0Domain + "/api/v2/users/" + primaryUserId + "/identities";

            Map<String, Object> linkData = new HashMap<>();
            linkData.put("user_id", secondaryUserId);
            linkData.put("provider", provider);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(linkData, headers);

            // Log the request details for debugging
            log.info("Account linking request - URL: {}", linkUrl);
            log.info("Account linking request - Headers: {}", headers);
            log.info("Account linking request - Body: {}", linkData);

            ResponseEntity<Map[]> response = restTemplate.postForEntity(linkUrl, request, Map[].class);

            // Log the response for debugging
            log.debug("Account linking response - Status: {}, Body: {}", response.getStatusCode(), response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully linked user {} to primary user {}", secondaryUserId, primaryUserId);
            } else {
                log.error("Failed to link users. Status: {}, Response: {}, URL: {}",
                        response.getStatusCode(), response.getBody(), linkUrl);
                throw new CodedErrorException(CodedError.AUTH0_LINKING_FAILED,
                        Map.of("primaryUserId", primaryUserId, "secondaryUserId", secondaryUserId,
                                "status", response.getStatusCode().toString(), "response",
                                response.getBody().toString()));
            }
        } catch (CodedErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error linking users in Auth0: {} -> {} (provider: {})", secondaryUserId, primaryUserId, provider,
                    e);
            throw new CodedErrorException(CodedError.AUTH0_LINKING_FAILED,
                    Map.of("primaryUserId", primaryUserId, "secondaryUserId", secondaryUserId, "provider", provider),
                    e);
        }
    }

    /**
     * Perform automatic account linking for users with the same email
     */
    public Mono<Void> performAccountLinking(String currentUserId, String email, String provider) {
        return getUsersByEmail(email)
                .flatMap(users -> {
                    if (users == null || users.size() <= 1) {
                        log.debug("No duplicate users found for email: {}", email);
                        return Mono.empty();
                    }

                    // Sort users with priority: username-defined users first, then by creation date
                    users.sort((user1, user2) -> {
                        String username1 = (String) user1.get("username");
                        String username2 = (String) user2.get("username");

                        // If one has username and the other doesn't, prioritize the one with username
                        boolean hasUsername1 = username1 != null && !username1.trim().isEmpty();
                        boolean hasUsername2 = username2 != null && !username2.trim().isEmpty();

                        if (hasUsername1 && !hasUsername2) {
                            return -1;
                        }
                        if (!hasUsername1 && hasUsername2) {
                            return 1;
                        }

                        // If both have or both don't have username, sort by creation date (oldest
                        // first)
                        String createdAt1 = (String) user1.get("created_at");
                        String createdAt2 = (String) user2.get("created_at");
                        return (createdAt1 != null ? createdAt1 : "0").compareTo(createdAt2 != null ? createdAt2 : "0");
                    });

                    String primaryUserId = (String) users.get(0).get("user_id");
                    String primaryUsername = (String) users.get(0).get("username");
                    List<Map<String, Object>> secondaryUsers = users.subList(1, users.size());

                    log.info("Found {} users with email {}. Primary: {} (username: {}), Secondaries: {}",
                            users.size(), email, primaryUserId,
                            primaryUsername != null ? primaryUsername : "none",
                            secondaryUsers.stream().map(u -> u.get("user_id")).toArray());

                    log.debug("Current user ID: {}, Primary user ID: {}", currentUserId, primaryUserId);

                    // Log user details for debugging
                    for (Map<String, Object> user : users) {
                        log.debug("User details: user_id={}, username={}, identities={}",
                                user.get("user_id"), user.get("username"), user.get("identities"));
                    }

                    // Link all secondary users to the primary user
                    return Mono.<Void>fromRunnable(() -> {
                        for (Map<String, Object> user : secondaryUsers) {
                            String userId = (String) user.get("user_id");
                            // Skip if this is the primary user (shouldn't happen, but safety check)
                            if (!userId.equals(primaryUserId)) {
                                // Extract provider from user identities data
                                String userProvider = extractProviderFromUserData(user);
                                log.info("Linking secondary user {} (provider: {}) to primary user {}", userId,
                                        userProvider, primaryUserId);
                                try {
                                    linkUsers(primaryUserId, userId, userProvider);
                                } catch (Exception e) {
                                    log.warn("Failed to link user {} to primary user {}: {}", userId, primaryUserId,
                                            e.getMessage());
                                }
                            }
                        }
                    });
                })
                .onErrorResume(e -> {
                    log.error("Error during account linking for email: {}", email, e);
                    // Don't fail the login process for linking errors
                    return Mono.empty();
                });
    }

    /**
     * Extract provider from Auth0 user data (from identities array)
     */
    @SuppressWarnings("unchecked")
    private String extractProviderFromUserData(Map<String, Object> user) {
        try {
            // Get identities array from user data
            Object identitiesObj = user.get("identities");
            if (identitiesObj instanceof List) {
                List<Map<String, Object>> identities = (List<Map<String, Object>>) identitiesObj;
                if (!identities.isEmpty()) {
                    // Get the first identity's provider
                    String provider = (String) identities.get(0).get("provider");
                    if (provider != null && !provider.trim().isEmpty()) {
                        return provider;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract provider from user data: {}", e.getMessage());
        }

        // Fallback: extract from user_id if identities not available
        String userId = (String) user.get("user_id");
        if (userId != null && userId.contains("|")) {
            return userId.substring(0, userId.indexOf("|"));
        }

        return "auth0"; // Default fallback
    }
}
