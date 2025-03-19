#!/usr/bin/env python3
"""
Dota 2 Hero Ability Scraper - Working Version

This script scrapes hero ability data from Liquipedia and overwrites the placeholder templates
for heroes that still have generic ability names.
"""
import urllib.request
import urllib.error
import json
import os
import re
import time
import random
import ssl
import glob

# Try to import BeautifulSoup, but continue without it if not available
try:
    from bs4 import BeautifulSoup
    HAVE_BS4 = True
except ImportError:
    print("BeautifulSoup not installed. Will use regex-based parsing only.")
    print("For better results, install with: pip install beautifulsoup4")
    HAVE_BS4 = False

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
    
    # Remove HTML tags if string still has them
    if isinstance(text, str) and "<" in text and ">" in text:
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
        if isinstance(text, str):
            text = text.replace(entity, replacement)
    
    # Clean whitespace
    if isinstance(text, str):
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

def extract_abilities_regex(html_content, hero_name):
    """Extract abilities using regex for basic parsing when BeautifulSoup is not available"""
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
            
            # ... [rest of the code remains the same]
            # Determine damage type, affects, cooldown, mana cost, special values, notes, etc.
            damage_type = "none"
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
                
            # Create ability object
            ability = {
                "id": idx + 1,  # Will be updated later
                "name": ability_name,
                "description": description,
                "type": ability_type if not is_ultimate else "ultimate",
                "pierces_immunity": False,
                "behavior": behavior,
                "damage_type": damage_type,
                "affects": affects,
                "special_values": {},
                "cooldown": [0, 0, 0, 0] if not is_ultimate else [0, 0, 0],
                "mana_cost": [0, 0, 0, 0] if not is_ultimate else [0, 0, 0],
                "notes": ""
            }
            
            # Determine if this is an innate ability
            if ability_type == "innate" or "innate" in description.lower() or ability_name.lower() in ["counter helix", "jingu mastery", "coat of blood"]:
                ability["type"] = "innate"
                innate_abilities.append(ability)
            else:
                abilities.append(ability)
    
    # Handle special cases for certain heroes
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

