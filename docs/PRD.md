# Dota 2 Draft Assistant - Product Requirements & Status

**Last Updated:** November 16, 2025  
**Current Version:** 0.1.0-SNAPSHOT

---

## 🎯 Vision & Core Goal

Build a **reasoning agent** that provides superior draft recommendations compared to existing tools (Valve Dota Plus, Overwolf DotaPlus) by using:
- **LLM-powered reasoning** (Groq API) instead of simple winrate aggregates
- **Contextual understanding** of draft state, synergies, and counters
- **Player-specific data** to personalize recommendations
- **Team composition analysis** for strategic depth

---

## 🚨 Current Critical Issues

### High Priority Bugs
1. **Match Sync Not Working Properly**
   - Only retrieving matches from 3+ days ago
   - Missing today's matches (3 played, 0 synced)
   - API returning error status 15 (Access Denied/Invalid Auth)
   - Location: `MatchHistoryService.retrieveMatchHistory()`

2. **Admin Panel Upload Issues**
   - Some uploads failing and showing "eternally waiting" status
   - Match enrichment showing "processed=2, successful=0, failed=0"
   - Possible queue/worker thread issue
   - Location: Match enrichment service

3. **Authentication Window Not Closing**
   - Browser window says "will close in 7 seconds" but doesn't
   - Requires manual close after successful auth
   - Location: `AuthCallbackServer` callback handling

4. **Groq API Configuration**
   - Recently fixed: Updated from decommissioned `llama3-8b-8192` to `llama3-70b-8192`
   - API URL corrected to `https://api.groq.com/openai`
   - Still needs valid API key for testing

5. **Test Failures**
   - 4 test failures out of 32 tests
   - `AuthCallbackServerTest.testCallbackHandling` - Connection refused
   - `SteamAuthenticationManagerTest.testExtractionWithResponseNonce` - Assertion failure
   - `DatabaseMigrationTest.testInitialize` - Mock verification failure
   - `MatchEnrichmentServiceTest.testEnqueueMultipleMatches` - Assertion failure

---

## ✅ What's Working

