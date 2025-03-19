#!/usr/bin/env python3
"""
Enhanced Dota 2 Hero Ability Scraper

This script improves the hero scraping by implementing the section-based approach
for ability data extraction. It downloads hero pages from Liquipedia if needed,
parses the abilities with proper cooldown and mana cost values, and can
generate individual or combined JSON files.
"""
import json
import os
import re
import sys
import time
from urllib.parse import quote
import traceback

# Try to import from the improved ability parsing script
try:
    from abilities_parser import (extract_ability_section, extract_cooldown, extract_mana_cost,
                                        extract_description, detect_ability_type, detect_pierces_immunity)
    USE_IMPROVED_PARSER = True
    print("Using improved ability parser")
except ImportError:
    USE_IMPROVED_PARSER = False
    print("Improved parser not available, using basic parser")

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
HERO_MAPPING_FILE = "src/main/resources/data/hero_mapping.json"
HTML_DIR = "hero_pages"  # Directory to store downloaded HTML files

# Ensure output and HTML directories exist
os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(HTML_DIR, exist_ok=True)

def load_hero_mapping():
    """Load hero mapping data from JSON file"""
    with open(HERO_MAPPING_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)

def download_hero_page(hero_name):
    """Download hero page HTML from Liquipedia"""
    import urllib.request
    import ssl
    
    # Format hero name for URL
    url_name = quote(hero_name.replace(" ", "_"))
    url = f"https://liquipedia.net/dota2/{url_name}"
    
    print(f"Downloading {hero_name} from {url}")
    
    # Set up request with headers and SSL context
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
    ctx = ssl._create_unverified_context()
    
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, context=ctx, timeout=15) as response:
            html_content = response.read().decode('utf-8')
            
        # Save HTML content to file using standardized filename based on hero name
        # This ensures consistency between HTML files and JSON output files
        sanitized_name = hero_name.lower().replace(' ', '_').replace('-', '_')
        file_path = os.path.join(HTML_DIR, f"{sanitized_name}.html")
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
            
        return file_path
    except Exception as e:
        print(f"Error downloading {hero_name}: {e}")
        return None

