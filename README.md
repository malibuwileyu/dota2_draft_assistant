# Dota 2 Draft Assistant

A desktop application that simulates Dota 2's drafting process with an AI opponent, helping players practice and improve their drafting skills.

![Dota 2 Draft Assistant](docs/screenshots/app_screenshot.png)

## Features

- **Draft Simulation**: Complete implementation of Captain's Mode and All Pick drafting formats
- **AI Opponent**: Meta-driven drafting decisions with hero synergy and counter picking
- **Hero Analysis**: Access to complete hero data, role flexibility, and team composition analysis
- **Draft Insights**: Detailed analysis of team composition strengths and weaknesses
- **Learning Tools**: Post-draft feedback and strategic suggestions
- **Overlay Mode**: Future feature to provide assistance during real Dota 2 drafts

## Technology Stack

- **Java 17** - Core programming language
- **JavaFX** - UI framework
- **Spring Core** - Dependency injection
- **SQLite** - Local data storage
- **OpenDota API** - Game data source

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Installation

1. Clone the repository
   ```bash
   git clone https://github.com/yourusername/dota2-draft-assistant.git
   cd dota2-draft-assistant
   ```

2. Build the project
   ```bash
   mvn clean install
   ```

3. Run the application
   ```bash
   mvn javafx:run
   ```

### Creating a Native Package

Create a native package for your platform:

```bash
mvn javafx:jlink jpackage
```

## Configuration

Edit `src/main/resources/application.properties` to customize:

- API settings
- Default rank for statistics
- AI difficulty level
- Draft timer settings

## Project Structure

```
com.dota2assistant/
├── core/             # Core business logic
│   ├── draft/        # Draft rules and mechanics
│   ├── ai/           # AI decision making
│   └── analysis/     # Draft analysis
├── data/             # Data access and management
│   ├── api/          # API clients (OpenDota)
│   ├── db/           # Database operations
│   ├── model/        # Data models
│   └── repository/   # Data repositories
├── ui/               # User interface
│   ├── controller/   # JavaFX controllers
│   ├── view/         # View components
│   ├── component/    # Reusable UI components
│   └── overlay/      # Game overlay implementation
└── util/             # Utility classes
```

## Documentation

See the [docs](./docs) directory for detailed documentation, including:
- Product Requirements Document (PRD)
- Technical Design Document (TDD)

## Development

### Running Tests

```bash
mvn test
```

### Running a Single Test

```bash
mvn test -Dtest=TestClassName#testMethodName
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Roadmap

1. **Phase 1**: Basic draft simulation and AI opponent
2. **Phase 2**: Enhanced data analysis and team composition insights
3. **Phase 3**: In-game overlay integration
4. **Phase 4**: Player tendency analysis and team-specific draft strategies

## Acknowledgments

- Data provided by [OpenDota API](https://docs.opendota.com/)
- Inspired by professional Dota 2 drafting strategies