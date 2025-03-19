#!/bin/bash
# Script to run tests and evaluation for the Dota 2 Draft Assistant

# Variables
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$APP_DIR/logs"
LOG_FILE="$LOG_DIR/tests_and_evaluation.log"

# Create log directory if it doesn't exist
mkdir -p "$LOG_DIR"

# Log file header
echo "================================" > "$LOG_FILE"
echo "Tests and Evaluation Run $(date)" >> "$LOG_FILE"
echo "================================" >> "$LOG_FILE"

# Function to log messages
log() {
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1"
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1" >> "$LOG_FILE"
}

# Run unit tests
run_tests() {
    log "Running unit tests..."
    mvn test -B >> "$LOG_FILE" 2>&1
    
    if [ $? -eq 0 ]; then
        log "Unit tests completed successfully"
        return 0
    else
        log "Unit tests failed"
        return 1
    fi
}

# Build the application
build_app() {
    log "Building application..."
    mvn clean install -DskipTests -B >> "$LOG_FILE" 2>&1
    
    if [ $? -eq 0 ]; then
        log "Application built successfully"
        return 0
    else
        log "Application build failed"
        return 1
    fi
}

# Start application for evaluation
start_app() {
    log "Starting application for evaluation..."
    
    # Check if the application is already running
    if pgrep -f "dota2-draft-assistant" > /dev/null; then
        log "Application already running"
    else
        # Start application in background
        nohup mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080" > "$LOG_DIR/app.log" 2>&1 &
        APP_PID=$!
        
        # Wait for application to start (adjust timeout as needed)
        log "Waiting for application to start (PID: $APP_PID)..."
        for i in {1..30}; do
            if curl -s http://localhost:8080/api/health > /dev/null; then
                log "Application started successfully"
                return 0
            fi
            sleep 2
        done
        
        log "Timed out waiting for application to start"
        return 1
    fi
}

# Stop application after evaluation
stop_app() {
    log "Stopping application..."
    
    # Find PID of running application
    APP_PID=$(pgrep -f "dota2-draft-assistant")
    
    if [ -n "$APP_PID" ]; then
        kill "$APP_PID"
        log "Application stopped (PID: $APP_PID)"
    else
        log "Application not running"
    fi
}

# Run model evaluation
run_evaluation() {
    log "Running model evaluation..."
    
    # Run the evaluation script
    "$APP_DIR/scripts/run_model_evaluation.sh" --eval --bench >> "$LOG_FILE" 2>&1
    
    if [ $? -eq 0 ]; then
        log "Model evaluation completed successfully"
        return 0
    else
        log "Model evaluation failed"
        return 1
    fi
}

# Main execution flow
log "Starting tests and evaluation"

# Run unit tests first
run_tests
if [ $? -ne 0 ]; then
    log "Aborting due to test failures"
    exit 1
fi

# Build application
build_app
if [ $? -ne 0 ]; then
    log "Aborting due to build failure"
    exit 1
fi

# If tests and build pass, proceed with evaluation
start_app
if [ $? -eq 0 ]; then
    # Run evaluation
    run_evaluation
    
    # After evaluation is done, stop the application
    stop_app
else
    log "Skipping evaluation due to application startup failure"
    exit 1
fi

log "Tests and evaluation completed"