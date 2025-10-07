# Sniffer Courting System — Design Story
## made prior to 0.93-Rebranding-to-Realtime


> Romanticized taming loop that treats sniffers as discerning companions. Players earn their trust
> through fragrant gifts, mindful strolls, and respectful interaction.

---

## 1. Overview

* **Scope**: Server-side Fabric add-on that layers a three-phase courting ritual atop vanilla sniffer
  behavior. No custom models or GUIs; relies on action bar hints, particles, and existing items.
* **Goal**: Preserve the sniffer’s ancient, curious aura while giving players an involved but cozy way
  to befriend them.
* **Philosophy**: Courting is opt-in. Ignore it and the sniffer acts vanilla. Lean in and you unlock a
  uniquely bonded, tameable sniffer with bespoke perks and cosmetic flourishes.

---

## 2. Core Loop

| Phase | Player Action | Sniffer Response | Meter Gain |
| ----- | ------------- | ---------------- | ---------- |
| I — Fragrant Introductions | Offer a bundle loaded with ≥3 unique flowers (tagged small flowers count, including modded blooms). | Eats 3–5 flowers, emotes with heart+spore particles, enters a one-day cooldown. During the day’s randomly chosen **Blossom Favor hour**, bouquets grant an extra boost. | +20% per successful bouquet by default (+5% bonus during Blossom Favor). |
| II — Meadow Stroll | Lead the sniffer through tagged lush flora and respect Admiration prompts. | Trails behind calmly, sniffs interesting plants, reacts to missteps with mild chuffs. | +10% per 10s compliant walk, +5% sniff bonuses, −5% penalties. |
| III — Commitment Bloom | Present one last fresh bundle after the stroll. | Consumes a final trio, celebrates with effects, flips to tamed. | +100% phase completion (no repeats). |

1. **Acquire a wild sniffer** (egg, ruins, or natural spawn). It remains a standard vanilla entity
   until courting begins.
2. **Phase I – Fragrant Introductions**
   * Right-click the sniffer while holding a **Flower Bundle** (a bundle preloaded with ≥3 unique
     vanilla flowers, including nether blooms). Any modded flower that opts into the standard
     `#minecraft:small_flowers` tag—or the companion `#petsplus:sniffer_bundle_flowers` tag exposed by
     this system—counts automatically with no extra code. Packs that already tag their blooms “just
     work.”
   * The sniffer consumes **3–5 flowers** (random, respecting uniqueness) and increments its hidden
     `courting_phase_percent[0]` meter. Each feed awards **20 percentage points** by default, locks a
     **full in-game day (~20 real minutes) cooldown on the sniffer itself** (shared across all
     players), and surfaces feedback (heart+spore particle mix, soft sniff). Bouquet variety
     matters—see the fatigue system below. The timer keys off the world day counter so offerings
     naturally refresh at dawn.
   * While the sniffer remains in Phase I, dawn also rolls a **Blossom Favor hour**—a random
     Minecraft hour (0–23) stored per sniffer. Any bouquet offered during that one-hour window grants
     a **+5% bonus** progress before fatigue modifiers and releases faint pollen shimmer particles so
     attentive players notice the opportunity. The bonus never bypasses the daily cooldown; it simply
     makes the accepted bouquet that day more rewarding if timed thoughtfully.
   * When the phase I meter reaches **100%** (defaults to five successful offerings, but configurable
     via tuning files), the sniffer enters a “Courting Stroll” posture: lowered head, gentle hum,
     refuses further offerings until walked.

### Preparing Flower Bundles

* **Loading a bundle**: Craft a vanilla Bundle, then right-click plants to stash them inside. The
  tooltip lists each flower so you can confirm you have at least three unique entries before
  approaching the sniffer.
* **Easy flower sources**: Flower Forests and Cherry Groves provide quick variety early on, while
  wandering traders and village decorative pots offer rarer blooms without biome hunting.
* **Reuse the bouquet**: The same prepared bundle can serve Phase I offerings and the Phase III
  finale—just keep it stocked with fresh, varied flowers so fatigue penalties don’t trigger when it
  matters most.
