-- SQLite version (without PostgreSQL syntax)
-- Create temporary tables to hold the JSON data
CREATE TEMPORARY TABLE IF NOT EXISTS temp_match_details (match_id INTEGER, data TEXT);
CREATE TEMPORARY TABLE IF NOT EXISTS temp_match_drafts (match_id INTEGER, data TEXT);

-- Import active patch if it doesn't exist
INSERT OR IGNORE INTO patches (name, release_date, is_active, weight)
VALUES ('7.35d', '2024-03-01', 1, 1.0);

-- Create match_hero_picks table if it doesn't exist (for analysis)
CREATE TABLE IF NOT EXISTS match_hero_picks (
    match_id BIGINT, 
    hero_id INTEGER, 
    team INTEGER, -- 0 for Radiant, 1 for Dire
    position INTEGER, -- 1-5 positions
    is_winner BOOLEAN,
    PRIMARY KEY (match_id, hero_id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_match_hero_picks_match_id ON match_hero_picks(match_id);
CREATE INDEX IF NOT EXISTS idx_match_hero_picks_hero_id ON match_hero_picks(hero_id);

-- Update database version
INSERT OR IGNORE INTO db_version (version, description) 
VALUES (4, 'Import match data - SQLite compatible');

-- Drop temporary tables
DROP TABLE IF EXISTS temp_match_details;
DROP TABLE IF EXISTS temp_match_drafts;