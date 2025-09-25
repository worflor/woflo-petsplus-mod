package woflo.petsplus.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.mixin.MobEntityAccessor;

import java.util.EnumSet;

/**
 * Subtle mood-influenced behaviors that enhance vanilla AI without overhauling it.
 * These are micro-behaviors that make pets feel more alive and responsive to their emotional state.
 */
public class MoodInfluencedBehaviors {

    /**
     * Add subtle mood-influenced behavior goals to a pet.
     * These run at low priority and don't interfere with core AI.
     */
    public static void addMoodBehaviors(MobEntity pet) {
        if (pet.getWorld().isClient) return;
        
        MobEntityAccessor accessor = (MobEntityAccessor) pet;
        
        // Add mood-influenced fidget behaviors (very low priority)
        accessor.getGoalSelector().add(25, new MoodFidgetGoal(pet));
        
        // Add mood-influenced movement variations
        accessor.getGoalSelector().add(26, new MoodMovementVariationGoal(pet));
        
        // Add mood-influenced looking/head movements
        accessor.getGoalSelector().add(27, new MoodLookBehaviorGoal(pet));
    }

    /**
     * Subtle fidget behaviors that reflect the pet's emotional state.
     * These are micro-animations and small movements that don't interfere with main actions.
     */
    public static class MoodFidgetGoal extends Goal {
        private final MobEntity pet;
        private int fidgetTimer = 0;
        private int behaviorDuration = 0;
        private FidgetType currentFidget = null;

        private enum FidgetType {
            HEAD_TILT,      // Curious, playful
            PAW_SCRAPE,     // Restless, anxious
            TAIL_SWISH,     // Happy, excited
            CROUCH_FIDGET,  // Afraid, cautious
            STRETCH,        // Calm, content
            SNIFF_GROUND,   // Curious, focused
            SHAKE,          // Wet, uncomfortable
            YAWN           // Tired, relaxed
        }

        public MoodFidgetGoal(MobEntity pet) {
            this.pet = pet;
            this.setControls(EnumSet.noneOf(Control.class)); // Don't interfere with other goals
        }

        @Override
        public boolean canStart() {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return false;
            
            // Only fidget when not actively doing something important
            if (pet.getNavigation().isFollowingPath() || pet.getTarget() != null) return false;
            
            // Restless pets fidget more frequently
            int baseChance = pc.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.3f) ? 30 : 80;
            
            // Afraid pets fidget more
            if (pc.hasMoodAbove(PetComponent.Mood.AFRAID, 0.2f)) baseChance = 20;
            
            return pet.getRandom().nextInt(baseChance) == 0;
        }

        @Override
        public boolean shouldContinue() {
            return behaviorDuration > 0 && currentFidget != null;
        }

        @Override
        public void start() {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            currentFidget = selectFidgetForMood(pc);
            behaviorDuration = switch (currentFidget) {
                case HEAD_TILT -> 20 + pet.getRandom().nextInt(15);
                case PAW_SCRAPE -> 15 + pet.getRandom().nextInt(10);
                case TAIL_SWISH -> 25 + pet.getRandom().nextInt(20);
                case CROUCH_FIDGET -> 30 + pet.getRandom().nextInt(25);
                case STRETCH -> 40 + pet.getRandom().nextInt(30);
                case SNIFF_GROUND -> 35 + pet.getRandom().nextInt(15);
                case SHAKE -> 25;
                case YAWN -> 20;
            };
            fidgetTimer = 0;
        }

