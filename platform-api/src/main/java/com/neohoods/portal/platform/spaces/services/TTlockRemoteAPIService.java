package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.spaces.entities.AccessCodeEntity;

@Service
public class TTlockRemoteAPIService {

    private static final Logger logger = LoggerFactory.getLogger(TTlockRemoteAPIService.class);

    @Value("${ttlock.api-url:https://api.ttlock.com}")
    private String ttlockApiUrl;

    @Value("${ttlock.api-key:}")
    private String ttlockApiKey;

    @Value("${ttlock.device-id:}")
    private String ttlockDeviceId;

    private final RestTemplate restTemplate;

    public TTlockRemoteAPIService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Create a temporary access code on TTlock device
     */
    public TTlockCodeResponse createTemporaryCode(AccessCodeEntity accessCode) {
        String url = ttlockApiUrl + "/devices/" + ttlockDeviceId + "/codes";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("code", accessCode.getCode());
        requestBody.put("expires_at", accessCode.getExpiresAt().toString());
        requestBody.put("description", "Réservation - " + accessCode.getReservation().getSpace().getName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + ttlockApiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<TTlockCodeResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                TTlockCodeResponse.class);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            logger.info("Successfully created TTlock code for reservation {}",
                    accessCode.getReservation().getId());
            return response.getBody();
        } else {
            logger.error("Failed to create TTlock code: HTTP {} - {}",
                    response.getStatusCode(), response.getBody());
            throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                    Map.of("statusCode", response.getStatusCode(), "responseBody", response.getBody()));
        }
    }

    /**
     * Update an existing access code on TTlock device
     */
    public TTlockCodeResponse updateTemporaryCode(String ttlockCodeId, AccessCodeEntity accessCode) {
        String url = ttlockApiUrl + "/devices/" + ttlockDeviceId + "/codes/" + ttlockCodeId;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("code", accessCode.getCode());
        requestBody.put("expires_at", accessCode.getExpiresAt().toString());
        requestBody.put("description", "Réservation - " + accessCode.getReservation().getSpace().getName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + ttlockApiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<TTlockCodeResponse> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                request,
                TTlockCodeResponse.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            logger.info("Successfully updated TTlock code {} for reservation {}",
                    ttlockCodeId, accessCode.getReservation().getId());
            return response.getBody();
        } else {
            logger.error("Failed to update TTlock code {}: HTTP {} - {}",
                    ttlockCodeId, response.getStatusCode(), response.getBody());
            throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                    Map.of("operation", "update", "codeId", ttlockCodeId, "statusCode", response.getStatusCode(),
                            "responseBody", response.getBody()));
        }
    }

    /**
     * Delete an access code from TTlock device
     */
    public boolean deleteTemporaryCode(String ttlockCodeId) {
        String url = ttlockApiUrl + "/devices/" + ttlockDeviceId + "/codes/" + ttlockCodeId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + ttlockApiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                request,
                Void.class);

        return response.getStatusCode() == HttpStatus.NO_CONTENT;
    }

    /**
     * Get device status
     */
    public TTlockDeviceStatus getDeviceStatus() {
        String url = ttlockApiUrl + "/devices/" + ttlockDeviceId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + ttlockApiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<TTlockDeviceStatus> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                TTlockDeviceStatus.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            logger.info("Successfully retrieved TTlock device status");
            return response.getBody();
        } else {
            logger.error("Failed to get TTlock device status: HTTP {} - {}",
                    response.getStatusCode(), response.getBody());
            throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                    Map.of("operation", "getDeviceStatus", "statusCode", response.getStatusCode(), "responseBody",
                            response.getBody()));
        }
    }

    /**
     * Get all codes for the device
     */
    public TTlockCodesList getDeviceCodes() {
        String url = ttlockApiUrl + "/devices/" + ttlockDeviceId + "/codes";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + ttlockApiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<TTlockCodesList> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                TTlockCodesList.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            logger.info("Successfully retrieved TTlock device codes");
            return response.getBody();
        } else {
            logger.error("Failed to get TTlock device codes: HTTP {} - {}",
                    response.getStatusCode(), response.getBody());
            throw new CodedErrorException(CodedError.TTLOCK_API_ERROR,
                    Map.of("operation", "getDeviceCodes", "statusCode", response.getStatusCode(), "responseBody",
                            response.getBody()));
        }
    }

    // Response DTOs
    public static class TTlockCodeResponse {
        private String id;
        private String code;
        private String expiresAt;
        private String description;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(String expiresAt) {
            this.expiresAt = expiresAt;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class TTlockDeviceStatus {
        private String id;
        private String name;
        private String status;
        private boolean online;
        private LocalDateTime lastSeen;

        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isOnline() {
            return online;
        }

        public void setOnline(boolean online) {
            this.online = online;
        }

        public LocalDateTime getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(LocalDateTime lastSeen) {
            this.lastSeen = lastSeen;
        }
    }

    public static class TTlockCodesList {
        private java.util.List<TTlockCodeResponse> codes;
        private int total;

        // Getters and setters
        public java.util.List<TTlockCodeResponse> getCodes() {
            return codes;
        }

        public void setCodes(java.util.List<TTlockCodeResponse> codes) {
            this.codes = codes;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

}
