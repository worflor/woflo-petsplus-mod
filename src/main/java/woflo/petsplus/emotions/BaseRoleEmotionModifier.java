package woflo.petsplus.emotions;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.EmotionContextMapper;

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
        // Default implementation returns base emotions unchanged
        return new HashMap<>(baseEmotions);
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
        // Default implementation returns base emotions unchanged
        return new HashMap<>(baseEmotions);
    }
    
    @Override
    public Map<PetComponent.Emotion, Float> modifyEnvironmentalEmotions(
        MobEntity pet,
        PetComponent petComp,
        String eventType,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        // Default implementation returns base emotions unchanged
        return new HashMap<>(baseEmotions);
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
        if (replace) {
            emotions.put(emotion, amount);
        } else {
            emotions.merge(emotion, amount, Float::sum);
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
        return source.isOf(net.minecraft.entity.damage.DamageTypes.MAGIC) ||
               source.isOf(net.minecraft.entity.damage.DamageTypes.INDIRECT_MAGIC) ||
               source.isOf(net.minecraft.entity.damage.DamageTypes.WITHER) ||
               source.isOf(net.minecraft.entity.damage.DamageTypes.DRAGON_BREATH);
    }
    
    /**
     * Scale emotion intensity based on pet level.
     * 
     * @param baseAmount the base emotion amount
     * @param petComp the pet component
     * @return the scaled emotion amount
     */
    protected float scaleByLevel(float baseAmount, PetComponent petComp) {
        int level = petComp.getLevel();
        // Scale emotions with level, but with diminishing returns
        return baseAmount * (1.0f + (float) Math.log(level + 1) / 4.0f);
    }
    
    /**
     * Scale emotion intensity based on damage amount.
     * 
     * @param baseAmount the base emotion amount
     * @param damageAmount the damage amount
     * @return the scaled emotion amount
     */
    protected float scaleByDamage(float baseAmount, float damageAmount) {
        // Scale emotions with damage, but cap at reasonable limits
        float damageFactor = Math.min(damageAmount / 10.0f, 2.0f);
        return baseAmount * damageFactor;
    }
}