#!/usr/bin/env python3
import argparse
import os
import subprocess
import time
import sys

def run_command(command):
    """
    Run a shell command and return its output.
    
    Args:
        command (str): Command to run
        
    Returns:
        tuple: (success, output)
    """
    try:
        result = subprocess.run(
            command,
            shell=True,
            check=True,
            text=True,
            capture_output=True
        )
        return True, result.stdout
    except subprocess.CalledProcessError as e:
        return False, f"Error: {e.stderr}"

def ensure_scripts_executable():
    """Make sure the Python scripts are executable"""
    scripts = ["fetch_pro_matches.py", "fetch_match_details.py"]
    for script in scripts:
        if os.path.exists(script):
            os.chmod(script, 0o755)  # rwxr-xr-x

def main():
    parser = argparse.ArgumentParser(description="Collect Dota 2 match data from OpenDota API")
    parser.add_argument("--match-limit", type=int, default=100,
                        help="Number of pro matches to retrieve (default: 100)")
    parser.add_argument("--data-dir", type=str, 
                       help="Directory to save data to (uses project structure by default)")
    parser.add_argument("--delay", type=int, default=1,
                       help="Delay between API calls in seconds (default: 1)")
    parser.add_argument("--phase", choices=["pro_matches", "match_details", "all"],
                       default="all", help="Phase to run (default: all)")
    
    args = parser.parse_args()
    
    # Ensure scripts are executable
    ensure_scripts_executable()
    
    # Create output directory if it doesn't exist
    os.makedirs(args.data_dir, exist_ok=True)
    
    timestamp = time.strftime("%Y%m%d_%H%M%S")
    match_ids_file = os.path.join(args.data_dir, f"match_ids_{timestamp}.txt")
    
    # Phase 1: Fetch pro matches
    if args.phase in ["pro_matches", "all"]:
        print("\n=== Phase 1: Fetching Pro Matches ===")
        cmd = f"./fetch_pro_matches.py --limit {args.match_limit} --output-dir {args.data_dir} --delay {args.delay}"
        success, output = run_command(cmd)
        
        if not success:
            print(f"Failed to fetch pro matches: {output}")
            if args.phase == "all":
                print("Cannot proceed to Phase 2 without match IDs. Exiting.")
                sys.exit(1)
        else:
            print(output)
            # Find the most recently created match_ids file
            match_ids_files = [f for f in os.listdir(args.data_dir) if f.startswith("match_ids_")]
            if match_ids_files:
                match_ids_files.sort(reverse=True)  # Sort by name (timestamp) in reverse order
                match_ids_file = os.path.join(args.data_dir, match_ids_files[0])
                print(f"Using match IDs from: {match_ids_file}")
            else:
                print("No match ID file was created. Cannot proceed to Phase 2.")
                if args.phase == "all":
                    sys.exit(1)
    
    # Phase 2: Fetch match details
    if args.phase in ["match_details", "all"]:
        if not os.path.exists(match_ids_file) and args.phase == "match_details":
            print(f"Error: Match IDs file {match_ids_file} not found.")
            print("Please run Phase 1 first or specify an existing match IDs file.")
            sys.exit(1)
            
        print("\n=== Phase 2: Fetching Match Details ===")
        cmd = f"./fetch_match_details.py --match-file {match_ids_file} --output-dir {args.data_dir} --delay {args.delay}"
        success, output = run_command(cmd)
        
        if not success:
            print(f"Failed to fetch match details: {output}")
        else:
            print(output)
    
    print("\n=== Data Collection Complete ===")
    print(f"All data has been saved to: {args.data_dir}")
    
if __name__ == "__main__":
    main()