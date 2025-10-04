# Leveling and Progression

Pets level from 1 to 30 by sharing owner XP. Milestone tributes gate major unlocks.

## Experience Sharing

Pets gain XP when their owner does, using the same sources as player XP (kills, mining, smelting, breeding, fishing, trading, advancements).

**Distribution:**
- Owner's XP is **split evenly** among all owned pets within **32 blocks**
- Each pet's share is then modified by level scaling, characteristics, and participation
- More pets in range = less XP per pet

**Level scaling modifiers:**
- Levels 1–5: 160% (fast early bonding)
- Levels 6–10: 130%
- Levels 11–15: 110%
- Levels 16–20: 100% (1:1 with player)
- Levels 21–25: 80%
- Levels 26–30: 70% (endgame grind)

## The Level Curve

XP requirement per level uses the formula:
```
XP = (level - 1) × base_linear + (level - 1)² × quadratic_factor
```

Feature levels (3, 7, 12, 17, 23, 27) apply a bonus multiplier to their requirement. Exact curve varies by role's `xpCurve` definition.

## Feature Levels

**Standard feature levels:** 3, 7, 12, 17, 23, 27

These levels unlock abilities or ability ranks. Exact rewards depend on the role's `level_rewards` configuration.

## Tribute Gates

Tribute levels (default: 10, 20, 30) pause XP gain until you pay tribute. Sneak + right-click the pet with the required item to unlock.

Tribute items and levels are defined per role in `xpCurve.tributeMilestones`.

## Level Rewards

Roles define `level_rewards` that grant rewards at specific levels. Common reward types:
- `ability_unlock` — unlock an ability
- `stat_boost` — increase base stats
- `tribute_required` — gate progression with tribute

Exact rewards are role-specific and datapack-configurable.

## XP Modifiers

Final XP gain formula:
```
pet_xp = owner_xp × config_modifier × level_scale × learning_modifier × participation_modifier
```

**Participation modifier** factors:
- Recent combat activity (player or pet)
- AFK detection (no input for 5 minutes reduces gains)

**Learning modifier** from characteristics (trait-based XP multiplier).

## Death

Dead pets lose all XP, levels, and abilities. They drop a **Proof of Existence** memorial item.
