#!/bin/bash
# Script to validate the ML model with known good scenarios

# Check if running on Windows
WINDOWS=0
if [[ "$(uname -s)" == *"MINGW"* ]] || [[ "$(uname -s)" == *"MSYS"* ]] || [[ -n "$WINDIR" ]]; then
    WINDOWS=1
    echo "Windows environment detected, adjusting paths and commands"
fi

# Fix paths for Windows if needed
fix_path() {
    if [ $WINDOWS -eq 1 ]; then
        # Convert /mnt/c/... style paths to C:/... for Windows compatibility
        echo "$1" | sed 's|^/mnt/\([a-z]\)/|\U\1:/|'
    else
        echo "$1"
    fi
}

# Variables
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCENARIOS_DIR="$APP_DIR/data/scenarios"
RESULTS_DIR="$APP_DIR/data/validation_results"
LOG_FILE="$APP_DIR/logs/model_validation.log"

# Create directories if they don't exist
mkdir -p "$SCENARIOS_DIR"
mkdir -p "$RESULTS_DIR"
mkdir -p "$(dirname "$LOG_FILE")"

echo "=============================" > "$LOG_FILE"
echo "Model Validation Run $(date)" >> "$LOG_FILE"
echo "=============================" >> "$LOG_FILE"

# Log function
log() {
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1"
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1" >> "$LOG_FILE"
}

# Run the model against a set of known good scenarios
log "Starting model validation with known-good scenarios"

# Create basic scenarios if none exist
if [ ! "$(ls -A "$SCENARIOS_DIR")" ]; then
    log "No scenarios found, creating basic scenarios..."
    
    # Create directory for scenarios
    mkdir -p "$SCENARIOS_DIR"
    
    # Create basic scenarios
    echo '{
        "allyHeroIds": [22, 17, 19, 53],
        "enemyHeroIds": [8, 11, 25, 32, 35],
        "bannedHeroIds": [1, 6, 33, 41, 86, 90, 114],
        "expectedPickId": 26
    }' > "$SCENARIOS_DIR/stun_combo_scenario.json"
    
    echo '{
        "allyHeroIds": [5, 31, 26, 75],
        "enemyHeroIds": [1, 10, 49, 62, 81],
        "bannedHeroIds": [8, 74, 86, 87, 92, 114],
        "expectedPickId": 64
    }' > "$SCENARIOS_DIR/magic_damage_scenario.json"
    
    echo '{
        "allyHeroIds": [6, 35, 44, 54],
        "enemyHeroIds": [36, 43, 67, 74, 79],
        "bannedHeroIds": [1, 8, 14, 38, 41, 82, 84],
        "expectedPickId": 28
    }' > "$SCENARIOS_DIR/physical_damage_scenario.json"
    
    log "Created basic scenarios in $SCENARIOS_DIR"
fi

# Count all scenarios
scenario_count=$(find "$SCENARIOS_DIR" -name "*.json" | wc -l)
log "Found $scenario_count scenarios for validation"

# Track results
passed=0
failed=0
total=0

# Process each scenario
for scenario_file in "$SCENARIOS_DIR"/*.json; do
    if [ -f "$scenario_file" ]; then
        scenario_name=$(basename "$scenario_file" .json)
        log "Testing scenario: $scenario_name"
        
        # Extract expected pick from scenario
        expected_pick=$(grep -o '"expectedPickId": [0-9]*' "$scenario_file" | awk '{print $2}')
        
        # Run model evaluation on this scenario
        if [ $WINDOWS -eq 1 ]; then
            # Use direct Java execution instead of the script on Windows
            log "Running Windows validation mode"
            cd "$APP_DIR"
            result=$(mvn exec:java -Dexec.mainClass="com.dota2assistant.core.ai.ModelValidation" -Dexec.args="--scenario=$(fix_path "$scenario_file")")
            status=$?
        else
            # Use the script on Unix-like systems
            result=$(./scripts/run_model_evaluation.sh --scenario="$scenario_file")
            status=$?
        fi
        
        # Check if the run was successful
        if [ $status -eq 0 ]; then
            # Extract top recommendation from results
            result_file=$(find "$RESULTS_DIR" -name "scenario_${scenario_name}_*.json" -type f -printf "%T@ %p\n" | sort -n | tail -1 | cut -d' ' -f2-)
            
            if [ -f "$result_file" ]; then
                top_pick=$(grep -o '"ml_top_pick": [0-9]*' "$result_file" | awk '{print $2}')
                
                # Compare expected vs actual
                if [ "$top_pick" = "$expected_pick" ]; then
                    log "✓ PASS: $scenario_name - Model correctly recommended hero $expected_pick"
                    passed=$((passed+1))
                else
                    log "✗ FAIL: $scenario_name - Model recommended hero $top_pick, expected $expected_pick"
                    failed=$((failed+1))
                fi
            else
                log "✗ FAIL: $scenario_name - Could not find results file"
                failed=$((failed+1))
            fi
        else
            log "✗ FAIL: $scenario_name - Evaluation failed with status $status"
            failed=$((failed+1))
        fi
        
        total=$((total+1))
    fi
done

# Calculate success rate
if [ $total -gt 0 ]; then
    success_rate=$(echo "scale=2; $passed * 100 / $total" | bc)
else
    success_rate=0
fi

# Print results
log "---------------------------------"
log "Validation Complete"
log "Passed: $passed/$total ($success_rate%)"
log "Failed: $failed/$total"
log "---------------------------------"

# Check if validation passed the threshold
PASS_THRESHOLD=80  # 80% success rate required to pass
if (( $(echo "$success_rate >= $PASS_THRESHOLD" | bc -l) )); then
    log "VALIDATION PASSED: Success rate above threshold ($PASS_THRESHOLD%)"
    echo "VALIDATION PASSED: Success rate $success_rate% (threshold: $PASS_THRESHOLD%)"
    exit 0
else
    log "VALIDATION FAILED: Success rate below threshold ($PASS_THRESHOLD%)"
    echo "VALIDATION FAILED: Success rate $success_rate% (threshold: $PASS_THRESHOLD%)"
    exit 1
fi