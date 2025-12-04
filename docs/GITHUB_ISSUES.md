# GitHub Issues & Project Board Setup

This document provides instructions and templates for setting up GitHub Issues and a Project Board for the Dota 2 Draft Assistant v3 rebuild.

---

## Quick Setup (Automated)

```bash
# Make the script executable
chmod +x scripts/create-github-issues.sh

# Run the script (requires GitHub CLI)
./scripts/create-github-issues.sh
```

---

## Manual Setup

If you prefer to create issues manually or the script doesn't work, follow these instructions.

### Step 1: Create Labels

Go to **Settings → Labels** and create:

#### Priority Labels
| Label | Color | Description |
|-------|-------|-------------|
| `priority:p0` | #b60205 | Launch blocker - must have |
| `priority:p1` | #d93f0b | Important but not critical |
| `priority:p2` | #fbca04 | Nice to have |

#### Phase Labels
| Label | Color | Description |
|-------|-------|-------------|
| `phase:0-foundation` | #0e8a16 | Phase 0: Project foundation |
| `phase:1-domain` | #1d76db | Phase 1: Core domain logic |
| `phase:2-infrastructure` | #5319e7 | Phase 2: Infrastructure layer |
| `phase:3-ui` | #f9d0c4 | Phase 3: User interface |
| `phase:4-packaging` | #d4c5f9 | Phase 4: Packaging & release |
| `phase:5-personalization` | #c5def5 | Phase 5: Personalization |

#### Category Labels
| Label | Color | Description |
|-------|-------|-------------|
| `category:domain` | #bfd4f2 | Domain layer (pure business logic) |
| `category:ui` | #e99695 | UI layer (JavaFX) |
| `category:infra` | #d4c5f9 | Infrastructure layer |
| `category:testing` | #c2e0c6 | Testing related |
| `category:docs` | #fef2c0 | Documentation |
| `category:devops` | #bfdadc | CI/CD and build |

#### Type Labels
| Label | Color | Description |
|-------|-------|-------------|
| `type:feature` | #a2eeef | New feature |
| `type:bug` | #d73a4a | Bug fix |
| `type:refactor` | #fef2c0 | Code refactoring |
| `type:chore` | #ededed | Maintenance task |

### Step 2: Create Milestones

Go to **Issues → Milestones → New Milestone** and create:

| Milestone | Due Date | Description |
|-----------|----------|-------------|
| Phase 0: Foundation | Jan 17, 2025 | Project setup, no features |
| Phase 1: Core Domain | Feb 7, 2025 | Draft engine, recommendations |
| Phase 2: Infrastructure | Feb 28, 2025 | API clients, repositories |
| Phase 3: User Interface | Mar 28, 2025 | JavaFX controllers, CSS |
| Phase 4: Packaging | Apr 11, 2025 | Native installers, CI/CD |
| Alpha Release | Apr 11, 2025 | REQ-001 to REQ-010 complete |
| Phase 5: Personalization | May 9, 2025 | Steam auth, personalization |
| Beta Release | May 9, 2025 | REQ-011 to REQ-016 complete |
| GA Release v1.0 | Jun 6, 2025 | Bug fixes, polish |

### Step 3: Create Project Board

1. Go to **Projects → New Project**
2. Select "Board" template
3. Name: "Dota 2 Draft Assistant v3 Rebuild"
4. Create columns:

| Column | Purpose |
|--------|---------|
| 📋 Backlog | Issues not yet started |
| 🔄 In Progress | Currently being worked on |
| 👀 In Review | PR submitted, awaiting review |
| ✅ Done | Completed and merged |
| 🚫 Blocked | Waiting on external dependency |

### Step 4: Add Views

Create these filtered views:

1. **Phase 0** - Filter: `label:phase:0-foundation`
2. **Phase 1** - Filter: `label:phase:1-domain`
3. **Phase 2** - Filter: `label:phase:2-infrastructure`
4. **Phase 3** - Filter: `label:phase:3-ui`
5. **Phase 4** - Filter: `label:phase:4-packaging`
6. **Phase 5** - Filter: `label:phase:5-personalization`
7. **P0 Only** - Filter: `label:priority:p0`
8. **My Issues** - Filter: `assignee:@me`

---

## Issue Templates

