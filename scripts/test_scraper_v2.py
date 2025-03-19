#!/usr/bin/env python3
"""
Test the improved hero scraper with a small subset of heroes
"""
from hero_scraper_v2 import scrape_hero, save_hero_data, format_hero_name_for_file
import os
import time
import random
import traceback

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
TEST_HEROES = ["Axe", "Crystal Maiden", "Invoker", "Enigma"]

def main():
    """Test the hero scraper with a small subset of heroes"""
    print(f"Testing scraper on {len(TEST_HEROES)} heroes")
    
    heroes_data = {"heroes": []}
    success_count = 0
    
    # Ensure output directory exists
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    for hero_name in TEST_HEROES:
        try:
            print(f"Processing {hero_name}...")
            hero_data = scrape_hero(hero_name)
            
            # Debug print
            print(f"Hero data for {hero_name}: {hero_data is not None}")
            if hero_data:
                print(f"Abilities: {len(hero_data.get('abilities', []))} - Innate: {len(hero_data.get('innate_abilities', []))}")
            
            if hero_data and (hero_data.get("abilities") or hero_data.get("innate_abilities")):
                file_name = f"{format_hero_name_for_file(hero_name)}_abilities.json"
                if save_hero_data(hero_data, file_name):
                    print(f"Saved {file_name} with {len(hero_data['abilities'])} abilities and {len(hero_data['innate_abilities'])} innate abilities")
                    heroes_data["heroes"].append(hero_data)
                    success_count += 1
            
            # Be nice to the website
            # Add a delay between requests
            time.sleep(1 + random.random())
            
        except Exception as e:
            print(f"Error processing {hero_name}: {e}")
            traceback.print_exc()
    
    print(f"Successfully processed {success_count} out of {len(TEST_HEROES)} heroes")

if __name__ == "__main__":
    main()