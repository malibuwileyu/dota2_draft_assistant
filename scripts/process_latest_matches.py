#!/usr/bin/env python3
import os
import sys
import argparse
import subprocess
from datetime import datetime

def get_latest_match_ids_file(matches_dir):
    """
    Find the most recent match_ids file in the matches directory.
    
    Args:
        matches_dir (str): Directory containing match_ids files
    
    Returns:
        str: Path to the most recent match_ids file
    """
    if not os.path.exists(matches_dir):
        print(f"Error: Directory '{matches_dir}' does not exist.")
        return None
    
    match_id_files = []
    for file in os.listdir(matches_dir):
        if file.startswith("match_ids_") and file.endswith(".txt"):
            file_path = os.path.join(matches_dir, file)
            match_id_files.append((file_path, os.path.getmtime(file_path)))
    
    if not match_id_files:
        print(f"Error: No match_ids files found in '{matches_dir}'.")
        return None
    
    # Sort by modification time (most recent first)
    match_id_files.sort(key=lambda x: x[1], reverse=True)
    
    latest_file = match_id_files[0][0]
    print(f"Found latest match IDs file: {latest_file}")
    return latest_file

def count_existing_match_files(details_dir, drafts_dir=None):
    """
    Count existing match details and draft files.
    
    Args:
        details_dir (str): Directory containing match detail files
        drafts_dir (str): Directory containing draft files
        
    Returns:
        tuple: (details_count, drafts_count)
    """
    details_count = sum(1 for f in os.listdir(details_dir) if f.startswith("match_") and f.endswith(".json"))
    
    drafts_count = 0
    if drafts_dir and os.path.exists(drafts_dir):
        drafts_count = sum(1 for f in os.listdir(drafts_dir) if f.startswith("draft_") and f.endswith(".json"))
    
    return details_count, drafts_count

def main():
    parser = argparse.ArgumentParser(description="Process latest Dota 2 match IDs")
    parser.add_argument("--base-dir", type=str, help="Base directory for match data (defaults to project structure)")
    parser.add_argument("--fetch-limit", type=int, default=100, help="Maximum number of new matches to fetch")
    parser.add_argument("--delay", type=int, default=1, help="Delay between API calls in seconds (default: 1)")
    parser.add_argument("--update-only", action="store_true", help="Only update if we have new matches to process")
    
    args = parser.parse_args()
    
    # Set up base directory
    if args.base_dir:
        base_dir = args.base_dir
    else:
        # Use default project structure
        script_dir = os.path.dirname(os.path.abspath(__file__))
        base_dir = os.path.join(script_dir, "..", "src", "main", "resources", "data", "matches")
    
    # Check that the base directory exists
    if not os.path.exists(base_dir):
        print(f"Error: Base directory '{base_dir}' does not exist.")
        return 1
    
    # Find the latest match IDs file
    latest_file = get_latest_match_ids_file(base_dir)
    if not latest_file:
        print("No match ID files found. Please run fetch_pro_matches.py first.")
        return 1
    
    # Count existing match files
    details_dir = os.path.join(base_dir, "details")
    drafts_dir = os.path.join(base_dir, "drafts")
    
    if not os.path.exists(details_dir):
        os.makedirs(details_dir)
    
    if not os.path.exists(drafts_dir):
        os.makedirs(drafts_dir)
    
    existing_details, existing_drafts = count_existing_match_files(details_dir, drafts_dir)
    print(f"Found {existing_details} existing match detail files and {existing_drafts} draft files.")
    
    # Read the latest match IDs
    with open(latest_file, 'r') as f:
        match_ids = [line.strip() for line in f if line.strip()]
    
    print(f"Found {len(match_ids)} match IDs in the latest file.")
    
    # Only proceed if there are new matches to process or update-only is not set
    if not args.update_only or len(match_ids) > existing_details:
        # Run fetch_match_details.py with the latest match IDs file
        fetch_script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fetch_match_details.py")
        
        cmd = [
            "python3", 
            fetch_script, 
            "--match-file", latest_file, 
            "--delay", str(args.delay), 
            "--output-dir", base_dir
        ]
        
        print(f"Running command: {' '.join(cmd)}")
        subprocess.run(cmd)
        
        # Count new totals
        new_details, new_drafts = count_existing_match_files(details_dir, drafts_dir)
        print(f"After processing: {new_details} match details (+{new_details - existing_details}) and {new_drafts} drafts (+{new_drafts - existing_drafts})")
        
        return 0
    else:
        print("No new matches to process. Skipping fetch operation.")
        return 0

if __name__ == "__main__":
    sys.exit(main())