# Dota 2 Draft Assistant - Product Requirements Document

**Version:** 3.0  
**Last Updated:** December 4, 2025  
**Owner:** Ryan Heron  
**Status:** Draft

---

## 1. Executive Summary

### 1.1 Problem Statement

Dota 2 players face a **30-second decision window** during drafts to select from 124+ heroes, but existing tools (Dota Plus, Overwolf) only show raw win rates without explaining *why* a pick is good. Competitive players (Archon to Immortal rank) and team captains need contextual analysis that considers team composition, counter-picks, and player skill—not just statistics.

### 1.2 Solution Overview

A cross-platform desktop application that provides **AI-powered draft recommendations with natural language explanations**. Unlike competitors, recommendations are personalized to the player's hero pool and include ability-level reasoning (e.g., "Silencer counters Storm Spirit because Global Silence interrupts Ball Lightning").

### 1.3 Success Metrics

| Metric | Current State | Target | Timeline |
|--------|---------------|--------|----------|
| Recommendation usefulness rating | N/A | 70%+ rated "useful" | 3 months post-launch |
| Daily Active Users (DAU) | 0 | 1,000 | 6 months post-launch |
| Draft completion rate | N/A | 80%+ of started drafts | Launch |
| App crash rate | N/A | <0.5% | Launch |
| Week 1 retention | N/A | 40%+ | 1 month post-launch |

### 1.4 Key Stakeholders

| Role | Name | Responsibility |
|------|------|----------------|
| Product Owner | Ryan Heron | Final decisions on scope/priority |
| Tech Lead | Ryan Heron | Technical feasibility & architecture |
| Design Lead | Ryan Heron | UX/UI decisions |
| QA Lead | Ryan Heron | Test strategy & acceptance |

---

## 2. Background & Context

### 2.1 Current State

Players currently solve this problem by:
- **Dota Plus ($4/month)**: Shows win rate percentages but no explanations. Generic—doesn't consider player's hero pool.
- **Overwolf DotaPlus (Free)**: Overlay with statistics. Heavy performance impact, privacy concerns (data collection).
- **Dotabuff/OpenDota (Web)**: Deep statistics but not real-time. Must alt-tab during draft.
- **Memory/Experience**: Most players just "know" from experience, but this fails against unfamiliar matchups.

**Pain Points:**
- 30-second timer leaves no time for research
- Win rates don't explain *why*—doesn't help learning
- No personalization to player's actual hero pool
- Web tools require alt-tabbing (loses draft context)

### 2.2 Why Now?

1. **LLM accessibility**: Groq API provides sub-second inference, making real-time AI explanations viable
2. **v1 learnings**: Previous Java implementation validated the concept but had architectural issues (now understood)
3. **Market gap**: No tool combines AI explanations + player personalization + offline capability
4. **Dota 2 complexity growth**: 124+ heroes, facets (7.36), innates (7.35) make drafting harder than ever

### 2.3 Related Initiatives

| Initiative | Relationship | Impact |
|------------|--------------|--------|
| v1 Java Implementation | Predecessor | Provides domain logic, hero data, and lessons learned |
| OpenDota API | Dependency | Primary data source for matches and statistics |
| Groq API | Dependency | LLM provider for AI explanations |
| Steam Web API | Dependency | Player authentication and profile data |

### 2.4 Assumptions

- **A1**: Players value understanding *why* a pick is good, not just seeing a win rate
- **A2**: Groq API will remain available with acceptable latency (<3s) and pricing
- **A3**: OpenDota free tier (60 requests/min) is sufficient for server-side data aggregation
- **A4**: Steam OpenID will continue to be supported for authentication
- **A5**: Players are willing to install a desktop application (vs. web-only)
- **A6**: Dota 2's draft format (Captain's Mode) will remain stable through 2025
- **A7**: Backend hosting on Railway/Fly.io free tier is sufficient for <1000 DAU
- **A8**: Users accept occasional latency (<500ms) for recommendations in exchange for better quality

### 2.5 Constraints

- **C1**: Must use Java 21 + JavaFX for desktop client (architectural decision based on v1 learnings)
- **C2**: No game memory reading (violates Dota 2 ToS, risk of VAC ban)
- **C3**: Budget: ~$20/month for backend infrastructure (Railway/Fly.io/Render free tier to start)
- **C4**: Timeline: MVP (Phase 1) by end of Q1 2025
- **C5**: macOS code signing requires Apple Developer Program ($99/year)
- **C6**: API keys (Groq) stored server-side only; never shipped in client binary

### 2.6 System Architecture Overview

The application follows a **client-server architecture** to enable:
- **API Key Security**: Groq/OpenDota keys never leave the server
- **Shared Caching**: Synergy/counter data computed once, shared across users
- **LLM Cost Optimization**: Cache identical recommendation requests
- **Hot Updates**: Algorithm changes without client releases

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DESKTOP CLIENT (JavaFX)                            │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  ┌────────────────────┐│
│  │ Draft UI    │  │ Hero Grid    │  │ Local Cache │  │ Offline Fallback   ││
│  │ (all logic) │  │ (images)     │  │ (SQLite)    │  │ (recommendations)  ││
│  └─────────────┘  └──────────────┘  └─────────────┘  └────────────────────┘│
│                              │                                               │
└──────────────────────────────┼───────────────────────────────────────────────┘
                               │ HTTPS REST API
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BACKEND SERVICE (Spring Boot)                      │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  ┌────────────────────┐│
│  │ /recommend  │  │ /explain     │  │ /sync       │  │ /auth              ││
│  │ (scoring)   │  │ (Groq LLM)   │  │ (OpenDota)  │  │ (Steam OpenID)     ││
│  └─────────────┘  └──────────────┘  └─────────────┘  └────────────────────┘│
│                              │                                               │
│  ┌───────────────────────────┴───────────────────────────────────────────┐  │
│  │                    Shared Data (PostgreSQL/Redis)                      │  │
│  │  • Hero synergies/counters  • LLM response cache  • Rate limit state   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
        ┌──────────┐    ┌──────────┐    ┌──────────┐
        │ Groq API │    │ OpenDota │    │ Steam    │
        │  (LLM)   │    │  (Data)  │    │  (Auth)  │
        └──────────┘    └──────────┘    └──────────┘
