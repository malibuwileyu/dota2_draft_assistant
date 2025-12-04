# Dota 2 Draft Assistant - Technical Design Document v3.1

**Version:** 3.1  
**Last Updated:** December 4, 2025  
**Status:** Architectural Planning  
**Companion Document:** PRD_v3.md

---

## Executive Summary

This document outlines the technical architecture for the Dota 2 Draft Assistant as a **client-server application**.

**Architecture Decision (v3.1):** Pivoted from embedded to client-server for:
- **API Key Security** - Groq/OpenDota keys never shipped in client binary
- **Shared Caching** - Synergy/counter data computed once, shared across users
- **LLM Cost Optimization** - Cache identical recommendation requests
- **Hot Updates** - Algorithm changes without client releases

**Key insight from v1:** The problems weren't Java or JavaFX—they were architectural. A 4500-line controller class, tight coupling, and improper macOS packaging caused the issues. The fix is better architecture, not a different language.

This rebuild includes:
- **Desktop Client (JavaFX)** - UI, local draft simulation, offline cache
- **Backend Service (Spring Boot)** - Recommendations, LLM, authentication
- **Clean Architecture** - Proper separation of concerns on both client and server
- **Cross-platform packaging** - Using jpackage correctly for macOS, Windows, Linux

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              USER'S MACHINE                                          │
│  ┌───────────────────────────────────────────────────────────────────────────────┐  │
│  │                      DESKTOP CLIENT (JavaFX + Spring Core)                     │  │
│  │                                                                                │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │  │
│  │  │  Draft UI    │  │  Hero Grid   │  │  Analysis    │  │  Settings UI     │   │  │
│  │  │  Controller  │  │  Controller  │  │  Controller  │  │  Controller      │   │  │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────────┘   │  │
│  │         │                 │                 │                  │              │  │
│  │         └─────────────────┴─────────────────┴──────────────────┘              │  │
│  │                                    │                                          │  │
│  │                           ┌────────▼────────┐                                 │  │
│  │                           │  CLIENT SERVICES │                                │  │
│  │                           │  • DraftService  │                                │  │
│  │                           │  • HeroService   │                                │  │
│  │                           │  • CacheService  │                                │  │
│  │                           └────────┬────────┘                                 │  │
│  │                                    │                                          │  │
│  │         ┌──────────────────────────┼──────────────────────────┐               │  │
│  │         │                          │                          │               │  │
│  │  ┌──────▼──────┐           ┌───────▼───────┐          ┌───────▼───────┐      │  │
│  │  │ SQLite Cache│           │ BackendClient │          │ Domain Logic  │      │  │
│  │  │ (heroes,    │           │ (HTTP to      │          │ (DraftEngine, │      │  │
│  │  │  synergies) │           │  backend API) │          │  immutable)   │      │  │
│  │  └─────────────┘           └───────┬───────┘          └───────────────┘      │  │
│  │                                    │                                          │  │
│  └────────────────────────────────────┼──────────────────────────────────────────┘  │
└───────────────────────────────────────┼─────────────────────────────────────────────┘
                                        │ HTTPS
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           BACKEND SERVICE (Spring Boot)                              │
│                              (Railway / Fly.io)                                      │
│                                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │                            REST API LAYER                                    │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │    │
│  │  │ /recommend   │  │ /explain     │  │ /heroes      │  │ /auth        │     │    │
│  │  │ (scoring)    │  │ (Groq LLM)   │  │ (data)       │  │ (Steam)      │     │    │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │    │
│  │         └─────────────────┴─────────────────┴─────────────────┘             │    │
│  └─────────────────────────────────────┬───────────────────────────────────────┘    │
│                                        │                                             │
│  ┌─────────────────────────────────────▼───────────────────────────────────────┐    │
│  │                          APPLICATION SERVICES                                │    │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐   │    │
│  │  │ RecommendService │  │ ExplanationService│  │ SyncService              │   │    │
│  │  │ (scoring logic)  │  │ (prompt + cache)  │  │ (OpenDota fetch)         │   │    │
│  │  └────────┬─────────┘  └────────┬─────────┘  └────────────┬─────────────┘   │    │
│  └───────────┼─────────────────────┼─────────────────────────┼─────────────────┘    │
│              │                     │                         │                       │
│  ┌───────────▼─────────────────────▼─────────────────────────▼─────────────────┐    │
│  │                              INFRASTRUCTURE                                  │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │    │
│  │  │ PostgreSQL   │  │ Redis Cache  │  │ GroqClient   │  │ OpenDotaClient│    │    │
│  │  │ (hero data,  │  │ (LLM cache,  │  │ (LLM API)    │  │ (match data)  │    │    │
│  │  │  synergies)  │  │  rate limits)│  │              │  │               │    │    │
│  │  └──────────────┘  └──────────────┘  └──────┬───────┘  └───────┬───────┘    │    │
│  └─────────────────────────────────────────────┼──────────────────┼────────────┘    │
└────────────────────────────────────────────────┼──────────────────┼──────────────────┘
                                                 │                  │
                               ┌─────────────────┘                  └──────────────┐
                               ▼                                                   ▼
                        ┌──────────────┐                                   ┌──────────────┐
                        │   Groq API   │                                   │  OpenDota    │
                        │  (LLM)       │                                   │   API        │
                        └──────────────┘                                   └──────────────┘
