package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.neohoods.portal.platform.services.matrix.space.MatrixConversationContextService;

@DisplayName("MatrixConversationContextService Unit Tests")
class MatrixConversationContextServiceTest {

    private MatrixConversationContextService conversationService;

    private static final String ROOM_ID = "!room:chat.neohoods.com";

    @BeforeEach
    void setUp() {
        conversationService = new MatrixConversationContextService();
        ReflectionTestUtils.setField(conversationService, "maxHistoryPerRoom", 20);
        ReflectionTestUtils.setField(conversationService, "conversationContextEnabled", true);
    }

    @Test
    @DisplayName("addUserMessage should add message to history")
    void testAddUserMessage_AddsToHistory() {
        // Given
        String message = "Hello, bot!";

        // When
        conversationService.addUserMessage(ROOM_ID, message);

        // Then
        List<Map<String, Object>> history = conversationService.getConversationHistory(ROOM_ID);
        assertEquals(1, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals(message, history.get(0).get("content"));
    }

    @Test
    @DisplayName("addAssistantResponse should add response to history")
    void testAddAssistantResponse_AddsToHistory() {
        // Given
        String response = "Hello, user!";

        // When
        conversationService.addAssistantResponse(ROOM_ID, response);

        // Then
        List<Map<String, Object>> history = conversationService.getConversationHistory(ROOM_ID);
        assertEquals(1, history.size());
        assertEquals("assistant", history.get(0).get("role"));
        assertEquals(response, history.get(0).get("content"));
    }

    @Test
    @DisplayName("getConversationHistory should return empty when disabled")
    void testGetConversationHistory_Disabled() {
        // Given
        ReflectionTestUtils.setField(conversationService, "conversationContextEnabled", false);
        conversationService.addUserMessage(ROOM_ID, "Test message");

        // When
        List<Map<String, Object>> history = conversationService.getConversationHistory(ROOM_ID);

        // Then
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("getConversationHistory should limit to maxHistoryPerRoom")
    void testGetConversationHistory_LimitsHistory() {
        // Given
        ReflectionTestUtils.setField(conversationService, "maxHistoryPerRoom", 3);
        
        // Add more messages than limit
        for (int i = 0; i < 5; i++) {
            conversationService.addUserMessage(ROOM_ID, "Message " + i);
        }

        // When
        List<Map<String, Object>> history = conversationService.getConversationHistory(ROOM_ID);

        // Then
        assertTrue(history.size() <= 3);
    }

    @Test
    @DisplayName("clearHistory should remove room history")
    void testClearHistory_RemovesHistory() {
        // Given
        conversationService.addUserMessage(ROOM_ID, "Test message");
        assertEquals(1, conversationService.getHistorySize(ROOM_ID));

        // When
        conversationService.clearHistory(ROOM_ID);

        // Then
        assertEquals(0, conversationService.getHistorySize(ROOM_ID));
        assertTrue(conversationService.getConversationHistory(ROOM_ID).isEmpty());
    }

    @Test
    @DisplayName("getHistorySize should return correct size")
    void testGetHistorySize_ReturnsCorrectSize() {
        // Given
        conversationService.addUserMessage(ROOM_ID, "Message 1");
        conversationService.addAssistantResponse(ROOM_ID, "Response 1");
        conversationService.addUserMessage(ROOM_ID, "Message 2");

        // When
        int size = conversationService.getHistorySize(ROOM_ID);

        // Then
        assertEquals(3, size);
    }

    @Test
    @DisplayName("isEnabled should return enabled status")
    void testIsEnabled_ReturnsStatus() {
        // Given
        ReflectionTestUtils.setField(conversationService, "conversationContextEnabled", true);

        // When
        boolean enabled = conversationService.isEnabled();

        // Then
        assertTrue(enabled);
    }
}

