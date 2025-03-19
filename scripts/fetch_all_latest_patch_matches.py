#!/usr/bin/env python3
import os
import sys
import time
import argparse
import subprocess
import json
import glob
from datetime import datetime

def get_latest_patch_timestamp():
    """Get the timestamp of the latest patch using get_latest_patch.py"""
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        sys.path.append(script_dir)
        
        try:
            from get_latest_patch import get_latest_patch
            patch_info = get_latest_patch()
            if patch_info and 'timestamp' in patch_info:
                print(f"Using latest patch: {patch_info.get('name', 'unknown')} from {patch_info.get('date_str', 'unknown date')}")
                return patch_info['timestamp'], patch_info.get('name', 'unknown')
        except ImportError:
            print("Could not import get_latest_patch module")
    except Exception as e:
        print(f"Error getting latest patch timestamp: {e}")
    
    return None, None

def get_existing_match_ids(match_dir):
    """Get existing match IDs from the details directory"""
    details_dir = os.path.join(match_dir, "details")
    
    if not os.path.exists(details_dir):
        return set()
        
    existing_ids = set()
    for file in os.listdir(details_dir):
        if file.startswith("match_") and file.endswith(".json"):
            try:
                match_id = int(file[6:-5])  # Extract match ID from "match_XXXXXX.json"
                existing_ids.add(match_id)
            except ValueError:
                pass
                
    return existing_ids

def download_and_process_matches(patch_timestamp=None, max_matches=1000, delay=2):
    """Download and process all matches from the latest patch"""
    # Setup paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_dir = os.path.dirname(script_dir)
    base_match_dir = os.path.join(project_dir, "src", "main", "resources", "data", "matches")
    
    # Create directories if they don't exist
    os.makedirs(base_match_dir, exist_ok=True)
    os.makedirs(os.path.join(base_match_dir, "details"), exist_ok=True)
    os.makedirs(os.path.join(base_match_dir, "drafts"), exist_ok=True)
    
    # Get existing match IDs to avoid duplicates
    existing_ids = get_existing_match_ids(base_match_dir)
    print(f"Found {len(existing_ids)} existing match detail files")
    
    # Get latest patch timestamp if not provided
    if not patch_timestamp:
        patch_timestamp, patch_name = get_latest_patch_timestamp()
        if not patch_timestamp:
            print("Error: Could not determine the latest patch timestamp")
            return False
    
    # Step 1: Download pro match listings
    print("\n===== STEP 1: Downloading Pro Match Listings =====")
    fetch_matches_script = os.path.join(script_dir, "fetch_pro_matches.py")
    
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    pro_matches_file = os.path.join(base_match_dir, f"pro_matches_{timestamp}.json")
    match_ids_file = os.path.join(base_match_dir, f"match_ids_{timestamp}.txt")
    
    # Use 'python' instead of 'python3' for Windows compatibility
    python_cmd = "python"
    
    # Convert paths to use correct format for the OS
    fetch_matches_script_path = os.path.normpath(fetch_matches_script)
    base_match_dir_path = os.path.normpath(base_match_dir)
    
    fetch_cmd = [
        python_cmd,
        fetch_matches_script_path,
        "--min-timestamp", str(patch_timestamp),
        "--max-matches", str(max_matches),
        "--delay", str(delay),
        "--output-dir", base_match_dir_path
    ]
    
    print(f"Running command: {' '.join(fetch_cmd)}")
    result = subprocess.run(fetch_cmd, capture_output=True, text=True)
    
    if result.returncode != 0:
        print(f"Error running fetch_pro_matches.py: {result.stderr}")
        return False
    
    print(result.stdout)
    
    # Step 2: Find the latest match IDs file
    # Sometimes the timestamp in the filename is slightly different, so look for the newest file
    if not os.path.exists(match_ids_file):
        # Try to find any match_ids file with a similar timestamp
        match_id_files = glob.glob(os.path.join(base_match_dir, f"match_ids_*.txt"))
        if match_id_files:
            # Sort by modification time (most recent first)
            match_id_files.sort(key=os.path.getmtime, reverse=True)
            match_ids_file = match_id_files[0]
            print(f"Using newest match IDs file instead: {match_ids_file}")
        else:
            print(f"Error: No match IDs files found in {base_match_dir}")
            return False
    
    with open(match_ids_file, 'r') as f:
        downloaded_ids = [line.strip() for line in f if line.strip()]
    
    # Filter out matches we already have
    new_match_ids = []
    for match_id in downloaded_ids:
        try:
            if int(match_id) not in existing_ids:
                new_match_ids.append(match_id)
        except ValueError:
            # Skip invalid match IDs
            pass
    
    # Write filtered IDs to a new file
    filtered_ids_file = os.path.join(base_match_dir, f"filtered_match_ids_{timestamp}.txt")
    with open(filtered_ids_file, 'w') as f:
        for match_id in new_match_ids:
            f.write(f"{match_id}\n")
    
    print(f"Filtered {len(downloaded_ids)} total matches to {len(new_match_ids)} new matches")
    
    # Step 3: Process filtered match IDs to get details and drafts
    if new_match_ids:
        print("\n===== STEP 3: Downloading Match Details =====")
        fetch_details_script = os.path.join(script_dir, "fetch_match_details.py")
        
        # Convert paths for details script too
        fetch_details_script_path = os.path.normpath(fetch_details_script)
        filtered_ids_file_path = os.path.normpath(filtered_ids_file)
        
        details_cmd = [
            python_cmd,
            fetch_details_script_path,
            "--match-file", filtered_ids_file_path,
            "--delay", str(delay),
            "--output-dir", base_match_dir_path
        ]
        
        print(f"Running command: {' '.join(details_cmd)}")
        result = subprocess.run(details_cmd)
        
        if result.returncode != 0:
            print(f"Error running fetch_match_details.py")
            return False
    else:
        print("No new matches to process. All matches from the latest patch are already downloaded.")
    
    # Step 4: Count final results
    final_existing = get_existing_match_ids(base_match_dir)
    draft_dir = os.path.join(base_match_dir, "drafts")
    draft_count = len([f for f in os.listdir(draft_dir) if f.startswith("draft_") and f.endswith(".json")])
    
    print(f"\nFinal count: {len(final_existing)} match details and {draft_count} draft files")
    print(f"Added {len(final_existing) - len(existing_ids)} new match details")
    
    return True

def main():
    parser = argparse.ArgumentParser(description="Download all pro matches from the latest patch")
    parser.add_argument("--max-matches", type=int, help="Optional: Maximum number of matches to download (default: all available)")
    parser.add_argument("--delay", type=int, default=2, help="Delay between API calls in seconds (default: 2)")
    parser.add_argument("--patch-timestamp", type=int, help="Specific patch timestamp to use instead of latest")
    parser.add_argument("--all", action="store_true", help="Download all available matches from the patch without limit")
    
    args = parser.parse_args()
    
    # If --all is specified or no max_matches is provided, use a very high number to get all matches
    max_matches = args.max_matches
    if args.all or max_matches is None:
        max_matches = 10000  # A very high number to effectively get all matches
    
    success = download_and_process_matches(
        patch_timestamp=args.patch_timestamp,
        max_matches=max_matches,
        delay=args.delay
    )
    
    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(main())