```

---

## Technology Decision: Keep Java, Fix the Architecture

### Why NOT Switch Languages

| Alternative | Why Not |
|-------------|---------|
| Electron/Tauri | Web apps pretending to be desktop. Heavy, slow, wrong abstraction. |
| Rust | Massive rewrite for no clear benefit. Learning curve. Overkill. |
| C++/Qt | Complex, error-prone, slow iteration. |
| Python/PyQt | Distribution nightmare, performance issues. |
| C#/.NET MAUI | Microsoft ecosystem lock-in, immature on macOS/Linux. |

### Why Keep Java + JavaFX

1. **Existing code** - Domain logic (draft engine, AI, analysis) is already written and working
2. **True cross-platform** - JavaFX works on macOS, Windows, Linux when packaged correctly
3. **Performance** - JVM is fast; startup can be <3s with proper optimization
4. **Mature ecosystem** - Excellent libraries, tooling, IDE support
5. **Maintainability** - Large talent pool, well-understood patterns

### What Actually Needs to Change

| Problem | Solution |
|---------|----------|
| 4500-line MainController | Split into focused controllers (<200 lines each) |
| Tight coupling | Proper dependency injection with Spring |
| Mixed concerns | Clean Architecture layers |
| macOS packaging issues | Proper jpackage configuration, code signing |
| Slow startup | Lazy loading, optimized Spring context |
| Limited testing | Comprehensive unit/integration tests |

---

## Technology Stack

### Desktop Client Technologies

| Component | Technology | Version | Notes |
|-----------|------------|---------|-------|
| **Language** | Java | 21 (LTS) | Records, pattern matching, virtual threads |
| **UI Framework** | JavaFX | 21 | OpenJFX, properly modularized |
| **Build Tool** | Gradle | 8.x | Faster than Maven, better Kotlin DSL |
| **DI Framework** | Spring Core | 6.x | Lightweight (no Boot on client) |
| **Local Cache** | SQLite | 3.x | Via sqlite-jdbc, heroes + cached synergies |
| **HTTP Client** | Java HttpClient | Built-in | Modern, async, for backend API |
| **JSON** | Jackson | 2.16+ | Fast, mature, well-supported |
| **Logging** | SLF4J + Logback | 2.x | Standard, flexible |
| **Packaging** | jpackage | JDK 21 | Native installers for all platforms |

### Backend Service Technologies

| Component | Technology | Version | Notes |
|-----------|------------|---------|-------|
| **Language** | Java | 21 (LTS) | Same as client for code sharing |
| **Framework** | Spring Boot | 3.2+ | Full web stack, REST API |
| **Database** | PostgreSQL | 15+ | Hero data, synergies, player stats |
| **Cache** | Redis | 7.x | LLM response cache, rate limits |
| **HTTP Client** | Java HttpClient | Built-in | For Groq, OpenDota, Steam |
| **JSON** | Jackson | 2.16+ | API serialization |
| **Validation** | Jakarta Validation | 3.x | Request validation |
| **API Docs** | OpenAPI 3 | 3.x | Auto-generated from annotations |
| **Hosting** | Railway / Fly.io | - | Auto-scaling, free tier to start |

### Shared Domain Module

| Component | Notes |
|-----------|-------|
| **domain-model** | Hero, DraftState, Recommendation records |
| **domain-logic** | DraftEngine, scoring algorithms |
| | *Pure Java, no framework deps—used by both client and server* |

### Removed from v1

| Removed | Reason |
|---------|--------|
| HikariCP (client) | SQLite doesn't need connection pooling |
| Spring Security OAuth | Steam auth handled server-side |
| json-simple, org.json | Consolidate on Jackson only |
| OkHttp | Java HttpClient is sufficient |

---

## Architecture

### Client-Server Split

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DESKTOP CLIENT                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      PRESENTATION (UI)                                   │   │
│  │  JavaFX Controllers, FXML Views, CSS Styling                            │   │
│  │  Each controller <200 lines, single responsibility                      │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │                      CLIENT APPLICATION                                  │   │
│  │  DraftService (orchestrates), CacheService, BackendClient               │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │                      SHARED DOMAIN (Pure Java)                           │   │
│  │  DraftEngine, DraftState, Hero, Recommendation                          │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │                      CLIENT INFRASTRUCTURE                               │   │
│  │  SQLite (cache), HTTP Client (to backend)                               │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                       │ HTTPS
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              BACKEND SERVICE                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      REST API (Controllers)                              │   │
│  │  RecommendController, ExplainController, AuthController, HeroController │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │                      SERVER APPLICATION                                  │   │
│  │  RecommendationService, ExplanationService, SyncService, AuthService    │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │                      SHARED DOMAIN (Pure Java)                           │   │
│  │  RecommendationEngine, SynergyScorer, CounterScorer (same as client)    │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │                      SERVER INFRASTRUCTURE                               │   │
│  │  PostgreSQL, Redis, GroqClient, OpenDotaClient, SteamClient             │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Key Principle: Dependencies Point Inward

- Presentation depends on Application
- Application depends on Domain
- Infrastructure depends on Domain
- **Domain depends on NOTHING external**
- **Shared domain module** used by both client and server

### Package Structure

```
com.dota2assistant/
├── Dota2DraftAssistant.java       # Application entry point
├── config/
│   ├── AppConfig.java             # Spring configuration
│   ├── DatabaseConfig.java        # SQLite setup
│   └── ApiConfig.java             # HTTP clients
│
├── domain/                         # PURE BUSINESS LOGIC (no Spring)
│   ├── draft/
│   │   ├── DraftEngine.java       # Interface
│   │   ├── CaptainsModeDraft.java # Implementation
│   │   ├── AllPickDraft.java      # Implementation
│   │   ├── DraftState.java        # Immutable record
│   │   ├── DraftAction.java       # Immutable record
│   │   └── DraftPhase.java        # Enum
│   ├── recommendation/
│   │   ├── RecommendationEngine.java
│   │   ├── SynergyScorer.java
│   │   ├── CounterScorer.java
│   │   ├── RoleScorer.java
│   │   └── Recommendation.java    # Record
│   ├── analysis/
│   │   ├── AnalysisEngine.java
│   │   ├── TeamComposition.java
│   │   ├── WinProbability.java
│   │   └── DamageAnalysis.java
│   └── model/
│       ├── Hero.java              # Record
│       ├── Ability.java           # Record
│       ├── HeroAttributes.java    # Record
│       └── Team.java              # Enum
│
├── application/                    # USE CASES (Spring services)
│   ├── DraftService.java          # Coordinates draft operations
│   ├── HeroService.java           # Hero data access
│   ├── RecommendationService.java # Generates recommendations
│   ├── AnalysisService.java       # Team analysis
│   ├── AuthService.java           # Steam authentication
│   ├── SyncService.java           # Data synchronization
│   └── SettingsService.java       # User preferences
│
├── infrastructure/                 # EXTERNAL ADAPTERS
│   ├── persistence/
│   │   ├── SqliteHeroRepository.java
│   │   ├── SqliteMatchRepository.java
│   │   ├── SqliteSettingsRepository.java
│   │   └── DatabaseMigrator.java
│   ├── api/
│   │   ├── OpenDotaClient.java
│   │   ├── SteamAuthClient.java
│   │   └── GroqClient.java
│   └── storage/
│       └── SecureCredentialStore.java
│
├── ui/                             # JAVAFX PRESENTATION
│   ├── controller/
│   │   ├── MainController.java         # <100 lines, delegates to sub-controllers
│   │   ├── DraftController.java        # Draft board UI
│   │   ├── HeroGridController.java     # Hero selection grid
│   │   ├── TeamPanelController.java    # Team picks display
│   │   ├── RecommendationController.java
│   │   ├── AnalysisController.java
│   │   ├── SettingsController.java
│   │   └── LoginController.java
│   ├── component/
│   │   ├── HeroCard.java          # Reusable hero display
│   │   ├── TimerDisplay.java      # Draft timer
│   │   └── WinProbabilityBar.java
│   ├── view/
│   │   └── ViewFactory.java       # FXML loading
│   └── util/
│       └── FxUtils.java           # JavaFX helpers
│
└── util/
    ├── JsonUtils.java
    └── ImageCache.java
