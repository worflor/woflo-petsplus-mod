package woflo.petsplus.emotions;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.EmotionContextMapper;
import woflo.petsplus.emotions.modifiers.CursedOneEmotionModifier;
import woflo.petsplus.emotions.modifiers.EnchantmentBoundEmotionModifier;
import woflo.petsplus.emotions.modifiers.GuardianEmotionModifier;
import woflo.petsplus.emotions.modifiers.StrikerEmotionModifier;
import woflo.petsplus.emotions.modifiers.SupportEmotionModifier;
import woflo.petsplus.emotions.modifiers.ScoutEmotionModifier;
import woflo.petsplus.emotions.modifiers.SkyriderEmotionModifier;
import woflo.petsplus.emotions.modifiers.EepyEeperEmotionModifier;
import woflo.petsplus.emotions.modifiers.EclipsedEmotionModifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that manages role-based emotion modifiers.
 * Provides centralized access to all role emotion modifiers and handles
 * the application of modifiers to emotion processing.
 */
public class RoleEmotionRegistry {
    
    private static final RoleEmotionRegistry INSTANCE = new RoleEmotionRegistry();
    
    private final Map<Identifier, List<RoleEmotionModifier>> modifiersByRole = new ConcurrentHashMap<>();
    private final List<RoleEmotionModifier> allModifiers = new ArrayList<>();
    private boolean initialized = false;
    
    private RoleEmotionRegistry() {}
    
    /**
     * Get the singleton instance of the registry.
     * 
     * @return the registry instance
     */
    public static RoleEmotionRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the registry and register all built-in modifiers.
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        
        Petsplus.LOGGER.info("Initializing RoleEmotionRegistry");
        
        // Register built-in role modifiers
        register(new CursedOneEmotionModifier());
        register(new EnchantmentBoundEmotionModifier());
        register(new GuardianEmotionModifier());
        register(new StrikerEmotionModifier());
        register(new SupportEmotionModifier());
        register(new ScoutEmotionModifier());
        register(new SkyriderEmotionModifier());
        register(new EepyEeperEmotionModifier());
        register(new EclipsedEmotionModifier());
        
