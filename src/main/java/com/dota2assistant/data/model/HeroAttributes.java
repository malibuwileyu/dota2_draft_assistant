package com.dota2assistant.data.model;

public class HeroAttributes {
    private double baseStrength;
    private double baseAgility;
    private double baseIntelligence;
    private double strengthGain;
    private double agilityGain;
    private double intelligenceGain;
    private int moveSpeed;
    private double armor;
    private double attackDamageMin;
    private double attackDamageMax;
    private double attackRate;
    private int attackRange;
    
    public HeroAttributes() {
    }

    public double getBaseStrength() {
        return baseStrength;
    }

    public void setBaseStrength(double baseStrength) {
        this.baseStrength = baseStrength;
    }

    public double getBaseAgility() {
        return baseAgility;
    }

    public void setBaseAgility(double baseAgility) {
        this.baseAgility = baseAgility;
    }

    public double getBaseIntelligence() {
        return baseIntelligence;
    }

    public void setBaseIntelligence(double baseIntelligence) {
        this.baseIntelligence = baseIntelligence;
    }

    public double getStrengthGain() {
        return strengthGain;
    }

    public void setStrengthGain(double strengthGain) {
        this.strengthGain = strengthGain;
    }

    public double getAgilityGain() {
        return agilityGain;
    }

    public void setAgilityGain(double agilityGain) {
        this.agilityGain = agilityGain;
    }

    public double getIntelligenceGain() {
        return intelligenceGain;
    }

    public void setIntelligenceGain(double intelligenceGain) {
        this.intelligenceGain = intelligenceGain;
    }

    public int getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(int moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    public double getArmor() {
        return armor;
    }

    public void setArmor(double armor) {
        this.armor = armor;
    }

    public double getAttackDamageMin() {
        return attackDamageMin;
    }

    public void setAttackDamageMin(double attackDamageMin) {
        this.attackDamageMin = attackDamageMin;
    }

    public double getAttackDamageMax() {
        return attackDamageMax;
    }

    public void setAttackDamageMax(double attackDamageMax) {
        this.attackDamageMax = attackDamageMax;
    }

    public double getAttackRate() {
        return attackRate;
    }

    public void setAttackRate(double attackRate) {
        this.attackRate = attackRate;
    }

    public int getAttackRange() {
        return attackRange;
    }

    public void setAttackRange(int attackRange) {
        this.attackRange = attackRange;
    }
}