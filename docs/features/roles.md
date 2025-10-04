# Roles

Roles define what your pet *does*. Each role comes with a unique ability set that unlocks as your pet levels up, transforming them from a basic companion into a specialized ally.

## The Nine Roles

Every pet can be assigned one role. Choose based on your playstyle, the pet's species, or just what feels right narratively.

### 1. Guardian
A loyal shield that steps between you and the world.

**Identity:** Defensive tank, damage sponge, knockback resist  
**Playstyle:** Frontline protector  
**Key Abilities:**
- Absorb portion of owner's incoming damage
- Reduce owner's knockback
- Taunt hostiles toward itself
- Generate shield barriers at high levels

**Best for:** Players who take lots of hits, melee-focused combat, exploring dangerous areas

### 2. Striker
A closer with a taste for last hits.

**Identity:** Execute specialist, burst damage, combat finisher  
**Playstyle:** Aggressive DPS  
**Key Abilities:**
- Deal bonus damage to low-health enemies
- Empower owner's attacks when nearby
- Critical strike chance on wounded targets
- Stack kill momentum for increasing damage

**Best for:** Fast-paced combat, boss fights, aggressive exploration

### 3. Support
A cozy caretaker that keeps you going longer.

**Identity:** Healer, buffer, sustain provider  
**Playstyle:** Passive aura support  
**Key Abilities:**
- Extend potion duration for owner and allies
- Share beneficial status effects in radius
- Passive regeneration aura
- Cleanse negative effects at high levels

**Best for:** Long expeditions, group play, potion-heavy builds

### 4. Scout
Eyes like lanterns, pockets for days.

**Identity:** Explorer, loot magnet, utility specialist  
**Playstyle:** Exploration focus  
**Key Abilities:**
- Illuminate nearest hostile (glowing effect)
- Attract nearby XP orbs and items toward owner
- Reveal nearby ores/structures
- Increase loot drop chances

**Best for:** Mining, exploration, treasure hunting, casual play

### 5. Skyrider
Wind at your back, sky in your stride.

**Identity:** Mobility specialist, fall damage negation, aerial control  
**Playstyle:** Vertical movement enhancement  
**Key Abilities:**
- Soften fall damage for owner
- Launch enemies into air with owner's strikes
- Grant brief slow-falling or levitation bursts
- Boost jump height and air control

**Best for:** Mountain traversal, elytra-free flight, knockback-focused combat

### 6. Enchantment-Bound
Your gear's echo, humming with second chances.

**Identity:** Luck modifier, enchantment synergy, work-speed enhancer  
**Playstyle:** Passive economic boost  
**Key Abilities:**
- Extend efficiency/haste burst duration on tools
- Nudge Fortune/Looting drop chances
- Reduce experience cost for repairs
- Chance to preserve item durability

**Best for:** Builders, miners, enchanters, grinders

### 7. Cursed One
A beautiful bad omen that turns fear into leverage.

**Identity:** High-risk glass cannon, soul-bound companion  
**Playstyle:** Death-linked powerhouse  
**Key Abilities:**
- Massive stat boosts and unique combat effects
- **Curse of Binding:** Pet dies when owner dies
- Cannot be revived or recovered
- Extremely high damage output and special triggers

**Best for:** Hardcore players, high-stakes runs, dramatic storytelling

### 8. Eepy Eeper
A cozy buddy that rewards slowing down.

**Identity:** Rest-based healer, campfire companion  
**Playstyle:** Passive downtime benefits  
**Key Abilities:**
- Create healing aura when resting near owner
- Boost natural regeneration during sleep/sitting
- Reduce hunger drain while idle
- Amplify bed sleep benefits

**Best for:** Base-builders, slow-paced exploration, peaceful mode

### 9. Eclipsed
Void-kissed mischief that paints targets for ruin.

**Identity:** Debuff specialist, void magic, ender utility  
**Playstyle:** Target marking and ender-themed tricks  
**Key Abilities:**
- Brand enemies for bonus damage from all sources
- Reveal nearby hidden mobs (invisibility, walls)
- Mobile ender chest access
- Void-aligned combat debuffs

