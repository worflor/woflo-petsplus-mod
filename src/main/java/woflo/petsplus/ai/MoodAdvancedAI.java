package woflo.petsplus.ai;


import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.mixin.MobEntityAccessor;

import net.minecraft.util.Identifier;

import java.util.EnumSet;

/**
 * Advanced mood-influenced enhancements to vanilla AI behaviors.
 * Modifies attack patterns, pathfinding, and combat behaviors based on mood state.
 */
public class MoodAdvancedAI {

    /**
     * Add advanced mood-influenced AI enhancements.
     * These modify existing vanilla behaviors rather than replacing them.
     */
    public static void enhanceVanillaAI(MobEntity pet) {
        if (pet.getWorld().isClient) return;
        
        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        
        // Add mood-influenced attack behavior modifier
        accessor.getGoalSelector().add(3, new MoodAttackEnhancement(pet));
        
        // Add mood-influenced pathfinding adjustments
        accessor.getGoalSelector().add(28, new MoodPathfindingEnhancement(pet));
        
        // Add mood-based stamina and recovery behaviors
        accessor.getGoalSelector().add(29, new MoodStaminaManager(pet));
    }

    /**
     * Enhances attack behaviors based on mood.
     * Modifies attack speed, aggression, and combat positioning.
     */
    public static class MoodAttackEnhancement extends Goal {
        private final MobEntity pet;
        private int moodCheckTimer = 0;
        private float baseAttackSpeed;
        private boolean initialized = false;

        public MoodAttackEnhancement(MobEntity pet) {
            this.pet = pet;
            this.setControls(EnumSet.noneOf(Control.class)); // Don't interfere with other goals
        }

        @Override
        public boolean canStart() {
            return pet.getTarget() != null && pet.getTarget().isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            if (!initialized) {
                // Store base attack speed from existing attack goals
                MobEntityAccessor accessor = (MobEntityAccessor) pet;
                accessor.getGoalSelector().getGoals().stream()
                    .filter(goal -> goal.getGoal() instanceof MeleeAttackGoal)
                    .findFirst()
                    .ifPresent(goal -> {
                        // We can't directly access private fields, so we'll use a default
                        baseAttackSpeed = 1.0f;
                    });
                if (baseAttackSpeed == 0) baseAttackSpeed = 1.0f;
                initialized = true;
            }
            moodCheckTimer = 0;
        }

        @Override
        public void tick() {
            moodCheckTimer++;
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            // Apply mood modifications every half second
            if (moodCheckTimer % 10 == 0) {
                applyMoodAttackModifications(pc);
            }

            // Apply mood-based combat positioning
            if (moodCheckTimer % 20 == 0) {
                applyMoodCombatPositioning(pc);
            }
        }

        private void applyMoodAttackModifications(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return;

            float attackSpeedMultiplier = getMoodAttackSpeedMultiplier(mood);
            
            // Apply mood-specific combat behaviors
            applyMoodCombatBehavior(mood);

            // Apply attack speed modification by adjusting movement toward target
            if (pet.getTarget() != null && attackSpeedMultiplier != 1.0f) {
                double distance = pet.squaredDistanceTo(pet.getTarget());
                if (distance > 4.0 && distance < 64.0) { // In combat range
                    Vec3d toTarget = pet.getTarget().getPos().subtract(pet.getPos()).normalize();
                    float speedMod = (attackSpeedMultiplier - 1.0f) * 0.1f; // Convert to movement modifier
                    pet.setVelocity(pet.getVelocity().add(toTarget.multiply(speedMod)));
                }
            }
        }
        
        private float getMoodAttackSpeedMultiplier(PetComponent.Mood mood) {
            return switch (mood) {
                case ANGRY -> 1.25f; // 25% faster attacks
                case PASSIONATE -> 1.15f; // 15% faster attacks
                case PROTECTIVE -> 1.1f; // 10% faster when protecting
                case FOCUSED -> 1.05f; // Slightly faster, more precise
                case AFRAID -> 0.85f; // Slower, hesitant attacks
                case RESTLESS -> 0.9f + (pet.getRandom().nextFloat() * 0.3f); // 0.9 to 1.2 - erratic
                case SISU -> 1.02f; // Slightly enhanced
                default -> 1.0f; // Normal speed for all other moods
            };
        }
        
