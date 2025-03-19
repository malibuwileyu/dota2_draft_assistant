#!/usr/bin/env python3
"""
Direct Hero Ability Data Entry for Dota 2 Draft Assistant

This script directly adds manually entered ability data for commonly used heroes.
Rather than relying on unreliable web scraping, this script contains hardcoded ability
information for popular heroes.
"""
import json
import os
import sys

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Hero ability data structures
HERO_DATA = {
    "drow_ranger": {
        "id": 6,
        "name": "drow_ranger",
        "localized_name": "Drow Ranger",
        "abilities": [
            {
                "id": 601,
                "name": "Frost Arrows",
                "description": "Adds a freezing effect to Drow's attacks, slowing enemy movement and dealing bonus damage.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "auto-cast",
                "damage_type": "physical",
                "affects": ["enemies"],
                "special_values": {
                    "attack_damage_bonus": [10, 15, 20, 25],
                    "move_speed_slow": [0.10, 0.25, 0.40, 0.55],
                    "slow_duration": [1.5, 1.5, 1.5, 1.5]
                },
                "cooldown": [0, 0, 0, 0],
                "mana_cost": [9, 10, 11, 12],
                "notes": "Frost Arrows is a Unique Attack Modifier that can be manually cast or set to autocast."
            },
            {
                "id": 602,
                "name": "Gust",
                "description": "Releases a powerful gust of wind that silences and knocks back enemies in a line, also applying a movement speed slow.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "directional",
                "damage_type": "none",
                "affects": ["enemies"],
                "special_values": {
                    "radius": [350, 350, 350, 350],
                    "distance": [450, 500, 550, 600],
                    "knockback_distance": [350, 350, 350, 350],
                    "silence_duration": [3, 4, 5, 6],
                    "move_speed_slow": [0.15, 0.30, 0.45, 0.60],
                    "slow_duration": [3, 4, 5, 6]
                },
                "cooldown": [19, 16, 13, 10],
                "mana_cost": [90, 90, 90, 90],
                "notes": "Knocks back enemies in a 350 radius cone up to 350 units."
            },
            {
                "id": 603,
                "name": "Multishot",
                "description": "Drow fires a volley of arrows in a cone behind the targets in front of her, dealing damage to enemies they pass through.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "point target",
                "damage_type": "physical",
                "affects": ["enemies"],
                "special_values": {
                    "radius_start": [150, 150, 150, 150],
                    "radius_end": [400, 400, 400, 400],
                    "range": [1250, 1250, 1250, 1250],
                    "arrow_count": [8, 12, 16, 20],
                    "arrow_damage": [65, 80, 95, 110],
                    "movement_slow": [0.30, 0.40, 0.50, 0.60],
                    "slow_duration": [2, 2, 2, 2]
                },
                "cooldown": [17, 14, 11, 8],
                "mana_cost": [70, 80, 90, 100],
                "notes": "Drow channels briefly before releasing the arrows."
            },
            {
                "id": 604,
                "name": "Marksmanship",
                "description": "Drow's precision and focus allow her to deal extra damage with her attacks. The bonus is lost when enemy heroes are near.",
                "type": "ultimate",
                "pierces_immunity": False,
                "behavior": "passive",
                "damage_type": "physical",
                "affects": ["self"],
                "special_values": {
                    "bonus_agility": [0, 0, 0],
                    "chance_to_pierce": [0, 0, 0],
                    "agility_multiplier": [0.25, 0.35, 0.45],
                    "disable_range": [400, 400, 400]
                },
                "cooldown": [0, 0, 0],
                "mana_cost": [0, 0, 0],
                "notes": "Provides a passive agility bonus based on Drow's current agility. Disabled when enemy heroes are within 400 range."
            }
        ],
        "innate_abilities": []
    },
    "anti_mage": {
        "id": 1,
        "name": "anti_mage",
        "localized_name": "Anti-Mage",
        "abilities": [
            {
                "id": 101,
                "name": "Mana Break",
                "description": "Burns an opponent's mana on each attack and deals damage equal to a percentage of the mana burned. Passively grants spell resistance.",
                "type": "passive",
                "pierces_immunity": False,
                "behavior": "passive",
                "damage_type": "physical",
                "affects": ["enemies"],
                "special_values": {
                    "mana_burned": [28, 40, 52, 64],
                    "damage_per_mana": [0.6, 0.6, 0.6, 0.6],
                    "spell_resistance": [15, 25, 35, 45]
                },
                "cooldown": [0, 0, 0, 0],
                "mana_cost": [0, 0, 0, 0],
                "notes": "Mana Break is a Unique Attack Modifier that does not stack with other mana burning effects."
            },
            {
                "id": 102,
                "name": "Blink",
                "description": "Short distance teleportation that allows Anti-Mage to move in and out of combat.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "point target",
                "damage_type": "none",
                "affects": ["self"],
                "special_values": {
                    "range": [925, 1000, 1075, 1150],
                    "cooldown_reduction_talent": [1.5, 1.5, 1.5, 1.5]
                },
                "cooldown": [15, 12, 9, 6],
                "mana_cost": [60, 60, 60, 60],
                "notes": "Anti-Mage briefly disappears from the map during the blink."
            },
            {
                "id": 103,
                "name": "Counterspell",
                "description": "Passively grants magic resistance. When activated, creates an anti-magic shell around Anti-Mage that reflects targeted enemy spells back to their caster.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "no target",
                "damage_type": "none",
                "affects": ["self", "enemies"],
                "special_values": {
                    "duration": [1.2, 1.2, 1.2, 1.2],
                    "cooldown": [15, 11, 7, 3]
                },
                "cooldown": [15, 11, 7, 3],
                "mana_cost": [45, 50, 55, 60],
                "notes": "Only reflects targeted spells, not area spells."
            },
            {
                "id": 104,
                "name": "Mana Void",
                "description": "Creates a dangerous antimagic field at a target's location that deals damage for each point of mana missing from the target. Nearby enemies also take damage.",
                "type": "ultimate",
                "pierces_immunity": False,
                "behavior": "unit target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "damage_per_mana": [0.8, 0.95, 1.1],
                    "radius": [450, 450, 450],
                    "stun_duration": [0.3, 0.45, 0.6]
                },
                "cooldown": [100, 80, 60],
                "mana_cost": [125, 200, 275],
                "notes": "Deals damage based on the target's missing mana, not current mana."
            }
        ],
        "innate_abilities": []
    },
    "axe": {
        "id": 2,
        "name": "axe",
        "localized_name": "Axe",
        "abilities": [
            {
                "id": 201,
                "name": "Berserker's Call",
                "description": "Axe taunts nearby enemies, forcing them to attack him while granting bonus armor.",
                "type": "active",
                "pierces_immunity": True,
                "behavior": "no target",
                "damage_type": "none",
                "affects": ["enemies", "self"],
                "special_values": {
                    "radius": [315, 315, 315, 315],
                    "duration": [1.8, 2.2, 2.6, 3],
                    "bonus_armor": [12, 13, 14, 15]
                },
                "cooldown": [17, 15, 13, 11],
                "mana_cost": [80, 90, 100, 110],
                "notes": "Forces enemies to attack Axe even if they are spell immune."
            },
            {
                "id": 202,
                "name": "Battle Hunger",
                "description": "Enrages an enemy, dealing damage over time until they kill a unit or the duration ends. Slows their movement speed.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "unit target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "damage_per_second": [16, 24, 32, 40],
                    "duration": [10, 10, 10, 10],
                    "move_speed_slow": [12, 12, 12, 12]
                },
                "cooldown": [20, 15, 10, 5],
                "mana_cost": [50, 60, 70, 80],
                "notes": "Damage stops if the target kills a unit."
            },
            {
                "id": 203,
                "name": "Counter Helix",
                "description": "When attacked, Axe has a chance to spin, dealing pure damage to nearby enemies.",
                "type": "passive",
                "pierces_immunity": False,
                "behavior": "passive",
                "damage_type": "pure",
                "affects": ["enemies"],
                "special_values": {
                    "chance": [20, 20, 20, 20],
                    "radius": [275, 275, 275, 275],
                    "damage": [70, 100, 130, 160]
                },
                "cooldown": [0.45, 0.4, 0.35, 0.3],
                "mana_cost": [0, 0, 0, 0],
                "notes": "Triggers on attack, even if the attack misses."
            },
            {
                "id": 204,
                "name": "Culling Blade",
                "description": "Axe executes an enemy below a health threshold, dealing massive damage. If successful, grants Axe and nearby allies bonus movement speed.",
                "type": "active",
                "pierces_immunity": True,
                "behavior": "unit target",
                "damage_type": "magical",
                "affects": ["enemies", "allies"],
                "special_values": {
                    "threshold": [250, 325, 400],
                    "damage": [150, 200, 250],
                    "speed_bonus": [30, 30, 30],
                    "speed_duration": [6, 6, 6]
                },
                "cooldown": [75, 65, 55],
                "mana_cost": [60, 120, 180],
                "notes": "Refreshes cooldown on successful kill."
            }
        ],
        "innate_abilities": [
            {
                "id": 205,
                "name": "Coat of Blood",
                "description": "Axe permanently gains bonus armor whenever an enemy dies to Culling Blade or within 400 range.",
                "type": "innate",
                "behavior": "passive",
                "affects": ["self"],
                "special_values": {
                    "armor_per_kill": [0.2, 0.3, 0.4, 0.5],
                    "culling_kill_multiplier": [3, 3, 3, 3]
                },
                "notes": "Scales with Culling Blade level. Stacks infinitely."
            }
        ]
    },
    "crystal_maiden": {
        "id": 5,
        "name": "crystal_maiden",
        "localized_name": "Crystal Maiden",
        "abilities": [
            {
                "id": 501,
                "name": "Crystal Nova",
                "description": "Creates a blast of damaging frost that slows enemy movement and attack speed in an area.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "point target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "radius": [425, 425, 425, 425],
                    "damage": [100, 150, 200, 250],
                    "movement_slow": [20, 30, 40, 50],
                    "attack_slow": [20, 30, 40, 50],
                    "duration": [4, 4.5, 5, 5.5]
                },
                "cooldown": [12, 11, 10, 9],
                "mana_cost": [130, 145, 160, 175],
                "notes": "Crystal Nova slows attack and movement speed of all enemies in the area of effect."
            },
            {
                "id": 502,
                "name": "Frostbite",
                "description": "Encases an enemy unit in ice, preventing movement and attack, while dealing damage over time.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "unit target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "duration": [1.5, 2, 2.5, 3],
                    "damage_per_second": [50, 60, 70, 80],
                    "cast_range": [550, 550, 550, 550]
                },
                "cooldown": [9, 8, 7, 6],
                "mana_cost": [120, 130, 140, 150],
                "notes": "Target cannot move or attack, but can still cast spells."
            },
            {
                "id": 503,
                "name": "Arcane Aura",
                "description": "Provides bonus mana regeneration to all allied heroes globally. Crystal Maiden receives additional regeneration.",
                "type": "passive",
                "pierces_immunity": False,
                "behavior": "passive",
                "damage_type": "none",
                "affects": ["allies", "self"],
                "special_values": {
                    "mana_regen": [1, 1.5, 2, 2.5],
                    "self_bonus": [2, 3, 4, 5]
                },
                "cooldown": [0, 0, 0, 0],
                "mana_cost": [0, 0, 0, 0],
                "notes": "Affects all allied heroes globally, including creep-heroes."
            },
            {
                "id": 504,
                "name": "Freezing Field",
                "description": "Crystal Maiden channels energy to unleash waves of damaging frost that slow attack and movement speed. Explosions happen randomly in the area of effect.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "no target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "radius": [810, 810, 810],
                    "explosion_radius": [250, 250, 250],
                    "damage_per_explosion": [105, 170, 250],
                    "explosion_interval": [0.1, 0.1, 0.1],
                    "move_slow": [30, 40, 50],
                    "attack_slow": [30, 40, 50],
                    "duration": [10, 10, 10]
                },
                "cooldown": [110, 100, 90],
                "mana_cost": [200, 400, 600],
                "notes": "Crystal Maiden is unable to move or perform actions during this channeled ultimate."
            }
        ],
        "innate_abilities": [
            {
                "id": 505,
                "name": "Crystallize",
                "description": "Crystal Maiden's presence causes ice to form around her, significantly slowing nearby enemies when she activates abilities.",
                "type": "innate",
                "behavior": "passive",
                "affects": ["enemies"],
                "special_values": {
                    "slow_amount": [15, 20, 25, 30],
                    "radius": [350, 350, 350, 350],
                    "duration": [2.5, 2.5, 2.5, 2.5]
                },
                "notes": "Crystallize applies a slowing effect to enemies whenever Crystal Maiden casts an ability."
            }
        ]
    },
    "juggernaut": {
        "id": 8,
        "name": "juggernaut",
        "localized_name": "Juggernaut",
        "abilities": [
            {
                "id": 801,
                "name": "Blade Fury",
                "description": "Juggernaut spins his blade, becoming immune to magic and dealing damage to enemy units in a small radius around him.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "no target",
                "damage_type": "magical",
                "affects": ["enemies", "self"],
                "special_values": {
                    "radius": [250, 250, 250, 250],
                    "duration": [5, 5, 5, 5],
                    "damage_per_second": [85, 110, 135, 160]
                },
                "cooldown": [42, 34, 26, 18],
                "mana_cost": [120, 110, 100, 90],
                "notes": "Juggernaut is spell immune during Blade Fury but can still be affected by some spells and items."
            },
            {
                "id": 802,
                "name": "Healing Ward",
                "description": "Summons a Healing Ward that heals all nearby allied units based on their maximum health.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "point target",
                "damage_type": "none",
                "affects": ["allies"],
                "special_values": {
                    "heal_percent": [3, 4, 5, 6],
                    "radius": [500, 500, 500, 500],
                    "movement_speed": [300, 325, 350, 375],
                    "duration": [25, 25, 25, 25],
                    "ward_hp": [2, 3, 4, 5]
                },
                "cooldown": [42, 38, 34, 30],
                "mana_cost": [120, 125, 130, 135],
                "notes": "The Healing Ward can be controlled and follows Juggernaut by default. It can be destroyed by enemies."
            },
            {
                "id": 803,
                "name": "Blade Dance",
                "description": "Gives Juggernaut a chance to deal critical damage on each attack.",
                "type": "passive",
                "pierces_immunity": False,
                "behavior": "passive",
                "damage_type": "physical",
                "affects": ["enemies"],
                "special_values": {
                    "crit_chance": [20, 25, 30, 35],
                    "crit_multiplier": [200, 200, 200, 200]
                },
                "cooldown": [0, 0, 0, 0],
                "mana_cost": [0, 0, 0, 0],
                "notes": "Critical strikes deal double damage."
            },
            {
                "id": 804,
                "name": "Omnislash",
                "description": "Juggernaut leaps toward the target enemy unit with a damaging attack and then slashes other nearby enemy units, jumping between them. Juggernaut is invulnerable during Omnislash.",
                "type": "ultimate",
                "pierces_immunity": False,
                "behavior": "unit target",
                "damage_type": "physical",
                "affects": ["enemies"],
                "special_values": {
                    "jumps": [3, 6, 9],
                    "damage": [200, 215, 230],
                    "jump_range": [425, 425, 425],
                    "duration": [3, 3.25, 3.5]
                },
                "cooldown": [130, 115, 100],
                "mana_cost": [200, 275, 350],
                "notes": "Juggernaut is invulnerable during Omnislash but can still use items."
            }
        ],
        "innate_abilities": []
    }
}

