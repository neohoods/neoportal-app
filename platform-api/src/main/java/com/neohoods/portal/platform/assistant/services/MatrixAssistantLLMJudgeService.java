package com.neohoods.portal.platform.assistant.services;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.entities.LLMJudgeEvaluationEntity;
import com.neohoods.portal.platform.repositories.LLMJudgeEvaluationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for LLM-as-a-Judge evaluation of AI assistant responses.
 * Evaluates responses asynchronously and stores evaluations in the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.llm-judge.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantLLMJudgeService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final MessageSource messageSource;
    private final LLMJudgeEvaluationRepository evaluationRepository;

    @Value("${neohoods.portal.matrix.assistant.llm-judge.provider:${neohoods.portal.matrix.assistant.ai.provider}}")
    private String provider;

    @Value("${neohoods.portal.matrix.assistant.llm-judge.api-key:${neohoods.portal.matrix.assistant.ai.api-key}}")
    private String apiKey;

    @Value("${neohoods.portal.matrix.assistant.llm-judge.model:${neohoods.portal.matrix.assistant.ai.model}}")
    private String model;

    @Value("${neohoods.portal.matrix.assistant.llm-judge.enabled}")
    private boolean judgeEnabled;

    @Value("${neohoods.portal.matrix.assistant.llm-judge.low-score-threshold:60}")
    private Integer lowScoreThreshold; // Default: 60

    private static final String MISTRAL_API_BASE_URL = "https://api.mistral.ai/v1";

    /**
     * Evaluates an AI assistant response using LLM-as-a-Judge.
     * This is called asynchronously after a response is sent.
     * 
     * @param roomId Room ID where the response was sent
     * @param userId User ID who asked the question
     * @param messageId Matrix event ID of the bot's response message
     * @param userQuestion Original user question
     * @param botResponse Bot's response
     * @return Mono that completes when evaluation is stored
     */
    public Mono<Void> evaluateResponseAsync(
            String roomId,
            String userId,
            String messageId,
            String userQuestion,
            String botResponse) {
        
        if (!judgeEnabled) {
            log.debug("LLM-as-a-Judge is disabled, skipping evaluation");
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                log.info("Evaluating response for message {} in room {}", messageId, roomId);
                
                // Call LLM-as-a-Judge
                LLMJudgeEvaluation evaluation = evaluateWithLLM(userQuestion, botResponse);
                
                // Store evaluation
                LLMJudgeEvaluationEntity entity = LLMJudgeEvaluationEntity.builder()
                        .id(UUID.randomUUID())
                        .roomId(roomId)
                        .userId(userId)
                        .messageId(messageId)
                        .userQuestion(userQuestion)
                        .botResponse(botResponse)
                        .evaluationScore(evaluation.getScore())
                        .correctnessScore(evaluation.getCorrectness())
                        .clarityScore(evaluation.getClarity())
                        .completenessScore(evaluation.getCompleteness())
                        .evaluationFeedback(evaluation.getFeedback())
                        .issues(evaluation.getIssuesJson())
                        .evaluatedAt(OffsetDateTime.now())
                        .createdAt(OffsetDateTime.now())
                        .warnedInItRoom(false)
                        .build();
                
                evaluationRepository.save(entity);
                log.info("Stored LLM-as-a-Judge evaluation for message {}: score {}", messageId, evaluation.getScore());
                
                // Check if we need to warn in IT room (low score)
                if (evaluation.getScore() < lowScoreThreshold) {
                    log.warn("Low score detected for message {}: {} (threshold: {})", messageId, evaluation.getScore(), lowScoreThreshold);
                    // Warning will be sent by MatrixAssistantLLMJudgeNotificationService
                }
            } catch (Exception e) {
                log.error("Error evaluating response with LLM-as-a-Judge", e);
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
          .then();
    }

    /**
     * Evaluates a response using LLM-as-a-Judge API
     */
    private LLMJudgeEvaluation evaluateWithLLM(String userQuestion, String botResponse) {
        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(MISTRAL_API_BASE_URL)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            // Build evaluation prompt
            String evaluationPrompt = buildEvaluationPrompt(userQuestion, botResponse);

            // Prepare request
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", List.of(
                    Map.of("role", "system", "content", getSystemPrompt()),
                    Map.of("role", "user", "content", evaluationPrompt)
            ));
            request.put("response_format", Map.of("type", "json_object"));
            request.put("temperature", 0.3); // Lower temperature for more consistent evaluations

            // Call API
            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse response
            Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                String content = (String) message.get("content");
                
                // Parse JSON response
                return parseEvaluationResponse(content);
            }

            throw new RuntimeException("No response from LLM-as-a-Judge");
        } catch (Exception e) {
            log.error("Error calling LLM-as-a-Judge API", e);
            // Return default evaluation on error
            return LLMJudgeEvaluation.builder()
                    .score(50)
                    .correctness(50)
                    .clarity(50)
                    .completeness(50)
                    .feedback("Error evaluating response: " + e.getMessage())
                    .issuesJson("[]")
                    .build();
        }
    }

    /**
     * Builds the evaluation prompt for LLM-as-a-Judge
     */
    private String buildEvaluationPrompt(String userQuestion, String botResponse) {
        return String.format(
                "Evaluate the following AI assistant response:\n\n" +
                "User Question: %s\n\n" +
                "Bot Response: %s\n\n" +
                "Please evaluate this response and return a JSON object with the following structure:\n" +
                "{\n" +
                "  \"score\": <0-100 overall score>,\n" +
                "  \"correctness\": <0-100 score for factual accuracy>,\n" +
                "  \"clarity\": <0-100 score for clarity and readability>,\n" +
                "  \"completeness\": <0-100 score for completeness of answer>,\n" +
                "  \"feedback\": \"<detailed feedback>\",\n" +
                "  \"issues\": [\"<issue1>\", \"<issue2>\", ...]\n" +
                "}",
                userQuestion, botResponse);
    }

    /**
     * Gets the system prompt for LLM-as-a-Judge
     */
    private String getSystemPrompt() {
        return "You are an expert evaluator of AI assistant responses. " +
               "Evaluate responses based on:\n" +
               "1. Correctness: Is the information accurate and factually correct?\n" +
               "2. Clarity: Is the response clear and easy to understand?\n" +
               "3. Completeness: Does the response fully answer the user's question?\n" +
               "4. Helpfulness: Is the response helpful and actionable?\n\n" +
               "Return your evaluation as a JSON object with scores (0-100) and detailed feedback.";
    }

    /**
     * Parses the JSON response from LLM-as-a-Judge
     */
    @SuppressWarnings("unchecked")
    private LLMJudgeEvaluation parseEvaluationResponse(String jsonContent) {
        try {
            Map<String, Object> evaluation = objectMapper.readValue(jsonContent, Map.class);
            
            return LLMJudgeEvaluation.builder()
                    .score(((Number) evaluation.getOrDefault("score", 50)).intValue())
                    .correctness(((Number) evaluation.getOrDefault("correctness", 50)).intValue())
                    .clarity(((Number) evaluation.getOrDefault("clarity", 50)).intValue())
                    .completeness(((Number) evaluation.getOrDefault("completeness", 50)).intValue())
                    .feedback((String) evaluation.getOrDefault("feedback", "No feedback provided"))
                    .issuesJson(objectMapper.writeValueAsString(evaluation.getOrDefault("issues", List.of())))
                    .build();
        } catch (Exception e) {
            log.error("Error parsing evaluation response", e);
            return LLMJudgeEvaluation.builder()
                    .score(50)
                    .correctness(50)
                    .clarity(50)
                    .completeness(50)
                    .feedback("Error parsing evaluation: " + e.getMessage())
                    .issuesJson("[]")
                    .build();
        }
    }

    /**
     * Gets evaluation statistics for a report
     */
    public EvaluationReport generateReport(OffsetDateTime since) {
        List<LLMJudgeEvaluationEntity> evaluations = evaluationRepository.findByEvaluatedAtBetween(
                since, OffsetDateTime.now());
        
        if (evaluations.isEmpty()) {
            return EvaluationReport.builder()
                    .totalEvaluations(0L)
                    .averageScore(0.0)
                    .lowScoreCount(0L)
                    .evaluations(List.of())
                    .build();
        }

        double avgScore = evaluations.stream()
                .mapToInt(e -> e.getEvaluationScore() != null ? e.getEvaluationScore() : 50)
                .average()
                .orElse(0.0);

        long lowScoreCount = evaluations.stream()
                .filter(e -> e.getEvaluationScore() != null && e.getEvaluationScore() < lowScoreThreshold)
                .count();

        return EvaluationReport.builder()
                .totalEvaluations((long) evaluations.size())
                .averageScore(avgScore)
                .lowScoreCount(lowScoreCount)
                .evaluations(evaluations)
                .build();
    }

    /**
     * Internal class for evaluation result
     */
    @lombok.Data
    @lombok.Builder
    private static class LLMJudgeEvaluation {
        private Integer score;
        private Integer correctness;
        private Integer clarity;
        private Integer completeness;
        private String feedback;
        private String issuesJson;
    }

    /**
     * Report data structure
     */
    @lombok.Data
    @lombok.Builder
    public static class EvaluationReport {
        private Long totalEvaluations;
        private Double averageScore;
        private Long lowScoreCount;
        private List<LLMJudgeEvaluationEntity> evaluations;
    }
}

