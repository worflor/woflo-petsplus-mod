# Pets+ — Game Design Document (GDD)

> Server-only Fabric mod that deepens tamed pets via an XP-bond system. Vanilla clients only (no client mod), no new textures or models, fully data-driven roles.

---

## 1) Goals & Constraints

### Goals

* Make **tamed pets** meaningfully useful in combat, trials, bosses, and exploration.
* **Nonlinear** progression with engaging micro-rewards at every level.
* **Role archetypes** that are pet-agnostic (wolves, cats, parrots, llamas, camels, horses, modded tames).
* Minimal micromanagement; clear, readable feedback.
* Feels native: passive, intuitive — you forget it’s a mod until it’s gone.

### Hard Constraints

* **Server-only**: works on vanilla clients (no custom UI).
* **No new visuals**: use existing items, particles, sounds, armor/collars/banners.
* **Data-driven**: roles and abilities defined via JSON/datapack; no species hardcoding.
* **Low overhead**: avoid per-tick global scans; rely on event hooks and short, pet-local schedulers.

### High-Stakes Identity

* Exception (Cursed One): retains bond on its own death and auto-resurrects; if the owner dies, the pet is destroyed permanently (final curse pulse).

* **Pet death = total bond loss** (progress tied to that specific entity). Configurable “downed state” optional for casual servers.

---

## 2) Core Loop

1. **Player gains XP** (combat, mining, etc.).
2. Nearby owned, eligible pets **siphon a share** as **Pet XP**.
3. Pets level **1→30** (auto).
4. At **10 / 20 / 30**, progression **pauses** until the owner pays a **tribute** by throwing the required item near the pet (or sneak-use on the pet):

   * **Level 10** → **Gold Ingot**
   * **Level 20** → **Diamond**
   * **Level 30** → **Netherite Ingot**
5. Level grants: small stat bumps every level, **ability slot** unlocks at select levels, **role milestone** perks at 10/20/30.

---

## 3) Roles (Archetypes)

> Any tameable (including modded) may take any role. Expression differs naturally (e.g., melee vs flying pathing), but mechanics are shared.

### 3.1 Guardian (Tank / Protection)

* **Theme**: Damage interception, protective pulses, stability.
* **Baseline**: +Knockback Resistance scalar, modest Max HP scalar.
* **Current Kit**

  * **Bulwark (passive, cooldown)**: The pet redirects a portion of incoming damage from the owner to itself, then primes the owner's next attack with **Guardian's Blessing**—a reliable counterattack rider that inflicts Weakness on the foe and surges the owner with a heartbeat of Strength. The blessing fires whether you're on foot or mounted, but steadies a mount with a brief resistance pulse when you're in the saddle, alongside the usual projectile DR hooks.
  * **Feature unlock**: On successful redirect, the owner's next attack gains a small damage bonus and rider effect (short expiry); Guardian's Blessing now tracks through the owner's combat state so the empowered swing sticks until it's actually thrown.

### 3.2 Striker (Burst / Execution)

* **Theme**: Pick targets, finish them fast.
* **Baseline**: +Attack Damage/Speed scalars (tunable caps).
* **Current Kit**

  * **Execution (owner-based, pet-agnostic)**: With a Striker nearby, once a foe you've recently tagged dips under the execute threshold (default 35% HP), your follow-up strike becomes a true finisher and slays them outright (boss-safe). Chaining executions within ~3s now ramps an "execution momentum" buff — every finisher adds roughly +2% (slightly stronger as your Striker levels) to the threshold, refreshing the window, yet hard-capped at 45%. Let it lapse and the buff bleeds off a stack at a time instead of dropping to zero, giving you a short grace period. Successful streaks spark a faint soul-flame flourish and a soft sweep hit that climbs in pitch with your momentum for immersive but unobtrusive feedback.
  * **L7 – Finisher Mark**: Hitting a target under a threshold can tag it, empowering the owner's next attack greatly vs that target and applying a short slow on hit. Brief mount pep when mounted.

### 3.3 Support (Potion Aura / Safeguard)

* **Baseline – Potion Carrier (diluted)**:

  * Load **one** vanilla potion into the pet (right-click).
  * Cleanse with a **milk bucket** to purge the stored brew (consumes it and frees the slot).
  * Emits **aura pulses** to **owner + pet** (optionally teammates via config) at **reduced potency**, short durations, **every \~7s** while near (no constant ticking).
