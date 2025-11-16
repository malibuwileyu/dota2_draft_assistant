-- Schema for match_details table to store enriched match data

-- Add has_details column to matches table if it doesn't already exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'matches' AND column_name = 'has_details'
    ) THEN
        ALTER TABLE matches ADD COLUMN has_details BOOLEAN DEFAULT FALSE;
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

-- Create index on updated_at for faster queries
CREATE INDEX IF NOT EXISTS idx_match_details_updated_at ON match_details(updated_at);

-- Comment for user reference
COMMENT ON TABLE match_details IS 'Stores detailed match data from the API for enrichment purposes';
COMMENT ON COLUMN match_details.match_id IS 'Match ID, foreign key to matches table';
COMMENT ON COLUMN match_details.raw_data IS 'Raw JSON data from the API';
COMMENT ON COLUMN match_details.updated_at IS 'Timestamp when the details were last updated';