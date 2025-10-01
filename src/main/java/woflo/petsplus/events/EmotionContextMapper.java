package woflo.petsplus.events;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetCharacteristics;

import java.util.EnumMap;
import java.util.Map;

/**
 * Context-aware emotion mapping system that provides precise emotional responses
 * based on situation-specific factors, pet personality traits, and event importance.
 */
public class EmotionContextMapper {
    
    /**
     * Context factors that influence emotion selection and intensity
     */
    public static class ContextFactors {
        public final float eventImportance;
        public final float relationshipStrength;
        public final float personalSafety;
        public final float ownerSafety;
        public final boolean isFirstTime;
        public final boolean isHighIntensityMoment;
        public final PetCharacteristics personality;
        public final float healthRatio;
        public final float ownerHealthRatio;
        
        public ContextFactors(float eventImportance, float relationshipStrength, float personalSafety,
                            float ownerSafety, boolean isFirstTime, boolean isHighIntensityMoment,
                            PetCharacteristics personality, float healthRatio, float ownerHealthRatio) {
            this.eventImportance = MathHelper.clamp(eventImportance, 0f, 1f);
            this.relationshipStrength = MathHelper.clamp(relationshipStrength, 0f, 1f);
            this.personalSafety = MathHelper.clamp(personalSafety, 0f, 1f);
            this.ownerSafety = MathHelper.clamp(ownerSafety, 0f, 1f);
            this.isFirstTime = isFirstTime;
            this.isHighIntensityMoment = isHighIntensityMoment;
            this.personality = personality;
            this.healthRatio = MathHelper.clamp(healthRatio, 0f, 1f);
            this.ownerHealthRatio = MathHelper.clamp(ownerHealthRatio, 0f, 1f);
        }
    }
    
    /**
     * Emotion response with context-aware intensity scaling
     */
    public static class EmotionResponse {
        public final PetComponent.Emotion emotion;
        public final float baseIntensity;
        public final float scaledIntensity;
        
        public EmotionResponse(PetComponent.Emotion emotion, float baseIntensity, float scaledIntensity) {
            this.emotion = emotion;
            this.baseIntensity = baseIntensity;
            this.scaledIntensity = scaledIntensity;
        }
    }
    
    /**
     * Maps combat damage events to contextually appropriate emotions
     */
    public static Map<PetComponent.Emotion, Float> mapCombatDamage(
            MobEntity pet, PetComponent petComp, DamageSource source, float amount, 
            boolean isOwnerAttacker, boolean isPetVictim) {
        
        ContextFactors context = buildCombatContext(pet, petComp, source, amount, isOwnerAttacker, isPetVictim);
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        
        if (isPetVictim) {
            mapPetDamageEmotions(emotions, context, source, amount);
        } else if (isOwnerAttacker) {
            mapOwnerAttackEmotions(emotions, context, amount);
        }
        
        return emotions;
    }
    
    /**
     * Maps social interaction events to appropriate emotions
     */
    public static Map<PetComponent.Emotion, Float> mapSocialInteraction(
            MobEntity pet, PetComponent petComp, PlayerEntity player, 
            SocialInteractionType type, Object context) {
        
        ContextFactors factors = buildSocialContext(pet, petComp, player, type, context);
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        
        switch (type) {
            case PETTING -> mapPettingEmotions(emotions, factors);
            case FEEDING -> mapFeedingEmotions(emotions, factors);
            case BREEDING -> mapBreedingEmotions(emotions, factors);
            case TRIBUTE -> mapTributeEmotions(emotions, factors);
            case HEALING -> mapHealingEmotions(emotions, factors);
        }
        
        return emotions;
    }
    
    /**
     * Maps environmental events to appropriate emotions
     */
    public static Map<PetComponent.Emotion, Float> mapEnvironmentalEvent(
            MobEntity pet, PetComponent petComp, EnvironmentalEventType type, 
            Object context) {
        
        ContextFactors factors = buildEnvironmentalContext(pet, petComp, type, context);
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        
        switch (type) {
            case DISCOVERY -> mapDiscoveryEmotions(emotions, factors);
            case DANGER -> mapDangerEmotions(emotions, factors);
            case WEATHER -> mapWeatherEmotions(emotions, factors);
            case TERRAIN -> mapTerrainEmotions(emotions, factors);
            case TIME_CHANGE -> mapTimeEmotions(emotions, factors);
        }
        
        return emotions;
    }
    
