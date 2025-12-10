#!/bin/bash

# =============================================================================
# Add Remaining GitHub Issues - Phases 3-8
# =============================================================================
#
# This script adds issues that were not created by the initial setup.
# Run from repository root: ./scripts/add-remaining-issues.sh
#
# Prerequisites:
#   1. GitHub CLI installed: brew install gh
#   2. Authenticated: gh auth login
# =============================================================================

set -e

REPO="malibuwileyu/dota2_draft_assistant"

echo "========================================"
echo "Adding Remaining Issues (Phases 3-8)"
echo "========================================"

# Check gh is installed and authenticated
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed."
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo "Error: Not authenticated with GitHub CLI."
    exit 1
fi

# =============================================================================
# Create new labels for Phases 6-8 and new REQs
# =============================================================================
echo ""
echo "Creating new labels..."
echo "----------------------------------------"

create_label() {
    local name="$1"
    local color="$2"
    local description="$3"
    
    if gh label create "$name" --color "$color" --description "$description" --repo "$REPO" 2>/dev/null; then
        echo "✓ Created label: $name"
    else
        echo "• Label exists: $name"
    fi
}

# New phase labels
create_label "phase:6-pipeline" "0d7f8d" "Phase 6: Data Pipeline"
create_label "phase:7-analysis" "ff6b6b" "Phase 7: Analysis Engine"
create_label "phase:8-insights" "4ecdc4" "Phase 8: Insights UI"

# New REQ labels for Phases 6-8
create_label "req:040" "c5def5" "REQ-040: Match Data Pipeline"
create_label "req:041" "c5def5" "REQ-041: Macro Analysis Engine"
create_label "req:042" "c5def5" "REQ-042: Phase Analysis Engine"
create_label "req:043" "c5def5" "REQ-043: Micro Analysis Engine"
create_label "req:044" "c5def5" "REQ-044: Session/Behavioral Analysis"
create_label "req:045" "c5def5" "REQ-045: Draft Order Analysis"
create_label "req:046" "c5def5" "REQ-046: Insights Page"
create_label "req:047" "c5def5" "REQ-047: Draft History Browser"

# Additional category label
create_label "category:analysis" "e6b8af" "Analysis/reasoning engine"

# =============================================================================
# Create new milestones
# =============================================================================
echo ""
echo "Creating new milestones..."
echo "----------------------------------------"

create_milestone() {
    local title="$1"
    local description="$2"
    local due_date="$3"
    
    if gh api repos/$REPO/milestones -f title="$title" -f description="$description" -f due_on="$due_date" 2>/dev/null; then
        echo "✓ Created milestone: $title"
    else
        echo "• Milestone exists: $title"
    fi
}

create_milestone "Phase 6: Data Pipeline" "Match history ingestion, parsing, storage, sync infrastructure." "2025-08-01T00:00:00Z"
create_milestone "Phase 7: Analysis Engine" "Pattern detection, 4-stage analysis, insights generation." "2025-10-03T00:00:00Z"
create_milestone "Phase 8: Insights UI" "Insights page, draft history browser, recommendations display." "2025-12-05T00:00:00Z"
create_milestone "v1.3 Release" "Full analysis engine with insights UI." "2025-12-19T00:00:00Z"

# =============================================================================
# Function to create issues
# =============================================================================
create_issue() {
    local title="$1"
    local body="$2"
    local labels="$3"
    local milestone="$4"
    
    if gh issue create --title "$title" --body "$body" --label "$labels" --milestone "$milestone" --repo "$REPO" 2>/dev/null; then
        echo "✓ Created: $title"
    else
        echo "✗ Failed: $title"
    fi
    
    # Small delay to avoid rate limiting
    sleep 0.5
}

# =============================================================================
# PHASE 3: REMAINING UI ISSUES
# =============================================================================
echo ""
echo "Phase 3: Remaining UI Issues..."
echo "----------------------------------------"

create_issue "P3-002: Create HomeController" \
"## Description
Home screen controller with new draft button and recent drafts list.

