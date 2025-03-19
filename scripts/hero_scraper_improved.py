#!/usr/bin/env python3
"""
Improved Hero Scraper for Dota 2 Draft Assistant
Uses direct HTTP requests and regex for better parsing
"""
import urllib.request
import urllib.error
import json
import os
import re
import time
import ssl
import random

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
BASE_URL = "https://liquipedia.net/dota2/{}"

# Starting IDs (following axe_abilities.json format)
HERO_ID_MAP = {
    "Anti-Mage": 1,
    "Axe": 2, 
    "Bane": 3,
    "Bloodseeker": 4,
    "Crystal Maiden": 5,
    "Drow Ranger": 6,
    "Earthshaker": 7,
    "Juggernaut": 8,
    "Mirana": 9,
    "Morphling": 10,
    "Shadow Fiend": 11,
    "Phantom Lancer": 12,
    "Puck": 13,
    "Pudge": 14,
    "Razor": 15
}

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

def format_hero_name_for_url(hero_name):
    """Format hero name for URL"""
    # Replace spaces with underscores and ensure proper capitalization
    return hero_name.replace(" ", "_")

def format_hero_name_for_file(hero_name):
    """Format hero name for filename"""
    # Convert to lowercase and replace spaces/dashes with underscores
    return hero_name.lower().replace(" ", "_").replace("-", "_")

def clean_html(html):
    """Clean HTML text"""
    # Remove HTML tags
    text = re.sub(r'<[^>]+>', '', html)
    # Replace HTML entities
    text = text.replace('&nbsp;', ' ').replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
    text = text.replace('&#160;', ' ')
    # Clean whitespace
    text = re.sub(r'\s+', ' ', text).strip()
    return text

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
        # Return as is if numeric parsing fails
        return [value_text.strip()]

