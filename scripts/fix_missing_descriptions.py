#!/usr/bin/env python3
"""
Script to fix missing descriptions in Dota 2 hero abilities JSON files.
This will update the all_heroes_abilities.json file with hardcoded descriptions
for heroes that have missing descriptions.
"""
import json
import os
import sys

# Define hardcoded descriptions for abilities
ABILITY_DESCRIPTIONS = {
    # Anti-Mage
    "Mana Break": "Anti-Mage's attacks burn an opponent's mana on each hit and deal damage equal to a percentage of the mana burnt.",
    "Blink": "Anti-Mage teleports to a target point up to a limited distance away.",
    "Counterspell": "Anti-Mage creates an anti-magic shell around himself that reflects most targeted spells.",
    "Counterspell Ally": "Anti-Mage creates an anti-magic shell around an ally that reflects most targeted spells.",
    "Mana Void": "Anti-Mage creates a powerful blast at the target enemy unit that damages nearby enemies based on how much mana is missing from the target.",

    # Axe
    "Berserker's Call": "Axe taunts nearby enemy units, forcing them to attack him, while gaining bonus armor during the duration.",
    "Battle Hunger": "Enrages an enemy unit, causing it to take damage over time until it kills another unit or the duration ends. Slows the unit's movement by a percent and increases the caster's movement speed by the same percent.",
    "Counter Helix": "After taking damage, Axe performs a helix counter attack, dealing pure damage to all nearby enemies.",
    "Culling Blade": "Axe spots a weakness and strikes, instantly killing an enemy unit with low health, or dealing moderate damage otherwise. When an enemy hero is culled, its death is credited to Axe, and all of Axe's allies near the target gain bonus movement speed.",

    # Bane
    "Enfeeble": "Bane weakens an enemy hero, reducing their attack damage and casting capabilities.",
    "Brain Sap": "Bane saps the life force of an enemy unit, dealing pure damage and healing Bane for the same amount.",
    "Nightmare": "Puts a target unit to sleep, rendering it unable to act. It also infects nearby units with the current level of Nightmare.",
    "Fiend's Grip": "Bane grips an enemy unit, disabling it and dealing damage over time while channeling.",

    # Bloodseeker
    "Bloodrage": "Bloodseeker drives a unit into a bloodthirsty rage, increasing attack damage and causing it to take increased damage from all sources.",
    "Blood Rite": "Bloodseeker creates a ritual site that, after a delay, erupts to damage and silence all enemies within its range.",
    "Thirst": "Bloodseeker gains increased movement speed and attack damage when nearby enemies have low health.",
    "Rupture": "Bloodseeker causes a unit's skin to rupture, dealing heavy damage if the unit moves.",

    # Crystal Maiden
    "Crystal Nova": "Creates a burst of damaging frost that slows enemy movement and attack speed in an area.",
    "Frostbite": "Encases an enemy unit in ice, preventing movement and attack while dealing damage over time.",
    "Arcane Aura": "Provides additional mana regeneration to all allied heroes globally.",
    "Crystal Clone": "Creates a clone of Crystal Maiden that attacks enemies.",
    "Freezing Field": "Creates an ice storm around Crystal Maiden that slows enemies and causes random explosions of frost to damage enemies.",

    # Drow Ranger
    "Frost Arrows": "Adds a frost effect to Drow's attacks, slowing enemy movement.",
    "Gust": "Drow releases a gust of wind that knocks back and silences enemy units in a line.",
    "Multishot": "Drow fires arrows in a cone, dealing damage to all enemies hit.",
    "Marksmanship": "Grants Drow bonus agility and a chance for her attacks to pierce through enemy units when no heroes are nearby.",

    # Earthshaker
    "Fissure": "Earthshaker slams the ground, creating an impassable ridge of stone while stunning and damaging enemy units along its line.",
    "Enchant Totem": "Earthshaker empowers his totem, gaining bonus damage on his next attack and the ability to leap to a target area.",
    "Aftershock": "Causes the earth to shake underfoot, adding additional stun and damage to all of Earthshaker's abilities.",
    "Echo Slam": "Earthshaker slams the ground with his totem, sending out a damaging echo that is magnified by each enemy unit in range.",

    # Juggernaut
    "Blade Fury": "Juggernaut spins his blade, becoming immune to magic and dealing damage to nearby enemy units.",
    "Healing Ward": "Summons a healing ward that heals all nearby allied units based on their max health.",
    "Blade Dance": "Gives Juggernaut a chance to deal critical damage on each attack.",
    "Swiftslash": "Juggernaut performs a mini version of Omnislash, jumping to enemies and dealing damage.",
    "Omnislash": "Juggernaut leaps towards the target enemy unit with a damaging attack and then slashes other nearby enemy units, becoming invulnerable for the duration.",

    # Mirana
    "Starstorm": "Mirana calls down a shower of meteors to damage nearby enemy units.",
    "Sacred Arrow": "Mirana fires an arrow that stuns and damages enemy units it hits. The further the arrow travels, the greater the stun.",
    "Leap": "Mirana leaps forward into battle, temporarily increasing the speed of herself and nearby allies.",
    "Moonlight Shadow": "Mirana and nearby allies become invisible until the buff duration ends or they attack.",
    
    # Morphling
    "Waveform": "Morphling dissolves into a wave, surging forward and dealing damage to enemies in his path.",
    "Adaptive Strike (Agility)": "Morphling fires a blast of water toward an enemy, dealing damage and knocking back based on his Agility level.",
    "Adaptive Strike (Strength)": "Morphling fires a blast of water toward an enemy, stunning and dealing damage based on his Strength level.",
    "Attribute Shift (Agility Gain)": "Morphling shifts his form to increase his Agility while decreasing his Strength.",
    "Attribute Shift (Strength Gain)": "Morphling shifts his form to increase his Strength while decreasing his Agility.",
    "Morph": "Morphling transforms into a targeted hero, gaining their abilities.",
    
    # Phantom Lancer
    "Spirit Lance": "Phantom Lancer hurls a magic lance at a target enemy unit that damages and slows, while creating an illusion to attack the target.",
    "Doppelganger": "Phantom Lancer briefly vanishes and creates illusions to confuse enemies.",
    "Phantom Rush": "Phantom Lancer and nearby illusions gain the ability to quickly charge at a targeted enemy.",
    "Juxtapose": "Phantom Lancer's attacks have a chance to create illusions of himself.",
    
    # Puck
    "Illusory Orb": "Puck launches a magic orb that damages enemy units in its path.",
    "Waning Rift": "Puck releases a burst of faerie dust that damages and silences nearby enemy units.",
    "Phase Shift": "Puck temporarily shifts into another dimension where it is immune from harm.",
    "Ethereal Jaunt": "Allows Puck to teleport to its Illusory Orb's current location.",
    "Dream Coil": "Creates a coil of volatile magic that stuns enemy heroes and damages them. Enemies linked to the coil cannot move outside without taking damage and getting stunned.",
    
    # Pudge
    "Meat Hook": "Pudge launches a hook that impales the first unit it encounters, dragging it back to Pudge.",
    "Rot": "A toxic cloud that deals damage to enemy units around Pudge and slows their movement, at the cost of some of Pudge's health.",
    "Flesh Heap": "Pudge gains strength and magic resistance from heroes that die in his vicinity.",
    "Meat Shield": "Pudge creates a shield of flesh that absorbs damage and can be transferred to allies.",
    "Dismember": "Pudge chews on an enemy unit, disabling it and dealing damage while healing Pudge based on his strength.",
    
    # Razor
    "Plasma Field": "Razor releases a wave of electrified plasma that damages nearby enemies.",
    "Static Link": "Razor creates a link with an enemy hero, gradually siphoning its attack damage.",
    "Unstable Current": "Razor's unstable electrical currents damage nearby enemies who cast spells and increase his movement speed.",
    "Eye of the Storm": "Razor summons a lightning storm around him that strikes nearby enemy units with lightning bolts.",
    
    # Sand King
    "Burrowstrike": "Sand King burrows underground and resurfaces at the target point, damaging and stunning enemies in his path.",
    "Sand Storm": "Sand King creates a violent sandstorm around himself that damages enemy units and makes Sand King invisible.",
    "Caustic Finale": "Sand King's attacks poison enemies, causing them to explode on death, damaging nearby enemies.",
    "Epicenter": "After channeling, Sand King sends out a series of damaging pulses around himself that slow enemy units.",
    
    # Storm Spirit
    "Static Remnant": "Storm Spirit creates an explosive remnant of himself that lasts for a short duration and detonates when enemies come near it.",
    "Electric Vortex": "Storm Spirit creates a vortex of energy that pulls an enemy towards him.",
    "Overload": "Storm Spirit's attacks gain bonus damage and slowing effect after casting a spell.",
    "Ball Lightning": "Storm Spirit transforms into a ball of lightning that travels to a target point, damaging enemies along the way.",
    
    # Sven
    "Storm Hammer": "Sven unleashes his magical gauntlet at a target enemy, dealing damage and stunning enemy units in a small radius.",
    "Great Cleave": "Sven's attacks hit multiple targets with cleaving damage.",
    "Warcry": "Sven motivates nearby allied heroes for battle, increasing their armor and movement speed.",
    "God's Strength": "Sven channels his rogue strength, granting him bonus damage.",
    
    # Tiny
    "Avalanche": "Tiny tosses a chaotic avalanche of stones that damage and stun enemy units.",
    "Toss": "Tiny grabs the nearest unit and throws it to the target area, dealing damage where it lands.",
    "Tree Grab": "Tiny rips a tree from the ground, using it as a weapon to enhance his attacks and splash damage.",
    "Tree Volley": "Tiny throws a volley of trees at enemies, damaging enemies in a large area.",
    "Grow": "Tiny grows in size and power, gaining bonus movement speed, damage, and increasing his Toss damage.",
    
    # Vengeful Spirit
    "Magic Missile": "Vengeful Spirit launches a magic missile at an enemy unit, dealing damage and stunning.",
    "Wave of Terror": "Vengeful Spirit sends out a wave that reduces enemy armor and provides vision.",
    "Vengeance Aura": "Vengeful Spirit's presence increases the damage of nearby allied units.",
    "Nether Swap": "Vengeful Spirit swaps positions with a target unit, friend or foe.",
    
    # Windranger
    "Shackleshot": "Windranger fires a shackle at a target that latches to nearby trees or enemy units, binding them together.",
    "Powershot": "Windranger charges her bow for up to 1 second for a powerful shot that damages enemy units in a line.",
    "Windrun": "Windranger moves with incredible speed, evading physical attacks and gaining increased movement speed.",
    "Gale Force": "Windranger uses the wind to push enemies in a chosen direction.",
    "Focus Fire": "Windranger channels the wind to attack a single target with maximum attack speed.",

    # Witch Doctor
    "Paralyzing Cask": "Witch Doctor releases a bouncing cask that stuns and damages enemies it hits.",
    "Voodoo Restoration": "Witch Doctor creates a healing aura around himself that restores health to allied units.",
    "Maledict": "Witch Doctor curses enemy units in an area, causing them to take damage based on how much health they have lost since the curse began.",
    "Death Ward": "Witch Doctor summons a deadly ward to attack enemy heroes.",
    
    # Tidehunter
    "Gush": "Tidehunter hurls a watery blob that damages and slows an enemy unit.",
    "Kraken Shell": "Provides Tidehunter with damage block and removes debuffs if he takes too much damage.",
    "Anchor Smash": "Tidehunter swings his anchor, damaging nearby enemy units and reducing their damage.",
    "Ravage": "Slams the ground, causing tentacles to knock all nearby enemy units into the air, stunning and damaging them.",
    
    # Slardar
    "Guardian Sprint": "Slardar gains bonus movement speed while taking increased damage.",
    "Slithereen Crush": "Slardar slams the ground, stunning and damaging nearby enemy units.",
    "Bash of the Deep": "Gives Slardar a chance to bash and damage enemy units with his attacks.",
    "Corrosive Haze": "Reduces enemy armor to amplify physical damage and provides True Sight of the target.",
    
    # Shadow Shaman
    "Ether Shock": "Shadow Shaman releases a bolt of electricity that strikes multiple enemy units.",
    "Hex": "Shadow Shaman transforms an enemy unit into a harmless critter.",
    "Shackles": "Shadow Shaman binds an enemy unit in place, dealing damage over time.",
    "Mass Serpent Ward": "Shadow Shaman summons powerful serpent wards to attack enemy units.",
    
    # Lina
    "Dragon Slave": "Lina unleashes a wave of fire that damages enemies in a line.",
    "Light Strike Array": "Lina summons a column of flames that damages and stuns enemies.",
    "Fiery Soul": "Grants bonus attack and movement speed each time Lina casts a spell.",
    "Flame Cloak": "Lina surrounds herself with a cloak of flames that damages nearby enemies.",
    "Laguna Blade": "Lina unleashes a huge bolt of lightning that deals massive damage to a single target.",

    # Riki
    "Smoke Screen": "Riki throws a smoke bomb, creating a cloud that silences and blinds enemies within it.",
    "Blink Strike": "Riki teleports behind the target and backstabs them.",
    "Tricks of the Trade": "Riki phases out of the world while striking random nearby enemy units from the shadows.",
    "Cloak and Dagger": "Riki turns invisible when not attacking, and deals bonus damage when attacking from behind.",
    
    # Lich
    "Frost Blast": "Lich unleashes a blast of ice that damages and slows a target enemy and nearby foes.",
    "Frost Shield": "Lich encases an ally in a frigid barrier, slowing and damaging nearby enemies.",
    "Sinister Gaze": "Lich gazes upon a target, pulling them closer while stealing their mana.",
    "Ice Spire": "Lich creates a spire of ice that can be used to bounce his other abilities.",
    "Chain Frost": "Lich releases a ball of frost that bounces between nearby enemy units, dealing damage and slowing them.",
    
    # Enigma
    "Malefice": "Enigma focuses dark energies on a target, causing periodic damage and stuns.",
    "Demonic Conversion": "Enigma transforms a creep into three fragments of himself, creating eidolons that attack enemies.",
    "Midnight Pulse": "Enigma creates a field of dark resonance at a target location, damaging enemies based on their max health.",
    "Black Hole": "Enigma creates a vortex that pulls in nearby enemy units, disabling and damaging them.",
    
    # Shadow Fiend
    "Shadowraze": "Shadow Fiend razes the ground directly in front of him, dealing damage to enemies in a small area.",
    "Necromastery": "Shadow Fiend captures the souls of heroes and units he kills, giving him bonus damage.",
    "Feast of Souls": "Shadow Fiend consumes the souls of nearby enemies, dealing damage and gaining health.",
    "Presence of the Dark Lord": "The presence of Shadow Fiend reduces the armor of nearby enemy units.",
    "Requiem of Souls": "Shadow Fiend gathers his captured souls and releases them as lines of demonic energy, dealing damage based on the number of souls gathered.",
    
    # Io (Wisp)
    "Tether": "Io tethers to an allied hero, granting both units bonus movement speed and shared healing.",
    "Spirits": "Io summons five spirits that circle around it, damaging enemy units they come in contact with.",
    "Overcharge": "Io increases its attack speed and reduces damage taken, at the cost of its health and mana.",
    "Relocate": "Io teleports itself and a tethered ally to any location on the map for a short duration before returning.",
    
    # Sniper
    "Shrapnel": "Sniper covers the target area in a rain of shrapnel that damages enemies and reveals the terrain.",
    "Headshot": "Sniper's attacks have a chance to momentarily stun the target and deal bonus damage.",
    "Take Aim": "Increases Sniper's attack range, allowing him to attack from greater distances.",
    "Assassinate": "Sniper takes time to line up a long-range shot at a target enemy unit, then fires a devastating bullet.",
    
    # Lion
    "Earth Spike": "Lion summons spikes from the earth, damaging and stunning enemies in a line.",
    "Mana Drain": "Lion drains mana from a target enemy, transferring it to himself.",
    "Finger of Death": "Lion zaps an enemy unit with a huge burst of magical energy, dealing massive damage.",
    
    # Kunkka
    "Torrent": "Kunkka summons a torrent at the target area, damaging and launching enemy units into the air.",
    "Tidebringer": "Kunkka's sword gains a tide-bringing power, splashing all units in a large cleave with bonus damage.",
    "X Marks the Spot": "Kunkka marks an allied or enemy hero's position with an X, allowing him to recall them to that position.",
    "Tidal Wave": "Kunkka summons a massive wave that pushes enemies and their vessels.",
    "Ghostship": "Kunkka summons a ghostly ship that crashes through the battle, damaging enemies and stunning them at the crash point.",
    
    # Drow Ranger extras
    "Glacier": "Drow creates a massive glacier that slows and damages enemies in its path.",
    
    # Razor extras
    "Storm Surge": "Razor gains increased movement speed and vision range.",
    
    # Sand King extras
    "Stinger": "Sand King's spikes impale enemies, dealing damage and reducing healing.",
    
    # Shadow Shaman extras
    "Chicken Fingers": "Shadow Shaman transforms enemies into chickens.",
    
    # Tidehunter extras
    "Dead in the Water": "Tidehunter summons the deep sea to drown his enemies.",
    
    # Witch Doctor extras
    "Voodoo Switcheroo": "Witch Doctor exchanges positions with his Death Ward.",
    
    # Enigma extras
    "Demonic Summoning": "Enigma summons demonic forces to attack enemies.",
    
    # Faceless Void
    "Time Walk": "Faceless Void moves between realities, dodging damage taken in the previous few seconds.",
    "Time Dilation": "Faceless Void temporarily freezes time around nearby enemy heroes, pausing their ability cooldowns and slowing them.",
    "Time Lock": "Faceless Void's attacks lock an opponent in time, stunning and dealing bonus damage.",
    "Chronosphere": "Faceless Void creates a sphere of frozen time where all units except himself are locked in place.",
    "Time Zone": "Faceless Void creates a zone where time flows differently, slowing enemies inside it.",
    
    # Invoker
    "Quas": "One of Invoker's three elemental components. Provides health regeneration and grants strength to his spells.",
    "Wex": "One of Invoker's three elemental components. Provides attack speed and grants agility to his spells.",
    "Exort": "One of Invoker's three elemental components. Provides attack damage and grants intelligence to his spells.",
    "Invoke": "Invoker melds his three active elements to create a new spell.",
    "Invoked Spell 1": "First slot for Invoker's invoked spells.",
    "Invoked Spell 2": "Second slot for Invoker's invoked spells.",
    "Cold Snap": "Invoker chills an enemy with a sudden blast of ice, causing it to take damage and stun whenever it performs an action.",
    "Ghost Walk": "Invoker turns invisible, gaining extra movement speed while slowing nearby enemies.",
    "Ice Wall": "Invoker creates a wall of ice that blocks and slows enemy units.",
    "E.M.P.": "Invoker summons a magnetic field that detonates, draining mana from nearby enemies.",
    "Tornado": "Invoker summons a tornado that sweeps in a line, lifting enemies and making them vulnerable to his magic.",
    "Alacrity": "Invoker infuses an ally with energy, giving it bonus attack speed and damage.",
    "Sun Strike": "Invoker calls down a beam of intense solar energy, dealing damage to enemies caught in its area.",
    "Forge Spirit": "Invoker forges a being of pure flame, useful for scouting and dealing damage.",
    "Chaos Meteor": "Invoker pulls a flaming meteor from space onto the battlefield, damaging enemies in its path.",
    "Deafening Blast": "Invoker unleashes a sonic wave that knocks back, disarms, and damages enemies in its path."
}

