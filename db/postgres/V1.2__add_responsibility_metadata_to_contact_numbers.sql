-- Add responsibility and metadata columns to contact_numbers table
ALTER TABLE contact_numbers
ADD COLUMN IF NOT EXISTS responsibility TEXT,
ADD COLUMN IF NOT EXISTS metadata TEXT;

-- Add comments for documentation
COMMENT ON COLUMN contact_numbers.responsibility IS 'Responsibility/scope of the contact (e.g., "garages, portails", "canalisations, système de chauffage", "chaufferie")';
COMMENT ON COLUMN contact_numbers.metadata IS 'Additional metadata/instructions (e.g., "QR code à scanner", "numéro à donner")';




