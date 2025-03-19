-- Initial schema for Dota 2 Draft Assistant

-- Heroes table
CREATE TABLE heroes (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    localized_name TEXT NOT NULL,
    primary_attr TEXT NOT NULL,
    attack_type TEXT NOT NULL,
    roles TEXT,
    base_health REAL,
    base_mana REAL,
    base_armor REAL,
    base_mr REAL,
    base_attack_min INTEGER,
    base_attack_max INTEGER,
    base_str INTEGER,
    base_agi INTEGER,
    base_int INTEGER,
    str_gain REAL,
    agi_gain REAL,
    int_gain REAL,
    attack_range INTEGER,
    move_speed INTEGER,
    image_path TEXT,
    icon_path TEXT,
    added_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP
);

-- Abilities table
CREATE TABLE abilities (
    id SERIAL PRIMARY KEY,
    hero_id INTEGER REFERENCES heroes(id),
    name TEXT NOT NULL,
    localized_name TEXT NOT NULL,
    description TEXT,
    ability_type TEXT,
    behavior TEXT,
    affects TEXT,
    damage_type TEXT,
    is_ultimate BOOLEAN DEFAULT FALSE,
    cooldown TEXT,
    mana_cost TEXT,
    image_path TEXT,
    added_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP
);

-- Ability attributes table
CREATE TABLE ability_attributes (
    id SERIAL PRIMARY KEY,
    ability_id INTEGER REFERENCES abilities(id),
    name TEXT NOT NULL,
    value TEXT NOT NULL,
    is_core BOOLEAN DEFAULT FALSE
);

-- Patches table
CREATE TABLE patches (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    release_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    is_active BOOLEAN DEFAULT FALSE,
    weight REAL DEFAULT 1.0
);

-- Matches table
CREATE TABLE matches (
    id BIGINT PRIMARY KEY,
    patch_id INTEGER REFERENCES patches(id),
    start_time TIMESTAMP NOT NULL,
    duration INTEGER NOT NULL,
    radiant_win BOOLEAN NOT NULL,
    game_mode INTEGER NOT NULL,
    league_id INTEGER,
    league_name TEXT,
    match_data TEXT,
    added_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Match players table
CREATE TABLE match_players (
    id SERIAL PRIMARY KEY,
    match_id BIGINT REFERENCES matches(id),
    account_id BIGINT,
    hero_id INTEGER REFERENCES heroes(id),
    player_slot INTEGER,
    is_radiant BOOLEAN NOT NULL,
    kills INTEGER,
    deaths INTEGER,
    assists INTEGER,
    gold_per_min REAL,
    xp_per_min REAL,
    hero_damage INTEGER,
    tower_damage INTEGER,
    hero_healing INTEGER,
    last_hits INTEGER,
    lane INTEGER,
    role TEXT
);

-- Draft actions table
CREATE TABLE draft_actions (
    id SERIAL PRIMARY KEY,
    match_id BIGINT REFERENCES matches(id),
    hero_id INTEGER REFERENCES heroes(id),
    is_pick BOOLEAN NOT NULL,
    team TEXT NOT NULL,
    "order" INTEGER NOT NULL
);

-- Players table
CREATE TABLE players (
    account_id BIGINT PRIMARY KEY,
    steam_id TEXT UNIQUE,
    username TEXT,
    personaname TEXT,
    avatar TEXT,
    last_match_time TIMESTAMP,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP
);

-- Player heroes table
CREATE TABLE player_heroes (
    id SERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES players(account_id),
    hero_id INTEGER REFERENCES heroes(id),
    games INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    last_played TIMESTAMP,
    avg_kills REAL,
    avg_deaths REAL,
    avg_assists REAL,
    comfort_score REAL
);

-- Teams table
CREATE TABLE teams (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    tag TEXT,
    logo_url TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Team players table
CREATE TABLE team_players (
    id SERIAL PRIMARY KEY,
    team_id INTEGER REFERENCES teams(id),
    account_id BIGINT REFERENCES players(account_id),
    join_date TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Team heroes table
CREATE TABLE team_heroes (
    id SERIAL PRIMARY KEY,
    team_id INTEGER REFERENCES teams(id),
    hero_id INTEGER REFERENCES heroes(id),
    games INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    last_played TIMESTAMP,
    pick_rate REAL,
    ban_rate REAL
);

-- User profiles table
CREATE TABLE user_profiles (
    id SERIAL PRIMARY KEY,
    account_id BIGINT UNIQUE REFERENCES players(account_id),
    email TEXT UNIQUE,
    preferences TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- Hero synergies table
CREATE TABLE hero_synergies (
    hero1_id INTEGER REFERENCES heroes(id),
    hero2_id INTEGER REFERENCES heroes(id),
    games INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    synergy_score REAL,
    PRIMARY KEY (hero1_id, hero2_id)
);

-- Hero counters table
CREATE TABLE hero_counters (
    hero_id INTEGER REFERENCES heroes(id),
    counter_id INTEGER REFERENCES heroes(id),
    games INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    counter_score REAL,
    PRIMARY KEY (hero_id, counter_id)
);

-- Meta statistics table
CREATE TABLE meta_statistics (
    patch_id INTEGER REFERENCES patches(id),
    hero_id INTEGER REFERENCES heroes(id),
    pick_count INTEGER DEFAULT 0,
    ban_count INTEGER DEFAULT 0,
    win_count INTEGER DEFAULT 0,
    pick_rate REAL,
    ban_rate REAL,
    win_rate REAL,
    contest_rate REAL,
    league_id INTEGER,
    PRIMARY KEY (patch_id, hero_id, league_id)
);

-- Create indexes
-- Heroes and abilities lookup indexes
CREATE INDEX idx_heroes_name ON heroes(name);
CREATE INDEX idx_abilities_hero_id ON abilities(hero_id);
CREATE INDEX idx_ability_attributes_ability_id ON ability_attributes(ability_id);

-- Match analysis indexes
CREATE INDEX idx_matches_patch_id ON matches(patch_id);
CREATE INDEX idx_match_players_match_id ON match_players(match_id);
CREATE INDEX idx_match_players_account_id ON match_players(account_id);
CREATE INDEX idx_match_players_hero_id ON match_players(hero_id);
CREATE INDEX idx_draft_actions_match_id ON draft_actions(match_id);
CREATE INDEX idx_draft_actions_hero_id ON draft_actions(hero_id);

-- Player statistics indexes
CREATE INDEX idx_player_heroes_account_id ON player_heroes(account_id);
CREATE INDEX idx_player_heroes_hero_id ON player_heroes(hero_id);
CREATE INDEX idx_team_players_team_id ON team_players(team_id);
CREATE INDEX idx_team_heroes_team_id ON team_heroes(team_id);
CREATE INDEX idx_team_heroes_hero_id ON team_heroes(hero_id);

-- Meta statistics indexes
CREATE INDEX idx_meta_statistics_patch_id ON meta_statistics(patch_id);
CREATE INDEX idx_meta_statistics_hero_id ON meta_statistics(hero_id);
CREATE INDEX idx_meta_statistics_league_id ON meta_statistics(league_id);