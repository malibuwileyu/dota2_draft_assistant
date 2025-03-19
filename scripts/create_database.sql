-- Create database for Dota 2 Draft Assistant
CREATE DATABASE dota2_draft_assistant;

-- Connect to the new database
\c dota2_draft_assistant

-- Create a specific user with password (change 'your_password' to a secure password)
CREATE USER dota2_user WITH PASSWORD 'your_password';

-- Grant privileges to the user
GRANT ALL PRIVILEGES ON DATABASE dota2_draft_assistant TO dota2_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO dota2_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO dota2_user;

-- Allow the user to create new tables, needed for schema migrations
ALTER ROLE dota2_user CREATEDB;

-- Display confirmation
\echo 'Database dota2_draft_assistant created successfully.'
\echo 'User dota2_user created with necessary permissions.'