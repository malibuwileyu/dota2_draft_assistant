#!/bin/bash
# Script to generate expert-readable model explanations for human review

# Variables
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCENARIOS_DIR="$APP_DIR/data/scenarios"
REVIEW_DIR="$APP_DIR/data/expert_reviews"
LOG_FILE="$APP_DIR/logs/expert_review.log"

# Create directories if they don't exist
mkdir -p "$SCENARIOS_DIR"
mkdir -p "$REVIEW_DIR"
mkdir -p "$(dirname "$LOG_FILE")"

echo "=============================" > "$LOG_FILE"
echo "Expert Review Run $(date)" >> "$LOG_FILE"
echo "=============================" >> "$LOG_FILE"

# Log function
log() {
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1"
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1" >> "$LOG_FILE"
}

# Run the app if it's not already running
start_app() {
    # Check if the application is already running
    if curl -s http://localhost:8080/api/health > /dev/null; then
        log "Application already running"
        return 0
    else
        log "Starting application..."
        nohup mvn spring-boot:run > "$APP_DIR/logs/app.log" 2>&1 &
        APP_PID=$!
        
        # Wait for application to start
        for i in {1..30}; do
            if curl -s http://localhost:8080/api/health > /dev/null; then
                log "Application started successfully"
                return 0
            fi
            sleep 2
        done
        
        log "Failed to start application"
        return 1
    fi
}

# Function to get hero name from ID
get_hero_name() {
    local hero_id=$1
    local hero_name=$(curl -s "http://localhost:8080/api/heroes/$hero_id" | grep -o '"localizedName":"[^"]*"' | sed 's/"localizedName":"//;s/"//')
    echo "$hero_name"
}

# Function to create human-readable scenario description
describe_scenario() {
    local scenario_file=$1
    local output_file=$2
    
    # Extract hero IDs from scenario
    ally_ids=$(grep -o '"allyHeroIds": \[[^]]*\]' "$scenario_file" | sed 's/"allyHeroIds": \[//;s/\]//')
    enemy_ids=$(grep -o '"enemyHeroIds": \[[^]]*\]' "$scenario_file" | sed 's/"enemyHeroIds": \[//;s/\]//')
    banned_ids=$(grep -o '"bannedHeroIds": \[[^]]*\]' "$scenario_file" | sed 's/"bannedHeroIds": \[//;s/\]//')
    expected_id=$(grep -o '"expectedPickId": [0-9]*' "$scenario_file" | awk '{print $2}')
    
    # Start the scenario description
    echo "# Expert Review: $(basename "$scenario_file" .json)" > "$output_file"
    echo "Date: $(date)" >> "$output_file"
    echo "" >> "$output_file"
    echo "## Scenario" >> "$output_file"
    
    # Add Allied Heroes
    echo "### Allied Heroes:" >> "$output_file"
    for id in $(echo $ally_ids | tr ',' ' '); do
        hero_name=$(get_hero_name $id)
        echo "- $hero_name (ID: $id)" >> "$output_file"
    done
    echo "" >> "$output_file"
    
    # Add Enemy Heroes
    echo "### Enemy Heroes:" >> "$output_file"
    for id in $(echo $enemy_ids | tr ',' ' '); do
        hero_name=$(get_hero_name $id)
        echo "- $hero_name (ID: $id)" >> "$output_file"
    done
    echo "" >> "$output_file"
    
    # Add Banned Heroes
    echo "### Banned Heroes:" >> "$output_file"
    for id in $(echo $banned_ids | tr ',' ' '); do
        hero_name=$(get_hero_name $id)
        echo "- $hero_name (ID: $id)" >> "$output_file"
    done
    echo "" >> "$output_file"
    
    # Add Expected Pick
    expected_hero=$(get_hero_name $expected_id)
    echo "### Expected Pick:" >> "$output_file"
    echo "- $expected_hero (ID: $expected_id)" >> "$output_file"
    echo "" >> "$output_file"
}

