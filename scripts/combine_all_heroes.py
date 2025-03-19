#!/usr/bin/env python3
"""
Combine all individual hero JSON files into one comprehensive file.
"""
import os
import json
import glob

# Configuration
INPUT_DIR = "src/main/resources/data/abilities"
OUTPUT_FILE = os.path.join(INPUT_DIR, "all_heroes_abilities.json")

def main():
    """Combine all hero JSON files into one file"""
    # Find all hero ability files
    pattern = os.path.join(INPUT_DIR, "*_abilities.json")
    hero_files = glob.glob(pattern)
    
    # Filter out the example file and all_heroes file
    hero_files = [f for f in hero_files if not os.path.basename(f).startswith("all_") and not os.path.basename(f).startswith("hero_abilities_example")]
    
    print(f"Found {len(hero_files)} hero files")
    
    # Load each file and combine heroes
    all_heroes = {"heroes": []}
    
    for file_path in hero_files:
        try:
            with open(file_path, 'r') as f:
                hero_data = json.load(f)
                
                # Add hero to combined data if it exists
                if "heroes" in hero_data and len(hero_data["heroes"]) > 0:
                    hero = hero_data["heroes"][0]
                    all_heroes["heroes"].append(hero)
                    print(f"Added {hero['localized_name']} to combined data")
        except Exception as e:
            print(f"Error processing {file_path}: {e}")
    
    # Sort heroes by ID
    all_heroes["heroes"].sort(key=lambda h: h["id"])
    
    # Save the combined file
    with open(OUTPUT_FILE, 'w') as f:
        json.dump(all_heroes, f, indent=2)
    
    print(f"Saved combined data with {len(all_heroes['heroes'])} heroes to {OUTPUT_FILE}")

if __name__ == "__main__":
    main()