#!/usr/bin/env python3
"""
Improved Hero Ability Scraper for Dota 2 Draft Assistant v2
Revamped to better parse Liquipedia HTML structure
"""
import urllib.request
import urllib.error
import json
import os
import re
import time
import ssl
import random
from collections import defaultdict

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
BASE_URL = "https://liquipedia.net/dota2/{}"

# Hero ID mapping for consistency
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
    "Razor": 15,
    "Sand King": 16,
    "Storm Spirit": 17,
    "Sven": 18,
    "Tiny": 19,
    "Vengeful Spirit": 20,
    "Windranger": 21,
    "Zeus": 22,
    "Kunkka": 23,
    "Lina": 24,
    "Lion": 25,
    "Shadow Shaman": 26,
    "Slardar": 27,
    "Tidehunter": 28,
    "Witch Doctor": 29,
    "Lich": 30,
    "Riki": 31,
    "Enigma": 32,
    "Faceless Void": 33,
    "Phantom Assassin": 34,
    "Io": 35,
    "Invoker": 36
}

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

def format_hero_name_for_url(hero_name):
    """Format hero name for URL"""
    return hero_name.replace(" ", "_")

def format_hero_name_for_file(hero_name):
    """Format hero name for filename"""
    return hero_name.lower().replace(" ", "_").replace("-", "_")

def clean_html(html):
    """Clean HTML text"""
    if html is None:
        return ""
        
    # Remove HTML tags
    text = re.sub(r'<[^>]+>', '', html)
    
    # Replace HTML entities
    entities = {
        '&nbsp;': ' ', 
        '&amp;': '&', 
        '&lt;': '<', 
        '&gt;': '>', 
        '&#160;': ' ',
        '&quot;': '"'
    }
    for entity, replacement in entities.items():
        text = text.replace(entity, replacement)
    
    # Clean whitespace
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def parse_ability_values(value_text):
    """Parse ability values like '10/20/30/40'"""
    if not value_text or value_text.lower() in ['none', 'n/a', '-']:
        return []
    
    # Handle ranges like "10-20"
    if "-" in value_text and not "/" in value_text:
        try:
            parts = value_text.split("-")
            if len(parts) == 2:
                start, end = map(float, parts)
                return [start, end]
        except:
            pass

    # Handle standard format like "10/20/30/40"
    try:
        values = []
        for v in value_text.split("/"):
            try:
                values.append(float(v.strip()))
            except ValueError:
                # If we can't convert to float, keep as is
                values.append(v.strip())
        return values
    except:
        # Return as string if parsing fails
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

def extract_abilities(html_content):
    """Extract all abilities from the hero page"""
    abilities = []
    innate_abilities = []
    
    # Debug check
    print(f"HTML content size: {len(html_content)} bytes")
    
    # Find ability sections - look for table headers with ability names
    ability_sections = []
    # Try different pattern approaches
    
    # Look for abilities section by section heading
    abilities_section_match = re.search(r'<div class="section-heading">\s*<h3>Abilities</h3>.*?(<table.*?</table>)', html_content, re.DOTALL)
    
    if abilities_section_match:
        abilities_table = abilities_section_match.group(1)
        # Extract individual ability rows from the table
        ability_rows = re.findall(r'<tr>(.*?)</tr>', abilities_table, re.DOTALL)
        
        print(f"Found {len(ability_rows)} ability rows in the abilities table")
        
        # Skip the header row
        if len(ability_rows) > 1:
            ability_rows = ability_rows[1:]
        
        for idx, row in enumerate(ability_rows):
            # Parse the ability from the row
            ability = parse_ability_from_row(row, idx + 1)
            if ability:
                # Determine if this is an innate ability
                if ability.get("type") == "innate" or "innate" in ability.get("description", "").lower() or ability.get("name", "").lower() in ["counter helix", "jingu mastery", "coat of blood"]:
                    ability["type"] = "innate"
                    innate_abilities.append(ability)
                else:
                    abilities.append(ability)
    else:
        # Alternative approach: Look for div with ability-table class
        ability_tables = re.findall(r'<div class="ability-table">(.*?)</div>', html_content, re.DOTALL)
        
        print(f"Found {len(ability_tables)} ability tables with class ability-table")
        
        for idx, section in enumerate(ability_tables):
            ability = parse_ability_section(section, idx + 1)
            
            if ability:
                # Determine if this is an innate ability
                if ability.get("type") == "innate" or "innate" in ability.get("description", "").lower() or ability.get("name", "").lower() in ["counter helix", "jingu mastery", "coat of blood"]:
                    ability["type"] = "innate"
                    innate_abilities.append(ability)
                else:
                    abilities.append(ability)
    
    print(f"Extracted {len(abilities)} regular abilities and {len(innate_abilities)} innate abilities")
    return abilities, innate_abilities


