# Leveling and Progression

Leveling is the bond meter between you and your pet. Every glimmer of XP
you earn spills outward, powering the abilities linked to their chosen
[role](roles.md). Shared effort, shared glow-up.

## The Shared XP Loop

If you earn XP, every nearby pet does too. Combat, mining, smelting,
breeding, fishing, trading, advancements—if it fills your bar, it echoes
through the pack as long as they’re close enough. Stack it with mood boosts
from the [emotions loop](emotions.md) or smart [stimulus setups](stimulus.md)
to keep everyone in motion.

- **Range:** 32 blocks from you.
- **Split:** XP divides evenly across owned pets inside that radius.
- **Flavor:** After the split, each pet nudges their share based on level
  scaling, their innate learning lean, and whether they were actually
  helping.

Roll with the whole crew for vibes or stick to a tight duo for thicker
slices.

### Level Scaling Tiers

Pets climb from level 1 to 30. Early levels rocket so new bonds come
online quickly; late levels slow down to respect endgame toys.

| Level Band | Multiplier | Notes |
|------------|------------|-------|
| 1–5        | 160%       | Hyper-growth while the bond forms |
| 6–10       | 130%       | Still fast, still forgiving |
| 11–15      | 110%       | Smoothing into midgame |
| 16–20      | 100%       | One-to-one with player XP |
| 21–25      | 80%        | Slow climb toward capstone unlocks |
| 26–30      | 70%        | Endgame grind for ultimate abilities |

### Participation & Learning

Each pet keeps tabs on what they were doing when XP rained in:

- **Participation:** If they haven’t lifted a paw in five minutes, they
  get a small penalty. Active helpers keep full credit, especially when you
  match their current [mood](moods.md) to the activity.
- **Learning:** Their [nature](natures.md) and baked-in quirks tilt how
  fast they learn. Some are prodigies, some are professional nappers.

## The Curve Behind the Scenes

XP needed per level ramps up on a gentle slope. Those same feature
breakpoints—levels **3, 7, 12, 17, 23, and 27**—layer extra spice on top.
Each [role](roles.md#role-rewards-and-breakpoints) carries its own
`xpCurve`, so a Guardian’s grind may feel different from an Eepy Eeper’s
nap-filled journey. Datapack makers can swap those numbers without
touching code, letting you align progression with a pet’s [nature](natures.md)
or pack story.

## Tribute Gates

Three ritual checkpoints force you to prove the bond before pressing on:

| Tribute Level | Default Offering | What It Means |
|---------------|-----------------|---------------|
| 10            | Gold ingot       | “We’re committed.” |
| 20            | Diamond          | “We’ve been through things.” |
| 30            | Netherite scrap  | “We’re in this forever.” |

When a pet hits a tribute level, XP gain pauses. Sneak + right-click the
offering to prove the bond and resume leveling. Each role can override
these milestones in its datapack JSON.

## Role Rewards and Feature Levels

Levels aren’t just numbers. Every role packs a reward table that snaps into place at feature levels:

- **Level 1:** Passive identity arrives the moment you assign the role.
- **Level 3:** First active ability or aura.
- **Level 7:** Second ability or Rank II upgrade.
- **Level 12:** Midgame spike—think Support’s wider aura or Striker’s kill momentum.
- **Level 17:** Rank III tier or a fresh trick.
- **Level 23:** High-tier upgrade, often pack-defining utility.
- **Level 27:** Final polish before the last tribute.
- **Level 30:** Post-tribute ultimate that redefines the kit.

See the [Roles overview](roles.md#the-nine-roles) for what each
specialization unlocks along the way.

## Tuning the Flow

Want to remix progression? The global config adjusts how hard XP hits,
while each role JSON controls scaling, tributes, and reward timing. Nudge
a few numbers and you’ve got a whole new leveling story that still honors
your preferred [design philosophy](design_philosophy.md).

## Death & Recovery

Losing a pet is brutal. They drop a **Proof of Existence** memorial and
every level, ability, and tribute disappears with them. If you revive
them through other mods or miracles, the leveling climb restarts from
scratch.

## Config Hooks

Server owners and modpack creators can redefine the whole loop through
datapacks and configs—slow-burn RPG arcs, hyper-fast arenas, whatever
fits your world. Every level should feel like a shared story between you
and your companion, especially when paired with customized [roles](roles.md)
and mood-aware [emotions](emotions.md).
