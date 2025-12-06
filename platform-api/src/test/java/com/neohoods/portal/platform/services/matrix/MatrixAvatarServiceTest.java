package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.matrix.ApiException;
import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import com.neohoods.portal.platform.services.matrix.space.MatrixAvatarService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMediaService;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAvatarService Unit Tests")
class MatrixAvatarServiceTest {

    @Mock
    private MatrixOAuth2Service oauth2Service;

    @Mock
    private MatrixMediaService mediaService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ApiClient apiClient;

    @InjectMocks
    private MatrixAvatarService matrixAvatarService;

    private static final String HOMESERVER_URL = "https://matrix.neohoods.com";
    private static final String BOT_USER_ID = "@alfred-local:chat.neohoods.com";
    private static final String ROOM_ID = "!room:chat.neohoods.com";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String PERMANENT_TOKEN = "test-permanent-token";
    private static final String AVATAR_URL = "https://example.com/avatar.jpg";
    private static final String MXC_URL = "mxc://server.com/abc123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matrixAvatarService, "disabled", false);
        ReflectionTestUtils.setField(matrixAvatarService, "homeserverUrl", HOMESERVER_URL);
        ReflectionTestUtils.setField(matrixAvatarService, "localAssistantEnabled", true); // Enable local assistant to
                                                                                          // use configured user ID
        ReflectionTestUtils.setField(matrixAvatarService, "localAssistantUserId", BOT_USER_ID);
        ReflectionTestUtils.setField(matrixAvatarService, "localAssistantPermanentToken", PERMANENT_TOKEN);
        ReflectionTestUtils.setField(matrixAvatarService, "botAvatarUrl", AVATAR_URL);
        ReflectionTestUtils.setField(matrixAvatarService, "botDisplayName", "Alfred");
    }

    @Test
    @DisplayName("updateBotAvatar should return false when disabled")
    void testUpdateBotAvatar_WhenDisabled() {
        // Given
        ReflectionTestUtils.setField(matrixAvatarService, "disabled", true);

        // When
        boolean result = matrixAvatarService.updateBotAvatar();

        // Then
        assertFalse(result);
        verify(mediaService, never()).uploadImageToMatrix(anyString());
    }

    @Test
    @DisplayName("updateBotAvatar should return false when no avatar URL configured")
    void testUpdateBotAvatar_NoAvatarUrl() throws ApiException {
        // Given - local assistant enabled so getAssistantUserId returns configured user
        // ID
        ReflectionTestUtils.setField(matrixAvatarService, "botAvatarUrl", null);
        // No need to mock getMatrixApiClient since the method returns early when
        // botAvatarUrl is null

        // When
        boolean result = matrixAvatarService.updateBotAvatar();

        // Then
        assertFalse(result);
        verify(mediaService, never()).uploadImageToMatrix(anyString());
    }

    @Test
    @DisplayName("updateBotAvatar should return false when upload fails")
    void testUpdateBotAvatar_UploadFails() throws ApiException {
        // Given - local assistant enabled so getAssistantUserId returns configured user
        // ID
        when(oauth2Service.getMatrixApiClient(anyString(), isNull(), anyBoolean(), anyString(), anyString(),
                isNull()))
                .thenReturn(Optional.of(apiClient));
        when(mediaService.uploadImageToMatrix(AVATAR_URL)).thenReturn(null);

        // When
        boolean result = matrixAvatarService.updateBotAvatar();

        // Then
        assertFalse(result);
        verify(mediaService).uploadImageToMatrix(AVATAR_URL);
    }

    @Test
    @DisplayName("updateBotDisplayName should return false when disabled")
    void testUpdateBotDisplayName_WhenDisabled() {
        // Given
        ReflectionTestUtils.setField(matrixAvatarService, "disabled", true);

        // When
        boolean result = matrixAvatarService.updateBotDisplayName();

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("updateBotDisplayName should return true on success")
    void testUpdateBotDisplayName_Success() throws ApiException {
        // Given - local assistant enabled so getAssistantUserId returns configured user
        // ID
        when(oauth2Service.getMatrixApiClient(anyString(), isNull(), anyBoolean(), anyString(), anyString(),
                isNull()))
                .thenReturn(Optional.of(apiClient));

        Map<String, Object> profileResponse = new HashMap<>();
        profileResponse.put("displayname", "Old Name");
        ResponseEntity<Map> response = new ResponseEntity<>(profileResponse, HttpStatus.OK);
        // First call is get current display name, second call is update
        when(restTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .thenReturn(response)
                .thenReturn(new ResponseEntity<>(new HashMap<>(), HttpStatus.OK));

        // When
        boolean result = matrixAvatarService.updateBotDisplayName();

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("updateRoomAvatar should return false when disabled")
    void testUpdateRoomAvatar_WhenDisabled() {
        // Given
        ReflectionTestUtils.setField(matrixAvatarService, "disabled", true);

        // When
        boolean result = matrixAvatarService.updateRoomAvatar(ROOM_ID, AVATAR_URL);

        // Then
        assertFalse(result);
        verify(mediaService, never()).uploadImageToMatrix(anyString());
    }

    @Test
    @DisplayName("updateRoomAvatar should return false when upload fails")
    void testUpdateRoomAvatar_UploadFails() {
        // Given
        when(oauth2Service.getMatrixApiClient(anyString(), isNull(), anyBoolean(), anyString(), anyString(),
                isNull()))
                .thenReturn(Optional.of(apiClient));
        when(mediaService.uploadImageToMatrix(AVATAR_URL)).thenReturn(null);

        // When
        boolean result = matrixAvatarService.updateRoomAvatar(ROOM_ID, AVATAR_URL);

        // Then
        assertFalse(result);
        verify(mediaService).uploadImageToMatrix(AVATAR_URL);
    }

    @Test
    @DisplayName("updateRoomAvatar should return true on success")
    void testUpdateRoomAvatar_Success() {
        // Given
        when(oauth2Service.getMatrixApiClient(anyString(), isNull(), anyBoolean(), anyString(), anyString(),
                isNull()))
                .thenReturn(Optional.of(apiClient));
        when(mediaService.uploadImageToMatrix(AVATAR_URL)).thenReturn(MXC_URL);

        Map<String, Object> responseBody = new HashMap<>();
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        // Mock the room avatar update call - use PUT method
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), any(Class.class))).thenReturn(response);

        // When
        boolean result = matrixAvatarService.updateRoomAvatar(ROOM_ID, AVATAR_URL);

        // Then
        assertTrue(result);
        verify(mediaService).uploadImageToMatrix(AVATAR_URL);
    }
}