```

---

## Domain Layer (Core Business Logic)

### Draft Engine

```java
// domain/draft/DraftEngine.java
public interface DraftEngine {
    DraftState initDraft(DraftMode mode, boolean timerEnabled);
    DraftState pickHero(DraftState state, Hero hero);
    DraftState banHero(DraftState state, Hero hero);
    DraftState undo(DraftState state);
    boolean isComplete(DraftState state);
    Team getCurrentTeam(DraftState state);
    DraftPhase getCurrentPhase(DraftState state);
}

// domain/draft/DraftState.java - Immutable!
public record DraftState(
    DraftMode mode,
    DraftPhase phase,
    Team currentTeam,
    int turnIndex,
    List<Hero> radiantPicks,
    List<Hero> direPicks,
    List<Hero> radiantBans,
    List<Hero> direBans,
    List<Hero> availableHeroes,
    boolean timerEnabled,
    int remainingTime,
    int radiantReserveTime,
    int direReserveTime,
    List<DraftAction> history
) {
    // Immutable - all modifications return new instances
    public DraftState withPick(Team team, Hero hero) {
        var newPicks = team == Team.RADIANT 
            ? append(radiantPicks, hero) 
            : append(direPicks, hero);
        var newAvailable = remove(availableHeroes, hero);
        var newHistory = append(history, new DraftAction(ActionType.PICK, team, hero));
        
        return new DraftState(
            mode, phase, currentTeam, turnIndex + 1,
            team == Team.RADIANT ? newPicks : radiantPicks,
            team == Team.DIRE ? newPicks : direPicks,
            radiantBans, direBans, newAvailable,
            timerEnabled, remainingTime,
            radiantReserveTime, direReserveTime, newHistory
        );
    }
    
    private static <T> List<T> append(List<T> list, T item) {
        var result = new ArrayList<>(list);
        result.add(item);
        return List.copyOf(result);
    }
    
    private static <T> List<T> remove(List<T> list, T item) {
        return list.stream()
            .filter(i -> !i.equals(item))
            .toList();
    }
}
```

### Captain's Mode Implementation

```java
// domain/draft/CaptainsModeDraft.java
public class CaptainsModeDraft implements DraftEngine {
    
