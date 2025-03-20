-- Script to verify PostgreSQL connection and check table structure

-- Display PostgreSQL version
SELECT version();

-- Check if tables exist
SELECT table_name 
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

-- Count rows in important tables
SELECT 'heroes' AS table_name, COUNT(*) AS row_count FROM heroes
UNION ALL
SELECT 'abilities', COUNT(*) FROM abilities
UNION ALL
SELECT 'matches', COUNT(*) FROM matches
UNION ALL
SELECT 'players', COUNT(*) FROM players
UNION ALL
SELECT 'patches', COUNT(*) FROM patches
UNION ALL
SELECT 'hero_synergies', COUNT(*) FROM hero_synergies
UNION ALL
SELECT 'hero_counters', COUNT(*) FROM hero_counters
ORDER BY table_name;

-- Check database user permissions
SELECT 
    grantee, 
    privilege_type
FROM 
    information_schema.role_table_grants 
WHERE 
    table_name = 'heroes' AND 
    grantee = 'dota2_user';

-- If you have no records, try importing the simplified match data
-- psql -U postgres -d dota2_draft_assistant -f H:/Projects/dota2_draft_assistant/scripts/import_matches_simplified.sql