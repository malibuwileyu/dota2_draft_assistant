# Dota 2 Draft Assistant - Implementation Plan

**Version**: 2.1  
**Last Updated**: December 4, 2025  
**Author**: Ryan Heron  
**Status**: Planning  
**PRD Reference**: [PRD_v3.md](./PRD_v3.md)  
**Tech Spec Reference**: [TDD_v3.md](./TDD_v3.md)

---

## 1. Project Overview

### 1.1 Objective

Build a **client-server application** that provides AI-powered draft recommendations for Dota 2 players. The system consists of:

1. **Desktop Client (JavaFX)** - UI, local draft simulation, offline cache
2. **Backend Service (Spring Boot)** - Recommendations, LLM integration, authentication

The application simulates Captain's Mode and All Pick drafts, generates hero recommendations based on synergy/counter matrices, and uses Groq LLM to explain *why* picks are good—not just show win rates.

**Core Problem**: Players have 30 seconds to choose from 124+ heroes during drafts. Existing tools show statistics but don't explain reasoning or consider the player's actual hero pool.

**Solution**: A JavaFX desktop client backed by a Spring Boot API service. Client works offline with cached data; backend provides recommendations, LLM explanations, and shared synergy data.

**Architecture Decision (v2.1)**: Pivoted from embedded to client-server for:
- API key security (never in client binary)
- Shared LLM response caching (cost reduction)
- Hot algorithm updates (no client release needed)
- Rate limiting at server level

### 1.2 Success Criteria

- [ ] **SC-1**: Application starts in <3 seconds on cold launch (95th percentile)
- [ ] **SC-2**: Local recommendations generate in <100ms (5 heroes scored)
- [ ] **SC-3**: All P0 requirements (REQ-001 to REQ-010) pass acceptance tests
- [ ] **SC-4**: Native installers work on clean macOS, Windows, Linux machines
- [ ] **SC-5**: Zero P0 bugs at GA launch
- [ ] **SC-6**: 80%+ test coverage on domain layer
- [ ] **SC-7**: No single file exceeds 200 lines of code
- [ ] **SC-8**: Crash rate <0.5% in production

### 1.3 Non-Goals (Explicit Exclusions)

- **NG-1**: Mobile application — Desktop-first; mobile Dota 2 not a primary use case
- **NG-2**: Real-time overlay — Screen capture/OCR too complex; ToS risk
- **NG-3**: Automated drafting (bot) — Explicitly violates Dota 2 ToS
- **NG-4**: Opponent scouting — Privacy concerns; deferred to v2
- **NG-5**: Team roster management — Complexity; not core to individual player value
- **NG-6**: Web application — Desktop native is the explicit requirement
- **NG-7**: PostgreSQL support — SQLite only; removes unnecessary complexity

### 1.4 Assumptions & Dependencies

| ID | Assumption/Dependency | Risk if Wrong | Mitigation |
|----|----------------------|---------------|------------|
| A-1 | Java 21 LTS will remain supported through 2027 | Low - Oracle commitment | N/A |
| A-2 | Groq API remains available with <3s latency | Medium - Startup risk | Local fallback explanations; abstract LLM interface |
| A-3 | OpenDota free tier (60 req/min) is sufficient | Medium - Could throttle | Aggressive caching; batch requests; paid tier fallback |
| A-4 | Steam OpenID continues support | Low - Stable since 2010 | Graceful degradation to anonymous mode |
| A-5 | Dota 2 Captain's Mode format stable through 2025 | Low - Rarely changes | Modular draft engine; sequence configurable |
| A-6 | Users will install desktop apps | Medium - Trend is web | Focus on lightweight native experience |
| D-1 | OpenJFX 21 cross-platform support | Would require rewrite | Extensive platform testing in CI |
| D-2 | jpackage produces working installers | Critical path | Test on clean VMs per platform |
| D-3 | sqlite-jdbc works on all platforms | Would break offline | Integration tests per platform |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              USER'S MACHINE                                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│   ┌───────────────────────────────────────────────────────────────────────────┐     │
│   │                      DESKTOP CLIENT (JavaFX)                               │     │
│   │                                                                            │     │
│   │   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────┐    │     │
│   │   │   JavaFX UI  │  │ DraftService │  │ BackendClient (HTTP)         │    │     │
│   │   │  Controllers │→ │ HeroService  │→ │ • getRecommendations()       │    │     │
│   │   │              │  │ CacheService │  │ • getExplanation()           │    │     │
│   │   └──────────────┘  └──────────────┘  │ • syncPlayerData()           │    │     │
│   │                            │          └──────────────┬───────────────┘    │     │
│   │                     ┌──────▼──────┐                  │                    │     │
│   │                     │ SQLite Cache│     (offline)    │                    │     │
│   │                     │ heroes.db   │ ◄── fallback ────┘                    │     │
│   │                     └─────────────┘                                       │     │
│   └───────────────────────────────────────────────────────────────────────────┘     │
│                                                    │                                 │
└────────────────────────────────────────────────────┼─────────────────────────────────┘
                                                     │ HTTPS
                                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           BACKEND SERVICE (Spring Boot)                              │