### Core Infrastructure
- ✅ **Application Compiles & Runs** (65 source files, Maven build successful)
- ✅ **Database System** (PostgreSQL with HikariCP connection pool, v10 migrations)
- ✅ **Steam Authentication** (OAuth login flow functional)
- ✅ **User Profile System** (Steam integration, profile persistence)
- ✅ **Hero Data** (124 heroes loaded with abilities)
- ✅ **Draft Simulation** (Captain's Mode implementation)
- ✅ **Hero Synergies** (7,754 synergies loaded)
- ✅ **Hero Counters** (15,809 counters loaded)
- ✅ **Match Repository** (Local caching, database storage)
- ✅ **Admin Monitoring** (Dashboard for sync/enrichment processes)

### UI Components
- ✅ Main application window with TabPane navigation
- ✅ Login/logout flow
- ✅ Player profile tab with match history
- ✅ Win probability visualization bar
- ✅ Draft interface with hero grid
- ✅ Team composition display
- ✅ Settings and sync controls

---

## 🏗️ Architecture

### Technology Stack
- **Language:** Java 17 (LTS)
- **UI:** JavaFX 17
- **Build:** Maven
- **Database:** PostgreSQL (production), SQLite (fallback)
- **DI:** Spring Core/Boot 2.7.16
- **APIs:** OpenDota API, Steam Web API, Groq API
- **Auth:** Steam OpenID/OAuth2
- **Logging:** SLF4J + Logback

### Package Structure
```
com.dota2assistant/
├── core/
│   ├── draft/         ✅ Draft rules and mechanics
│   ├── ai/            ⚠️  AI engines (Groq integrated, needs tuning)
│   └── analysis/      ✅ Draft analysis
├── data/
│   ├── api/           ✅ OpenDota, Steam API clients
│   ├── db/            ✅ PostgreSQL database manager
│   ├── model/         ✅ Hero, Match, Player models
│   ├── repository/    ✅ Data repositories
│   └── service/       ⚠️  Match sync/enrichment (needs fixing)
├── ui/
│   ├── controller/    ✅ JavaFX controllers
│   ├── view/          ✅ FXML views
│   └── component/     ✅ Reusable components
└── util/              ✅ Utility classes
```

---

## 📋 Implementation Roadmap

### Phase 1: Foundation & Cleanup ✅ (90% COMPLETE)

#### Completed
- [x] Remove fake ML components
- [x] Clean up redundant scripts
- [x] Consolidate documentation
- [x] Fix Groq API integration
- [x] Database migration system (v10)
- [x] Steam authentication flow
- [x] User profile management
- [x] Match history retrieval
- [x] Hero data loading (124 heroes)
- [x] Synergy/counter matrices
- [x] Admin monitoring dashboard

#### Critical Issues to Fix
- [ ] Match sync API authentication (error status 15)
- [ ] Auth window auto-close
- [ ] Match enrichment queue
- [ ] Failing unit tests (4 tests)

---

### Phase 2: Data Pipeline Stability 🔧 (60% COMPLETE)

#### High Priority
1. **Fix Match Sync Issues**
   - [ ] Debug Steam API authentication (error 15)
   - [ ] Investigate missing recent matches
   - [ ] Add better error handling and logging
   - [ ] Test incremental vs. full sync

2. **Fix Match Enrichment**
   - [ ] Debug queue processing (eternally waiting)
   - [ ] Investigate worker thread issues
   - [ ] Add enrichment retry mechanism
   - [ ] Improve admin panel status display

3. **Data Quality & Management**
   - [ ] Implement patch detection system
   - [ ] Add data weighting based on patch age
   - [ ] Create data pruning for outdated matches
   - [ ] Add data verification processes

#### Medium Priority
- [ ] Optimize API rate limiting
- [ ] Improve caching strategy
- [ ] Add offline mode with cached data
- [ ] Implement data conflict resolution

---

### Phase 3: Hero Data Completion 📊 (30% COMPLETE)

**Current Status:** ~40 heroes with full ability data out of 124

#### Tasks
- [ ] Complete ability data for remaining 84 heroes
- [ ] Validate ability descriptions and values
- [ ] Classify ability types (stun, slow, nuke, etc.)
- [ ] Identify spell immunity interactions
- [ ] Extract synergy patterns from abilities
- [ ] Create ability-based counter reasoning

#### Tools
- ✅ `scripts/enhanced_hero_scraper.py` - Main scraper
- ✅ `scripts/abilities_parser.py` - Parser module

---

### Phase 4: AI Reasoning Engine 🤖 (40% COMPLETE)

#### Completed
- [x] Groq LPU integration infrastructure
- [x] Basic recommendation generation
- [x] Fallback to heuristics when Groq unavailable
- [x] Draft state context building
- [x] Recommendation explanation generation

#### Remaining
- [ ] **Improve Prompt Engineering**
  - [ ] Add team composition context
  - [ ] Include ability interaction analysis
  - [ ] Provide counter reasoning
  - [ ] Explain timing windows and win conditions

- [ ] **Integration with Hero Data**
  - [ ] Feed ability data into prompts
  - [ ] Use synergy/counter matrices
  - [ ] Include player-specific hero pools

- [ ] **Testing & Tuning**
  - [ ] Test with real API key
  - [ ] Evaluate recommendation quality
  - [ ] Compare against baseline AI
  - [ ] Iterate on prompts based on feedback

---

### Phase 5: Player-Specific Recommendations 👤 (30% COMPLETE)

#### Completed
- [x] Player match history retrieval
- [x] Basic performance stats (win rate, KDA)
- [x] Match data storage in database
- [x] Player profile display

#### Remaining
- [ ] **Comfort Hero Detection**
  - [ ] Calculate hero pick frequency
  - [ ] Weight by win rate and consistency
  - [ ] Identify "signature heroes"
  - [ ] Mark one-trick players

- [ ] **Weighting Algorithm**
  - [ ] 60% global meta / 40% player-specific (standard)
  - [ ] 80% global / 20% player (<500 matches)
  - [ ] Recent focus (last 3 months) vs. all-time

- [ ] **UI Integration**
  - [ ] Show player stats on hero cards
  - [ ] Visual indicators for comfort heroes
  - [ ] Color-coded performance metrics
  - [ ] Tooltips showing weighting rationale

- [ ] **Draft Engine Integration**
  - [ ] Update win probability with player proficiency
  - [ ] Adjust recommendations based on hero pool
  - [ ] Consider opponent player tendencies for bans

---

### Phase 6: Hero Counter Reasoning 🎯 (NOT STARTED)

#### Tasks
- [ ] **Content Development**
  - [ ] Generate AI counter explanations
  - [ ] Verify for accuracy
  - [ ] Store in database for consistency
  - [ ] Focus on common matchups first

- [ ] **Counter Classification**
  - [ ] Lane matchup advantages/disadvantages
  - [ ] Mid-game interactions
  - [ ] Late-game interactions
  - [ ] Ability-specific counters

- [ ] **Context-Aware Reasoning**
  - [ ] Consider game state
  - [ ] Account for item timings
  - [ ] Include positioning factors

- [ ] **UI Implementation**
  - [ ] Collapsible "Counter Details" section
  - [ ] Expandable tooltips
  - [ ] Icon system for counter types
  - [ ] Lane-specific indicators

---

### Phase 7: Team Integration 🤝 (NOT STARTED)

#### Tasks
- [ ] **Team Data API**
  - [ ] Research Dota 2 team ID access
  - [ ] Retrieve team roster information
  - [ ] Validate "actual teams" (5 players, >3 games together)
  - [ ] Track roster changes

- [ ] **Team Profiles**
  - [ ] Create team selection interface
  - [ ] Import from OpenDota API
  - [ ] Manual team creation
  - [ ] Team stats aggregation

- [ ] **Team Analysis**
  - [ ] Team drafting pattern recognition
  - [ ] Ban recommendations targeting players
  - [ ] Team vs. team matchup history
  - [ ] Strategic counter-drafting

- [ ] **Temporary Teams**
  - [ ] Draft-specific roster assembly
  - [ ] Substitute management
  - [ ] Analysis without established history

---

### Phase 8: UI/UX Polish 🎨 (50% COMPLETE)

#### Completed
- [x] Main application layout
- [x] Win probability bar
- [x] Player profile tab
- [x] Admin monitoring interface

#### Remaining
- [ ] **Visual Improvements**
  - [ ] Fix win probability bar colors (red/green issue)
  - [ ] Add loading indicators for network operations
  - [ ] Implement smooth transitions
  - [ ] Add animation for draft progress

- [ ] **Recommendation Cards**
  - [ ] Redesign with expanded context
  - [ ] Add detailed tooltips
  - [ ] Create collapsible panels
  - [ ] Show synergy visualizations

- [ ] **Meta Ranking Page**
  - [ ] Top heroes by pick/ban rate
  - [ ] Filter by tournament/league
  - [ ] Hero popularity trends
  - [ ] Role-specific rankings

- [ ] **Settings & Customization**
  - [ ] Light/dark theme options
  - [ ] Responsive layouts
  - [ ] Privacy controls
  - [ ] Preference management

---

### Phase 9: Advanced Analytics 📈 (FUTURE)

#### Features
- [ ] Timeline-based win probability
- [ ] Power spike analysis
- [ ] Game phase strength (early/mid/late)
- [ ] Position-based recommendations
- [ ] Historical trend analysis
- [ ] Detailed player performance metrics
  - [ ] Positioning analysis
  - [ ] Objective control
  - [ ] Fight participation
  - [ ] Resource utilization

---

### Phase 10: Game Overlay 🎮 (FUTURE)

#### Features
- [ ] Game detection
- [ ] Overlay implementation
- [ ] Real-time draft monitoring
- [ ] In-game recommendation display
- [ ] Hotkey system
- [ ] Performance optimization

---

## 🚀 Current Sprint: Bug Fixes (Weeks 1-2)

### Sprint Goals
1. **Fix Match Sync Authentication** - Debug Steam API error 15
2. **Fix Match Enrichment Queue** - Debug worker threads
3. **Fix Auth Window Auto-Close** - Add JavaScript timer
4. **Fix Unit Tests** - Update mocks and assertions

### Success Criteria
- [ ] All unit tests passing (currently 28/32)
- [ ] Match sync success rate >95%
- [ ] Match enrichment completion rate >90%
- [ ] No manual window closing required

---

## 📊 Success Metrics

### Technical Metrics
- **Test Pass Rate:** 28/32 (87.5%) → Target: 100%
- **Match Sync Rate:** Unknown (error 15) → Target: >95%
- **Match Enrichment:** 0% success → Target: >90%
- **API Response Time:** <2s average ✅
- **Application Startup:** ~10s ✅
- **Database Queries:** <100ms ✅

### Feature Completeness
- **Hero Data:** 40/124 (32%) → Target: 100%
- **Player Recommendations:** 30% → Target: 100%
- **Counter Reasoning:** 0% → Target: 100%
- **AI Quality:** Unknown → Target: Better than Dota Plus
- **Team Integration:** 0% → Target: MVP

---

## 🔧 Build Commands

```bash
# Clean build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run application
mvn javafx:run

# Run specific test
mvn test -Dtest=TestClassName#testMethodName

# Package application
mvn javafx:jlink jpackage
```

---

## 📚 Documentation Structure

- **`docs/PRD.md`** - This file - Living product requirements and status
- **`docs/TDD.md`** - Technical design document
- **`docs/database_schema.md`** - Database schema reference
- **`docs/DATABASE_MIGRATION.md`** - Migration operational guide
- **`README.md`** - Project overview and getting started
- **`team_composition.md`** - Dota 2 domain knowledge reference
- **`.cursorrules`** - Development guidelines

---

## 🎯 Competitive Advantage

**vs. Valve Dota Plus:**
- ✅ Player-specific recommendations (they use global winrates)
- ✅ LLM reasoning with explanations (they just show numbers)
- ⚠️ Need better prompt engineering to fully leverage this

**vs. Overwolf DotaPlus:**
- ✅ Deeper integration with player data
- ✅ Team composition analysis
- ⚠️ Need to complete hero data for ability-based reasoning

**Path to Market Leadership:**
1. Fix critical bugs (match sync, enrichment) ← **IMMEDIATE**
2. Enhance Groq AI with better prompts ← **HIGH PRIORITY**
3. Add player-specific recommendations ← **HIGH PRIORITY**
4. Complete hero data for ability reasoning ← **MEDIUM PRIORITY**
5. Implement counter reasoning system ← **MEDIUM PRIORITY**
6. Add team integration for strategic depth ← **FUTURE**

---

**Next Review:** After Sprint 1 completion (bug fixes)
