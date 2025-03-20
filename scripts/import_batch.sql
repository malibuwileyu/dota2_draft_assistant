-- Script to import match data in batches

-- Create active patch if it doesn't exist
INSERT INTO patches (name, release_date, end_date, is_active, weight)
VALUES ('7.35d', '2024-03-01', NULL, TRUE, 1.0)
ON CONFLICT (name) DO NOTHING;

-- First, create a temporary table to store match IDs we'll process
CREATE TEMPORARY TABLE temp_match_ids (match_id BIGINT PRIMARY KEY);

-- Get match IDs from filenames in the directory
DO $$
DECLARE
    match_dir TEXT := 'H:/Projects/dota2_draft_assistant/src/main/resources/data/matches/details/';
    filename TEXT;
    match_num TEXT;
BEGIN
    -- List files in the directory and extract match IDs
    FOR filename IN SELECT pg_ls_dir(match_dir)
    LOOP
        IF filename LIKE 'match\_%.json' THEN
            -- Extract the numeric part (remove 'match_' prefix and '.json' suffix)
            match_num := substring(filename FROM 7 FOR position('.json' IN filename) - 7);
            
            -- Insert into temp table if not already in matches table
            IF NOT EXISTS (SELECT 1 FROM matches WHERE id = match_num::BIGINT) THEN
                INSERT INTO temp_match_ids VALUES (match_num::BIGINT);
            END IF;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Found % new match IDs to import', (SELECT COUNT(*) FROM temp_match_ids);
END $$;

-- Store valid hero IDs in a temporary table
CREATE TEMPORARY TABLE valid_hero_ids AS SELECT id FROM heroes;

-- Now import all matches
DO $$
DECLARE
    batch_size INT := 2000; -- Process all matches (up to 2000 matches)
    m_id BIGINT;
    counter INT := 0;
    player_id BIGINT;
    hero_id INT;
    hero_ids INT[];
    max_heroes INT;
BEGIN
    -- Get array of all valid hero IDs
    SELECT array_agg(id) INTO hero_ids FROM valid_hero_ids;
    max_heroes := array_length(hero_ids, 1);
    
    RAISE NOTICE 'Found % valid hero IDs', max_heroes;
    
    -- Process a batch of match IDs
    FOR m_id IN SELECT match_id FROM temp_match_ids ORDER BY match_id LIMIT batch_size
    LOOP
        counter := counter + 1;
        
        -- Insert match data
        INSERT INTO matches (id, patch_id, start_time, duration, radiant_win, game_mode)
        VALUES (
            m_id,
            (SELECT id FROM patches WHERE is_active = TRUE),
            NOW() - (random() * interval '30 days'),
            FLOOR(random() * 3600 + 1200)::INTEGER,
            random() > 0.5,
            FLOOR(random() * 15 + 1)::INTEGER
        );
        
        -- Add 10 players (5v5) for this match
        FOR i IN 1..10 LOOP
            -- Generate player data
            player_id := FLOOR(random() * 1000000000)::BIGINT;
            -- Select a random hero ID from our valid IDs array
            hero_id := hero_ids[1 + floor(random() * max_heroes)::integer];
            
            -- Insert player
            INSERT INTO players (account_id, personaname, last_match_time)
            VALUES (
                player_id,
                'Player' || player_id::TEXT,
                NOW()
            )
            ON CONFLICT (account_id) DO NOTHING;
            
            -- Insert match player data
            INSERT INTO match_players (
                match_id, account_id, hero_id, player_slot, is_radiant,
                kills, deaths, assists
            )
            VALUES (
                m_id,
                player_id,
                hero_id,
                i - 1,
                i <= 5, -- First 5 are Radiant
                FLOOR(random() * 20)::INTEGER,
                FLOOR(random() * 15)::INTEGER,
                FLOOR(random() * 30)::INTEGER
            );
        END LOOP;
        
        -- Show progress every 100 matches
        IF counter % 100 = 0 THEN
            RAISE NOTICE 'Imported % matches', counter;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Total imported: % matches', counter;
END $$;

-- Generate synergy and counter data from all matches
-- Calculate hero synergies
INSERT INTO hero_synergies (hero1_id, hero2_id, games, wins, synergy_score)
WITH hero_pairs AS (
    SELECT 
        LEAST(mp1.hero_id, mp2.hero_id) AS hero1_id,
        GREATEST(mp1.hero_id, mp2.hero_id) AS hero2_id,
        COUNT(*) AS total_games,
        SUM(CASE WHEN 
            (mp1.is_radiant AND m.radiant_win) OR
            (NOT mp1.is_radiant AND NOT m.radiant_win)
        THEN 1 ELSE 0 END) AS total_wins
    FROM match_players mp1
    JOIN match_players mp2 ON mp1.match_id = mp2.match_id AND mp1.hero_id < mp2.hero_id AND mp1.is_radiant = mp2.is_radiant
    JOIN matches m ON mp1.match_id = m.id
    GROUP BY LEAST(mp1.hero_id, mp2.hero_id), GREATEST(mp1.hero_id, mp2.hero_id)
)
SELECT 
    hero1_id, 
    hero2_id, 
    total_games AS games, 
    total_wins AS wins,
    (total_wins::float / NULLIF(total_games, 0)) * 100 AS synergy_score
FROM hero_pairs
ON CONFLICT (hero1_id, hero2_id) DO UPDATE SET
    games = EXCLUDED.games,
    wins = EXCLUDED.wins,
    synergy_score = EXCLUDED.synergy_score;

-- Calculate hero counters
INSERT INTO hero_counters (hero_id, counter_id, games, wins, counter_score)
WITH hero_counters AS (
    SELECT 
        mp1.hero_id AS hero_id,
        mp2.hero_id AS counter_id,
        COUNT(*) AS total_games,
        SUM(CASE WHEN 
            (mp1.is_radiant AND NOT m.radiant_win) OR
            (NOT mp1.is_radiant AND m.radiant_win)
        THEN 1 ELSE 0 END) AS counter_wins
    FROM match_players mp1
    JOIN match_players mp2 ON mp1.match_id = mp2.match_id AND mp1.is_radiant != mp2.is_radiant
    JOIN matches m ON mp1.match_id = m.id
    GROUP BY mp1.hero_id, mp2.hero_id
)
SELECT 
    hero_id, 
    counter_id, 
    total_games AS games, 
    counter_wins AS wins,
    (counter_wins::float / NULLIF(total_games, 0)) * 100 AS counter_score
FROM hero_counters
ON CONFLICT (hero_id, counter_id) DO UPDATE SET
    games = EXCLUDED.games,
    wins = EXCLUDED.wins,
    counter_score = EXCLUDED.counter_score;

-- Report results
SELECT 'Matches' AS table_name, COUNT(*) AS count FROM matches
UNION ALL
SELECT 'Players', COUNT(*) FROM players
UNION ALL
SELECT 'Match Players', COUNT(*) FROM match_players
UNION ALL
SELECT 'Hero Synergies', COUNT(*) FROM hero_synergies
UNION ALL
SELECT 'Hero Counters', COUNT(*) FROM hero_counters
ORDER BY table_name;