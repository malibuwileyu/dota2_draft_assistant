-- Script to import hero data from JSON file

-- Create a temporary table to hold the JSON data
CREATE TEMPORARY TABLE temp_heroes (data JSONB);

-- Create a function to load the JSON file
CREATE OR REPLACE FUNCTION load_hero_json(file_path TEXT)
RETURNS VOID AS $$
DECLARE
    json_content TEXT;
BEGIN
    -- Read the file content using platform-compatible approach
    IF current_setting('server_version_num')::integer >= 100000 THEN
        -- For PostgreSQL 10+ use pg_read_file
        SELECT pg_read_file(file_path) INTO json_content;
        INSERT INTO temp_heroes (data) VALUES (json_content::jsonb);
    ELSE
        -- For older versions or when permissions are an issue, use the command to copy content
        EXECUTE format('COPY temp_heroes FROM PROGRAM ''"%s\windows\import_json_helper.bat" "%s" "nul" > "%s\temp\heroes.json" && type "%s\temp\heroes.json"''', 
                      substring(file_path from '(.*)src'), file_path, substring(file_path from '(.*)src'), substring(file_path from '(.*)src'));
    END IF;
    
    -- Process the JSON data
    INSERT INTO heroes (
        id, name, localized_name, primary_attr, attack_type, roles,
        base_health, base_mana, base_armor, base_mr,
        base_attack_min, base_attack_max, 
        base_str, base_agi, base_int,
        str_gain, agi_gain, int_gain,
        attack_range, move_speed,
        image_path, icon_path
    )
    SELECT 
        (hero->>'id')::INTEGER AS id,
        hero->>'name' AS name,
        COALESCE(hero->>'localized_name', hero->>'name') AS localized_name,
        hero->>'primary_attr' AS primary_attr,
        hero->>'attack_type' AS attack_type,
        hero->>'roles' AS roles,
        (hero->>'base_health')::REAL AS base_health,
        (hero->>'base_mana')::REAL AS base_mana,
        (hero->>'base_armor')::REAL AS base_armor,
        (hero->>'base_mr')::REAL AS base_mr,
        (hero->>'base_attack_min')::INTEGER AS base_attack_min,
        (hero->>'base_attack_max')::INTEGER AS base_attack_max,
        (hero->>'base_str')::INTEGER AS base_str,
        (hero->>'base_agi')::INTEGER AS base_agi,
        (hero->>'base_int')::INTEGER AS base_int,
        (hero->>'str_gain')::REAL AS str_gain,
        (hero->>'agi_gain')::REAL AS agi_gain,
        (hero->>'int_gain')::REAL AS int_gain,
        (hero->>'attack_range')::INTEGER AS attack_range,
        (hero->>'move_speed')::INTEGER AS move_speed,
        hero->>'img' AS image_path,
        hero->>'icon' AS icon_path
    FROM jsonb_array_elements(
        (SELECT data FROM temp_heroes LIMIT 1)
    ) AS hero;
END;
$$ LANGUAGE plpgsql;

-- Use the function to load heroes data
SELECT load_hero_json('H:/Projects/dota2_draft_assistant/src/main/resources/data/heroes.json');

-- Drop the function and temporary table
DROP FUNCTION load_hero_json(TEXT);
DROP TABLE temp_heroes;

-- Verify the data loaded correctly
SELECT COUNT(*) AS hero_count FROM heroes;
SELECT id, localized_name, primary_attr FROM heroes LIMIT 10;