-- Add UNIT_JOIN_REQUEST and RESERVATION to notifications type CHECK constraint
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'ADMIN_NEW_USER',
    'NEW_ANNOUNCEMENT',
    'RESERVATION',
    'UNIT_INVITATION',
    'UNIT_JOIN_REQUEST'
));