def extract_abilities_beautifulsoup(html_content, hero_name):
    """Extract abilities using BeautifulSoup for better parsing"""
    abilities = []
    innate_abilities = []
    
    if not HAVE_BS4:
        debug_print("BeautifulSoup not available, skipping BS4 parsing")
        return abilities, innate_abilities
        
    soup = BeautifulSoup(html_content, 'html.parser')
    
    # Find the abilities section
    abilities_section = soup.find('span', {'class': 'mw-headline', 'id': 'Abilities'})
    if not abilities_section:
        debug_print(f"Could not find abilities section for {hero_name}")
        return abilities, innate_abilities
    
    # Get the section container
    section_container = abilities_section.parent
    if not section_container:
        debug_print(f"Could not find section container for {hero_name}")
        return abilities, innate_abilities
    
    # Try to find ability divs
    ability_divs = section_container.find_next_siblings('div', style=lambda value: value and 'display: inline-block' in value)
    
    if not ability_divs:
        # Alternative: look for divs with class 'ability-background'
        ability_divs = soup.find_all('div', {'class': 'ability-background'})
    
    for idx, div in enumerate(ability_divs):
        # Skip if this isn't actually an ability
        if 'aghanim' in div.text.lower():
            continue
        
        # Extract ability name
        name_element = div.find('h3') or div.find('div', {'class': 'ability-header'})
        if not name_element:
            continue
            
        ability_name = clean_text(name_element.get_text())
        debug_print(f"Found ability {idx+1}: {ability_name}")
        
        # Extract description
        description_element = div.find('p') or div.find('div', {'class': 'ability-description'})
        description = clean_text(description_element.get_text()) if description_element else ""
        
        # Determine if active/passive
        ability_type = "active"
        if "passive" in div.text.lower() or "passive" in description.lower():
            ability_type = "passive"
        
        # Check if this is an ultimate ability
        is_ultimate = False
        if "ultimate" in div.text.lower() or "ultimate" in description.lower():
            is_ultimate = True
            
        # Determine behavior
        behavior = "no target"
        if ability_type == "passive":
            behavior = "passive"
        elif re.search(r'target (unit|enemy|ally|hero)', div.text, re.IGNORECASE):
            behavior = "unit target"
        elif re.search(r'target (area|point|location)', div.text, re.IGNORECASE):
            behavior = "point target"
        
        # Determine damage type
        damage_type = "none"
        damage_element = div.find(text=re.compile('damage type', re.IGNORECASE))
        if damage_element:
            parent = damage_element.parent
            if parent and parent.next_sibling:
                damage_text = clean_text(parent.next_sibling.string)
                if "magical" in damage_text.lower():
                    damage_type = "magical"
                elif "physical" in damage_text.lower():
                    damage_type = "physical"
                elif "pure" in damage_text.lower():
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
        cooldown_values = []
        mana_cost_values = []
        
        # Look for cooldown
        cooldown_element = div.find(text=re.compile('cooldown', re.IGNORECASE))
        if cooldown_element:
            parent = cooldown_element.parent
            if parent and parent.next_sibling:
                cooldown_text = clean_text(parent.next_sibling.string)
                cooldown_values = parse_ability_values(cooldown_text)
        
        # Look for mana cost
        mana_element = div.find(text=re.compile('mana( cost)?', re.IGNORECASE))
        if mana_element:
            parent = mana_element.parent
            if parent and parent.next_sibling:
                mana_text = clean_text(parent.next_sibling.string)
                mana_cost_values = parse_ability_values(mana_text)
        
        # Default values if not found
        if not cooldown_values:
            cooldown_values = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
            
        if not mana_cost_values:
            mana_cost_values = [0, 0, 0, 0] if not is_ultimate else [0, 0, 0]
        
        # Look for special values
        special_values = {}
        
        # Look for common stats
        for stat_name, keywords in {
            "radius": ["radius", "aoe"],
            "range": ["range", "cast range", "distance"],
            "duration": ["duration", "lasts"],
            "damage": ["damage", "deals"],
        }.items():
            for keyword in keywords:
                element = div.find(text=re.compile(f"{keyword}", re.IGNORECASE))
                if element:
                    parent = element.parent
                    if parent and parent.next_sibling:
                        value_text = clean_text(parent.next_sibling.string)
                        values = parse_ability_values(value_text)
                        if values:
                            special_values[stat_name] = values
                            break
        
        # Extract any notes
        notes = ""
        notes_header = div.find('h4', text=re.compile('notes', re.IGNORECASE))
        if notes_header:
            notes_list = notes_header.find_next('ul')
            if notes_list:
                note_items = notes_list.find_all('li')
                if note_items:
                    notes = " ".join([clean_text(note.get_text()) for note in note_items])
        
        # Check if ability pierces spell immunity
        pierces_immunity = False
        if "pierces spell immunity" in div.text.lower() or "pierces spell immunity" in description.lower():
            pierces_immunity = True
        
        # Create ability object
        ability = {
            "id": idx + 1,  # Will be updated later
            "name": ability_name,
            "description": description,
            "type": ability_type if not is_ultimate else "ultimate",
            "pierces_immunity": pierces_immunity,
            "behavior": behavior,
            "damage_type": damage_type,
            "affects": affects,
            "special_values": special_values,
            "cooldown": cooldown_values[:4] if len(cooldown_values) >= 4 else cooldown_values + [0] * (4 - len(cooldown_values)),
            "mana_cost": mana_cost_values[:4] if len(mana_cost_values) >= 4 else mana_cost_values + [0] * (4 - len(mana_cost_values)),
            "notes": notes
        }
        
        # Determine if this is an innate ability
        if (ability_type == "innate" or 
            "innate" in description.lower() or 
            "innate" in ability_name.lower() or 
            ability_name.lower() in ["counter helix", "jingu mastery", "coat of blood", 
                                    "presence of the dark lord", "crystallize"]):
            ability["type"] = "innate"
            innate_abilities.append(ability)
        else:
            abilities.append(ability)
    
    # Handle special cases for certain heroes
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

def scrape_hero(hero_name, hero_id=None, fallback_names=None):
    """Scrape hero data from Liquipedia with multiple name formats"""
    
    # Format hero name for URL and potential alternatives
    url_formats = []
    if fallback_names:
        url_formats.extend(fallback_names)
    else:
        # Generate possible URL formats
        url_formats = [
            hero_name.replace(" ", "_"),  # Standard format: Anti_Mage
            hero_name.replace(" ", "-"),  # Hyphenated: Anti-Mage
            hero_name.replace("-", "_"),  # Underscore instead of hyphen: Anti_Mage
            hero_name.replace("-", " ").replace(" ", "_"),  # Space to underscore: Anti_Mage
            hero_name.lower().replace(" ", "_").replace("-", "_")  # All lowercase with underscores
        ]
    
    html_content = None
    successful_url = None
    
    # Try each format until one works
    for url_format in url_formats:
        url = BASE_URL.format(url_format)
        debug_print(f"Trying to scrape {hero_name} from {url}")
        
        try:
            content = fetch_url(url)
            if content and "Abilities" in content:
                html_content = content
                successful_url = url
                debug_print(f"Successfully fetched {url}")
                break
        except Exception as e:
            debug_print(f"Error fetching {url}: {e}")
    
    if not html_content:
        debug_print(f"Failed to fetch content for {hero_name} after trying multiple formats")
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
    
    # Extract abilities using BeautifulSoup for better parsing
    abilities, innate_abilities = extract_abilities_beautifulsoup(html_content, hero_name)
    
    # If no abilities found or BS4 not available, use our own regex-based extraction
    if not abilities:
        debug_print(f"No abilities found with BeautifulSoup for {hero_name}, using regex-based extraction")
        # Define a simplified version of extract_abilities inline
        abilities, innate_abilities = extract_abilities_regex(html_content, hero_name)
    
    # Update ability IDs
    for idx, ability in enumerate(abilities):
        ability["id"] = hero_id * 100 + (idx + 1)
    
    for idx, ability in enumerate(innate_abilities):
        ability["id"] = hero_id * 100 + len(abilities) + (idx + 1)
    
    hero_data["abilities"] = abilities
    hero_data["innate_abilities"] = innate_abilities
    
    # Print results summary
    debug_print(f"For {hero_name} using {successful_url}:")
    debug_print(f"  Found {len(abilities)} regular abilities:")
    for ability in abilities:
        debug_print(f"    - {ability['name']}")
    debug_print(f"  Found {len(innate_abilities)} innate abilities:")
    for ability in innate_abilities:
        debug_print(f"    - {ability['name']}")
    
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

