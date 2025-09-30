package woflo.petsplus.ai;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import woflo.petsplus.ai.MoodGoalThrottleConfig;
import woflo.petsplus.ai.MoodGoalThrottles;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.tags.PetsplusBlockTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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
        private static final float SPEED_EPSILON = 1.0e-4f;
        private static final Identifier STRETCH_SPEED_MODIFIER_ID = Identifier.of("petsplus", "stretch_fidget_speed");
        private float currentStretchMultiplier = 1.0f;
        private final EnumMap<FidgetType, Long> fidgetCooldowns = new EnumMap<>(FidgetType.class);
        private long lastFidgetTick = -200;
        private long nextGlobalStartTick = 0;
        private float globalChanceAccumulator = 0.0f;
        private long lastChanceTick = Long.MIN_VALUE;
        private Vec3d sniffFocus;
        private float cachedAgilityModifier = 0.0f;
        private float cachedVitalityModifier = 0.0f;
        private List<SniffPreference> sniffPreferences = List.of();
        private PetComponent.Mood cachedPreferenceMood;
        private int cachedPreferenceStrengthBucket = -1;

        private static final float SNIFF_WEIGHT_EPSILON = 1.0e-4f;

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

            if (pet.getNavigation().isFollowingPath() || pet.getTarget() != null) return false;

            long now = pet.getWorld().getTime();
            if (now < nextGlobalStartTick) return false;

            PetComponent.Mood mood = pc.getCurrentMood();
            float moodStrength = mood != null ? MathHelper.clamp(pc.getMoodStrength(mood), 0f, 1f) : 0f;
            float pressure = MathHelper.clamp((now - lastFidgetTick) / 200f, 0f, 1.5f);
            MoodGoalThrottleConfig throttleConfig = mood != null ? MoodGoalThrottles.getConfig(mood) : null;

            int rollBound = throttleConfig != null ? throttleConfig.baseChance() : 90;
            int strengthBonus = throttleConfig != null && throttleConfig.strengthChanceBonus() > 0
                ? throttleConfig.strengthChanceBonus()
                : 30;
            int minRoll = throttleConfig != null ? throttleConfig.minRollBound() : 12;
            int maxRoll = throttleConfig != null ? throttleConfig.maxRollBound() : 140;
            if (mood == PetComponent.Mood.RESTLESS || pc.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.4f)) {
                rollBound -= 25;
            }
            if (mood == PetComponent.Mood.AFRAID || pc.hasMoodAbove(PetComponent.Mood.AFRAID, 0.25f)) {
                rollBound -= 20;
            }
            rollBound -= Math.round(moodStrength * strengthBonus);

            if (throttleConfig != null && throttleConfig.pressureIntervalTicks() > 0
                && throttleConfig.pressureChanceBonus() > 0) {
                long sinceLast = now - lastFidgetTick;
                int steps = (int) Math.min(
                    throttleConfig.maxPressureBonus() / Math.max(throttleConfig.pressureChanceBonus(), 1),
                    sinceLast / throttleConfig.pressureIntervalTicks()
                );
                rollBound -= steps * throttleConfig.pressureChanceBonus();
            } else {
                rollBound -= Math.round(pressure * 20f);
            }

            float agilityScaling = MathHelper.clamp(1.0f - cachedAgilityModifier * 0.45f, 0.85f, 1.15f);
            rollBound = Math.round(rollBound * agilityScaling);
            rollBound = MathHelper.clamp(rollBound, minRoll, maxRoll);

            if (rollBound <= 1) {
                globalChanceAccumulator = 0.0f;
                lastChanceTick = now;
                return true;
            }

            if (now != lastChanceTick) {
                float increment = 1.0f / (float) rollBound;
                globalChanceAccumulator = MathHelper.clamp(globalChanceAccumulator + increment, 0.0f, 1.0f);
                lastChanceTick = now;
            }
            float roll = pet.getRandom().nextFloat();
            if (roll < globalChanceAccumulator) {
                globalChanceAccumulator = 0.0f;
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldContinue() {
            return behaviorDuration > 0 && currentFidget != null;
        }

        @Override
        public void start() {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;

            refreshCharacteristicModifiers(pc);
            updateSniffPreferences(pc);

            nextGlobalStartTick = pet.getWorld().getTime() + 20;
            sniffFocus = null;
            currentFidget = selectFidgetForMood(pc);
            behaviorDuration = switch (currentFidget) {
                case HEAD_TILT -> 20 + pet.getRandom().nextInt(15);
                case PAW_SCRAPE -> 15 + pet.getRandom().nextInt(10);
                case TAIL_SWISH -> 25 + pet.getRandom().nextInt(20);
                case CROUCH_FIDGET -> scaleDuration(30, 25, pc);
                case STRETCH -> scaleDuration(40, 30, pc);
                case SNIFF_GROUND -> {
                    sniffFocus = findSniffTarget();
                    yield scaleDuration(35, 20, pc);
                }
                case SHAKE -> 25;
                case YAWN -> 20;
            };
            fidgetTimer = 0;
            globalChanceAccumulator = 0.0f;
            lastChanceTick = pet.getWorld().getTime();
            if (currentFidget != FidgetType.STRETCH && Math.abs(currentStretchMultiplier - 1.0f) > SPEED_EPSILON) {
                SpeedModifierHelper.clearMovementSpeedModifier(pet, STRETCH_SPEED_MODIFIER_ID);
                currentStretchMultiplier = 1.0f;
            }
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
                    applyStretchSpeedAdjustment();
                }
                case SNIFF_GROUND -> {
                    if (fidgetTimer % 6 == 0) {
                        if (sniffFocus == null || pet.squaredDistanceTo(sniffFocus) > 9.0) {
                            sniffFocus = findSniffTarget();
                        }

                        Vec3d focus = sniffFocus != null ? sniffFocus : pet.getPos().subtract(0, 1, 0);
                        pet.getLookControl().lookAt(focus.x, focus.y, focus.z);
                        if (sniffFocus != null && pet.getRandom().nextBoolean()) {
                            Vec3d nudge = sniffFocus.subtract(pet.getPos()).normalize().multiply(0.05);
                            pet.setVelocity(pet.getVelocity().add(nudge.x, 0, nudge.z));
                        }
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
            FidgetType previous = currentFidget;
            currentFidget = null;
            behaviorDuration = 0;
            fidgetTimer = 0;
            pet.setSneaking(false); // Reset any crouching
            if (Math.abs(currentStretchMultiplier - 1.0f) > SPEED_EPSILON) {
                SpeedModifierHelper.clearMovementSpeedModifier(pet, STRETCH_SPEED_MODIFIER_ID);
                currentStretchMultiplier = 1.0f;
            }
            long now = pet.getWorld().getTime();
            if (previous != null) {
                fidgetCooldowns.put(previous, now + getCooldownFor(previous));
            }
            lastFidgetTick = now;
            nextGlobalStartTick = Math.max(nextGlobalStartTick, now + 30);
            sniffFocus = null;
            globalChanceAccumulator = 0.0f;
            lastChanceTick = now;
        }

        private FidgetType selectFidgetForMood(PetComponent pc) {
            PetComponent.Mood currentMood = pc.getCurrentMood();
            if (currentMood == null) return FidgetType.SNIFF_GROUND;

            List<FidgetType> weighted = new ArrayList<>();
            float intensity = MathHelper.clamp(pc.getMoodStrength(currentMood), 0f, 1f);

            switch (currentMood) {
                case HAPPY, PLAYFUL -> {
                    addFidgetOption(weighted, FidgetType.TAIL_SWISH, 2f);
                    addFidgetOption(weighted, FidgetType.HEAD_TILT, 1.5f);
                }
                case CURIOUS -> {
                    addFidgetOption(weighted, FidgetType.SNIFF_GROUND, 2.2f + intensity);
                    addFidgetOption(weighted, FidgetType.HEAD_TILT, 1.3f);
                }
                case RESTLESS -> {
                    addFidgetOption(weighted, FidgetType.PAW_SCRAPE, 2.4f + intensity);
                    addFidgetOption(weighted, FidgetType.TAIL_SWISH, 1.6f);
                }
                case AFRAID -> {
                    addFidgetOption(weighted, FidgetType.CROUCH_FIDGET, 2.3f + intensity * 0.5f);
                    addFidgetOption(weighted, FidgetType.HEAD_TILT, 1.2f);
                }
                case CALM, BONDED -> {
                    addFidgetOption(weighted, FidgetType.STRETCH, adjustStretchWeight(1.8f));
                    addFidgetOption(weighted, FidgetType.YAWN, 1.6f);
                }
                case FOCUSED -> {
                    addFidgetOption(weighted, FidgetType.SNIFF_GROUND, 2.0f + intensity);
                }
                case PROTECTIVE -> addFidgetOption(weighted, FidgetType.HEAD_TILT, 2.0f + intensity * 0.5f);
                case PASSIONATE -> {
                    addFidgetOption(weighted, FidgetType.TAIL_SWISH, 2.0f + intensity);
                    addFidgetOption(weighted, FidgetType.SHAKE, 1.0f + intensity * 0.5f);
                }
                case YUGEN, SAUDADE -> addFidgetOption(weighted, FidgetType.YAWN, 2.2f);
                case SISU -> {
                    addFidgetOption(weighted, FidgetType.STRETCH, adjustStretchWeight(2.3f));
                    addFidgetOption(weighted, FidgetType.HEAD_TILT, 1.0f);
                }
                case ANGRY -> {
                    addFidgetOption(weighted, FidgetType.PAW_SCRAPE, 2.5f + intensity);
                    addFidgetOption(weighted, FidgetType.SHAKE, 1.4f);
                }
                default -> {}
            }

            if (weighted.isEmpty()) {
                addFidgetOption(weighted, FidgetType.values()[pet.getRandom().nextInt(FidgetType.values().length)], 1.0f);
            }

            weighted.removeIf(type -> {
                Long cooldown = fidgetCooldowns.get(type);
                return cooldown != null && pet.getWorld().getTime() < cooldown;
            });

            if (weighted.isEmpty()) {
                return FidgetType.SNIFF_GROUND;
            }

            return weighted.get(pet.getRandom().nextInt(weighted.size()));
        }

        private void applyStretchSpeedAdjustment() {
            float targetMultiplier;
            if (fidgetTimer <= 20) {
                targetMultiplier = 0.8f;
            } else if (fidgetTimer <= 25) {
                targetMultiplier = 1.05f;
            } else {
                targetMultiplier = 1.0f;
            }

            if (Math.abs(targetMultiplier - currentStretchMultiplier) > SPEED_EPSILON) {
                SpeedModifierHelper.applyMovementSpeedMultiplier(
                    pet,
                    STRETCH_SPEED_MODIFIER_ID,
                    targetMultiplier
                );
                currentStretchMultiplier = targetMultiplier;
            }
        }

        private int scaleDuration(int base, int variance, PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            float strength = mood != null ? MathHelper.clamp(pc.getMoodStrength(mood), 0f, 1f) : 0f;
            int randomComponent = pet.getRandom().nextInt(Math.max(1, variance));
            int moodBonus = Math.round(strength * 10f);
            float agilityFactor = MathHelper.clamp(1.0f + cachedAgilityModifier * 0.6f, 0.75f, 1.25f);
            int scaledMoodBonus = MathHelper.clamp(Math.round(moodBonus * agilityFactor), 0, 10);
            int duration = base + scaledMoodBonus + randomComponent;
            int maxDuration = base + Math.max(0, variance - 1) + 10;
            return MathHelper.clamp(duration, base, maxDuration);
        }

        private void addFidgetOption(List<FidgetType> pool, FidgetType type, float weight) {
            int count = Math.max(1, Math.round(weight * 2));
            for (int i = 0; i < count; i++) {
                pool.add(type);
            }
        }

        private float adjustStretchWeight(float baseWeight) {
            float factor = MathHelper.clamp(1.0f + cachedVitalityModifier * 0.6f, 0.85f, 1.15f);
            return Math.max(0.2f, baseWeight * factor);
        }

        private void refreshCharacteristicModifiers(PetComponent pc) {
            cachedAgilityModifier = 0.0f;
            cachedVitalityModifier = 0.0f;

            var characteristics = pc.getCharacteristics();
            if (characteristics != null) {
                var roleType = pc.getRoleType(false);
                cachedAgilityModifier = characteristics.getAgilityModifier(roleType);
                cachedVitalityModifier = characteristics.getVitalityModifier(roleType);
            }
        }

        private long getCooldownFor(FidgetType type) {
            return switch (type) {
                case STRETCH -> 140L;
                case YAWN -> 120L;
                case SHAKE -> 100L;
                case CROUCH_FIDGET -> 110L;
                case SNIFF_GROUND -> 80L;
                case HEAD_TILT -> 70L;
                case TAIL_SWISH -> 75L;
                case PAW_SCRAPE -> 90L;
            };
        }

        private Vec3d findSniffTarget() {
            if (sniffPreferences.isEmpty()) {
                PetComponent pc = PetComponent.get(pet);
                if (pc != null) {
                    updateSniffPreferences(pc);
                }
            }

            if (sniffPreferences.isEmpty()) {
                return null;
            }

            BlockPos origin = pet.getBlockPos();
            int radius = 3;
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            Vec3d bestTarget = null;
            float bestWeight = -Float.MAX_VALUE;
            double closestDistance = Double.MAX_VALUE;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        BlockState state = pet.getWorld().getBlockState(mutable);
                        if (state.isAir()) continue;

                        float weight = getSniffWeight(state);
                        if (weight <= 0f) continue;

                        double distance = mutable.getSquaredDistance(origin);
                        if (weight > bestWeight + SNIFF_WEIGHT_EPSILON
                            || (Math.abs(weight - bestWeight) <= SNIFF_WEIGHT_EPSILON && distance < closestDistance)) {
                            bestWeight = weight;
                            closestDistance = distance;
                            bestTarget = Vec3d.ofCenter(mutable);
                        }
                    }
                }
            }

            return bestTarget;
        }

        private float getSniffWeight(BlockState state) {
            for (SniffPreference preference : sniffPreferences) {
                if (state.isIn(preference.tag())) {
                    return preference.weight();
                }
            }
            return -1f;
        }

        private void updateSniffPreferences(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            float moodStrength = mood != null ? MathHelper.clamp(pc.getMoodStrength(mood), 0f, 1f) : 0f;
            int strengthBucket = MathHelper.clamp(Math.round(moodStrength * 10f), 0, 10);

            if (!sniffPreferences.isEmpty()
                && cachedPreferenceMood == mood
                && cachedPreferenceStrengthBucket == strengthBucket) {
                return;
            }

            Map<TagKey<Block>, Float> weights = new LinkedHashMap<>();

            addSniffWeight(weights, PetsplusBlockTags.SNIFF_COMFORTS, 0.35f);
            addSniffWeight(weights, PetsplusBlockTags.SNIFF_CURIOSITIES, 0.32f);
            addSniffWeight(weights, PetsplusBlockTags.SNIFF_WARDING, 0.3f);
            addSniffWeight(weights, PetsplusBlockTags.SNIFF_STORAGE, 0.28f);
            addSniffWeight(weights, BlockTags.CROPS, 0.25f);
            addSniffWeight(weights, BlockTags.FLOWERS, 0.3f);
            addSniffWeight(weights, BlockTags.WOOL, 0.25f);
            addSniffWeight(weights, BlockTags.BEDS, 0.25f);

            if (mood != null) {
                if (isCuriousAligned(mood)) {
                    float curiousWeight = 0.55f + (moodStrength * 0.45f);
                    addSniffWeight(weights, PetsplusBlockTags.SNIFF_CURIOSITIES, curiousWeight);
                    addSniffWeight(weights, BlockTags.CROPS, 0.25f + moodStrength * 0.2f);
                }
                if (isProtectiveAligned(mood)) {
                    float protectiveWeight = 0.5f + (moodStrength * 0.4f);
                    addSniffWeight(weights, PetsplusBlockTags.SNIFF_WARDING, protectiveWeight);
                    addSniffWeight(weights, PetsplusBlockTags.SNIFF_STORAGE, 0.45f + moodStrength * 0.35f);
                }
                if (isCalmAligned(mood)) {
                    float calmWeight = 0.6f + (moodStrength * 0.4f);
                    addSniffWeight(weights, PetsplusBlockTags.SNIFF_COMFORTS, calmWeight);
                    addSniffWeight(weights, BlockTags.FLOWERS, 0.45f + moodStrength * 0.25f);
                    addSniffWeight(weights, BlockTags.WOOL, 0.35f + moodStrength * 0.2f);
                    addSniffWeight(weights, BlockTags.BEDS, 0.35f + moodStrength * 0.2f);
                }
            }

            List<Map.Entry<TagKey<Block>, Float>> entries = new ArrayList<>(weights.entrySet());
            entries.sort(Comparator
                .comparing(Map.Entry<TagKey<Block>, Float>::getValue)
                .reversed()
                .thenComparing(entry -> entry.getKey().id()));
            List<SniffPreference> sorted = new ArrayList<>(entries.size());
            for (Map.Entry<TagKey<Block>, Float> entry : entries) {
                sorted.add(new SniffPreference(entry.getKey(), entry.getValue()));
            }
            sniffPreferences = List.copyOf(sorted);
            cachedPreferenceMood = mood;
            cachedPreferenceStrengthBucket = strengthBucket;
        }

        private void addSniffWeight(Map<TagKey<Block>, Float> weights, TagKey<Block> tag, float weight) {
            if (tag == null || weight <= 0f) {
                return;
            }

            weights.merge(tag, weight, (existing, replacement) -> Math.max(existing, replacement));
        }

        private boolean isCuriousAligned(PetComponent.Mood mood) {
            return switch (mood) {
                case CURIOUS, PLAYFUL, HAPPY, FOCUSED, SISU -> true;
                default -> false;
            };
        }

        private boolean isProtectiveAligned(PetComponent.Mood mood) {
            return switch (mood) {
                case PROTECTIVE, ANGRY, PASSIONATE, AFRAID, RESTLESS -> true;
                default -> false;
            };
        }

        private boolean isCalmAligned(PetComponent.Mood mood) {
            return switch (mood) {
                case CALM, BONDED, YUGEN, SAUDADE -> true;
                default -> false;
            };
        }

        private record SniffPreference(TagKey<Block> tag, float weight) {
        }
    }

    /**
     * Subtle movement variations based on mood.
     * Affects stride length, speed variations, and movement patterns without breaking core AI.
     */
    public static class MoodMovementVariationGoal extends Goal {
        private final MobEntity pet;
        private int adjustmentTimer = 0;
        private static final Identifier MOVEMENT_VARIATION_MODIFIER_ID = Identifier.of("petsplus", "mood_variation_speed");
        private static final float SPEED_EPSILON = 1.0e-4f;
        private float currentSpeedMultiplier = 1.0f;
        private int contextTimer;
        private Vec3d cachedItemInterest;
        private long lastItemInterestTick = Long.MIN_VALUE;
        private static final int ITEM_INTEREST_REFRESH_TICKS = 30;
        private float cachedAgilityModifier = 0.0f;
        private float cachedVitalityModifier = 0.0f;
        private final EnumMap<PetComponent.Mood, Long> moodActionCooldowns = new EnumMap<>(PetComponent.Mood.class);
        private static final int HAPPY_HOP_COOLDOWN = 45;
        private static final int RESTLESS_JITTER_COOLDOWN = 25;

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
            if (Math.abs(currentSpeedMultiplier - 1.0f) > SPEED_EPSILON) {
                SpeedModifierHelper.clearMovementSpeedModifier(pet, MOVEMENT_VARIATION_MODIFIER_ID);
                currentSpeedMultiplier = 1.0f;
            }
            adjustmentTimer = 0;
            contextTimer = 0;
            cachedItemInterest = null;
            lastItemInterestTick = Long.MIN_VALUE;

            PetComponent pc = PetComponent.get(pet);
            cacheCharacteristicModifiers(pc);
            moodActionCooldowns.clear();
        }

        @Override
        public void tick() {
            adjustmentTimer++;
            contextTimer++;
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

            if (contextTimer % 15 == 0) {
                applyContextualNudges(pc);
            }
        }

        private void applyMoodMovementEffects(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) {
                if (Math.abs(currentSpeedMultiplier - 1.0f) > SPEED_EPSILON) {
                    SpeedModifierHelper.clearMovementSpeedModifier(pet, MOVEMENT_VARIATION_MODIFIER_ID);
                    currentSpeedMultiplier = 1.0f;
                }
                return;
            }

            float speedMultiplier = 1.0f;
            boolean inWater = pet.isTouchingWater();
            BlockState ground = pet.getWorld().getBlockState(pet.getBlockPos().down());
            float slipperiness = ground.getBlock().getSlipperiness();

            switch (mood) {
                case HAPPY, PLAYFUL -> {
                    // Bouncy, energetic movement
                    float min = 1.05f;
                    float max = 1.15f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float agilityFactor = MathHelper.clamp(1.0f + cachedAgilityModifier * 0.25f, 0.9f, 1.1f);
                    speedMultiplier = clampToRange(base * agilityFactor, min, max);
                    if (pet.isOnGround() && pet.getRandom().nextInt(20) == 0
                        && tryTriggerMoodAction(mood, HAPPY_HOP_COOLDOWN)) {
                        pet.jump(); // Occasional happy hops with cooldown
                    }
                }
                case CURIOUS -> {
                    // Variable speed - stopping and starting
                    float bias = MathHelper.clamp(0.5f + cachedAgilityModifier * 0.5f, 0.2f, 0.8f);
                    boolean goFast = pet.getRandom().nextFloat() < bias;
                    float base = goFast ? 1.1f : 0.9f;
                    float focusFactor = MathHelper.clamp(1.0f + cachedVitalityModifier * 0.2f, 0.9f, 1.1f);
                    speedMultiplier = clampToRange(base * focusFactor, 0.9f, 1.1f);
                }
                case RESTLESS -> {
                    // Erratic speed changes
                    float min = 0.8f;
                    float max = 1.4f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float agilityFactor = MathHelper.clamp(1.0f + cachedAgilityModifier * 0.3f, 0.85f, 1.15f);
                    float stabilityFactor = MathHelper.clamp(1.0f - Math.max(0.0f, cachedVitalityModifier) * 0.25f, 0.85f, 1.0f);
                    speedMultiplier = clampToRange(base * agilityFactor * stabilityFactor, min, max);
                }
                case AFRAID -> {
                    // Quick, nervous movements
                    float dashBias = MathHelper.clamp(0.5f + cachedAgilityModifier * 0.6f, 0.2f, 0.85f);
                    boolean dash = pet.getRandom().nextFloat() < dashBias;
                    float base = dash ? 1.2f : 0.7f;
                    float vitalityFactor = dash
                        ? MathHelper.clamp(1.0f - cachedVitalityModifier * 0.35f, 0.85f, 1.0f)
                        : MathHelper.clamp(1.0f + cachedVitalityModifier * 0.35f, 0.85f, 1.15f);
                    speedMultiplier = clampToRange(base * vitalityFactor, 0.7f, 1.2f);
                }
                case CALM, BONDED -> {
                    // Steady, relaxed movement
                    float min = 0.95f;
                    float max = 1.05f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float steadiness = MathHelper.clamp(0.5f + cachedVitalityModifier * 1.2f, 0.0f, 1.0f);
                    speedMultiplier = clampToRange(MathHelper.lerp(steadiness, base, 1.0f), min, max);
                }
                case FOCUSED -> {
                    // Deliberate, measured movement
                    float focusFactor = MathHelper.clamp(1.0f + (cachedAgilityModifier + cachedVitalityModifier) * 0.15f, 0.9f, 1.1f);
                    speedMultiplier = clampToRange(1.0f * focusFactor, 0.95f, 1.05f);
                }
                case PROTECTIVE -> {
                    // Alert, ready-to-respond movement
                    float min = 1.02f;
                    float max = 1.08f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float agilityFactor = MathHelper.clamp(1.0f + cachedAgilityModifier * 0.2f, 0.9f, 1.1f);
                    speedMultiplier = clampToRange(base * agilityFactor, min, max);
                }
                case ANGRY -> {
                    // Aggressive, purposeful movement
                    float min = 1.1f;
                    float max = 1.2f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float agilityFactor = MathHelper.clamp(1.0f + cachedAgilityModifier * 0.3f, 0.85f, 1.15f);
                    speedMultiplier = clampToRange(base * agilityFactor, min, max);
                }
                case PASSIONATE -> {
                    // Intense, driven movement
                    float min = 1.08f;
                    float max = 1.2f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float agilityFactor = MathHelper.clamp(1.0f + cachedAgilityModifier * 0.28f, 0.85f, 1.15f);
                    speedMultiplier = clampToRange(base * agilityFactor, min, max);
                }
                case YUGEN -> {
                    // Contemplative, measured movement
                    float min = 0.9f;
                    float max = 1.0f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float vitalityFactor = MathHelper.clamp(1.0f - cachedVitalityModifier * 0.3f, 0.9f, 1.1f);
                    speedMultiplier = clampToRange(base * vitalityFactor, min, max);
                }
                case SAUDADE -> {
                    // Melancholy, slower movement
                    float min = 0.85f;
                    float max = 0.95f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float vitalityFactor = MathHelper.clamp(1.0f - cachedVitalityModifier * 0.25f, 0.9f, 1.1f);
                    speedMultiplier = clampToRange(base * vitalityFactor, min, max);
                }
                case SISU -> {
                    // Determined, steady movement
                    float min = 1.0f;
                    float max = 1.05f;
                    float base = min + (pet.getRandom().nextFloat() * (max - min));
                    float agilityFactor = MathHelper.clamp(1.0f + cachedAgilityModifier * 0.2f, 0.9f, 1.1f);
                    speedMultiplier = clampToRange(base * agilityFactor, min, max);
                }
            }

            if (inWater && speedMultiplier > 1.0f) {
                speedMultiplier = Math.min(speedMultiplier, 1.02f);
            }
            if (slipperiness > 0.8f) {
                speedMultiplier = MathHelper.lerp(0.5f, speedMultiplier, 0.95f);
            }

            if (Math.abs(speedMultiplier - currentSpeedMultiplier) > SPEED_EPSILON) {
                SpeedModifierHelper.applyMovementSpeedMultiplier(
                    pet,
                    MOVEMENT_VARIATION_MODIFIER_ID,
                    speedMultiplier
                );
                currentSpeedMultiplier = speedMultiplier;
            }
        }

        private void cacheCharacteristicModifiers(PetComponent pc) {
            cachedAgilityModifier = 0.0f;
            cachedVitalityModifier = 0.0f;

            if (pc == null) {
                return;
            }

            var characteristics = pc.getCharacteristics();
            if (characteristics != null) {
                var roleType = pc.getRoleType(false);
                cachedAgilityModifier = characteristics.getAgilityModifier(roleType);
                cachedVitalityModifier = characteristics.getVitalityModifier(roleType);
            }
        }

        private float clampToRange(float value, float min, float max) {
            return MathHelper.clamp(value, min, max);
        }

        private void applyStrideVariations(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null || !pet.getNavigation().isFollowingPath()) return;

            Vec3d velocity = pet.getVelocity();
            double speed = velocity.horizontalLength();

            if (speed < 0.01) return; // Not moving

            BlockState groundState = pet.getWorld().getBlockState(pet.getBlockPos().down());
            boolean onSlipperySurface = groundState.isIn(PetsplusBlockTags.SLIPPERY_SURFACES)
                || groundState.isIn(BlockTags.ICE);
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

            if (onSlipperySurface && mood != PetComponent.Mood.CALM && mood != PetComponent.Mood.BONDED) {
                pet.setVelocity(pet.getVelocity().multiply(0.92));
            }
        }

        @Override
        public void stop() {
            if (Math.abs(currentSpeedMultiplier - 1.0f) > SPEED_EPSILON) {
                SpeedModifierHelper.clearMovementSpeedModifier(pet, MOVEMENT_VARIATION_MODIFIER_ID);
                currentSpeedMultiplier = 1.0f;
            }
            cachedItemInterest = null;
            lastItemInterestTick = Long.MIN_VALUE;
        }

        private void applyContextualNudges(PetComponent pc) {
            PlayerEntity owner = getOwner();
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return;

            if (owner != null && pet.squaredDistanceTo(owner) > 9.0 && (mood == PetComponent.Mood.CALM || mood == PetComponent.Mood.BONDED)) {
                Vec3d toOwner = owner.getPos().subtract(pet.getPos()).normalize().multiply(0.06);
                pet.setVelocity(pet.getVelocity().add(toOwner.x, 0, toOwner.z));
            }

            if (mood == PetComponent.Mood.CURIOUS) {
                Optional<Vec3d> interest = findNearbyItemInterest();
                interest.ifPresent(target -> {
                    Vec3d nudge = target.subtract(pet.getPos()).normalize().multiply(0.05);
                    pet.setVelocity(pet.getVelocity().add(nudge.x, 0, nudge.z));
                });
            }

            if (mood == PetComponent.Mood.RESTLESS && pet.getRandom().nextBoolean()) {
                if (tryTriggerMoodAction(PetComponent.Mood.RESTLESS, RESTLESS_JITTER_COOLDOWN)) {
                    Vec3d wander = new Vec3d(pet.getRandom().nextFloat() - 0.5f, 0, pet.getRandom().nextFloat() - 0.5f)
                        .normalize().multiply(0.04);
                    pet.setVelocity(pet.getVelocity().add(wander));
                }
            }
        }

        private boolean tryTriggerMoodAction(PetComponent.Mood mood, int cooldownTicks) {
            if (mood == null || cooldownTicks <= 0) {
                return false;
            }
            long now = pet.getWorld().getTime();
            long last = moodActionCooldowns.getOrDefault(mood, Long.MIN_VALUE);
            if (now - last < cooldownTicks) {
                return false;
            }
            moodActionCooldowns.put(mood, now);
            return true;
        }

        private PlayerEntity getOwner() {
            if (pet instanceof PetsplusTameable tameable) {
                return tameable.petsplus$getOwner() instanceof PlayerEntity player ? player : null;
            }
            return null;
        }

        private Optional<Vec3d> findNearbyItemInterest() {
            long now = pet.getWorld().getTime();
            if (now - lastItemInterestTick <= ITEM_INTEREST_REFRESH_TICKS) {
                return Optional.ofNullable(cachedItemInterest);
            }

            lastItemInterestTick = now;
            List<ItemEntity> items = pet.getWorld().getEntitiesByClass(ItemEntity.class, pet.getBoundingBox().expand(4.0), ItemEntity::isAlive);
            if (items.isEmpty()) {
                cachedItemInterest = null;
                return Optional.empty();
            }

            ItemEntity closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (ItemEntity item : items) {
                double distance = pet.squaredDistanceTo(item);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = item;
                }
            }

            cachedItemInterest = closest != null ? closest.getPos() : null;
            return Optional.ofNullable(cachedItemInterest);
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
        private Vec3d cachedThreatTarget;
        private long lastThreatScanTick = Long.MIN_VALUE;
        private Vec3d cachedMovingTarget;
        private long lastMovingTargetScanTick = Long.MIN_VALUE;
        private Vec3d cachedExplorationTarget;
        private long lastExplorationScanTick = Long.MIN_VALUE;
        private Vec3d cachedSafetyTarget;
        private long lastSafetyScanTick = Long.MIN_VALUE;
        private Vec3d cachedInspectionTarget;
        private long lastInspectionScanTick = Long.MIN_VALUE;
        private static final int THREAT_SCAN_INTERVAL_TICKS = 20;
        private static final int MOVING_TARGET_SCAN_INTERVAL_TICKS = 15;
        private static final int EXPLORATION_SCAN_INTERVAL_TICKS = 40;
        private static final int SAFETY_SCAN_INTERVAL_TICKS = 40;
        private static final int INSPECTION_SCAN_INTERVAL_TICKS = 30;
        private float cachedAgilityModifier = 0.0f;
        private float cachedVitalityModifier = 0.0f;
        private float cachedFocusBlend = 0.0f;
        private float focusIntervalScale = 1.0f;
        private int lookUpdateInterval = 10;
        private int threatScanInterval = THREAT_SCAN_INTERVAL_TICKS;
        private int movingTargetScanInterval = MOVING_TARGET_SCAN_INTERVAL_TICKS;
        private int explorationScanInterval = EXPLORATION_SCAN_INTERVAL_TICKS;
        private int safetyScanInterval = SAFETY_SCAN_INTERVAL_TICKS;
        private int inspectionScanInterval = INSPECTION_SCAN_INTERVAL_TICKS;

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

            cacheCharacteristicModifiers(pc);
            resetLookCaches();
            currentLookTarget = selectLookTarget(pc);
            int baseDuration = calculateLookDuration(pc);
            lookDuration = applyFocusToDuration(baseDuration);
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
            if (scanTimer % lookUpdateInterval == 0) {
                updateLookBehavior(pc);
            }
        }

        @Override
        public void stop() {
            currentLookTarget = null;
            lookDuration = 0;
            scanTimer = 0;
            resetLookCaches();
        }

        private Vec3d selectLookTarget(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return getRandomLookTarget();

            // Mood -> look pool mapping:
            //   * Exploration (workstations, curiosities): CURIOUS, SISU
            //   * Threat scanning with safety fallback: PROTECTIVE, ANGRY, PASSIONATE
            //   * Safety (warmth, beacons, comforts): CALM, BONDED, AFRAID
            //   * Dynamic scans: RESTLESS, PLAYFUL/HAPPY, FOCUSED, YUGEN/SAUDADE
            return switch (mood) {
                case CURIOUS -> getExplorationTarget();
                case PROTECTIVE -> getThreatScanTarget().orElseGet(this::getOwnerOrSafetyTarget);
                case AFRAID -> getOwnerOrSafetyTarget();
                case FOCUSED -> getDetailedInspectionTarget().orElseGet(this::getRandomLookTarget);
                case PLAYFUL, HAPPY -> getPlayfulLookTarget();
                case RESTLESS -> getRandomMovingTarget().orElseGet(this::getRandomLookTarget);
                case ANGRY, PASSIONATE -> getThreatScanTarget().orElseGet(this::getOwnerOrSafetyTarget); // Alert with safety fallback
                case CALM, BONDED -> getOwnerOrSafetyTarget(); // Look toward owner for comfort
                case YUGEN, SAUDADE -> getDetailedInspectionTarget().orElseGet(this::getRandomLookTarget); // Contemplative inspection
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

        private int applyFocusToDuration(int baseDuration) {
            float focusScale = MathHelper.clamp(1.0f + cachedFocusBlend, 0.85f, 1.15f);
            int min = Math.max(10, Math.round(baseDuration * 0.85f));
            int max = Math.round(baseDuration * 1.15f);
            return MathHelper.clamp(Math.round(baseDuration * focusScale), min, Math.max(min, max));
        }

        private void updateLookBehavior(PetComponent pc) {
            PetComponent.Mood mood = pc.getCurrentMood();
            if (mood == null) return;

            switch (mood) {
                case CURIOUS -> {
                    // Scan around systematically
                    if (scanTimer % scaledInterval(20) == 0) {
                        currentLookTarget = getExplorationTarget();
                    }
                }
                case RESTLESS -> {
                    // Constantly changing focus
                    if (scanTimer % scaledInterval(8) == 0) {
                        currentLookTarget = getRandomMovingTarget().orElseGet(this::getRandomLookTarget);
                    }
                }
                case PROTECTIVE -> {
                    // Threat assessment scans
                    if (scanTimer % scaledInterval(15) == 0) {
                        currentLookTarget = getThreatScanTarget().orElseGet(this::getRandomLookTarget);
                    }
                }
                case AFRAID -> {
                    // Quick safety checks
                    if (scanTimer % scaledInterval(12) == 0) {
                        currentLookTarget = getOwnerOrSafetyTarget();
                    }
                }
                case PLAYFUL, HAPPY -> {
                    // Playful, bouncy looking
                    if (scanTimer % scaledInterval(16) == 0) {
                        currentLookTarget = getPlayfulLookTarget();
                    }
                }
                case FOCUSED -> {
                    // Concentrated inspection
                    if (scanTimer % scaledInterval(25) == 0) {
                        currentLookTarget = getDetailedInspectionTarget().orElseGet(this::getRandomLookTarget);
                    }
                }
                case ANGRY, PASSIONATE -> {
                    // Aggressive scanning
                    if (scanTimer % scaledInterval(12) == 0) {
                        currentLookTarget = getThreatScanTarget().orElseGet(this::getRandomLookTarget);
                    }
                }
                case CALM, BONDED -> {
                    // Gentle, comfortable looking
                    if (scanTimer % scaledInterval(30) == 0) {
                        currentLookTarget = getOwnerOrSafetyTarget();
                    }
                }
                case YUGEN, SAUDADE -> {
                    // Contemplative, distant looking
                    if (scanTimer % scaledInterval(35) == 0) {
                        currentLookTarget = getDetailedInspectionTarget().orElseGet(this::getRandomLookTarget);
                    }
                }
                case SISU -> {
                    // Determined exploration
                    if (scanTimer % scaledInterval(22) == 0) {
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
            Vec3d target = refreshExplorationTarget();
            return target != null ? target : getRandomLookTarget();
        }

        private Optional<Vec3d> getThreatScanTarget() {
            long now = pet.getWorld().getTime();
            if (now - lastThreatScanTick <= threatScanInterval) {
                return Optional.ofNullable(cachedThreatTarget);
            }

            lastThreatScanTick = now;
            List<HostileEntity> hostiles = pet.getWorld().getEntitiesByClass(
                HostileEntity.class,
                pet.getBoundingBox().expand(12.0),
                LivingEntity::isAlive
            );

            HostileEntity closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (HostileEntity hostile : hostiles) {
                double distance = pet.squaredDistanceTo(hostile);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = hostile;
                }
            }

            cachedThreatTarget = closest != null
                ? closest.getPos().add(0, closest.getStandingEyeHeight(), 0)
                : null;
            return Optional.ofNullable(cachedThreatTarget);
        }

        private Vec3d getOwnerOrSafetyTarget() {
            // Look toward owner if nearby, otherwise look for safe spots
            PlayerEntity owner = getOwner();
            if (owner != null && pet.squaredDistanceTo(owner) < 64) {
                return owner.getPos().add(0, owner.getStandingEyeHeight() * 0.7, 0);
            }
            Vec3d safety = refreshSafetyTarget();
            return safety != null ? safety : getRandomLookTarget();
        }

        private Optional<Vec3d> getDetailedInspectionTarget() {
            Vec3d inspection = refreshInspectionTarget();
            if (inspection != null) {
                return Optional.of(inspection);
            }
            return Optional.of(pet.getPos().add(0, -0.5, 0));
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

        private Optional<Vec3d> getRandomMovingTarget() {
            long now = pet.getWorld().getTime();
            if (now - lastMovingTargetScanTick <= movingTargetScanInterval) {
                return Optional.ofNullable(cachedMovingTarget);
            }

            lastMovingTargetScanTick = now;
            List<LivingEntity> entities = pet.getWorld().getEntitiesByClass(
                LivingEntity.class,
                pet.getBoundingBox().expand(10.0),
                entity -> entity != pet && entity.isAlive()
            );
            if (entities.isEmpty()) {
                cachedMovingTarget = null;
                return Optional.empty();
            }

            LivingEntity choice = entities.get(pet.getRandom().nextInt(entities.size()));
            cachedMovingTarget = choice.getPos().add(0, choice.getStandingEyeHeight() * 0.6, 0);
            return Optional.ofNullable(cachedMovingTarget);
        }

        private Optional<Vec3d> findInterestingBlock(Predicate<BlockState> predicate) {
            return findInterestingBlock(predicate, 6, 2.5);
        }

        private Vec3d refreshExplorationTarget() {
            long now = pet.getWorld().getTime();
            if (cachedExplorationTarget == null || now - lastExplorationScanTick > explorationScanInterval) {
                lastExplorationScanTick = now;
                cachedExplorationTarget = findInterestingBlock(state ->
                    state.isIn(PetsplusBlockTags.EXPLORATION_WORKSTATIONS)
                        || state.isIn(PetsplusBlockTags.EXPLORATION_CURIOSITIES)
                        || state.isIn(PetsplusBlockTags.SNIFF_CURIOSITIES)
                        || state.isIn(PetsplusBlockTags.SNIFF_STORAGE)
                        || state.isIn(BlockTags.CROPS)
                        || state.isIn(BlockTags.FLOWERS)
                ).orElse(null);
            }
            return cachedExplorationTarget;
        }

        private Vec3d refreshSafetyTarget() {
            long now = pet.getWorld().getTime();
            if (cachedSafetyTarget == null || now - lastSafetyScanTick > safetyScanInterval) {
                lastSafetyScanTick = now;
                cachedSafetyTarget = findInterestingBlock(state ->
                    state.isIn(PetsplusBlockTags.SAFETY_WARMTH)
                        || state.isIn(PetsplusBlockTags.SAFETY_BEACONS)
                        || state.isIn(PetsplusBlockTags.SNIFF_COMFORTS)
                        || state.isIn(BlockTags.BEDS)
                        || state.isIn(BlockTags.WOOL)
                ).orElse(null);
            }
            return cachedSafetyTarget;
        }

        private Vec3d refreshInspectionTarget() {
            long now = pet.getWorld().getTime();
            if (cachedInspectionTarget == null || now - lastInspectionScanTick > inspectionScanInterval) {
                lastInspectionScanTick = now;
                cachedInspectionTarget = findInterestingBlock(state -> !state.isAir(), 3, 1.5)
                    .orElse(null);
            }
            return cachedInspectionTarget;
        }

        private Optional<Vec3d> findInterestingBlock(Predicate<BlockState> predicate, int radius, double verticalRange) {
            BlockPos origin = pet.getBlockPos();
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            int maxY = MathHelper.floor(verticalRange);
            double closestDistance = Double.MAX_VALUE;
            Vec3d closest = null;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -maxY; dy <= maxY; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        BlockState state = pet.getWorld().getBlockState(mutable);
                        if (predicate.test(state)) {
                            double distance = mutable.getSquaredDistance(origin);
                            if (distance < closestDistance) {
                                closestDistance = distance;
                                closest = Vec3d.ofCenter(mutable);
                            }
                        }
                    }
                }
            }

            return Optional.ofNullable(closest);
        }

        private PlayerEntity getOwner() {
            if (pet instanceof PetsplusTameable tameable) {
                return tameable.petsplus$getOwner() instanceof PlayerEntity player ? player : null;
            }
            return null;
        }

        private void resetLookCaches() {
            cachedThreatTarget = null;
            cachedMovingTarget = null;
            cachedExplorationTarget = null;
            cachedSafetyTarget = null;
            cachedInspectionTarget = null;
            lastThreatScanTick = Long.MIN_VALUE;
            lastMovingTargetScanTick = Long.MIN_VALUE;
            lastExplorationScanTick = Long.MIN_VALUE;
            lastSafetyScanTick = Long.MIN_VALUE;
            lastInspectionScanTick = Long.MIN_VALUE;
        }

        private void cacheCharacteristicModifiers(PetComponent pc) {
            cachedAgilityModifier = 0.0f;
            cachedVitalityModifier = 0.0f;

            var characteristics = pc.getCharacteristics();
            if (characteristics != null) {
                var roleType = pc.getRoleType(false);
                cachedAgilityModifier = characteristics.getAgilityModifier(roleType);
                cachedVitalityModifier = characteristics.getVitalityModifier(roleType);
            }

            cachedFocusBlend = MathHelper.clamp((cachedAgilityModifier + cachedVitalityModifier) * 0.5f, -0.25f, 0.25f);
            focusIntervalScale = MathHelper.clamp(1.0f - cachedFocusBlend * 0.6f, 0.75f, 1.25f);
            lookUpdateInterval = MathHelper.clamp(Math.round(10 * focusIntervalScale), 6, 14);
            threatScanInterval = scaledInterval(THREAT_SCAN_INTERVAL_TICKS);
            movingTargetScanInterval = scaledInterval(MOVING_TARGET_SCAN_INTERVAL_TICKS);
            explorationScanInterval = scaledInterval(EXPLORATION_SCAN_INTERVAL_TICKS);
            safetyScanInterval = scaledInterval(SAFETY_SCAN_INTERVAL_TICKS);
            inspectionScanInterval = scaledInterval(INSPECTION_SCAN_INTERVAL_TICKS);
        }

        private int scaledInterval(int baseTicks) {
            int scaled = Math.round(baseTicks * focusIntervalScale);
            int min = Math.max(4, baseTicks - 6);
            int max = baseTicks + 6;
            return MathHelper.clamp(scaled, min, max);
        }
    }
}
