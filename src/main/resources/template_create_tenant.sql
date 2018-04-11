CREATE ROLE myuniversity_mymodule PASSWORD 'myuniversity' NOSUPERUSER NOCREATEDB INHERIT LOGIN;

GRANT myuniversity_mymodule TO CURRENT_USER;

CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- declare tables -- 
CREATE TABLE IF NOT EXISTS myuniversity_mymodule.waitlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), 
    jsonb JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS myuniversity_mymodule.queuers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), 
    jsonb JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS myuniversity_mymodule.courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), 
    jsonb JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS myuniversity_mymodule.instructors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), 
    jsonb JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS myuniversity_mymodule.items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), 
    jsonb JSONB NOT NULL
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA myuniversity_mymodule TO myuniversity_mymodule;
