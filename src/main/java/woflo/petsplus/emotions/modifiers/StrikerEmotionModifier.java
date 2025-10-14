package woflo.petsplus.emotions.modifiers;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.emotions.BaseRoleEmotionModifier;
import woflo.petsplus.events.EmotionContextMapper;
import woflo.petsplus.state.PetComponent;

import java.util.Map;

/**
 * Striker emotion modifier that provides unique emotional responses
 * based on the aggressive combat nature of this role.
 * 
 * Key behaviors:
 * - Enjoys combat and hunting
 * - Excited by violence and destruction
 * - Competitive and aggressive
 * - Enhanced response to successful attacks
 */
public class StrikerEmotionModifier extends BaseRoleEmotionModifier {
    
    // Constants for magic numbers
    private static final float HIGH_PRIORITY = 10.0f;
    private static final float POWERFUL_ATTACK_THRESHOLD = 10.0f;
    private static final float POWERFUL_ATTACK_CHEERFUL_SCALE = 0.2f;
    private static final float POWERFUL_ATTACK_FOCUS_SCALE = 0.15f;
    
    public StrikerEmotionModifier() {
        super(PetRoleType.STRIKER_ID, (int) HIGH_PRIORITY); // High priority for strong role identity
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
        
        // Striker pets are excited by combat
        if (!isPetVictim) {
            // Owner or pet is dealing damage - Striker gets excited
            addEmotion(emotions, PetComponent.Emotion.KEFI, scaleByDamage(0.4f, amount));
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.FOCUSED, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.2f, amount));
            
            Petsplus.LOGGER.debug("Striker pet {} excited by combat damage", pet.getUuidAsString());
        }
        
        // When Striker pet is damaged
        if (isPetVictim) {
            // Striker shows defiance and anger rather than fear
            addEmotion(emotions, PetComponent.Emotion.FRUSTRATION, scaleByDamage(0.4f, amount));
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.SISU, scaleByDamage(0.25f, amount));
            
            // Remove fear-based emotions
            removeEmotion(emotions, PetComponent.Emotion.ANGST);
            removeEmotion(emotions, PetComponent.Emotion.FOREBODING);
            removeEmotion(emotions, PetComponent.Emotion.REGRET);
        }
        
        // Enhanced response to owner attacks
        if (isOwnerAttacker && !isPetVictim) {
            addEmotion(emotions, PetComponent.Emotion.PRIDE, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.KEFI, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.FOCUSED, scaleByDamage(0.2f, amount));
        }
        
        // Special response to powerful attacks
        if (amount > POWERFUL_ATTACK_THRESHOLD) {
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, POWERFUL_ATTACK_CHEERFUL_SCALE);
            addEmotion(emotions, PetComponent.Emotion.FOCUSED, POWERFUL_ATTACK_FOCUS_SCALE);
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
                // Striker pets enjoy rougher handling
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.PLAYFULNESS, 0.2f);
                
                // Less traditional affection
                removeEmotion(emotions, PetComponent.Emotion.CONTENT);
                removeEmotion(emotions, PetComponent.Emotion.QUERECIA);
            }
            
            case FEEDING -> {
                // Striker pets see feeding as fuel for combat
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.FOCUSED, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.1f);
            }
            
            case HEALING -> {
                // Striker pets are impatient with healing
                addEmotion(emotions, PetComponent.Emotion.RESTLESS, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.FRUSTRATION, 0.15f);
                
                // Remove patient emotions
                removeEmotion(emotions, PetComponent.Emotion.RELIEF);
                removeEmotion(emotions, PetComponent.Emotion.CONTENT);
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
            case "combat", "battle", "fight" -> {
                // Striker pets love combat environments
                addEmotion(emotions, PetComponent.Emotion.KEFI, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.FOCUSED, 0.25f);
            }
            
            case "hunting", "hunt" -> {
                // Striker pets enjoy hunting
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.FOCUSED, 0.25f);
            }
            
            case "blood", "violence" -> {
                // Striker pets are excited by violence
                addEmotion(emotions, PetComponent.Emotion.KEFI, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.FOCUSED, 0.2f);
            }
            
            case "weapons", "armor" -> {
                // Striker pets appreciate combat equipment
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.FOCUSED, 0.1f);
            }
            
            case "peace", "quiet" -> {
                // Striker pets are bored by peace
                addEmotion(emotions, PetComponent.Emotion.ENNUI, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.RESTLESS, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.FRUSTRATION, 0.2f);
                
                // Remove positive peaceful emotions
                removeEmotion(emotions, PetComponent.Emotion.CONTENT);
                removeEmotion(emotions, PetComponent.Emotion.CHEERFUL);
            }
            
            case "competition", "contest" -> {
                // Striker pets love competition
                addEmotion(emotions, PetComponent.Emotion.KEFI, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.FOCUSED, 0.25f);
            }
        }
        
        return emotions;
    }
}
