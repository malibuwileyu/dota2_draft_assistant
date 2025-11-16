# Dota 2 Draft Assistant - Database Schema

This document outlines the database schema design for the Dota 2 Draft Assistant application. The schema is designed to support efficient storage and retrieval of hero data, abilities, match information, player data, and draft recommendations.

## Schema Overview

![Database Schema Overview](schema_overview.png)

## Tables

### heroes

Stores basic information about all Dota 2 heroes.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY | Hero ID from Dota 2 API |
| name | TEXT | NOT NULL | Internal hero name (e.g., "anti_mage") |
| localized_name | TEXT | NOT NULL | Display name (e.g., "Anti-Mage") |
| primary_attr | TEXT | NOT NULL | Primary attribute (str, agi, int) |
| attack_type | TEXT | NOT NULL | Attack type (Melee, Ranged) |
| roles | TEXT | | JSON array of roles |
| base_health | REAL | | Base health value |
| base_mana | REAL | | Base mana value |
| base_armor | REAL | | Base armor value |
| base_mr | REAL | | Base magic resistance |
| base_attack_min | INTEGER | | Minimum base attack damage |
| base_attack_max | INTEGER | | Maximum base attack damage |
| base_str | INTEGER | | Base strength |
| base_agi | INTEGER | | Base agility |
| base_int | INTEGER | | Base intelligence |
| str_gain | REAL | | Strength gain per level |
| agi_gain | REAL | | Agility gain per level |
| int_gain | REAL | | Intelligence gain per level |
| attack_range | INTEGER | | Attack range |
| move_speed | INTEGER | | Movement speed |
| image_path | TEXT | | Path to hero image |
| icon_path | TEXT | | Path to hero icon |
| added_date | DATETIME | DEFAULT CURRENT_TIMESTAMP | Date hero was added to database |
| updated_date | DATETIME | | Date hero was last updated |

### abilities

Stores hero ability information.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique ability ID |
| hero_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Associated hero ID |
| name | TEXT | NOT NULL | Ability name (e.g., "mana_break") |
| localized_name | TEXT | NOT NULL | Display name (e.g., "Mana Break") |
| description | TEXT | | Ability description |
| ability_type | TEXT | | Type (Active, Passive, Ultimate) |
| behavior | TEXT | | Behavior type (Point Target, No Target, etc.) |
| affects | TEXT | | Affected unit types (Heroes, Creeps, etc.) |
| damage_type | TEXT | | Damage type (Magical, Physical, Pure) |
| is_ultimate | BOOLEAN | DEFAULT 0 | Whether the ability is an ultimate |
| cooldown | TEXT | | JSON array of cooldown values at different levels |
| mana_cost | TEXT | | JSON array of mana costs at different levels |
| image_path | TEXT | | Path to ability image |
| added_date | DATETIME | DEFAULT CURRENT_TIMESTAMP | Date ability was added |
| updated_date | DATETIME | | Date ability was last updated |

### ability_attributes

Stores detailed ability attributes and values.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique attribute ID |
| ability_id | INTEGER | FOREIGN KEY REFERENCES abilities(id) | Associated ability ID |
| name | TEXT | NOT NULL | Attribute name (e.g., "damage") |
| value | TEXT | NOT NULL | Attribute value(s) as JSON |
| is_core | BOOLEAN | DEFAULT 0 | Whether this is a core attribute |

### patches

Stores information about game patches.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique patch ID |
| name | TEXT | NOT NULL UNIQUE | Patch version (e.g., "7.38") |
| release_date | DATETIME | NOT NULL | Release date of the patch |
| end_date | DATETIME | | End date of the patch (when next patch began) |
| is_active | BOOLEAN | DEFAULT 0 | Whether this is the current active patch |
| weight | REAL | DEFAULT 1.0 | Weight for data from this patch (1.0 = current, 0.5 = previous, etc.) |

### matches

Stores basic match information.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | BIGINT | PRIMARY KEY | Match ID from Dota 2 API |
| patch_id | INTEGER | FOREIGN KEY REFERENCES patches(id) | Associated patch ID |
| start_time | DATETIME | NOT NULL | When the match started |
| duration | INTEGER | NOT NULL | Match duration in seconds |
| radiant_win | BOOLEAN | NOT NULL | Whether Radiant won |
| game_mode | INTEGER | NOT NULL | Game mode ID |
| league_id | INTEGER | | League ID if a professional match |
| league_name | TEXT | | Name of league if available |
| match_data | TEXT | | Full match JSON data (compressed/optional) |
| added_date | DATETIME | DEFAULT CURRENT_TIMESTAMP | Date match was added |

### match_players

Stores player information for each match.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique entry ID |
| match_id | BIGINT | FOREIGN KEY REFERENCES matches(id) | Associated match ID |
| account_id | BIGINT | | Player's account ID |
| hero_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Hero played |
| player_slot | INTEGER | | Player slot number (0-4 Radiant, 128-132 Dire) |
| is_radiant | BOOLEAN | NOT NULL | Whether player was on Radiant |
| kills | INTEGER | | Kill count |
| deaths | INTEGER | | Death count |
| assists | INTEGER | | Assist count |
| gold_per_min | REAL | | Gold per minute |
| xp_per_min | REAL | | Experience per minute |
| hero_damage | INTEGER | | Hero damage dealt |
| tower_damage | INTEGER | | Tower damage dealt |
| hero_healing | INTEGER | | Hero healing done |
| last_hits | INTEGER | | Last hit count |
| lane | INTEGER | | Lane position (1=Safe, 2=Mid, 3=Off, 4/5=Support) |
| role | TEXT | | Detected role |

### draft_actions

