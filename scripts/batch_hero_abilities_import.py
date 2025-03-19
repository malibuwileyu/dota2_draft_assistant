#!/usr/bin/env python3
"""
Batch Hero Ability Data Import for Dota 2 Draft Assistant

This script allows you to quickly import pre-formatted ability data
for multiple heroes at once. It's designed to work with the manual_hero_abilities_creator.py
and combines all generated files into a single all_heroes_abilities.json file.
"""
import json
import os
import sys
from collections import defaultdict
import re

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
HEROES_FILE = "src/main/resources/data/heroes.json"
COMBINED_FILE = os.path.join(OUTPUT_DIR, "all_heroes_abilities.json")

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

def load_heroes():
    """Load the heroes from the heroes.json file"""
    try:
        with open(HEROES_FILE, 'r') as f:
            heroes = json.load(f)
        return heroes
    except Exception as e:
        print(f"Error loading heroes: {e}")
        return []

def get_hero_by_name(heroes, name):
    """Get a hero by name or ID"""
    name_lower = name.lower()
    
    # Try exact match first
    for hero in heroes:
        if hero["localized_name"].lower() == name_lower:
            return hero
    
    # Try contains match
    matches = [h for h in heroes if name_lower in h["localized_name"].lower()]
    if len(matches) == 1:
        return matches[0]
    
    # Try by ID
    if name.isdigit():
        id_match = [h for h in heroes if h["id"] == int(name)]
        if id_match:
            return id_match[0]
    
    return None

def save_hero_data(hero_data, file_name):
    """Save hero data to a JSON file"""
    file_path = os.path.join(OUTPUT_DIR, file_name)
    try:
        with open(file_path, 'w') as f:
            json.dump({"heroes": [hero_data]}, f, indent=2)
        return True
    except Exception as e:
        print(f"Error saving file: {e}")
        return False

def create_hero_template(hero_id, hero_name, localized_name):
    """Create a basic hero template"""
    template = {
        "id": hero_id,
        "name": hero_name.lower().replace(' ', '_'),
        "localized_name": localized_name,
        "abilities": [],
        "innate_abilities": []
    }
    return template

def import_hero_batch(heroes, data_text):
    """Import hero ability data from text format"""
    lines = data_text.strip().split('\n')
    
    current_hero = None
    hero_data = None
    current_ability = None
    in_special_values = False
    
    for line in lines:
        line = line.strip()
        if not line:
            continue
        
        # Start of hero section
        if line.startswith("# "):
            hero_name = line[2:].strip()
            hero = get_hero_by_name(heroes, hero_name)
            
            if hero:
                hero_id = hero["id"]
                hero_name = hero["name"].replace("npc_dota_hero_", "")
                localized_name = hero["localized_name"]
                
                hero_data = create_hero_template(hero_id, hero_name, localized_name)
                current_hero = hero
                print(f"Processing {localized_name} (ID: {hero_id})")
            else:
                print(f"ERROR: Hero not found: {hero_name}")
                current_hero = None
                hero_data = None
        
        # Skip if no hero is being processed
        if not hero_data:
            continue
        
        # Start of ability section
        if line.startswith("## ") and hero_data:
            ability_type = "active"
            if "(Ultimate)" in line:
                ability_type = "ultimate"
            elif "(Passive)" in line:
                ability_type = "passive"
            elif "(Innate)" in line:
                ability_type = "innate"
            
            ability_name = re.sub(r'\(.*?\)', '', line[3:]).strip()
            
            ability_index = len(hero_data["abilities"]) + 1
            ability_id = hero_data["id"] * 100 + ability_index
            
            ability = {
                "id": ability_id,
                "name": ability_name,
                "description": "",
                "type": ability_type,
                "pierces_immunity": False,
                "behavior": "passive" if ability_type == "passive" else "no target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {},
                "cooldown": [0, 0, 0] if ability_type == "ultimate" else [0, 0, 0, 0],
                "mana_cost": [0, 0, 0] if ability_type == "ultimate" else [0, 0, 0, 0],
                "notes": ""
            }
            
            if ability_type == "innate":
                ability["behavior"] = "passive"
                hero_data["innate_abilities"].append(ability)
                current_ability = ability
            else:
                hero_data["abilities"].append(ability)
                current_ability = ability
            
            in_special_values = False
        
        # Ability details
        if current_ability and line.startswith("- "):
            property_line = line[2:].strip()
            
            if ":" in property_line:
                key, value = [part.strip() for part in property_line.split(":", 1)]
                
                if in_special_values:
                    try:
                        # Special value format: "- name: 10, 20, 30, 40"
                        values = [float(v.strip()) for v in value.split(",")]
                        current_ability["special_values"][key] = values
                    except ValueError:
                        print(f"  Error parsing special value: {property_line}")
                else:
                    if key == "Description":
                        current_ability["description"] = value
                    elif key == "Behavior":
                        current_ability["behavior"] = value.lower()
                    elif key == "Type":
                        current_ability["type"] = value.lower()
                    elif key == "Damage Type":
                        current_ability["damage_type"] = value.lower()
                    elif key == "Affects":
                        current_ability["affects"] = [a.strip() for a in value.split(",")]
                    elif key == "Pierces Immunity" and value.lower() == "yes":
                        current_ability["pierces_immunity"] = True
                    elif key == "Cooldown":
                        try:
                            current_ability["cooldown"] = [float(v.strip()) for v in value.split(",")]
                        except ValueError:
                            print(f"  Error parsing cooldown: {value}")
                    elif key == "Mana Cost":
                        try:
                            current_ability["mana_cost"] = [float(v.strip()) for v in value.split(",")]
                        except ValueError:
                            print(f"  Error parsing mana cost: {value}")
                    elif key == "Notes":
                        current_ability["notes"] = value
        
        # Special values section
        if current_ability and line == "### Special Values":
            in_special_values = True
        
        # End of hero section
        if line.startswith("---") and hero_data:
            # Save hero data
            file_name = f"{hero_data['name']}_abilities.json"
            if save_hero_data(hero_data, file_name):
                print(f"Saved abilities for {hero_data['localized_name']}")
            
            # Reset state
            current_hero = None
            hero_data = None
            current_ability = None
            in_special_values = False