* **Consumption**: the stored potion is broken into **8 baseline charges for a standard 3m brew (1 charge consumed per pulse)**, scaling linearly with the potion's actual duration and the pet's accumulated **aura efficiency** bonuses.
  * **Perched sip math**: the L5 perk applies a **20% sip discount** (configurable), so each pulse only spends **0.8 charge** while perched (≈10 pulses per bottle at defaults).
  * **Why use this vs drinking?** Hands-free, persistent coverage, shared with pet, minimal micromanagement; weaker than drinking but **longer-lived**.
* **Current Kit**

  * **L5 – Perch Potion Efficiency**: While perched, the pet “sips” less per pulse, extending potion lifespan.
  * **L15 – Mounted Cone Aura**: From the owner's mount, projects a short cone aura that shares the stored potion briefly.

> No periodic heal-on-kill loop. No duration refresh gimmicks. Numbers configurable.

### 3.4 Scout (Detection / Mobility)

* **Theme**: Information advantage, positioning.
* **Baseline**: +Move Speed scalar; occasional **Glowing** ping on nearby hostiles at combat start.
* **Current Kit**

  * **L3 – Loot Wisp**: After combat ends, nearby drops and XP within a short radius gently drift toward the owner for a moment.

### 3.5 Skyrider (Air Control / Fall Safety)

* **Theme**: Updrafts, levitation disruption, fall mastery.
* **Baseline**: Reduced fall damage for pet; small chance to “gust dodge” on hit (minor knockback escape with a short internal cooldown).
* **Current Kit**

  * **L7 – Windlash Rider**: When the owner begins a fall, grant a brief jump boost and empower the owner's next attack with a small knock‑up on hit (short window).

### 3.6 Enchantment‑Bound (Arcana / Enchants)

* Theme: Your gear hums with the pet’s bond, echoing enchantments and surging briefly when the runes align.
* Baseline: Owner‑centric, pet‑agnostic. Triggers from your actions; no pet attacks required.
* Current Kit

  * Mining Echo (Haste pulse): Breaking blocks near your Enchantment‑Bound pet grants a brief Haste burst. Duration scales with config; synergizes heavily while Arcane Focus is active.
  * Extra Drop Echo (blocks): On block breaks, there’s a tiny chance to duplicate the drops once; Fortune slightly improves the odds. Tuned for subtle value over time.
  * Unbreaking Echo (durability): Small chance to prevent 1 point of durability loss on your current tool when mining; this chance doubles during a Mining Focus surge.
  * Swim Echo (flow): While swimming with Aqua Affinity or Depth Strider equipped, you periodically gain a short Dolphin’s Grace. During Swim Focus, the grace re‑arms more reliably.
  * Arcane Focus (L20+, surges; +1 charge at L30): Contextual “focus buckets” (Combat, Mining, Swim) can briefly surge, doubling that bucket’s echo potency/rate for a short window. Focus uses shared charges with a cooldown. Unlocks at L20 with 1 charge; at L30, you can hold 2 charges. Charges replenish after cooldown.

> Notes: Focus is automatic and non‑intrusive; you’ll hear a soft enchantment table chime when it surges. All numbers are configurable.

### 3.7 Cursed One (Deathbound / Risk–Reward)

* **Theme**: Life‑bound pet that thrives on dying and returning. Powerful but unstable — your death destroys it permanently.
* **Baseline**

  * **Bound to owner**: cannot sit or be transferred.
  * **Owner death**: pet vanishes permanently (final curse pulse).
  * **Pet death**: emits a curse pulse (**Wither I + Weakness I, 5s**) to mobs in radius; **25%** chance of a minor debuff to owner (configurable).
  * **Auto‑resurrects** near owner after ~**15s**.
* **Current Kit**

  * **Owner save (sacrifice)**: When the owner would die, a nearby Cursed pet can prevent death and apply totem‑like boons with cursed side effects, sacrificing itself in the process (chance scales with pet level).
  * **Self‑resurrection (immortality, cd)**: From mid‑levels onward, the pet refuses ordinary death and instead resurges with brief boons/drawbacks; large cooldown gates repeats. If the owner dies, all Cursed pets perish.
  * **L12 – Doom Echo**: On low owner health, emits a short Weakness pulse to nearby hostiles and primes a small heal on the owner's next hit.

