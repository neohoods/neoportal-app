package com.neohoods.portal.platform.repositories;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.neohoods.portal.platform.entities.LLMJudgeEvaluationEntity;

public interface LLMJudgeEvaluationRepository extends CrudRepository<LLMJudgeEvaluationEntity, UUID> {
    
    List<LLMJudgeEvaluationEntity> findByRoomId(String roomId);
    
    List<LLMJudgeEvaluationEntity> findByUserId(String userId);
    
    List<LLMJudgeEvaluationEntity> findByMessageId(String messageId);
    
    List<LLMJudgeEvaluationEntity> findByEvaluatedAtBetween(OffsetDateTime start, OffsetDateTime end);
    
    @Query("SELECT e FROM LLMJudgeEvaluationEntity e WHERE e.evaluationScore < :threshold AND e.warnedInItRoom = false")
    List<LLMJudgeEvaluationEntity> findLowScoreUnwarned(@Param("threshold") Integer threshold);
    
    @Query("SELECT AVG(e.evaluationScore) FROM LLMJudgeEvaluationEntity e WHERE e.evaluatedAt >= :since")
    Double getAverageScoreSince(@Param("since") OffsetDateTime since);
    
    @Query("SELECT COUNT(e) FROM LLMJudgeEvaluationEntity e WHERE e.evaluatedAt >= :since")
    Long getCountSince(@Param("since") OffsetDateTime since);
}

