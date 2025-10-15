-- Additional test data for improved test coverage
-- This file adds more comprehensive data for testing

-- Additional spaces with different configurations for testing
INSERT INTO spaces (id, name, description, instructions, type, status, tenant_price, owner_price, cleaning_fee, deposit, currency, min_duration_days, max_duration_days, requires_apartment_access, max_annual_reservations, used_annual_reservations, allowed_hours_start, allowed_hours_end, digital_lock_id, access_code_enabled, enable_notifications, created_at, updated_at) VALUES 
-- Additional guest room with quota limits
('550e8400-e29b-41d4-a716-446655440115', 'Chambre d''hôte Premium', 'Chambre d''hôte premium avec quota annuel limité', 'Chambre premium avec quota de 3 réservations par an', 'GUEST_ROOM', 'ACTIVE', 15.00, 5.00, 75.00, 50.00, 'EUR', 1, 5, true, 3, 0, '14:00', '12:00', '550e8400-e29b-41d4-a716-446655440001', true, true, '2024-01-10 09:00:00+00', '2024-01-15 10:30:00+00'),
-- Additional coworking space with different pricing
('550e8400-e29b-41d4-a716-446655440116', 'Bureau coworking C', 'Bureau coworking avec prix différent', 'Bureau avec prix spécial', 'COWORKING', 'ACTIVE', 12.00, 6.00, 0.00, 0.00, 'EUR', 1, 3, false, 0, 0, '09:00', '18:00', '550e8400-e29b-41d4-a716-446655440004', true, true, '2024-01-10 09:00:00+00', '2024-01-13 14:20:00+00'),
-- Space with zero quota (for testing quota validation)
('550e8400-e29b-41d4-a716-446655440117', 'Espace Test Quota Zero', 'Espace avec quota zéro pour tests', 'Espace de test avec quota zéro', 'COMMON_ROOM', 'ACTIVE', 25.00, 15.00, 0.00, 0.00, 'EUR', 1, 1, false, 0, 0, '08:00', '22:00', '550e8400-e29b-41d4-a716-446655440002', true, true, '2024-01-10 09:00:00+00', '2024-01-15 10:30:00+00'),
-- Space with high quota for testing
('550e8400-e29b-41d4-a716-446655440118', 'Espace Test Quota Elevé', 'Espace avec quota élevé pour tests', 'Espace de test avec quota élevé', 'GUEST_ROOM', 'ACTIVE', 20.00, 10.00, 100.00, 0.00, 'EUR', 1, 10, true, 20, 0, '15:00', '11:00', '550e8400-e29b-41d4-a716-446655440001', true, true, '2024-01-10 09:00:00+00', '2024-01-15 10:30:00+00'),
-- Space with different time slots for testing
('550e8400-e29b-41d4-a716-446655440119', 'Espace Test Horaires', 'Espace avec horaires spéciaux', 'Espace de test avec horaires différents', 'COMMON_ROOM', 'ACTIVE', 30.00, 20.00, 0.00, 0.00, 'EUR', 1, 2, false, 0, 0, '10:00', '16:00', '550e8400-e29b-41d4-a716-446655440002', true, true, '2024-01-10 09:00:00+00', '2024-01-15 10:30:00+00');

