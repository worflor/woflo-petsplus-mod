# Pets+ Documentation (i guess)

**[Design Philosophy](design_philosophy.md)** — Understand how these systems fit together and why they do what they do.

--- 

Unfortunately, your *real pets* have *real feelings* now.

---

## Core Systems

### [Natures](natures.md)
**Your pet's permanent personality.**

Where you tame a pet shapes who they become. Tame in cold climates for Frisky, hot areas for Fierce. Born pets roll special natures like Blossom in a Cherry Grove, Echoed in the Deep Dark, or Solace if birthed alone. Each pet gets unique stat variance, so no two companions are identical. These baseline traits determine how your pet feels about everything.

### [Emotions](emotions.md)
**29 different feelings, all shifting in real-time.**

Your pet tracks Pride, Curiosity, Frustration, Hiraeth, Kefi, and 24 more emotions at once. Actions have weight. Beat them up and they remember. Give treats and they remember that too, just differently. Emotions decay over time, weaken when repeated too often, and intensify under pressure. When enough of them pile up, things get complicated.

### [Moods](moods.md)
**When emotions peak, moods take over.**

14 core moods like Happy, Playful, Angry, and Saudade shape how your pet acts. Three ultra-rare moods exist too: Pack Spirit when pets bond through combat, Arcane Overflow near heavy enchanting, and Echoed Resonance after surviving Warden encounters. Moods stack, conflict, and change behavior. But what causes these emotional spikes in the first place?

### [Stimulus](stimulus.md)
**Every action ripples outward.**

Combat, exploration, eating, achievements—everything pushes emotional responses to nearby pets. Bond strength, nature, and proximity all shape how they react. Visual feedback shows up in chat and actionbar so you know exactly when you messed up. Pets also gossip, sharing rumors and memories with each other. Of course, emotional baggage alone doesn't help in a fight.

### [Roles](roles.md)
**Nine specializations, each with unique abilities.**

Guardian tanks damage. Striker finishes kills. Support heals allies. Scout finds loot. Skyrider boosts mobility. Enchantment-Bound improves luck. Cursed One embraces chaos. Eepy Eeper rewards rest. Eclipsed manipulates the void. Feature levels at 3, 7, 12, 17, 23, and 27 unlock new abilities. Switching roles resets progress but keeps personality. Speaking of progress.

### [Leveling](leveling.md)
**Shared experience, shared growth.**

Your XP splits evenly among pets within 32 blocks. Early levels go fast with 160% scaling, late game slows to 70%. Tribute gates block progress at level 10, 20, and 30 until you offer gold, diamond, or netherite scrap. Death wipes all progress. At least you'll have achievements to show for the journey.

### [Advancements](advancements.md)
**Track your journey through achievements.**

Five branches reward different playstyles: Bonding Basics for first steps, Emotional Journey for witnessing peak moods, Mystical Connections for special moments, Role Specialization for mastery, and Special milestones for unique events. Some are implemented, others are planned. Either way, your pets remember everything.

## Additional Features

**Social System** — Pets gossip in circles when grouped, whisper one-on-one when alone. They remember first meetings, notice long separations, and respond when packmates show strong emotions.

**More tames** - Frogs with slimeballs, Rabbits with carrots, and turtles with seagrass.

**Pet Trading** — Sneak and right-click another player while holding a leash to transfer ownership. Your pet remembers.

## How Systems Connect

**Nature shapes emotions.** Each nature sets base emotional weights and volatility. Fierce pets anger faster, Frisky pets play harder.

**Emotions form moods.** High intensity triggers mood states. Stack Cheerful, Pride, and Kefi for Passionate. Stack Angst, Frustration, and Hiraeth for Restless.

**Events trigger emotions.** Killing enemies boosts Pride. Dying near pets spikes Angst. Eating without sharing builds Frustration. Context matters.

**Roles unlock abilities.** Leveling opens new skills at specific thresholds. Each role has different progression paths.

**Learning affects growth.** XP gain scales with innate talent, proximity to action, and level curve. Tribute gates add progression checkpoints.

## Datapack and Configs

Customize everything through datapacks and configs:

- **Role definitions** — abilities, XP curves, tribute items, stat scaling (JSON datapacks)
- **Ability definitions** — cooldowns, effects, triggers, descriptions (JSON datapacks)
- **Mood system** — emotion weights, decay rates, threshold adjustments (asset configs)
- **Emotion-to-mood mappings** — how emotions blend into moods (asset configs)
- **Context cues** — visual feedback for emotional events (asset configs)