        initialized = true;
        Petsplus.LOGGER.info("RoleEmotionRegistry initialized with {} modifiers", allModifiers.size());
    }
    
    /**
     * Register a role emotion modifier.
     * 
     * @param modifier the modifier to register
     */
    public void register(RoleEmotionModifier modifier) {
        Identifier roleId = modifier.getRoleId();
        
        // Add to role-specific list
        modifiersByRole.computeIfAbsent(roleId, k -> new ArrayList<>()).add(modifier);
        
        // Sort modifiers by priority (highest first)
        modifiersByRole.get(roleId).sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        // Add to global list
        allModifiers.add(modifier);
        
        // Sort global list by priority
        allModifiers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        Petsplus.LOGGER.debug("Registered role emotion modifier for role: {}", roleId);
    }
    
    /**
     * Apply role-based modifications to combat emotions.
     * 
     * @param pet the pet entity
     * @param petComp the pet component
     * @param source the damage source
     * @param amount the damage amount
     * @param isOwnerAttacker whether the owner is the attacker
     * @param isPetVictim whether the pet is the victim
     * @param baseEmotions the base emotions from the standard system
     * @return modified emotions with role-specific adjustments
     */
    public Map<PetComponent.Emotion, Float> applyCombatModifiers(
        MobEntity pet,
        PetComponent petComp,
        DamageSource source,
        float amount,
        boolean isOwnerAttacker,
        boolean isPetVictim,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        Map<PetComponent.Emotion, Float> result = new HashMap<>(baseEmotions);
        
        // Get applicable modifiers for this pet's role
        List<RoleEmotionModifier> applicableModifiers = getApplicableModifiers(pet, petComp);
        
        // Apply modifiers in priority order
        for (RoleEmotionModifier modifier : applicableModifiers) {
            try {
                Map<PetComponent.Emotion, Float> modified = modifier.modifyCombatEmotions(
                    pet, petComp, source, amount, isOwnerAttacker, isPetVictim, result
                );
                result = modified;
            } catch (Exception e) {
                Petsplus.LOGGER.error("Error applying combat emotion modifier for role {}", 
                    modifier.getRoleId(), e);
            }
        }
        
        return result;
    }
    
    /**
     * Apply role-based modifications to social emotions.
     * 
     * @param pet the pet entity
     * @param petComp the pet component
     * @param player the interacting player
     * @param type the type of social interaction
     * @param context the interaction context
     * @param baseEmotions the base emotions from the standard system
     * @return modified emotions with role-specific adjustments
     */
    public Map<PetComponent.Emotion, Float> applySocialModifiers(
        MobEntity pet,
        PetComponent petComp,
        PlayerEntity player,
        EmotionContextMapper.SocialInteractionType type,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        Map<PetComponent.Emotion, Float> result = new HashMap<>(baseEmotions);
        
        // Get applicable modifiers for this pet's role
        List<RoleEmotionModifier> applicableModifiers = getApplicableModifiers(pet, petComp);
        
        // Apply modifiers in priority order
        for (RoleEmotionModifier modifier : applicableModifiers) {
            try {
                Map<PetComponent.Emotion, Float> modified = modifier.modifySocialEmotions(
                    pet, petComp, player, type, context, result
                );
                result = modified;
            } catch (Exception e) {
                Petsplus.LOGGER.error("Error applying social emotion modifier for role {}", 
                    modifier.getRoleId(), e);
            }
        }
        
        return result;
    }
    
    /**
     * Apply role-based modifications to environmental emotions.
     * 
     * @param pet the pet entity
     * @param petComp the pet component
     * @param eventType the type of environmental event
     * @param context the event context
     * @param baseEmotions the base emotions from the standard system
     * @return modified emotions with role-specific adjustments
     */
    public Map<PetComponent.Emotion, Float> applyEnvironmentalModifiers(
        MobEntity pet,
        PetComponent petComp,
        String eventType,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        Map<PetComponent.Emotion, Float> result = new HashMap<>(baseEmotions);
        
        // Get applicable modifiers for this pet's role
        List<RoleEmotionModifier> applicableModifiers = getApplicableModifiers(pet, petComp);
        
        // Apply modifiers in priority order
        for (RoleEmotionModifier modifier : applicableModifiers) {
            try {
                Map<PetComponent.Emotion, Float> modified = modifier.modifyEnvironmentalEmotions(
                    pet, petComp, eventType, context, result
                );
                result = modified;
            } catch (Exception e) {
                Petsplus.LOGGER.error("Error applying environmental emotion modifier for role {}", 
                    modifier.getRoleId(), e);
            }
        }
        
        return result;
    }
    
    /**
     * Get all applicable modifiers for a pet.
     * 
     * @param pet the pet entity
     * @param petComp the pet component
     * @return list of applicable modifiers in priority order
     */
    private List<RoleEmotionModifier> getApplicableModifiers(MobEntity pet, PetComponent petComp) {
        List<RoleEmotionModifier> applicable = new ArrayList<>();
        
        // Check all modifiers to see if they should apply
        for (RoleEmotionModifier modifier : allModifiers) {
            if (modifier.shouldApply(pet, petComp)) {
                applicable.add(modifier);
            }
        }
        
        // Sort by priority (highest first)
        applicable.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        return applicable;
    }
    
    /**
     * Get all registered modifiers for a specific role.
     * 
     * @param roleId the role identifier
     * @return list of modifiers for the role
     */
    public List<RoleEmotionModifier> getModifiersForRole(Identifier roleId) {
        return modifiersByRole.getOrDefault(roleId, Collections.emptyList());
    }
    
    /**
     * Get all registered modifiers.
     * 
     * @return list of all modifiers
     */
    public List<RoleEmotionModifier> getAllModifiers() {
        return new ArrayList<>(allModifiers);
    }
    
    /**
     * Check if the registry has been initialized.
     * 
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Clear all registered modifiers (for testing purposes).
     */
    public void clear() {
        modifiersByRole.clear();
        allModifiers.clear();
        initialized = false;
    }
}