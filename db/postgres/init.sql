CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "users" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "username" varchar(255) NOT NULL UNIQUE,
    "email" varchar(255) NOT NULL UNIQUE,
    "password" varchar(255) NOT NULL,
    "first_name" varchar(255),
    "last_name" varchar(255),
    "flat_number" varchar(255),
    "street_address" varchar(255),
    "city" varchar(255),
    "postal_code" varchar(255),
    "country" varchar(255),
    "preferred_language" varchar(255),
    "avatar_url" varchar(255),
    "created_at" timestamp DEFAULT CURRENT_TIMESTAMP,
    "is_email_verified" boolean NOT NULL DEFAULT false,
    "disabled" boolean NOT NULL DEFAULT false,
    "status" varchar(255) NOT NULL DEFAULT 'WAITING_FOR_EMAIL' CHECK (status IN ('WAITING_FOR_EMAIL', 'ACTIVE', 'INACTIVE')),
    "user_type" varchar(255) CHECK (user_type IN ('ADMIN', 'OWNER', 'LANDLORD', 'TENANT', 'SYNDIC', 'EXTERNAL', 'CONTRACTOR', 'COMMERCIAL_PROPERTY_OWNER', 'GUEST'))
);



CREATE TABLE "help_categories" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "display_order" integer,
    "icon" varchar(255),
    "name" varchar(255)
);

CREATE TABLE "help_articles" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "content" varchar(4000),
    "display_order" integer,
    "title" varchar(255),
    "category_id" uuid REFERENCES help_categories(id)
);

CREATE TABLE "notifications" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "user_id" uuid REFERENCES users(id),
    "already_read" boolean NOT NULL,
    "author" varchar(255),
    "date" timestamp(6) with time zone,
    "payload" jsonb,
    "type" varchar(255) CHECK (type IN (
        'ADMIN_NEW_USER'
    ))
);


CREATE TABLE settings (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "is_registration_enabled" BOOLEAN NOT NULL DEFAULT false,
    "sso_enabled" BOOLEAN NOT NULL DEFAULT false,
    "sso_client_id" varchar(255),
    "sso_client_secret" varchar(255),
    "sso_token_endpoint" varchar(255),
    "sso_authorization_endpoint" varchar(255),
    "sso_scope" varchar(255)
);

CREATE TABLE "notification_settings" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "user_id" uuid REFERENCES users(id) NOT NULL,
    "enable_notifications" boolean NOT NULL DEFAULT true
);


CREATE TABLE "user_roles" (
    "user_id" uuid REFERENCES users(id),
    "role" varchar(255)
);

CREATE TABLE "user_properties" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "user_id" uuid REFERENCES users(id) ON DELETE CASCADE,
    "property_type" varchar(255) NOT NULL CHECK (property_type IN ('APARTMENT', 'GARAGE', 'PARKING', 'COMMERCIAL', 'OTHER')),
    "name" varchar(255) NOT NULL
);

CREATE TABLE "applications" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "name" varchar(255) NOT NULL,
    "url" varchar(255) NOT NULL,
    "icon" varchar(255) NOT NULL,
    "help_text" text NOT NULL,
    "disabled" boolean NOT NULL DEFAULT false
);

CREATE TABLE "infos" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "next_ag_date" date,
    "rules_url" varchar(255)
);

CREATE TABLE "delegates" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "info_id" uuid REFERENCES infos(id) ON DELETE CASCADE,
    "building" varchar(255),
    "first_name" varchar(255),
    "last_name" varchar(255),
    "email" varchar(255),
    "matrix_user" varchar(255)
);

CREATE TABLE "contact_numbers" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "info_id" uuid REFERENCES infos(id) ON DELETE CASCADE,
    "contact_type" varchar(50) CHECK (contact_type IN ('syndic', 'emergency')),
    "type" varchar(255),
    "description" text,
    "availability" varchar(255),
    "response_time" varchar(255),
    "name" varchar(255),
    "phone_number" varchar(255),
    "email" varchar(255),
    "office_hours" varchar(255),
    "address" text
);

CREATE TABLE "announcements" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "title" varchar(255) NOT NULL,
    "content" text NOT NULL,
    "created_at" timestamp with time zone NOT NULL,
    "updated_at" timestamp with time zone NOT NULL,
    "category" varchar(50) NOT NULL CHECK (category IN ('COMMUNITY_EVENT', 'LOST_AND_FOUND', 'SAFETY_ALERT', 'MAINTENANCE_NOTICE', 'SOCIAL_GATHERING', 'OTHER')) DEFAULT 'OTHER'
);

CREATE TABLE "custom_pages" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "ref" varchar(255) NOT NULL UNIQUE,
    "page_order" integer,
    "position" varchar(50) NOT NULL CHECK (position IN ('FOOTER_LINKS', 'COPYRIGHT', 'FOOTER_HELP')),
    "title" varchar(255) NOT NULL,
    "content" text NOT NULL
);

CREATE TABLE "newsletters" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "title" varchar(255) NOT NULL,
    "subject" varchar(255) NOT NULL,
    "content" text,
    "status" varchar(50) NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENDING', 'SENT', 'CANCELLED', 'FAILED')) DEFAULT 'DRAFT',
    "created_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    "scheduled_at" timestamp with time zone,
    "sent_at" timestamp with time zone,
    "created_by" uuid NOT NULL,
    "recipient_count" integer,
    "audience_type" varchar(50) NOT NULL DEFAULT 'ALL' CHECK (audience_type IN ('ALL', 'USER_TYPES', 'SPECIFIC_USERS')),
    "audience_user_types" jsonb,
    "audience_user_ids" jsonb,
    "audience_exclude_user_ids" jsonb,
    FOREIGN KEY ("created_by") REFERENCES "users"("id") ON DELETE CASCADE
);

CREATE TABLE "newsletter_logs" (
    "id" uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    "newsletter_id" uuid NOT NULL,
    "user_id" uuid NOT NULL,
    "user_email" varchar(255) NOT NULL,
    "status" varchar(50) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'BOUNCED')) DEFAULT 'PENDING',
    "sent_at" timestamp with time zone,
    "error_message" text,
    "created_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY ("newsletter_id") REFERENCES "newsletters"("id") ON DELETE CASCADE,
    FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE
);

CREATE INDEX "idx_newsletter_logs_newsletter_id" ON "newsletter_logs"("newsletter_id");
CREATE INDEX "idx_newsletter_logs_user_id" ON "newsletter_logs"("user_id");
CREATE INDEX "idx_newsletter_logs_status" ON "newsletter_logs"("status");
CREATE INDEX "idx_newsletter_logs_created_at" ON "newsletter_logs"("created_at");