        @Override
        public void tick() {
            fidgetTimer++;
            behaviorDuration--;

            switch (currentFidget) {
                case HEAD_TILT -> {
                    // Subtle head movements - look around curiously
                    if (fidgetTimer % 8 == 0) {
                        float yaw = pet.getYaw() + pet.getRandom().nextFloat() * 60 - 30;
                        pet.getLookControl().lookAt(
                            pet.getX() + Math.sin(Math.toRadians(yaw)) * 3,
                            pet.getEyeY(),
                            pet.getZ() + Math.cos(Math.toRadians(yaw)) * 3
                        );
                    }
                }
                case PAW_SCRAPE -> {
                    // Restless ground scraping - very subtle movement
                    if (fidgetTimer % 10 == 0 && pet.isOnGround()) {
                        Vec3d velocity = pet.getVelocity();
                        pet.setVelocity(velocity.x * 0.95, velocity.y, velocity.z * 0.95);
                    }
                }
                case TAIL_SWISH -> {
                    // Happy energy - slight bouncing motion
                    if (fidgetTimer % 12 == 0 && pet.isOnGround() && pet.getRandom().nextBoolean()) {
                        pet.setVelocity(pet.getVelocity().add(0, 0.05, 0));
                    }
                }
                case CROUCH_FIDGET -> {
                    // Fearful crouching - make pet appear smaller/lower
                    if (fidgetTimer % 15 == 0) {
                        pet.setSneaking(pet.getRandom().nextBoolean());
                    }
                }
                case STRETCH -> {
                    // Content stretching - temporary speed reduction, then brief acceleration
                    if (fidgetTimer < 20) {
                        pet.setMovementSpeed(pet.getMovementSpeed() * 0.8f);
                    } else if (fidgetTimer == 20) {
                        pet.setMovementSpeed(pet.getMovementSpeed() * 1.25f);
                    }
                }
                case SNIFF_GROUND -> {
                    // Curious sniffing - look down periodically
                    if (fidgetTimer % 8 == 0) {
                        pet.getLookControl().lookAt(
                            pet.getX(), 
                            pet.getY() - 1, 
                            pet.getZ()
                        );
                    }
                }
                case SHAKE -> {
                    // Quick shake animation - brief movement
                    if (fidgetTimer % 5 == 0) {
                        float shakeX = (pet.getRandom().nextFloat() - 0.5f) * 0.1f;
                        float shakeZ = (pet.getRandom().nextFloat() - 0.5f) * 0.1f;
                        pet.setVelocity(pet.getVelocity().add(shakeX, 0, shakeZ));
                    }
                }
                case YAWN -> {
                    // Relaxed yawning - slow head movement
                    if (fidgetTimer == 10) {
                        pet.getLookControl().lookAt(pet.getX(), pet.getEyeY() + 1, pet.getZ());
                    }
                }
            }
        }

        @Override
        public void stop() {
            currentFidget = null;
            behaviorDuration = 0;
            fidgetTimer = 0;
            pet.setSneaking(false); // Reset any crouching
        }