<!-- Eepy Eeper: not present in current build; section intentionally omitted to match shipped code. -->

### 3.8 Eclipsed (Void/End / Disruption)

* **Theme**: Darkness, teleport flickers, void rescue.
* **Baseline**: Small chance to **short-teleport** when damaged; on first aggro, nearby hostiles get **Darkness** micro-pulse (boss-safe).
* **Current Kit**

  * **L17 – Voidbrand**: When combat begins, mark a foe and prime the owner's next attack with a strong bonus vs marked plus a brief slow on hit.
  * **L23 – Phase Partner (perched)**: While perched, periodically grants the owner a short speed boon and a powerful next‑attack rider.
  * **L27 – Perch Ping (perched, in combat)**: Periodically pings the nearest hostile, applying a brief Darkness and re‑marking for the voidbrand synergy (boss‑safe tuning).

---

## 4) Leveling & Progression (1→30)

### 4.1 XP Intake

* Trigger only on **player XP gain events** (kills, smelting, ores, etc.).
* **Share**: default **12%** of gained XP converted to **Pet XP**, split among eligible owned pets in range (**≤12 blocks**; config).
* **Participation bonus**: +X% if pet or owner tagged the killed mob recently.
* **Farm dampening**: reduced share if mob wasn’t targeting a player recently or lacks “recent player damage” tag.

### 4.2 Tribute Gates

* On level-up reaching **10 / 20 / 30**, set `gate_pending` and **pause further level progression** (XP can bank or be discarded per config).
* Tribute detection:

  * Preferred: **sneak-use** required item on the pet (zero scan).
  * Alt: **throw item** near pet; while `gate_pending`, run a **short, pet-local check** every \~10–20 ticks for ≤6s. Consume 1 item on match.
* UI: action bar & brief bossbar **“Awaiting Tribute: Gold/Diamond/Netherite”**.

### 4.3 Per-Level Rewards (nonlinear, engaging)

* **Every level grants something**. Suggested cadence (configurable):

  * **Odd levels**: +1% **Role Scalar** (e.g., Guardian DR, Striker offense, Support aura efficiency, Scout detection, Skyrider mobility, Eclipsed disruption, Enchantment‑Bound echo strength, Cursed One malediction potency/radius, Eepy Eeper slumber potency/radius).
  * **Even levels**: alternating **+0.5 hearts Max HP** (cap) and **+2% Move Speed** (cap).
  * **Feature levels** (e.g., **3, 7, 12, 17, 23, 27**): unlock/upgrade an **Ability Slot** (choose from role’s ability list; branching).
  * **Milestones**: **10/20/30** (tribute; major perk as listed in Roles).

### 4.4 Level Curve

* Smooth early, steeper late. Default formula (configurable):

  * `requiredPetXP(L) = base * L^1.8 + step * L` with `base=20`, `step=10`.
* Soft cap tools:

  * Per-minute Pet XP cap; diminishing returns if not in combat.

---

## 5) Abilities Framework (Data-Driven)

### 5.1 Triggers (examples)

* `on_owner_xp_gain`, `on_owner_begin_fall`, `on_owner_low_health`, `on_owner_kill`
* `on_pet_attack`, `on_pet_crit`, `on_pet_hurt`, `on_combat_start`
* `on_aggro_acquired`, `on_void_fall_detected`
* `on_owner_block_break`, `on_owner_tool_durability_loss`, `on_owner_swim_start`
* `on_pet_death`, `on_pet_resurrect`, `on_owner_death`
* `on_pet_destroyed` (permanent; not downed/resurrectable)
* `on_owner_sleep_start`, `on_owner_sleep_complete`, `on_owner_respawn_anchor_use`, `on_owner_respawn`
* `on_pet_sit_toggle`, `on_phantom_target_attempt`
* `interval_while_active` (pet-local scheduler; never global)

### 5.2 Effects (examples)

