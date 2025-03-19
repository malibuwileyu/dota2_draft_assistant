#!/usr/bin/env python3
"""
Examine the downloaded HTML to extract ability information
"""
import re
import json
import os

# Configuration
INPUT_FILE = "axe_page.html"
OUTPUT_DIR = "src/main/resources/data/abilities"
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "axe_abilities_extracted.json")

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

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

def main():
    # Read the HTML file
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        html_content = f.read()
    
    print(f"Read {len(html_content)} bytes from {INPUT_FILE}")
    
    # Look for the abilities section
    abilities_section = re.search(r'<span class="mw-headline" id="Abilities">Abilities</span>.*?<table.*?>(.*?)</table>', html_content, re.DOTALL)
    
    if abilities_section:
        abilities_table = abilities_section.group(1)
        print(f"Found abilities section: {len(abilities_table)} bytes")
        
        # Extract ability rows
        ability_rows = re.findall(r'<tr>(.*?)</tr>', abilities_table, re.DOTALL)
        
        if ability_rows:
            print(f"Found {len(ability_rows)} ability rows")
            
            # Skip the header row
            ability_rows = ability_rows[1:]
            
            abilities_data = []
            
            for idx, row in enumerate(ability_rows):
                # Extract columns
                columns = re.findall(r'<td.*?>(.*?)</td>', row, re.DOTALL)
                
                if len(columns) >= 2:
                    ability_name = clean_html(columns[0])
                    ability_desc = clean_html(columns[1])
                    
                    print(f"Ability {idx+1}: {ability_name}")
                    print(f"Description: {ability_desc[:100]}...")
                    
                    ability_data = {
                        "id": idx + 1,
                        "name": ability_name,
                        "description": ability_desc
                    }
                    
                    # If there's a third column, it often has cooldown and mana info
                    if len(columns) >= 3:
                        stats = clean_html(columns[2])
                        print(f"Stats: {stats}")
                        
                        cd_match = re.search(r'CD:\s*([0-9/.]+)', stats)
                        if cd_match:
                            ability_data["cooldown_raw"] = cd_match.group(1)
                        
                        mana_match = re.search(r'Mana:\s*([0-9/.]+)', stats)
                        if mana_match:
                            ability_data["mana_cost_raw"] = mana_match.group(1)
                    
                    abilities_data.append(ability_data)
            
            # Save the extracted data
            with open(OUTPUT_FILE, "w") as f:
                json.dump({"abilities": abilities_data}, f, indent=2)
            
            print(f"Saved {len(abilities_data)} abilities to {OUTPUT_FILE}")
        else:
            print("No ability rows found in the abilities table")
    else:
        print("No abilities section found")
        
        # Try looking for individual ability sections
        ability_sections = re.findall(r'<div class="ability-header.*?">(.*?)</div>.*?<div class="ability-info">(.*?)</div>', html_content, re.DOTALL)
        
        if ability_sections:
            print(f"Found {len(ability_sections)} individual ability sections")
            
            abilities_data = []
            
            for idx, (header, info) in enumerate(ability_sections):
                name_match = re.search(r'<div class="ability-name">(.*?)</div>', header, re.DOTALL)
                if name_match:
                    ability_name = clean_html(name_match.group(1))
                    
                    desc_match = re.search(r'<div class="ability-description">(.*?)</div>', info, re.DOTALL)
                    ability_desc = clean_html(desc_match.group(1)) if desc_match else ""
                    
                    print(f"Ability {idx+1}: {ability_name}")
                    print(f"Description: {ability_desc[:100]}...")
                    
                    abilities_data.append({
                        "id": idx + 1,
                        "name": ability_name,
                        "description": ability_desc
                    })
            
            # Save the extracted data
            with open(OUTPUT_FILE, "w") as f:
                json.dump({"abilities": abilities_data}, f, indent=2)
            
            print(f"Saved {len(abilities_data)} abilities to {OUTPUT_FILE}")
        else:
            print("No individual ability sections found")

if __name__ == "__main__":
    main()