-- Create reservation_feedback table
CREATE TABLE reservation_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    cleanliness INTEGER CHECK (cleanliness >= 1 AND cleanliness <= 5),
    communication INTEGER CHECK (communication >= 1 AND communication <= 5),
    value INTEGER CHECK (value >= 1 AND value <= 5),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_reservation_feedback_reservation 
        FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_feedback_user 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- Unique constraint: one feedback per reservation
    CONSTRAINT uk_reservation_feedback_reservation 
        UNIQUE (reservation_id),
    
    -- Check constraints for optional ratings
    CONSTRAINT chk_cleanliness_range CHECK (cleanliness IS NULL OR (cleanliness >= 1 AND cleanliness <= 5)),
    CONSTRAINT chk_communication_range CHECK (communication IS NULL OR (communication >= 1 AND communication <= 5)),
    CONSTRAINT chk_value_range CHECK (value IS NULL OR (value >= 1 AND value <= 5))
);

-- Create indexes for better performance
CREATE INDEX idx_reservation_feedback_reservation_id ON reservation_feedback(reservation_id);
CREATE INDEX idx_reservation_feedback_user_id ON reservation_feedback(user_id);
CREATE INDEX idx_reservation_feedback_submitted_at ON reservation_feedback(submitted_at);

-- Create a function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_reservation_feedback_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to automatically update updated_at
CREATE TRIGGER tr_reservation_feedback_updated_at
    BEFORE UPDATE ON reservation_feedback
    FOR EACH ROW
    EXECUTE FUNCTION update_reservation_feedback_updated_at();

-- Add some sample feedback data (optional)
INSERT INTO reservation_feedback (reservation_id, user_id, rating, comment, cleanliness, communication, value) 
SELECT 
    r.id,
    r.user_id,
    CASE 
        WHEN random() < 0.3 THEN 5
        WHEN random() < 0.6 THEN 4
        WHEN random() < 0.8 THEN 3
        WHEN random() < 0.95 THEN 2
        ELSE 1
    END as rating,
    CASE 
        WHEN random() < 0.7 THEN 'Great experience!'
        WHEN random() < 0.9 THEN 'Good overall'
        ELSE 'Could be better'
    END as comment,
    CASE 
        WHEN random() < 0.8 THEN 
            CASE 
                WHEN random() < 0.3 THEN 5
                WHEN random() < 0.6 THEN 4
                WHEN random() < 0.8 THEN 3
                ELSE 2
            END
        ELSE NULL
    END as cleanliness,
    CASE 
        WHEN random() < 0.7 THEN 
            CASE 
                WHEN random() < 0.4 THEN 5
                WHEN random() < 0.7 THEN 4
                ELSE 3
            END
        ELSE NULL
    END as communication,
    CASE 
        WHEN random() < 0.6 THEN 
            CASE 
                WHEN random() < 0.3 THEN 5
                WHEN random() < 0.6 THEN 4
                ELSE 3
            END
        ELSE NULL
    END as value
FROM reservations r 
WHERE r.status = 'COMPLETED' 
    AND random() < 0.3  -- Only 30% of completed reservations have feedback
LIMIT 10;
