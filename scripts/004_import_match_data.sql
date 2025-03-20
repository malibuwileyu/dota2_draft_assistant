-- Script to import match data from JSON files

-- Create temporary tables to hold the JSON data
CREATE TEMPORARY TABLE temp_match_details (match_id BIGINT, data JSONB);
CREATE TEMPORARY TABLE temp_match_drafts (match_id BIGINT, data JSONB);

-- Create a function to load match details
CREATE OR REPLACE FUNCTION load_match_details()
RETURNS VOID AS $$$
DECLARE
    json_file TEXT;
    match_id BIGINT;
    match_data JSONB;
    draft_data JSONB;
    player_entry JSONB;
    patch_id INTEGER;
    match_data_row RECORD;
BEGIN
    -- Import active patch if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM patches WHERE is_active = TRUE) THEN
        INSERT INTO patches (name, release_date, end_date, is_active, weight)
        VALUES ('7.35d', '2024-03-01', NULL, TRUE, 1.0)
        RETURNING id INTO patch_id;
    ELSE
        SELECT id INTO patch_id FROM patches WHERE is_active = TRUE;
    END IF;
    
    -- Process each match detail file in the temp table
    FOR match_data_row IN SELECT * FROM temp_match_details
    LOOP
        match_id := match_data_row.match_id;
        match_data := match_data_row.data;
        
        -- Insert match data
        INSERT INTO matches (
            id, patch_id, start_time, duration, radiant_win, 
            game_mode, league_id, league_name, match_data, added_date
        ) VALUES (
            match_id,
            patch_id,
            to_timestamp((match_data->>'start_time')::bigint),
            (match_data->>'duration')::integer,
            (match_data->>'radiant_win')::boolean,
            (match_data->>'game_mode')::integer,
            (match_data->>'leagueid')::integer,
            match_data->>'league_name',
            match_data::text,
            CURRENT_TIMESTAMP
        ) ON CONFLICT (id) DO NOTHING;
        
        -- Insert player data
        FOR player_entry IN SELECT value FROM jsonb_array_elements(match_data->'players')
        LOOP
            -- Insert player record if it doesn't exist
            INSERT INTO players (account_id, steam_id, personaname, last_match_time)
            VALUES (
                (player_entry->>'account_id')::bigint,
                player_entry->>'steamid',
                player_entry->>'personaname',
                CURRENT_TIMESTAMP
            ) ON CONFLICT (account_id) DO UPDATE SET 
                last_match_time = CURRENT_TIMESTAMP,
                personaname = EXCLUDED.personaname;
            
            -- Insert match player data
            INSERT INTO match_players (
                match_id, account_id, hero_id, player_slot, is_radiant,
                kills, deaths, assists, gold_per_min, xp_per_min,
                hero_damage, tower_damage, hero_healing, last_hits, lane
            ) VALUES (
                match_id,
                (player_entry->>'account_id')::bigint,
                (player_entry->>'hero_id')::integer,
                (player_entry->>'player_slot')::integer,
                player_entry->>'isRadiant' = 'true',
                (player_entry->>'kills')::integer,
                (player_entry->>'deaths')::integer,
                (player_entry->>'assists')::integer,
                (player_entry->>'gold_per_min')::real,
                (player_entry->>'xp_per_min')::real,
                (player_entry->>'hero_damage')::integer,
                (player_entry->>'tower_damage')::integer,
                (player_entry->>'hero_healing')::integer,
                (player_entry->>'last_hits')::integer,
                (player_entry->>'lane')::integer
            ) ON CONFLICT DO NOTHING;
            
            -- Update player hero statistics
            INSERT INTO player_heroes (account_id, hero_id, games, wins, last_played)
            VALUES (
                (player_entry->>'account_id')::bigint,
                (player_entry->>'hero_id')::integer,
                1,
                CASE WHEN 
                    (player_entry->>'isRadiant' = 'true' AND match_data->>'radiant_win' = 'true') OR
                    (player_entry->>'isRadiant' = 'false' AND match_data->>'radiant_win' = 'false')
                THEN 1 ELSE 0 END,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (account_id, hero_id) DO UPDATE SET
                games = player_heroes.games + 1,
                wins = player_heroes.wins + CASE WHEN 
                    (player_entry->>'isRadiant' = 'true' AND match_data->>'radiant_win' = 'true') OR
                    (player_entry->>'isRadiant' = 'false' AND match_data->>'radiant_win' = 'false')
                THEN 1 ELSE 0 END,
                last_played = CURRENT_TIMESTAMP;
        END LOOP;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Create a function to load draft data
CREATE OR REPLACE FUNCTION load_match_drafts()
RETURNS VOID AS $$$
DECLARE
    draft_data_row RECORD;
    draft_action JSONB;
    hero_id INTEGER;
    team TEXT;
    is_pick BOOLEAN;
    action_order INTEGER;
BEGIN
    -- Process each draft file in the temp table
    FOR draft_data_row IN SELECT * FROM temp_match_drafts
    LOOP
        -- Process each draft action
        FOR draft_action IN SELECT jsonb_array_elements(draft_data_row.data->'draft') AS action
        LOOP
            hero_id := (draft_action.action->>'hero_id')::integer;
            team := draft_action.action->>'team';
            is_pick := draft_action.action->>'is_pick' = 'true';
            action_order := (draft_action.action->>'order')::integer;
            
            -- Insert draft action
            INSERT INTO draft_actions (match_id, hero_id, is_pick, team, "order")
            VALUES (draft_data_row.match_id, hero_id, is_pick, team, action_order)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
    
    -- Update meta statistics based on draft data
    INSERT INTO meta_statistics (patch_id, hero_id, pick_count, ban_count, win_count, pick_rate, ban_rate, win_rate, league_id)
    WITH stats AS (
        SELECT 
            p.id AS patch_id,
            d.hero_id,
            m.league_id,
            SUM(CASE WHEN d.is_pick THEN 1 ELSE 0 END) AS picks,
            SUM(CASE WHEN NOT d.is_pick THEN 1 ELSE 0 END) AS bans,
            SUM(CASE WHEN d.is_pick AND 
                ((d.team = 'radiant' AND m.radiant_win) OR 
                 (d.team = 'dire' AND NOT m.radiant_win))
                THEN 1 ELSE 0 END) AS wins
        FROM draft_actions d
        JOIN matches m ON d.match_id = m.id
        JOIN patches p ON m.patch_id = p.id
        GROUP BY p.id, d.hero_id, m.league_id
    )
    SELECT 
        patch_id,
        hero_id,
        picks AS pick_count,
        bans AS ban_count,
        wins AS win_count,
        picks::float / NULLIF((SELECT COUNT(DISTINCT match_id) FROM draft_actions), 0) AS pick_rate,
        bans::float / NULLIF((SELECT COUNT(DISTINCT match_id) FROM draft_actions), 0) AS ban_rate,
        wins::float / NULLIF(picks, 0) AS win_rate,
        league_id
    FROM stats
    ON CONFLICT (patch_id, hero_id, league_id) DO UPDATE SET
        pick_count = EXCLUDED.pick_count,
        ban_count = EXCLUDED.ban_count,
        win_count = EXCLUDED.win_count,
        pick_rate = EXCLUDED.pick_rate,
        ban_rate = EXCLUDED.ban_rate,
        win_rate = EXCLUDED.win_rate;
END;
$$ LANGUAGE plpgsql;

-- Function to load a single match file
CREATE OR REPLACE FUNCTION load_match_file(file_path TEXT, match_id BIGINT, is_draft BOOLEAN)
RETURNS VOID AS $$$
DECLARE
    json_content TEXT;
    parsed_data JSONB;
BEGIN
    -- Read the file content using platform-compatible approach
    IF current_setting('server_version_num')::integer >= 100000 THEN
        -- For PostgreSQL 10+ use pg_read_file
        SELECT pg_read_file(file_path) INTO json_content;
        
        -- Parse the JSON and load into the appropriate temporary table
        IF is_draft THEN
            -- For draft files
            parsed_data := jsonb_build_object('draft', COALESCE(json_content::jsonb, '[]'::jsonb));
            INSERT INTO temp_match_drafts (match_id, data) 
            VALUES (match_id, jsonb_strip_nulls(parsed_data));
        ELSE
            -- For match detail files
            parsed_data := json_content::jsonb;
            INSERT INTO temp_match_details (match_id, data)
            VALUES (match_id, jsonb_strip_nulls(parsed_data));
        END IF;
    ELSE
        -- For older versions or when permissions are an issue
        IF is_draft THEN
            -- For draft files
            EXECUTE format('COPY (SELECT %L, jsonb_strip_nulls(jsonb_build_object(''draft'', 
                            COALESCE((SELECT data FROM pg_read_binary_file(''%s'')::text::jsonb), ''[]''::jsonb)))) 
                            TO ''%s\temp\match_%s.json''', 
                            match_id, file_path, substring(file_path from '(.*)src'), match_id);
            EXECUTE format('COPY temp_match_drafts FROM ''%s\temp\match_%s.json''', 
                          substring(file_path from '(.*)src'), match_id);
        ELSE
            -- For match detail files
            EXECUTE format('COPY (SELECT %L, jsonb_strip_nulls((SELECT data FROM pg_read_binary_file(''%s'')::text::jsonb))) 
                            TO ''%s\temp\match_%s.json''', 
                            match_id, file_path, substring(file_path from '(.*)src'), match_id);
            EXECUTE format('COPY temp_match_details FROM ''%s\temp\match_%s.json''', 
                          substring(file_path from '(.*)src'), match_id);
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Import match details from the resources directory
DO $$
DECLARE
    file_path TEXT;
    match_id BIGINT;
    file_name TEXT;
    details_exists BOOLEAN;
