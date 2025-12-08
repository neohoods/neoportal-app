package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;

import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPServer;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantMCPAdapter Unit Tests")
class MatrixAssistantMCPAdapterTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private MatrixAssistantMCPServer mcpServer;

    @InjectMocks
    private MatrixAssistantMCPAdapter mcpAdapter;

    private MatrixAssistantAuthContext authContext;
    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mcpAdapter, "mcpBaseUrl", "http://localhost:8080/mcp");

        testUser = new UserEntity();
        testUser.setId(java.util.UUID.randomUUID());
        testUser.setPreferredLanguage("en");

        authContext = MatrixAssistantAuthContext.builder()
                .matrixUserId("@testuser:chat.neohoods.com")
                .roomId("!room:chat.neohoods.com")
                .isDirectMessage(false)
                .userEntity(testUser)
                .build();
    }

    @Test
    @DisplayName("getOrCreateSession should create new session for room")
    void testGetOrCreateSession_CreatesNew() {
        // Given
        String roomId = "!room:chat.neohoods.com";

        // When
        String session1 = mcpAdapter.getOrCreateSession(roomId);
        String session2 = mcpAdapter.getOrCreateSession(roomId);

        // Then
        assertNotNull(session1);
        assertNotNull(session2);
        assertEquals(session1, session2); // Same room should return same session
    }

    @Test
    @DisplayName("getOrCreateSession should create different sessions for different rooms")
    void testGetOrCreateSession_DifferentRooms() {
        // Given
        String roomId1 = "!room1:chat.neohoods.com";
        String roomId2 = "!room2:chat.neohoods.com";

        // When
        String session1 = mcpAdapter.getOrCreateSession(roomId1);
        String session2 = mcpAdapter.getOrCreateSession(roomId2);

        // Then
        assertNotNull(session1);
        assertNotNull(session2);
        assertTrue(!session1.equals(session2)); // Different rooms should have different sessions
    }

    @Test
    @DisplayName("listTools should delegate to mcpServer")
    void testListTools_DelegatesToServer() {
        // Given
        List<MatrixMCPModels.MCPTool> tools = new ArrayList<>();
        when(mcpServer.listTools()).thenReturn(tools);

        // When
        List<MatrixMCPModels.MCPTool> result = mcpAdapter.listTools();

        // Then
        assertNotNull(result);
    }

    @Test
    @DisplayName("callMCPToolDirect should call mcpServer directly")
    void testCallMCPToolDirect_CallsServer() {
        // Given
        Map<String, Object> arguments = new HashMap<>();
        MatrixMCPModels.MCPToolResult expectedResult = MatrixMCPModels.MCPToolResult.builder()
                .isError(false)
                .content(List.of(MatrixMCPModels.MCPContent.builder()
                        .type("text")
                        .text("Test result")
                        .build()))
                .build();
        when(mcpServer.callTool(anyString(), eq(arguments), eq(authContext)))
                .thenReturn(expectedResult);

        // When
        MatrixMCPModels.MCPToolResult result = mcpAdapter.callMCPToolDirect(
                "test_tool", arguments, authContext);

        // Then
        assertNotNull(result);
        assertFalse(result.isError());
    }
}

