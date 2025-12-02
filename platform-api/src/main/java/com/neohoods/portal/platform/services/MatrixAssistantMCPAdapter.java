package com.neohoods.portal.platform.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.neohoods.portal.platform.api.internal.mcp.MatrixAssistantMCPController.MCPCallToolRequest;
import com.neohoods.portal.platform.api.internal.mcp.MatrixAssistantMCPController.MCPCallToolParams;
import com.neohoods.portal.platform.api.internal.mcp.MatrixAssistantMCPController.MCPCallToolResponse;
import com.neohoods.portal.platform.api.internal.mcp.MatrixAssistantMCPController.MCPListToolsResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Adaptateur pour transformer les function calls Mistral en requêtes MCP.
 * Gère les sessions MCP et les appels HTTP vers le serveur MCP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.mcp.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantMCPAdapter {

    private final WebClient.Builder webClientBuilder;
    private final MatrixAssistantMCPServer mcpServer;

    @Value("${neohoods.portal.matrix.assistant.mcp.base-url:http://localhost:8080/mcp}")
    private String mcpBaseUrl;

    // Stockage des sessions MCP par roomId (en mémoire)
    private final Map<String, String> sessionStore = new ConcurrentHashMap<>();

    /**
     * Récupère ou crée une session MCP pour une room
     */
    public String getOrCreateSession(String roomId) {
        return sessionStore.computeIfAbsent(roomId, k -> UUID.randomUUID().toString());
    }

    /**
     * Liste tous les outils MCP disponibles
     * Utilisé pour construire la liste de functions à passer à Mistral
     */
    public List<MatrixAssistantMCPServer.MCPTool> listTools() {
        return mcpServer.listTools();
    }

    /**
     * Transforme un function call Mistral en requête MCP et exécute l'outil
     * 
     * @param functionName Nom de la fonction appelée par Mistral
     * @param functionArguments Arguments de la fonction
     * @param authContext Contexte d'autorisation
     * @return Résultat de l'outil MCP
     */
    public Mono<MatrixAssistantMCPServer.MCPToolResult> callMCPTool(
            String functionName,
            Map<String, Object> functionArguments,
            MatrixAssistantAuthContext authContext) {
        
        log.info("Adapting Mistral function call {} to MCP tool call", functionName);
        
        // Récupérer ou créer la session MCP
        String sessionId = getOrCreateSession(authContext.getRoomId());
        
        // Construire la requête MCP JSON-RPC 2.0
        MCPCallToolRequest request = new MCPCallToolRequest();
        request.setId(1); // ID de requête (peut être incrémenté si nécessaire)
        request.setMethod("tools/call");
        
        MCPCallToolParams params = new MCPCallToolParams();
        params.setName(functionName);
        params.setArguments(functionArguments != null ? functionArguments : new HashMap<>());
        request.setParams(params);
        
        // Construire les headers avec le contexte d'autorisation
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Matrix-User-Id", authContext.getMatrixUserId());
        headers.set("X-Matrix-Room-Id", authContext.getRoomId());
        headers.set("X-Matrix-Is-DM", String.valueOf(authContext.isDirectMessage()));
        headers.set("X-MCP-Session-Id", sessionId);
        
        // Appeler le serveur MCP via HTTP
        WebClient webClient = webClientBuilder.baseUrl(mcpBaseUrl).build();
        
        return webClient.post()
                .uri("/tools/call")
                .headers(h -> h.addAll(headers))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MCPCallToolResponse.class)
                .map(response -> {
                    if (response.getError() != null) {
                        log.error("MCP tool call error: {} - {}", 
                                response.getError().getCode(), 
                                response.getError().getMessage());
                        // Créer un résultat d'erreur
                        return MatrixAssistantMCPServer.MCPToolResult.builder()
                                .isError(true)
                                .content(List.of(MatrixAssistantMCPServer.MCPContent.builder()
                                        .type("text")
                                        .text("Error: " + response.getError().getMessage())
                                        .build()))
                                .build();
                    }
                    
                    // Extraire le résultat de la réponse MCP
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = response.getResult();
                    if (result != null && result.containsKey("content")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
                        List<MatrixAssistantMCPServer.MCPContent> contents = contentList.stream()
                                .map(contentMap -> {
                                    MatrixAssistantMCPServer.MCPContent.MCPContentBuilder builder = 
                                            MatrixAssistantMCPServer.MCPContent.builder();
                                    builder.type((String) contentMap.get("type"));
                                    if (contentMap.containsKey("text")) {
                                        builder.text((String) contentMap.get("text"));
                                    }
                                    return builder.build();
                                })
                                .toList();
                        
                        return MatrixAssistantMCPServer.MCPToolResult.builder()
                                .isError(false)
                                .content(contents)
                                .build();
                    }
                    
                    // Résultat vide si pas de contenu
                    return MatrixAssistantMCPServer.MCPToolResult.builder()
                            .isError(false)
                            .content(List.of())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error calling MCP tool {}: {}", functionName, e.getMessage(), e);
                    return Mono.just(MatrixAssistantMCPServer.MCPToolResult.builder()
                            .isError(true)
                            .content(List.of(MatrixAssistantMCPServer.MCPContent.builder()
                                    .type("text")
                                    .text("Error calling MCP tool: " + e.getMessage())
                                    .build()))
                            .build());
                });
    }

    /**
     * Appelle directement l'outil MCP sans passer par HTTP (pour usage interne)
     * Plus efficace que l'appel HTTP
     */
    public MatrixAssistantMCPServer.MCPToolResult callMCPToolDirect(
            String toolName,
            Map<String, Object> arguments,
            MatrixAssistantAuthContext authContext) {
        
        log.info("Calling MCP tool {} directly (internal call) with arguments: {}", toolName, arguments);
        MatrixAssistantMCPServer.MCPToolResult result = mcpServer.callTool(toolName, arguments, authContext);
        
        // Logger le résultat (le résultat est déjà loggé dans MatrixAssistantMCPServer, mais on peut ajouter un log ici aussi)
        if (result.isError()) {
            log.warn("MCP tool {} returned error in adapter", toolName);
        } else {
            log.debug("MCP tool {} completed successfully in adapter", toolName);
        }
        
        return result;
    }
}





