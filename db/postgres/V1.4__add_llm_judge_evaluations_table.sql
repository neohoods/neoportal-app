-- Migration V1.4: Add llm_judge_evaluations table for LLM-as-a-Judge evaluations
-- This table stores evaluations of AI assistant responses, including scores, feedback, and user reactions

CREATE TABLE IF NOT EXISTS llm_judge_evaluations (
    id UUID PRIMARY KEY,
    room_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    user_question TEXT,
    bot_response TEXT,
    evaluation_score INTEGER,
    correctness_score INTEGER,
    clarity_score INTEGER,
    completeness_score INTEGER,
    evaluation_feedback TEXT,
    issues TEXT,
    user_reaction_emoji VARCHAR(50),
    user_reaction_sentiment INTEGER,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    warned_in_it_room BOOLEAN DEFAULT FALSE
);

-- Add indexes for common queries
CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_message_id ON llm_judge_evaluations(message_id);
CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_room_id ON llm_judge_evaluations(room_id);
CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_user_id ON llm_judge_evaluations(user_id);
CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_evaluated_at ON llm_judge_evaluations(evaluated_at);
CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_score ON llm_judge_evaluations(evaluation_score);
CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_warned ON llm_judge_evaluations(warned_in_it_room) WHERE warned_in_it_room = FALSE;

-- Add comments for documentation
COMMENT ON TABLE llm_judge_evaluations IS 'Stores LLM-as-a-Judge evaluations of AI assistant responses';
COMMENT ON COLUMN llm_judge_evaluations.evaluation_score IS 'Overall score from LLM-as-a-Judge (0-100)';
COMMENT ON COLUMN llm_judge_evaluations.user_reaction_sentiment IS 'Sentiment of user emoji reaction: -1 (negative), 0 (neutral), 1 (positive)';
COMMENT ON COLUMN llm_judge_evaluations.warned_in_it_room IS 'Whether a warning was sent to IT room for low score';

