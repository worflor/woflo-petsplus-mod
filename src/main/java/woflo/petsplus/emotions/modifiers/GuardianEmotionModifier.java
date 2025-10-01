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
 * Guardian emotion modifier that provides unique emotional responses
 * based on the protective nature of this role.
 * 
 * Key behaviors:
 * - Highly protective of owner
 * - Alert to danger and threats
 * - Strong sense of duty and responsibility
 * - Enhanced response to owner being attacked
 */
public class GuardianEmotionModifier extends BaseRoleEmotionModifier {
    
    // Constants for magic numbers
    private static final float HIGH_PRIORITY = 10.0f;
    
    public GuardianEmotionModifier() {
        super(PetRoleType.GUARDIAN_ID, (int) HIGH_PRIORITY); // High priority for strong role identity
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
        
        // Guardian pets are extremely protective when owner is in danger
        if (!isPetVictim && isOwnerDamageSource(source, petComp)) {
            // Owner is attacking - Guardian feels supportive and ready
            addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, scaleByDamage(0.5f, amount));
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.2f, amount));
        }
        
        // When owner is being attacked (source attacker is not owner)
        if (!isOwnerAttacker && source.getAttacker() != null && source.getAttacker() != pet) {
            PlayerEntity owner = petComp.getOwner();
            if (owner != null && source.getAttacker() == owner) {
                // Owner is under attack - Guardian becomes highly protective
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.8f);
                addEmotion(emotions, PetComponent.Emotion.ANGST, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.6f);
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.3f);
                
                Petsplus.LOGGER.debug("Guardian pet {} protective of owner under attack", pet.getUuidAsString());
            }
        }
        
        // When Guardian pet is damaged
        if (isPetVictim) {
            // Guardian shows resilience and determination
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.4f, amount));
            addEmotion(emotions, PetComponent.Emotion.SISU, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, scaleByDamage(0.2f, amount));
            
            // Less fear, more determination
            removeEmotion(emotions, PetComponent.Emotion.ANGST);
            removeEmotion(emotions, PetComponent.Emotion.FOREBODING);
        }
        
        // Enhanced response to threats near owner
        if (!isPetVictim && !isOwnerAttacker) {
            addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.2f, amount));
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
                // Guardian pets enjoy protective physical contact
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.QUERECIA, 0.1f);
            }
            
            case FEEDING -> {
                // Guardian pets see feeding as care for their charge
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.1f);
            }
            
            case HEALING -> {
                // Guardian pets are very concerned about healing
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.2f);
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
            case "danger", "threat" -> {
                // Guardian pets are highly alert to danger
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.5f);
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.ANGST, 0.2f);
            }
            
            case "night", "darkness" -> {
                // Guardian pets are more vigilant at night
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.2f);
            }
            
            case "home", "safety" -> {
                // Guardian pets feel content in safe environments
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.QUERECIA, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.2f);
            }
            
            case "fortress", "walls" -> {
                // Guardian pets appreciate defensive structures
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.15f);
            }
            
            case "intruder", "stranger" -> {
                // Guardian pets are wary of strangers
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.FOREBODING, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.35f);
            }
        }
        
        return emotions;
    }
}