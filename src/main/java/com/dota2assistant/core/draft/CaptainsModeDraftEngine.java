package com.dota2assistant.core.draft;

import com.dota2assistant.data.model.Hero;
import com.dota2assistant.data.repository.HeroRepository;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the DraftEngine interface for Captain's Mode drafting.
 * <p>
 * Captain's Mode Draft Order (as of Dota 2 patch 7.33):
 * 1. Ban Phase 1: ABBABBA (A = First Pick/Radiant, B = Second Pick/Dire) - 7 bans
 * 2. Pick Phase 1: AB (2 picks)
 * 3. Ban Phase 2: AAB (3 bans)
 * 4. Pick Phase 2: BAABBA (6 picks)
 * 5. Ban Phase 3: ABBA (4 bans)
 * 6. Pick Phase 3: AB (2 picks)
 * 
 * Total: 14 bans (7 per team) and 10 picks (5 per team)
 */
public class CaptainsModeDraftEngine implements DraftEngine {

    private static final Logger logger = LoggerFactory.getLogger(CaptainsModeDraftEngine.class);
    
    private static final int DEFAULT_PICK_TIME = 30; // seconds
    private static final int DEFAULT_BAN_TIME = 30; // seconds
    private static final int DEFAULT_RESERVE_TIME = 130; // seconds per team
    
    private final HeroRepository heroRepository;
    private final DraftState draftState;
    private final Map<Team, Integer> reserveTime; // Reserve time in seconds per team
    private final ReadOnlyBooleanWrapper playerTurnProperty;
    private final Random random = new Random();
    private ScheduledExecutorService timerExecutor;
    
    // The order of turns in Captain's Mode draft
    private static final List<TurnInfo> DRAFT_SEQUENCE = Arrays.asList(
        // Ban Phase 1: ABBABBA (7 bans total)
        // First pick team (Radiant) gets 1st, 4th, 7th bans
        // Second pick team (Dire) gets 2nd, 3rd, 5th, 6th bans
        new TurnInfo(Team.RADIANT, DraftPhase.CM_BAN_1, true), // 1
        new TurnInfo(Team.DIRE, DraftPhase.CM_BAN_1, true),    // 2
        new TurnInfo(Team.DIRE, DraftPhase.CM_BAN_1, true),    // 3
        new TurnInfo(Team.RADIANT, DraftPhase.CM_BAN_1, true), // 4
        new TurnInfo(Team.DIRE, DraftPhase.CM_BAN_1, true),    // 5
        new TurnInfo(Team.DIRE, DraftPhase.CM_BAN_1, true),    // 6
        new TurnInfo(Team.RADIANT, DraftPhase.CM_BAN_1, true), // 7
        
        // Pick Phase 1: AB (2 picks)
        new TurnInfo(Team.RADIANT, DraftPhase.CM_PICK_1, false), // 8
        new TurnInfo(Team.DIRE, DraftPhase.CM_PICK_1, false),    // 9
        
        // Ban Phase 2: AAB (3 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.CM_BAN_2, true), // 10
        new TurnInfo(Team.RADIANT, DraftPhase.CM_BAN_2, true), // 11
        new TurnInfo(Team.DIRE, DraftPhase.CM_BAN_2, true),    // 12
        
        // Pick Phase 2: BAABBA (6 picks)
        new TurnInfo(Team.DIRE, DraftPhase.CM_PICK_2, false),    // 13
        new TurnInfo(Team.RADIANT, DraftPhase.CM_PICK_2, false), // 14
        new TurnInfo(Team.RADIANT, DraftPhase.CM_PICK_2, false), // 15
        new TurnInfo(Team.DIRE, DraftPhase.CM_PICK_2, false),    // 16
        new TurnInfo(Team.DIRE, DraftPhase.CM_PICK_2, false),    // 17
        new TurnInfo(Team.RADIANT, DraftPhase.CM_PICK_2, false), // 18
        
        // Ban Phase 3: ABBA (4 bans)
        new TurnInfo(Team.RADIANT, DraftPhase.CM_BAN_3, true), // 19
        new TurnInfo(Team.DIRE, DraftPhase.CM_BAN_3, true),    // 20
        new TurnInfo(Team.DIRE, DraftPhase.CM_BAN_3, true),    // 21
        new TurnInfo(Team.RADIANT, DraftPhase.CM_BAN_3, true), // 22
        
        // Pick Phase 3: AB (2 picks)
        new TurnInfo(Team.RADIANT, DraftPhase.CM_PICK_3, false), // 23
        new TurnInfo(Team.DIRE, DraftPhase.CM_PICK_3, false)     // 24
    );
    
    /**
     * Current position in the draft sequence
     */
    private int currentTurnIndex;
    
    /**
     * Gets the current turn index
     * @return the current turn index
     */
    public int getCurrentTurnIndex() {
        return currentTurnIndex;
    }
    