* Apply/remove **Mob Effects** (duration/amplifier)
* Modify **Attributes** (bundled AttributeModifiers)
* **Teleport** short range; **reduce fall damage** within radius
* **Redirect damage** (capped per hit)
* **Cleanse** N negative effects
* **Magnetize** XP orbs/drops (soft drift, time-boxed)
* **Extra drop roll** on whitelist (`extra_drop_roll_whitelist` for Fortune/Looting echoes)
* **Prevent durability loss chance** (Unbreaking echo)
* **Surge bucket scalar** for Arcane Focus (mining/combat/swim)
* **Resurrect pet after delay**; **area curse pulse** with status effects
* **Owner damage bonus while alive** (Binding Pact)
* **Set owner hunger to full**; **grant Saturation**; **heal pet to full** on sleep
* **Prevent Phantom targeting** the owner while pet lives (toggle)
* **Teleport owner to respawn point**; **cancel lethal damage**; **apply Blindness**; **remove owner XP**
* **Knockout pet until N sleep cycles** (unavailable until condition met)

### 5.3 Ability Slots

* Each role exposes an **ability pool**. At Feature levels, the owner selects one to slot or upgrade (chat UI).
* Selections are **stored** per pet; can **respec** with a tagged item (e.g., Paper carrying a data component).

---

## 6) Data & Persistence

### 6.1 Pet Component (per-entity)

```json
{
  "PetsPlus": {
    "level": 18,
    "xp": 1425,
    "gate_pending": "none|gold|diamond|netherite",
    "role": "petsplus:striker",
    "ability_slots": [
      {"slot":0,"id":"petsplus:momentum","rank":2}
    ],
    "scalars": {"offense":0.12,"defense":0.05,"aura":0.08,"mobility":0.04,"disruption":0.03,"echo":0.04,"curse":0.04,"slumber":0.04},
    "cooldowns": {
      "safeguard_day": 1234567,
      "overrun": 0
    }
  }
}
```

### 6.2 Attribute Bundle

* One **stable UUID bundle** per pet, recalculated only on:

  * level-up, role change, slot change, tribute unlock, pet tame/sit toggle (if needed).

### 6.3 Player Component (per-owner)

```json
{
  "PetsPlusPlayer": {
    "best_friend_foreverer_pet_uuid": "00000000-0000-0000-0000-000000000000", 
    "best_friend_foreverer_awarded_ts": 0,
    "or_not_awarded": false
  }
}
```

* Set `best_friend_foreverer_pet_uuid` the first time the player earns "Even Bester Friends Foreverer" (persist forever; do not overwrite on later L30 pets).
* Store `best_friend_foreverer_awarded_ts` (world time or epoch) if you need analytics/cooldowns; primarily acts as provenance metadata.
* Use `or_not_awarded` as a one-time flag so "Or not." cannot trigger twice per player (or leak across respawns/backups).
* Award "Or not." when the tracked pet is permanently destroyed:
  * Non-Cursed pets: on normal death.
  * Cursed One: only when the owner dies and the pet is permanently destroyed.
* Never award on temporary/downed states or ordinary Cursed One self-deaths (auto-resurrect cases).

---

## 7) JSON Role Schema (Datapack-Extendable)

```json
{
  "role_id": "petsplus:skyrider",
  "display_name": "Skyrider",
  "default_scalars_per_level": {"mobility": 0.01},
  "feature_levels": [3,7,12,17,23,27],
  "milestones": {
    "10": [{"type":"grant_ability","id":"petsplus:updraft"}],
    "20": [{"type":"grant_ability","id":"petsplus:levitate_pulse"}],
    "30": [{"type":"grant_ability","id":"petsplus:skybond"}]
  },
  "abilities": [
    {
      "id": "petsplus:updraft",
      "trigger": {"event":"owner_begin_fall","min_fall":3,"cooldown_ticks":240},
      "effects": [{"type":"effect","target":"owner","id":"minecraft:slow_falling","duration":40,"amplifier":0}]
    },
    {
      "id": "petsplus:levitate_pulse",
      "trigger": {"event":"pet_crit","chance":0.25},
      "effects": [{"type":"effect","target":"victim","id":"minecraft:levitation","duration":20,"amplifier":0}]
    },
    {
      "id": "petsplus:skybond",
      "trigger": {"event":"passive"},
      "effects": [
        {"type":"flag","key":"no_fall_damage_pet","value":true},
        {"type":"fall_reduction_near_owner","percent":0.5,"radius":8}
      ]
    }
  ]
}
```

