package com.neohoods.portal.platform.spaces.services;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
public class NukiRemoteAPIService {

    private static final Logger logger = LoggerFactory.getLogger(NukiRemoteAPIService.class);

    @Value("${nuki.api-url}")
    private String nukiApiUrl;

    @Value("${nuki.token}")
    private String nukiToken;

    private final RestTemplate restTemplate;

    public NukiRemoteAPIService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Create a temporary access code on Nuki device
     */
    public NukiCodeResponse createTemporaryCode(AccessCodeEntity accessCode) {
        try {
            String url = nukiApiUrl + "/smartlock/" + accessCode.getReservation().getSpace().getDigitalLockId()
                    + "/auth";

            // Format dates as ISO 8601 UTC as required by Nuki API
            DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            String allowedFromDate = accessCode.getReservation().getStartDate()
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                    .format(formatter);
            String allowedUntilDate = accessCode.getExpiresAt()
                    .atOffset(ZoneOffset.UTC)
                    .format(formatter);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", "Guest - " + accessCode.getReservation().getSpace().getName());
            requestBody.put("type", 0); // Temporary code
            requestBody.put("code", accessCode.getCode());
            requestBody.put("allowedFromDate", allowedFromDate);
            requestBody.put("allowedUntilDate", allowedUntilDate);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + nukiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<NukiCodeResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    NukiCodeResponse.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                logger.info("Successfully created Nuki code {} for reservation {}",
                        accessCode.getCode(), accessCode.getReservation().getId());
                return response.getBody();
            } else {
                logger.error("Failed to create Nuki code: HTTP {} - {}", response.getStatusCode(), response.getBody());
                throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                        Map.of("operation", "createCode", "statusCode", response.getStatusCode(), "responseBody",
                                response.getBody()));
            }

        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error creating Nuki code for reservation {}: {}",
                    accessCode.getReservation().getId(), e.getMessage(), e);
            throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                    Map.of("operation", "createCode", "reservationId", accessCode.getReservation().getId()), e);
        }
    }

    /**
     * Delete an access code from Nuki device
     * Note: This method requires both smartlockId and authId
     */
    public boolean deleteTemporaryCode(String smartlockId, String authId) {
        try {
            String url = nukiApiUrl + "/smartlock/" + smartlockId + "/auth/" + authId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + nukiToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    request,
                    Void.class);

            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                logger.info("Successfully deleted Nuki code {} from smartlock {}", authId, smartlockId);
                return true;
            } else {
                logger.error("Failed to delete Nuki code: HTTP {} - {}", response.getStatusCode(), response.getBody());
                throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                        Map.of("operation", "deleteCode", "smartlockId", smartlockId, "authId", authId,
                                "statusCode", response.getStatusCode()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error deleting Nuki code {} from smartlock {}: {}", authId, smartlockId, e.getMessage(), e);
            throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                    Map.of("operation", "deleteCode", "smartlockId", smartlockId, "authId", authId), e);
        }
    }

    /**
     * Update an access code on Nuki device
     */
    public NukiCodeResponse updateTemporaryCode(String smartlockId, String authId, AccessCodeEntity accessCode) {
        try {
            String url = nukiApiUrl + "/smartlock/" + smartlockId + "/auth/" + authId;

            // Format dates as ISO 8601 UTC as required by Nuki API
            DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            String allowedFromDate = accessCode.getReservation().getStartDate()
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                    .format(formatter);
            String allowedUntilDate = accessCode.getExpiresAt()
                    .atOffset(ZoneOffset.UTC)
                    .format(formatter);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", "Guest - " + accessCode.getReservation().getSpace().getName());
            requestBody.put("type", 0); // Temporary code
            requestBody.put("code", accessCode.getCode());
            requestBody.put("allowedFromDate", allowedFromDate);
            requestBody.put("allowedUntilDate", allowedUntilDate);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + nukiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<NukiCodeResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    request,
                    NukiCodeResponse.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Successfully updated Nuki code {} for reservation {}",
                        accessCode.getCode(), accessCode.getReservation().getId());
                return response.getBody();
            } else {
                logger.error("Failed to update Nuki code: HTTP {} - {}", response.getStatusCode(), response.getBody());
                throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                        Map.of("operation", "updateCode", "smartlockId", smartlockId, "authId", authId,
                                "statusCode", response.getStatusCode(), "responseBody", response.getBody()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error updating Nuki code for reservation {}: {}",
                    accessCode.getReservation().getId(), e.getMessage(), e);
            throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                    Map.of("operation", "updateCode", "smartlockId", smartlockId, "authId", authId,
                            "reservationId", accessCode.getReservation().getId()),
                    e);
        }
    }

    /**
     * Get device status
     */
    public NukiDeviceStatus getDeviceStatus(String deviceId) {
        try {
            String url = nukiApiUrl + "/smartlock/" + deviceId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + nukiToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<NukiDeviceStatus> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    NukiDeviceStatus.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Successfully retrieved Nuki device status for device {}", deviceId);
                return response.getBody();
            } else {
                logger.error("Failed to get Nuki device status: HTTP {} - {}", response.getStatusCode(),
                        response.getBody());
                throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                        Map.of("operation", "getDeviceStatus", "deviceId", deviceId, "statusCode",
                                response.getStatusCode()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error getting Nuki device status for device {}: {}", deviceId, e.getMessage(), e);
            throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                    Map.of("operation", "getDeviceStatus", "deviceId", deviceId), e);
        }
    }

    /**
     * List all smartlocks
     */
    public NukiSmartlockListResponse listSmartlocks() {
        try {
            String url = nukiApiUrl + "/smartlock";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + nukiToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<NukiSmartlockListResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    NukiSmartlockListResponse.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Successfully retrieved Nuki smartlocks list");
                return response.getBody();
            } else {
                logger.error("Failed to get Nuki smartlocks list: HTTP {} - {}", response.getStatusCode(),
                        response.getBody());
                throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                        Map.of("operation", "listSmartlocks", "statusCode", response.getStatusCode()));
            }
        } catch (CodedErrorException e) {
            // Re-throw coded exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error getting Nuki smartlocks list: {}", e.getMessage(), e);
            throw new CodedErrorException(CodedError.NUKI_API_ERROR,
                    Map.of("operation", "listSmartlocks"), e);
        }
    }

    // Response DTOs
    public static class NukiCodeResponse {
        private String id;
        private String name;
        private int type;
        private boolean enabled;
        private boolean remoteAllowed;
        private String allowedFromDate;
        private String allowedUntilDate;
        private String code;
        private LocalDateTime createdAt;

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

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRemoteAllowed() {
            return remoteAllowed;
        }

        public void setRemoteAllowed(boolean remoteAllowed) {
            this.remoteAllowed = remoteAllowed;
        }

        public String getAllowedFromDate() {
            return allowedFromDate;
        }

        public void setAllowedFromDate(String allowedFromDate) {
            this.allowedFromDate = allowedFromDate;
        }

        public String getAllowedUntilDate() {
            return allowedUntilDate;
        }

        public void setAllowedUntilDate(String allowedUntilDate) {
            this.allowedUntilDate = allowedUntilDate;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class NukiDeviceStatus {
        private String smartlockId;
        private String name;
        private int state;
        private int stateName;
        private boolean batteryCritical;
        private int batteryCharge;
        private boolean online;
        private LocalDateTime lastSeen;
        private int config;
        private int adminPinState;
        private int securityLevel;
        private int doorSensorState;
        private int doorSensorStateName;

        // Getters and setters
        public String getSmartlockId() {
            return smartlockId;
        }

        public void setSmartlockId(String smartlockId) {
            this.smartlockId = smartlockId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getState() {
            return state;
        }

        public void setState(int state) {
            this.state = state;
        }

        public int getStateName() {
            return stateName;
        }

        public void setStateName(int stateName) {
            this.stateName = stateName;
        }

        public boolean isBatteryCritical() {
            return batteryCritical;
        }

        public void setBatteryCritical(boolean batteryCritical) {
            this.batteryCritical = batteryCritical;
        }

        public int getBatteryCharge() {
            return batteryCharge;
        }

        public void setBatteryCharge(int batteryCharge) {
            this.batteryCharge = batteryCharge;
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

        public int getConfig() {
            return config;
        }

        public void setConfig(int config) {
            this.config = config;
        }

        public int getAdminPinState() {
            return adminPinState;
        }

        public void setAdminPinState(int adminPinState) {
            this.adminPinState = adminPinState;
        }

        public int getSecurityLevel() {
            return securityLevel;
        }

        public void setSecurityLevel(int securityLevel) {
            this.securityLevel = securityLevel;
        }

        public int getDoorSensorState() {
            return doorSensorState;
        }

        public void setDoorSensorState(int doorSensorState) {
            this.doorSensorState = doorSensorState;
        }

        public int getDoorSensorStateName() {
            return doorSensorStateName;
        }

        public void setDoorSensorStateName(int doorSensorStateName) {
            this.doorSensorStateName = doorSensorStateName;
        }
    }

    public static class NukiSmartlockListResponse {
        private List<NukiDeviceStatus> smartlocks;

        public List<NukiDeviceStatus> getSmartlocks() {
            return smartlocks;
        }

        public void setSmartlocks(List<NukiDeviceStatus> smartlocks) {
            this.smartlocks = smartlocks;
        }
    }
}
