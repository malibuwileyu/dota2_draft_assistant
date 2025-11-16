# Dota 2 Draft Assistant - Technical Design Document

## 1. Architecture Overview

### 1.1 System Architecture
The application will follow a Model-View-Controller (MVC) architecture pattern:
- **Model**: Core data structures and business logic
- **View**: JavaFX UI components
- **Controller**: Handles user input and updates model/view

### 1.2 Key Components
- **Core**: Business logic, draft rules, and AI opponent
- **Data**: API integration, local database, and data processing
- **UI**: JavaFX interface and overlay
- **Utils**: Helper utilities for common operations

## 2. Technology Stack

- **Language**: Java 17 (LTS)
- **UI Framework**: JavaFX 17
- **Build Tool**: Maven
- **Database**: SQLite (local data storage)
- **HTTP Client**: OkHttp (for API calls)
- **JSON Processing**: Jackson
- **Logging**: SLF4J with Logback
- **Testing**: JUnit 5, Mockito
- **Dependency Injection**: Spring Core (lightweight)

## 3. Package Structure

```
com.dota2assistant/
├── core/
│   ├── draft/         # Draft logic and rules
│   ├── ai/            # AI opponent implementation
│   └── analysis/      # Draft analysis algorithms
├── data/
│   ├── api/           # API integration (OpenDota)
│   ├── db/            # Local database operations
│   ├── model/         # Data transfer objects
│   └── repository/    # Data access layer
├── ui/
│   ├── controller/    # UI controllers
│   ├── view/          # JavaFX views
│   ├── component/     # Reusable UI components
│   └── overlay/       # Game overlay implementation
└── util/              # Utility classes
```

## 4. Core Components Design

### 4.1 Hero Data Model
```java
public class Hero {
    private int id;
    private String name;
    private String localizedName;
    private List<String> roles;
    private Map<Integer, Double> roleFrequency; // position -> frequency
    private HeroAttributes attributes;
    private List<Ability> abilities;
    // Methods and additional properties...
}
```

### 4.2 Draft Engine
- Manages the draft state and enforces game rules
- Tracks hero selections, bans, and turn order
- Implements different draft modes (Captain's Mode, All Pick)
- Provides timer functionality for timed mode

### 4.3 AI Decision Engine
- Utilizes hero synergy matrices and counter data
- Implements different drafting strategies based on rank
- Calculates optimal picks/bans based on current draft state
- Provides reasoning for AI decisions

### 4.4 Analysis Engine
- Evaluates team composition strengths/weaknesses
- Calculates win probability based on hero selections
- Identifies timing windows for each team
- Provides strategic feedback on draft decisions

## 5. Data Management

### 5.1 Local Database Schema
- **Heroes**: Basic hero information
- **Abilities**: Hero abilities data
- **Synergies**: Hero synergy scores
- **Counters**: Hero counter relationships
- **Matches**: Recent match data for analysis
- **Settings**: Application settings and user preferences

### 5.2 Data Update Process
- Scheduled daily updates for meta data
- Accelerated updates after patches/tournaments
- Background processing to minimize UI impact
- Caching to improve performance

### 5.3 External API Integration
- OpenDota API for match data and statistics
- Rate limiting and error handling
- Data transformation and normalization
- Fallback mechanisms for API unavailability

## 6. User Interface Design

### 6.1 Main Application Interface
- Hero grid with filtering capabilities
- Draft timeline showing picks/bans
- Team composition panel with synergy visualization
- Suggestions panel with reasoning
- Draft strength comparison indicator

### 6.2 Game Overlay
- Transparent overlay that appears when Dota 2 is running
- Compact suggestion display
- Minimal visual footprint during actual gameplay
- Hotkeys for showing/hiding overlay elements
- System tray integration for quick access

## 7. Cross-cutting Concerns

### 7.1 Performance Considerations
- Asynchronous processing for API calls and data analysis
- Efficient rendering for overlay with minimal game impact
- Memory management for large datasets
- Background thread management

### 7.2 Error Handling and Logging
- Comprehensive exception handling
- Structured logging with different severity levels
- User-friendly error messages
- Telemetry for application health monitoring

### 7.3 Security
- Secure storage of API keys
- Data validation for all external inputs
- Protection against injection attacks in database queries
- Safe handling of game process interaction

## 8. Testing Strategy

### 8.1 Unit Testing
- Core logic components (draft rules, AI decisions)
- Data processing and transformation
- Utility functions

### 8.2 Integration Testing
- API communication
- Database operations
- Component interactions

### 8.3 UI Testing
- JavaFX component tests
- User flow validations

## 9. Deployment and Packaging

### 9.1 Application Packaging
- JavaFX jlink and jpackage for native application bundles
- Windows installer (.msi) creation
- Automatic update mechanism
- Splash screen and application icons

### 9.2 System Requirements
- Windows 10 or higher
- Java Runtime Environment (bundled)
- Minimum 4GB RAM
- 500MB disk space

## 10. Development Roadmap

### 10.1 Phase 1: MVP
- Basic draft simulation (Captain's Mode)
- Simple UI with hero grid
- Local hero database
- Basic AI opponent

### 10.2 Phase 2: Enhanced Features
- All Pick draft mode
- Advanced AI with improved decision making
- Data-driven suggestions and analysis
- Post-draft feedback

### 10.3 Phase 3: Overlay Integration
- Game detection and overlay implementation
- Real-time draft assistance
- Performance optimizations
- Advanced team composition analysis