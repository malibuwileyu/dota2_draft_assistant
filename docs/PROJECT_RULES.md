# Dota 2 Draft Assistant - Project Rules & Workflow

**Version:** 2.0  
**Updated:** December 10, 2025  
**Status:** Active Development

---

## Overview

This document defines the development workflow, branching strategy, commit conventions, and CI/CD pipeline for the Dota 2 Draft Assistant project. It covers both the **frontend** (JavaFX desktop app) and **backend** (Spring Boot API on Railway).

---

## 1. Repository Structure

```
dota_assistant/
├── dota2_draft_assistant/     # Frontend - JavaFX Desktop App
│   ├── src/main/java/         # Application code
│   ├── src/test/java/         # Unit & integration tests
│   ├── .github/workflows/     # CI/CD pipelines
│   └── docs/                  # Project documentation
│
└── (linked) dota2_draft_backend/  # Backend - Spring Boot API
    ├── src/main/java/         # API code
    ├── src/test/java/         # Tests
    └── .github/workflows/     # CI/CD pipelines
```

---

## 2. Branching Strategy

### Branch Types

| Branch | Purpose | Naming Convention |
|--------|---------|-------------------|
| `main` | Production-ready code. Deploys automatically. | `main` |
| `develop` | Integration branch for features | `develop` |
| `feature/*` | New features | `feature/P3-006-team-panel` |
| `bugfix/*` | Bug fixes | `bugfix/P3-012-login-crash` |
| `hotfix/*` | Urgent production fixes | `hotfix/security-patch` |
| `release/*` | Release preparation | `release/v1.0.0` |

### Branch Flow

```
feature/P3-006 → develop → release/v1.0.0 → main
                    ↑
                bugfix/
```

### Rules

1. **Never push directly to `main`** - Always use pull requests
2. **Feature branches** must be prefixed with issue ID when applicable
3. **Delete branches** after merging
4. **Squash merge** for feature branches to keep history clean

---

## 3. Commit Message Convention

### Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer with issue references]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no code change |
| `refactor` | Refactoring, no new feature |
| `test` | Adding tests |
| `chore` | Maintenance tasks |
| `perf` | Performance improvements |

### Scope (Optional)

- `domain` - Domain layer
- `ui` - User interface
- `infra` - Infrastructure
- `api` - Backend API
- `auth` - Authentication
- `build` - Build system
- `ci` - CI/CD

### Issue References (IMPORTANT)

**To automatically close issues, use these keywords:**

```
Closes #123
Fixes #123
Resolves #123
```

### Examples

```bash
# Feature with auto-close
feat(ui): implement DraftTower component

Closes #43

# Bug fix
fix(auth): handle WebView focus on macOS

Fixes #89

# Multiple issues
feat(domain): add session analysis

Implements session tracking and exhaustion detection.

Closes #71
Closes #72

# No issue reference (chore)
chore(build): update Gradle to 8.5
```

---

## 4. Pull Request Process

### PR Template

```markdown
## Summary
Brief description of changes

## Related Issues
Closes #XX
Relates to #YY

## Type of Change
- [ ] Bug fix (non-breaking)
- [ ] New feature (non-breaking)
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated if needed
```

### Review Requirements

| Target Branch | Required Reviews | Checks Required |
|---------------|------------------|-----------------|
| `develop` | 1 | CI passes |
| `main` | 1+ | CI passes, all tests pass |
| `release/*` | 2 | All checks pass |

---

## 5. CI/CD Pipeline

### Frontend Pipeline (`.github/workflows/ci.yml`)

```
Push/PR to main/develop
         │
         ▼
    ┌─────────────┐
    │   Build &   │ (ubuntu, windows, macos)
    │    Test     │
    └─────────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌───────┐ ┌─────────┐
│ Code  │ │Security │
│Quality│ │  Scan   │
└───────┘ └─────────┘
    │
    ▼
┌─────────────────┐
│  Auto-Close     │ (main only)
│    Issues       │
└─────────────────┘
```