def parse_ability_from_row(row, idx):
    """Parse ability details from a table row"""
    # Extract ability name (usually in the first column)
    name_match = re.search(r'<td[^>]*>(.*?)</td>', row, re.DOTALL)
    if not name_match:
        return None
    
    ability_name = clean_html(name_match.group(1))
    
    # Skip if this is just a header row or empty
    if not ability_name or ability_name.lower() in ["ability", "name", "key"] or len(ability_name) < 2:
        return None
    
    # Skip Aghanim's upgrades
    if "aghanim" in ability_name.lower():
        return None
    
    # Extract columns
    columns = re.findall(r'<td[^>]*>(.*?)</td>', row, re.DOTALL)
    if len(columns) < 2:
        return None
    
    # Get description
    description = ""
    if len(columns) > 1:
        description = clean_html(columns[1])
    
    # Determine if active/passive
    ability_type = "active"
    if "passive" in description.lower():
        ability_type = "passive"
    
    # Basic ability detection for behavior
    behavior = "no target"
    if ability_type == "passive":
        behavior = "passive"
    else:
        if re.search(r'target (unit|enemy|ally|hero)', description, re.IGNORECASE):
            behavior = "unit target"
        elif re.search(r'target (area|point|location)', description, re.IGNORECASE):
            behavior = "point target"
    
    # Damage type
    damage_type = "none"
    for dtype in ["physical", "magical", "pure"]:
        if dtype in description.lower():
            damage_type = dtype
            break
    
    # Who it affects
    affects = []
    if re.search(r'\b(enemies|enemy|foes|foe|opponents|opponent)\b', description, re.IGNORECASE):
        affects.append("enemies")
    if re.search(r'\b(allies|ally|friendly|friendlies|team)\b', description, re.IGNORECASE):
        affects.append("allies")
    if not affects or re.search(r'\b(self|himself|herself|itself|caster)\b', description, re.IGNORECASE):
        affects.append("self")
    
    # Extract cooldown and mana costs if provided in a separate column
    cooldown = [0, 0, 0, 0]
    mana_cost = [0, 0, 0, 0]
    
    if len(columns) > 2:
        stats_text = clean_html(columns[2])
        
        cd_match = re.search(r'(?:CD|Cooldown):\s*([0-9/.]+)', stats_text, re.IGNORECASE)
        if cd_match:
            cooldown = parse_ability_values(cd_match.group(1)) or cooldown
        
        mana_match = re.search(r'(?:MP|Mana):\s*([0-9/.]+)', stats_text, re.IGNORECASE)
        if mana_match:
            mana_cost = parse_ability_values(mana_match.group(1)) or mana_cost
    
    # Extract value patterns from description
    special_values = {}
    param_patterns = {
        'damage': r'(\d+(?:/\d+){0,3})\s*damage',
        'duration': r'(\d+(?:\.\d+)?(?:/\d+(?:\.\d+)?){0,3})\s*seconds?',
        'radius': r'(\d+(?:/\d+){0,3})\s*radius',
        'range': r'(\d+(?:/\d+){0,3})\s*range'
    }
    
    for param, pattern in param_patterns.items():
        match = re.search(pattern, description, re.IGNORECASE)
        if match:
            values = parse_ability_values(match.group(1))
            if values:
                special_values[param] = values
    
    # Check for immunity piercing
    pierces_immunity = False
    if re.search(r'pierces spell immunity', description, re.IGNORECASE):
        pierces_immunity = True
    
    # Extract notes if any
    notes = ""
    notes_match = re.search(r'Notes?:\s*(.*?)(?:$|[.;])', description, re.IGNORECASE)
    if notes_match:
        notes = notes_match.group(1).strip()
    
    return {
        "id": idx,  # Will be updated later
        "name": ability_name,
        "description": description,
        "type": ability_type,
        "pierces_immunity": pierces_immunity,
        "behavior": behavior,
        "damage_type": damage_type,
        "affects": affects,
        "special_values": special_values,
        "cooldown": cooldown[:4] if len(cooldown) >= 4 else cooldown + [0] * (4 - len(cooldown)),
        "mana_cost": mana_cost[:4] if len(mana_cost) >= 4 else mana_cost + [0] * (4 - len(mana_cost)),
        "notes": notes
    }