3. **Phase II – Meadow Stroll**
   * Put the sniffer on a lead and guide it through **lush biomes** or player-made gardens flagged by
     blocks tagged `#courting_sniffer_likes` (flowers, moss, small dripleaf, hanging roots, cherry
     leaves, etc.).
   * Every **10 seconds** of compliant walking adds **10 percentage points**. Bonus ticks trigger when
     the sniffer walks within 1.5 blocks of a tagged plant and pauses to sniff, granting an additional
     5%. The sniffer tracks a shared stroll cooldown, so helpers can jump in after the timer resets.
   * **Mistakes**: stepping on or breaking the plant mid-sniff deducts points and plays a sad chuff.
     Small stumble for the sniffer to sell the moment. Penalties are mild (−5%) keeping the mini-game
     forgiving.
  * Random **“Admiration Moments”** (≈15% chance every sniff) now blend in a light rhythm mini-game.
    When the moment begins the sniffer hums while a nearby **note block chime** plays a pitch-coded
    cue. Depending on the note, **1–3 seconds** later a matching crouch-tone rings; the player must
    tap sneak within a forgiving ±0.4s window of that second chime while remaining stationary. Each
    success grows an **Admiration Streak multiplier** that speeds up progress: the first success pays
    the base +10% stroll bonus, then adds **+0.25×** to a personal multiplier (1.0× → 1.25× → 1.50×, up
    to a configurable cap). Subsequent perfect crouches multiply the bonus (e.g., a 1.75× streak awards
    +17.5%). Missing the cue, moving away, or yanking the sniffer instantly resets the streak to 1.0×,
    so skipping moments is harmless while attentive play accelerates the walk with sparkling particle
    riffs.
  * Whenever the sniffer finishes sniffing an untouched tagged block it records the location as a
    **favorite find** (limited to the five most recent). If the player guides the sniffer back to that
    exact block without anyone disturbing it, the sniffer releases soft **memory particles** and grants
    a small stroll bonus, reinforcing gentle pathing without adding any grind.
   * When the `courting_phase_percent[1]` meter hits **100%**, the sniffer performs a joyful hop,
     head-dips the player, and unlocks the final phase.
4. **Phase III – Commitment Bloom**
   * Present one final **Flower Bundle** with ≥3 unique flowers. The sniffer consumes an exact trio,
     pushes `courting_phase_percent[2]` to **100%**, and flips into `tamed=true`, assigning
     the player as its companion. The fatigue rules still apply, so players must end on a fresh
     fragrance set rather than recycling the previous bouquets.
   * Emits celebratory particles (spore blossom swirl + custom sound mix) and grants the player the
     temporary status effect **“Shared Fragrance”**: +10s speed I and friend particle cloud.
   * The sniffer gains access to pet systems (sit/follow, teleport to owner, gentle excavation ping).

### 2.1 Abstracting the Courting Framework

To support future mobs, the ritual is expressed as a **three-phase courting framework** that feeds the
same code paths with species-specific hooks:

* Each creature registers a `CourtingProfile` with three `PhaseDefinition` entries (`0..2`). Every
  definition declares how player actions earn percentage progress, the cooldown behavior, eligible
  items/blocks, and optional failure rules.
* The shared controller stores progress as `courting_phase_percent[0..2]` (0–100%) and a single
  `active_phase` index. Mob-specific logic listens to `PhaseStart`, `PhaseProgress`, and
  `PhaseComplete` events to trigger animations, sounds, or reward unlocks.
* Common helpers—bouquet parsing, fatigue tracking, admiration prompts—live in neutral utility
  classes. Species can opt into or override behaviors by swapping out evaluator functions while still
  inheriting caching for modded flowers or tagged terrain.
* Datapacks can author additional mob profiles by referencing registry IDs and tags; no hard-coded
  sniffer checks remain in the framework layer. The sniffer simply ships with the default profile
  described above.
* Example follow-ups: foxes could prefer berry bundles for phase I, stealthy woodland stalks for phase
  II, and a final “den furnishing” gift for phase III—all implemented by swapping interaction handlers
  while relying on the same controller, fatigue queues, and percent meters.

---

## 3. Systems & Data Hooks

### 3.1 Flower Bundle Detection
* Bundles are parsed for `ItemStack` entries with the `minecraft:small_flowers` tag + whitelisted
  specialties (wither rose, cherry blossom). Unique count logic ignores damage values.
* **Modded flower support**:
  * A lightweight startup scan builds the accepted-flower cache from tag contents, so modded blooms
    become valid the moment they contribute to the tag—no manual registry needed.
  * Mods/datapacks that prefer an isolated namespace can instead target
    `#petsplus:sniffer_bundle_flowers`; the tag includes `#minecraft:small_flowers` by default so most
    content piggybacks automatically, while allowing curated overrides.
  * For compatibility with older mods that forget to tag their flowers, a fallback check accepts any
    `BlockItem` whose block appears in `BlockTags.FLOWERS` at runtime, keeping edge cases playable
    without extra patches.
* Consumption removes entries at random until 3–5 unique flowers are eaten, emitting each flower’s
  vanilla pickup sound pitched up slightly (0.95 → 1.10).