│                              Railway / Fly.io                                        │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│   ┌───────────────────────────────────────────────────────────────────────────┐     │
│   │                            REST API LAYER                                  │     │
│   │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │     │
│   │   │ /recommend   │  │ /explain     │  │ /heroes      │  │ /auth        │  │     │
│   │   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │     │
│   └──────────┼─────────────────┼─────────────────┼─────────────────┼──────────┘     │
│              │                 │                 │                 │                 │
│   ┌──────────▼─────────────────▼─────────────────▼─────────────────▼──────────┐     │
│   │                          APPLICATION SERVICES                              │     │
│   │   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐        │     │
│   │   │RecommendService  │  │ExplanationService│  │   SyncService    │        │     │
│   │   │ • scoring logic  │  │ • Groq prompts   │  │ • OpenDota fetch │        │     │
│   │   │ • personalization│  │ • response cache │  │ • Steam auth     │        │     │
│   │   └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘        │     │
│   └────────────┼─────────────────────┼─────────────────────┼──────────────────┘     │
│                │                     │                     │                         │
│   ┌────────────▼─────────────────────▼─────────────────────▼──────────────────┐     │
│   │                           INFRASTRUCTURE                                   │     │
│   │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │     │
│   │   │ PostgreSQL   │  │ Redis Cache  │  │ GroqClient   │  │OpenDotaClient│  │     │
│   │   │ (hero data,  │  │ (LLM cache,  │  │ (LLM API)    │  │(match data)  │  │     │
│   │   │  synergies)  │  │  rate limits)│  │              │  │              │  │     │
│   │   └──────────────┘  └──────────────┘  └──────┬───────┘  └───────┬──────┘  │     │
│   └──────────────────────────────────────────────┼──────────────────┼─────────┘     │
│                                                  │                  │               │
└──────────────────────────────────────────────────┼──────────────────┼───────────────┘
                                                   │                  │
                                    ┌──────────────┘                  └───────────┐
                                    ▼                                             ▼
                             ┌──────────────┐                             ┌──────────────┐
                             │   Groq API   │                             │  OpenDota /  │
                             │   (LLM)      │                             │  Steam API   │
                             └──────────────┘                             └──────────────┘
```

### 2.2 Component Inventory

#### Desktop Client Components

| Component | Purpose | Technology | Failure Mode | Recovery Strategy |
|-----------|---------|------------|--------------|-------------------|
| **JavaFX UI** | User interaction, display | JavaFX 21 | UI thread blocked | Async operations; Platform.runLater() |
| **DraftService** | Orchestrate draft operations | Spring Core | Invalid state | Return to last valid state; immutable snapshots |
| **DraftEngine** | Draft logic, state machine | Pure Java | Invalid transition | Throw IllegalStateException; undo available |
| **BackendClient** | HTTP calls to backend | Java HttpClient | Timeout/5xx | Offline fallback; retry with backoff |
| **CacheService** | Manage local SQLite cache | sqlite-jdbc | DB corruption | Re-initialize from bundled baseline |
| **LocalRecommendationEngine** | Offline scoring fallback | Pure Java | Missing synergy data | Use cached data; return neutral scores |

#### Backend Service Components

| Component | Purpose | Technology | Failure Mode | Recovery Strategy |
|-----------|---------|------------|--------------|-------------------|
| **RecommendController** | REST API for recommendations | Spring Boot | Invalid request | Return 400 with validation errors |
| **RecommendationService** | Score heroes with full data | Spring Service | Missing data | Return neutral score (0.5); log warning |
| **ExplanationService** | LLM explanations via Groq | Spring Service | Timeout/429 | Return cached response; fallback text |
| **GroqClient** | Groq API integration | Java HttpClient | Timeout/401/429 | Retry with backoff; cache responses |
| **OpenDotaClient** | Match history, hero stats | Java HttpClient | Rate limit (429) | Exponential backoff; cache aggressively |
| **SteamAuthService** | Steam OpenID auth | Spring Service | Steam unavailable | Return error; client uses anonymous mode |
| **PostgreSQL** | Hero data, synergies, stats | PostgreSQL 15 | Connection lost | Connection pool retry; health checks |
| **Redis** | LLM cache, rate limits | Redis 7 | Connection lost | Degrade to no-cache mode; log warning |

### 2.3 Data Flow Diagram

```
[User Action: Hero Selection]
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. VALIDATE INPUT                                                        │
│    ├── Hero ID exists? (reject if null/invalid)                         │
│    ├── Hero available in current draft? (reject if picked/banned)       │
│    └── Action valid for phase? (reject pick during ban phase)           │
│                                                                          │
│    Rejection → Show user-friendly error message                         │
└─────────────────────────────────────────────────────────────────────────┘
         │ Valid hero selection
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. AUTHORIZE ACTION                                                      │
│    ├── Is it this team's turn? (state.currentTeam)                      │
│    └── Is draft still in progress? (state.phase != COMPLETED)           │
│                                                                          │
│    Unauthorized → Ignore action silently (not user error)               │
└─────────────────────────────────────────────────────────────────────────┘
         │ Authorized
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. PROCESS (Domain Layer - Pure Functions)                               │
│    ├── DraftEngine.pickHero(state, hero) → newState                     │
│    │       └── Returns NEW immutable DraftState                         │
│    ├── Calculate next turn (turnIndex + 1)                              │
│    ├── Determine next phase (check sequence array)                      │
│    └── Append to history (for undo)                                     │
│                                                                          │
│    All operations return new objects; no mutation                       │
└─────────────────────────────────────────────────────────────────────────┘
         │ New DraftState
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. PERSIST (Application Layer)                                           │
│    ├── Update currentState reference in DraftService                    │
│    ├── Auto-save to SQLite for crash recovery (async)                   │
│    └── Log action: "[PICK] Team=RADIANT Hero=Anti-Mage Turn=8"          │
│                                                                          │
│    Persistence failure → Log error; app continues (data in memory)      │
└─────────────────────────────────────────────────────────────────────────┘
         │ State persisted
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. UPDATE UI (Presentation Layer)                                        │
│    ├── Platform.runLater() for thread safety                            │
│    ├── Update team panels (picks/bans)                                  │
│    ├── Update hero grid (disable selected hero)                         │
│    ├── Update phase indicator                                           │
│    ├── Trigger async recommendation refresh                             │
│    └── Update win probability bar                                       │
│                                                                          │
│    UI errors → Log, don't crash; user sees stale state temporarily      │
└─────────────────────────────────────────────────────────────────────────┘
         │
         ▼