def get_placeholder_heroes():
    """Find hero files with placeholder abilities"""
    placeholder_heroes = []
    pattern = os.path.join(OUTPUT_DIR, "*_abilities.json")
    hero_files = [f for f in glob.glob(pattern) 
                  if not os.path.basename(f).startswith("all_") 
                  and not os.path.basename(f).startswith("hero_abilities_example")]
    
    for file_path in hero_files:
        try:
            with open(file_path, 'r') as f:
                hero_data = json.load(f)
                if "heroes" in hero_data and len(hero_data["heroes"]) > 0:
                    hero = hero_data["heroes"][0]
                    
                    # Check if this hero has placeholder abilities
                    has_placeholders = False
                    if "abilities" in hero and len(hero["abilities"]) > 0:
                        for ability in hero["abilities"]:
                            if "Ability " in ability["name"] or ability["name"] in ["First Ability", "Second Ability", "Third Ability", "Ultimate"]:
                                has_placeholders = True
                                break
                    
                    if has_placeholders:
                        placeholder_heroes.append({
                            "name": hero["localized_name"],
                            "id": hero["id"],
                            "file_name": os.path.basename(file_path)
                        })
        except Exception as e:
            debug_print(f"Error checking {file_path}: {e}")
    
    return placeholder_heroes

def main():
    """Main function"""
    try:
        import glob
    except ImportError:
        print("Error: glob module not found. This should be part of the standard library")
        return
    
    print("Finding hero files with placeholder abilities...")
    placeholder_heroes = get_placeholder_heroes()
    print(f"Found {len(placeholder_heroes)} heroes with placeholder abilities")
    
    if not placeholder_heroes:
        print("No placeholder heroes found. All hero data is already populated.")
        return
    
    # Special handling for certain heroes with non-standard URLs
    special_cases = {
        "Anti-Mage": ["Anti-Mage", "Anti_Mage", "AntiMage"],
        "Queen of Pain": ["Queen_of_Pain", "QueenOfPain"],
        "Vengeful Spirit": ["Vengeful_Spirit", "VengefulSpirit"],
        "Shadow Fiend": ["Shadow_Fiend", "ShadowFiend"],
        "Crystal Maiden": ["Crystal_Maiden", "CrystalMaiden"],
        "Nature's Prophet": ["Nature's_Prophet", "Natures_Prophet", "NaturesProphet"]
    }
    
    # Process each hero with placeholder abilities
    success_count = 0
    
    for hero_info in placeholder_heroes:
        hero_name = hero_info["name"]
        hero_id = hero_info["id"]
        
        try:
            print(f"\nProcessing {hero_name}...")
            
            # Check if this hero has special case URLs
            fallback_names = special_cases.get(hero_name)
            
            hero_data = scrape_hero(hero_name, hero_id, fallback_names)
            
            if hero_data and (hero_data["abilities"] or hero_data["innate_abilities"]):
                # If at least one ability was found, save the file
                file_name = hero_info["file_name"]
                if save_hero_data(hero_data, file_name):
                    print(f"✓ Saved {file_name} with {len(hero_data['abilities'])} abilities and {len(hero_data['innate_abilities'])} innate abilities")
                    success_count += 1
            else:
                print(f"✗ No abilities found for {hero_name}")
            
            # Be nice to the website
            time.sleep(1 + random.random())
            
        except Exception as e:
            print(f"✗ Error processing {hero_name}: {e}")
            import traceback
            traceback.print_exc()
    
    print(f"\nSuccessfully processed {success_count} out of {len(placeholder_heroes)} placeholder heroes")
    
    # Combine all heroes into one file if any were updated
    if success_count > 0:
        try:
            from combine_all_heroes import main as combine_main
            print("\nCombining all hero files...")
            combine_main()
        except Exception as e:
            print(f"Error combining hero files: {e}")

if __name__ == "__main__":
    main()