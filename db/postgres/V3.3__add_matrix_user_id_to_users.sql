-- Migration V3.3: Add matrix_user_id column to users table
-- This column stores the Matrix user ID associated with each user account

ALTER TABLE users
ADD COLUMN IF NOT EXISTS matrix_user_id VARCHAR(255);

-- Add comment for documentation
COMMENT ON COLUMN users.matrix_user_id IS 'Matrix user ID (e.g., @username:chat.neohoods.com) associated with this user account';