> Example schema for datapack integration. The current build registers abilities in code; datapack loading is planned/compatible.

---

## 8) UI & UX (Vanilla-Client)

* **Action Bar**: short, frequent feedback (XP gain, procs, cooldowns).
* **Chat (clickable)**: `/pets` opens text menu to set role, pick abilities, view stats, link tribute hints. Hover tooltips describe effects/cds.
* **Bossbar (transient)**: level milestone banner (2–3s).
* **Particles/Sounds**: use vanilla (hearts, crit, end-rod, sculk, villager happy; wolf howl, cat purr, chorus pop, elytra whoosh).
* **Collar-aware particles**: where supported (e.g., dust), tint particles to the pet’s collar dye.
* **Color coding**: suggest collar/banner dye to indicate role (optional).

### 8.1 Personality & Immersion Touches

* **Quippy action bar lines** that stay readable: “{Pet} has your back (+Res I)” or “{Pet} spotted trouble (Glowing)”.
* **Role banter**: Guardian damage soak flashes “The truest form of Sacrilege—holding the line.” Support crit heal plays “Mmmm healing magic!”; upgrade confirmations can wink “Is this designer? Absolutely.”
* **Subtle emotes**: happy purr/bark + small heart burst on level-up; brief whine when low HP triggers a safeguard.
* **Context hints**: when gated, a short bossbar + action bar “Awaiting Tribute: {Gold/Diamond/Netherite}”.
* **Follow feel**: smooth follow distance and gentle owner-teleport to avoid pop-in; pets auto-sit when you open a container and stand when you leave.
* **Ambient tells**: soft particles when auras pulse; brief dust trail when Scout speed bonus engages; arcane glint swirl when Arcane Focus surges; ash/smoke wisp on Cursed One death/resurrection; sleepy 'Z' wisps when Nap Time aura is active.
* **Accessibility**: concise chat summaries with hover tooltips for numbers; no spam, capped frequency.

---

## 9) Commands & Permissions

* `/pets` – open/manage UI for owned pets nearby.
* `/pets role <petName|uuid> <roleId>` – change role (consumes respec token if configured).
* `/pets info <pet>` – view bond, level, cooldowns, abilities.
* `/pets tribute <pet>` – reiterate current tribute requirement.
* **Permissions** (LuckPerms ready):

  * `petsplus.use` (base)
  * `petsplus.manage` (role/ability changes)
  * `petsplus.admin` (bypass tribute/flags; debug)

---

## 10) Balance & Anti-Exploit

* **XP Intake Rules**

  * Full Pet XP only if mob **recently targeted a player** or **tagged by owner/pet damage**.
  * Otherwise **reduced** (config scalar).
  * Per-minute Pet XP **cap** per pet; soft DR if cap exceeded.

* **Boss Safety**

  * CC durations (Levitation, Darkness, Slowness) **short** and/or **reduced vs boss-tagged** entities.
  * Immunity list via tags for boss/projectile entities.
  * Tag bosses that should shrug CC with `#petsplus:cc_resistant`.

* **Tributes**

  * Item consumption is **exactly one**; confirm in chat with pet name & level.
  * Tribute unlock affects **only that pet**.

* **Death Penalty**

  * Pet death **wipes bond**; optional “downed state” (immobile, feed within 15s) toggle in config.

---

## 11) Default Config (Suggested)