def parse_ability_section(section, idx):
    """Parse a single ability section"""
    # Get ability name
    name_match = re.search(r'<th.*?>(.*?)</th>', section, re.DOTALL)
    if not name_match:
        return None
    
    ability_name = clean_html(name_match.group(1))
    
    # Skip Aghanim's upgrades
    if "aghanim" in ability_name.lower():
        return None
    
    # Get description
    desc_match = re.search(r'<div class="ability-description">(.*?)</div>', section, re.DOTALL)
    description = clean_html(desc_match.group(1)) if desc_match else ""
    
    # Check if this is an ultimate
    is_ultimate = re.search(r'class="ulti-img"', section, re.DOTALL) is not None
    
    # Parse type (active/passive)
    ability_type = "active"
    type_match = re.search(r'<div [^>]*>\s*<b>([^<]+)</b>', section, re.DOTALL)
    if type_match:
        if "passive" in type_match.group(1).lower():
            ability_type = "passive"
    
    # Parse behavior (target type)
    behavior = "no target"
    if ability_type == "passive":
        behavior = "passive"
    else:
        # Try to determine from section text
        if re.search(r'\b(unit target|targets a unit|target unit|enemy target|allied target)\b', section, re.IGNORECASE):
            behavior = "unit target"
        elif re.search(r'\b(point target|ground target|targeted location|point on the map)\b', section, re.IGNORECASE):
            behavior = "point target"
    
    # Damage type
    damage_type = "none"
    damage_match = re.search(r'<div [^>]*>\s*<b>Damage</b>:?\s*([^<]+)</div>', section, re.IGNORECASE | re.DOTALL)
    if damage_match:
        damage_text = damage_match.group(1).lower().strip()
        if "magical" in damage_text:
            damage_type = "magical"
        elif "physical" in damage_text:
            damage_type = "physical"
        elif "pure" in damage_text:
            damage_type = "pure"
    
    # If damage type not found in dedicated section, try to infer from description
    if damage_type == "none" and description:
        if "magical damage" in description.lower():
            damage_type = "magical"
        elif "physical damage" in description.lower():
            damage_type = "physical"
        elif "pure damage" in description.lower():
            damage_type = "pure"
    
    # Determine who it affects
    affects = []
    if re.search(r'\b(enemies|enemy|foes|foe|opponents|opponent)\b', description, re.IGNORECASE):
        affects.append("enemies")
    if re.search(r'\b(allies|ally|friendly|friendlies|team)\b', description, re.IGNORECASE):
        affects.append("allies")
    if re.search(r'\b(self|himself|herself|itself|caster)\b', description, re.IGNORECASE) or not affects:
        affects.append("self")
    
    # Extract special values
    special_values = {}
    
    # Look for stat rows - these are in the format <label>: <value>
    stat_rows = re.finditer(r'<div class="ability-stat-row">.*?<div class="ability-stat-label">(.*?)</div>.*?<div class="ability-stat-value">(.*?)</div>', section, re.DOTALL)
    for row_match in stat_rows:
        try:
            key = clean_html(row_match.group(1)).lower().replace(' ', '_')
            value_text = clean_html(row_match.group(2))
            val = parse_ability_values(value_text)
            if val:
                special_values[key] = val
        except:
            continue
    
    # Also look for any values in description
    param_patterns = {
        'damage': r'(\d+(?:/\d+){0,3})\s*damage',
        'duration': r'(\d+(?:\.\d+)?(?:/\d+(?:\.\d+)?){0,3})\s*seconds?',
        'radius': r'(\d+(?:\.\d+)?(?:/\d+(?:\.\d+)?){0,3})\s*radius'
    }
    
    for param, pattern in param_patterns.items():
        if param not in special_values:  # Only add if not already found
            match = re.search(pattern, description, re.IGNORECASE)
            if match:
                values = parse_ability_values(match.group(1))
                if values:
                    special_values[param] = values
    
    # Parse cooldown
    cooldown = [0, 0, 0, 0]
    cooldown_match = re.search(r'<div class="cooldown-label">.*?</div>\s*([^<]+)', section, re.DOTALL)
    if cooldown_match:
        cooldown_text = clean_html(cooldown_match.group(1))
        cd_values = parse_ability_values(cooldown_text)
        if cd_values:
            # Fill up to 4 values for regular abilities, or 3 for ultimates
            expected_length = 3 if is_ultimate else 4
            while len(cd_values) < expected_length:
                if len(cd_values) > 0:
                    cd_values.append(cd_values[-1])  # Repeat the last value
                else:
                    cd_values.append(0)  # Add zeros if no values
            cooldown = cd_values
    
    # Parse mana cost
    mana_cost = [0, 0, 0, 0]
    mana_match = re.search(r'<div class="manacost-label">.*?</div>\s*([^<]+)', section, re.DOTALL)
    if mana_match:
        mana_text = clean_html(mana_match.group(1))
        mana_values = parse_ability_values(mana_text)
        if mana_values:
            # Fill up to 4 values for regular abilities, or 3 for ultimates
            expected_length = 3 if is_ultimate else 4
            while len(mana_values) < expected_length:
                if len(mana_values) > 0:
                    mana_values.append(mana_values[-1])  # Repeat the last value
                else:
                    mana_values.append(0)  # Add zeros if no values
            mana_cost = mana_values
    
    # Extract notes
    notes = ""
    notes_section = re.search(r'<span class="info-title">Notes:</span>(.*?)</ul>', section, re.DOTALL)
    if notes_section:
        note_items = re.findall(r'<li>(.*?)</li>', notes_section.group(1), re.DOTALL)
        if note_items:
            notes = " ".join([clean_html(note) for note in note_items])
    
    # Check for immunity piercing
    pierces_immunity = False
    if re.search(r'pierces.{0,30}spell.{0,10}immunity', section, re.IGNORECASE | re.DOTALL):
        pierces_immunity = True
    
    return {
        "id": idx,  # Will be updated later
        "name": ability_name,
        "description": description,
        "type": ability_type,
        "pierces_immunity": pierces_immunity,
        "behavior": behavior,
        "damage_type": damage_type,
        "affects": affects,
        "special_values": special_values,
        "cooldown": cooldown[:4] if len(cooldown) >= 4 else cooldown + [0] * (4 - len(cooldown)),
        "mana_cost": mana_cost[:4] if len(mana_cost) >= 4 else mana_cost + [0] * (4 - len(mana_cost)),
        "notes": notes
    }

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
    hero_id = HERO_ID_MAP.get(hero_name, 100)  # Default to 100 if not found
    
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
    
    # Get abilities
    abilities, innate_abilities = extract_abilities(html_content)
    
    # Update IDs
    for idx, ability in enumerate(abilities):
        ability["id"] = hero_id * 100 + (idx + 1)
    
    for idx, ability in enumerate(innate_abilities):
        ability["id"] = hero_id * 100 + len(abilities) + (idx + 1)
    
    hero_data["abilities"] = abilities
    hero_data["innate_abilities"] = innate_abilities
    
    return hero_data