[UI Updated: User sees new draft state]
```

### 2.4 Boundary Definitions

| Boundary | Trust Level | Validation Required | Error Handling |
|----------|-------------|---------------------|----------------|
| **User → UI** | Untrusted | Hero ID validation; action type | Show error toast; don't crash |
| **UI → Application** | Trusted | Type assertion via method signature | IllegalArgumentException |
| **Application → Domain** | Trusted | Preconditions checked in domain | IllegalStateException |
| **Domain → Domain** | Fully Trusted | None (records are self-validating) | N/A |
| **Application → Infrastructure** | Semi-trusted | Repository returns Optional<T> | Handle empty; log if unexpected |
| **Infrastructure → SQLite** | Trusted | SQL prepared statements | SQLException wrapped; retry once |
| **Infrastructure → Groq API** | Untrusted | Response schema validation | Timeout; invalid JSON → fallback |
| **Infrastructure → OpenDota API** | Untrusted | Response schema validation | 429 → backoff; 5xx → retry |
| **Infrastructure → Steam API** | Untrusted | OpenID signature verification | Invalid sig → reject auth |

---

## 3. Directory Structure

```
dota2_draft_assistant/
├── settings.gradle.kts           # Multi-project build settings
├── gradlew                       # Gradle wrapper (Unix)
├── gradlew.bat                   # Gradle wrapper (Windows)
│
├── domain/                        # SHARED DOMAIN MODULE (Pure Java)
│   ├── build.gradle.kts
│   └── src/main/java/com/dota2assistant/domain/
│       ├── draft/
│       │   ├── DraftEngine.java, CaptainsModeDraft.java, etc.
│       ├── recommendation/
│       │   ├── RecommendationEngine.java, SynergyScorer.java, etc.
│       ├── model/
│       │   ├── Hero.java, DraftState.java, Recommendation.java
│       └── analysis/
│           └── AnalysisEngine.java, TeamComposition.java
│
├── client/                        # DESKTOP CLIENT (JavaFX)
│   ├── build.gradle.kts          # Client-specific dependencies
│   └── src/
│       ├── main/java/com/dota2assistant/client/
│       │   ├── Dota2DraftAssistant.java      # Entry point
│       │   ├── config/
│       │   │   ├── AppConfig.java
│       │   │   └── DatabaseConfig.java       # SQLite cache
│       │   ├── application/                   # Client services
│       │   │   ├── DraftService.java
│       │   │   ├── CacheService.java
│       │   │   └── RecommendationService.java # Calls backend or offline
│       │   ├── infrastructure/
│       │   │   ├── BackendClient.java        # HTTP to backend API
│       │   │   ├── SqliteCacheRepository.java
│       │   │   └── LocalRecommendationEngine.java
│       │   └── ui/
│       │       ├── controller/
│       │       │   ├── MainController.java (<100 lines)
│       │       │   ├── DraftController.java (<200 lines)
│       │       │   ├── HeroGridController.java
│       │       │   └── ...
│       │       └── component/
│       │           ├── HeroCard.java
│       │           └── ...
│       └── main/resources/
│           ├── fxml/, css/, images/, data/
│           └── db/migrations/                # Client SQLite schema
│
├── backend/                       # BACKEND SERVICE (Spring Boot)
│   ├── build.gradle.kts          # Server-specific dependencies
│   └── src/
│       ├── main/java/com/dota2assistant/backend/
│       │   ├── BackendApplication.java       # Spring Boot entry
│       │   ├── config/
│       │   │   ├── SecurityConfig.java
│       │   │   ├── RedisConfig.java
│       │   │   └── CorsConfig.java
│       │   ├── controller/
│       │   │   ├── RecommendController.java  # POST /api/v1/recommend
│       │   │   ├── ExplainController.java    # POST /api/v1/explain
│       │   │   ├── HeroController.java       # GET /api/v1/heroes
│       │   │   └── AuthController.java       # /api/v1/auth/*
│       │   ├── service/
│       │   │   ├── RecommendationService.java
│       │   │   ├── ExplanationService.java
│       │   │   └── SyncService.java
│       │   └── infrastructure/
│       │       ├── GroqClient.java
│       │       ├── OpenDotaClient.java
│       │       ├── SteamAuthService.java
│       │       └── repository/
│       │           ├── HeroRepository.java   # PostgreSQL
│       │           └── SynergyRepository.java
│       └── main/resources/
│           ├── application.yml
│           └── db/migration/                 # Flyway PostgreSQL migrations
│
├── docs/
│   ├── PRD_v3.md
│   ├── TDD_v3.md
│   └── IMPLEMENTATION_PLAN_v2.md
│
└── scripts/
    ├── deploy-backend.sh
    └── build-all-platforms.sh
```

### 3.1 File Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Domain Models | PascalCase, no suffix | `Hero.java`, `DraftState.java` |
| Interfaces | PascalCase, descriptive | `DraftEngine.java`, `HeroRepository.java` |
| Implementations | PascalCase with prefix/context | `CaptainsModeDraft.java`, `SqliteHeroRepository.java` |
| Services | PascalCase + Service | `DraftService.java` |
| Controllers | PascalCase + Controller | `DraftController.java` |
| Enums | PascalCase | `Team.java`, `DraftPhase.java` |
| Exceptions | PascalCase + Exception | `DraftValidationException.java` |
| Tests | Same name + Test | `CaptainsModeDraftTest.java` |
| FXML files | kebab-case | `hero-grid.fxml` |
| CSS files | kebab-case | `dark-theme.css` |
| SQL migrations | `V###__description.sql` | `V001__initial_schema.sql` |

### 3.2 Package Rules

**Shared Domain Module** (`domain/`):
| Package | Import Rules | Max Lines/File |
|---------|--------------|----------------|
| `domain.*` | NO framework imports (Spring, JavaFX, JDBC) | 200 |

**Client Module** (`client/`):
| Package | Import Rules | Max Lines/File |
|---------|--------------|----------------|
| `client.application.*` | May import domain.*, Spring Core | 200 |
| `client.infrastructure.*` | May import domain.*, HttpClient, Jackson, SQLite | 200 |
| `client.ui.*` | May import domain.*, application.*, JavaFX | 200 |