    // Captain's Mode sequence: 24 total actions
    private static final List<TurnInfo> SEQUENCE = List.of(
        // Ban Phase 1: ABBABBA (7 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_1, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_1, true),
        // Pick Phase 1: AB (2 picks)
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_1, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_1, false),
        // Ban Phase 2: AAB (3 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_2, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_2, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_2, true),
        // Pick Phase 2: BAABBA (6 picks)
        new TurnInfo(Team.DIRE, DraftPhase.PICK_2, false),
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_2, false),
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_2, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_2, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_2, false),
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_2, false),
        // Ban Phase 3: ABBA (4 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_3, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_3, true),
        new TurnInfo(Team.DIRE, DraftPhase.BAN_3, true),
        new TurnInfo(Team.RADIANT, DraftPhase.BAN_3, true),
        // Pick Phase 3: AB (2 picks)
        new TurnInfo(Team.RADIANT, DraftPhase.PICK_3, false),
        new TurnInfo(Team.DIRE, DraftPhase.PICK_3, false)
    );
    
    @Override
    public DraftState initDraft(DraftMode mode, boolean timerEnabled) {
        if (mode != DraftMode.CAPTAINS_MODE) {
            throw new IllegalArgumentException("Use CaptainsModeDraft for Captain's Mode only");
        }
        
        var firstTurn = SEQUENCE.get(0);
        return new DraftState(
            mode,
            firstTurn.phase(),
            firstTurn.team(),
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(), // Available heroes set by caller
            timerEnabled,
            firstTurn.isBan() ? 30 : 30,
            130,
            130,
            List.of()
        );
    }
    
    @Override
    public DraftState pickHero(DraftState state, Hero hero) {
        validateAction(state, hero, false);
        
        var currentTurn = SEQUENCE.get(state.turnIndex());
        var newState = state.withPick(currentTurn.team(), hero);
        
        return advanceToNextTurn(newState);
    }
    
    @Override
    public DraftState banHero(DraftState state, Hero hero) {
        validateAction(state, hero, true);
        
        var currentTurn = SEQUENCE.get(state.turnIndex());
        var newState = state.withBan(currentTurn.team(), hero);
        
        return advanceToNextTurn(newState);
    }
    
    @Override
    public DraftState undo(DraftState state) {
        if (state.history().isEmpty()) {
            throw new IllegalStateException("Nothing to undo");
        }
        
        var lastAction = state.history().getLast();
        // Reconstruct previous state from history
        // ...implementation...
    }
    
    private DraftState advanceToNextTurn(DraftState state) {
        int nextIndex = state.turnIndex() + 1;
        
        if (nextIndex >= SEQUENCE.size()) {
            return state.withPhase(DraftPhase.COMPLETED);
        }
        
        var nextTurn = SEQUENCE.get(nextIndex);
        return state
            .withTurnIndex(nextIndex)
            .withPhase(nextTurn.phase())
            .withCurrentTeam(nextTurn.team())
            .withRemainingTime(nextTurn.isBan() ? 30 : 30);
    }
    
    private void validateAction(DraftState state, Hero hero, boolean expectBan) {
        if (state.phase() == DraftPhase.COMPLETED) {
            throw new IllegalStateException("Draft is complete");
        }
        
        if (!state.availableHeroes().contains(hero)) {
            throw new IllegalArgumentException("Hero not available: " + hero.localizedName());
        }
        
        var currentTurn = SEQUENCE.get(state.turnIndex());
        if (currentTurn.isBan() != expectBan) {
            throw new IllegalStateException(
                expectBan ? "Cannot ban during pick phase" : "Cannot pick during ban phase"
            );
        }
    }
    
    private record TurnInfo(Team team, DraftPhase phase, boolean isBan) {}
}
```

### Recommendation Engine

```java
// domain/recommendation/RecommendationEngine.java
public class RecommendationEngine {
    
    private final SynergyScorer synergyScorer;
    private final CounterScorer counterScorer;
    private final RoleScorer roleScorer;
    private final MetaScorer metaScorer;
    
    public RecommendationEngine(
            SynergyScorer synergyScorer,
            CounterScorer counterScorer,
            RoleScorer roleScorer,
            MetaScorer metaScorer) {
        this.synergyScorer = synergyScorer;
        this.counterScorer = counterScorer;
        this.roleScorer = roleScorer;
        this.metaScorer = metaScorer;
    }
    
    public List<Recommendation> getRecommendations(
            DraftState state, 
            Team forTeam, 
            int count) {
        
        var allies = forTeam == Team.RADIANT ? state.radiantPicks() : state.direPicks();
        var enemies = forTeam == Team.RADIANT ? state.direPicks() : state.radiantPicks();
        
        return state.availableHeroes().stream()
            .map(hero -> scoreHero(hero, allies, enemies, state))
            .sorted(Comparator.comparingDouble(Recommendation::score).reversed())
            .limit(count)
            .toList();
    }
    
    private Recommendation scoreHero(
            Hero hero, 
            List<Hero> allies, 
            List<Hero> enemies,
            DraftState state) {
        
        var synergyScore = synergyScorer.score(hero, allies);
        var counterScore = counterScorer.score(hero, enemies);
        var roleScore = roleScorer.score(hero, allies, state.phase());
        var metaScore = metaScorer.score(hero);
        
        // Weighted combination
        double totalScore = 
            0.25 * synergyScore.value() +
            0.25 * counterScore.value() +
            0.30 * roleScore.value() +
            0.20 * metaScore.value();
        
        var reasons = List.of(synergyScore, counterScore, roleScore, metaScore);
        
        return new Recommendation(hero, totalScore, reasons, null);
    }
}

// domain/recommendation/Recommendation.java
public record Recommendation(
    Hero hero,
    double score,
    List<ScoreComponent> reasons,
    String llmExplanation  // Populated by LLM service if enabled
) {}

public record ScoreComponent(
    String type,      // "synergy", "counter", "role", "meta"
    double value,     // 0.0 to 1.0
    String description
) {}
```

---

## Application Layer (Use Cases)

```java
// application/DraftService.java
@Service
public class DraftService {
    
    private final DraftEngine captainsMode;
    private final DraftEngine allPick;
    private final HeroRepository heroRepository;
    
    private DraftState currentState;
    
    public DraftService(
            CaptainsModeDraft captainsMode,
            AllPickDraft allPick,
            HeroRepository heroRepository) {
        this.captainsMode = captainsMode;
        this.allPick = allPick;
        this.heroRepository = heroRepository;
    }
    
    public DraftState initDraft(DraftMode mode, boolean timerEnabled) {
        var engine = mode == DraftMode.CAPTAINS_MODE ? captainsMode : allPick;
        var heroes = heroRepository.findAll();
        
        currentState = engine.initDraft(mode, timerEnabled)
            .withAvailableHeroes(heroes);
        
        return currentState;
    }
    
    public DraftState pickHero(int heroId) {
        var hero = heroRepository.findById(heroId)
            .orElseThrow(() -> new IllegalArgumentException("Hero not found: " + heroId));
        
        var engine = getEngine(currentState.mode());
        currentState = engine.pickHero(currentState, hero);
        
        return currentState;
    }
    
    public DraftState banHero(int heroId) {
        var hero = heroRepository.findById(heroId)
            .orElseThrow(() -> new IllegalArgumentException("Hero not found: " + heroId));
        
        var engine = getEngine(currentState.mode());
        currentState = engine.banHero(currentState, hero);
        
        return currentState;
    }
    