```

**Offline Mode**: Client works offline with cached data. Recommendations degrade gracefully (no LLM explanations, cached synergies only).

---

## 3. User & Market Analysis

### 3.1 Target Users

#### Primary Persona: The Tryhard

- **Who**: Ranked Dota 2 player (Archon to Immortal, ~2500-6000 MMR), plays 10-20 games/week, age 18-35
- **Goals**: Climb MMR, improve draft decisions, understand meta
- **Pain Points**: 
  - Limited time to research 124 heroes
  - Dota Plus feels generic ("I already know Ursa is good")
  - Loses drafts to unexpected counter-picks
- **Quote**: "I want to know *why* I should pick Phoenix here, not just that it has 54% win rate"

#### Secondary Persona: The Captain

- **Who**: Team captain for amateur/semi-pro team, responsible for drafting in scrims/tournaments
- **Goals**: Out-draft opponents, consider teammate hero pools, exploit enemy tendencies
- **Pain Points**:
  - 30-second timer leaves no room for analysis
  - Must remember 5 players' hero pools
  - No tool tracks opponent tendencies
- **Quote**: "I need to know what to ban against *this specific player*, not global ban rates"

#### Tertiary Persona: The Educator

- **Who**: Streamers, coaches, content creators
- **Goals**: Explain draft decisions to audience, teach drafting concepts
- **Pain Points**:
  - No tool generates explanations suitable for content
  - Must manually explain each pick
- **Quote**: "I wish I could show my viewers *why* this draft wins, with visuals"

### 3.2 User Journey Map

```
[Playing Ranked]     [Queue Pops]      [Draft Phase]        [Picks/Bans]         [Game Starts]
      │                   │                  │                    │                    │
      ▼                   ▼                  ▼                    ▼                    ▼
  Want to win ──► Need to draft ──► 30s to decide ──► Make pick/ban ──► Hope it works
      │                   │                  │                    │                    │
      ▼                   ▼                  ▼                    ▼                    ▼
  [No prep time]   [Open app?]      [See recommendations]  [Understand why]   [Learn for next time]
                   [Alt-tab?]       [Get explanations]     [Make informed pick]
