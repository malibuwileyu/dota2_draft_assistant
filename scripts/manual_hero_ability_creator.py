#!/usr/bin/env python3
"""
Manual Dota 2 Hero Ability JSON Creator

This script creates a template JSON file for a hero's abilities that can be manually edited.
The structure follows the format defined in src/main/resources/data/abilities/hero_abilities_example.json.
"""
import json
import os
import re

# Configuration
OUTPUT_DIR = "src/main/resources/data/abilities"

# Ensure output directory exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

def create_hero_template(hero_name, hero_id):
    """Create a template JSON file for a hero's abilities"""
    # Format hero name for file
    file_name = hero_name.lower().replace(" ", "_").replace("-", "_")
    
    # Create ability templates for the hero
    if hero_name == "Axe":
        abilities = [
            {
                "id": hero_id * 100 + 1,
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
                "id": hero_id * 100 + 2,
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
                "id": hero_id * 100 + 3,
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
                "id": hero_id * 100 + 4,
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
        ]
        innate_abilities = [
            {
                "id": hero_id * 100 + 5,
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
    elif hero_name == "Crystal Maiden":
        abilities = [
            {
                "id": hero_id * 100 + 1,
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
                "id": hero_id * 100 + 2,
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
                "id": hero_id * 100 + 3,
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
                "id": hero_id * 100 + 4,
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
        ]
        innate_abilities = [
            {
                "id": hero_id * 100 + 5,
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
    elif hero_name == "Shadow Fiend":
        abilities = [
            {
                "id": hero_id * 100 + 1,
                "name": "Shadowraze (Near)",
                "description": "Shadow Fiend razes the ground directly in front of him, dealing damage to enemies in the area.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "no target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "damage": [90, 160, 230, 300],
                    "radius": [250, 250, 250, 250],
                    "distance": [200, 200, 200, 200]
                },
                "cooldown": [10, 10, 10, 10],
                "mana_cost": [75, 80, 85, 90],
                "notes": "The three Shadowraze abilities share cooldowns and interact with each other."
            },
            {
                "id": hero_id * 100 + 2,
                "name": "Shadowraze (Medium)",
                "description": "Shadow Fiend razes the ground at medium distance in front of him, dealing damage to enemies in the area.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "no target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "damage": [90, 160, 230, 300],
                    "radius": [250, 250, 250, 250],
                    "distance": [450, 450, 450, 450]
                },
                "cooldown": [10, 10, 10, 10],
                "mana_cost": [75, 80, 85, 90],
                "notes": "The three Shadowraze abilities share cooldowns and interact with each other."
            },
            {
                "id": hero_id * 100 + 3,
                "name": "Shadowraze (Far)",
                "description": "Shadow Fiend razes the ground far in front of him, dealing damage to enemies in the area.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "no target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "damage": [90, 160, 230, 300],
                    "radius": [250, 250, 250, 250],
                    "distance": [700, 700, 700, 700]
                },
                "cooldown": [10, 10, 10, 10],
                "mana_cost": [75, 80, 85, 90],
                "notes": "The three Shadowraze abilities share cooldowns and interact with each other."
            },
            {
                "id": hero_id * 100 + 4,
                "name": "Necromastery",
                "description": "Shadow Fiend captures the soul of units that die near him, gaining bonus damage for each soul captured.",
                "type": "passive",
                "pierces_immunity": False,
                "behavior": "passive",
                "damage_type": "none",
                "affects": ["self"],
                "special_values": {
                    "damage_per_soul": [2, 2, 2, 2],
                    "max_souls": [18, 24, 30, 36],
                    "soul_death_release": [30, 40, 50, 60]
                },
                "cooldown": [0, 0, 0, 0],
                "mana_cost": [0, 0, 0, 0],
                "notes": "Shadow Fiend loses half his souls on death. Souls can be gained from denying allied units."
            },
            {
                "id": hero_id * 100 + 5,
                "name": "Requiem of Souls",
                "description": "Shadow Fiend unleashes his collected souls as lines of demonic energy, dealing massive damage to nearby enemies. Enemies hit are slowed and have their attack damage reduced.",
                "type": "active",
                "pierces_immunity": False,
                "behavior": "no target",
                "damage_type": "magical",
                "affects": ["enemies"],
                "special_values": {
                    "damage_per_line": [80, 120, 160],
                    "line_count_limit": [15, 22, 29],
                    "slow_duration": [5, 5, 5],
                    "max_slow": [50, 60, 70],
                    "soul_death_release": [30, 40, 50]
                },
                "cooldown": [120, 110, 100],
                "mana_cost": [150, 175, 200],
                "notes": "The number of lines depends on the number of souls collected through Necromastery."
            }
        ]
        innate_abilities = [
            {
                "id": hero_id * 100 + 6,
                "name": "Presence of the Dark Lord",
                "description": "Shadow Fiend's ominous presence reduces the armor of nearby enemies.",
                "type": "innate",
                "behavior": "passive",
                "affects": ["enemies"],
                "special_values": {
                    "armor_reduction": [3, 4, 5, 6],
                    "radius": [900, 900, 900, 900]
                },
                "notes": "Affect all enemy units in the area, including buildings."
            }
        ]
    else:
        abilities = []
        innate_abilities = []
        for idx in range(4):
            ability_id = hero_id * 100 + idx + 1
            abilities.append({
                "id": ability_id,
                "name": f"Ability {idx+1}",
                "description": "Description goes here",
                "type": "active" if idx < 3 else "ultimate",
                "pierces_immunity": False,
                "behavior": ["unit target", "no target", "passive", "point target"][idx % 4],
                "damage_type": ["magical", "physical", "pure", "none"][idx % 4],
                "affects": ["enemies"],
                "special_values": {
                    "value1": [100, 200, 300, 400] if idx < 3 else [200, 300, 400],
                    "value2": [5, 10, 15, 20] if idx < 3 else [10, 15, 20]
                },
                "cooldown": [10, 9, 8, 7] if idx < 3 else [100, 90, 80],
                "mana_cost": [100, 110, 120, 130] if idx < 3 else [150, 225, 300],
                "notes": "This is a placeholder ability."
            })
        
        innate_abilities.append({
            "id": hero_id * 100 + 5,
            "name": "Innate Ability",
            "description": "Description of innate ability",
            "type": "innate",
            "behavior": "passive",
            "affects": ["self"],
            "special_values": {
                "bonus": [5, 10, 15, 20]
            },
            "notes": "This is a placeholder innate ability."
        })
    
    # Create hero data structure
    hero_data = {
        "id": hero_id,
        "name": file_name,
        "localized_name": hero_name,
        "abilities": abilities,
        "innate_abilities": innate_abilities,
        "synergies": [],
        "counters": []
    }
    
    # Save to JSON file
    file_path = os.path.join(OUTPUT_DIR, f"{file_name}_abilities.json")
    with open(file_path, 'w') as f:
        json.dump({"heroes": [hero_data]}, f, indent=2)
    
    print(f"Created template for {hero_name} at {file_path}")

def main():
    """Main function"""
    heroes = [
        {"name": "Axe", "id": 2},
        {"name": "Crystal Maiden", "id": 5},
        {"name": "Shadow Fiend", "id": 11},
        {"name": "Invoker", "id": 36}
    ]
    
    for hero in heroes:
        create_hero_template(hero["name"], hero["id"])
    
    print(f"Created templates for {len(heroes)} heroes")

if __name__ == "__main__":
    main()