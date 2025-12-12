package com.neohoods.portal.platform.assistant.services;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import java.time.Duration;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for managing Mistral Document Library.
 * Creates a library and uploads RAG documents at startup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MistralDocumentLibraryService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${neohoods.portal.matrix.assistant.rag.custom-documentation-file:classpath:rag-custom-documentation.md}")
    private String customDocumentationFile;

    private static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";

    private String libraryId;

    /**
     * Creates a document library in Mistral
     * 
     * @param name        Library name
     * @param description Library description
     * @return Library ID
     */
    public Mono<String> createLibrary(String name, String description) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("description", description);

        log.info("Creating Mistral Document Library: {}", name);

        return webClient.post()
                .uri("/libraries")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    String id = (String) response.get("id");
                    if (id == null || id.isEmpty()) {
                        throw new RuntimeException("Library creation failed: no ID returned");
                    }
                    this.libraryId = id;
                    log.info("✓ Created Mistral Document Library: {} with ID: {}", name, id);
                    return id;
                })
                .onErrorResume(e -> {
                    log.error("Failed to create Mistral Document Library: {}", e.getMessage(), e);
                    return Mono.error(new RuntimeException("Failed to create Mistral Document Library", e));
                });
    }

    /**
     * Uploads a document to the library
     * 
     * @param libraryId Library ID
     * @param fileName  File name
     * @param content   Document content
     * @return Document ID
     */
    public Mono<String> uploadDocument(String libraryId, String fileName, String content) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("file_name", fileName);
        requestBody.put("content", content);

        log.debug("Uploading document {} to library {}", fileName, libraryId);

        return webClient.post()
                .uri("/libraries/{libraryId}/documents", libraryId)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    String documentId = (String) response.get("id");
                    if (documentId == null || documentId.isEmpty()) {
                        throw new RuntimeException("Document upload failed: no ID returned for " + fileName);
                    }
                    log.debug("✓ Uploaded document {} to library {}: documentId={}", fileName, libraryId, documentId);
                    return documentId;
                })
                .onErrorResume(e -> {
                    log.error("Failed to upload document {} to library {}: {}", fileName, libraryId, e.getMessage(), e);
                    return Mono.error(new RuntimeException("Failed to upload document " + fileName, e));
                });
    }

    /**
     * Initializes the document library with RAG documents
     * 
     * @return Library ID
     */
    public Mono<String> initializeLibrary() {
        if (!ragEnabled) {
            log.info("RAG is disabled, skipping Document Library initialization");
            return Mono.just("");
        }

        return createLibrary("NeoHoods Documentation", "Documentation for NeoHoods platform and spaces")
                .flatMap(libId -> {
                    this.libraryId = libId;
                    return uploadRAGDocuments(libId)
                            .thenReturn(libId); // Return libId after upload completes
                });
    }

    /**
     * Uploads RAG documents to the library
     * 
     * @param libraryId Library ID
     * @return Mono that completes when all documents are uploaded
     */
    private Mono<Void> uploadRAGDocuments(String libraryId) {
        Mono<Void> uploadDefaultDoc = uploadDocumentFromResource(
                libraryId,
                "classpath:matrix-rag-documentation.yaml",
                "matrix-rag-documentation.yaml");

        Mono<Void> uploadCustomDoc = Mono.empty();
        if (customDocumentationFile != null && !customDocumentationFile.isEmpty()) {
            uploadCustomDoc = uploadDocumentFromResource(
                    libraryId,
                    customDocumentationFile,
                    "custom-documentation.md");
        }

        return Mono.when(uploadDefaultDoc, uploadCustomDoc)
                .doOnSuccess(v -> log.info("✓ All RAG documents uploaded to library {}", libraryId))
                .onErrorResume(e -> {
                    log.warn("Some RAG documents failed to upload: {}", e.getMessage());
                    return Mono.empty(); // Continue even if some documents fail
                });
    }

    /**
     * Uploads a document from a resource file
     * 
     * @param libraryId    Library ID
     * @param resourcePath Resource path (classpath:, file:, etc.)
     * @param fileName     File name for the document
     * @return Mono that completes when document is uploaded
     */
    private Mono<Void> uploadDocumentFromResource(String libraryId, String resourcePath, String fileName) {
        try {
            Resource resource = resourceLoader.getResource(resourcePath);
            if (!resource.exists()) {
                log.warn("RAG document resource not found: {}, skipping", resourcePath);
                return Mono.empty();
            }

            try (InputStream inputStream = resource.getInputStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                if (content == null || content.trim().isEmpty()) {
                    log.warn("RAG document is empty: {}, skipping", resourcePath);
                    return Mono.empty();
                }

                return uploadDocument(libraryId, fileName, content)
                        .then()
                        .doOnSuccess(v -> log.info("✓ Uploaded RAG document: {}", fileName));
            }
        } catch (Exception e) {
            log.warn("Failed to load RAG document from {}: {}", resourcePath, e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * Gets the library ID (cached)
     * 
     * @return Library ID or null if not created
     */
    public String getLibraryId() {
        return libraryId;
    }

    /**
     * Checks if the library is initialized
     * 
     * @return true if library ID is set
     */
    public boolean isInitialized() {
        return libraryId != null && !libraryId.isEmpty();
    }
}