### Standard Issue Template

```markdown
## Description
[One paragraph description of the task]

## Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

## Technical Notes
[Optional: code snippets, API references, etc.]

## Test Cases
- Case 1: Given X, when Y, then Z
- Case 2: Given A, when B, then C

## Estimated Hours: X
## Dependencies: [Task IDs]
## Requirements: [REQ-XXX]
```

### Bug Report Template

```markdown
## Bug Description
[Clear description of the bug]

## Steps to Reproduce
1. Step 1
2. Step 2
3. Step 3

## Expected Behavior
[What should happen]

## Actual Behavior
[What actually happens]

## Environment
- OS: [e.g., macOS 14.0]
- Java Version: [e.g., 21.0.1]
- App Version: [e.g., 0.1.0-alpha]

## Screenshots
[If applicable]

## Logs
```
[Relevant log output]
```
```

---

## Complete Issue List

### Phase 0: Foundation (10 issues)

| ID | Title | Priority | Est. Hours |
|----|-------|----------|------------|
| P0-001 | Initialize Gradle project with Java 21 | P0 | 2 |
| P0-002 | Configure JavaFX 21 with jlink/jpackage | P0 | 4 |
| P0-003 | Create package structure per TDD | P0 | 2 |
| P0-004 | Configure Spring Boot 3.2 (no web) | P0 | 4 |
| P0-005 | Set up SQLite with migrations | P0 | 4 |
| P0-006 | Define domain model records | P0 | 4 |
| P0-007 | Set up JUnit 5 + Mockito + AssertJ | P0 | 2 |
| P0-008 | Configure structured logging | P0 | 2 |
| P0-009 | Create base error classes | P0 | 2 |
| P0-010 | Import hero data from v1 | P0 | 4 |

**Phase 0 Total: 30 hours**

### Phase 1: Core Domain (15 issues)

| ID | Title | Priority | Est. Hours |
|----|-------|----------|------------|
| P1-001 | Implement DraftEngine interface | P0 | 2 |
| P1-002 | Implement CaptainsModeDraft | P0 | 8 |
| P1-003 | Implement AllPickDraft | P0 | 4 |
| P1-004 | Implement DraftState immutability | P0 | 4 |
| P1-005 | Implement undo/redo via history | P0 | 4 |
| P1-006 | Implement HeroRepository interface | P0 | 2 |
| P1-007 | Implement SqliteHeroRepository | P0 | 4 |
| P1-008 | Implement DraftService | P0 | 4 |
| P1-009 | Implement RecommendationEngine | P0 | 8 |
| P1-010 | Implement SynergyScorer | P0 | 4 |
| P1-011 | Implement CounterScorer | P0 | 4 |
| P1-012 | Implement RoleScorer | P0 | 4 |
| P1-013 | Import synergy/counter data | P0 | 4 |
| P1-014 | Implement WinProbabilityCalculator | P0 | 4 |
| P1-015 | Domain layer unit tests - 80%+ coverage | P0 | 8 |

**Phase 1 Total: 68 hours**

### Phase 2: Infrastructure (12 issues)

| ID | Title | Priority | Est. Hours |
|----|-------|----------|------------|
| P2-001 | Implement GroqClient | P1 | 6 |
| P2-002 | Implement LLM prompt builder | P1 | 4 |
| P2-003 | Implement LLM fallback | P1 | 4 |
| P2-004 | Implement OpenDotaClient | P1 | 6 |
| P2-005 | Implement OpenDota caching | P1 | 4 |
| P2-006 | Implement SteamAuthClient | P1 | 8 |
| P2-007 | Implement session persistence | P1 | 4 |
| P2-008 | Implement SecureCredentialStore | P1 | 4 |
| P2-009 | Implement file fallback for credentials | P1 | 2 |
| P2-010 | Implement RateLimiter utility | P0 | 4 |
| P2-011 | Implement retry with backoff | P0 | 4 |
| P2-012 | Infrastructure integration tests | P0 | 8 |

**Phase 2 Total: 58 hours**

### Phase 3: User Interface (18 issues)

