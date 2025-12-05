package com.neohoods.portal.platform.services.matrix.space;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

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

import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing Matrix media operations.
 * Handles image upload, download, and comparison.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "neohoods.portal.matrix.enabled" }, havingValue = "true", matchIfMissing = false)
public class MatrixMediaService {

    private final MatrixOAuth2Service oauth2Service;
    private final RestTemplate restTemplate;

    @Value("${neohoods.portal.matrix.disabled}")
    private boolean disabled;

    @Value("${neohoods.portal.matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${neohoods.portal.matrix.local-assistant.enabled}")
    private boolean localAssistantEnabled;

    @Value("${neohoods.portal.matrix.local-assistant.permanent-token}")
    private String localAssistantPermanentToken;

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
     * Upload an image from HTTP/HTTPS URL to Matrix and return MXC URL
     * 
     * @param imageUrl HTTP/HTTPS URL of the image
     * @return MXC URL (mxc://...) or null if upload failed
     */
    public String uploadImageToMatrix(String imageUrl) {
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
            // Priority: 1) localAssistantPermanentToken, 2) OAuth2 token from token service
            String accessToken = localAssistantPermanentToken != null && !localAssistantPermanentToken.isEmpty()
                    ? localAssistantPermanentToken
                    : oauth2Service.getUserAccessToken().orElse(null);

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
     * Download image from HTTP/HTTPS URL
     * 
     * @param imageUrl HTTP/HTTPS URL
     * @return Image bytes or null if download failed
     */
    public byte[] downloadImageFromUrl(String imageUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    imageUrl,
                    HttpMethod.GET,
                    null,
                    byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            log.debug("Error downloading image from {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Download image from Matrix MXC URL
     * 
     * @param mxcUrl MXC URL (mxc://server/mediaId)
     * @return Image bytes or null if download failed
     */
    public byte[] downloadImageFromMxc(String mxcUrl) {
        try {
            // Parse MXC URL: mxc://server/mediaId
            if (!mxcUrl.startsWith("mxc://")) {
                log.warn("Invalid MXC URL format: {}", mxcUrl);
                return null;
            }

            String mxcPath = mxcUrl.substring(6); // Remove "mxc://"
            String[] parts = mxcPath.split("/", 2);
            if (parts.length != 2) {
                log.warn("Invalid MXC URL format (cannot split): {}", mxcUrl);
                return null;
            }

            String server = parts[0];
            String mediaId = parts[1];

            // Build download URL
            String normalizedUrl = normalizeHomeserverUrl(homeserverUrl);
            String downloadUrl = UriComponentsBuilder.fromHttpUrl(normalizedUrl)
                    .pathSegment("_matrix", "media", "v3", "download", server, mediaId)
                    .build()
                    .toUriString();

            log.debug("Downloading image from Matrix: {}", downloadUrl);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    downloadUrl,
                    HttpMethod.GET,
                    null,
                    byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Successfully downloaded image from Matrix: {} bytes", response.getBody().length);
                return response.getBody();
            } else {
                log.warn("Failed to download image from Matrix: HTTP {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.warn("Error downloading image from Matrix {}: {}", mxcUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Calculate SHA-256 hash of image bytes
     * 
     * @param imageData Image bytes
     * @return Hex string of SHA-256 hash
     */
    public String calculateImageHash(byte[] imageData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(imageData);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return "";
        }
    }

    /**
     * Compare two images to check if they are identical
     * Downloads both images and compares their SHA-256 hash
     * 
     * @param sourceUrl     HTTP/HTTPS URL of the source image
     * @param currentMxcUrl MXC URL of the current image
     * @return true if images are identical, false if different, null if comparison
     *         failed
     */
    public Boolean imagesAreIdentical(String sourceUrl, String currentMxcUrl) {
        try {
            log.debug("Downloading source image from {}...", sourceUrl);
            // Download source image
            byte[] sourceImage = downloadImageFromUrl(sourceUrl);
            if (sourceImage == null || sourceImage.length == 0) {
                log.warn("Failed to download source image from {} (null or empty)", sourceUrl);
                return null; // Comparison failed
            }
            log.debug("Source image downloaded: {} bytes", sourceImage.length);

            log.debug("Downloading current image from Matrix {}...", currentMxcUrl);
            // Download current image from Matrix
            byte[] currentImage = downloadImageFromMxc(currentMxcUrl);
            if (currentImage == null || currentImage.length == 0) {
                log.warn("Failed to download current image from Matrix {} (null or empty)", currentMxcUrl);
                return null; // Comparison failed
            }
            log.debug("Current image downloaded: {} bytes", currentImage.length);

            // Compare hash of both images
            String sourceHash = calculateImageHash(sourceImage);
            String currentHash = calculateImageHash(currentImage);

            if (sourceHash == null || sourceHash.isEmpty() || currentHash == null || currentHash.isEmpty()) {
                log.warn("Failed to calculate image hashes (source: {}, current: {})", sourceHash, currentHash);
                return null; // Comparison failed
            }

            boolean identical = sourceHash.equals(currentHash);
            if (identical) {
                log.info("Images are identical (hash: {})", sourceHash);
            } else {
                log.info("Images differ - source hash: {} ({} bytes), current hash: {} ({} bytes)",
                        sourceHash, sourceImage.length, currentHash, currentImage.length);
            }
            return identical;
        } catch (Exception e) {
            log.warn("Error comparing images: {}", e.getMessage(), e);
            return null; // If comparison fails, return null to indicate failure
        }
    }
}

