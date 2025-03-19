# Dota 2 Hero Abilities Dataset

This directory contains JSON files describing Dota 2 heroes and their abilities, which are used by the Dota 2 Draft Assistant application for hero recommendation and team composition analysis.

## File Structure

- `hero_abilities_example.json`: Example format for hero ability files
- `*_abilities.json`: Individual hero ability files (one per hero)
- `all_heroes_abilities.json`: Combined file containing data for all heroes

## JSON Schema

Each hero file follows this structure:

```json
{
  "heroes": [
    {
      "id": 1,
      "name": "hero_name",
      "localized_name": "Hero Name",
      "abilities": [
        {
          "id": 101,
          "name": "Ability Name",
          "description": "Ability description",
          "type": "active|passive",
          "pierces_immunity": true|false,
          "behavior": "unit target|point target|no target|passive",
          "damage_type": "physical|magical|pure|none",
          "affects": ["enemies"|"allies"|"self"],
          "special_values": {
            "parameter1": [value1, value2, value3, value4],
            "parameter2": [value1, value2, value3, value4]
          },
          "cooldown": [c1, c2, c3, c4],
          "mana_cost": [m1, m2, m3, m4],
          "notes": "Additional information about the ability"
        }
        // More abilities...
      ],
      "innate_abilities": [
        // Similar structure to normal abilities, but with type="innate"
      ],
      "synergies": [],
      "counters": []
    }
  ]
}
```

## Data Generation

The data files were generated through several steps:

1. Template creation: Example hero files were created manually to establish the correct format
2. Template generation: `generate_hero_templates.py` was used to create templates for all heroes
3. Manual updates: The template files were manually updated with accurate ability information
4. Combination: `combine_heroes.py` was used to combine all individual hero files into `all_heroes_abilities.json`

## Synergies and Counters

The `synergies` and `counters` arrays are left empty in the initial data generation. These will be populated by the ML model based on:

- Statistical analysis of hero win rates when played together
- Analysis of ability interactions and synergies
- Evaluation of counter-play mechanics

## Notes

- Ability values are represented as arrays, with each element corresponding to a skill level (usually 1-4)
- Ultimate abilities typically have 3 levels instead of 4
- Some heroes have innate abilities that are always available
- The `pierces_immunity` field indicates whether an ability works against spell immune targets