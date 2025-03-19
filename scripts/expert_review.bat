@echo off
echo =================================================
echo Dota 2 Draft Assistant - Expert Review Generator
echo =================================================
echo.

REM Variables
set APP_DIR=%~dp0..
set SCENARIOS_DIR=%APP_DIR%\data\scenarios
set REVIEW_DIR=%APP_DIR%\data\expert_reviews

REM Create directories if they don't exist
if not exist "%SCENARIOS_DIR%" mkdir "%SCENARIOS_DIR%"
if not exist "%REVIEW_DIR%" mkdir "%REVIEW_DIR%"
if not exist "%APP_DIR%\logs" mkdir "%APP_DIR%\logs"

echo [INFO] Starting expert review generation

REM Change to application directory for Maven execution
cd /d "%APP_DIR%"
echo [INFO] Working directory: %CD%

REM Set a dummy API key for testing (would be set by environment in production)
set GROQ_API_KEY=dummy_key_for_demo_purpose_only

REM Run the very simple expert review generator
echo [INFO] Generating expert review templates...
java -cp target/classes com.dota2assistant.core.ai.VerySimpleExpertReview

if errorlevel 1 (
    echo [ERROR] Expert review generation failed
    exit /b 1
) else (
    echo.
    echo [SUCCESS] Expert review templates generated successfully
    echo.
    echo Review templates are located in:
    echo %REVIEW_DIR%
    echo.
    echo Please open these files in a text editor to provide feedback
    echo on the model's recommendation quality and reasoning.
    exit /b 0
)