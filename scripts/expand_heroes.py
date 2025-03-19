#!/usr/bin/env python3
"""
Expand the hero ability generator to create templates for more heroes
"""
import json
import os
from manual_hero_ability_creator import create_hero_template

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
BASIC_HEROES = [
    {"name": "Antimage", "id": 1, "display": "Anti-Mage"},
    {"name": "Axe", "id": 2},
    {"name": "Bane", "id": 3},
    {"name": "Bloodseeker", "id": 4},
    {"name": "CrystalMaiden", "id": 5, "display": "Crystal Maiden"},
    {"name": "DrowRanger", "id": 6, "display": "Drow Ranger"},
    {"name": "Earthshaker", "id": 7},
    {"name": "Juggernaut", "id": 8},
    {"name": "Mirana", "id": 9},
    {"name": "Morphling", "id": 10},
    {"name": "ShadowFiend", "id": 11, "display": "Shadow Fiend"},
    {"name": "PhantomLancer", "id": 12, "display": "Phantom Lancer"},
    {"name": "Puck", "id": 13},
    {"name": "Pudge", "id": 14},
    {"name": "Razor", "id": 15},
    {"name": "SandKing", "id": 16, "display": "Sand King"},
    {"name": "StormSpirit", "id": 17, "display": "Storm Spirit"},
    {"name": "Sven", "id": 18},
    {"name": "Tiny", "id": 19},
    {"name": "VengefulSpirit", "id": 20, "display": "Vengeful Spirit"}
]

# Additional popular heroes
ADDITIONAL_HEROES = [
    {"name": "Windranger", "id": 21},
    {"name": "Zeus", "id": 22},
    {"name": "Kunkka", "id": 23},
    {"name": "Lina", "id": 24},
    {"name": "Lion", "id": 25},
    {"name": "ShadowShaman", "id": 26, "display": "Shadow Shaman"},
    {"name": "Slardar", "id": 27},
    {"name": "Tidehunter", "id": 28},
    {"name": "WitchDoctor", "id": 29, "display": "Witch Doctor"},
    {"name": "Lich", "id": 30},
    {"name": "Riki", "id": 31},
    {"name": "Enigma", "id": 32},
    {"name": "FacelessVoid", "id": 33, "display": "Faceless Void"},
    {"name": "PhantomAssassin", "id": 34, "display": "Phantom Assassin"},
    {"name": "Io", "id": 35},
    {"name": "Invoker", "id": 36}
]

def format_hero_name(hero):
    """Format hero name for display"""
    if "display" in hero:
        return hero["display"]
    else:
        # Convert camelCase to normal name
        name = hero["name"]
        result = name[0]
        for i in range(1, len(name)):
            if name[i].isupper():
                result += " " + name[i]
            else:
                result += name[i]
        return result

def main():
    """Main function"""
    # Combine all heroes
    all_heroes = BASIC_HEROES + ADDITIONAL_HEROES
    
    # Process each hero
    for hero in all_heroes:
        hero_name = format_hero_name(hero)
        try:
            create_hero_template(hero_name, hero["id"])
        except Exception as e:
            print(f"Error creating template for {hero_name}: {e}")
    
    print(f"Created templates for {len(all_heroes)} heroes")

if __name__ == "__main__":
    main()