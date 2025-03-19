#!/bin/bash
# Script to run the ML model evaluation framework

# Variables
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EVAL_DIR="$APP_DIR/data/evaluation"
BENCHMARK_DIR="$APP_DIR/data/benchmarks"
LOG_FILE="$APP_DIR/logs/model_evaluation.log"

# Create directories if they don't exist
mkdir -p "$EVAL_DIR"
mkdir -p "$BENCHMARK_DIR"
mkdir -p "$APP_DIR/logs"

# Log file header
echo "================================" >> "$LOG_FILE"
echo "Model Evaluation Run $(date)" >> "$LOG_FILE"
echo "================================" >> "$LOG_FILE"

# Function to log messages
log() {
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1"
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] $1" >> "$LOG_FILE"
}

# Check if the application is running
check_app_status() {
    # Try to access the evaluation API endpoint
    response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/ml/evaluation/run-evaluation || echo "failed")
    
    if [ "$response" = "failed" ]; then
        log "Application doesn't appear to be running. Make sure it's started before running this script."
        exit 1
    fi
}

# Run specific evaluations
run_evaluation() {
    log "Running comprehensive ML model evaluation..."
    response=$(curl -s -X POST http://localhost:8080/api/ml/evaluation/run-evaluation)
    
    if [ $? -eq 0 ]; then
        log "Evaluation completed successfully"
        # Save response to a file
        echo "$response" > "$EVAL_DIR/latest_evaluation_$(date +"%Y%m%d%H%M%S").json"
    else
        log "Error running evaluation: $response"
    fi
}

# Run performance benchmarks
run_benchmarks() {
    log "Running ML model performance benchmarks..."
    response=$(curl -s -X POST http://localhost:8080/api/ml/evaluation/run-benchmark)
    
    if [ $? -eq 0 ]; then
        log "Benchmark completed successfully"
        # Save response to a file
        echo "$response" > "$BENCHMARK_DIR/latest_benchmark_$(date +"%Y%m%d%H%M%S").json"
    else
        log "Error running benchmark: $response"
    fi
}

# Run parameter optimization
optimize_parameters() {
    log "Running parameter optimization..."
    response=$(curl -s -X POST http://localhost:8080/api/ml/evaluation/optimize-parameters)
    
    if [ $? -eq 0 ]; then
        log "Parameter optimization completed successfully"
        # Save response to a file
        echo "$response" > "$BENCHMARK_DIR/parameter_optimization_$(date +"%Y%m%d%H%M%S").json"
    else
        log "Error optimizing parameters: $response"
    fi
}

# Test a specific scenario
test_scenario() {
    if [ -z "$1" ]; then
        log "No scenario file specified"
        return
    fi
    
    scenario_file="$1"
    
    if [ ! -f "$scenario_file" ]; then
        log "Scenario file $scenario_file not found"
        return
    }
    
    log "Testing scenario from $scenario_file..."
    response=$(curl -s -X POST -H "Content-Type: application/json" -d "@$scenario_file" http://localhost:8080/api/ml/evaluation/evaluate-scenario)
    
    if [ $? -eq 0 ]; then
        log "Scenario evaluation completed successfully"
        # Extract scenario name from filename
        scenario_name=$(basename "$scenario_file" .json)
        echo "$response" > "$EVAL_DIR/scenario_${scenario_name}_$(date +"%Y%m%d%H%M%S").json"
    else
        log "Error evaluating scenario: $response"
    fi
}

# Main execution
log "Starting ML model evaluation framework"

check_app_status

# Process command line arguments
if [ $# -eq 0 ]; then
    # No arguments, run everything
    run_evaluation
    run_benchmarks
    optimize_parameters
else
    # Process specific commands
    for arg in "$@"; do
        case "$arg" in
            --eval|--evaluation)
                run_evaluation
                ;;
            --bench|--benchmark)
                run_benchmarks
                ;;
            --opt|--optimize)
                optimize_parameters
                ;;
            --scenario=*)
                scenario_file="${arg#*=}"
                test_scenario "$scenario_file"
                ;;
            --help)
                echo "Usage: $0 [options]"
                echo "Options:"
                echo "  --eval, --evaluation   Run comprehensive evaluation"
                echo "  --bench, --benchmark   Run performance benchmarks"
                echo "  --opt, --optimize      Run parameter optimization"
                echo "  --scenario=FILE        Test a specific scenario from JSON file"
                echo "  --help                 Show this help message"
                exit 0
                ;;
            *)
                log "Unknown option: $arg"
                echo "Use --help for usage information"
                ;;
        esac
    done
fi

log "ML model evaluation completed"