import com.dota2assistant.data.model.Ability;
import com.dota2assistant.data.repository.HeroAbilitiesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class SimpleAbilityTest {
    
    static class HeroAbilitiesContainer {
        private List<HeroAbilitiesData> heroes;
        
        public List<HeroAbilitiesData> getHeroes() {
            return heroes;
        }
        
        public void setHeroes(List<HeroAbilitiesData> heroes) {
            this.heroes = heroes;
        }
    }
    
    static class HeroAbilitiesData {
        private int id;
        private String name;
        @JsonProperty("localized_name")
        private String localizedName;
        private List<Ability> abilities;
        
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
        
        public List<Ability> getAbilities() {
            return abilities;
        }
        
        public void setAbilities(List<Ability> abilities) {
            this.abilities = abilities;
        }
    }
    
    public static void main(String[] args) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
            File file = new File("src/main/resources/data/abilities/axe_abilities.json");
            
            try {
                System.out.println("Testing deserialization of " + file.getName());
                
                HeroAbilitiesContainer container = objectMapper.readValue(file, HeroAbilitiesContainer.class);
                
                if (container != null && container.getHeroes() != null && !container.getHeroes().isEmpty()) {
                    HeroAbilitiesData heroData = container.getHeroes().get(0);
                    
                    System.out.println("Hero: " + heroData.getLocalizedName() + " (ID: " + heroData.getId() + ")");
                    System.out.println("Abilities count: " + (heroData.getAbilities() != null ? heroData.getAbilities().size() : 0));
                    
                    if (heroData.getAbilities() != null) {
                        for (Ability ability : heroData.getAbilities()) {
                            System.out.println("\nAbility: " + ability.getName());
                            System.out.println("Description: " + ability.getDescription());
                            System.out.println("Type: " + ability.getType());
                            System.out.println("Damage Type: " + ability.getDamageType());
                            System.out.println("Special Values: " + (ability.getSpecialValues() != null ? ability.getSpecialValues().size() : 0) + " entries");
                            
                            if (ability.getSpecialValues() != null && !ability.getSpecialValues().isEmpty()) {
                                System.out.println("First special value key: " + ability.getSpecialValues().keySet().iterator().next());
                            }
                        }
                    }
                    
                    System.out.println("\nDeserialization successful!");
                } else {
                    System.err.println("Failed to parse hero data from file");
                }
            } catch (Exception e) {
                System.err.println("Error deserializing " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}