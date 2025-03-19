#!/usr/bin/env python3
import requests
from bs4 import BeautifulSoup
import json
import os
import re
import time
from urllib.parse import quote

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
BASE_URL = "https://liquipedia.net/dota2/{}"
HERO_LIST_URL = "https://liquipedia.net/dota2/Heroes"

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Hero ID counter
hero_id_counter = 1000
ability_id_counter = 10000

def clean_text(text):
    """Clean text from HTML parsing"""
    return text.strip().replace('\n', ' ').replace('\t', '').replace('\r', '')

def format_hero_name_for_url(hero_name):
    """Format hero name for URL"""
    # Replace spaces with underscores and ensure proper capitalization
    return hero_name.replace(" ", "_")

def format_hero_name_for_file(hero_name):
    """Format hero name for filename"""
    # Convert to lowercase and replace spaces/dashes with underscores
    return hero_name.lower().replace(" ", "_").replace("-", "_")

def parse_ability_values(value_text):
    """Parse ability values like '10/20/30/40'"""
    if not value_text or value_text.lower() in ['none', 'n/a']:
        return []
    
    # Handle ranges like "10-20"
    if "-" in value_text and not "/" in value_text:
        try:
            start, end = map(float, value_text.split("-"))
            return [start, end]
        except:
            pass

    # Handle standard format like "10/20/30/40"
    try:
        values = [float(v.strip()) for v in value_text.split("/")]
        return values
    except:
        # Return as string if numeric parsing fails
        return [value_text.strip()]

def scrape_hero_abilities(hero_name):
    """Scrape hero abilities from Liquipedia"""
    url_name = format_hero_name_for_url(hero_name)
    url = BASE_URL.format(url_name)
    
    print(f"Scraping {hero_name} from {url}")
    
    try:
        # Add a user agent to avoid being blocked
        headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
        response = requests.get(url, headers=headers)
        response.raise_for_status()
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return None
    
    soup = BeautifulSoup(response.text, 'html.parser')
    
    # Extract hero details
    global hero_id_counter
    hero_id = hero_id_counter
    hero_id_counter += 1
    
    # Extract proper name with spaces (if available)
    hero_display_name = soup.select_one('h1#firstHeading')
    localized_name = hero_display_name.text.strip() if hero_display_name else hero_name
    
    # Initialize hero data structure
    hero_data = {
        "id": hero_id,
        "name": format_hero_name_for_file(hero_name),
        "localized_name": localized_name,
        "abilities": [],
        "innate_abilities": [],
        "synergies": [],
        "counters": []
    }
    
    # Find ability tables
    ability_tables = soup.select('.ability-table')
    
    global ability_id_counter
    
    for table in ability_tables:
        # Get ability name
        ability_header = table.select_one('th')
        if not ability_header:
            continue
        
        ability_name = ability_header.text.strip()
        
        # Skip Aghanim's Shard/Scepter as they're typically enhancements
        if "Aghanim" in ability_name:
            continue
            
        # Get ability section (may contain multiple elements)
        ability_section = table.select_one('.ability-info')
        if not ability_section:
            continue
        
        # Extract description
        description_elem = ability_section.select_one('.ability-description')
        description = clean_text(description_elem.text) if description_elem else ""
        
        # Determine if ability is active, passive or ultimate
        ability_type = "active"  # default
        if "passive" in description.lower() or "passively" in description.lower():
            ability_type = "passive"
        
        # Check if this is an ultimate ability
        is_ultimate = False
        if table.select_one('.ulti-img') or "ultimate" in description.lower():
            is_ultimate = True
        
        # Determine behavior
        behavior = "no target"  # default
        if "target" in description.lower():
            if "point" in description.lower():
                behavior = "point target"
            else:
                behavior = "unit target"
        elif "passive" in description.lower():
            behavior = "passive"
        
        # Determine damage type
        damage_type = "none"  # default
        for dType in ["physical", "magical", "pure"]:
            if dType in description.lower():
                damage_type = dType
                break
        
        # Determine who it affects
        affects = []
        if "enemie" in description.lower() or "foe" in description.lower():
            affects.append("enemies")
        if "allie" in description.lower() or "friendly" in description.lower():
            affects.append("allies")
        if "self" in description.lower() or not affects:  # Default to self if nothing else
            affects.append("self")
        
        # Extract special values
        special_values = {}
        
        # Look for common stat patterns in tables
        stat_rows = ability_section.select('.ability-stat-row')
        for row in stat_rows:
            try:
                label = row.select_one('.ability-stat-label')
                value = row.select_one('.ability-stat-value')
                if label and value:
                    key = label.text.strip().lower().replace(' ', '_')
                    val = parse_ability_values(value.text.strip())
                    if val:
                        special_values[key] = val
            except:
                continue
        
        # Extract cooldown and mana cost
        cooldown = [0, 0, 0, 0]  # default
        mana_cost = [0, 0, 0, 0]  # default
        
        cooldown_elem = ability_section.select_one('.cooldown-label')
        if cooldown_elem and cooldown_elem.next_sibling:
            cooldown_text = cooldown_elem.next_sibling.strip()
            cooldown = parse_ability_values(cooldown_text) or cooldown
        
        mana_elem = ability_section.select_one('.manacost-label')
        if mana_elem and mana_elem.next_sibling:
            mana_text = mana_elem.next_sibling.strip()
            mana_cost = parse_ability_values(mana_text) or mana_cost
        
        # Extract any notes
        notes = ""
        notes_section = ability_section.select_one('.info-title')
        if notes_section and "Notes:" in notes_section.text:
            notes_list = notes_section.find_next('ul')
            if notes_list:
                note_items = notes_list.select('li')
                notes = " ".join([clean_text(note.text) for note in note_items])
        
        # Create ability object
        ability_id = ability_id_counter
        ability_id_counter += 1
        
        ability = {
            "id": ability_id,
            "name": ability_name,
            "description": description,
            "type": "active" if not behavior == "passive" else "passive",
            "behavior": behavior,
            "damage_type": damage_type,
            "affects": affects,
            "special_values": special_values,
            "cooldown": cooldown[:4],  # Limit to 4 levels
            "mana_cost": mana_cost[:4],  # Limit to 4 levels
            "notes": notes
        }
        
        # Determine if this is an innate ability
        if "innate" in description.lower() or ability_name.lower() in ["jingu mastery", "counter helix"]:
            ability["type"] = "innate"
            hero_data["innate_abilities"].append(ability)
        else:
            hero_data["abilities"].append(ability)
    
    return hero_data

