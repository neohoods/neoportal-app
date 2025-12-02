package com.neohoods.portal.platform.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.services.MatrixAssistantMCPServer.MCPContent;
import com.neohoods.portal.platform.services.MatrixAssistantMCPServer.MCPTool;
import com.neohoods.portal.platform.services.MatrixAssistantMCPServer.MCPToolResult;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service pour l'intégration avec Mistral AI.
 * Gère les appels LLM, les function calls, et l'orchestration avec RAG et MCP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantAIService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final MatrixAssistantMCPAdapter mcpAdapter;

    // Optional: RAG service (only available if RAG is enabled)
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MatrixAssistantRAGService ragService;

    @Value("${neohoods.portal.matrix.assistant.ai.provider:mistral}")
    private String provider;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key:}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.ai.model:mistral-small}")
    private String model;

    @Value("${neohoods.portal.matrix.assistant.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${neohoods.portal.matrix.assistant.rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${neohoods.portal.matrix.assistant.mcp.enabled:true}")
    private boolean mcpEnabled;

    private static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";

    /**
     * Génère une réponse à un message utilisateur en utilisant Mistral AI
     * avec support pour RAG et MCP
     */
    public Mono<String> generateResponse(
            String userMessage,
            String conversationHistory,
            MatrixAssistantAuthContext authContext) {

        if (!aiEnabled) {
            return Mono.just("L'assistant IA n'est pas activé.");
        }

        log.info("Generating AI response for message: {} (user: {})",
                userMessage.substring(0, Math.min(50, userMessage.length())),
                authContext.getMatrixUserId());

        // 1. Récupérer le contexte RAG si nécessaire
        Mono<String> ragContextMono = Mono.just("");
        if (ragEnabled && ragService != null) {
            ragContextMono = ragService.searchRelevantContext(userMessage)
                    .map(context -> context.isEmpty() ? "" : "Contexte de documentation:\n" + context + "\n\n");
        }

        // 2. Construire la liste des tools MCP disponibles
        List<Map<String, Object>> tools = new ArrayList<>();
        if (mcpEnabled) {
            List<MCPTool> mcpTools = mcpAdapter.listTools();
            tools = mcpTools.stream()
                    .map(this::convertMCPToolToMistralFunction)
                    .collect(Collectors.toList());
        }

        // 3. Construire le prompt système
        final String systemPrompt = buildSystemPrompt(authContext);
        final List<Map<String, Object>> finalTools = tools;

        // 4. Appeler Mistral API avec function calling
        return ragContextMono.flatMap(ragContext -> {
            return callMistralAPI(userMessage, conversationHistory, ragContext, systemPrompt, finalTools, authContext);
        });
    }

    /**
     * Appelle l'API Mistral avec function calling
     */
    private Mono<String> callMistralAPI(
            String userMessage,
            String conversationHistory,
            String ragContext,
            String systemPrompt,
            List<Map<String, Object>> tools,
            MatrixAssistantAuthContext authContext) {

        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // Construire les messages
        List<Map<String, Object>> messages = new ArrayList<>();

        // Message système
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt + "\n\n" + ragContext);
        messages.add(systemMsg);

        // Historique de conversation (si disponible)
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            // TODO: Parser l'historique et l'ajouter aux messages
            // Pour l'instant, on ignore l'historique
        }

        // Message utilisateur
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // Construire le body de la requête
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        if (!tools.isEmpty()) {
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto"); // Le modèle décide d'appeler un tool ou non
        }
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        log.debug("Calling Mistral API with model: {}, tools: {}", model, tools.size());

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    // Traiter la réponse Mistral
                    return processMistralResponse(response, authContext, messages);
                })
                .onErrorResume(e -> {
                    log.error("Error calling Mistral API: {}",
                            e instanceof Exception ? ((Exception) e).getMessage() : e.toString(), e);
                    return Mono.just("Désolé, une erreur s'est produite lors de la génération de la réponse.");
                });
    }

    /**
     * Traite la réponse Mistral et gère les function calls si nécessaire
     */
    @SuppressWarnings("unchecked")
    private Mono<String> processMistralResponse(Map<String, Object> response, MatrixAssistantAuthContext authContext,
            List<Map<String, Object>> previousMessages) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return Mono.just("Aucune réponse générée.");
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        // Vérifier si Mistral veut appeler une fonction
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            // Mistral veut appeler un ou plusieurs tools
            log.info("Mistral requested {} tool call(s)", toolCalls.size());

            // Appeler le premier tool (on peut itérer sur tous si nécessaire)
            Map<String, Object> toolCall = toolCalls.get(0);
            String toolCallId = (String) toolCall.get("id");
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String functionName = (String) function.get("name");
            String functionArgumentsJson = (String) function.get("arguments");

            // Parser les arguments JSON
            try {
                Map<String, Object> functionArguments = objectMapper.readValue(
                        functionArgumentsJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                // Appeler l'outil MCP
                MatrixAssistantMCPServer.MCPToolResult toolResult = mcpAdapter.callMCPToolDirect(functionName,
                        functionArguments, authContext);

                // Construire un message avec le résultat du tool
                String toolResultText = toolResult.getContent().stream()
                        .map(MCPContent::getText)
                        .filter(text -> text != null)
                        .collect(Collectors.joining("\n"));

                // Appeler à nouveau Mistral avec le résultat du tool
                // On passe le message assistant original avec tool_calls et le tool_call_id
                return callMistralWithToolResult(previousMessages, message, toolCallId, functionName, toolResultText,
                        authContext);
            } catch (Exception e) {
                log.error("Error parsing function arguments: {}", e.getMessage(), e);
                return Mono.just("Erreur lors de l'appel de l'outil: " + e.getMessage());
            }
        }

        // Pas de function call, retourner la réponse directe
        String content = (String) message.get("content");
        return Mono.just(content != null ? content : "Aucune réponse générée.");
    }

    /**
     * Appelle Mistral avec le résultat d'un tool call
     * 
     * @param previousMessages Les messages précédents (système + utilisateur)
     * @param assistantMessage Le message assistant original avec tool_calls
     * @param toolCallId       L'ID du tool call (nécessaire pour associer le
     *                         résultat)
     * @param functionName     Le nom de la fonction appelée
     * @param toolResult       Le résultat du tool call
     * @param authContext      Le contexte d'autorisation
     */
    private Mono<String> callMistralWithToolResult(
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            String toolCallId,
            String functionName,
            String toolResult,
            MatrixAssistantAuthContext authContext) {
        WebClient webClient = webClientBuilder
                .baseUrl(MISTRAL_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        List<Map<String, Object>> messages = new ArrayList<>();

        // Inclure tous les messages précédents (système + utilisateur)
        messages.addAll(previousMessages);

        // Message assistant original avec tool_calls (nécessaire pour le contexte)
        messages.add(assistantMessage);

        // Message avec le résultat du tool (doit inclure tool_call_id)
        Map<String, Object> toolMsg = new HashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", toolCallId);
        toolMsg.put("content", toolResult);
        messages.add(toolMsg);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        String content = (String) message.get("content");
                        return content != null ? content : "Aucune réponse générée.";
                    }
                    return "Aucune réponse générée.";
                })
                .onErrorResume(e -> {
                    log.error("Error calling Mistral with tool result: {}", e.getMessage(), e);
                    return Mono.just("Désolé, une erreur s'est produite lors de la génération de la réponse finale.");
                });
    }

    /**
     * Construit le prompt système pour Mistral
     */
    private String buildSystemPrompt(MatrixAssistantAuthContext authContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Tu es Alfred, l'assistant IA pour NeoHoods, une plateforme de gestion de copropriété.\n");
        prompt.append("Tu réponds aux questions des résidents via Matrix (Element).\n\n");

        if (authContext.isPublicResponse()) {
            prompt.append("ATTENTION: Cette conversation est dans une room publique. ");
            prompt.append("Ta réponse sera visible par tous les membres de la room. ");
            prompt.append("Ne partage pas d'informations sensibles ou personnelles.\n\n");
        } else {
            prompt.append("Cette conversation est en message privé (DM). ");
            prompt.append("Tu peux accéder aux informations personnelles de l'utilisateur si nécessaire.\n\n");
        }

        prompt.append("## Capacités disponibles:\n\n");
        prompt.append("### Informations générales:\n");
        prompt.append("- Répondre aux questions sur l'utilisation d'Element (installation, threads, encryption)\n");
        prompt.append("- Donner les numéros d'urgence (ACAF, etc.) - utilise get_emergency_numbers\n");
        prompt.append("- Dire qui habite à quel appartement ou étage - utilise get_resident_info\n");
        prompt.append("- Expliquer pourquoi une réservation a échoué - utilise get_reservation_details\n\n");

        prompt.append("### Gestion des espaces et réservations:\n");
        prompt.append("**IMPORTANT: Pour les questions sur les espaces, SUIS TOUJOURS CE FLOW:**\n\n");
        prompt.append("1. **Lister les espaces**: Quand l'utilisateur demande quels espaces sont disponibles, ");
        prompt.append(
                "quels espaces peuvent être réservés, ou liste les espaces, utilise TOUJOURS list_spaces en PREMIER.\n");
        prompt.append(
                "   - Exemples: 'Quels sont les espaces réservables?', 'Liste les espaces', 'Quels espaces puis-je réserver?'\n\n");

        prompt.append("2. **Détails d'un espace**: Quand l'utilisateur demande des détails sur un espace spécifique ");
        prompt.append("(règles, tarifs, description), utilise get_space_info avec l'ID de l'espace.\n");
        prompt.append(
                "   - Exemples: 'Quels sont les tarifs de l'espace X?', 'Quelles sont les règles de l'espace Y?'\n\n");

        prompt.append("3. **Vérifier disponibilité**: Quand l'utilisateur demande si un espace est disponible ");
        prompt.append(
                "sur une période (même vague comme 'Noël', 'semaine prochaine'), utilise check_space_availability.\n");
        prompt.append("   - Exemples: 'Est-ce disponible à Noël?', 'Disponibilité du X au Y', 'Période de Noël'\n");
        prompt.append(
                "   - Tu peux interpréter les périodes: 'Noël' = 24-25 décembre, 'semaine prochaine' = dans 7 jours, etc.\n\n");

        if (authContext.isAuthenticated()) {
            prompt.append("4. **Créer une réservation**: Quand l'utilisateur veut réserver un espace:\n");
            prompt.append("   a) Vérifie d'abord la disponibilité avec check_space_availability\n");
            prompt.append("   b) Présente un RÉCAPITULATIF avec:\n");
            prompt.append("      - Nom de l'espace\n");
            prompt.append("      - Dates (début et fin)\n");
            prompt.append("      - Tarif total estimé (si disponible)\n");
            prompt.append("   c) DEMANDE CONFIRMATION avant d'appeler create_reservation\n");
            prompt.append("   d) Seulement après confirmation explicite, appelle create_reservation\n");
            prompt.append("   e) Après création, informe l'utilisateur qu'un lien de paiement sera généré\n\n");

            prompt.append(
                    "5. **Mes réservations**: Quand l'utilisateur demande ses réservations, utilise list_my_reservations.\n");
            prompt.append(
                    "   - Exemples: 'Mes réservations', 'Quelles sont mes réservations?', 'Liste mes réservations'\n\n");

            prompt.append(
                    "6. **Code d'accès**: Quand l'utilisateur demande le code d'accès ou les instructions pour une réservation, ");
            prompt.append("utilise get_reservation_access_code avec l'ID de la réservation.\n");
            prompt.append("   - Inclus toujours les instructions de check-in, check-out, draps, etc.\n\n");
        }

        prompt.append("## Règles importantes:\n");
        prompt.append("- Sois proactif: Si l'utilisateur demande 'les espaces', utilise list_spaces IMMÉDIATEMENT\n");
        prompt.append("- Sois conversationnel: Guide l'utilisateur dans le flow de réservation naturellement\n");
        prompt.append("- Sois précis: Utilise toujours les bons outils MCP pour chaque type de question\n");
        prompt.append("- Sois concis mais complet: Donne toutes les informations nécessaires sans être verbeux\n");
        prompt.append("- Utilise le Markdown pour formater tes réponses (listes, gras, etc.)\n");
        prompt.append("- Sois amical et professionnel\n");

        return prompt.toString();
    }

    /**
     * Convertit un outil MCP en format function pour Mistral
     */
    private Map<String, Object> convertMCPToolToMistralFunction(MCPTool mcpTool) {
        Map<String, Object> function = new HashMap<>();
        function.put("type", "function");

        Map<String, Object> functionDef = new HashMap<>();
        functionDef.put("name", mcpTool.getName());
        functionDef.put("description", mcpTool.getDescription());
        functionDef.put("parameters", mcpTool.getInputSchema());

        function.put("function", functionDef);
        return function;
    }
}
