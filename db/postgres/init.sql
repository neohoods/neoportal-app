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
    "status" varchar(255) NOT NULL DEFAULT 'WAITING_FOR_EMAIL' CHECK (status IN ('WAITING_FOR_EMAIL', 'ACTIVE', 'INACTIVE'))
);

CREATE TABLE "user_roles" (
    "user_id" uuid REFERENCES users(id),
    "role" varchar(255)
);