def scrape_all_heroes():
    """Scrape a list of all heroes from the hero page"""
    try:
        # Add a user agent to avoid being blocked
        headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
        response = requests.get(HERO_LIST_URL, headers=headers)
        response.raise_for_status()
    except Exception as e:
        print(f"Error fetching hero list: {e}")
        return []
    
    soup = BeautifulSoup(response.text, 'html.parser')
    hero_links = soup.select('.hero-grid a')
    heroes = []
    
    for link in hero_links:
        try:
            hero_name = link.get('title')
            if hero_name:
                heroes.append(hero_name)
        except:
            continue
    
    return heroes

def main():
    # You can specify heroes manually, or use the scraper to get all heroes
    heroes = scrape_all_heroes()
    
    # Alternative: Manually specify heroes to scrape
    # heroes = [
    #     "Anti-Mage", "Axe", "Bane", "Bloodseeker", "Crystal Maiden", 
    #     "Drow Ranger", "Earthshaker", "Juggernaut", "Mirana", "Morphling"
    # ]
    
    print(f"Found {len(heroes)} heroes to scrape")
    
    heroes_data = {"heroes": []}
    
    for hero_name in heroes:
        try:
            print(f"Processing {hero_name}...")
            hero_data = scrape_hero_abilities(hero_name)
            
            if hero_data:
                # Save individual hero file
                file_name = f"{hero_data['name']}_abilities.json"
                file_path = os.path.join(OUTPUT_DIR, file_name)
                
                # Create a single-hero file
                with open(file_path, 'w') as f:
                    json.dump({"heroes": [hero_data]}, f, indent=2)
                
                print(f"Saved {file_name}")
                
                # Add to complete heroes data
                heroes_data["heroes"].append(hero_data)
            
            # Be nice to the website
            time.sleep(2)
            
        except Exception as e:
            print(f"Error processing {hero_name}: {e}")
    
    # Save complete file (all heroes)
    with open(os.path.join(OUTPUT_DIR, "all_heroes_abilities.json"), 'w') as f:
        json.dump(heroes_data, f, indent=2)
    
    print(f"Scraped {len(heroes_data['heroes'])} heroes successfully")

if __name__ == "__main__":
    main()