    private static void mapPetDamageEmotions(Map<PetComponent.Emotion, Float> emotions, 
                                            ContextFactors context, DamageSource source, float amount) {
        
        float damageIntensity = calculateDamageIntensity(amount, context.healthRatio);
        
        if (source.getAttacker() instanceof PlayerEntity attacker && 
            context.relationshipStrength > 0.8f && isPetOwner(attacker, context)) {
            
            // Betrayal trauma - owner hurt pet
            addEmotion(emotions, PetComponent.Emotion.REGRET, 0.4f * damageIntensity, context);
            addEmotion(emotions, PetComponent.Emotion.FOREBODING, 0.3f * damageIntensity, context);
            addEmotion(emotions, PetComponent.Emotion.ANGST, 0.5f * damageIntensity, context);
            
        } else if (isEnvironmentalDamage(source)) {
            // Environmental damage - caution and endurance
            addEmotion(emotions, PetComponent.Emotion.STARTLE, 0.2f * damageIntensity, context);
            addEmotion(emotions, PetComponent.Emotion.GAMAN, 0.3f * damageIntensity, context);
            
        } else {
            // Combat damage - fear and protectiveness
            addEmotion(emotions, PetComponent.Emotion.ANGST, 0.4f * damageIntensity, context);
            addEmotion(emotions, PetComponent.Emotion.FOREBODING, 0.3f * damageIntensity, context);
            
            if (context.ownerSafety < 0.5f) {
                addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.5f * damageIntensity, context);
            }
        }
        
