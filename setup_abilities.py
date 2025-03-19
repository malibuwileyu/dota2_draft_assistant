#!/usr/bin/env python3
"""
Setup script for Dota 2 Hero Abilities management

This script provides an easy interface to all the hero ability tools
to quickly generate templates, input ability data, and combine files.
"""
import os
import subprocess
import sys

SCRIPTS_DIR = "scripts"

def run_script(script_name, *args):
    """Run a Python script with arguments"""
    script_path = os.path.join(SCRIPTS_DIR, script_name)
    
    if not os.path.exists(script_path):
        print(f"ERROR: Script not found: {script_path}")
        return False
    
    try:
        cmd = [sys.executable, script_path] + list(args)
        subprocess.run(cmd)
        return True
    except Exception as e:
        print(f"Error running script: {e}")
        return False

def main():
    """Main function"""
    print("Dota 2 Hero Abilities Setup")
    print("==========================")
    
    print("\nThis script helps you manage hero ability data.")
    print("Choose from the following options:")
    print("\n1. Generate hero ability templates (markdown format)")
    print("2. Import hero ability data (batch mode)")
    print("3. Manually input hero ability data")
    print("4. Combine all hero ability files")
    print("5. Exit")
    
    choice = input("\nEnter choice: ")
    
    if choice == "1":
        run_script("generate_ability_templates.py")
    elif choice == "2":
        run_script("batch_hero_abilities_import.py")
    elif choice == "3":
        run_script("manual_hero_abilities_creator.py")
    elif choice == "4":
        # Run the combine function from one of our scripts
        run_script("manual_hero_abilities_creator.py")
    elif choice == "5":
        print("Exiting")
    else:
        print("Invalid choice")

if __name__ == "__main__":
    main()