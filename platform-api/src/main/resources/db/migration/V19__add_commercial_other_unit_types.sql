-- Add COMMERCIAL and OTHER to unit types
ALTER TABLE units DROP CONSTRAINT IF EXISTS units_type_check;
ALTER TABLE units ADD CONSTRAINT units_type_check CHECK (type IN ('FLAT', 'GARAGE', 'PARKING', 'COMMERCIAL', 'OTHER'));

