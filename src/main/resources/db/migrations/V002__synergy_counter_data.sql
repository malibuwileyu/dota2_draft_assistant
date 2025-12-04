-- =============================================================================
-- V002: Default Synergy/Counter Data
-- Placeholder synergy and counter scores (to be updated from OpenDota)
-- =============================================================================

-- This migration is intentionally minimal.
-- Synergy and counter data will be populated by:
-- 1. The hero data import process (P0-010)
-- 2. OpenDota API sync (Phase 2)

-- Insert default settings
INSERT OR IGNORE INTO user_settings (key, value) VALUES ('theme', 'dark');
INSERT OR IGNORE INTO user_settings (key, value) VALUES ('recommendation_weight_synergy', '0.25');
INSERT OR IGNORE INTO user_settings (key, value) VALUES ('recommendation_weight_counter', '0.25');
INSERT OR IGNORE INTO user_settings (key, value) VALUES ('recommendation_weight_role', '0.30');
INSERT OR IGNORE INTO user_settings (key, value) VALUES ('recommendation_weight_meta', '0.20');
INSERT OR IGNORE INTO user_settings (key, value) VALUES ('timer_enabled', 'false');
INSERT OR IGNORE INTO user_settings (key, value) VALUES ('player_team', 'RADIANT');