Stores the pick/ban sequence for matches.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique draft action ID |
| match_id | BIGINT | FOREIGN KEY REFERENCES matches(id) | Associated match ID |
| hero_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Hero picked/banned |
| is_pick | BOOLEAN | NOT NULL | Whether action was a pick (true) or ban (false) |
| team | TEXT | NOT NULL | Team taking action ("radiant" or "dire") |
| order | INTEGER | NOT NULL | Order of action in the draft (0-indexed) |

### players

Stores information about players.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| account_id | BIGINT | PRIMARY KEY | Steam account ID |
| steam_id | TEXT | UNIQUE | Steam ID |
| username | TEXT | | Steam username |
| personaname | TEXT | | Steam persona name |
| avatar | TEXT | | URL to player avatar |
| last_match_time | DATETIME | | Last time player played a match |
| created_date | DATETIME | DEFAULT CURRENT_TIMESTAMP | Date player was first added |
| updated_date | DATETIME | | Date player was last updated |

### player_heroes

Stores player performance with specific heroes.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique entry ID |
| account_id | BIGINT | FOREIGN KEY REFERENCES players(account_id) | Player account ID |
| hero_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Hero ID |
| games | INTEGER | DEFAULT 0 | Games played with this hero |
| wins | INTEGER | DEFAULT 0 | Games won with this hero |
| last_played | DATETIME | | Last time played this hero |
| avg_kills | REAL | | Average kills |
| avg_deaths | REAL | | Average deaths |
| avg_assists | REAL | | Average assists |
| comfort_score | REAL | | Computed comfort score (0-1) |

### teams

Stores team information.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY | Team ID (from Dota 2 API when available) |
| name | TEXT | NOT NULL | Team name |
| tag | TEXT | | Team tag |
| logo_url | TEXT | | URL to team logo |
| created_date | DATETIME | DEFAULT CURRENT_TIMESTAMP | Date team was added |

### team_players

Stores team roster information.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique entry ID |
| team_id | INTEGER | FOREIGN KEY REFERENCES teams(id) | Team ID |
| account_id | BIGINT | FOREIGN KEY REFERENCES players(account_id) | Player account ID |
| join_date | DATETIME | | When player joined team |
| is_active | BOOLEAN | DEFAULT 1 | Whether player is currently on team |

### team_heroes

Stores aggregated team performance with heroes.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique entry ID |
| team_id | INTEGER | FOREIGN KEY REFERENCES teams(id) | Team ID |
| hero_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Hero ID |
| games | INTEGER | DEFAULT 0 | Games played with this hero |
| wins | INTEGER | DEFAULT 0 | Games won with this hero |
| last_played | DATETIME | | Last time team played this hero |
| pick_rate | REAL | | How often the team picks this hero |
| ban_rate | REAL | | How often the team bans this hero |

### user_profiles

Stores application user information.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique user ID |
| account_id | BIGINT | UNIQUE FOREIGN KEY REFERENCES players(account_id) | Associated player account |
| email | TEXT | UNIQUE | User email (optional) |
| preferences | TEXT | | User preferences as JSON |
| created_date | DATETIME | DEFAULT CURRENT_TIMESTAMP | Account creation date |
| last_login | DATETIME | | Last login date |

### hero_synergies

Stores hero synergy statistics.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| hero1_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | First hero ID |
| hero2_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Second hero ID |
| games | INTEGER | DEFAULT 0 | Games played together |
| wins | INTEGER | DEFAULT 0 | Games won together |
| synergy_score | REAL | | Calculated synergy score |
| PRIMARY KEY | | (hero1_id, hero2_id) | Composite primary key |

### hero_counters

Stores hero counter statistics.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| hero_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Hero ID |
| counter_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Counter hero ID |
| games | INTEGER | DEFAULT 0 | Games played against each other |
| wins | INTEGER | DEFAULT 0 | Games where counter hero won |
| counter_score | REAL | | Calculated counter effectiveness |
| PRIMARY KEY | | (hero_id, counter_id) | Composite primary key |

### meta_statistics

Stores current meta statistics.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| patch_id | INTEGER | FOREIGN KEY REFERENCES patches(id) | Associated patch |
| hero_id | INTEGER | FOREIGN KEY REFERENCES heroes(id) | Hero ID |
| pick_count | INTEGER | DEFAULT 0 | Number of picks |
| ban_count | INTEGER | DEFAULT 0 | Number of bans |
| win_count | INTEGER | DEFAULT 0 | Number of wins when picked |
| pick_rate | REAL | | Pick rate percentage |
| ban_rate | REAL | | Ban rate percentage |
| win_rate | REAL | | Win rate percentage |
| contest_rate | REAL | | Combined pick and ban rate |
| league_id | INTEGER | NULL | League ID if league-specific, NULL for all |
| PRIMARY KEY | | (patch_id, hero_id, league_id) | Composite primary key |

## Database Migrations

Initial database creation and migrations will be handled through SQL scripts:

1. `001_initial_schema.sql` - Creates the initial tables
2. `002_import_hero_data.sql` - Imports hero data from JSON
3. `003_import_ability_data.sql` - Imports ability data from JSON 
4. `004_import_match_data.sql` - Imports match data from JSON

## Indexing Strategy

The following indexes will be created to optimize common queries:

```sql
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
```

## Implementation Plan

1. Set up SQLite database with the schema above
2. Create data migration utilities to import existing JSON data
3. Update repository classes to use database instead of JSON files
4. Add database versioning and upgrade mechanism
5. Implement caching layer for frequently accessed data
6. Add backup/restore functionality for the database

## Database Connection Configuration

Configuration will be stored in `application.properties`:

```properties
db.type=sqlite
db.url=jdbc:sqlite:dota2assistant.db
db.username=
db.password=
db.pool.initialSize=5
db.pool.maxSize=20
```