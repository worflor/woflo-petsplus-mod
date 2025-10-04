# Roles

Roles decide *how* your pet shows up for you. Pick a specialization and
their emotional life, [XP curve](leveling.md), and learned tricks snap
into focus. Change the role later and they keep the vibes, but levels,
abilities, and tributes reset.

## How Roles Fit Into the Ecosystem

- **They ride on leveling.** Feature breakpoints at 3, 7, 12, 17, 23, and
  27 unlock the good stuff. Peek at the [leveling guide](leveling.md) for
  the pacing.
- **They respect personality.** A Fierce pet plays Guardian differently
  than a Frisky one because [natures](natures.md) tilt stats and learning.
- **They blend with moods.** A Striker in an [Angry mood](moods.md) goes
  feral. A Support basking in **Bonded** doubles down on healing.
- **They’re remixable.** Everything lives in JSON, so adding or tweaking a
  role is a datapack edit away.

## Role Quick Reference

| Role | Core Fantasy | Signature Gifts |
|------|--------------|-----------------|
| **Guardian** | Living shield, loyal bulwark | Redirects damage, taunts foes, shrugs off knockback |
| **Striker** | Executioner with zero chill | Execute bonuses, crit windows, kill momentum |
| **Support** | Cozy sustain engine | Regen auras, potion sharing, cleanses |
| **Scout** | Treasure-seeking pathfinder | Glowing marks, loot luck, ore sense |
| **Skyrider** | Gravity-denying windrunner | Fall negation, jump boosts, launch effects |
| **Enchantment-Bound** | Gear-whispering luck totem | Fortune nudges, repair discounts, durability saves |
| **Cursed One** | Soul-bound glass cannon | Extreme spikes, lethal linkage to owner |
| **Eepy Eeper** | Restful hearth spirit | Sleep buffs, hunger relief, chill-zone healing |
| **Eclipsed** | Void-marking saboteur | Target branding, ender utility, debuffs |

Each role carries its own `xpCurve`, stat lean, and unlock list. What
follows are the vibes, highlights, and why you’d bring each into a pack.

## The Nine Roles

### Guardian — “I’ve got you.”

- **Identity:** Frontline protector that soaks hits meant for you.
- **Vibe:** Stoic, immovable, stubbornly loyal.
- **Standout Unlocks:**
  - Redirect a chunk of your damage starting at Level 3.
  - Project barriers and crowd-control auras in the late 20s.
  - Passive knockback resistance from Level 1 keeps them anchored.
- **Best When:** You are melee-focused, wandering dangerous places, or
  just need a safety net while you experiment.

### Striker — “Point me at the problem.”

- **Identity:** Burst finisher obsessed with last hits.
- **Vibe:** Relentless, hungry, high-tempo.
- **Standout Unlocks:**
  - Execute bonuses that scale with enemy missing health.
  - Shared crit windows that juice your own attacks.
  - Momentum stacks that escalate damage on kill streaks.
- **Best When:** You’re hunting bosses, speed-clearing structures, or
  want a companion that rewards aggression.

### Support — “Drink some water, bestie.”

- **Identity:** Walking comfort zone that keeps the squad upright.
- **Vibe:** Gentle, nurturing, stubbornly positive.
- **Standout Unlocks:**
  - Regeneration aura around Level 3 that blankets the squad.
  - Potion duration extension and sharing mid levels.
  - Cleanses and emergency heals in the 20s.
- **Best When:** You’re in long expeditions, co-op sessions, or leaning on
  potion-heavy builds.

### Scout — “I know a shortcut.”

- **Identity:** Exploration specialist with pockets full of trinkets.
- **Vibe:** Curious, chatty, always wandering ahead.
- **Standout Unlocks:**
  - Glowing tags for nearby hostiles.
  - Loot magnetism and XP orb attraction while you roam.
  - Ore pings and structure whispers at higher ranks.
- **Best When:** You’re spelunking, map-filling, or chasing loot tables.