# Function to get model recommendations and explanations
get_model_output() {
    local scenario_file=$1
    local output_file=$2
    
    # Run model evaluation on this scenario
    log "Getting model recommendations for $(basename "$scenario_file" .json)"
    
    # Make API call to get recommendations
    response=$(curl -s -X POST -H "Content-Type: application/json" -d @"$scenario_file" "http://localhost:8080/api/ml/evaluation/evaluate-scenario")
    
    # Extract recommendations
    echo "## Model Recommendations" >> "$output_file"
    
    # Top picks
    echo "### Top Recommendations:" >> "$output_file"
    top_picks=$(echo "$response" | grep -o '"ml_recommendations":\[[^]]*\]' | sed 's/"ml_recommendations":\[//;s/\]//')
    
    # If we can extract recommendations
    if [ -n "$top_picks" ]; then
        IFS=',' read -ra pick_array <<< "$top_picks"
        rank=1
        for pick in "${pick_array[@]}"; do
            hero_name=$(get_hero_name "$pick")
            echo "$rank. $hero_name (ID: $pick)" >> "$output_file"
            rank=$((rank+1))
        done
    else
        echo "Could not extract recommendations" >> "$output_file"
    fi
    echo "" >> "$output_file"
    
    # Get explanations
    echo "### Explanations:" >> "$output_file"
    
    # Get reasoning for the top recommendation
    top_pick=$(echo "$top_picks" | cut -d',' -f1)
    
    if [ -n "$top_pick" ]; then
        explanation=$(curl -s "http://localhost:8080/api/heroes/$top_pick/recommendation-reasoning")
        
        # Print specific factors in the explanation
        echo "#### Statistical Factors:" >> "$output_file"
        stats=$(echo "$explanation" | grep -o '"Statistics":"[^"]*"' | sed 's/"Statistics":"//;s/"//')
        echo "$stats" >> "$output_file"
        echo "" >> "$output_file"
        
        echo "#### Ability Analysis:" >> "$output_file"
        abilities=$(echo "$explanation" | grep -o '"Abilities":"[^"]*"' | sed 's/"Abilities":"//;s/"//')
        echo "$abilities" >> "$output_file"
        echo "" >> "$output_file"
        
        echo "#### Team Synergy:" >> "$output_file"
        synergy=$(echo "$explanation" | grep -o '"Team Synergy":"[^"]*"' | sed 's/"Team Synergy":"//;s/"//')
        echo "$synergy" >> "$output_file"
        echo "" >> "$output_file"
        
        echo "#### Enemy Counters:" >> "$output_file"
        counters=$(echo "$explanation" | grep -o '"Enemy Counters":"[^"]*"' | sed 's/"Enemy Counters":"//;s/"//')
        echo "$counters" >> "$output_file"
        echo "" >> "$output_file"
    else
        echo "Could not get explanation for top recommendation" >> "$output_file"
    fi
}

# Function to add expert review template
add_review_template() {
    local output_file=$1
    
    echo "## Expert Review" >> "$output_file"
    echo "" >> "$output_file"
    echo "### Recommendation Quality (1-5):" >> "$output_file"
    echo "___ " >> "$output_file"
    echo "" >> "$output_file"
    echo "### Reasoning Quality (1-5):" >> "$output_file"
    echo "___ " >> "$output_file"
    echo "" >> "$output_file"
    echo "### Comments on Recommendations:" >> "$output_file"
    echo "```" >> "$output_file"
    echo "" >> "$output_file"
    echo "```" >> "$output_file"
    echo "" >> "$output_file"
    echo "### Comments on Reasoning:" >> "$output_file"
    echo "```" >> "$output_file"
    echo "" >> "$output_file"
    echo "```" >> "$output_file"
    echo "" >> "$output_file"
    echo "### Suggested Improvements:" >> "$output_file"
    echo "```" >> "$output_file"
    echo "" >> "$output_file"
    echo "```" >> "$output_file"
    echo "" >> "$output_file"
    echo "### Additional Notes:" >> "$output_file"
    echo "```" >> "$output_file"
    echo "" >> "$output_file"
    echo "```" >> "$output_file"
}

# Main function to process scenarios
main() {
    log "Starting expert review generation"
    
    # Start the application
    start_app
    if [ $? -ne 0 ]; then
        log "Cannot proceed without application"
        exit 1
    fi
    
    # Process each scenario
    for scenario_file in "$SCENARIOS_DIR"/*.json; do
        if [ -f "$scenario_file" ]; then
            scenario_name=$(basename "$scenario_file" .json)
            review_file="$REVIEW_DIR/${scenario_name}_review_$(date +"%Y%m%d").md"
            
            log "Processing scenario: $scenario_name"
            
            # Create the review file
            describe_scenario "$scenario_file" "$review_file"
            get_model_output "$scenario_file" "$review_file"
            add_review_template "$review_file"
            
            log "Created review file: $review_file"
        fi
    done
    
    log "Expert review generation complete"
    echo "Expert review files have been generated in: $REVIEW_DIR"
    echo "Please review these files and provide feedback on model recommendations."
}

# Execute main function
main