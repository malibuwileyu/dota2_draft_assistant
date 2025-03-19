#!/usr/bin/env python3
import datetime
import time

def get_patch_releases():
    """
    Manually define known patch release dates and timestamps.
    This is used when the API doesn't provide reliable patch data.
    
    Returns:
        list of dictionaries with patch info
    """
    # Hard-coded patch dates - update this as new patches are released
    patches = [
        {"name": "7.35c", "date": "2024-03-01", "timestamp": 1709251200},
        {"name": "7.35b", "date": "2024-02-19", "timestamp": 1708300800},
        {"name": "7.35a", "date": "2024-02-09", "timestamp": 1707436800},
        {"name": "7.35", "date": "2024-02-01", "timestamp": 1706745600},
        {"name": "7.34e", "date": "2023-12-07", "timestamp": 1701907200},
        {"name": "7.34d", "date": "2023-10-25", "timestamp": 1698192000},
        {"name": "7.34c", "date": "2023-10-06", "timestamp": 1696550400},
        {"name": "7.34b", "date": "2023-09-18", "timestamp": 1694995200},
        {"name": "7.34", "date": "2023-08-22", "timestamp": 1692662400}
    ]
    
    return patches

def main():
    patches = get_patch_releases()
    
    print("Available patch timestamps for filtering matches:")
    print("------------------------------------------------")
    
    for patch in patches:
        print(f"Patch {patch['name']}: {patch['date']} (Unix timestamp: {patch['timestamp']})")
    
    # Print the latest patch
    latest = patches[0]
    print("\n\nLatest patch information:")
    print(f"Name: {latest['name']}")
    print(f"Date: {latest['date']}")
    print(f"Unix timestamp: {latest['timestamp']}")
    
    # Print command to use with fetch_pro_matches.py
    print("\nTo fetch pro matches from the latest patch:")
    print(f"python scripts/fetch_pro_matches.py --min-timestamp {latest['timestamp']} --max-matches 1000")
    
    # Print command to use with fetch_pro_matches.py for multiple patches
    print("\nTo fetch pro matches from the current and previous patch:")
    if len(patches) > 1:
        print(f"python scripts/fetch_pro_matches.py --min-timestamp {patches[1]['timestamp']} --max-matches 1000")
    
    # Current time information for reference
    current_timestamp = int(time.time())
    current_date = datetime.datetime.now().strftime("%Y-%m-%d")
    print(f"\nCurrent time: {current_date} (Unix timestamp: {current_timestamp})")
    
    if latest['timestamp'] > current_timestamp:
        print("WARNING: Latest patch timestamp is in the future!")

if __name__ == "__main__":
    main()