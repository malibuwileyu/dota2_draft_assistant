#!/usr/bin/env python3
"""
Generate Hero Ability Templates for Batch Import in Markdown Format

This script creates template markdown files that can be filled in with ability details
and then imported using the batch_hero_abilities_import.py script.
"""
import json
import os
import sys
import re

# Configuration
HEROES_FILE = "src/main/resources/data/heroes.json"
OUTPUT_DIR = "ability_templates"

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

def sanitize_name(name):
    """Convert a hero name to a valid file name"""
    name = name.lower()
    name = re.sub(r'[^a-z0-9]', '_', name)
    return name

def create_hero_template(hero):
    """Create a markdown template for a hero"""
    hero_id = hero["id"]
    hero_name = hero["localized_name"]
    
    template = [f"# {hero_name}"]
    
    # Regular abilities (3)
    for i in range(1, 4):
        template.append(f"\n## Ability {i}")
        template.append("- Description: ")
        template.append("- Behavior: no target")
        template.append("- Damage Type: magical")
        template.append("- Affects: enemies")
        template.append("- Pierces Immunity: No")
        template.append("- Cooldown: 0, 0, 0, 0")
        template.append("- Mana Cost: 0, 0, 0, 0")
        template.append("- Notes: ")
        template.append("\n### Special Values")
        template.append("- value1: 0, 0, 0, 0")
        template.append("- value2: 0, 0, 0, 0")
    
    # Ultimate ability
    template.append(f"\n## Ability 4 (Ultimate)")
    template.append("- Description: ")
    template.append("- Behavior: no target")
    template.append("- Damage Type: magical")
    template.append("- Affects: enemies")
    template.append("- Pierces Immunity: No")
    template.append("- Cooldown: 0, 0, 0")
    template.append("- Mana Cost: 0, 0, 0")
    template.append("- Notes: ")
    template.append("\n### Special Values")
    template.append("- value1: 0, 0, 0")
    template.append("- value2: 0, 0, 0")
    
    # Mark the end of hero section
    template.append("\n---")
    
    return "\n".join(template)

def generate_templates(heroes, output_mode="individual"):
    """Generate templates for heroes"""
    if output_mode == "individual":
        for hero in heroes:
            template = create_hero_template(hero)
            file_name = f"{sanitize_name(hero['localized_name'])}_template.md"
            file_path = os.path.join(OUTPUT_DIR, file_name)
            
            with open(file_path, 'w') as f:
                f.write(template)
            
            print(f"Created template for {hero['localized_name']}")
    else:
        # All heroes in a single file
        templates = []
        for hero in heroes:
            template = create_hero_template(hero)
            templates.append(template)
        
        file_path = os.path.join(OUTPUT_DIR, "all_hero_templates.md")
        with open(file_path, 'w') as f:
            f.write("\n\n".join(templates))
        
        print(f"Created combined template file with {len(heroes)} heroes")

def main():
    """Main function"""
    print("Dota 2 Hero Ability Template Generator")
    print("=====================================")
    
    heroes = load_heroes()
    if not heroes:
        print("No heroes found. Exiting.")
        return
    
    print(f"Loaded {len(heroes)} heroes from heroes.json")
    
    print("\nOptions:")
    print("1. Generate templates for all heroes (separate files)")
    print("2. Generate templates for all heroes (single file)")
    print("3. Generate template for specific hero")
    print("4. Exit")
    
    choice = input("Enter choice: ")
    
    if choice == "1":
        generate_templates(heroes, "individual")
        
    elif choice == "2":
        generate_templates(heroes, "combined")
        
    elif choice == "3":
        print("\nAvailable heroes:")
        for i, hero in enumerate(sorted(heroes, key=lambda h: h["localized_name"])):
            print(f"{i+1:3d}. {hero['localized_name']}")
        
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
            template = create_hero_template(selected_hero)
            file_name = f"{sanitize_name(selected_hero['localized_name'])}_template.md"
            file_path = os.path.join(OUTPUT_DIR, file_name)
            
            with open(file_path, 'w') as f:
                f.write(template)
            
            print(f"Created template for {selected_hero['localized_name']} at {file_path}")
            
            # Also print to console
            print("\nTemplate:")
            print("=========")
            print(template)
        else:
            print("Hero not found")
    
    elif choice == "4":
        print("Exiting")
    
    else:
        print("Invalid choice")

if __name__ == "__main__":
    main()