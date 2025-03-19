#!/usr/bin/env python3
import requests
import json
import time
import os
import argparse
import sys
from datetime import datetime
from pathlib import Path

class MatchDetailFetcher:
    def __init__(self, output_dir=None, rate_limit_delay=1):
        """
        Initialize the MatchDetailFetcher.
        
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
        self.match_details_dir = os.path.join(self.output_dir, "details")
        
        # Create output directories if they don't exist
        os.makedirs(self.match_details_dir, exist_ok=True)
        print(f"Using output directory: {self.output_dir}")
        
    def fetch_match_details(self, match_id):
        """
        Fetch details for a specific match from OpenDota API.
        
        Args:
            match_id (int): The ID of the match to retrieve
            
        Returns:
            dict: Match details or None if there was an error
        """
        url = f"{self.base_url}/matches/{match_id}"
        
        try:
            print(f"Fetching details for match {match_id}...")
            response = requests.get(url)
            response.raise_for_status()
            time.sleep(self.rate_limit_delay)  # Respect rate limits
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"Error fetching match {match_id}: {e}")
            return None
            
    def save_match_details(self, match_details, match_id):
        """
        Save match details to a JSON file.
        
        Args:
            match_details (dict): The match details to save
            match_id (int): The ID of the match
            
        Returns:
            str: Path to the saved file
        """
        if not match_details:
            print(f"No details available for match {match_id}")
            return None
            
        filepath = os.path.join(self.match_details_dir, f"match_{match_id}.json")
        
        with open(filepath, 'w') as f:
            json.dump(match_details, f, indent=2)
            
        print(f"Saved details for match {match_id} to {filepath}")
        return filepath
        
    def extract_draft_information(self, match_details):
        """
        Extract draft information from match details.
        
        Args:
            match_details (dict): The match details to extract information from
            
        Returns:
            dict: Draft information including picks, bans, and order
        """
        if not match_details or "picks_bans" not in match_details:
            return None
            
        picks_bans = match_details.get("picks_bans", [])
        
        # Create structured draft information
        draft_info = {
            "match_id": match_details.get("match_id"),
            "radiant_win": match_details.get("radiant_win"),
            "start_time": match_details.get("start_time"),
            "duration": match_details.get("duration"),
            "radiant_picks": [],
            "dire_picks": [],
            "radiant_bans": [],
            "dire_bans": [],
            "draft_sequence": []
        }
        
        for item in picks_bans:
            team_name = "radiant" if item["team"] == 0 else "dire"
            action_type = "picks" if item["is_pick"] else "bans"
            draft_info[f"{team_name}_{action_type}"].append({
                "hero_id": item["hero_id"],
                "order": item["order"]
            })
            
            draft_info["draft_sequence"].append({
                "team": team_name,
                "action": "pick" if item["is_pick"] else "ban",
                "hero_id": item["hero_id"],
                "order": item["order"]
            })
            
        return draft_info
        
    def save_draft_information(self, draft_info, match_id):
        """
        Save draft information to a JSON file.
        
        Args:
            draft_info (dict): The draft information to save
            match_id (int): The ID of the match
            
        Returns:
            str: Path to the saved file
        """
        if not draft_info:
            print(f"No draft information available for match {match_id}")
            return None
            
        drafts_dir = os.path.join(self.output_dir, "drafts")
        os.makedirs(drafts_dir, exist_ok=True)
        
        filepath = os.path.join(drafts_dir, f"draft_{match_id}.json")
        
        with open(filepath, 'w') as f:
            json.dump(draft_info, f, indent=2)
            
        print(f"Saved draft information for match {match_id} to {filepath}")
        return filepath
    
    def process_match_id_file(self, match_id_file):
        """
        Process a file containing match IDs.
        
        Args:
            match_id_file (str): Path to the file containing match IDs
            
        Returns:
            tuple: (successful_count, failed_count, total_count)
        """
        try:
            with open(match_id_file, 'r') as f:
                match_ids = [line.strip() for line in f if line.strip()]
        except Exception as e:
            print(f"Error reading match ID file: {e}")
            return 0, 0, 0
            
        total_count = len(match_ids)
        successful_count = 0
        failed_count = 0
        
        print(f"Processing {total_count} match IDs from {match_id_file}")
        
        for i, match_id in enumerate(match_ids, 1):
            print(f"Processing match {i}/{total_count} (ID: {match_id})...")
            
            try:
                match_details = self.fetch_match_details(match_id)
                
                if match_details:
                    self.save_match_details(match_details, match_id)
                    
                    draft_info = self.extract_draft_information(match_details)
                    if draft_info:
                        self.save_draft_information(draft_info, match_id)
                        successful_count += 1
                    else:
                        print(f"No draft information for match {match_id}")
                        failed_count += 1
                else:
                    failed_count += 1
            except Exception as e:
                print(f"Error processing match {match_id}: {e}")
                failed_count += 1
                
        return successful_count, failed_count, total_count
        
def find_latest_match_ids_file(base_dir):
    """
    Find the most recent match_ids file in the directory.
    
    Args:
        base_dir (str): Directory to search in
    
    Returns:
        str: Path to the most recent match_ids file or None if not found
    """
    if not os.path.exists(base_dir):
        return None
        
    # Find all match_ids files
    match_id_files = []
    for file in os.listdir(base_dir):
        if file.startswith("match_ids_") and file.endswith(".txt"):
            file_path = os.path.join(base_dir, file)
            match_id_files.append((file_path, os.path.getmtime(file_path)))
            
    if not match_id_files:
        return None
        
    # Sort by modification time (newest first)
    match_id_files.sort(key=lambda x: x[1], reverse=True)
    
    # Return the path to the newest file
    return match_id_files[0][0]

def main():
    parser = argparse.ArgumentParser(description="Fetch Dota 2 match details from OpenDota API")
    parser.add_argument("--match-id", type=str, help="Single match ID to fetch")
    parser.add_argument("--match-file", type=str, help="File containing match IDs, one per line")
    parser.add_argument("--output-dir", type=str, help="Directory to save output files (uses project structure by default)")
    parser.add_argument("--delay", type=int, default=1, 
                       help="Delay between API calls in seconds (default: 1)")
    parser.add_argument("--auto", action="store_true", 
                       help="Automatically use the most recent match_ids file")
    
    args = parser.parse_args()
    
    # Initialize the fetcher
    fetcher = MatchDetailFetcher(output_dir=args.output_dir, rate_limit_delay=args.delay)
    
    # If --auto is specified or no specific input is provided, try to find the latest match_ids file
    if (not args.match_id and not args.match_file) or args.auto:
        latest_file = find_latest_match_ids_file(fetcher.output_dir)
        if latest_file:
            print(f"Using the most recent match IDs file: {latest_file}")
            args.match_file = latest_file
        elif not args.match_id:  # Only exit if we have no match_id and couldn't find a file
            print("Error: No match IDs file found and no match ID provided")
            parser.print_help()
            sys.exit(1)
    elif not args.match_id and not args.match_file:
        print("Error: You must provide either --match-id or --match-file")
        parser.print_help()
        sys.exit(1)
    
    if args.match_id:
        # Process a single match ID
        match_details = fetcher.fetch_match_details(args.match_id)
        if match_details:
            fetcher.save_match_details(match_details, args.match_id)
            draft_info = fetcher.extract_draft_information(match_details)
            if draft_info:
                fetcher.save_draft_information(draft_info, args.match_id)
                print(f"Successfully processed match {args.match_id}")
            else:
                print(f"No draft information available for match {args.match_id}")
    
    if args.match_file:
        # Process multiple match IDs from file
        successful, failed, total = fetcher.process_match_id_file(args.match_file)
        print(f"Completed processing {total} matches: {successful} successful, {failed} failed")
        
if __name__ == "__main__":
    main()