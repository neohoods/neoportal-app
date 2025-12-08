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

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAuthContextService;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPServer;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPTool;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels.MCPToolResult;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.JwtService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final UsersRepository usersRepository;
    private final JwtService jwtService;

    /**
     * Endpoint MCP: Liste tous les outils disponibles pour l'utilisateur
     * POST /mcp/tools/list
     * Les outils admin sont filtrés selon les rôles de l'utilisateur
     * Requiert un JWT token dans le header Authorization
     */
    @PostMapping(value = "/tools/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MCPListToolsResponse>> listTools(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Span-Id", required = false) String spanId,
            @RequestHeader(value = "X-Conversation-Trace-Id", required = false) String conversationTraceId,
            @RequestHeader(value = "X-Room-Id", required = false) String roomId,
            @RequestBody(required = false) MCPRequest request) {

        // Set tracing context in MDC for log correlation
        try {
            if (traceId != null) {
                MDC.put("traceId", traceId);
            }
            if (spanId != null) {
                MDC.put("spanId", spanId);
            }
            if (conversationTraceId != null) {
                MDC.put("conversationTraceId", conversationTraceId);
            }
            if (roomId != null) {
                MDC.put("roomId", roomId);
            }

            // JWT est requis, toutes les informations viennent du token
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new MatrixAssistantAuthContext.UnauthorizedException("JWT token required");
            }

            String token = authorization.substring("Bearer ".length());
            var claims = jwtService.verifyToken(token);
            String userIdStr = claims.getSubject();
            UserEntity user = usersRepository.findById(java.util.UUID.fromString(userIdStr))
                    .orElseThrow(() -> new MatrixAssistantAuthContext.UnauthorizedException("User not found"));
            String jwtMatrixUserId = (String) claims.getClaim("matrixUserId");
            String jwtRoomId = (String) claims.getClaim("roomId");
            Boolean jwtIsDM = (Boolean) claims.getClaim("isDM");
            MatrixAssistantAuthContext authContext = authContextService.createAuthContextFromUser(
                    user,
                    jwtMatrixUserId,
                    jwtRoomId,
                    jwtIsDM != null ? jwtIsDM : false);

            log.debug("MCP listTools called for user: {} (admin: {})",
                    authContext.getMatrixUserId(), mcpServer.isAdminUser(authContext));

            // Filtrer les outils selon les rôles de l'utilisateur
            List<MCPTool> tools = mcpServer.listToolsForUser(authContext);

            MCPListToolsResponse response = new MCPListToolsResponse();
            response.setJsonrpc("2.0");
            response.setId(request != null ? request.getId() : 1);
            response.setResult(Map.of("tools", tools));

            return Mono.just(ResponseEntity.ok(response));
        } catch (MatrixAssistantAuthContext.UnauthorizedException e) {
            log.warn("Unauthorized MCP listTools call: {}", e.getMessage());
            MCPListToolsResponse response = new MCPListToolsResponse();
            response.setJsonrpc("2.0");
            response.setId(request != null ? request.getId() : 1);
            MCPError error = new MCPError();
            error.setCode(-32001); // Unauthorized
            error.setMessage(e.getMessage());
            response.setError(error);
            return Mono.just(ResponseEntity.status(401).body(response));
        } catch (Exception e) {
            log.error("Error listing MCP tools: {}", e.getMessage(), e);
            MCPListToolsResponse response = new MCPListToolsResponse();
            response.setJsonrpc("2.0");
            response.setId(request != null ? request.getId() : 1);
            MCPError error = new MCPError();
            error.setCode(-32603); // Internal error
            error.setMessage("Internal error: " + e.getMessage());
            response.setError(error);
            return Mono.just(ResponseEntity.status(500).body(response));
        } finally {
            // Clean up MDC
            MDC.remove("traceId");
            MDC.remove("spanId");
            MDC.remove("conversationTraceId");
            MDC.remove("roomId");
        }
    }

    /**
     * Endpoint MCP: Appelle un outil spécifique
     * POST /mcp/tools/call
     * Requiert un JWT token dans le header Authorization
     */
    @PostMapping(value = "/tools/call", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MCPCallToolResponse>> callTool(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Span-Id", required = false) String spanId,
            @RequestHeader(value = "X-Conversation-Trace-Id", required = false) String conversationTraceId,
            @RequestHeader(value = "X-Room-Id", required = false) String roomId,
            @RequestBody MCPCallToolRequest request) {

        // Set tracing context in MDC for log correlation
        try {
            if (traceId != null) {
                MDC.put("traceId", traceId);
            }
            if (spanId != null) {
                MDC.put("spanId", spanId);
            }
            if (conversationTraceId != null) {
                MDC.put("conversationTraceId", conversationTraceId);
            }
            if (roomId != null) {
                MDC.put("roomId", roomId);
            }

            log.info("MCP callTool: {} called", request.getParams().getName());

            // JWT est requis, toutes les informations viennent du token
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new MatrixAssistantAuthContext.UnauthorizedException("JWT token required");
            }

            String token = authorization.substring("Bearer ".length());
            var claims = jwtService.verifyToken(token);
            String userIdStr = claims.getSubject();
            UserEntity user = usersRepository.findById(java.util.UUID.fromString(userIdStr))
                    .orElseThrow(() -> new MatrixAssistantAuthContext.UnauthorizedException("User not found"));
            String jwtMatrixUserId = (String) claims.getClaim("matrixUserId");
            String jwtRoomId = (String) claims.getClaim("roomId");
            Boolean jwtIsDM = (Boolean) claims.getClaim("isDM");
            MatrixAssistantAuthContext authContext = authContextService.createAuthContextFromUser(
                    user,
                    jwtMatrixUserId,
                    jwtRoomId,
                    jwtIsDM != null ? jwtIsDM : false);

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
            return Mono.just(ResponseEntity.status(401).body(response));
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
        } finally {
            // Clean up MDC
            MDC.remove("traceId");
            MDC.remove("spanId");
            MDC.remove("conversationTraceId");
            MDC.remove("roomId");
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