### 3.2 Flower Fatigue System
* **Goal**: Encourage bouquet variety without hard blocking progress.
* The sniffer stores the **registry IDs** (string form, e.g., `minecraft:blue_orchid`) of flowers from
  the last two accepted bouquets in a rolling history array. Because the IDs come from the item
  registry and not hard-coded lists, any modded flower carrying the proper tag participates
  automatically.
* When evaluating a new bouquet, build a `signature` set of its top three unique flowers (sorted
  alphabetically for deterministic comparison) and compare against history:
  * **Round 1 Fatigue** – If the signature overlaps by ≥2 flowers with the most recent bouquet,
    progress from this offering is halved and the sniffer emotes a playful sneeze particle burst.
  * **Round 2 Fatigue** – If it also overlaps by ≥2 flowers with the second-most recent bouquet,
    the sniffer refuses the offering, consuming nothing and locking a 10s “needs a fresh scent”
    cooldown. Action bar copy nudges the player to diversify.
* Successfully delivering a bouquet that differs by at least two flowers clears fatigue and shifts the
  history queue.
* Phase II stroll bonuses ignore fatigue (the sniffer is reacting to the environment), but Phase III’s
  final bouquet respects the same rules to keep the climax special.

### 3.3 Progress Tracking
* Each courting-capable mob stores a **per-player ledger** inside a persistent `DataComponent`:
  ```
  { "courtship": {
      "players": {
        "<uuid>": {
          "phase": 0|1|2|3,
          "courting_phase_percent": [0..100, 0..100, 0..100],
          "recent_bouquet_signatures": ["minecraft:blue_orchid|minecraft:pink_tulip|minecraft:peony", ...],
          "admiration_multiplier": 1.0
        },
        "<uuid2>": { ... }
      },
      "mob_cooldowns": {
        "offering_available_on_day": world_day,
        "stroll_bonus_available_at": game_time,
        "phase1_blossom_hour": 7
      },
      "display_player": "<uuid>"
  }}
  ```
* Interactions always update the ledger entry keyed to the acting player, so progress can continue even
  if someone else has partially courted the same sniffer. Global cooldowns stored on the mob gate each
  offering or stroll bonus, meaning **any** player can swoop in once the timer expires and continue the
  ritual. For Phase I offerings the cooldown jumps to the next Minecraft dawn, so the ledger compares the
  world-day index instead of raw tick timestamps and records the newly rolled Blossom Favor hour.
  Each entry also tracks the player’s Admiration streak multiplier so rhythm successes only reward the
  dancer who earned them. The `display_player` is whichever courter most recently advanced a phase;
  their name fuels action-bar messaging until another player overtakes them.
* A `ServerWorldMixin` piggybacks on the bouquet refresh hook: when `timeOfDay % 24000 == 0`, it
  updates `offering_available_on_day` and calls `CourtingScheduler#assignBlossomFavor`. The scheduler
  seeds a `RandomSource` with the sniffer’s persistent ID plus the world day to pick a stable
  `phase1_blossom_hour` (0–23) for the next cycle, storing it in the shared cooldown block so clients
  can surface it in tooltips if desired.
* To avoid heavy polling, a `SnifferEntityMixin` already responsible for courting bookkeeping performs a
  quick comparison every 40 ticks while the sniffer is in Phase I: it checks whether the current
  `timeOfDay` falls inside the assigned hour and, if so, emits a subtle pollen puff once per minute to
  hint at the bonus. No additional cooldown fields are required—the system simply marks the boost hour
  and lets the standard daily offering gate enforce pacing.
* Meter values sync to clients via `EntityDataAccessor` and leak to action bar as cozy copy (“Sniffer is
  still getting acquainted…”, “Sniffer wants a floral stroll…”). Other mobs reuse the same messaging
  scaffolding with their own localized strings.

### 3.4 Favorite Find Memories

* **Trigger points**: Favorite finds are captured only when the sniffer’s sniff interaction completes
  on a block that matches `#petsplus:courting_sniffer_likes` and remains unbroken. No background scan or
  tick loop is required.
* **Storage**: The mob maintains a tiny ring buffer (`favorite_find_positions[5]`) of block positions
  with the world time when they were marked. Entries expire automatically after five in-game minutes or
  when the block changes.
* **Bonus check**: During each future sniff start, the system performs a direct lookup against the
  buffered coordinates to see whether the current block matches a saved favorite. When it does, the
  sniffer emits emerald-hued memory particles, plays a soft bell chime, and awards an extra +5%
  stroll bonus (before multipliers) while praising the player via action bar copy.
* **Cost control**: Because all logic fires from sniff events and maintains at most five positions, the
  feature adds negligible tick cost even on busy servers. Favorites are stored per sniffer—not per
  player—to keep the data footprint minimal.