def fetch_url(url):
    """Fetch URL content with retry logic"""
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
    ctx = ssl._create_unverified_context()
    
    max_retries = 3
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=ctx, timeout=15) as response:
                return response.read().decode('utf-8')
        except Exception as e:
            print(f"Error fetching URL (attempt {attempt+1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                time.sleep(1 + random.random() * 2)  # Randomized delay
            else:
                raise
    return None

def extract_ability_section(html_content):
    """Extract ability section from HTML content"""
    # Find the abilities section
    ability_section_match = re.search(r'<div class="table-responsive">.*?<table class="wikitable sortable".*?>(.*?)</table>', html_content, re.DOTALL)
    if not ability_section_match:
        return []
    
    ability_section = ability_section_match.group(1)
    
    # Extract ability rows
    ability_rows = re.findall(r'<tr>(.*?)</tr>', ability_section, re.DOTALL)
    
    abilities = []
    for row in ability_rows[1:]:  # Skip header row
        ability = extract_ability_from_row(row)
        if ability:
            abilities.append(ability)
    
    return abilities

def extract_ability_from_row(row):
    """Extract ability details from a table row"""
    # Extract name from first column
    name_match = re.search(r'<td.*?>(.*?)</td>', row, re.DOTALL)
    if not name_match:
        return None
    
    ability_name = clean_html(name_match.group(1))
    
    # Skip empty rows or headers
    if not ability_name or ability_name.lower() == "name":
        return None
    
    # Skip Aghanim's upgrades
    if "aghanim" in ability_name.lower():
        return None
    
    # Extract remaining columns
    columns = re.findall(r'<td.*?>(.*?)</td>', row, re.DOTALL)
    
    if len(columns) < 3:
        return None
    
    # Safely get other columns
    description = clean_html(columns[1]) if len(columns) > 1 else ""
    
    # Determine ability type
    ability_type = "active"
    if "passive" in description.lower():
        ability_type = "passive"
    
    # Get behavior
    behavior = "no target"
    if "target" in description.lower():
        if "point" in description.lower() or "area" in description.lower():
            behavior = "point target"
        else:
            behavior = "unit target"
    elif "passive" in description.lower():
        behavior = "passive"
    
    # Determine damage type
    damage_type = "none"
    for dtype in ["physical", "magical", "pure"]:
        if dtype in description.lower():
            damage_type = dtype
            break
    
    # Determine who it affects
    affects = []
    if "enemy" in description.lower() or "enemies" in description.lower() or "foe" in description.lower():
        affects.append("enemies")
    if "ally" in description.lower() or "allies" in description.lower() or "friendly" in description.lower():
        affects.append("allies")
    if "self" in description.lower() or not affects:
        affects.append("self")
    
    # Extract cooldown and mana
    cooldown = [0, 0, 0, 0]
    mana = [0, 0, 0, 0]
    
    # Look for cooldown and mana information
    if len(columns) > 2:
        cooldown_text = clean_html(columns[2])
        if "cd:" in cooldown_text.lower():
            cd_match = re.search(r'CD:\s*([0-9/.]+)', cooldown_text, re.IGNORECASE)
            if cd_match:
                cooldown = parse_ability_values(cd_match.group(1))
        
        if "mana:" in cooldown_text.lower():
            mana_match = re.search(r'Mana:\s*([0-9/.]+)', cooldown_text, re.IGNORECASE)
            if mana_match:
                mana = parse_ability_values(mana_match.group(1))
    
    # Extract special values from description
    special_values = {}
    
    # Look for standard patterns in description
    value_patterns = [
        (r'(\d+)%', 'percent'),
        (r'(\d+/\d+/\d+/\d+)', 'value'),
        (r'(\d+/\d+/\d+)', 'ultimate_value'),
        (r'(\d+)', 'fixed_value')
    ]
    
    for pattern, key in value_patterns:
        matches = re.findall(pattern, description)
        if matches and key not in special_values:
            values = parse_ability_values(matches[0])
            if values:
                special_values[key] = values
    
    # Is it an innate ability?
    is_innate = "innate" in description.lower() or re.search(r'passive\s+innate', description, re.IGNORECASE)
    
    # Look for explicit ability parameters in the description
    param_patterns = {
        'damage': r'(\d+/\d+/\d+/?\d*)\s*damage',
        'radius': r'(\d+)\s*radius',
        'duration': r'(\d+(?:\.\d+)?/\d+(?:\.\d+)?/\d+(?:\.\d+)?/?\d*(?:\.\d+)?)\s*seconds?',
        'stun_duration': r'stuns?.*?for\s+(\d+(?:\.\d+)?/\d+(?:\.\d+)?/\d+(?:\.\d+)?/?\d*(?:\.\d+)?)\s*seconds?',
    }
    
    for param, pattern in param_patterns.items():
        match = re.search(pattern, description, re.IGNORECASE)
        if match:
            values = parse_ability_values(match.group(1))
            if values:
                special_values[param] = values
    
    # Extract notes from description
    notes = ""
    notes_match = re.search(r'Notes?:(.*?)(?:\n|$)', description, re.IGNORECASE)
    if notes_match:
        notes = notes_match.group(1).strip()
    
    # Generate sensible mock data for missing fields
    if not special_values and ability_type == "active":
        if "damage" in description.lower():
            special_values["damage"] = [75, 150, 225, 300] if ability_type != "ultimate" else [200, 300, 400]
        if "duration" in description.lower():
            special_values["duration"] = [1, 2, 3, 4] if ability_type != "ultimate" else [2, 3, 4]
    
    # Check for immunity piercing
    pierces_immunity = False
    if "magic immunity" in description.lower() and not "does not affect" in description.lower():
        pierces_immunity = True
    
    return {
        "name": ability_name,
        "description": description,
        "type": ability_type,
        "pierces_immunity": pierces_immunity,
        "behavior": behavior,
        "damage_type": damage_type,
        "affects": affects,
        "special_values": special_values,
        "cooldown": cooldown[:4] if len(cooldown) > 0 else [0, 0, 0, 0],
        "mana_cost": mana[:4] if len(mana) > 0 else [0, 0, 0, 0],
        "notes": notes
    }

def get_ability_details_from_liquipedia(html_content):
    """Get detailed ability info from Liquipedia"""
    abilities = []
    innate_abilities = []
    
    # Find all ability divs
    ability_divs = re.finditer(r'<div class="ability-table">(.*?)</div>(?=\s*<div class="ability-table"|$)', html_content, re.DOTALL)
    
    ability_id = 0
    for div_match in ability_divs:
        ability_div = div_match.group(1)
        
        # Get ability name
        name_match = re.search(r'<th.*?>(.*?)</th>', ability_div, re.DOTALL)
        if not name_match:
            continue
        
        ability_name = clean_html(name_match.group(1))
        
        # Skip Aghanim's
        if "aghanim" in ability_name.lower():
            continue
        
        # Get description
        desc_match = re.search(r'<div class="ability-description">(.*?)</div>', ability_div, re.DOTALL)
        description = clean_html(desc_match.group(1)) if desc_match else ""
        
        # Is this an ultimate ability?
        is_ultimate = "ultimate" in description.lower() or re.search(r'ulti-img', ability_div, re.DOTALL)
        
        # Ability type
        ability_type = "active"
        if "passive" in description.lower() or "passively" in description.lower():
            ability_type = "passive"
        
        # Determine behavior
        behavior = "no target"
        if "target" in description.lower():
            if "point" in description.lower() or "area" in description.lower():
                behavior = "point target"
            else:
                behavior = "unit target"
        elif "passive" in description.lower():
            behavior = "passive"
        
        # Damage type
        damage_type = "none"
        for dtype in ["physical", "magical", "pure"]:
            if dtype in description.lower():
                damage_type = dtype
                break
        
        # Affects
        affects = []
        if "enemy" in description.lower() or "enemies" in description.lower() or "foe" in description.lower():
            affects.append("enemies")
        if "ally" in description.lower() or "allies" in description.lower() or "friendly" in description.lower():
            affects.append("allies")
        if "self" in description.lower() or not affects:
            affects.append("self")
        
        # Special values
        special_values = {}
        
        # Look for stat rows
        stat_rows = re.finditer(r'<div class="ability-stat-row">.*?<div class="ability-stat-label">(.*?)</div>.*?<div class="ability-stat-value">(.*?)</div>', ability_div, re.DOTALL)
        for row_match in stat_rows:
            try:
                key = clean_html(row_match.group(1)).lower().replace(' ', '_')
                value_text = clean_html(row_match.group(2))
                val = parse_ability_values(value_text)
                if val:
                    special_values[key] = val
            except:
                continue
        
        # Cooldown
        cooldown = [0, 0, 0, 0]
        cooldown_match = re.search(r'<div class="cooldown-label">.*?</div>\s*([^<]+)', ability_div, re.DOTALL)
        if cooldown_match:
            cooldown_text = clean_html(cooldown_match.group(1))
            cd_values = parse_ability_values(cooldown_text)
            if cd_values:
                cooldown = cd_values
        
        # Mana cost
        mana_cost = [0, 0, 0, 0]
        mana_match = re.search(r'<div class="manacost-label">.*?</div>\s*([^<]+)', ability_div, re.DOTALL)
        if mana_match:
            mana_text = clean_html(mana_match.group(1))
            mana_values = parse_ability_values(mana_text)
            if mana_values:
                mana_cost = mana_values
        
        # Notes
        notes = ""
        notes_section = re.search(r'<span class="info-title">Notes:</span>(.*?)</ul>', ability_div, re.DOTALL)
        if notes_section:
            note_items = re.findall(r'<li>(.*?)</li>', notes_section.group(1), re.DOTALL)
            notes = " ".join([clean_html(note) for note in note_items])
        
        # Check for immunity piercing
        pierces_immunity = False
        if "pierce" in description.lower() and "immunity" in description.lower():
            pierces_immunity = True
        
        # Create ability object
        ability_id += 1
        ability = {
            "id": ability_id,
            "name": ability_name,
            "description": description,
            "type": ability_type,
            "pierces_immunity": pierces_immunity,
            "behavior": behavior,
            "damage_type": damage_type,
            "affects": affects,
            "special_values": special_values,
            "cooldown": cooldown[:4],
            "mana_cost": mana_cost[:4],
            "notes": notes
        }
        
        # Determine if this is an innate ability
        if "innate" in description.lower() or ability_name.lower() in ["jingu mastery", "counter helix", "coat of blood"]:
            ability["type"] = "innate"
            innate_abilities.append(ability)
        else:
            abilities.append(ability)
    
    return abilities, innate_abilities

def scrape_hero(hero_name):
    """Scrape hero data from Liquipedia"""
    url_name = format_hero_name_for_url(hero_name)
    url = BASE_URL.format(url_name)
    
    print(f"Scraping {hero_name} from {url}")
    
    # Fetch hero page
    try:
        html_content = fetch_url(url)
        if not html_content:
            print(f"Failed to fetch content for {hero_name}")
            return None
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return None
    
    # Get hero ID
    hero_id = HERO_ID_MAP.get(hero_name, random.randint(100, 500))
    
    # Extract hero details
    hero_data = {
        "id": hero_id,
        "name": format_hero_name_for_file(hero_name),
        "localized_name": hero_name,
        "abilities": [],
        "innate_abilities": [],
        "synergies": [],
        "counters": []
    }
    
    # Get detailed ability info
    abilities, innate_abilities = get_ability_details_from_liquipedia(html_content)
    
    # Update IDs
    for idx, ability in enumerate(abilities):
        ability["id"] = hero_id * 100 + (idx + 1)
    
    for idx, ability in enumerate(innate_abilities):
        ability["id"] = hero_id * 100 + len(abilities) + (idx + 1)
    
    hero_data["abilities"] = abilities
    hero_data["innate_abilities"] = innate_abilities
    
    return hero_data

def main():
    # Hero list
    heroes = list(HERO_ID_MAP.keys())
    print(f"Processing {len(heroes)} heroes")
    
    heroes_data = {"heroes": []}
    
    for hero_name in heroes:
        try:
            print(f"Processing {hero_name}...")
            hero_data = scrape_hero(hero_name)
            
            if hero_data:
                # Save individual hero file
                file_name = f"{hero_data['name']}_abilities.json"
                file_path = os.path.join(OUTPUT_DIR, file_name)
                
                # Create a single-hero file
                with open(file_path, 'w') as f:
                    json.dump({"heroes": [hero_data]}, f, indent=2)
                
                print(f"Saved {file_name} with {len(hero_data['abilities'])} abilities")
                
                # Add to complete heroes data
                heroes_data["heroes"].append(hero_data)
            
            # Be nice to the website
            time.sleep(1 + random.random())
            
        except Exception as e:
            print(f"Error processing {hero_name}: {e}")
    
    # Save complete file
    with open(os.path.join(OUTPUT_DIR, "all_heroes_abilities.json"), 'w') as f:
        json.dump(heroes_data, f, indent=2)
    
    print(f"Scraped {len(heroes_data['heroes'])} heroes successfully")

if __name__ == "__main__":
    main()