```json
{
  "xpShareBase": 0.12,
  "xpShareMax": 0.20,
  "xpRange": 12,
  "perMinutePetXpCap": 200,
  "participationBonus": 0.20,
  "farmDampenScalar": 0.25,

  "featureLevels": [3,7,12,17,23,27],
  "tributeItems": {"10":"minecraft:gold_ingot","20":"minecraft:diamond","30":"minecraft:netherite_ingot"},
  "bankXpWhileGated": true,

  "support": {
    "auraRadius": 6,
    "pulseIntervalTicks": 140,
    "potionSipPerPulse": 0.125,
    "baselinePotencyScalar": 0.40,
    "durationScalarL10": 1.20,
    "durationScalarL30": 1.50,
    "safeguardOwnerHpThreshold": 0.40,
    "safeguardCooldownDay": true,
    "l30BurstInternalCdTicks": 300
  },

  "skyrider": {
    "ownerSlowFallingCd": 240,
    "levitateCritChance": 0.25,
    "ownerFallReductionRadius": 8,
    "ownerFallReductionPct": 0.5
  },

  "eclipsed": {
    "aggroDarknessCd": 400,
    "bigHitWarpThresholdPct": 0.20,
    "bigHitWarpChance": 0.40,
    "abyssRecallOncePerDay": true
  },

  "bond": {
    "stargazeWindowTicks": 2400,
    "stargazeHoldTicks": 600,
    "stargazeRange": 3.0
  },
  "enchantment_bound": {
    "extraDuplicationChanceBase": 0.05,
    "durabilityNoLossChance": 0.025,
    "miningHasteBaseTicks": 40,
    "focusCooldownTicks": 1200,
    "focusSurgeDurationTicks": 200
  },
  "cursed_one": {
    "resurrectionDelayTicks": 300,
    "deathPulseRadius": 6.0,
    "deathPulseMobEffects": [{"id":"minecraft:wither","duration":100,"amplifier":0},{"id":"minecraft:weakness","duration":100,"amplifier":0}],
    "ownerMinorDebuffChance": 0.25,
    "bindingPactOwnerDamageBonusPct": 0.10,
    "ownerWeaknessOnPetDeathDurationTicks": 200,
    "ownerWeaknessOnPetDeathAmplifier": 1,
    "ownerKillSupercharge": true,
    "resurrectionNegativeAuraDurationTicks": 200,
    "resurrectionNegativeAura": [{"id":"minecraft:weakness","amplifier":0},{"id":"minecraft:slowness","amplifier":0},{"id":"minecraft:hunger","amplifier":0}],
    "ownerDeathPermadelete": true
  }
  ,
  "eepy_eeper": {
    "slowMoveScalar": 0.90,
    "phantomImmunity": true,
    "napRegenRadius": 4.0,
    "napRegenAmplifier": 0,
    "restfulOwnerRegenDurationTicks": 200,
    "restfulOwnerSaturationAmplifier": 1,
    "restfulOwnerSaturationDurationTicks": 200,
    "restfulPetResistanceAmplifier": 0,
    "restfulPetResistanceDurationTicks": 200,
    "bonusPetXpPerSleep": 25,
    "dreamEscapeRange": 8.0,
    "dreamEscapeOwnerBlindnessDurationTicks": 600,
    "dreamEscapeRemoveAllXp": true,
    "dreamEscapeTeleportToRespawn": true,
    "dreamEscapeSleepCyclesRequired": 3
  }
}
```

All values are overridable via datapack/server config.

---

## 12) Implementation Notes (Fabric, 1.21.x server)

### 12.1 Hooks (avoid global ticking)

* **Player XP gain**: intercept `addExperience`/relevant Fabric event → compute share and distribute to eligible pets within range.
* **Entity damaged/kill**: mark “recentDamagedByOwnerOrPet” to qualify full XP.
* **Level-Up**: when `xp ≥ threshold`, update `level`, emit UI, recalc attribute bundle.
* **Tribute**: on `gate_pending`, prefer **sneak-use** handler; alternate **pet-local task** (≤6s) that scans for ItemEntity within r=2.2.
* **Ability triggers**: register to discrete events (`on_owner_begin_fall`, `on_pet_crit`, etc.).
* **Support aura**: when loaded & owner near, schedule **pet-local pulse task** (every \~7s) that applies effects and consumes a “sip”.

### 12.2 Data Components / Tagged Items

* **Respec Token**: `minecraft:paper` with data component `petsplus:respec=true`.
* **Linked Whistle (optional)**: `minecraft:goat_horn` with `petsplus:linked_pet=<uuid>` (recall on cooldown).
* **Role identifiers** stored as strings, abilities by namespaced IDs.

### 12.3 Networking & UI

* No client mod. Use:

  * `ServerPlayerEntity#sendMessage` (chat, with click/hover)
  * Action bar (`GameInfoOverlay` packets)
  * Bossbar API for transient milestone bars
  * Vanilla particles/sounds

---

## 13) Edge Cases & Rules

