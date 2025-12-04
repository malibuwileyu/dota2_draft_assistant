-- =============================================================================
-- V001: Initial Schema
-- Creates core tables for heroes, abilities, synergies, and counters
-- =============================================================================

-- Heroes table
CREATE TABLE IF NOT EXISTS heroes (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    localized_name TEXT NOT NULL,
    primary_attr TEXT NOT NULL CHECK (primary_attr IN ('STRENGTH', 'AGILITY', 'INTELLIGENCE', 'UNIVERSAL')),
    attack_type TEXT NOT NULL CHECK (attack_type IN ('MELEE', 'RANGED')),
    roles TEXT NOT NULL,           -- JSON array: ["Carry", "Escape"]
    positions TEXT NOT NULL,       -- JSON object: {"1": 0.6, "2": 0.3}
    attributes TEXT NOT NULL,      -- JSON object: base stats
    image_url TEXT,
    icon_url TEXT,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

-- Abilities table
CREATE TABLE IF NOT EXISTS abilities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    hero_id INTEGER NOT NULL REFERENCES heroes(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    ability_type TEXT CHECK (ability_type IN ('basic', 'ultimate', 'innate', 'talent')),
    damage_type TEXT CHECK (damage_type IN ('physical', 'magical', 'pure', NULL)),
    cooldown TEXT,                 -- JSON array: [10, 9, 8, 7]
    mana_cost TEXT,                -- JSON array: [100, 110, 120, 130]
    created_at TEXT DEFAULT (datetime('now'))
);

-- Hero synergies (win rate when on same team)
CREATE TABLE IF NOT EXISTS hero_synergies (
    hero1_id INTEGER NOT NULL REFERENCES heroes(id) ON DELETE CASCADE,
    hero2_id INTEGER NOT NULL REFERENCES heroes(id) ON DELETE CASCADE,
    games INTEGER NOT NULL DEFAULT 0,
    wins INTEGER NOT NULL DEFAULT 0,
    synergy_score REAL NOT NULL DEFAULT 0.5,
    updated_at TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (hero1_id, hero2_id),
    CHECK (hero1_id < hero2_id)    -- Ensure consistent ordering
);

-- Hero counters (win rate when facing each other)
CREATE TABLE IF NOT EXISTS hero_counters (
    hero_id INTEGER NOT NULL REFERENCES heroes(id) ON DELETE CASCADE,
    counter_id INTEGER NOT NULL REFERENCES heroes(id) ON DELETE CASCADE,
    games INTEGER NOT NULL DEFAULT 0,
    wins INTEGER NOT NULL DEFAULT 0,
    counter_score REAL NOT NULL DEFAULT 0.5,
    updated_at TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (hero_id, counter_id)
);

-- Player hero statistics (for personalization)
CREATE TABLE IF NOT EXISTS player_hero_stats (
    account_id INTEGER NOT NULL,
    hero_id INTEGER NOT NULL REFERENCES heroes(id) ON DELETE CASCADE,
    games INTEGER NOT NULL DEFAULT 0,
    wins INTEGER NOT NULL DEFAULT 0,
    avg_kills REAL DEFAULT 0,
    avg_deaths REAL DEFAULT 0,
    avg_assists REAL DEFAULT 0,
    comfort_score REAL DEFAULT 0.5,
    last_played TEXT,
    updated_at TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (account_id, hero_id)
);

-- Draft history (saved drafts)
CREATE TABLE IF NOT EXISTS draft_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mode TEXT NOT NULL CHECK (mode IN ('CAPTAINS_MODE', 'ALL_PICK')),
    radiant_picks TEXT NOT NULL,   -- JSON array of hero IDs
    dire_picks TEXT NOT NULL,      -- JSON array of hero IDs
    radiant_bans TEXT,             -- JSON array of hero IDs
    dire_bans TEXT,                -- JSON array of hero IDs
    player_team TEXT CHECK (player_team IN ('RADIANT', 'DIRE')),
    notes TEXT,
    created_at TEXT DEFAULT (datetime('now'))
);

-- User settings
CREATE TABLE IF NOT EXISTS user_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT DEFAULT (datetime('now'))
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_abilities_hero_id ON abilities(hero_id);
CREATE INDEX IF NOT EXISTS idx_synergies_hero1 ON hero_synergies(hero1_id);
CREATE INDEX IF NOT EXISTS idx_synergies_hero2 ON hero_synergies(hero2_id);
CREATE INDEX IF NOT EXISTS idx_counters_hero ON hero_counters(hero_id);
CREATE INDEX IF NOT EXISTS idx_counters_counter ON hero_counters(counter_id);
CREATE INDEX IF NOT EXISTS idx_player_stats_account ON player_hero_stats(account_id);
CREATE INDEX IF NOT EXISTS idx_draft_history_created ON draft_history(created_at DESC);

