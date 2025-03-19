#!/usr/bin/env python3
"""
Parse the Axe abilities from HTML file, reading only the first 1000 lines
Creates a complete abilities JSON file compatible with the application.
Also extracts cooldown and mana cost information from the HTML.
"""
import json
import sys
import re

def main():
    # Check if file path is provided
    if len(sys.argv) < 2:
        print("Usage: python parse_axe_abilities.py <html_file>")
        sys.exit(1)

    html_file = sys.argv[1]
    
    # Read only the first 1000 lines of the HTML file - we need more to find cooldown and mana costs
    with open(html_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()[:1000]  # Read more lines to include cooldown and mana cost data
        html_content = ''.join(lines)
    
    # Look for the abilities section specifically in lines 105-115
    abilities_found = False
    for i, line in enumerate(lines):
        if i >= 105 and i <= 115 and 'data-target="#Abilities"' in line:
            print("Found abilities section at line", i+1)
            abilities_found = True
            
            # Get the next 5 lines which should contain the ability names
            ability_lines = lines[i+1:i+6]
            
            # Extract ability names from the lines
            ability_names = []
            for ability_line in ability_lines:
                match = re.search(r'data-target="#([^"]+)".*?<span class="toctext">(.*?)</span>', ability_line)
                if match:
                    ability_id = match.group(1)
                    ability_name = match.group(2)
                    if "Aghanim" not in ability_name and "Talent" not in ability_name:
                        ability_names.append(ability_name)
            
            print(f"Found {len(ability_names)} ability names:")
            for name in ability_names:
                print(f"- {name}")
            break
    
    if not abilities_found:
        print("Could not find abilities section with data-target='#Abilities'")
    
    # Find innate ability specifically around line 96-98
    innate_found = False
    for i, line in enumerate(lines):
        if i >= 95 and i <= 100 and 'data-target="#Innate"' in line:
            print("\nFound innate section at line", i+1)
            innate_found = True
            
            # Look for Coat of Blood in the next few lines
            for j in range(i+1, i+5):
                if j < len(lines):
                    coat_match = re.search(r'data-target="#Coat_of_Blood".*?<span class="toctext">(.*?)</span>', lines[j])
                    if coat_match:
                        innate_name = coat_match.group(1)
                        print(f"Innate ability name: {innate_name}")
                        break
            break
    
    if not innate_found:
        print("\nCould not find innate section")
        
    # Print lines around important data
    print("\nShowing relevant portions of HTML:")
    for i, line in enumerate(lines):
        if 'data-target="#Abilities"' in line or 'data-target="#Innate"' in line:
            print(f"\nLine {i+1} (important):")
            # Show 5 lines before and after for context
            start = max(0, i-5)
            end = min(len(lines), i+6)
            for j in range(start, end):
                prefix = "→ " if j == i else "  "
                print(f"{prefix}{j+1}: {lines[j]}")
    
    # Create a complete abilities JSON file
    if abilities_found:
        hero_data = {
            "heroes": [
                {
                    "id": 2,  # Axe's ID
                    "name": "axe",
                    "localized_name": "Axe",
                    "abilities": [],
                    "innate_abilities": []
                }
            ]
        }
        
        # Add abilities
        for idx, ability_name in enumerate(ability_names):
            ability_id = 2 * 100 + idx + 1  # Starting with 201
            
            # Default values based on Axe's known abilities
            cooldown = [0, 0, 0, 0]
            mana_cost = [0, 0, 0, 0]
            behavior = "active"
            damage_type = "physical"
            description = f"{ability_name} - Description requires full HTML parsing"
            
            # Customize for known abilities and try to find cooldown and mana cost in HTML
            if "Berserker's Call" in ability_name:
                # Try to find Berserker's Call cooldown
                berserkers_call_section = re.search(r'<span class="mw-headline" id="Berserker.*?</span>.*?spellcost_value.*?Cooldown.*?spellcost_value[^>]*>([^<]+)</div>', html_content, re.DOTALL)
                if berserkers_call_section:
                    cooldown_text = berserkers_call_section.group(1).strip()
                    print(f"Found Berserker's Call cooldown: {cooldown_text}")
                    try:
                        cooldown = [int(x) for x in cooldown_text.split('/')]
                    except:
                        cooldown = [17, 15, 13, 11]  # Default from known values
                else:
                    cooldown = [17, 15, 13, 11]  # Default from known values
                    
                # Try to find Berserker's Call mana cost
                mana_section = re.search(r'<span class="mw-headline" id="Berserker.*?</span>.*?Mana Cost.*?spellcost_value[^>]*>([^<]+)</div>', html_content, re.DOTALL)
                if mana_section:
                    mana_text = mana_section.group(1).strip()
                    print(f"Found Berserker's Call mana cost: {mana_text}")
                    try:
                        mana_cost = [int(x) for x in mana_text.split('/')]
                    except:
                        mana_cost = [80, 90, 100, 110]  # Default from known values
                else:
                    mana_cost = [80, 90, 100, 110]  # Default from known values
                    
                damage_type = "none"
                behavior = "no target"
                description = "Axe taunts nearby enemies, forcing them to attack him while granting bonus armor."
            elif "Battle Hunger" in ability_name:
                # Try to find Battle Hunger cooldown
                battle_hunger_section = re.search(r'<span class="mw-headline" id="Battle_Hunger">.*?spellcost_value.*?Cooldown.*?spellcost_value[^>]*>([^<]+)</div>', html_content, re.DOTALL)
                if battle_hunger_section:
                    cooldown_text = battle_hunger_section.group(1).strip()
                    print(f"Found Battle Hunger cooldown: {cooldown_text}")
                    try:
                        cooldown = [int(x) for x in cooldown_text.split('/')]
                    except:
                        cooldown = [20, 15, 10, 5]  # Default from known values
                else:
                    cooldown = [20, 15, 10, 5]  # Default from known values
                    
                # Try to find Battle Hunger mana cost
                mana_section = re.search(r'<span class="mw-headline" id="Battle_Hunger">.*?Mana Cost.*?spellcost_value[^>]*>([^<]+)</div>', html_content, re.DOTALL)
                if mana_section:
                    mana_text = mana_section.group(1).strip()
                    print(f"Found Battle Hunger mana cost: {mana_text}")
                    try:
                        mana_cost = [int(x) for x in mana_text.split('/')]
                    except:
                        mana_cost = [50, 60, 70, 80]  # Default from known values
                else:
                    mana_cost = [50, 60, 70, 80]  # Default from known values
                
                damage_type = "magical"
                behavior = "unit target"
                description = "Enrages an enemy, dealing damage over time until they kill a unit or the duration ends. Slows their movement speed."
            elif "Counter Helix" in ability_name:
                cooldown = [0.45, 0.4, 0.35, 0.3]
                mana_cost = [0, 0, 0, 0]
                damage_type = "pure"
                behavior = "passive"
                description = "When attacked, Axe has a chance to spin, dealing pure damage to nearby enemies."
            elif "Culling Blade" in ability_name:
                cooldown = [75, 65, 55]
                mana_cost = [60, 120, 180]
                damage_type = "magical"
                behavior = "unit target"
                description = "Axe executes an enemy below a health threshold, dealing massive damage. If successful, grants Axe and nearby allies bonus movement speed."
            
            ability = {
                "id": ability_id,
                "name": ability_name,
                "description": description,
                "type": "passive" if "Counter Helix" in ability_name else "active",
                "pierces_immunity": "Berserker's Call" in ability_name or "Culling Blade" in ability_name,
                "behavior": behavior,
                "damage_type": damage_type,
                "affects": ["enemies"] if "Counter Helix" in ability_name else ["enemies", "self"],
                "special_values": {},
                "cooldown": cooldown,
                "mana_cost": mana_cost,
                "notes": ""
            }
            
            hero_data["heroes"][0]["abilities"].append(ability)
        
        # Add innate ability if found
        if innate_found:
            innate = {
                "id": 2 * 100 + len(ability_names) + 1,  # Next available ID
                "name": "Coat of Blood",
                "description": "Axe permanently gains bonus armor whenever an enemy dies to Culling Blade or within 400 range.",
                "type": "innate",
                "behavior": "passive",
                "affects": ["self"],
                "special_values": {
                    "armor_per_kill": [0.2, 0.3, 0.4, 0.5],
                    "culling_kill_multiplier": [3, 3, 3, 3]
                },
                "notes": "Scales with Culling Blade level. Stacks infinitely."
            }
            hero_data["heroes"][0]["innate_abilities"].append(innate)
        
        # Save to file
        output_file = "parsed_axe_abilities.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(hero_data, f, indent=2)
        
        print(f"\nSaved hero abilities to {output_file}")

if __name__ == "__main__":
    main()