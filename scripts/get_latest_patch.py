#!/usr/bin/env python3
import requests
import json
import datetime
import time

def parse_date_to_timestamp(date_str):
    """
    Parse a date string to Unix timestamp.
    Handles both ISO format strings and direct timestamp ints.
    """
    if not date_str:
        return 0
        
    # If already a timestamp
    if isinstance(date_str, int) or date_str.isdigit():
        return int(date_str)
        
    # Parse ISO date string (e.g. '2010-12-24T00:00:00Z' or '2025-02-19T13:48:29.412Z')
    try:
        from dateutil import parser  # More robust date parsing
        dt = parser.parse(date_str)
        return int(dt.timestamp())
    except ImportError:
        # Fall back to manual parsing if dateutil not available
        try:
            # Handle fractional seconds
            if '.' in date_str:
                clean_date = date_str.split('.')[0].replace('Z', '').replace('T', ' ')
                dt = datetime.datetime.strptime(clean_date, '%Y-%m-%d %H:%M:%S')
            else:
                clean_date = date_str.replace('Z', '').replace('T', ' ')
                dt = datetime.datetime.strptime(clean_date, '%Y-%m-%d %H:%M:%S')
            return int(dt.timestamp())
        except Exception as e:
            print(f"Error parsing date: {date_str} - {e}")
            # Use a hardcoded recent timestamp as fallback (February 19, 2025)
            return 1740121709

def get_latest_patch():
    """
    Get the latest patch information from OpenDota API.
    
    Returns:
        dict: Latest patch information including timestamp and version
    """
    url = "https://api.opendota.com/api/constants/patch"
    
    try:
        response = requests.get(url)
        response.raise_for_status()
        
        # Get the JSON data
        patches = response.json()
        
        if not patches:
            print("Error: Empty response from API")
            return None
            
        # Find the latest patch by ID (highest number)
        latest_patch = None
        latest_id = -1
        
        for patch in patches:
            # Use ID since it's a reliable sequential identifier
            patch_id = patch.get('id')
            if patch_id is not None and int(patch_id) > latest_id:
                latest_id = int(patch_id)
                latest_patch = patch
        
        if latest_patch:
            # Convert the date string to a Unix timestamp
            date_str = latest_patch.get('date')
            timestamp = parse_date_to_timestamp(date_str)
            
            # Format the response
            result = {
                'id': latest_patch.get('id'),
                'name': latest_patch.get('name'),
                'raw_date': date_str,
                'timestamp': timestamp,
                'date_str': datetime.datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d')
            }
            return result
        else:
            print("Error: Could not determine latest patch")
            return None
            
    except Exception as e:
        print(f"Error getting patch data: {e}")
        return None

def main():
    latest_patch = get_latest_patch()
    
    if latest_patch:
        print(f"Latest patch: {latest_patch['name']} (ID: {latest_patch['id']})")
        print(f"Released on: {latest_patch['date_str']}")
        print(f"Unix timestamp: {latest_patch['timestamp']}")
        
        # Print command to fetch matches since this patch
        print("\nTo fetch pro matches since this patch:")
        print(f"python scripts/fetch_pro_matches.py --min-timestamp {latest_patch['timestamp']} --max-matches 1000")
    else:
        print("Could not retrieve latest patch information")

if __name__ == "__main__":
    main()