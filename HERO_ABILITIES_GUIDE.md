# Hero Abilities Guide

This guide explains how to use the tools we've created to manage hero abilities data for the Dota 2 Draft Assistant.

## Quick Start

Run the `setup_abilities.py` script to access all tools:

```bash
python setup_abilities.py
```

This will present you with a menu of options to generate templates, import data, and combine files.

## Tools Overview

We've created several tools to help you manage hero abilities data:

1. **Template Generator** (`generate_ability_templates.py`): Creates template Markdown files for hero abilities.
2. **Batch Import** (`batch_hero_abilities_import.py`): Imports ability data from Markdown files.
3. **Manual Entry** (`manual_hero_abilities_creator.py`): Interactive tool for manually inputting ability data.
4. **Combine All** (in various scripts): Combines individual hero files into one master file.

## Workflow Options

### Option 1: Manual Entry (Interactive)

The manual entry tool is good for entering data for a small number of heroes:

1. Run `python scripts/manual_hero_abilities_creator.py`
2. Select option 2 to manually input abilities for a hero
3. Enter the hero name or number
4. Follow the prompts to enter ability details
5. Choose to combine all files when done

### Option 2: Batch Import (Recommended)

This is the most efficient way to create data for multiple heroes:

1. Run `python scripts/generate_ability_templates.py` to create templates
2. Fill in the template files with hero ability data
3. Run `python scripts/batch_hero_abilities_import.py` to import the data
4. Select option 2 to import from file
5. Enter the path to your filled-in template file
6. Choose to combine all files when done

## Template Format

The template format is simple Markdown that looks like this:

```markdown
# Hero Name

## Ability Name (Optional: Ultimate/Passive/Innate)
- Description: Description text here
- Behavior: no target/unit target/point target/directional/passive/auto-cast
- Damage Type: magical/physical/pure/none
- Affects: enemies, allies, self
- Pierces Immunity: Yes/No
- Cooldown: 10, 9, 8, 7
- Mana Cost: 100, 120, 140, 160
- Notes: Additional notes

### Special Values
- damage: 100, 200, 300, 400
- duration: 2, 3, 4, 5

---
```

See `hero_ability_example.md` for a complete example with Anti-Mage and Axe.

## Tips for Completing All Heroes

1. Start by generating templates for all heroes:
   ```
   python scripts/generate_ability_templates.py
   ```
   Then select option 2 to create a single file with all heroes.

2. Use the `hero_ability_example.md` file as a reference for the format.

3. Work on filling in data for a few heroes at a time, saving your progress as you go.

4. Use online resources like the [Dota 2 Wiki](https://dota2.fandom.com/wiki/Heroes) to get accurate ability information.

5. Import your completed data regularly using the batch import tool.

6. Always run the combine function after importing to update the master file.

## Output Files

All hero ability files are saved in the `src/main/resources/data/abilities/` directory:

- Individual hero files: `{hero_name}_abilities.json`
- Combined file: `all_heroes_abilities.json`

The application uses the combined file to load all hero abilities at once.

## Help and Troubleshooting

If you encounter any issues:

- Check that your template files follow the exact format shown above
- Verify that hero names match those in the `heroes.json` file
- Look for syntax errors in your JSON files
- Run the tools with Python 3.6 or higher

For further assistance, please open an issue in the project repository.