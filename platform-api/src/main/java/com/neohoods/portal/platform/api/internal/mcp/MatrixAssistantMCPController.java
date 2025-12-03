package com.neohoods.portal.platform.api.internal.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neohoods.portal.platform.services.matrix.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantAuthContextService;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer.MCPTool;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer.MCPToolResult;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Contrôleur REST pour le serveur MCP (Model Context Protocol).
 * Expose les endpoints MCP standards pour permettre au LLM d'appeler les
 * outils.
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.mcp.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantMCPController {

    private final MatrixAssistantMCPServer mcpServer;
    private final MatrixAssistantAuthContextService authContextService;

    /**
     * Endpoint MCP: Liste tous les outils disponibles
     * POST /mcp/tools/list
     */
    @PostMapping(value = "/tools/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MCPListToolsResponse>> listTools(
            @RequestHeader(value = "X-Matrix-User-Id", required = false) String matrixUserId,
            @RequestHeader(value = "X-Matrix-Room-Id", required = false) String roomId,
            @RequestHeader(value = "X-Matrix-Is-DM", required = false) String isDmStr,
            @RequestBody(required = false) MCPRequest request) {

        log.debug("MCP listTools called by user: {} in room: {}", matrixUserId, roomId);

        List<MCPTool> tools = mcpServer.listTools();

        MCPListToolsResponse response = new MCPListToolsResponse();
        response.setJsonrpc("2.0");
        response.setId(request != null ? request.getId() : 1);
        response.setResult(Map.of("tools", tools));

        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * Endpoint MCP: Appelle un outil spécifique
     * POST /mcp/tools/call
     */
    @PostMapping(value = "/tools/call", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MCPCallToolResponse>> callTool(
            @RequestHeader("X-Matrix-User-Id") String matrixUserId,
            @RequestHeader("X-Matrix-Room-Id") String roomId,
            @RequestHeader("X-Matrix-Is-DM") String isDmStr,
            @RequestHeader(value = "X-MCP-Session-Id", required = false) String sessionId,
            @RequestBody MCPCallToolRequest request) {

        log.info("MCP callTool: {} called by user: {} in room: {} (DM: {})",
                request.getParams().getName(), matrixUserId, roomId, isDmStr);

        try {
            // Créer le contexte d'autorisation
            boolean isDM = "true".equalsIgnoreCase(isDmStr);
            MatrixAssistantAuthContext authContext = authContextService.createAuthContext(
                    matrixUserId, roomId, isDM);

            // Appeler l'outil MCP
            MCPToolResult result = mcpServer.callTool(
                    request.getParams().getName(),
                    request.getParams().getArguments(),
                    authContext);

            // Construire la réponse JSON-RPC 2.0
            MCPCallToolResponse response = new MCPCallToolResponse();
            response.setJsonrpc("2.0");
            response.setId(request.getId());

            if (result.isError()) {
                // Erreur selon JSON-RPC 2.0
                MCPError error = new MCPError();
                error.setCode(-32000); // Server error
                error.setMessage(result.getContent().isEmpty()
                        ? "Unknown error"
                        : result.getContent().get(0).getText());
                response.setError(error);
            } else {
                // Succès
                response.setResult(Map.of("content", result.getContent()));
            }

            return Mono.just(ResponseEntity.ok(response));
        } catch (MatrixAssistantAuthContext.UnauthorizedException e) {
            log.warn("Unauthorized MCP tool call: {}", e.getMessage());
            MCPCallToolResponse response = new MCPCallToolResponse();
            response.setJsonrpc("2.0");
            response.setId(request.getId());
            MCPError error = new MCPError();
            error.setCode(-32001); // Unauthorized
            error.setMessage(e.getMessage());
            response.setError(error);
            return Mono.just(ResponseEntity.status(403).body(response));
        } catch (Exception e) {
            log.error("Error calling MCP tool: {}", e.getMessage(), e);
            MCPCallToolResponse response = new MCPCallToolResponse();
            response.setJsonrpc("2.0");
            response.setId(request.getId());
            MCPError error = new MCPError();
            error.setCode(-32603); // Internal error
            error.setMessage("Internal error: " + e.getMessage());
            response.setError(error);
            return Mono.just(ResponseEntity.status(500).body(response));
        }
    }

    /**
     * Modèles pour les requêtes/réponses MCP (JSON-RPC 2.0)
     */
    @Data
    public static class MCPRequest {
        private String jsonrpc = "2.0";
        private Integer id;
        private String method;
    }

    @Data
    public static class MCPCallToolRequest {
        private String jsonrpc = "2.0";
        private Integer id;
        private String method = "tools/call";
        private MCPCallToolParams params;
    }

    @Data
    public static class MCPCallToolParams {
        private String name;
        private Map<String, Object> arguments;
    }

    @Data
    public static class MCPListToolsResponse {
        private String jsonrpc = "2.0";
        private Integer id;
        private Map<String, Object> result;
        private MCPError error;
    }

    @Data
    public static class MCPCallToolResponse {
        private String jsonrpc = "2.0";
        private Integer id;
        private Map<String, Object> result;
        private MCPError error;
    }

    @Data
    public static class MCPError {
        private Integer code;
        private String message;
        private Object data;
    }
}