```

**Current Pain Points:**
- Alt-tabbing loses draft context
- Win rates don't explain reasoning
- No personalization to player's heroes

**Desired State:**
- Single glance at recommendations
- Understand *why* via AI explanation
- Picks consider player's hero pool

### 3.3 Competitive Landscape

| Competitor | Strengths | Weaknesses | Our Differentiation |
|------------|-----------|------------|---------------------|
| **Valve Dota Plus** ($4/mo) | Built into client, official data | No explanations, no personalization, subscription | AI explanations, player-specific, free core features |
| **Overwolf DotaPlus** (Free) | Real-time overlay, free | Heavy (Electron), privacy concerns, intrusive | Lightweight (native Java), local-only data |
| **Dotabuff/OpenDota** (Web) | Deep statistics, free | Not real-time, requires alt-tab, no draft focus | Desktop app, draft-focused, instant recommendations |
| **Stratz** (Web) | Good analytics, some AI | Web-only, no draft assistance | Desktop app, real-time, explanations |

---

## 4. Requirements

### 4.1 Functional Requirements

#### MUST HAVE (P0) - Launch Blockers

---

**REQ-001**: Captain's Mode Draft Simulation

- **Description**: Simulate the complete Captain's Mode draft sequence (14 bans, 10 picks) with correct turn order per Dota 2 patch 7.37+
- **User Story**: As a player, I want to simulate a Captain's Mode draft so that I can practice drafting or explore pick/ban strategies
- **Acceptance Criteria**:
  - [ ] Given a new draft is started, when I select Captain's Mode, then the draft follows the sequence: Ban Phase 1 (ABBABBA), Pick Phase 1 (AB), Ban Phase 2 (AAB), Pick Phase 2 (BAABBA), Ban Phase 3 (ABBA), Pick Phase 3 (AB)
  - [ ] Given it's Radiant's turn, when the turn indicator is displayed, then it shows "Radiant" with green highlighting
  - [ ] Given it's Dire's turn, when the turn indicator is displayed, then it shows "Dire" with red highlighting
  - [ ] Given a hero is picked, when I try to pick/ban the same hero, then the action is rejected with an error message
- **Edge Cases**:
  - User tries to pick during ban phase → Show error "Cannot pick during ban phase"
  - User tries to ban during pick phase → Show error "Cannot ban during pick phase"
  - All heroes somehow unavailable → Should never happen (124 heroes > 24 actions), but show error if it does
- **Dependencies**: REQ-003 (Hero Database)

---

**REQ-002**: All Pick Mode Simulation

- **Description**: Simulate All Pick draft mode for ranked play practice
- **User Story**: As a ranked player, I want to simulate All Pick drafts so that I can practice the most common draft mode
- **Acceptance Criteria**:
  - [ ] Given All Pick mode is selected, when the draft starts, then both teams can pick simultaneously (no enforced turn order)
  - [ ] Given a hero is picked by one team, when the other team tries to pick the same hero, then the action is rejected
  - [ ] Given 5 heroes are picked per team, when the 10th hero is selected, then the draft is marked complete
- **Edge Cases**:
  - Same hero selected by both teams simultaneously → First selection wins (timestamp-based in simulation)
- **Dependencies**: REQ-003 (Hero Database)

---

**REQ-003**: Hero Database (Offline)

- **Description**: Complete database of all 124+ Dota 2 heroes with attributes, abilities, roles, and position frequencies, available offline
- **User Story**: As a player, I want all hero data available without internet so that I can use the app anywhere
- **Acceptance Criteria**:
  - [ ] Given the app is launched offline, when I view the hero grid, then all 124+ heroes are displayed with portraits
  - [ ] Given I select a hero, when I view details, then I see: name, primary attribute, attack type, roles, all abilities with descriptions
  - [ ] Given a new Dota 2 patch releases, when I sync data online, then hero changes are updated within 24 hours
  - [ ] Given hero data is corrupted, when the app starts, then it re-initializes from bundled baseline data
- **Edge Cases**:
  - New hero added in patch → Manual update to bundled data required; sync updates
  - Hero reworked → Ability data replaced entirely
- **Dependencies**: None (foundational)

---

**REQ-004**: Hero Selection Grid

- **Description**: Filterable, searchable grid of available heroes during draft
- **User Story**: As a player, I want to quickly find heroes by name, attribute, or role so that I can make decisions within the 30-second timer
- **Acceptance Criteria**:
  - [ ] Given the hero grid is displayed, when I type in the search box, then heroes are filtered by name within 50ms
  - [ ] Given I select a filter (Strength/Agility/Intelligence/Universal), when applied, then only heroes of that attribute are shown
  - [ ] Given I select a role filter (Carry, Support, etc.), when applied, then only heroes with that role are shown
  - [ ] Given a hero is picked or banned, when viewing the grid, then that hero is visually disabled (grayed out, unclickable)
- **Edge Cases**:
  - Search term matches no heroes → Show "No heroes found" message
  - Multiple filters applied → AND logic (must match all filters)
- **Dependencies**: REQ-003 (Hero Database)

---

**REQ-005**: Local Recommendation Engine

- **Description**: Generate pick/ban recommendations using synergy/counter matrices without requiring internet
- **User Story**: As a player, I want recommendations even when offline so that the app is always useful
- **Acceptance Criteria**:
  - [ ] Given a draft is in progress, when I request recommendations, then 5 heroes are shown ranked by score within 100ms
  - [ ] Given my team has 2 physical damage heroes, when I request recommendations, then magical damage heroes are scored higher
  - [ ] Given the enemy picked a hero, when I request recommendations, then counter-picks for that hero are scored higher
  - [ ] Given synergy/counter data is unavailable for a hero pair, when scoring, then default to neutral (0.5) score
- **Edge Cases**:
  - Brand new hero with no synergy data → Use role-based heuristics only
  - All recommended heroes are banned → Show next best 5 from available pool
- **Dependencies**: REQ-003 (Hero Database)

---

**REQ-006**: Win Probability Display

- **Description**: Real-time win probability estimate based on current draft state
- **User Story**: As a player, I want to see how my draft is going so that I can adjust my strategy
- **Acceptance Criteria**:
  - [ ] Given a draft is in progress, when a hero is picked/banned, then the win probability bar updates within 200ms
  - [ ] Given equal team compositions, when viewing probability, then it shows approximately 50/50
  - [ ] Given a heavily favored draft, when viewing probability, then the visual clearly indicates advantage (color gradient)
- **Edge Cases**:
  - First pick (no enemy heroes) → Show 50% baseline
  - Probability model error → Show "Unable to calculate" rather than wrong number
- **Dependencies**: REQ-005 (Recommendation Engine)

---

**REQ-007**: Draft Undo/Redo

- **Description**: Ability to undo and redo draft actions for exploration
- **User Story**: As a player, I want to explore "what if" scenarios so that I can understand alternative drafts
- **Acceptance Criteria**:
  - [ ] Given I made a pick/ban, when I click Undo, then the draft returns to the previous state
  - [ ] Given I undid an action, when I click Redo, then the action is reapplied
  - [ ] Given I undo multiple times, when I make a new action, then the redo history is cleared
- **Edge Cases**:
  - Undo at draft start → Undo button disabled
  - Redo with no undo history → Redo button disabled
- **Dependencies**: REQ-001 (Draft Simulation)

---

**REQ-008**: Team Composition Display

- **Description**: Visual breakdown of team strengths and weaknesses
- **User Story**: As a player, I want to see what my team is lacking so that I can fill gaps with my picks
- **Acceptance Criteria**:
  - [ ] Given a team has heroes picked, when viewing composition, then I see damage type distribution (physical/magical/pure)
  - [ ] Given a team has heroes picked, when viewing composition, then I see disable/control count
  - [ ] Given a team lacks a role (e.g., no hard carry), when viewing composition, then a warning indicator is shown
- **Edge Cases**:
  - Empty team → Show "No heroes selected" with all indicators at zero
- **Dependencies**: REQ-003 (Hero Database), REQ-001 (Draft Simulation)

---

**REQ-009**: Radiant/Dire Perspective Toggle

- **Description**: Switch between playing as Radiant or Dire
- **User Story**: As a player, I want to set my team so that recommendations are for the correct side
- **Acceptance Criteria**:
  - [ ] Given I select Radiant perspective, when recommendations are shown, then they are for the Radiant team
  - [ ] Given I select Dire perspective, when recommendations are shown, then they are for the Dire team
  - [ ] Given I switch perspective mid-draft, when viewing recommendations, then they update for the new team
- **Edge Cases**:
  - Switch perspective after draft complete → No-op (draft already done)
- **Dependencies**: REQ-005 (Recommendation Engine)

---

**REQ-010**: Application Packaging (Cross-Platform)

- **Description**: Native installers for macOS, Windows, and Linux
- **User Story**: As a user, I want to install the app like any other application so that I don't need technical knowledge
- **Acceptance Criteria**:
  - [ ] Given I download the macOS installer, when I run it, then I get a .dmg that installs to /Applications
  - [ ] Given I download the Windows installer, when I run it, then I get an .msi that installs with standard wizard
  - [ ] Given I download the Linux package, when I run it, then I get a .deb that installs via apt or double-click
  - [ ] Given the app is installed, when I launch it, then it starts within 3 seconds
- **Edge Cases**:
  - macOS Gatekeeper blocks unsigned app → Requires Apple Developer signing (C5)
  - Windows SmartScreen warning → Requires code signing certificate
- **Dependencies**: None

---

#### SHOULD HAVE (P1) - Important but Not Critical

---

**REQ-011**: Groq LLM Integration

- **Description**: AI-powered explanations for recommendations via Groq API
- **User Story**: As a player, I want to understand *why* a hero is recommended so that I can learn and make informed decisions
- **Acceptance Criteria**:
  - [ ] Given I request an explanation, when Groq API is available, then I receive a natural language explanation within 3 seconds
  - [ ] Given the explanation is generated, when displayed, then it references specific abilities and interactions
  - [ ] Given Groq API is unavailable, when I request explanation, then I see basic heuristic explanation (fallback)
- **Edge Cases**:
  - API timeout (>10s) → Show fallback explanation with "AI unavailable" note
  - API rate limit → Queue requests, show loading state
  - Invalid API key → Show configuration error, prompt user to update settings
- **Dependencies**: REQ-005 (Recommendation Engine)

---

**REQ-012**: Steam Authentication

- **Description**: Login with Steam account to identify player
- **User Story**: As a player, I want to log in with my Steam account so that the app knows who I am for personalized recommendations
- **Acceptance Criteria**:
  - [ ] Given I click "Login with Steam", when the Steam login page opens, then I can authenticate with my Steam credentials
  - [ ] Given I authenticate successfully, when returning to the app, then my Steam profile (name, avatar) is displayed
  - [ ] Given I am logged in, when I restart the app, then my session persists (no re-login required)
  - [ ] Given I click "Logout", when confirmed, then my session is cleared and I return to logged-out state
- **Edge Cases**:
  - Steam servers unavailable → Show error "Steam authentication unavailable, try again later"
  - User cancels auth flow → Return to logged-out state gracefully
- **Dependencies**: None

---

**REQ-013**: Match History Import

- **Description**: Fetch player's match history from OpenDota for personalization
- **User Story**: As a logged-in player, I want my match history imported so that recommendations consider my hero pool
- **Acceptance Criteria**:
  - [ ] Given I am logged in, when I trigger sync, then my last 100 matches are fetched from OpenDota
  - [ ] Given matches are fetched, when processed, then hero statistics are calculated (games, wins, KDA per hero)
  - [ ] Given sync is complete, when I view my profile, then I see my top heroes and win rates
  - [ ] Given OpenDota rate limit is hit, when syncing, then retry with exponential backoff
- **Edge Cases**:
  - Private profile on Steam → Show error "Profile is private, cannot fetch matches"
  - New account with <10 matches → Show "Not enough data for personalization"
- **Dependencies**: REQ-012 (Steam Authentication)

---

**REQ-014**: Personalized Recommendations

- **Description**: Weight recommendations by player's hero performance
- **User Story**: As a logged-in player, I want recommendations to consider my hero pool so that I'm not suggested heroes I can't play
- **Acceptance Criteria**:
  - [ ] Given I have match history, when recommendations are generated, then my comfort heroes are scored higher
  - [ ] Given I have <20% win rate on a hero, when that hero would be recommended, then a warning indicator is shown
  - [ ] Given I have no data on a hero, when that hero is recommended, then it's marked as "New for you"
  - [ ] Given I configure weighting (Settings), when set to 80/20 global/personal, then scoring reflects that ratio
- **Edge Cases**:
  - Player only plays 10 heroes → Don't penalize unfamiliar heroes too much (still show meta picks)
  - Hero reworked since player's matches → Treat as "New for you"
- **Dependencies**: REQ-013 (Match History Import), REQ-005 (Recommendation Engine)

---

**REQ-015**: Draft Timer Mode

- **Description**: Real game timer simulation (30s pick/ban, 130s reserve per team)
- **User Story**: As a player, I want to practice with real time pressure so that I'm prepared for ranked games
- **Acceptance Criteria**:
  - [ ] Given timer mode is enabled, when a turn starts, then a 30-second countdown begins
  - [ ] Given the timer reaches 0, when reserve time is available, then reserve time starts counting down
  - [ ] Given reserve time reaches 0, when no selection is made, then a random available hero is selected
  - [ ] Given timer mode is disabled, when drafting, then no time limits are enforced
- **Edge Cases**:
  - User makes selection with 1 second left → Selection accepted, timer stops
  - Both teams' reserve time depleted → All subsequent selections have no time limit (match actual game behavior)
- **Dependencies**: REQ-001 (Draft Simulation)

---

**REQ-016**: Settings Persistence

- **Description**: Save user preferences locally
- **User Story**: As a user, I want my settings saved so that I don't have to reconfigure the app each time
- **Acceptance Criteria**:
  - [ ] Given I change a setting (theme, hotkeys, etc.), when I restart the app, then the setting persists
  - [ ] Given I configure API keys, when stored, then they are saved to OS secure storage (Keychain/Credential Manager)
  - [ ] Given I click "Reset to Defaults", when confirmed, then all settings return to defaults
- **Edge Cases**:
  - Settings file corrupted → Reset to defaults with notification
  - OS secure storage unavailable → Fall back to encrypted file with warning
- **Dependencies**: None

---

#### COULD HAVE (P2) - Nice to Have

---

**REQ-020**: Dark/Light Theme Toggle

- **Description**: Switch between dark and light color themes
- **User Story**: As a user, I want to choose my theme so that the app matches my preferences
- **Acceptance Criteria**:
  - [ ] Given I select dark theme, when the app renders, then backgrounds are dark and text is light
  - [ ] Given I select light theme, when the app renders, then backgrounds are light and text is dark
  - [ ] Given I change theme, when applied, then all screens update immediately (no restart)
- **Dependencies**: None

---

**REQ-021**: Global Hotkey

- **Description**: Show/hide app with a keyboard shortcut from anywhere
- **User Story**: As a player, I want to toggle the app quickly so that I can check recommendations during a real draft
- **Acceptance Criteria**:
  - [ ] Given the app is running, when I press Ctrl+Shift+D (default), then the app window shows/hides
  - [ ] Given I'm in another application, when I press the hotkey, then the Draft Assistant comes to foreground
  - [ ] Given I configure a custom hotkey, when I press it, then it triggers show/hide
- **Edge Cases**:
  - Hotkey conflicts with another app → Show warning, suggest alternatives
- **Dependencies**: None

---

**REQ-022**: System Tray Integration

- **Description**: App minimizes to system tray with quick actions
- **User Story**: As a user, I want the app running in the background so that it's ready when I need it
- **Acceptance Criteria**:
  - [ ] Given I close the app window, when system tray mode is enabled, then the app minimizes to tray instead of quitting
  - [ ] Given the app is in tray, when I right-click the icon, then I see: Show, New Draft, Settings, Quit
  - [ ] Given I click the tray icon, when the app is minimized, then the window restores
- **Dependencies**: None

---

**REQ-023**: Draft History

- **Description**: Save and review past draft simulations
- **User Story**: As a player, I want to review my practice drafts so that I can learn from past sessions
- **Acceptance Criteria**:
  - [ ] Given I complete a draft, when I click "Save Draft", then it's stored with timestamp
  - [ ] Given I have saved drafts, when I open Draft History, then I see a list sorted by date
  - [ ] Given I select a past draft, when I click "Review", then I can step through it action by action
- **Dependencies**: REQ-001 (Draft Simulation)

---

**REQ-024**: Export Draft as Image

- **Description**: Export completed draft as an image for sharing
- **User Story**: As a content creator, I want to export drafts as images so that I can share on social media
- **Acceptance Criteria**:
  - [ ] Given a draft is complete, when I click "Export", then a PNG image of both teams is generated
  - [ ] Given the image is generated, when saved, then it includes: team picks, bans, and timestamp
- **Dependencies**: REQ-001 (Draft Simulation)

---

#### WON'T HAVE (P3) - Explicitly Out of Scope for v1

---

**REQ-030**: Mobile Application

- **Reason**: Desktop-first strategy; mobile Dota 2 is not a primary use case
- **Future Consideration**: If user demand emerges, reconsider for v2 (2026)

---

**REQ-031**: Real-Time Overlay During Live Drafts

- **Reason**: Requires screen capture/OCR complexity; high risk of Dota 2 ToS issues
- **Future Consideration**: Phase 5 (Q4 2025) if legal review confirms compliance

---

**REQ-032**: Team Management (Multi-Player Rosters)

- **Reason**: Complexity; not core to individual player value prop
- **Future Consideration**: Phase 4 (Q3 2025) after core features validated

---

**REQ-033**: Opponent Lookup/Scouting

- **Reason**: Privacy concerns; requires additional API calls per opponent
- **Future Consideration**: Phase 4 (Q3 2025) with clear opt-in/privacy controls

---

**REQ-034**: Automated Drafting (Bot Play)

- **Reason**: Against Dota 2 ToS; not aligned with "learning tool" value prop
- **Future Consideration**: Never—explicitly rejected

---

### 4.2 Non-Functional Requirements

#### Performance (NFR-P)

| ID | Requirement | Target | Measurement Method |
|----|-------------|--------|-------------------|
| NFR-P001 | Application cold start | < 3 seconds | Time to interactive UI, 95th percentile |
| NFR-P002 | Application warm start | < 500ms | App already in memory |
| NFR-P003 | Hero grid render | < 50ms | 124 heroes with images |
| NFR-P004 | Backend recommendation API | < 500ms | P95 latency, including network |
| NFR-P005 | LLM explanation (via backend) | < 3 seconds | P95 latency, end-to-end |
| NFR-P006 | Search/filter response | < 50ms | Keystroke to result update (local) |
| NFR-P007 | Client memory usage (idle) | < 150MB | After 1 hour of use |
| NFR-P008 | Client memory usage (active) | < 250MB | During draft with all features |
| NFR-P009 | Backend API response | < 200ms | P95 latency, excluding LLM calls |
| NFR-P010 | Offline fallback time | < 100ms | Cached recommendations when offline |

#### Security (NFR-S)

| ID | Requirement | Standard/Implementation |
|----|-------------|------------------------|
| NFR-S001 | API keys storage | Server-side only; never in client binary |
| NFR-S002 | No telemetry without consent | Explicit opt-in required; default off |
| NFR-S003 | Steam auth | OpenID 2.0 via backend (no client-side secrets) |
| NFR-S004 | Local data encryption | SQLite database not encrypted (acceptable for non-sensitive data) |
| NFR-S005 | Code signing | macOS: Apple Developer signed; Windows: Authenticode signed |
| NFR-S006 | No game memory access | ToS compliance; no VAC ban risk |
| NFR-S007 | Backend HTTPS | TLS 1.3 required; HSTS enabled |
| NFR-S008 | Rate limiting | Per-IP and per-user limits on backend |
| NFR-S009 | Input validation | All backend endpoints validate input schemas |

#### Scalability (NFR-SC)

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-SC001 | Local cache size | < 50MB (hero data, cached synergies) |
| NFR-SC002 | Local draft history | Up to 100 saved drafts |
| NFR-SC003 | Backend concurrent users | 100 simultaneous connections |
| NFR-SC004 | Backend database size | < 1GB (synergies, counters, LLM cache) |
| NFR-SC005 | LLM cache hit rate | > 60% (reduce API costs) |

*Note: Backend hosted on Railway/Fly.io with auto-scaling. Client stores minimal cache.*

#### Availability (NFR-A)

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-A001 | Offline functionality | Core draft simulation works offline (no recommendations) |
| NFR-A002 | Graceful degradation | If backend unavailable, use cached synergy data + local scoring |
| NFR-A003 | Client crash recovery | Auto-save draft state every action; resume on restart |
| NFR-A004 | Backend uptime | 99% (7.2 hours downtime/month acceptable) |
| NFR-A005 | Backend health checks | Automatic restart on failure |

#### Usability (NFR-U)

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-U001 | Time to first draft | < 30 seconds from app launch |
| NFR-U002 | Time to find a hero | < 5 seconds via search |
| NFR-U003 | Keyboard navigation | Full draft completable without mouse |
| NFR-U004 | Accessibility | WCAG 2.1 AA compliance for color contrast |
| NFR-U005 | Error recovery | All errors have actionable messages and recovery path |

---

## 5. User Interface Requirements

### 5.1 Key Screens/Flows

#### Screen 1: Home/Dashboard

- **Purpose**: Entry point; quick access to start drafts or view profile
- **Entry Points**: App launch, system tray click
- **Key Elements**:
  - "New Draft" button (prominent)
  - Recent drafts (if any)
  - Player profile summary (if logged in)
  - Current meta highlights (top 5 pick/ban rates)
- **Exit Points**: Draft Interface, Settings, Profile
- **Wireframe Reference**: See Section 5.4

#### Screen 2: Draft Interface

- **Purpose**: Core drafting experience with picks, bans, recommendations
- **Entry Points**: "New Draft" from Home, Resume from Draft History
- **Key Elements**:
  - Team panels (Radiant left, Dire right) showing picks
  - Ban panel (center-top) showing banned heroes
  - Hero grid (center) with search/filter
  - Recommendation panel (bottom) with top 5 suggestions
  - Timer display (if enabled)
  - Phase indicator (Ban Phase 1, Pick Phase 2, etc.)
  - Undo/Redo buttons
  - Show Analysis button
- **Exit Points**: Home (via back/complete), Settings
- **Wireframe Reference**: See Section 5.4

#### Screen 3: Analysis Panel (Expandable)

- **Purpose**: Detailed team composition analysis
- **Entry Points**: "Show Analysis" from Draft Interface
- **Key Elements**:
  - Win probability gauge (0-100%)
  - Damage type pie chart (physical/magical/pure)
  - Role coverage indicators
  - Disable/control count
  - Power spike timeline (early/mid/late)
  - Selected hero's detailed explanation
- **Exit Points**: Collapse back to Draft Interface
- **Wireframe Reference**: See Section 5.4

#### Screen 4: Settings

- **Purpose**: Configure app behavior and API keys
- **Entry Points**: Gear icon from any screen
- **Key Elements**:
  - Theme selection (Dark/Light)
  - Groq API key input (masked)
  - Steam account connection status
  - Recommendation weighting slider (global vs. personal)
  - Hotkey configuration
  - Data management (clear cache, export data)
- **Exit Points**: Save & Close, Cancel
- **Wireframe Reference**: See Section 5.4

### 5.2 Design Principles

1. **Speed First**: Every interaction must feel instant (<100ms response); pre-compute where possible
2. **Clarity Over Density**: Show top 5 recommendations, not 20; progressive disclosure for details
3. **Dark by Default**: Match Dota 2 aesthetic; reduce eye strain during night gaming
4. **Keyboard-Driven**: Full navigation via keyboard for power users; mouse as secondary
5. **No Surprises**: Undo always available; no destructive actions without confirmation

### 5.3 Brand/Style Requirements

- **Color Palette**: 
  - Primary: Dark slate (#0f172a background, #1e293b surfaces)
  - Radiant: Green (#92a525)
  - Dire: Red (#c23c2a)
  - Accent: Blue (#0ea5e9)
- **Typography**: System fonts (SF Pro on macOS, Segoe UI on Windows, Ubuntu on Linux)
- **Iconography**: Minimal, monochrome icons; hero portraits are the primary imagery
- **Design System**: Custom; no external dependency

### 5.4 Wireframe Reference

```
┌─────────────────────────────────────────────────────────────────────┐
│ [Timer: 0:25]              CAPTAIN'S MODE              [⚙️ Settings]│
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  RADIANT                    BAN PHASE 1                      DIRE  │
│  ┌──────────┐                                           ┌──────────┐│
│  │ 🟢 Pick 1│    ╔═══════════════════════════════╗     │ Pick 1 🔴││
│  │ 🟢 Pick 2│    ║    🚫 🚫 🚫 🚫 🚫 🚫 🚫       ║     │ Pick 2 🔴││
│  │ 🟢 Pick 3│    ║         (Banned Heroes)       ║     │ Pick 3 🔴││
│  │ 🟢 Pick 4│    ╚═══════════════════════════════╝     │ Pick 4 🔴││
│  │ 🟢 Pick 5│                                          │ Pick 5 🔴││
│  └──────────┘                                          └──────────┘│
│                                                                     │
│  ═══════════════════════════════════════════════════════════════════│
│  [Search: ___________] [Filter: All ▼] [Sort: Name ▼]              │
│  ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐    │
│  │Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│    │
│  ├────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┤    │
│  │Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│Hero│    │
│  └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘    │
│  ═══════════════════════════════════════════════════════════════════│
│                        RECOMMENDATIONS                              │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ 🥇 Phoenix (92%) - Counters Luna, synergizes with Magnus        ││
│  │ 🥈 Enigma (88%)  - Strong teamfight, BKB-piercing ultimate      ││
│  │ 🥉 Tidehunter (85%) - Reliable initiation, Kraken Shell vs PA  ││
│  └─────────────────────────────────────────────────────────────────┘│
│  [📊 Show Analysis] [💡 Explain Top Pick] [↩️ Undo] [🔄 Reset]      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. Data Requirements