-- Add allowed days for new spaces
INSERT INTO space_allowed_days (space_id, day_of_week) VALUES 
-- Premium guest room (all days)
('550e8400-e29b-41d4-a716-446655440115', 'MONDAY'),
('550e8400-e29b-41d4-a716-446655440115', 'TUESDAY'),
('550e8400-e29b-41d4-a716-446655440115', 'WEDNESDAY'),
('550e8400-e29b-41d4-a716-446655440115', 'THURSDAY'),
('550e8400-e29b-41d4-a716-446655440115', 'FRIDAY'),
('550e8400-e29b-41d4-a716-446655440115', 'SATURDAY'),
('550e8400-e29b-41d4-a716-446655440115', 'SUNDAY'),
-- Coworking C (weekdays only)
('550e8400-e29b-41d4-a716-446655440116', 'MONDAY'),
('550e8400-e29b-41d4-a716-446655440116', 'TUESDAY'),
('550e8400-e29b-41d4-a716-446655440116', 'WEDNESDAY'),
('550e8400-e29b-41d4-a716-446655440116', 'THURSDAY'),
('550e8400-e29b-41d4-a716-446655440116', 'FRIDAY'),
-- Test spaces (all days)
('550e8400-e29b-41d4-a716-446655440117', 'MONDAY'),
('550e8400-e29b-41d4-a716-446655440117', 'TUESDAY'),
('550e8400-e29b-41d4-a716-446655440117', 'WEDNESDAY'),
('550e8400-e29b-41d4-a716-446655440117', 'THURSDAY'),
('550e8400-e29b-41d4-a716-446655440117', 'FRIDAY'),
('550e8400-e29b-41d4-a716-446655440117', 'SATURDAY'),
('550e8400-e29b-41d4-a716-446655440117', 'SUNDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'MONDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'TUESDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'WEDNESDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'THURSDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'FRIDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'SATURDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'SUNDAY'),
('550e8400-e29b-41d4-a716-446655440119', 'MONDAY'),
('550e8400-e29b-41d4-a716-446655440119', 'TUESDAY'),
('550e8400-e29b-41d4-a716-446655440119', 'WEDNESDAY'),
('550e8400-e29b-41d4-a716-446655440119', 'THURSDAY'),
('550e8400-e29b-41d4-a716-446655440119', 'FRIDAY'),
('550e8400-e29b-41d4-a716-446655440119', 'SATURDAY'),
('550e8400-e29b-41d4-a716-446655440119', 'SUNDAY');

-- Add conflict types for new spaces
INSERT INTO space_conflict_types (space_id, conflict_type) VALUES 
('550e8400-e29b-41d4-a716-446655440115', 'GUEST_ROOM'),
('550e8400-e29b-41d4-a716-446655440116', 'COWORKING'),
('550e8400-e29b-41d4-a716-446655440117', 'COMMON_ROOM'),
('550e8400-e29b-41d4-a716-446655440118', 'GUEST_ROOM'),
('550e8400-e29b-41d4-a716-446655440119', 'COMMON_ROOM');

-- Add cleaning days for spaces that need them
INSERT INTO space_cleaning_days (space_id, day_of_week) VALUES 
-- Premium guest room (Monday and Tuesday)
('550e8400-e29b-41d4-a716-446655440115', 'MONDAY'),
('550e8400-e29b-41d4-a716-446655440115', 'TUESDAY'),
-- High quota guest room (Wednesday and Thursday)
('550e8400-e29b-41d4-a716-446655440118', 'WEDNESDAY'),
('550e8400-e29b-41d4-a716-446655440118', 'THURSDAY');

-- Add some test reservations for comprehensive testing
INSERT INTO reservations (id, space_id, user_id, start_date, end_date, status, payment_status, total_price, platform_fee_amount, platform_fixed_fee_amount, stripe_payment_intent_id, stripe_session_id, payment_expires_at, access_code, access_code_generated_at, access_code_revoked_at, cancellation_reason, cancelled_at, cancelled_by, created_at, updated_at) VALUES 
-- Past confirmed reservation
('550e8400-e29b-41d4-a716-446655440301', '550e8400-e29b-41d4-a716-446655440111', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-01-01', '2024-01-03', 'CONFIRMED', 'SUCCEEDED', 20.00, 0.40, 0.25, 'pi_past_001', 'cs_past_001', '2024-01-01 10:15:00+00', 'ABC123', '2024-01-01 10:00:00+00', '2024-01-03 12:00:00+00', NULL, NULL, NULL, '2024-01-01 10:00:00+00', '2024-01-03 12:00:00+00'),
-- Future confirmed reservation
('550e8400-e29b-41d4-a716-446655440302', '550e8400-e29b-41d4-a716-446655440111', 'c4e8c95e-682b-440d-b6d5-6297f0d13633', '2024-12-01', '2024-12-03', 'CONFIRMED', 'SUCCEEDED', 20.00, 0.40, 0.25, 'pi_future_001', 'cs_future_001', '2024-11-30 10:15:00+00', 'DEF456', '2024-12-01 10:00:00+00', NULL, NULL, NULL, NULL, '2024-11-30 10:00:00+00', '2024-12-01 10:00:00+00'),
-- Cancelled reservation
('550e8400-e29b-41d4-a716-446655440303', '550e8400-e29b-41d4-a716-446655440112', '593e726d-14b1-477e-967c-72bec8478a45', '2024-02-01', '2024-02-01', 'CANCELLED', 'CANCELLED', 20.00, 0.40, 0.25, 'pi_cancelled_001', 'cs_cancelled_001', '2024-02-01 10:15:00+00', NULL, NULL, NULL, 'Change of plans', '2024-02-01 09:00:00+00', '593e726d-14b1-477e-967c-72bec8478a45', '2024-02-01 08:00:00+00', '2024-02-01 09:00:00+00'),
-- Pending payment reservation
('550e8400-e29b-41d4-a716-446655440304', '550e8400-e29b-41d4-a716-446655440113', 'f336a25e-7bed-4f44-a360-f758aecd7d09', '2024-12-15', '2024-12-16', 'PENDING_PAYMENT', 'PENDING', 8.00, 0.16, 0.25, 'pi_pending_001', 'cs_pending_001', '2024-12-15 10:15:00+00', NULL, NULL, NULL, NULL, NULL, NULL, '2024-12-15 10:00:00+00', '2024-12-15 10:00:00+00'),
-- Reservation with quota usage
('550e8400-e29b-41d4-a716-446655440305', '550e8400-e29b-41d4-a716-446655440115', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-03-01', '2024-03-02', 'CONFIRMED', 'SUCCEEDED', 15.00, 0.30, 0.25, 'pi_quota_001', 'cs_quota_001', '2024-03-01 10:15:00+00', 'GHI789', '2024-03-01 10:00:00+00', NULL, NULL, NULL, NULL, '2024-03-01 10:00:00+00', '2024-03-01 10:00:00+00');

-- Update quota usage for spaces
UPDATE spaces SET used_annual_reservations = 1 WHERE id = '550e8400-e29b-41d4-a716-446655440115';

-- Add audit logs for test reservations
INSERT INTO reservation_audit_log (id, reservation_id, event_type, old_value, new_value, log_message, performed_by, created_at) VALUES 
-- Creation logs
('550e8400-e29b-41d4-a716-446655440401', '550e8400-e29b-41d4-a716-446655440301', 'CREATED', NULL, 'PENDING_PAYMENT', 'Reservation created', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-01-01 10:00:00+00'),
('550e8400-e29b-41d4-a716-446655440402', '550e8400-e29b-41d4-a716-446655440301', 'STATUS_CHANGE', 'PENDING_PAYMENT', 'CONFIRMED', 'Reservation confirmed', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-01-01 10:05:00+00'),
('550e8400-e29b-41d4-a716-446655440403', '550e8400-e29b-41d4-a716-446655440301', 'PAYMENT_RECEIVED', NULL, 'pi_past_001', 'Payment received', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-01-01 10:05:00+00'),
('550e8400-e29b-41d4-a716-446655440404', '550e8400-e29b-41d4-a716-446655440301', 'CODE_GENERATED', NULL, 'ABC123', 'Access code generated', 'system', '2024-01-01 10:00:00+00'),
('550e8400-e29b-41d4-a716-446655440405', '550e8400-e29b-41d4-a716-446655440301', 'CODE_REVOKED', 'ABC123', NULL, 'Access code revoked', 'system', '2024-01-03 12:00:00+00'),
-- Future reservation logs
('550e8400-e29b-41d4-a716-446655440406', '550e8400-e29b-41d4-a716-446655440302', 'CREATED', NULL, 'PENDING_PAYMENT', 'Reservation created', 'c4e8c95e-682b-440d-b6d5-6297f0d13633', '2024-11-30 10:00:00+00'),
('550e8400-e29b-41d4-a716-446655440407', '550e8400-e29b-41d4-a716-446655440302', 'STATUS_CHANGE', 'PENDING_PAYMENT', 'CONFIRMED', 'Reservation confirmed', 'c4e8c95e-682b-440d-b6d5-6297f0d13633', '2024-11-30 10:05:00+00'),
('550e8400-e29b-41d4-a716-446655440408', '550e8400-e29b-41d4-a716-446655440302', 'PAYMENT_RECEIVED', NULL, 'pi_future_001', 'Payment received', 'c4e8c95e-682b-440d-b6d5-6297f0d13633', '2024-11-30 10:05:00+00'),
('550e8400-e29b-41d4-a716-446655440409', '550e8400-e29b-41d4-a716-446655440302', 'CODE_GENERATED', NULL, 'DEF456', 'Access code generated', 'system', '2024-12-01 10:00:00+00'),
-- Cancelled reservation logs
('550e8400-e29b-41d4-a716-446655440410', '550e8400-e29b-41d4-a716-446655440303', 'CREATED', NULL, 'PENDING_PAYMENT', 'Reservation created', '593e726d-14b1-477e-967c-72bec8478a45', '2024-02-01 08:00:00+00'),
('550e8400-e29b-41d4-a716-446655440411', '550e8400-e29b-41d4-a716-446655440303', 'CANCELLED', 'PENDING_PAYMENT', 'Change of plans', 'Reservation cancelled', '593e726d-14b1-477e-967c-72bec8478a45', '2024-02-01 09:00:00+00'),
-- Pending payment logs
('550e8400-e29b-41d4-a716-446655440412', '550e8400-e29b-41d4-a716-446655440304', 'CREATED', NULL, 'PENDING_PAYMENT', 'Reservation created', 'f336a25e-7bed-4f44-a360-f758aecd7d09', '2024-12-15 10:00:00+00'),
-- Quota reservation logs
('550e8400-e29b-41d4-a716-446655440413', '550e8400-e29b-41d4-a716-446655440305', 'CREATED', NULL, 'PENDING_PAYMENT', 'Reservation created', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-03-01 10:00:00+00'),
('550e8400-e29b-41d4-a716-446655440414', '550e8400-e29b-41d4-a716-446655440305', 'STATUS_CHANGE', 'PENDING_PAYMENT', 'CONFIRMED', 'Reservation confirmed', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-03-01 10:05:00+00'),
('550e8400-e29b-41d4-a716-446655440415', '550e8400-e29b-41d4-a716-446655440305', 'PAYMENT_RECEIVED', NULL, 'pi_quota_001', 'Payment received', '8cf28343-7b32-4365-8c04-305f342a2cee', '2024-03-01 10:05:00+00'),
('550e8400-e29b-41d4-a716-446655440416', '550e8400-e29b-41d4-a716-446655440305', 'CODE_GENERATED', NULL, 'GHI789', 'Access code generated', 'system', '2024-03-01 10:00:00+00');

-- Add access codes for confirmed reservations
INSERT INTO access_codes (id, reservation_id, code, generated_at, revoked_at, created_at, updated_at) VALUES 
('550e8400-e29b-41d4-a716-446655440501', '550e8400-e29b-41d4-a716-446655440301', 'ABC123', '2024-01-01 10:00:00+00', '2024-01-03 12:00:00+00', '2024-01-01 10:00:00+00', '2024-01-03 12:00:00+00'),
('550e8400-e29b-41d4-a716-446655440502', '550e8400-e29b-41d4-a716-446655440302', 'DEF456', '2024-12-01 10:00:00+00', NULL, '2024-12-01 10:00:00+00', '2024-12-01 10:00:00+00'),
('550e8400-e29b-41d4-a716-446655440503', '550e8400-e29b-41d4-a716-446655440305', 'GHI789', '2024-03-01 10:00:00+00', NULL, '2024-03-01 10:00:00+00', '2024-03-01 10:00:00+00');

-- Add space images for new spaces
INSERT INTO space_images (id, space_id, url, alt_text, is_primary, order_index, created_at, updated_at) VALUES 
('550e8400-e29b-41d4-a716-446655440601', '550e8400-e29b-41d4-a716-446655440115', 'https://local.portal.neohoods.com:4200/assets/spaces/chambre-dhotes.jpg', 'Chambre d''hôte Premium', true, 0, '2024-01-10 09:00:00+00', '2024-01-10 09:00:00+00'),
('550e8400-e29b-41d4-a716-446655440602', '550e8400-e29b-41d4-a716-446655440116', 'https://local.portal.neohoods.com:4200/assets/spaces/coworking.jpg', 'Bureau coworking C', true, 0, '2024-01-10 09:00:00+00', '2024-01-10 09:00:00+00'),
('550e8400-e29b-41d4-a716-446655440603', '550e8400-e29b-41d4-a716-446655440117', 'https://local.portal.neohoods.com:4200/assets/spaces/common-space.jpg', 'Espace Test Quota Zero', true, 0, '2024-01-10 09:00:00+00', '2024-01-10 09:00:00+00'),
('550e8400-e29b-41d4-a716-446655440604', '550e8400-e29b-41d4-a716-446655440118', 'https://local.portal.neohoods.com:4200/assets/spaces/chambre-dhotes.jpg', 'Espace Test Quota Elevé', true, 0, '2024-01-10 09:00:00+00', '2024-01-10 09:00:00+00'),
('550e8400-e29b-41d4-a716-446655440605', '550e8400-e29b-41d4-a716-446655440119', 'https://local.portal.neohoods.com:4200/assets/spaces/common-space.jpg', 'Espace Test Horaires', true, 0, '2024-01-10 09:00:00+00', '2024-01-10 09:00:00+00');