    public DraftState undo() {
        var engine = getEngine(currentState.mode());
        currentState = engine.undo(currentState);
        return currentState;
    }
    
    public DraftState getCurrentState() {
        return currentState;
    }
    
    private DraftEngine getEngine(DraftMode mode) {
        return mode == DraftMode.CAPTAINS_MODE ? captainsMode : allPick;
    }
}
```

---

## Infrastructure Layer

### SQLite Repository

```java
// infrastructure/persistence/SqliteHeroRepository.java
@Repository
public class SqliteHeroRepository implements HeroRepository {
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    public SqliteHeroRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public List<Hero> findAll() {
        String sql = """
            SELECT id, name, localized_name, primary_attr, attack_type,
                   roles, positions, attributes, image_url, icon_url
            FROM heroes
            ORDER BY localized_name
            """;
        
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            
            var heroes = new ArrayList<Hero>();
            while (rs.next()) {
                heroes.add(mapRow(rs));
            }
            return heroes;
            
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load heroes", e);
        }
    }
    
    @Override
    public Optional<Hero> findById(int id) {
        String sql = "SELECT * FROM heroes WHERE id = ?";
        
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find hero: " + id, e);
        }
    }
    
    private Hero mapRow(ResultSet rs) throws SQLException {
        try {
            return new Hero(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("localized_name"),
                Attribute.valueOf(rs.getString("primary_attr").toUpperCase()),
                AttackType.valueOf(rs.getString("attack_type").toUpperCase()),
                objectMapper.readValue(rs.getString("roles"), new TypeReference<List<String>>() {}),
                objectMapper.readValue(rs.getString("positions"), new TypeReference<Map<Integer, Double>>() {}),
                objectMapper.readValue(rs.getString("attributes"), HeroAttributes.class),
                rs.getString("image_url"),
                rs.getString("icon_url"),
                List.of() // Abilities loaded separately if needed
            );
        } catch (JsonProcessingException e) {
            throw new RepositoryException("Failed to parse hero JSON", e);
        }
    }
}
```

### Groq LLM Client

```java
// infrastructure/api/GroqClient.java
@Component
public class GroqClient {
    
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    
    public GroqClient(
            @Value("${groq.api.key:}") String apiKey,
            @Value("${groq.api.model:llama3-70b-8192}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
    
    public CompletableFuture<String> generateExplanation(String prompt) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        
        var requestBody = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", 
                    "You are an expert Dota 2 strategist. Be concise but thorough."),
                Map.of("role", "user", "content", prompt)
            ),
            "max_tokens", 500,
            "temperature", 0.7
        );
        
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(15))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ApiException("Groq API error: " + response.statusCode());
                    }
                    return extractContent(response.body());
                });
                
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private String extractContent(String responseBody) {
        try {
            var root = objectMapper.readTree(responseBody);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (JsonProcessingException e) {
            throw new ApiException("Failed to parse Groq response", e);
        }
    }
}
```

---

## Backend REST API

### API Contracts

```yaml
openapi: 3.0.3
info:
  title: Dota 2 Draft Assistant API
  version: 1.0.0

paths:
  /api/v1/recommend:
    post:
      summary: Get hero recommendations
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RecommendRequest'
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RecommendResponse'

  /api/v1/explain:
    post:
      summary: Get LLM explanation for a recommendation
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ExplainRequest'
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ExplainResponse'

components:
  schemas:
    RecommendRequest:
      type: object
      properties:
        radiantPicks:
          type: array
          items:
            type: integer
        direPicks:
          type: array
          items:
            type: integer
        radiantBans:
          type: array
          items:
            type: integer
        direBans:
          type: array
          items:
            type: integer
        forTeam:
          type: string
          enum: [RADIANT, DIRE]
        phase:
          type: string
          enum: [BAN_1, PICK_1, BAN_2, PICK_2, BAN_3, PICK_3]
        playerAccountId:
          type: integer
          description: Optional, for personalized recommendations

    RecommendResponse:
      type: object
      properties:
        recommendations:
          type: array
          items:
            $ref: '#/components/schemas/Recommendation'
        winProbability:
          type: number
        cached:
          type: boolean

    Recommendation:
      type: object
      properties:
        heroId:
          type: integer
        score:
          type: number
        reasons:
          type: array
          items:
            $ref: '#/components/schemas/ScoreComponent'

    ScoreComponent:
      type: object
      properties:
        type:
          type: string
          enum: [synergy, counter, role, meta, personal]
        value:
          type: number
        description:
          type: string

    ExplainRequest:
      type: object
      properties:
        heroId:
          type: integer
        radiantPicks:
          type: array
          items:
            type: integer
        direPicks:
          type: array
          items:
            type: integer
        forTeam:
          type: string
          enum: [RADIANT, DIRE]

    ExplainResponse:
      type: object
      properties:
        explanation:
          type: string
        cached:
          type: boolean
```

### Backend Controller Example

```java
// backend/controller/RecommendController.java
@RestController
@RequestMapping("/api/v1")
public class RecommendController {
    
    private final RecommendationService recommendationService;
    
    @PostMapping("/recommend")
    public ResponseEntity<RecommendResponse> recommend(
            @Valid @RequestBody RecommendRequest request) {
        
        var state = buildDraftState(request);
        var recommendations = recommendationService.getRecommendations(
            state, request.getForTeam(), 5);
        var winProb = recommendationService.calculateWinProbability(state);
        
        return ResponseEntity.ok(new RecommendResponse(
            recommendations, winProb, false
        ));
    }
    
