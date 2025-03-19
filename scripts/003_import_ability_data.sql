-- Script to import ability data from JSON file

-- Create a temporary table to hold the JSON data
CREATE TEMPORARY TABLE temp_abilities (data JSONB);

-- Create a function to load the JSON file
CREATE OR REPLACE FUNCTION load_abilities_json(file_path TEXT)
RETURNS VOID AS $$
DECLARE
    json_content TEXT;
    hero_id INTEGER;
    ability_data JSONB;
    ability_id INTEGER;
    hero_data RECORD;
BEGIN
    -- Read the file content using platform-compatible approach
    IF current_setting('server_version_num')::integer >= 100000 THEN
        -- For PostgreSQL 10+ use pg_read_file
        SELECT pg_read_file(file_path) INTO json_content;
        INSERT INTO temp_abilities (data) VALUES (json_content::jsonb);
    ELSE
        -- For older versions or when permissions are an issue, use the command to copy content
        EXECUTE format('COPY temp_abilities FROM PROGRAM ''"%s\windows\import_json_helper.bat" "%s" "nul" > "%s\temp\abilities.json" && type "%s\temp\abilities.json"''', 
                     substring(file_path from '(.*)src'), file_path, substring(file_path from '(.*)src'), substring(file_path from '(.*)src'));
    END IF;
    
    -- Process the JSON data
    FOR hero_data IN SELECT * FROM jsonb_array_elements((SELECT data->'heroes' FROM temp_abilities LIMIT 1)) AS hero
    LOOP
        -- Get the hero ID
        hero_id := (hero_data.hero->>'id')::INTEGER;
        
        -- Insert regular abilities
        FOR ability_data IN SELECT jsonb_array_elements(hero_data.hero->'abilities') AS ability
        LOOP
            -- Insert into abilities table
            INSERT INTO abilities (
                hero_id, name, localized_name, description, ability_type, behavior, damage_type, is_ultimate
            ) VALUES (
                hero_id,
                ability_data.ability->>'name',
                ability_data.ability->>'name',
                ability_data.ability->>'description',
                ability_data.ability->>'type',
                ability_data.ability->>'behavior',
                ability_data.ability->>'damage_type',
                CASE WHEN ability_data.ability->>'type' = 'ultimate' THEN TRUE ELSE FALSE END
            )
            RETURNING id INTO ability_id;
            
            -- Insert special values as ability attributes
            IF ability_data.ability->'special_values' IS NOT NULL THEN
                INSERT INTO ability_attributes (ability_id, name, value, is_core)
                SELECT 
                    ability_id,
                    key,
                    jsonb_typeof(value) = 'array' 
                        ? jsonb_array_elements_text(value)::TEXT
                        : value::TEXT,
                    TRUE
                FROM jsonb_each(ability_data.ability->'special_values')
                WHERE jsonb_typeof(value) != 'object';
            END IF;
        END LOOP;
        
        -- Insert innate abilities
        IF hero_data.hero->'innate_abilities' IS NOT NULL AND jsonb_array_length(hero_data.hero->'innate_abilities') > 0 THEN
            FOR ability_data IN SELECT jsonb_array_elements(hero_data.hero->'innate_abilities') AS ability
            LOOP
                -- Insert into abilities table
                INSERT INTO abilities (
                    hero_id, name, localized_name, description, ability_type, behavior, damage_type, is_ultimate
                ) VALUES (
                    hero_id,
                    ability_data.ability->>'name',
                    ability_data.ability->>'name',
                    ability_data.ability->>'description',
                    'innate',
                    ability_data.ability->>'behavior',
                    'none',
                    FALSE
                )
                RETURNING id INTO ability_id;
                
                -- Insert special values as ability attributes
                IF ability_data.ability->'special_values' IS NOT NULL THEN
                    INSERT INTO ability_attributes (ability_id, name, value, is_core)
                    SELECT 
                        ability_id,
                        key,
                        CASE WHEN jsonb_typeof(value) = 'array' 
                            THEN (SELECT jsonb_array_elements_text(value) LIMIT 1)::TEXT
                            ELSE value::TEXT
                        END,
                        TRUE
                    FROM jsonb_each(ability_data.ability->'special_values')
                    WHERE jsonb_typeof(value) != 'object';
                END IF;
            END LOOP;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Use the function to load abilities data
SELECT load_abilities_json('src/main/resources/data/abilities/all_heroes_abilities.json');

-- Drop the function and temporary table
DROP FUNCTION load_abilities_json(TEXT);
DROP TABLE temp_abilities;

-- Verify the data loaded correctly
SELECT COUNT(*) AS ability_count FROM abilities;
SELECT hero_id, name, ability_type FROM abilities LIMIT 20;