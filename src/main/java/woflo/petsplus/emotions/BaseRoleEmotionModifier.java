package woflo.petsplus.emotions;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.EmotionContextMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base abstract class for role emotion modifiers that provides common functionality
 * and utility methods for implementing role-specific emotional responses.
 */
public abstract class BaseRoleEmotionModifier implements RoleEmotionModifier {
    
    private final Identifier roleId;
    private final int priority;
    
    protected BaseRoleEmotionModifier(Identifier roleId, int priority) {
        this.roleId = roleId;
        this.priority = priority;
    }
    
    protected BaseRoleEmotionModifier(Identifier roleId) {
        this(roleId, 0);
    }
    
    @Override
    public Identifier getRoleId() {
        return roleId;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public Map<PetComponent.Emotion, Float> modifyCombatEmotions(
        MobEntity pet,
        PetComponent petComp,
        DamageSource source,
        float amount,
        boolean isOwnerAttacker,
        boolean isPetVictim,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        // Input validation
        if (pet == null || petComp == null || baseEmotions == null) {
            return Collections.emptyMap();
        }
        
        // Validate amount is not NaN or infinite
        if (Float.isNaN(amount) || Float.isInfinite(amount)) {
            amount = 0.0f;
        }
        
        // Default implementation returns base emotions unchanged
        return Collections.unmodifiableMap(new HashMap<>(baseEmotions));
    }
    
    @Override
    public Map<PetComponent.Emotion, Float> modifySocialEmotions(
        MobEntity pet,
        PetComponent petComp,
        PlayerEntity player,
        EmotionContextMapper.SocialInteractionType type,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        // Input validation
        if (pet == null || petComp == null || type == null || baseEmotions == null) {
            return Collections.emptyMap();
        }
        
        // Default implementation returns base emotions unchanged
        return Collections.unmodifiableMap(new HashMap<>(baseEmotions));
    }
    
    @Override
    public Map<PetComponent.Emotion, Float> modifyEnvironmentalEmotions(
        MobEntity pet,
        PetComponent petComp,
        String eventType,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        // Input validation
        if (pet == null || petComp == null || eventType == null || baseEmotions == null) {
            return Collections.emptyMap();
        }
        
        // Default implementation returns base emotions unchanged
        return Collections.unmodifiableMap(new HashMap<>(baseEmotions));
    }
    
    /**
     * Utility method to add or modify an emotion in the emotion map.
     * 
     * @param emotions the emotion map to modify
     * @param emotion the emotion to add/modify
     * @param amount the emotion amount
     * @param replace whether to replace existing value (true) or add to it (false)
     */
    protected void modifyEmotion(Map<PetComponent.Emotion, Float> emotions,
                                PetComponent.Emotion emotion, float amount, boolean replace) {
        // Null safety checks
        if (emotions == null || emotion == null) {
            return;
        }
        
        // Validate amount is not NaN or infinite
        if (Float.isNaN(amount) || Float.isInfinite(amount)) {
            return;
        }
        
        // Bounds checking for amount
        amount = Math.max(-100.0f, Math.min(100.0f, amount));
        
        if (replace) {
            emotions.put(emotion, amount);
        } else {
            emotions.merge(emotion, amount, Float::sum);
            // Ensure the result is also bounded
            emotions.computeIfPresent(emotion, (k, v) ->
                Math.max(-100.0f, Math.min(100.0f, v)));
        }
    }
    
    /**
     * Utility method to add an emotion to the emotion map (adds to existing value).
     * 
     * @param emotions the emotion map to modify
     * @param emotion the emotion to add
     * @param amount the emotion amount
     */
    protected void addEmotion(Map<PetComponent.Emotion, Float> emotions, 
                             PetComponent.Emotion emotion, float amount) {
        modifyEmotion(emotions, emotion, amount, false);
    }
    
    /**
     * Utility method to set an emotion in the emotion map (replaces existing value).
     * 
     * @param emotions the emotion map to modify
     * @param emotion the emotion to set
     * @param amount the emotion amount
     */
    protected void setEmotion(Map<PetComponent.Emotion, Float> emotions, 
                             PetComponent.Emotion emotion, float amount) {
        modifyEmotion(emotions, emotion, amount, true);
    }
    
    /**
     * Utility method to remove an emotion from the emotion map.
     * 
     * @param emotions the emotion map to modify
     * @param emotion the emotion to remove
     */
    protected void removeEmotion(Map<PetComponent.Emotion, Float> emotions,
                                PetComponent.Emotion emotion) {
        // Null safety checks
        if (emotions == null || emotion == null) {
            return;
        }
        emotions.remove(emotion);
    }
    
    /**
     * Check if the damage source is from the owner.
     * 
     * @param source the damage source
     * @param petComp the pet component
     * @return true if the damage source is from the owner
     */
    protected boolean isOwnerDamageSource(DamageSource source, PetComponent petComp) {
        // Null safety checks
        if (source == null || petComp == null) {
            return false;
        }
        
        PlayerEntity owner = petComp.getOwner();
        return owner != null && source.getAttacker() == owner;
    }
    
    /**
     * Check if the damage is magical in nature.
     * 
     * @param source the damage source
     * @return true if the damage is magical
     */
    protected boolean isMagicalDamage(DamageSource source) {
        // Null safety check
        if (source == null) {
            return false;
        }
        
        return source.isOf(DamageTypes.MAGIC) ||
               source.isOf(DamageTypes.INDIRECT_MAGIC) ||
               source.isOf(DamageTypes.WITHER) ||
               source.isOf(DamageTypes.DRAGON_BREATH) ||
               source.isOf(DamageTypes.THORNS) ||
               source.isOf(DamageTypes.SONIC_BOOM) ||
               source.isOf(DamageTypes.FREEZE) ||
               source.isOf(DamageTypes.ON_FIRE) ||
               source.isOf(DamageTypes.IN_FIRE) ||
               source.isOf(DamageTypes.LAVA) ||
               source.isOf(DamageTypes.HOT_FLOOR) ||
               source.isOf(DamageTypes.CAMPFIRE) ||
               source.isOf(DamageTypes.FIREBALL) ||
               source.isOf(DamageTypes.UNATTRIBUTED_FIREBALL);
    }
    
    /**
     * Scale emotion intensity based on pet level.
     * 
     * @param baseAmount the base emotion amount
     * @param petComp the pet component
     * @return the scaled emotion amount
     */
    protected float scaleByLevel(float baseAmount, PetComponent petComp) {
        // Input validation
        if (petComp == null) {
            return baseAmount;
        }
        
        // Validate baseAmount is not NaN or infinite
        if (Float.isNaN(baseAmount) || Float.isInfinite(baseAmount)) {
            return 0.0f;
        }
        
        int level = petComp.getLevel();
        // Fixed mathematical formula for consistent scaling
        // Using a more stable logarithmic scaling with proper bounds
        float scaleFactor = 1.0f + (float) Math.log1p(level) / 4.0f;
        // Ensure scale factor is reasonable
        scaleFactor = Math.max(0.1f, Math.min(3.0f, scaleFactor));
        
        return baseAmount * scaleFactor;
    }
    
    /**
     * Scale emotion intensity based on damage amount.
     * 
     * @param baseAmount the base emotion amount
     * @param damageAmount the damage amount
     * @return the scaled emotion amount
     */
    protected float scaleByDamage(float baseAmount, float damageAmount) {
        // Input validation
        if (Float.isNaN(baseAmount) || Float.isInfinite(baseAmount)) {
            return 0.0f;
        }
        
        if (Float.isNaN(damageAmount) || Float.isInfinite(damageAmount)) {
            return baseAmount;
        }
        
        // Handle negative damage amounts by using absolute value
        float absDamage = Math.abs(damageAmount);
        
        // Scale emotions with damage, but cap at reasonable limits
        // Fixed division by zero issue
        float damageFactor = absDamage > 0.0f ? Math.min(absDamage / 10.0f, 2.0f) : 0.0f;
        
        return baseAmount * damageFactor;
    }
}