**Backend Module** (`backend/`):
| Package | Import Rules | Max Lines/File |
|---------|--------------|----------------|
| `backend.controller.*` | May import domain.*, service.*, Spring Web | 200 |
| `backend.service.*` | May import domain.*, infrastructure.*, Spring | 200 |
| `backend.infrastructure.*` | May import domain.*, JDBC, Redis, HttpClient | 200 |

---

## 4. Error Handling Strategy

### 4.1 Error Taxonomy

```java
// util/AppError.java
public abstract class AppError extends RuntimeException {
    private final String code;
    private final boolean operational;  // Expected vs unexpected
    private final Map<String, Object> context;
    
    protected AppError(String code, String message, boolean operational, 
                       Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.operational = operational;
        this.context = context != null ? context : Map.of();
    }
    
    public String getCode() { return code; }
    public boolean isOperational() { return operational; }
    public Map<String, Object> getContext() { return context; }
}

// Domain Errors (Operational - expected, recoverable)
public class DraftValidationException extends AppError {
    public DraftValidationException(String message, Map<String, Object> context) {
        super("DRAFT_VALIDATION_ERROR", message, true, context, null);
    }
}

public class HeroNotFoundException extends AppError {
    public HeroNotFoundException(int heroId) {
        super("HERO_NOT_FOUND", "Hero not found: " + heroId, true, 
              Map.of("heroId", heroId), null);
    }
}

public class InvalidDraftPhaseException extends AppError {
    public InvalidDraftPhaseException(DraftPhase current, String attemptedAction) {
        super("INVALID_DRAFT_PHASE", 
              "Cannot " + attemptedAction + " during " + current, true,
              Map.of("currentPhase", current, "attemptedAction", attemptedAction), null);
    }
}

// Infrastructure Errors (May or may not be operational)
public class RepositoryException extends AppError {
    public RepositoryException(String message, Throwable cause) {
        super("REPOSITORY_ERROR", message, false, Map.of(), cause);
    }
}

public class ApiException extends AppError {
    private final int statusCode;
    
    public ApiException(String service, int statusCode, String message) {
        super("API_ERROR", service + " API error: " + message, 
              statusCode == 429,  // Rate limits are operational
              Map.of("service", service, "statusCode", statusCode), null);
        this.statusCode = statusCode;
    }
    
    public int getStatusCode() { return statusCode; }
}

public class AuthenticationException extends AppError {
    public AuthenticationException(String message) {
        super("AUTH_ERROR", message, true, Map.of(), null);
    }
}
```

### 4.2 Error Response Contract (for logging)

```java
// util/ErrorLog.java
public record ErrorLog(
    String code,
    String message,
    String requestId,
    Instant timestamp,
    Map<String, Object> context,
    String stackTrace  // Only for non-operational errors
) {
    public static ErrorLog from(AppError error, String requestId) {
        return new ErrorLog(
            error.getCode(),
            error.getMessage(),
            requestId,
            Instant.now(),
            error.getContext(),
            error.isOperational() ? null : getStackTrace(error)
        );
    }
}
```

### 4.3 Error Boundaries

| Layer | Catches | Action |
|-------|---------|--------|
| **UI Controller** | All exceptions | Log; show user-friendly toast; don't crash |
| **Application Service** | Domain exceptions | Translate to UI-friendly message; rethrow if unrecoverable |
| **Domain Engine** | None | Throw specific exceptions; caller handles |
| **Repository** | SQLException | Wrap in RepositoryException; include SQL context |
| **API Client** | IOException, HttpTimeoutException | Wrap in ApiException; include HTTP context |
| **Groq Client** | All | Fallback to local explanation; log warning |
| **OpenDota Client** | 429 | Exponential backoff; throw if exhausted |
| **Steam Client** | All | Fallback to anonymous mode; log warning |

### 4.4 Error Recovery Strategies

```java
// Example: Groq client with fallback
public class GroqClient {
    
    public CompletableFuture<String> generateExplanation(Recommendation rec) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(
                generateLocalExplanation(rec)  // Immediate fallback
            );
        }
        
        return httpClient.sendAsync(buildRequest(rec), BodyHandlers.ofString())
            .orTimeout(10, TimeUnit.SECONDS)
            .thenApply(this::parseResponse)
            .exceptionally(ex -> {
                log.warn("Groq API failed, using local fallback", ex);
                return generateLocalExplanation(rec);  // Graceful fallback
            });
    }
    
    private String generateLocalExplanation(Recommendation rec) {
        var sb = new StringBuilder();
        sb.append(rec.hero().localizedName()).append(" is recommended because:\n");
        for (var reason : rec.reasons()) {
            sb.append("• ").append(reason.description()).append("\n");
        }
        return sb.toString();
    }
}

// Example: OpenDota with retry
public class OpenDotaClient {
    
    private final RateLimiter rateLimiter = new RateLimiter(60, Duration.ofMinutes(1));
    
    public List<Match> fetchMatches(long accountId, int limit) {
        return retryWithBackoff(() -> {
            rateLimiter.acquire();  // Block if rate limited
            
            var response = httpClient.send(buildRequest(accountId, limit),
                BodyHandlers.ofString());
            
            if (response.statusCode() == 429) {
                throw new ApiException("OpenDota", 429, "Rate limited");
            }
            
            if (response.statusCode() != 200) {
                throw new ApiException("OpenDota", response.statusCode(), 
                    response.body());
            }
            
            return parseMatches(response.body());
        }, 3, Duration.ofSeconds(1), Duration.ofSeconds(30));
    }
    
    private <T> T retryWithBackoff(Supplier<T> operation, int maxRetries, 
                                    Duration initialDelay, Duration maxDelay) {
        Duration delay = initialDelay;
        ApiException lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (ApiException e) {
                lastException = e;
                if (!e.isOperational() || attempt == maxRetries) {
                    throw e;
                }
                log.warn("Attempt {} failed, retrying in {}ms", attempt, delay.toMillis());
                sleep(delay);
                delay = delay.multipliedBy(2);
                if (delay.compareTo(maxDelay) > 0) delay = maxDelay;
            }
        }
        throw lastException;
    }
}
```

---

## 5. Implementation Phases

