#!/usr/bin/env python3
import requests
import json
import time
import os
import sys
import argparse
from datetime import datetime

class ProMatchFetcher:
    def __init__(self, output_dir=None, rate_limit_delay=1):
        """
        Initialize the ProMatchFetcher.
        
        Args:
            output_dir (str): Directory to store the fetched data
            rate_limit_delay (int): Delay between API calls in seconds to respect rate limits
        """
        self.base_url = "https://api.opendota.com/api"
        
        # Set default output directory based on platform
        if output_dir is None:
            # Get the current script's directory and navigate to the target folder
            script_dir = os.path.dirname(os.path.abspath(__file__))
            base_dir = os.path.join(script_dir, "src", "main", "resources", "data", "matches")
            self.output_dir = base_dir
        else:
            self.output_dir = output_dir
            
        self.rate_limit_delay = rate_limit_delay
        
        # Create output directory if it doesn't exist
        os.makedirs(self.output_dir, exist_ok=True)
        print(f"Using output directory: {self.output_dir}")
        
    def fetch_pro_matches(self, limit=100, less_than_match_id=None):
        """
        Fetch professional match data from OpenDota API.
        
        Args:
            limit (int): Number of matches to retrieve
            less_than_match_id (int): Optional match ID for pagination, gets matches with ID lower than this
            
        Returns:
            list: List of match data dictionaries
        """
        url = f"{self.base_url}/proMatches"
        params = {"limit": limit}
        
        if less_than_match_id:
            params["less_than_match_id"] = less_than_match_id
        
        try:
            response = requests.get(url, params=params)
            response.raise_for_status()
            result = response.json()
            print(f"Fetched {len(result)} pro matches from API")
            return result
        except requests.exceptions.RequestException as e:
            print(f"Error fetching pro matches: {e}")
            return []
            
    def save_matches(self, matches, filename=None):
        """
        Save match data to a JSON file.
        
        Args:
            matches (list): List of match data dictionaries
            filename (str): Optional filename to use
            
        Returns:
            str: Path to the saved file
        """
        if not filename:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"pro_matches_{timestamp}.json"
            
        filepath = os.path.join(self.output_dir, filename)
        
        with open(filepath, 'w') as f:
            json.dump(matches, f, indent=2)
            
        print(f"Saved {len(matches)} matches to {filepath}")
        return filepath
        
    def extract_match_ids(self, matches):
        """
        Extract match IDs from match data.
        
        Args:
            matches (list): List of match data dictionaries
            
        Returns:
            list: List of match IDs
        """
        return [match["match_id"] for match in matches if "match_id" in match]
        
    def save_match_ids(self, match_ids, filename=None):
        """
        Save match IDs to a text file, one ID per line.
        
        Args:
            match_ids (list): List of match IDs
            filename (str): Optional filename to use
            
        Returns:
            str: Path to the saved file
        """
        if not filename:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"match_ids_{timestamp}.txt"
            
        filepath = os.path.join(self.output_dir, filename)
        
        with open(filepath, 'w') as f:
            for match_id in match_ids:
                f.write(f"{match_id}\n")
                
        print(f"Saved {len(match_ids)} match IDs to {filepath}")
        return filepath

def get_latest_patch_timestamp():
    """
    Get the timestamp of the latest patch using the get_latest_patch.py script.
    
    Returns:
        int: Unix timestamp of the latest patch or None if not available
    """
    try:
        # Import and use the get_latest_patch module
        script_dir = os.path.dirname(os.path.abspath(__file__))
        sys.path.append(script_dir)
        
        # Try to import the module
        try:
            from get_latest_patch import get_latest_patch
            patch_info = get_latest_patch()
            if patch_info and 'timestamp' in patch_info:
                print(f"Using latest patch: {patch_info.get('name', 'unknown')} from {patch_info.get('date_str', 'unknown date')}")
                return patch_info['timestamp']
        except ImportError:
            print("Could not import get_latest_patch module")
            return None
    except Exception as e:
        print(f"Error getting latest patch timestamp: {e}")
        return None

