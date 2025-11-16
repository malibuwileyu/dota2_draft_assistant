-- Migration to add a unique constraint to match_players table for match_id and account_id
-- This ensures that a player can only appear once in each match, which is logically correct
-- and enables ON CONFLICT (match_id, account_id) to work properly

-- Insert version record
INSERT INTO db_version (version, description) VALUES (9, 'Add unique constraint to match_players') 
ON CONFLICT (version) DO NOTHING;

-- Remove any potential duplicates first
-- This deletes the duplicates by keeping the lowest ID for each match_id, account_id pair
DELETE FROM match_players
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY match_id, account_id ORDER BY id) as rnum
        FROM match_players
    ) t
    WHERE t.rnum > 1
);

-- Handle the special case of test data with match_id=123456789 and account_id=111111
-- Delete these test records since they're causing problems
DELETE FROM match_players WHERE match_id = 123456789 AND account_id = 111111;

-- Add the unique constraint if it doesn't exist
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'match_players_match_account_unique') THEN
        ALTER TABLE match_players ADD CONSTRAINT match_players_match_account_unique UNIQUE (match_id, account_id);
    END IF;
END $$;