# Refined AI Behaviors

This document describes the refined AI behaviors implemented in Pets+ to enhance pet intelligence and emotional responsiveness.

## Overview

The refined AI system introduces four key behavior categories that create more dynamic and emotionally-driven pet interactions:

1. **Emotion-Driven Defense** - Combat responses based on Fear emotion
2. **Mood-Based Environmental Curiosity** - Exploration triggered by positive moods
3. **Personality-Infused Ambience** - Idle animations reflecting pet nature
4. **Simple Robust Fetch** - Straightforward item retrieval behavior

## 1. Self-Preservation Goal (Emotion-Driven Defense)

### Description
A combat response system that activates when a pet's Fear emotion exceeds a configurable threshold. The behavior varies based on the pet's role:

- **Guardian**: Enters defensive stance, using protective abilities
- **Scavenger**: Seeks safety near owner
- **Other roles**: Retreats from threat

### Implementation
- **File**: `src/main/java/woflo/petsplus/ai/goals/combat/SelfPreservationGoal.java`
- **Goal ID**: `petsplus:self_preservation`
- **Priority**: 12 (high priority for combat)
- **Activation**: Fear emotion > 0.6

### Configuration
```json
{
  "self_preservation": {
    "fear_threshold": 0.6,
    "retreat_distance": 12.0,
    "owner_protection_radius": 6.0,
    "max_duration_seconds": 10
  }
}
```

### Emotional Feedback
- **Startle**: 0.15 (reinforces fear response)
- **Vigilant**: 0.10 (increased alertness)
- **Worried**: 0.08 (concern about threat)
- **Contagion**: Spreads Startle emotion to nearby pets

## 2. Curiosity Goal (Mood-Based Environmental Curiosity)

### Description
An exploration system that drives pets to interact with interesting blocks in their environment. Only activates when the pet has a positive mood (Happy, Playful, Curious, Bonded).

### Implementation
- **File**: `src/main/java/woflo/petsplus/ai/goals/special/CuriosityGoal.java`
- **Goal ID**: `petsplus:curiosity`
- **Priority**: 16 (lower than self-preservation)
- **Activation**: Positive mood dominant

### Block Tags
Pets will investigate blocks tagged with `petsplus:pet_interest_points`, including:
- Beds, chests, jukeboxes
- Workstations (crafting table, furnace, etc.)
- Decorative blocks (flower pots, lecterns)
- Special blocks (beacon, enchanting table, etc.)

### Configuration
```json
{
  "curiosity": {
    "scan_radius_blocks": 8.0,
    "interaction_radius_blocks": 2.0,
    "interaction_duration_seconds": 3,
    "max_duration_seconds": 15
  }
}
```

### Emotional Feedback
- **Curious**: 0.20 (reinforces exploration)
- **Cheerful**: 0.15 (joy of discovery)
- **Content**: 0.10 (satisfaction)
- **Contagion**: Spreads Curious emotion to nearby pets

## 3. Perform Ambient Animation Goal (Personality-Infused Ambience)

### Description
A data-driven system that plays ambient animations based on pet's nature and current mood. Creates more lifelike idle behavior through weighted animation selection.

### Implementation
- **File**: `src/main/java/woflo/petsplus/ai/goals/idle/PerformAmbientAnimationGoal.java`
- **Goal ID**: `petsplus:perform_ambient_animation`
- **Priority**: 30 (low priority for ambient animations)

### Animation Data
Animations are defined in `src/main/resources/data/petsplus/ambient_animations.json` with:
- Nature compatibility (lazy, energetic, curious, etc.)
- Mood compatibility (calm, playful, happy, etc.)
- Weighted selection based on compatibility
- Duration and animation type

### Animation Types
- **Stretch**: Full body stretch
- **Yawn**: Tired yawn
- **Shake**: Body shake
- **Tail Wag**: Excited tail wagging
- **Ear Perk**: Attentive ear movement
- **Groom**: Self-cleaning behavior
- And more...

### Configuration
```json
{
  "ambient_animations": {
    "enable_nature_filtering": true,
    "enable_mood_filtering": true,
    "min_cooldown_seconds": 15,
    "max_cooldown_seconds": 60
  }
}
```

### Emotional Feedback
Varies by animation type:
- **Stretch/Yawn/Groom**: Content emotion
- **Shake/Tail Wag/Roll**: Playfulness emotion
- **Ear Perk/Sniff/Head Tilt**: Curious emotion

## 4. Simple Fetch Item Goal (Robust Fetch)

### Description
A straightforward fetch behavior that picks up nearby items and brings them to the owner. Designed to be robust and simple without complex behaviors.

### Implementation
- **File**: `src/main/java/woflo/petsplus/ai/goals/play/SimpleFetchItemGoal.java`
- **Goal ID**: `petsplus:fetch_item`
- **Priority**: 18 (medium priority for play)

### Behavior Phases
1. **Seeking**: Find nearest item within range
2. **Collecting**: Move to and pick up the item
3. **Returning**: Return to owner with item
4. **Delivering**: Drop item at owner's feet

### Configuration
```json
{
  "simple_fetch": {
    "max_item_distance": 12.0,
    "max_owner_distance": 20.0,
    "max_fetch_time_seconds": 15,
    "enable_bond_influence": true
  }
}
```

### Emotional Feedback
- **Playfulness**: 0.15 (playful activity)
- **Loyalty**: 0.10 (bringing item to owner)
- **Cheerful**: 0.08 (happy to help)
- **Contagion**: Spreads Playfulness emotion

## Integration with Pet Component System

All new behaviors integrate with the existing PetComponent system:

- **Emotion System**: Behaviors trigger and respond to emotions
- **Mood System**: Activation conditions based on mood states
- **Nature System**: Animation selection influenced by pet nature
- **Role System**: Combat behaviors vary by pet role
- **Bond System**: Engagement influenced by bond strength

## Configuration

All behaviors can be configured through `src/main/resources/assets/petsplus/configs/refined_ai_behaviors.json`:

- Thresholds and distances
- Duration and cooldown times
- Feature toggles
- Debug logging options

## Future Enhancements

Potential areas for future expansion:

1. **More Role-Specific Behaviors**: Additional behaviors for each pet role
2. **Environmental Awareness**: More sophisticated block interaction
3. **Learning System**: Pets learning from repeated interactions
4. **Social Behaviors**: Enhanced pet-to-pet interactions
5. **Weather/Time Influences**: Behaviors affected by environment

## Testing

To test the new behaviors:

1. Spawn a pet with different natures and roles
2. Trigger Fear emotion (e.g., through combat) to test Self-Preservation
3. Ensure pet has positive mood to test Curiosity
4. Place interesting blocks nearby to test environmental interaction
5. Drop items on ground to test Simple Fetch
6. Observe ambient animations during idle periods

Debug logging can be enabled in the configuration file to trace behavior activation.