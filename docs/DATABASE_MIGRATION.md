# Dota 2 Draft Assistant - PostgreSQL Migration

This document provides information about the PostgreSQL database migration for the Dota 2 Draft Assistant application.

## Overview

The application has been updated to use PostgreSQL instead of SQLite for better performance, reliability, and scalability. This migration includes:

1. Creating a PostgreSQL database schema
2. Implementing SQL scripts to import data from JSON files
3. Adding support for both SQLite and PostgreSQL in the application code
4. Providing a batch script to automate database setup

## Database Setup

### Prerequisites

- PostgreSQL 17+ installed on your system
- PostgreSQL command-line tools (psql) available in your PATH

### Setup Steps

1. Navigate to the `scripts` directory
2. Run the `setup_database.bat` script to:
   - Create the database and user
   - Create the database schema
   - Import hero data
   - Import ability data
   - Import match data

```bash
cd scripts
./setup_database.bat
```

This script will prompt you for the postgres user password. The database creation script will create a new user `dota2_user` with the password specified in the script.

## Configuration

To configure the application to use PostgreSQL:

1. Open `src/main/resources/application.properties`
2. Set the following properties:

```properties
# Enable PostgreSQL
database.type=postgresql
database.url=jdbc:postgresql://localhost:5432/dota2_draft_assistant
database.username=dota2_user
database.password=your_password
```

To use SQLite (legacy mode):

```properties
# Enable SQLite
database.type=sqlite
database.file=dota2assistant.db
```

## Database Schema

The PostgreSQL schema includes the following main tables:

- `heroes`: Contains basic hero information
- `abilities`: Contains hero abilities data
- `ability_attributes`: Contains detailed ability attributes
- `matches`: Contains match data from professional games
- `match_players`: Contains player data from matches
- `draft_actions`: Contains draft picks and bans
- `hero_synergies`: Contains computed hero synergy statistics
- `hero_counters`: Contains computed hero counter statistics

## Migration Scripts

The migration process uses the following SQL scripts:

1. `create_database.sql`: Creates the database and user
2. `001_initial_schema.sql`: Creates the database tables and indexes
3. `002_import_hero_data.sql`: Imports hero data from JSON files
4. `004_import_ability_data.sql`: Imports ability data from JSON files
5. `004_import_match_data.sql`: Imports match data and computes statistics

## Java Implementation

The Java code has been updated to support both database types:

- `DatabaseManager`: Interface for database operations
- `SqliteDatabaseManager`: Implementation for SQLite
- `PostgreSqlDatabaseManager`: Implementation for PostgreSQL

The appropriate implementation is selected based on the `database.type` property in the application.properties file.

## Additional Notes

- The PostgreSQL implementation does not handle schema migration internally. All schema changes must be applied using external SQL scripts.
- Make sure your PostgreSQL server allows connections from the application with the configured credentials.
- The batch script assumes a local PostgreSQL installation with default port (5432).