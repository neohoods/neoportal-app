package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;
import com.neohoods.portal.platform.services.matrix.space.MatrixMembershipService;
import com.neohoods.portal.platform.services.matrix.space.MatrixMessageService;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixMessageService Unit Tests")
class MatrixMessageServiceTest {

    @Mock
    private MatrixOAuth2Service oauth2Service;

    @Mock
    private MatrixMembershipService membershipService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ApiClient apiClient;

    @InjectMocks
    private MatrixMessageService matrixMessageService;

    private static final String HOMESERVER_URL = "https://matrix.neohoods.com";
    private static final String BOT_USER_ID = "@alfred-local:chat.neohoods.com";
    private static final String ROOM_ID = "!room:chat.neohoods.com";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String PERMANENT_TOKEN = "test-permanent-token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matrixMessageService, "disabled", false);
        ReflectionTestUtils.setField(matrixMessageService, "homeserverUrl", HOMESERVER_URL);
        ReflectionTestUtils.setField(matrixMessageService, "localAssistantEnabled", true); // Enable local assistant to
                                                                                           // use configured user ID
        ReflectionTestUtils.setField(matrixMessageService, "localAssistantUserId", BOT_USER_ID);
        ReflectionTestUtils.setField(matrixMessageService, "localAssistantPermanentToken", PERMANENT_TOKEN);
    }

    @Test
    @DisplayName("sendMessage should return false when disabled")
    void testSendMessage_WhenDisabled() {
        // Given
        ReflectionTestUtils.setField(matrixMessageService, "disabled", true);

        // When
        boolean result = matrixMessageService.sendMessage(ROOM_ID, "Test message");

        // Then
        assertFalse(result);
        verify(membershipService, never()).getUserRoomMembership(anyString(), anyString());
    }

    @Test
    @DisplayName("sendMessage should return false when no access token available")
    void testSendMessage_NoAccessToken() {
        // Given
        when(oauth2Service.getMatrixApiClient(anyString(), anyString(), anyBoolean(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        // When
        boolean result = matrixMessageService.sendMessage(ROOM_ID, "Test message");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("sendMessage should return false when bot cannot join room")
    void testSendMessage_CannotJoinRoom() {
        // Given - local assistant enabled so getAssistantUserId returns configured user
        // ID
        when(oauth2Service.getMatrixApiClient(anyString(), isNull(), anyBoolean(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(apiClient));
        when(membershipService.getUserRoomMembership(BOT_USER_ID, ROOM_ID))
                .thenReturn(Optional.empty());
        when(membershipService.joinRoomAsBot(ROOM_ID)).thenReturn(false);

        // When
        boolean result = matrixMessageService.sendMessage(ROOM_ID, "Test message");

        // Then
        assertFalse(result);
        verify(membershipService).joinRoomAsBot(ROOM_ID);
        verify(membershipService).getUserRoomMembership(BOT_USER_ID, ROOM_ID);
    }

    @Test
    @DisplayName("convertMarkdownToMatrixHtml should convert bold text")
    void testConvertMarkdownToMatrixHtml_Bold() {
        // Given
        String markdown = "This is **bold** text";

        // When
        String result = matrixMessageService.convertMarkdownToMatrixHtml(markdown);

        // Then
        assertTrue(result.contains("<strong>bold</strong>"));
    }

    @Test
    @DisplayName("convertMarkdownToMatrixHtml should convert italic text")
    void testConvertMarkdownToMatrixHtml_Italic() {
        // Given
        String markdown = "This is *italic* text";

        // When
        String result = matrixMessageService.convertMarkdownToMatrixHtml(markdown);

        // Then
        assertTrue(result.contains("<em>italic</em>"));
    }

    @Test
    @DisplayName("convertMarkdownToMatrixHtml should convert line breaks")
    void testConvertMarkdownToMatrixHtml_LineBreaks() {
        // Given
        String markdown = "Line 1\nLine 2";

        // When
        String result = matrixMessageService.convertMarkdownToMatrixHtml(markdown);

        // Then
        assertTrue(result.contains("<br />"));
    }

    @Test
    @DisplayName("convertMarkdownToMatrixHtml should return original text if no formatting")
    void testConvertMarkdownToMatrixHtml_NoFormatting() {
        // Given
        String markdown = "Plain text without formatting";

        // When
        String result = matrixMessageService.convertMarkdownToMatrixHtml(markdown);

        // Then
        assertEquals(markdown, result);
    }

    @Test
    @DisplayName("escapeHtml should escape HTML special characters")
    void testEscapeHtml() {
        // Given
        String text = "<script>alert('XSS')</script>";

        // When
        String result = matrixMessageService.escapeHtml(text);

        // Then
        assertTrue(result.contains("&lt;"));
        assertTrue(result.contains("&gt;"));
        assertTrue(result.contains("&#39;"));
    }

    @Test
    @DisplayName("sendTypingIndicator should return false when disabled")
    void testSendTypingIndicator_WhenDisabled() {
        // Given
        ReflectionTestUtils.setField(matrixMessageService, "disabled", true);

        // When
        boolean result = matrixMessageService.sendTypingIndicator(ROOM_ID, true, 30000);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("sendTypingIndicator should return true on success")
    void testSendTypingIndicator_Success() {
        // Given - local assistant enabled so getAssistantUserId returns configured user
        // ID
        // getMatrixAccessToken is called, need to mock it
        when(oauth2Service.getMatrixApiClient(eq(HOMESERVER_URL), isNull(), eq(true), eq(PERMANENT_TOKEN),
                eq(BOT_USER_ID), isNull()))
                .thenReturn(Optional.of(apiClient));

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(Void.class))).thenReturn(response);

        // When
        boolean result = matrixMessageService.sendTypingIndicator(ROOM_ID, true, 30000);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("sendTypingIndicator should return false on failure")
    void testSendTypingIndicator_Failure() {
        // Given - local assistant enabled so getAssistantUserId returns configured user
        // ID
        when(oauth2Service.getMatrixApiClient(eq(HOMESERVER_URL), isNull(), eq(true), eq(PERMANENT_TOKEN),
                eq(BOT_USER_ID), isNull()))
                .thenReturn(Optional.of(apiClient));

        ResponseEntity<Void> response = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), any(), any(), eq(Void.class))).thenReturn(response);

        // When
        boolean result = matrixMessageService.sendTypingIndicator(ROOM_ID, true, 30000);

        // Then
        assertFalse(result);
    }
}
