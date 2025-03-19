#!/usr/bin/env python3
import requests
import json
import datetime
import time

def get_latest_patch_timestamp():
    """
    Get the timestamp of the latest patch from OpenDota API.
    The patch endpoint returns patch data including timestamps.
    
    Returns:
        tuple: (timestamp, patch_version, release_date)
    """
    url = "https://api.opendota.com/api/constants/patch"
    
    try:
        response = requests.get(url)
        response.raise_for_status()
        patches = response.json()
        
        # The patches should be provided as a list with patch data
        # Make sure we're working with a list
        if not isinstance(patches, list):
            print("Unexpected API response format - patches not in list format")
            return None, None, None
        
        # Sort patches by date, newest first (if they have start_date)
        valid_patches = [p for p in patches if "start_date" in p]
        if not valid_patches:
            print("No patches with start_date found in the API response")
            return None, None, None
            
        valid_patches.sort(key=lambda x: x["start_date"], reverse=True)
        
        # Get the latest patch
        latest_patch = valid_patches[0]
        timestamp = latest_patch["start_date"]
        name = latest_patch.get("name", f"Patch {latest_patch.get('id', 'unknown')}")
        
        # Convert timestamp to human-readable date
        date_str = datetime.datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d")
        
        return (timestamp, name, date_str)
    
    except Exception as e:
        print(f"Error getting patch data: {e}")
        return None, None, None

def print_recent_patches(count=5):
    """Print the most recent patches and their timestamps"""
    url = "https://api.opendota.com/api/constants/patch"
    
    try:
        response = requests.get(url)
        response.raise_for_status()
        patches = response.json()
        
        # Make sure we have a list format
        if not isinstance(patches, list):
            print("Unexpected API response format - patches not in list format")
            return
        
        # Filter to patches with start_date and sort
        valid_patches = [p for p in patches if "start_date" in p]
        valid_patches.sort(key=lambda x: x["start_date"], reverse=True)
        
        if not valid_patches:
            print("No patches with timestamp information found")
            return
        
        # Print the most recent patches
        print(f"Most recent {count} patches:")
        for i, patch in enumerate(valid_patches[:count]):
            timestamp = patch["start_date"]
            name = patch.get("name", f"Patch {patch.get('id', i+1)}")
            date_str = datetime.datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d")
            print(f"{i+1}. {name}: {date_str} (Unix timestamp: {timestamp})")
        
    except Exception as e:
        print(f"Error getting patch data: {e}")

def main():
    timestamp, version, date = get_latest_patch_timestamp()
    
    if timestamp:
        print(f"Latest patch: {version}")
        print(f"Released on: {date}")
        print(f"Unix timestamp: {timestamp}")
        
        # Print command example for fetch_pro_matches.py
        print("\nTo fetch all pro matches since this patch:")
        print(f"python scripts/fetch_pro_matches.py --min-timestamp {timestamp} --max-matches 1000")
    else:
        print("Could not retrieve patch information")
    
    # Print recent patches as well
    print("\n")
    print_recent_patches()

if __name__ == "__main__":
    main()