BEGIN
    -- Check if details directory exists
    BEGIN
        PERFORM pg_ls_dir('H:/Projects/dota2_draft_assistant/src/main/resources/data/matches/details/');
        details_exists := true;
    EXCEPTION WHEN OTHERS THEN
        details_exists := false;
    END;

    -- Import match details if directory exists
    IF details_exists THEN
        FOR file_name IN 
            SELECT 'H:/Projects/dota2_draft_assistant/src/main/resources/data/matches/details/' || file 
            FROM pg_ls_dir('H:/Projects/dota2_draft_assistant/src/main/resources/data/matches/details/') AS file
            WHERE file LIKE 'match_%.json'
        LOOP
            -- Extract match ID from filename
            match_id := (regexp_replace(file_name, '.*match_([0-9]+)\.json$', E'\\1'))::BIGINT;
            
            -- Load the match file
            PERFORM load_match_file(file_name, match_id, false);
        END LOOP;
    END IF;
    
    -- Process the match details if we loaded any
    IF details_exists THEN
        PERFORM load_match_details();
    END IF;
    
    -- Check if drafts directory exists
    DECLARE
        drafts_exists BOOLEAN;
    BEGIN
        BEGIN
            PERFORM pg_ls_dir('H:/Projects/dota2_draft_assistant/src/main/resources/data/matches/drafts/');
            drafts_exists := true;
        EXCEPTION WHEN OTHERS THEN
            drafts_exists := false;
        END;
        
        -- Import draft data if directory exists
        IF drafts_exists THEN
            FOR file_name IN 
                SELECT 'H:/Projects/dota2_draft_assistant/src/main/resources/data/matches/drafts/' || file 
                FROM pg_ls_dir('H:/Projects/dota2_draft_assistant/src/main/resources/data/matches/drafts/') AS file
                WHERE file LIKE 'draft_%.json'
            LOOP
                -- Extract match ID from filename
                match_id := (regexp_replace(file_name, '.*draft_([0-9]+)\.json$', E'\\1'))::BIGINT;
                
                -- Load the draft file
                PERFORM load_match_file(file_name, match_id, true);
            END LOOP;
        END IF;
        
        -- Process the draft data if we loaded any
        IF drafts_exists THEN
            PERFORM load_match_drafts();
        END IF;
    END;
END $$;

-- Calculate hero synergies based on match data
DO $$
BEGIN
    -- Only perform calculations if we have match data
    IF EXISTS (SELECT 1 FROM matches LIMIT 1) THEN
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
    END IF;
END $$;

-- Calculate hero counters based on match data
DO $$
BEGIN
    -- Only perform calculations if we have match data
    IF EXISTS (SELECT 1 FROM matches LIMIT 1) THEN
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
    END IF;
END $$;

-- Drop temporary functions and tables
DROP FUNCTION load_match_file(TEXT, BIGINT, BOOLEAN);
DROP FUNCTION load_match_details();
DROP FUNCTION load_match_drafts();
DROP TABLE temp_match_details;
DROP TABLE temp_match_drafts;

-- Verify the data loaded correctly
SELECT COUNT(*) AS match_count FROM matches;
SELECT COUNT(*) AS player_count FROM match_players;
SELECT COUNT(*) AS draft_action_count FROM draft_actions;