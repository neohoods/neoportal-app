package com.neohoods.portal.platform.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @Value("${neohoods.portal.matrix.assistant.ai.api-key:}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled:false}")
    private boolean ragEnabled;

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

        log.debug("Searching RAG context for query: {}", query.substring(0, Math.min(50, query.length())));

        // TODO: Implémenter la recherche vectorielle avec Mistral embeddings
        // Pour l'instant, retourner une recherche simple par mots-clés
        return Mono.fromCallable(() -> {
            String lowerQuery = query.toLowerCase();
            List<String> relevantChunks = new ArrayList<>();

            for (DocumentChunk chunk : documentChunks) {
                if (chunk.getContent().toLowerCase().contains(lowerQuery)) {
                    relevantChunks.add(chunk.getContent());
                    if (relevantChunks.size() >= 3) { // Limiter à 3 chunks
                        break;
                    }
                }
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

        log.info("Loaded {} document chunks for RAG", documentChunks.size());
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
