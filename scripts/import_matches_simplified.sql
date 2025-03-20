-- Simplified script to populate the matches table with sample data
-- This creates a single active patch and static match data

-- Create active patch if it doesn't exist
INSERT INTO patches (name, release_date, end_date, is_active, weight)
VALUES ('7.35d', '2024-03-01', NULL, TRUE, 1.0)
ON CONFLICT (name) DO NOTHING;

-- Insert sample match data
INSERT INTO matches (id, patch_id, start_time, duration, radiant_win, game_mode, league_id, league_name, match_data)
VALUES 
  (123456789, (SELECT id FROM patches WHERE is_active = TRUE), NOW(), 3600, TRUE, 1, 1, 'Test League', '{}'::TEXT),
  (123456790, (SELECT id FROM patches WHERE is_active = TRUE), NOW(), 3600, FALSE, 1, 1, 'Test League', '{}'::TEXT)
ON CONFLICT (id) DO NOTHING;

-- Insert sample player data
INSERT INTO players (account_id, steam_id, personaname, last_match_time)
VALUES 
  (111111, 'steam1', 'Player1', NOW()),
  (222222, 'steam2', 'Player2', NOW()),
  (333333, 'steam3', 'Player3', NOW()),
  (444444, 'steam4', 'Player4', NOW()) 
ON CONFLICT (account_id) DO NOTHING;

-- Insert match player data
INSERT INTO match_players (match_id, account_id, hero_id, player_slot, is_radiant, kills, deaths, assists)
VALUES 
  (123456789, 111111, 1, 0, TRUE, 10, 5, 15),
  (123456789, 222222, 2, 1, TRUE, 8, 7, 12),
  (123456789, 333333, 3, 2, FALSE, 6, 10, 8),
  (123456789, 444444, 4, 3, FALSE, 3, 12, 5),
  (123456790, 111111, 5, 0, TRUE, 7, 8, 10),
  (123456790, 222222, 6, 1, TRUE, 5, 9, 11)
ON CONFLICT DO NOTHING;

-- Insert sample hero synergies
INSERT INTO hero_synergies (hero1_id, hero2_id, games, wins, synergy_score)
VALUES 
  (1, 2, 100, 60, 60.0),
  (3, 4, 80, 40, 50.0),
  (5, 6, 90, 54, 60.0)
ON CONFLICT (hero1_id, hero2_id) DO UPDATE SET
  games = EXCLUDED.games,
  wins = EXCLUDED.wins,
  synergy_score = EXCLUDED.synergy_score;

-- Insert sample hero counters
INSERT INTO hero_counters (hero_id, counter_id, games, wins, counter_score)
VALUES 
  (1, 3, 120, 80, 66.7),
  (2, 4, 100, 65, 65.0),
  (5, 3, 110, 70, 63.6)
ON CONFLICT (hero_id, counter_id) DO UPDATE SET
  games = EXCLUDED.games,
  wins = EXCLUDED.wins,
  counter_score = EXCLUDED.counter_score;

-- Output count of records
SELECT COUNT(*) AS match_count FROM matches;
SELECT COUNT(*) AS player_count FROM players;
SELECT COUNT(*) AS match_player_count FROM match_players;
SELECT COUNT(*) AS synergy_count FROM hero_synergies;
SELECT COUNT(*) AS counter_count FROM hero_counters;