### 6.1 Data Entities

| Entity | Description | Key Attributes | Relationships |
|--------|-------------|----------------|---------------|
| Hero | A playable Dota 2 character | id, name, localizedName, primaryAttribute, attackType, roles, positions | Has many Abilities |
| Ability | A hero's skill | id, name, description, abilityType, damageType, cooldown[], manaCost[] | Belongs to Hero |
| HeroSynergy | Win rate when two heroes are on same team | hero1Id, hero2Id, games, wins, synergyScore | References Hero (x2) |
| HeroCounter | Win rate when hero faces another | heroId, counterId, games, wins, counterScore | References Hero (x2) |
| Match | A played game | id, startTime, duration, radiantWin, gameMode | Has many MatchPlayers |
| MatchPlayer | Player's performance in a match | matchId, heroId, accountId, kills, deaths, assists | Belongs to Match, References Hero |
| PlayerHeroStat | Aggregated stats per hero for a player | accountId, heroId, games, wins, avgKda, comfortScore | References Hero |
| DraftHistory | A saved draft simulation | id, timestamp, mode, radiantPicks[], direPicks[], radiantBans[], direBans[] | References Hero (multiple) |
| UserSettings | Application preferences | theme, hotkey, groqApiKey, recommendationWeighting | Singleton |

