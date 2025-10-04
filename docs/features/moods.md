# Moods

Moods are the visible surface of your pet's emotional life. While 30 emotions churn beneath the hood, moods are what you actually see and interact with.

*See also: [Emotions](emotions.md) for the full list of feelings, and [Stimulus System](stimulus.md) for how events trigger emotional responses.*

## What Are Moods?

A mood is the dominant emotional state your pet is currently experiencing. It's calculated by blending active [emotions](emotions.md) together, weighted by their intensity, freshness, and context.

Think of moods as labels for complex emotional cocktails. Your pet isn't just "happy" — they're experiencing a specific blend of Cheerful, Hopeful, and Playfulness that the system recognizes as the **Happy** mood.

*Learn more about how emotions are processed in the [Mood Engine](stimulus.md#the-mood-engine-where-emotions-become-moods) section of the Stimulus System.*

## The Core Moods

Pets+ tracks 14 base moods, each with distinct behavioral and visual characteristics.

### Positive Cluster
1. **Happy** — joyful, bright, tail-wagging contentment
2. **Playful** — energetic, bouncing, ready for action
3. **Curious** — alert, exploring, nose-first into the world
4. **Passionate** — enthusiastic, intense, fired up

### Bonded Cluster
5. **Bonded** — attached, loyal, orbiting their owner
6. **Calm** — peaceful, relaxed, unbothered

### Complex / Reflective
7. **Yūgen** — profound awareness, quiet mystery
8. **Focused** — concentrated, task-oriented
9. **Sisu** — determined endurance, grit in adversity
10. **Saudade** — wistful longing, nostalgia

### Defensive Cluster
11. **Protective** — defensive, watchful, ready to intercede
12. **Restless** — agitated, pacing, uneasy

### Negative Cluster
13. **Afraid** — scared, retreating, avoiding threats
14. **Angry** — mad, aggressive, lashing out

## Ultra-Rare Moods

Three special moods require extreme conditions to manifest. These aren't calculated from standard emotion blending; they trigger only when specific circumstances align.

15. **Echoed Resonance** — triggered by extended Deep Dark exposure or surviving Warden encounters; heightened senses and courage
16. **Arcane Overflow** — proximity to heavy enchanting activity or high-level Enchantment-Bound pets; drunk on arcane energy
17. **Pack Spirit** — when 3+ bonded pets triumph together; synchronized shared power

Ultra-rare moods have unique visual effects and behavioral modifiers. They're not meant to be farmed. They're meant to be *moments*.

## Mood Intensity

Moods aren't binary. Each has an intensity level (0.0 to 1.0) representing how strongly it's being felt.

Low-intensity Happy is a content pet lounging nearby. High-intensity Happy is a whirlwind of tail wags and excited circles.

Intensity affects:
- **Visual feedback** — particle effects, animation speed, color saturation
- **AI behavior** — goal priority, movement patterns, reaction thresholds
- **Mood advancement triggers** — some achievements require witnessing a mood "at its most intense"

## How Moods Shift

Moods update in real time as emotions rise and fall. The system uses adaptive momentum: fresh, strong emotions cause fast mood switches, while old, weak emotions create slow drifts.

**Example flow:**
1. Pet is Calm (high intensity)
2. Owner gets attacked
3. Protectiveness and Vigilant spike hard
4. Mood rapidly shifts to Protective (high intensity)
5. Combat ends
6. Protectiveness slowly decays
7. Mood gradually drifts back toward Calm

The transition speed depends on emotional context. A sudden shock switches moods instantly. Gradual environmental changes produce smooth, slow shifts.

## Mood-Based AI

Each mood has associated AI goals that activate when that mood dominates.

- **Happy** → Follow owner closely, wag tail
- **Playful** → Chase items, bounce around
- **Curious** → Investigate blocks, wander toward new chunks
- **Bonded** → Orbit owner, refuse to leave their side
- **Calm** → Rest, find sunbeams, minimal movement
- **Passionate** → Charge toward objectives, high energy movement
- **Yūgen** → Stargazing behavior, contemplative sitting
- **Focused** → Lock onto tasks, ignore distractions
- **Sisu** → Push through obstacles, refuse to quit
- **Saudade** → Return to familiar locations, nostalgic circling
- **Protective** → Guard owner, position between threats
- **Restless** → Pace, circle, agitated movement
- **Afraid** → Flee from threats, stay behind owner
- **Angry** → Attack hostile targets, aggressive posture

Mood AI doesn't replace role abilities. It *layers on top*. A Guardian pet in Protective mood will prioritize defensive positioning even more aggressively.

## Checking Your Pet's Mood

Moods display in the action bar when you're near your pet. You'll see the current mood label and intensity indicator.

Visual cues:
- **Particle effects** — color and pattern match the mood
- **Animation speed** — high-intensity moods have faster, more exaggerated animations
- **Body language** — ear position, tail movement, posture

## Why Moods Matter

Moods make your pet feel alive. They're not just stat blocks with hitboxes. They have bad days and good days. They get excited when you return after a long absence. They sulk when left alone too long.

This isn't decorative. Mood affects combat effectiveness (Angry pets hit harder, Afraid pets dodge better), exploration behavior (Curious pets find loot more often), and social dynamics (Pack Spirit synchronizes nearby pets).

But more than mechanics, moods are *storytelling*. Your pet's mood history tells you what kind of life they've lived. A pet stuck in Restless or Saudade is telling you something. Pay attention.
