package woflo.petsplus.ai.context;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.context.perception.EnvironmentPerceptionBridge;
import woflo.petsplus.ai.context.perception.PetContextCache;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

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
    PetContextCrowdSummary crowdSummary,
    BlockPos currentPos,
    long worldTime,
    boolean isDaytime,
    StimulusSnapshot stimuli,
    SocialSnapshot socialSnapshot,
    boolean dormant,

    // Recent history (for variety)
    Deque<Identifier> recentGoals,
    Map<Identifier, Long> lastExecuted,
    Map<String, Integer> quirkCounters,
    
    // Behavioural state (PetsPlus only, defaults for vanilla)
    float behavioralMomentum, // 0=still/tired, 0.5=neutral, 1=hyperactive
    BehaviouralEnergyProfile behaviouralEnergyProfile
) {
    
    /**
     * Capture a context snapshot for a PetsPlus pet.
     */
    public static PetContext capture(MobEntity mob, PetComponent pc) {
        if (pc != null) {
            PetContextCache cache = pc.getContextCache();
            if (cache != null) {
                return cache.snapshot(mob, () -> captureFresh(mob, pc));
            }
        }
        return captureFresh(mob, pc);
    }

    /**
     * Build a fresh snapshot without consulting caches. Public for testing and
     * for callers that need an immediate capture irrespective of cache state.
     */
    public static PetContext captureFresh(MobEntity mob, @Nullable PetComponent pc) {
        PlayerEntity owner = null;
        float distanceToOwner = Float.MAX_VALUE;
        boolean ownerNearby = false;

        if (pc != null) {
            owner = pc.getCachedOwnerEntity();
            if (owner == null) {
                owner = pc.getOwner();
            }
            distanceToOwner = pc.getCachedOwnerDistance();
            if (owner != null) {
                if (distanceToOwner == Float.MAX_VALUE || Float.isNaN(distanceToOwner)) {
                    distanceToOwner = (float) mob.distanceTo(owner);
                }
                ownerNearby = pc.isCachedOwnerNearby();
                if (!ownerNearby) {
                    ownerNearby = distanceToOwner < 16.0f;
                }
            } else {
                distanceToOwner = Float.MAX_VALUE;
                ownerNearby = false;
            }
        } else {
            owner = mob instanceof net.minecraft.entity.passive.TameableEntity tameable
                ? (PlayerEntity) tameable.getOwner()
                : null;
            if (owner != null) {
                distanceToOwner = (float) mob.distanceTo(owner);
                ownerNearby = distanceToOwner < 16.0f;
            }
        }

        if (Float.isNaN(distanceToOwner) || distanceToOwner < 0f) {
            distanceToOwner = Float.MAX_VALUE;
            ownerNearby = false;
        }

        List<Entity> entitySnapshot;
        PetContextCrowdSummary crowdSummary;
        if (pc != null) {
            List<Entity> cachedEntities = pc.getCachedCrowdEntities();
            if (cachedEntities != null && !cachedEntities.isEmpty()) {
                entitySnapshot = cachedEntities;
                PetContextCrowdSummary cachedSummary = pc.getCachedCrowdSummary();
                crowdSummary = cachedSummary != null ? cachedSummary : PetContextCrowdSummary.fromEntities(mob, entitySnapshot);
            } else {
                entitySnapshot = captureNearbyEntities(mob);
                crowdSummary = PetContextCrowdSummary.fromEntities(mob, entitySnapshot);
            }
        } else {
            entitySnapshot = captureNearbyEntities(mob);
            crowdSummary = PetContextCrowdSummary.fromEntities(mob, entitySnapshot);
        }

        var world = mob.getEntityWorld();
        long actualWorldTime = world != null ? world.getTime() : 0L;
        boolean worldDaytime = world != null && world.isDay();
        long worldTime = actualWorldTime;
        boolean isDaytime = worldDaytime;

        if (pc != null) {
            EnvironmentPerceptionBridge.WorldSnapshot worldSnapshot = pc.getCachedWorldSnapshot();
            if (worldSnapshot != null) {
                worldTime = worldSnapshot.worldTime();
                isDaytime = worldSnapshot.daytime();
            }
        }

        StimulusSnapshot stimulusSnapshot = pc != null
            ? pc.snapshotStimuli(actualWorldTime)
            : StimulusSnapshot.empty();
        SocialSnapshot socialSnapshot = pc != null
            ? pc.snapshotSocialGraph()
            : SocialSnapshot.empty();
        boolean dormant = pc != null ? pc.isDormant() : computeVanillaDormant(mob);
        
        // PetsPlus-specific data
        PetComponent.Mood mood = pc != null ? pc.getCurrentMood() : null;
        int moodLevel = pc != null ? pc.getMoodLevel() : 0;
        Map<PetComponent.Mood, Float> moodBlend = pc != null && pc.getMoodBlend() != null
            ? Map.copyOf(pc.getMoodBlend())
            : Collections.emptyMap();
        Map<PetComponent.Emotion, Float> activeEmotions = pc != null && pc.getActiveEmotions() != null
            ? Map.copyOf(pc.getActiveEmotions())
            : Collections.emptyMap();
        
        PetRoleType role = pc != null ? pc.getRoleType() : null;
        Identifier natureId = pc != null ? pc.getNatureId() : null;
        PetComponent.NatureEmotionProfile natureProfile = pc != null ? pc.getNatureEmotionProfile() : null;
        
        int level = pc != null ? pc.getLevel() : 1;
        float bondStrength = pc != null ? (float) (pc.getBondStrength() / 100.0) : 0.0f; // Normalize to 0-1
        long ticksAlive = pc != null ? (long) mob.age : mob.age; // Use mob age as fallback
        
        // History tracking
        Deque<Identifier> recentGoals = pc != null
            ? new ArrayDeque<>(pc.getRecentGoalsSnapshot())
            : AdaptiveGoal.getFallbackRecentGoals(mob);
        Map<Identifier, Long> lastExecuted = pc != null
            ? Map.copyOf(pc.getGoalExecutionTimestamps())
            : AdaptiveGoal.getFallbackLastExecuted(mob);
        Map<String, Integer> quirkCounters = pc != null
            ? Map.copyOf(pc.getQuirkCountersSnapshot())
            : Collections.emptyMap();
        
        // Behavioral energy stack
        BehaviouralEnergyProfile energyProfile = pc != null
            ? pc.getMoodEngine().getBehaviouralEnergyProfile()
            : BehaviouralEnergyProfile.neutral();
        float momentum = energyProfile.momentum();

        return new PetContext(
            mob, pc,
            mood, moodLevel, moodBlend, activeEmotions,
            role, natureId, natureProfile,
            level, bondStrength, ticksAlive,
            owner, ownerNearby, distanceToOwner,
            entitySnapshot, crowdSummary, mob.getBlockPos(), worldTime, isDaytime,
            stimulusSnapshot, socialSnapshot, dormant,
            recentGoals, lastExecuted, quirkCounters,
            momentum,
            energyProfile
        );
    }

    private static boolean computeVanillaDormant(MobEntity mob) {
        if (mob == null) {
            return false;
        }
        if (mob.isAiDisabled()) {
            return true;
        }
        var world = mob.getEntityWorld();
        if (world == null) {
            return false;
        }
        if (world.getPlayers().isEmpty()) {
            return true;
        }
        double activationRadiusSq = 128.0 * 128.0;
        for (var player : world.getPlayers()) {
            if (player == null) {
                continue;
            }
            if (player.squaredDistanceTo(mob) <= activationRadiusSq) {
                return false;
            }
        }
        return true;
    }

    private static List<Entity> captureNearbyEntities(MobEntity mob) {
        if (mob == null) {
            return List.of();
        }
        var world = mob.getEntityWorld();
        if (world == null) {
            return List.of();
        }
        List<Entity> nearby = world.getOtherEntities(mob, mob.getBoundingBox().expand(8.0), e -> true);
        if (nearby == null || nearby.isEmpty()) {
            return List.of();
        }
        return List.copyOf(nearby);
    }
    
    /**
     * Capture a context snapshot for a vanilla mob (no PetsPlus component).
     */
    public static PetContext captureVanilla(MobEntity mob) {
        return captureFresh(mob, null);
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
     * Get the current strength of a mood blend entry.
     */
    public float getMoodStrength(PetComponent.Mood mood) {
        return moodBlend.getOrDefault(mood, 0f);
    }

    /**
     * Convenience accessor matching bean-style naming for integrations.
     */
    @Nullable
    public PlayerEntity getOwner() {
        return owner;
    }
    
    /**
     * Get ticks since a goal was last executed.
     */
    public long ticksSince(GoalDefinition goal) {
        return worldTime - lastExecuted.getOrDefault(goal.id(), 0L);
    }

    public float socialCharge() {
        return behaviouralEnergyProfile != null ? behaviouralEnergyProfile.socialCharge() : 0.45f;
    }

    public float physicalStamina() {
        return behaviouralEnergyProfile != null ? behaviouralEnergyProfile.physicalStamina() : 0.65f;
    }

    public float mentalFocus() {
        return behaviouralEnergyProfile != null ? behaviouralEnergyProfile.mentalFocus() : 0.6f;
    }

    public float normalizedSocialActivity() {
        return behaviouralEnergyProfile != null ? behaviouralEnergyProfile.normalizedSocialActivity() : 0f;
    }

    public float normalizedPhysicalActivity() {
        return behaviouralEnergyProfile != null ? behaviouralEnergyProfile.normalizedPhysicalActivity() : 0f;
    }

    public float normalizedMentalActivity() {
        return behaviouralEnergyProfile != null ? behaviouralEnergyProfile.normalizedMentalActivity() : 0f;
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