def save_hero_data(hero_data, file_name):
    """Save hero data to a JSON file"""
    file_path = os.path.join(OUTPUT_DIR, file_name)
    try:
        with open(file_path, 'w') as f:
            json.dump({"heroes": [hero_data]}, f, indent=2)
        return True
    except Exception as e:
        print(f"Error saving file: {e}")
        return False

def main():
    """Main function to save hardcoded hero data to files"""
    print(f"Writing ability data for {len(HERO_DATA)} heroes")
    
    success_count = 0
    for hero_name, hero_data in HERO_DATA.items():
        try:
            file_name = f"{hero_name}_abilities.json"
            if save_hero_data(hero_data, file_name):
                print(f"✓ Saved {file_name} with {len(hero_data['abilities'])} abilities and {len(hero_data.get('innate_abilities', []))} innate abilities")
                success_count += 1
        except Exception as e:
            print(f"✗ Error processing {hero_name}: {e}")
    
    print(f"\nSuccessfully processed {success_count} out of {len(HERO_DATA)} heroes")
    
    # Combine all heroes into one file
    try:
        import glob
        
        print("\nCombining all hero files...")
        all_heroes = {"heroes": []}
        
        # Find all hero ability files
        pattern = os.path.join(OUTPUT_DIR, "*_abilities.json")
        hero_files = glob.glob(pattern)
        
        # Filter out the example file and all_heroes file
        hero_files = [f for f in hero_files if not os.path.basename(f).startswith("all_") and not os.path.basename(f).startswith("hero_abilities_example")]
        
        print(f"Found {len(hero_files)} hero files")
        
        # Load each file and combine heroes
        for file_path in hero_files:
            try:
                with open(file_path, 'r') as f:
                    hero_data = json.load(f)
                    
                    # Add hero to combined data if it exists
                    if "heroes" in hero_data and len(hero_data["heroes"]) > 0:
                        hero = hero_data["heroes"][0]
                        all_heroes["heroes"].append(hero)
                        print(f"Added {hero['localized_name']} to combined data")
            except Exception as e:
                print(f"Error processing {file_path}: {e}")
        
        # Sort heroes by ID
        all_heroes["heroes"].sort(key=lambda h: h["id"])
        
        # Save the combined file
        output_file = os.path.join(OUTPUT_DIR, "all_heroes_abilities.json")
        with open(output_file, 'w') as f:
            json.dump(all_heroes, f, indent=2)
        
        print(f"Saved combined data with {len(all_heroes['heroes'])} heroes to {output_file}")
    
    except Exception as e:
        print(f"Error combining hero files: {e}")

if __name__ == "__main__":
    main()