def combine_all_hero_files():
    """Combine all hero ability files into one file"""
    try:
        all_heroes = {"heroes": []}
        
        # Find all hero ability files
        import glob
        pattern = os.path.join(OUTPUT_DIR, "*_abilities.json")
        hero_files = glob.glob(pattern)
        
        # Filter out the all_heroes file
        hero_files = [f for f in hero_files if not os.path.basename(f).startswith("all_") 
                      and not os.path.basename(f).startswith("hero_abilities_example")]
        
        print(f"Found {len(hero_files)} hero files")
        
        # Load each file and combine heroes
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
        with open(COMBINED_FILE, 'w') as f:
            json.dump(all_heroes, f, indent=2)
        
        print(f"Saved combined data with {len(all_heroes['heroes'])} heroes to {COMBINED_FILE}")
    
    except Exception as e:
        print(f"Error combining hero files: {e}")

def main():
    """Main function"""
    print("Dota 2 Hero Batch Ability Import")
    print("================================")
    
    heroes = load_heroes()
    if not heroes:
        print("No heroes found. Exiting.")
        return
    
    print(f"Loaded {len(heroes)} heroes from heroes.json")
    
    print("\nThis tool allows you to import ability data for multiple heroes at once.")
    print("Format your data like this:")
    print("# Hero Name")
    print("## Ability Name (Ultimate/Passive/Innate)")
    print("- Description: Description text here")
    print("- Behavior: no target/unit target/point target/directional/passive/auto-cast")
    print("- Damage Type: magical/physical/pure/none")
    print("- Affects: enemies, allies, self")
    print("- Pierces Immunity: Yes/No")
    print("- Cooldown: 10, 9, 8, 7")
    print("- Mana Cost: 100, 120, 140, 160")
    print("- Notes: Additional notes")
    print("### Special Values")
    print("- damage: 100, 200, 300, 400")
    print("- duration: 2, 3, 4, 5")
    print("---")
    
    print("\nOptions:")
    print("1. Import from text")
    print("2. Import from file")
    print("3. Combine all hero files")
    print("4. Exit")
    
    choice = input("Enter choice: ")
    
    if choice == "1":
        print("\nPaste the formatted ability data below (end with a blank line):")
        lines = []
        while True:
            line = input()
            if not line:
                break
            lines.append(line)
        
        if lines:
            data_text = "\n".join(lines)
            import_hero_batch(heroes, data_text)
            
            # Ask if user wants to combine files
            combine = input("Combine all hero files? (y/n) [y]: ")
            if combine.lower() != 'n':
                combine_all_hero_files()
        else:
            print("No data provided.")
    
    elif choice == "2":
        file_path = input("Enter the path to the file: ")
        if os.path.exists(file_path):
            try:
                with open(file_path, 'r') as f:
                    data_text = f.read()
                import_hero_batch(heroes, data_text)
                
                # Ask if user wants to combine files
                combine = input("Combine all hero files? (y/n) [y]: ")
                if combine.lower() != 'n':
                    combine_all_hero_files()
            except Exception as e:
                print(f"Error reading file: {e}")
        else:
            print(f"File not found: {file_path}")
    
    elif choice == "3":
        combine_all_hero_files()
    
    elif choice == "4":
        print("Exiting")
    
    else:
        print("Invalid choice")

if __name__ == "__main__":
    main()