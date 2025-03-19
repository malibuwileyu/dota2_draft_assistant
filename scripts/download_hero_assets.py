import requests
import json
import os
import shutil
import time
import re
from concurrent.futures import ThreadPoolExecutor
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.retry import Retry

# URLs and directories
API_URL = "https://api.opendota.com/api/heroStats"
RESOURCES_DIR = "./src/main/resources"
IMAGES_DIR = f"{RESOURCES_DIR}/images/heroes"
ICONS_DIR = f"{RESOURCES_DIR}/images/heroes/icons"
DATA_DIR = f"{RESOURCES_DIR}/data"

# Create directories if they don't exist
os.makedirs(IMAGES_DIR, exist_ok=True)
os.makedirs(ICONS_DIR, exist_ok=True)
os.makedirs(DATA_DIR, exist_ok=True)

# Configure session with retries and backoff
session = requests.Session()
retries = Retry(
    total=5,  # number of retries
    backoff_factor=1,  # wait 1, 2, 4, 8, 16 seconds between retries
    status_forcelist=[429, 500, 502, 503, 504]  # retry on these status codes
)
session.mount('https://', HTTPAdapter(max_retries=retries))

# Rate limiting
REQUEST_DELAY = 0.5  # seconds between requests

def sanitize_filename(name):
    """Convert a hero name to a safe filename"""
    # Remove special characters and replace spaces with underscores
    safe_name = re.sub(r'[^\w\s-]', '', name).strip().lower()
    safe_name = re.sub(r'[\s-]+', '_', safe_name)
    return safe_name

def download_image(url, filepath):
    """Download an image from URL to filepath with rate limiting"""
    try:
        time.sleep(REQUEST_DELAY)  # Rate limiting delay
        response = session.get(url, stream=True)
        if response.status_code == 200:
            with open(filepath, 'wb') as f:
                shutil.copyfileobj(response.raw, f)
            print(f"Downloaded {url} to {filepath}")
            return True
        else:
            print(f"Failed to download {url}: HTTP {response.status_code}")
            return False
    except Exception as e:
        print(f"Error downloading {url}: {e}")
        return False

def process_hero(hero):
    """Process a single hero: download images and return cleaned data"""
    hero_id = hero['id']
    internal_name = hero.get('name', '').replace('npc_dota_hero_', '')
    localized_name = hero.get('localized_name', f"Hero_{hero_id}")
    
    # Create a safe filename using both ID and name
    safe_name = sanitize_filename(localized_name)
    filename = f"{hero_id}_{safe_name}"
    
    # Handle image URLs - using CDN URL pattern and OpenDota API as fallback
    if hero.get('img'):
        # Try Dota 2 CDN first
        img_url_cdn = f"https://cdn.dota2.com/apps/dota2/images/heroes/{internal_name}_full.png"
        img_path = f"{IMAGES_DIR}/{filename}.png"
        
        # If CDN fails, use OpenDota URL
        if not download_image(img_url_cdn, img_path):
            img_url_api = f"https://api.opendota.com{hero.get('img').split('?')[0]}"
            download_image(img_url_api, img_path)
        
        # Create a symlink with just the ID for backward compatibility
        id_path = f"{IMAGES_DIR}/{hero_id}.png"
        if os.path.exists(img_path) and not os.path.exists(id_path):
            try:
                # For Windows, copy the file instead of creating a symlink
                if os.name == 'nt':
                    shutil.copy2(img_path, id_path)
                else:
                    os.symlink(os.path.basename(img_path), id_path)
            except Exception as e:
                print(f"Warning: Could not create ID-based link for {hero_id}: {e}")
        
        # Update path in hero data to local resource
        hero['img'] = f"/images/heroes/{filename}.png"
    
    if hero.get('icon'):
        # Try Dota 2 CDN first
        icon_url_cdn = f"https://cdn.dota2.com/apps/dota2/images/heroes/{internal_name}_icon.png"
        icon_path = f"{ICONS_DIR}/{filename}.png"
        
        # If CDN fails, use OpenDota URL
        if not download_image(icon_url_cdn, icon_path):
            icon_url_api = f"https://api.opendota.com{hero.get('icon').split('?')[0]}"
            download_image(icon_url_api, icon_path)
        
        # Create a symlink with just the ID for backward compatibility
        id_path = f"{ICONS_DIR}/{hero_id}.png"
        if os.path.exists(icon_path) and not os.path.exists(id_path):
            try:
                # For Windows, copy the file instead of creating a symlink
                if os.name == 'nt':
                    shutil.copy2(icon_path, id_path)
                else:
                    os.symlink(os.path.basename(icon_path), id_path)
            except Exception as e:
                print(f"Warning: Could not create ID-based link for {hero_id}: {e}")
        
        # Update path in hero data to local resource
        hero['icon'] = f"/images/heroes/icons/{filename}.png"
    
    # Clean up trend data which we don't need to store
    trend_fields = [
        'turbo_picks_trend', 'turbo_wins_trend',
        'pub_pick_trend', 'pub_win_trend'
    ]
    for field in trend_fields:
        hero.pop(field, None)
    
    # Convert null to None for proper JSON serialization
    for key, value in hero.items():
        if value is None:
            hero[key] = None
    
    return hero

def create_hero_mapping(heroes):
    """Create a mapping file that helps translate between hero IDs and names"""
    mapping = {}
    for hero in heroes:
        hero_id = hero['id']
        internal_name = hero.get('name', '').replace('npc_dota_hero_', '')
        localized_name = hero.get('localized_name', f"Hero_{hero_id}")
        safe_name = sanitize_filename(localized_name)
        
        mapping[hero_id] = {
            'id': hero_id,
            'name': internal_name,
            'localized_name': localized_name,
            'file_name': f"{hero_id}_{safe_name}",
            'attributes': hero.get('primary_attr', 'unknown'),
            'roles': hero.get('roles', [])
        }
    
    # Save the mapping file
    with open(f"{DATA_DIR}/hero_mapping.json", 'w', encoding='utf-8') as f:
        json.dump(mapping, f, indent=2, ensure_ascii=False)
    
    print(f"Created hero mapping file at {DATA_DIR}/hero_mapping.json")

def main():
    print("Fetching hero data from OpenDota API...")
    try:
        response = session.get(API_URL)
        response.raise_for_status()
    except requests.exceptions.RequestException as e:
        print(f"Failed to fetch hero data: {e}")
        return
    
    heroes = response.json()
    print(f"Found {len(heroes)} heroes")
    
    # Process heroes with limited concurrency
    with ThreadPoolExecutor(max_workers=3) as executor:
        processed_heroes = list(executor.map(process_hero, heroes))
    
    # Create hero ID to name mapping
    create_hero_mapping(processed_heroes)
    
    # Save the processed hero data to JSON file
    with open(f"{DATA_DIR}/heroes.json", 'w', encoding='utf-8') as f:
        json.dump(processed_heroes, f, indent=2, ensure_ascii=False)
    
    print(f"Saved hero data to {DATA_DIR}/heroes.json")
    print(f"Downloaded {len(processed_heroes)} hero images and icons")
    
    # Print stats on missing hero IDs
    existing_ids = set(hero['id'] for hero in processed_heroes)
    max_id = max(existing_ids)
    expected_ids = set(range(1, max_id + 1))
    missing_ids = expected_ids - existing_ids
    
    if missing_ids:
        print("\nMissing hero IDs (these are normal gaps in the sequence):")
        print(', '.join(str(id) for id in sorted(missing_ids)))

if __name__ == "__main__":
    main()