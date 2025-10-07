# Emotions

Your pet's emotional life runs deeper than you'd expect. Behind every tail wag and every protective growl sits a system tracking 42 distinct emotions, each influencing how your pet experiences and reacts to the world.

*See also: [Stimulus System](stimulus.md) for how events trigger emotions, and [Moods](moods.md) for how emotions blend into visible states.*

## How Emotions Work

Emotions aren't static labels. They're dynamic weights that rise and fall based on what your pet experiences. Feed your pet, and Cheerful spikes. Leave them alone for too long, and Hiraeth (longing) creeps in. Get ambushed while traveling together, and Vigilant surges alongside Protectiveness.

*Learn more about how events trigger emotions in the [Stimulus System](stimulus.md).*

Each emotion has:
- **Intensity** — how strongly the emotion is felt right now
- **Impact budget** — accumulated emotional weight over time
- **Cadence** — how often this emotion gets triggered
- **Freshness** — how recent the last trigger was

These metrics combine to determine which emotions dominate your pet's current state.

## The 42 Emotions

Emotions are grouped into thematic clusters that reflect different aspects of your pet's inner experience.

### Positive / Bright
Uplifting feelings that arise from joy, connection, and satisfaction.

- **Cheerful** — simple happiness
- **Hopeful** — optimistic anticipation
- **Glee** — explosive joy
- **Blissful** — serene contentment
- **Content** — quiet satisfaction
- **Playfulness** — energetic fun
- **Kefi** — spirited enthusiasm
- **Pride** — dignified accomplishment
- **Relief** — tension released
- **Hanyauku** — playful tiptoe walking

### Connection / Social
Emotions tied to bonds, relationships, and belonging.

- **Ubuntu** — interconnectedness and humanity
- **Querecia** — deep sense of rest and belonging
- **Empathy** — shared feeling
- **Loyalty** — steadfast devotion
- **Sobremesa** — lingering after a meal together
- **Pack Spirit** — unity with the group (ultra-rare)

### Threat / Aversion
Responses to danger, discomfort, or unwanted situations.

- **Startle** — sudden alarm
- **Vigilant** — alert watchfulness
- **Protectiveness** — defensive instinct (as state)
- **Protective** — protective action (as emotion)
- **Foreboding** — anticipatory dread
- **Worried** — anxious concern
- **Angst** — gnawing worry
- **Frustration** — blocked goals
- **Disgust** — revulsion

### Reflective / Contemplative
Quiet, observational, or philosophical states.

- **Yūgen** — profound, mysterious awareness
- **Stoic** — calm endurance
- **Focused** — concentrated attention
- **Gaman** — patient perseverance
- **Lagom** — "just right" balance
- **Wabi-Sabi** — beauty in imperfection
- **Mono no Aware** — awareness of impermanence
- **Curious** — hunger for discovery

### Longing / Melancholic
Yearning, nostalgia, and bittersweet emotions.

- **Hiraeth** — homesickness, longing for the familiar
- **Saudade** — wistful nostalgia
- **Fernweh** — wanderlust, farsickness
- **Melancholy** — pensive sadness
- **Nostalgia** — longing for the past
- **Regret** — remorse over past actions
- **Ennui** — listless boredom

### Resilient / Determined
Endurance, grit, and inner strength.

- **Sisu** — determined grit
- **Restless** — unsettled energy

### Ultra-Rare States
Exceptional emotions triggered only under special circumstances.

- **Echoed Resonance** — Deep Dark attunement
- **Arcane Overflow** — enchantment saturation

## What Triggers Emotions?

Emotions respond to gameplay events in real time. No background polling, no tick spam. Just clean, event-driven reactions.

**Examples:**
- Being pet by owner → Cheerful, Content
- Combat with hostiles → Kefi, Vigilant, Protectiveness
- Owner takes damage → Protectiveness, Angst
- Exploring new chunks → Curiosity, Hopeful
- Left alone for extended periods → Hiraeth, Saudade
- Witnessing owner die → Terror, Dread
- Music from jukebox → Yūgen, Lagom
- Breeding success → Pride, Pack Spirit

The system tracks context. A pet getting attacked while alone triggers different emotions than getting attacked while their owner is nearby. Personality traits (from characteristics and natures) modulate how intensely each emotion is felt.

## Habituation and Sensitization

Emotions aren't static responses. Your pet adapts.

**Habituation** — Repeated exposure to the same trigger *decreases* sensitivity over time. Pet your companion a hundred times in a row, and the Cheerful spike gets smaller. This is psychologically accurate; novelty fades.

**Sensitization** — Infrequent or unexpected triggers *increase* sensitivity. A pet that rarely sees combat will react more intensely when it happens.

This creates emergent personality. A pet constantly thrown into battle becomes stoic and unflinching. A pampered housecat startles at every shadow.

## Emotional Contagion

Emotions spread between nearby pets. A pack traveling together synchronizes emotionally. One pet's fear can ripple through the group. Shared victories amplify Pride and Pack Spirit across all participants.

Bond strength affects contagion. Strongly bonded pets are more emotionally attuned to each other.

## How Emotions Become Moods

Emotions don't display directly. Instead, they blend together into recognizable **mood states**. Think of emotions as ingredients and moods as the resulting dish.

Multiple emotions combine, weighted by their current intensity and freshness, to produce the dominant mood. The system recalculates this blend continuously as new events occur.

A pet experiencing high Cheerful + moderate Playfulness + low Curiosity will likely show a **Happy** mood. Swap in high Vigilant + moderate Protectiveness + low Dread, and you get **Protective** mood instead.

See the [Moods](moods.md) documentation for how this blending works and what each mood means.

## Why This Matters

Emotions give your pet memory and personality. A pet that's been through hell and back won't react the same as a fresh tame. Environmental storytelling emerges naturally. You don't need to track this consciously; it just happens, and you notice it in how your pet behaves.

The system is designed to reward observation. Pay attention to your pet's mood shifts, and you'll learn what they respond to. Ignore them, and you're missing half the mod.
