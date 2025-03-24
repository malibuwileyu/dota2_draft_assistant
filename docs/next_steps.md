# Dota 2 Draft Assistant - Next Steps

This document outlines the planned enhancements for the Dota 2 Draft Assistant to improve functionality, performance, and personalization.

## 1. Database Implementation

**Current Issue:** The application uses JSON files for data storage, which is inefficient for shipping, updating, and querying.

**Solution:**
- [x] Design database schema for heroes, abilities, matches, players, teams, and preferences
- [x] Implement SQLite for local use with option to upgrade to PostgreSQL for cloud deployment
- [x] Create migration scripts to move existing JSON data to database
- [x] Implement database versioning system for schema updates
- [x] ~~Add backup/restore functionality for local database~~ (Not needed for MVP)
- [x] ~~Optimize query performance for draft-time recommendations~~ (Not needed for MVP)

**Expected Benefits:**
- Reduced application size
- Faster data access and filtering
- Easier updates to match/hero data
- Better separation of application and data

## 2. Steam Integration

**Current Issue:** The application doesn't use player-specific data for recommendations.

**Solution:**
- [x] Implement Steam OAuth login
- [x] Create user profile system tied to Steam ID
- [x] Add Steam API integration to fetch player match history
- [x] Store player hero preferences and performance metrics
- [x] Set up match synchronization and enrichment process
- [x] Add admin monitoring for data sync processes
- [ ] Calculate player-specific hero affinities based on pick frequency and win rate
- [ ] Adjust recommendations based on player history and comfort heroes
- [ ] Add privacy settings for data usage

**Expected Benefits:**
- Personalized draft recommendations
- Recommendations that account for player hero pools
- Better user investment through personalization

## 3. Team Integration

**Current Issue:** No way to associate groups of players as teams or target specific teams.

**Solution:**
- [ ] Create team profiles that can be selected during drafting
- [ ] Allow importing team data from OpenDota API
- [ ] Enable manual team creation and player assignment
- [ ] Implement team stats aggregation from individual player data
- [ ] Add team-vs-team analysis for targeted counter-drafting
- [ ] Create admin tools for team management

**Expected Benefits:**
- Strategic recommendations against specific teams
- Team-oriented drafting focus
- Support for amateur teams and scrims

## 4. Draft Recommendation Enhancements

**Current Issue:** Recommendations need more context and customization for the user.

**Solution:**
- [x] Integrate advanced AI recommendations using Groq API
- [x] Expand pick/ban display to include context reasons
- [ ] Add player-specific indicators (e.g., "One-trick player," "Signature hero")
- [ ] Implement role-based filtering of recommendations
- [x] Add confidence metrics to recommendations
- [x] Create visual indicators for synergies and counters
- [ ] Add historical matchup statistics for hero-vs-hero comparisons
- [x] Include performance metrics in recommendations (win rate, KDA, etc.)
- [x] Add win percentage visualization bar above drafting area

**Expected Benefits:**
- More informed drafting decisions
- Better understanding of recommendation rationale
- Higher-quality bans targeting specific players

## 5. Preference Engine

**Current Issue:** Cannot account for player preferences when drafting.

**Solution:**
- [ ] Develop preference weighting system for hero recommendations
- [ ] Allow manual adjustment of hero preference weights
- [ ] Create player preference profiles based on match history
- [ ] Implement one-trick player detection for opponents
- [ ] Add team preference analysis for recurring patterns
- [ ] Create preference override options for strategic drafts
- [ ] Develop algorithms to balance meta picks vs. comfort picks

**Expected Benefits:**
- Recommendations that account for player comfort
- Better ban targeting for opponent signature heroes
- Balance between meta strength and player proficiency

## 6. Data Management System

**Current Issue:** No automated data updating or aging of outdated data.

**Solution:**
- [x] Implement automated data collection schedule
- [x] Create caching system to minimize API calls
- [x] Add manual data refresh controls
- [x] Create data health monitoring dashboard
- [ ] Create patch detection system to identify new patches
- [ ] Develop data weighting algorithm based on patch age
- [ ] Implement data pruning for outdated matches
- [ ] Support offline mode with cached data
- [ ] Implement data verification and cleansing processes

**Expected Benefits:**
- Always up-to-date recommendations
- Proper weighting of current meta vs. historical data
- Optimal storage usage with relevant data

