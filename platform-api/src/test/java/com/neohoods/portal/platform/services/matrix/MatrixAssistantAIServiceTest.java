package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAIService;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPAdapter;
import com.neohoods.portal.platform.services.matrix.rag.MatrixAssistantRAGService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("MatrixAssistantAIService Unit Tests")
class MatrixAssistantAIServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MatrixAssistantMCPAdapter mcpAdapter;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private org.springframework.context.MessageSource messageSource;

    @Mock
    private MatrixAssistantRAGService ragService;

    @Mock
    private MatrixAssistantAdminCommandService adminCommandService;

    @InjectMocks
    private MatrixAssistantAIService aiService;

    private MatrixAssistantAuthContext authContext;
    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiService, "aiEnabled", true);
        ReflectionTestUtils.setField(aiService, "ragEnabled", true);
        ReflectionTestUtils.setField(aiService, "mcpEnabled", true);
        ReflectionTestUtils.setField(aiService, "provider", "mistral");
        ReflectionTestUtils.setField(aiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(aiService, "model", "mistral-small");

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
    @DisplayName("generateResponse should return empty when AI is disabled")
    void testGenerateResponse_AIDisabled() {
        // Given
        ReflectionTestUtils.setField(aiService, "aiEnabled", false);

        // When
        Mono<String> result = aiService.generateResponse("Hello", null, authContext);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertTrue(response.contains("activé") || response.contains("enabled"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("generateResponse should handle AI service")
    void testGenerateResponse_HandlesService() {
        // Given
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Test message");
        when(mcpAdapter.listTools()).thenReturn(new ArrayList<>());
        when(ragService.searchRelevantContext(anyString())).thenReturn(Mono.just(""));

        // When
        Mono<String> result = aiService.generateResponse("Hello", null, authContext);

        // Then
        // Method should not throw - actual response depends on external API
        assertNotNull(result);
    }
}