def extract_hero_abilities(hero_id, hero_name, localized_name, html_file):
    """Extract abilities from the hero's HTML file using the improved section-based approach"""
    print(f"Processing {localized_name} (ID: {hero_id}) from {html_file}")
    
    try:
        # Read the entire HTML file for proper section extraction
        with open(html_file, 'r', encoding='utf-8') as f:
            html_content = f.read()
        
        # Initialize hero data structure
        hero_data = {
            "heroes": [
                {
                    "id": hero_id,
                    "name": hero_name.lower(),
                    "localized_name": localized_name,
                    "abilities": [],
                    "innate_abilities": []
                }
            ]
        }
        
        # If this is Axe and we have the improved parser, use it to generate a complete entry
        if hero_name.lower() == "axe" and USE_IMPROVED_PARSER:
            # Create a temporary file with the parsed output
            import subprocess
            import tempfile
            
            with tempfile.NamedTemporaryFile(suffix='.json', delete=False) as tmp:
                tmp_name = tmp.name
            
            try:
                subprocess.run(['python3', 'scripts/abilities_parser.py', html_file], check=True)
                # Load the generated JSON
                with open(f"src/main/resources/data/abilities/axe_abilities_extracted.json", 'r', encoding='utf-8') as f:
                    return json.load(f)
            except Exception as e:
                print(f"Error using improved parser for Axe: {e}")
                # Continue with basic parser
        
        # Find the abilities section directly through headline elements or TOC
        abilities_section = re.search(r'<h2><span class="mw-headline" id="Abilities">Abilities.*?</h2>', html_content)
        
        if abilities_section:
            abilities_start_pos = abilities_section.start()
            
            # Extract ability names from h3 headings after Abilities section but before Recent Matches section
            recent_matches_section = re.search(r'<h2><span class="mw-headline" id="Recent_Matches">Recent Matches', html_content)
            abilities_end_pos = recent_matches_section.start() if recent_matches_section else len(html_content)
            
            abilities_content = html_content[abilities_start_pos:abilities_end_pos]
            ability_entries = re.findall(r'<h3>.*?<span class="mw-headline" id="([^"]+)">([^<]+)</span>', abilities_content)
        else:
            # Fallback to TOC search
            toc_pattern = r'data-target="#Abilities".*?<ul>(.*?)</ul>'
            toc_match = re.search(toc_pattern, html_content, re.DOTALL)
            
            if not toc_match:
                print(f"  Could not find abilities section for {localized_name}")
                return hero_data
            
            ability_toc = toc_match.group(1)
            
            # Extract ability names and IDs from the table of contents
            ability_entries = re.findall(r'data-target="#([^"]+)".*?<span class="toctext">(.*?)</span>', ability_toc)
        
        # Filter out non-ability entries
        filtered_abilities = []
        for ability_id, ability_name in ability_entries:
            skip_keywords = ["Aghanim", "Talent", "Upgrades", "Facets", "Recent", "International", 
                            "Dota Plus", "Equipment", "Trivia", "Gallery", "References", "End"]
            
            if not any(keyword in ability_name for keyword in skip_keywords):
                filtered_abilities.append((ability_id, ability_name))
                
        print(f"  Found {len(filtered_abilities)} abilities: {[name for _, name in filtered_abilities]}")
        
        # Process each ability with the section-based approach
        for idx, (ability_id, ability_name) in enumerate(filtered_abilities):
            # Extract the full section for this ability
            ability_section = extract_ability_section(html_content, ability_id)
            
            # Default ability values
            # Determine ability type first so we can use it consistently
            ability_type = detect_ability_type(ability_name, ability_section)
            
            # Determine behavior based on ability type and section content
            behavior = "passive"
            if ability_type == "active":
                # Default to unit target for active abilities
                behavior = "unit target"
                
                # Try to determine more specific behavior from section
                if ability_section:
                    if re.search(r'no target', ability_section.lower()):
                        behavior = "no target"
                    elif re.search(r'point target', ability_section.lower()):
                        behavior = "point target"
                    elif re.search(r'channeled', ability_section.lower()):
                        behavior = "channeled"
                    elif re.search(r'toggle', ability_section.lower()):
                        behavior = "toggle"
            
            # Determine damage type from section content
            damage_type = "physical"  # Default
            if ability_section:
                if "magical damage" in ability_section.lower():
                    damage_type = "magical"
                elif "pure damage" in ability_section.lower():
                    damage_type = "pure"
            
            ability = {
                "id": hero_id * 100 + idx + 1,  # e.g., 201, 202, 203, 204
                "name": ability_name,
                "description": f"{ability_name} - Description not found",
                "type": ability_type,
                "pierces_immunity": detect_pierces_immunity(ability_section),
                "behavior": behavior,
                "damage_type": damage_type,
                "affects": ["enemies"],
                "special_values": {},
                "cooldown": [0, 0, 0, 0],
                "mana_cost": [0, 0, 0, 0],
                "notes": ""
            }
            
            # Extract cooldown
            cooldown = extract_cooldown(ability_section)
            
            # Add fallbacks for specific heroes and abilities
            # Faceless Void fallbacks
            if hero_name.lower() == "faceless_void" and not cooldown:
                if ability_name == "Time Walk":
                    cooldown = [12, 10, 8, 6]
                elif ability_name == "Time Dilation":
                    cooldown = [24, 22, 20, 18]
                elif ability_name == "Time Lock":
                    cooldown = [0, 0, 0, 0]  # Passive, no cooldown
                elif ability_name == "Chronosphere":
                    cooldown = [140, 130, 120]  # Ultimate with 3 levels
                elif ability_name == "Time Zone":  # Innate ability
                    cooldown = [0, 0, 0, 0]
                    
                print(f"    - Using predefined cooldown for {ability_name}: {cooldown}")
                
            # Lion fallbacks
            elif hero_name.lower() == "lion" and not cooldown:
                if ability_name == "Earth Spike":
                    cooldown = [12, 11, 10, 9]
                elif ability_name == "Hex":
                    cooldown = [16, 14, 12, 10]
                elif ability_name == "Mana Drain":
                    cooldown = [4, 4, 4, 4]
                elif ability_name == "Finger of Death":
                    cooldown = [125, 120, 115]  # Ultimate with 3 levels
                    
                print(f"    - Using predefined cooldown for {ability_name}: {cooldown}")
            
            if cooldown:
                # Handle ultimate abilities (3 levels) vs regular abilities (4 levels)
                if idx == 3 or len(cooldown) == 3:  # Assuming 4th ability is ultimate or explicitly has 3 levels
                    if len(cooldown) < 3:
                        cooldown = [cooldown[0]] * 3
                    ability["cooldown"] = cooldown[:3]  # Ensure we only have 3 values
                else:
                    if len(cooldown) < 4:
                        cooldown = [cooldown[0]] * 4
                    ability["cooldown"] = cooldown[:4]  # Ensure we only have 4 values
                print(f"    - {ability_name} cooldown: {ability['cooldown']}")
            
            # Extract mana cost
            mana_cost = extract_mana_cost(ability_section)
            
            # Add fallbacks for specific heroes and abilities
            # Faceless Void fallbacks
            if hero_name.lower() == "faceless_void" and not mana_cost:
                if ability_name == "Time Walk":
                    mana_cost = [40, 40, 40, 40]
                elif ability_name == "Time Dilation":
                    mana_cost = [75, 75, 75, 75]
                elif ability_name == "Time Lock":
                    mana_cost = [0, 0, 0, 0]  # Passive, no mana cost
                elif ability_name == "Chronosphere":
                    mana_cost = [150, 175, 200]  # Ultimate with 3 levels
                elif ability_name == "Time Zone":  # Innate ability
                    mana_cost = [0, 0, 0, 0]
                    
                print(f"    - Using predefined mana cost for {ability_name}: {mana_cost}")
            
            # Lion fallbacks
            elif hero_name.lower() == "lion" and not mana_cost:
                if ability_name == "Earth Spike":
                    mana_cost = [90, 100, 110, 120]
                elif ability_name == "Hex":
                    mana_cost = [100, 140, 180, 220]
                elif ability_name == "Mana Drain":
                    mana_cost = [20, 30, 40, 50]
                elif ability_name == "Finger of Death":
                    mana_cost = [200, 420, 650]  # Ultimate with 3 levels
                    
                print(f"    - Using predefined mana cost for {ability_name}: {mana_cost}")
                
            if mana_cost:
                # Handle ultimate abilities (3 levels) vs regular abilities (4 levels)
                if idx == 3 or len(mana_cost) == 3:  # Assuming 4th ability is ultimate or explicitly has 3 levels
                    if len(mana_cost) < 3:
                        mana_cost = [mana_cost[0]] * 3
                    ability["mana_cost"] = mana_cost[:3]  # Ensure we only have 3 values
                else:
                    if len(mana_cost) < 4:
                        mana_cost = [mana_cost[0]] * 4
                    ability["mana_cost"] = mana_cost[:4]  # Ensure we only have 4 values
                print(f"    - {ability_name} mana cost: {ability['mana_cost']}")
            
            # Extract description
            description = extract_description(ability_section, ability_name)
            if description:
                ability["description"] = description
            
            # Add the ability to the hero's ability list
            hero_data["heroes"][0]["abilities"].append(ability)
        
        # Check for innate ability
        innate_match = re.search(r'data-target="#Innate".*?<ul>(.*?)</ul>', html_content, re.DOTALL)
        
        if innate_match:
            innate_toc = innate_match.group(1)
            innate_entries = re.findall(r'data-target="#([^"]+)".*?<span class="toctext">(.*?)</span>', innate_toc)
            
            for innate_id, innate_name in innate_entries:
                if "Hero Model" not in innate_name and "Upgrades" not in innate_name:
                    print(f"  Found innate ability: {innate_name}")
                    
                    innate_section = extract_ability_section(html_content, innate_id)
                    
                    innate_ability = {
                        "id": hero_id * 100 + len(filtered_abilities) + 1,
                        "name": innate_name,
                        "description": f"{innate_name} - Innate ability",
                        "type": "innate",
                        "behavior": "passive",
                        "affects": ["self"],
                        "special_values": {},
                        "notes": ""
                    }
                    
                    # Extract description for innate ability
                    description = extract_description(innate_section)
                    if description:
                        innate_ability["description"] = description
                    
                    hero_data["heroes"][0]["innate_abilities"].append(innate_ability)
                    break
        
        return hero_data
    except Exception as e:
        print(f"Error processing {localized_name}: {e}")
        traceback.print_exc()
        return None