### 3.5 Biome & Block Tags
* New datapack tags:
  * `#petsplus:courting_sniffer_likes` → block list for stroll scoring.
  * `#petsplus:courting_sniffer_biomes` → optional biome weighting (lush cave, cherry grove, flower
    forest) for passive progress ticks.
* Enables modpack authors to extend the courting experience with custom flora.

### 3.6 Failure & Reset Handling
* Ignoring the sniffer for **5 in-game days** during phase 1 or 2 decays the active
  `courting_phase_percent` value by 25%.
* Direct damage from the player during courting **resets the current phase** and locks a one-day
  cool-down (“the sniffer is hurt and distant”).
* Killing the sniffer preserves no progress (vanilla death).

### 3.7 Multiplayer & Ownership Rules
* **Ledger-Driven Claims**: Because every player receives their own ledger entry, a sniffer can be
  courted by multiple people at different progression levels simultaneously. The UI highlights the most
  recent contributor (“Sniffer is courting with Alex…”) but other players are never blocked from trying.
* **Helpers**: Friends who interact while the primary courter is online simply progress their own
  ledger entry. If cooperative taming is desired, server packs can keep a shared bouquet on hand and
  trade lead duties so both ledgers advance in parallel.
* **Offline Steals**: Logging off does not lock a claim. If another player completes Phase III while the
  original courter is paused or absent, the tame binds to the finisher. This keeps the system simple and
  mirrors vanilla behavior where the last interaction decides ownership.
* **Final Bond**: The act of delivering the Phase III bouquet always determines the owner. Whoever
  completes their ledger and fires the final offering becomes the sniffer’s companion, regardless of who
  progressed earlier.
* **Server Hooks**: A datapack boolean `allow_shared_courting` still lets administrators grant
  co-ownership, flipping the Phase III resolution to tame the sniffer for every ledger entry at 100%.

---

## 4. Post-Tame Rewards

* **Tame Trait – Verdant Nose**: The sniffer periodically sniffs out nearby moss/flower-rich areas,
  placing temporary sparkling particles to guide the owner to hidden patches.
* **Cosmetic Saddlebag**: Right-click with leather to add a visual satchel (no storage, pure flavor).
* **Emote Emulation**: Sneak-right-click makes the sniffer perform its “Admiration Moment” swirl around
  the player, reinforcing the bond.
* **Shared Fragrance Cooldown**: Re-triggerable every Minecraft day by feeding a single unique flower
  (no bundles needed once tamed).

---

## 5. UX & Feedback

* **Action Bar States**
  * “The sniffer is considering your bouquet…” (Phase I cooldown).
  * “Lead your sniffer through fragrant paths.” (Phase II active).
  * “One final bouquet will seal your bond.” (Phase III prompt).
  * Admiration streak text (“Rhythm linked: +25% stroll boost!”) pulses briefly after successful cues so
    players read how their multiplier is climbing.
* **Particles**: Use a palette of heart, spore blossom, and allay dust to imply romance without new
  assets.
* **Sounds**: Blend sniffer sniff loops, goat bleats pitched down, and bell chimes for milestones.

---

## 6. Configurability

* `flower_consumption_range`: default `[3,5]`.
* `phase_goal_percent`: default `[100, 100, 100]`.
* `phase1_offering_increment_percent`: default `20` (per accepted bouquet).
* `phase1_offering_cooldown_days`: default `1` (one bouquet per Minecraft day per sniffer).
* `phase1_blossom_bonus_percent`: default `5` (added on top of the base increment during the favor hour).
* `phase1_blossom_particle_interval_ticks`: default `1200` (one minute) for the reminder puff cadence.
* `phase2_walk_increment_percent`: default `10` (per 10s compliant lead walk).
* `phase2_sniff_bonus_percent`: default `5`.
* `phase2_penalty_percent`: default `5`.
* `phase2_admiration_bonus_percent`: default `10`.
* `phase2_admiration_multiplier_increment`: default `0.25`.
* `phase2_admiration_multiplier_cap`: default `2.0` (equivalent to a 2× bonus).
* `stroll_bonus_interval`: default `10s`.
* `admiration_chance`: default `0.15`.
* `decay_days`: default `5`.
* `fatigue_overlap_threshold`: default `2` (how many matching flowers trigger fatigue tiers).
* `fatigue_cooldown_seconds`: default `10`.
* Server admins can disable penalties, adjust timers, or skip entire phases for accessibility.

---

## 7. Future Hooks

* Integrate with **Pet XP bond** once tamed: initial bond bonus for completing the courtship story.
* Optional **journal entry** (advancement + lore book) unlocked after taming, summarizing the courting
  journey with screenshots or text prompts.
* Cross-mod hook: bees near a courting sniffer adopt a calm state, emphasizing the floral vibe.

