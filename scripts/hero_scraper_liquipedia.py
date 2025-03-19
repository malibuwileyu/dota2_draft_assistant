#!/usr/bin/env python3
"""
Dota 2 Hero Ability Scraper for Liquipedia
"""
import urllib.request
import urllib.error
import json
import os
import re
import time
import random
import ssl

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"
BASE_URL = "https://liquipedia.net/dota2/{}"
DEBUG = True

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

def debug_print(message):
    """Print debug messages if DEBUG is enabled"""
    if DEBUG:
        print(message)

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
        '&ndash;': '-'
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
            debug_print(f"Error fetching URL (attempt {attempt+1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                time.sleep(1 + random.random() * 2)  # Randomized delay
            else:
                raise
    return None

def parse_ability_values(value_text):
    """Parse ability values like '10/20/30/40'"""
    if not value_text or value_text.lower() in ['none', 'n/a', '-']:
        return []
    
    # Handle ranges like "10-20"
    if "-" in value_text and "/" not in value_text:
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
    except:
        # Return as string if parsing fails
        return [value_text.strip()]

def extract_abilities(html_content, hero_name):
    """Extract abilities from the Liquipedia page"""
    abilities = []
    innate_abilities = []
    
    # First try to find the abilities section header
    abilities_header_match = re.search(r'<span class="mw-headline" id="Abilities">Abilities</span>', html_content)
    if not abilities_header_match:
        debug_print("Could not find abilities section header")
        return abilities, innate_abilities
    
    # Get the section after the header
    section_start = abilities_header_match.end()
    next_section_match = re.search(r'<span class="mw-headline" id="[^"]+', html_content[section_start:])
    
    if next_section_match:
        section_end = section_start + next_section_match.start()
        abilities_section = html_content[section_start:section_end]
    else:
        abilities_section = html_content[section_start:]
    
    debug_print(f"Abilities section length: {len(abilities_section)} characters")
    
    # Try to find ability divs in standard format
    ability_divs = re.finditer(r'<div style="display: ?inline-block; ?vertical-align: ?top;[^>]*?>(.*?)<h3[^>]*>(.*?)</h3>(.*?)(?=<div style="display: ?inline-block; ?vertical-align: ?top;|<span class="mw-headline"|$)', abilities_section, re.DOTALL)
    
    for idx, match in enumerate(ability_divs):
        full_div = match.group(0)
        ability_name_div = match.group(2)
        ability_content = match.group(3)
        
        ability_name = clean_text(ability_name_div)
        
        # Skip Aghanim's Scepter/Shard upgrades
        if "aghanim" in ability_name.lower():
            continue
            
        debug_print(f"Found ability {idx+1}: {ability_name}")
        
        # Try to find the description
        description = ""
        description_match = re.search(r'<div[^>]*?>(.*?)<div class="ttw-abil-extra"', full_div, re.DOTALL)
        if description_match:
            description = clean_text(description_match.group(1))
        else:
            # Alternative pattern
            description_match = re.search(r'<p>(.*?)</p>', full_div, re.DOTALL)
            if description_match:
                description = clean_text(description_match.group(1))
            else:
                # Just grab a chunk of text
                text_match = re.search(r'<div[^>]*?>(.*?)</div>', full_div, re.DOTALL)
                if text_match:
                    description = clean_text(text_match.group(1))
        
        debug_print(f"Description: {description[:100]}...")
        
        # Determine if active/passive
        ability_type = "active"
        if "passive" in full_div.lower() or "passive" in description.lower():
            ability_type = "passive"
        
        # Check if this is an ultimate ability
        is_ultimate = False
        if re.search(r'ultimate', full_div, re.IGNORECASE) or re.search(r'ultimate', description, re.IGNORECASE):
            is_ultimate = True
            
        # Determine behavior
        behavior = "no target"
        if ability_type == "passive":
            behavior = "passive"
        elif re.search(r'target (unit|enemy|ally|hero)', full_div, re.IGNORECASE) or re.search(r'target (unit|enemy|ally|hero)', description, re.IGNORECASE):
            behavior = "unit target"
        elif re.search(r'target (area|point|location)', full_div, re.IGNORECASE) or re.search(r'target (area|point|location)', description, re.IGNORECASE):
            behavior = "point target"
        
        # Determine damage type
        damage_type = "none"
        damage_match = re.search(r'Damage Type</b>:?\s*([^<]+)', full_div, re.IGNORECASE)
        if damage_match:
            damage_text = damage_match.group(1).lower().strip()
            if "magical" in damage_text:
                damage_type = "magical"
            elif "physical" in damage_text:
                damage_type = "physical"
            elif "pure" in damage_text:
                damage_type = "pure"
        
        # If damage type not found in specific tag, check description
        if damage_type == "none":
            if "magical damage" in description.lower():
                damage_type = "magical"
            elif "physical damage" in description.lower():
                damage_type = "physical"
            elif "pure damage" in description.lower():
                damage_type = "pure"
        
        # Extract who it affects
        affects = []
        if re.search(r'\b(enemies|enemy|foes|foe|opponents|opponent)\b', description, re.IGNORECASE):
            affects.append("enemies")
        if re.search(r'\b(allies|ally|friendly|friendlies|team)\b', description, re.IGNORECASE):
            affects.append("allies")
        if not affects or re.search(r'\b(self|himself|herself|itself|caster)\b', description, re.IGNORECASE):
            affects.append("self")
        
        # Extract cooldown and mana cost
        cooldown = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
        mana_cost = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
        
        cooldown_match = re.search(r'Cooldown</b>:?\s*([0-9/.]+)', full_div, re.IGNORECASE)
        if cooldown_match:
            cooldown = parse_ability_values(cooldown_match.group(1)) or cooldown
        
        mana_match = re.search(r'Mana( Cost)?</b>:?\s*([0-9/.]+)', full_div, re.IGNORECASE)
        if mana_match:
            mana_cost = parse_ability_values(mana_match.group(2)) or mana_cost
        
        # Look for special values
        special_values = {}
        
        # Look for various ability stats
        stat_patterns = {
            "radius": r'Radius</b>:?\s*([0-9/.]+)',
            "range": r'Cast Range</b>:?\s*([0-9/.]+)',
            "duration": r'Duration</b>:?\s*([0-9/.]+)',
            "damage": r'Damage</b>:?\s*([0-9/.]+)'
        }
        
        for key, pattern in stat_patterns.items():
            match = re.search(pattern, full_div, re.IGNORECASE)
            if match:
                values = parse_ability_values(match.group(1))
                if values:
                    special_values[key] = values
        
        # Extract any notes
        notes = ""
        notes_match = re.search(r'<h4>Notes:</h4>.*?<ul>(.*?)</ul>', full_div, re.DOTALL)
        if notes_match:
            note_items = re.findall(r'<li>(.*?)</li>', notes_match.group(1), re.DOTALL)
            if note_items:
                notes = " ".join([clean_text(note) for note in note_items])
        
        # Check if ability pierces spell immunity
        pierces_immunity = False
        if re.search(r'pierces spell immunity', full_div, re.IGNORECASE) or re.search(r'pierces spell immunity', description, re.IGNORECASE):
            pierces_immunity = True
        
        # Create ability object
        ability = {
            "id": idx + 1,  # Will be updated later
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
        
        # Determine if this is an innate ability
        if ability_type == "innate" or "innate" in description.lower() or ability_name.lower() in ["counter helix", "jingu mastery", "coat of blood"]:
            ability["type"] = "innate"
            innate_abilities.append(ability)
        else:
            abilities.append(ability)
    
    # If we didn't find any abilities using the standard format, try alternate patterns
    if not abilities and not innate_abilities:
        # Try another common format
        ability_sections = re.finditer(r'<div class="ability-background">.*?<div class="ability-header">(.*?)</div>.*?<div class="ability-info">(.*?)</div>', html_content, re.DOTALL)
        
        for idx, match in enumerate(ability_sections):
            header = match.group(1)
            info = match.group(2)
            
            # Extract ability name
            name_match = re.search(r'<div class="ability-name">(.*?)</div>', header, re.DOTALL)
            if not name_match:
                continue
                
            ability_name = clean_text(name_match.group(1))
            
            # Skip Aghanim's Scepter/Shard upgrades
            if "aghanim" in ability_name.lower():
                continue
                
            debug_print(f"Found ability {idx+1} (alt format): {ability_name}")
            
            # Extract description
            desc_match = re.search(r'<div class="ability-description">(.*?)</div>', info, re.DOTALL)
            description = clean_text(desc_match.group(1)) if desc_match else ""
            
            debug_print(f"Description: {description[:100]}...")
            
            # Determine if active/passive
            ability_type = "active"
            type_match = re.search(r'<div><b>(.*?)</b></div>', info, re.DOTALL)
            if type_match and "passive" in type_match.group(1).lower():
                ability_type = "passive"
            elif "passive" in description.lower():
                ability_type = "passive"
                
            # Check if this is an ultimate ability
            is_ultimate = re.search(r'class="ulti-img"', header, re.DOTALL) is not None
                
            # Determine behavior
            behavior = "no target"
            if ability_type == "passive":
                behavior = "passive"
            elif re.search(r'(unit target|targets a unit|target unit|enemy target|allied target)', info, re.IGNORECASE):
                behavior = "unit target"
            elif re.search(r'(point target|ground target|targeted location|point on the map)', info, re.IGNORECASE):
                behavior = "point target"
            
            # Determine damage type
            damage_type = "none"
            damage_match = re.search(r'<b>Damage</b>:?\s*([^<]+)', info, re.DOTALL)
            if damage_match:
                damage_text = damage_match.group(1).lower().strip()
                if "magical" in damage_text:
                    damage_type = "magical"
                elif "physical" in damage_text:
                    damage_type = "physical"
                elif "pure" in damage_text:
                    damage_type = "pure"
                    
            # If damage type not found in specific tag, check description
            if damage_type == "none":
                if "magical damage" in description.lower():
                    damage_type = "magical"
                elif "physical damage" in description.lower():
                    damage_type = "physical"
                elif "pure damage" in description.lower():
                    damage_type = "pure"
            
            # Extract who it affects
            affects = []
            if re.search(r'\b(enemies|enemy|foes|foe|opponents|opponent)\b', description, re.IGNORECASE):
                affects.append("enemies")
            if re.search(r'\b(allies|ally|friendly|friendlies|team)\b', description, re.IGNORECASE):
                affects.append("allies")
            if not affects or re.search(r'\b(self|himself|herself|itself|caster)\b', description, re.IGNORECASE):
                affects.append("self")
                
            # Extract cooldown and mana cost
            cooldown = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
            mana_cost = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
            
            cooldown_match = re.search(r'<div class="cooldown-label">.*?</div>\s*([^<]+)', info, re.DOTALL)
            if cooldown_match:
                cooldown_text = clean_text(cooldown_match.group(1))
                cd_values = parse_ability_values(cooldown_text)
                if cd_values:
                    cooldown = cd_values
            
            mana_match = re.search(r'<div class="manacost-label">.*?</div>\s*([^<]+)', info, re.DOTALL)
            if mana_match:
                mana_text = clean_text(mana_match.group(1))
                mana_values = parse_ability_values(mana_text)
                if mana_values:
                    mana_cost = mana_values
                    
            # Extract special values
            special_values = {}
            
            # Look for stat rows
            stat_rows = re.finditer(r'<div class="ability-stat-row">.*?<div class="ability-stat-label">(.*?)</div>.*?<div class="ability-stat-value">(.*?)</div>', info, re.DOTALL)
            for row_match in stat_rows:
                try:
                    key = clean_text(row_match.group(1)).lower().replace(' ', '_')
                    value_text = clean_text(row_match.group(2))
                    val = parse_ability_values(value_text)
                    if val:
                        special_values[key] = val
                except:
                    continue
                    
            # Extract notes
            notes = ""
            notes_section = re.search(r'<span class="info-title">Notes:</span>(.*?)</ul>', info, re.DOTALL)
            if notes_section:
                note_items = re.findall(r'<li>(.*?)</li>', notes_section.group(1), re.DOTALL)
                if note_items:
                    notes = " ".join([clean_text(note) for note in note_items])
                    
            # Check if ability pierces spell immunity
            pierces_immunity = False
            if re.search(r'pierces spell immunity', info, re.IGNORECASE) or re.search(r'pierces spell immunity', description, re.IGNORECASE):
                pierces_immunity = True
                
            # Create ability object
            ability = {
                "id": idx + 1,  # Will be updated later
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
            
            # Determine if this is an innate ability
            if ability_type == "innate" or "innate" in description.lower() or ability_name.lower() in ["counter helix", "jingu mastery", "coat of blood"]:
                ability["type"] = "innate"
                innate_abilities.append(ability)
            else:
                abilities.append(ability)
    
    # If specific hero case handling is needed
    if hero_name.lower() == "axe" and not innate_abilities:
        # Check if Counter Helix is in abilities and move it to innate
        for idx, ability in enumerate(abilities):
            if ability["name"].lower() == "counter helix":
                ability["type"] = "innate"
                innate_abilities.append(ability)
                del abilities[idx]
                break
                
    debug_print(f"Extracted {len(abilities)} regular abilities and {len(innate_abilities)} innate abilities")
    return abilities, innate_abilities

def scrape_hero(hero_name, hero_id=None):
    """Scrape hero data from Liquipedia"""
    # Format hero name for URL
    url_name = hero_name.replace(" ", "_")
    url = BASE_URL.format(url_name)
    
    debug_print(f"Scraping {hero_name} from {url}")
    
    # Fetch hero page
    try:
        html_content = fetch_url(url)
        if not html_content:
            debug_print(f"Failed to fetch content for {hero_name}")
            return None
    except Exception as e:
        debug_print(f"Error fetching {url}: {e}")
        return None
    
    # Use provided ID or generate one
    if hero_id is None:
        hero_id = random.randint(100, 999)
    
    # Format hero name for file
    file_name = hero_name.lower().replace(" ", "_").replace("-", "_")
    
    # Create hero data structure
    hero_data = {
        "id": hero_id,
        "name": file_name,
        "localized_name": hero_name,
        "abilities": [],
        "innate_abilities": [],
        "synergies": [],
        "counters": []
    }
    
    # Extract abilities
    abilities, innate_abilities = extract_abilities(html_content, hero_name)
    
    # Update ability IDs
    for idx, ability in enumerate(abilities):
        ability["id"] = hero_id * 100 + (idx + 1)
    
    for idx, ability in enumerate(innate_abilities):
        ability["id"] = hero_id * 100 + len(abilities) + (idx + 1)
    
    hero_data["abilities"] = abilities
    hero_data["innate_abilities"] = innate_abilities
    
    return hero_data

def save_hero_data(hero_data, file_name):
    """Save hero data to a JSON file"""
    file_path = os.path.join(OUTPUT_DIR, file_name)
    try:
        with open(file_path, 'w') as f:
            json.dump({"heroes": [hero_data]}, f, indent=2)
        return True
    except Exception as e:
        debug_print(f"Error saving file: {e}")
        return False

def main():
    """Main function"""
    # Test heroes - can be replaced with a complete list
    heroes = [
        {"name": "Axe", "id": 2},
        {"name": "Crystal Maiden", "id": 5},
        {"name": "Invoker", "id": 36},
        {"name": "Shadow Fiend", "id": 11}
    ]
    
    # Process each hero
    success_count = 0
    all_heroes_data = {"heroes": []}
    
    for hero_info in heroes:
        hero_name = hero_info["name"]
        hero_id = hero_info["id"]
        
        try:
            debug_print(f"Processing {hero_name}...")
            hero_data = scrape_hero(hero_name, hero_id)
            
            if hero_data and (hero_data["abilities"] or hero_data["innate_abilities"]):
                # Save individual hero file
                file_name = f"{hero_data['name']}_abilities.json"
                if save_hero_data(hero_data, file_name):
                    debug_print(f"Saved {file_name} with {len(hero_data['abilities'])} abilities and {len(hero_data['innate_abilities'])} innate abilities")
                    all_heroes_data["heroes"].append(hero_data)
                    success_count += 1
            else:
                debug_print(f"No abilities found for {hero_name}")
            
            # Be nice to the website
            time.sleep(1 + random.random())
            
        except Exception as e:
            debug_print(f"Error processing {hero_name}: {e}")
            import traceback
            traceback.print_exc()
    
    # Save combined file
    if all_heroes_data["heroes"]:
        with open(os.path.join(OUTPUT_DIR, "scraped_heroes_abilities.json"), 'w') as f:
            json.dump(all_heroes_data, f, indent=2)
            
        debug_print(f"Saved combined data with {len(all_heroes_data['heroes'])} heroes")
    
    debug_print(f"Successfully processed {success_count} out of {len(heroes)} heroes")

if __name__ == "__main__":
    main()