**Triggers:**
- Push to `main`, `develop`
- Pull requests to `main`, `develop`

**Jobs:**
1. **Build & Test** - Multi-platform (Ubuntu, Windows, macOS)
2. **Code Quality** - Style checks, file size limits, domain layer purity
3. **Security Scan** - Trivy vulnerability scanner
4. **Auto-Close Issues** - Parses commits for `Closes/Fixes/Resolves #XX`

### Backend Pipeline (`.github/workflows/ci.yml`)

```
Push/PR to main/develop
         │
         ▼
    ┌─────────────┐
    │   Build &   │ (with Postgres/Redis)
    │    Test     │
    └─────────────┘
         │
         ▼
    ┌─────────────┐
    │ Code Quality│
    └─────────────┘
         │
    ┌────┴────┐ (main only)
    │         │
    ▼         ▼
┌───────┐ ┌─────────┐
│Auto-  │ │ Deploy  │
│Close  │ │ Railway │
└───────┘ └─────────┘
```

**Triggers:**
- Push to `main`, `develop`
- Pull requests to `main`, `develop`

**Jobs:**
1. **Build & Test** - With Postgres 16 and Redis 7 services
2. **Code Quality** - File size limits, domain checks
3. **Auto-Close Issues** - Parses commits
4. **Deploy** - Railway auto-deploys on main push

### Release Pipeline (`.github/workflows/release.yml`)

**Triggers:**
- Tag push matching `v*` (e.g., `v1.0.0`, `v0.1.0-alpha`)

**Jobs:**
1. Create GitHub Release (draft)
2. Build macOS `.dmg`
3. Build Windows `.msi`
4. Build Linux `.deb`
5. Publish release

---

## 6. Issue Management

### Issue ID Format

```
P<phase>-<number>: <title>

Examples:
P0-001: Initialize Gradle project
P3-006: Create TeamPanelController
P7-015: Implement PositioningAnalyzer
```

### Labels

| Label | Description | Color |
|-------|-------------|-------|
| `priority:p0` | Launch blocker | Red |
| `priority:p1` | Important | Orange |
| `priority:p2` | Nice to have | Yellow |
| `phase:0-foundation` | Phase 0 tasks | Green |
| `phase:1-domain` | Phase 1 tasks | Blue |
| `phase:2-infrastructure` | Phase 2 tasks | Purple |
| `phase:3-ui` | Phase 3 tasks | Pink |
| `phase:4-packaging` | Phase 4 tasks | Lavender |
| `phase:5-personalization` | Phase 5 tasks | Light Blue |
| `phase:6-pipeline` | Phase 6 tasks | Teal |
| `phase:7-analysis` | Phase 7 tasks | Coral |
| `phase:8-insights` | Phase 8 tasks | Mint |
| `category:domain` | Domain layer | Light Green |
| `category:ui` | UI layer | Salmon |
| `category:infra` | Infrastructure | Lavender |
| `category:analysis` | Analysis engine | Mauve |
| `type:feature` | New feature | Cyan |
| `type:bug` | Bug fix | Red |
| `type:chore` | Maintenance | Gray |
| `req:XXX` | Requirement traceability | Light Blue |

### Auto-Closing Issues

Issues are automatically closed when:

1. A commit to `main` contains `Closes #XX`, `Fixes #XX`, or `Resolves #XX`
2. The CI pipeline parses the commit message
3. GitHub API closes the issue with a comment linking to the commit

**Best Practice:** Include issue references in commit messages, not just PR descriptions.

---

## 7. Code Standards

### File Size Limits

| File Type | Max Lines | Note |
|-----------|-----------|------|
| Domain classes | 200 | Pure business logic |
| Controllers/Views | 500 | UI can be verbose |
| Services | 300 | |
| Repositories | 200 | |
| Tests | 500 | Comprehensive coverage OK |

### Architecture Rules

1. **Domain Layer Purity**
   - NO framework imports in `domain/model/`, `domain/draft/`, `domain/recommendation/`
   - Exception: JPA annotations on backend domain models

