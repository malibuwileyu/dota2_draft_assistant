# Dota 2 Draft Assistant - Implementation Plan

**Version:** 1.0  
**Last Updated:** December 4, 2025  
**Based On:** PRD_v3.md, TDD_v3.md  
**Target:** GA Release June 2025

---

## Executive Summary

This plan breaks down the v3 rebuild into **4 phases** with **48 implementation tasks**. Each task references specific requirements (REQ-XXX) from the PRD and can be worked as independent tickets.

**Timeline Overview:**
- **Phase 0 (Foundation):** 2 weeks - Project setup, no features
- **Phase 1 (Alpha):** 6 weeks - Core draft simulation (REQ-001 to REQ-010)
- **Phase 2 (Beta):** 6 weeks - AI & personalization (REQ-011 to REQ-016)
- **Phase 3 (Polish):** 4 weeks - Nice-to-haves + bug fixes (REQ-020 to REQ-024)

**Total Estimated Effort:** 18 weeks (4.5 months)

---

## Phase 0: Project Foundation (Weeks 1-2)

**Goal:** Set up the new project structure with Gradle, Java 21, and Clean Architecture scaffolding. No features—just the skeleton.

### TASK-001: Initialize Gradle Project

**Effort:** 4 hours  
**Dependencies:** None  
**Requirements:** Foundation for all REQ-*

**Description:**
Create new Gradle project with Java 21, JavaFX 21, and Spring Boot 3.2 dependencies.

**Deliverables:**
- [ ] `build.gradle.kts` with all dependencies from TDD_v3
- [ ] `settings.gradle.kts` with project name
- [ ] `.gitignore` updated for Gradle
- [ ] Gradle wrapper committed (`gradlew`, `gradlew.bat`)
- [ ] Project compiles with `./gradlew build`

**Acceptance Criteria:**
```
Given the project is cloned fresh
When I run ./gradlew build
Then it completes successfully with no errors
```

---

### TASK-002: Create Package Structure

**Effort:** 2 hours  
**Dependencies:** TASK-001  
**Requirements:** Foundation for all REQ-*

**Description:**
Create the Clean Architecture package structure as defined in TDD_v3.

**Deliverables:**
- [ ] `com.dota2assistant.config/` - Spring configuration
- [ ] `com.dota2assistant.domain.draft/` - Draft engine
- [ ] `com.dota2assistant.domain.recommendation/` - Recommendation logic
- [ ] `com.dota2assistant.domain.analysis/` - Team analysis
- [ ] `com.dota2assistant.domain.model/` - Domain models (Hero, Ability, etc.)
- [ ] `com.dota2assistant.application/` - Services
- [ ] `com.dota2assistant.infrastructure.persistence/` - SQLite repos
- [ ] `com.dota2assistant.infrastructure.api/` - API clients
- [ ] `com.dota2assistant.ui.controller/` - JavaFX controllers
- [ ] `com.dota2assistant.ui.component/` - Reusable UI components
- [ ] Placeholder classes in each package with `// TODO` comments

**Acceptance Criteria:**
```
Given the package structure exists
When I review any package
Then it has at least one placeholder class indicating its purpose
```

---

### TASK-003: Configure Spring Boot Application

**Effort:** 4 hours  
**Dependencies:** TASK-002  
**Requirements:** Foundation for all REQ-*

**Description:**
Set up Spring Boot 3.2 with minimal configuration for a desktop app (no web server).

**Deliverables:**
- [ ] `Dota2DraftAssistant.java` - Main class with JavaFX + Spring integration
- [ ] `AppConfig.java` - Component scanning configuration
- [ ] `application.properties` - Default properties
- [ ] Application starts and shows blank JavaFX window
- [ ] Spring context initializes without errors

**Acceptance Criteria:**
```
Given I run ./gradlew run
When the application starts
Then a blank JavaFX window appears within 5 seconds
And no Spring errors in console
```

---

### TASK-004: Set Up SQLite Database Infrastructure

**Effort:** 4 hours  
**Dependencies:** TASK-002  
**Requirements:** REQ-003 (Hero Database)

**Description:**
Configure SQLite with connection pooling and migration support.

**Deliverables:**
- [ ] `DatabaseConfig.java` - DataSource bean configuration
- [ ] `DatabaseMigrator.java` - SQL migration runner
- [ ] `migrations/V001__initial_schema.sql` - Hero, Ability, Synergy tables
- [ ] Database file created at `~/.dota2assistant/data.db`
- [ ] Migrations run on startup

**Acceptance Criteria:**
```
Given the application starts for the first time
When database initialization runs
Then data.db is created in user home directory
And all tables from V001 exist
```

---

### TASK-005: Create Domain Models (Records)

**Effort:** 4 hours  
**Dependencies:** TASK-002  
**Requirements:** REQ-003, REQ-001

**Description:**
Implement immutable domain models as Java records per TDD_v3.

**Deliverables:**
- [ ] `Hero.java` record - id, name, localizedName, primaryAttribute, etc.
- [ ] `Ability.java` record - id, name, description, abilityType, etc.
- [ ] `HeroAttributes.java` record - base stats
- [ ] `DraftState.java` record - immutable draft state
- [ ] `DraftAction.java` record - pick/ban action
- [ ] `Recommendation.java` record - hero + score + reasons
- [ ] Enums: `Team`, `DraftPhase`, `DraftMode`, `Attribute`, `AttackType`
- [ ] Unit tests for record equality and immutability

