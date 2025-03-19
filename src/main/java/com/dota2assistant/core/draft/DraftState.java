package com.dota2assistant.core.draft;

import com.dota2assistant.data.model.Hero;

import java.util.ArrayList;
import java.util.List;

public class DraftState {
    private DraftMode mode;
    private DraftPhase currentPhase;
    private Team currentTeam;
    private boolean timerEnabled;
    private int remainingTime;
    private boolean draftInProgress;
    private boolean draftComplete;
    
    private final List<Hero> radiantPicks;
    private final List<Hero> direPicks;
    private final List<Hero> bannedHeroes;
    private final List<Hero> availableHeroes;
    
    public DraftState() {
        this.radiantPicks = new ArrayList<>();
        this.direPicks = new ArrayList<>();
        this.bannedHeroes = new ArrayList<>();
        this.availableHeroes = new ArrayList<>();
        this.currentPhase = DraftPhase.NOT_STARTED;
        this.draftInProgress = false;
        this.draftComplete = false;
    }

    public DraftMode getMode() {
        return mode;
    }

    public void setMode(DraftMode mode) {
        this.mode = mode;
    }

    public DraftPhase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(DraftPhase currentPhase) {
        this.currentPhase = currentPhase;
    }

    public Team getCurrentTeam() {
        return currentTeam;
    }

    public void setCurrentTeam(Team currentTeam) {
        this.currentTeam = currentTeam;
    }

    public boolean isTimerEnabled() {
        return timerEnabled;
    }

    public void setTimerEnabled(boolean timerEnabled) {
        this.timerEnabled = timerEnabled;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }

    public boolean isDraftInProgress() {
        return draftInProgress;
    }

    public void setDraftInProgress(boolean draftInProgress) {
        this.draftInProgress = draftInProgress;
    }

    public boolean isDraftComplete() {
        return draftComplete;
    }

    public void setDraftComplete(boolean draftComplete) {
        this.draftComplete = draftComplete;
    }

    public List<Hero> getRadiantPicks() {
        return radiantPicks;
    }
    
    public void addRadiantPick(Hero hero) {
        this.radiantPicks.add(hero);
        this.availableHeroes.remove(hero);
    }

    public List<Hero> getDirePicks() {
        return direPicks;
    }
    
    public void addDirePick(Hero hero) {
        this.direPicks.add(hero);
        this.availableHeroes.remove(hero);
    }

    public List<Hero> getBannedHeroes() {
        return bannedHeroes;
    }
    
    public void addBannedHero(Hero hero) {
        this.bannedHeroes.add(hero);
        this.availableHeroes.remove(hero);
    }

    public List<Hero> getAvailableHeroes() {
        return availableHeroes;
    }
    
    public void setAvailableHeroes(List<Hero> heroes) {
        this.availableHeroes.clear();
        this.availableHeroes.addAll(heroes);
    }
    
    public boolean isHeroAvailable(Hero hero) {
        return this.availableHeroes.contains(hero);
    }
}