**Best for:** Endgame players, End dimension exploration, coordinated combat

## How Roles Work

Roles are assigned when you first register a pet. You can change a pet's role later, but doing so **resets their level, XP, and all unlocked abilities**. Their bond, memories, and personality remain unchanged.

Each role has:
- **Passive effects** — always active (auras, buffs)
- **Active abilities** — triggered manually or on specific events
- **Ability unlocks** — earned at feature levels (3, 7, 12, 17, 23, 27)
- **Stat biases** — minor modifiers to base stats

### Passive Auras

Many roles have **passive auras** that pulse on intervals:
- **Interval** — how often the aura fires (e.g., 160 ticks = 8 seconds)
- **Radius** — effect range from the pet (usually 6-12 blocks)
- **Min Level** — level required to activate (often 1, sometimes higher)
- **Sitting Requirement** — some auras only work when pet is sitting

Auras target different groups:
- **OWNER** — only you
- **OWNER_AND_PETS** — you + all your pets in range
- **OWNER_AND_ALLIES** — you + nearby friendly players
- **ALL_ALLIES** — all friendly entities (players + pets + villagers)

Example: Support's regeneration aura fires every 160 ticks, affects OWNER_AND_PETS within 8 blocks, requires level 3+.

### Ability Cooldowns

Active abilities have cooldowns tracked per pet:
- Stored as `String cooldownKey` → `long cooldownEndTick`
- Cooldown length varies by ability (30 seconds to 5 minutes)
- Abilities become available again once `worldTime > cooldownEndTick`
- No cooldown reduction mechanics (yet)

## Ability Ranks

Abilities unlock in stages:
- **Rank I** (early levels) — basic version
- **Rank II** (mid levels) — improved version
- **Rank III** (high levels) — powerful version
- **Max Rank** (level 30) — ultimate version

Max-rank abilities are unlocked after paying the level 30 tribute. They're significantly more powerful than earlier ranks.

## Multi-Pet Synergies

Different roles complement each other. A Guardian + Support + Striker pack covers defense, sustain, and damage. A Scout + Eclipsed duo excels at exploration and combat initiation.

Role diversity matters more in group content, but even solo players benefit from having multiple pets with different specializations for different situations.

## Level-Gated Features

Most abilities require specific levels. Roles define **level rewards** that unlock abilities at precise thresholds.

**Common unlock pattern:**
- **Level 1:** Role assigned, passive effects active
- **Level 3:** First ability (Rank I)
- **Level 7:** Second ability or first upgrade (Rank II)
- **Level 12:** Third ability or major upgrade
- **Level 17:** Fourth ability or second upgrade (Rank III)
- **Level 23:** Fifth ability or powerful upgrade
- **Level 27:** Sixth ability or near-max power
- **Level 30 (post-tribute):** Max-rank abilities, role-defining ultimate

Exact unlocks vary per role. Guardians get defensive layers. Strikers get damage escalation. Support gets wider auras.

## Configuration and Datapacks

Roles are **datapack-driven**. You can:
- Adjust passive aura intervals and radii
- Modify ability cooldowns and power levels
- Change stat scaling curves
- Add new abilities to existing roles
- Create entirely new roles with custom JSON

The config file includes per-role override sections. Changes apply on reload — no code changes required.

## Choosing a Role

There's no wrong choice. Every role is viable. Pick based on:
- **Your playstyle** — aggressive, defensive, exploratory, etc.
- **The pet's species** — wolves make thematic Guardians, parrots fit Scout, cats suit Eepy Eeper
- **Narrative fit** — what story are you telling with this companion?
- **Pack composition** — what roles are you missing?

You can always tame more pets or breed new ones to try other roles.

## Role Configuration

Roles are data-driven. Server admins and modpack creators can edit role definitions via JSON files in `config/petsplus/roles/`.

This includes:
- Ability unlock levels
- Stat modifiers
- Passive aura effects
- XP curve adjustments
- Tribute requirements

See the configuration docs for details.
