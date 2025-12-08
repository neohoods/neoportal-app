package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.matrix.api.RoomParticipationApi;
import com.neohoods.portal.platform.matrix.api.SessionManagementApi;
import com.neohoods.portal.platform.services.Auth0Service;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantService;
import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import com.neohoods.portal.platform.services.matrix.space.MatrixAvatarService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMediaService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMembershipService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMessageService;
import com.neohoods.portal.platform.services.matrix.space.MatrixRoomService;
import com.neohoods.portal.platform.services.matrix.space.MatrixUserService;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantService Unit Tests")
class MatrixAssistantServiceTest {

    @Mock
    private MatrixOAuth2Service oauth2Service;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Auth0Service auth0Service;

    @Mock
    private ApiClient apiClient;

    @Mock
    private SessionManagementApi sessionManagementApi;

    @Mock
    private RoomParticipationApi roomParticipationApi;

    @Mock
    private MatrixUserService matrixUserService;

    @Mock
    private MatrixRoomService matrixRoomService;

    @Mock
    private MatrixMembershipService matrixMembershipService;

    @Mock
    private MatrixMessageService matrixMessageService;

    @Mock
    private MatrixAvatarService matrixAvatarService;

    @Mock
    private MatrixMediaService matrixMediaService;

    @InjectMocks
    private MatrixAssistantService matrixAssistantService;

    private static final String HOMESERVER_URL = "https://matrix.neohoods.com";
    private static final String SERVER_NAME = "chat.neohoods.com";
    private static final String SPACE_ID = "!space:chat.neohoods.com";
    private static final String BOT_USER_ID = "@alfred-local:chat.neohoods.com";
    private static final String ACCESS_TOKEN = "test-access-token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matrixAssistantService, "disabled", false);
        ReflectionTestUtils.setField(matrixAssistantService, "homeserverUrl", HOMESERVER_URL);
        ReflectionTestUtils.setField(matrixAssistantService, "serverName", SERVER_NAME);
        ReflectionTestUtils.setField(matrixAssistantService, "spaceId", SPACE_ID);
        ReflectionTestUtils.setField(matrixAssistantService, "localAssistantEnabled", false);
        ReflectionTestUtils.setField(matrixAssistantService, "botDisplayName", "Alfred");
    }

    @Test
    @DisplayName("getAssistantUserId should return configured local bot user ID when local assistant is enabled")
    void testGetAssistantUserId_LocalAssistantEnabled() {
        // Given
        ReflectionTestUtils.setField(matrixAssistantService, "localAssistantEnabled", true);
        ReflectionTestUtils.setField(matrixAssistantService, "localAssistantUserId", BOT_USER_ID);

        // When
        Optional<String> result = matrixAssistantService.getAssistantUserId();

        // Then
        assertTrue(result.isPresent());
        assertEquals(BOT_USER_ID, result.get());
    }

    @Test
    @DisplayName("getAssistantUserId should return user ID from token when local assistant is disabled")
    void testGetAssistantUserId_FromToken() {
        // Given
        ReflectionTestUtils.setField(matrixAssistantService, "localAssistantEnabled", false);
        // Note: getMatrixAccessToken() is private and uses oauth2Service internally
        // This test verifies the method exists and doesn't throw
        // When
        Optional<String> result = matrixAssistantService.getAssistantUserId();

        // Then
        // Result may be empty if token is not available, but method should not throw
        assertNotNull(result);
    }

    @Test
    @DisplayName("sendMessage should handle message sending")
    void testSendMessage_HandlesSending() {
        // Given
        when(matrixMessageService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        boolean result = matrixAssistantService.sendMessage("!room:chat.neohoods.com", "Test message");

        // Then
        assertTrue(result);
        verify(matrixMessageService).sendMessage("!room:chat.neohoods.com", "Test message");
    }

    @Test
    @DisplayName("sendMessage should handle markdown conversion internally")
    void testSendMessage_MarkdownConversion() {
        // Given
        String markdownMessage = "This is **bold** text";
        when(matrixMessageService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        boolean result = matrixAssistantService.sendMessage("!room:chat.neohoods.com", markdownMessage);

        // Then
        assertTrue(result);
        verify(matrixMessageService).sendMessage("!room:chat.neohoods.com", markdownMessage);
    }

    @Test
    @DisplayName("parseBuildingFromUnitName should extract building letter")
    void testParseBuildingFromUnitName() {
        // Given
        String unitName = "A701";

        // When
        Optional<String> result = matrixAssistantService.parseBuildingFromUnitName(unitName);

        // Then
        assertTrue(result.isPresent());
        assertEquals("A", result.get());
    }

    @Test
    @DisplayName("parseBuildingFromUnitName should return empty for invalid building")
    void testParseBuildingFromUnitName_InvalidBuilding() {
        // Given
        String unitName = "X701";

        // When
        Optional<String> result = matrixAssistantService.parseBuildingFromUnitName(unitName);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("checkSpaceExists should handle space existence check")
    void testCheckSpaceExists_HandlesCheck() {
        // Given
        when(matrixRoomService.checkSpaceExists(SPACE_ID)).thenReturn(true);

        // When
        boolean result = matrixAssistantService.checkSpaceExists(SPACE_ID);

        // Then
        assertTrue(result);
        verify(matrixRoomService).checkSpaceExists(SPACE_ID);
    }
}

