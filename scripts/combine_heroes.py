#!/usr/bin/env python3
"""
Combine all individual hero ability JSON files into a single comprehensive file.
"""
import os
import json
import glob

# Configuration
INPUT_DIR = "src/main/resources/data/abilities"
OUTPUT_FILE = "src/main/resources/data/abilities/all_heroes_abilities.json"

def main():
    # Get all JSON files in the directory
    json_files = glob.glob(os.path.join(INPUT_DIR, "*_abilities.json"))
    
    # Skip the combined file if it exists
    json_files = [f for f in json_files if not os.path.basename(f).startswith("all_")]
    
    print(f"Found {len(json_files)} hero files")
    
    # Combine all heroes
    all_heroes = {"heroes": []}
    
    for file_path in json_files:
        try:
            with open(file_path, 'r') as f:
                hero_data = json.load(f)
                
                if "heroes" in hero_data and len(hero_data["heroes"]) > 0:
                    hero = hero_data["heroes"][0]
                    
                    # Skip the example files
                    if hero["name"] == "hero_abilities_example":
                        continue
                        
                    # Add the hero to the combined data
                    all_heroes["heroes"].append(hero)
                    print(f"Added {hero['localized_name']}")
        except Exception as e:
            print(f"Error processing {file_path}: {e}")
    
    # Sort heroes by ID
    all_heroes["heroes"] = sorted(all_heroes["heroes"], key=lambda h: h["id"])
    
    # Save the combined file
    with open(OUTPUT_FILE, 'w') as f:
        json.dump(all_heroes, f, indent=2)
    
    print(f"Saved {len(all_heroes['heroes'])} heroes to {OUTPUT_FILE}")

if __name__ == "__main__":
    main()