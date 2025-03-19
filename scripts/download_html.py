#!/usr/bin/env python3
"""
Download HTML content from Liquipedia for manual inspection
"""
import urllib.request
import ssl
import os

# Configuration
HERO_NAME = "Axe"
OUTPUT_FILE = "axe_page.html"
URL = f"https://liquipedia.net/dota2/{HERO_NAME}"

# Create a context that doesn't validate certificates
context = ssl._create_unverified_context()

# Set up a request with a proper user agent
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
req = urllib.request.Request(URL, headers=headers)

print(f"Downloading {URL}...")
try:
    with urllib.request.urlopen(req, context=context) as response:
        html_content = response.read()
        
        # Save the HTML content to a file
        with open(OUTPUT_FILE, "wb") as f:
            f.write(html_content)
        
        print(f"Saved {len(html_content)} bytes to {OUTPUT_FILE}")
except Exception as e:
    print(f"Error downloading page: {e}")