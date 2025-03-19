#!/usr/bin/env python3
"""
Fallback Hero Scraper that works without external dependencies.
Uses only Python standard libraries.
"""
import urllib.request
import urllib.error
import urllib.parse
import json
import os
import re
import time
import ssl

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
BASE_URL = "https://liquipedia.net/dota2/{}"
HERO_LIST = [
    "Anti-Mage", "Axe", "Bane", "Bloodseeker", "Crystal Maiden", 
    "Drow Ranger", "Earthshaker", "Juggernaut", "Mirana", "Morphling",
    "Shadow Fiend", "Phantom Lancer", "Puck", "Pudge", "Razor"
]

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Hero ID counter
hero_id_counter = 1000
ability_id_counter = 10000

# Basic HTML parsing regex patterns
ABILITY_SECTION_PATTERN = r'<div class="ability-table">.*?<th.*?>(.*?)</th>.*?</div>(?=<div class="ability-table"|$)'
DESCRIPTION_PATTERN = r'<div class="ability-description">(.*?)</div>'
STAT_ROW_PATTERN = r'<div class="ability-stat-row">.*?<div class="ability-stat-label">(.*?)</div>.*?<div class="ability-stat-value">(.*?)</div>.*?</div>'
COOLDOWN_PATTERN = r'<div class="cooldown-label">.*?</div>\s*([^<]+)'
MANA_PATTERN = r'<div class="manacost-label">.*?</div>\s*([^<]+)'

def clean_text(text):
    """Clean text from HTML parsing"""
    # Remove HTML tags
    text = re.sub(r'<.*?>', '', text)
    # Replace HTML entities
    text = text.replace('&nbsp;', ' ').replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
    # Clean whitespace
    return re.sub(r'\s+', ' ', text).strip()

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
    value_text = value_text.strip()
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

def fetch_page(url):
    """Fetch page content with retry logic and SSL context"""
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
    
    # Create SSL context that ignores certificate validation
    context = ssl._create_unverified_context()
    
    max_retries = 3
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=context, timeout=10) as response:
                return response.read().decode('utf-8')
        except Exception as e:
            print(f"Error fetching {url} (attempt {attempt+1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                time.sleep(2 * (attempt + 1))  # Exponential backoff
            else:
                raise
    return None

def scrape_hero_abilities(hero_name):
    """Scrape hero abilities from Liquipedia"""
    url_name = format_hero_name_for_url(hero_name)
    url = BASE_URL.format(url_name)
    
    print(f"Scraping {hero_name} from {url}")
    
    try:
        html_content = fetch_page(url)
        if not html_content:
            return None
    except Exception as e:
        print(f"Failed to fetch {url}: {e}")
        return None
    
    # Extract hero details
    global hero_id_counter
    hero_id = hero_id_counter
    hero_id_counter += 1
    
    # Initialize hero data structure
    hero_data = {
        "id": hero_id,
        "name": format_hero_name_for_file(hero_name),
        "localized_name": hero_name,  # Use provided name as fallback
        "abilities": [],
        "innate_abilities": [],
        "synergies": [],
        "counters": []
    }
    
    # Find all ability sections
    ability_sections = re.finditer(ABILITY_SECTION_PATTERN, html_content, re.DOTALL)
    
    global ability_id_counter
    
    for match in ability_sections:
        section = match.group(0)
        ability_name_match = re.search(r'<th.*?>(.*?)</th>', section, re.DOTALL)
        if not ability_name_match:
            continue
            
        ability_name = clean_text(ability_name_match.group(1))
        
        # Skip Aghanim's Shard/Scepter
        if "Aghanim" in ability_name:
            continue
        
        # Extract description
        description_match = re.search(DESCRIPTION_PATTERN, section, re.DOTALL)
        description = clean_text(description_match.group(1)) if description_match else ""
        
        # Determine if ability is active, passive or ultimate
        ability_type = "active"  # default
        if "passive" in description.lower() or "passively" in description.lower():
            ability_type = "passive"
        
        # Check if this is an ultimate ability
        is_ultimate = False
        if "ultimate" in description.lower():
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
        
        # Look for stat rows
        stat_rows = re.finditer(STAT_ROW_PATTERN, section, re.DOTALL)
        for row_match in stat_rows:
            try:
                key = clean_text(row_match.group(1)).lower().replace(' ', '_')
                value_text = clean_text(row_match.group(2))
                val = parse_ability_values(value_text)
                if val:
                    special_values[key] = val
            except Exception as e:
                print(f"Error parsing stat row: {e}")
                continue
        
        # Extract cooldown and mana cost
        cooldown = [0, 0, 0, 0]  # default
        mana_cost = [0, 0, 0, 0]  # default
        
        cooldown_match = re.search(COOLDOWN_PATTERN, section, re.DOTALL)
        if cooldown_match:
            cooldown_text = clean_text(cooldown_match.group(1))
            cooldown = parse_ability_values(cooldown_text) or cooldown
        
        mana_match = re.search(MANA_PATTERN, section, re.DOTALL)
        if mana_match:
            mana_text = clean_text(mana_match.group(1))
            mana_cost = parse_ability_values(mana_text) or mana_cost
        
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
            "notes": ""
        }
        
        # Determine if this is an innate ability
        if "innate" in description.lower() or ability_name.lower() in ["jingu mastery", "counter helix"]:
            ability["type"] = "innate"
            hero_data["innate_abilities"].append(ability)
        else:
            hero_data["abilities"].append(ability)
    
    return hero_data

def main():
    # Use predefined hero list
    heroes = HERO_LIST
    
    print(f"Processing {len(heroes)} heroes")
    
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