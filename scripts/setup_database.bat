@echo off
echo Setting up PostgreSQL database for Dota 2 Draft Assistant

REM Automatically detect PostgreSQL path - you may need to adjust this
for /d %%i in ("C:\Program Files\PostgreSQL\*") do (
    if exist "%%i\bin\psql.exe" set PGBIN=%%i\bin
)
set SCRIPTS_DIR=%~dp0

REM Create temp directory for JSON processing
if not exist "%SCRIPTS_DIR%temp" mkdir "%SCRIPTS_DIR%temp"

echo.
echo Step 1: Creating database and user...
echo (You will be prompted for the postgres user password)
"%PGBIN%\psql" -U postgres -f "%SCRIPTS_DIR%create_database.sql"

echo.
echo Step 2: Creating initial schema...
"%PGBIN%\psql" -U postgres -d dota2_draft_assistant -f "%SCRIPTS_DIR%001_initial_schema.sql"

echo.
echo Step 3: Importing hero data...
"%PGBIN%\psql" -U postgres -d dota2_draft_assistant -f "%SCRIPTS_DIR%002_import_hero_data.sql"

echo.
echo Step 4: Importing ability data...
"%PGBIN%\psql" -U postgres -d dota2_draft_assistant -f "%SCRIPTS_DIR%003_import_ability_data.sql"

echo.
echo Step 5: Importing match data...
"%PGBIN%\psql" -U postgres -d dota2_draft_assistant -f "%SCRIPTS_DIR%004_import_match_data.sql"

echo.
echo Database setup complete!
echo.
echo Next steps:
echo 1. Update the application.properties file with your database connection information
echo.

REM Clean up temp directory
if exist "%SCRIPTS_DIR%temp" rmdir /s /q "%SCRIPTS_DIR%temp"

pause