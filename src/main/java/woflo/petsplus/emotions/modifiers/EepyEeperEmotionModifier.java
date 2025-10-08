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
 * Eepy Eeper emotion modifier that provides unique emotional responses
 * based on the sleepy nature of this role.
 */
public class EepyEeperEmotionModifier extends BaseRoleEmotionModifier {
    
    // Constants for magic numbers
    private static final float HIGH_PRIORITY = 10.0f;
    private static final float WAKEUP_DAMAGE_THRESHOLD = 12.0f;
    private static final float WAKEUP_STARTLE_SCALE = 0.2f;
    private static final float WAKEUP_WORRIED_SCALE = 0.15f;
    
    public EepyEeperEmotionModifier() {
        super(PetRoleType.EEPY_EEPER_ID, (int) HIGH_PRIORITY);
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
        
        // Eepy Eeper pets are reluctant and sleepy in combat
        if (!isPetVictim) {
            // Owner or pet is dealing damage - Eepy Eeper is barely awake
            addEmotion(emotions, PetComponent.Emotion.LAGOM, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.CONTENT, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.ENNUI, scaleByDamage(0.2f, amount));
            
            Petsplus.LOGGER.debug("Eepy Eeper pet {} reluctantly participating in combat", pet.getUuidAsString());
        }
        
        // When Eepy Eeper pet is damaged
        if (isPetVictim) {
            // Eepy Eeper shows sleepy discomfort and wants rest
            addEmotion(emotions, PetComponent.Emotion.ENNUI, scaleByDamage(0.35f, amount));
            addEmotion(emotions, PetComponent.Emotion.RELIEF, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.LAGOM, scaleByDamage(0.2f, amount));
            
            // Remove high-energy emotions
            removeEmotion(emotions, PetComponent.Emotion.KEFI);
            removeEmotion(emotions, PetComponent.Emotion.GLEE);
            removeEmotion(emotions, PetComponent.Emotion.FOCUSED);
        }
        
        // Enhanced response to being woken up by significant damage
        if (amount > WAKEUP_DAMAGE_THRESHOLD) {
            addEmotion(emotions, PetComponent.Emotion.STARTLE, WAKEUP_STARTLE_SCALE);
            addEmotion(emotions, PetComponent.Emotion.WORRIED, WAKEUP_WORRIED_SCALE);
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
                // Eepy Eeper pets enjoy gentle, sleep-inducing interactions
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.LAGOM, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.15f);
                
                // Remove stimulating emotions
                removeEmotion(emotions, PetComponent.Emotion.GLEE);
                removeEmotion(emotions, PetComponent.Emotion.PLAYFULNESS);
            }
            
            case FEEDING -> {
                // Eepy Eeper pets see feeding as pre-sleep routine
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.LAGOM, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.2f);
            }
            
            case HEALING -> {
                // Eepy Eeper pets love healing as it's rest time
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.LAGOM, 0.25f);
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
            case "sleep", "night", "bed" -> {
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.LAGOM, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.3f);
            }
            case "quiet", "peace" -> {
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.LAGOM, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.ENNUI, 0.2f);
            }
        }
        
        return emotions;
    }
}
