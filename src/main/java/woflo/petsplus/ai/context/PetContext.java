package woflo.petsplus.ai.context;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.goals.GoalType;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.*;

/**
 * Immutable snapshot of pet state for goal evaluation.
 * Prevents repeated component lookups during suggestion phase.
 * 
 * Supports both PetsPlus pets (with full personality) and vanilla mobs (basic functionality).
 */
public record PetContext(
    MobEntity mob,
    @Nullable PetComponent component,
    
    // Mood/Emotion state (PetsPlus only, null for vanilla)
    @Nullable PetComponent.Mood currentMood,
    int moodLevel,
    Map<PetComponent.Mood, Float> moodBlend,
    Map<PetComponent.Emotion, Float> activeEmotions,
    
    // Identity (PetsPlus only, null/default for vanilla)
    @Nullable PetRoleType role,
    @Nullable Identifier natureId,
    @Nullable PetComponent.NatureEmotionProfile natureProfile,
    
    // Progression (PetsPlus only, defaults for vanilla)
    int level,
    float bondStrength,
    long ticksAlive,
    
    // Environmental (universal)
    @Nullable PlayerEntity owner,
    boolean ownerNearby,
    float distanceToOwner,
    List<Entity> nearbyEntities,
    BlockPos currentPos,
    long worldTime,
    boolean isDaytime,
    
    // Recent history (for variety)
    Deque<GoalType> recentGoals,
    Map<GoalType, Long> lastExecuted,
    Map<String, Integer> quirkCounters,
    
    // Behavioral state (PetsPlus only, defaults for vanilla)
    float behavioralMomentum  // 0=still/tired, 0.5=neutral, 1=hyperactive
) {
    
    /**
     * Capture a context snapshot for a PetsPlus pet.
     */
    public static PetContext capture(MobEntity mob, PetComponent pc) {
        PlayerEntity owner = pc != null ? pc.getOwner() : (mob instanceof net.minecraft.entity.passive.TameableEntity t ? (PlayerEntity) t.getOwner() : null);
        float distanceToOwner = owner != null ? (float) mob.distanceTo(owner) : Float.MAX_VALUE;
        boolean ownerNearby = distanceToOwner < 16.0f;
        
        List<Entity> nearbyEntities = mob.getWorld().getOtherEntities(mob, 
            mob.getBoundingBox().expand(8.0), 
            e -> true);
        
        long worldTime = mob.getWorld().getTime();
        boolean isDaytime = mob.getWorld().isDay();
        
        // PetsPlus-specific data
        PetComponent.Mood mood = pc != null ? pc.getCurrentMood() : null;
        int moodLevel = pc != null ? pc.getMoodLevel() : 0;
        Map<PetComponent.Mood, Float> moodBlend = pc != null ? pc.getMoodBlend() : Collections.emptyMap();
        Map<PetComponent.Emotion, Float> activeEmotions = Collections.emptyMap(); // TODO: Add getActiveEmotions() to PetComponent
        
        PetRoleType role = pc != null ? pc.getRoleType() : null;
        Identifier natureId = pc != null ? pc.getNatureId() : null;
        PetComponent.NatureEmotionProfile natureProfile = pc != null ? pc.getNatureEmotionProfile() : null;
        
        int level = pc != null ? pc.getLevel() : 1;
        float bondStrength = pc != null ? (float) (pc.getBondStrength() / 100.0) : 0.0f; // Normalize to 0-1
        long ticksAlive = pc != null ? (long) mob.age : mob.age; // Use mob age as fallback
        
        // History tracking
        Deque<GoalType> recentGoals = new ArrayDeque<>();
        Map<GoalType, Long> lastExecuted = new EnumMap<>(GoalType.class);
        Map<String, Integer> quirkCounters = new HashMap<>();
        
        // Behavioral momentum
        float momentum = pc != null ? pc.getMoodEngine().getBehavioralMomentum() : 0.5f;
        
        return new PetContext(
            mob, pc,
            mood, moodLevel, moodBlend, activeEmotions,
            role, natureId, natureProfile,
            level, bondStrength, ticksAlive,
            owner, ownerNearby, distanceToOwner,
            nearbyEntities, mob.getBlockPos(), worldTime, isDaytime,
            recentGoals, lastExecuted, quirkCounters,
            momentum
        );
    }
    
    /**
     * Capture a context snapshot for a vanilla mob (no PetsPlus component).
     */
    public static PetContext captureVanilla(MobEntity mob) {
        return capture(mob, null);
    }
    
    /**
     * Check if an emotion is above a threshold (PetsPlus only).
     */
    public boolean hasEmotionAbove(PetComponent.Emotion emotion, float threshold) {
        return activeEmotions.getOrDefault(emotion, 0f) >= threshold;
    }
    
    /**
     * Check if a mood is present in the blend above a threshold (PetsPlus only).
     */
    public boolean hasMoodInBlend(PetComponent.Mood mood, float threshold) {
        return moodBlend.getOrDefault(mood, 0f) >= threshold;
    }
    
    /**
     * Get ticks since a goal was last executed.
     */
    public long ticksSince(GoalType goal) {
        return worldTime - lastExecuted.getOrDefault(goal, 0L);
    }
    
    /**
     * Check if this is a PetsPlus pet (has full personality system).
     */
    public boolean hasPetsPlusComponent() {
        return component != null;
    }
    
    /**
     * Get the age category of the pet.
     */
    public AgeCategory getAgeCategory() {
        long dayInTicks = 24000;
        if (ticksAlive < dayInTicks * 2) {
            return AgeCategory.YOUNG;
        } else if (ticksAlive < dayInTicks * 7) {
            return AgeCategory.ADULT;
        } else {
            return AgeCategory.MATURE;
        }
    }
    
    public enum AgeCategory {
        YOUNG,
        ADULT,
        MATURE
    }
}
