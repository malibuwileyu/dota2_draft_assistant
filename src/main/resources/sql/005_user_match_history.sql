-- User Match History Schema Updates
-- Version 5

-- Make sure version info is tracked
INSERT INTO db_version (version, description) VALUES (5, 'User match history enhancements')
ON CONFLICT (version) DO NOTHING;

-- Check if auth tables exist, create if not
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'auth_sessions') THEN
        -- Authentication sessions table
        CREATE TABLE auth_sessions (
            id SERIAL PRIMARY KEY,
            account_id BIGINT NOT NULL,
            session_token TEXT NOT NULL UNIQUE,
            created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            expire_time TIMESTAMP NOT NULL,
            last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            ip_address TEXT,
            user_agent TEXT,
            device_info TEXT,
            is_active BOOLEAN DEFAULT TRUE,
            FOREIGN KEY (account_id) REFERENCES players(account_id)
        );

        CREATE INDEX idx_auth_sessions_account_id ON auth_sessions(account_id);
        CREATE INDEX idx_auth_sessions_token ON auth_sessions(session_token);
    END IF;

    IF NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'login_history') THEN
        -- Login history table
        CREATE TABLE login_history (
            id SERIAL PRIMARY KEY,
            account_id BIGINT NOT NULL,
            login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            login_ip TEXT,
            login_method TEXT,
            success BOOLEAN DEFAULT TRUE,
            user_agent TEXT,
            FOREIGN KEY (account_id) REFERENCES players(account_id)
        );

        CREATE INDEX idx_login_history_account_id ON login_history(account_id);
    END IF;

    IF NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'user_preference_types') THEN
        -- User preference types table
        CREATE TABLE user_preference_types (
            id SERIAL PRIMARY KEY,
            name TEXT NOT NULL UNIQUE,
            description TEXT,
            default_value TEXT,
            data_type TEXT DEFAULT 'string',
            ui_component TEXT DEFAULT 'text',
            category TEXT,
            display_order INTEGER DEFAULT 0,
            created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    END IF;

    IF NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'user_preferences') THEN
        -- User preferences table
        CREATE TABLE user_preferences (
            id SERIAL PRIMARY KEY,
            account_id BIGINT NOT NULL,
            preference_type_id INTEGER NOT NULL,
            value TEXT,
            created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (account_id) REFERENCES players(account_id),
            FOREIGN KEY (preference_type_id) REFERENCES user_preference_types(id),
            UNIQUE (account_id, preference_type_id)
        );

        CREATE INDEX idx_user_preferences_account_id ON user_preferences(account_id);
    END IF;

    IF NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'user_skill_metrics') THEN
        -- User skill metrics table
        CREATE TABLE user_skill_metrics (
            id SERIAL PRIMARY KEY,
            account_id BIGINT NOT NULL,
            metric_name TEXT NOT NULL,
            metric_value DOUBLE PRECISION NOT NULL,
            created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (account_id) REFERENCES players(account_id),
            UNIQUE (account_id, metric_name)
        );

        CREATE INDEX idx_user_skill_metrics_account_id ON user_skill_metrics(account_id);
    END IF;
END $$;

-- Table for player match history sync status
CREATE TABLE IF NOT EXISTS player_match_history_sync (
    account_id BIGINT PRIMARY KEY,
    last_sync_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sync_in_progress BOOLEAN DEFAULT FALSE,
    full_sync_completed BOOLEAN DEFAULT FALSE,
    matches_count INTEGER DEFAULT 0,
    last_match_id BIGINT,
    next_sync_date TIMESTAMP,
    sync_frequency TEXT DEFAULT 'DAILY',
    FOREIGN KEY (account_id) REFERENCES players(account_id)
);