def main():
    """Main function to update descriptions in all_heroes_abilities.json"""
    # Path to the combined file
    combined_file = "src/main/resources/data/abilities/all_heroes_abilities.json"
    
    if not os.path.exists(combined_file):
        print(f"Error: {combined_file} not found")
        sys.exit(1)
        
    # Load the combined file
    with open(combined_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    # Track changes
    descriptions_updated = 0
    heroes_updated = set()
    
    # Update descriptions for all abilities
    for hero in data["heroes"]:
        hero_name = hero["name"]
        updated_hero = False
        
        for ability in hero["abilities"]:
            ability_name = ability["name"]
            
            # Check if this ability has a missing description
            if "Description not found" in ability["description"]:
                # Use hardcoded description if available
                if ability_name in ABILITY_DESCRIPTIONS:
                    ability["description"] = ABILITY_DESCRIPTIONS[ability_name]
                    updated_hero = True
                    descriptions_updated += 1
                    print(f"Updated: {hero_name} - {ability_name}")
                else:
                    print(f"No description available for: {hero_name} - {ability_name}")
            
            # Also update the ability type based on name
            if ability_name == "Blade Dance" or ability_name == "Vengeance Aura" or ability_name == "Presence of the Dark Lord":
                ability["type"] = "passive"
                updated_hero = True
            
        if updated_hero:
            heroes_updated.add(hero_name)
    
    # Save the updated file
    with open(combined_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
    
    print(f"\nUpdated {descriptions_updated} descriptions across {len(heroes_updated)} heroes")
    print(f"File saved to {combined_file}")

if __name__ == "__main__":
    main()