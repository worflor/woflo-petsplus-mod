# Behavior States Architecture: Three-Layer Emotional System

## The Problem with Current Approaches

The current mood system suffers from **emotional hijacking** - inappropriate emotions overpowering situationally relevant ones. Previous solutions like the lane system were complex and hard to extend. We need a clean, extensible architecture that separates concerns properly.

## The Solution: Three-Layer Architecture

### 🔬 Layer 1: Emotions (Raw Emotional Data)

**The primitive building blocks - what actually happened to and around the pet**

These are the fundamental emotional responses triggered by events:

- `PROTECTIVENESS`, `KEFI`, `MELANCHOLY`, `CURIOSITY`, `FRUSTRATION`
- Raw values that get pushed by events and decay over time
- **No interpretation or filtering** - just pure emotional response data
- Example: Getting attacked pushes `FEAR: 0.8` and `ANGER: 0.6`

### 🎭 Layer 2: Behavior States (The Emotional Interpreter)

**The context-aware filter that gives emotions meaning**

Behavior states are **perspectives** that interpret the same emotions differently:

#### Guardian Mode (Combat-Focused)

```java
GUARDIAN_MODE {
    amplify: [PROTECTIVENESS: 2.2x, STOIC: 1.8x, FOCUSED: 1.6x]
    dampen: [PLAYFUL: 0.2x, CURIOUS: 0.4x, CHEERFUL: 0.3x]
    priority: PROTECTION_EMOTIONS
    triggers: [owner_under_attack, nearby_hostiles, low_owner_health]
    duration: combat_end + 30s_cooldown
}
```

#### Bonding Mode (Social Connection)  

```java
BONDING_MODE {
    amplify: [KEFI: 1.8x, UBUNTU: 2.0x, CHEERFUL: 1.5x, BLISSFUL: 1.4x]
    dampen: [ANGST: 0.3x, ENNUI: 0.2x, AFRAID: 0.4x]
    priority: CONNECTION_EMOTIONS
    triggers: [owner_interaction, feeding, petting, shared_activities]
    duration: interaction_end + 60s_fade
}
```

#### Scout Mode (Exploration & Discovery)

```java
SCOUT_MODE {
    amplify: [CURIOUS: 2.0x, FERNWEH: 1.7x, HOPEFUL: 1.4x, GLEE: 1.3x]
    dampen: [LAGOM: 0.4x, WABI_SABI: 0.5x, REGRET: 0.3x]
    priority: DISCOVERY_EMOTIONS
    triggers: [new_biome, unexplored_area, owner_moving_fast, loot_detection]
    duration: exploration_activity + 45s_fade
}
```

#### Sanctuary Mode (Rest & Peace)

```java
SANCTUARY_MODE {
    amplify: [LAGOM: 2.1x, WABI_SABI: 1.6x, SOBREMESA: 1.8x, YUGEN: 1.5x]
    dampen: [RESTLESS: 0.1x, STARTLE: 0.3x, FRUSTRATION: 0.2x]
    priority: TRANQUIL_EMOTIONS
    triggers: [sitting, safe_area, nighttime, low_activity]
    duration: while_conditions_met + 20s_fade
}
```

#### Work Mode (Productivity & Focus)

```java
WORK_MODE {
    amplify: [FOCUSED: 2.0x, GAMAN: 1.8x, RELIEF: 1.4x]
    dampen: [PLAYFUL: 0.4x, RESTLESS: 0.6x, HIRAETH: 0.3x]
    priority: PRODUCTIVITY_EMOTIONS
    triggers: [owner_mining, owner_building, crafting_nearby]
    duration: work_activity + 30s_fade
}
```

#### Hunt Mode (Predator Instincts)

