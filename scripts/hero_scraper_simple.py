#!/usr/bin/env python3
"""
Simple Dota 2 Hero Ability Scraper focusing on specific HTML markers
"""
import urllib.request
import ssl
import re
import json
import os
import time
import random

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
BASE_URL = "https://liquipedia.net/dota2/{}"
HERO_IDS = {
    "Axe": 2,
    "Crystal Maiden": 5,
    "Invoker": 36,
    "Shadow Fiend": 11
}

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

def clean_text(text):
    """Clean HTML text"""
    if text is None:
        return ""
        
    # Remove HTML tags
    text = re.sub(r'<[^>]+>', '', text)
    
    # Replace HTML entities
    entities = {
        '&nbsp;': ' ', 
        '&amp;': '&', 
        '&lt;': '<', 
        '&gt;': '>', 
        '&#160;': ' ',
        '&quot;': '"',
        '&ndash;': '-',
        '&#39;': "'"
    }
    for entity, replacement in entities.items():
        text = text.replace(entity, replacement)
    
    # Clean whitespace
    text = re.sub(r'\s+', ' ', text).strip()
    return text

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
                time.sleep(1 + random.random() * 2)
            else:
                raise
    return None

def parse_values(value_text):
    """Parse values like 10/20/30/40"""
    if not value_text or value_text.lower() in ['none', 'n/a', '-']:
        return []
    
    values = []
    for v in value_text.split("/"):
        v = v.strip()
        try:
            values.append(float(v))
        except ValueError:
            # Handle percentages
            if v.endswith('%'):
                try:
                    values.append(float(v.rstrip('%')) / 100)
                except:
                    values.append(v)
            else:
                values.append(v)
    return values

def get_ability_sections(html_content):
    """Extract ability sections from HTML content"""
    # Find ability section headers (h3 tags with IDs for abilities)
    ability_matches = re.finditer(r'<h3><span[^>]*id="([^"]+)"[^>]*></span><span[^>]*>([^<]+)</span>', html_content, re.DOTALL)
    
    abilities = []
    for match in ability_matches:
        ability_id = match.group(1)
        ability_name = match.group(2).strip()
        
        # Skip if not a main ability
        if 'Aghanim' in ability_name:
            continue
            
        print(f"Found ability: {ability_name}")
        
        # Find the spellcard for this ability
        spell_card_match = re.search(f'<div[^>]*class="spellcard-wrapper"[^>]*id="{ability_id}"[^>]*>(.*?)<div[^>]*id="[^"]+"[^>]*>|<div[^>]*class="spellcard-wrapper"[^>]*id="{ability_id}"[^>]*>(.*?)</div>\s*</div>', html_content, re.DOTALL)
        
        if spell_card_match:
            spell_card = spell_card_match.group(1) or spell_card_match.group(2)
            abilities.append((ability_name, spell_card))
        else:
            print(f"Could not find spellcard for {ability_name}")
    
    return abilities

