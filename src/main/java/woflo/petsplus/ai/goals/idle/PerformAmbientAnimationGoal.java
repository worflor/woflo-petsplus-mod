package woflo.petsplus.ai.goals.idle;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ai.context.perception.EnvironmentPerceptionBridge;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.state.processing.OwnerFocusSnapshot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * Performs ambient animations based on pet's nature and current mood.
 * Selects animations from a data-driven catalogue with weighted selection.
 */
public class PerformAmbientAnimationGoal extends AdaptiveGoal {
    private static final String ANIMATIONS_DATA_PATH = "petsplus:ambient_animations.json";
    private static final Map<Identifier, List<AmbientAnimation>> ANIMATION_CACHE = new HashMap<>();
    private static final long CONTEXT_STIMULUS_MAX_AGE = 200L;
    
    private AmbientAnimation currentAnimation;
    private int animationTicks;
    private Random random;

    public PerformAmbientAnimationGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PERFORM_AMBIENT_ANIMATION), EnumSet.of(Control.LOOK));
        this.random = new Random();
    }

    @Override
    protected boolean canStartGoal() {
        // Check if animations are loaded
        List<AmbientAnimation> animations = getAnimations();
        if (animations.isEmpty()) {
            return false;
        }
        
        // Select an appropriate animation based on pet's nature and mood
        currentAnimation = selectWeightedAnimation(animations);
        return currentAnimation != null;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (currentAnimation == null) {
            return false;
        }
        
        // Continue until animation is complete
        return animationTicks < currentAnimation.durationTicks;
    }

    @Override
    protected void onStartGoal() {
        animationTicks = 0;
        Petsplus.LOGGER.debug("[PerformAmbientAnimation] Pet {} performing animation: {}", 
            mob.getDisplayName().getString(), currentAnimation.name);
    }

    @Override
    protected void onStopGoal() {
        currentAnimation = null;
        animationTicks = 0;
    }

    @Override
    protected void onTickGoal() {
        if (currentAnimation == null) {
            return;
        }
        
        animationTicks++;
        float progress = animationTicks / (float) currentAnimation.durationTicks;
        
        // Perform animation-specific behavior
        switch (currentAnimation.animationType) {
            case STRETCH -> performStretchAnimation(progress);
            case YAWN -> performYawnAnimation(progress);
            case SHAKE -> performShakeAnimation(progress);
            case TAIL_WAG -> performTailWagAnimation(progress);
            case EAR_PERK -> performEarPerkAnimation(progress);
            case CROUCH -> performCrouchAnimation(progress);
            case GROOM -> performGroomAnimation(progress);
            case CIRCLE -> performCircleAnimation(progress);
            case PAW -> performPawAnimation(progress);
            case SNIFF -> performSniffAnimation(progress);
            case HEAD_TILT -> performHeadTiltAnimation(progress);
            case ROLL -> performRollAnimation(progress);
        }
    }
    
    private List<AmbientAnimation> getAnimations() {
        Identifier id = Identifier.of(ANIMATIONS_DATA_PATH);
        
        // Return cached animations if available
        if (ANIMATION_CACHE.containsKey(id)) {
            return ANIMATION_CACHE.get(id);
        }
        
        // Load animations from data file
        List<AmbientAnimation> animations = new ArrayList<>();
        
        try {
            ResourceManager resourceManager = mob.getEntityWorld().getServer().getResourceManager();
            Optional<Resource> resource = resourceManager.getResource(id);
            
            if (resource.isPresent()) {
                try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream())) {
                    JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                    JsonArray animationsArray = json.getAsJsonArray("animations");
                    
                    for (JsonElement element : animationsArray) {
                        JsonObject animJson = element.getAsJsonObject();
                        AmbientAnimation animation = parseAnimation(animJson);
                        if (animation != null) {
                            animations.add(animation);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to load ambient animations data", e);
        }
        
        // Cache the loaded animations
        ANIMATION_CACHE.put(id, animations);
        return animations;
    }
    
    private AmbientAnimation parseAnimation(JsonObject json) {
        try {
            String id = json.get("id").getAsString();
            String name = json.get("name").getAsString();
            String description = json.get("description").getAsString();
            int durationTicks = json.get("duration_ticks").getAsInt();
            
            List<String> natures = new ArrayList<>();
            JsonArray naturesArray = json.getAsJsonArray("natures");
            for (JsonElement element : naturesArray) {
                natures.add(element.getAsString());
            }
            
            List<String> moods = new ArrayList<>();
            JsonArray moodsArray = json.getAsJsonArray("moods");
            for (JsonElement element : moodsArray) {
                moods.add(element.getAsString());
            }
            
            float weight = json.get("weight").getAsFloat();
            AnimationType animationType = AnimationType.valueOf(json.get("animation_type").getAsString());

            List<String> weatherTags = parseStringList(json, "weather");
            List<Identifier> requiredStimuli = parseIdentifierList(json, "required_stimuli");
            JsonObject timeWindowJson = json.has("time_window") && json.get("time_window").isJsonObject()
                ? json.getAsJsonObject("time_window")
                : null;
            TimeWindow timeWindow = parseTimeWindow(timeWindowJson);
            Set<OwnerFocusCondition> ownerFocus = parseOwnerFocusConditions(json);
            CourtesyRequirement courtesyRequirement = parseCourtesyRequirement(json);

            return new AmbientAnimation(id, name, description, durationTicks, natures, moods, weight, animationType,
                weatherTags, requiredStimuli, timeWindow, ownerFocus, courtesyRequirement);
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to parse ambient animation", e);
            return null;
        }
    }

    private List<String> parseStringList(JsonObject json, String key) {
        if (json == null || key == null || !json.has(key)) {
            return List.of();
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element != null && !element.isJsonNull()) {
                values.add(element.getAsString());
            }
        }
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private List<Identifier> parseIdentifierList(JsonObject json, String key) {
        if (json == null || key == null || !json.has(key)) {
            return List.of();
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<Identifier> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            Identifier id = Identifier.tryParse(element.getAsString());
            if (id != null) {
                values.add(id);
            }
        }
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private Set<OwnerFocusCondition> parseOwnerFocusConditions(JsonObject json) {
        List<String> focusTags = parseStringList(json, "owner_focus");
        if (focusTags.isEmpty()) {
            return Set.of();
        }
        EnumSet<OwnerFocusCondition> conditions = EnumSet.noneOf(OwnerFocusCondition.class);
        for (String tag : focusTags) {
            OwnerFocusCondition.fromString(tag).ifPresent(conditions::add);
        }
        return conditions.isEmpty() ? Set.of() : EnumSet.copyOf(conditions);
    }

    private CourtesyRequirement parseCourtesyRequirement(JsonObject json) {
        if (json == null) {
            return CourtesyRequirement.none();
        }
        if (json.has("courtesy") && json.get("courtesy").isJsonObject()) {
            JsonObject courtesyJson = json.getAsJsonObject("courtesy");
            boolean required = courtesyJson.has("required") && courtesyJson.get("required").getAsBoolean();
            float minBonus = courtesyJson.has("min_bonus") ? courtesyJson.get("min_bonus").getAsFloat() : 0.0f;
            float boost = courtesyJson.has("weight_boost") ? courtesyJson.get("weight_boost").getAsFloat() : 0.18f;
            return new CourtesyRequirement(required, minBonus, boost);
        }
        boolean required = json.has("courtesy_required") && json.get("courtesy_required").getAsBoolean();
        float minBonus = json.has("courtesy_min_bonus") ? json.get("courtesy_min_bonus").getAsFloat() : 0.0f;
        float boost = required ? 0.24f : 0.18f;
        return new CourtesyRequirement(required, minBonus, boost);
    }

    private TimeWindow parseTimeWindow(@Nullable JsonObject json) {
        if (json == null) {
            return null;
        }
        int start = json.has("start") ? json.get("start").getAsInt() : 0;
        int end = json.has("end") ? json.get("end").getAsInt() : 24000;
        return new TimeWindow(MathHelper.clamp(start, 0, 24000), MathHelper.clamp(end, 0, 24000));
    }
    
    private AmbientAnimation selectWeightedAnimation(List<AmbientAnimation> animations) {
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) {
            return null;
        }
        
        // Get pet's nature and current mood
        String petNature = pc.getNatureId() != null ? pc.getNatureId().getPath() : null;
        PetComponent.Mood currentMood = pc.getDominantMood();
        String moodString = currentMood != null ? currentMood.name().toLowerCase() : null;
        
        // Filter animations by nature and mood compatibility
        List<AmbientAnimation> compatibleAnimations = new ArrayList<>();
        
        EnvironmentPerceptionBridge.EnvironmentSnapshot environmentSnapshot = pc.getCachedEnvironmentSnapshot();
        EnvironmentPerceptionBridge.WorldSnapshot worldSnapshot = pc.getCachedWorldSnapshot();
        long worldTime = mob.getEntityWorld().getTime();
        StimulusSnapshot stimuli = pc.snapshotStimuli(worldTime);
        PetComponent.OwnerCourtesyState courtesyState = pc.getOwnerCourtesyState(worldTime);
        OwnerFocusSnapshot ownerFocus = pc.getOwnerFocusSnapshot();

        for (AmbientAnimation animation : animations) {
            float compatibilityScore = calculateCompatibility(animation, petNature, moodString);
            if (compatibilityScore <= 0) {
                continue;
            }
            float contextWeight = computeContextWeight(animation, environmentSnapshot, worldSnapshot,
                stimuli, mob, ownerFocus, courtesyState, worldTime);
            if (contextWeight <= 0.0f) {
                continue;
            }
            compatibleAnimations.add(animation.withWeight(animation.weight * compatibilityScore * contextWeight));
        }
        
        if (compatibleAnimations.isEmpty()) {
            return null;
        }
        
        // Weighted random selection
        float totalWeight = 0;
        for (AmbientAnimation animation : compatibleAnimations) {
            totalWeight += animation.weight;
        }
        
        float randomValue = random.nextFloat() * totalWeight;
        float currentWeight = 0;
        
        for (AmbientAnimation animation : compatibleAnimations) {
            currentWeight += animation.weight;
            if (randomValue <= currentWeight) {
                return animation;
            }
        }
        
        return compatibleAnimations.get(compatibleAnimations.size() - 1);
    }
    
    private float calculateCompatibility(AmbientAnimation animation, String petNature, String moodString) {
        float compatibility = 0.5f; // Base compatibility
        
        // Check nature compatibility
        if (petNature != null && !animation.natures.isEmpty()) {
            boolean natureMatches = animation.natures.stream()
                .anyMatch(nature -> petNature.contains(nature) || nature.contains(petNature));
            
            if (natureMatches) {
                compatibility += 0.3f;
            }
        }
        
        // Check mood compatibility
        if (moodString != null && !animation.moods.isEmpty()) {
            boolean moodMatches = animation.moods.stream()
                .anyMatch(mood -> moodString.contains(mood) || mood.contains(moodString));
            
            if (moodMatches) {
                compatibility += 0.3f;
            }
        }
        
        return Math.min(compatibility, 1.0f);
    }

    static float computeContextWeight(AmbientAnimation animation,
                                      @Nullable EnvironmentPerceptionBridge.EnvironmentSnapshot environmentSnapshot,
                                      @Nullable EnvironmentPerceptionBridge.WorldSnapshot worldSnapshot,
                                      @Nullable StimulusSnapshot stimuli,
                                      MobEntity mob,
                                      @Nullable OwnerFocusSnapshot ownerFocus,
                                      PetComponent.OwnerCourtesyState courtesyState,
                                      long now) {
        if (animation == null) {
            return 0.0f;
        }
        PetComponent.OwnerCourtesyState courtesy = courtesyState == null
            ? PetComponent.OwnerCourtesyState.inactive()
            : courtesyState;
        OwnerFocusSnapshot focusSnapshot = ownerFocus != null ? ownerFocus : OwnerFocusSnapshot.idle();
        boolean courtesyActive = courtesy.isActive(now);
        OwnerFocusSnapshot courtesyFocus = courtesyActive ? courtesy.focusSnapshot() : OwnerFocusSnapshot.idle();

        boolean requiresWeather = !animation.weather.isEmpty();
        boolean requiresStimuli = !animation.requiredStimuli.isEmpty();
        boolean requiresTime = animation.timeWindow != null;
        float weight = 1.0f;

        if (requiresWeather) {
            boolean raining = environmentSnapshot != null ? environmentSnapshot.raining() : mob.getEntityWorld().isRaining();
            boolean thundering = environmentSnapshot != null ? environmentSnapshot.thundering() : mob.getEntityWorld().isThundering();
            boolean daytime = environmentSnapshot != null ? environmentSnapshot.daytime() : mob.getEntityWorld().isDay();
            boolean match = false;
            for (String tag : animation.weather) {
                if (tag == null) {
                    continue;
                }
                String trimmed = tag.trim().toLowerCase(Locale.ROOT);
                switch (trimmed) {
                    case "rain" -> match |= raining;
                    case "thunder" -> match |= thundering;
                    case "clear" -> match |= !raining && !thundering;
                    case "day" -> match |= daytime;
                    case "night" -> match |= !daytime;
                    default -> {
                        // Ignore unknown tags
                    }
                }
                if (match) {
                    break;
                }
            }
            if (!match) {
                return 0.0f;
            }
            weight *= 1.1f;
        }

        if (requiresTime) {
            long timeOfDay = worldSnapshot != null ? worldSnapshot.timeOfDay() : mob.getEntityWorld().getTimeOfDay();
            if (!animation.timeWindow.contains(timeOfDay)) {
                return 0.0f;
            }
            weight *= 1.1f;
        }

        if (requiresStimuli) {
            boolean matched = false;
            if (stimuli != null && !stimuli.isEmpty()) {
                for (StimulusSnapshot.Event event : stimuli.events()) {
                    if (event == null) {
                        continue;
                    }
                    if (animation.requiredStimuli.contains(event.type())
                        && event.ageTicks() <= CONTEXT_STIMULUS_MAX_AGE) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                return 0.0f;
            }
            weight *= 1.1f;
        }

        if (!animation.ownerFocus.isEmpty()) {
            boolean matched = animation.ownerFocus.stream()
                .anyMatch(condition -> condition.matches(focusSnapshot)
                    || (courtesyActive && condition.matches(courtesyFocus)));
            if (!matched) {
                return 0.0f;
            }
            weight *= 1.05f;
        }

        if (!animation.courtesyRequirement.isSatisfied(courtesy, now)) {
            return 0.0f;
        }
        weight *= animation.courtesyRequirement.weightMultiplier(courtesy, now);

        return MathHelper.clamp(weight, 0.0f, 2.5f);
    }
    
    // Animation implementation methods
    private void performStretchAnimation(float progress) {
        // Simple head and body stretch
        float stretchAmount = MathHelper.sin(progress * MathHelper.PI) * 20f;
        mob.setPitch(-stretchAmount);
        
        // Extend body slightly
        if (progress < 0.5f) {
            mob.setBodyYaw(mob.getBodyYaw() + 2f);
        } else {
            mob.setBodyYaw(mob.getBodyYaw() - 2f);
        }
    }
    
    private void performYawnAnimation(float progress) {
        // Open mouth animation (simplified as head tilt)
        float yawAmount = MathHelper.sin(progress * MathHelper.PI) * 15f;
        mob.setPitch(yawAmount);
        
        // Slight head shake
        if (progress > 0.3f && progress < 0.7f) {
            mob.headYaw = mob.getBodyYaw() + MathHelper.sin(animationTicks * 0.5f) * 5f;
        }
    }
    
    private void performShakeAnimation(float progress) {
        // Rapid body shaking
        if (progress < 0.8f) {
            float shakeAmount = MathHelper.sin(animationTicks * 0.8f) * 10f;
            float currentBodyYaw = mob.getBodyYaw();
            mob.bodyYaw = currentBodyYaw + shakeAmount;
            mob.headYaw = currentBodyYaw + shakeAmount;
        }
    }
    
    private void performTailWagAnimation(float progress) {
        // Tail wagging (simplified as body wiggle)
        float wagAmount = MathHelper.sin(animationTicks * 0.4f) * 8f;
        mob.bodyYaw = mob.getBodyYaw() + wagAmount;
    }
    
    private void performEarPerkAnimation(float progress) {
        // Ear perking (simplified as head movement)
        float perkAmount = MathHelper.sin(progress * MathHelper.PI) * 10f;
        mob.setPitch(-perkAmount);
        
        // Look around briefly
        if (progress > 0.2f && progress < 0.8f) {
            mob.headYaw = mob.getBodyYaw() + MathHelper.sin(animationTicks * 0.3f) * 15f;
        }
    }
    
    private void performCrouchAnimation(float progress) {
        // Crouching pose
        float crouchAmount = MathHelper.sin(progress * MathHelper.PI) * 15f;
        mob.setPitch(crouchAmount);
        
        // Lower body slightly
        if (progress < 0.5f) {
            mob.setBodyYaw(mob.getBodyYaw() - 1f);
        } else {
            mob.setBodyYaw(mob.getBodyYaw() + 1f);
        }
    }
    
    private void performGroomAnimation(float progress) {
        // Grooming motion (head turning to body)
        float groomAmount = MathHelper.sin(progress * MathHelper.PI) * 30f;
        mob.headYaw = mob.getBodyYaw() + groomAmount;
        mob.setPitch(Math.abs(groomAmount) * 0.5f);
    }
    
    private void performCircleAnimation(float progress) {
        // Circling motion
        float circleAmount = progress * 360f;
        mob.setBodyYaw(mob.getBodyYaw() + circleAmount / 10f); // Slow rotation
    }
    
    private void performPawAnimation(float progress) {
        // Pawing motion (head movement toward ground)
        float pawAmount = MathHelper.sin(progress * MathHelper.PI) * 25f;
        mob.setPitch(pawAmount);
    }
    
    private void performSniffAnimation(float progress) {
        // Sniffing motion (quick head movements)
        if (progress < 0.8f) {
            float sniffAmount = MathHelper.sin(animationTicks * 0.6f) * 8f;
            mob.setPitch(sniffAmount);
            mob.headYaw = mob.getBodyYaw() + MathHelper.sin(animationTicks * 0.4f) * 5f;
        }
    }
    
    private void performHeadTiltAnimation(float progress) {
        // Head tilting
        float tiltAmount = MathHelper.sin(progress * MathHelper.PI) * 20f;
        mob.setPitch(tiltAmount);
        mob.headYaw = mob.getBodyYaw() + tiltAmount * 0.5f;
    }
    
    private void performRollAnimation(float progress) {
        // Rolling motion (simplified as body rotation)
        float rollAmount = progress * 360f;
        mob.setBodyYaw(mob.getBodyYaw() + rollAmount / 15f); // Slow rotation
        mob.setPitch(MathHelper.sin(progress * MathHelper.PI * 2) * 15f);
    }

    @Override
    protected float calculateEngagement() {
        // Base engagement for ambient animations
        return 0.4f;
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        // Minimal emotional feedback for ambient animations
        if (currentAnimation == null) {
            return EmotionFeedback.NONE;
        }
        
        return switch (currentAnimation.animationType) {
            case STRETCH, YAWN, GROOM -> new EmotionFeedback.Builder()
                .add(PetComponent.Emotion.CONTENT, 0.05f)
                .build();
            case SHAKE, TAIL_WAG, ROLL -> new EmotionFeedback.Builder()
                .add(PetComponent.Emotion.PLAYFULNESS, 0.08f)
                .build();
            case EAR_PERK, SNIFF, HEAD_TILT -> new EmotionFeedback.Builder()
                .add(PetComponent.Emotion.CURIOUS, 0.06f)
                .build();
            default -> EmotionFeedback.NONE;
        };
    }
    
    /**
     * Represents an ambient animation that can be performed by a pet.
     */
    static class AmbientAnimation {
        final String id;
        final String name;
        final String description;
        final int durationTicks;
        final List<String> natures;
        final List<String> moods;
        float weight;
        final AnimationType animationType;
        final Set<String> weather;
        final Set<Identifier> requiredStimuli;
        final TimeWindow timeWindow;
        final Set<OwnerFocusCondition> ownerFocus;
        final CourtesyRequirement courtesyRequirement;

        AmbientAnimation(String id, String name, String description, int durationTicks,
                         List<String> natures, List<String> moods, float weight, AnimationType animationType,
                         List<String> weather, List<Identifier> requiredStimuli, TimeWindow timeWindow,
                         Collection<OwnerFocusCondition> ownerFocus, CourtesyRequirement courtesyRequirement) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.durationTicks = durationTicks;
            this.natures = natures;
            this.moods = moods;
            this.weight = weight;
            this.animationType = animationType;
            this.weather = weather.isEmpty() ? Set.of() : new HashSet<>(weather);
            this.requiredStimuli = requiredStimuli.isEmpty() ? Set.of() : new HashSet<>(requiredStimuli);
            this.timeWindow = timeWindow;
            if (ownerFocus == null || ownerFocus.isEmpty()) {
                this.ownerFocus = Set.of();
            } else {
                this.ownerFocus = EnumSet.copyOf(ownerFocus);
            }
            this.courtesyRequirement = courtesyRequirement == null ? CourtesyRequirement.none() : courtesyRequirement;
        }

        AmbientAnimation withWeight(float newWeight) {
            Collection<OwnerFocusCondition> focusCopy = ownerFocus.isEmpty() ? Set.of() : EnumSet.copyOf(ownerFocus);
            return new AmbientAnimation(id, name, description, durationTicks, natures, moods, newWeight, animationType,
                new ArrayList<>(weather), new ArrayList<>(requiredStimuli), timeWindow, focusCopy, courtesyRequirement);
        }
    }

    private enum OwnerFocusCondition {
        CROUCHING {
            @Override
            boolean matches(OwnerFocusSnapshot snapshot) {
                return snapshot != null && snapshot.crouching();
            }
        },
        USING_ITEM {
            @Override
            boolean matches(OwnerFocusSnapshot snapshot) {
                return snapshot != null && snapshot.usingItem();
            }
        },
        SCREEN_OPEN {
            @Override
            boolean matches(OwnerFocusSnapshot snapshot) {
                return snapshot != null && snapshot.screenOpen();
            }
        },
        HANDS_BUSY {
            @Override
            boolean matches(OwnerFocusSnapshot snapshot) {
                return snapshot != null && snapshot.handsBusy();
            }
        },
        SLEEPING {
            @Override
            boolean matches(OwnerFocusSnapshot snapshot) {
                return snapshot != null && snapshot.isSleeping();
            }
        };

        abstract boolean matches(OwnerFocusSnapshot snapshot);

        static Optional<OwnerFocusCondition> fromString(@Nullable String value) {
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "CROUCH", "CROUCHING" -> Optional.of(CROUCHING);
                case "USING_ITEM", "USE_ITEM", "ITEM" -> Optional.of(USING_ITEM);
                case "SCREEN", "SCREEN_OPEN", "GUI" -> Optional.of(SCREEN_OPEN);
                case "HANDS_BUSY", "BUSY_HANDS", "HANDS" -> Optional.of(HANDS_BUSY);
                case "SLEEP", "SLEEPING" -> Optional.of(SLEEPING);
                default -> Optional.empty();
            };
        }
    }

    private static final class CourtesyRequirement {
        private static final CourtesyRequirement NONE = new CourtesyRequirement(false, 0.0f, 0.0f);

        private final boolean requireActive;
        private final float minDistanceBonus;
        private final float weightBoost;

        CourtesyRequirement(boolean requireActive, float minDistanceBonus, float weightBoost) {
            this.requireActive = requireActive;
            this.minDistanceBonus = Math.max(0.0f, minDistanceBonus);
            this.weightBoost = Math.max(0.0f, weightBoost);
        }

        static CourtesyRequirement none() {
            return NONE;
        }

        boolean isSatisfied(PetComponent.OwnerCourtesyState state, long now) {
            if (!requireActive && minDistanceBonus <= 0.0f) {
                return true;
            }
            if (state == null || !state.isActive(now)) {
                return !requireActive && minDistanceBonus <= 0.0f;
            }
            return state.distanceBonus() >= minDistanceBonus;
        }

        float weightMultiplier(PetComponent.OwnerCourtesyState state, long now) {
            if (state == null || !state.isActive(now) || weightBoost <= 0.0f) {
                return 1.0f;
            }
            double normalized = MathHelper.clamp(state.distanceBonus() / 3.0d, 0.0d, 1.0d);
            return (float) (1.0d + normalized * weightBoost);
        }
    }

    private static final class TimeWindow {
        private final int start;
        private final int end;

        TimeWindow(int start, int end) {
            this.start = MathHelper.clamp(start, 0, 24000);
            this.end = MathHelper.clamp(end, 0, 24000);
        }

        boolean contains(long timeOfDay) {
            int wrapped = (int) (timeOfDay % 24000L);
            if (start == end) {
                return true;
            }
            if (start < end) {
                return wrapped >= start && wrapped <= end;
            }
            return wrapped >= start || wrapped <= end;
        }
    }
    
    /**
     * Types of ambient animations.
     */
    enum AnimationType {
        STRETCH,
        YAWN,
        SHAKE,
        TAIL_WAG,
        EAR_PERK,
        CROUCH,
        GROOM,
        CIRCLE,
        PAW,
        SNIFF,
        HEAD_TILT,
        ROLL
    }
}
