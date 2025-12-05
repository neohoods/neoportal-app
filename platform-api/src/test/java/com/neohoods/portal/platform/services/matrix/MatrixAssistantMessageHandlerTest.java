package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAIService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContextService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantMessageHandler;

import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantService;
import com.neohoods.portal.platform.services.matrix.space.MatrixConversationContextService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantMessageHandler Unit Tests")
class MatrixAssistantMessageHandlerTest {

        @Mock
        private MatrixAssistantAIService aiService;

        @Mock
        private MatrixAssistantAuthContextService authContextService;

        @Mock
        private MatrixAssistantService matrixAssistantService;

        @Mock
        private MatrixConversationContextService conversationContextService;

        @Mock
        private MessageSource messageSource;

        @InjectMocks
        private MatrixAssistantMessageHandler messageHandler;

        private MatrixAssistantAuthContext authContext;
        private UserEntity testUser;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(messageHandler, "aiEnabled", true);

                testUser = new UserEntity();
                testUser.setId(java.util.UUID.randomUUID());
                testUser.setPreferredLanguage("en");

                authContext = MatrixAssistantAuthContext.builder()
                                .matrixUserId("@testuser:chat.neohoods.com")
                                .roomId("!room:chat.neohoods.com")
                                .isDirectMessage(false)
                                .userEntity(Optional.of(testUser))
                                .build();
        }

        @Test
        @DisplayName("handleMessage should return empty when AI is disabled")
        void testHandleMessage_AIDisabled() {
                // Given
                ReflectionTestUtils.setField(messageHandler, "aiEnabled", false);

                // When
                Mono<String> result = messageHandler.handleMessage(
                                "!room:chat.neohoods.com",
                                "@user:chat.neohoods.com",
                                "Hello",
                                false);

                // Then
                StepVerifier.create(result)
                                .verifyComplete();
        }

        @Test
        @DisplayName("handleMessage should process message when AI is enabled")
        void testHandleMessage_AIEnabled() {
                // Given
                when(authContextService.createAuthContext(anyString(), anyString(), eq(false)))
                                .thenReturn(authContext);
                when(conversationContextService.getConversationHistory(anyString()))
                                .thenReturn(new ArrayList<>());
                when(aiService.generateResponse(anyString(), any(), any(), eq(authContext)))
                                .thenReturn(Mono.just("Test response"));

                // When
                Mono<String> result = messageHandler.handleMessage(
                                "!room:chat.neohoods.com",
                                "@user:chat.neohoods.com",
                                "Hello",
                                false);

                // Then
                StepVerifier.create(result)
                                .assertNext(response -> {
                                        assertNotNull(response);
                                })
                                .verifyComplete();
        }

        @Test
        @DisplayName("handleMessage should handle errors gracefully")
        void testHandleMessage_HandlesErrors() {
                // Given
                when(authContextService.createAuthContext(anyString(), anyString(), eq(false)))
                                .thenThrow(new RuntimeException("Test error"));
                when(messageSource.getMessage(anyString(), any(), any()))
                                .thenReturn("Error message");

                // When
                Mono<String> result = messageHandler.handleMessage(
                                "!room:chat.neohoods.com",
                                "@user:chat.neohoods.com",
                                "Hello",
                                false);

                // Then
                StepVerifier.create(result)
                                .assertNext(response -> {
                                        assertNotNull(response);
                                        // Should return error message
                                })
                                .verifyComplete();
        }
}
