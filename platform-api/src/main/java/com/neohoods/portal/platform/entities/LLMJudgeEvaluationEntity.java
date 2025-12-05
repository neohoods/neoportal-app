package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing LLM-as-a-Judge evaluations of AI assistant responses.
 * Stores evaluations of responses, including scores, feedback, and user reactions.
 */
@Entity
@Table(name = "llm_judge_evaluations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMJudgeEvaluationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false, length = 255)
    private String roomId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId; // Matrix event ID of the bot's response message

    @Column(name = "user_question", columnDefinition = "TEXT")
    private String userQuestion;

    @Column(name = "bot_response", columnDefinition = "TEXT")
    private String botResponse;

    @Column(name = "evaluation_score")
    private Integer evaluationScore; // 0-100 score from LLM-as-a-Judge

    @Column(name = "correctness_score")
    private Integer correctnessScore; // 0-100

    @Column(name = "clarity_score")
    private Integer clarityScore; // 0-100

    @Column(name = "completeness_score")
    private Integer completenessScore; // 0-100

    @Column(name = "evaluation_feedback", columnDefinition = "TEXT")
    private String evaluationFeedback; // Detailed feedback from LLM-as-a-Judge

    @Column(name = "issues", columnDefinition = "TEXT")
    private String issues; // JSON array of issues found

    @Column(name = "user_reaction_emoji", length = 50)
    private String userReactionEmoji; // Emoji from user reaction (e.g., "👍", "👎")

    @Column(name = "user_reaction_sentiment")
    private Integer userReactionSentiment; // -1 (negative), 0 (neutral), 1 (positive)

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "warned_in_it_room")
    private Boolean warnedInItRoom; // Whether a warning was sent to IT room for low score
}

