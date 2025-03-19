#!/usr/bin/env python3
# Test script for hero_scraper that processes a limited set of heroes

from hero_scraper import scrape_hero_abilities, format_hero_name_for_file
import json
import os

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Test with a smaller set of heroes
test_heroes = [
    "Anti-Mage",
    "Invoker",
    "Rubick",
    "Shadow Fiend",
    "Crystal Maiden"
]

print(f"Testing with {len(test_heroes)} heroes")

heroes_data = {"heroes": []}

for hero_name in test_heroes:
    try:
        print(f"Processing {hero_name}...")
        hero_data = scrape_hero_abilities(hero_name)
        
        if hero_data:
            # Save individual hero file
            file_name = f"{hero_data['name']}_abilities.json"
            file_path = os.path.join(OUTPUT_DIR, file_name)
            
            # Create a single-hero file
            with open(file_path, 'w') as f:
                json.dump({"heroes": [hero_data]}, f, indent=2)
            
            print(f"Saved {file_name}")
            
            # Add to complete heroes data
            heroes_data["heroes"].append(hero_data)
        
    except Exception as e:
        print(f"Error processing {hero_name}: {e}")

# Save test heroes file
with open(os.path.join(OUTPUT_DIR, "test_heroes_abilities.json"), 'w') as f:
    json.dump(heroes_data, f, indent=2)

print(f"Scraped {len(heroes_data['heroes'])} test heroes successfully")