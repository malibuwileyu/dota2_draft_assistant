package com.dota2assistant.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Hero {
    private int id;
    private String name;
    private String localizedName;
    private String primaryAttribute;
    private String attackType;
    private List<String> roles;
    private String imageUrl;
    private Map<Integer, Double> roleFrequency; // position -> frequency
    private boolean hasDefaultRoleFrequency = false; // Flag to indicate if using default values
    private HeroAttributes attributes;
    private List<Ability> abilities;
    @JsonProperty("innate_abilities")
    private List<InnateAbility> innateAbilities;
    private Map<Integer, Double> synergies; // hero_id -> synergy score
    private Map<Integer, Double> counters; // hero_id -> counter score
    
    public Hero() {
        this.roles = new ArrayList<>();
        this.roleFrequency = new HashMap<>();
        this.abilities = new ArrayList<>();
        this.innateAbilities = new ArrayList<>();
        this.synergies = new HashMap<>();
        this.counters = new HashMap<>();
    }
    
    public Hero(int id, String name, String localizedName) {
        this();
        this.id = id;
        this.name = name;
        this.localizedName = localizedName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalizedName() {
        return localizedName;
    }

    public void setLocalizedName(String localizedName) {
        this.localizedName = localizedName;
    }

    public String getPrimaryAttribute() {
        return primaryAttribute;
    }

    public void setPrimaryAttribute(String primaryAttribute) {
        this.primaryAttribute = primaryAttribute;
    }

    public String getAttackType() {
        return attackType;
    }

    public void setAttackType(String attackType) {
        this.attackType = attackType;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public void addRole(String role) {
        if (this.roles == null) {
            this.roles = new ArrayList<>();
        }
        this.roles.add(role);
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Map<Integer, Double> getRoleFrequency() {
        // If the role frequency map is empty, populate it with default values
        if ((roleFrequency == null || roleFrequency.isEmpty()) && !hasDefaultRoleFrequency) {
            populateDefaultRoleFrequency();
        }
        return roleFrequency;
    }

    public void setRoleFrequency(Map<Integer, Double> roleFrequency) {
        this.roleFrequency = roleFrequency;
        this.hasDefaultRoleFrequency = false;
    }
    
    public void addRoleFrequency(int position, double frequency) {
        this.roleFrequency.put(position, frequency);
        this.hasDefaultRoleFrequency = false;
    }
    
    /**
     * Check if this hero is using default role frequency data
     * rather than data derived from match analysis
     */
    public boolean hasDefaultRoleFrequency() {
        return hasDefaultRoleFrequency;
    }
    
    /**
     * Populate the role frequency map with default position values
     * based on the hero's roles and attributes.
     */
    private void populateDefaultRoleFrequency() {
        if (roles == null || roles.isEmpty()) {
            // If no role information, provide a generic distribution
            roleFrequency.put(1, 0.2);
            roleFrequency.put(2, 0.2);
            roleFrequency.put(3, 0.2);
            roleFrequency.put(4, 0.2);
            roleFrequency.put(5, 0.2);
            hasDefaultRoleFrequency = true;
            return;
        }
        
        // Initialize all positions with a small baseline frequency
        for (int i = 1; i <= 5; i++) {
            roleFrequency.put(i, 0.05);
        }
        
        // Adjust frequencies based on role keywords
        if (hasRole("Carry") || hasRole("Hard Carry")) {
            roleFrequency.put(1, 0.6);
            roleFrequency.put(2, 0.2);
        }
        
        if (hasRole("Mid") || hasRole("Midlaner") || hasRole("Nuker")) {
            roleFrequency.put(2, 0.6);
        }
        
        if (hasRole("Offlaner") || hasRole("Durable") || hasRole("Initiator")) {
            roleFrequency.put(3, 0.6);
        }
        
        if (hasRole("Support") || hasRole("Disabler")) {
            roleFrequency.put(4, 0.4);
            roleFrequency.put(5, 0.4);
        }
        
        if (hasRole("Hard Support") || hasRole("Healer")) {
            roleFrequency.put(5, 0.6);
        }
        
        // Use primary attribute as a supplement to roles
        if (primaryAttribute != null) {
            if (primaryAttribute.equals("str") && roleFrequency.get(3) < 0.3) {
                roleFrequency.put(3, roleFrequency.get(3) + 0.2);
            } else if (primaryAttribute.equals("agi") && roleFrequency.get(1) < 0.3) {
                roleFrequency.put(1, roleFrequency.get(1) + 0.2);
            } else if (primaryAttribute.equals("int") && roleFrequency.get(5) < 0.3) {
                roleFrequency.put(5, roleFrequency.get(5) + 0.2);
            }
        }
        
        hasDefaultRoleFrequency = true;
    }
    
    /**
     * Helper method to check if hero has a specific role
     */
    private boolean hasRole(String role) {
        if (roles == null) return false;
        return roles.stream()
            .anyMatch(r -> r.equalsIgnoreCase(role));
    }

    public HeroAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(HeroAttributes attributes) {
        this.attributes = attributes;
    }

    public List<Ability> getAbilities() {
        return abilities;
    }

    public void setAbilities(List<Ability> abilities) {
        this.abilities = abilities;
    }
    
    public void addAbility(Ability ability) {
        if (this.abilities == null) {
            this.abilities = new ArrayList<>();
        }
        this.abilities.add(ability);
    }
    
    public List<InnateAbility> getInnateAbilities() {
        return innateAbilities;
    }
    
    public void setInnateAbilities(List<InnateAbility> innateAbilities) {
        this.innateAbilities = innateAbilities;
    }
    
    public void addInnateAbility(InnateAbility innateAbility) {
        if (this.innateAbilities == null) {
            this.innateAbilities = new ArrayList<>();
        }
        this.innateAbilities.add(innateAbility);
    }

    public Map<Integer, Double> getSynergies() {
        return synergies;
    }

    public void setSynergies(Map<Integer, Double> synergies) {
        this.synergies = synergies;
    }
    
    public void addSynergy(int heroId, double score) {
        this.synergies.put(heroId, score);
    }

    public Map<Integer, Double> getCounters() {
        return counters;
    }

    public void setCounters(Map<Integer, Double> counters) {
        this.counters = counters;
    }
    
    public void addCounter(int heroId, double score) {
        this.counters.put(heroId, score);
    }
    
    public double getSynergyScore(int heroId) {
        return synergies.getOrDefault(heroId, 0.0);
    }
    
    public double getCounterScore(int heroId) {
        return counters.getOrDefault(heroId, 0.0);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hero hero = (Hero) o;
        return id == hero.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return localizedName;
    }
}