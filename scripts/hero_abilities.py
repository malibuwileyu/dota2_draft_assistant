#!/usr/bin/env python3
"""
Hero Abilities Data Collection Script for Dota 2 Draft Assistant

This script collects hero abilities data from external sources and formats it
into the structure needed by the application for AI-based hero synergy and counter analysis.

Usage:
    python hero_abilities.py [--output OUTPUT] [--heroes HEROES_FILE]

Options:
    --output OUTPUT        Output directory for JSON files [default: src/main/resources/data/abilities]
    --heroes HEROES_FILE   Path to heroes.json file [default: src/main/resources/data/heroes.json]
"""

import argparse
import json
import os
import re
import sys
from typing import Dict, List, Any, Optional, Tuple
import requests
from pathlib import Path

# Configuration
DEFAULT_OUTPUT_DIR = "src/main/resources/data/abilities"
DEFAULT_HEROES_FILE = "src/main/resources/data/heroes.json"
HERO_ABILITIES_URL_TEMPLATE = "https://api.opendota.com/api/constants/abilities"
HERO_DATA_URL = "https://api.opendota.com/api/constants/heroes"

# Type aliases
HeroDict = Dict[str, Any]
AbilityDict = Dict[str, Any]

def parse_arguments() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description='Collect hero abilities data for Dota 2 Draft Assistant')
    parser.add_argument('--output', default=DEFAULT_OUTPUT_DIR, 
                        help=f'Output directory for JSON files [default: {DEFAULT_OUTPUT_DIR}]')
    parser.add_argument('--heroes', default=DEFAULT_HEROES_FILE,
                        help=f'Path to heroes.json file [default: {DEFAULT_HEROES_FILE}]')
    return parser.parse_args()

def ensure_directory(directory: str) -> None:
    """Ensure the specified directory exists."""
    os.makedirs(directory, exist_ok=True)

def fetch_json(url: str) -> Dict[str, Any]:
    """Fetch JSON data from URL."""
    print(f"Fetching data from {url}...")
    try:
        response = requests.get(url)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"Error fetching data: {e}")
        sys.exit(1)

def load_heroes_file(file_path: str) -> List[HeroDict]:
    """Load heroes from JSON file."""
    try:
        with open(file_path, 'r') as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError) as e:
        print(f"Error loading heroes file: {e}")
        sys.exit(1)

def process_ability_data(ability_data: Dict[str, Any], ability_id: int) -> AbilityDict:
    """Process raw ability data into our standardized format."""
    # Default ability structure
    processed_ability = {
        "id": ability_id,
        "name": ability_data.get("dname", "Unknown"),
        "description": ability_data.get("desc", ""),
        "type": determine_ability_type(ability_data),
        "behavior": extract_behavior(ability_data),
        "damage_type": extract_damage_type(ability_data),
        "affects": extract_affects(ability_data),
        "special_values": extract_special_values(ability_data),
        "cooldown": extract_array_value(ability_data, "cd"),
        "mana_cost": extract_array_value(ability_data, "mc"),
        "notes": ability_data.get("notes", "")
    }
    
    return processed_ability

def determine_ability_type(ability_data: Dict[str, Any]) -> str:
    """Determine if ability is active, passive, toggle, etc."""
    behavior = ability_data.get("behavior", "")
    if isinstance(behavior, str):
        if "PASSIVE" in behavior:
            return "passive"
        elif "TOGGLE" in behavior:
            return "toggle"
        else:
            return "active"
    return "active"  # Default to active if unclear

def extract_behavior(ability_data: Dict[str, Any]) -> str:
    """Extract ability behavior/targeting."""
    behavior = ability_data.get("behavior", "")
    if isinstance(behavior, str):
        if "POINT" in behavior:
            return "point target"
        elif "UNIT_TARGET" in behavior:
            return "unit target"
        elif "NO_TARGET" in behavior:
            return "no target"
        elif "PASSIVE" in behavior:
            return "passive"
        elif "TOGGLE" in behavior:
            return "toggle"
    return "unknown"

def extract_damage_type(ability_data: Dict[str, Any]) -> str:
    """Extract ability damage type."""
    damage_type = ability_data.get("dmg_type", "")
    if damage_type == "DAMAGE_TYPE_PHYSICAL":
        return "physical"
    elif damage_type == "DAMAGE_TYPE_MAGICAL":
        return "magical"
    elif damage_type == "DAMAGE_TYPE_PURE":
        return "pure"
    return "none"

def extract_affects(ability_data: Dict[str, Any]) -> List[str]:
    """Extract what the ability affects (enemies, allies, self)."""
    affects = []
    target_team = ability_data.get("target_team", "")
    
    if isinstance(target_team, str):
        if "ENEMY" in target_team:
            affects.append("enemies")
        if "FRIENDLY" in target_team or "ALLIED" in target_team:
            affects.append("allies")
        if "SELF" in target_team or (not target_team and determine_ability_type(ability_data) == "passive"):
            affects.append("self")
    
    # Default to self if nothing else is specified
    if not affects:
        affects.append("self")
    
    return affects

def extract_array_value(ability_data: Dict[str, Any], key: str) -> List[float]:
    """Extract array values (cooldown, mana cost, etc.)."""
    value = ability_data.get(key, 0)
    
    # Handle different formats
    if isinstance(value, list):
        return value
    elif isinstance(value, (int, float)):
        return [float(value)]
    elif isinstance(value, str) and '/' in value:
        return [float(x.strip()) for x in value.split('/') if x.strip()]
    
    return [0]  # Default

