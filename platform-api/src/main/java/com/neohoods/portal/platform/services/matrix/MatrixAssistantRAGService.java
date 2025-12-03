package com.neohoods.portal.platform.services.matrix;

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
 * Service RAG (Retrieval-Augmented Generation) pour la documentation.
 * Utilise Mistral embeddings pour rechercher dans la documentation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.rag.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantRAGService {

    private final WebClient.Builder webClientBuilder;
    private final ResourceLoader resourceLoader;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key:}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${neohoods.portal.matrix.assistant.rag.custom-documentation-file:}")
    private String customDocumentationFile;

    private static final String MISTRAL_EMBEDDINGS_API_URL = "https://api.mistral.ai/v1/embeddings";

    // Stockage simple des embeddings en mémoire (à remplacer par une DB vectorielle
    // en production)
    private final List<DocumentChunk> documentChunks = new ArrayList<>();

    /**
     * Recherche le contexte pertinent dans la documentation pour une question
     */
    public Mono<String> searchRelevantContext(String query) {
        if (!ragEnabled || documentChunks.isEmpty()) {
            return Mono.just("");
        }

        String queryPreview = query.length() > 50 ? query.substring(0, 50) + "..." : query;
        log.debug("Searching RAG context for query: {} (total chunks: {})", queryPreview, documentChunks.size());

        // TODO: Implémenter la recherche vectorielle avec Mistral embeddings
        // Pour l'instant, retourner une recherche simple par mots-clés
        return Mono.fromCallable(() -> {
            String lowerQuery = query.toLowerCase();
            List<String> relevantChunks = new ArrayList<>();
            List<String> matchedTitles = new ArrayList<>();

            // Recherche par mots-clés dans le contenu
            for (DocumentChunk chunk : documentChunks) {
                String lowerContent = chunk.getContent().toLowerCase();
                // Vérifier si au moins un mot du query est dans le contenu
                String[] queryWords = lowerQuery.split("\\s+");
                boolean matches = false;
                for (String word : queryWords) {
                    if (word.length() > 2 && lowerContent.contains(word)) { // Ignorer les mots trop courts
                        matches = true;
                        break;
                    }
                }
                
                if (matches) {
                    relevantChunks.add(chunk.getContent());
                    matchedTitles.add(chunk.getTitle());
                    if (relevantChunks.size() >= 3) { // Limiter à 3 chunks
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
     * Indexe un document dans le système RAG
     */
    public void indexDocument(String title, String content) {
        // Découper le contenu en chunks
        String[] chunks = content.split("\n\n");
        for (String chunk : chunks) {
            if (chunk.trim().length() > 50) { // Ignorer les chunks trop courts
                documentChunks.add(new DocumentChunk(title, chunk.trim()));
            }
        }
        log.info("Indexed document '{}' with {} chunks", title, chunks.length);
    }

    /**
     * Charge la documentation initiale au démarrage de l'application
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadInitialDocumentation() {
        log.info("Loading initial documentation for RAG");

        // Guide d'installation Element sur mobile
        indexDocument("Element Mobile Installation",
                "Pour installer Element sur votre téléphone:\n\n" +
                        "1. Allez sur le Play Store (Android) ou App Store (iOS)\n" +
                        "2. Recherchez 'Element'\n" +
                        "3. Installez l'application\n" +
                        "4. Ouvrez Element et connectez-vous avec votre compte Matrix\n" +
                        "5. Votre serveur Matrix est: chat.neohoods.com\n\n" +
                        "Une fois connecté, vous verrez toutes vos rooms et pourrez recevoir des notifications.");

        // Documentation des réservations
        indexDocument("Réservation d'espaces",
                "Pour réserver un espace:\n\n" +
                        "1. Allez dans la section 'Espaces' de l'application\n" +
                        "2. Sélectionnez l'espace que vous souhaitez réserver\n" +
                        "3. Choisissez les dates de début et de fin\n" +
                        "4. Vérifiez la disponibilité\n" +
                        "5. Confirmez la réservation\n" +
                        "6. Effectuez le paiement\n\n" +
                        "Les réservations peuvent échouer si:\n" +
                        "- L'espace n'est pas disponible pour cette période\n" +
                        "- Vous n'avez pas les permissions nécessaires\n" +
                        "- Le paiement n'a pas été complété dans les 15 minutes");

        // Guide Element
        indexDocument("Element - Threads et Encryption",
                "Element utilise Matrix, un protocole de messagerie décentralisé et chiffré.\n\n" +
                        "Threads:\n" +
                        "- Les threads permettent d'organiser les conversations\n" +
                        "- Cliquez sur 'Répondre dans un thread' pour créer un thread\n" +
                        "- Les threads apparaissent dans le panneau latéral\n\n" +
                        "Encryption:\n" +
                        "- Element utilise le chiffrement end-to-end (E2EE) par défaut\n" +
                        "- Vos messages sont chiffrés et ne peuvent être lus que par les participants\n" +
                        "- Les clés de chiffrement sont stockées sur vos appareils\n" +
                        "- Si vous perdez l'accès à tous vos appareils, vous devrez réinitialiser les clés\n" +
                        "- Pour éviter cela, configurez une clé de récupération (recovery key)");

        // Charger la documentation complémentaire personnalisée si configurée
        loadCustomDocumentation();

        log.info("Loaded {} document chunks for RAG", documentChunks.size());
    }

    /**
     * Charge la documentation complémentaire personnalisée depuis un fichier configuré
     * Le fichier peut être :
     * - Un fichier local (file:/path/to/file)
     * - Un fichier classpath (classpath:path/to/file)
     * - Un fichier absolu (/path/to/file)
     */
    private void loadCustomDocumentation() {
        if (customDocumentationFile == null || customDocumentationFile.isEmpty()) {
            log.debug("No custom documentation file configured");
            return;
        }

        log.info("Loading custom documentation from: {}", customDocumentationFile);

        try {
            String content;
            
            // Essayer d'abord comme Resource Spring (classpath:, file:, etc.)
            try {
                Resource resource = resourceLoader.getResource(customDocumentationFile);
                if (resource.exists() && resource.isReadable()) {
                    try (InputStream is = resource.getInputStream()) {
                        content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    log.info("Loaded custom documentation from resource: {}", customDocumentationFile);
                } else {
                    // Si la resource n'existe pas, essayer comme chemin de fichier absolu
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
                // Si la resource échoue, essayer comme chemin de fichier absolu
                Path filePath = Paths.get(customDocumentationFile);
                if (Files.exists(filePath) && Files.isReadable(filePath)) {
                    content = Files.readString(filePath);
                    log.info("Loaded custom documentation from file path: {}", customDocumentationFile);
                } else {
                    log.warn("Custom documentation file not found: {} - {}", customDocumentationFile, e.getMessage());
                    return;
                }
            }

            // Indexer le contenu personnalisé
            // Le fichier peut contenir plusieurs sections séparées par "---" ou des lignes vides
            String[] sections = content.split("---");
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (!section.isEmpty()) {
                    // Extraire le titre (première ligne) si présent
                    String[] lines = section.split("\n", 2);
                    String title = lines.length > 0 && !lines[0].trim().isEmpty() 
                            ? lines[0].trim() 
                            : "Custom Documentation " + (i + 1);
                    String sectionContent = lines.length > 1 ? lines[1].trim() : section;
                    
                    indexDocument(title, sectionContent);
                }
            }

            log.info("Successfully loaded custom documentation from: {}", customDocumentationFile);
        } catch (IOException e) {
            log.error("Error loading custom documentation from: {}", customDocumentationFile, e);
        } catch (Exception e) {
            log.error("Unexpected error loading custom documentation from: {}", customDocumentationFile, e);
        }
    }

    /**
     * Représente un chunk de document indexé
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class DocumentChunk {
        private String title;
        private String content;
        // TODO: Ajouter embedding vectoriel quand on implémente la recherche
        // vectorielle
    }
}
