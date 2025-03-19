# Anti-Mage

## Mana Break
- Description: Burns an opponent's mana on each attack and deals damage equal to a percentage of the mana burned. Passively grants spell resistance.
- Type: passive
- Behavior: passive
- Damage Type: physical
- Affects: enemies
- Pierces Immunity: No
- Cooldown: 0, 0, 0, 0
- Mana Cost: 0, 0, 0, 0
- Notes: Mana Break is a Unique Attack Modifier that does not stack with other mana burning effects.

### Special Values
- mana_burned: 28, 40, 52, 64
- damage_per_mana: 0.6, 0.6, 0.6, 0.6
- spell_resistance: 15, 25, 35, 45

## Blink
- Description: Short distance teleportation that allows Anti-Mage to move in and out of combat.
- Type: active
- Behavior: point target
- Damage Type: none
- Affects: self
- Pierces Immunity: No
- Cooldown: 15, 12, 9, 6
- Mana Cost: 60, 60, 60, 60
- Notes: Anti-Mage briefly disappears from the map during the blink.

### Special Values
- range: 925, 1000, 1075, 1150
- cooldown_reduction_talent: 1.5, 1.5, 1.5, 1.5

## Counterspell
- Description: Passively grants magic resistance. When activated, creates an anti-magic shell around Anti-Mage that reflects targeted enemy spells back to their caster.
- Type: active
- Behavior: no target
- Damage Type: none
- Affects: self, enemies
- Pierces Immunity: No
- Cooldown: 15, 11, 7, 3
- Mana Cost: 45, 50, 55, 60
- Notes: Only reflects targeted spells, not area spells.

### Special Values
- duration: 1.2, 1.2, 1.2, 1.2

## Mana Void (Ultimate)
- Description: Creates a dangerous antimagic field at a target's location that deals damage for each point of mana missing from the target. Nearby enemies also take damage.
- Type: ultimate
- Behavior: unit target
- Damage Type: magical
- Affects: enemies
- Pierces Immunity: No
- Cooldown: 100, 80, 60
- Mana Cost: 125, 200, 275
- Notes: Deals damage based on the target's missing mana, not current mana.

### Special Values
- damage_per_mana: 0.8, 0.95, 1.1
- radius: 450, 450, 450
- stun_duration: 0.3, 0.45, 0.6

---

# Axe

## Berserker's Call
- Description: Axe taunts nearby enemies, forcing them to attack him while granting bonus armor.
- Type: active
- Behavior: no target
- Damage Type: none
- Affects: enemies, self
- Pierces Immunity: Yes
- Cooldown: 17, 15, 13, 11
- Mana Cost: 80, 90, 100, 110
- Notes: Forces enemies to attack Axe even if they are spell immune.

### Special Values
- radius: 315, 315, 315, 315
- duration: 1.8, 2.2, 2.6, 3
- bonus_armor: 12, 13, 14, 15

## Battle Hunger
- Description: Enrages an enemy, dealing damage over time until they kill a unit or the duration ends. Slows their movement speed.
- Type: active
- Behavior: unit target
- Damage Type: magical
- Affects: enemies
- Pierces Immunity: No
- Cooldown: 20, 15, 10, 5
- Mana Cost: 50, 60, 70, 80
- Notes: Damage stops if the target kills a unit.

### Special Values
- damage_per_second: 16, 24, 32, 40
- duration: 10, 10, 10, 10
- move_speed_slow: 12, 12, 12, 12

## Counter Helix
- Description: When attacked, Axe has a chance to spin, dealing pure damage to nearby enemies.
- Type: passive
- Behavior: passive
- Damage Type: pure
- Affects: enemies
- Pierces Immunity: No
- Cooldown: 0.45, 0.4, 0.35, 0.3
- Mana Cost: 0, 0, 0, 0
- Notes: Triggers on attack, even if the attack misses.

### Special Values
- chance: 20, 20, 20, 20
- radius: 275, 275, 275, 275
- damage: 70, 100, 130, 160

## Culling Blade (Ultimate)
- Description: Axe executes an enemy below a health threshold, dealing massive damage. If successful, grants Axe and nearby allies bonus movement speed.
- Type: ultimate
- Behavior: unit target
- Damage Type: magical
- Affects: enemies, allies
- Pierces Immunity: Yes
- Cooldown: 75, 65, 55
- Mana Cost: 60, 120, 180
- Notes: Refreshes cooldown on successful kill.

### Special Values
- threshold: 250, 325, 400
- damage: 150, 200, 250
- speed_bonus: 30, 30, 30
- speed_duration: 6, 6, 6

## Coat of Blood (Innate)
- Description: Axe permanently gains bonus armor whenever an enemy dies to Culling Blade or within 400 range.
- Type: innate
- Behavior: passive
- Affects: self
- Notes: Scales with Culling Blade level. Stacks infinitely.

### Special Values
- armor_per_kill: 0.2, 0.3, 0.4, 0.5
- culling_kill_multiplier: 3, 3, 3, 3

---