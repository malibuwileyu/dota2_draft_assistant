package com.dota2assistant.gsi.model;

import java.util.Objects;

/**
 * Represents a hero pick or ban in the draft.
 */
public class DraftPick {
    private int heroId;
    
    public int getHeroId() {
        return heroId;
    }
    
    public void setHeroId(int heroId) {
        this.heroId = heroId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DraftPick draftPick = (DraftPick) o;
        return heroId == draftPick.heroId;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(heroId);
    }
}