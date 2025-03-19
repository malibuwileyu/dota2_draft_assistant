# Dota 2 Draft Assistant - Data Pipeline

This document describes the data pipeline for the Dota 2 Draft Assistant application's hero recommendation system.

## Overview

The Dota 2 Draft Assistant uses a combination of structured data and machine learning to provide intelligent hero recommendations during the draft phase. The system analyzes hero abilities, synergies, and counters to suggest optimal picks based on the current draft state.

## Pipeline Components

### 1. Hero Ability Data Collection

We've created a structured dataset of hero abilities with the following components:

- **Source Files**: Individual JSON files for each hero (`*_abilities.json`)
- **Data Schema**: Standardized format including ability descriptions, special values, and behavior
- **Combined Dataset**: Aggregated file (`all_heroes_abilities.json`) containing all hero data

The data collection phase includes:
- Creating template files with the correct JSON structure
- Manually populating hero data with accurate ability information
- Combining all hero files into a single comprehensive dataset

### 2. Feature Engineering

Once the raw data is collected, we extract meaningful features:

- **Ability Classification**: Categorize abilities as stuns, slows, nukes, etc.
- **Numerical Representation**: Convert textual descriptions to numerical features
- **Semantic Analysis**: Extract key patterns and meanings from ability descriptions
- **Effect Quantification**: Evaluate the impact of abilities based on damage, duration, etc.

### 3. Model Development

The recommendation model consists of:

- **Embedding Layer**: Convert hero and ability data into vector representations
- **Synergy Detection**: Identify complementary ability combinations
- **Counter Analysis**: Determine effective counters to specific heroes
- **Team Composition Evaluation**: Assess overall team balance and effectiveness

### 4. Training Process

The model training involves:

- **Match Data Integration**: Use real match data to inform synergy and counter predictions
- **Supervised Learning**: Train on known effective combinations and counter-picks
- **Reinforcement Feedback**: Adjust recommendations based on win rate statistics
- **Cross-Validation**: Ensure the model generalizes well to new drafting scenarios

### 5. Integration with Application

The trained model is integrated with the Java application:

- **Real-time Inference**: Generate recommendations during the drafting phase
- **Explanatory Output**: Provide reasoning behind each recommendation
- **API Interface**: Connect the ML pipeline with the application's UI components

## Current Status

- ✅ Created structured JSON schema for hero ability data
- ✅ Generated template files for 30+ heroes
- ✅ Populated complete data for several key heroes
- ✅ Built data combination and processing scripts
- ⬜ Implement initial feature engineering for ability classification
- ⬜ Develop baseline ML model for recommendations
- ⬜ Integrate model with Java application

## Tools & Technologies

- **Data Format**: JSON for structured hero data
- **Processing**: Python scripts for data preparation and combination
- **ML Framework**: (To be determined - potential options include TensorFlow, PyTorch, or scikit-learn)
- **Integration**: Java interfaces for model inference
- **Acceleration**: Potential use of Groq LPU for real-time inference

## Next Steps

1. Complete the manual data entry for remaining hero ability files
2. Implement basic ability classification system using pattern matching
3. Develop initial ML model for hero recommendations
4. Integrate the model with the Java application
5. Refine the model based on user feedback and match outcomes