**Acceptance Criteria:**
```
Given a DraftState record
When I call withPick() or withBan()
Then a NEW DraftState is returned
And the original is unchanged
```

---

### TASK-006: Set Up Testing Infrastructure

**Effort:** 2 hours  
**Dependencies:** TASK-001  
**Requirements:** NFR-U (Test coverage)

**Description:**
Configure JUnit 5, Mockito, and AssertJ for testing.

**Deliverables:**
- [ ] Test dependencies in `build.gradle.kts`
- [ ] `src/test/java/` structure mirrors main
- [ ] Sample test class that passes
- [ ] `./gradlew test` runs successfully
- [ ] Test report generated in `build/reports/tests/`

**Acceptance Criteria:**
```
Given I run ./gradlew test
When tests complete
Then a test report is generated
And coverage report shows 0% (no production code yet)
```

---

### TASK-007: Import Hero Data from v1

**Effort:** 4 hours  
**Dependencies:** TASK-004, TASK-005  
**Requirements:** REQ-003

**Description:**
Migrate existing hero JSON data from v1 into new SQLite schema.

**Deliverables:**
- [ ] Data migration script (can be Gradle task or standalone)
- [ ] All 124+ heroes imported with attributes
- [ ] All abilities imported per hero
- [ ] Role and position data imported
- [ ] Verification query confirms data integrity

**Acceptance Criteria:**
```
Given the migration script runs
When I query SELECT COUNT(*) FROM heroes
Then the result is >= 124
And each hero has at least 4 abilities
```

---

### Phase 0 Milestone Checklist

- [ ] `./gradlew build` passes
- [ ] `./gradlew run` shows blank window
- [ ] `./gradlew test` runs (even if no real tests)
- [ ] SQLite database created with schema
- [ ] 124+ heroes in database
- [ ] All domain model records compile
- [ ] Clean Architecture packages exist

**Phase 0 Complete When:** All above items checked, code committed to `feature/v3-foundation` branch.

---

## Phase 1: Alpha - Core Draft Simulation (Weeks 3-8)

**Goal:** Implement REQ-001 through REQ-010. Fully functional draft simulation with local recommendations. No network features.

### Sprint 1.1: Draft Engine (Weeks 3-4)

---

### TASK-008: Implement DraftEngine Interface

**Effort:** 2 hours  
**Dependencies:** TASK-005  
**Requirements:** REQ-001, REQ-002

**Description:**
Define the draft engine interface that both Captain's Mode and All Pick will implement.

**Deliverables:**
- [ ] `DraftEngine.java` interface with methods:
  - `initDraft(DraftMode, boolean timerEnabled)`
  - `pickHero(DraftState, Hero) → DraftState`
  - `banHero(DraftState, Hero) → DraftState`
  - `undo(DraftState) → DraftState`
  - `isComplete(DraftState) → boolean`
  - `getCurrentTeam(DraftState) → Team`
  - `getCurrentPhase(DraftState) → DraftPhase`

**Acceptance Criteria:**
```
Given the interface is defined
When I implement a mock
Then all methods have clear contracts via Javadoc
```

---

### TASK-009: Implement Captain's Mode Draft Engine

**Effort:** 8 hours  
**Dependencies:** TASK-008  
**Requirements:** REQ-001

**Description:**
Implement the full Captain's Mode draft sequence per TDD_v3.

**Deliverables:**
- [ ] `CaptainsModeDraft.java` implementing `DraftEngine`
- [ ] 24-action sequence: Ban1(7) → Pick1(2) → Ban2(3) → Pick2(6) → Ban3(4) → Pick3(2)
- [ ] Correct team turns (ABBABBA for Ban1, AB for Pick1, etc.)
- [ ] State transitions return new immutable `DraftState`
- [ ] Validation: can't pick during ban phase, can't select unavailable hero
- [ ] 100% unit test coverage for this class

**Acceptance Criteria:**
```
Given a new Captain's Mode draft
When I execute all 24 actions correctly
Then the draft completes with 5 picks per team and 7 bans per team

Given it's Ban Phase 1
When I try to pick a hero
Then an IllegalStateException is thrown with message "Cannot pick during ban phase"
```

---

### TASK-010: Implement All Pick Draft Engine

**Effort:** 4 hours  
**Dependencies:** TASK-008  
**Requirements:** REQ-002