### Phase 0: Foundation (Week 1-2)

**Goal**: Set up infrastructure that makes the wrong thing impossible.

| Task ID | Task | Priority | Acceptance Criteria | Est. Hours |
|---------|------|----------|---------------------|------------|
| P0-001 | Initialize Gradle project with Java 21 | P0 | `./gradlew build` passes | 2 |
| P0-002 | Configure JavaFX 21 with jlink/jpackage | P0 | `./gradlew run` shows blank window | 4 |
| P0-003 | Create package structure per TDD | P0 | All packages exist with placeholder classes | 2 |
| P0-004 | Configure Spring Boot 3.2 (no web) | P0 | Spring context initializes; app starts | 4 |
| P0-005 | Set up SQLite with migrations | P0 | `V001__initial_schema.sql` runs on startup | 4 |
| P0-006 | Define domain model records | P0 | All records compile; immutable | 4 |
| P0-007 | Set up JUnit 5 + Mockito + AssertJ | P0 | `./gradlew test` runs (even with 0 tests) | 2 |
| P0-008 | Configure structured logging (SLF4J + Logback) | P0 | Logs appear with [timestamp] [level] [class] | 2 |
| P0-009 | Create base error classes | P0 | All errors extend AppError | 2 |
| P0-010 | Import hero data from v1 | P0 | 124+ heroes in SQLite | 4 |

**Phase 0 Checklist**:
- [ ] `./gradlew build` passes
- [ ] `./gradlew run` shows blank JavaFX window
- [ ] `./gradlew test` runs
- [ ] SQLite database created at `~/.dota2assistant/data.db`
- [ ] All domain model records compile
- [ ] 124+ heroes queryable from database
- [ ] Logging works with structured format
- [ ] No file exceeds 200 lines

**Phase 0 Quality Gates**:
- [ ] `strict` mode enabled in Gradle Java plugin
- [ ] No `@SuppressWarnings` annotations
- [ ] All records are immutable (final fields only)
- [ ] Domain package has zero framework imports

---

### Phase 1: Core Domain (Weeks 3-5)

**Goal**: Build the business logic with no external dependencies.

| Task ID | Task | Priority | Depends On | Acceptance Criteria | Est. Hours |
|---------|------|----------|------------|---------------------|------------|
| P1-001 | Implement `DraftEngine` interface | P0 | P0-006 | Interface defined with clear contracts | 2 |
| P1-002 | Implement `CaptainsModeDraft` | P0 | P1-001 | 24-action sequence works; 100% test coverage | 8 |
| P1-003 | Implement `AllPickDraft` | P0 | P1-001 | Simultaneous picks work; tests pass | 4 |
| P1-004 | Implement `DraftState` immutability | P0 | P0-006 | `withPick()` returns new instance; tests verify | 4 |
| P1-005 | Implement undo/redo via history | P0 | P1-004 | Undo restores previous state; redo works | 4 |
| P1-006 | Implement `HeroRepository` interface | P0 | P0-005 | `findAll()`, `findById()` contracts defined | 2 |
| P1-007 | Implement `SqliteHeroRepository` | P0 | P1-006 | Integration test passes; 124+ heroes loaded | 4 |
| P1-008 | Implement `DraftService` | P0 | P1-002, P1-007 | Orchestrates draft; loads heroes from repo | 4 |
| P1-009 | Implement `RecommendationEngine` | P0 | P0-006 | Top 5 heroes scored in <100ms | 8 |
| P1-010 | Implement `SynergyScorer` | P0 | P1-009 | Score based on ally synergy matrix | 4 |
| P1-011 | Implement `CounterScorer` | P0 | P1-009 | Score based on enemy counter matrix | 4 |
| P1-012 | Implement `RoleScorer` | P0 | P1-009 | Score based on team role gaps | 4 |
| P1-013 | Import synergy/counter data | P0 | P0-005 | 15,000+ hero pair scores in SQLite | 4 |
| P1-014 | Implement `WinProbabilityCalculator` | P0 | P1-009 | Returns 0-100%; updates in <200ms | 4 |
| P1-015 | Domain layer unit tests | P0 | All above | 80%+ coverage on domain | 8 |

**Phase 1 Quality Gates**:
- [ ] All domain code has **zero** external imports (no DB, no HTTP, no Spring)
- [ ] All services accept and return **typed domain objects**
- [ ] All edge cases have explicit tests (undo at start, pick during ban, etc.)
- [ ] No `any` equivalent (Object) types in domain layer
- [ ] Recommendation generation <100ms (benchmark test)
- [ ] All methods have Javadoc with @throws

---

### Phase 2A: Client Infrastructure (Weeks 6-7)

**Goal**: Build client-side HTTP client and caching for backend communication.

| Task ID | Task | Priority | Depends On | Acceptance Criteria | Est. Hours |
|---------|------|----------|------------|---------------------|------------|
| P2A-001 | Implement `BackendClient` | P0 | P0-008 | HTTP calls to backend API | 4 |
| P2A-002 | Implement `CacheService` | P0 | P0-005 | SQLite cache for heroes/synergies | 4 |
| P2A-003 | Implement offline fallback | P0 | P2A-002 | Works when backend unavailable | 4 |
| P2A-004 | Implement `RateLimiter` utility | P0 | - | Token bucket; blocks when exhausted | 4 |
| P2A-005 | Implement retry with backoff | P0 | - | Exponential backoff; max retries | 4 |
| P2A-006 | Implement `LocalRecommendationEngine` | P0 | P1-009 | Offline scoring with cached data | 6 |
| P2A-007 | Client integration tests | P0 | All above | Tests with mocked backend | 6 |

**Phase 2A Quality Gates**:
- [ ] BackendClient has 10s timeout
- [ ] All API errors wrapped in `ApiException`
- [ ] Offline fallback works within 100ms
- [ ] No hardcoded backend URLs (configurable)
- [ ] Client tests pass without real backend

---

### Phase 2B: Backend Service (Weeks 7-9)

**Goal**: Build the backend API that serves recommendations and handles external APIs.

