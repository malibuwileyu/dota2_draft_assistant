# Dota 2 Draft Assistant BrainLift

## Owners
- Ryan Heron (Project Owner)

## Initiative Overview
**Big Picture**: Build a reasoning-powered draft assistant that outcompetes existing tools (Valve Dota Plus, Overwolf DotaPlus) by using LLM-powered contextual analysis instead of simple statistical aggregates, delivering explanations that teach players *why* picks work rather than just *what* to pick.

## Purpose

### Primary Purpose
To create a Dota 2 draft companion that provides superior hero recommendations through contextual understanding of ability interactions, team composition synergies, and player-specific strengths—addressing the fundamental limitation of existing tools that reduce complex strategic decisions to naked win percentages.

### In-Scope
- LLM-powered draft recommendations with detailed reasoning (Groq API integration)
- Ability-based synergy and counter analysis (why heroes work together/against each other)
- Team composition scoring (teamfight, control, damage balance, initiation, saves)
- Player-specific recommendations based on match history and comfort heroes
- Pro match data analysis from OpenDota API
- Desktop application with future overlay support
- Steam authentication for personalized profiles

### Out-of-Scope
- Real-time in-game coaching beyond draft phase (Phase 10 future goal)
- Mobile application (desktop-first approach)
- Coaching marketplace or community features
- Direct game client integration (API-only approach)
- Smurf detection (not a core differentiator)

### Assumptions
- Dota 2 players want to understand *why* a pick is good, not just see a number
- Existing tools' reliance on aggregate winrates creates a ceiling on recommendation quality
- LLM inference latency (via Groq) is acceptable for draft phase decisions
- OpenDota API provides sufficient data quality for analysis
- Players will use a separate desktop app during draft if it provides superior value

---

## DOK4 - Spiky Points of View (SPOVs)

> SPOVs are CONTRARIAN, DEFENSIBLE positions that challenge conventional wisdom. Each must be bold enough to be falsifiable and backed by evidence chains.

### SPOV 1: "Win Rate Is a Lie—Ability Interaction Understanding Is the True Draft Edge"

**Claim**: Draft tools that rank heroes by aggregate win percentages fundamentally mislead players. Real draft skill comes from understanding *why* heroes counter each other through ability interactions (e.g., "Earthshaker's Fissure blocks Phantom Lancer's illusion escape paths"), not from memorizing statistical rankings that collapse context into a single number.

**Why This Is Contrarian**: The entire Dota companion app market—Overwolf DotaPlus, Valve's Dota Plus, DotaBuff, Stratz—is built on win rate displays. The conventional wisdom is that statistics are objective and ability descriptions are subjective hand-waving. This SPOV argues the opposite: raw statistics are actively harmful without contextual interpretation, and ability-based reasoning is more actionable.

**Evidence Chain**:
- Supported by DOK3: #Insight-1 (Aggregate Statistics Mask Contextual Reality), #Insight-2 (Ability Interactions Are Learnable Patterns)
- Key Facts:
  - Overwolf DotaPlus displays win rates without contextual reasoning (DOK2.1.1)
  - The codebase implements `AbilityInteractionAnalyzer` and `AbilityClassifier` that evaluate specific ability properties (stun duration, BKB-pierce, save mechanics) rather than just statistical correlations
  - Pro players discuss counters in terms of ability interactions ("Silencer counters Storm because Global Silence prevents Ball Lightning escape"), not win percentages

**Falsifiable Prediction**: If win rate displays were sufficient, high-MMR players would not need coaches explaining *why* certain picks work. Prediction: Users who receive ability-based explanations will make more contextually-appropriate draft deviations from "statistically optimal" picks AND achieve equal or better outcomes than users following pure win rate recommendations.

---

### SPOV 2: "Overwolf DotaPlus Is Built on Sand—Valve API Restrictions Will Collapse It"

**Claim**: Overwolf-based Dota 2 tools like DotaPlus operate at the mercy of Valve's API access decisions. Valve has already disabled third-party tool functions, and any serious draft assistant must be architected to function with increasingly restricted data access. Native desktop applications using public match data are more defensible than overlay-based tools relying on live game state scraping.

**Why This Is Contrarian**: Overwolf DotaPlus has significant market share and brand recognition. The conventional view is that its established position makes it safe. This SPOV argues its technical foundation (Overwolf overlay + game state access) is a liability, not an asset.