    @PostMapping("/explain")
    public ResponseEntity<ExplainResponse> explain(
            @Valid @RequestBody ExplainRequest request) {
        
        var explanation = recommendationService.explainPick(
            request.getHeroId(),
            request.getRadiantPicks(),
            request.getDirePicks(),
            request.getForTeam()
        );
        
        return ResponseEntity.ok(new ExplainResponse(
            explanation.text(), explanation.cached()
        ));
    }
}
```

### Client Backend Integration

```java
// client/infrastructure/BackendClient.java
@Component
public class BackendClient {
    
    private static final String BASE_URL = "https://api.dota2assistant.com";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public CompletableFuture<RecommendResponse> getRecommendations(
            DraftState state, Team forTeam) {
        
        var request = new RecommendRequest(
            state.radiantPicks().stream().map(Hero::id).toList(),
            state.direPicks().stream().map(Hero::id).toList(),
            state.radiantBans().stream().map(Hero::id).toList(),
            state.direBans().stream().map(Hero::id).toList(),
            forTeam,
            state.phase(),
            null  // Optional player ID
        );
        
        return sendAsync("/api/v1/recommend", request, RecommendResponse.class);
    }
    
    public CompletableFuture<String> getExplanation(
            int heroId, DraftState state, Team forTeam) {
        
        var request = new ExplainRequest(
            heroId,
            state.radiantPicks().stream().map(Hero::id).toList(),
            state.direPicks().stream().map(Hero::id).toList(),
            forTeam
        );
        
        return sendAsync("/api/v1/explain", request, ExplainResponse.class)
            .thenApply(ExplainResponse::explanation);
    }
    
    private <T, R> CompletableFuture<R> sendAsync(
            String path, T body, Class<R> responseType) {
        
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(10))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ApiException("Backend", response.statusCode(), 
                            response.body());
                    }
                    try {
                        return objectMapper.readValue(response.body(), responseType);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
```

### Offline Fallback

```java
// client/application/RecommendationService.java
@Service
public class RecommendationService {
    
    private final BackendClient backendClient;
    private final LocalRecommendationEngine localEngine;  // Offline fallback
    private final CacheService cacheService;
    
    public CompletableFuture<List<Recommendation>> getRecommendations(
            DraftState state, Team forTeam) {
        
        // Try backend first
        return backendClient.getRecommendations(state, forTeam)
            .thenApply(RecommendResponse::recommendations)
            .exceptionally(ex -> {
                log.warn("Backend unavailable, using local fallback", ex);
                // Fall back to local scoring with cached synergy data
                return localEngine.getRecommendations(state, forTeam, 5);
            });
    }
}
```

---

## UI Layer

### Controller Decomposition

**Before (v1):** One 4500-line `MainController.java`

**After (v3):** Multiple focused controllers

```java
// ui/controller/MainController.java - Now <100 lines!
@Component
public class MainController {
    
    @FXML private TabPane mainTabPane;
    @FXML private BorderPane draftPane;
    @FXML private VBox settingsPane;
    
    private final DraftController draftController;
    private final SettingsController settingsController;
    private final LoginController loginController;
    
    public MainController(
            DraftController draftController,
            SettingsController settingsController,
            LoginController loginController) {
        this.draftController = draftController;
        this.settingsController = settingsController;
        this.loginController = loginController;
    }
    
    @FXML
    public void initialize() {
        // Wire up sub-controllers
        draftController.setContainer(draftPane);
        settingsController.setContainer(settingsPane);
        
        // Set up tab change listeners
        mainTabPane.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldTab, newTab) -> onTabChanged(newTab));
    }
    
    private void onTabChanged(Tab tab) {
        // Handle tab switching
    }
}
```

```java
// ui/controller/DraftController.java - ~200 lines
@Component
public class DraftController {
    
    private final DraftService draftService;
    private final RecommendationService recommendationService;
    private final HeroGridController heroGridController;
    private final TeamPanelController radiantPanel;
    private final TeamPanelController direPanel;
    private final RecommendationController recommendationController;
    
    @FXML private BorderPane container;
    @FXML private Label phaseLabel;
    @FXML private Label timerLabel;
    @FXML private Button undoButton;
    
    private Team playerTeam = Team.RADIANT;
    
    public DraftController(
            DraftService draftService,
            RecommendationService recommendationService,
            HeroGridController heroGridController,
            TeamPanelController radiantPanel,
            TeamPanelController direPanel,
            RecommendationController recommendationController) {
        this.draftService = draftService;
        this.recommendationService = recommendationService;
        this.heroGridController = heroGridController;
        this.radiantPanel = radiantPanel;
        this.direPanel = direPanel;
        this.recommendationController = recommendationController;
    }
    
    @FXML
    public void initialize() {
        heroGridController.setOnHeroSelected(this::onHeroSelected);
        undoButton.setOnAction(e -> onUndo());
    }
    
    public void startNewDraft(DraftMode mode, boolean timerEnabled) {
        var state = draftService.initDraft(mode, timerEnabled);
        updateUI(state);
        updateRecommendations();
    }
    
    private void onHeroSelected(Hero hero) {
        var state = draftService.getCurrentState();
        if (!isPlayerTurn(state)) return;
        
        try {
            DraftState newState;
            if (isBanPhase(state)) {
                newState = draftService.banHero(hero.id());
            } else {
                newState = draftService.pickHero(hero.id());
            }
            updateUI(newState);
            updateRecommendations();
        } catch (Exception e) {
            showError("Invalid action: " + e.getMessage());
        }
    }
    
    private void onUndo() {
        try {
            var state = draftService.undo();
            updateUI(state);
            updateRecommendations();
        } catch (Exception e) {
            showError("Cannot undo: " + e.getMessage());
        }
    }
    
    private void updateUI(DraftState state) {
        Platform.runLater(() -> {
            phaseLabel.setText(formatPhase(state.phase()));
            timerLabel.setText(formatTime(state.remainingTime()));
            undoButton.setDisable(state.history().isEmpty());
            
            radiantPanel.update(state.radiantPicks(), state.radiantBans());
            direPanel.update(state.direPicks(), state.direBans());
            heroGridController.update(state.availableHeroes());
        });
    }
    
