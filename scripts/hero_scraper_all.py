#!/usr/bin/env python3
"""
Dota 2 Hero Ability Scraper - Processes all heroes from local HTML files
"""
import json
import os
import re
import sys
import time
from urllib.parse import quote

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
            
        # Save HTML content to file
        file_path = os.path.join(HTML_DIR, f"{hero_name.lower().replace(' ', '_')}.html")
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
            
        return file_path
    except Exception as e:
        print(f"Error downloading {hero_name}: {e}")
        return None

def extract_hero_abilities(hero_id, hero_name, localized_name, html_file):
    """Extract abilities from the hero's HTML file"""
    print(f"Processing {hero_name} (ID: {hero_id}) from {html_file}")
    
    try:
        # Read only the first 1000 lines which should contain navigation
        with open(html_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()[:1000]
            html_content = ''.join(lines)
        
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
        
        # Look for abilities section
        abilities_found = False
        ability_names = []
        
        # Find the line with abilities section
        for i, line in enumerate(lines):
            if 'data-target="#Abilities"' in line:
                abilities_found = True
                
                # Get the next several lines which should contain the ability names
                ability_count = 0
                for j in range(i+1, i+20):
                    if j < len(lines) and ability_count < 4:  # Only get the first 4 actual abilities
                        match = re.search(r'data-target="#([^"]+)".*?<span class="toctext">(.*?)</span>', lines[j])
                        if match:
                            ability_id = match.group(1)
                            ability_name = match.group(2)
                            # Skip non-ability entries
                            if ("Aghanim" not in ability_name and 
                                "Talent" not in ability_name and 
                                "Upgrades" not in ability_name and 
                                "Facets" not in ability_name and
                                "Recent" not in ability_name and
                                "International" not in ability_name and
                                "Dota Plus" not in ability_name and
                                "Equipment" not in ability_name and
                                "Trivia" not in ability_name and
                                "Gallery" not in ability_name and
                                "References" not in ability_name and
                                "End" not in ability_name):
                                ability_names.append(ability_name)
                                ability_count += 1
                
                print(f"  Found {len(ability_names)} abilities: {', '.join(ability_names)}")
                break
        
        # Find innate abilities
        innate_found = False
        innate_name = None
        
        for i, line in enumerate(lines):
            if 'data-target="#Innate"' in line:
                innate_found = True
                
                # Look for innate ability names in the next few lines
                for j in range(i+1, i+10):
                    if j < len(lines):
                        # Coat of Blood is a common pattern but other heroes might have different innate abilities
                        innate_match = re.search(r'data-target="#([^"]+)".*?<span class="toctext">(.*?)</span>', lines[j])
                        if innate_match and not ("Hero Model" in innate_match.group(2) or "Upgrades" in innate_match.group(2)):
                            innate_name = innate_match.group(2)
                            print(f"  Found innate ability: {innate_name}")
                            break
                            
                break
        
        # Process regular abilities
        if abilities_found and ability_names:
            for idx, ability_name in enumerate(ability_names):
                ability_id = hero_id * 100 + idx + 1
                
                # Default ability properties
                ability = {
                    "id": ability_id,
                    "name": ability_name,
                    "description": f"{ability_name} description",
                    "type": "active",
                    "pierces_immunity": False,
                    "behavior": "no target",
                    "damage_type": "magical",
                    "affects": ["enemies"],
                    "special_values": {},
                    "cooldown": [0, 0, 0, 0],
                    "mana_cost": [0, 0, 0, 0],
                    "notes": ""
                }
                
                # Determine if ability is passive based on common passive ability patterns
                passive_words = ["passive", "aura", "presence", "essence"]
                if (any(word in ability_name.lower() for word in passive_words) or
                    "Counter Helix" in ability_name or
                    "Corrosive Skin" in ability_name or
                    "Backtrack" in ability_name or
                    "Resistance" in ability_name):
                    ability["type"] = "passive"
                    ability["behavior"] = "passive"
                
                # Ultimate abilities usually have only 3 levels
                if idx == 3 or "cataclysm" in ability_name.lower() or "requiem" in ability_name.lower():
                    ability["cooldown"] = [0, 0, 0]
                    ability["mana_cost"] = [0, 0, 0]
                
                hero_data["heroes"][0]["abilities"].append(ability)
        
        # Process innate ability if found
        if innate_found and innate_name:
            innate_id = hero_id * 100 + len(ability_names) + 1
            
            innate_ability = {
                "id": innate_id,
                "name": innate_name,
                "description": f"{innate_name} innate ability",
                "type": "innate",
                "behavior": "passive",
                "affects": ["self"],
                "special_values": {},
                "notes": ""
            }
            
            hero_data["heroes"][0]["innate_abilities"].append(innate_ability)
        
        return hero_data
    except Exception as e:
        print(f"Error processing {hero_name}: {e}")
        import traceback
        traceback.print_exc()
        return None

def process_hero(hero_id, hero_name, localized_name, download=False):
    """Process a single hero, downloading if necessary"""
    hero_file_name = hero_name.lower().replace(' ', '_')
    html_file = os.path.join(HTML_DIR, f"{hero_file_name}.html")
    
    # Download if requested or file doesn't exist
    if download or not os.path.exists(html_file):
        html_file = download_hero_page(hero_name)
        if not html_file:
            return False
    
    # Extract abilities
    hero_data = extract_hero_abilities(hero_id, hero_name, localized_name, html_file)
    if not hero_data:
        return False
    
    # Save to file
    output_file = os.path.join(OUTPUT_DIR, f"{hero_file_name}_abilities.json")
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
    
    print(f"Processing {total_heroes} heroes...")
    
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
            import traceback
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
    
    parser = argparse.ArgumentParser(description='Process Dota 2 hero abilities')
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