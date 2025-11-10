-- Migration to make residence_role NOT NULL with default value
-- First, update all NULL values to 'TENANT' (default)
UPDATE unit_members SET residence_role = 'TENANT' WHERE residence_role IS NULL;

-- Now make the column NOT NULL with default
ALTER TABLE unit_members 
    ALTER COLUMN residence_role SET NOT NULL,
    ALTER COLUMN residence_role SET DEFAULT 'TENANT';

