-- Dota 2 Draft Assistant Database Migration
-- Script 007: Player Recommendation Schema
-- This script adds tables for player recommendations based on match history

-- Make sure version info is tracked
INSERT INTO db_version (version, description) VALUES (7, 'Player recommendation schema')
ON CONFLICT (version) DO NOTHING;

-- Create table for player hero performance metrics
CREATE TABLE IF NOT EXISTS player_hero_performance (
    account_id BIGINT NOT NULL,
    hero_id INTEGER NOT NULL,
    matches_count INTEGER DEFAULT 0,
    wins_count INTEGER DEFAULT 0,
    total_kills INTEGER DEFAULT 0,
    total_deaths INTEGER DEFAULT 0,
    total_assists INTEGER DEFAULT 0,
    last_played TIMESTAMP,
    impact_score REAL,
    comfort_score REAL,
    performance_score REAL,
    pick_rate REAL,
    is_comfort_pick BOOLEAN DEFAULT FALSE,
    calculated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, hero_id),
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id)
);

-- Create table for player recommendations
CREATE TABLE IF NOT EXISTS player_recommendations (
    id SERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    hero_id INTEGER NOT NULL,
    recommendation_type TEXT NOT NULL, -- 'STANDARD', 'DRAFT_SPECIFIC', 'META', 'COMFORT'
    recommendation_score REAL NOT NULL,
    global_weight REAL NOT NULL,
    personal_weight REAL NOT NULL,
    recommendation_reason TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id)
);

-- Create table for player role preferences
CREATE TABLE IF NOT EXISTS player_role_preferences (
    account_id BIGINT NOT NULL,
    role_position INTEGER NOT NULL, -- 1-5 for positions
    preference_score REAL NOT NULL, -- 0-1 for preference level
    PRIMARY KEY (account_id, role_position),
    FOREIGN KEY (account_id) REFERENCES players(account_id)
);

-- Create table for player performance by role
CREATE TABLE IF NOT EXISTS player_role_performance (
    account_id BIGINT NOT NULL,
    role_position INTEGER NOT NULL, -- 1-5 for positions
    matches_count INTEGER DEFAULT 0,
    wins_count INTEGER DEFAULT 0,
    performance_score REAL,
    calculated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_position),
    FOREIGN KEY (account_id) REFERENCES players(account_id)
);

-- Create table for player hero performance by role
CREATE TABLE IF NOT EXISTS player_hero_role_performance (
    account_id BIGINT NOT NULL,
    hero_id INTEGER NOT NULL,
    role_position INTEGER NOT NULL, -- 1-5 for positions
    matches_count INTEGER DEFAULT 0,
    wins_count INTEGER DEFAULT 0,
    performance_score REAL,
    PRIMARY KEY (account_id, hero_id, role_position),
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (hero_id) REFERENCES heroes(id)
);

-- Create table for player match calculation history
-- This tracks when performance metrics were last calculated
CREATE TABLE IF NOT EXISTS player_calculations (
    account_id BIGINT PRIMARY KEY,
    last_calculation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    matches_processed INTEGER DEFAULT 0,
    calculation_duration INTEGER, -- in milliseconds
    is_complete BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    FOREIGN KEY (account_id) REFERENCES players(account_id)
);

-- Create table for player recent matches summary
CREATE TABLE IF NOT EXISTS player_recent_stats (
    account_id BIGINT PRIMARY KEY,
    recent_matches INTEGER DEFAULT 0,
    recent_wins INTEGER DEFAULT 0,
    favorite_hero_id INTEGER,
    most_successful_hero_id INTEGER,
    avg_kda REAL,
    calculated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES players(account_id),
    FOREIGN KEY (favorite_hero_id) REFERENCES heroes(id),
    FOREIGN KEY (most_successful_hero_id) REFERENCES heroes(id)
);

-- Add view for consolidated player performance data
CREATE OR REPLACE VIEW player_performance_view AS
SELECT 
    php.account_id,
    php.hero_id,
    h.localized_name AS hero_name,
    php.matches_count,
    php.wins_count,
    CASE WHEN php.matches_count > 0 THEN (php.wins_count::REAL / php.matches_count) ELSE 0 END AS win_rate,
    CASE WHEN php.total_deaths > 0 THEN ((php.total_kills + php.total_assists)::REAL / php.total_deaths) 
         ELSE (php.total_kills + php.total_assists) END AS kda_ratio,
    php.impact_score,
    php.comfort_score,
    php.performance_score,
    php.is_comfort_pick,
    php.last_played
FROM 
    player_hero_performance php
JOIN 
    heroes h ON php.hero_id = h.id;

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_player_hero_performance_account ON player_hero_performance(account_id);
CREATE INDEX IF NOT EXISTS idx_player_hero_performance_hero ON player_hero_performance(hero_id);
CREATE INDEX IF NOT EXISTS idx_player_hero_performance_comfort ON player_hero_performance(is_comfort_pick);
CREATE INDEX IF NOT EXISTS idx_player_recommendations_account ON player_recommendations(account_id);
CREATE INDEX IF NOT EXISTS idx_player_recommendations_type ON player_recommendations(recommendation_type);
CREATE INDEX IF NOT EXISTS idx_player_role_performance_account ON player_role_performance(account_id);
CREATE INDEX IF NOT EXISTS idx_player_hero_role_account ON player_hero_role_performance(account_id);