    /**
     * Constructor for the CaptainsModeDraftEngine
     * 
     * @param heroRepository The repository for accessing hero data
     */
    public CaptainsModeDraftEngine(HeroRepository heroRepository) {
        this.heroRepository = heroRepository;
        this.draftState = new DraftState();
        this.reserveTime = new HashMap<>();
        reserveTime.put(Team.RADIANT, DEFAULT_RESERVE_TIME);
        reserveTime.put(Team.DIRE, DEFAULT_RESERVE_TIME);
        this.playerTurnProperty = new ReadOnlyBooleanWrapper();
    }
    
    @Override
    public void initDraft(DraftMode draftMode, boolean timerEnabled) {
        if (draftMode != DraftMode.CAPTAINS_MODE) {
            throw new IllegalArgumentException("This engine only supports Captain's Mode drafting");
        }
        
        // Reset state
        draftState.getRadiantPicks().clear();
        draftState.getDirePicks().clear();
        draftState.getBannedHeroes().clear();
        
        // Load all heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        draftState.setAvailableHeroes(allHeroes);
        
        // Initialize draft
        draftState.setMode(draftMode);
        draftState.setTimerEnabled(timerEnabled);
        draftState.setDraftInProgress(true);
        draftState.setDraftComplete(false);
        
        // Start from the beginning of the draft sequence
        currentTurnIndex = 0;
        TurnInfo firstTurn = DRAFT_SEQUENCE.get(currentTurnIndex);
        
        // Set initial state
        draftState.setCurrentTeam(firstTurn.team);
        draftState.setCurrentPhase(firstTurn.phase);
        
        // Reset reserve time
        reserveTime.put(Team.RADIANT, DEFAULT_RESERVE_TIME);
        reserveTime.put(Team.DIRE, DEFAULT_RESERVE_TIME);
        
        // Set timer
        if (timerEnabled) {
            draftState.setRemainingTime(firstTurn.isBan ? DEFAULT_BAN_TIME : DEFAULT_PICK_TIME);
            startTimer();
        }
        
        // Update player turn property based on the current team
        playerTurnProperty.set(draftState.getCurrentTeam() == Team.RADIANT);
        
        logger.info("Draft initialized: {} with timer {}", draftMode, timerEnabled);
    }
    
    @Override
    public DraftState getCurrentState() {
        return draftState;
    }
    
    @Override
    public List<Hero> getAvailableHeroes() {
        return new ArrayList<>(draftState.getAvailableHeroes());
    }
    
    @Override
    public boolean selectHero(Hero hero) {
        // Validate hero selection
        if (!draftState.isDraftInProgress()) {
            logger.warn("Cannot select hero when draft is not in progress");
            return false;
        }
        
        if (!draftState.isHeroAvailable(hero)) {
            logger.warn("Hero is not available for selection: {}", hero.getLocalizedName());
            return false;
        }
        
        // Check if we're in a pick phase
        TurnInfo currentTurn = DRAFT_SEQUENCE.get(currentTurnIndex);
        if (currentTurn.isBan) {
            logger.warn("Cannot pick hero during ban phase");
            return false;
        }
        
        // Add hero to the appropriate team
        if (draftState.getCurrentTeam() == Team.RADIANT) {
            draftState.addRadiantPick(hero);
            logger.info("Radiant picked {}", hero.getLocalizedName());
        } else {
            draftState.addDirePick(hero);
            logger.info("Dire picked {}", hero.getLocalizedName());
        }
        
        // Move to next turn
        advanceToNextTurn();
        return true;
    }
    
    @Override
    public boolean banHero(Hero hero) {
        // Validate hero ban
        if (!draftState.isDraftInProgress()) {
            logger.warn("Cannot ban hero when draft is not in progress");
            return false;
        }
        
        if (!draftState.isHeroAvailable(hero)) {
            logger.warn("Hero is not available for banning: {}", hero.getLocalizedName());
            return false;
        }
        
        // Check if we're in a ban phase
        TurnInfo currentTurn = DRAFT_SEQUENCE.get(currentTurnIndex);
        if (!currentTurn.isBan) {
            logger.warn("Cannot ban hero during pick phase");
            return false;
        }
        
        // Ban the hero
        draftState.addBannedHero(hero);
        logger.info("{} banned {}", draftState.getCurrentTeam(), hero.getLocalizedName());
        
        // Move to next turn
        advanceToNextTurn();
        return true;
    }
    
    @Override
    public List<Hero> getTeamPicks(Team team) {
        return team == Team.RADIANT ? 
                new ArrayList<>(draftState.getRadiantPicks()) : 
                new ArrayList<>(draftState.getDirePicks());
    }
    
    @Override
    public List<Hero> getBannedHeroes() {
        return new ArrayList<>(draftState.getBannedHeroes());
    }
    
    @Override
    public boolean isDraftInProgress() {
        return draftState.isDraftInProgress();
    }
    
