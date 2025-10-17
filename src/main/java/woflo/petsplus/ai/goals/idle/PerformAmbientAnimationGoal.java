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
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Performs ambient animations based on pet's nature and current mood.
 * Selects animations from a data-driven catalogue with weighted selection.
 */
public class PerformAmbientAnimationGoal extends AdaptiveGoal {
    private static final String ANIMATIONS_DATA_PATH = "petsplus:ambient_animations.json";
    private static final Map<Identifier, List<AmbientAnimation>> ANIMATION_CACHE = new HashMap<>();
    
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
            ResourceManager resourceManager = mob.getWorld().getServer().getResourceManager();
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
            
            return new AmbientAnimation(id, name, description, durationTicks, natures, moods, weight, animationType);
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to parse ambient animation", e);
            return null;
        }
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
        
        for (AmbientAnimation animation : animations) {
            float compatibilityScore = calculateCompatibility(animation, petNature, moodString);
            if (compatibilityScore > 0) {
                // Adjust weight by compatibility
                compatibleAnimations.add(animation.withWeight(animation.weight * compatibilityScore));
            }
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
    
    // Animation implementation methods
    private void performStretchAnimation(float progress) {
        // Simple head and body stretch
        float stretchAmount = MathHelper.sin(progress * MathHelper.PI) * 20f;
        mob.setPitch(-stretchAmount);
        
        // Extend body slightly
        if (progress < 0.5f) {
            mob.getBodyYaw += 2f;
        } else {
            mob.getBodyYaw -= 2f;
        }
    }
    
    private void performYawnAnimation(float progress) {
        // Open mouth animation (simplified as head tilt)
        float yawAmount = MathHelper.sin(progress * MathHelper.PI) * 15f;
        mob.setPitch(yawAmount);
        
        // Slight head shake
        if (progress > 0.3f && progress < 0.7f) {
            mob.headYaw = mob.getBodyYaw + MathHelper.sin(animationTicks * 0.5f) * 5f;
        }
    }
    
    private void performShakeAnimation(float progress) {
        // Rapid body shaking
        if (progress < 0.8f) {
            float shakeAmount = MathHelper.sin(animationTicks * 0.8f) * 10f;
            mob.bodyYaw = mob.getBodyYaw() + shakeAmount;
            mob.headYaw = mob.getBodyYaw() + shakeAmount;
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
            mob.getBodyYaw() -= 1f;
        } else {
            mob.getBodyYaw() += 1f;
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
        mob.getBodyYaw() += circleAmount / 10f; // Slow rotation
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
        mob.getBodyYaw() += rollAmount / 15f; // Slow rotation
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
    private static class AmbientAnimation {
        final String id;
        final String name;
        final String description;
        final int durationTicks;
        final List<String> natures;
        final List<String> moods;
        float weight;
        final AnimationType animationType;
        
        AmbientAnimation(String id, String name, String description, int durationTicks,
                         List<String> natures, List<String> moods, float weight, AnimationType animationType) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.durationTicks = durationTicks;
            this.natures = natures;
            this.moods = moods;
            this.weight = weight;
            this.animationType = animationType;
        }
        
        AmbientAnimation withWeight(float newWeight) {
            return new AmbientAnimation(id, name, description, durationTicks, natures, moods, newWeight, animationType);
        }
    }
    
    /**
     * Types of ambient animations.
     */
    private enum AnimationType {
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