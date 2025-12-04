# Dota 2 Draft Assistant

A cross-platform desktop application that provides **AI-powered draft recommendations** for Dota 2 players. Unlike tools that only show win rates, this assistant explains *why* a pick is good using LLM-powered analysis.

## Features

- **Draft Simulation**: Complete Captain's Mode and All Pick draft sequences
- **AI Recommendations**: LLM-powered explanations via Groq API (with offline fallback)
- **Hero Database**: 124+ heroes with abilities, roles, and position data—works offline
- **Team Analysis**: Damage type balance, disable assessment, power spike timing
- **Personalized**: Recommendations weighted by your hero pool (via Steam login)
- **Cross-Platform**: Native installers for macOS, Windows, and Linux

## Current Status

> **⚠️ Architecture Rebuild in Progress**
> 
> This project is being rebuilt from v1 (problematic architecture) to v3 (Clean Architecture).
> See `docs/PRD_v3.md` and `docs/TDD_v3.md` for the planned architecture.

### What's Changing (v1 → v3)

| Aspect | v1 (Current) | v3 (Planned) |
|--------|--------------|--------------|
| Java Version | 17 | 21 (LTS) |
| Build Tool | Maven | Gradle |
| Database | PostgreSQL + SQLite | SQLite only |
| Architecture | Monolithic (4500-line controller) | Clean Architecture |
| Startup Time | ~10s | <3s target |
| Test Coverage | Limited | 80%+ target |

## Technology Stack (v3)

- **Java 21** - Records, pattern matching, virtual threads
- **JavaFX 21** - Cross-platform UI
- **Gradle** - Build tool
- **Spring Boot 3.2** - Lightweight DI
- **SQLite** - Embedded database
- **Groq API** - LLM recommendations
- **OpenDota API** - Match data
- **Steam Web API** - Authentication

## Getting Started

### Prerequisites

- Java 21 or higher ([Temurin](https://adoptium.net/) recommended)
- Gradle 8.x (or use included wrapper)

### Installation (v3 - Coming Soon)

```bash
# Clone the repository
git clone https://github.com/malibuwileyu/dota2_draft_assistant.git
cd dota2_draft_assistant

# Build the project
./gradlew build

# Run the application
./gradlew run

# Create native installer for your platform
./gradlew jpackage
```

### Installation (v1 - Current)

```bash
# Build with Maven
mvn clean install

# Run
mvn javafx:run
```

## Configuration

Create `application.properties.override` in the project root:

```properties
# Groq API (for AI explanations)
groq.api.enabled=true
groq.api.key=YOUR_GROQ_API_KEY
groq.api.model=llama3-70b-8192

# Steam Authentication (optional, for personalization)
steam.api.key=YOUR_STEAM_API_KEY

# Database
database.type=sqlite
database.file=dota2assistant.db
```

## Project Structure (v3)

```
com.dota2assistant/
├── config/              # Spring configuration
├── domain/              # Pure business logic (no framework deps)
│   ├── draft/           # Draft engine, state machine
│   ├── recommendation/  # Scoring algorithms
│   └── analysis/        # Team composition analysis
├── application/         # Use cases / services
├── infrastructure/      # External adapters
│   ├── persistence/     # SQLite repositories
│   └── api/             # OpenDota, Steam, Groq clients
└── ui/                  # JavaFX controllers and views
    ├── controller/      # <200 lines each
    └── component/       # Reusable UI components
```

## Documentation

Documentation is stored locally in `docs/` (not tracked in git):

- **PRD_v3.md** - Product Requirements (what to build, traceable requirements)
- **TDD_v3.md** - Technical Design (how to build it)

## Development

### Running Tests

```bash
# v3 (Gradle)
./gradlew test

# v1 (Maven)
mvn test
```

### Code Quality Targets

- No class >200 lines
- Domain layer has zero framework dependencies
- 80%+ test coverage on domain logic
- All requirements traceable via REQ-XXX IDs

## Roadmap

| Phase | Timeline | Focus |
|-------|----------|-------|
| **Alpha** | Feb 2025 | Core draft simulation, local recommendations |
| **Beta** | Apr 2025 | Groq LLM, Steam auth, personalization |
| **GA v1.0** | Jun 2025 | All P0/P1 features, cross-platform installers |

See `docs/PRD_v3.md` for detailed requirements and `docs/TDD_v3.md` for technical specs.

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/REQ-XXX-description`)
3. Follow Clean Architecture layers
4. Ensure tests pass and coverage maintained
5. Open a Pull Request referencing the requirement ID

## License

This project is licensed under the MIT License.

## Acknowledgments

- [OpenDota API](https://docs.opendota.com/) - Match data and statistics
- [Groq](https://groq.com/) - Fast LLM inference
- [Dota 2 Wiki](https://dota2.fandom.com/) - Ability data reference
