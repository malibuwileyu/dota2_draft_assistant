-- Dota 2 Draft Assistant Database Migration
-- Script 006: Team Data Schema
-- This script adds tables for team data and player roster information

-- Make sure version info is tracked
INSERT INTO db_version (version, description) VALUES (6, 'Team data and player roster tables')
ON CONFLICT (version) DO NOTHING;

-- Teams table
CREATE TABLE IF NOT EXISTS teams (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    tag TEXT,
    logo_url TEXT,
    is_professional BOOLEAN DEFAULT FALSE,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_matches INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0
);

-- Team roster table
CREATE TABLE IF NOT EXISTS team_roster (
    team_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    position INTEGER,  -- 1-5 for positions, 6 for substitute, 7 for coach
    is_active BOOLEAN DEFAULT TRUE,
    join_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    leave_date TIMESTAMP,
    PRIMARY KEY (team_id, account_id),
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (account_id) REFERENCES players(account_id)
);

-- Team hero statistics
CREATE TABLE IF NOT EXISTS team_heroes (
    team_id BIGINT NOT NULL,
    hero_id INTEGER NOT NULL,
    matches_played INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    bans_against INTEGER DEFAULT 0,
    last_played TIMESTAMP,
    PRIMARY KEY (team_id, hero_id),
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id)
);

-- Team hero compositions (combinations that work well)
CREATE TABLE IF NOT EXISTS team_hero_compositions (
    id SERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    matches_played INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    last_played TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES teams(id)
);

-- Heroes in each composition
CREATE TABLE IF NOT EXISTS team_composition_heroes (
    composition_id INTEGER NOT NULL,
    hero_id INTEGER NOT NULL,
    position INTEGER, -- 1-5 for positions
    PRIMARY KEY (composition_id, hero_id),
    FOREIGN KEY (composition_id) REFERENCES team_hero_compositions(id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id)
);

-- Team match history
CREATE TABLE IF NOT EXISTS team_matches (
    id SERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    is_radiant BOOLEAN DEFAULT TRUE,
    won BOOLEAN,
    tournament_name TEXT,
    opponent_team_id BIGINT,
    match_date TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (match_id) REFERENCES matches(id),
    FOREIGN KEY (opponent_team_id) REFERENCES teams(id)
);

-- User-created teams
CREATE TABLE IF NOT EXISTS user_teams (
    id SERIAL PRIMARY KEY,
    creator_account_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    is_favorite BOOLEAN DEFAULT FALSE,
    is_public BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (creator_account_id) REFERENCES players(account_id),
    FOREIGN KEY (team_id) REFERENCES teams(id)
);

-- Team drafting patterns
CREATE TABLE IF NOT EXISTS team_draft_patterns (
    id SERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    pattern_type TEXT NOT NULL, -- FIRST_PHASE_PICKS, SECOND_PHASE_PICKS, BAN_PRIORITY
    hero_id INTEGER NOT NULL,
    frequency REAL NOT NULL,  -- 0.0 to 1.0 representing percentage of drafts
    position INTEGER,  -- 1-5 for positions
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id),
    UNIQUE (team_id, pattern_type, hero_id)
);

-- Team player hero preferences
CREATE TABLE IF NOT EXISTS team_player_heroes (
    team_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    hero_id INTEGER NOT NULL,
    matches_played INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    signature_level INTEGER DEFAULT 0, -- 0-5 scale of how "signature" this hero is
    last_played TIMESTAMP,
    PRIMARY KEY (team_id, account_id, hero_id),
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id)
);

-- Team vs Team matchups
CREATE TABLE IF NOT EXISTS team_matchups (
    team_id BIGINT NOT NULL,
    opponent_team_id BIGINT NOT NULL,
    matches_played INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    last_played TIMESTAMP,
    PRIMARY KEY (team_id, opponent_team_id),
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (opponent_team_id) REFERENCES teams(id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_teams_is_professional ON teams(is_professional);
CREATE INDEX IF NOT EXISTS idx_team_roster_account_id ON team_roster(account_id);
CREATE INDEX IF NOT EXISTS idx_team_roster_active ON team_roster(is_active);
CREATE INDEX IF NOT EXISTS idx_team_heroes_matches_played ON team_heroes(matches_played);
CREATE INDEX IF NOT EXISTS idx_team_matches_match_id ON team_matches(match_id);
CREATE INDEX IF NOT EXISTS idx_team_matches_team_id ON team_matches(team_id);
CREATE INDEX IF NOT EXISTS idx_team_matches_opponent_team_id ON team_matches(opponent_team_id);
CREATE INDEX IF NOT EXISTS idx_team_player_heroes_signature ON team_player_heroes(signature_level);
CREATE INDEX IF NOT EXISTS idx_user_teams_creator ON user_teams(creator_account_id);
CREATE INDEX IF NOT EXISTS idx_user_teams_favorite ON user_teams(is_favorite);