* **Eligibility**: pet must be **tamed, not sitting**, within range, same dimension.
* **Modded opt-outs**: By default any `Tameable` with an owner qualifies. Tag entity types with `#petsplus:excluded_tames` or set `petsplus:eligible=false` to opt out.
* **Multiple owners nearby**: a pet only ever bonds from its **owner**; ignore non-owner XP.
* **Offline owner**: pet accrues nothing.
* **Unloaded chunks**: no processing; all logic event-based on loaded entities.
* **Dimension transfer**: clear temporary timers; re-arm abilities as needed.
* **PvP**: pets ignore players unless both players opted in (config flag/permission).
* **Villager/Iron Golem**: never aggroed by pets unless configured.
* **Cursed One specifics**: cannot sit or be transferred; retains bond on pet death and auto‑resurrects after a delay; on owner death, the pet is permanently destroyed with a final pulse.
* **Eepy Eeper specifics**: while the pet is alive, the owner is not a valid Phantom target (configurable); Dream’s Escape consumes sleep cycles and removes all player XP.

---

## 14) Testing Plan

* **Unit**: XP share math, level thresholds, gating, cooldown accounting.
* Eepy Eeper: sleep triggers, hunger restore on sleep; Dream's Escape lethal-cancel + teleport to respawn, XP drain, pet knockout, three-sleep lockout.
* Or not.: bind first L30-awarding pet to player; award only when that specific pet is permanently destroyed (Cursed case fires on owner death only).
* **Integration**: tribute throw vs sneak-use; Support potion loading/ejection; aura pulse cadence; Skyrider fall hooks; Eclipsed void recall; Enchantment-Bound echoes (whitelist + surge); durability prevention hook; Cursed One death pulse/resurrection/owner-death permadelete.
* Hidden bond: tribute grants 120s stargaze window; crouch-with-pet timer fires advancement only when night, both in range, and no movement break.
* **Balance**: mob farm scenarios (afk grinder), boss fights (warden/dragon/wither/trials), multiplayer with four to six pets present.
* **Perf**: profile with 30+ pets in a raid arena; verify no global tickers.
* **Recovery**: server restart mid-cooldown, NBT persistence for component fields.
## 15) Advancements (Flair, Cosmetic Only)

* **"Best Friends Forever++"** — get your first Pets+ pet (first time a tamed pet gains Pet XP/bond).
* **"Trial-Ready"** — unlock any L10 milestone.
* **"The Melody of the Wind"** — Skyrider L20 unlocked. Where space's symphony frays the mind, the wind hums relief for the weary traveler.
* **"Edgewalker"** — Eclipsed L30 unlocked.
* **"Even Bester Friends Foreverer"** — reach Level 30.
* **"Or not."** — The specific pet that earned you "Even Bester Friends Foreverer" suffers a permanent death (not a temporary/downed state). So much for "foreverer".
* **"The Truest Form of Sacrilege"** — Guardian redirects 400+ damage for its owner without dropping below half HP. Luckily this team chose a frontline. or at least appointed one.
* **"Mmmm Healing Magic!"** — Support pulses healing on five unique allies within one Minecraft day. Feels like a deluxe spa, minus the towel service.
* **"Is This Designer?"** — Upgrade a pet ability to its max rank using tribute-earned slots. Absolutely couture, totally data-driven.
* **"I love you and me"** — Hidden: within two minutes of paying a tribute, crouch beside your bonded pet for 30 uninterrupted seconds at night while the pet stays sitting.
* **"Noo, Luna!"** — Trigger Dream's Escape once. A heartfelt, dramatic save; your companion took the hit so you didn't.
* **"At what cost…"** — Trigger Dream's Escape twice. Somber; survival weighs heavy when it's not free.
* **"Heartless, but Alive."** — Trigger Dream's Escape three times. Darkly satirical; you're still here. That's what counts… right?