def main():
    parser = argparse.ArgumentParser(description="Fetch professional Dota 2 matches from OpenDota API")
    parser.add_argument("--limit", type=int, default=100, help="Number of matches to retrieve (default: 100)")
    parser.add_argument("--min-timestamp", type=int, help="Minimum start_time timestamp to filter matches (for patch filtering)")
    parser.add_argument("--output-dir", type=str, help="Directory to save output files (uses project structure by default)")
    parser.add_argument("--delay", type=int, default=1, help="Delay between API calls in seconds (default: 1)")
    parser.add_argument("--ids-only", action="store_true", help="Only save match IDs, not full match data")
    parser.add_argument("--max-matches", type=int, default=500, help="Maximum number of matches to fetch across all requests (default: 500)")
    parser.add_argument("--latest-patch", action="store_true", help="Automatically filter matches from the latest patch")
    
    args = parser.parse_args()
    
    # If --latest-patch is specified, get the timestamp for the latest patch
    min_timestamp = args.min_timestamp
    if args.latest_patch and not min_timestamp:
        min_timestamp = get_latest_patch_timestamp()
        if min_timestamp:
            print(f"Using timestamp from latest patch: {min_timestamp}")
    
    fetcher = ProMatchFetcher(output_dir=args.output_dir, rate_limit_delay=args.delay)
    
    # Track total matches and use paging to get more than the API limit (100) per request
    all_matches = []
    last_match_id = None
    total_fetched = 0
    max_matches = args.max_matches
    empty_batches_in_a_row = 0
    
    # Keep fetching until we hit the max or run out of matches
    while total_fetched < max_matches:
        print(f"Fetching batch of matches (total so far: {total_fetched})...")
        # Use less_than_match_id for pagination
        batch = fetcher.fetch_pro_matches(limit=100, less_than_match_id=last_match_id)
        
        # Check if we got any matches back
        if not batch or len(batch) == 0:
            empty_batches_in_a_row += 1
            print(f"No matches in this batch. Attempts with no matches: {empty_batches_in_a_row}")
            
            # If we've had multiple empty batches, assume we're done
            if empty_batches_in_a_row >= 2:
                print("Multiple empty batches received. No more matches available.")
                break
                
            # Otherwise try again with a delay
            time.sleep(args.delay * 3)
            continue
        
        # Reset empty batch counter since we got data
        empty_batches_in_a_row = 0
        
        # Filter by minimum timestamp if provided
        if min_timestamp:
            filtered_batch = [match for match in batch if match.get("start_time", 0) >= min_timestamp]
            print(f"Filtered to {len(filtered_batch)} matches after timestamp check.")
            
            # If no matches pass the filter, this could mean we've gone too far back in time
            if not filtered_batch:
                print("No matches from the current patch in this batch. We may have reached the patch boundary.")
                # Try one more batch to confirm
                if last_match_id:
                    last_match_id = batch[-1]["match_id"]
                    continue
                else:
                    break
                    
            batch = filtered_batch
        
        # Update for next iteration
        if batch:
            last_match_id = batch[-1]["match_id"]
            all_matches.extend(batch)
            total_fetched += len(batch)
            print(f"Added {len(batch)} matches, new total: {total_fetched}")
            
            # Small visual progress indicator for large fetches
            if total_fetched % 500 == 0:
                print(f"===== Milestone: {total_fetched} matches downloaded =====")
        
        # Check if we've hit the requested maximum
        if max_matches > 0 and len(all_matches) >= max_matches:
            all_matches = all_matches[:max_matches]  # Trim to max requested
            print(f"Reached maximum requested matches: {max_matches}")
            break
            
        # Delay between batch requests to respect API rate limits
        time.sleep(args.delay * 2)  # Extra delay between batches
    
    if not all_matches:
        print("No matches retrieved. Exiting.")
        return
        
    if not args.ids_only:
        fetcher.save_matches(all_matches)
        
    match_ids = fetcher.extract_match_ids(all_matches)
    fetcher.save_match_ids(match_ids)
    
    print(f"Successfully retrieved {len(all_matches)} professional matches.")

if __name__ == "__main__":
    main()