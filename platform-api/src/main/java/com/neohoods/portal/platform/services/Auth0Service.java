package com.neohoods.portal.platform.services;

import java.util.HashMap;
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(userData, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(usersUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully registered user in Auth0: {}", email);
                return;
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
    public boolean userExists(String email) {
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
            // Return false for user existence check to avoid blocking signup on Auth0
            // errors
            return false;
        }
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
}