| ID | Title | Priority | Est. Hours |
|----|-------|----------|------------|
| P3-001 | Create MainController (<100 lines) | P0 | 4 |
| P3-002 | Create HomeController | P0 | 4 |
| P3-003 | Create DraftController | P0 | 8 |
| P3-004 | Create HeroGridController | P0 | 6 |
| P3-005 | Create HeroCard component | P0 | 4 |
| P3-006 | Create TeamPanelController | P0 | 4 |
| P3-007 | Create RecommendationController | P0 | 4 |
| P3-008 | Create WinProbabilityBar | P0 | 4 |
| P3-009 | Create PhaseIndicator | P0 | 2 |
| P3-010 | Create TimerDisplay | P1 | 4 |
| P3-011 | Create SettingsController | P1 | 4 |
| P3-012 | Create LoginController | P1 | 4 |
| P3-013 | Implement hero search (<50ms) | P0 | 4 |
| P3-014 | Implement keyboard navigation | P1 | 6 |
| P3-015 | Create dark theme CSS | P0 | 4 |
| P3-016 | Create light theme CSS | P2 | 2 |
| P3-017 | Load hero images asynchronously | P0 | 4 |
| P3-018 | UI responsiveness testing | P0 | 8 |

**Phase 3 Total: 80 hours**

### Phase 4: Packaging (12 issues)

| ID | Title | Priority | Est. Hours |
|----|-------|----------|------------|
| P4-001 | Configure jpackage for macOS | P0 | 8 |
| P4-002 | Configure jpackage for Windows | P0 | 8 |
| P4-003 | Configure jpackage for Linux | P0 | 4 |
| P4-004 | Add application icons | P0 | 2 |
| P4-005 | Test on clean VMs | P0 | 8 |
| P4-006 | Configure macOS code signing | P1 | 4 |
| P4-007 | Configure GitHub Actions CI | P0 | 4 |
| P4-008 | Configure multi-platform release | P0 | 4 |
| P4-009 | Startup time optimization | P0 | 4 |
| P4-010 | Memory usage profiling | P0 | 4 |
| P4-011 | Create release notes template | P0 | 2 |
| P4-012 | Bug bash and fixes | P0 | 16 |

**Phase 4 Total: 68 hours**

### Phase 5: Personalization (9 issues)

| ID | Title | Priority | Est. Hours |
|----|-------|----------|------------|
| P5-001 | Integrate Steam login in UI | P1 | 4 |
| P5-002 | Fetch match history on login | P1 | 4 |
| P5-003 | Calculate hero comfort scores | P1 | 6 |
| P5-004 | Implement PersonalScorer | P1 | 4 |
| P5-005 | Display personal stats in UI | P1 | 4 |
| P5-006 | Implement recommendation weighting slider | P1 | 2 |
| P5-007 | Implement timer mode | P1 | 6 |
| P5-008 | Implement draft history saving | P2 | 4 |
| P5-009 | Implement draft export as PNG | P2 | 4 |

**Phase 5 Total: 38 hours**

---

## Summary

| Phase | Issues | Est. Hours | Deadline |
|-------|--------|------------|----------|
| Phase 0 | 10 | 30 | Jan 17, 2025 |
| Phase 1 | 15 | 68 | Feb 7, 2025 |
| Phase 2 | 12 | 58 | Feb 28, 2025 |
| Phase 3 | 18 | 80 | Mar 28, 2025 |
| Phase 4 | 12 | 68 | Apr 11, 2025 |
| Phase 5 | 9 | 38 | May 9, 2025 |
| **Total** | **76** | **342** | |

**At 40 hours/week, this is approximately 8.5 weeks of full-time work.**

---

## Workflow

### Starting Work on an Issue

1. Assign yourself to the issue
2. Move to "In Progress" column
3. Create a feature branch: `feature/P0-001-gradle-setup`
4. Commit with issue reference: `git commit -m "P0-001: Initialize Gradle project"`

### Completing Work

1. Push branch and create PR
2. Link PR to issue: "Closes #123"
3. Move issue to "In Review"
4. After merge, issue auto-closes and moves to "Done"

### Issue Dependencies

If an issue is blocked:
1. Add comment explaining blocker
2. Move to "Blocked" column
3. Link to blocking issue
4. Move back to "In Progress" when unblocked

---

*This document accompanies the `create-github-issues.sh` script and `IMPLEMENTATION_PLAN_v2.md`.*

