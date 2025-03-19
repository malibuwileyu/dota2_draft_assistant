#!/usr/bin/env python3
"""
Generate hero ability templates based on existing files.
This creates skeleton JSON files for all heroes, which can then be manually populated.
"""
import os
import json

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
TEMPLATE_FILES = [
    "axe_abilities.json", 
    "invoker_abilities.json", 
    "rubick_abilities.json"
]

# Hero name mappings - edit this list to add all heroes
# Key: Hero display name, Value: Filename format (lowercase with underscores)
HEROES = {
    # Heroes with manually created files
    "Axe": "axe",
    "Invoker": "invoker",
    "Rubick": "rubick",
    
    # Core heroes
    "Anti-Mage": "anti_mage",
    "Bloodseeker": "bloodseeker",
    "Crystal Maiden": "crystal_maiden",
    "Drow Ranger": "drow_ranger",
    "Earthshaker": "earthshaker", 
    "Juggernaut": "juggernaut",
    "Mirana": "mirana",
    "Morphling": "morphling",
    "Shadow Fiend": "shadow_fiend",
    "Phantom Lancer": "phantom_lancer",
    "Puck": "puck",
    "Pudge": "pudge",
    "Razor": "razor",
    
    # Support heroes
    "Bane": "bane",
    "Crystal Maiden": "crystal_maiden",
    "Lich": "lich",
    "Lion": "lion",
    "Shadow Shaman": "shadow_shaman",
    "Witch Doctor": "witch_doctor",
    "Zeus": "zeus",
    
    # Additional popular heroes
    "Faceless Void": "faceless_void",
    "Phantom Assassin": "phantom_assassin",
    "Sniper": "sniper",
    "Windranger": "windranger",
    "Io": "io",
    "Tiny": "tiny",
    "Lina": "lina",
    "Queen of Pain": "queen_of_pain",
    "Storm Spirit": "storm_spirit",
    "Enigma": "enigma",
    "Tidehunter": "tidehunter"
}

def load_template():
    """Load a template file to use as base"""
    for template_file in TEMPLATE_FILES:
        try:
            file_path = os.path.join(OUTPUT_DIR, template_file)
            with open(file_path, 'r') as f:
                template = json.load(f)
                print(f"Using {template_file} as template")
                return template
        except:
            continue
    
    raise Exception("No template files found")

def create_skeleton_file(hero_name, filename, template, hero_id):
    """Create a skeleton JSON file for a hero"""
    file_path = os.path.join(OUTPUT_DIR, f"{filename}_abilities.json")
    
    # Check if file already exists with content
    try:
        with open(file_path, 'r') as f:
            existing_data = json.load(f)
            if existing_data["heroes"][0]["abilities"] or existing_data["heroes"][0]["innate_abilities"]:
                print(f"Skipping {hero_name} - file already has abilities defined")
                return
    except:
        pass  # File doesn't exist or is invalid, continue
    
    # Create a skeleton based on template
    skeleton = {
        "heroes": [
            {
                "id": hero_id,
                "name": filename,
                "localized_name": hero_name,
                "abilities": [
                    {
                        "id": hero_id * 100 + 1,
                        "name": "First Ability",
                        "description": "Description goes here",
                        "type": "active",
                        "pierces_immunity": False,
                        "behavior": "unit target",
                        "damage_type": "magical",
                        "affects": ["enemies"],
                        "special_values": {
                            "damage": [100, 175, 250, 325],
                            "duration": [1.5, 2.0, 2.5, 3.0]
                        },
                        "cooldown": [12, 10, 8, 6],
                        "mana_cost": [100, 120, 140, 160],
                        "notes": "Notes about the ability"
                    },
                    {
                        "id": hero_id * 100 + 2,
                        "name": "Second Ability",
                        "description": "Description goes here",
                        "type": "active",
                        "pierces_immunity": False,
                        "behavior": "no target",
                        "damage_type": "none",
                        "affects": ["self", "allies"],
                        "special_values": {
                            "bonus": [10, 20, 30, 40],
                            "duration": [5, 6, 7, 8]
                        },
                        "cooldown": [20, 18, 16, 14],
                        "mana_cost": [80, 90, 100, 110],
                        "notes": "Notes about the ability"
                    },
                    {
                        "id": hero_id * 100 + 3,
                        "name": "Third Ability",
                        "description": "Description goes here",
                        "type": "passive",
                        "pierces_immunity": False,
                        "behavior": "passive",
                        "damage_type": "none",
                        "affects": ["self"],
                        "special_values": {
                            "bonus": [5, 10, 15, 20]
                        },
                        "cooldown": [0, 0, 0, 0],
                        "mana_cost": [0, 0, 0, 0],
                        "notes": "Notes about the ability"
                    },
                    {
                        "id": hero_id * 100 + 4,
                        "name": "Ultimate",
                        "description": "Description goes here",
                        "type": "active",
                        "pierces_immunity": True,
                        "behavior": "point target",
                        "damage_type": "magical",
                        "affects": ["enemies"],
                        "special_values": {
                            "damage": [300, 450, 600],
                            "radius": [400, 450, 500],
                            "duration": [3, 4, 5]
                        },
                        "cooldown": [120, 100, 80],
                        "mana_cost": [200, 300, 400],
                        "notes": "Notes about the ability"
                    }
                ],
                "innate_abilities": [
                    {
                        "id": hero_id * 100 + 5,
                        "name": "Innate Ability",
                        "description": "Description goes here",
                        "type": "innate",
                        "behavior": "passive",
                        "affects": ["self"],
                        "special_values": {
                            "bonus": [5, 10, 15, 20]
                        },
                        "notes": "Notes about the innate ability"
                    }
                ],
                "synergies": [],
                "counters": []
            }
        ]
    }
    
    # Save the file
    with open(file_path, 'w') as f:
        json.dump(skeleton, f, indent=2)
    
    print(f"Created skeleton for {hero_name} at {file_path}")

def main():
    # Load template
    template = load_template()
    
    # Create directory if not exists
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Generate files for all heroes
    for idx, (hero_name, filename) in enumerate(HEROES.items()):
        hero_id = 20 + idx  # Start IDs from 20
        create_skeleton_file(hero_name, filename, template, hero_id)
    
    print(f"Generated templates for {len(HEROES)} heroes")

if __name__ == "__main__":
    main()