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
 * Eclipsed emotion modifier that provides unique emotional responses
 * based on the void/dark nature of this role.
 */
public class EclipsedEmotionModifier extends BaseRoleEmotionModifier {
    
    public EclipsedEmotionModifier() {
        super(PetRoleType.ECLIPSED_ID, 10);
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
        
        // Eclipsed pets are mysterious and void-like in combat
        if (!isPetVictim) {
            // Owner or pet is dealing damage - Eclipsed draws from void power
            addEmotion(emotions, PetComponent.Emotion.YUGEN, scaleByDamage(0.35f, amount));
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.25f, amount));
            
            Petsplus.LOGGER.debug("Eclipsed pet {} drawing from void in combat", pet.getUuidAsString());
        }
        
        // When Eclipsed pet is damaged
        if (isPetVictim) {
            // Eclipsed shows mysterious resilience and void connection
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.4f, amount));
            addEmotion(emotions, PetComponent.Emotion.YUGEN, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.MELANCHOLY, scaleByDamage(0.2f, amount));
            
            // Remove overly emotional responses
            removeEmotion(emotions, PetComponent.Emotion.GLEE);
            removeEmotion(emotions, PetComponent.Emotion.KEFI);
            removeEmotion(emotions, PetComponent.Emotion.STARTLE);
        }
        
        // Enhanced response to shadow or void-related damage
        if (amount > 15.0f) {
            addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.25f);
            addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.2f);
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
                // Eclipsed pets appreciate mysterious, gentle interactions
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.15f);
                
                // Remove overly cheerful emotions
                removeEmotion(emotions, PetComponent.Emotion.GLEE);
                removeEmotion(emotions, PetComponent.Emotion.CHEERFUL);
            }
            
            case FEEDING -> {
                // Eclipsed pets see feeding as mysterious sustenance
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.15f);
            }
            
            case HEALING -> {
                // Eclipsed pets are stoic during healing, drawing from void energy
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.2f);
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
            case "darkness", "void", "end" -> {
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.3f);
            }
            case "shadows", "night" -> {
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.2f);
            }
        }
        
        return emotions;
    }
}