        private void applyMoodCombatBehavior(PetComponent.Mood mood) {
            switch (mood) {
                case ANGRY -> {
                    // More likely to continue attacking
                    if (pet.getRandom().nextFloat() < 0.1f) {
                        pet.setAttacking(true);
                    }
                }
                case PROTECTIVE -> {
                    // Enhanced protection - look for owner threats
                    PlayerEntity owner = getOwner();
                    if (owner != null && pet.squaredDistanceTo(owner) > 16) {
                        // Try to get closer to owner during combat
                        Vec3d toOwner = owner.getPos().subtract(pet.getPos()).normalize();
                        pet.setVelocity(pet.getVelocity().add(toOwner.multiply(0.1)));
                    }
                }
                case AFRAID -> {
                    // More likely to retreat after attacking
                    if (pet.getRandom().nextFloat() < 0.15f && pet.getTarget() != null) {
                        Vec3d awayFromTarget = pet.getPos().subtract(pet.getTarget().getPos()).normalize();
                        pet.setVelocity(pet.getVelocity().add(awayFromTarget.multiply(0.2)));
                    }
                }
                case RESTLESS -> {
                    // Erratic attack patterns - sometimes miss intentionally
                    if (pet.getRandom().nextFloat() < 0.05f) {
                        // Slight miss - attack slightly off-target
                        Vec3d randomOffset = new Vec3d(
                            (pet.getRandom().nextFloat() - 0.5f) * 0.5f,
                            0,
                            (pet.getRandom().nextFloat() - 0.5f) * 0.5f
                        );
                        pet.setVelocity(pet.getVelocity().add(randomOffset));
                    }
                }
                case SISU -> {
                    // Determined - less likely to be knocked back
                    pet.setVelocity(pet.getVelocity().multiply(0.9, 1.0, 0.9));
                }
                default -> {
                    // No special behavior for other moods
                }
            }
        }

        private void applyMoodCombatPositioning(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null || pet.getTarget() == null) return;

            PlayerEntity owner = getOwner();
            Vec3d petPos = pet.getPos();
            Vec3d targetPos = pet.getTarget().getPos();

            switch (mood) {
                case PROTECTIVE -> {
                    // Try to position between owner and threat
                    if (owner != null) {
                        Vec3d ownerPos = owner.getPos();
                        Vec3d midpoint = ownerPos.add(targetPos).multiply(0.5);
                        Vec3d toMidpoint = midpoint.subtract(petPos);
                        if (toMidpoint.lengthSquared() > 1.0) {
                            pet.setVelocity(pet.getVelocity().add(toMidpoint.normalize().multiply(0.1)));
                        }
                    }
                }
                case ANGRY, PASSIONATE -> {
                    // Aggressive positioning - try to flank or get behind target
                    Vec3d targetVel = pet.getTarget().getVelocity();
                    if (targetVel.lengthSquared() > 0.01) {
                        // Predict where target is going and intercept
                        Vec3d predictedPos = targetPos.add(targetVel.multiply(10));
                        Vec3d intercept = predictedPos.subtract(petPos).normalize();
                        pet.setVelocity(pet.getVelocity().add(intercept.multiply(0.08)));
                    }
                }
                case AFRAID -> {
                    // Hit and run tactics - stay at range when possible
                    double distance = petPos.squaredDistanceTo(targetPos);
                    if (distance < 9.0) { // Too close
                        Vec3d awayFromTarget = petPos.subtract(targetPos).normalize();
                        pet.setVelocity(pet.getVelocity().add(awayFromTarget.multiply(0.15)));
                    }
                }
                case FOCUSED -> {
                    // Optimal positioning - try to attack from best angle
                    // Stay at ideal combat distance (around 2-3 blocks)
                    double distance = petPos.squaredDistanceTo(targetPos);
                    if (distance < 4.0) { // Too close
                        Vec3d away = petPos.subtract(targetPos).normalize().multiply(0.05);
                        pet.setVelocity(pet.getVelocity().add(away));
                    } else if (distance > 16.0) { // Too far
                        Vec3d closer = targetPos.subtract(petPos).normalize().multiply(0.05);
                        pet.setVelocity(pet.getVelocity().add(closer));
                    }
                }
            }
        }

