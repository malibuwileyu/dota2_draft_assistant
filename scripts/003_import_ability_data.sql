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
    hero_row JSONB;
    ability_row JSONB;
    innate_ability JSONB;
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
    
    -- Process the JSON data - extract the heroes array
    FOR hero_row IN 
        SELECT jsonb_array_elements((SELECT data->'heroes' FROM temp_abilities LIMIT 1))
    LOOP
        -- Get the hero ID
        hero_id := (hero_row->>'id')::INTEGER;
        
        -- Process regular abilities
        IF (hero_row->'abilities') IS NOT NULL THEN
            FOR ability_row IN 
                SELECT jsonb_array_elements(hero_row->'abilities')
            LOOP
                -- Insert into abilities table
                INSERT INTO abilities (
                    hero_id, name, localized_name, description, ability_type, behavior, damage_type, is_ultimate
                ) VALUES (
                    hero_id,
                    ability_row->>'name',
                    ability_row->>'name',
                    ability_row->>'description',
                    ability_row->>'type',
                    ability_row->>'behavior',
                    ability_row->>'damage_type',
                    CASE WHEN ability_row->>'type' = 'ultimate' THEN TRUE ELSE FALSE END
                )
                RETURNING id INTO ability_id;
                
                -- Insert special values as ability attributes
                IF (ability_row->'special_values') IS NOT NULL THEN
                    INSERT INTO ability_attributes (ability_id, name, value, is_core)
                    SELECT 
                        ability_id,
                        key,
                        CASE WHEN jsonb_typeof(value) = 'array' 
                            THEN (SELECT string_agg(t::TEXT, ',') FROM jsonb_array_elements_text(value) AS t)
                            ELSE value::TEXT
                        END,
                        TRUE
                    FROM jsonb_each(ability_row->'special_values')
                    WHERE jsonb_typeof(value) != 'object';
                END IF;
            END LOOP;
        END IF;
        
        -- Process innate abilities
        IF (hero_row->'innate_abilities') IS NOT NULL AND jsonb_array_length(hero_row->'innate_abilities') > 0 THEN
            FOR innate_ability IN 
                SELECT jsonb_array_elements(hero_row->'innate_abilities')
            LOOP
                -- Insert into abilities table
                INSERT INTO abilities (
                    hero_id, name, localized_name, description, ability_type, behavior, damage_type, is_ultimate
                ) VALUES (
                    hero_id,
                    innate_ability->>'name',
                    innate_ability->>'name',
                    innate_ability->>'description',
                    'innate',
                    innate_ability->>'behavior',
                    'none',
                    FALSE
                )
                RETURNING id INTO ability_id;
                
                -- Insert special values as ability attributes
                IF (innate_ability->'special_values') IS NOT NULL THEN
                    INSERT INTO ability_attributes (ability_id, name, value, is_core)
                    SELECT 
                        ability_id,
                        key,
                        CASE WHEN jsonb_typeof(value) = 'array' 
                            THEN (SELECT string_agg(t::TEXT, ',') FROM jsonb_array_elements_text(value) AS t)
                            ELSE value::TEXT
                        END,
                        TRUE
                    FROM jsonb_each(innate_ability->'special_values')
                    WHERE jsonb_typeof(value) != 'object';
                END IF;
            END LOOP;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Use the function to load abilities data
SELECT load_abilities_json('H:/Projects/dota2_draft_assistant/src/main/resources/data/abilities/all_heroes_abilities.json');

-- Drop the function and temporary table
DROP FUNCTION load_abilities_json(TEXT);
DROP TABLE temp_abilities;

-- Verify the data loaded correctly
SELECT COUNT(*) AS ability_count FROM abilities;
SELECT hero_id, name, ability_type FROM abilities LIMIT 20;