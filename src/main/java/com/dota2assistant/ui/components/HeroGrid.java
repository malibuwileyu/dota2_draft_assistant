package com.dota2assistant.ui.components;

import com.dota2assistant.domain.model.Attribute;
import com.dota2assistant.domain.model.Hero;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Grid of heroes organized by attribute columns.
 */
public class HeroGrid extends HBox {
    
    private final AttributeColumn strColumn = new AttributeColumn(Attribute.STRENGTH);
    private final AttributeColumn agiColumn = new AttributeColumn(Attribute.AGILITY);
    private final AttributeColumn intColumn = new AttributeColumn(Attribute.INTELLIGENCE);
    private final AttributeColumn uniColumn = new AttributeColumn(Attribute.UNIVERSAL);
    
    private Consumer<Hero> onHeroClick = h -> {};
    
    public HeroGrid() {
        setSpacing(8);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #0a0e14;");
        getChildren().addAll(strColumn, agiColumn, intColumn, uniColumn);
    }
    
    public void setOnHeroClick(Consumer<Hero> handler) {
        this.onHeroClick = handler;
        strColumn.setOnHeroClick(handler);
        agiColumn.setOnHeroClick(handler);
        intColumn.setOnHeroClick(handler);
        uniColumn.setOnHeroClick(handler);
    }
    
    public void setAllHeroes(List<Hero> heroes) {
        Map<Attribute, List<Hero>> byAttr = heroes.stream()
            .collect(Collectors.groupingBy(Hero::primaryAttribute));
        
        strColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.STRENGTH, List.of())));
        agiColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.AGILITY, List.of())));
        intColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.INTELLIGENCE, List.of())));
        uniColumn.setHeroes(sorted(byAttr.getOrDefault(Attribute.UNIVERSAL, List.of())));
    }
    
    public void update(List<Hero> availableHeroes) {
        Set<Integer> ids = availableHeroes.stream().map(Hero::id).collect(Collectors.toSet());
        strColumn.updateAvailability(ids);
        agiColumn.updateAvailability(ids);
        intColumn.updateAvailability(ids);
        uniColumn.updateAvailability(ids);
    }
    
    private List<Hero> sorted(List<Hero> heroes) {
        return heroes.stream()
            .sorted(Comparator.comparing(Hero::localizedName))
            .toList();
    }
}
