package com.neohoods.portal.platform.services.matrix.assistant;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.LLMJudgeEvaluationEntity;
import com.neohoods.portal.platform.repositories.LLMJudgeEvaluationRepository;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending notifications to IT room about low-scoring evaluations.
 * Runs periodically to check for low scores and send warnings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.llm-judge.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantLLMJudgeNotificationService {

    private final LLMJudgeEvaluationRepository evaluationRepository;
    private final MatrixAssistantService matrixAssistantService;
    private final MessageSource messageSource;

    @Value("${neohoods.portal.matrix.space-id}")
    private String spaceId;

    @Value("${neohoods.portal.matrix.assistant.llm-judge.low-score-threshold:60}")
    private Integer lowScoreThreshold;

    private static final String IT_ROOM_NAME = "IT";

    /**
     * Checks for low-scoring evaluations and sends warnings to IT room.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkAndWarnLowScores() {
        if (!isEnabled()) {
            return;
        }

        try {
            List<LLMJudgeEvaluationEntity> lowScores = evaluationRepository.findLowScoreUnwarned(lowScoreThreshold);

            if (lowScores.isEmpty()) {
                return;
            }

            log.info("Found {} low-scoring evaluations to warn about", lowScores.size());

            // Get IT room ID
            Optional<String> itRoomId = matrixAssistantService.getRoomIdByName(IT_ROOM_NAME, spaceId);
            if (itRoomId.isEmpty()) {
                log.warn("IT room not found, cannot send warnings");
                return;
            }

            // Send warning for each low score
            for (LLMJudgeEvaluationEntity evaluation : lowScores) {
                sendWarningToItRoom(itRoomId.get(), evaluation);
                evaluation.setWarnedInItRoom(true);
                evaluationRepository.save(evaluation);
            }

        } catch (Exception e) {
            log.error("Error checking and warning about low scores", e);
        }
    }

    /**
     * Sends a warning message to IT room about a low-scoring evaluation
     */
    private void sendWarningToItRoom(String itRoomId, LLMJudgeEvaluationEntity evaluation) {
        try {
            String warningMessage = buildWarningMessage(evaluation);
            boolean sent = matrixAssistantService.sendMessage(itRoomId, warningMessage);
            if (sent) {
                log.info("Sent warning to IT room about low score for message {}", evaluation.getMessageId());
            } else {
                log.error("Failed to send warning to IT room for message {}", evaluation.getMessageId());
            }
        } catch (Exception e) {
            log.error("Error sending warning to IT room", e);
        }
    }

    /**
     * Builds the warning message for IT room
     */
    private String buildWarningMessage(LLMJudgeEvaluationEntity evaluation) {
        return String.format(
                "⚠️ **Low Score Alert**\n\n" +
                        "**Score:** %d/100 (threshold: %d)\n" +
                        "**Room:** %s\n" +
                        "**User:** %s\n" +
                        "**Question:** %s\n" +
                        "**Response:** %s\n" +
                        "**Feedback:** %s\n" +
                        "**Message ID:** %s\n",
                evaluation.getEvaluationScore() != null ? evaluation.getEvaluationScore() : 0,
                lowScoreThreshold,
                evaluation.getRoomId(),
                evaluation.getUserId(),
                truncate(evaluation.getUserQuestion(), 200),
                truncate(evaluation.getBotResponse(), 200),
                truncate(evaluation.getEvaluationFeedback(), 300),
                evaluation.getMessageId());
    }

    /**
     * Truncates text to max length
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "N/A";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Checks if the service is enabled
     */
    private boolean isEnabled() {
        // Service is enabled if the conditional property is true
        return true;
    }
}
