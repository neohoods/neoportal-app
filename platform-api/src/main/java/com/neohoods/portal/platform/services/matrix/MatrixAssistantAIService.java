package com.neohoods.portal.platform.services.matrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer.MCPContent;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer.MCPTool;

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
    private final ResourceLoader resourceLoader;

    @Value("${neohoods.portal.matrix.assistant.ai.system-prompt-file:classpath:matrix-assistant-system-prompt.txt}")
    private String systemPromptFile;

    private String baseSystemPrompt;

    // Optional: RAG service (only available if RAG is enabled)
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MatrixAssistantRAGService ragService;

    // Optional: Admin command service
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MatrixAssistantAdminCommandService adminCommandService;

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
     * Charge le prompt système depuis le fichier de ressources au démarrage
     */
    @PostConstruct
    public void loadSystemPrompt() {
        try {
            Resource resource = resourceLoader.getResource(systemPromptFile);
            if (!resource.exists()) {
                log.warn("System prompt file not found: {}, using default", systemPromptFile);
                baseSystemPrompt = getDefaultSystemPrompt();
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                baseSystemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log.info("Loaded system prompt from: {}", systemPromptFile);
            }
        } catch (Exception e) {
            log.error("Error loading system prompt from: {}, using default", systemPromptFile, e);
            baseSystemPrompt = getDefaultSystemPrompt();
        }
    }

    /**
     * Prompt système par défaut (fallback si le fichier n'est pas trouvé)
     */
    private String getDefaultSystemPrompt() {
        return "Tu es Alfred, l'assistant IA pour NeoHoods, une plateforme de gestion de copropriété.\n" +
                "Tu réponds aux questions des résidents via Matrix (Element).\n\n" +
                "⚠️ RÈGLE ABSOLUE - NE JAMAIS INVENTER D'INFORMATIONS:\n" +
                "- Si tu n'as PAS l'information dans le contexte de conversation OU via les outils MCP, " +
                "tu dois répondre: \"Je n'ai pas cette information. Laissez-moi la rechercher pour vous.\" " +
                "puis utiliser l'outil MCP approprié.\n";
    }

    /**
     * Génère une réponse à un message utilisateur en utilisant Mistral AI
     * avec support pour RAG et MCP
     */
    public Mono<String> generateResponse(
            String userMessage,
            String conversationHistory,
            MatrixAssistantAuthContext authContext) {
        return generateResponse(userMessage, conversationHistory, null, authContext);
    }

    /**
     * Génère une réponse à un message utilisateur en utilisant Mistral AI
     * avec support pour RAG et MCP
     * 
     * @param userMessage             Message de l'utilisateur
     * @param conversationHistory     Historique de conversation (format string,
     *                                pour compatibilité)
     * @param conversationHistoryList Historique de conversation (format liste,
     *                                préféré)
     * @param authContext             Contexte d'autorisation
     */
    public Mono<String> generateResponse(
            String userMessage,
            String conversationHistory,
            List<Map<String, Object>> conversationHistoryList,
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
            return callMistralAPI(userMessage, conversationHistory, conversationHistoryList, ragContext, systemPrompt,
                    finalTools, authContext);
        });
    }

    /**
     * Appelle l'API Mistral avec function calling
     */
    private Mono<String> callMistralAPI(
            String userMessage,
            String conversationHistory,
            List<Map<String, Object>> conversationHistoryList,
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

        // Optimisation: Si c'est le premier message de la conversation, envoyer le
        // prompt système complet
        // Sinon, utiliser un prompt système minimal (le modèle a déjà le contexte dans
        // l'historique)
        boolean isFirstMessage = (conversationHistoryList == null || conversationHistoryList.isEmpty()) &&
                (conversationHistory == null || conversationHistory.isEmpty());

        String systemPromptToUse;
        if (isFirstMessage) {
            // Premier message: prompt système complet
            systemPromptToUse = systemPrompt + "\n\n" + ragContext;
            log.debug("Using full system prompt (first message in conversation)");
        } else {
            // Messages suivants: prompt système minimal (juste les règles critiques)
            systemPromptToUse = buildMinimalSystemPrompt(authContext) + "\n\n" + ragContext;
            log.debug("Using minimal system prompt (conversation in progress)");
        }

        // Message système
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPromptToUse);
        messages.add(systemMsg);

        // Historique de conversation (si disponible)
        // Préférer la liste si disponible, sinon parser la string
        if (conversationHistoryList != null && !conversationHistoryList.isEmpty()) {
            // Utiliser directement la liste (format préféré)
            messages.addAll(conversationHistoryList);
            log.debug("Added {} messages from conversation history (list format)", conversationHistoryList.size());
        } else if (conversationHistory != null && !conversationHistory.isEmpty()) {
            // Parser l'historique string (format legacy)
            // Format attendu: "role: content\nrole: content\n..."
            String[] lines = conversationHistory.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                int colonIndex = line.indexOf(":");
                if (colonIndex > 0) {
                    String role = line.substring(0, colonIndex).trim();
                    String content = line.substring(colonIndex + 1).trim();
                    if (("user".equals(role) || "assistant".equals(role)) && !content.isEmpty()) {
                        Map<String, Object> histMsg = new HashMap<>();
                        histMsg.put("role", role);
                        histMsg.put("content", content);
                        messages.add(histMsg);
                    }
                }
            }
            log.debug("Added {} messages from conversation history (string format)", messages.size() - 1); // -1 pour
                                                                                                           // system
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
                    return processMistralResponse(response, authContext, messages, ragContext);
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
            List<Map<String, Object>> previousMessages, String ragContext) {
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
                        authContext, ragContext);
            } catch (Exception e) {
                log.error("Error parsing function arguments: {}", e.getMessage(), e);
                return Mono.just("Erreur lors de l'appel de l'outil: " + e.getMessage());
            }
        }

        // Pas de function call, retourner la réponse directe
        // MAIS: Si la question nécessite des données (adresses, numéros, etc.),
        // on ne devrait pas avoir de réponse directe sans tool call
        String content = (String) message.get("content");

        // LOG: Toutes les réponses finales du bot pour audit
        if (content != null) {
            log.info("🤖 BOT FINAL RESPONSE (no tool call) [user={}, room={}]: {}",
                    authContext.getMatrixUserId(),
                    authContext.getRoomId(),
                    content.length() > 500 ? content.substring(0, 500) + "..." : content);
        }

        // Vérifier si la réponse semble contenir des informations inventées
        // (adresses, numéros de téléphone, horaires, etc. qui ne sont pas dans le
        // contexte RAG ou toolResult)
        if (content != null && containsPotentiallyInventedInfo(content)) {
            // Vérifier si les informations suspectes sont dans le RAG
            boolean infoInRag = ragContext != null && !ragContext.isEmpty() &&
                    ragContext.toLowerCase()
                            .contains(content.toLowerCase().substring(0, Math.min(100, content.length())));

            if (!infoInRag) {
                log.warn(
                        "🚨 Bot response may contain invented information (no tool call, not in RAG). Forcing tool usage. Content: {}",
                        content.substring(0, Math.min(200, content.length())));
                // Ne pas envoyer cette réponse, retourner un message indiquant qu'on n'a pas
                // l'info
                return Mono.just("Je n'ai pas cette information disponible. Laissez-moi la rechercher pour vous.");
            } else {
                log.debug("Bot response contains potentially invented info but it's in RAG context, allowing it.");
            }
        }

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
     * @param ragContext       Le contexte RAG (peut contenir des informations
     *                         valides)
     */
    private Mono<String> callMistralWithToolResult(
            List<Map<String, Object>> previousMessages,
            Map<String, Object> assistantMessage,
            String toolCallId,
            String functionName,
            String toolResult,
            MatrixAssistantAuthContext authContext,
            String ragContext) {
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

                        // LOG: Toutes les réponses finales du bot pour audit
                        if (content != null) {
                            log.info("🤖 BOT FINAL RESPONSE [user={}, room={}]: {}",
                                    authContext.getMatrixUserId(),
                                    authContext.getRoomId(),
                                    content.length() > 500 ? content.substring(0, 500) + "..." : content);
                        }

                        // Vérifier si la réponse contient des informations inventées
                        // Si le toolResult ne contenait pas l'info demandée (ex: adresse, horaires)
                        // mais que la réponse en contient, c'est suspect
                        if (content != null && toolResult != null) {
                            String lowerContent = content.toLowerCase();
                            String lowerToolResult = toolResult.toLowerCase();

                            // Vérifier si la question demandait des horaires
                            // (on peut détecter ça dans le contexte précédent ou dans la réponse)
                            boolean askingForHours = lowerContent.contains("horaires") ||
                                    lowerContent.contains("horaire") ||
                                    lowerContent.contains("ouverture") ||
                                    lowerContent.contains("ouvert");

                            // Vérifier si la réponse contient des horaires
                            boolean responseHasHours = lowerContent
                                    .matches(".*(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*") ||
                                    lowerContent.matches(".*\\d+h\\d+.*\\d+h\\d+.*") ||
                                    lowerContent.matches(
                                            ".*(du|de)\\s+(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*");

                            // Vérifier si toolResult contient des horaires
                            boolean toolResultHasHours = lowerToolResult.contains("horaire") ||
                                    lowerToolResult.contains("ouverture") ||
                                    lowerToolResult.matches(".*\\d+h.*\\d+h.*");

                            // Si on demandait des horaires, que la réponse en contient, mais que toolResult
                            // n'en a pas, vérifier si c'est dans le RAG
                            if (askingForHours && responseHasHours && !toolResultHasHours) {
                                // Vérifier si les horaires sont dans le RAG
                                boolean hoursInRag = ragContext != null && !ragContext.isEmpty() &&
                                        (ragContext.toLowerCase().contains("horaire") ||
                                                ragContext.toLowerCase().contains("ouverture") ||
                                                ragContext.toLowerCase().matches(".*\\d+h.*\\d+h.*"));

                                if (!hoursInRag) {
                                    log.warn("🚨 BOT INVENTED OPENING HOURS! Tool result: {}, Response: {}, RAG: {}",
                                            toolResult.substring(0, Math.min(300, toolResult.length())),
                                            content.substring(0, Math.min(300, content.length())),
                                            ragContext != null
                                                    ? ragContext.substring(0, Math.min(200, ragContext.length()))
                                                    : "null");
                                    return "Je n'ai pas les horaires d'ouverture disponibles dans mes données.";
                                } else {
                                    log.debug("Opening hours found in RAG context, allowing response.");
                                }
                            }

                            // Si on cherchait une adresse mais que toolResult ne la contient pas
                            boolean askingForAddress = lowerToolResult.contains("adresse") ||
                                    lowerToolResult.contains("postal");
                            boolean responseHasAddress = lowerContent
                                    .matches(".*\\d+\\s+(rue|avenue|boulevard|place).*") ||
                                    lowerContent.matches(".*\\d{5}\\s+paris.*");

                            if (askingForAddress && responseHasAddress &&
                                    !lowerToolResult.contains("adresse:")) {
                                // Vérifier si l'adresse est dans le RAG
                                boolean addressInRag = ragContext != null && !ragContext.isEmpty() &&
                                        (ragContext.toLowerCase().contains("adresse") ||
                                                ragContext.toLowerCase()
                                                        .matches(".*\\d+\\s+(rue|avenue|boulevard|place).*"));

                                if (!addressInRag) {
                                    log.warn("🚨 BOT INVENTED ADDRESS! Tool result: {}, Response: {}, RAG: {}",
                                            toolResult.substring(0, Math.min(300, toolResult.length())),
                                            content.substring(0, Math.min(300, content.length())),
                                            ragContext != null
                                                    ? ragContext.substring(0, Math.min(200, ragContext.length()))
                                                    : "null");
                                    return "Je n'ai pas cette information disponible dans mes données.";
                                } else {
                                    log.debug("Address found in RAG context, allowing response.");
                                }
                            }

                            // Vérification générale des informations inventées
                            // Si la réponse contient des patterns suspects mais que toolResult ne les
                            // contient pas
                            if (containsPotentiallyInventedInfo(content)) {
                                // Vérifier si toolResult contient au moins une partie de ces infos
                                boolean toolResultHasInfo = lowerToolResult.contains("adresse") ||
                                        lowerToolResult.contains("téléphone") ||
                                        lowerToolResult.contains("phone") ||
                                        lowerToolResult.contains("horaire") ||
                                        lowerToolResult.contains("ouverture");

                                if (!toolResultHasInfo) {
                                    // Vérifier si l'information est dans le RAG
                                    boolean infoInRag = ragContext != null && !ragContext.isEmpty() &&
                                            (ragContext.toLowerCase().contains("adresse") ||
                                                    ragContext.toLowerCase().contains("téléphone") ||
                                                    ragContext.toLowerCase().contains("phone") ||
                                                    ragContext.toLowerCase().contains("horaire") ||
                                                    ragContext.toLowerCase().contains("ouverture"));

                                    if (!infoInRag) {
                                        log.warn("🚨 BOT INVENTED INFORMATION! Tool result: {}, Response: {}, RAG: {}",
                                                toolResult.substring(0, Math.min(300, toolResult.length())),
                                                content.substring(0, Math.min(300, content.length())),
                                                ragContext != null
                                                        ? ragContext.substring(0, Math.min(200, ragContext.length()))
                                                        : "null");
                                        return "Je n'ai pas cette information disponible dans mes données.";
                                    } else {
                                        log.debug("Information found in RAG context, allowing response.");
                                    }
                                }
                            }
                        }

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
     * Vérifie si une réponse contient potentiellement des informations inventées
     * (adresses, numéros de téléphone, horaires, etc. qui ressemblent à des données
     * réelles)
     */
    private boolean containsPotentiallyInventedInfo(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase();

        // Détecter des patterns qui suggèrent des informations inventées:
        // - Adresses avec numéros de rue (ex: "1 Rue", "75010", etc.)
        // - Numéros de téléphone français (format +33 ou 0X XX XX XX XX)
        // - Codes postaux français (5 chiffres)
        // - Horaires d'ouverture (ex: "8h30", "lundi au vendredi", "9h-17h", etc.)
        boolean hasAddressPattern = lowerContent.matches(".*\\d+\\s+(rue|avenue|boulevard|place|chemin|allée).*") ||
                lowerContent.matches(".*\\d{5}\\s+paris.*") ||
                lowerContent.matches(".*adresse.*\\d+.*");

        boolean hasPhonePattern = lowerContent
                .matches(".*\\+33\\s*[0-9]\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}.*") ||
                lowerContent.matches(".*0[1-9]\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}\\s*[0-9]{2}.*");

        boolean hasPostalCode = lowerContent.matches(".*\\d{5}.*");

        // Détecter les horaires d'ouverture (patterns communs)
        boolean hasOpeningHours = lowerContent
                .matches(".*(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*") ||
                lowerContent.matches(".*\\d+h\\d+.*\\d+h\\d+.*") ||
                lowerContent.matches(
                        ".*(horaires|horaire|ouverture).*(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*")
                ||
                lowerContent.matches(".*(du|de)\\s+(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche).*\\d+h.*");

        // Si on a une adresse, un numéro de téléphone, ou des horaires ET que le
        // contexte ne mentionne
        // pas explicitement qu'on a utilisé un outil, c'est suspect
        return (hasAddressPattern || hasPhonePattern) && hasPostalCode || hasOpeningHours;
    }

    /**
     * Construit un prompt système minimal pour les messages suivants dans une
     * conversation
     * (le prompt complet est déjà dans l'historique du premier message)
     */
    private String buildMinimalSystemPrompt(MatrixAssistantAuthContext authContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Tu es Alfred, l'assistant IA pour NeoHoods.\n");
        prompt.append("⚠️ RÈGLE ABSOLUE: Ne JAMAIS inventer d'informations. Utilise les outils MCP si nécessaire.\n");

        if (authContext.isPublicResponse()) {
            prompt.append("ATTENTION: Room publique - ne partage pas d'informations sensibles.\n");
        }

        // Ajouter seulement les commandes admin si nécessaire (partie dynamique)
        if (adminCommandService != null && adminCommandService.isAdminUser(authContext.getMatrixUserId())) {
            String adminCommands = adminCommandService.getAdminCommandsForPrompt();
            if (adminCommands != null && !adminCommands.isEmpty()) {
                prompt.append("\n").append(adminCommands);
            }
        }

        return prompt.toString();
    }

    /**
     * Construit le prompt système complet pour Mistral en chargeant le template
     * depuis les ressources
     * et en remplaçant les placeholders dynamiques
     */
    private String buildSystemPrompt(MatrixAssistantAuthContext authContext) {
        if (baseSystemPrompt == null || baseSystemPrompt.isEmpty()) {
            log.warn("Base system prompt not loaded, using default");
            baseSystemPrompt = getDefaultSystemPrompt();
        }

        String prompt = baseSystemPrompt;

        // Remplacer {CONTEXT_PUBLIC_OR_PRIVATE}
        String contextMessage;
        if (authContext.isPublicResponse()) {
            contextMessage = "ATTENTION: Cette conversation est dans une room publique. " +
                    "Ta réponse sera visible par tous les membres de la room. " +
                    "Ne partage pas d'informations sensibles ou personnelles.\n\n";
        } else {
            contextMessage = "Cette conversation est en message privé (DM). " +
                    "Tu peux accéder aux informations personnelles de l'utilisateur si nécessaire.\n\n";
        }
        prompt = prompt.replace("{CONTEXT_PUBLIC_OR_PRIVATE}", contextMessage);

        // Remplacer {RESERVATION_FLOW}
        String reservationFlow = "";
        if (authContext.isAuthenticated()) {
            reservationFlow = "4. **Créer une réservation**: Quand l'utilisateur veut réserver un espace:\n" +
                    "   a) Vérifie d'abord la disponibilité avec check_space_availability\n" +
                    "   b) Présente un RÉCAPITULATIF avec:\n" +
                    "      - Nom de l'espace\n" +
                    "      - Dates (début et fin)\n" +
                    "      - Tarif total estimé (si disponible)\n" +
                    "   c) DEMANDE CONFIRMATION avant d'appeler create_reservation\n" +
                    "   d) Seulement après confirmation explicite, appelle create_reservation\n" +
                    "   e) Après création, informe l'utilisateur qu'un lien de paiement sera généré\n\n" +
                    "5. **Mes réservations**: Quand l'utilisateur demande ses réservations, utilise list_my_reservations.\n"
                    +
                    "   - Exemples: 'Mes réservations', 'Quelles sont mes réservations?', 'Liste mes réservations'\n\n"
                    +
                    "6. **Code d'accès**: Quand l'utilisateur demande le code d'accès ou les instructions pour une réservation, "
                    +
                    "utilise get_reservation_access_code avec l'ID de la réservation.\n" +
                    "   - Inclus toujours les instructions de check-in, check-out, draps, etc.\n\n";
        }
        prompt = prompt.replace("{RESERVATION_FLOW}", reservationFlow);

        // Remplacer {ADMIN_COMMANDS}
        String adminCommandsSection = "";
        if (adminCommandService != null && adminCommandService.isAdminUser(authContext.getMatrixUserId())) {
            String adminCommands = adminCommandService.getAdminCommandsForPrompt();
            if (adminCommands != null && !adminCommands.isEmpty()) {
                adminCommandsSection = "\n" + adminCommands +
                        "\n**IMPORTANT**: Ces commandes sont réservées aux administrateurs uniquement. " +
                        "Tu peux les utiliser car tu es admin, mais NE mentionne JAMAIS ces commandes " +
                        "aux utilisateurs non-administrateurs. Si un utilisateur non-admin te demande " +
                        "ce que tu peux faire ou quelles sont tes capacités, ne liste PAS ces commandes admin.\n";
            }
        }
        prompt = prompt.replace("{ADMIN_COMMANDS}", adminCommandsSection);

        // Remplacer {NO_ADMIN_WARNING}
        String noAdminWarning = "";
        if (adminCommandService == null || !adminCommandService.isAdminUser(authContext.getMatrixUserId())) {
            noAdminWarning = "\n**IMPORTANT**: Si un utilisateur te demande ce que tu peux faire, " +
                    "liste uniquement les capacités générales (informations, réservations, etc.). " +
                    "NE mentionne JAMAIS de commandes admin ou de fonctionnalités réservées aux administrateurs.\n";
        }
        prompt = prompt.replace("{NO_ADMIN_WARNING}", noAdminWarning);

        return prompt;
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