> Bullets keep punchy tone; the localization block below is the authoritative in-game copy.
> Implementation: Hidden advancement `petsplus:bond_stargaze` with criterion `stargaze_timeout` (starts a 120s window after tribute; if it's night and you stay crouched within 3 blocks of your sitting pet for 30 continuous seconds, you earn it).

### UI & Quip Lines (final)

```properties
petsplus.ui.awaiting_tribute=Awaiting Tribute: {0}
petsplus.ui.level_up={0} reached Level {1}!
petsplus.ui.role_set=Role set to {0}
petsplus.ui.support_loaded=Loaded potion: {0}
petsplus.ui.support_safeguard=Safeguard! Cleansed and Regeneration applied.
petsplus.error.not_owner=You are not the owner of this pet.
petsplus.error.gate_locked=Progress locked. Toss a {0} near the pet or sneak-use to unlock.

petsplus.quip.guardian_pulse={0} holds the line. (+Resistance I)
petsplus.quip.scout_spot=Eyes up: {0} marks threats.
petsplus.quip.arcane_focus=Runes thrum around you ({0}s)
petsplus.quip.curse_pulse=A bad feeling spills from {0}…
petsplus.quip.nap_time={0} curls up. Regen drifts out.
petsplus.quip.dream_escape=Dream's Escape! {0} yanked you back to bed.
petsplus.quip.guardian_frontline=The truest form of Sacrilege—{0} is on the front line!
petsplus.quip.support_heal={0} shares the good stuff.
petsplus.quip.upgrade_designer=Is this designer? {0}'s upgrade says yes.
```

---

## 16) Advancement Localization (final)

```properties
# Core bonding
petsplus.adv.first_pet.title=Best Friends Forever++
petsplus.adv.first_pet.desc=A quiet glance... It’s official—you belong to each other.

# Tribute gate
petsplus.adv.trial_ready.title=Trial-Ready
petsplus.adv.trial_ready.desc=Gold clinks. Fate unpauses.

# Skyrider (replacement)
petsplus.adv.melody_wind.title=The Melody of the Wind
petsplus.adv.melody_wind.desc=Where space’s symphony frays the mind, the wind hums relief for the weary traveler.

# Eclipsed milestone
petsplus.adv.edgewalker.title=Edgewalker
petsplus.adv.edgewalker.desc=You danced on the edge; the void hit the renegade in playful retalliation.

# Level 30 bond
petsplus.adv.even_bester.title=Even Bester Friends Foreverer
petsplus.adv.even_bester.desc=You grew togetherer, you survived togetherer—now you’re together foreverer.

# Permanent loss
petsplus.adv.or_not.title=Or not.
petsplus.adv.or_not.desc=Forever is long. Goodbye was instant.

# Guardian tank check (spelling fixed)
petsplus.adv.sacrilege.title=The Truest Form of Sacrilege
petsplus.adv.sacrilege.desc=Luckily you chose a frontline. Or at least appointed one.

# Support heal
petsplus.adv.mmm_healing_magic.title=Mmmm Healing Magic!
petsplus.adv.mmm_healing_magic.desc=Never bring a gun to a wavy-hands fight.

# Upgrades/maxing
petsplus.adv.is_this_designer.title=Is This Designer?
petsplus.adv.is_this_designer.desc=We got a couple of upgrades if you like 'em bizzare, we could give you an upgrade that can play guitar, like.

# Hidden bond moment
petsplus.adv.i_love_you_and_me.title=I love you and me
petsplus.adv.i_love_you_and_me.desc=You know I got ya.

# Eepy Eeper clutch 
petsplus.adv.noo_luna.title=Noo, Luna!
petsplus.adv.noo_luna.desc=A heartfelt, dramatic sacrifice; your companion took the hit so you didn’t.

petsplus.adv.at_what_cost.title=At what cost…
petsplus.adv.at_what_cost.desc=Twice now, you monster.

petsplus.adv.heartless_but_alive.title=Heartless, but Alive.
petsplus.adv.heartless_but_alive.desc=Three times, huh. The world feels heavier than it did yesterday...
```


## 17) Open Integration Points

* **Datapack roles/abilities** registry for modpacks.
* **Boss tags** for CC scaling.
* **Permissions** hooks (LuckPerms).
* **API**: minimal accessor to query a pet’s bond/role for other server plugins.

---

## 18) Stretch (Post-1.0, Optional)

* **Downed State** (revive window) toggleable.
* **Cross-pet synergies** (e.g., Guardian+Support proximity bonus).
* **Pet trials**: optional challenges to grant temporary boons.

---

## 19) Out-of-Scope (1.0)

* Storage/auto-haul (Courier-like).
* New models or custom client UI.
* Full automation of resource gathering.

---

## 20) Summary

Pets+ adds **nine distinct, data-driven roles** and a **30-level bond** with **tribute gates**, turning every tame into a viable companion for **combat and exploration**—**server-only**, **vanilla-friendly**, and **balanced** around high-stakes progression.


