# Dota 2 Hero Ability Scraper

This script scrapes hero ability data from Liquipedia and generates JSON files for the Dota 2 Draft Assistant application.

## Requirements

- Python 3.6+
- requests
- beautifulsoup4

## Installation

```bash
pip install -r requirements.txt
```

## Usage

Run the script from the project root directory:

```bash
python hero_scraper.py
```

This will:
1. Scrape the Liquipedia Dota 2 Heroes page to get a list of heroes
2. Create individual JSON files for each hero in `src/main/resources/data/abilities/`
3. Create a combined file with all heroes at `src/main/resources/data/abilities/all_heroes_abilities.json`

## JSON Schema

The generated JSON follows this structure:

```json
{
  "heroes": [
    {
      "id": 1000,
      "name": "anti_mage",
      "localized_name": "Anti-Mage",
      "abilities": [
        {
          "id": 10000,
          "name": "Mana Break",
          "description": "...",
          "type": "passive",
          "behavior": "passive",
          "damage_type": "physical",
          "affects": ["enemies"],
          "special_values": {
            "mana_per_hit": [28, 40, 52, 64],
            "mana_burn_damage_percent": [50, 50, 50, 50]
          },
          "cooldown": [0, 0, 0, 0],
          "mana_cost": [0, 0, 0, 0],
          "notes": "..."
        },
        // More abilities...
      ],
      "innate_abilities": [
        // Innate abilities...
      ],
      "synergies": [],
      "counters": []
    }
  ]
}
```

## Notes

- The script uses a delay between requests to avoid rate limiting
- You may need to manually verify and correct some ability data
- Synergies and counters are intentionally left empty as these will be populated by the ML model
- The script attempts to categorize abilities based on their descriptions, but manual verification is recommended