def save_hero_data(hero_data, file_name):
    """Save hero data to JSON file"""
    file_path = os.path.join(OUTPUT_DIR, file_name)
    try:
        with open(file_path, 'w') as f:
            json.dump({"heroes": [hero_data]}, f, indent=2)
        return True
    except Exception as e:
        print(f"Error saving {file_path}: {e}")
        return False

def main():
    # Get list of hero files to update
    hero_files = []
    for file_name in os.listdir(OUTPUT_DIR):
        if file_name.endswith("_abilities.json") and not file_name.startswith("all_") and not file_name.startswith("hero_abilities_example"):
            hero_name_file = file_name.replace("_abilities.json", "")
            hero_files.append(hero_name_file)
    
    print(f"Found {len(hero_files)} hero files to update")
    
    # Create a reverse mapping from filename format to hero name
    heroes_to_process = {}
    for hero_name, hero_id in HERO_ID_MAP.items():
        file_name = format_hero_name_for_file(hero_name)
        if file_name in hero_files:
            heroes_to_process[hero_name] = file_name
    
    # Add any missing heroes
    for hero_file in hero_files:
        found = False
        for hero_name in HERO_ID_MAP:
            if format_hero_name_for_file(hero_name) == hero_file:
                found = True
                break
        if not found:
            # Try to reverse the file name to a hero name
            display_name = hero_file.replace('_', ' ').title()
            heroes_to_process[display_name] = hero_file
    
    print(f"Processing {len(heroes_to_process)} heroes")
    
    # Process each hero
    heroes_data = {"heroes": []}
    success_count = 0
    
    for hero_name, file_prefix in heroes_to_process.items():
        try:
            print(f"Processing {hero_name}...")
            hero_data = scrape_hero(hero_name)
            
            if hero_data and (hero_data["abilities"] or hero_data["innate_abilities"]):
                # Save individual hero file
                file_name = f"{file_prefix}_abilities.json"
                if save_hero_data(hero_data, file_name):
                    print(f"Saved {file_name} with {len(hero_data['abilities'])} abilities and {len(hero_data['innate_abilities'])} innate abilities")
                    heroes_data["heroes"].append(hero_data)
                    success_count += 1
                
            # Be nice to the website
            time.sleep(1 + random.random())
            
        except Exception as e:
            print(f"Error processing {hero_name}: {e}")
    
    # Save combined file
    if heroes_data["heroes"]:
        # Sort heroes by ID
        heroes_data["heroes"].sort(key=lambda h: h["id"])
        
        # Save the combined file
        combined_path = os.path.join(OUTPUT_DIR, "all_heroes_abilities.json")
        with open(combined_path, 'w') as f:
            json.dump(heroes_data, f, indent=2)
        
        print(f"Saved combined file with {len(heroes_data['heroes'])} heroes")
    
    print(f"Successfully processed {success_count} out of {len(heroes_to_process)} heroes")

if __name__ == "__main__":
    main()