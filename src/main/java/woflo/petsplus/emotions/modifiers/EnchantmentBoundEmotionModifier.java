package woflo.petsplus.emotions.modifiers;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.emotions.BaseRoleEmotionModifier;
import woflo.petsplus.events.EmotionContextMapper;
import woflo.petsplus.state.PetComponent;

import java.util.Map;

/**
 * Enchantment Bound emotion modifier that provides unique emotional responses
 * based on the magical nature of this role.
 * 
 * Key behaviors:
 * - Loves enchantments and magical items
 * - Resonates with magical energy and spells
 * - Has heightened awareness of magical phenomena
 * - Finds comfort in enchanted environments
 */
public class EnchantmentBoundEmotionModifier extends BaseRoleEmotionModifier {
    
    public EnchantmentBoundEmotionModifier() {
        super(PetRoleType.ENCHANTMENT_BOUND_ID, 10); // High priority for strong role identity
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
        
        // Special response to magical damage
        if (isMagicalDamage(source)) {
            // Enchantment Bound pets are fascinated by magic, even when harmful
            addEmotion(emotions, PetComponent.Emotion.YUGEN, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.CURIOUS, scaleByDamage(0.25f, amount));
            
            // Less fear of magical damage
            removeEmotion(emotions, PetComponent.Emotion.ANGST);
            removeEmotion(emotions, PetComponent.Emotion.FOREBODING);
            
            Petsplus.LOGGER.debug("Enchantment Bound pet {} fascinated by magical damage", pet.getUuidAsString());
        }
        
        // Enhanced response when owner uses magical attacks
        if (isOwnerAttacker && isMagicalDamage(source)) {
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.KEFI, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.PRIDE, scaleByDamage(0.2f, amount));
        }
        
        // When pet is damaged by non-magical means
        if (isPetVictim && !isMagicalDamage(source)) {
            // Enchantment Bound pets are more vulnerable to physical damage
            addEmotion(emotions, PetComponent.Emotion.ANGST, scaleByDamage(0.3f, amount));
            addEmotion(emotions, PetComponent.Emotion.FOREBODING, scaleByDamage(0.25f, amount));
            addEmotion(emotions, PetComponent.Emotion.REGRET, scaleByDamage(0.2f, amount));
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
                // Enchantment Bound pets enjoy magical touches
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.15f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.1f);
            }
            
            case FEEDING -> {
                // Check if food is magical (potions, enchanted items)
                if (context instanceof ItemStack stack && stack.hasEnchantments()) {
                    addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.3f);
                    addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.25f);
                    addEmotion(emotions, PetComponent.Emotion.KEFI, 0.2f);
                }
            }
            
            case HEALING -> {
                // Enchantment Bound pets love magical healing
                addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.2f);
            }
            
            case BREEDING -> {
                // Enchantment Bound pets feel magical affinity during breeding
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.2f);
            }
            
            case TRIBUTE -> {
                // Enchantment Bound pets find tribute spiritually resonant
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.25f);
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
            case "enchanting", "enchantment_table" -> {
                // Enchantment Bound pets love enchanting
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.25f);
            }
            
            case "magic", "magical" -> {
                // General magical phenomena
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.25f);
            }
            
            case "bookshelf", "library" -> {
                // Enchantment Bound pets feel at home with knowledge
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.2f);
            }
            
            case "potion", "brewing" -> {
                // Alchemical magic
                addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.15f);
            }
            
            case "end", "end_portal" -> {
                // End magic is particularly fascinating
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.4f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.35f);
                addEmotion(emotions, PetComponent.Emotion.FOREBODING, 0.1f); // Slight danger awareness
            }
            
            case "nether_portal" -> {
                // Portal magic
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.3f);
                addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.2f);
            }
            
            case "beacon", "pyramid" -> {
                // Beacon magic
                addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.25f);
                addEmotion(emotions, PetComponent.Emotion.YUGEN, 0.2f);
                addEmotion(emotions, PetComponent.Emotion.LAGOM, 0.15f);
            }
            
            case "mundane", "ordinary" -> {
                // Enchantment Bound pets are bored by mundane things
                addEmotion(emotions, PetComponent.Emotion.ENNUI, 0.2f);
                
                // Remove positive emotions
                removeEmotion(emotions, PetComponent.Emotion.CHEERFUL);
                removeEmotion(emotions, PetComponent.Emotion.CHEERFUL);
            }
        }
        
        return emotions;
    }
}
