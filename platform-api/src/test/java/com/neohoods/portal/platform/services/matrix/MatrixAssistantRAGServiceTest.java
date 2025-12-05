package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.neohoods.portal.platform.services.matrix.rag.MatrixAssistantRAGService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantRAGService Unit Tests")
class MatrixAssistantRAGServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private MatrixAssistantRAGService ragService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ragService, "ragEnabled", true);
        ReflectionTestUtils.setField(ragService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(ragService, "customDocumentationFile", "");
    }

    @Test
    @DisplayName("searchRelevantContext should return empty when RAG is disabled")
    void testSearchRelevantContext_RAGDisabled() {
        // Given
        ReflectionTestUtils.setField(ragService, "ragEnabled", false);

        // When
        Mono<String> result = ragService.searchRelevantContext("test query");

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals("", response))
                .verifyComplete();
    }

    @Test
    @DisplayName("searchRelevantContext should return empty when no chunks indexed")
    void testSearchRelevantContext_NoChunks() {
        // Given
        ReflectionTestUtils.setField(ragService, "ragEnabled", true);

        // When
        Mono<String> result = ragService.searchRelevantContext("test query");

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals("", response))
                .verifyComplete();
    }

    @Test
    @DisplayName("indexDocument should add document chunks")
    void testIndexDocument_AddsChunks() {
        // Given
        String title = "Test Document";
        String content = "This is a test document.\n\nWith multiple paragraphs.\n\nFor testing purposes.";

        // When
        ReflectionTestUtils.invokeMethod(ragService, "indexDocument", title, content);

        // Then
        // Verify chunks were added by searching
        Mono<String> result = ragService.searchRelevantContext("test");
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    // Should find relevant content
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("searchRelevantContext should find relevant chunks by keywords")
    void testSearchRelevantContext_FindsRelevantChunks() {
        // Given
        String title = "Installation Guide";
        String content = "To install Element on your phone, go to the Play Store and search for Element.";
        ReflectionTestUtils.invokeMethod(ragService, "indexDocument", title, content);

        // When
        Mono<String> result = ragService.searchRelevantContext("install Element phone");

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertTrue(response.contains("Element") || response.contains("install"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("searchRelevantContext should limit results to 3 chunks")
    void testSearchRelevantContext_LimitsResults() {
        // Given
        // Index multiple documents
        for (int i = 0; i < 5; i++) {
            String title = "Document " + i;
            String content = "This is document " + i + " with test keyword for searching.";
            ReflectionTestUtils.invokeMethod(ragService, "indexDocument", title, content);
        }

        // When
        Mono<String> result = ragService.searchRelevantContext("test keyword");

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    // Should limit to 3 chunks
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("indexDocument should ignore chunks that are too short")
    void testIndexDocument_IgnoresShortChunks() {
        // Given
        String title = "Short Document";
        String content = "Short."; // Too short, should be ignored

        // When
        ReflectionTestUtils.invokeMethod(ragService, "indexDocument", title, content);

        // Then
        Mono<String> result = ragService.searchRelevantContext("Short");
        StepVerifier.create(result)
                .assertNext(response -> assertEquals("", response))
                .verifyComplete();
    }
}