    private void updateRecommendations() {
        var state = draftService.getCurrentState();
        recommendationService.getRecommendations(state, playerTeam, 5)
            .thenAccept(recs -> Platform.runLater(() -> 
                recommendationController.update(recs)));
    }
    
    private boolean isPlayerTurn(DraftState state) {
        return state.currentTeam() == playerTeam;
    }
    
    private boolean isBanPhase(DraftState state) {
        return state.phase().name().startsWith("BAN");
    }
}
```

```java
// ui/controller/HeroGridController.java - ~150 lines
@Component
public class HeroGridController {
    
    @FXML private GridPane heroGrid;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterCombo;
    
    private Consumer<Hero> onHeroSelected;
    private List<Hero> allHeroes = List.of();
    private Set<Integer> highlightedHeroIds = Set.of();
    
    @FXML
    public void initialize() {
        searchField.textProperty().addListener((obs, old, newVal) -> filterHeroes());
        filterCombo.setOnAction(e -> filterHeroes());
    }
    
    public void setOnHeroSelected(Consumer<Hero> handler) {
        this.onHeroSelected = handler;
    }
    
    public void update(List<Hero> availableHeroes) {
        this.allHeroes = availableHeroes;
        filterHeroes();
    }
    
    public void setHighlightedHeroes(Set<Integer> heroIds) {
        this.highlightedHeroIds = heroIds;
        filterHeroes();
    }
    
    private void filterHeroes() {
        var searchTerm = searchField.getText().toLowerCase();
        var roleFilter = filterCombo.getValue();
        
        var filtered = allHeroes.stream()
            .filter(h -> searchTerm.isEmpty() || 
                h.localizedName().toLowerCase().contains(searchTerm))
            .filter(h -> roleFilter == null || "All".equals(roleFilter) ||
                h.roles().contains(roleFilter))
            .toList();
        
        renderGrid(filtered);
    }
    
    private void renderGrid(List<Hero> heroes) {
        heroGrid.getChildren().clear();
        
        int col = 0, row = 0;
        for (var hero : heroes) {
            var card = createHeroCard(hero);
            heroGrid.add(card, col, row);
            
            col++;
            if (col >= 12) {
                col = 0;
                row++;
            }
        }
    }
    
    private Node createHeroCard(Hero hero) {
        var card = new HeroCard(hero);
        card.setHighlighted(highlightedHeroIds.contains(hero.id()));
        card.setOnMouseClicked(e -> {
            if (onHeroSelected != null) {
                onHeroSelected.accept(hero);
            }
        });
        return card;
    }
}
```

---

## Build & Packaging

### Gradle Configuration

```kotlin
// build.gradle.kts
plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

application {
    mainClass = "com.dota2assistant.Dota2DraftAssistant"
    mainModule = "com.dota2assistant"
}

