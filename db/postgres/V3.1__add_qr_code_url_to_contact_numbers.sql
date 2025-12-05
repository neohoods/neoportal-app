-- Add qr_code_url column to contact_numbers table for storing QR code image URLs
ALTER TABLE contact_numbers
ADD COLUMN IF NOT EXISTS qr_code_url VARCHAR(500);

-- Add comment for documentation
COMMENT ON COLUMN contact_numbers.qr_code_url IS 'URL of QR code image (e.g., for Otis elevator service)';


