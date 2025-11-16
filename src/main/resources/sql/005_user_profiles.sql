-- Dota 2 Draft Assistant Database Migration
-- Script 005: User Profiles for Steam Authentication
-- This script enhances the existing user profile tables to better support Steam authentication

-- Update players table with additional Steam profile fields
ALTER TABLE players ADD COLUMN IF NOT EXISTS profile_url TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS avatar_medium_url TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS avatar_full_url TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS time_created BIGINT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS last_logoff BIGINT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT TRUE;

-- Create sessions table for authentication tracking
CREATE TABLE IF NOT EXISTS auth_sessions (
    id SERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    session_token TEXT NOT NULL UNIQUE,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expire_time TIMESTAMP,
    last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address TEXT,
    user_agent TEXT,
    device_info TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (account_id) REFERENCES players(account_id)
);

-- Create table for user preferences with structured approach
CREATE TABLE IF NOT EXISTS user_preference_types (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    default_value TEXT
);

CREATE TABLE IF NOT EXISTS user_preferences (
    id SERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    preference_type_id INTEGER NOT NULL,
    value TEXT NOT NULL,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (preference_type_id) REFERENCES user_preference_types(id),
    UNIQUE (account_id, preference_type_id)
);

-- Pre-populate common preference types
INSERT INTO user_preference_types (name, description, default_value)
VALUES
    ('draft_auto_suggest', 'Auto-suggest heroes during draft', 'true'),
    ('theme', 'UI theme preference', 'system'),
    ('privacy_level', 'Privacy level for user data', 'standard'),
    ('match_history_public', 'Whether match history is public', 'true'),
    ('preferred_roles', 'Preferred roles when playing', '["any"]'),
    ('favorite_heroes', 'List of favorite heroes', '[]'),
    ('dashboard_layout', 'Dashboard widget layout', 'default')
ON CONFLICT (name) DO NOTHING;

-- Create table for user skill levels
CREATE TABLE IF NOT EXISTS user_skill_metrics (
    id SERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    metric_name TEXT NOT NULL,
    metric_value REAL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    UNIQUE (account_id, metric_name)
);

-- Create table for tracking login history
CREATE TABLE IF NOT EXISTS login_history (
    id SERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    login_ip TEXT,
    login_method TEXT,
    success BOOLEAN DEFAULT TRUE,
    user_agent TEXT,
    FOREIGN KEY (account_id) REFERENCES players(account_id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_auth_sessions_account_id ON auth_sessions(account_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_session_token ON auth_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_user_preferences_account_id ON user_preferences(account_id);
CREATE INDEX IF NOT EXISTS idx_user_skill_metrics_account_id ON user_skill_metrics(account_id);
CREATE INDEX IF NOT EXISTS idx_login_history_account_id ON login_history(account_id);

-- Update the db_version table
INSERT INTO db_version (version, description)
VALUES (5, 'User profile enhancements for Steam authentication')
ON CONFLICT (version) DO NOTHING;