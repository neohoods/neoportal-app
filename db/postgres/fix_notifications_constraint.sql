-- Fix notifications_type_check constraint to include UNIT_JOIN_REQUEST
-- This script updates the constraint to match the current init.sql definition

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'ADMIN_NEW_USER',
    'NEW_ANNOUNCEMENT',
    'RESERVATION',
    'UNIT_INVITATION',
    'UNIT_JOIN_REQUEST'
));