### Skyrider — “Falling is canceled.”

- **Identity:** Mobility powerhouse that fights gravity on your behalf.
- **Vibe:** Airy, daring, eternally moving.
- **Standout Unlocks:**
  - Fall damage softeners from Level 1 onward.
  - Air-launch counterattacks and crowd control around Level 12.
  - Burst levitation or slow-fall windows near the capstone.
- **Best When:** You lack an elytra, build vertically, or weaponize
  knockback.

### Enchantment-Bound — “Your tools remember more.”

- **Identity:** Economic enabler that whispers to gear.
- **Vibe:** Soft hum of lapis dust and tinkering benches.
- **Standout Unlocks:**
  - Efficiency and haste bursts for your tools.
  - Fortune/Looting nudges and better repair costs midgame.
  - Durability preservation chances once tributes are paid.
- **Best When:** You grind resources, maintain a base, or live in the
  enchanting table UI.

### Cursed One — “We burn bright or not at all.”

- **Identity:** Soul-bound damage monster with lethal strings attached.
- **Vibe:** Dramatic, intense, unflinchingly loyal.
- **Standout Unlocks:**
  - Massive stat spikes the moment the role is assigned.
  - Owner death = pet death. No revival, no second chances.
  - Chaotic combat procs at higher ranks.
- **Best When:** You’re playing hardcore, living dangerously, or telling
  a tragic story on purpose.

### Eepy Eeper — “Nap time is sacred.”

- **Identity:** Rest-focused aura buddy that rewards slowing down.
- **Vibe:** Cozy blanket, campfire crackle, gentle purring.
- **Standout Unlocks:**
  - Resting near the pet creates healing zones early on.
  - Sleep amplifiers top off hunger and regen overnight.
  - Downtime buffs reduce hunger drain while idle.
- **Best When:** You’re base-building, roleplaying slice-of-life, or need
  calm between adventures.

### Eclipsed — “Mark them for the void.”

- **Identity:** Debuff artist infused with ender magic.
- **Vibe:** Whispered secrets, violet sparks, calculated strikes.
- **Standout Unlocks:**
  - Branding marks that make targets take bonus damage from everyone.
  - Detection of invisible or hidden mobs while scouting ahead.
  - Portable ender chest access and void-aligned debuffs late game.
- **Best When:** You’re staging End expeditions, coordinating raids, or
  love battlefield control.

## Role Rewards and Breakpoints

Every role ships with a `level_rewards` list that maps straight onto the
[feature levels](leveling.md#role-rewards-and-feature-levels). Expect the
pattern below, but individual JSON definitions can remix specifics:

- **Level 1:** Identity passive(s) active immediately.
- **Level 3:** First active ability or aura (Rank I).
- **Level 7:** Second ability or Rank II upgrade.
- **Level 12:** Major utility spike.
- **Level 17:** Rank III tier or fresh mechanic.
- **Level 23:** High-tier upgrade or support effect.
- **Level 27:** Pre-tribute capstone prep.
- **Level 30:** Post-tribute ultimate ability.

Switching roles wipes this progress, but tributes already paid stay
consumed—choose carefully.

## Stacking Roles in a Pack

Mixing specializations multiplies options:

- **Guardian + Support + Striker** covers defense, sustain, and burst.
- **Scout + Eclipsed** hunts treasure and neutralizes threats before they
  reach you.
- **Skyrider + Enchantment-Bound** keeps builders safe while accelerating
  block gathering.

Swap pets depending on mood, biome, or story beat. They remember
everything, so play the ensemble cast.

## Customizing Roles

- `passives` set up aura intervals, radius, and sit rules.
- `abilities` handle cooldowns, triggers, and rank scaling.
- `attributes` bias health, damage, speed, or armor to fit the fantasy.
- `xpCurve` defines tribute milestones and reward pacing.

Refresh the datapack to hot-swap adjustments. No code changes required—just
your imagination and a quick `/reload`.