def parse_ability(name, spell_card):
    """Parse ability details from a spellcard"""
    # Extract basic info
    description_match = re.search(r'<div[^>]*>(.*?)</div>\s*</div>\s*<div[^>]*margin-left', spell_card, re.DOTALL)
    description = ""
    if description_match:
        description = clean_text(description_match.group(1))
    else:
        # Try alternate pattern
        alt_desc_match = re.search(r'vertical-align:top; padding-right:2px; padding-bottom:5px; font-size:85%;">(.*?)</div>', spell_card, re.DOTALL)
        if alt_desc_match:
            description = clean_text(alt_desc_match.group(1))
    
    # Determine if active/passive
    ability_type = "active"
    if "passive" in spell_card.lower() or "passive" in description.lower():
        ability_type = "passive"
    
    # Determine if ultimate ability
    is_ultimate = "ulti-img" in spell_card or name.lower() in ["culling blade", "freezing field", "requiem of souls"]
    
    # Extract behavior
    behavior = "no target"
    if ability_type == "passive":
        behavior = "passive"
    elif re.search(r'target_unit', spell_card, re.IGNORECASE) or re.search(r'Enemy Units', spell_card, re.IGNORECASE):
        behavior = "unit target"
    elif re.search(r'target_point', spell_card, re.IGNORECASE) or re.search(r'target_area', spell_card, re.IGNORECASE):
        behavior = "point target"
    
    # Determine damage type
    damage_type = "none"
    damage_match = re.search(r'<div[^>]*>Damage Type</b>:?\s*([^<]+)', spell_card, re.IGNORECASE)
    if damage_match:
        damage_text = clean_text(damage_match.group(1)).lower()
        if "magical" in damage_text:
            damage_type = "magical"
        elif "physical" in damage_text:
            damage_type = "physical"
        elif "pure" in damage_text:
            damage_type = "pure"
    
    # If damage type not found, check description
    if damage_type == "none" and description:
        if "magical damage" in description.lower():
            damage_type = "magical"
        elif "physical damage" in description.lower():
            damage_type = "physical"
        elif "pure damage" in description.lower():
            damage_type = "pure"
    
    # Extract who it affects
    affects = []
    affect_match = re.search(r'<span[^>]*>Affects</span>.*?<div[^>]*>(.*?)</div>', spell_card, re.DOTALL)
    if affect_match:
        affect_text = clean_text(affect_match.group(1)).lower()
        if "enemy" in affect_text or "enemies" in affect_text:
            affects.append("enemies")
        if "ally" in affect_text or "allies" in affect_text:
            affects.append("allies")
        if "self" in affect_text or not affects:
            affects.append("self")
    else:
        # Determine from description
        if re.search(r'\b(enemies|enemy|foes|foe|opponents|opponent)\b', description, re.IGNORECASE):
            affects.append("enemies")
        if re.search(r'\b(allies|ally|friendly|friendlies|team)\b', description, re.IGNORECASE):
            affects.append("allies")
        if not affects or re.search(r'\b(self|himself|herself|itself|caster)\b', description, re.IGNORECASE):
            affects.append("self")
    
    # Extract special values
    special_values = {}
    
    # Look for common ability values
    value_patterns = [
        (r'<span[^>]*>Radius</span>:?\s*[^0-9]*([0-9./]+)', "radius"),
        (r'<span[^>]*>Cast Range</span>:?\s*[^0-9]*([0-9./]+)', "cast_range"),
        (r'<span[^>]*>Duration</span>:?\s*[^0-9]*([0-9./]+)', "duration"),
        (r'<span[^>]*>Damage</span>:?\s*[^0-9]*([0-9./]+)', "damage"),
        (r'<span[^>]*>Armor Bonus</span>:?\s*[^0-9]*([0-9./]+)', "armor_bonus"),
        (r'<span[^>]*>Move Speed Bonus</span>:?\s*[^0-9]*([0-9./]+)', "move_speed_bonus")
    ]
    
    for pattern, key in value_patterns:
        match = re.search(pattern, spell_card, re.DOTALL | re.IGNORECASE)
        if match:
            values = parse_values(match.group(1))
            if values:
                special_values[key] = values
    
    # Extract notes
    notes = ""
    notes_match = re.search(r'<h4>Notes:</h4>.*?<ul>(.*?)</ul>', spell_card, re.DOTALL)
    if notes_match:
        note_items = re.findall(r'<li>(.*?)</li>', notes_match.group(1), re.DOTALL)
        if note_items:
            notes = " ".join([clean_text(note) for note in note_items])
    
    # Extract cooldown
    cooldown = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
    cooldown_match = re.search(r'<div[^>]*>(?:<a[^>]*>)?<img[^>]*Cooldown[^>]*>(?:</a>)?</div>\s*<div[^>]*>\s*([0-9./]+)', spell_card, re.DOTALL)
    if cooldown_match:
        cooldown_values = parse_values(cooldown_match.group(1))
        if cooldown_values:
            cooldown = cooldown_values
    
    # Extract mana cost
    mana_cost = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
    mana_match = re.search(r'<div[^>]*>(?:<a[^>]*>)?<img[^>]*Mana[^>]*>(?:</a>)?</div>\s*<div[^>]*>\s*([0-9./]+)', spell_card, re.DOTALL)
    if mana_match:
        mana_values = parse_values(mana_match.group(1))
        if mana_values:
            mana_cost = mana_values
    
    # Check if it pierces spell immunity
    pierces_immunity = False
    if re.search(r'Pierces Debuff Immunity', spell_card, re.IGNORECASE) or re.search(r'pierces spell immunity', spell_card + description, re.IGNORECASE):
        pierces_immunity = True
    
    # Is this an innate ability?
    is_innate = "innate" in spell_card.lower() or "innate" in description.lower() or name.lower() in ["counter helix", "jingu mastery", "coat of blood"]
    
    return {
        "name": name,
        "description": description,
        "type": "innate" if is_innate else ability_type,
        "pierces_immunity": pierces_immunity,
        "behavior": behavior,
        "damage_type": damage_type,
        "affects": affects,
        "special_values": special_values,
        "cooldown": cooldown[:4] if len(cooldown) >= 4 else cooldown + [0] * (4 - len(cooldown)),
        "mana_cost": mana_cost[:4] if len(mana_cost) >= 4 else mana_cost + [0] * (4 - len(mana_cost)),
        "notes": notes
    }

def get_hero_abilities(hero_name, hero_id):
    """Get abilities for a specific hero"""
    print(f"Processing {hero_name}...")
    
    # Format URL
    url_name = hero_name.replace(" ", "_")
    url = BASE_URL.format(url_name)
    
    # Fetch page content
    html_content = fetch_url(url)
    if not html_content:
        print(f"Failed to fetch page for {hero_name}")
        return None
    
    # Extract ability sections
    ability_sections = get_ability_sections(html_content)
    if not ability_sections:
        print(f"No ability sections found for {hero_name}")
        return None
    
    # Create hero data structure
    hero_data = {
        "id": hero_id,
        "name": hero_name.lower().replace(" ", "_").replace("-", "_"),
        "localized_name": hero_name,
        "abilities": [],
        "innate_abilities": [],
        "synergies": [],
        "counters": []
    }
    
    # Parse each ability
    for idx, (name, spell_card) in enumerate(ability_sections):
        ability = parse_ability(name, spell_card)
        if ability:
            if ability["type"] == "innate":
                ability["id"] = hero_id * 100 + len(hero_data["abilities"]) + 1
                hero_data["innate_abilities"].append(ability)
            else:
                ability["id"] = hero_id * 100 + idx + 1
                hero_data["abilities"].append(ability)
    
    return hero_data

def main():
    """Main function"""
    for hero_name, hero_id in HERO_IDS.items():
        try:
            hero_data = get_hero_abilities(hero_name, hero_id)
            
            if hero_data:
                # Save to JSON file
                file_name = f"{hero_data['name']}_abilities.json"
                file_path = os.path.join(OUTPUT_DIR, file_name)
                
                with open(file_path, 'w') as f:
                    json.dump({"heroes": [hero_data]}, f, indent=2)
                
                print(f"Saved {file_name} with {len(hero_data['abilities'])} abilities and {len(hero_data['innate_abilities'])} innate abilities")
            
            # Be nice to the website
            time.sleep(1 + random.random())
            
        except Exception as e:
            print(f"Error processing {hero_name}: {e}")
            import traceback
            traceback.print_exc()
    
    print("Done!")

if __name__ == "__main__":
    main()