## Acceptance Criteria
- [ ] \`HomeController.java\` in \`ui.controller\` (<150 lines)
- [ ] \"New Draft\" button prominent and functional
- [ ] Draft mode selector (Captain's Mode / All Pick)
- [ ] Recent drafts list (last 5) with click-to-resume
- [ ] Player profile summary (if logged in)
- [ ] Quick stats section (games, win rate)

## Technical Notes
- Use VBox layout with proper spacing
- Recent drafts loaded from DraftHistoryService
- Profile section hidden if not logged in

## Test Cases
- New draft button starts draft flow
- Recent draft click resumes that draft
- Profile shows/hides based on login state

## Estimated Hours: 4
## Dependencies: P3-001
## Requirements: NFR-U001" \
"priority:p0,phase:3-ui,type:feature,category:ui" \
"Phase 3: User Interface"

create_issue "P3-006: Create TeamPanelController" \
"## Description
Controller for displaying team picks and bans.

## Acceptance Criteria
- [ ] \`TeamPanelController.java\` (<150 lines)
- [ ] Shows 5 pick slots per team
- [ ] Shows ban slots (7 for Captain's Mode)
- [ ] Visual distinction between Radiant (green) and Dire (red)
- [ ] Empty slot placeholder
- [ ] Filled slot shows hero portrait + name
- [ ] Highlights current pick/ban position

## Technical Notes
\`\`\`java
public void updatePicks(Team team, List<Hero> picks) {
    Platform.runLater(() -> {
        for (int i = 0; i < 5; i++) {
            HeroSlot slot = pickSlots.get(team).get(i);
            slot.setHero(i < picks.size() ? picks.get(i) : null);
        }
    });
}
\`\`\`

## Test Cases
- Empty draft shows 5 empty slots
- After pick, slot shows hero
- Current slot is highlighted

## Estimated Hours: 4
## Dependencies: P3-005
## Requirements: REQ-008" \
"priority:p0,phase:3-ui,type:feature,category:ui,req:008" \
"Phase 3: User Interface"

create_issue "P3-007: Create RecommendationController" \
"## Description
Controller for displaying hero recommendations.

## Acceptance Criteria
- [ ] \`RecommendationController.java\` (<150 lines)
- [ ] Shows top 5 recommended heroes
- [ ] Each recommendation shows: hero portrait, name, score percentage
- [ ] Click on recommendation selects that hero
- [ ] \"Explain\" button per recommendation (triggers LLM)
- [ ] Loading state while calculating
- [ ] Updates after each pick/ban (<200ms)

## Technical Notes
- Subscribe to DraftService state changes
- Async recommendation calculation
- Cache recent explanations

## Test Cases
- Recommendations update after pick
- Click on recommendation triggers selection
- Explain button shows explanation modal

## Estimated Hours: 4
## Dependencies: P1-009
## Requirements: REQ-005" \
"priority:p0,phase:3-ui,type:feature,category:ui,req:005" \
"Phase 3: User Interface"

create_issue "P3-008: Create WinProbabilityBar" \
"## Description
Visual bar showing real-time win probability.

## Acceptance Criteria
- [ ] \`WinProbabilityBar.java\` component
- [ ] Horizontal bar with Radiant left, Dire right
- [ ] Color gradient (green <-> red)
- [ ] Percentage label in center
- [ ] Smooth animation on updates (200ms transition)
- [ ] Updates within 200ms of state change

## Technical Notes
\`\`\`java
public void setWinProbability(double radiantChance) {
    // radiantChance is 0.0 to 1.0
    Timeline animation = new Timeline(
        new KeyFrame(Duration.millis(200),
            new KeyValue(bar.widthProperty(), radiantChance * totalWidth)
        )
    );
    animation.play();
}
\`\`\`

## Test Cases
- 50/50 shows bar in middle
- 70/30 shows bar 70% left
- Animation is smooth

## Estimated Hours: 4
## Dependencies: P1-014
## Requirements: REQ-006" \
"priority:p0,phase:3-ui,type:feature,category:ui,req:006" \
"Phase 3: User Interface"

create_issue "P3-009: Create PhaseIndicator" \
"## Description
Component showing current draft phase and turn.

## Acceptance Criteria
- [ ] \`PhaseIndicator.java\` component
- [ ] Shows phase name (Ban Phase 1, Pick Phase 2, etc.)
- [ ] Shows whose turn (Radiant/Dire with color)
- [ ] Shows action type (Pick/Ban)
- [ ] Prominent and readable
- [ ] Updates instantly on state change

## Test Cases
- Shows \"Ban Phase 1 - Radiant Ban\" at start
- Updates on each action

## Estimated Hours: 2
## Dependencies: None
## Requirements: REQ-001" \
"priority:p0,phase:3-ui,type:feature,category:ui,req:001" \
"Phase 3: User Interface"

create_issue "P3-010: Create TimerDisplay" \
"## Description
Countdown timer for draft turns.

## Acceptance Criteria
- [ ] \`TimerDisplay.java\` component
- [ ] 30-second countdown per turn
- [ ] Reserve time display (130s per team)
- [ ] Color change when low (<10s = yellow, <5s = red)
- [ ] Sound alert option at 5s
- [ ] Pause/resume capability
- [ ] Auto-random when time expires

## Test Cases
- Timer counts down correctly
- Color changes at thresholds
- Auto-random triggers at 0

## Estimated Hours: 4
## Dependencies: None
## Requirements: REQ-015" \
"priority:p1,phase:3-ui,type:feature,category:ui,req:015" \
"Phase 3: User Interface"

create_issue "P3-011: Create SettingsController" \
"## Description
Settings screen for app configuration.

## Acceptance Criteria
- [ ] \`SettingsController.java\` (<150 lines)
- [ ] Theme toggle (Dark/Light)
- [ ] API key input (Groq - masked)
- [ ] Recommendation weighting slider (Global vs Personal)
- [ ] Hotkey configuration
- [ ] \"Clear Cache\" button
- [ ] \"Reset to Defaults\" button
- [ ] Save/Cancel buttons

## Test Cases
- Theme change applies immediately
- Settings persist after restart
- Reset restores defaults

## Estimated Hours: 4
## Dependencies: P2-008
## Requirements: REQ-016" \
"priority:p1,phase:3-ui,type:feature,category:ui,req:016" \
"Phase 3: User Interface"

create_issue "P3-012: Create LoginController" \
"## Description
Steam login UI component.

## Acceptance Criteria
- [ ] \`LoginController.java\` (<100 lines)
- [ ] \"Login with Steam\" button (when logged out)
- [ ] User profile display (avatar, name) when logged in
- [ ] Logout button
- [ ] Loading state during auth
- [ ] Error handling with user-friendly messages

## Test Cases
- Login button opens Steam auth
- Profile shows after successful login
- Logout clears session

## Estimated Hours: 4
## Dependencies: P2-006
## Requirements: REQ-012" \
"priority:p1,phase:3-ui,type:feature,category:ui,req:012" \
"Phase 3: User Interface"

create_issue "P3-014: Implement keyboard navigation" \
"## Description
Full keyboard navigation for power users.

## Acceptance Criteria
- [ ] Tab navigation between sections
- [ ] Arrow keys in hero grid
- [ ] Enter to select hero
- [ ] Escape to cancel/back
- [ ] Number keys (1-5) to select recommendations
- [ ] Ctrl+Z for undo, Ctrl+Y for redo
- [ ] Focus indicators visible
- [ ] No mouse required for full draft

## Test Cases
- Complete draft using only keyboard
- Focus indicators visible at all times

## Estimated Hours: 6
## Dependencies: P3-004
## Requirements: NFR-U003" \
"priority:p1,phase:3-ui,type:feature,category:ui" \
"Phase 3: User Interface"

create_issue "P3-016: Create light theme CSS" \
"## Description
Light color theme alternative.

## Acceptance Criteria
- [ ] \`light-theme.css\` in resources/css
- [ ] Light background (#f8fafc)
- [ ] Dark text (#0f172a)
- [ ] Radiant green: #65a30d
- [ ] Dire red: #dc2626
- [ ] WCAG 2.1 AA compliant
- [ ] Toggle works without restart

## Estimated Hours: 2
## Dependencies: P3-015
## Requirements: REQ-020" \
"priority:p2,phase:3-ui,type:feature,category:ui" \
"Phase 3: User Interface"

create_issue "P3-017: Load hero images asynchronously" \
"## Description
Async image loading to prevent UI blocking.

## Acceptance Criteria
- [ ] Hero images load in background thread
- [ ] Placeholder shown while loading
- [ ] No UI freeze during image loading
- [ ] Image cache (in-memory)
- [ ] Graceful fallback for missing images
- [ ] Grid renders immediately, images fill in

## Technical Notes
\`\`\`java
ExecutorService imageLoader = Executors.newFixedThreadPool(4);
imageLoader.submit(() -> {
    Image image = new Image(hero.iconUrl(), true); // background loading
    Platform.runLater(() -> heroCard.setImage(image));
});
\`\`\`

## Estimated Hours: 4
## Dependencies: P3-005
## Requirements: NFR-P003" \
"priority:p0,phase:3-ui,type:feature,category:ui" \
"Phase 3: User Interface"

create_issue "P3-018: UI responsiveness testing" \
"## Description
Verify all UI interactions meet performance targets.

## Acceptance Criteria
- [ ] Hero search <50ms response
- [ ] Recommendation update <200ms
- [ ] Win probability update <200ms
- [ ] No UI freeze >100ms
- [ ] No dropped frames during normal use
- [ ] Benchmark tests for critical paths
- [ ] Performance regression tests in CI

## Test Cases
- Search 10,000 iterations, 95th percentile <50ms
- Recommendation refresh benchmark
- Memory profiling shows no leaks

## Estimated Hours: 8
## Dependencies: All P3-* tasks
## Requirements: NFR-P" \
"priority:p0,phase:3-ui,type:chore,category:testing" \
"Phase 3: User Interface"

# =============================================================================
# PHASE 4: REMAINING PACKAGING ISSUES
# =============================================================================
echo ""
echo "Phase 4: Remaining Packaging Issues..."
echo "----------------------------------------"

create_issue "P4-004: Add application icons" \
"## Description
Create and add application icons for all platforms.

## Acceptance Criteria
- [ ] macOS: .icns file (multiple resolutions)
- [ ] Windows: .ico file (multiple resolutions)
- [ ] Linux: .png files (16x16 to 512x512)
- [ ] Icon visible in taskbar/dock
- [ ] Icon visible in file manager
- [ ] Consistent branding across platforms

## Estimated Hours: 2
## Dependencies: None
## Requirements: REQ-010" \
"priority:p0,phase:4-packaging,type:feature,category:devops,req:010" \
"Phase 4: Packaging"

create_issue "P4-005: Test on clean VMs" \
"## Description
Verify installers work on fresh OS installations.

## Acceptance Criteria
- [ ] macOS 12+ (Monterey) - clean VM test
- [ ] macOS 13+ (Ventura) - clean VM test
- [ ] Windows 10 - clean VM test
- [ ] Windows 11 - clean VM test
- [ ] Ubuntu 20.04 LTS - clean VM test
- [ ] Ubuntu 22.04 LTS - clean VM test
- [ ] No pre-installed Java required
- [ ] Document any OS-specific issues

## Estimated Hours: 8
## Dependencies: P4-001, P4-002, P4-003
## Requirements: REQ-010" \
"priority:p0,phase:4-packaging,type:chore,category:testing,req:010" \
"Phase 4: Packaging"

create_issue "P4-006: Configure macOS code signing" \
"## Description
Set up Apple Developer code signing to avoid Gatekeeper warnings.

## Acceptance Criteria
- [ ] Apple Developer account configured
- [ ] Signing certificate obtained
- [ ] Notarization process documented
- [ ] jpackage configured with signing identity
- [ ] App passes Gatekeeper without warning
- [ ] CI/CD includes signing step

## Technical Notes
Requires Apple Developer Program ($99/year).
Consider: skip for alpha, add for beta/GA.

## Estimated Hours: 4
## Dependencies: P4-001
## Requirements: NFR-S005" \
"priority:p1,phase:4-packaging,type:feature,category:devops" \
"Phase 4: Packaging"

create_issue "P4-010: Memory usage profiling" \
"## Description
Profile and optimize memory usage.

## Acceptance Criteria
- [ ] Profile with Java Flight Recorder / VisualVM
- [ ] Idle memory <150MB (target)
- [ ] Active memory <250MB (target)
- [ ] No memory leaks after 1 hour use
- [ ] Hero images use soft references (GC-friendly)
- [ ] Document memory optimization findings

## Test Cases
- 1 hour usage test with memory monitoring
- Stress test with rapid draft creation

## Estimated Hours: 4
## Dependencies: All Phase 3
## Requirements: NFR-P007, NFR-P008" \
"priority:p0,phase:4-packaging,type:feature,category:ui" \
"Phase 4: Packaging"

create_issue "P4-011: Create release notes template" \
"## Description
Standardized release notes format.

## Acceptance Criteria
- [ ] RELEASE_NOTES.md template created
- [ ] Sections: New Features, Bug Fixes, Known Issues, Upgrade Notes
- [ ] Version number and date
- [ ] Links to relevant issues
- [ ] Installation instructions per platform
- [ ] Automated generation from commits (optional)

## Estimated Hours: 2
## Dependencies: None
## Requirements: DevOps" \
"priority:p0,phase:4-packaging,type:chore,category:docs" \
"Phase 4: Packaging"

create_issue "P4-012: Bug bash and fixes" \
"## Description
Comprehensive testing and bug fixing before release.

## Acceptance Criteria
- [ ] All P0 requirements verified
- [ ] All P1 requirements verified
- [ ] Edge cases tested
- [ ] Error handling verified
- [ ] Performance targets met
- [ ] Zero P0 bugs open
- [ ] <3 P1 bugs open
- [ ] All platforms tested

## Estimated Hours: 16
## Dependencies: All Phase 4 tasks
## Requirements: Release gate" \
"priority:p0,phase:4-packaging,type:chore,category:testing" \
"Phase 4: Packaging"

# =============================================================================
# PHASE 5: PERSONALIZATION
# =============================================================================
echo ""
echo "Phase 5: Personalization..."
echo "----------------------------------------"

create_issue "P5-001: Integrate Steam login in UI" \
"## Description
Add Steam login button and profile display to the UI.

## Acceptance Criteria
- [ ] \"Login with Steam\" button on home screen
- [ ] Opens Steam authentication in WebView/browser
- [ ] Returns to app after successful login
- [ ] Profile avatar and name displayed
- [ ] Logout button functional
- [ ] Loading state during authentication

## Test Cases
- Login flow completes successfully
- Profile displays after login
- Logout clears session

## Estimated Hours: 4
## Dependencies: P2-006, P3-012
## Requirements: REQ-012" \
"priority:p1,phase:5-personalization,type:feature,category:ui,req:012" \
"Phase 5: Personalization"

create_issue "P5-002: Fetch match history on login" \
"## Description
Import player's recent matches from OpenDota after login.

## Acceptance Criteria
- [ ] Fetch last 100 matches on first login
- [ ] Incremental sync on subsequent logins
- [ ] Progress indicator during sync
- [ ] Handle private profiles gracefully
- [ ] Store matches in local SQLite
- [ ] Background sync (non-blocking)

## Technical Notes
- Use OpenDota /players/{account_id}/matches endpoint
- Rate limit: 60 req/min
- Cache to avoid re-fetching

## Estimated Hours: 4
## Dependencies: P2-004, P2-005, P5-001
## Requirements: REQ-013" \
"priority:p1,phase:5-personalization,type:feature,category:infra,req:013" \
"Phase 5: Personalization"

create_issue "P5-003: Calculate hero comfort scores" \
"## Description
Compute per-hero comfort/proficiency scores from match history.

## Acceptance Criteria
- [ ] ComfortScoreCalculator service
- [ ] Inputs: games played, wins, KDA, recency
- [ ] Score 0.0-1.0 per hero
- [ ] Higher weight for recent games
- [ ] Update scores after each sync
- [ ] Store in SQLite player_hero_stats table

## Formula
\`\`\`
comfortScore = (winRate * 0.4) + (gamesPlayed / 100 * 0.3) + (recencyBonus * 0.3)
recencyBonus = exponentialDecay(daysSinceLastPlayed)
\`\`\`

## Estimated Hours: 6
## Dependencies: P5-002
## Requirements: REQ-014" \
"priority:p1,phase:5-personalization,type:feature,category:domain,req:014" \
"Phase 5: Personalization"

create_issue "P5-004: Implement PersonalScorer" \
"## Description
Add personal comfort to recommendation scoring.

## Acceptance Criteria
- [ ] PersonalScorer.java in domain.recommendation
- [ ] score(Hero, comfortScores) returns ScoreComponent
- [ ] Integrates with RecommendationEngine
- [ ] Low comfort = warning indicator
- [ ] No data = \"New for you\" indicator
- [ ] Configurable weighting (global vs personal)

## Estimated Hours: 4
## Dependencies: P5-003, P1-009
## Requirements: REQ-014" \
"priority:p1,phase:5-personalization,type:feature,category:domain,req:014" \
"Phase 5: Personalization"

create_issue "P5-005: Display personal stats in UI" \
"## Description
Show player's hero statistics in the UI.

## Acceptance Criteria
- [ ] Hero tooltip shows: games, win rate, KDA
- [ ] Hero grid can sort by comfort
- [ ] Profile page shows top heroes
- [ ] Win rate trends (optional)
- [ ] Performance sparklines (optional)

## Estimated Hours: 4
## Dependencies: P5-003
## Requirements: REQ-014" \
"priority:p1,phase:5-personalization,type:feature,category:ui,req:014" \
"Phase 5: Personalization"

create_issue "P5-006: Implement recommendation weighting slider" \
"## Description
Let users adjust global vs personal recommendation weighting.

## Acceptance Criteria
- [ ] Slider in settings: 0% (global only) to 100% (personal only)
- [ ] Default: 70% global, 30% personal
- [ ] Changes apply immediately
- [ ] Persists in settings
- [ ] Tooltip explains the setting

## Estimated Hours: 2
## Dependencies: P5-004, P3-011
## Requirements: REQ-014" \
"priority:p1,phase:5-personalization,type:feature,category:ui,req:014" \
"Phase 5: Personalization"

create_issue "P5-007: Implement timer mode" \
"## Description
Add real draft timer simulation.

## Acceptance Criteria
- [ ] Toggle to enable/disable timer
- [ ] 30-second turn timer
- [ ] 130-second reserve time per team
- [ ] Reserve time activates when turn timer expires
- [ ] Auto-random when all time expires
- [ ] Visual warnings (color changes, sounds)
- [ ] Pause capability

## Test Cases
- Timer counts down correctly
- Reserve time kicks in after turn time
- Auto-random selects valid hero

## Estimated Hours: 6
## Dependencies: P3-010
## Requirements: REQ-015" \
"priority:p1,phase:5-personalization,type:feature,category:domain,req:015" \
"Phase 5: Personalization"

create_issue "P5-008: Implement draft history saving" \
"## Description
Save completed drafts for later review.

## Acceptance Criteria
- [ ] DraftHistoryService with save/load
- [ ] Auto-save on draft completion
- [ ] Manual save option
- [ ] SQLite table for draft history
- [ ] Store: picks, bans, timestamp, mode, outcome (if known)
- [ ] List view of past drafts
- [ ] Click to review step-by-step

## Estimated Hours: 4
## Dependencies: P0-005
## Requirements: REQ-023" \
"priority:p2,phase:5-personalization,type:feature,category:domain" \
"Phase 5: Personalization"

create_issue "P5-009: Implement draft export as PNG" \
"## Description
Export completed draft as shareable image.

## Acceptance Criteria
- [ ] \"Export\" button after draft completion
- [ ] PNG image with both teams' picks/bans
- [ ] Timestamp and mode included
- [ ] Win probability at completion
- [ ] File save dialog
- [ ] Clipboard copy option

## Technical Notes
Use JavaFX snapshot:
\`\`\`java
WritableImage snapshot = draftPane.snapshot(new SnapshotParameters(), null);
\`\`\`

## Estimated Hours: 4
## Dependencies: P3-006
## Requirements: REQ-024" \
"priority:p2,phase:5-personalization,type:feature,category:ui" \
"Phase 5: Personalization"

# =============================================================================
# PHASE 6: DATA PIPELINE
# =============================================================================
echo ""
echo "Phase 6: Data Pipeline..."
echo "----------------------------------------"

create_issue "P6-001: Design match data schema" \
"## Description
Design comprehensive database schema for match history and events.

## Acceptance Criteria
- [ ] Schema supports all OpenDota match fields
- [ ] Event tables for kills, deaths, items, gold/xp
- [ ] Session tracking table
- [ ] Patch version tracking
- [ ] Indexes for common queries
- [ ] ERD diagram documented

## Schema Tables
- matches (id, start_time, duration, game_mode, radiant_win, patch_version)
- match_players (match_id, account_id, hero_id, player_slot, kills, deaths, assists, ...)
- match_events (match_id, event_type, time, player_id, target_id, ...)
- user_sessions (id, user_id, start_time, end_time, match_count)
- patches (version, release_date, hero_reworks)

## Estimated Hours: 8
## Dependencies: None
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:domain,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-002: Create Flyway migrations for match tables" \
"## Description
Create database migrations for match data schema.

## Acceptance Criteria
- [ ] V002__matches_schema.sql
- [ ] V003__match_events_schema.sql
- [ ] V004__sessions_schema.sql
- [ ] V005__patches_schema.sql
- [ ] All migrations run cleanly
- [ ] Rollback scripts available

## Estimated Hours: 4
## Dependencies: P6-001
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-003: Implement MatchRepository" \
"## Description
Repository for match data CRUD operations.

## Acceptance Criteria
- [ ] MatchRepository interface in domain
- [ ] PostgresMatchRepository implementation
- [ ] Methods: save, findById, findByUserId, findByHero
- [ ] Batch insert support (for sync)
- [ ] Efficient queries with proper indexes
- [ ] Unit tests

## Estimated Hours: 6
## Dependencies: P6-002
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-004: Implement MatchEventRepository" \
"## Description
Repository for granular match events (kills, items, etc).

## Acceptance Criteria
- [ ] MatchEventRepository interface
- [ ] PostgresMatchEventRepository implementation
- [ ] Methods: saveEvents, findByMatch, findByPlayer, findByType
- [ ] Efficient bulk inserts
- [ ] Query by time range within match

## Estimated Hours: 6
## Dependencies: P6-002
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-005: Implement Token Bucket rate limiter" \
"## Description
Rate limiter that respects API limits while allowing bursts.

## Acceptance Criteria
- [ ] TokenBucketRateLimiter.java
- [ ] Configurable: max tokens, refill rate
- [ ] acquire() blocks until token available
- [ ] tryAcquire() returns immediately
- [ ] Thread-safe
- [ ] Respects OpenDota 60 req/min limit

## Technical Notes
\`\`\`java
public class TokenBucketRateLimiter {
    private final int maxTokens;
    private final Duration refillPeriod;
    private AtomicInteger tokens;
    private Instant lastRefill;
    
    public void acquire() throws InterruptedException {
        while (tokens.get() <= 0) {
            refillIfNeeded();
            Thread.sleep(100);
        }
        tokens.decrementAndGet();
    }
}
\`\`\`

## Estimated Hours: 4
## Dependencies: None
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-006: Implement Priority Queue for API requests" \
"## Description
Queue that prioritizes user requests over background enrichment.

## Acceptance Criteria
- [ ] PriorityApiQueue.java
- [ ] Priority levels: USER_INITIATED (high), ENRICHMENT (low)
- [ ] User requests always processed first
- [ ] Queue persists across restarts (optional)
- [ ] Progress tracking per request
- [ ] Thread-safe

## Estimated Hours: 6
## Dependencies: P6-005
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-007: Implement OpenDotaMatchClient (extended)" \
"## Description
Extended OpenDota client for fetching parsed match data.

## Acceptance Criteria
- [ ] fetchParsedMatch(matchId) - full match details
- [ ] fetchMatchEvents(matchId) - kills, items, runes, etc.
- [ ] fetchPlayerRecentMatches(accountId, limit)
- [ ] Rate limiting integrated
- [ ] Retry with backoff on failures
- [ ] Parse all relevant fields

## Estimated Hours: 8
## Dependencies: P6-005, P2-004
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-008: Implement StratzMatchClient" \
"## Description
STRATZ API client for supplemental match data.

## Acceptance Criteria
- [ ] StratzMatchClient.java
- [ ] GraphQL queries for match data
- [ ] Synergy/counter data from STRATZ
- [ ] Rate limiting
- [ ] Used as fallback or supplement to OpenDota

## Estimated Hours: 6
## Dependencies: P6-005
## Requirements: REQ-040" \
"priority:p2,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-009: Implement incremental sync logic" \
"## Description
Sync only new matches since last sync.

## Acceptance Criteria
- [ ] Track last sync timestamp per user
- [ ] Fetch only matches after last sync
- [ ] Handle gaps (missed matches)
- [ ] Merge with existing data (no duplicates)
- [ ] Update last sync on success

## Technical Notes
- Store lastSyncMatchId per user
- Query /matches?since_match_id=X
- Handle pagination

## Estimated Hours: 6
## Dependencies: P6-003, P6-007
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-010: Implement background job scheduler" \
"## Description
Non-blocking background job system for data syncing.

## Acceptance Criteria
- [ ] BackgroundJobScheduler.java
- [ ] Jobs run on separate thread pool
- [ ] Progress tracking API
- [ ] Job cancellation support
- [ ] Error handling with retries
- [ ] Job history logging

## Estimated Hours: 8
## Dependencies: None
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-011: Implement session detection" \
"## Description
Group matches into play sessions based on time gaps.

## Acceptance Criteria
- [ ] SessionDetector.java
- [ ] Session = matches with <2 hour gaps
- [ ] Track: session start, end, match count, W/L
- [ ] Store sessions in database
- [ ] Query sessions by date range

## Technical Notes
\`\`\`python
def detect_sessions(matches, gap_threshold=2*60*60):
    sessions = []
    current_session = [matches[0]]
    for match in matches[1:]:
        if match.start_time - current_session[-1].end_time > gap_threshold:
            sessions.append(current_session)
            current_session = []
        current_session.append(match)
    return sessions
\`\`\`

## Estimated Hours: 4
## Dependencies: P6-003
## Requirements: REQ-044" \
"priority:p1,phase:6-pipeline,type:feature,category:domain,req:044" \
"Phase 6: Data Pipeline"

create_issue "P6-012: Implement patch boundary detection" \
"## Description
Track Dota 2 patches and hero reworks for analysis boundaries.

## Acceptance Criteria
- [ ] PatchTracker.java
- [ ] Store patch versions with release dates
- [ ] Track hero reworks (significant ability changes)
- [ ] getPatchForMatch(matchId) method
- [ ] getMatchesSamePatch(matchId) filter
- [ ] Manual update process for new patches

## Estimated Hours: 4
## Dependencies: P6-002
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:domain,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-013: Implement Redis caching for match data" \
"## Description
Cache recently accessed match data in Redis.

## Acceptance Criteria
- [ ] Cache recent matches (24hr TTL)
- [ ] Cache analysis results
- [ ] Cache-aside pattern
- [ ] Invalidation on new data
- [ ] Fallback to DB on cache miss

## Estimated Hours: 4
## Dependencies: P6-003
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-014: Implement sync progress API" \
"## Description
API endpoint for clients to check sync status.

## Acceptance Criteria
- [ ] GET /api/v1/sync/status/{userId}
- [ ] Returns: status (pending/running/complete/error), progress %, matches synced
- [ ] WebSocket updates (optional)
- [ ] Client can poll status

## Estimated Hours: 4
## Dependencies: P6-010
## Requirements: REQ-040" \
"priority:p1,phase:6-pipeline,type:feature,category:infra,req:040" \
"Phase 6: Data Pipeline"

create_issue "P6-015: Pipeline integration tests" \
"## Description
End-to-end tests for the data pipeline.

## Acceptance Criteria
- [ ] Test full sync flow with mocked APIs
- [ ] Test incremental sync
- [ ] Test session detection
- [ ] Test rate limiting behavior
- [ ] Test error recovery
- [ ] Test data integrity

## Estimated Hours: 8
## Dependencies: All P6-*
## Requirements: Quality gate" \
"priority:p1,phase:6-pipeline,type:chore,category:testing" \
"Phase 6: Data Pipeline"

# =============================================================================
# PHASE 7: ANALYSIS ENGINE
# =============================================================================
echo ""
echo "Phase 7: Analysis Engine (this will take a moment)..."
echo "----------------------------------------"

# Stage 1: Macro Analysis
create_issue "P7-001: Implement HeroPerformanceAnalyzer" \
"## Description
Analyze hero performance across match history.

## Acceptance Criteria
- [ ] HeroPerformanceAnalyzer.java
- [ ] Calculate: win rate, games, KDA by time range
- [ ] Filter by: patch, role, game mode
- [ ] Confidence intervals for small samples
- [ ] Compare to rank average benchmarks

## Output Structure
\`\`\`java
record HeroPerformance(
    int heroId,
    int games,
    int wins,
    double winRate,
    double confidenceLower,
    double confidenceUpper,
    double avgKda,
    LocalDateTime lastPlayed
) {}
\`\`\`

## Estimated Hours: 6
## Dependencies: P6-003
## Requirements: REQ-041" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:041" \
"Phase 7: Analysis Engine"

create_issue "P7-002: Implement confidence interval calculation" \
"## Description
Wilson score confidence intervals for win rates.

## Acceptance Criteria
- [ ] WilsonScore.java utility
- [ ] Calculate 95% confidence intervals
- [ ] Handle small sample sizes correctly
- [ ] Flag unreliable data (<5 games)
- [ ] Unit tests with known values

## Formula (Wilson Score)
\`\`\`
CI = (p + z²/2n ± z√(p(1-p)/n + z²/4n²)) / (1 + z²/n)
where z = 1.96 for 95% confidence
\`\`\`

## Estimated Hours: 4
## Dependencies: None
## Requirements: REQ-041" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:041" \
"Phase 7: Analysis Engine"

create_issue "P7-003: Implement MatchupAnalyzer" \
"## Description
Analyze performance against specific enemy heroes.

## Acceptance Criteria
- [ ] MatchupAnalyzer.java
- [ ] Win rate vs each enemy hero
- [ ] Identify problem matchups (WR < 40%)
- [ ] Identify strong matchups (WR > 60%)
- [ ] Account for sample size
- [ ] Filter by your hero

## Estimated Hours: 6
## Dependencies: P6-003, P7-002
## Requirements: REQ-041" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:041" \
"Phase 7: Analysis Engine"

create_issue "P7-004: Implement TemporalAnalyzer" \
"## Description
Detect patterns based on time of day and day of week.

## Acceptance Criteria
- [ ] TemporalAnalyzer.java
- [ ] Win rate by hour of day (0-23)
- [ ] Win rate by day of week
- [ ] Identify best/worst times to play
- [ ] Statistical significance testing

## Example Output
\`\`\`
Best times: Sat 2-6pm (62% WR, 45 games)
Worst times: Weekday 11pm+ (38% WR, 30 games)
\`\`\`

## Estimated Hours: 6
## Dependencies: P6-003
## Requirements: REQ-041" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:041" \
"Phase 7: Analysis Engine"

create_issue "P7-005: Implement StreakAnalyzer" \
"## Description
Detect win/loss streaks and momentum patterns.

## Acceptance Criteria
- [ ] StreakAnalyzer.java
- [ ] Track current streak
- [ ] Find longest streaks
- [ ] Win rate after 2+ wins vs 2+ losses
- [ ] Detect \"break even\" patterns

## Estimated Hours: 4
## Dependencies: P6-003
## Requirements: REQ-041" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:041" \
"Phase 7: Analysis Engine"

create_issue "P7-006: Implement RoleDistributionAnalyzer" \
"## Description
Analyze role/position distribution and flexibility.

## Acceptance Criteria
- [ ] RoleDistributionAnalyzer.java
- [ ] Position frequency (1-5)
- [ ] Role flexibility score
- [ ] Win rate by position
- [ ] Detect one-trick vs flex player

## Estimated Hours: 4
## Dependencies: P6-003
## Requirements: REQ-041" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:041" \
"Phase 7: Analysis Engine"

create_issue "P7-007: Macro analysis unit tests" \
"## Description
Unit tests for Stage 1 analyzers.

## Acceptance Criteria
- [ ] 80%+ coverage on P7-001 to P7-006
- [ ] Test edge cases (no data, single game, etc.)
- [ ] Test statistical calculations
- [ ] Test with mock data

## Estimated Hours: 6
## Dependencies: P7-001 to P7-006
## Requirements: Quality gate" \
"priority:p1,phase:7-analysis,type:chore,category:testing" \
"Phase 7: Analysis Engine"

# Stage 2: Phase Analysis
create_issue "P7-008: Implement LaningAnalyzer" \
"## Description
Analyze laning phase performance (first 10-12 minutes).

## Acceptance Criteria
- [ ] LaningAnalyzer.java
- [ ] Track: CS@10, K/D@10, XP@10
- [ ] Compare to benchmarks by rank/role
- [ ] Lane outcome (won/lost/even)
- [ ] Identify laning weaknesses

## Estimated Hours: 8
## Dependencies: P6-004
## Requirements: REQ-042" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:042" \
"Phase 7: Analysis Engine"

create_issue "P7-009: Implement FarmEfficiencyAnalyzer" \
"## Description
Analyze farming patterns and efficiency.

## Acceptance Criteria
- [ ] FarmEfficiencyAnalyzer.java
- [ ] GPM over time (not just final)
- [ ] Compare to role benchmarks
- [ ] Detect farm drop-offs
- [ ] Net worth differential tracking

## Estimated Hours: 6
## Dependencies: P6-004
## Requirements: REQ-042" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:042" \
"Phase 7: Analysis Engine"

create_issue "P7-010: Implement TimingWindowAnalyzer" \
"## Description
Detect power spike windows and scaling patterns.

## Acceptance Criteria
- [ ] TimingWindowAnalyzer.java
- [ ] Identify hero power spikes (item timings, level spikes)
- [ ] Track game tempo (early/mid/late game focus)
- [ ] Compare actual timings to expected

## Estimated Hours: 6
## Dependencies: P6-004
## Requirements: REQ-042" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:042" \
"Phase 7: Analysis Engine"

create_issue "P7-011: Implement GamePhaseClassifier" \
"## Description
Classify game segments into phases.

## Acceptance Criteria
- [ ] GamePhaseClassifier.java
- [ ] Laning phase: 0-12 minutes
- [ ] Mid game: 12-30 minutes  
- [ ] Late game: 30+ minutes
- [ ] Dynamic detection based on events (towers, Roshan)

## Estimated Hours: 4
## Dependencies: P6-004
## Requirements: REQ-042" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:042" \
"Phase 7: Analysis Engine"

create_issue "P7-012: Implement benchmark data service" \
"## Description
Fetch and store rank-specific performance benchmarks.

## Acceptance Criteria
- [ ] BenchmarkService.java
- [ ] Fetch from OpenDota benchmarks API
- [ ] Store per-rank averages (Herald to Immortal)
- [ ] CS, GPM, XPM, KDA benchmarks by hero
- [ ] Update periodically (weekly?)

## Estimated Hours: 6
## Dependencies: P6-007
## Requirements: REQ-042" \
"priority:p1,phase:7-analysis,type:feature,category:infra,req:042" \
"Phase 7: Analysis Engine"

create_issue "P7-013: Phase analysis unit tests" \
"## Description
Unit tests for Stage 2 analyzers.

## Acceptance Criteria
- [ ] 80%+ coverage on P7-008 to P7-012
- [ ] Test with realistic match data
- [ ] Test benchmark comparisons
- [ ] Test phase classification edge cases

## Estimated Hours: 6
## Dependencies: P7-008 to P7-012
## Requirements: Quality gate" \
"priority:p1,phase:7-analysis,type:chore,category:testing" \
"Phase 7: Analysis Engine"

# Stage 3: Micro Analysis
create_issue "P7-014: Implement DeathAnalyzer" \
"## Description
Detailed analysis of deaths and their patterns.

## Acceptance Criteria
- [ ] DeathAnalyzer.java
- [ ] Death timing clustering (early game deaths, respawn deaths)
- [ ] Death location heatmap data
- [ ] Death streak detection
- [ ] Killer analysis (who kills you most)
- [ ] Avoidable death classification

## Output
\`\`\`java
record DeathPattern(
    String pattern,  // \"EARLY_DEATHS\", \"DEATH_AFTER_RESPAWN\", etc.
    int occurrences,
    double avgGoldLost,
    List<Integer> matchIds
) {}
\`\`\`

## Estimated Hours: 8
## Dependencies: P6-004
## Requirements: REQ-043" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:043" \
"Phase 7: Analysis Engine"

create_issue "P7-015: Implement PositioningAnalyzer" \
"## Description
Analyze map positioning patterns.

## Acceptance Criteria
- [ ] PositioningAnalyzer.java
- [ ] Map quadrant frequency
- [ ] Danger zone detection (dying in enemy territory)
- [ ] Farming patterns (jungle vs lane)
- [ ] Team fight positioning

## Estimated Hours: 8
## Dependencies: P6-004
## Requirements: REQ-043" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:043" \
"Phase 7: Analysis Engine"

create_issue "P7-016: Implement ItemTimingAnalyzer" \
"## Description
Compare item timings to benchmarks.

## Acceptance Criteria
- [ ] ItemTimingAnalyzer.java
- [ ] Track key item timings (BKB, Blink, etc.)
- [ ] Compare to hero/rank benchmarks
- [ ] Flag slow timings
- [ ] Correlate timings with win rate

## Estimated Hours: 6
## Dependencies: P6-004, P7-012
## Requirements: REQ-043" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:043" \
"Phase 7: Analysis Engine"

create_issue "P7-017: Implement FightParticipationAnalyzer" \
"## Description
Analyze team fight participation and contribution.

## Acceptance Criteria
- [ ] FightParticipationAnalyzer.java
- [ ] Fight attendance rate
- [ ] Kill/assist ratio in fights
- [ ] Damage contribution
- [ ] Identify \"missing fights\" pattern

## Estimated Hours: 6
## Dependencies: P6-004
## Requirements: REQ-043" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:043" \
"Phase 7: Analysis Engine"

create_issue "P7-018: Implement ObjectiveAnalyzer" \
"## Description
Analyze objective participation (towers, Roshan).

## Acceptance Criteria
- [ ] ObjectiveAnalyzer.java
- [ ] Tower damage contribution
- [ ] Roshan participation
- [ ] Objective taking timing
- [ ] Correlation with wins

## Estimated Hours: 4
## Dependencies: P6-004
## Requirements: REQ-043" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:043" \
"Phase 7: Analysis Engine"

create_issue "P7-019: Micro analysis unit tests" \
"## Description
Unit tests for Stage 3 analyzers.

## Acceptance Criteria
- [ ] 80%+ coverage on P7-014 to P7-018
- [ ] Test with detailed event data
- [ ] Test pattern detection accuracy

## Estimated Hours: 6
## Dependencies: P7-014 to P7-018
## Requirements: Quality gate" \
"priority:p1,phase:7-analysis,type:chore,category:testing" \
"Phase 7: Analysis Engine"

# Stage 4: Cross-Reference & Session
create_issue "P7-020: Implement RootCauseAnalyzer" \
"## Description
Chain analysis stages to find root causes.

## Acceptance Criteria
- [ ] RootCauseAnalyzer.java
- [ ] Connect: deaths → farm deficit → late items → loss
- [ ] Identify primary contributing factors
- [ ] Generate causal chains
- [ ] Prioritize by impact

## Example Chain
\`\`\`
Root cause analysis for 42% WR on Juggernaut:
1. Early deaths (avg 2.3 before 10min) → Farm deficit
2. Farm deficit → BF at 18min (benchmark: 14min)
3. Late BF → Missed timing window → Lost fights
4. Lost fights → Game losses
Recommendation: Focus on laning survival
\`\`\`

## Estimated Hours: 8
## Dependencies: P7-001 to P7-018
## Requirements: REQ-044" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:044" \
"Phase 7: Analysis Engine"

create_issue "P7-021: Implement SessionAnalyzer" \
"## Description
Analyze performance degradation within sessions.

## Acceptance Criteria
- [ ] SessionAnalyzer.java
- [ ] Win rate by game # in session
- [ ] Performance metrics over session
- [ ] Detect fatigue/tilt patterns
- [ ] Optimal session length recommendation

## Estimated Hours: 6
## Dependencies: P6-011
## Requirements: REQ-044" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:044" \
"Phase 7: Analysis Engine"

create_issue "P7-022: Implement TiltDetector" \
"## Description
Detect tilt from behavioral signals.

## Acceptance Criteria
- [ ] TiltDetector.java
- [ ] Signals: instant requeue after loss, unusual hero picks
- [ ] Signals: item destruction, feeding patterns
- [ ] Tilt score calculation
- [ ] Confidence level

## Behavioral Signals
\`\`\`python
TILT_SIGNALS = {
    'instant_requeue': 3,      # <30s between games
    'unusual_hero': 2,         # Hero not in top 10
    'losing_streak_continue': 2,
    'kda_drop': 3,             # KDA 50% below average
}
\`\`\`

## Estimated Hours: 8
## Dependencies: P6-011, P7-021
## Requirements: REQ-044" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:044" \
"Phase 7: Analysis Engine"

create_issue "P7-023: Implement PartyAnalyzer" \
"## Description
Analyze solo vs party performance.

## Acceptance Criteria
- [ ] PartyAnalyzer.java
- [ ] Solo queue win rate vs party
- [ ] Performance with specific friends
- [ ] Party size impact
- [ ] Identify good/bad party combinations

## Estimated Hours: 6
## Dependencies: P6-003
## Requirements: REQ-044" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:044" \
"Phase 7: Analysis Engine"

create_issue "P7-024: Implement QueueExhaustionDetector" \
"## Description
Detect performance drop from excessive queuing.

## Acceptance Criteria
- [ ] QueueExhaustionDetector.java
- [ ] Games-per-session vs win rate correlation
- [ ] Identify optimal session length
- [ ] Warning thresholds

## Estimated Hours: 4
## Dependencies: P7-021
## Requirements: REQ-044" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:044" \
"Phase 7: Analysis Engine"

create_issue "P7-025: Implement DraftOrderAnalyzer" \
"## Description
Analyze counterpick patterns and draft order impact.

## Acceptance Criteria
- [ ] DraftOrderAnalyzer.java
- [ ] Track when user's hero is picked (phase 1/2/3)
- [ ] Detect counterpick frequency
- [ ] Win rate by pick phase
- [ ] Identify heroes that get countered often

## Example Output
\`\`\`
Hero: Medusa
- Picked early (Phase 1-2): 75% get countered
- Common counters: Anti-Mage (45%), PL (30%)
- Win rate when countered: 35%
- Win rate when NOT countered: 58%
Recommendation: Save Medusa for last pick
\`\`\`

## Estimated Hours: 8
## Dependencies: P6-003
## Requirements: REQ-045" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:045" \
"Phase 7: Analysis Engine"

create_issue "P7-026: Implement ComebackThrowDetector" \
"## Description
Detect critical swing points in games.

## Acceptance Criteria
- [ ] ComebackThrowDetector.java
- [ ] Track gold advantage over time
- [ ] Identify swing points (>5k gold swing)
- [ ] Classify: comeback or throw
- [ ] Correlate with events (fights, deaths)

## Estimated Hours: 6
## Dependencies: P6-004
## Requirements: REQ-044" \
"priority:p1,phase:7-analysis,type:feature,category:analysis,req:044" \
"Phase 7: Analysis Engine"

create_issue "P7-027: Cross-reference analysis unit tests" \
"## Description
Unit tests for Stage 4 analyzers.

## Acceptance Criteria
- [ ] 80%+ coverage on P7-020 to P7-026
- [ ] Test causal chain detection
- [ ] Test session analysis accuracy
- [ ] Test tilt detection thresholds

## Estimated Hours: 8
## Dependencies: P7-020 to P7-026
## Requirements: Quality gate" \
"priority:p1,phase:7-analysis,type:chore,category:testing" \
"Phase 7: Analysis Engine"

# =============================================================================
# PHASE 8: INSIGHTS UI
# =============================================================================
echo ""
echo "Phase 8: Insights UI..."
echo "----------------------------------------"

create_issue "P8-001: Design Insights page layout" \
"## Description
Design unique Insights page that differs from competitors.

## Acceptance Criteria
- [ ] Wireframes/mockups created
- [ ] \"What's Working\" section design
- [ ] \"What's Not Working\" section design
- [ ] Progress section design
- [ ] Draft history list design
- [ ] Distinctive aesthetic (not DotaPlus clone)

## Design Goals
- Analyst, not dashboard - provide insights, not just data
- Action-oriented - every insight has a recommendation
- Clear hierarchy - most important issues first

## Estimated Hours: 6
## Dependencies: None
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:046" \
"Phase 8: Insights UI"

create_issue "P8-002: Implement InsightsView shell" \
"## Description
Create the Insights page structure.

## Acceptance Criteria
- [ ] InsightsView.java (main page)
- [ ] Proper layout structure
- [ ] Empty states for no data
- [ ] Loading states
- [ ] Error handling

## Estimated Hours: 4
## Dependencies: P8-001
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:046" \
"Phase 8: Insights UI"

create_issue "P8-003: Implement What's Working component" \
"## Description
Display positive patterns and strengths.

## Acceptance Criteria
- [ ] WhatsWorkingComponent.java
- [ ] List of positive insights
- [ ] Each insight: title, evidence, recommendation
- [ ] Expandable details
- [ ] Sorted by confidence/impact

## Example
\`\`\`
✅ YOUR INVOKER IS ON FIRE
You're 8-2 (80% WR) on Invoker this week.
• 45% higher KDA than your average
• 3min faster Aghs timing
Keep picking Invoker when it fits the draft!
\`\`\`

## Estimated Hours: 8
## Dependencies: P7-001 to P7-007, P8-002
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:046" \
"Phase 8: Insights UI"

create_issue "P8-004: Implement What's Not Working component" \
"## Description
Display problems with actionable recommendations.

## Acceptance Criteria
- [ ] WhatsNotWorkingComponent.java
- [ ] List of problem insights
- [ ] Each: problem, evidence, specific recommendation
- [ ] Priority indicators (fix this first)
- [ ] Link to relevant data

## Example
\`\`\`
⚠️ JUGGERNAUT ISN'T WORKING RIGHT NOW
You're 3-9 (25% WR) on Juggernaut in 7 days.
Root cause: Early deaths → slow BF → missed timing
RECOMMENDATION: Try Phantom Assassin instead -
you're 65% WR and similar playstyle.
\`\`\`

## Estimated Hours: 10
## Dependencies: P7-020, P8-002
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:046" \
"Phase 8: Insights UI"

create_issue "P8-005: Implement time range selector" \
"## Description
Allow users to filter insights by time range.

## Acceptance Criteria
- [ ] TimeRangeSelector.java component
- [ ] Options: 7 days, 30 days, 90 days, This Patch, All Time
- [ ] Updates all components on change
- [ ] Persists selection

## Estimated Hours: 4
## Dependencies: P8-002
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:046" \
"Phase 8: Insights UI"

create_issue "P8-006: Implement ProgressSection" \
"## Description
Show user's progress and trends.

## Acceptance Criteria
- [ ] ProgressSection.java
- [ ] Win rate trend chart
- [ ] Hero performance changes
- [ ] Role distribution
- [ ] MMR trend (if available)
- [ ] Comparison to previous period

## Estimated Hours: 8
## Dependencies: P7-001 to P7-006, P8-002
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:046" \
"Phase 8: Insights UI"

create_issue "P8-007: Implement DraftHistoryList" \
"## Description
Scrollable list of past games.

## Acceptance Criteria
- [ ] DraftHistoryList.java
- [ ] Each row: hero, result, KDA, duration, time ago
- [ ] Color coding (green/red for W/L)
- [ ] Infinite scroll / pagination
- [ ] Filter by hero, result, mode
- [ ] Click opens detail view

## Estimated Hours: 8
## Dependencies: P6-003, P8-002
## Requirements: REQ-047" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:047" \
"Phase 8: Insights UI"

create_issue "P8-008: Implement MatchDetailPopout" \
"## Description
Detailed match breakdown on click.

## Acceptance Criteria
- [ ] MatchDetailPopout.java
- [ ] Full match summary
- [ ] Death timeline with analysis
- [ ] Item timing comparison
- [ ] Phase breakdown (laning/mid/late)
- [ ] Key events highlighted
- [ ] Close button

## Estimated Hours: 12
## Dependencies: P7-014 to P7-018, P8-007
## Requirements: REQ-047" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:047" \
"Phase 8: Insights UI"

create_issue "P8-009: Implement insight explanation generator" \
"## Description
Generate human-readable explanations from analysis data.

## Acceptance Criteria
- [ ] InsightExplanationGenerator.java
- [ ] Template-based text generation
- [ ] Include: problem, evidence, recommendation
- [ ] Vary phrasing (not repetitive)
- [ ] Appropriate tone (coaching, not critical)

## Estimated Hours: 8
## Dependencies: P7-020
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:domain,req:046" \
"Phase 8: Insights UI"

create_issue "P8-010: Implement LLM insight enhancement (optional)" \
"## Description
Use AI to polish insight explanations.

## Acceptance Criteria
- [ ] Optional AI enhancement pass
- [ ] Falls back to template if AI fails
- [ ] Cache AI responses
- [ ] Rate limit AI calls
- [ ] A/B test AI vs template

## Estimated Hours: 6
## Dependencies: P8-009, P2-001
## Requirements: REQ-046" \
"priority:p2,phase:8-insights,type:feature,category:infra,req:046" \
"Phase 8: Insights UI"

create_issue "P8-011: Implement recommendation cards" \
"## Description
Display actionable recommendations.

## Acceptance Criteria
- [ ] RecommendationCard.java
- [ ] Types: \"Try this hero\", \"Ban this\", \"Save for last pick\"
- [ ] Include evidence/reasoning
- [ ] Quick action buttons (add to pool, etc.)

## Estimated Hours: 6
## Dependencies: P7-020, P7-025
## Requirements: REQ-046" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:046" \
"Phase 8: Insights UI"

create_issue "P8-012: Implement session warnings" \
"## Description
Display tilt/exhaustion warnings.

## Acceptance Criteria
- [ ] SessionWarning.java component
- [ ] Non-intrusive notification style
- [ ] Dismissable
- [ ] \"You seem tilted\" with evidence
- [ ] \"Consider taking a break\" suggestion
- [ ] Configurable sensitivity

## Estimated Hours: 6
## Dependencies: P7-022
## Requirements: REQ-044" \
"priority:p1,phase:8-insights,type:feature,category:ui,req:044" \
"Phase 8: Insights UI"

create_issue "P8-013: Insights page integration tests" \
"## Description
End-to-end tests for Insights page.

## Acceptance Criteria
- [ ] Test full data flow (analysis → UI)
- [ ] Test empty states
- [ ] Test time range changes
- [ ] Test match detail popout
- [ ] Test with various data scenarios

## Estimated Hours: 8
## Dependencies: All P8-*
## Requirements: Quality gate" \
"priority:p1,phase:8-insights,type:chore,category:testing" \
"Phase 8: Insights UI"

create_issue "P8-014: Performance optimization" \
"## Description
Ensure Insights page loads quickly.

## Acceptance Criteria
- [ ] Page loads in <1s with 100 matches
- [ ] Analysis results cached
- [ ] Lazy loading for history list
- [ ] No UI blocking
- [ ] Profile and optimize hot paths

## Estimated Hours: 6
## Dependencies: All P8-*
## Requirements: NFR-P" \
"priority:p1,phase:8-insights,type:feature,category:ui" \
"Phase 8: Insights UI"

# =============================================================================
# DONE
# =============================================================================
echo ""
echo "========================================"
echo "Issue creation complete!"
echo "========================================"
echo ""
echo "Summary of issues created:"
echo "  - Phase 3 remaining: 13 issues"
echo "  - Phase 4 remaining: 6 issues"
echo "  - Phase 5: 9 issues"
echo "  - Phase 6: 15 issues"
echo "  - Phase 7: 27 issues"
echo "  - Phase 8: 14 issues"
echo "  - TOTAL: 84 new issues"
echo ""
echo "Next steps:"
echo "1. Review issues at: https://github.com/$REPO/issues"
echo "2. Add issues to project board"
echo "3. Assign milestones if not set correctly"
echo ""