**Description:**
Implement All Pick draft mode (simpler than Captain's Mode).

**Deliverables:**
- [ ] `AllPickDraft.java` implementing `DraftEngine`
- [ ] No enforced turn order (both teams can pick anytime)
- [ ] No bans in standard All Pick
- [ ] Draft complete when both teams have 5 heroes
- [ ] Validation: can't pick already-picked hero
- [ ] Unit tests

**Acceptance Criteria:**
```
Given an All Pick draft
When both teams have 5 picks
Then isComplete() returns true

Given Radiant picked Anti-Mage
When Dire tries to pick Anti-Mage
Then an IllegalArgumentException is thrown
```

---

### TASK-011: Implement Undo/Redo Functionality

**Effort:** 4 hours  
**Dependencies:** TASK-009  
**Requirements:** REQ-007

**Description:**
Add undo/redo capability to draft engine using immutable state history.

**Deliverables:**
- [ ] `DraftState` includes `List<DraftAction> history`
- [ ] `undo()` returns previous state from history
- [ ] Redo implemented via action replay
- [ ] Undo at start returns same state (or throws)
- [ ] New action clears redo stack
- [ ] Unit tests for undo/redo edge cases

**Acceptance Criteria:**
```
Given I made 3 picks
When I undo twice
Then the draft state reflects only 1 pick

Given I undid an action
When I make a new pick
Then redo is no longer available
```

---

### TASK-012: Create DraftService (Application Layer)

**Effort:** 4 hours  
**Dependencies:** TASK-009, TASK-010  
**Requirements:** REQ-001, REQ-002

**Description:**
Create the service layer that coordinates draft operations and loads heroes.

**Deliverables:**
- [ ] `DraftService.java` with `@Service` annotation
- [ ] Injects `HeroRepository` to load available heroes
- [ ] Holds current `DraftState` (mutable at service level)
- [ ] Methods: `initDraft()`, `pickHero(id)`, `banHero(id)`, `undo()`, `getCurrentState()`
- [ ] Translates hero IDs to Hero objects

**Acceptance Criteria:**
```
Given DraftService is injected
When I call initDraft(CAPTAINS_MODE, false)
Then getCurrentState() returns a valid initial state with all heroes available
```

---

### Sprint 1.2: Hero Grid UI (Weeks 5-6)

---

### TASK-013: Implement HeroRepository

**Effort:** 4 hours  
**Dependencies:** TASK-004, TASK-007  
**Requirements:** REQ-003

**Description:**
SQLite repository for hero data access.

**Deliverables:**
- [ ] `HeroRepository.java` interface in domain
- [ ] `SqliteHeroRepository.java` in infrastructure
- [ ] Methods: `findAll()`, `findById(id)`, `findByName(name)`, `findWithAbilities(id)`
- [ ] Caches hero list on first load (heroes don't change during runtime)
- [ ] Integration test with real SQLite

**Acceptance Criteria:**
```
Given the database has 124 heroes
When I call findAll()
Then 124 Hero records are returned
And each has a non-null localizedName
```

---

### TASK-014: Create Hero Grid FXML View

**Effort:** 6 hours  
**Dependencies:** TASK-003  
**Requirements:** REQ-004

**Description:**
JavaFX FXML layout for the hero selection grid.

**Deliverables:**
- [ ] `HeroGrid.fxml` - Grid layout with search/filter controls
- [ ] `HeroGridController.java` - Controller with <200 lines
- [ ] Search TextField filters heroes by name
- [ ] Attribute filter ComboBox (All/Str/Agi/Int/Universal)
- [ ] Role filter ComboBox (All/Carry/Support/etc.)
- [ ] GridPane or FlowPane for hero cards
- [ ] CSS styling for dark theme

**Acceptance Criteria:**
```
Given the hero grid is displayed
When I type "anti" in search
Then only Anti-Mage (and any other matching heroes) are visible

Given I select "Strength" filter
When the grid updates
Then only Strength heroes are shown
```

---

### TASK-015: Create HeroCard Component

**Effort:** 4 hours  
**Dependencies:** TASK-014  
**Requirements:** REQ-004

**Description:**
Reusable hero card component showing portrait and name.

**Deliverables:**
- [ ] `HeroCard.java` extends VBox or custom Region
- [ ] Shows hero portrait image (64x64 or 128x128)
- [ ] Shows localized name below portrait
- [ ] Hover effect (slight scale or border)
- [ ] Disabled state (grayed out, unclickable) for picked/banned heroes
- [ ] Highlighted state (golden border) for recommended heroes
- [ ] Click handler property

**Acceptance Criteria:**
```
Given a hero is banned
When the grid renders
Then that hero's card is grayed out and unclickable

Given a hero is in recommendations
When the grid renders
Then that hero's card has a golden highlight
```

---

### TASK-016: Load Hero Images

**Effort:** 4 hours  
**Dependencies:** TASK-015  
**Requirements:** REQ-003

**Description:**
Load hero portrait images from resources or CDN.

**Deliverables:**
- [ ] Copy hero images from v1 `resources/images/heroes/`
- [ ] `ImageCache.java` utility for lazy loading and caching
- [ ] Fallback to placeholder image if hero image missing
- [ ] Images load asynchronously (don't block UI)

**Acceptance Criteria:**
```
Given the app starts
When the hero grid loads
Then all hero images appear within 2 seconds
And no "image not found" errors in console
```

---

### TASK-017: Implement Hero Selection Flow

**Effort:** 6 hours  
**Dependencies:** TASK-012, TASK-014, TASK-015  
**Requirements:** REQ-001, REQ-004

**Description:**
Wire hero grid clicks to draft service actions.

**Deliverables:**
- [ ] Clicking a hero during pick phase calls `draftService.pickHero(id)`
- [ ] Clicking a hero during ban phase calls `draftService.banHero(id)`
- [ ] Grid updates to disable picked/banned heroes
- [ ] Error handling for invalid actions (show alert)
- [ ] Keyboard support: type to search, Enter to select first match

**Acceptance Criteria:**
```
Given it's Radiant's ban phase
When I click on Anti-Mage
Then Anti-Mage is banned for both teams
And Anti-Mage's card becomes disabled
And it becomes Dire's turn
```

---

### Sprint 1.3: Team Display & Recommendations (Weeks 7-8)

---

### TASK-018: Create Team Panel Component

**Effort:** 4 hours  
**Dependencies:** TASK-015  
**Requirements:** REQ-008

**Description:**
UI component showing a team's picks and bans.

**Deliverables:**
- [ ] `TeamPanel.java` or `TeamPanel.fxml`
- [ ] Shows team name (Radiant/Dire) with color indicator
- [ ] 5 pick slots (filled or empty placeholder)
- [ ] Ban section showing team's bans
- [ ] Updates reactively when draft state changes
- [ ] Active turn indicator (glowing border)

**Acceptance Criteria:**
```
Given Radiant has picked 2 heroes
When viewing the Radiant panel
Then 2 hero portraits are shown in pick slots
And 3 empty placeholders remain
```

---

### TASK-019: Create Draft Interface Layout

**Effort:** 6 hours  
**Dependencies:** TASK-014, TASK-018  
**Requirements:** REQ-001, REQ-004, REQ-008

**Description:**
Main draft screen combining all components.

**Deliverables:**
- [ ] `DraftView.fxml` - Main draft layout
- [ ] `DraftController.java` - Coordinates sub-controllers (<200 lines)
- [ ] Layout: Radiant panel (left) | Hero grid + Recommendations (center) | Dire panel (right)
- [ ] Phase indicator at top (e.g., "BAN PHASE 1 - Radiant's Turn")
- [ ] Undo/Reset buttons in toolbar
- [ ] Responsive to window resize (min 1024px width)

**Acceptance Criteria:**
```
Given a draft is in progress
When I view the draft interface
Then I see both team panels, the hero grid, and the current phase
And all elements are properly aligned and readable
```

---

### TASK-020: Implement Local Recommendation Engine

**Effort:** 8 hours  
**Dependencies:** TASK-005, TASK-013  
**Requirements:** REQ-005

**Description:**
Scoring algorithm for hero recommendations without LLM.

**Deliverables:**
- [ ] `RecommendationEngine.java` in domain layer
- [ ] `SynergyScorer.java` - scores hero synergy with allies
- [ ] `CounterScorer.java` - scores hero effectiveness vs enemies
- [ ] `RoleScorer.java` - scores based on team role gaps
- [ ] `MetaScorer.java` - scores based on global pick/win rates
- [ ] Weighted combination per TDD_v3 formula
- [ ] Returns top N heroes sorted by score
- [ ] Unit tests with mock data

**Acceptance Criteria:**
```
Given my team has 3 physical damage heroes
When I request recommendations
Then magical damage heroes score higher on damage balance

Given the enemy has Phantom Assassin
When I request recommendations
Then heroes with break/evasion counters (Axe, MKB builders) score higher
```

---

### TASK-021: Import Synergy/Counter Data

**Effort:** 4 hours  
**Dependencies:** TASK-004  
**Requirements:** REQ-005

**Description:**
Import synergy and counter matrices from v1 or compute fresh.

**Deliverables:**
- [ ] `hero_synergies` table populated
- [ ] `hero_counters` table populated
- [ ] Data for all hero pairs (124 × 123 = 15,252 pairs per matrix)
- [ ] Synergy score: win rate when on same team
- [ ] Counter score: win rate when facing each other
- [ ] Migration script or data import task

**Acceptance Criteria:**
```
Given the data is imported
When I query synergy for Crystal Maiden + Juggernaut
Then a synergy score > 0.5 is returned (they synergize well)

When I query counter for Anti-Mage vs Storm Spirit
Then a counter score > 0.5 is returned (AM counters Storm)
```

---

### TASK-022: Create Recommendation Panel UI

**Effort:** 4 hours  
**Dependencies:** TASK-020  
**Requirements:** REQ-005

**Description:**
UI component showing top 5 recommendations.

**Deliverables:**
- [ ] `RecommendationPanel.java` or FXML
- [ ] Shows 5 recommendation cards
- [ ] Each card: hero portrait, name, score (%), brief reason
- [ ] Clicking a recommendation highlights that hero in grid
- [ ] Double-clicking selects the hero
- [ ] Updates after each pick/ban

**Acceptance Criteria:**
```
Given a draft is in progress
When recommendations update
Then 5 heroes are shown with scores
And the highest score is first

Given I click a recommendation
When the grid updates
Then that hero is highlighted with a golden border
```

---

### TASK-023: Implement Win Probability Calculation

**Effort:** 4 hours  
**Dependencies:** TASK-020  
**Requirements:** REQ-006

**Description:**
Calculate and display win probability based on draft state.

**Deliverables:**
- [ ] `WinProbabilityCalculator.java` in domain
- [ ] Uses synergy/counter scores aggregated
- [ ] Returns 0-100% for Radiant win probability
- [ ] `WinProbabilityBar.java` UI component
- [ ] Visual bar with Radiant green / Dire red split
- [ ] Percentage label

**Acceptance Criteria:**
```
Given teams are empty
When win probability calculates
Then it returns approximately 50%

Given Radiant has strong counters to all Dire heroes
When win probability calculates
Then Radiant's percentage is > 60%
```

---

### TASK-024: Implement Perspective Toggle

**Effort:** 2 hours  
**Dependencies:** TASK-019  
**Requirements:** REQ-009

**Description:**
Allow user to switch which team they're "playing as".

**Deliverables:**
- [ ] Toggle button or radio: "Playing as: Radiant / Dire"
- [ ] Recommendations update for selected team
- [ ] Visual indicator of "your" team vs "enemy" team
- [ ] Persists selection across draft reset

**Acceptance Criteria:**
```
Given I select "Dire" as my team
When recommendations generate
Then they are optimized for Dire's next pick
And the Dire panel is visually highlighted as "my team"
```

---

### TASK-025: Create Home/Dashboard Screen

**Effort:** 4 hours  
**Dependencies:** TASK-019  
**Requirements:** Foundation

**Description:**
Landing screen with navigation to draft modes.

**Deliverables:**
- [ ] `HomeView.fxml` + `HomeController.java`
- [ ] "New Draft" button → Mode selection dialog
- [ ] Mode selection: Captain's Mode / All Pick
- [ ] Timer toggle option
- [ ] Navigation to Settings
- [ ] Recent drafts list (placeholder for now)

**Acceptance Criteria:**
```
Given I launch the app
When the home screen loads
Then I see "New Draft" and "Settings" options

Given I click "New Draft"
When the mode dialog appears
Then I can select Captain's Mode or All Pick
And optionally enable timer
```

---

### TASK-026: Implement Application Packaging

**Effort:** 8 hours  
**Dependencies:** All prior tasks  
**Requirements:** REQ-010

**Description:**
Configure jpackage for native installers on all platforms.

**Deliverables:**
- [ ] Gradle jlink + jpackage configuration per TDD_v3
- [ ] macOS: `.dmg` installer
- [ ] Windows: `.msi` installer
- [ ] Linux: `.deb` package
- [ ] Application icon included
- [ ] Installer tested on clean VM/machine for each platform

**Acceptance Criteria:**
```
Given I run ./gradlew jpackage on macOS
When the build completes
Then a .dmg file is created in build/jpackage/

Given I install the .dmg on a Mac without Java
When I launch the app
Then it runs successfully (bundled JRE)
```

---

### Phase 1 Milestone Checklist (Alpha)

- [ ] Captain's Mode draft works end-to-end
- [ ] All Pick draft works end-to-end
- [ ] Hero grid with search/filter
- [ ] Team panels show picks and bans
- [ ] Local recommendations appear (top 5)
- [ ] Win probability bar updates
- [ ] Undo/redo works
- [ ] Perspective toggle works
- [ ] Native installer builds for all 3 platforms
- [ ] <3 second startup time
- [ ] <100ms recommendation generation

**Alpha Release Criteria:** 
- All above items checked
- <5 P0 bugs
- Internal testing complete
- Tag: `v0.1.0-alpha`

---

## Phase 2: Beta - AI & Personalization (Weeks 9-14)

**Goal:** Implement REQ-011 through REQ-016. Add Groq LLM, Steam auth, and personalized recommendations.

### Sprint 2.1: Groq LLM Integration (Weeks 9-10)

---

### TASK-027: Implement Groq API Client

**Effort:** 6 hours  
**Dependencies:** TASK-003  
**Requirements:** REQ-011

**Description:**
HTTP client for Groq API chat completions.

**Deliverables:**
- [ ] `GroqClient.java` in infrastructure/api
- [ ] Uses Java HttpClient (built-in)
- [ ] Methods: `generateExplanation(prompt) → CompletableFuture<String>`
- [ ] Handles auth (Bearer token from config)
- [ ] Timeout handling (10s max)
- [ ] Error handling for rate limits, auth failures
- [ ] Unit tests with mocked responses

**Acceptance Criteria:**
```
Given a valid API key is configured
When I call generateExplanation("Why pick Phoenix?")
Then a response is returned within 10 seconds

Given the API key is invalid
When I call generateExplanation
Then an ApiException is thrown with clear message
```

---

### TASK-028: Create LLM Prompt Builder

**Effort:** 4 hours  
**Dependencies:** TASK-027  
**Requirements:** REQ-011

**Description:**
Build structured prompts for recommendation explanations.

**Deliverables:**
- [ ] `PromptBuilder.java` utility class
- [ ] Includes: hero name, abilities summary, current draft state
- [ ] Includes: ally heroes with roles, enemy heroes
- [ ] Includes: draft phase context (early/mid/late)
- [ ] Output limited to ~500 tokens
- [ ] Template strings for consistent formatting

**Acceptance Criteria:**
```
Given Phoenix is recommended against Luna + Magnus
When the prompt is built
Then it includes:
  - Phoenix's abilities (Supernova, Sun Ray, etc.)
  - Luna and Magnus as enemies
  - Request for specific counter reasoning
```

---

### TASK-029: Integrate LLM Explanations into UI

**Effort:** 4 hours  
**Dependencies:** TASK-027, TASK-028, TASK-022  
**Requirements:** REQ-011

**Description:**
Add "Explain" button to recommendation panel that fetches LLM explanation.

**Deliverables:**
- [ ] "💡 Explain" button on recommendation cards
- [ ] Loading indicator while fetching
- [ ] Explanation displayed in expandable panel or modal
- [ ] Markdown rendering for formatted explanation
- [ ] Caching: same recommendation doesn't re-fetch
- [ ] Fallback to local explanation if Groq fails

**Acceptance Criteria:**
```
Given I click "Explain" on Phoenix recommendation
When the LLM responds
Then a detailed explanation appears with ability references

Given Groq API is unavailable
When I click "Explain"
Then a basic local explanation appears with "AI unavailable" note
```

---

### TASK-030: Add Groq Configuration UI

**Effort:** 2 hours  
**Dependencies:** TASK-027  
**Requirements:** REQ-016

**Description:**
Settings screen for Groq API key.

**Deliverables:**
- [ ] API key input field (masked)
- [ ] "Test Connection" button
- [ ] Save to OS secure storage
- [ ] Status indicator (configured/not configured)

**Acceptance Criteria:**
```
Given I enter a Groq API key
When I click "Test Connection"
Then success/failure is displayed
And the key is saved securely
```

---

### Sprint 2.2: Steam Authentication (Weeks 11-12)

---

### TASK-031: Implement Steam OpenID Client

**Effort:** 8 hours  
**Dependencies:** TASK-003  
**Requirements:** REQ-012

**Description:**
Steam OpenID authentication flow.

**Deliverables:**
- [ ] `SteamAuthClient.java` in infrastructure/api
- [ ] Opens system browser for Steam login
- [ ] Local callback server to receive auth response
- [ ] Extracts Steam ID from OpenID response
- [ ] Handles auth cancellation gracefully
- [ ] Unit tests with mocked auth flow

**Acceptance Criteria:**
```
Given I click "Login with Steam"
When the browser opens and I authenticate
Then my Steam ID is captured
And I return to the app logged in

Given I cancel the Steam login
When the browser closes
Then the app shows "Login cancelled" and remains logged out
```

---

### TASK-032: Fetch Steam Profile Data

**Effort:** 4 hours  
**Dependencies:** TASK-031  
**Requirements:** REQ-012

**Description:**
Fetch player profile from Steam Web API.

**Deliverables:**
- [ ] `SteamApiClient.java` for profile fetching
- [ ] Fetches: persona name, avatar URL
- [ ] Stores profile locally in SQLite
- [ ] Avatar image downloaded and cached

**Acceptance Criteria:**
```
Given I am authenticated with Steam
When profile data is fetched
Then my Steam name and avatar are displayed in the app
```

---

### TASK-033: Implement Session Persistence

**Effort:** 4 hours  
**Dependencies:** TASK-031, TASK-032  
**Requirements:** REQ-012

**Description:**
Remember login across app restarts.

**Deliverables:**
- [ ] Session token stored in OS secure storage
- [ ] On app start, check for valid session
- [ ] Auto-login if session valid
- [ ] Session expiry handling
- [ ] Logout clears session

**Acceptance Criteria:**
```
Given I logged in yesterday
When I launch the app today
Then I am automatically logged in (no Steam browser)

Given I click "Logout"
When I restart the app
Then I am not logged in
```

---

### TASK-034: Create Login/Profile UI

**Effort:** 4 hours  
**Dependencies:** TASK-031, TASK-032, TASK-033  
**Requirements:** REQ-012

**Description:**
UI for login state and profile display.

**Deliverables:**
- [ ] Login button on home screen (when logged out)
- [ ] Profile widget showing avatar + name (when logged in)
- [ ] Dropdown with: View Profile, Sync Matches, Logout
- [ ] Login state persists across navigation

**Acceptance Criteria:**
```
Given I am logged out
When I view the home screen
Then a "Login with Steam" button is prominent

Given I am logged in
When I view the home screen
Then my avatar and name are displayed
```

---

### Sprint 2.3: Personalization (Weeks 13-14)

---

### TASK-035: Implement OpenDota Match History Client

**Effort:** 6 hours  
**Dependencies:** TASK-032  
**Requirements:** REQ-013

**Description:**
Fetch player's match history from OpenDota.

**Deliverables:**
- [ ] `OpenDotaClient.java` methods for player matches
- [ ] Fetches last 100 matches (configurable)
- [ ] Rate limit handling (60/min free tier)
- [ ] Stores matches in SQLite
- [ ] Incremental sync (only fetch new matches)

**Acceptance Criteria:**
```
Given I trigger match sync
When OpenDota responds
Then my last 100 matches are stored locally
And hero statistics are calculated

Given I sync again 1 hour later
When only 5 new matches exist
Then only 5 API calls are made
```

---

### TASK-036: Calculate Player Hero Statistics

**Effort:** 4 hours  
**Dependencies:** TASK-035  
**Requirements:** REQ-013

**Description:**
Aggregate hero performance stats from match history.

**Deliverables:**
- [ ] `PlayerHeroStat` entity: accountId, heroId, games, wins, avgKda, comfortScore
- [ ] Comfort score algorithm per PRD:
  - 40% games played (normalized)
  - 35% win rate delta from average
  - 15% recency
  - 10% consistency
- [ ] Stats recalculated on each sync
- [ ] Stored in `player_hero_stats` table

**Acceptance Criteria:**
```
Given I have 50 matches on Anti-Mage with 60% win rate
When comfort score calculates
Then Anti-Mage has a high comfort score (>0.7)

Given I haven't played Meepo in 6 months
When comfort score calculates
Then Meepo has a low recency factor
```

---

### TASK-037: Integrate Personal Stats into Recommendations

**Effort:** 6 hours  
**Dependencies:** TASK-036, TASK-020  
**Requirements:** REQ-014

**Description:**
Modify recommendation engine to weight by player's hero pool.

**Deliverables:**
- [ ] `PersonalScorer.java` added to recommendation engine
- [ ] Weighting configurable: global vs. personal (default 60/40)
- [ ] Comfort heroes boosted in score
- [ ] Poor performance heroes flagged with warning
- [ ] "New for you" indicator for unplayed heroes

**Acceptance Criteria:**
```
Given I am logged in with match history
When recommendations generate
Then my comfort heroes appear higher in the list

Given I have 20% win rate on Invoker
When Invoker would be recommended
Then a warning indicator is shown
```

---

### TASK-038: Display Personal Stats in UI

**Effort:** 4 hours  
**Dependencies:** TASK-036  
**Requirements:** REQ-014

**Description:**
Show player stats in hero tooltips and profile.

**Deliverables:**
- [ ] Hero card tooltip shows: games, win rate, comfort level
- [ ] Profile screen shows top 10 comfort heroes
- [ ] Visual indicator on hero grid for comfort heroes
- [ ] Stats refresh button in profile

**Acceptance Criteria:**
```
Given I hover over a hero I play often
When the tooltip appears
Then it shows "42 games, 58% win rate, Comfort: High"

Given I view my profile
When the hero stats section loads
Then my top 10 heroes are shown ranked by comfort score
```

---

### TASK-039: Implement Draft Timer

**Effort:** 4 hours  
**Dependencies:** TASK-009  
**Requirements:** REQ-015

**Description:**
Real game timer simulation.

**Deliverables:**
- [ ] Timer component: 30s countdown per action
- [ ] Reserve time: 130s per team
- [ ] Visual timer bar that depletes
- [ ] Audio cue at 5 seconds remaining (optional, configurable)
- [ ] Auto-random selection when time expires
- [ ] Timer can be enabled/disabled at draft start

**Acceptance Criteria:**
```
Given timer mode is enabled
When my turn starts
Then a 30-second countdown begins

Given I run out of pick time
When reserve time is available
Then reserve time starts counting down

Given reserve time is exhausted
When I don't pick
Then a random available hero is selected
```

---

### TASK-040: Implement Settings Persistence

**Effort:** 4 hours  
**Dependencies:** TASK-030  
**Requirements:** REQ-016

**Description:**
Save all user preferences locally.

**Deliverables:**
- [ ] `SettingsRepository.java` for key-value storage
- [ ] Settings: theme, hotkeys, recommendation weighting, timer sounds
- [ ] Load on startup, save on change
- [ ] "Reset to Defaults" option
- [ ] Settings migration for version upgrades

**Acceptance Criteria:**
```
Given I change the theme to light
When I restart the app
Then the light theme is applied

Given I click "Reset to Defaults"
When confirmed
Then all settings return to defaults
```

---

### Phase 2 Milestone Checklist (Beta)

- [ ] Groq LLM explanations work (with fallback)
- [ ] Steam login/logout works
- [ ] Session persists across restarts
- [ ] Match history syncs from OpenDota
- [ ] Comfort scores calculated
- [ ] Personalized recommendations work
- [ ] Player stats visible in UI
- [ ] Timer mode works
- [ ] Settings persist
- [ ] 50 external beta testers recruited
- [ ] 80%+ satisfaction rating

**Beta Release Criteria:**
- All above items checked
- <3 P0 bugs, <10 P1 bugs
- Performance targets still met
- Tag: `v0.2.0-beta`

---

## Phase 3: Polish & Nice-to-Haves (Weeks 15-18)

**Goal:** Implement P2 features (REQ-020 to REQ-024), fix bugs, and prepare for GA.

---

### TASK-041: Implement Dark/Light Theme Toggle

**Effort:** 4 hours  
**Dependencies:** TASK-040  
**Requirements:** REQ-020

**Description:**
Full theme support with CSS switching.

**Deliverables:**
- [ ] `dark-theme.css` and `light-theme.css`
- [ ] Theme toggle in settings
- [ ] Theme applied immediately (no restart)
- [ ] All components respect theme variables

**Acceptance Criteria:**
```
Given I select light theme
When the theme applies
Then all screens have light backgrounds and dark text
And the change is immediate
```

---

### TASK-042: Implement Global Hotkey

**Effort:** 4 hours  
**Dependencies:** None  
**Requirements:** REQ-021

**Description:**
System-wide keyboard shortcut to show/hide app.

**Deliverables:**
- [ ] JNativeHook or similar library for global hotkeys
- [ ] Default: Ctrl+Shift+D (Cmd+Shift+D on Mac)
- [ ] Configurable in settings
- [ ] Works when app is minimized or in background

**Acceptance Criteria:**
```
Given the app is running in background
When I press Ctrl+Shift+D
Then the app window comes to foreground

Given I configured Alt+D as the hotkey
When I press Alt+D
Then the app toggles visibility
```

---

### TASK-043: Implement System Tray Integration

**Effort:** 4 hours  
**Dependencies:** None  
**Requirements:** REQ-022

**Description:**
Minimize to system tray with quick actions.

**Deliverables:**
- [ ] System tray icon
- [ ] Right-click menu: Show, New Draft, Settings, Quit
- [ ] Close button minimizes to tray (configurable)
- [ ] Double-click tray icon shows window

**Acceptance Criteria:**
```
Given I close the main window
When tray mode is enabled
Then the app minimizes to tray instead of quitting

Given I right-click the tray icon
When the menu appears
Then I can start a new draft directly
```

---

### TASK-044: Implement Draft History

**Effort:** 6 hours  
**Dependencies:** TASK-012  
**Requirements:** REQ-023

**Description:**
Save and review past draft simulations.

**Deliverables:**
- [ ] `DraftHistory` entity and repository
- [ ] "Save Draft" button on completion
- [ ] Draft History screen listing past drafts
- [ ] Click to review: step through action by action
- [ ] Delete draft option

**Acceptance Criteria:**
```
Given I completed a draft
When I click "Save Draft"
Then the draft is stored with timestamp

Given I have 5 saved drafts
When I open Draft History
Then I see all 5 listed with dates
```

---

### TASK-045: Implement Export Draft as Image

**Effort:** 4 hours  
**Dependencies:** TASK-044  
**Requirements:** REQ-024

**Description:**
Generate PNG image of completed draft.

**Deliverables:**
- [ ] "Export as Image" button
- [ ] Renders both team picks, bans, timestamp
- [ ] JavaFX snapshot to PNG
- [ ] Save dialog with default filename
- [ ] Optional: copy to clipboard

**Acceptance Criteria:**
```
Given a draft is complete
When I click "Export as Image"
Then a PNG file is saved showing both teams' drafts
```

---

### TASK-046: Bug Bash & Performance Tuning

**Effort:** 16 hours (distributed)  
**Dependencies:** All prior tasks  
**Requirements:** All NFR-*

**Description:**
Fix bugs from beta feedback and optimize performance.

**Deliverables:**
- [ ] All P0 bugs fixed
- [ ] All P1 bugs fixed or deprioritized
- [ ] Startup time <3s verified
- [ ] Recommendation time <100ms verified
- [ ] Memory usage <200MB verified
- [ ] No memory leaks after 1 hour use

**Acceptance Criteria:**
```
Given I run the app for 1 hour
When I check memory usage
Then it is stable and <300MB

Given all automated tests pass
When I run the full suite
Then 0 failures and >80% coverage on domain
```

---

### TASK-047: Documentation & Help

**Effort:** 8 hours  
**Dependencies:** All features complete  
**Requirements:** Usability

**Description:**
In-app help and updated documentation.

**Deliverables:**
- [ ] "Help" menu item with basic guide
- [ ] Tooltips on all major UI elements
- [ ] README.md finalized
- [ ] CHANGELOG.md for releases
- [ ] GitHub release notes template

**Acceptance Criteria:**
```
Given I am new to the app
When I open the Help section
Then I can learn how to start a draft and understand recommendations
```

---

### TASK-048: GA Release Preparation

**Effort:** 8 hours  
**Dependencies:** TASK-046, TASK-047  
**Requirements:** REQ-010

**Description:**
Final release preparation and launch.

**Deliverables:**
- [ ] Version bumped to 1.0.0
- [ ] All platforms tested on clean installs
- [ ] Code signing configured (macOS, optional Windows)
- [ ] GitHub release created with binaries
- [ ] Announcement prepared (Reddit, Discord, etc.)

**Acceptance Criteria:**
```
Given I download the GA release
When I install on a clean machine
Then the app runs perfectly without any Java installation required
```

---

## Phase Summary

| Phase | Weeks | Tasks | Key Deliverable |
|-------|-------|-------|-----------------|
| **Phase 0** | 1-2 | TASK-001 to TASK-007 | Project foundation, no features |
| **Phase 1** | 3-8 | TASK-008 to TASK-026 | Alpha: Core draft simulation |
| **Phase 2** | 9-14 | TASK-027 to TASK-040 | Beta: AI + Personalization |
| **Phase 3** | 15-18 | TASK-041 to TASK-048 | GA: Polish + Release |

---

## Dependency Graph

```
TASK-001 (Gradle)
    └── TASK-002 (Packages)
        ├── TASK-003 (Spring Boot)
        │   └── TASK-004 (SQLite)
        │       └── TASK-007 (Import Heroes)
        │           └── TASK-013 (HeroRepository)
        │               └── TASK-014 (Hero Grid UI)
        │                   └── TASK-017 (Selection Flow)
        │
        └── TASK-005 (Domain Models)
            └── TASK-008 (DraftEngine Interface)
                ├── TASK-009 (Captain's Mode)
                │   └── TASK-011 (Undo/Redo)
                │       └── TASK-012 (DraftService)
                │           └── TASK-020 (Recommendations)
                │               └── TASK-022 (Rec Panel UI)
                │
                └── TASK-010 (All Pick)

TASK-027 (Groq Client)
    └── TASK-028 (Prompt Builder)
        └── TASK-029 (LLM in UI)

TASK-031 (Steam Auth)
    └── TASK-032 (Steam Profile)
        └── TASK-033 (Session)
            └── TASK-035 (Match History)
                └── TASK-036 (Hero Stats)
                    └── TASK-037 (Personalization)
```

---

## Risk Mitigation Tasks

| Risk | Mitigation Task | When |
|------|-----------------|------|
| R1: Groq unavailable | TASK-029 fallback logic | Phase 2 |
| R2: OpenDota rate limits | TASK-035 caching + retry | Phase 2 |
| R7: God class pattern | Code review on all controller PRs | Ongoing |

---

## Definition of Done (Per Task)

- [ ] Code compiles without warnings
- [ ] Unit tests pass (if applicable)
- [ ] No file >200 lines
- [ ] Domain classes have no framework imports
- [ ] Acceptance criteria verified manually
- [ ] Code reviewed by at least 1 person
- [ ] PR merged to `develop` branch

---

## Next Steps

1. **Create GitHub Issues** from each TASK-XXX
2. **Set up GitHub Project board** with phases as columns
3. **Start Phase 0** immediately
4. **Recruit beta testers** during Phase 1

---

*This implementation plan is derived from PRD_v3.md and TDD_v3.md. All TASK-XXX items reference REQ-XXX requirements for traceability.*

