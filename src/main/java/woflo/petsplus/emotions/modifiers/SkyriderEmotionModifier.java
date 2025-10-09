package woflo.petsplus.emotions.modifiers;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.emotions.BaseRoleEmotionModifier;
import woflo.petsplus.events.EmotionContextMapper;
import woflo.petsplus.state.PetComponent;

import java.util.Map;

/**
 * Skyrider emotion modifier that provides unique emotional responses
 * based on the flying nature of this role.
 */
public class SkyriderEmotionModifier extends BaseRoleEmotionModifier {
    
    // Constants for magic numbers
    private static final float HIGH_PRIORITY = 10.0f;
    private static final float AERIAL_ADVANTAGE_THRESHOLD = 10.0f;
    private static final float AERIAL_ADVANTAGE_CHEERFUL_SCALE = 0.2f;
    private static final float AERIAL_ADVANTAGE_CONTENT_SCALE = 0.15f;
    
    public SkyriderEmotionModifier() {
        super(PetRoleType.SKYRIDER_ID, (int) HIGH_PRIORITY);
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
        Map<PetComponent.Emotion, Float> emotions = super.modifyCombatEmotions(
            pet, petComp, source, amount, isOwnerAttacker, isPetVictim, baseEmotions
        );
        
        // Skyrider pets are agile and free in combat
        if (!isPetVictim) {
            // Owner or pet is dealing damage - Skyrider feels free and joyful
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, scaleByDamage(0.35f, amount));
            addEmotion(emotions, PetComponent.Emotion.FERNWEH, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.FOCUSED, scaleByDamage(0.2f, amount));
            
            Petsplus.LOGGER.debug("Skyrider pet {} soaring in combat", pet.getUuidAsString());
        }
        
        // When Skyrider pet is damaged
        if (isPetVictim) {
            // Skyrider shows resilience and desire for freedom
            addEmotion(emotions, PetComponent.Emotion.FERNWEH, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.HOPEFUL, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.SISU, scaleByDamage(0.2f, amount));
            
            // Remove grounded emotions
            removeEmotion(emotions, PetComponent.Emotion.ANGST);
            removeEmotion(emotions, PetComponent.Emotion.FOREBODING);
        }
        
        // Enhanced response to aerial combat or high ground advantage
        if (amount > AERIAL_ADVANTAGE_THRESHOLD) {
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, AERIAL_ADVANTAGE_CHEERFUL_SCALE);
            addEmotion(emotions, PetComponent.Emotion.CONTENT, AERIAL_ADVANTAGE_CONTENT_SCALE);
        }
        
        return emotions;
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
        Map<PetComponent.Emotion, Float> emotions = super.modifySocialEmotions(
            pet, petComp, player, type, context, baseEmotions
        );
        
        switch (type) {
            case PETTING -> {
                // Skyrider pets enjoy light, free-flowing interactions
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.15f);
                
                // Less restrictive emotions
                removeEmotion(emotions, PetComponent.Emotion.STOIC);
                removeEmotion(emotions, PetComponent.Emotion.LAGOM);
            }
            
            case FEEDING -> {
                // Skyrider pets see feeding as fuel for flight
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.FERNWEH, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.15f);
            }
            
            case HEALING -> {
                // Skyrider pets are eager to get back to flying
                addEmotion(emotions, PetComponent.Emotion.RESTLESS, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.FERNWEH, 0.1f);
            }
        }
        
        return emotions;
    }
    
    @Override
    public Map<PetComponent.Emotion, Float> modifyEnvironmentalEmotions(
        MobEntity pet,
        PetComponent petComp,
        String eventType,
        Object context,
        Map<PetComponent.Emotion, Float> baseEmotions
    ) {
        Map<PetComponent.Emotion, Float> emotions = super.modifyEnvironmentalEmotions(
            pet, petComp, eventType, context, baseEmotions
        );
        
        switch (eventType.toLowerCase()) {
            case "flying", "flight", "air" -> {
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.FERNWEH, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
            }
            case "heights", "mountains", "sky" -> {
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.2f);
            }
        }
        
        return emotions;
    }
}
