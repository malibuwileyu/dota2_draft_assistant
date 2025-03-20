-- Fix database schema for Dota 2 Draft Assistant

-- 1. Add image_url column to heroes table if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'heroes' AND column_name = 'image_url'
    ) THEN
        ALTER TABLE heroes ADD COLUMN image_url TEXT;
        
        -- Copy data from image_path to image_url if image_path exists
        IF EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_name = 'heroes' AND column_name = 'image_path'
        ) THEN
            UPDATE heroes SET image_url = image_path;
        END IF;
    END IF;
END $$;

-- 2. Create match_hero_picks table if it doesn't exist
CREATE TABLE IF NOT EXISTS match_hero_picks (
    match_id BIGINT, 
    hero_id INTEGER, 
    team INTEGER, -- 0 for Radiant, 1 for Dire
    position INTEGER, -- 1-5 positions
    is_winner BOOLEAN,
    PRIMARY KEY (match_id, hero_id)
);

-- 3. Fix foreign key reference - first remove if already exists with wrong reference
DO $$
BEGIN
    -- Check if there's a foreign key constraint
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE table_name = 'match_hero_picks' 
        AND constraint_type = 'FOREIGN KEY'
    ) THEN
        -- Drop the existing constraint
        ALTER TABLE match_hero_picks DROP CONSTRAINT IF EXISTS match_hero_picks_match_id_fkey;
    END IF;
    
    -- Now add the correct foreign key constraint referencing matches(id)
    ALTER TABLE match_hero_picks ADD CONSTRAINT match_hero_picks_match_id_fkey 
    FOREIGN KEY (match_id) REFERENCES matches(id);
EXCEPTION
    -- If there's an error (e.g., constraint doesn't exist or table doesn't exist), just continue
    WHEN OTHERS THEN
        RAISE NOTICE 'Error fixing foreign key constraint: %', SQLERRM;
END $$;

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_match_hero_picks_match_id ON match_hero_picks(match_id);
CREATE INDEX IF NOT EXISTS idx_match_hero_picks_hero_id ON match_hero_picks(hero_id);