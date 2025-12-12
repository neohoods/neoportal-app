package com.neohoods.portal.platform.assistant.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Duration;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.assistant.model.SpaceStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for managing Mistral Agents API.
 * Creates and manages reusable agents for different workflow steps.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MistralAgentsService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${neohoods.portal.matrix.assistant.ai.api-key}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.ai.model}")
    private String model;

    private static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";

    // Cache of created agent IDs by agent name
    private final Map<String, String> agentIdsCache = new ConcurrentHashMap<>();

    /**
     * Represents a Mistral Agent configuration
     */
    public static class AgentConfig {
        private String name;
        private String description;
        private String instructions;
        private List<Map<String, Object>> tools;
        private List<String> libraryIds;
        private Map<String, Object> completionArgs;

        public AgentConfig(String name, String description, String instructions) {
            this.name = name;
            this.description = description;
            this.instructions = instructions;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getInstructions() {
            return instructions;
        }

        public void setInstructions(String instructions) {
            this.instructions = instructions;
        }

        public List<Map<String, Object>> getTools() {
            return tools;
        }

        public void setTools(List<Map<String, Object>> tools) {
            this.tools = tools;
        }

        public List<String> getLibraryIds() {
            return libraryIds;
        }

        public void setLibraryIds(List<String> libraryIds) {
            this.libraryIds = libraryIds;
        }

        public Map<String, Object> getCompletionArgs() {
            return completionArgs;
        }

        public void setCompletionArgs(Map<String, Object> completionArgs) {
            this.completionArgs = completionArgs;
        }
    }

    /**
     * Creates a Mistral agent
     * 
     * @param config Agent configuration
     * @return Agent ID
     */
    public Mono<String> createAgent(AgentConfig config) {
        // Configure HttpClient with longer timeouts for Mistral API
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
        requestBody.put("model", model);
        requestBody.put("name", config.getName());
        requestBody.put("description", config.getDescription());
        requestBody.put("instructions", config.getInstructions());

        if (config.getTools() != null && !config.getTools().isEmpty()) {
            requestBody.put("tools", config.getTools());
        }

        // Note: Some models (like devstral-medium-latest) don't support builtin
        // connectors
        // We'll try with document_library first, and if it fails with code 3004, retry
        // without it
        boolean includeDocumentLibrary = config.getLibraryIds() != null && !config.getLibraryIds().isEmpty();
        if (includeDocumentLibrary) {
            // Add document_library tool with library_ids
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) requestBody.getOrDefault("tools",
                    new java.util.ArrayList<>());
            Map<String, Object> documentLibraryTool = new HashMap<>();
            documentLibraryTool.put("type", "document_library");
            documentLibraryTool.put("library_ids", config.getLibraryIds());
            tools.add(documentLibraryTool);
            requestBody.put("tools", tools);
        }

        if (config.getCompletionArgs() != null && !config.getCompletionArgs().isEmpty()) {
            requestBody.put("completion_args", config.getCompletionArgs());
        }

        // Log detailed request for troubleshooting
        try {
            String requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("🤖 Creating Mistral Agent: {}", config.getName());
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("📋 Request Details:");
            log.info("  • Model: {}", model);
            log.info("  • Name: {}", config.getName());
            log.info("  • Description: {}", config.getDescription());
            log.info("  • Instructions length: {} characters",
                    config.getInstructions() != null ? config.getInstructions().length() : 0);
            log.info("  • Tools count: {}",
                    config.getTools() != null ? config.getTools().size() : 0);
            log.info("  • Library IDs: {}",
                    config.getLibraryIds() != null ? config.getLibraryIds() : "none");
            log.info("  • Completion args: {}",
                    config.getCompletionArgs() != null ? config.getCompletionArgs() : "none");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("📤 Full Request Body:\n{}", requestJson);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } catch (Exception e) {
            log.warn("Failed to serialize request body for logging: {}", e.getMessage());
        }

        return webClient.post()
                .uri("/agents")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    String agentId = (String) response.get("id");
                    if (agentId == null || agentId.isEmpty()) {
                        log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        log.error("❌ Agent creation failed: No ID returned");
                        log.error("  • Agent name: {}", config.getName());
                        log.error("  • Response: {}", response);
                        log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        throw new RuntimeException(
                                "Agent creation failed: no ID returned for agent " + config.getName());
                    }
                    agentIdsCache.put(config.getName(), agentId);
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.info("✅ Successfully created Mistral Agent: {}", config.getName());
                    log.info("  • Agent ID: {}", agentId);
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    return agentId;
                })
                .onErrorResume(e -> {
                    log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.error("❌ Failed to create Mistral Agent: {}", config.getName());
                    log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.error("📋 Error Details:");
                    log.error("  • Error type: {}", e.getClass().getSimpleName());
                    log.error("  • Error message: {}", e.getMessage());

                    // Check if error is due to builtin connectors not supported (code 3004)
                    boolean isBuiltinConnectorError = false;
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException webClientEx = (org.springframework.web.reactive.function.client.WebClientResponseException) e;
                        log.error("  • HTTP Status: {} {}",
                                webClientEx.getStatusCode().value(),
                                webClientEx.getStatusCode());
                        log.error("  • Response headers: {}", webClientEx.getHeaders());
                        String responseBody = webClientEx.getResponseBodyAsString();
                        if (responseBody != null && !responseBody.isEmpty()) {
                            try {
                                // Try to pretty-print JSON response if possible
                                Object responseObj = objectMapper.readValue(responseBody, Object.class);
                                String prettyResponse = objectMapper.writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(responseObj);
                                log.error(
                                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                log.error("📥 Error Response Body (formatted):\n{}", prettyResponse);
                                log.error(
                                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                                // Check if error code is 3004 (builtin connectors not supported)
                                @SuppressWarnings("unchecked")
                                Map<String, Object> errorMap = (Map<String, Object>) responseObj;
                                Object errorCode = errorMap.get("code");
                                if (errorCode != null && errorCode.toString().equals("3004")) {
                                    isBuiltinConnectorError = true;
                                }
                            } catch (Exception parseEx) {
                                log.error("  • Response body (raw): {}", responseBody);
                            }
                        } else {
                            log.error("  • Response body: (empty or null)");
                        }
                    }

                    // If error is due to builtin connectors and we included document_library, retry
                    // without it
                    if (isBuiltinConnectorError && includeDocumentLibrary) {
                        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        log.warn("⚠️  Model {} does not support builtin connectors (document_library)", model);
                        log.warn("🔄 Retrying agent creation without document_library...");
                        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                        // Remove document_library from tools and retry
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> tools = (List<Map<String, Object>>) requestBody.get("tools");
                        if (tools != null) {
                            tools.removeIf(tool -> "document_library".equals(tool.get("type")));
                            requestBody.put("tools", tools);
                        }

                        // Retry without document_library
                        return webClient.post()
                                .uri("/agents")
                                .bodyValue(requestBody)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .map(response -> {
                                    @SuppressWarnings("unchecked")
                                    String agentId = (String) response.get("id");
                                    if (agentId == null || agentId.isEmpty()) {
                                        throw new RuntimeException(
                                                "Agent creation failed: no ID returned for agent " + config.getName());
                                    }
                                    agentIdsCache.put(config.getName(), agentId);
                                    log.info(
                                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                    log.info("✅ Successfully created Mistral Agent: {} (without document_library)",
                                            config.getName());
                                    log.info("  • Agent ID: {}", agentId);
                                    log.warn("  ⚠️  Note: Document library not available for this model");
                                    log.info(
                                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                    return agentId;
                                })
                                .onErrorResume(retryError -> {
                                    log.error(
                                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                    log.error("❌ Retry also failed for agent: {}", config.getName());
                                    log.error("  • Error: {}", retryError.getMessage());
                                    log.error(
                                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                    return Mono.error(new RuntimeException("Failed to create Mistral agent "
                                            + config.getName() + " even without document_library", retryError));
                                });
                    }

                    log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.error("Stack trace:", e);
                    return Mono.error(new RuntimeException("Failed to create Mistral agent " + config.getName(), e));
                });
    }

    /**
     * Gets an agent ID by name (from cache)
     * 
     * @param agentName Agent name
     * @return Agent ID or null if not found
     */
    public String getAgentId(String agentName) {
        return agentIdsCache.get(agentName);
    }

    /**
     * Gets agent ID for a space step
     * 
     * @param step Space step
     * @return Agent ID or null if not found
     */
    public String getAgentIdForStep(SpaceStep step) {
        String agentName = getAgentNameForStep(step);
        return agentIdsCache.get(agentName);
    }

    /**
     * Gets agent name for a space step
     * 
     * @param step Space step
     * @return Agent name
     */
    public static String getAgentNameForStep(SpaceStep step) {
        return step.name() + "_AGENT";
    }

    /**
     * Gets routing agent name
     */
    public static String getRoutingAgentName() {
        return "ROUTING_AGENT";
    }

    /**
     * Checks if an agent exists in cache
     * 
     * @param agentName Agent name
     * @return true if agent exists
     */
    public boolean hasAgent(String agentName) {
        return agentIdsCache.containsKey(agentName);
    }

    /**
     * Clears the agent cache (useful for testing)
     */
    public void clearCache() {
        agentIdsCache.clear();
    }
}
