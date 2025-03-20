-- SQLite version (without PostgreSQL syntax)
-- Create a temporary table to hold the JSON data
CREATE TEMPORARY TABLE IF NOT EXISTS temp_abilities (data TEXT);

-- This is just a placeholder since the real data will be loaded through Java

-- Update database version
INSERT OR IGNORE INTO db_version (version, description) 
VALUES (3, 'Import ability data - SQLite compatible');

-- Drop temporary table
DROP TABLE IF EXISTS temp_abilities;