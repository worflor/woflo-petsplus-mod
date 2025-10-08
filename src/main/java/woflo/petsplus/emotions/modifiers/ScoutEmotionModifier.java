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
 * Scout emotion modifier that provides unique emotional responses
 * based on the exploratory nature of this role.
 */
public class ScoutEmotionModifier extends BaseRoleEmotionModifier {
    
    // Constants for magic numbers
    private static final float HIGH_PRIORITY = 10.0f;
    private static final float WEAKNESS_DISCOVERY_THRESHOLD = 8.0f;
    private static final float WEAKNESS_DISCOVERY_CURIOSITY_SCALE = 0.2f;
    private static final float WEAKNESS_DISCOVERY_HOPE_SCALE = 0.15f;
    
    public ScoutEmotionModifier() {
        super(PetRoleType.SCOUT_ID, (int) HIGH_PRIORITY);
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
        
        // Scout pets are alert and cautious in combat
        if (!isPetVictim) {
            // Owner or pet is dealing damage - Scout gets focused and vigilant
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.FOCUSED, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.CURIOUS, scaleByDamage(0.2f, amount));
            
            Petsplus.LOGGER.debug("Scout pet {} alert during combat", pet.getUuidAsString());
        }
        
        // When Scout pet is damaged
        if (isPetVictim) {
            // Scout shows caution and awareness rather than aggression
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.35f, amount));
            addEmotion(emotions, PetComponent.Emotion.WORRIED, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.CURIOUS, scaleByDamage(0.2f, amount));
            
            // Remove overly aggressive emotions
            removeEmotion(emotions, PetComponent.Emotion.KEFI);
            removeEmotion(emotions, PetComponent.Emotion.GLEE);
        }
        
        // Enhanced response to discovering enemy weaknesses
        if (amount > WEAKNESS_DISCOVERY_THRESHOLD) {
            addEmotion(emotions, PetComponent.Emotion.CURIOUS, WEAKNESS_DISCOVERY_CURIOSITY_SCALE);
            addEmotion(emotions, PetComponent.Emotion.HOPEFUL, WEAKNESS_DISCOVERY_HOPE_SCALE);
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
                // Scout pets enjoy gentle, reassuring interactions
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.15f);
                
                // Less intense emotions
                removeEmotion(emotions, PetComponent.Emotion.GLEE);
                removeEmotion(emotions, PetComponent.Emotion.KEFI);
            }
            
            case FEEDING -> {
                // Scout pets see feeding as preparation for exploration
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.FERNWEH, 0.1f);
            }
            
            case HEALING -> {
                // Scout pets are patient with healing, seeing it as recovery time
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.1f);
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
            case "exploration", "discovery" -> {
                addEmotion(emotions, PetComponent.Emotion.GLEE, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.FERNWEH, 0.3f);
            }
            case "new_area", "unknown" -> {
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.2f);
            }
        }
        
        return emotions;
    }
}
