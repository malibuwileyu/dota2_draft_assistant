#!/usr/bin/env python3
"""
Manual Hero Ability Data Entry for Dota 2 Draft Assistant

This script helps to manually create ability data for all heroes in Dota 2.
It loads the heroes.json file and allows generating templates for all heroes,
then provides a way to manually input details for each ability.
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

def create_ability_template(hero_id, hero_name, localized_name):
    """Create an ability template for a hero"""
    template = {
        "id": hero_id,
        "name": hero_name.lower().replace(' ', '_'),
        "localized_name": localized_name,
        "abilities": [],
        "innate_abilities": []
    }
    
    # Standard ability IDs start at hero_id * 100 + 1
    for i in range(1, 5):
        ability_id = hero_id * 100 + i
        template["abilities"].append({
            "id": ability_id,
            "name": f"Ability {i}",
            "description": f"Description for {localized_name}'s ability {i}",
            "type": "active" if i < 4 else "ultimate",
            "pierces_immunity": False,
            "behavior": "no target",
            "damage_type": "magical",
            "affects": ["enemies"],
            "special_values": {},
            "cooldown": [0, 0, 0, 0] if i < 4 else [0, 0, 0],
            "mana_cost": [0, 0, 0, 0] if i < 4 else [0, 0, 0],
            "notes": ""
        })
    
    return template

def generate_all_templates():
    """Generate templates for all heroes"""
    heroes = load_heroes()
    templates = []
    
    for hero in heroes:
        hero_id = hero["id"]
        hero_name = hero["name"].replace("npc_dota_hero_", "")
        localized_name = hero["localized_name"]
        template = create_ability_template(hero_id, hero_name, localized_name)
        templates.append(template)
        
    return templates

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

def sanitize_name(name):
    """Convert a hero name to a valid file name"""
    name = name.lower()
    name = re.sub(r'[^a-z0-9]', '_', name)
    return name

def collect_hero_abilities(hero, existing_heroes=None):
    """Collect ability information for a hero"""
    hero_id = hero["id"]
    hero_name = hero["name"].replace("npc_dota_hero_", "")
    localized_name = hero["localized_name"]
    
    template = create_ability_template(hero_id, hero_name, localized_name)
    
    print(f"\n=== {localized_name} (ID: {hero_id}) ===")
    
    # Collect information for each ability
    for i, ability in enumerate(template["abilities"]):
        ability_type = "ultimate" if i == 3 else "active"
        print(f"\nAbility {i+1}" + (" (Ultimate)" if i == 3 else ""))
        
        name = input(f"Name: ")
        if name:
            ability["name"] = name
        
        description = input(f"Description: ")
        if description:
            ability["description"] = description
        
        ability_type = input(f"Type (active/passive/ultimate) [{ability_type}]: ") or ability_type
        ability["type"] = ability_type
        
        if ability_type.lower() != "passive":
            behavior = input(f"Behavior (no target/unit target/point target/directional/passive/auto-cast): ")
            if behavior:
                ability["behavior"] = behavior
            
            pierces_immunity = input(f"Pierces immunity (True/False) [False]: ")
            if pierces_immunity.lower() == "true":
                ability["pierces_immunity"] = True
        else:
            ability["behavior"] = "passive"
        
        damage_type = input(f"Damage type (magical/physical/pure/none) [magical]: ")
        if damage_type:
            ability["damage_type"] = damage_type
        
        affects = input(f"Affects (enemies/allies/self) [enemies]: ")
        if affects:
            ability["affects"] = [target.strip() for target in affects.split(',')]
        
        # Special values
        print("Special values (leave blank to skip):")
        while True:
            key = input("  Special value name (leave blank when done): ")
            if not key:
                break
                
            value_str = input(f"  Values for {key} (comma-separated): ")
            if value_str:
                try:
                    values = [float(v.strip()) for v in value_str.split(',')]
                    ability["special_values"][key] = values
                except ValueError:
                    print("  Invalid value format. Should be numbers separated by commas.")
        
        # Cooldown
        levels = 3 if ability_type == "ultimate" else 4
        cooldown_str = input(f"Cooldown values ({levels} levels, comma-separated): ")
        if cooldown_str:
            try:
                cooldown = [float(c.strip()) for c in cooldown_str.split(',')]
                ability["cooldown"] = cooldown[:levels]
            except ValueError:
                print("Invalid cooldown format. Using default values.")
        
        # Mana cost
        mana_cost_str = input(f"Mana cost values ({levels} levels, comma-separated): ")
        if mana_cost_str:
            try:
                mana_cost = [float(m.strip()) for m in mana_cost_str.split(',')]
                ability["mana_cost"] = mana_cost[:levels]
            except ValueError:
                print("Invalid mana cost format. Using default values.")
        
        notes = input(f"Notes: ")
        if notes:
            ability["notes"] = notes
    
    # Check for innate abilities
    has_innate = input("\nDoes this hero have innate abilities? (y/n) [n]: ").lower()
    if has_innate == 'y':
        print("Adding innate ability:")
        innate_ability = {
            "id": hero_id * 100 + 5,
            "name": "",
            "description": "",
            "type": "innate",
            "behavior": "passive",
            "affects": ["self"],
            "special_values": {},
            "notes": ""
        }
        
        name = input(f"Name: ")
        if name:
            innate_ability["name"] = name
        
        description = input(f"Description: ")
        if description:
            innate_ability["description"] = description
        
        affects = input(f"Affects (enemies/allies/self) [self]: ")
        if affects:
            innate_ability["affects"] = [target.strip() for target in affects.split(',')]
        
        # Special values for innate ability
        print("Special values (leave blank to skip):")
        while True:
            key = input("  Special value name (leave blank when done): ")
            if not key:
                break
                
            value_str = input(f"  Values for {key} (comma-separated): ")
            if value_str:
                try:
                    values = [float(v.strip()) for v in value_str.split(',')]
                    innate_ability["special_values"][key] = values
                except ValueError:
                    print("  Invalid value format. Should be numbers separated by commas.")
        
        notes = input(f"Notes: ")
        if notes:
            innate_ability["notes"] = notes
            
        template["innate_abilities"].append(innate_ability)
    
    return template

def combine_all_hero_files():
    """Combine all hero ability files into one file"""
    try:
        all_heroes = {"heroes": []}
        
        # Find all hero ability files
        pattern = os.path.join(OUTPUT_DIR, "*_abilities.json")
        import glob
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

def get_existing_heroes():
    """Get existing heroes from the combined file"""
    try:
        if os.path.exists(COMBINED_FILE):
            with open(COMBINED_FILE, 'r') as f:
                data = json.load(f)
                return {hero["id"]: hero for hero in data["heroes"]}
        return {}
    except Exception as e:
        print(f"Error getting existing heroes: {e}")
        return {}

def main():
    """Main function"""
    print("Dota 2 Hero Ability Creator")
    print("==========================")
    
    heroes = load_heroes()
    if not heroes:
        print("No heroes found. Exiting.")
        return
    
    existing_heroes = get_existing_heroes()
    print(f"Loaded {len(heroes)} heroes from heroes.json")
    print(f"Found {len(existing_heroes)} existing hero ability files")
    
    while True:
        print("\nOptions:")
        print("1. Generate templates for all heroes")
        print("2. Manually input abilities for a hero")
        print("3. Combine all hero files")
        print("4. Exit")
        
        choice = input("Enter choice: ")
        
        if choice == "1":
            templates = generate_all_templates()
            count = 0
            for template in templates:
                hero_name = template["name"]
                file_name = f"{hero_name}_abilities.json"
                
                # Skip if file already exists
                file_path = os.path.join(OUTPUT_DIR, file_name)
                if os.path.exists(file_path):
                    print(f"Skipping {file_name} (already exists)")
                    continue
                    
                if save_hero_data(template, file_name):
                    print(f"Created template for {template['localized_name']}")
                    count += 1
            
            print(f"Generated {count} new templates")
            
        elif choice == "2":
            print("\nAvailable heroes:")
            for i, hero in enumerate(sorted(heroes, key=lambda h: h["localized_name"])):
                status = "✓" if hero["id"] in existing_heroes else " "
                print(f"{i+1:3d}. [{status}] {hero['localized_name']}")
            
            hero_choice = input("\nEnter hero number (or name): ")
            selected_hero = None
            
            if hero_choice.isdigit():
                idx = int(hero_choice) - 1
                if 0 <= idx < len(heroes):
                    selected_hero = heroes[idx]
            else:
                # Search by name
                name_lower = hero_choice.lower()
                matches = [h for h in heroes if name_lower in h["localized_name"].lower()]
                if len(matches) == 1:
                    selected_hero = matches[0]
                elif len(matches) > 1:
                    print("\nMultiple matches found:")
                    for i, hero in enumerate(matches):
                        print(f"{i+1}. {hero['localized_name']}")
                    sub_choice = input("Select hero number: ")
                    if sub_choice.isdigit():
                        idx = int(sub_choice) - 1
                        if 0 <= idx < len(matches):
                            selected_hero = matches[idx]
            
            if selected_hero:
                if selected_hero["id"] in existing_heroes:
                    overwrite = input(f"{selected_hero['localized_name']} already has abilities defined. Overwrite? (y/n) [n]: ")
                    if overwrite.lower() != 'y':
                        continue
                
                hero_data = collect_hero_abilities(selected_hero)
                file_name = f"{hero_data['name']}_abilities.json"
                
                if save_hero_data(hero_data, file_name):
                    print(f"Saved abilities for {hero_data['localized_name']}")
                    
                # Ask if user wants to combine files
                combine = input("Combine all hero files? (y/n) [y]: ")
                if combine.lower() != 'n':
                    combine_all_hero_files()
            else:
                print("Hero not found")
                
        elif choice == "3":
            combine_all_hero_files()
            
        elif choice == "4":
            print("Exiting")
            break
            
        else:
            print("Invalid choice")

if __name__ == "__main__":
    main()