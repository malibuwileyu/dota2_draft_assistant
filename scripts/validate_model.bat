@echo off
echo =================================================
echo Dota 2 Draft Assistant - Model Validation Script
echo =================================================
echo.

REM Variables
set APP_DIR=%~dp0..
set SCENARIOS_DIR=%APP_DIR%\data\scenarios
set RESULTS_DIR=%APP_DIR%\data\validation_results

REM Create directories if they don't exist
if not exist "%SCENARIOS_DIR%" mkdir "%SCENARIOS_DIR%"
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"
if not exist "%APP_DIR%\logs" mkdir "%APP_DIR%\logs"

echo [INFO] Starting model validation

REM Create basic scenarios if none exist
if not exist "%SCENARIOS_DIR%\stun_combo_scenario.json" (
    echo [INFO] Creating sample scenarios...
    
    REM Create scenario directory
    mkdir "%SCENARIOS_DIR%" 2>nul
    
    echo [INFO] Creating stun combo scenario...
    (
        echo {
        echo     "allyHeroIds": [22, 17, 19, 53],
        echo     "enemyHeroIds": [8, 11, 25, 32, 35],
        echo     "bannedHeroIds": [1, 6, 33, 41, 86, 90, 114],
        echo     "expectedPickId": 26
        echo }
    ) > "%SCENARIOS_DIR%\stun_combo_scenario.json"
    
    echo [INFO] Creating magic damage scenario...
    (
        echo {
        echo     "allyHeroIds": [5, 31, 26, 75],
        echo     "enemyHeroIds": [1, 10, 49, 62, 81],
        echo     "bannedHeroIds": [8, 74, 86, 87, 92, 114],
        echo     "expectedPickId": 64
        echo }
    ) > "%SCENARIOS_DIR%\magic_damage_scenario.json"
    
    echo [INFO] Creating physical damage scenario...
    (
        echo {
        echo     "allyHeroIds": [6, 35, 44, 54],
        echo     "enemyHeroIds": [36, 43, 67, 74, 79],
        echo     "bannedHeroIds": [1, 8, 14, 38, 41, 82, 84],
        echo     "expectedPickId": 28
        echo }
    ) > "%SCENARIOS_DIR%\physical_damage_scenario.json"
)

REM Count scenarios
set scenario_count=0
for %%f in ("%SCENARIOS_DIR%\*.json") do set /a scenario_count+=1
echo [INFO] Found %scenario_count% test scenarios

REM Change to application directory for Maven execution
cd /d "%APP_DIR%"
echo [INFO] Working directory: %CD%

REM Track results
set passed=0
set failed=0
set total=0

REM Process each scenario
echo.
echo [INFO] Running validation on each scenario...
echo -------------------------------------------

for %%f in ("%SCENARIOS_DIR%\*.json") do (
    set /a total+=1
    echo [TEST] Validating scenario: %%~nf
    
    REM Set a dummy API key for testing (would be set by environment in production)
    set GROQ_API_KEY=dummy_key_for_demo_purpose_only
    
    REM Run the very simple validator
    java -cp target/classes com.dota2assistant.core.ai.VerySimpleValidator --scenario=%%f
    
    if errorlevel 1 (
        echo [FAIL] Scenario %%~nf failed validation
        set /a failed+=1
    ) else (
        echo [PASS] Scenario %%~nf passed validation
        set /a passed+=1
    )
    echo.
)

REM Calculate success rate
set success_rate=0
if %total% GTR 0 (
    set /a success_rate=passed*100/total
)

REM Print results
echo -------------------------------------------
echo Validation Results:
echo - Scenarios tested: %total%
echo - Passed: %passed%
echo - Failed: %failed%
echo - Success rate: %success_rate%%%
echo -------------------------------------------

REM Check if validation passed the threshold
set PASS_THRESHOLD=80
if %success_rate% GEQ %PASS_THRESHOLD% (
    echo VALIDATION PASSED: Success rate above threshold (%PASS_THRESHOLD%%%)
    exit /b 0
) else (
    echo VALIDATION FAILED: Success rate below threshold (%PASS_THRESHOLD%%%)
    exit /b 1
)