def extract_special_values(ability_data: Dict[str, Any]) -> Dict[str, List[float]]:
    """Extract special values from ability data."""
    special_values = {}
    
    if "special_values" in ability_data and isinstance(ability_data["special_values"], list):
        for special in ability_data["special_values"]:
            if not isinstance(special, dict):
                continue
                
            name = special.get("name", "").lower().replace("special_", "")
            value = special.get("value", 0)
            
            # Handle different value formats
            if isinstance(value, list):
                special_values[name] = value
            elif isinstance(value, (int, float)):
                special_values[name] = [float(value)]
            elif isinstance(value, str) and '/' in value:
                special_values[name] = [float(x.strip()) for x in value.split('/') if x.strip()]
    
    return special_values

def generate_innate_abilities(hero_data: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Generate innate abilities based on hero attributes and traits."""
    innate_abilities = []
    
    # Example: Generate an innate ability based on the hero's attack type
    attack_type = hero_data.get("attack_type", "").lower()
    if attack_type == "melee":
        innate_abilities.append({
            "id": 900000 + hero_data.get("id", 0),
            "name": "Melee Attack",
            "description": "This hero attacks enemies at close range.",
            "type": "innate",
            "behavior": "passive",
            "damage_type": "physical",
            "affects": ["self"],
            "special_values": {
                "attack_range": [150]
            }
        })
    elif attack_type == "ranged":
        # Get the attack range
        attack_range = hero_data.get("attack_range", 600)
        innate_abilities.append({
            "id": 900000 + hero_data.get("id", 0),
            "name": "Ranged Attack",
            "description": f"This hero attacks enemies from a distance of {attack_range} units.",
            "type": "innate",
            "behavior": "passive",
            "damage_type": "physical",
            "affects": ["self"],
            "special_values": {
                "attack_range": [attack_range]
            }
        })
    
    # You can add more innate abilities based on other hero attributes
    # like movement speed, base stats, etc.
    
    return innate_abilities

def generate_hero_abilities(hero: HeroDict, abilities_data: Dict[str, Any], hero_ability_mapping: Dict[str, List[str]]) -> Dict[str, Any]:
    """Generate complete hero abilities data for a single hero."""
    hero_name = hero.get("name", "").replace("npc_dota_hero_", "")
    hero_id = hero.get("id", 0)
    
    # Get abilities for this hero from the mapping
    hero_ability_names = hero_ability_mapping.get(hero_name, [])
    abilities = []
    ability_id_counter = hero_id * 100 + 1
    
    for ability_name in hero_ability_names:
        if ability_name in abilities_data:
            ability_data = abilities_data[ability_name]
            processed_ability = process_ability_data(ability_data, ability_id_counter)
            abilities.append(processed_ability)
            ability_id_counter += 1
    
    # Generate innate abilities
    innate_abilities = generate_innate_abilities(hero)
    
    # Compile the complete hero abilities data
    hero_abilities_data = {
        "id": hero_id,
        "name": hero_name,
        "localized_name": hero.get("localized_name", hero_name.capitalize()),
        "abilities": abilities,
        "innate_abilities": innate_abilities,
        "synergies": [],  # To be filled in later based on analysis
        "counters": []    # To be filled in later based on analysis
    }
    
    return hero_abilities_data

def save_hero_abilities(hero_abilities_data: Dict[str, Any], output_dir: str) -> None:
    """Save hero abilities data to JSON file."""
    hero_name = hero_abilities_data["name"]
    file_path = os.path.join(output_dir, f"{hero_name}_abilities.json")
    
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(hero_abilities_data, f, indent=2)
    
    print(f"Saved abilities for {hero_abilities_data['localized_name']} to {file_path}")

def save_all_heroes_abilities(all_heroes_abilities: List[Dict[str, Any]], output_dir: str) -> None:
    """Save all heroes abilities to a single JSON file."""
    file_path = os.path.join(output_dir, "hero_abilities.json")
    
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump({"heroes": all_heroes_abilities}, f, indent=2)
    
    print(f"Saved all hero abilities to {file_path}")

def main() -> None:
    """Main function to orchestrate the data collection and processing."""
    args = parse_arguments()
    ensure_directory(args.output)
    
    # Load our hero data
    heroes = load_heroes_file(args.heroes)
    
    # Fetch abilities data
    abilities_data = fetch_json(HERO_ABILITIES_URL_TEMPLATE)
    hero_data = fetch_json(HERO_DATA_URL)
    
    # Create mapping of hero names to their abilities
    hero_ability_mapping = {}
    for hero_name, hero_info in hero_data.items():
        if isinstance(hero_info, dict) and "abilities" in hero_info:
            clean_name = hero_name.replace("npc_dota_hero_", "")
            hero_ability_mapping[clean_name] = hero_info["abilities"]
    
    all_heroes_abilities = []
    
    for hero in heroes:
        hero_abilities_data = generate_hero_abilities(hero, abilities_data, hero_ability_mapping)
        all_heroes_abilities.append(hero_abilities_data)
        save_hero_abilities(hero_abilities_data, args.output)
    
    save_all_heroes_abilities(all_heroes_abilities, args.output)
    print(f"Successfully processed abilities for {len(all_heroes_abilities)} heroes.")

if __name__ == "__main__":
    main()