        private FidgetType selectFidgetForMood(PetComponent pc) {
            PetComponent.Mood currentMood = pc.getCurrentMood();
            if (currentMood == null) return FidgetType.SNIFF_GROUND;

            return switch (currentMood) {
                case HAPPY, PLAYFUL -> pet.getRandom().nextBoolean() ? FidgetType.TAIL_SWISH : FidgetType.HEAD_TILT;
                case CURIOUS -> pet.getRandom().nextBoolean() ? FidgetType.HEAD_TILT : FidgetType.SNIFF_GROUND;
                case RESTLESS -> pet.getRandom().nextBoolean() ? FidgetType.PAW_SCRAPE : FidgetType.TAIL_SWISH;
                case AFRAID -> pet.getRandom().nextBoolean() ? FidgetType.CROUCH_FIDGET : FidgetType.HEAD_TILT;
                case CALM, BONDED -> pet.getRandom().nextBoolean() ? FidgetType.STRETCH : FidgetType.YAWN;
                case FOCUSED -> FidgetType.SNIFF_GROUND;
                case PROTECTIVE -> FidgetType.HEAD_TILT; // Alert scanning
                case PASSIONATE -> FidgetType.TAIL_SWISH; // Energetic
                case YUGEN, SAUDADE -> FidgetType.YAWN; // Contemplative/melancholy
                case SISU -> FidgetType.STRETCH; // Determined/resilient
                case ANGRY -> FidgetType.PAW_SCRAPE; // Agitated
                default -> FidgetType.values()[pet.getRandom().nextInt(FidgetType.values().length)];
            };
        }
    }

    /**
     * Subtle movement variations based on mood.
     * Affects stride length, speed variations, and movement patterns without breaking core AI.
     */
    public static class MoodMovementVariationGoal extends Goal {
        private final MobEntity pet;
        private int adjustmentTimer = 0;
        private float baseSpeed;
        private boolean initialized = false;

        public MoodMovementVariationGoal(MobEntity pet) {
            this.pet = pet;
            this.setControls(EnumSet.noneOf(Control.class));
        }

        @Override
        public boolean canStart() {
            return pet.getNavigation().isFollowingPath() || pet.getVelocity().horizontalLengthSquared() > 0.01;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            if (!initialized) {
                baseSpeed = pet.getMovementSpeed();
                initialized = true;
            }
            adjustmentTimer = 0;
        }

        @Override
        public void tick() {
            adjustmentTimer++;
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            // Apply mood-influenced movement modifications every half second
            if (adjustmentTimer % 10 == 0) {
                applyMoodMovementEffects(pc);
            }

            // Apply stride variations
            if (adjustmentTimer % 5 == 0) {
                applyStrideVariations(pc);
            }
        }

        private void applyMoodMovementEffects(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return;

            float speedMultiplier = 1.0f;
            
            switch (mood) {
                case HAPPY, PLAYFUL -> {
                    // Bouncy, energetic movement
                    speedMultiplier = 1.05f + (pet.getRandom().nextFloat() * 0.1f);
                    if (pet.isOnGround() && pet.getRandom().nextInt(20) == 0) {
                        pet.jump(); // Occasional happy hops
                    }
                }
                case CURIOUS -> {
                    // Variable speed - stopping and starting
                    speedMultiplier = pet.getRandom().nextBoolean() ? 1.1f : 0.9f;
                }
                case RESTLESS -> {
                    // Erratic speed changes
                    speedMultiplier = 0.8f + (pet.getRandom().nextFloat() * 0.6f); // 0.8 to 1.4
                }
                case AFRAID -> {
                    // Quick, nervous movements
                    speedMultiplier = pet.getRandom().nextBoolean() ? 1.2f : 0.7f;
                }
                case CALM, BONDED -> {
                    // Steady, relaxed movement
                    speedMultiplier = 0.95f + (pet.getRandom().nextFloat() * 0.1f); // 0.95 to 1.05
                }
                case FOCUSED -> {
                    // Deliberate, measured movement
                    speedMultiplier = 1.0f; // Consistent speed
                }
                case PROTECTIVE -> {
                    // Alert, ready-to-respond movement
                    speedMultiplier = 1.02f + (pet.getRandom().nextFloat() * 0.06f);
                }
                case ANGRY -> {
                    // Aggressive, purposeful movement
                    speedMultiplier = 1.1f + (pet.getRandom().nextFloat() * 0.1f);
                }
                case PASSIONATE -> {
                    // Intense, driven movement
                    speedMultiplier = 1.08f + (pet.getRandom().nextFloat() * 0.12f);
                }
                case YUGEN -> {
                    // Contemplative, measured movement
                    speedMultiplier = 0.9f + (pet.getRandom().nextFloat() * 0.1f);
                }
                case SAUDADE -> {
                    // Melancholy, slower movement
                    speedMultiplier = 0.85f + (pet.getRandom().nextFloat() * 0.1f);
                }
                case SISU -> {
                    // Determined, steady movement
                    speedMultiplier = 1.0f + (pet.getRandom().nextFloat() * 0.05f);
                }
            }

            // Apply the speed adjustment
            pet.setMovementSpeed(baseSpeed * speedMultiplier);
        }

        private void applyStrideVariations(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null || !pet.getNavigation().isFollowingPath()) return;

            Vec3d velocity = pet.getVelocity();
            double speed = velocity.horizontalLength();
            
            if (speed < 0.01) return; // Not moving

            switch (mood) {
                case PLAYFUL, HAPPY -> {
                    // Zigzag movement occasionally
                    if (pet.getRandom().nextInt(30) == 0) {
                        float sideStep = (pet.getRandom().nextFloat() - 0.5f) * 0.3f;
                        Vec3d perpendicular = new Vec3d(-velocity.z, 0, velocity.x).normalize();
                        pet.setVelocity(velocity.add(perpendicular.multiply(sideStep)));
                    }
                }
                case RESTLESS -> {
                    // Slight random direction changes
                    if (pet.getRandom().nextInt(15) == 0) {
                        float randomOffset = (pet.getRandom().nextFloat() - 0.5f) * 0.2f;
                        Vec3d perpendicular = new Vec3d(-velocity.z, 0, velocity.x).normalize();
                        pet.setVelocity(velocity.add(perpendicular.multiply(randomOffset)));
                    }
                }
                case AFRAID -> {
                    // Jittery, less direct movement
                    if (pet.getRandom().nextInt(8) == 0) {
                        float jitter = (pet.getRandom().nextFloat() - 0.5f) * 0.15f;
                        Vec3d perpendicular = new Vec3d(-velocity.z, 0, velocity.x).normalize();
                        pet.setVelocity(velocity.add(perpendicular.multiply(jitter)));
                    }
                }
                case FOCUSED, PROTECTIVE -> {
                    // More direct, purposeful movement - reduce random variations
                    pet.setVelocity(velocity.multiply(1.02)); // Slight forward bias
                }
                case CURIOUS -> {
                    // Variable direction changes - exploring
                    if (pet.getRandom().nextInt(25) == 0) {
                        float exploreOffset = (pet.getRandom().nextFloat() - 0.5f) * 0.25f;
                        Vec3d perpendicular = new Vec3d(-velocity.z, 0, velocity.x).normalize();
                        pet.setVelocity(velocity.add(perpendicular.multiply(exploreOffset)));
                    }
                }
                case ANGRY, PASSIONATE -> {
                    // Aggressive, direct movement with slight forward bias
                    pet.setVelocity(velocity.multiply(1.03));
                }
                case CALM, BONDED, SISU -> {
                    // Steady, reliable movement - minimal variation
                    // No modifications - keep natural movement
                }
                case YUGEN, SAUDADE -> {
                    // Contemplative, slower direction changes
                    if (pet.getRandom().nextInt(40) == 0) {
                        float contemplativeOffset = (pet.getRandom().nextFloat() - 0.5f) * 0.1f;
                        Vec3d perpendicular = new Vec3d(-velocity.z, 0, velocity.x).normalize();
                        pet.setVelocity(velocity.add(perpendicular.multiply(contemplativeOffset)));
                    }
                }
            }
        }

        @Override
        public void stop() {
            // Reset to base speed when stopping
            if (initialized) {
                pet.setMovementSpeed(baseSpeed);
            }
        }
    }

    /**
     * Mood-influenced looking behavior.
     * Changes how pets scan their environment and focus their attention.
     */
    public static class MoodLookBehaviorGoal extends Goal {
        private final MobEntity pet;
        private int scanTimer = 0;
        private Vec3d currentLookTarget = null;
        private int lookDuration = 0;

        public MoodLookBehaviorGoal(MobEntity pet) {
            this.pet = pet;
            this.setControls(EnumSet.of(Control.LOOK));
        }

        @Override
        public boolean canStart() {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return false;
            
            // Don't interfere with combat or important navigation
            if (pet.getTarget() != null || pet.getNavigation().isFollowingPath()) return false;
            
            // More frequent looking for curious/restless pets
            int chance = pc.hasMoodAbove(PetComponent.Mood.CURIOUS, 0.3f) ? 15 : 
                        pc.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.3f) ? 20 : 40;
            
            return pet.getRandom().nextInt(chance) == 0;
        }

        @Override
        public boolean shouldContinue() {
            return lookDuration > 0;
        }

        @Override
        public void start() {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            currentLookTarget = selectLookTarget(pc);
            lookDuration = calculateLookDuration(pc);
            scanTimer = 0;
        }

        @Override
        public void tick() {
            scanTimer++;
            lookDuration--;

            if (currentLookTarget != null) {
                pet.getLookControl().lookAt(
                    currentLookTarget.x,
                    currentLookTarget.y,
                    currentLookTarget.z
                );
            }

            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            // Dynamic look target updates based on mood
            if (scanTimer % 10 == 0) {
                updateLookBehavior(pc);
            }
        }

        @Override
        public void stop() {
            currentLookTarget = null;
            lookDuration = 0;
            scanTimer = 0;
        }

        private Vec3d selectLookTarget(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return getRandomLookTarget();

            return switch (mood) {
                case CURIOUS -> getExplorationTarget();
                case PROTECTIVE -> getThreatScanTarget();
                case AFRAID -> getOwnerOrSafetyTarget();
                case FOCUSED -> getDetailedInspectionTarget();
                case PLAYFUL, HAPPY -> getPlayfulLookTarget();
                case RESTLESS -> getRandomMovingTarget();
                case ANGRY, PASSIONATE -> getThreatScanTarget(); // Alert and aggressive
                case CALM, BONDED -> getOwnerOrSafetyTarget(); // Look toward owner for comfort
                case YUGEN, SAUDADE -> getDetailedInspectionTarget(); // Contemplative inspection
                case SISU -> getExplorationTarget(); // Determined exploration
                default -> getRandomLookTarget();
            };
        }

        private int calculateLookDuration(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return 30;

            return switch (mood) {
                case FOCUSED -> 60 + pet.getRandom().nextInt(40); // Long, concentrated looks
                case CURIOUS -> 40 + pet.getRandom().nextInt(30); // Moderate attention span
                case RESTLESS -> 15 + pet.getRandom().nextInt(15); // Quick, darting looks
                case AFRAID -> 20 + pet.getRandom().nextInt(20);   // Brief, nervous glances
                case PLAYFUL -> 25 + pet.getRandom().nextInt(25);  // Variable attention
                default -> 30 + pet.getRandom().nextInt(20);
            };
        }

        private void updateLookBehavior(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return;

            switch (mood) {
                case CURIOUS -> {
                    // Scan around systematically
                    if (scanTimer % 20 == 0) {
                        currentLookTarget = getExplorationTarget();
                    }
                }
                case RESTLESS -> {
                    // Constantly changing focus
                    if (scanTimer % 8 == 0) {
                        currentLookTarget = getRandomMovingTarget();
                    }
                }
                case PROTECTIVE -> {
                    // Threat assessment scans
                    if (scanTimer % 15 == 0) {
                        currentLookTarget = getThreatScanTarget();
                    }
                }
                case AFRAID -> {
                    // Quick safety checks
                    if (scanTimer % 12 == 0) {
                        currentLookTarget = getOwnerOrSafetyTarget();
                    }
                }
                case PLAYFUL, HAPPY -> {
                    // Playful, bouncy looking
                    if (scanTimer % 16 == 0) {
                        currentLookTarget = getPlayfulLookTarget();
                    }
                }
                case FOCUSED -> {
                    // Concentrated inspection
                    if (scanTimer % 25 == 0) {
                        currentLookTarget = getDetailedInspectionTarget();
                    }
                }
                case ANGRY, PASSIONATE -> {
                    // Aggressive scanning
                    if (scanTimer % 12 == 0) {
                        currentLookTarget = getThreatScanTarget();
                    }
                }
                case CALM, BONDED -> {
                    // Gentle, comfortable looking
                    if (scanTimer % 30 == 0) {
                        currentLookTarget = getOwnerOrSafetyTarget();
                    }
                }
                case YUGEN, SAUDADE -> {
                    // Contemplative, distant looking
                    if (scanTimer % 35 == 0) {
                        currentLookTarget = getDetailedInspectionTarget();
                    }
                }
                case SISU -> {
                    // Determined exploration
                    if (scanTimer % 22 == 0) {
                        currentLookTarget = getExplorationTarget();
                    }
                }
            }
        }

        private Vec3d getRandomLookTarget() {
            float yaw = pet.getRandom().nextFloat() * 360;
            float pitch = (pet.getRandom().nextFloat() - 0.5f) * 60; // -30 to 30 degrees
            
            double distance = 3 + pet.getRandom().nextDouble() * 5;
            double x = pet.getX() + Math.sin(Math.toRadians(yaw)) * distance;
            double z = pet.getZ() + Math.cos(Math.toRadians(yaw)) * distance;
            double y = pet.getEyeY() + Math.sin(Math.toRadians(pitch)) * distance;
            
            return new Vec3d(x, y, z);
        }

        private Vec3d getExplorationTarget() {
            // Look at interesting blocks or features nearby
            return getRandomLookTarget(); // For now, same as random but could be enhanced
        }

        private Vec3d getThreatScanTarget() {
            // Scan perimeter for potential threats
            float yaw = pet.getYaw() + (pet.getRandom().nextFloat() - 0.5f) * 120; // 60 degree arc
            double distance = 8 + pet.getRandom().nextDouble() * 4;
            
            double x = pet.getX() + Math.sin(Math.toRadians(yaw)) * distance;
            double z = pet.getZ() + Math.cos(Math.toRadians(yaw)) * distance;
            double y = pet.getEyeY() + (pet.getRandom().nextFloat() - 0.3f) * 2;
            
            return new Vec3d(x, y, z);
        }

        private Vec3d getOwnerOrSafetyTarget() {
            // Look toward owner if nearby, otherwise look for safe spots
            PlayerEntity owner = (PlayerEntity) ((net.minecraft.entity.passive.TameableEntity) pet).getOwner();
            if (owner != null && pet.squaredDistanceTo(owner) < 64) {
                return owner.getPos().add(0, owner.getHeight() * 0.5, 0);
            }
            return getRandomLookTarget();
        }

        private Vec3d getDetailedInspectionTarget() {
            // Look down or at nearby objects for detailed inspection
            double x = pet.getX() + (pet.getRandom().nextFloat() - 0.5f) * 2;
            double z = pet.getZ() + (pet.getRandom().nextFloat() - 0.5f) * 2;
            double y = pet.getY() - 0.5 + pet.getRandom().nextFloat();
            
            return new Vec3d(x, y, z);
        }

        private Vec3d getPlayfulLookTarget() {
            // Playful, bouncy look targets
            float yaw = pet.getRandom().nextFloat() * 360;
            double distance = 2 + pet.getRandom().nextDouble() * 4;
            double x = pet.getX() + Math.sin(Math.toRadians(yaw)) * distance;
            double z = pet.getZ() + Math.cos(Math.toRadians(yaw)) * distance;
            double y = pet.getEyeY() + (pet.getRandom().nextFloat() - 0.2f) * 3; // Can look up more
            
            return new Vec3d(x, y, z);
        }

        private Vec3d getRandomMovingTarget() {
            // Erratic, quickly changing targets
            return getRandomLookTarget();
        }
    }
}