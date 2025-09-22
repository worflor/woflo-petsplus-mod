package woflo.petsplus.api;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Represents a pet ability with triggers and effects.
 */
public class Ability {
    private final Identifier id;
    private final Trigger trigger;
    private final List<Effect> effects;
    private final JsonObject config;
    
    public Ability(Identifier id, Trigger trigger, List<Effect> effects, JsonObject config) {
        this.id = id;
        this.trigger = trigger;
        this.effects = effects;
        this.config = config;
    }
    
    public Identifier getId() {
        return id;
    }
    
    public Trigger getTrigger() {
        return trigger;
    }
    
    public List<Effect> getEffects() {
        return effects;
    }
    
    public JsonObject getConfig() {
        return config;
    }
    
    /**
     * Attempts to activate this ability given the trigger context.
     * @param context The trigger context
     * @return true if the ability was activated
     */
    public boolean tryActivate(TriggerContext context) {
        // Check pet level requirement first
        if (context.getPet() == null) {
            return false; // No pet, cannot activate
        }

        PetComponent petComp = PetComponent.get(context.getPet());
        if (petComp != null) {
            int requiredLevel = getRequiredLevel();
            if (!petComp.hasLevel(requiredLevel)) {
                return false; // Pet doesn't have required level
            }
        }
        
        if (!trigger.shouldActivate(context)) {
            return false;
        }
        
        EffectContext effectContext = new EffectContext(
            context.getWorld(),
            context.getPet(),
            context.getOwner(),
            context
        );
        
        boolean anySucceeded = false;
        for (Effect effect : effects) {
            if (effect.execute(effectContext)) {
                anySucceeded = true;
            }
        }
        
        return anySucceeded;
    }
    
    /**
     * Get the required level for this ability.
     * Reads from the config or returns 1 (default).
     */
    public int getRequiredLevel() {
        if (config != null && config.has("required_level")) {
            return config.get("required_level").getAsInt();
        }
        return 1; // Default level requirement
    }
}