    @Override
    public boolean isDraftComplete() {
        return draftState.isDraftComplete();
    }
    
    @Override
    public int getRemainingTime() {
        return draftState.getRemainingTime();
    }
    
    @Override
    public void resetDraft() {
        // Stop timer if running
        if (timerExecutor != null) {
            timerExecutor.shutdownNow();
            timerExecutor = null;
        }
        
        // Reset state
        draftState.setDraftInProgress(false);
        draftState.setDraftComplete(false);
        draftState.setCurrentPhase(DraftPhase.NOT_STARTED);
        draftState.getRadiantPicks().clear();
        draftState.getDirePicks().clear();
        draftState.getBannedHeroes().clear();
        
        // Reset available heroes
        List<Hero> allHeroes = heroRepository.getAllHeroes();
        draftState.setAvailableHeroes(allHeroes);
        
        // Reset reserve time
        reserveTime.put(Team.RADIANT, DEFAULT_RESERVE_TIME);
        reserveTime.put(Team.DIRE, DEFAULT_RESERVE_TIME);
        
        logger.info("Draft reset");
    }
    
    @Override
    public ReadOnlyBooleanProperty playerTurnProperty() {
        // Make sure this is updated whenever the state changes
        logger.info("playerTurnProperty accessed - current value: {}", playerTurnProperty.get());
        return playerTurnProperty.getReadOnlyProperty();
    }
    
    @Override
    public Team getCurrentTeam() {
        return draftState.getCurrentTeam();
    }
    
    @Override
    public DraftPhase getCurrentPhase() {
        return draftState.getCurrentPhase();
    }
    
    /**
     * Advances to the next turn in the draft sequence
     */
    private void advanceToNextTurn() {
        // Stop the current timer
        if (timerExecutor != null) {
            timerExecutor.shutdownNow();
            timerExecutor = null;
        }
        
        // Increment turn index
        currentTurnIndex++;
        
        // Check if draft is complete
        if (currentTurnIndex >= DRAFT_SEQUENCE.size()) {
            draftState.setDraftInProgress(false);
            draftState.setDraftComplete(true);
            draftState.setCurrentPhase(DraftPhase.COMPLETED);
            logger.info("Draft completed");
            return;
        }
        
        // Set up next turn
        TurnInfo nextTurn = DRAFT_SEQUENCE.get(currentTurnIndex);
        draftState.setCurrentTeam(nextTurn.team);
        draftState.setCurrentPhase(nextTurn.phase);
        
        // Set timer
        if (draftState.isTimerEnabled()) {
            draftState.setRemainingTime(nextTurn.isBan ? DEFAULT_BAN_TIME : DEFAULT_PICK_TIME);
            startTimer();
        }
        
        // Update player turn property
        playerTurnProperty.set(draftState.getCurrentTeam() == Team.RADIANT);
        
        logger.info("Advanced to next turn: {} {}", 
                draftState.getCurrentTeam(), 
                draftState.getCurrentPhase());
    }
    
    /**
     * Starts the timer for the current turn
     */
    private void startTimer() {
        if (timerExecutor != null) {
            timerExecutor.shutdownNow();
        }
        
        timerExecutor = new ScheduledThreadPoolExecutor(1);
        timerExecutor.scheduleAtFixedRate(() -> {
            // Decrement time
            int remainingTime = draftState.getRemainingTime();
            if (remainingTime > 0) {
                draftState.setRemainingTime(remainingTime - 1);
            } else {
                // Time's up, use reserve time
                Team currentTeam = draftState.getCurrentTeam();
                int teamReserveTime = reserveTime.get(currentTeam);
                
                if (teamReserveTime > 0) {
                    // Use reserve time
                    reserveTime.put(currentTeam, teamReserveTime - 1);
                } else {
                    // No time left, make random selection
                    makeRandomSelection();
                    // This will also stop the timer
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Makes a random selection when time runs out
     */
    private void makeRandomSelection() {
        if (!draftState.isDraftInProgress() || draftState.getAvailableHeroes().isEmpty()) {
            return;
        }
        
        // Get a random available hero
        List<Hero> availableHeroes = draftState.getAvailableHeroes();
        Hero randomHero = availableHeroes.get(random.nextInt(availableHeroes.size()));
        
        // Check if we're in a ban or pick phase
        TurnInfo currentTurn = DRAFT_SEQUENCE.get(currentTurnIndex);
        if (currentTurn.isBan) {
            banHero(randomHero);
        } else {
            selectHero(randomHero);
        }
    }
    
    /**
     * Helper class to represent a turn in the draft sequence
     */
    private static class TurnInfo {
        final Team team;
        final DraftPhase phase;
        final boolean isBan;
        
        TurnInfo(Team team, DraftPhase phase, boolean isBan) {
            this.team = team;
            this.phase = phase;
            this.isBan = isBan;
        }
    }
}