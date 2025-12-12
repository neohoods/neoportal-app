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

        if (config.getLibraryIds() != null && !config.getLibraryIds().isEmpty()) {
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

        log.info("Creating Mistral agent: {} (model: {})", config.getName(), model);

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
                    log.info("✓ Created Mistral agent: {} with ID: {}", config.getName(), agentId);
                    return agentId;
                })
                .onErrorResume(e -> {
                    log.error("Failed to create Mistral agent {}: {}", config.getName(), e.getMessage(), e);
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
