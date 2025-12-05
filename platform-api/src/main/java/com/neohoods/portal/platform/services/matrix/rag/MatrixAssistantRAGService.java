package com.neohoods.portal.platform.services.matrix.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * RAG (Retrieval-Augmented Generation) service for documentation.
 * Uses Mistral embeddings to search in documentation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.rag.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantRAGService {

    private final WebClient.Builder webClientBuilder;
    private final ResourceLoader resourceLoader;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled}")
    private boolean ragEnabled;

    @Value("${neohoods.portal.matrix.assistant.rag.custom-documentation-file}")
    private String customDocumentationFile;

    @Value("${neohoods.portal.matrix.assistant.rag.embeddings-api-url}")
    private String embeddingsApiUrl;

    // Simple in-memory storage of embeddings (to be replaced by a vector database
    // in production)
    private final List<DocumentChunk> documentChunks = new ArrayList<>();

    /**
     * Searches for relevant context in the documentation for a question
     */
    public Mono<String> searchRelevantContext(String query) {
        if (!ragEnabled || documentChunks.isEmpty()) {
            return Mono.just("");
        }

        String queryPreview = query.length() > 50 ? query.substring(0, 50) + "..." : query;
        log.debug("Searching RAG context for query: {} (total chunks: {})", queryPreview, documentChunks.size());

        // Future improvement: Implement vector search with Mistral embeddings for
        // better semantic matching
        // For now, return a simple keyword search
        return Mono.fromCallable(() -> {
            String lowerQuery = query.toLowerCase();
            List<String> relevantChunks = new ArrayList<>();
            List<String> matchedTitles = new ArrayList<>();

            // Keyword search in content
            for (DocumentChunk chunk : documentChunks) {
                String lowerContent = chunk.getContent().toLowerCase();
                // Check if at least one word from the query is in the content
                String[] queryWords = lowerQuery.split("\\s+");
                boolean matches = false;
                for (String word : queryWords) {
                    if (word.length() > 2 && lowerContent.contains(word)) { // Ignore words that are too short
                        matches = true;
                        break;
                    }
                }

                if (matches) {
                    relevantChunks.add(chunk.getContent());
                    matchedTitles.add(chunk.getTitle());
                    if (relevantChunks.size() >= 3) { // Limit to 3 chunks
                        break;
                    }
                }
            }

            if (!relevantChunks.isEmpty()) {
                log.debug("Found {} relevant chunks for query: {} (matched: {})",
                        relevantChunks.size(), queryPreview, matchedTitles);
            } else {
                log.debug("No relevant chunks found for query: {}", queryPreview);
            }

            return String.join("\n\n", relevantChunks);
        });
    }

    /**
     * Indexes a document in the RAG system
     */
    public void indexDocument(String title, String content) {
        // Split content into chunks
        String[] chunks = content.split("\n\n");
        for (String chunk : chunks) {
            if (chunk.trim().length() > 50) { // Ignore chunks that are too short
                documentChunks.add(new DocumentChunk(title, chunk.trim()));
            }
        }
        log.info("Indexed document '{}' with {} chunks", title, chunks.length);
    }

    /**
     * Loads initial documentation at application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadInitialDocumentation() {
        log.info("Loading initial documentation for RAG");

        // Load documentation from resource file
        loadDocumentationFromResource();

        // Load custom complementary documentation if configured
        loadCustomDocumentation();

        log.info("Loaded {} document chunks for RAG", documentChunks.size());
    }

    /**
     * Loads initial documentation from resource file
     */
    private void loadDocumentationFromResource() {
        try {
            Resource resource = resourceLoader.getResource("classpath:matrix-rag-documentation.yaml");
            if (resource.exists() && resource.isReadable()) {
                try (InputStream is = resource.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    parseAndIndexDocumentation(content);
                    log.info("Loaded documentation from matrix-rag-documentation.yaml");
                }
            } else {
                log.warn("matrix-rag-documentation.yaml not found, skipping initial documentation load");
            }
        } catch (Exception e) {
            log.error("Error loading documentation from matrix-rag-documentation.yaml", e);
        }
    }

    /**
     * Parses YAML documentation content and indexes it
     */
    private void parseAndIndexDocumentation(String content) {
        // Simple YAML parsing - split by "---" separator
        String[] sections = content.split("---");
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) {
                continue;
            }

            String[] lines = section.split("\n", 2);
            String title = lines.length > 0 && !lines[0].trim().isEmpty()
                    ? lines[0].trim().replace("title:", "").trim()
                    : "Documentation";
            String sectionContent = lines.length > 1 ? lines[1].trim() : section;

            // Remove YAML markers if present
            title = title.replaceAll("^['\"]|['\"]$", "");

            indexDocument(title, sectionContent);
        }
    }

    /**
     * Loads custom complementary documentation from a configured file
     * The file can be:
     * - A local file (file:/path/to/file)
     * - A classpath file (classpath:path/to/file)
     * - An absolute file path (/path/to/file)
     */
    private void loadCustomDocumentation() {
        if (customDocumentationFile == null || customDocumentationFile.isEmpty()) {
            log.debug("No custom documentation file configured");
            return;
        }

        log.info("Loading custom documentation from: {}", customDocumentationFile);

        try {
            String content;

            // Try first as Spring Resource (classpath:, file:, etc.)
            try {
                Resource resource = resourceLoader.getResource(customDocumentationFile);
                if (resource.exists() && resource.isReadable()) {
                    try (InputStream is = resource.getInputStream()) {
                        content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    log.info("Loaded custom documentation from resource: {}", customDocumentationFile);
                } else {
                    // If resource doesn't exist, try as absolute file path
                    Path filePath = Paths.get(customDocumentationFile);
                    if (Files.exists(filePath) && Files.isReadable(filePath)) {
                        content = Files.readString(filePath);
                        log.info("Loaded custom documentation from file path: {}", customDocumentationFile);
                    } else {
                        log.warn("Custom documentation file not found: {}", customDocumentationFile);
                        return;
                    }
                }
            } catch (Exception e) {
                // If resource fails, try as absolute file path
                Path filePath = Paths.get(customDocumentationFile);
                if (Files.exists(filePath) && Files.isReadable(filePath)) {
                    content = Files.readString(filePath);
                    log.info("Loaded custom documentation from file path: {}", customDocumentationFile);
                } else {
                    log.warn("Custom documentation file not found: {} - {}", customDocumentationFile, e.getMessage());
                    return;
                }
            }

            // Index custom content
            // File can contain multiple sections separated by "---" or empty lines
            parseAndIndexDocumentation(content);

            log.info("Successfully loaded custom documentation from: {}", customDocumentationFile);
        } catch (IOException e) {
            log.error("Error loading custom documentation from: {}", customDocumentationFile, e);
        } catch (Exception e) {
            log.error("Unexpected error loading custom documentation from: {}", customDocumentationFile, e);
        }
    }

    /**
     * Represents an indexed document chunk
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class DocumentChunk {
        private String title;
        private String content;
        // Future improvement: Add vector embedding field when implementing vector
        // search
    }
}
