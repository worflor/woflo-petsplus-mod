package woflo.petsplus.api;

import net.minecraft.util.Identifier;

/**
 * Represents a pet role with associated abilities and behavior.
 */
public enum PetRole {
    GUARDIAN(Identifier.of("petsplus", "guardian")),
    STRIKER(Identifier.of("petsplus", "striker")),
    SUPPORT(Identifier.of("petsplus", "support")),
    SCOUT(Identifier.of("petsplus", "scout")),
    SKYRIDER(Identifier.of("petsplus", "skyrider")),
    ENCHANTMENT_BOUND(Identifier.of("petsplus", "enchantment_bound")),
    CURSED_ONE(Identifier.of("petsplus", "cursed_one")),
    EEPY_EEPER(Identifier.of("petsplus", "eepy_eeper")),
    ECLIPSED(Identifier.of("petsplus", "eclipsed"));
    
    private final Identifier id;
    
    PetRole(Identifier id) {
        this.id = id;
    }
    
    public Identifier getId() {
        return id;
    }
    
    public String getKey() {
        return id.getPath();
    }
    
    public String getDisplayName() {
        return switch (this) {
            case GUARDIAN -> "Guardian";
            case STRIKER -> "Striker";
            case SUPPORT -> "Support";
            case SCOUT -> "Scout";
            case SKYRIDER -> "Skyrider";
            case ENCHANTMENT_BOUND -> "Enchantment Bound";
            case CURSED_ONE -> "Cursed One";
            case EEPY_EEPER -> "Eepy Eeper";
            case ECLIPSED -> "Eclipsed";
        };
    }
    
    public static PetRole fromKey(String key) {
        for (PetRole role : values()) {
            if (role.getKey().equals(key)) {
                return role;
            }
        }
        return null;
    }
}