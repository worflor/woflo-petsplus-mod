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
 * Cursed One emotion modifier that provides unique emotional responses
 * based on the cursed nature of this role.
 * 
 * Key behaviors:
 * - Enjoys owner hits (masochistic tendencies)
 * - Finds pleasure in dark and dangerous situations
 * - Resonates with death and destruction
 * - Has heightened emotional responses to pain and suffering
 */
public class CursedOneEmotionModifier extends BaseRoleEmotionModifier {
    
    public CursedOneEmotionModifier() {
        super(PetRoleType.CURSED_ONE_ID, 10); // High priority for strong role identity
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
        
        // Validate damage amount - skip processing for zero/negative damage
        if (amount <= 0.0f) {
            return emotions;
        }
        
        // Cursed One enjoys owner hits - unique masochistic behavior
        // Fixed redundant condition check - simplified logic
        if (isPetVictim && isOwnerCausedDamage(source, petComp)) {
            // Replace negative emotions with pleasure/pain enjoyment
            removeEmotion(emotions, PetComponent.Emotion.ANGST);
            removeEmotion(emotions, PetComponent.Emotion.FOREBODING);
            removeEmotion(emotions, PetComponent.Emotion.REGRET);
            
            // Add enjoyment of pain with consistent scaling
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, scaleByDamage(0.4f, amount));
            addEmotion(emotions, PetComponent.Emotion.KEFI, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.2f, amount));
            
            Petsplus.LOGGER.debug("Cursed One pet {} enjoyed owner hit (damage: {})", pet.getUuidAsString(), amount);
        }
        
        // Enhanced response to combat and destruction
        if (!isPetVictim) {
            // Cursed One enjoys when owner deals damage
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, scaleByDamage(0.2f, amount));
            addEmotion(emotions, PetComponent.Emotion.KEFI, scaleByDamage(0.15f, amount));
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.1f, amount));
        }
        
        // Special response to magical damage
        if (isMagicalDamage(source)) {
            addEmotion(emotions, PetComponent.Emotion.YUGEN, scaleByDamage(0.2f, amount));
            addEmotion(emotions, PetComponent.Emotion.CURIOUS, scaleByDamage(0.15f, amount));
        }
        
        // When pet is damaged by others (not owner)
        if (isPetVictim && !isOwnerAttacker) {
            // Cursed One shows resilience and defiance
            addEmotion(emotions, PetComponent.Emotion.STOIC, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.SISU, scaleByDamage(0.2f, amount));
            addEmotion(emotions, PetComponent.Emotion.VIGILANT, scaleByDamage(0.25f, amount));
            
            // Less fear, more defiance
            removeEmotion(emotions, PetComponent.Emotion.ANGST);
            removeEmotion(emotions, PetComponent.Emotion.FOREBODING);
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
                // Cursed One enjoys rough handling
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.KEFI, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.1f);
                
                // Less traditional affection
                removeEmotion(emotions, PetComponent.Emotion.CONTENT);
                removeEmotion(emotions, PetComponent.Emotion.QUERECIA);
            }
            
            case FEEDING -> {
                // Cursed One enjoys dark/forbidden foods
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.1f);
            }
            
            case HEALING -> {
                // Cursed One views healing as interruption of suffering
                // More thematically appropriate masochistic response
                addEmotion(emotions, PetComponent.Emotion.FRUSTRATION, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.MELANCHOLY, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.ENNUI, 0.1f);
                
                // Remove positive healing emotions
                removeEmotion(emotions, PetComponent.Emotion.RELIEF);
                removeEmotion(emotions, PetComponent.Emotion.UBUNTU);
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
            case "darkness", "night" -> {
                // Cursed One enjoys darkness
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.2f);
            }
            
            case "death", "destruction" -> {
                // Cursed One resonates with death
                addEmotion(emotions, PetComponent.Emotion.MONO_NO_AWARE, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.STOIC, 0.2f);
            }
            
            case "blood", "violence" -> {
                // Cursed One is excited by violence
                addEmotion(emotions, PetComponent.Emotion.KEFI, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.2f);
            }
            
            case "nether", "hell" -> {
                // Cursed One feels at home in hellish environments
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.QUERECIA, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.25f);
            }
            
            case "holy", "blessing" -> {
                // Cursed One is uncomfortable with holy things
                addEmotion(emotions, PetComponent.Emotion.DISGUST, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.FRUSTRATION, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.FOREBODING, 0.2f);
                
                // Remove positive emotions
                removeEmotion(emotions, PetComponent.Emotion.CHEERFUL);
                removeEmotion(emotions, PetComponent.Emotion.CHEERFUL);
            }
        }
        
        return emotions;
    }
    
    /**
     * Check if the damage was caused by the owner, including indirect damage scenarios.
     * This method handles environmental damage caused by the owner (e.g., fire, lava, etc.)
     *
     * @param source the damage source
     * @param petComp the pet component
     * @return true if the damage was caused by the owner (directly or indirectly)
     */
    private boolean isOwnerCausedDamage(DamageSource source, PetComponent petComp) {
        // First check direct owner damage
        if (isOwnerDamageSource(source, petComp)) {
            return true;
        }
        
        // Check for indirect owner-caused environmental damage
        if (source != null && petComp != null) {
            PlayerEntity owner = petComp.getOwner();
            if (owner != null) {
                // Check if damage source is owned by or created by the owner
                return source.getSource() == owner ||
                       (source.getAttacker() != null && source.getAttacker().getCommandTags().contains("owner_" + owner.getUuidAsString()));
            }
        }
        
        return false;
    }
}
