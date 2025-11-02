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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registry that manages role-based emotion modifiers.
 * Provides centralized access to all role emotion modifiers and handles
 * the application of modifiers to emotion processing.
 */
public class RoleEmotionRegistry {
    
    private static final RoleEmotionRegistry INSTANCE = new RoleEmotionRegistry();
    
    private final Map<Identifier, List<RoleEmotionModifier>> modifiersByRole = new ConcurrentHashMap<>();
    private final List<RoleEmotionModifier> allModifiers = new CopyOnWriteArrayList<>();
    private volatile boolean initialized = false;
    
    private final ReentrantLock registerLock = new ReentrantLock();
    private final ReentrantLock initializeLock = new ReentrantLock();
    
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
        // Fast path check without synchronization for already initialized case
        if (initialized) {
            return;
        }
        
        initializeLock.lock();
        try {
            // Double-check before initializing
            if (initialized) {
                return;
            }
            
            Petsplus.LOGGER.info("Initializing RoleEmotionRegistry");
            
            // Register built-in role modifiers
            registerBuiltinModifiers();
            
            // Mark initialized
            initialized = true;
            Petsplus.LOGGER.info("RoleEmotionRegistry initialized with {} modifiers", allModifiers.size());
        } finally {
            initializeLock.unlock();
        }
    }
    
    /**
     * Register built-in modifiers during initialization.
     * This method is called while holding the initializeLock to ensure thread safety.
     */
    private void registerBuiltinModifiers() {
        // Direct registration without additional locking since we're already under initializeLock
        registerInternal(new CursedOneEmotionModifier());
        registerInternal(new EnchantmentBoundEmotionModifier());
        registerInternal(new GuardianEmotionModifier());
        registerInternal(new StrikerEmotionModifier());
        registerInternal(new SupportEmotionModifier());
        registerInternal(new ScoutEmotionModifier());
        registerInternal(new SkyriderEmotionModifier());
        registerInternal(new EepyEeperEmotionModifier());
        registerInternal(new EclipsedEmotionModifier());
    }
    
    /**
     * Internal registration method that doesn't acquire additional locks.
     * Used during initialization when initializeLock is already held.
     *
     * @param modifier the modifier to register
     */
    private void registerInternal(RoleEmotionModifier modifier) {
        Identifier roleId = modifier.getRoleId();
        
        // Get or create role-specific modifiers list
        List<RoleEmotionModifier> roleModifiers = modifiersByRole.computeIfAbsent(roleId, k -> new ArrayList<>());
        
        // Snapshot to avoid concurrent modification during sorting
        List<RoleEmotionModifier> newRoleModifiers = new ArrayList<>(roleModifiers);
        newRoleModifiers.add(modifier);
        
        // Sort modifiers by priority (highest first)
        newRoleModifiers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        // Replace the list atomically
        modifiersByRole.put(roleId, newRoleModifiers);
        
        // Add to global list (CopyOnWriteArrayList handles thread safety)
        allModifiers.add(modifier);
        
        Petsplus.LOGGER.debug("Registered role emotion modifier for role: {}", roleId);
    }
    
    /**
     * Register a role emotion modifier.
     *
     * @param modifier the modifier to register
     */
    public void register(RoleEmotionModifier modifier) {
        registerLock.lock();
        try {
            Identifier roleId = modifier.getRoleId();
            
            // Get or create role-specific modifiers list
            List<RoleEmotionModifier> roleModifiers = modifiersByRole.computeIfAbsent(roleId, k -> new ArrayList<>());
            
            // Snapshot to avoid concurrent modification during sorting
            List<RoleEmotionModifier> newRoleModifiers = new ArrayList<>(roleModifiers);
            newRoleModifiers.add(modifier);
            
            // Sort modifiers by priority (highest first)
            newRoleModifiers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            
            // Replace the list atomically
            modifiersByRole.put(roleId, newRoleModifiers);
            
            // Add to global list (CopyOnWriteArrayList handles thread safety)
            allModifiers.add(modifier);
            
            Petsplus.LOGGER.debug("Registered role emotion modifier for role: {}", roleId);
        } finally {
            registerLock.unlock();
        }
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
        
        // Snapshot to prevent concurrent modification
        List<RoleEmotionModifier> modifiersSnapshot = new ArrayList<>(allModifiers);
        
        // Check all modifiers to see if they should apply
        for (RoleEmotionModifier modifier : modifiersSnapshot) {
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
        registerLock.lock();
        try {
            modifiersByRole.clear();
            allModifiers.clear();
            initialized = false;
        } finally {
            registerLock.unlock();
        }
    }
    
    /**
     * Shutdown the registry and clean up resources.
     */
    public void shutdown() {
        // Lock ordering: registerLock before initializeLock
        registerLock.lock();
        try {
            initializeLock.lock();
            try {
                Petsplus.LOGGER.info("Shutting down RoleEmotionRegistry");
                
                // Clear all data structures
                modifiersByRole.clear();
                allModifiers.clear();
                
                // Reset initialization state
                initialized = false;
                
                Petsplus.LOGGER.info("RoleEmotionRegistry shutdown complete");
            } finally {
                initializeLock.unlock();
            }
        } finally {
            registerLock.unlock();
        }
    }
}