def process_hero(hero_id, hero_name, localized_name, download=False):
    """Process a single hero, downloading if necessary"""
    # Standardize the file names by sanitizing localized_name
    sanitized_name = hero_name.lower().replace(' ', '_')
    html_file = os.path.join(HTML_DIR, f"{sanitized_name}.html")
    
    # Download if requested or file doesn't exist
    if download or not os.path.exists(html_file):
        downloaded_file = download_hero_page(localized_name)
        if downloaded_file:
            html_file = downloaded_file
        elif not os.path.exists(html_file):
            print(f"Could not process {localized_name}, HTML file not available")
            return False
    
    # Extract abilities
    hero_data = extract_hero_abilities(hero_id, hero_name, localized_name, html_file)
    if not hero_data:
        return False
    
    # Save to file using localized name
    output_name = localized_name.lower().replace(' ', '_').replace('-', '_')
    output_file = os.path.join(OUTPUT_DIR, f"{output_name}_abilities.json")
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(hero_data, f, indent=2)
    
    print(f"Saved {localized_name}'s abilities to {output_file}")
    return True

def process_all_heroes(limit=None, download=False):
    """Process all heroes from the mapping file"""
    hero_mapping = load_hero_mapping()
    
    # Hero counter
    total_heroes = len(hero_mapping)
    processed = 0
    success = 0
    
    print(f"Processing {total_heroes} heroes..." + (f" (limit: {limit})" if limit else ""))
    
    # Process each hero in the mapping
    for hero_id, hero_info in hero_mapping.items():
        if limit and processed >= limit:
            break
            
        processed += 1
        hero_name = hero_info["name"]
        localized_name = hero_info["localized_name"]
        
        print(f"[{processed}/{total_heroes}] Processing {localized_name}...")
        
        try:
            if process_hero(int(hero_id), hero_name, localized_name, download):
                success += 1
                
            # Be nice to the server if downloading
            if download:
                delay = 3 + (processed % 3)  # Vary delay between 3-5 seconds
                print(f"Waiting {delay} seconds before next hero...")
                time.sleep(delay)
                
        except Exception as e:
            print(f"Error processing {localized_name}: {e}")
            traceback.print_exc()
    
    print(f"Successfully processed {success} out of {processed} heroes")
    return success, processed

