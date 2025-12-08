package com.neohoods.portal.platform.assistant.mcp;

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

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

/**
 * Adapter to transform Mistral function calls into MCP requests.
 * Manages MCP sessions and HTTP calls to the MCP server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.mcp.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantMCPAdapter {

    private final WebClient.Builder webClientBuilder;
    private final MatrixAssistantMCPServer mcpServer;
    private final com.neohoods.portal.platform.services.JwtService jwtService;

    @Value("${neohoods.portal.matrix.assistant.mcp.base-url}")
    private String mcpBaseUrl;

    // MCP session storage by roomId (in-memory)
    private final Map<String, String> sessionStore = new ConcurrentHashMap<>();

    /**
     * Gets or creates an MCP session for a room
     */
    public String getOrCreateSession(String roomId) {
        return sessionStore.computeIfAbsent(roomId, k -> UUID.randomUUID().toString());
    }

    /**
     * Lists all available MCP tools
     * Used to build the list of functions to pass to Mistral
     */
    public List<MatrixMCPModels.MCPTool> listTools() {
        return mcpServer.listTools();
    }

    /**
     * Transforms a Mistral function call into an MCP request and executes the tool
     * 
     * @param functionName      Name of the function called by Mistral
     * @param functionArguments Function arguments
     * @param authContext       Authorization context
     * @return MCP tool result
     */
    public Mono<MatrixMCPModels.MCPToolResult> callMCPTool(
            String functionName,
            Map<String, Object> functionArguments,
            MatrixAssistantAuthContext authContext) {

        log.info("Adapting Mistral function call {} to MCP tool call", functionName);

        // Get or create MCP session
        String sessionId = getOrCreateSession(authContext.getRoomId());

        // Build MCP JSON-RPC 2.0 request
        MCPCallToolRequest request = new MCPCallToolRequest();
        request.setId(1); // Request ID (can be incremented if needed)
        request.setMethod("tools/call");

        MCPCallToolParams params = new MCPCallToolParams();
        params.setName(functionName);
        params.setArguments(functionArguments != null ? functionArguments : new HashMap<>());
        request.setParams(params);

        // Build headers with authorization context (JWT) and tracing information
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-MCP-Session-Id", sessionId);
        
        // Add tracing headers from MDC for log correlation
        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        String conversationTraceId = MDC.get("conversationTraceId");
        String roomId = MDC.get("roomId");
        
        if (traceId != null) {
            headers.set("X-Trace-Id", traceId);
        }
        if (spanId != null) {
            headers.set("X-Span-Id", spanId);
        }
        if (conversationTraceId != null) {
            headers.set("X-Conversation-Trace-Id", conversationTraceId);
        }
        if (roomId != null) {
            headers.set("X-Room-Id", roomId);
        }
        
        try {
            String jwt = jwtService.createAssistantToken(
                    authContext.getAuthenticatedUser(),
                    authContext.getMatrixUserId(),
                    authContext.getRoomId(),
                    authContext.isDirectMessage());
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        } catch (Exception e) {
            log.error("Failed to create assistant JWT: {}", e.getMessage(), e);
            // fallback to legacy headers
            headers.set("X-Matrix-User-Id", authContext.getMatrixUserId());
            headers.set("X-Matrix-Room-Id", authContext.getRoomId());
            headers.set("X-Matrix-Is-DM", String.valueOf(authContext.isDirectMessage()));
        }

        // Call MCP server via HTTP
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
                        // Create error result
                        return MatrixMCPModels.MCPToolResult.builder()
                                .isError(true)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                        .type("text")
                                        .text("Error: " + response.getError().getMessage())
                                        .build()))
                                .build();
                    }

                    // Extract result from MCP response
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = response.getResult();
                    if (result != null && result.containsKey("content")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
                        List<MatrixMCPModels.MCPContent> contents = contentList.stream()
                                .map(contentMap -> {
                                    MatrixMCPModels.MCPContent.MCPContentBuilder builder = MatrixMCPModels.MCPContent
                                            .builder();
                                    builder.type((String) contentMap.get("type"));
                                    if (contentMap.containsKey("text")) {
                                        builder.text((String) contentMap.get("text"));
                                    }
                                    return builder.build();
                                })
                                .toList();

                        return MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(contents)
                                .build();
                    }

                    // Empty result if no content
                    return MatrixMCPModels.MCPToolResult.builder()
                            .isError(false)
                            .content(List.of())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error calling MCP tool {}: {}", functionName, e.getMessage(), e);
                    return Mono.just(MatrixMCPModels.MCPToolResult.builder()
                            .isError(true)
                            .content(List.of(MatrixMCPModels.MCPContent.builder()
                                    .type("text")
                                    .text("Error calling MCP tool: " + e.getMessage())
                                    .build()))
                            .build());
                });
    }

    /**
     * Calls MCP tool directly without going through HTTP (for internal use)
     * More efficient than HTTP call
     */
    public MatrixMCPModels.MCPToolResult callMCPToolDirect(
            String toolName,
            Map<String, Object> arguments,
            MatrixAssistantAuthContext authContext) {

        log.info("Calling MCP tool {} directly (internal call) with arguments: {}", toolName, arguments);
        MatrixMCPModels.MCPToolResult result = mcpServer.callTool(toolName, arguments, authContext);

        // Log result (already logged in MatrixAssistantMCPServer, but can add log here
        // too)
        if (result.isError()) {
            log.warn("MCP tool {} returned error in adapter", toolName);
        } else {
            log.debug("MCP tool {} completed successfully in adapter", toolName);
        }

        return result;
    }
}
