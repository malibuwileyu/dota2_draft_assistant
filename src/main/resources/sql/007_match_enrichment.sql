-- PostgreSQL script for match enrichment functionality
-- This script adds the necessary tables and columns for match data enrichment

-- Add has_details column to matches table if it doesn't already exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'matches' AND column_name = 'has_details'
    ) THEN
        ALTER TABLE matches ADD COLUMN has_details BOOLEAN DEFAULT FALSE;
        RAISE NOTICE 'Added has_details column to matches table';
    ELSE
        RAISE NOTICE 'has_details column already exists in matches table';
    END IF;
END
$$;

-- Create match_details table if it doesn't exist
CREATE TABLE IF NOT EXISTS match_details (
    match_id BIGINT PRIMARY KEY,
    raw_data TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (match_id) REFERENCES matches(id)
);

-- Create index on match_details updated_at for faster queries
CREATE INDEX IF NOT EXISTS idx_match_details_updated_at ON match_details(updated_at);

-- Add index for has_details to efficiently find matches needing enrichment
CREATE INDEX IF NOT EXISTS idx_matches_has_details ON matches(has_details);

-- Add a function to find matches needing enrichment
CREATE OR REPLACE FUNCTION find_matches_needing_enrichment(_limit INTEGER DEFAULT 100)
RETURNS TABLE (match_id BIGINT) AS $$
BEGIN
    RETURN QUERY 
    SELECT m.id 
    FROM matches m
    LEFT JOIN match_details md ON m.id = md.match_id
    WHERE (m.has_details = false OR m.has_details IS NULL)
    AND md.match_id IS NULL
    ORDER BY m.start_time DESC
    LIMIT _limit;
END;
$$ LANGUAGE plpgsql;

-- Add function to get match details
CREATE OR REPLACE FUNCTION get_match_details(_match_id BIGINT)
RETURNS TABLE (
    match_id BIGINT,
    start_time TIMESTAMP,
    duration INTEGER,
    radiant_win BOOLEAN,
    game_mode INTEGER,
    lobby_type INTEGER,
    has_details BOOLEAN,
    raw_details TEXT
) AS $$
BEGIN
    RETURN QUERY 
    SELECT 
        m.id,
        m.start_time,
        m.duration,
        m.radiant_win,
        m.game_mode,
        m.lobby_type,
        m.has_details,
        md.raw_data
    FROM matches m
    LEFT JOIN match_details md ON m.id = md.match_id
    WHERE m.id = _match_id;
END;
$$ LANGUAGE plpgsql;

-- Update database version
INSERT INTO db_version (version, description) 
VALUES (7, 'Match enrichment functionality')
ON CONFLICT (version) DO UPDATE 
SET description = 'Match enrichment functionality (updated)';

-- Add lobby_type, patch, and region columns to matches table if they don't exist
DO $$
BEGIN
    -- Add lobby_type column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'matches' AND column_name = 'lobby_type'
    ) THEN
        ALTER TABLE matches ADD COLUMN lobby_type INTEGER DEFAULT 0;
        RAISE NOTICE 'Added lobby_type column to matches table';
    END IF;

    -- Add patch column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'matches' AND column_name = 'patch'
    ) THEN
        ALTER TABLE matches ADD COLUMN patch INTEGER DEFAULT NULL;
        RAISE NOTICE 'Added patch column to matches table';
    END IF;

    -- Add region column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'matches' AND column_name = 'region'
    ) THEN
        ALTER TABLE matches ADD COLUMN region INTEGER DEFAULT NULL;
        RAISE NOTICE 'Added region column to matches table';
    END IF;
END
$$;