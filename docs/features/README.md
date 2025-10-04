# Pets+ Features

Core systems reference for the Pets+ mod.

## Core Systems

### [Natures](natures.md)
Stat distributions and emotional baselines assigned at taming or birth. Wild natures (temperature-based) vs born natures (breeding-based). Each nature rolls individual variance (±15% stats, ±10% multipliers) for pet uniqueness.

### [Emotions](emotions.md)
30-emotion spectrum tracking pet feelings in real-time. Emotions decay over time, habituate with repetition, and sensitize with intensity. Drives mood formation and behavioral responses.

### [Moods](moods.md)
Temporary emotional states formed when emotions reach intensity thresholds. 14 core moods + 3 ultra-rare moods. Moods persist for durations, modify behavior, and can stack or conflict.

### [Stimulus](stimulus.md)
Event-driven emotion triggers. Gameplay events (combat, exploration, owner actions) push emotional stimuli to nearby pets. Context-aware responses based on bond, nature, proximity, and pack presence. Includes emotion context cues (visual feedback) and gossip system (social memory sharing).

### [Roles](roles.md)
9 specializations defining abilities and playstyle. Passive auras pulse on intervals, active abilities have cooldowns, unlocks at feature levels (3, 7, 12, 17, 23, 27). Role-switching resets progression but preserves bond/personality. Fully datapack-configurable.

### [Leveling](leveling.md)
XP progression from 1-30. Owner XP is split evenly among pets within 32 blocks. Level scaling modifiers (160% at low levels, 70% at endgame). Tribute gates at configurable milestones. Death loses all progression.

## Additional Features

**Social System** - Pets share gossip in circles (8.5-block range) and whispers (6-block 1-on-1), form packs, recognize greetings/reunions, and respond empathetically to nearby companions.

**Advancements** - Extensive achievement tree with 6 branches: Bonding Basics, Emotional Journey (13 emotions), Mystical Connections, Role Specialization, and Special milestones. See main README for full list.

**Pet Trading** - Transfer ownership via leash exchange between players.

## System Interactions

**Nature → Emotions:** Nature defines base emotional weights and volatility multipliers.  
**Emotions → Moods:** High-intensity emotions trigger mood formation.  
**Stimulus → Emotions:** Events push emotional deltas modified by context.  
**Roles → Abilities:** Leveling unlocks role-specific abilities at feature levels.  
**Leveling → Stats:** XP gain modified by characteristics, participation, and level curve.

## Datapack Support

All systems support datapack customization:
- Nature definitions and stat curves
- Emotion weights and decay rates
- Mood triggers and durations
- Stimulus mappings and intensities
- Role abilities and rewards
- XP curves and tribute items