**Evidence Chain**:
- Supported by DOK3: #Insight-3 (Platform Dependency Is Existential Risk), #Insight-4 (Native Applications Offer Superior Stability)
- Key Facts:
  - Valve's update disabled functions used by third-party tools including Overwolf DotaPlus ([esports.gg](https://esports.gg/news/dota-2/dota-2-update-kills-third-party-applications-including-overwolf/))
  - Overwolf DotaPlus relies on public data and game state access which can be restricted ([sportskeeda.com](https://www.sportskeeda.com/esports/should-tools-like-overwolf-dotaplus-allowed-dota-2))
  - This project uses only OpenDota API + Steam Web API (public, stable, Valve-sanctioned data sources)
  - Overwolf overlay introduces latency and resource consumption issues ([support.overwolf.com](https://support.overwolf.com/))

**Falsifiable Prediction**: Within 24 months, Valve will further restrict third-party access to live game state, forcing Overwolf-based tools to rebuild or shut down. Our architecture (public API data only) will continue functioning without modification.

---

### SPOV 3: "Player-Specific Recommendations Require AI Reasoning, Not Just Filtering"

**Claim**: Current tools' approach to "personalization" (filtering recommendations by heroes the player has played) is naive. True player-specific recommendations require AI reasoning about *why* a player's hero pool and playstyle interact with the current draft—not just reducing the recommendation set to previously-played heroes.

**Why This Is Contrarian**: Dota Plus (Valve) and DotaPlus (Overwolf) both claim "personalized" recommendations, implemented as filters on aggregate data. The industry assumes personalization = filtering. This SPOV argues personalization without contextual reasoning is worthless.

**Evidence Chain**:
- Supported by DOK3: #Insight-5 (Filtering Is Not Personalization), #Insight-6 (AI Can Reason About Player Tendencies)
- Key Facts:
  - The codebase implements player-specific weighting: "60% global meta / 40% player-specific (standard), 80% global / 20% player (<500 matches)" (PRD.md)
  - The codebase includes "comfort hero detection" with hero pick frequency, win rate weighting, and "signature hero" identification
  - Groq LLM integration generates explanations referencing player's current team composition AND player's historical performance patterns
  - Overwolf DotaPlus offers "player notes" but not AI-reasoned recommendations based on player tendencies

**Falsifiable Prediction**: Given two equally-statistically-optimal picks (e.g., 52% win rate each), an AI system that reasons about player's specific hero proficiency will recommend the pick that yields higher actual win rates for that player compared to a system that ignores player history.

---

### SPOV 4: "The Dota Draft Problem Is Not Data—It's Interpretation"

**Claim**: The bottleneck in draft assistance is not data availability (OpenDota provides comprehensive match data) but data interpretation. Existing tools fail because they present data without interpretation. LLM-powered tools can bridge this gap by converting data into strategic narratives.

**Why This Is Contrarian**: The Dota data space is crowded with analytics tools (DotaBuff, Stratz, OpenDota itself). The conventional view is that more/better data visualization wins. This SPOV argues visualization without explanation is a solved (and insufficient) problem.

**Evidence Chain**:
- Supported by DOK3: #Insight-7 (Data Abundance Requires Interpretation Layer), #Insight-8 (LLMs Excel at Converting Data to Narrative)
- Key Facts:
  - OpenDota API provides comprehensive match, hero, and player data (used by this project)
  - The codebase's `GroqLpuIntegration` generates multi-paragraph explanations covering: synergies with team, effectiveness against enemies, team composition contribution, timing/power spikes, and position flexibility
  - Existing tools (DotaPlus, DotaBuff) show numbers; this tool explains *why* those numbers matter in context
  - Professional analysis panels spend time *explaining* why stats matter—tools should do this automatically

**Falsifiable Prediction**: Users given narrative explanations ("Pick Faceless Void because his Chronosphere enables your team's lacking teamfight capability, and the enemy lacks BKB-piercing control to deal with him") will learn draft concepts faster than users shown only statistical displays ("Faceless Void: 53.2% win rate, +2.1% synergy with your team").

---

### SPOV 5: "Team Composition Scoring Is Underrated—Pros Do It, Tools Don't"

**Claim**: Professional teams explicitly evaluate draft compositions on dimensions like "teamfight capability," "control density," "damage type balance," and "save potential." Existing consumer tools completely ignore composition scoring, leaving a massive capability gap between pro drafting methodology and amateur tool support.

**Why This Is Contrarian**: Most tools focus on hero-vs-hero (counters) or hero-with-hero (synergies) analysis. Composition-level analysis is considered "too complex" for automated tools. This SPOV argues it's not only possible but essential.

**Evidence Chain**:
- Supported by DOK3: #Insight-9 (Pros Think in Composition Dimensions), #Insight-10 (Composition Scoring Is Implementable)
- Key Facts:
  - The codebase implements `AbilityClassifier.HeroAbilityProfile` with scores for: teamfight, control, burstDamage, sustainedDamage, initiation, save, mobility, vision, bkbPierce
  - `DraftAnalysisService.evaluateTeamCompositionBalance()` evaluates how each pick addresses team needs (e.g., "Adds needed teamfight capability," "Adds BKB-piercing control for late game")
  - team_composition.md documents the domain knowledge: "Successful team composition requires careful balance of role distribution, power spike timing, damage diversity, strategic clarity, counter-pick awareness"
  - Neither Valve Dota Plus nor Overwolf DotaPlus surfaces composition-level analysis

**Falsifiable Prediction**: A draft tool that surfaces composition scores (e.g., "Your team's control score is low at 0.3; picking Tidehunter would raise it to 0.7") will produce more balanced drafts than tools that only show individual hero statistics.

---

## DOK3 - Strategic Insights

> Insights are NON-OBVIOUS conclusions drawn from synthesizing multiple facts. They connect raw data to strategic implications.

### Insight 1: Aggregate Statistics Mask Contextual Reality

**Synthesis**: A hero's overall win rate is a population average across all contexts. A hero with 52% overall win rate might have 65% win rate against specific compositions and 40% against others. Displaying aggregate rates without context actively misleads players into suboptimal picks.

**Supporting Evidence**:
- Technical Architecture: The codebase calculates `synergyScore` and `counterScore` for each hero *relative to the current draft state*, not using global averages
- Market Analysis: Overwolf DotaPlus and Valve Dota Plus display global win rates with minimal contextual adjustment
- Domain Knowledge: team_composition.md explains that hero effectiveness depends heavily on team context ("Magnus + Sven = AoE physical damage combo")

**Strategic Implication**: Default to contextual scoring over aggregate statistics in all UI displays and recommendations.

**Feeds SPOVs**: #SPOV-1, #SPOV-4

---

### Insight 2: Ability Interactions Are Learnable Patterns

**Synthesis**: Hero counters and synergies emerge from ability mechanics (stun chains, escape disruption, damage amplification). These interactions follow patterns that can be encoded and explained: "Heroes with long stuns synergize with heroes with channeled spells" or "Heroes with spell immunity pierce counter mobile heroes."

**Supporting Evidence**:
- Technical Architecture: `AbilityInteractionAnalyzer` evaluates specific ability properties (control type, BKB-pierce, save mechanics) to calculate synergy/counter scores
- Technical Architecture: `AbilityClassifier` categorizes abilities into actionable types (teamfight, control, burstDamage, etc.)
- Domain Knowledge: PRD.md Phase 6 plans "Generate AI counter explanations" based on ability interactions

**Strategic Implication**: Invest in completing ability data (currently ~40/124 heroes complete) to unlock full ability-based reasoning.

**Feeds SPOVs**: #SPOV-1, #SPOV-5

---

### Insight 3: Platform Dependency Is Existential Risk

**Synthesis**: Tools built on Overwolf's platform or relying on game state scraping are vulnerable to Valve's policy changes. Valve has demonstrated willingness to disable third-party tool functionality. Any serious draft assistant must architect for data access restrictions.

**Supporting Evidence**:
- Market Analysis: "Valve's update disabled functions used by third-party tools like Overwolf's DotaPlus" ([esports.gg](https://esports.gg/news/dota-2/dota-2-update-kills-third-party-applications-including-overwolf/))
- Market Analysis: "Overwolf's DotaPlus relies on public data, limiting its effectiveness" when profiles are private ([sportskeeda.com](https://www.sportskeeda.com/esports/should-tools-like-overwolf-dotaplus-allowed-dota-2))
- Technical Architecture: This project uses only OpenDota API (sanctioned, stable) and Steam Web API (official)

**Strategic Implication**: Never depend on game state scraping or unsanctioned data access methods. Design all features to work with publicly available, stable APIs.

**Feeds SPOVs**: #SPOV-2

---

### Insight 4: Native Applications Offer Superior Stability and Performance

**Synthesis**: Overwolf's overlay architecture introduces latency and resource consumption that a native desktop application avoids. For a tool used during the high-stakes draft phase, reliability and performance are critical differentiators.

**Supporting Evidence**:
- Technical Architecture: Project is built as JavaFX desktop application with Spring DI—native performance, no overlay overhead
- Market Analysis: "Overwolf apps operate as overlays, which can introduce latency and resource consumption issues" ([support.overwolf.com](https://support.overwolf.com/))
- Technical Architecture: The app uses HikariCP connection pooling for database performance, local caching strategy

**Strategic Implication**: Maintain desktop-first architecture. Phase 10 overlay implementation should be designed for minimal resource impact.

**Feeds SPOVs**: #SPOV-2

---

### Insight 5: Filtering Is Not Personalization

**Synthesis**: Existing tools implement "personalization" by filtering global recommendations to heroes the player has played. This misses the point—true personalization means understanding *how* a player's specific hero pool and playstyle interact with the current draft context.

**Supporting Evidence**:
- Technical Architecture: PRD.md specifies weighted recommendations: "60% global meta / 40% player-specific (standard), 80% global / 20% player (<500 matches)"
- Technical Architecture: Player profile system tracks hero performance (games, wins, avg KDA, comfort_score) for personalized weighting
- Market Analysis: Overwolf DotaPlus offers "player notes that persist across accounts" but not AI-reasoned personalization

**Strategic Implication**: Implement player-specific weighting that reasons about *why* a player's hero pool matters in context, not just filtering by previous picks.

**Feeds SPOVs**: #SPOV-3

---

### Insight 6: AI Can Reason About Player Tendencies

**Synthesis**: LLM systems can incorporate player history into prompts and generate recommendations that reference specific player strengths ("Your win rate on carry heroes suggests picking a late-game carry here, which also addresses your team's damage needs").

**Supporting Evidence**:
- Technical Architecture: `GroqLpuIntegration.generateGroqRecommendationExplanation()` builds prompts with ally/enemy hero details and position data
- Technical Architecture: PRD.md Phase 5 specifies "Comfort Hero Detection" with "signature hero" identification
- Domain Knowledge: Professional players are drafted around based on their hero pools—tools should mirror this

**Strategic Implication**: Complete Phase 5 (Player-Specific Recommendations) to unlock AI reasoning about individual player tendencies.

**Feeds SPOVs**: #SPOV-3

---

### Insight 7: Data Abundance Requires Interpretation Layer

**Synthesis**: OpenDota provides match-level, player-level, and hero-level data. The bottleneck is not data availability but converting data into actionable strategic insight. Raw data display is a commodity; interpretation is the differentiator.

**Supporting Evidence**:
- Technical Architecture: Project stores 1,700+ pro match drafts with full pick/ban sequences
- Technical Architecture: Database schema includes `hero_synergies` (7,754 synergies), `hero_counters` (15,809 counters), `meta_statistics`
- Market Analysis: DotaBuff, Stratz, OpenDota all provide data visualization—none provide AI interpretation

**Strategic Implication**: Prioritize explanation generation over data display. Every statistic shown should be accompanied by interpretation.

**Feeds SPOVs**: #SPOV-4

---

### Insight 8: LLMs Excel at Converting Data to Narrative

**Synthesis**: Large Language Models are well-suited for the task of converting structured data (hero stats, synergy scores, composition needs) into natural language explanations that players can understand and learn from.

**Supporting Evidence**:
- Technical Architecture: Groq LLM integration (llama3-70b-8192 model) generates explanations covering synergies, counters, composition contribution, timing, and position flexibility
- Technical Architecture: Prompts include hero abilities, role frequency, and draft phase context
- Technical Architecture: Fallback to heuristic explanations when LLM unavailable ensures reliability

**Strategic Implication**: Continue investment in prompt engineering (PRD.md Phase 4) to maximize explanation quality.

**Feeds SPOVs**: #SPOV-4

---

### Insight 9: Pros Think in Composition Dimensions

**Synthesis**: Professional Dota 2 analysis explicitly discusses team compositions in terms of capabilities: "This team lacks frontline," "They need a save," "The damage is too magic-heavy." Amateur tools ignore this level of analysis entirely.

**Supporting Evidence**:
- Domain Knowledge: team_composition.md covers "Teamfight Potential," "Crowd Control (CC)," "Damage Mix," "Tower Pushing," "Frontline Presence," "Chase Potential"
- Domain Knowledge: Example composition analysis: "Mixed damage types, strong teamfight presence, multiple CC options, clear power spikes, balanced scaling"
- Technical Architecture: `DraftAnalysisService.analyzeTeamComposition()` scores teams on teamfight, control, burstDamage, sustainedDamage, initiation, save, mobility, vision, bkbPierce

**Strategic Implication**: Surface composition scores prominently in UI. Explain picks in terms of what they add to composition.

**Feeds SPOVs**: #SPOV-5

---

### Insight 10: Composition Scoring Is Implementable

**Synthesis**: Team composition analysis, though complex, can be systematically implemented by scoring heroes on capability dimensions and aggregating with diminishing returns to measure coverage.

**Supporting Evidence**:
- Technical Architecture: `AbilityClassifier.HeroAbilityProfile` scores heroes on multiple dimensions from ability analysis
- Technical Architecture: `updateScoreWithDiminishingReturns()` prevents over-stacking single capabilities
- Technical Architecture: `collectCompositionReasons()` generates explanations like "Adds essential teamfight capability," "Adds BKB-piercing control for late game"

**Strategic Implication**: Composition scoring is a solved problem in the codebase—now needs UI surfacing and user testing.

**Feeds SPOVs**: #SPOV-5

---

## Experts

> Real people with verifiable credentials who inform this domain.

### Builders/Practitioners

#### Howard "Nahaz" Bates
- **Who**: Statistics professor and former professional Dota 2 analyst/panelist
- **Main Views**: Statistical analysis must be contextualized to be meaningful. Raw numbers without game context mislead viewers. Believes in combining quantitative data with qualitative analysis.
- **Why Follow**: His panel analysis demonstrates how statistics should be *explained* not just displayed—the exact approach this project takes with LLM explanations.
- **Locations**:
  - Twitter/X: [@NahazDota](https://twitter.com/NahazDota)
  - Liquipedia: [Nahaz](https://liquipedia.net/dota2/Nahaz)
  - Panels: TI5-TI9 analysis panels, countless tournaments

#### Kevin "Purge" Godec
- **Who**: Dota 2 educational content creator and professional analyst
- **Main Views**: Dota improvement comes from understanding mechanics and decision-making, not just copying builds. Created "Welcome to Dota, You Suck" and "Learn Dota" series. Emphasizes fundamentals over meta-chasing.
- **Why Follow**: His educational approach mirrors the project's goal of explaining *why* picks work rather than just recommending them.
- **Locations**:
  - Twitter/X: [@PurgeGamers](https://twitter.com/PurgeGamers)
  - YouTube: [PurgeGamers](https://www.youtube.com/user/PurgeGamers)
  - Website: [dotabuff.com/learn](https://www.dotabuff.com/learn/purge)

#### Brian "BSJ" Canavan
- **Who**: Professional Dota 2 coach and educational streamer
- **Main Views**: Draft phase is won through understanding team composition needs and opponent tendencies. Emphasizes the importance of learning draft through active reasoning, not pattern matching.
- **Why Follow**: His coaching methodology validates the approach of explaining reasoning rather than just showing statistics.
- **Locations**:
  - Twitter/X: [@BananaSlamJamma](https://twitter.com/BananaSlamJamma)
  - YouTube: [BSJ](https://www.youtube.com/channel/UCvTcxoyItMUSlw8T2MajftA)
  - Twitch: [bananaslamjamma](https://www.twitch.tv/bananaslamjamma)

### Domain Specialists

#### OpenDota Team
- **Who**: Maintainers of the OpenDota API and analytics platform
- **Main Views**: Open data benefits the Dota community. API access should be developer-friendly. Match data provides foundation for community tools.
- **Why Follow**: Primary data source for this project. Understanding their API design and data model is essential.
- **Locations**:
  - Website: [opendota.com](https://www.opendota.com)
  - API Docs: [docs.opendota.com](https://docs.opendota.com)
  - GitHub: [odota/core](https://github.com/odota/core)

### AI/ML Practitioners

#### Groq Team
- **Who**: Developers of the Groq LPU (Language Processing Unit) for fast LLM inference
- **Main Views**: Inference speed matters for real-time applications. LPU architecture can deliver >500 tokens/second, enabling interactive AI applications.
- **Why Follow**: The project uses Groq API for LLM recommendations. Understanding inference capabilities informs what's possible in real-time draft assistance.
- **Locations**:
  - Website: [groq.com](https://groq.com)
  - API Docs: [console.groq.com/docs](https://console.groq.com/docs)
  - Twitter/X: [@GroqInc](https://twitter.com/GroqInc)

---

## DOK2 - Knowledge Tree

> Organized summaries of facts by category.

### Category 1: Competitor Analysis
**Purpose**: Understand current market offerings and their limitations to identify differentiation opportunities.

#### 1.1: Overwolf DotaPlus Features & Limitations
**Summary**: Overwolf DotaPlus provides basic draft assistance through ban/pick suggestions, smurf detection, MMR tracking, and player notes. It relies on public match data and static algorithms, lacking contextual reasoning or AI-driven personalization. The Overwolf overlay architecture introduces potential performance issues.

**Key Facts (DOK1)**:
- Features include ban and pick suggestions, smurf detection, MMR tracking ([overwolf.com](https://www.overwolf.com/app/Overwolf-DotaPlus))
- Relies on public data, limiting effectiveness when player profiles are private ([sportskeeda.com](https://www.sportskeeda.com/esports/should-tools-like-overwolf-dotaplus-allowed-dota-2))
- Overwolf apps operate as overlays, which can introduce latency and resource consumption ([support.overwolf.com](https://support.overwolf.com/))
- Valve's update disabled functions used by third-party tools including Overwolf DotaPlus ([esports.gg](https://esports.gg/news/dota-2/dota-2-update-kills-third-party-applications-including-overwolf/))
- Uses in-app advertisements as monetization ([content.overwolf.com](https://content.overwolf.com/ads/revenue-page.pdf))

**Source**: Overwolf official documentation, industry coverage

#### 1.2: Valve Dota Plus Features & Limitations
**Summary**: Valve's official Dota Plus subscription ($3.99/month) offers hero suggestions during draft based on global statistics, hero progression systems, and in-game tips. Recommendations are based on aggregate win rates without personalization or contextual reasoning.

**Key Facts (DOK1)**:
- Shows hero suggestions during pick phase with win rate percentages
- Uses global statistics without player-specific customization
- Integrated into game client (no external tool needed)
- Limited explanation for *why* heroes are recommended

**Source**: Valve official Dota 2 website, in-game observation

#### 1.3: Analytics Platforms (DotaBuff, Stratz, OpenDota)
**Summary**: Analytics platforms provide comprehensive match data visualization but focus on data display rather than interpretation. They serve as data sources rather than draft assistants.

**Key Facts (DOK1)**:
- OpenDota provides free API access with comprehensive match data
- DotaBuff offers hero statistics, meta analysis, and player profiles
- Stratz provides similar functionality with different visualization
- None provide AI-driven draft recommendations or contextual explanations

**Source**: Platform documentation and usage

---

### Category 2: Technical Architecture
**Purpose**: Document the technical foundation that enables differentiated capabilities.

#### 2.1: LLM Integration (Groq API)
**Summary**: The project integrates Groq's LPU-powered API with the llama3-70b-8192 model for generating draft recommendations with detailed explanations. The system builds contextual prompts including hero abilities, team composition, and draft phase information.

**Key Facts (DOK1)**:
- Model: llama3-70b-8192 via Groq API
- Prompts include: hero details, abilities, position data, ally/enemy composition, draft stage
- Explanations cover: synergies, counters, composition contribution, timing, position flexibility
- Fallback to heuristic explanations when API unavailable
- Max tokens: 500, Temperature: 0.7

**Source**: `GroqLpuIntegration.java`

#### 2.2: Ability Analysis System
**Summary**: The project implements systematic ability analysis through two components: `AbilityInteractionAnalyzer` for calculating synergy/counter scores based on ability interactions, and `AbilityClassifier` for categorizing hero ability profiles across multiple dimensions.

**Key Facts (DOK1)**:
- AbilityClassifier scores: teamfight, control, burstDamage, sustainedDamage, initiation, save, mobility, vision, bkbPierce
- AbilityInteractionAnalyzer produces SynergyAnalysis and CounterAnalysis with scores and reasons
- 124 heroes loaded, ~40 with complete ability data (32% completion)
- Ability data stored in JSON files under `/resources/data/abilities/`

**Source**: `AbilityClassifier.java`, `AbilityInteractionAnalyzer.java`, `DraftAnalysisService.java`

#### 2.3: Recommendation Scoring Algorithm
**Summary**: Draft recommendations use a weighted scoring formula combining multiple factors: pick rate (meta popularity), win rate, synergy score, counter score, and composition score.

**Key Facts (DOK1)**:
- Pick recommendation formula: `(pickRate * 0.5) + (winRate * 0.1) + (synergyScore * 0.15) + (counterScore * 0.15) + (compositionScore * 0.1)`
- Ban recommendations adjust weighting based on draft phase and pick position
- Synergy scores calculated against all current team picks
- Counter scores calculated against all enemy picks
- Composition scores evaluate team needs with diminishing returns for over-stacking

**Source**: `DraftAnalysisService.java`

#### 2.4: Data Infrastructure
**Summary**: PostgreSQL database with comprehensive schema supporting heroes, abilities, matches, players, teams, synergies, counters, and meta statistics. Pro match data from OpenDota API provides the statistical foundation.

**Key Facts (DOK1)**:
- 124 heroes loaded with abilities
- 7,754 hero synergies loaded
- 15,809 hero counters loaded
- ~3,500 pro matches (7 months old, refresh needed)
- 1,736 match detail files, 1,717 draft files
- Database: PostgreSQL (production) with SQLite fallback
- Connection pooling: HikariCP

**Source**: `database_schema.md`, PRD.md

---

### Category 3: Domain Knowledge (Dota 2 Drafting)
**Purpose**: Document the strategic framework that informs recommendation logic.

#### 3.1: Team Composition Fundamentals
**Summary**: Successful Dota 2 drafts balance multiple dimensions: role distribution (positions 1-5), power spike timing (early/mid/late game), damage diversity (physical/magical), and strategic capabilities (teamfight, control, initiation, saves).

**Key Facts (DOK1)**:
- Five positions with distinct roles: Carry (1), Mid (2), Offlane (3), Soft Support (4), Hard Support (5)
- Scaling categories: Early-game, Mid-game, Late-game focus
- Teamfight elements: AoE damage, Crowd Control, Buffs/Debuffs
- CC types: Single-target, AoE, Soft CC
- Damage types: Physical (scales with items), Magical (spell-based)

**Source**: `team_composition.md`

#### 3.2: Strategic Archetypes
**Summary**: Teams can draft toward specific strategic identities: Deathball (early grouping/push), Split-Push (lane pressure/map control), Pick-Off (isolation/roaming), or Late-Game Scaling (defensive early, resource accumulation).

**Key Facts (DOK1)**:
- Deathball examples: Death Prophet, Jakiro, Underlord
- Split-Push examples: Nature's Prophet, Tinker
- Pick-Off examples: Night Stalker, Bounty Hunter
- Late-Game examples: Spectre, Invoker, Medusa
- Notable synergy combos: Magnus + Sven (AoE physical), Shadow Demon + Mirana (pick-off setup)

**Source**: `team_composition.md`

#### 3.3: Common Drafting Mistakes
**Summary**: Amateur drafts commonly fail due to: overemphasis on late game without early security, insufficient waveclear, poor vision control capabilities, mismatched timing windows across heroes, and single damage type focus.

**Key Facts (DOK1)**:
- Risk: Overemphasis on late game → vulnerable to early aggression
- Risk: Single damage type → easily countered by resistance items
- Risk: Mismatched timing windows → no cohesive power spike
- Mitigation: Balance power spikes, ensure role coverage, maintain damage diversity

**Source**: `team_composition.md`

---

### Category 4: Player Personalization
**Purpose**: Document the approach to player-specific recommendations.

#### 4.1: Weighting Strategy
**Summary**: Recommendations blend global meta data with player-specific performance data, with weighting adjusted based on player experience level.

**Key Facts (DOK1)**:
- Standard weighting: 60% global meta / 40% player-specific
- New player weighting (<500 matches): 80% global / 20% player
- Recent focus: Emphasize last 3 months vs. all-time performance
- Comfort score calculated from: games played, win rate, consistency

**Source**: PRD.md Phase 5

#### 4.2: Player Profile Data
**Summary**: Player profiles track match history and hero performance to enable personalized recommendations.

**Key Facts (DOK1)**:
- Database tracks: account_id, hero_id, games, wins, last_played, avg_kills, avg_deaths, avg_assists, comfort_score
- "Signature hero" detection for one-trick identification
- Steam authentication provides profile linkage
- Match history sync via OpenDota API (98.9% success rate)

**Source**: `database_schema.md`, PRD.md

---

## DOK1 - Raw Facts & Sources

### Source 1: Project PRD.md
- **Type**: Project Documentation
- **Key Quotes**:
  > "Build a **reasoning agent** that provides superior draft recommendations compared to existing tools (Valve Dota Plus, Overwolf DotaPlus) by using: LLM-powered reasoning (Groq API) instead of simple winrate aggregates"
  > "vs. Overwolf DotaPlus: ✅ Deeper integration with player data, ✅ Team composition analysis"
- **Statistics**: 28/32 tests passing (87.5%), 40/124 heroes with complete ability data (32%)
- **Link**: `/docs/PRD.md`

### Source 2: DraftAnalysisService.java
- **Type**: Source Code
- **Key Implementation Details**:
  - Weighted scoring: `(pickRate * 0.5) + (winRate * 0.1) + (synergyScore * 0.15) + (counterScore * 0.15) + (compositionScore * 0.1)`
  - Composition scoring with diminishing returns
  - Ability-based synergy/counter analysis integration
- **Link**: `/src/main/java/com/dota2assistant/core/analysis/DraftAnalysisService.java`

### Source 3: GroqLpuIntegration.java
- **Type**: Source Code
- **Key Implementation Details**:
  - Model: `llama3-70b-8192`
  - Generates explanations covering: synergies, counters, composition, timing, position
  - Fallback to basic explanation if API fails
- **Link**: `/src/main/java/com/dota2assistant/core/ai/GroqLpuIntegration.java`

### Source 4: Esports.gg - Valve Update Coverage
- **Type**: News Article
- **Key Quote**: "Valve launches update to shut down third-party software like Overwolf"
- **Link**: [esports.gg](https://esports.gg/news/dota-2/dota-2-update-kills-third-party-applications-including-overwolf/)

### Source 5: Sportskeeda - DotaPlus Analysis
- **Type**: Analysis Article
- **Key Quote**: "Overwolf's DotaPlus relies on public data, limiting its effectiveness"
- **Link**: [sportskeeda.com](https://www.sportskeeda.com/esports/should-tools-like-overwolf-dotaplus-allowed-dota-2)

---

## Business Validation

### S0 - Idea Stage
- [x] Problem validated (existing tools use naive statistics)
- [x] Solution hypothesis defined (LLM-powered contextual reasoning)
- [ ] Initial user interviews (n=0) — NEEDED

### S1 - MVP Stage
- [x] Working prototype (application compiles and runs)
- [ ] Early adopter feedback — NEEDED
- [ ] Key metrics defined — NEEDED

---

## Open Questions & Next Steps

### Research Questions
- [ ] What is the optimal weighting formula for pick recommendations? (A/B testing needed)
- [ ] How do users perceive LLM-generated explanations vs. statistical displays?
- [ ] What is the minimum viable ability data coverage for useful recommendations?

### Validation Steps
- [ ] Conduct 5-10 user interviews with Dota 2 players of varying skill levels
- [ ] Compare recommendation quality against Overwolf DotaPlus in controlled test
- [ ] Measure explanation comprehension and learning outcomes

### Technical Next Steps
- [ ] Update pro match dataset (current 3,500 matches are 7 months old)
- [ ] Complete ability data for remaining 84 heroes
- [ ] Fix 4 failing unit tests
- [ ] Test Groq API with real API key under production load

### Expert Outreach
- [ ] Contact BSJ for coaching perspective on tool utility
- [ ] Review Nahaz's panel analysis methodology for explanation patterns
- [ ] Engage OpenDota community for API integration feedback