```java
HUNT_MODE {
    amplify: [FOCUSED: 1.9x, PROTECTIVENESS: 1.5x, GLEE: 1.3x]
    dampen: [AFRAID: 0.2x, REGRET: 0.3x, MONO_NO_AWARE: 0.4x]
    priority: PREDATOR_EMOTIONS
    triggers: [target_spotted, execution_opportunity, combat_momentum]
    duration: hunt_complete + 15s_fade
}
```

#### Service Mode (Support & Assistance)

```java
SERVICE_MODE {
    amplify: [UBUNTU: 2.0x, RELIEF: 1.7x, HOPEFUL: 1.5x]
    dampen: [ENNUI: 0.3x, SAUDADE: 0.4x, ANGST: 0.2x]
    priority: HELPING_EMOTIONS
    triggers: [potion_sharing, healing_owner, buff_applied]
    duration: service_complete + 40s_fade
}
```

### 🎨 Layer 3: Moods (Player-Facing Display)

**The clean, understandable states players see**

Moods are **emergent properties** of emotions filtered through behavior states:

- `"Protective"` - when combat state amplifies protectiveness emotions
- `"Playful"` - when social state amplifies kefi and cheerfulness  
- `"Curious"` - when exploration state amplifies investigation emotions
- `"Content"` - when resting state amplifies peaceful emotions

## System Integration Points

### 🎯 Where Behavior States Get Triggered

**Role-Integrated Context Detection:**

```java
// Guardian pets prioritize Guardian Mode
if (pet.hasRole(GUARDIAN) && (pet.isInCombat() || owner.isUnderThreat())) {
    pet.setBehaviorState(GUARDIAN_MODE);
}

// Striker pets enter Hunt Mode during combat opportunities
if (pet.hasRole(STRIKER) && (targetAvailable() || executionOpportunity())) {
    pet.setBehaviorState(HUNT_MODE);
}

// Support pets activate Service Mode when helping
if (pet.hasRole(SUPPORT) && (potionSharingActive() || ownerNeedsHealing())) {
    pet.setBehaviorState(SERVICE_MODE);
}

// Scout pets enter Scout Mode during exploration
if (pet.hasRole(SCOUT) && (pet.isExploringNewArea() || lootDetected())) {
    pet.setBehaviorState(SCOUT_MODE);
}

// Work Mode for Enchantment-Bound during productivity activities
if (pet.hasRole(ENCHANTMENT_BOUND) && (owner.isMining() || owner.isBuilding())) {
    pet.setBehaviorState(WORK_MODE);
}

// Social interactions trigger Bonding Mode for any role
if (owner.isInteractingWithPet() || owner.isFeedingPet()) {
    pet.setBehaviorState(BONDING_MODE);
}

// Default to Sanctuary Mode when idle and safe
if (pet.isIdle() && pet.isInSafeSpace()) {
    pet.setBehaviorState(SANCTUARY_MODE);
}
```

**Behavior State Transitions:**

```java
public class BehaviorStateManager {
    private BehaviorState currentState = RESTING_STATE;
    private float stateInertia = 0.8f; // Resistance to state changes
    
    public void updateBehaviorState(EmotionalContext context) {
        BehaviorState suggestedState = context.getSuggestedState();
        
        // States have momentum - don't flip-flop constantly
        if (suggestedState.getPriority() > currentState.getPriority() * stateInertia) {
            transitionTo(suggestedState);
        }
    }
}
```

### 🔄 Emotion Processing Pipeline

```java
public class EmotionalProcessor {
    
    public void processEmotions(Pet pet) {
        // 1. Raw emotions get pushed by events
        EmotionMap rawEmotions = pet.getRawEmotions();
        
        // 2. Current behavior state interprets emotions
        BehaviorState currentState = pet.getBehaviorState();
        EmotionMap interpretedEmotions = currentState.interpret(rawEmotions);
        
        // 3. Interpreted emotions generate mood
        Mood resultingMood = MoodGenerator.generate(interpretedEmotions);
        
        // 4. Update pet's displayed mood
        pet.setMood(resultingMood);
    }
}
```