        private PlayerEntity getOwner() {
            if (pet instanceof net.minecraft.entity.passive.TameableEntity tameable) {
                return (PlayerEntity) tameable.getOwner();
            }
            return null;
        }
    }

    /**
     * Applies mood-based pathfinding enhancements.
     * Modifies pathfinding penalties and preferences based on emotional state.
     */
    public static class MoodPathfindingEnhancement extends Goal {
        private final MobEntity pet;
        private int adjustmentTimer = 0;

        public MoodPathfindingEnhancement(MobEntity pet) {
            this.pet = pet;
            this.setControls(EnumSet.noneOf(Control.class));
        }

        @Override
        public boolean canStart() {
            return pet.getNavigation().isFollowingPath() || pet.getTarget() != null;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            adjustmentTimer = 0;
        }

        @Override
        public void tick() {
            adjustmentTimer++;
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            // Apply pathfinding adjustments every 2 seconds
            if (adjustmentTimer % 40 == 0) {
                applyMoodPathfindingAdjustments(pc);
            }
        }

        private void applyMoodPathfindingAdjustments(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return;

            switch (mood) {
                case CURIOUS -> {
                    // More willing to explore dangerous areas
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 4.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 2.0f);
                }
                case AFRAID -> {
                    // Very cautious pathfinding
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 20.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 16.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.LAVA, 25.0f);
                }
                case ANGRY, PASSIONATE -> {
                    // Reckless pathfinding - willing to take risks
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 1.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 0.5f);
                }
                case PROTECTIVE -> {
                    // Balanced pathfinding - calculated risks for protection
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 6.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 3.0f);
                }
                case FOCUSED -> {
                    // Efficient pathfinding - find optimal routes
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.FENCE, -2.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DOOR_WOOD_CLOSED, -1.0f);
                }
                case RESTLESS -> {
                    // Impatient pathfinding - prefer faster routes even if riskier
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER, -1.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER_BORDER, -1.0f);
                }
                case CALM, BONDED -> {
                    // Standard pathfinding - no modifications
                    // Reset to neutral values
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_OTHER, 8.0f);
                    pet.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_OTHER, 4.0f);
                }
            }
        }
    }

    /**
     * Manages mood-based stamina and recovery behaviors.
     * Simulates energy levels and rest needs based on emotional state.
     */
    public static class MoodStaminaManager extends Goal {
        private final MobEntity pet;
        private int staminaTimer = 0;
        private float virtualStamina = 100.0f;
        private int restTimer = 0;
        private static final float SPEED_EPSILON = 1.0e-4f;
        private static final Identifier STAMINA_SPEED_MODIFIER_ID = Identifier.of("petsplus", "stamina_speed");
        private float currentSpeedMultiplier = 1.0f;
        private double baselineMovementSpeed = Double.NaN;
        private EntityAttributeInstance cachedSpeedAttribute;
        private int modifierSyncTicker = 0;
        private static final int MODIFIER_SYNC_INTERVAL_TICKS = 5;

        public MoodStaminaManager(MobEntity pet) {
            this.pet = pet;
            this.setControls(EnumSet.noneOf(Control.class));
        }

        @Override
        public boolean canStart() {
            return true; // Always active
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public void start() {
            virtualStamina = 100.0f;
            staminaTimer = 0;
            restTimer = 0;
            cacheBaselineMovementSpeed();
            modifierSyncTicker = 0;
            syncModifierState();
            if (Math.abs(currentSpeedMultiplier - 1.0f) > SPEED_EPSILON) {
                SpeedModifierHelper.clearMovementSpeedModifier(pet, STAMINA_SPEED_MODIFIER_ID);
                currentSpeedMultiplier = 1.0f;
            }
        }

        @Override
        public void stop() {
            if (Math.abs(currentSpeedMultiplier - 1.0f) > SPEED_EPSILON) {
                SpeedModifierHelper.clearMovementSpeedModifier(pet, STAMINA_SPEED_MODIFIER_ID);
                currentSpeedMultiplier = 1.0f;
            }
            baselineMovementSpeed = Double.NaN;
            cachedSpeedAttribute = null;
        }

        @Override
        public void tick() {
            staminaTimer++;
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            if (++modifierSyncTicker >= MODIFIER_SYNC_INTERVAL_TICKS) {
                modifierSyncTicker = 0;
                syncModifierState();
            }

            // Update stamina every second
            if (staminaTimer % 20 == 0) {
                updateStamina(pc);
            }

            // Apply stamina effects every half second
            if (staminaTimer % 10 == 0) {
                applyStaminaEffects(pc);
            }
        }

        private void updateStamina(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return;

            float staminaDrain = 1.0f; // Base drain per second
            float staminaRecovery = 0.5f; // Base recovery when resting

            // Calculate activity level
            boolean isActive = pet.getNavigation().isFollowingPath() || 
                              pet.getTarget() != null || 
                              pet.getVelocity().horizontalLengthSquared() > 0.01;

            switch (mood) {
                case RESTLESS -> {
                    staminaDrain = 2.0f; // Drain faster due to constant movement
                    staminaRecovery = 0.3f; // Harder to recover when restless
                }
                case ANGRY, PASSIONATE -> {
                    staminaDrain = 1.8f; // High energy drain from intense emotions
                    staminaRecovery = 0.4f;
                }
                case AFRAID -> {
                    staminaDrain = 1.5f; // Anxiety drains energy
                    staminaRecovery = 0.7f; // But recovers quickly when safe
                }
                case HAPPY, PLAYFUL -> {
                    staminaDrain = 1.3f; // Playful energy use
                    staminaRecovery = 0.8f; // Good recovery from positive emotions
                }
                case CALM, BONDED -> {
                    staminaDrain = 0.8f; // Efficient energy use
                    staminaRecovery = 1.0f; // Best recovery rate
                }
                case FOCUSED -> {
                    staminaDrain = 1.1f; // Focused effort is efficient
                    staminaRecovery = 0.6f;
                }
                case SISU -> {
                    staminaDrain = 0.9f; // Resilient - uses energy efficiently
                    staminaRecovery = 0.9f; // Good recovery
                }
            }

            if (isActive) {
                virtualStamina = Math.max(0, virtualStamina - staminaDrain);
                restTimer = 0;
            } else {
                restTimer++;
                if (restTimer > 60) { // Resting for 3+ seconds
                    virtualStamina = Math.min(100, virtualStamina + staminaRecovery);
                }
            }
        }

        private void applyStaminaEffects(PetComponent pc) {
            float targetMultiplier = 1.0f;
            if (virtualStamina <= 20) {
                targetMultiplier = 0.7f;
            } else if (virtualStamina <= 50) {
                targetMultiplier = 0.85f;
            } else if (virtualStamina <= 80 && pet.getNavigation().isFollowingPath()) {
                targetMultiplier = 0.95f;
            }

            if (Math.abs(targetMultiplier - currentSpeedMultiplier) > SPEED_EPSILON) {
                SpeedModifierHelper.applyMovementSpeedMultiplier(
                    pet,
                    STAMINA_SPEED_MODIFIER_ID,
                    targetMultiplier
                );
                currentSpeedMultiplier = targetMultiplier;
            }

            if (virtualStamina <= 50) {
                if (virtualStamina <= 20) {
                    if (pet.getRandom().nextInt(40) == 0) {
                        pet.getNavigation().stop();
                        pet.setSneaking(true); // Visual indicator of tiredness
                    }
                } else {
                    if (pet.getRandom().nextInt(100) == 0) {
                        pet.getNavigation().stop();
                    }
                }
            }

            // Reset sneaking when stamina recovers
            if (virtualStamina > 30 && pet.isSneaking()) {
                pet.setSneaking(false);
            }
        }

        private void cacheBaselineMovementSpeed() {
            cachedSpeedAttribute = pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            if (cachedSpeedAttribute == null) {
                baselineMovementSpeed = Double.NaN;
                return;
            }
            baselineMovementSpeed = cachedSpeedAttribute.getBaseValue();
        }

        private void syncModifierState() {
            EntityAttributeInstance speedAttribute = cachedSpeedAttribute;
            if (speedAttribute == null) {
                cachedSpeedAttribute = pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                speedAttribute = cachedSpeedAttribute;
            }
            if (speedAttribute == null) {
                baselineMovementSpeed = Double.NaN;
                currentSpeedMultiplier = 1.0f;
                return;
            }
            cachedSpeedAttribute = speedAttribute;

            double baseValue = speedAttribute.getBaseValue();
            if (Double.isNaN(baselineMovementSpeed) || Math.abs(baseValue - baselineMovementSpeed) > SPEED_EPSILON) {
                baselineMovementSpeed = baseValue;
            }

            if (speedAttribute.getModifier(STAMINA_SPEED_MODIFIER_ID) == null && Math.abs(currentSpeedMultiplier - 1.0f) > SPEED_EPSILON) {
                currentSpeedMultiplier = 1.0f;
            }
        }
    }
}
