# Dota 2 Draft Assistant - Data Collection

This directory contains scripts to collect professional match data from OpenDota API for use in the Dota 2 Draft Assistant application.

## Scripts Overview

### 1. `collect_dota2_data.py`

Main wrapper script that runs both data collection phases:

```bash
# Run both phases, collecting 100 matches
python collect_dota2_data.py --match-limit 100 --phase all

# Only fetch match IDs without details
python collect_dota2_data.py --match-limit 200 --phase pro_matches

# Only process existing match IDs file to get details
python collect_dota2_data.py --phase match_details
```

### 2. `fetch_pro_matches.py`

Fetches professional match IDs from OpenDota API:

```bash
# Fetch 100 pro matches
python fetch_pro_matches.py --limit 100

# Save to a specific directory with different rate limiting
python fetch_pro_matches.py --limit 200 --output-dir /custom/path --delay 2
```

### 3. `fetch_match_details.py`

Fetches detailed information for specific match IDs:

```bash
# Process a single match
python fetch_match_details.py --match-id 8215819476

# Process multiple matches from a file (one ID per line)
python fetch_match_details.py --match-file match_ids_20240317_120000.txt

# Custom output directory and API delay
python fetch_match_details.py --match-file match_ids.txt --output-dir /custom/path --delay 2
```

## Output Directory Structure

```
src/main/resources/data/matches/
├── details/             # Full match details JSON files
│   ├── match_12345.json
│   └── match_67890.json
├── drafts/              # Extracted draft sequences
│   ├── draft_12345.json
│   └── draft_67890.json
├── match_ids_*.txt      # Files containing match IDs
└── pro_matches_*.json   # Raw pro match list data
```

## Data Collection Process

1. **Phase 1**: Fetch professional match IDs from `/api/proMatches` endpoint
2. **Phase 2**: For each match ID, fetch detailed match data from `/api/matches/{match_id}` endpoint
3. The scripts extract draft information (picks, bans, sequence) from match details
4. The data is saved in JSON format for use by the Java application

## Java Integration

The collected data is used by the following Java components:

- `DraftDataRepository`: Loads and manages draft data from JSON files
- `DraftAnalysisService`: Analyzes drafts to generate recommendations
- `ProMatchDataAiDecisionEngine`: Uses the analysis to make AI decisions

## Rate Limiting

To respect OpenDota API rate limits:

- Default delay between API calls is 1 second
- Adjust using the `--delay` parameter if needed
- Consider getting an API key for production use

## Notes

- Data is stored in `/src/main/resources/data/matches` by default
- Draft data focuses on pick/ban sequences, team compositions, and match outcomes
- Only matches with complete draft information are processed
- The directory structure is automatically created if it doesn't exist