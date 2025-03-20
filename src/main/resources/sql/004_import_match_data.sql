-- SQLite version (without PostgreSQL syntax)
-- Create temporary tables to hold the JSON data
CREATE TEMPORARY TABLE IF NOT EXISTS temp_match_details (match_id INTEGER, data TEXT);
CREATE TEMPORARY TABLE IF NOT EXISTS temp_match_drafts (match_id INTEGER, data TEXT);

-- Import active patch if it doesn't exist
INSERT OR IGNORE INTO patches (name, release_date, is_active, weight)
VALUES ('7.35d', '2024-03-01', 1, 1.0);

-- This is just a placeholder since the real data will be loaded through Java

-- Update database version
INSERT OR IGNORE INTO db_version (version, description) 
VALUES (4, 'Import match data - SQLite compatible');

-- Drop temporary tables
DROP TABLE IF EXISTS temp_match_details;
DROP TABLE IF EXISTS temp_match_drafts;