dependencies {
    // Spring Boot (lightweight)
    implementation("org.springframework.boot:spring-boot-starter:3.2.0") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.0")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

jlink {
    options = listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
    
    launcher {
        name = "Dota2DraftAssistant"
    }
    
    jpackage {
        imageName = "Dota2DraftAssistant"
        installerName = "Dota2DraftAssistant"
        appVersion = project.version.toString()
        vendor = "Dota2Assistant"
        
        // Windows
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            installerType = "msi"
            installerOptions = listOf(
                "--win-dir-chooser",
                "--win-menu",
                "--win-shortcut"
            )
        }
        
        // macOS
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            installerType = "dmg"
            installerOptions = listOf(
                "--mac-package-name", "Dota2DraftAssistant"
            )
        }
        
        // Linux
        if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
            installerType = "deb"
            installerOptions = listOf(
                "--linux-shortcut"
            )
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
```

### Cross-Platform Build Commands

```bash
# Development
./gradlew run

# Run tests
./gradlew test

# Build native package for current platform
./gradlew jpackage

# Build outputs:
# Windows: build/jpackage/Dota2DraftAssistant-1.0.0.msi
# macOS:   build/jpackage/Dota2DraftAssistant-1.0.0.dmg
# Linux:   build/jpackage/dota2draftassistant_1.0.0_amd64.deb
```

### CI/CD (GitHub Actions)

```yaml
# .github/workflows/build.yml
name: Build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      - name: Test
        run: ./gradlew test

  build:
    needs: test
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      - name: Build
        run: ./gradlew jpackage
      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: package-${{ matrix.os }}
          path: build/jpackage/*
```

---

## Testing Strategy

### Unit Tests (Domain Layer)

```java
// test/java/com/dota2assistant/domain/draft/CaptainsModeDraftTest.java
class CaptainsModeDraftTest {
    
    private CaptainsModeDraft engine;
    private List<Hero> testHeroes;
    
    @BeforeEach
    void setUp() {
        engine = new CaptainsModeDraft();
        testHeroes = createTestHeroes(30);
    }
    
    @Test
    void initDraft_setsCorrectInitialState() {
        var state = engine.initDraft(DraftMode.CAPTAINS_MODE, false)
            .withAvailableHeroes(testHeroes);
        
        assertThat(state.phase()).isEqualTo(DraftPhase.BAN_1);
        assertThat(state.currentTeam()).isEqualTo(Team.RADIANT);
        assertThat(state.turnIndex()).isEqualTo(0);
        assertThat(state.radiantPicks()).isEmpty();
        assertThat(state.direPicks()).isEmpty();
    }
    
    @Test
    void banHero_removesFromAvailable() {
        var state = engine.initDraft(DraftMode.CAPTAINS_MODE, false)
            .withAvailableHeroes(testHeroes);
        var heroToBan = testHeroes.get(0);
        
        var newState = engine.banHero(state, heroToBan);
        
        assertThat(newState.availableHeroes()).doesNotContain(heroToBan);
        assertThat(newState.radiantBans()).contains(heroToBan);
    }
    
    @Test
    void banHero_duringPickPhase_throwsException() {
        var state = engine.initDraft(DraftMode.CAPTAINS_MODE, false)
            .withAvailableHeroes(testHeroes)
            .withPhase(DraftPhase.PICK_1)
            .withTurnIndex(7);
        
        assertThatThrownBy(() -> engine.banHero(state, testHeroes.get(0)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot ban during pick phase");
    }
    
    @Test
    void fullDraftSequence_completesCorrectly() {
        var state = engine.initDraft(DraftMode.CAPTAINS_MODE, false)
            .withAvailableHeroes(testHeroes);
        
        // Ban phase 1: 7 bans
        for (int i = 0; i < 7; i++) {
            var hero = state.availableHeroes().get(0);
            state = engine.banHero(state, hero);
        }
        assertThat(state.phase()).isEqualTo(DraftPhase.PICK_1);
        
        // Pick phase 1: 2 picks
        for (int i = 0; i < 2; i++) {
            var hero = state.availableHeroes().get(0);
            state = engine.pickHero(state, hero);
        }
        assertThat(state.phase()).isEqualTo(DraftPhase.BAN_2);
        
        // Continue through remaining phases...
        // (abbreviated for brevity)
        
        // After 24 total actions, draft should be complete
        assertThat(state.radiantPicks()).hasSize(5);
        assertThat(state.direPicks()).hasSize(5);
        assertThat(state.radiantBans()).hasSize(7);
        assertThat(state.direBans()).hasSize(7);
    }
    
    @Test
    void undo_restoresPreviousState() {
        var state = engine.initDraft(DraftMode.CAPTAINS_MODE, false)
            .withAvailableHeroes(testHeroes);
        var originalAvailable = state.availableHeroes().size();
        
        var heroToBan = testHeroes.get(0);
        state = engine.banHero(state, heroToBan);
        assertThat(state.availableHeroes()).hasSize(originalAvailable - 1);
        
        state = engine.undo(state);
        assertThat(state.availableHeroes()).hasSize(originalAvailable);
        assertThat(state.availableHeroes()).contains(heroToBan);
    }
    
    private List<Hero> createTestHeroes(int count) {
        return IntStream.range(1, count + 1)
            .mapToObj(i -> new Hero(
                i, "hero_" + i, "Hero " + i,
                Attribute.STRENGTH, AttackType.MELEE,
                List.of("Carry"), Map.of(1, 0.5),
                new HeroAttributes(20, 20, 20, 2.0, 2.0, 2.0, 300, 0, 50, 60, 150, 1.7),
                "", "", List.of()
            ))
            .toList();
    }
}
```

### Integration Tests

```java
// test/java/com/dota2assistant/integration/DraftIntegrationTest.java
@SpringBootTest
class DraftIntegrationTest {
    
    @Autowired
    private DraftService draftService;
    
    @Autowired
    private HeroRepository heroRepository;
    
    @Test
    void completeDraft_persistsAndLoadsCorrectly() {
        // Initialize draft
        var state = draftService.initDraft(DraftMode.CAPTAINS_MODE, false);
        
        // Verify heroes loaded from database
        assertThat(state.availableHeroes()).hasSizeGreaterThan(100);
        
        // Make some picks/bans
        draftService.banHero(1);
        draftService.banHero(2);
        
        var finalState = draftService.getCurrentState();
        assertThat(finalState.radiantBans()).hasSize(1);
        assertThat(finalState.direBans()).hasSize(1);
    }
}
```

---

## Migration from v1

### What to Keep

1. **Database schema** - Reuse existing SQLite schema
2. **Hero data** - All JSON files in resources/data/
3. **Hero images** - All images in resources/images/heroes/
4. **Synergy/counter data** - Computed matrices
5. **Match data** - If useful for analysis

### What to Rewrite

1. **Controllers** - Split into focused components
2. **Domain logic** - Make immutable, pure functions
3. **Services** - Clean interfaces, testable
4. **Configuration** - Gradle instead of Maven

### Migration Steps

1. Set up new Gradle project structure
2. Port domain models as records
3. Implement domain logic with tests
4. Port repositories to new interfaces
5. Build UI controllers incrementally
6. Test on macOS, Windows, Linux
7. Package with jpackage

---

## Summary

The v3.1 architecture keeps Java and JavaFX but introduces a proper client-server split:

| v1 Problem | v3.1 Solution |
|------------|-------------|
| 4500-line MainController | Split into 8+ focused controllers (<200 lines each) |
| Mutable state everywhere | Immutable records for domain models |
| Tight coupling | Clean Architecture layers with interfaces |
| No tests | Comprehensive unit and integration tests |
| Maven complexity | Gradle with cleaner DSL |
| macOS packaging issues | Proper jpackage configuration |
| API keys in client binary | Backend service holds all secrets |
| No shared data/caching | Redis cache for LLM responses, synergies |
| Direct Groq calls from client | Backend proxies with rate limiting |
| SQLite for everything | SQLite (client cache) + PostgreSQL (backend) |

**Key Architecture Changes in v3.1:**
- Client handles UI, draft simulation, offline fallback
- Backend handles recommendations, LLM, authentication, data sync
- Shared domain module for code reuse
- Offline mode gracefully degrades to cached data

**The language wasn't the problem. The architecture was.**

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 3.0 | Dec 4, 2025 | Initial Clean Architecture with embedded design |
| 3.1 | Dec 4, 2025 | **Client-server pivot**: Added backend service, API contracts, offline fallback |

---

*This TDD is the technical companion to PRD_v3.md. Update as implementation progresses.*