| Task ID | Task | Priority | Depends On | Acceptance Criteria | Est. Hours |
|---------|------|----------|------------|---------------------|------------|
| P2B-001 | Initialize Spring Boot backend project | P0 | - | `./gradlew bootRun` starts on port 8080 | 4 |
| P2B-002 | Configure PostgreSQL with migrations | P0 | P2B-001 | Flyway migrations run; schema created | 4 |
| P2B-003 | Configure Redis cache | P0 | P2B-001 | Connection works; cache operations tested | 4 |
| P2B-004 | Implement `RecommendController` | P0 | P2B-002 | POST /api/v1/recommend works | 4 |
| P2B-005 | Implement `RecommendationService` | P0 | P2B-004 | Scoring logic (shared domain) | 6 |
| P2B-006 | Implement `GroqClient` (backend) | P0 | P2B-001 | API call works; timeout handled | 6 |
| P2B-007 | Implement `ExplainController` | P0 | P2B-006 | POST /api/v1/explain works | 4 |
| P2B-008 | Implement LLM response caching | P0 | P2B-003, P2B-007 | Redis cache; 60% hit rate target | 4 |
| P2B-009 | Implement `OpenDotaClient` (backend) | P1 | P2B-001 | Match history fetched; rate limit handled | 6 |
| P2B-010 | Implement `SteamAuthService` | P1 | P2B-001 | OpenID flow works; Steam ID captured | 8 |
| P2B-011 | Implement `AuthController` | P1 | P2B-010 | /api/v1/auth/* endpoints work | 4 |
| P2B-012 | Implement `HeroController` | P0 | P2B-002 | GET /api/v1/heroes returns hero data | 4 |
| P2B-013 | Implement rate limiting (per-IP) | P0 | P2B-003 | Redis-backed; 60 req/min default | 4 |
| P2B-014 | Configure CORS for client | P0 | P2B-001 | Client can call backend | 2 |
| P2B-015 | Backend integration tests | P0 | All above | Tests with mocked external APIs | 8 |
| P2B-016 | Deploy to Railway/Fly.io | P0 | All above | Backend accessible via HTTPS | 4 |

**Phase 2B Quality Gates**:
- [ ] All API clients have timeout configuration
- [ ] All API clients validate response schemas
- [ ] All API errors return proper HTTP status codes
- [ ] Groq failure → return cached/fallback response
- [ ] OpenDota rate limit → exponential backoff
- [ ] No API keys in logs
- [ ] Redis cache reduces Groq calls by >50%
- [ ] Backend responds in <200ms (P95, excluding LLM)
- [ ] Health check endpoint at /api/v1/health

---

### Phase 3: User Interface (Weeks 10-13)

**Goal**: Build responsive, keyboard-navigable UI with <100ms interactions.

| Task ID | Task | Priority | Depends On | Acceptance Criteria | Est. Hours |
|---------|------|----------|------------|---------------------|------------|
| P3-001 | Create `MainController` (<100 lines) | P0 | P0-004 | Coordinates sub-controllers | 4 |
| P3-002 | Create `HomeController` | P0 | P3-001 | New Draft button, mode selection | 4 |
| P3-003 | Create `DraftController` | P0 | P1-008 | Wires draft service to UI | 8 |
| P3-004 | Create `HeroGridController` | P0 | P1-007 | 124 heroes displayed; filter works | 6 |
| P3-005 | Create `HeroCard` component | P0 | - | Portrait, name, disabled state | 4 |
| P3-006 | Create `TeamPanelController` | P0 | - | Shows 5 picks, bans for team | 4 |
| P3-007 | Create `RecommendationController` | P0 | P1-009 | Top 5 recommendations displayed | 4 |
| P3-008 | Create `WinProbabilityBar` | P0 | P1-014 | Visual bar; updates on pick | 4 |
| P3-009 | Create `PhaseIndicator` | P0 | - | Shows current phase and team turn | 2 |
| P3-010 | Create `TimerDisplay` | P1 | - | 30s countdown; reserve time | 4 |
| P3-011 | Create `SettingsController` | P1 | P2-008 | API keys, theme toggle | 4 |
| P3-012 | Create `LoginController` | P1 | P2-006 | Steam login button; profile display | 4 |
| P3-013 | Implement hero search (<50ms) | P0 | P3-004 | Type to filter; instant update | 4 |
| P3-014 | Implement keyboard navigation | P1 | All above | Full draft without mouse | 6 |
| P3-015 | Create dark theme CSS | P0 | - | Dark slate background; readable text | 4 |
| P3-016 | Create light theme CSS | P2 | P3-015 | Light alternative | 2 |
| P3-017 | Load hero images asynchronously | P0 | P3-005 | No UI blocking; placeholders while loading | 4 |
| P3-018 | UI responsiveness testing | P0 | All above | All interactions <100ms | 8 |

**Phase 3 Quality Gates**:
- [ ] No controller exceeds 200 lines
- [ ] All UI updates via `Platform.runLater()`
- [ ] All long operations run on background threads
- [ ] Hero search responds in <50ms
- [ ] Recommendation panel updates in <200ms
- [ ] Keyboard can complete entire draft
- [ ] Dark theme WCAG 2.1 AA compliant (contrast)
- [ ] No UI freezes for >100ms

---

### Phase 4: Packaging & Release (Weeks 14-15)

**Goal**: Create native installers that work on clean machines.

| Task ID | Task | Priority | Depends On | Acceptance Criteria | Est. Hours |
|---------|------|----------|------------|---------------------|------------|
| P4-001 | Configure jpackage for macOS | P0 | All prior | `.dmg` installs on clean Mac | 8 |
| P4-002 | Configure jpackage for Windows | P0 | All prior | `.msi` installs on clean Windows | 8 |
| P4-003 | Configure jpackage for Linux | P0 | All prior | `.deb` installs on clean Ubuntu | 4 |
| P4-004 | Add application icons | P0 | - | Icon visible in dock/taskbar | 2 |
| P4-005 | Test on clean VMs | P0 | P4-001,P4-002,P4-003 | Works without pre-installed Java | 8 |
| P4-006 | Configure macOS code signing | P1 | P4-001 | No Gatekeeper warning (if signing enabled) | 4 |
| P4-007 | Configure GitHub Actions CI | P0 | - | Build + test on push | 4 |
| P4-008 | Configure multi-platform release | P0 | P4-007 | Artifacts for all 3 platforms | 4 |
| P4-009 | Startup time optimization | P0 | - | <3s cold start verified | 4 |
| P4-010 | Memory usage profiling | P0 | - | <200MB idle, <300MB active | 4 |
| P4-011 | Create release notes template | P0 | - | Changelog, known issues, install instructions | 2 |
| P4-012 | Bug bash and fixes | P0 | All above | Zero P0/P1 bugs | 16 |

**Phase 4 Quality Gates**:
- [ ] All 3 platform installers tested on clean VMs
- [ ] Cold startup <3 seconds (95th percentile)
- [ ] Memory usage <200MB idle
- [ ] No crashes during 1-hour usage session
- [ ] All P0 bugs fixed
- [ ] Release artifacts uploaded to GitHub

---

### Phase 5: Personalization (Weeks 16-19) - Post-Alpha

**Goal**: Add Steam auth, match history, personalized recommendations.

| Task ID | Task | Priority | Depends On | Acceptance Criteria | Est. Hours |
|---------|------|----------|------------|---------------------|------------|
| P5-001 | Integrate Steam login in UI | P1 | P2-006 | Login button works; profile shown | 4 |
| P5-002 | Fetch match history on login | P1 | P2-004 | Last 100 matches imported | 4 |
| P5-003 | Calculate hero comfort scores | P1 | P5-002 | Per-hero stats stored | 6 |
| P5-004 | Implement `PersonalScorer` | P1 | P5-003 | Recommendations weighted by comfort | 4 |
| P5-005 | Display personal stats in UI | P1 | P5-003 | Hero tooltip shows games, win rate | 4 |
| P5-006 | Implement recommendation weighting slider | P1 | P5-004 | User can adjust global vs. personal | 2 |
| P5-007 | Implement timer mode | P1 | P3-010 | 30s countdown; reserve time; auto-random | 6 |
| P5-008 | Implement draft history saving | P2 | P3-003 | Save draft; review later | 4 |
| P5-009 | Implement draft export as PNG | P2 | P5-008 | Export button; save to file | 4 |

---

## 6. Risk Register

| ID | Risk | Likelihood | Impact | Mitigation | Contingency |
|----|------|------------|--------|------------|-------------|
| R-01 | Groq API becomes unavailable or pricing changes | Medium | High | Abstract LLM interface; local fallback; LLM cache reduces calls | Switch to alternative provider (OpenAI, Anthropic) |
| R-02 | OpenDota rate limits block personalization | Medium | Medium | Aggressive caching; batch requests; server-side fetching | Consider paid tier ($5/mo) or reduce fetch frequency |
| R-03 | Dota 2 patch changes draft format | Low | High | Modular draft engine; sequence is configurable data | Patch within 24 hours of game update |
| R-04 | macOS code signing rejected by Apple | Low | Medium | Submit for review early; notarization process ready | Distribute unsigned with manual Gatekeeper bypass instructions |
| R-05 | **Backend goes down** | Medium | High | Client offline fallback; cached synergies; health checks | Auto-restart via Railway/Fly.io; manual intervention if prolonged |
| R-06 | **Backend costs exceed budget** | Medium | Medium | Start on free tier; LLM caching reduces Groq costs; monitor usage | Implement hard rate limits; consider paid tier or alternative hosting |
| R-07 | **Redis/PostgreSQL data loss** | Low | High | Railway managed backups; export synergy data periodically | Restore from backup; re-seed from OpenDota |
| R-08 | v1 architectural patterns leak into v3 | Medium | Medium | Code reviews; 200-line file limit enforced; domain import checks | Refactor offending code before merge |
| R-09 | JavaFX rendering issues on specific platforms | Medium | Medium | Test on all platforms in CI; use stable OpenJFX version | Platform-specific CSS workarounds |
| R-10 | Startup time exceeds 3 seconds | Medium | High | Lazy loading; minimal client-side init; profile in Phase 4 | Defer hero image loading; async backend calls |
| R-11 | Test coverage insufficient for domain layer | Medium | Medium | 80% coverage gate in CI; mandatory tests for new features | Test-writing sprint before release |
| R-12 | **Backend latency too high** | Medium | Medium | Redis caching; database indexes; query optimization | Add CDN for static data; optimize hot paths |

---

## 7. Verification Checklist

### Before Each PR

- [ ] Code compiles with `./gradlew build` (no warnings)
- [ ] All tests pass with `./gradlew test`
- [ ] No new `@SuppressWarnings` annotations
- [ ] No file exceeds 200 lines
- [ ] Domain classes have no framework imports
- [ ] All errors handled (no empty catch blocks)
- [ ] Logging added for debugging (at appropriate levels)
- [ ] Javadoc on public methods

### Before Phase Completion

- [ ] All tasks in phase marked complete
- [ ] Quality gates for phase satisfied
- [ ] No P0 bugs open
- [ ] Performance benchmarks pass
- [ ] Code reviewed by at least 1 person
- [ ] Documentation updated

### Before Alpha Release

- [ ] All Phase 0-4 tasks complete
- [ ] SC-1 to SC-8 success criteria verified
- [ ] All 3 platform installers tested on clean VMs
- [ ] No P0 bugs; <3 P1 bugs
- [ ] 80%+ domain test coverage
- [ ] Security review (no credentials in code/logs)
- [ ] README with install instructions
- [ ] Release notes written

### Before GA Release

- [ ] All P0 + P1 requirements pass acceptance tests
- [ ] 50+ beta testers provided feedback
- [ ] 80%+ satisfaction rating in feedback
- [ ] All P0/P1 bugs from beta fixed
- [ ] Performance stable under 1 hour usage
- [ ] macOS signed (if budget allows)
- [ ] GitHub release with binaries
- [ ] Announcement post prepared

### Post-Deployment (First Week)

- [ ] Smoke tests pass on released version
- [ ] No error spike in user reports
- [ ] Crash rate <0.5%
- [ ] Performance within SLA
- [ ] User-facing features verified manually
- [ ] Monitoring dashboards reviewed (if analytics enabled)

---

## 8. Appendix

### A. Decision Log

| Date | Decision | Rationale | Alternatives Rejected |
|------|----------|-----------|----------------------|
| Dec 4, 2025 | Keep Java 21 + JavaFX | Existing code works; problems were architectural, not language | Rust/Tauri (rejected by user), Electron (heavy), C#/.NET (ecosystem lock-in) |
| Dec 4, 2025 | Gradle instead of Maven | Faster builds; cleaner DSL; better jpackage support | Keep Maven (slower, more verbose) |
| Dec 4, 2025 | Clean Architecture layers | Testability; domain isolation; prevents god classes | MVC only (too flat; promotes coupling) |
| Dec 4, 2025 | Immutable records for domain | Prevents mutation bugs; thread-safe; easier testing | Mutable POJOs (error-prone) |
| Dec 4, 2025 | 200-line file limit | Prevents god classes; forces decomposition | No limit (leads to 4500-line MainController) |
| Dec 4, 2025 | **Client-server architecture** | API key security, shared caching, LLM cost reduction, hot updates | Embedded (simpler but less secure, no sharing) |
| Dec 4, 2025 | SQLite on client, PostgreSQL on backend | Client needs portable cache; backend needs ACID, concurrent access | SQLite everywhere (concurrent writes problematic on server) |
| Dec 4, 2025 | Redis for LLM caching | Fast, TTL support, easy rate limiting | In-memory (loses on restart), PostgreSQL (slower) |
| Dec 4, 2025 | Railway/Fly.io for hosting | Free tier, auto-scaling, managed Postgres/Redis | Self-hosted (maintenance burden), AWS (complex) |
| Dec 4, 2025 | Offline fallback on client | Works when backend down; graceful degradation | Fail closed (poor UX when offline) |

### B. Open Questions

| ID | Question | Owner | Due Date | Resolution |
|----|----------|-------|----------|------------|
| Q-01 | Should we support Turbo/Ability Draft modes? | Product | Jan 2025 | Pending |
| Q-02 | Default global vs. personal weighting ratio? | Product | Feb 2025 | Pending |
| Q-03 | Invest in Windows code signing certificate? | Tech | Mar 2025 | Pending |
| Q-04 | Which LLM provider if Groq pricing changes? | Tech | Ongoing | Pending |
| Q-05 | Should draft history sync to cloud (v2)? | Product | Post-GA | Pending |

### C. Glossary

| Term | Definition |
|------|------------|
| **Captain's Mode** | Competitive draft format with structured pick/ban phases (24 total actions) |
| **All Pick** | Ranked draft format where teams pick simultaneously |
| **MMR** | Matchmaking Rating—numerical skill score (0-12000+) |
| **Position** | Farm priority: 1 (hard carry) to 5 (hard support) |
| **Meta** | Current strongest strategies/heroes in the game |
| **Counter** | Hero statistically strong against another hero |
| **Synergy** | Heroes that perform better on the same team |
| **Comfort Score** | Player's proficiency with a hero (games, win rate, recency) |
| **Operational Error** | Expected error that can be recovered from (e.g., rate limit) |
| **Non-Operational Error** | Unexpected error indicating a bug (e.g., null pointer) |
| **Domain Layer** | Pure business logic with no framework dependencies |
| **Application Layer** | Use cases that orchestrate domain logic |
| **Infrastructure Layer** | Adapters to external systems (DB, APIs) |

### D. Requirement Traceability

| Requirement | Phase | Task IDs | Test Coverage |
|-------------|-------|----------|---------------|
| REQ-001 (Captain's Mode) | Phase 1 | P1-001, P1-002 | CaptainsModeDraftTest |
| REQ-002 (All Pick) | Phase 1 | P1-001, P1-003 | AllPickDraftTest |
| REQ-003 (Hero Database) | Phase 0, 1 | P0-005, P0-010, P1-006, P1-007 | SqliteHeroRepositoryTest |
| REQ-004 (Hero Grid) | Phase 3 | P3-004, P3-005, P3-013 | HeroGridControllerTest |
| REQ-005 (Recommendations) | Phase 1 | P1-009 to P1-013 | RecommendationEngineTest |
| REQ-006 (Win Probability) | Phase 1, 3 | P1-014, P3-008 | WinProbabilityCalculatorTest |
| REQ-007 (Undo/Redo) | Phase 1 | P1-005 | DraftStateTest |
| REQ-008 (Team Composition) | Phase 3 | P3-006 | TeamPanelControllerTest |
| REQ-009 (Perspective Toggle) | Phase 3 | P3-003 | DraftControllerTest |
| REQ-010 (Packaging) | Phase 4 | P4-001 to P4-005 | Manual testing on VMs |
| REQ-011 (Groq LLM) | Phase 2 | P2-001 to P2-003 | GroqClientTest |
| REQ-012 (Steam Auth) | Phase 2, 5 | P2-006, P2-007, P5-001 | SteamAuthClientTest |
| REQ-013 (Match History) | Phase 2, 5 | P2-004, P2-005, P5-002 | OpenDotaClientTest |
| REQ-014 (Personalization) | Phase 5 | P5-003 to P5-006 | PersonalScorerTest |
| REQ-015 (Timer Mode) | Phase 5 | P5-007 | TimerDisplayTest |
| REQ-016 (Settings) | Phase 2, 3 | P2-008, P2-009, P3-011 | SettingsServiceTest |

---

*This implementation plan follows Systems Engineering principles: defense in depth, fail-safe defaults, explicit contracts, and observable operations. All tasks reference traceable requirements from PRD_v3.md.*

