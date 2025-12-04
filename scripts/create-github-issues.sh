#!/bin/bash

# =============================================================================
# Dota 2 Draft Assistant - GitHub Issue & Project Board Setup Script
# =============================================================================
# 
# Prerequisites:
#   1. GitHub CLI installed: brew install gh
#   2. Authenticated: gh auth login
#   3. Run from repository root
#
# Usage:
#   chmod +x scripts/create-github-issues.sh
#   ./scripts/create-github-issues.sh
#
# This script creates:
#   - GitHub Project Board with phases as columns
#   - Milestones for each phase
#   - Labels for categorization
#   - Issues for all implementation tasks
# =============================================================================

set -e  # Exit on error

REPO="malibuwileyu/dota2_draft_assistant"
PROJECT_NAME="Dota 2 Draft Assistant v3 Rebuild"

echo "========================================"
echo "Dota 2 Draft Assistant - Issue Setup"
echo "========================================"

# Check if gh is installed
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed."
    echo "Install with: brew install gh"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "Error: Not authenticated with GitHub CLI."
    echo "Run: gh auth login"
    exit 1
fi

echo ""
echo "Creating labels..."
echo "----------------------------------------"

# Create labels (will skip if already exists)
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

# Priority labels
create_label "priority:p0" "b60205" "Launch blocker - must have"
create_label "priority:p1" "d93f0b" "Important but not critical"
create_label "priority:p2" "fbca04" "Nice to have"

# Phase labels
create_label "phase:0-foundation" "0e8a16" "Phase 0: Project foundation"
create_label "phase:1-domain" "1d76db" "Phase 1: Core domain logic"
create_label "phase:2-infrastructure" "5319e7" "Phase 2: Infrastructure layer"
create_label "phase:3-ui" "f9d0c4" "Phase 3: User interface"
create_label "phase:4-packaging" "d4c5f9" "Phase 4: Packaging & release"
create_label "phase:5-personalization" "c5def5" "Phase 5: Personalization"

# Category labels
create_label "category:domain" "bfd4f2" "Domain layer (pure business logic)"
create_label "category:ui" "e99695" "UI layer (JavaFX)"
create_label "category:infra" "d4c5f9" "Infrastructure layer"
create_label "category:testing" "c2e0c6" "Testing related"
create_label "category:docs" "fef2c0" "Documentation"
create_label "category:devops" "bfdadc" "CI/CD and build"

# Type labels
create_label "type:feature" "a2eeef" "New feature"
create_label "type:bug" "d73a4a" "Bug fix"
create_label "type:refactor" "fef2c0" "Code refactoring"
create_label "type:chore" "ededed" "Maintenance task"

# REQ labels for traceability
create_label "req:001" "c5def5" "REQ-001: Captain's Mode Draft"
create_label "req:002" "c5def5" "REQ-002: All Pick Mode"
create_label "req:003" "c5def5" "REQ-003: Hero Database"
create_label "req:004" "c5def5" "REQ-004: Hero Selection Grid"
create_label "req:005" "c5def5" "REQ-005: Local Recommendations"
create_label "req:006" "c5def5" "REQ-006: Win Probability"
create_label "req:007" "c5def5" "REQ-007: Undo/Redo"
create_label "req:008" "c5def5" "REQ-008: Team Composition"
create_label "req:009" "c5def5" "REQ-009: Perspective Toggle"
create_label "req:010" "c5def5" "REQ-010: Application Packaging"
create_label "req:011" "c5def5" "REQ-011: Groq LLM Integration"
create_label "req:012" "c5def5" "REQ-012: Steam Authentication"
create_label "req:013" "c5def5" "REQ-013: Match History Import"
create_label "req:014" "c5def5" "REQ-014: Personalized Recommendations"
create_label "req:015" "c5def5" "REQ-015: Draft Timer Mode"
create_label "req:016" "c5def5" "REQ-016: Settings Persistence"

echo ""
echo "Creating milestones..."
echo "----------------------------------------"

# Create milestones
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

create_milestone "Phase 0: Foundation" "Project setup, no features. Gradle, packages, domain models, SQLite." "2025-01-17T00:00:00Z"
create_milestone "Phase 1: Core Domain" "Draft engine, recommendation engine, analysis engine. Pure Java." "2025-02-07T00:00:00Z"
create_milestone "Phase 2: Infrastructure" "API clients (Groq, OpenDota, Steam), repositories, error handling." "2025-02-28T00:00:00Z"
create_milestone "Phase 3: User Interface" "JavaFX controllers, components, CSS themes, keyboard navigation." "2025-03-28T00:00:00Z"
create_milestone "Phase 4: Packaging" "jpackage installers for macOS, Windows, Linux. CI/CD. Release." "2025-04-11T00:00:00Z"
create_milestone "Alpha Release" "Core draft simulation with local recommendations. REQ-001 to REQ-010." "2025-04-11T00:00:00Z"
create_milestone "Phase 5: Personalization" "Steam auth, match history, personalized recommendations, timer." "2025-05-09T00:00:00Z"
create_milestone "Beta Release" "Alpha + Groq LLM + Steam auth + personalization. REQ-011 to REQ-016." "2025-05-09T00:00:00Z"
create_milestone "GA Release v1.0" "Bug fixes, polish, full release." "2025-06-06T00:00:00Z"

echo ""
echo "Creating project board..."
echo "----------------------------------------"

# Create project board (GitHub Projects v2)
# Note: Projects v2 requires GraphQL API