-- Table for storing user player match details
CREATE TABLE IF NOT EXISTS user_match_details (
    id SERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    is_analyzed BOOLEAN DEFAULT FALSE,
    is_favorite BOOLEAN DEFAULT FALSE,
    is_hidden BOOLEAN DEFAULT FALSE,
    notes TEXT,
    user_tags TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (match_id) REFERENCES matches(id),
    UNIQUE (account_id, match_id)
);

-- Add indexes
CREATE INDEX IF NOT EXISTS idx_user_match_details_account_id ON user_match_details(account_id);
CREATE INDEX IF NOT EXISTS idx_user_match_details_match_id ON user_match_details(match_id);
CREATE INDEX IF NOT EXISTS idx_user_match_details_is_favorite ON user_match_details(is_favorite);
CREATE INDEX IF NOT EXISTS idx_player_match_history_sync_next_sync ON player_match_history_sync(next_sync_date);

-- Add columns to players table for profile customization if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM information_schema.columns 
                   WHERE table_name = 'players' AND column_name = 'badge_level') THEN
        ALTER TABLE players ADD COLUMN badge_level INTEGER;
    END IF;

    IF NOT EXISTS (SELECT FROM information_schema.columns 
                   WHERE table_name = 'players' AND column_name = 'badge_stars') THEN
        ALTER TABLE players ADD COLUMN badge_stars INTEGER;
    END IF;
    
    IF NOT EXISTS (SELECT FROM information_schema.columns 
                   WHERE table_name = 'players' AND column_name = 'profile_background') THEN
        ALTER TABLE players ADD COLUMN profile_background TEXT;
    END IF;
    
    IF NOT EXISTS (SELECT FROM information_schema.columns 
                   WHERE table_name = 'players' AND column_name = 'custom_avatar') THEN
        ALTER TABLE players ADD COLUMN custom_avatar TEXT;
    END IF;
END $$;

-- Add match schedule/notification system
CREATE TABLE IF NOT EXISTS user_match_notifications (
    id SERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    notification_type TEXT NOT NULL,  -- SOON, READY, ANALYSIS_COMPLETED, etc.
    match_id BIGINT,
    message TEXT NOT NULL,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_date TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (match_id) REFERENCES matches(id)
);

CREATE INDEX IF NOT EXISTS idx_user_match_notifications_account_id ON user_match_notifications(account_id);
CREATE INDEX IF NOT EXISTS idx_user_match_notifications_unread ON user_match_notifications(account_id, is_read);

-- Add patch note tracking and hero changes
CREATE TABLE IF NOT EXISTS patch_hero_changes (
    id SERIAL PRIMARY KEY,
    patch_id INTEGER NOT NULL,
    hero_id INTEGER NOT NULL,
    change_type TEXT NOT NULL, -- BUFF, NERF, REWORK
    change_description TEXT NOT NULL,
    ability_affected TEXT,
    importance INTEGER DEFAULT 5, -- 1-10 scale of how important the change is
    FOREIGN KEY (patch_id) REFERENCES patches(id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id)
);

-- Add default user preferences
INSERT INTO user_preference_types (name, description, default_value, category, display_order) VALUES
('match_history_refresh_frequency', 'How often to refresh match history', 'DAILY', 'Match History', 10),
('match_history_page_size', 'Number of matches to display per page', '20', 'Match History', 20),
('show_hidden_matches', 'Show hidden matches in match history', 'false', 'Match History', 30),
('auto_analyze_matches', 'Automatically analyze new matches', 'true', 'Analysis', 10),
('theme', 'UI theme', 'SYSTEM', 'Appearance', 10),
('stat_display_format', 'Format for displaying statistics', 'DECIMAL', 'Appearance', 20),
('default_draft_mode', 'Default draft mode', 'CAPTAINS_MODE', 'Draft', 10),
('match_alert_notifications', 'Enable match alert notifications', 'true', 'Notifications', 10),
('patch_note_notifications', 'Enable patch note notifications', 'true', 'Notifications', 20)
ON CONFLICT (name) DO NOTHING;