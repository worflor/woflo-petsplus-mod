# Stimulus System

Your pets feel things. Not randomly, not on a timer, but in response to the world around them.

This system is what connects game events to emotional reactions. When you pet your wolf, when danger appears, when the sun sets—your pet notices and responds. It's all event-driven and context-aware, creating the foundation for everything they do.

*See also: [Emotions](emotions.md) for the full list of feelings your pet can experience, and [Moods](moods.md) for how emotions blend into visible states.*

## The Big Picture: How It Works

Here's the complete flow:

1. **Event happens** → You pet your wolf, a hostile spawns nearby, you find a treasure
2. **Context evaluated** → The system checks your bond, your pet's mood, their nature, who's around
3. **[Emotions](emotions.md) triggered** → Multiple feelings get pushed based on the situation
4. **Mood Engine processes** → Emotions get blended into the [moods](moods.md) you actually see
5. **Behavior emerges** → Your pet's mood drives their actions, abilities, and responses
6. **You notice and react** → Visual feedback shows you what's happening, you adjust your behavior
7. **Cycle continues** → Your new actions create new stimuli

This creates a living emotional simulation. Your pets aren't scripted—they're responding to *you* and their world.

## Context Is Everything

The same event triggers different emotions depending on the situation. Your pet doesn't just react to *what* happens—they react to *how* it happens.

Example: You take damage

- **Close bond, pet nearby:** rushes to protect you, feels worried
- **Weak bond, pet distant:** barely notices, mild concern
- **Pet is already afraid:** panics instead of protecting
- **Pet is already angry:** gets fired up for battle

What shapes their reaction:

- **How close you are** — both emotionally (bond level) and physically (distance)
- **Their current [mood](moods.md)** — a happy pet and an angry pet respond very differently
- **Who they are** — [nature](natures.md) and characteristics matter
- **What they've been through** — recent experiences affect sensitivity
- **Who's around** — pack members influence each other
- **Where they are** — biome, weather, and dimension all play a role

Every event starts with a base emotional impact, then your pet's personality and situation shape how strongly they actually feel it. Think of it like this: a well-bonded pet with a calm nature won't panic as easily as a skittish stranger. A volatile pet in a pack will feed off everyone's energy.

## Different Pets, Different Feelings

Each species has its own personality quirks.

- **Wolves** love hunting together and feel proud of their kills
- **Cats** find the perfect sunny spot and think "this is exactly right"
- **Foxes** bounce around sweet berries but go dead serious stalking chickens
- **Parrots** get swept up in music and delight in mimicking sounds
- **Axolotls** are happiest in water and miss it terribly when dry

These behaviors come from data files, so datapacks can change them or add new species.

## What Events Get Tracked?

The stimulus system monitors a wide range of gameplay events:

**Owner Interactions:** Petting, feeding, healing, equipping armor, breeding, tribute offerings

**Combat Events:** Pet/owner dealing/taking damage, kills, assists, witnessing combat

**Environmental Changes:** Blocks placed/broken nearby, weather shifts, dimension changes, biome transitions, time of day, music from jukeboxes, bed usage

**Social Dynamics:** Other pets/players nearby, owner leaving/returning, pack formations, ownership changes, pet death witnessed

**Exploration:** New chunks, discovering structures, locating ores, swimming/diving, falling/flight, movement patterns

## The Mood Engine: Where It All Comes Together

Events trigger [emotions](emotions.md). The Mood Engine blends them into the [moods](moods.md) you see.

It works like real feelings: recent intense stuff matters more than old faded stuff. Each emotion feeds into multiple moods with different strengths, and your pet's [nature](natures.md) shapes how they process everything.

Sudden fear hits instantly. Contentment builds slowly. The system tries to mirror how actual emotions work.

## Putting It All Together

Here's the full loop:

1. **Something happens** — you pet your wolf, a hostile appears, treasure is found
2. **Emotions trigger** — your pet reacts based on who they are and what's going on
3. **Moods shift** — emotions blend together, strong recent feelings dominate
4. **Behavior changes** — mood drives what they do and how they act
5. **You notice** — visual feedback and hints show you what's happening
6. **You respond** — and your actions create new events

The stimulus system is the input. The Mood Engine is the processor. The result is a companion that feels real enough you forget it's variables and math.