PROJECT_ID=$(gh api graphql -f query='
mutation {
  createProjectV2(input: {
    ownerId: "'"$(gh api users/malibuwileyu --jq '.node_id')"'",
    title: "'"$PROJECT_NAME"'"
  }) {
    projectV2 {
      id
      number
    }
  }
}' --jq '.data.createProjectV2.projectV2.id' 2>/dev/null || echo "")

if [ -n "$PROJECT_ID" ]; then
    echo "✓ Created project board: $PROJECT_NAME"
    echo "  Project ID: $PROJECT_ID"
else
    echo "• Project board may already exist or creation failed"
    echo "  Create manually at: https://github.com/$REPO/projects"
fi

echo ""
echo "Creating issues..."
echo "----------------------------------------"

# Function to create an issue
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
}

# =============================================================================
# PHASE 0: FOUNDATION
# =============================================================================

echo ""
echo "Phase 0: Foundation..."
echo "----------------------------------------"

create_issue "P0-001: Initialize Gradle project with Java 21" \
"## Description
Initialize a new Gradle project configured for Java 21 LTS.

## Acceptance Criteria
- [ ] \`build.gradle.kts\` created with Java 21 toolchain
- [ ] \`settings.gradle.kts\` with project name \`dota2-draft-assistant\`
- [ ] Gradle wrapper files committed (\`gradlew\`, \`gradlew.bat\`)
- [ ] \`.gitignore\` updated for Gradle
- [ ] \`./gradlew build\` passes

## Technical Notes
\`\`\`kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
\`\`\`

## Estimated Hours: 2
## Dependencies: None
## Requirements: Foundation for all REQ-*" \
"priority:p0,phase:0-foundation,type:chore,category:devops" \
"Phase 0: Foundation"

create_issue "P0-002: Configure JavaFX 21 with jlink/jpackage" \
"## Description
Add JavaFX 21 dependencies and configure jlink/jpackage for native packaging.

## Acceptance Criteria
- [ ] JavaFX plugin added: \`org.openjfx.javafxplugin\`
- [ ] jlink plugin added: \`org.beryx.jlink\`
- [ ] Modules configured: \`javafx.controls\`, \`javafx.fxml\`, \`javafx.web\`
- [ ] \`./gradlew run\` shows blank JavaFX window
- [ ] \`./gradlew jpackage\` produces installer (on current platform)

## Technical Notes
\`\`\`kotlin
javafx {
    version = \"21\"
    modules = listOf(\"javafx.controls\", \"javafx.fxml\", \"javafx.web\")
}
\`\`\`

## Estimated Hours: 4
## Dependencies: P0-001
## Requirements: REQ-010" \
"priority:p0,phase:0-foundation,type:feature,category:devops,req:010" \
"Phase 0: Foundation"

create_issue "P0-003: Create package structure per TDD" \
"## Description
Create the Clean Architecture package structure as defined in TDD_v3.md.

## Acceptance Criteria
- [ ] \`com.dota2assistant.config/\` - Spring configuration
- [ ] \`com.dota2assistant.domain.draft/\` - Draft engine
- [ ] \`com.dota2assistant.domain.recommendation/\` - Recommendation logic
- [ ] \`com.dota2assistant.domain.analysis/\` - Team analysis
- [ ] \`com.dota2assistant.domain.model/\` - Domain models
- [ ] \`com.dota2assistant.application/\` - Services
- [ ] \`com.dota2assistant.infrastructure.persistence/\` - SQLite repos
- [ ] \`com.dota2assistant.infrastructure.api/\` - API clients
- [ ] \`com.dota2assistant.ui.controller/\` - JavaFX controllers
- [ ] \`com.dota2assistant.ui.component/\` - Reusable UI components
- [ ] Placeholder classes in each package

## Package Rules
| Package | Import Rules | Max Lines |
|---------|--------------|-----------|
| domain.* | NO framework imports | 200 |
| application.* | domain.*, Spring only | 200 |
| infrastructure.* | domain.*, Spring, JDBC, Jackson | 200 |
| ui.* | domain.*, application.*, JavaFX | 200 |

## Estimated Hours: 2
## Dependencies: P0-001
## Requirements: Foundation" \
"priority:p0,phase:0-foundation,type:chore,category:domain" \
"Phase 0: Foundation"

create_issue "P0-004: Configure Spring Boot 3.2 (no web)" \
"## Description
Set up Spring Boot 3.2 with minimal configuration for a desktop app (no web server).

## Acceptance Criteria
- [ ] Spring Boot 3.2 dependency added (exclude web starter)
- [ ] \`AppConfig.java\` with component scanning
- [ ] \`application.properties\` with defaults
- [ ] Application starts with Spring context initialized
- [ ] No web server (no embedded Tomcat)

## Technical Notes
\`\`\`kotlin
implementation(\"org.springframework.boot:spring-boot-starter:3.2.0\") {
    exclude(group = \"org.springframework.boot\", module = \"spring-boot-starter-logging\")
}
\`\`\`

## Estimated Hours: 4
## Dependencies: P0-002
## Requirements: Foundation" \
"priority:p0,phase:0-foundation,type:feature,category:infra" \
"Phase 0: Foundation"

create_issue "P0-005: Set up SQLite with migrations" \
"## Description
Configure SQLite database with connection and migration support.

## Acceptance Criteria
- [ ] \`sqlite-jdbc\` dependency added
- [ ] \`DatabaseConfig.java\` creates DataSource bean
- [ ] \`DatabaseMigrator.java\` runs SQL files on startup
- [ ] \`V001__initial_schema.sql\` creates hero/ability tables
- [ ] Database file created at \`~/.dota2assistant/data.db\`
- [ ] Migrations run automatically on startup

## Schema (V001)
\`\`\`sql
CREATE TABLE heroes (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    localized_name TEXT NOT NULL,
    primary_attr TEXT NOT NULL,
    attack_type TEXT NOT NULL,
    roles TEXT NOT NULL,  -- JSON array
    positions TEXT NOT NULL,  -- JSON object
    attributes TEXT NOT NULL,  -- JSON object
    image_url TEXT,
    icon_url TEXT
);

CREATE TABLE abilities (
    id INTEGER PRIMARY KEY,
    hero_id INTEGER NOT NULL REFERENCES heroes(id),
    name TEXT NOT NULL,
    description TEXT,
    ability_type TEXT,
    damage_type TEXT
);

CREATE TABLE hero_synergies (
    hero1_id INTEGER NOT NULL REFERENCES heroes(id),
    hero2_id INTEGER NOT NULL REFERENCES heroes(id),
    games INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    synergy_score REAL NOT NULL,
    PRIMARY KEY (hero1_id, hero2_id)
);

CREATE TABLE hero_counters (
    hero_id INTEGER NOT NULL REFERENCES heroes(id),
    counter_id INTEGER NOT NULL REFERENCES heroes(id),
    games INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    counter_score REAL NOT NULL,
    PRIMARY KEY (hero_id, counter_id)
);
\`\`\`

## Estimated Hours: 4
## Dependencies: P0-003
## Requirements: REQ-003" \
"priority:p0,phase:0-foundation,type:feature,category:infra,req:003" \
"Phase 0: Foundation"

create_issue "P0-006: Define domain model records" \
"## Description
Implement immutable domain models as Java records per TDD_v3.md.

## Acceptance Criteria
- [ ] \`Hero.java\` record with all fields
- [ ] \`Ability.java\` record
- [ ] \`HeroAttributes.java\` record
- [ ] \`DraftState.java\` record (immutable draft state)
- [ ] \`DraftAction.java\` record (pick/ban action)
- [ ] \`Recommendation.java\` record
- [ ] \`ScoreComponent.java\` record
- [ ] Enums: \`Team\`, \`DraftPhase\`, \`DraftMode\`, \`Attribute\`, \`AttackType\`
- [ ] All records are immutable (final fields only)
- [ ] Unit tests for record equality

## Example
\`\`\`java
public record Hero(
    int id,
    String name,
    String localizedName,
    Attribute primaryAttribute,
    AttackType attackType,
    List<String> roles,
    Map<Integer, Double> positions,
    HeroAttributes attributes,
    String imageUrl,
    String iconUrl,
    List<Ability> abilities
) {}
\`\`\`

## Estimated Hours: 4
## Dependencies: P0-003
## Requirements: REQ-003, REQ-001" \
"priority:p0,phase:0-foundation,type:feature,category:domain,req:001,req:003" \
"Phase 0: Foundation"

create_issue "P0-007: Set up JUnit 5 + Mockito + AssertJ" \
"## Description
Configure testing framework with JUnit 5, Mockito, and AssertJ.

## Acceptance Criteria
- [ ] JUnit 5 dependency in \`build.gradle.kts\`
- [ ] Mockito + mockito-junit-jupiter
- [ ] AssertJ for fluent assertions
- [ ] \`src/test/java/\` structure mirrors main
- [ ] Sample test class that passes
- [ ] \`./gradlew test\` runs successfully
- [ ] Test report generated in \`build/reports/tests/\`

## Estimated Hours: 2
## Dependencies: P0-001
## Requirements: NFR-U (Test coverage)" \
"priority:p0,phase:0-foundation,type:chore,category:testing" \
"Phase 0: Foundation"

create_issue "P0-008: Configure structured logging (SLF4J + Logback)" \
"## Description
Set up structured logging with SLF4J and Logback.

## Acceptance Criteria
- [ ] Logback dependency added
- [ ] \`logback.xml\` configuration file
- [ ] Log format: \`[timestamp] [level] [class] message\`
- [ ] Log levels configurable via properties
- [ ] Logs written to file and console
- [ ] No sensitive data in logs

## Log Format
\`\`\`
2025-01-15 10:30:45.123 [INFO] [DraftService] Draft started: mode=CAPTAINS_MODE
\`\`\`

## Estimated Hours: 2
## Dependencies: P0-001
## Requirements: Observability" \
"priority:p0,phase:0-foundation,type:chore,category:infra" \
"Phase 0: Foundation"

create_issue "P0-009: Create base error classes" \
"## Description
Define error taxonomy with base error class and specific error types.

## Acceptance Criteria
- [ ] \`AppError.java\` - base exception with code, operational flag, context
- [ ] \`DraftValidationException.java\` - domain validation errors
- [ ] \`HeroNotFoundException.java\` - hero not found
- [ ] \`InvalidDraftPhaseException.java\` - wrong phase for action
- [ ] \`RepositoryException.java\` - database errors
- [ ] \`ApiException.java\` - external API errors
- [ ] \`AuthenticationException.java\` - auth failures
- [ ] All errors extend AppError
- [ ] Context maps for debugging

## Example
\`\`\`java
public class InvalidDraftPhaseException extends AppError {
    public InvalidDraftPhaseException(DraftPhase current, String attemptedAction) {
        super(\"INVALID_DRAFT_PHASE\", 
              \"Cannot \" + attemptedAction + \" during \" + current, true,
              Map.of(\"currentPhase\", current, \"attemptedAction\", attemptedAction), null);
    }
}
\`\`\`

## Estimated Hours: 2
## Dependencies: P0-003
## Requirements: Error handling strategy" \
"priority:p0,phase:0-foundation,type:feature,category:domain" \
"Phase 0: Foundation"

create_issue "P0-010: Import hero data from v1" \
"## Description
Migrate existing hero JSON data from v1 into new SQLite schema.

## Acceptance Criteria
- [ ] Data migration script (Gradle task or standalone)
- [ ] All 124+ heroes imported with attributes
- [ ] All abilities imported per hero
- [ ] Role and position data imported
- [ ] Images copied to \`resources/images/heroes/\`
- [ ] Verification query: \`SELECT COUNT(*) FROM heroes\` returns >= 124
- [ ] Each hero has at least 4 abilities

## Data Sources
- \`dota2_draft_assistant/src/main/resources/data/heroes.json\`
- \`dota2_draft_assistant/src/main/resources/images/heroes/\`

## Estimated Hours: 4
## Dependencies: P0-005
## Requirements: REQ-003" \
"priority:p0,phase:0-foundation,type:chore,category:domain,req:003" \
"Phase 0: Foundation"

# =============================================================================
# PHASE 1: CORE DOMAIN
# =============================================================================

echo ""
echo "Phase 1: Core Domain..."
echo "----------------------------------------"

create_issue "P1-001: Implement DraftEngine interface" \
"## Description
Define the draft engine interface that both Captain's Mode and All Pick will implement.

## Acceptance Criteria
- [ ] \`DraftEngine.java\` interface created
- [ ] Methods defined:
  - \`initDraft(DraftMode, boolean timerEnabled)\`
  - \`pickHero(DraftState, Hero) → DraftState\`
  - \`banHero(DraftState, Hero) → DraftState\`
  - \`undo(DraftState) → DraftState\`
  - \`isComplete(DraftState) → boolean\`
  - \`getCurrentTeam(DraftState) → Team\`
  - \`getCurrentPhase(DraftState) → DraftPhase\`
- [ ] Javadoc on all methods with @throws

## Estimated Hours: 2
## Dependencies: P0-006
## Requirements: REQ-001, REQ-002" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:001,req:002" \
"Phase 1: Core Domain"

create_issue "P1-002: Implement CaptainsModeDraft" \
"## Description
Implement the full Captain's Mode draft sequence per TDD_v3.md.

## Acceptance Criteria
- [ ] \`CaptainsModeDraft.java\` implements \`DraftEngine\`
- [ ] 24-action sequence correct:
  - Ban Phase 1: ABBABBA (7 bans)
  - Pick Phase 1: AB (2 picks)
  - Ban Phase 2: AAB (3 bans)
  - Pick Phase 2: BAABBA (6 picks)
  - Ban Phase 3: ABBA (4 bans)
  - Pick Phase 3: AB (2 picks)
- [ ] State transitions return new immutable \`DraftState\`
- [ ] Validation: can't pick during ban phase
- [ ] Validation: can't ban during pick phase
- [ ] Validation: can't select unavailable hero
- [ ] 100% unit test coverage

## Test Cases
- Full 24-action sequence completes correctly
- Ban during pick phase throws \`InvalidDraftPhaseException\`
- Pick during ban phase throws \`InvalidDraftPhaseException\`
- Selecting banned hero throws \`IllegalArgumentException\`
- Selecting picked hero throws \`IllegalArgumentException\`

## Estimated Hours: 8
## Dependencies: P1-001
## Requirements: REQ-001" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:001" \
"Phase 1: Core Domain"

create_issue "P1-003: Implement AllPickDraft" \
"## Description
Implement All Pick draft mode (simpler than Captain's Mode).

## Acceptance Criteria
- [ ] \`AllPickDraft.java\` implements \`DraftEngine\`
- [ ] No enforced turn order (both teams can pick anytime)
- [ ] No bans in standard All Pick
- [ ] Draft complete when both teams have 5 heroes
- [ ] Validation: can't pick already-picked hero
- [ ] Unit tests

## Test Cases
- Draft completes with 5 picks per team
- Picking same hero twice throws exception
- Both teams can pick in any order

## Estimated Hours: 4
## Dependencies: P1-001
## Requirements: REQ-002" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:002" \
"Phase 1: Core Domain"

create_issue "P1-004: Implement DraftState immutability" \
"## Description
Ensure DraftState is fully immutable with proper copy-on-write semantics.

## Acceptance Criteria
- [ ] \`DraftState\` is a record with all final fields
- [ ] \`withPick(Team, Hero)\` returns new instance
- [ ] \`withBan(Team, Hero)\` returns new instance
- [ ] \`withPhase(DraftPhase)\` returns new instance
- [ ] \`withTurnIndex(int)\` returns new instance
- [ ] All List fields use \`List.copyOf()\`
- [ ] Original state unchanged after modifications
- [ ] Unit tests verify immutability

## Example
\`\`\`java
public DraftState withPick(Team team, Hero hero) {
    var newPicks = team == Team.RADIANT 
        ? append(radiantPicks, hero) 
        : append(direPicks, hero);
    var newAvailable = remove(availableHeroes, hero);
    return new DraftState(/* new values */);
}
\`\`\`

## Estimated Hours: 4
## Dependencies: P0-006
## Requirements: REQ-007" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:007" \
"Phase 1: Core Domain"

create_issue "P1-005: Implement undo/redo via history" \
"## Description
Add undo/redo capability to draft engine using immutable state history.

## Acceptance Criteria
- [ ] \`DraftState\` includes \`List<DraftAction> history\`
- [ ] \`undo()\` returns previous state from history
- [ ] Redo implemented via action replay
- [ ] Undo at start throws \`IllegalStateException\`
- [ ] New action clears redo stack
- [ ] Unit tests for all edge cases

## Test Cases
- Undo after 3 picks restores to 2 picks
- Undo then new pick clears redo
- Undo at draft start throws exception

## Estimated Hours: 4
## Dependencies: P1-002
## Requirements: REQ-007" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:007" \
"Phase 1: Core Domain"

create_issue "P1-006: Implement HeroRepository interface" \
"## Description
Define the hero repository interface in the domain layer.

## Acceptance Criteria
- [ ] \`HeroRepository.java\` in \`domain.repository\`
- [ ] Methods:
  - \`List<Hero> findAll()\`
  - \`Optional<Hero> findById(int id)\`
  - \`Optional<Hero> findByName(String name)\`
  - \`List<Hero> findWithAbilities(int id)\`
- [ ] No framework dependencies (pure interface)
- [ ] Javadoc on all methods

## Estimated Hours: 2
## Dependencies: P0-006
## Requirements: REQ-003" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:003" \
"Phase 1: Core Domain"

create_issue "P1-007: Implement SqliteHeroRepository" \
"## Description
SQLite implementation of HeroRepository.

## Acceptance Criteria
- [ ] \`SqliteHeroRepository.java\` in \`infrastructure.persistence\`
- [ ] Implements \`HeroRepository\`
- [ ] Uses prepared statements (SQL injection prevention)
- [ ] Caches hero list on first load (heroes don't change at runtime)
- [ ] Parses JSON fields (roles, positions, attributes) via Jackson
- [ ] Integration test with real SQLite

## Test Cases
- \`findAll()\` returns 124+ heroes
- \`findById()\` returns correct hero
- \`findByName()\` is case-insensitive

## Estimated Hours: 4
## Dependencies: P1-006, P0-005
## Requirements: REQ-003" \
"priority:p0,phase:1-domain,type:feature,category:infra,req:003" \
"Phase 1: Core Domain"

create_issue "P1-008: Implement DraftService" \
"## Description
Application layer service that coordinates draft operations.

## Acceptance Criteria
- [ ] \`DraftService.java\` with \`@Service\` annotation
- [ ] Injects \`HeroRepository\`
- [ ] Holds current \`DraftState\` (mutable at service level)
- [ ] Methods: \`initDraft()\`, \`pickHero(id)\`, \`banHero(id)\`, \`undo()\`, \`getCurrentState()\`
- [ ] Translates hero IDs to Hero objects
- [ ] Delegates to appropriate DraftEngine

## Estimated Hours: 4
## Dependencies: P1-002, P1-003, P1-007
## Requirements: REQ-001, REQ-002" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:001,req:002" \
"Phase 1: Core Domain"

create_issue "P1-009: Implement RecommendationEngine" \
"## Description
Core scoring algorithm for hero recommendations.

## Acceptance Criteria
- [ ] \`RecommendationEngine.java\` in \`domain.recommendation\`
- [ ] Constructor injects scorers: synergy, counter, role, meta
- [ ] \`getRecommendations(DraftState, Team, count)\` returns top N heroes
- [ ] Weighted combination: 25% synergy + 25% counter + 30% role + 20% meta
- [ ] Returns \`List<Recommendation>\` sorted by score descending
- [ ] Pure function - no side effects
- [ ] <100ms for 5 recommendations (benchmark test)

## Estimated Hours: 8
## Dependencies: P0-006
## Requirements: REQ-005" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:005" \
"Phase 1: Core Domain"

create_issue "P1-010: Implement SynergyScorer" \
"## Description
Score heroes based on synergy with allied heroes.

## Acceptance Criteria
- [ ] \`SynergyScorer.java\` in \`domain.recommendation\`
- [ ] \`score(Hero, List<Hero> allies)\` returns ScoreComponent
- [ ] Uses \`hero_synergies\` data
- [ ] Returns neutral (0.5) if no synergy data
- [ ] Unit tests with mock data

## Formula
\`\`\`
synergyScore = AVG(synergyMatrix[hero][ally] for ally in allies)
\`\`\`

## Estimated Hours: 4
## Dependencies: P1-009
## Requirements: REQ-005" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:005" \
"Phase 1: Core Domain"

create_issue "P1-011: Implement CounterScorer" \
"## Description
Score heroes based on effectiveness against enemy heroes.

## Acceptance Criteria
- [ ] \`CounterScorer.java\` in \`domain.recommendation\`
- [ ] \`score(Hero, List<Hero> enemies)\` returns ScoreComponent
- [ ] Uses \`hero_counters\` data
- [ ] Returns neutral (0.5) if no counter data
- [ ] Unit tests with mock data

## Formula
\`\`\`
counterScore = AVG(counterMatrix[hero][enemy] for enemy in enemies)
\`\`\`

## Estimated Hours: 4
## Dependencies: P1-009
## Requirements: REQ-005" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:005" \
"Phase 1: Core Domain"

create_issue "P1-012: Implement RoleScorer" \
"## Description
Score heroes based on team role gaps.

## Acceptance Criteria
- [ ] \`RoleScorer.java\` in \`domain.recommendation\`
- [ ] \`score(Hero, List<Hero> allies, DraftPhase)\` returns ScoreComponent
- [ ] Identifies missing roles (e.g., no hard carry)
- [ ] Boosts heroes that fill gaps
- [ ] Considers draft phase (early = flexible, late = specific)
- [ ] Unit tests

## Estimated Hours: 4
## Dependencies: P1-009
## Requirements: REQ-005" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:005" \
"Phase 1: Core Domain"

create_issue "P1-013: Import synergy/counter data" \
"## Description
Import synergy and counter matrices into SQLite.

## Acceptance Criteria
- [ ] \`hero_synergies\` table populated
- [ ] \`hero_counters\` table populated
- [ ] Data for all hero pairs (124 × 123 = 15,252 pairs per matrix)
- [ ] Synergy score: win rate when on same team
- [ ] Counter score: win rate when facing each other
- [ ] Migration script or data import task

## Estimated Hours: 4
## Dependencies: P0-005
## Requirements: REQ-005" \
"priority:p0,phase:1-domain,type:chore,category:domain,req:005" \
"Phase 1: Core Domain"

create_issue "P1-014: Implement WinProbabilityCalculator" \
"## Description
Calculate win probability based on draft state.

## Acceptance Criteria
- [ ] \`WinProbabilityCalculator.java\` in \`domain.analysis\`
- [ ] \`calculate(DraftState, Team)\` returns 0-100%
- [ ] Uses aggregated synergy/counter scores
- [ ] Empty teams = 50%
- [ ] Updates in <200ms
- [ ] Unit tests

## Estimated Hours: 4
## Dependencies: P1-010, P1-011
## Requirements: REQ-006" \
"priority:p0,phase:1-domain,type:feature,category:domain,req:006" \
"Phase 1: Core Domain"

create_issue "P1-015: Domain layer unit tests - 80%+ coverage" \
"## Description
Comprehensive unit tests for all domain classes.

## Acceptance Criteria
- [ ] CaptainsModeDraftTest - full sequence, edge cases
- [ ] AllPickDraftTest - basic flow, validation
- [ ] DraftStateTest - immutability verification
- [ ] RecommendationEngineTest - scoring accuracy
- [ ] SynergyScorerTest - matrix lookups
- [ ] CounterScorerTest - matrix lookups
- [ ] RoleScorerTest - gap detection
- [ ] WinProbabilityCalculatorTest - bounds, edge cases
- [ ] 80%+ coverage on domain package
- [ ] No mocking of domain classes (test with real objects)

## Estimated Hours: 8
## Dependencies: All P1-* tasks
## Requirements: Quality gate" \
"priority:p0,phase:1-domain,type:chore,category:testing" \
"Phase 1: Core Domain"

# =============================================================================
# PHASE 2: INFRASTRUCTURE
# =============================================================================

echo ""
echo "Phase 2: Infrastructure..."
echo "----------------------------------------"

create_issue "P2-001: Implement GroqClient" \
"## Description
HTTP client for Groq API chat completions.

## Acceptance Criteria
- [ ] \`GroqClient.java\` in \`infrastructure.api\`
- [ ] Uses Java HttpClient (built-in)
- [ ] \`generateExplanation(prompt)\` returns \`CompletableFuture<String>\`
- [ ] Auth via Bearer token from config
- [ ] 10s timeout
- [ ] Handles: 200 OK, 401 auth error, 429 rate limit, 5xx server error
- [ ] Unit tests with mocked responses

## Estimated Hours: 6
## Dependencies: P0-008
## Requirements: REQ-011" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:011" \
"Phase 2: Infrastructure"

create_issue "P2-002: Implement LLM prompt builder" \
"## Description
Build structured prompts for recommendation explanations.

## Acceptance Criteria
- [ ] \`PromptBuilder.java\` utility class
- [ ] Includes: hero name, abilities summary, draft state
- [ ] Includes: ally heroes with roles, enemy heroes
- [ ] Includes: draft phase context (early/mid/late)
- [ ] Output limited to ~500 tokens
- [ ] Template strings for consistency

## Estimated Hours: 4
## Dependencies: P2-001
## Requirements: REQ-011" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:011" \
"Phase 2: Infrastructure"

create_issue "P2-003: Implement LLM fallback" \
"## Description
Local explanation generation when Groq API fails.

## Acceptance Criteria
- [ ] \`generateLocalExplanation(Recommendation)\` method
- [ ] Uses ScoreComponent reasons to build text
- [ ] Falls back automatically on API timeout/error
- [ ] Logs warning when falling back
- [ ] Unit tests for fallback path

## Example Output
\`\`\`
Phoenix is recommended because:
• High synergy with Magnus (0.62)
• Strong counter to Luna (0.58)
• Team lacks magical damage dealer
\`\`\`

## Estimated Hours: 4
## Dependencies: P2-001
## Requirements: REQ-011" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:011" \
"Phase 2: Infrastructure"

create_issue "P2-004: Implement OpenDotaClient" \
"## Description
HTTP client for OpenDota API match data.

## Acceptance Criteria
- [ ] \`OpenDotaClient.java\` in \`infrastructure.api\`
- [ ] Methods:
  - \`fetchMatches(accountId, limit)\`
  - \`fetchHeroStats()\`
  - \`fetchPlayerProfile(accountId)\`
- [ ] Rate limit handling (60/min free tier)
- [ ] Exponential backoff on 429
- [ ] Response validation
- [ ] Unit tests with mocked responses

## Estimated Hours: 6
## Dependencies: P0-008
## Requirements: REQ-013" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:013" \
"Phase 2: Infrastructure"

create_issue "P2-005: Implement OpenDota caching" \
"## Description
Cache OpenDota responses to reduce API calls.

## Acceptance Criteria
- [ ] Match history cached in SQLite
- [ ] Incremental sync (only fetch new matches)
- [ ] Cache invalidation after 1 hour
- [ ] \`lastSyncTime\` tracked per player
- [ ] Reduces API calls by 80%+ on repeated syncs

## Estimated Hours: 4
## Dependencies: P2-004
## Requirements: REQ-013" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:013" \
"Phase 2: Infrastructure"

create_issue "P2-006: Implement SteamAuthClient" \
"## Description
Steam OpenID authentication flow.

## Acceptance Criteria
- [ ] \`SteamAuthClient.java\` in \`infrastructure.api\`
- [ ] Opens system browser for Steam login
- [ ] Local callback server to receive auth response
- [ ] Extracts Steam ID from OpenID response
- [ ] Handles auth cancellation gracefully
- [ ] Unit tests with mocked auth flow

## Flow
1. User clicks \"Login with Steam\"
2. Browser opens to Steam login page
3. User authenticates
4. Steam redirects to local callback URL
5. App captures Steam ID and closes browser

## Estimated Hours: 8
## Dependencies: P0-008
## Requirements: REQ-012" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:012" \
"Phase 2: Infrastructure"

create_issue "P2-007: Implement session persistence" \
"## Description
Remember login across app restarts.

## Acceptance Criteria
- [ ] Session token stored in OS secure storage
- [ ] On app start, check for valid session
- [ ] Auto-login if session valid
- [ ] Session expiry handling (30 days)
- [ ] Logout clears session
- [ ] Unit tests

## Estimated Hours: 4
## Dependencies: P2-006
## Requirements: REQ-012" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:012" \
"Phase 2: Infrastructure"

create_issue "P2-008: Implement SecureCredentialStore" \
"## Description
Store API keys in OS secure storage.

## Acceptance Criteria
- [ ] \`SecureCredentialStore.java\` in \`infrastructure.storage\`
- [ ] macOS: Keychain Access
- [ ] Windows: Credential Manager
- [ ] Linux: Secret Service API
- [ ] Methods: \`store(key, value)\`, \`retrieve(key)\`, \`delete(key)\`
- [ ] No credentials in plaintext files

## Estimated Hours: 4
## Dependencies: None
## Requirements: REQ-016, NFR-S001" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:016" \
"Phase 2: Infrastructure"

create_issue "P2-009: Implement file fallback for credentials" \
"## Description
Fallback credential storage when OS secure storage unavailable.

## Acceptance Criteria
- [ ] \`FileCredentialStore.java\` as fallback
- [ ] Encrypted file in user home directory
- [ ] Warns user when using fallback
- [ ] Triggered when SecureCredentialStore fails
- [ ] Unit tests

## Estimated Hours: 2
## Dependencies: P2-008
## Requirements: REQ-016" \
"priority:p1,phase:2-infrastructure,type:feature,category:infra,req:016" \
"Phase 2: Infrastructure"

create_issue "P2-010: Implement RateLimiter utility" \
"## Description
Token bucket rate limiter for API clients.

## Acceptance Criteria
- [ ] \`RateLimiter.java\` in \`infrastructure.api\`
- [ ] Constructor: \`RateLimiter(maxRequests, perDuration)\`
- [ ] \`acquire()\` blocks if rate exceeded
- [ ] \`tryAcquire()\` returns immediately
- [ ] Thread-safe
- [ ] Unit tests

## Estimated Hours: 4
## Dependencies: None
## Requirements: R-02 mitigation" \
"priority:p0,phase:2-infrastructure,type:feature,category:infra" \
"Phase 2: Infrastructure"

create_issue "P2-011: Implement retry with backoff" \
"## Description
Exponential backoff retry utility for API calls.

## Acceptance Criteria
- [ ] \`retryWithBackoff(operation, maxRetries, initialDelay, maxDelay)\`
- [ ] Exponential backoff: delay doubles each retry
- [ ] Max delay cap
- [ ] Only retries operational errors (rate limits)
- [ ] Throws after max retries exhausted
- [ ] Unit tests

## Estimated Hours: 4
## Dependencies: None
## Requirements: Error handling strategy" \
"priority:p0,phase:2-infrastructure,type:feature,category:infra" \
"Phase 2: Infrastructure"

create_issue "P2-012: Infrastructure integration tests" \
"## Description
Integration tests for all infrastructure components.

## Acceptance Criteria
- [ ] \`SqliteHeroRepositoryTest\` - real SQLite
- [ ] \`GroqClientTest\` - mocked HTTP responses
- [ ] \`OpenDotaClientTest\` - mocked HTTP responses
- [ ] \`SteamAuthClientTest\` - mocked auth flow
- [ ] \`SecureCredentialStoreTest\` - platform-specific
- [ ] All tests run in CI

## Estimated Hours: 8
## Dependencies: All P2-* tasks
## Requirements: Quality gate" \
"priority:p0,phase:2-infrastructure,type:chore,category:testing" \
"Phase 2: Infrastructure"

# =============================================================================
# PHASE 3: USER INTERFACE (abbreviated - create manually for remaining)
# =============================================================================

echo ""
echo "Phase 3: User Interface (abbreviated - key issues)..."
echo "----------------------------------------"

create_issue "P3-001: Create MainController (<100 lines)" \
"## Description
Main controller that coordinates sub-controllers.

## Acceptance Criteria
- [ ] \`MainController.java\` in \`ui.controller\`
- [ ] Less than 100 lines of code
- [ ] Injects sub-controllers via Spring
- [ ] Sets up tab navigation
- [ ] Delegates all logic to sub-controllers

## Estimated Hours: 4
## Dependencies: P0-004
## Requirements: Architecture constraint" \
"priority:p0,phase:3-ui,type:feature,category:ui" \
"Phase 3: User Interface"

create_issue "P3-003: Create DraftController" \
"## Description
Controller for the draft interface.

## Acceptance Criteria
- [ ] \`DraftController.java\` (<200 lines)
- [ ] Wires DraftService to UI
- [ ] Handles hero selection events
- [ ] Updates team panels on state change
- [ ] Triggers recommendation refresh
- [ ] Implements undo button

## Estimated Hours: 8
## Dependencies: P1-008
## Requirements: REQ-001, REQ-002, REQ-007" \
"priority:p0,phase:3-ui,type:feature,category:ui,req:001,req:002,req:007" \
"Phase 3: User Interface"

create_issue "P3-004: Create HeroGridController" \
"## Description
Controller for the hero selection grid.

## Acceptance Criteria
- [ ] \`HeroGridController.java\` (<150 lines)
- [ ] Displays 124+ heroes in grid
- [ ] Search filters in <50ms
- [ ] Attribute/role filter dropdowns
- [ ] Click handler for hero selection
- [ ] Disabled state for picked/banned heroes
- [ ] Highlighted state for recommended heroes

## Estimated Hours: 6
## Dependencies: P1-007
## Requirements: REQ-004" \
"priority:p0,phase:3-ui,type:feature,category:ui,req:004" \
"Phase 3: User Interface"

create_issue "P3-005: Create HeroCard component" \
"## Description
Reusable hero card UI component.

## Acceptance Criteria
- [ ] \`HeroCard.java\` extends VBox
- [ ] Shows hero portrait (64x64 or 128x128)
- [ ] Shows localized name
- [ ] Hover effect (scale or border)
- [ ] Disabled state (grayed out)
- [ ] Highlighted state (golden border)
- [ ] Click handler property

## Estimated Hours: 4
## Dependencies: None
## Requirements: REQ-004" \
"priority:p0,phase:3-ui,type:feature,category:ui,req:004" \
"Phase 3: User Interface"

create_issue "P3-015: Create dark theme CSS" \
"## Description
Dark color theme for the application.

## Acceptance Criteria
- [ ] \`dark-theme.css\` in resources/css
- [ ] Background: #0f172a
- [ ] Surface: #1e293b
- [ ] Radiant green: #92a525
- [ ] Dire red: #c23c2a
- [ ] Accent blue: #0ea5e9
- [ ] WCAG 2.1 AA compliant contrast
- [ ] All components styled

## Estimated Hours: 4
## Dependencies: None
## Requirements: NFR-U004" \
"priority:p0,phase:3-ui,type:feature,category:ui" \
"Phase 3: User Interface"

# =============================================================================
# PHASE 4: PACKAGING
# =============================================================================

echo ""
echo "Phase 4: Packaging..."
echo "----------------------------------------"

create_issue "P4-001: Configure jpackage for macOS" \
"## Description
Create macOS .dmg installer with jpackage.

## Acceptance Criteria
- [ ] \`./gradlew jpackage\` produces .dmg on macOS
- [ ] App installs to /Applications
- [ ] App icon visible in Finder
- [ ] Launches correctly on clean Mac (no pre-installed Java)
- [ ] Tested on macOS 12+ (Monterey and later)

## Estimated Hours: 8
## Dependencies: All Phase 3
## Requirements: REQ-010" \
"priority:p0,phase:4-packaging,type:feature,category:devops,req:010" \
"Phase 4: Packaging"

create_issue "P4-002: Configure jpackage for Windows" \
"## Description
Create Windows .msi installer with jpackage.

## Acceptance Criteria
- [ ] \`./gradlew jpackage\` produces .msi on Windows
- [ ] Standard installation wizard
- [ ] Start menu shortcut created
- [ ] Launches correctly on clean Windows 10/11
- [ ] Works without pre-installed Java

## Estimated Hours: 8
## Dependencies: All Phase 3
## Requirements: REQ-010" \
"priority:p0,phase:4-packaging,type:feature,category:devops,req:010" \
"Phase 4: Packaging"

create_issue "P4-003: Configure jpackage for Linux" \
"## Description
Create Linux .deb package with jpackage.

## Acceptance Criteria
- [ ] \`./gradlew jpackage\` produces .deb on Linux
- [ ] Installs via \`sudo apt install ./package.deb\`
- [ ] Desktop entry created
- [ ] Launches correctly on Ubuntu 20.04+
- [ ] Works without pre-installed Java

## Estimated Hours: 4
## Dependencies: All Phase 3
## Requirements: REQ-010" \
"priority:p0,phase:4-packaging,type:feature,category:devops,req:010" \
"Phase 4: Packaging"

create_issue "P4-007: Configure GitHub Actions CI" \
"## Description
Automated build and test pipeline.

## Acceptance Criteria
- [ ] \`.github/workflows/build.yml\` created
- [ ] Runs \`./gradlew test\` on push
- [ ] Runs on ubuntu-latest, windows-latest, macos-latest
- [ ] Fails if tests fail
- [ ] Uploads test reports as artifacts

## Estimated Hours: 4
## Dependencies: None
## Requirements: DevOps" \
"priority:p0,phase:4-packaging,type:chore,category:devops" \
"Phase 4: Packaging"

create_issue "P4-008: Configure multi-platform release" \
"## Description
Automated release builds for all platforms.

## Acceptance Criteria
- [ ] \`.github/workflows/release.yml\` created
- [ ] Triggered on tag push (v*)
- [ ] Builds installers for all 3 platforms
- [ ] Uploads to GitHub Release
- [ ] Creates release notes from changelog

## Estimated Hours: 4
## Dependencies: P4-007
## Requirements: REQ-010" \
"priority:p0,phase:4-packaging,type:chore,category:devops,req:010" \
"Phase 4: Packaging"

create_issue "P4-009: Startup time optimization" \
"## Description
Ensure cold startup is under 3 seconds.

## Acceptance Criteria
- [ ] Profile startup with Java Flight Recorder
- [ ] Identify bottlenecks
- [ ] Implement lazy loading for non-critical components
- [ ] Defer hero image loading
- [ ] Measure 95th percentile <3s
- [ ] Document optimizations

## Estimated Hours: 4
## Dependencies: All Phase 3
## Requirements: NFR-P001" \
"priority:p0,phase:4-packaging,type:feature,category:ui" \
"Phase 4: Packaging"

echo ""
echo "========================================"
echo "Issue creation complete!"
echo "========================================"
echo ""
echo "Next steps:"
echo "1. Review issues at: https://github.com/$REPO/issues"
echo "2. Create project board at: https://github.com/$REPO/projects"
echo "3. Add issues to project board columns"
echo "4. Assign issues to team members"
echo ""
echo "To add remaining Phase 3 and Phase 5 issues, run this script again"
echo "or create them manually in the GitHub UI."

