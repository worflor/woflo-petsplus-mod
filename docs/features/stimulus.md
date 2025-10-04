# Stimulus System

The stimulus system is how the world affects your pet's emotions. It's the bridge between gameplay events and emotional responses.

## Event-Driven Design

Pets+ doesn't poll. It doesn't tick-scan. It hooks into existing Minecraft events and reacts when relevant things happen.

This means:
- **Zero background overhead** when nothing is happening
- **Instant responses** when events occur
- **Clean, maintainable code** that extends naturally

The system listens for gameplay events, evaluates context, and pushes appropriate emotional stimuli to affected pets.

## What Gets Tracked?

The stimulus bus monitors a wide range of events:

### Owner Interactions
- Petting (right-click while sneaking)
- Feeding
- Healing with items
- Equipping armor/items
- Breeding participation
- Tribute offerings

### Combat Events
- Pet deals damage
- Pet takes damage
- Owner deals damage (nearby pet watches)
- Owner takes damage (nearby pet watches)
- Hostile killed by pet
- Hostile killed by owner (nearby pet assists)
- Pet kills another pet (bad)

### Environmental Changes
- Block placement/breaking nearby
- Weather shifts (rain start/stop, thunder)
- Dimension changes
- Biome transitions
- Time of day changes
- Music from jukeboxes
- Bed usage nearby

### Social Dynamics
- Other pets nearby
- Players nearby
- Owner leaving/returning
- Pack formations
- Trading ownership
- Pet death (other pets witness)

### Exploration
- Entering new chunks
- Discovering structures
- Locating valuable ores
- Swimming/diving
- Falling/flight
- Sprinting/movement patterns

## Context-Aware Responses

The same event triggers different emotions depending on context.

**Example: Owner takes damage**

- **High bond, pet nearby:** Protectiveness (0.35), Angst (0.25), Vigilant (0.15)
- **Low bond, pet far away:** Angst (0.10), Vigilant (0.05)
- **Pet is Afraid:** Dread (0.30), Terror (0.15) instead of Protectiveness
- **Pet is Angry:** Kefi (0.40), Protectiveness (0.30) — amplified aggression

Context includes:
- **Bond strength** with owner
- **Current mood** state
- **Personality traits** (characteristics, nature)
- **Recent emotional history** (habituation/sensitization)
- **Physical proximity** to the event
- **Pack presence** (other pets nearby)
- **Environmental factors** (biome, weather, dimension)

## Stimulus Intensity

Each stimulus has a base intensity (0.0–1.0), then gets modulated by context.

**Scaling factors:**
- **Bond resilience** — stronger bonds amplify positive emotions, dampen negative ones
- **Nature bias** — certain natures are more/less sensitive to specific emotions
- **Volatility multiplier** — from nature; affects how dramatically emotions swing
- **Personality modifiers** — from characteristics
- **Habituation** — repeated stimuli weaken over time
- **Contagion** — nearby pets spread emotional responses

Formula (simplified):
```
Final Intensity = Base × Nature Bias × Bond Factor × Habituation × (1 + Contagion)
```

The actual implementation is more nuanced, but that's the general idea.

## Species-Specific Triggers

Different pet species have unique stimulus responses.

**Examples:**
- **Wolves** → heightened Pack Spirit when hunting together, Pride on kills
- **Cats** → Lagom ("just right") when in perfect sunny spots, Curiosity spikes near fish
- **Foxes** → Playfulness near sweet berries, Focused when stalking chickens
- **Parrots** → Yūgen during music, Cheerful when mimicking sounds
- **Axolotls** → Content in water, Hiraeth when dry

This isn't hardcoded per species. It's data-driven through the emotional context mapper, so datapacks can extend it.

## Emotional Delta

When a stimulus fires, it creates an **EmotionDelta** — a discrete packet of emotional change.

Structure:
```
EmotionDelta {
  emotion: CHEERFUL,
  amount: 0.25,
  timestamp: 184726
}
```

The mood engine receives these deltas, applies contextual modulation, and integrates them into the pet's emotional state.

Multiple deltas can fire from a single event. Feeding your pet might push:
- Cheerful (0.30)
- Content (0.20)
- Bonded (0.15)

## Cooldowns and Rate Limiting

Some stimuli have built-in cooldowns to prevent spam.

- **Petting** → max once per 2 seconds (prevents click-farming happiness)
- **Music** → max once per 30 seconds per jukebox
- **Exploration** → new chunk discovery tracked with 10-second cooldown

Others (combat, damage) have no cooldown because they're inherently rate-limited by gameplay.

## Stimulus Debugging

The mod tracks stimulus history for debugging. You can view:
- What stimuli fired recently
- Their base and final intensities
- Which emotions were affected
- Contextual modifiers applied

This is mostly for development, but advanced players can use it to understand their pet's behavior.

## Emotion Context Cues

When significant stimuli fire, the system sends **context cues** to the owner. These are subtle chat messages or actionbar hints that explain what your pet is reacting to.

**Examples:**
- `[Your wolf senses danger nearby]` — when hostile detected
- `[Your cat feels cozy by the campfire]` — warmth stimulus
- `[Your parrot is delighted by the music]` — jukebox event

Cues have cooldowns to prevent spam (typically 200-1200 ticks) and only appear when the emotional change is meaningful. They're not mandatory announcements — they're flavor text that helps you understand your pet's inner world.

You can configure or disable cues in the config.

## Gossip System

Pets don't just react to events — they **remember and share** them.

When something interesting happens (campfires lit, treasures found, combat victories), pets create **gossip topics** with:
- **Topic ID** — what happened
- **Intensity** — how significant it was (0.0–1.0)
- **Confidence** — how sure the pet is (0.0–1.0)
- **Timestamp** — when it occurred

Pets within 24 blocks can **share gossip** through social routines:
- **GossipCircleRoutine** — pack members exchange stories during idle moments
- **GossipWhisperRoutine** — one-on-one information sharing

Gossip affects emotions indirectly. Hearing about a campfire gathering might boost Sobremesa even if the pet wasn't there. Learning about a nearby threat increases Vigilant.

This creates a **distributed memory system** — your pack develops shared knowledge without centralized tracking.

## Contagion Propagation

When a stimulus fires for one pet, it can spread to nearby pack members through **emotional contagion**.

The contagion share is:
- Proportional to the original stimulus intensity
- Modified by pack bond strength
- Distance-limited (12 blocks)
- Emotion-specific (some emotions spread more than others)

**High-contagion emotions:**
- Pack Spirit
- Terror
- Pride
- Kefi

**Low-contagion emotions:**
- Hiraeth
- Saudade
- Focused

This creates emergent pack dynamics. One pet's fear can panic the group. One pet's triumph can rally everyone.

## Why Stimulus Matters

The stimulus system is what makes pets feel *responsive*. They're not running on timers or random ticks. They're reacting to *you* and the world around them.

This creates a feedback loop. You notice your pet's mood shifts, you change your behavior, your pet notices and reacts, and the cycle continues. It's the core of the mod's emotional simulation.

Without stimulus, emotions would be static numbers. With it, they're a living, breathing system.