        // Low health desperation
        if (context.healthRatio < 0.3f) {
            addEmotion(emotions, PetComponent.Emotion.STOIC, 0.4f * (1f - context.healthRatio), context);
        }
    }
    
    private static void mapOwnerAttackEmotions(Map<PetComponent.Emotion, Float> emotions, 
                                             ContextFactors context, float damageAmount) {
        
        float intensity = calculateDamageIntensity(damageAmount, 1f);
        
        if (context.isHighIntensityMoment) {
            // Boss battle or significant combat
            addEmotion(emotions, PetComponent.Emotion.KEFI, 0.4f * intensity, context);
            addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.5f * intensity, context);
            addEmotion(emotions, PetComponent.Emotion.STOIC, 0.3f * intensity, context);
        } else {
            // Regular combat
            addEmotion(emotions, PetComponent.Emotion.PROTECTIVENESS, 0.3f * intensity, context);
            addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.2f * intensity, context);
        }
        
        // Adjust based on owner's safety
        if (context.ownerHealthRatio < 0.5f) {
            addEmotion(emotions, PetComponent.Emotion.ANGST, 0.4f * (1f - context.ownerHealthRatio), context);
        }
    }
    
    private static void mapPettingEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        float baseIntensity = context.isFirstTime ? 0.8f : 0.4f;
        
        addEmotion(emotions, PetComponent.Emotion.CHEERFUL, baseIntensity, context);
        addEmotion(emotions, PetComponent.Emotion.UBUNTU, baseIntensity * 0.7f, context);
        addEmotion(emotions, PetComponent.Emotion.QUERECIA, baseIntensity * 0.6f, context);
        
        // Personality-based variations
        if (context.personality != null) {
            if (isPlayfulPersonality(context.personality)) {
                addEmotion(emotions, PetComponent.Emotion.GLEE, baseIntensity * 0.5f, context);
            }
            if (isAffectionatePersonality(context.personality)) {
                addEmotion(emotions, PetComponent.Emotion.CONTENT, baseIntensity * 0.4f, context);
            }
        }
    }
    
    private static void mapDiscoveryEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        if (context.isHighIntensityMoment) {
            // Rare discovery
            addEmotion(emotions, PetComponent.Emotion.GLEE, 0.6f, context);
            addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.5f, context);
            addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.4f, context);
        } else {
            // Regular discovery
            addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.3f, context);
            addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.2f, context);
        }
    }
    
    private static void addEmotion(Map<PetComponent.Emotion, Float> emotions, 
                                 PetComponent.Emotion emotion, float baseIntensity, 
                                 ContextFactors context) {
        
        // Apply personality-based modifiers
        float modifiedIntensity = applyPersonalityModifiers(baseIntensity, emotion, context.personality);
        
        // Apply context-based scaling
        float scaledIntensity = modifiedIntensity * context.eventImportance;
        
        // Apply relationship strength modifier
        scaledIntensity *= (0.5f + 0.5f * context.relationshipStrength);
        
        // Apply safety modifier
        if (emotion == PetComponent.Emotion.ANGST || emotion == PetComponent.Emotion.FOREBODING) {
            scaledIntensity *= (2f - context.personalSafety);
        }
        
        // Ensure minimum threshold for meaningful responses
        if (scaledIntensity > 0.05f) {
            emotions.put(emotion, scaledIntensity);
        }
    }
    
    private static float applyPersonalityModifiers(float intensity, PetComponent.Emotion emotion, 
                                                 PetCharacteristics personality) {
        if (personality == null) return intensity;
        
        // Personality-based emotion amplification/dampening
        return switch (emotion) {
            case CHEERFUL, GLEE -> isPlayfulPersonality(personality) ? intensity * 1.3f : intensity;
            case PROTECTIVENESS -> isLoyalPersonality(personality) ? intensity * 1.4f : intensity;
            case CURIOUS -> isCuriousPersonality(personality) ? intensity * 1.5f : intensity;
            case ANGST, FOREBODING -> isNervousPersonality(personality) ? intensity * 1.3f : intensity * 0.8f;
            case STOIC, GAMAN -> isBravePersonality(personality) ? intensity * 1.2f : intensity;
            default -> intensity;
        };
    }
    
    private static ContextFactors buildCombatContext(MobEntity pet, PetComponent petComp, 
                                                   DamageSource source, float amount,
                                                   boolean isOwnerAttacker, boolean isPetVictim) {
        
        PlayerEntity owner = petComp.getOwner();
        float relationshipStrength = calculateRelationshipStrength(petComp);
        float personalSafety = isPetVictim ? (pet.getHealth() - amount) / pet.getMaxHealth() : 1f;
        float ownerSafety = owner != null ? owner.getHealth() / owner.getMaxHealth() : 1f;
        boolean isFirstTime = isFirstTimeExperience(petComp, "combat_damage");
        boolean isHighIntensity = isHighIntensityCombat(source, amount);
        
        return new ContextFactors(
            calculateEventImportance(amount, isHighIntensity),
            relationshipStrength,
            personalSafety,
            ownerSafety,
            isFirstTime,
            isHighIntensity,
            petComp.getCharacteristics(),
            pet.getHealth() / pet.getMaxHealth(),
            ownerSafety
        );
    }
    
    private static ContextFactors buildSocialContext(MobEntity pet, PetComponent petComp, 
                                                   PlayerEntity player, SocialInteractionType type, 
                                                   Object context) {
        
        float relationshipStrength = calculateRelationshipStrength(petComp);
        boolean isFirstTime = isFirstTimeExperience(petComp, type.name());
        
        return new ContextFactors(
            calculateSocialImportance(type),
            relationshipStrength,
            1f, // Safe during social interactions
            player.getHealth() / player.getMaxHealth(),
            isFirstTime,
            type == SocialInteractionType.BREEDING || type == SocialInteractionType.TRIBUTE,
            petComp.getCharacteristics(),
            pet.getHealth() / pet.getMaxHealth(),
            player.getHealth() / player.getMaxHealth()
        );
    }
    
    private static ContextFactors buildEnvironmentalContext(MobEntity pet, PetComponent petComp, 
                                                          EnvironmentalEventType type, Object context) {
        
        boolean isFirstTime = isFirstTimeExperience(petComp, "env_" + type.name());
        boolean isHighIntensity = type == EnvironmentalEventType.DISCOVERY && 
                                 context instanceof Boolean && (Boolean) context;
        
        return new ContextFactors(
            calculateEnvironmentalImportance(type, isHighIntensity),
            calculateRelationshipStrength(petComp),
            1f, // Assume safe unless danger event
            1f,
            isFirstTime,
            isHighIntensity,
            petComp.getCharacteristics(),
            pet.getHealth() / pet.getMaxHealth(),
            1f
        );
    }
    
    private static float calculateRelationshipStrength(PetComponent petComp) {
        // Base relationship on bond strength and interaction history
        long bondStrength = petComp.getBondStrength();
        float baseStrength = Math.min(1f, bondStrength / 5000f); // Max at 5000 bond
        
        // Boost for long-term companions
        Integer petCount = petComp.getStateData(PetComponent.StateKeys.PET_COUNT, Integer.class);
        if (petCount != null && petCount > 100) {
            baseStrength = Math.min(1f, baseStrength + 0.2f);
        }
        
        return baseStrength;
    }
    
    private static float calculateEventImportance(float damageAmount, boolean isHighIntensity) {
        float baseImportance = Math.min(1f, damageAmount / 20f); // Normalize to 20 damage = full importance
        return isHighIntensity ? Math.min(1f, baseImportance * 1.5f) : baseImportance;
    }
    
    private static float calculateSocialImportance(SocialInteractionType type) {
        return switch (type) {
            case BREEDING -> 0.9f;
            case TRIBUTE -> 0.8f;
            case PETTING -> 0.4f;
            case FEEDING -> 0.3f;
            case HEALING -> 0.6f;
        };
    }
    
    private static float calculateEnvironmentalImportance(EnvironmentalEventType type, boolean isHighIntensity) {
        float base = switch (type) {
            case DISCOVERY -> 0.6f;
            case DANGER -> 0.8f;
            case WEATHER -> 0.2f;
            case TERRAIN -> 0.3f;
            case TIME_CHANGE -> 0.1f;
        };
        return isHighIntensity ? Math.min(1f, base * 1.5f) : base;
    }
    
    private static float calculateDamageIntensity(float damage, float healthRatio) {
        float damageRatio = damage / 20f; // Normalize to 20 damage
        float healthFactor = 1f - healthRatio; // Higher when hurt
        return Math.min(1f, damageRatio * (1f + healthFactor));
    }
    
    private static boolean isFirstTimeExperience(PetComponent petComp, String experienceType) {
        String key = "first_" + experienceType;
        Boolean hasExperienced = petComp.getStateData(key, Boolean.class);
        if (hasExperienced == null || !hasExperienced) {
            petComp.setStateData(key, true);
            return true;
        }
        return false;
    }
    
    private static boolean isHighIntensityCombat(DamageSource source, float damage) {
        return damage > 10f || 
               (source != null && source.getSource() != null && 
                source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.MAGIC));
    }
    
    private static boolean isEnvironmentalDamage(DamageSource source) {
        return source != null && (
            source.isOf(DamageTypes.FALL) ||
            source.isOf(DamageTypes.IN_FIRE) ||
            source.isOf(DamageTypes.ON_FIRE) ||
            source.isOf(DamageTypes.LAVA) ||
            source.isOf(DamageTypes.DROWN) ||
            source.isOf(DamageTypes.STARVE) ||
            source.isOf(DamageTypes.CACTUS) ||
            source.isOf(DamageTypes.SWEET_BERRY_BUSH) ||
            source.isOf(DamageTypes.FREEZE) ||
            source.isOf(DamageTypes.MAGIC)
        );
    }
    
    // Missing helper methods for emotion mapping
    private static void mapFeedingEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.5f, context);
        addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.4f, context);
        addEmotion(emotions, PetComponent.Emotion.QUERECIA, 0.3f, context);
    }
    
    private static void mapBreedingEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.LOYALTY, 0.6f, context);
        addEmotion(emotions, PetComponent.Emotion.PLAYFULNESS, 0.5f, context);
        addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.4f, context);
    }
    
    private static void mapTributeEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.7f, context);
        addEmotion(emotions, PetComponent.Emotion.QUERECIA, 0.5f, context);
        addEmotion(emotions, PetComponent.Emotion.CHEERFUL, 0.4f, context);
    }
    
    private static void mapHealingEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.RELIEF, 0.6f, context);
        addEmotion(emotions, PetComponent.Emotion.UBUNTU, 0.5f, context);
        addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.3f, context);
    }
    
    private static void mapDangerEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.ANGST, 0.7f, context);
        addEmotion(emotions, PetComponent.Emotion.FOREBODING, 0.6f, context);
        addEmotion(emotions, PetComponent.Emotion.VIGILANT, 0.5f, context);
    }
    
    private static void mapWeatherEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.2f, context);
        addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.1f, context);
    }
    
    private static void mapTerrainEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.CURIOUS, 0.3f, context);
        addEmotion(emotions, PetComponent.Emotion.HOPEFUL, 0.2f, context);
    }
    
    private static void mapTimeEmotions(Map<PetComponent.Emotion, Float> emotions, ContextFactors context) {
        addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.1f, context);
        addEmotion(emotions, PetComponent.Emotion.CONTENT, 0.1f, context);
    }
    
    // Personality trait helper methods
    private static boolean isPetOwner(PlayerEntity player, ContextFactors context) {
        // This would need to be implemented based on how pet ownership is tracked
        return false; // Placeholder
    }
    
    private static boolean isPlayfulPersonality(PetCharacteristics personality) {
        return personality != null && personality.getXpLearningModifier(null) > 1.2f;
    }
    
    private static boolean isAffectionatePersonality(PetCharacteristics personality) {
        return personality != null && personality.getXpLearningModifier(null) > 1.0f;
    }
    
    private static boolean isLoyalPersonality(PetCharacteristics personality) {
        return personality != null && personality.getXpLearningModifier(null) > 1.1f;
    }
    
    private static boolean isCuriousPersonality(PetCharacteristics personality) {
        return personality != null && personality.getXpLearningModifier(null) > 1.3f;
    }
    
    private static boolean isNervousPersonality(PetCharacteristics personality) {
        return personality != null && personality.getXpLearningModifier(null) < 0.9f;
    }
    
    private static boolean isBravePersonality(PetCharacteristics personality) {
        return personality != null && personality.getXpLearningModifier(null) > 1.15f;
    }
    
    public enum SocialInteractionType {
        PETTING, FEEDING, BREEDING, TRIBUTE, HEALING
    }
    
    public enum EnvironmentalEventType {
        DISCOVERY, DANGER, WEATHER, TERRAIN, TIME_CHANGE
    }
}