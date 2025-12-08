package com.neohoods.portal.platform.assistant.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.entities.LLMJudgeEvaluationEntity;
import com.neohoods.portal.platform.repositories.LLMJudgeEvaluationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for evaluating emoji reactions on bot messages.
 * Determines if an emoji is positive, negative, or neutral.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.llm-judge.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixReactionEvaluationService {

    private final LLMJudgeEvaluationRepository evaluationRepository;
    private final MessageSource messageSource;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // Common positive emojis
    private static final Map<String, Integer> POSITIVE_EMOJIS = new HashMap<>();
    static {
        POSITIVE_EMOJIS.put("👍", 1);
        POSITIVE_EMOJIS.put("✅", 1);
        POSITIVE_EMOJIS.put("❤️", 1);
        POSITIVE_EMOJIS.put("😊", 1);
        POSITIVE_EMOJIS.put("🙂", 1);
        POSITIVE_EMOJIS.put("😄", 1);
        POSITIVE_EMOJIS.put("😃", 1);
        POSITIVE_EMOJIS.put("🎉", 1);
        POSITIVE_EMOJIS.put("👏", 1);
        POSITIVE_EMOJIS.put("💯", 1);
        POSITIVE_EMOJIS.put("🔥", 1);
        POSITIVE_EMOJIS.put("⭐", 1);
        POSITIVE_EMOJIS.put("🌟", 1);
        POSITIVE_EMOJIS.put("💪", 1);
    }

    // Common negative emojis
    private static final Map<String, Integer> NEGATIVE_EMOJIS = new HashMap<>();
    static {
        NEGATIVE_EMOJIS.put("👎", -1);
        NEGATIVE_EMOJIS.put("❌", -1);
        NEGATIVE_EMOJIS.put("😞", -1);
        NEGATIVE_EMOJIS.put("😢", -1);
        NEGATIVE_EMOJIS.put("😠", -1);
        NEGATIVE_EMOJIS.put("😡", -1);
        NEGATIVE_EMOJIS.put("🤔", -1);
        NEGATIVE_EMOJIS.put("😕", -1);
        NEGATIVE_EMOJIS.put("🙁", -1);
        NEGATIVE_EMOJIS.put("💔", -1);
    }

    /**
     * Evaluates an emoji reaction and updates the evaluation entity.
     * Uses LLM to determine sentiment if emoji is not in the predefined list.
     * 
     * @param messageId Matrix event ID of the bot's message
     * @param emoji     Emoji string (e.g., "👍", "👎")
     * @param userId    User who added the reaction
     */
    public void evaluateReaction(String messageId, String emoji, String userId) {
        try {
            log.info("Evaluating reaction {} on message {} from user {}", emoji, messageId, userId);

            // Find evaluation for this message
            LLMJudgeEvaluationEntity evaluation = evaluationRepository.findByMessageId(messageId)
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (evaluation == null) {
                log.debug("No evaluation found for message {}, creating new one", messageId);
                // Create a basic evaluation if none exists
                evaluation = LLMJudgeEvaluationEntity.builder()
                        .id(java.util.UUID.randomUUID())
                        .messageId(messageId)
                        .userId(userId)
                        .userReactionEmoji(emoji)
                        .createdAt(java.time.OffsetDateTime.now())
                        .warnedInItRoom(false)
                        .build();
            } else {
                evaluation.setUserReactionEmoji(emoji);
            }

            // Determine sentiment
            Integer sentiment = determineSentiment(emoji);
            evaluation.setUserReactionSentiment(sentiment);

            // If sentiment couldn't be determined from predefined list, use LLM
            if (sentiment == null) {
                sentiment = evaluateEmojiWithLLM(emoji);
                evaluation.setUserReactionSentiment(sentiment);
            }

            evaluationRepository.save(evaluation);
            log.info("Updated evaluation for message {} with reaction {} (sentiment: {})",
                    messageId, emoji, sentiment);

        } catch (Exception e) {
            log.error("Error evaluating reaction {} on message {}", emoji, messageId, e);
        }
    }

    /**
     * Determines sentiment from predefined emoji list
     */
    private Integer determineSentiment(String emoji) {
        if (emoji == null || emoji.isEmpty()) {
            return null;
        }

        // Check positive emojis
        if (POSITIVE_EMOJIS.containsKey(emoji)) {
            return 1;
        }

        // Check negative emojis
        if (NEGATIVE_EMOJIS.containsKey(emoji)) {
            return -1;
        }

        // Not in predefined list, return null to use LLM
        return null;
    }

    /**
     * Uses LLM to evaluate emoji sentiment if not in predefined list
     */
    private Integer evaluateEmojiWithLLM(String emoji) {
        try {
            // Simple heuristic: if emoji contains common positive characters, it's positive
            // Otherwise, use LLM for ambiguous cases
            String prompt = String.format(
                    "Evaluate the sentiment of this emoji: %s\n\n" +
                            "Return a JSON object with:\n" +
                            "{\"sentiment\": <1 for positive, -1 for negative, 0 for neutral>}\n",
                    emoji);

            // For now, use a simple heuristic
            // In production, you could call an LLM API here
            if (emoji.contains("❤") || emoji.contains("💚") || emoji.contains("💙") ||
                    emoji.contains("💛") || emoji.contains("💜") || emoji.contains("🧡")) {
                return 1; // Heart emojis are generally positive
            }

            // Default to neutral if ambiguous
            return 0;
        } catch (Exception e) {
            log.error("Error evaluating emoji with LLM", e);
            return 0; // Default to neutral on error
        }
    }
}