2. **Dependency Direction**
   ```
   UI → Application → Domain ← Infrastructure
   ```
   - Domain NEVER imports from UI, Application, or Infrastructure
   - Infrastructure implements Domain interfaces

3. **Immutability**
   - Use Java `record` for domain models
   - Use `List.copyOf()` for collections

### Testing Requirements

| Phase | Coverage Target |
|-------|-----------------|
| Domain Layer | 80%+ |
| Infrastructure | 70%+ |
| UI | 50%+ (manual testing OK) |

---

## 8. Release Process

### Version Numbering

```
MAJOR.MINOR.PATCH[-PRERELEASE]

Examples:
1.0.0        - First stable release
1.1.0        - New features
1.1.1        - Bug fixes
1.2.0-alpha  - Alpha release
1.2.0-beta   - Beta release
```

### Release Checklist

```markdown
## Pre-Release
- [ ] All P0 issues for milestone closed
- [ ] All tests pass on all platforms
- [ ] PHASE_COMPLETION_AUDIT.md updated
- [ ] Version bumped in build.gradle.kts
- [ ] CHANGELOG.md updated

## Release
- [ ] Create release branch: `release/vX.Y.Z`
- [ ] Final testing on release branch
- [ ] Merge to main
- [ ] Tag: `git tag vX.Y.Z && git push --tags`
- [ ] Verify GitHub Actions creates release
- [ ] Test installers on each platform

## Post-Release
- [ ] Merge main back to develop
- [ ] Announce release
- [ ] Update project board
```

---

## 9. Environment Variables

### Frontend (Desktop App)

| Variable | Description | Default |
|----------|-------------|---------|
| `GROQ_API_KEY` | Groq LLM API key (optional) | - |
| `STEAM_API_KEY` | Steam Web API key | - |

### Backend (Railway)

| Variable | Description | Required |
|----------|-------------|----------|
| `DATABASE_URL` | PostgreSQL connection | Yes (Railway provides) |
| `REDIS_URL` | Redis connection | Yes (Railway provides) |
| `GROQ_API_KEY` | Groq LLM API key | Yes |
| `STRATZ_API_KEY` | STRATZ API key | Yes |
| `STEAM_API_KEY` | Steam Web API key | Yes |
| `JWT_SECRET` | JWT signing secret | Yes |

---

## 10. Quick Reference

### Common Commands

```bash
# Frontend
cd dota2_draft_assistant
./gradlew build          # Build
./gradlew test           # Run tests
./gradlew run            # Run app
./gradlew jpackage       # Create installer

# Backend
cd dota2_draft_backend
./gradlew build          # Build
./gradlew test           # Run tests
./gradlew bootRun        # Run locally

# Git
git checkout -b feature/P3-XXX-description
git commit -m "feat(ui): add feature. Closes #XX"
git push -u origin feature/P3-XXX-description

# GitHub CLI
gh pr create --title "feat: ..." --body "Closes #XX"
gh issue close 123 --comment "Done in commit abc123"
gh issue list --label "phase:3-ui" --state open
```

### Workflow Summary

```
1. Pick issue from project board
2. Create feature branch: feature/P3-XXX-description
3. Implement with tests
4. Commit with "Closes #XX" in message
5. Push and create PR
6. CI runs automatically
7. Get review, merge to develop
8. When ready, merge develop to main
9. CI auto-closes referenced issues
```

---

## 11. Contact & Resources

- **Repository (Frontend):** [malibuwileyu/dota2_draft_assistant](https://github.com/malibuwileyu/dota2_draft_assistant)
- **Repository (Backend):** [malibuwileyu/dota2_draft_backend](https://github.com/malibuwileyu/dota2_draft_backend)
- **Backend URL:** https://d2draftassistantbackend-production.up.railway.app
- **Documentation:** `/docs/` folder in frontend repo

---

*Last updated by automated tooling. Review and approve changes before committing.*

