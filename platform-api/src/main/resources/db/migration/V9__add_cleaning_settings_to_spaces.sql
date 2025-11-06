-- Add cleaning company integration settings to spaces table

ALTER TABLE spaces
ADD COLUMN cleaning_enabled BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN cleaning_email VARCHAR(255),
ADD COLUMN cleaning_notifications_enabled BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN cleaning_calendar_enabled BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN cleaning_days_after_checkout INTEGER DEFAULT 0 NOT NULL,
ADD COLUMN cleaning_hour VARCHAR(5) DEFAULT '10:00' NOT NULL;



