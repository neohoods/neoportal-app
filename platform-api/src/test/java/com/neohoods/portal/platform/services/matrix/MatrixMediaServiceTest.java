package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import com.neohoods.portal.platform.services.matrix.space.MatrixMediaService;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixMediaService Unit Tests")
class MatrixMediaServiceTest {

    @Mock
    private MatrixOAuth2Service oauth2Service;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MatrixMediaService matrixMediaService;

    private static final String HOMESERVER_URL = "https://matrix.neohoods.com";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String PERMANENT_TOKEN = "test-permanent-token";
    private static final String IMAGE_URL = "https://example.com/image.jpg";
    private static final String MXC_URL = "mxc://server.com/abc123";
    private static final byte[] TEST_IMAGE_DATA = "fake image data".getBytes();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matrixMediaService, "disabled", false);
        ReflectionTestUtils.setField(matrixMediaService, "homeserverUrl", HOMESERVER_URL);
        ReflectionTestUtils.setField(matrixMediaService, "localAssistantEnabled", false);
        ReflectionTestUtils.setField(matrixMediaService, "localAssistantPermanentToken", PERMANENT_TOKEN);
        // Mock oauth2Service.getUserAccessToken() as fallback - use lenient since not
        // all tests need it
        org.mockito.Mockito.lenient().when(oauth2Service.getUserAccessToken()).thenReturn(Optional.of(ACCESS_TOKEN));
    }

    @Test
    @DisplayName("uploadImageToMatrix should return null when download fails")
    void testUploadImageToMatrix_DownloadFails() {
        // Given
        ResponseEntity<byte[]> downloadResponse = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        when(restTemplate.exchange(eq(IMAGE_URL), any(), any(), eq(byte[].class))).thenReturn(downloadResponse);

        // When
        String result = matrixMediaService.uploadImageToMatrix(IMAGE_URL);

        // Then
        assertNull(result);
        verify(restTemplate).exchange(eq(IMAGE_URL), any(), any(), eq(byte[].class));
    }

    @Test
    @DisplayName("uploadImageToMatrix should return null when image data is empty")
    void testUploadImageToMatrix_EmptyImageData() {
        // Given
        ResponseEntity<byte[]> downloadResponse = new ResponseEntity<>(new byte[0], HttpStatus.OK);
        when(restTemplate.exchange(eq(IMAGE_URL), eq(org.springframework.http.HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(downloadResponse);

        // When
        String result = matrixMediaService.uploadImageToMatrix(IMAGE_URL);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("uploadImageToMatrix should return MXC URL on success")
    void testUploadImageToMatrix_Success() {
        // Given - matrixAccessToken is set in setUp, so it will be used (no need to
        // mock getUserAccessToken)
        HttpHeaders downloadHeaders = new HttpHeaders();
        downloadHeaders.setContentType(MediaType.IMAGE_JPEG);
        ResponseEntity<byte[]> downloadResponse = new ResponseEntity<>(TEST_IMAGE_DATA, downloadHeaders, HttpStatus.OK);

        Map<String, String> uploadResponse = new java.util.HashMap<>();
        uploadResponse.put("content_uri", MXC_URL);
        ResponseEntity<Map> uploadResponseEntity = new ResponseEntity<>(uploadResponse, HttpStatus.OK);

        // Mock download call (first exchange call) - matches IMAGE_URL with GET method
        // and byte[].class
        when(restTemplate.exchange(
                eq(IMAGE_URL),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                eq(byte[].class))).thenReturn(downloadResponse);
        // Mock upload call (second exchange call) - matches any URL with POST method
        // and Map.class
        // The upload URL will contain "_matrix/media/v3/upload"
        // restTemplate.exchange(String url, HttpMethod method, HttpEntity
        // requestEntity, Class responseType)
        // Use anyString() since we can't easily match the exact URL, but we know it
        // contains the path
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.POST),
                any(org.springframework.http.HttpEntity.class),
                eq(Map.class))).thenReturn(uploadResponseEntity);

        // When
        String result = matrixMediaService.uploadImageToMatrix(IMAGE_URL);

        // Then
        assertEquals(MXC_URL, result);
        verify(restTemplate).exchange(eq(IMAGE_URL), any(), any(), eq(byte[].class));
    }

    @Test
    @DisplayName("downloadImageFromUrl should return image data on success")
    void testDownloadImageFromUrl_Success() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        ResponseEntity<byte[]> response = new ResponseEntity<>(TEST_IMAGE_DATA, headers, HttpStatus.OK);
        when(restTemplate.exchange(eq(IMAGE_URL), any(), any(), eq(byte[].class))).thenReturn(response);

        // When
        byte[] result = matrixMediaService.downloadImageFromUrl(IMAGE_URL);

        // Then
        assertNotNull(result);
        assertEquals(TEST_IMAGE_DATA.length, result.length);
    }

    @Test
    @DisplayName("downloadImageFromUrl should return null on failure")
    void testDownloadImageFromUrl_Failure() {
        // Given
        ResponseEntity<byte[]> response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        when(restTemplate.exchange(eq(IMAGE_URL), eq(org.springframework.http.HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(response);

        // When
        byte[] result = matrixMediaService.downloadImageFromUrl(IMAGE_URL);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("downloadImageFromMxc should return image data on success")
    void testDownloadImageFromMxc_Success() {
        // Given
        String serverName = "server.com";
        String mediaId = "abc123";
        String downloadUrl = "https://matrix.neohoods.com/_matrix/media/v3/download/" + serverName + "/" + mediaId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        ResponseEntity<byte[]> response = new ResponseEntity<>(TEST_IMAGE_DATA, headers, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(response);

        // When
        byte[] result = matrixMediaService.downloadImageFromMxc(MXC_URL);

        // Then
        assertNotNull(result);
        assertEquals(TEST_IMAGE_DATA.length, result.length);
    }

    @Test
    @DisplayName("downloadImageFromMxc should return null on invalid MXC URL")
    void testDownloadImageFromMxc_InvalidUrl() {
        // Given
        String invalidMxc = "invalid-url";

        // When
        byte[] result = matrixMediaService.downloadImageFromMxc(invalidMxc);

        // Then
        assertNull(result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(byte[].class));
    }

    @Test
    @DisplayName("calculateImageHash should return SHA-256 hash")
    void testCalculateImageHash() throws NoSuchAlgorithmException {
        // Given
        byte[] imageData = TEST_IMAGE_DATA;

        // When
        String hash = matrixMediaService.calculateImageHash(imageData);

        // Then
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        // SHA-256 produces 64 character hex string
        assertEquals(64, hash.length());

        // Verify it's consistent
        String hash2 = matrixMediaService.calculateImageHash(imageData);
        assertEquals(hash, hash2);
    }

    @Test
    @DisplayName("calculateImageHash should throw NullPointerException for null input")
    void testCalculateImageHash_NullInput() {
        // When/Then - expect NullPointerException since method doesn't handle null
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> {
            matrixMediaService.calculateImageHash(null);
        });
    }

    @Test
    @DisplayName("imagesAreIdentical should return true for identical images")
    void testImagesAreIdentical_Identical() {
        // Given - first call downloads from IMAGE_URL, second call downloads from MXC
        // URL
        // Use thenAnswer to handle multiple calls with different URLs
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(TEST_IMAGE_DATA, HttpStatus.OK));

        // When
        Boolean result = matrixMediaService.imagesAreIdentical(IMAGE_URL, MXC_URL);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("imagesAreIdentical should return false for different images")
    void testImagesAreIdentical_Different() {
        // Given - first call downloads from IMAGE_URL, second call downloads from MXC
        // URL with different data
        byte[] differentData = "different image data".getBytes();
        // Use thenAnswer to return different data on second call
        org.mockito.stubbing.Answer<ResponseEntity<byte[]>> answer = invocation -> {
            String url = invocation.getArgument(0);
            if (url.equals(IMAGE_URL)) {
                return new ResponseEntity<>(TEST_IMAGE_DATA, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(differentData, HttpStatus.OK);
            }
        };
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), any(), eq(byte[].class)))
                .thenAnswer(answer);

        // When
        Boolean result = matrixMediaService.imagesAreIdentical(IMAGE_URL, MXC_URL);

        // Then
        assertNotNull(result);
        assertFalse(result);
    }

    @Test
    @DisplayName("imagesAreIdentical should return null when download fails")
    void testImagesAreIdentical_DownloadFails() {
        // Given
        when(restTemplate.exchange(eq(IMAGE_URL), eq(org.springframework.http.HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.NOT_FOUND));

        // When
        Boolean result = matrixMediaService.imagesAreIdentical(IMAGE_URL, MXC_URL);

        // Then
        assertNull(result);
    }
}
