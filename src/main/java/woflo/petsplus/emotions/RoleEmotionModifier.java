package woflo.petsplus.emotions;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.events.EmotionContextMapper;

import java.util.Map;

/**
 * Interface for role-specific emotion modifiers that allow pets to have unique
 * emotional responses to certain events based on their role.
 * 
 * This system is separate from the personality system and provides immersive
 * role-specific behaviors like cursed_one enjoying owner hits or enchantment-bound
 * liking enchantments.
 */
public interface RoleEmotionModifier {
    
    /**
     * Get the role identifier this modifier applies to.
     * 
     * @return the role identifier
     */
    Identifier getRoleId();
    
    /**
     * Modify emotions for combat damage events based on the pet's role.
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
    Map<PetComponent.Emotion, Float> modifyCombatEmotions(
        MobEntity pet,
        PetComponent petComp,
        DamageSource source,
        float amount,
        boolean isOwnerAttacker,
        boolean isPetVictim,
        Map<PetComponent.Emotion, Float> baseEmotions
    );
    
    /**
     * Modify emotions for social interaction events based on the pet's role.
     * 
     * @param pet the pet entity
     * @param petComp the pet component
     * @param player the interacting player
     * @param type the type of social interaction
     * @param context the interaction context
     * @param baseEmotions the base emotions from the standard system
     * @return modified emotions with role-specific adjustments
     */
    Map<PetComponent.Emotion, Float> modifySocialEmotions(
        MobEntity pet,
        PetComponent petComp,
        PlayerEntity player,
        EmotionContextMapper.SocialInteractionType type,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    );
    
    /**
     * Modify emotions for environmental events based on the pet's role.
     * 
     * @param pet the pet entity
     * @param petComp the pet component
     * @param eventType the type of environmental event
     * @param context the event context
     * @param baseEmotions the base emotions from the standard system
     * @return modified emotions with role-specific adjustments
     */
    Map<PetComponent.Emotion, Float> modifyEnvironmentalEmotions(
        MobEntity pet,
        PetComponent petComp,
        String eventType,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    );
    
    /**
     * Check if this modifier should apply to the given pet.
     * This allows for additional conditions beyond just role type.
     * 
     * @param pet the pet entity
     * @param petComp the pet component
     * @return true if this modifier should apply
     */
    default boolean shouldApply(MobEntity pet, PetComponent petComp) {
        return petComp.hasRole(getRoleId());
    }
    
    /**
     * Get the priority of this modifier. Higher values are applied later.
     * This allows modifiers to override each other in a controlled way.
     * 
     * @return the priority value (default 0)
     */
    default int getPriority() {
        return 0;
    }
}