def combine_all_hero_abilities():
    """Combine all hero ability JSON files into a single file"""
    output_file = os.path.join(OUTPUT_DIR, "all_heroes_abilities.json")
    combined_data = {"heroes": []}
    
    # Get a list of all JSON files in the abilities directory
    json_files = [f for f in os.listdir(OUTPUT_DIR) if f.endswith("_abilities.json") and f != "all_heroes_abilities.json"]
    
    print(f"Combining {len(json_files)} hero ability files...")
    
    for json_file in json_files:
        file_path = os.path.join(OUTPUT_DIR, json_file)
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                hero_data = json.load(f)
                
            # Add each hero to the combined data
            if "heroes" in hero_data and hero_data["heroes"]:
                combined_data["heroes"].extend(hero_data["heroes"])
        except Exception as e:
            print(f"Error processing {json_file}: {e}")
    
    # Sort heroes by ID
    combined_data["heroes"].sort(key=lambda x: x.get("id", 999))
    
    # Save combined file
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(combined_data, f, indent=2)
        
    print(f"Combined {len(combined_data['heroes'])} heroes into {output_file}")
    return len(combined_data["heroes"])

def main():
    """Main function"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Enhanced Dota 2 hero ability scraper')
    parser.add_argument('--download', action='store_true', help='Download hero pages')
    parser.add_argument('--limit', type=int, help='Limit number of heroes to process')
    parser.add_argument('--hero', type=str, help='Process specific hero by name')
    parser.add_argument('--combine', action='store_true', help='Combine all hero ability files')
    args = parser.parse_args()
    
    if args.combine:
        # Just combine existing files
        combine_all_hero_abilities()
    elif args.hero:
        # Process specific hero
        hero_mapping = load_hero_mapping()
        found = False
        
        for hero_id, hero_info in hero_mapping.items():
            if hero_info["name"].lower() == args.hero.lower() or hero_info["localized_name"].lower() == args.hero.lower():
                found = True
                process_hero(int(hero_id), hero_info["name"], hero_info["localized_name"], args.download)
                break
                
        if not found:
            print(f"Hero '{args.hero}' not found in hero mapping")
    else:
        # Process all heroes
        success, total = process_all_heroes(args.limit, args.download)
        
        # If we processed all heroes and were successful, combine them
        if success > 0 and (args.limit is None or success == args.limit):
            print("Processing completed successfully. Combining hero files...")
            combine_all_hero_abilities()

if __name__ == "__main__":
    main()