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
 * Support emotion modifier that provides unique emotional responses
 * based on the healing and supportive nature of this role.
 * 
 * Key behaviors:
 * - Cares deeply about owner's well-being
 * - Responds strongly to healing and support actions
 * - Empathetic and nurturing
 * - Concerned about damage and injury
 */
public class SupportEmotionModifier extends BaseRoleEmotionModifier {
    
    public SupportEmotionModifier() {
        super(PetRoleType.SUPPORT_ID, 10); // High priority for strong role identity
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
        
        // Support pets are very concerned about any damage
        if (isPetVictim || isOwnerDamageSource(source, petComp)) {
            // Concern about injury
            addEmotion(emotions, PetComponent.Emotion.WORRIED, scaleByDamage(0.4f, amount));
            addEmotion(emotions, PetComponent.Emotion.EMPATHY, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.RELIEF, scaleByDamage(0.2f, amount));
            
            Petsplus.LOGGER.debug("Support pet {} concerned about damage", pet.getUuidAsString());
        }
        
        // Support pets are relieved when combat ends successfully
        if (!isPetVictim && amount > 5.0f) {
            addEmotion(emotions, PetComponent.Emotion.RELIEF, scaleByDamage(0.2f, amount));
            addEmotion(emotions, PetComponent.Emotion.UBUNTU, scaleByDamage(0.15f, amount));
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
                // Support pets enjoy gentle contact
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.EMPATHY, 0.15f);
            }
            
            case FEEDING -> {
                // Support pets see feeding as nurturing
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.SOBREMESA, 0.2f);
            }
            
            case HEALING -> {
                // Support pets love healing more than anything
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.GLEE, 0.25f);
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
            case "healing", "potion", "medicine" -> {
                // Support pets love healing environments
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.25f);
            }
            
            case "home", "safety", "shelter" -> {
                // Support pets feel safe in secure environments
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.QUERECIA, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.2f);
            }
            
            case "injury", "pain", "suffering" -> {
                // Support pets are very concerned about injury
                addEmotion(emotions, PetComponent.Emotion.WORRIED, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.EMPATHY, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.SAUDADE, 0.2f);
            }
            
            case "community", "together" -> {
                // Support pets love social harmony
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.SOBREMESA, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.2f);
            }
        }
        
        return emotions;
    }
}