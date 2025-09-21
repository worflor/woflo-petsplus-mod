# Pets+ — Game Design Document (GDD)

You are the coding agent for **Pets+** (Fabric 1.21.x, server-only, vanilla client). Implement the following **pet-agnostic fun adapters and role riders** exactly as specified. All changes must be **event-driven** (no global ticking), **data-driven** (JSON abilities), and **backwards compatible** with the existing GDD. Keep perf tight: short **pet-local** schedulers only.

---

## 0) Plumbing & Schema

### 0.1 Triggers/flags (new or finalized)

Add these to the ability trigger system and condition checks:

* `require_perched` (pet perched on owner's shoulder)
* `only_if_perched` (effect guard)
* `require_mounted_owner` (owner is riding a mount; expose `owner.getVehicle()`)
* `only_if_mounted` (effect guard that targets the mount entity)
* `owner_in_combat` (boolean set true on owner on first hit/taken, auto-clear after 6s of no combat events)
* Synthetic triggers:

  * `after_pet_redirect` (fires when Guardian Bulwark redirects damage)
  * `after_pet_blink` (fires when Eclipsed/other teleport happens)
  * `after_owner_sprint_start` (fires once on sprint start, ICD 3s)
  * `owner_projectile_crit` (fires on owner crit with bow/crossbow/trident)
  * `owner_dealt_damage` (payload: victim, damage, victim hp% after)
  * `owner_begin_fall` (existing)
  * `on_combat_end` (owner flag transitions false)

### 0.2 New effect types / parameters

Implement JSON-driven effects below (all support `boss_safe`, `icd_ticks`, `expire_ticks` where relevant):

* `owner_next_attack_bonus`
  Fields: `bonus_damage_pct`, `vs_tag` (optional), `on_hit_effect` (mob effect payload), `expire_ticks`. Works for melee and projectiles. Cleared on hit or expiry.
* `tag_target`
  Fields: `key`, `duration_ticks`.
* `retarget_nearest_hostile`
  Fields: `radius`, `store_as` (target handle key). No global scan; use nearby entities from pet's chunk.
* `buff` with `target` ∈ {`owner`,`pet`,`victim`,`mount`} and guards `only_if_mounted`, `only_if_perched`.
* `projectile_dr_for_owner`
  Fields: `percent`, `duration_ticks`.
* `fall_reduction_near_owner` (existing) add optional `apply_to_mount: true`.
* Support aura cone bias: extend aura pulse to accept `forward_cone_bias: true` and `extra_radius`.
* Enchantment echo toggles: add gates for `perched_haste_bonus`, `mounted_extra_rolls`.

### 0.3 Data/Persistence

* Pet component: add cooldown map entries for new abilities and riders.
* Owner temp state: `{ nextAttackRider: {...}, inCombatUntilTick, recentPetRedirectTick, lastBlinkTick }`.

### 0.4 Config (extend default JSON)

Add these keys with suggested defaults:

```json
{
  "guardian": {
    "projectileDrOnRedirectPct": 0.10,
    "shieldBashIcdTicks": 120
  },
  "striker": {
    "ownerExecuteBonusPct": 0.10,
    "finisherMarkBonusPct": 0.20,
    "finisherMarkDurationTicks": 80
  },
  "support": {
    "perchSipDiscount": 0.20,
    "mountedConeExtraRadius": 2
  },
  "scout": {
    "lootWispDurationTicks": 80
  },
  "skyrider": {
    "ownerProjLevitateChance": 0.10,
    "ownerProjLevitateIcdTicks": 200
  },
  "eclipsed": {
    "markDurationTicks": 80,
    "ownerBonusVsMarkedPct": 0.10,
    "ownerNextHitEffect": "minecraft:wither",
    "ownerNextHitEffectDurationTicks": 40,
    "phaseChargeInternalCdTicks": 400,
    "phaseChargeBonusDamagePct": 0.25,
    "phaseChargeWindowTicks": 100,
    "perchPingIntervalTicks": 140,
    "perchPingRadius": 8,
    "eventHorizonDurationTicks": 100,
    "eventHorizonRadius": 6.0,
    "eventHorizonProjectileDrPct": 0.25,
    "edgeStepFallReductionPct": 0.25,
    "edgeStepCooldownTicks": 240
  },
  "enchantment_bound": {
    "perchedHasteBonusTicks": 10,
    "mountedExtraRollsEnabled": true
  },
  "cursed_one": {
    "doomEchoHealOnNextHitPct": 0.15,
    "doomEchoWeaknessDurationTicks": 60
  },
  "eepy_eeper": {
    "perchNapExtraRadius": 1.0
  }
}
```

---

## 1) Role Riders & Abilities

### 1.1 Guardian (pet-agnostic tank)

**Rider:** on **Bulwark** redirect, apply `projectile_dr_for_owner` using `guardian.projectileDrOnRedirectPct` for 40 ticks (ICD `guardian.shieldBashIcdTicks`). If owner is mounted, also `buff` mount with Knockback Resistance +0.5 for 40 ticks.

**Ability JSON `petsplus:shield_bash_rider`:**

```json
{
  "id":"petsplus:shield_bash_rider",
  "trigger":{"event":"after_pet_redirect","internal_cd_ticks":120},
  "effects":[
    {"type":"owner_next_attack_bonus","bonus_damage_pct":0.10,
     "on_hit_effect":{"type":"effect","target":"victim","id":"minecraft:weakness","duration":40,"amplifier":0},
     "expire_ticks":100}
  ]
}
```

### 1.2 Striker (execution without pet DPS)

**L10 Executioner fallback:** also grant **owner** `+{striker.ownerExecuteBonusPct}` vs targets under 35% HP if target has `recentDamagedByOwnerOrPet`.

**Ability JSON `petsplus:finisher_mark`:**

```json
{
  "id":"petsplus:finisher_mark",
  "trigger":{"event":"owner_dealt_damage","target_hp_pct_below":0.40,"cooldown_ticks":200},
  "effects":[
    {"type":"tag_target","key":"petsplus:finisher","duration_ticks":${striker.finisherMarkDurationTicks}},
    {"type":"owner_next_attack_bonus","vs_tag":"petsplus:finisher","bonus_damage_pct":${striker.finisherMarkBonusPct},
     "on_hit_effect":{"type":"effect","target":"victim","id":"minecraft:slowness","duration":40,"amplifier":0},
     "expire_ticks":100},
    {"type":"buff","target":"mount","id":"minecraft:speed","duration":40,"amplifier":0,"only_if_mounted":true}
  ]
}
```

### 1.3 Support (QoL, still pet-agnostic)

* While `require_perched`, reduce potion sip per pulse by `support.perchSipDiscount`.
* While `require_mounted_owner`, aura pulses use `forward_cone_bias: true` and radius `+ support.mountedConeExtraRadius`.

### 1.4 Scout (info & mobility for everyone)

**L10 Spotter fallback:** if no `on_pet_attack` within 60 ticks after combat start, apply **Glowing 1s** to the **next target hit by owner** (ICD 15s shared).
**L20 Gale Pace:** if owner mounted, apply the speed scalar to the **mount** instead of owner.
**Ability JSON `petsplus:loot_wisp`:**

```json
{
  "id":"petsplus:loot_wisp",
  "trigger":{"event":"on_combat_end","cooldown_ticks":200},
  "effects":[
    {"type":"magnetize_drops_and_xp","radius":12,"duration_ticks":${scout.lootWispDurationTicks}}
  ]
}
```

### 1.5 Skyrider (air control without pet hits)

**L20 fallback:** on `owner_projectile_crit`, `chance = skyrider.ownerProjLevitateChance` to apply **Levitation I (10–20 ticks)** (boss-safe), ICD `skyrider.ownerProjLevitateIcdTicks`.

**Ability JSON `petsplus:windlash_rider`:**

```json
{
  "id":"petsplus:windlash_rider",
  "trigger":{"event":"owner_begin_fall","min_fall":3,"cooldown_ticks":120},
  "effects":[
    {"type":"buff","target":"owner","id":"minecraft:jump_boost","duration":40,"amplifier":0},
    {"type":"owner_next_attack_bonus",
     "on_hit_effect":{"type":"knockup","target":"victim","strength":0.35},
     "expire_ticks":100}
  ]
}
```

**Skybond aura update:** its `fall_reduction_near_owner` gains `"apply_to_mount": true`.

### 1.6 Enchantment-Bound (owner-centric echoes)

* If `require_perched`, add `enchantment_bound.perchedHasteBonusTicks` to Haste pulse duration.
* If owner is mounted and `enchantment_bound.mountedExtraRollsEnabled`, allow extra-roll path to include kills of **lassoed mobs by owner** (respect whitelist) and optionally **trampled crops** (behind config toggle).

### 1.7 Cursed One (risk fantasy on any pet)

**Ability JSON `petsplus:doom_echo`:**

```json
{
  "id":"petsplus:doom_echo",
  "trigger":{"event":"on_owner_low_health","owner_hp_pct_below":0.35,"internal_cd_ticks":400},
  "effects":[
    {"type":"area_effect","radius":5,"effects":[{"id":"minecraft:weakness","duration":60,"amplifier":0}]},
    {"type":"owner_next_attack_bonus","bonus_damage_pct":0.00,
     "on_hit_effect":{"type":"heal_owner_flat_pct","value":${cursed_one.doomEchoHealOnNextHitPct}},
     "expire_ticks":100},
    {"type":"effect","target":"owner","id":"minecraft:nausea","duration":20,"amplifier":0}
  ]
}
```

On **auto-resurrect**, also `buff` the **mount** with **Resistance I (60 ticks)** if mounted.

### 1.8 Eepy Eeper (cozy everywhere)

**Nap Time:** when **sitting** or **perched**, increase regen radius by `eepy_eeper.perchNapExtraRadius`.

### 1.9 Eclipsed (owner/perch loop; integrate fully)

Add the three abilities and riders:

**`petsplus:voidbrand`**

```json
{
  "id":"petsplus:voidbrand",
  "trigger":{"event":"aggro_acquired","cooldown_ticks":300},
  "effects":[
    {"type":"tag_target","key":"petsplus:voidbrand","duration_ticks":${eclipsed.markDurationTicks}},
    {"type":"owner_next_attack_bonus","vs_tag":"petsplus:voidbrand","bonus_damage_pct":${eclipsed.ownerBonusVsMarkedPct},
     "on_hit_effect":{"type":"effect","target":"victim","id":"${eclipsed.ownerNextHitEffect}","duration":${eclipsed.ownerNextHitEffectDurationTicks},"amplifier":0},
     "expire_ticks":100}
  ]
}
```

**`petsplus:phase_partner`**

```json
{
  "id":"petsplus:phase_partner",
  "trigger":{"event":"after_pet_blink","internal_cd_ticks":${eclipsed.phaseChargeInternalCdTicks}},
  "effects":[
    {"type":"buff","target":"owner","id":"minecraft:speed","duration":40,"amplifier":0,"only_if_perched":true},
    {"type":"owner_next_attack_bonus","bonus_damage_pct":${eclipsed.phaseChargeBonusDamagePct},
     "on_hit_effect":{"type":"effect","target":"victim","id":"minecraft:slowness","duration":40,"amplifier":0},
     "expire_ticks":${eclipsed.phaseChargeWindowTicks}}
  ]
}
```

**`petsplus:perch_ping`**

```json
{
  "id":"petsplus:perch_ping",
  "trigger":{"event":"interval_while_active","ticks":${eclipsed.perchPingIntervalTicks},"require_perched":true,"require_in_combat":true},
  "effects":[
    {"type":"retarget_nearest_hostile","radius":${eclipsed.perchPingRadius},"store_as":"pp_target"},
    {"type":"effect","target":"pp_target","id":"minecraft:darkness","duration":10,"amplifier":0,"boss_safe":true},
    {"type":"tag_target","target":"pp_target","key":"petsplus:voidbrand","duration_ticks":60}
  ]
}
```

**Abyss Recall landing rider (Event Horizon):** on successful void save, spawn a 5s zone (radius `eclipsed.eventHorizonRadius`) applying **Slowness I** to mobs and `projectile_dr_for_owner` with `eclipsed.eventHorizonProjectileDrPct`.

**Edge Step:** while `require_perched`, on `owner_begin_fall` (>3 blocks) apply `edgeStepFallReductionPct` fall damage reduction to owner (ICD `edgeStepCooldownTicks`).

---

## 2) Role JSON integration

* Append new abilities to each role's `abilities` pool and unlock via **feature levels** (3/7/12/17/23/27) without removing existing ones.
* Add rider behavior to **milestones** via hidden abilities (e.g., Guardian Bulwark rider attached to L10).
* Ensure all new effects respect `#petsplus:cc_resistant` and boss-safe rules.

---

## 3) Commands/UI/Feedback

* Action bar lines (reuse localization keys or add):

  * Guardian: "{0} holds the line. (+Proj DR)"
  * Striker: "{0} marked a finisher."
  * Scout: "{0} guides the spoils."
  * Skyrider: "Wind at your back."
  * Eclipsed: "{0} tears a seam in sight." / "Space folds politely around you."
  * Cursed: "A bad feeling spills from {0}…"
* No new UI screens; use existing chat hover and bossbar pulses.

---

## 4) Testing (must pass)

* Parrot in each role (never attacks): verify every role grants **owner-visible** power (next-hit riders, auras, fall/mark effects).
* Mounted owner (horse/camel/llama): verify mount-specific expressions (speed, KB-res, fall DR, Resistance on Cursed rez).
* Boss safety: Warden/Dragon/Wither—CC short and/or resisted; no permanent disables.
* Perf: 30 pets in raid arena; no global scans; confirm intervals are pet-local and bounded.

---

## 5) Files & Touchpoints

* Java:

  * `abilities/trigger/*.java` (new triggers)
  * `abilities/effects/*.java` (new effects)
  * `combat/OwnerNextAttackRider.java` (melee+projectile hooks)
  * `state/OwnerCombatState.java` (inCombat)
  * Patch Guardian redirect, Eclipsed blink, Skyrider fall hooks to emit synthetic triggers.
* Data (datapack):

  * `data/petsplus/roles/*` (append abilities)
  * `data/petsplus/abilities/*.json` (new abilities listed above)
  * `data/petsplus/tags/entity_types/cc_resistant.json` (already present, ensure used)
* Config: extend default as in §0.4; all values overridable.

Deliverables are merged, compiling, and feature-flagged by config; existing saves remain valid.
