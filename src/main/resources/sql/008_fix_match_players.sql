-- Fix for match_players table - Add missing 'won' column
-- This script adds the won column to the match_players table

-- Add won column to match_players table if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'match_players' AND column_name = 'won'
    ) THEN
        ALTER TABLE match_players ADD COLUMN won BOOLEAN DEFAULT FALSE;
        RAISE NOTICE 'Added won column to match_players table';
    ELSE
        RAISE NOTICE 'won column already exists in match_players table';
    END IF;
END
$$;

-- Update database version
INSERT INTO db_version (version, description) 
VALUES (8, 'Added won column to match_players table')
ON CONFLICT (version) DO UPDATE 
SET description = 'Added won column to match_players table (updated)';