-- SQLite version (without ON CONFLICT)
-- Create a temporary table to hold the JSON data
CREATE TEMPORARY TABLE IF NOT EXISTS temp_heroes (data TEXT);

-- This is just a placeholder since the real data will be loaded through Java
-- PRAGMA temp_store_directory = '.';

-- Update database version
INSERT OR IGNORE INTO db_version (version, description) 
VALUES (2, 'Import hero data - SQLite compatible');

-- Drop temporary table
DROP TABLE IF EXISTS temp_heroes;