### 6.2 Data Sources

| Source | Data Type | Integration Method | Frequency |
|--------|-----------|-------------------|-----------|
| OpenDota API | Heroes, matches, player stats | REST API | On-demand (user-triggered sync) |
| Steam Web API | Player authentication, profile | OpenID + REST | On login |
| Groq API | LLM explanations | REST API | On-demand (per explanation request) |
| Dota 2 Wiki | Ability details (scraping) | Web scraping (offline bundled) | Manual update per patch |
| Bundled JSON | Baseline hero data | Static files | Per release |

### 6.3 Data Privacy & Retention

- **PII Handling**: 
  - Steam ID and profile name stored locally only
  - No PII sent to third parties except Steam (for auth) and OpenDota (public match data)
- **Retention Policy**: 
  - Match history: Indefinite (user's machine)
  - Draft history: Indefinite until manually cleared
  - API keys: Until user removes them
- **Deletion Requirements**: 
  - "Clear All Data" option in Settings
  - Logout removes Steam session and profile data
  - Uninstall removes all local data (standard OS behavior)

---

## 7. Integration Requirements

### 7.1 External Integrations (Backend → External APIs)

| System | Purpose | Direction | Priority | Rate Limits |
|--------|---------|-----------|----------|-------------|
| OpenDota API | Match data, hero stats, player profiles | Inbound | P0 | 60/min (free), 1200/min (paid) |
| Steam Web API | Player authentication | Both | P1 | 100,000/day |
| Groq API | LLM recommendations | Outbound | P1 | Varies by plan |
| Dota 2 CDN | Hero images | Inbound | P0 | None |

### 7.2 Internal API (Client → Backend)

| Endpoint | Method | Purpose | Auth | Rate Limit |
|----------|--------|---------|------|------------|
| `POST /api/v1/recommend` | POST | Get hero recommendations | Optional (Steam session) | 60/min |
| `POST /api/v1/explain` | POST | Get LLM explanation for pick | Optional | 30/min |
| `GET /api/v1/heroes` | GET | Fetch all hero data | None | 10/min |
| `GET /api/v1/synergies` | GET | Fetch synergy/counter matrix | None | 10/min |
| `POST /api/v1/auth/steam` | POST | Start Steam OAuth flow | None | 10/min |
| `POST /api/v1/auth/callback` | POST | Complete Steam OAuth | Session | 10/min |
| `GET /api/v1/player/{steamId}` | GET | Get player profile + stats | Session | 30/min |
| `POST /api/v1/player/{steamId}/sync` | POST | Trigger match history sync | Session | 5/min |
| `GET /api/v1/health` | GET | Health check | None | 100/min |

### 7.3 External API Contracts (Backend → External)

| Endpoint | Method | Purpose | Auth |
|----------|--------|---------|------|
| `api.opendota.com/api/heroStats` | GET | Hero win rates and pick rates | None (API key optional) |
| `api.opendota.com/api/players/{id}/matches` | GET | Player match history | None |
| `api.opendota.com/api/matches/{id}` | GET | Match details | None |
| `steamcommunity.com/openid` | OpenID | Player authentication | OpenID 2.0 |
| `api.groq.com/openai/v1/chat/completions` | POST | LLM explanations | Bearer token |

---

## 8. Release Strategy

### 8.1 Phases

| Phase | Scope | Target Date | Success Criteria |
|-------|-------|-------------|------------------|
| **Alpha** | REQ-001 to REQ-010 (core draft simulation, local recommendations) | Feb 2025 | Internal testing complete, <5 P0 bugs |
| **Beta** | + REQ-011 to REQ-016 (Groq, Steam, personalization) | Apr 2025 | 50 external testers, 80%+ satisfaction |
| **GA (v1.0)** | All P0 + P1 requirements | Jun 2025 | 1,000 downloads, 70%+ recommendation usefulness |

### 8.2 Feature Flags

| Feature | Flag Name | Default State | Rollout Plan |
|---------|-----------|---------------|--------------|
| Groq LLM Integration | `groq_enabled` | Off | Enable for beta testers, then GA |
| Steam Authentication | `steam_auth_enabled` | Off | Enable for beta testers, then GA |
| Personalized Recommendations | `personalization_enabled` | Off | Enable after Steam auth stable |
| Timer Mode | `timer_mode_available` | On | Available to all from alpha |

### 8.3 Rollback Plan

- **Trigger**: >5% crash rate OR >20% negative feedback on core features
- **Process**: 
  1. Disable feature flags for problematic features
  2. Publish patch release reverting to last stable
  3. Notify users via in-app message
- **Communication**: GitHub release notes, Discord announcement (if community exists)

---

## 9. Success Criteria & Validation

### 9.1 Launch Criteria (Go/No-Go)

- [ ] All P0 requirements pass acceptance tests
- [ ] Performance targets (NFR-P) met: <3s start, <100ms recommendations
- [ ] No P0/P1 bugs open
- [ ] macOS, Windows, Linux installers tested on clean machines
- [ ] Groq API fallback tested (works when API unavailable)
- [ ] 10+ external beta testers approve UX

### 9.2 Post-Launch Metrics

| Metric | Measurement Method | Review Frequency |
|--------|-------------------|------------------|
| Daily Active Users | Local analytics (opt-in) | Weekly |
| Draft Completion Rate | Local analytics | Weekly |
| Recommendation Usefulness | In-app feedback prompt | Monthly |
| Crash Rate | Crash reporting (opt-in) | Daily (first month), Weekly (ongoing) |
| Feature Usage | Local analytics | Monthly |

### 9.3 Experiment Plan

| Hypothesis | Test Method | Success Threshold |
|------------|-------------|-------------------|
| AI explanations increase recommendation usefulness rating | A/B: with vs. without LLM explanations | +15% usefulness rating |
| Personalization increases engagement | Cohort: logged-in vs. anonymous users | +20% session duration |
| Dark theme preferred by majority | Analytics: theme selection | >70% choose dark |

---

## 10. Risks & Mitigations

| Risk ID | Risk | Probability | Impact | Mitigation |
|---------|------|-------------|--------|------------|
| R1 | Groq API becomes unavailable or too expensive | Medium | High | Local fallback explanations; abstract LLM layer for provider switching |
| R2 | OpenDota rate limits block personalization | Medium | Medium | Aggressive caching; batch requests; consider paid tier |
| R3 | Dota 2 patch changes draft format | Low | High | Modular draft engine; quick patch response process |
| R4 | macOS code signing rejected | Low | Medium | Submit for Apple review early; have notarization process ready |
| R5 | Users don't trust AI recommendations | Medium | High | Show reasoning transparently; always show data behind scores |
| R6 | Competitor (Valve) improves Dota Plus significantly | Low | High | Focus on explanations and personalization—hard to replicate quickly |
| R7 | v1 architectural patterns leak into v3 | Medium | Medium | Code reviews; strict layering; no >200-line files |

---

## 11. Open Questions

| ID | Question | Owner | Due Date | Status |
|----|----------|-------|----------|--------|
| Q1 | Should we support Turbo/Ability Draft modes? | Product Owner | Jan 2025 | Open |
| Q2 | What's the right default for global vs. personal weighting? | Product Owner | Feb 2025 | Open |
| Q3 | Do we need Windows code signing for GA? (Cost vs. SmartScreen warning) | Tech Lead | Mar 2025 | Open |
| Q4 | Should draft history sync to cloud (future feature)? | Product Owner | Post-GA | Open |
| Q5 | What LLM provider to use if Groq pricing changes? | Tech Lead | Ongoing | Open |

---

## 12. Appendix

### 12.1 Glossary

| Term | Definition |
|------|------------|
| MMR | Matchmaking Rating—numerical skill score (0-12000+) |
| Position | Farm priority in Dota 2: 1 (hard carry) to 5 (hard support) |
| Meta | Current strongest strategies and heroes in the game |
| Draft | Hero selection phase before the game starts |
| Ban | Removing a hero from the available pool for both teams |
| Pick | Selecting a hero for your team |
| Counter | A hero that is statistically strong against another hero |
| Synergy | Heroes that statistically perform better when on the same team |
| Captain's Mode | Competitive draft format with structured pick/ban phases |
| All Pick | Ranked draft format where teams pick simultaneously |
| Facet | Hero variant system added in Dota 2 patch 7.36 |
| Innate | Passive ability available from level 1 (patch 7.35+) |
| ToS | Terms of Service (Valve's rules for Dota 2) |
| VAC | Valve Anti-Cheat—ban system for cheating |

### 12.2 References

- [OpenDota API Documentation](https://docs.opendota.com/)
- [Steam Web API Documentation](https://developer.valvesoftware.com/wiki/Steam_Web_API)
- [Groq API Documentation](https://console.groq.com/docs)
- [Dota 2 Wiki](https://dota2.fandom.com/)
- [v1 Implementation Codebase](../src/)
- [TDD_v3.md](./TDD_v3.md) - Technical Design Document

### 12.3 Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Nov 2024 | Ryan Heron | Initial PRD for v1 (Java/JavaFX) |
| 2.0 | Dec 2024 | Ryan Heron | Revised for cross-platform considerations |
| 3.0 | Dec 4, 2025 | Ryan Heron | Complete rewrite with traceable requirements (REQ-XXX), acceptance criteria, MoSCoW prioritization, and spec-compliant structure |
| 3.1 | Dec 4, 2025 | Ryan Heron | **ARCHITECTURE PIVOT**: Client-server architecture. Added backend service for API key security, shared caching, LLM cost optimization. Updated NFRs, constraints, and API requirements. |

---

*This PRD is the single source of truth for product requirements. Technical implementation details are in [TDD_v3.md](./TDD_v3.md). All requirement IDs (REQ-XXX) should be referenced in technical specs and implementation tasks.*