## 7. UI/UX Improvements

**Current Issue:** Limited context in recommendations and basic interface.

**Solution:**
- [ ] Redesign recommendation cards with expanded context
- [ ] Add tooltips with detailed reasoning
- [ ] Implement drag-and-drop drafting interface
- [ ] Create collapsible panels for detailed information
- [ ] Add player/team statistics section
- [ ] Implement light/dark theme options
- [ ] Design responsive layouts for different screen sizes
- [ ] Add animation for draft progress and recommendations
- [ ] Fix win probability bar colors (red/green not displaying correctly)
- [ ] Create meta ranking page showing top heroes by pick/ban rate
  - [ ] Add filtering by tournament/league
  - [ ] Include visual representation of hero popularity trends
  - [ ] Show win rate correlation with pick/ban rate
  - [ ] Add role-specific meta rankings

**Expected Benefits:**
- More intuitive drafting experience
- Better understanding of recommendations
- Professional-looking application
- Quick access to current meta information

## 8. Advanced Analytics

**Current Issue:** Basic analysis without deeper insights.

**Solution:**
- [ ] Implement timeline-based win probability modeling
- [ ] Add power spike analysis for team compositions
- [ ] Create draft simulation with AI-based opponent prediction
- [ ] Develop hero synergy visualizations
- [ ] Implement game phase strength analysis (early/mid/late)
- [ ] Add position-based recommendations
- [ ] Create historical trend analysis for meta shifts
- [ ] Implement detailed player performance analysis (post-match)
  - [ ] Advanced positioning metrics
  - [ ] Objective focus and map control
  - [ ] Fight participation and impact
  - [ ] Lane efficiency and resource utilization
  - [ ] Decision-making patterns

**Expected Benefits:**
- Deeper strategic insights
- Better understanding of team composition strengths/weaknesses
- More accurate predictions of draft outcomes
- Detailed player performance evaluation beyond basic stats

## Priority Order

1. Database Implementation (Foundation for other features)
2. Data Management System (Ensures data relevancy)
3. Steam Integration (Enables personalization)
4. Draft Recommendation Enhancements (Improves core functionality)
5. Preference Engine (Adds personalized intelligence)
6. Team Integration (Enables team-based strategy)
7. UI/UX Improvements (Enhances user experience)
8. Advanced Analytics (Adds depth to analysis)

## Technical Considerations

- Ensure GDPR compliance for user data
- [x] Implement caching for API requests to reduce rate limiting issues 
- [x] Implement robust error handling for API failures
- [x] Add API key support for improved rate limits
- Consider scalability for potential cloud deployment
- Ensure offline functionality for LAN tournaments
- Design for extensibility to other MOBAs in the future

## 10. Authentication System Enhancements

**Current Issue:** The authentication system has been stabilized but could use further improvements for robustness and user experience.

**Solution:**
- [ ] Add loading indicators during authentication process
- [ ] Implement better visual feedback during authentication process
- [ ] Add timeout notifications with retry buttons
- [ ] Implement an authentication state machine for clearer state management
- [ ] Add authentication attempt rate limiting
- [ ] Implement retry logic for network failures
- [ ] Add token refresh mechanisms for long-lived sessions
- [ ] Implement secure token storage with encryption
- [ ] Add session validation for sensitive operations

**Expected Benefits:**
- More reliable authentication flow
- Better user experience during login/logout
- Enhanced security for user sessions
- Improved error handling and recovery

## 9. Cloud Integration & API Key Management

**Current Issue:** API keys and secrets are managed locally, creating security risks and deployment challenges.

**Solution:**
- [x] Implement Groq API integration for advanced LLM-based recommendations
- [ ] Implement cloud secret management integration (AWS Secrets Manager, Azure Key Vault, or GCP Secret Manager)
- [ ] Configure automatic secret injection into application environment at runtime
- [ ] Set up service-to-service authentication using managed identities where possible
- [ ] Create a centralized configuration service for all application instances
- [ ] Implement secure authentication flow for end users that doesn't expose API keys
- [ ] Establish key rotation policies and automated rotation procedures
- [ ] Add monitoring for API usage and quotas

**Expected Benefits:**
- Enhanced security for sensitive credentials
- Simplified deployment across multiple environments
- Centralized credential management
- Support for key rotation without application redeployment
- Better scalability for cloud deployment
- Reduced risk of credential exposure