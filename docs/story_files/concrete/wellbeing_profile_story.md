# Mental Wellbeing System Story

## Vision
Translate the overhauled emotion stack into a long-lived mental wellbeing ledger that spots emerging issues, celebrates care patterns, and feeds `ProofOfExistence` items with story-ready highlights. From taming to final memorials, the tracker should lean on the new impact-weighted mood synthesis, role scheduling, social routines, and sentimental artefacts so every datapoint already contributes to gameplay or narrative payoffs while staying invisible and cost-light in the background. Anchor the whole system on a reliable 100-day chapter cadence that packages long-lived telemetry into digestible highlight arcs without stranding near-term analytics.

## Guiding Tenets
- **Event-first, lazy everywhere else**: Piggyback on `EmotionContextCues`, gossip taps, and impact-weighted mood refreshes; only roll data forward when something changes or a day closes.
- **Interpretation over raw logging**: Store small, reproducible summaries (dominant blends, streak counters, resilience deltas) rather than full cue histories.
- **Cross-system literacy**: Surface hooks that nature archetypes, role milestones, social AI, and memorial items can read without bespoke adapters.
- **Player-trusting feedback**: Turn neglect warnings, gratitude callouts, and memory journal beats into actionable, lore-friendly nudges instead of punishments.
- **Proof-of-existence ready**: Design every summary so ancient memories condense into highlight reels for sentimental artefacts without revisiting raw telemetry, and ensure each 100-day chapter reads like a self-contained narrative beat.
- **Invisible footprint**: Keep CPU, memory, and storage impact tiny through caps, condensation, and deferred processing so large servers can forget the system exists until they query it.

## Systems to Plug Into
- **Impact-weighted mood engine** – Consume the normalized weight vector and hysteresis metadata to understand how moods shift between ticks and why rebounds succeed.
- **`PetComponent` state core** – Reuse stored role, bond level, milestone flags, and the shared `StateManager` timelines for contextual snapshots.【F:src/main/java/woflo/petsplus/state/PetComponent.java†L51-L152】
- **Nature & temperament roster** – Apply nature-driven volatility and resilience multipliers when evaluating streak severity so personalities flavour outcomes.【F:src/main/java/woflo/petsplus/state/PetComponent.java†L77-L92】
- **Social behaviour mesh** – Read pack/party presence from the swarm tracker and gossip system so communal care influences wellbeing without new polling loops.
- **Sentimental artefacts** – Feed daily summaries and highlight reels to `ProofOfExistence` and journals so the same ledger powers souvenirs, scenarios, and memorialisation endpoints.

## Signal & Storage Flow
Keep the pipeline lightweight by only materialising data when a tick matters.

1. **Stimulus & mood snapshot**
   - Subscribe to `EmotionContextCues` once a cue clears freshness gates. Capture timestamp, cue category, signed intensity delta, rebound identifiers, and the post-update dominant mood tuple from the impact-weighted engine.
   - Stamp the current role loadout, owner proximity, party size, and nature multipliers. Track whether the signal was self-mitigated (pet acted) or owner-resolved (player interaction) by sampling the behaviour scheduler outcome.
   - Log entries into a capped rolling scratchpad keyed by in-world day plus chunk cell to respect the new swarm orchestration. Deduplicate within configurable cooldown windows, but aggregate totals so ambient stressors still show pressure.
   - Flag candidate “moments” as the scratchpad fills—interactions that flip mood polarity, resolve long streaks, or unlock role milestones—and pass lightweight pointers to the highlight assembly flow.

2. **Daily rollup & resilience ledger**
   - When the server day flips or a pet unloads, fold scratchpad entries into a `WellbeingDay` record that captures the day’s arc:
     - **Mood narrative** – Peak positive/negative blends with percentages, plus hysteresis spans (ticks spent recovering or stuck).
     - **Resilience deltas** – Deltas against nature resilience, role fatigue, hunger/health baselines, and recent discipline timers.
     - **Care catalysts** – Owner interactions that resolved spikes and social assists (ally solidarity pulses, gossip cheers).
     - **Environmental anchors** – Biome tags, structure proximity, and weather glimpsed from the state manager to explain context.
   - Persist the last 30–45 entries (configurable) inside a compact circular buffer on the component. Flag aggregated days created while a pet was offline so downstream systems can soften streak penalties.
   - As entries fall off the buffer, stream their condensed summaries into the active chapter’s digest staging area so the 100-day chapter keeps an authoritative running tally even while the hot buffer stays tiny.
   - Emit moment references for notable rollups (e.g., “first winter survived”, “bond tier reached”) so the highlight assembler can expand a day’s story without rehydrating raw cues.

3. **Ongoing streak monitors**
   - Maintain realtime counters updated whenever a rollup writes:
     - **Neglect risk** – Consecutive days dominated by adverse blends without rebound events.
     - **Stability groove** – Stretches of calm/cheerful dominance, weighted by nature volatility to celebrate consistent care even for mercurial archetypes.
     - **Exploration appetite** – Rolling count of curiosity/focus surges tied to scouting roles or adventurous nature triggers.
     - **Community buffer** – Total minutes per day spent inside allied aura or gossip uplift range, used to dampen loneliness streaks.
   - When counters cross major thresholds, append a streak marker to the highlight queue rather than storing every daily delta.

4. **Derived windows (7 / 14 / 30 day)**
   - Compute on demand and cache with schema/version stamps covering bond stability, resilience score, owner reliability, emotional diversity, and neglect severity.
   - Archive the cached windows in the active chapter digest as varint snapshots so they can be rehydrated into long-form stories when `ProofOfExistence` items request deeper timelines.
   - Invalidate or recompute cached windows whenever a `WellbeingDay` is condensed into the digest staging area or a chapter rolls over. Use the shared `digest_rotated` observer signal so downstream consumers refresh their analytics without polling. When a chapter resets (death, revival, migration), start the caches empty unless the caller hydrates from the freshest digest to avoid double-counting condensed history.

## Lifecycle Chapters
Give every pet a narrative arc that survives long campaigns without bloating save data.

- **Taming bootstrap**
  - Start with a `TamingChapter` stub seeded from the onboarding temperament probe, default bond level, and first successful care action. Reset all streak counters, but mark the resilience baseline as "provisional" so the first seven `WellbeingDay` rollups can recalibrate without triggering neglect alarms. Treat this stub as the opening slice of the first 100-day chapter so the eventual digest carries the full origin arc.
  - Emit a `chapter_opened` hook for role scripts and onboarding UIs so they can surface the pet’s initial needs checklist while the chapter is live. Include metadata that this is day 0–X of the first 100-day arc so downstream stories can reference "chapter one" consistently.

- **Active care cadence**
  - Promote the ledger into an `ActiveChapter` after the bootstrap window or once the pet completes its first assignment loop. Allow neglect, stability, exploration, and community streaks to accumulate normally, and carry resilience deltas forward across days.
  - Track the cumulative day count with a `chapter_day_total` that sums the live buffer size and the staged digest days. When the total reaches 100 in-world days, roll the closing window into a `ChapterDigest` artefact, archive it, and reopen a fresh span so long-lived pets never accumulate unbounded per-day logs. Each digest should clearly label its ordinal (Chapter 1, Chapter 2, etc.) so proof items and analytics can stitch the pet’s career chronologically.
  - Use the highlight assembly flow to condense older `WellbeingDay` records into themed reels and nominate "chapter capstone" beats without restating chapter metadata.

- **Late-life easing**
  - When age, health, or custom triggers mark the pet as elderly, open a `SunsetChapter` that halves streak decay penalties, emphasises gratitude cues, and compresses exploration metrics in favour of comfort tracking. Keep resilience deltas but clamp negative swings to avoid punitive spirals. Continue counting toward the 100-day chapter cadence; if the pet doesn’t survive the full span, finalise the partial chapter with a "short chapter" flag so analytics know it closed early.
  - Notify social meshes and caretaker dashboards via `chapter_transitioned` so support roles can adjust routines (e.g., more rest emotes, slower schedules).
  - Route ongoing highlight queue entries through the highlight assembly flow to produce “legacy comfort” reels that emphasise caretakers, safe spaces, and final achievements. Annotate which highlights conclude the current 100-day arc versus ones deferred to the next chapter in case the pet recovers.

- **Death / revival boundaries**
  - On death, finalise the current chapter (even if it closes mid-100-day span), emit a memorial digest, and lock further ledger writes while keeping the streak snapshots and resilience aggregates intact for memorial queries.
  - If revival occurs, spin up a `RevivalChapter` that references the archived chapter digest ID, resets streaks, and copies only long-term temperament modifiers so the pet resumes with continuity but without inherited penalties. Start the revived timeline as day zero of a new 100-day chapter so continuity remains predictable.
  - Mirror the death digest into a compact memorial bundle that `ProofOfExistence` can render immediately while deeper timelines remain opt-in rehydrations.

Chapter metadata lives alongside the circular buffer, sharing the same schema version tags so migrations can move both day-level entries and chapter digests together.

## Highlight Assembly & Proof Artefacts
- **Moment intake** – Funnel scratchpad triggers and streak threshold markers into a shared highlight queue that stores timestamp, day index, trigger type, and lightweight context (role, nature, caretakers). Scope the queue per active chapter so eviction, rollover, and persistence rules mirror the digest cadence.
- **Queue lifecycle** – When a day is condensed or evicted from the live buffer, move any linked highlights into the digest staging area with their source identifiers. Prune the queue once it exceeds configurable caps (e.g., 64 moments and 16 streak markers) to guarantee constant bounds between rollovers.
- **Reel synthesis** – During the 100-day rollover, collapse staged highlights into themed reels (care streaks, crises averted, adventures, comfort arcs) and mark capstone beats. Attach the reel bundle to the emitted `ChapterDigest` so `ProofOfExistence` and memorial endpoints can hydrate story snippets without reopening full telemetry.
- **Partial closures** – If a chapter ends early (death, migration), finalise the queue immediately, tag the digest as partial, and flag open-ended highlights so downstream systems can either archive them as epilogues or roll them forward into the next chapter when applicable.
- **Observer hooks** – Broadcast `highlight_bundle_updated` alongside `digest_rotated` so UI caches and sentimental artefacts know when to refresh their reels without polling the entire ledger.

## Feedback Surfaces & Hooks
- **Care digest pings** – Lightweight chat toasts or advancement-style pop-ups summarizing the previous day’s highs/lows and nudging neglected needs.
- **Role-aware modifiers** – Allow role scripts (e.g., Guardian vigilance or Courier wander) to read wellbeing metrics and adapt cooldowns, leash radius, or support emotes.
- **Nature callouts** – Provide localized strings referencing the pet’s nature when notable metrics trigger (“Radiant keeps spirits high despite storms”).
- **Memorial & journal exports** – Serialise notable moments, streak climaxes, and highlight reels into the sentimental archive so `ProofOfExistence` and future diaries use the same language.
- **Server governance** – Expose aggregate wellbeing dashboards per owner for admin tooling or datapack automation (quests, caretaker scores).
- **Legacy export pipeline** – When a chapter closes (retirement or death), distill its `ChapterDigest` into story beats—peak moods, resilience swings, critical rescues—and attach minimal metadata (chapter ID, dominant caretakers, final resilience score) to the locked ledger. Provide hooks for memorial UIs to request either the digest summary or a replay hydration on demand, and ensure revivals spawn a fresh chapter while linking back to the archived digest for continuity.
- **Proof-of-existence storytelling** – Offer a `highlight_bundle` endpoint that lets artefacts fetch chapter-merged reels (100-day spans, merged weeks, or climactic scenarios) with optional drill-down handles into the archived digests if the player requests more detail.

## Implementation Notes
- House everything inside a `PetWellbeingTracker` owned by the `PetComponent`, registered and saved alongside the mood engine.
- Offer observer hooks (`WellbeingObserver` bus) so AI, command tooling, and datapacks can subscribe without polling.
- Use the shared `StateManager` scheduler to queue daily flush tasks, ensuring chunk-unloaded pets reconcile on next activation.
- Provide debug commands: `/petsplus wellbeing <pet>` (JSON dump) and `/petsplus wellbeing replay <uuid>` (per-day timeline).
- Mirror the impact-weighted mood palette when storing dominant blends so UI layers can reuse authored colours without conversion.
- Guard against runaway growth with per-day entry caps, numeric compression (varint packs), and schema versioning for safe migrations.
- Defer heavy highlight synthesis to idle ticks or admin requests so moment merging never interferes with active play.

## Archival Budget & Compression
- **Rolling condensation** – As the circular buffer sheds entries, drip their summaries into the digest staging area so the active chapter always knows how many days have been archived. When the staged total plus the live buffer reaches 100 days, finalise the batch into a `ChapterDigest`, clear the staging area, and reopen the buffer for the next span. Condensation runs as part of this 100-day rollover rather than on every overflow tick. Tag partial chapters (e.g., death-triggered closes) so downstream systems understand why a digest covers fewer than 100 days.
- **Budget targets** – Keep each digest under 1.5 KB by coalescing repeated cues, quantising mood percentages to single-byte steps, and truncating streak histories after three tiers (minor, moderate, severe).
- **Version resilience** – Tag digests with the same schema version as the source `WellbeingDay` entries; migrations must map both directions so analytics tooling can hydrate summaries or fall back to raw days.
- **Observer signalling** – Fire a `digest_rotated` event whenever condensation runs so UI caches, datapacks, and admin dashboards refresh their aggregates without re-reading the full ledger.
- **Storage hygiene** – Track cumulative bytes written per pet and expose it to the telemetry overlay to spot runaway cases during SMP testing.

## Edge Cases & Testing
- **Offline gaps** – Record last processed day and synthesize “cooled” days with decayed streak multipliers when a pet was unloaded for long stretches.
- **Ownership changes** – Split streaks at transfer but preserve rolling windows flagged as "legacy" so sentimental items can reference prior caretakers.
- **Death / revival arcs** – Lock the ledger on death, generate closure notes, and when revival occurs start a fresh chapter while retaining links for memorial exports.
- **Chapter rollover** – Validate the 100-day digest rotation on endurance pets; confirm streak counters and resilience aggregates hand off cleanly between chapter digests and the refreshed live buffer.
- **Highlight integrity** – Ensure merged reels match the original moment references (no duplicates, consistent chronology) and stay under size caps even on multi-year pets.
- **Cross-mod cues** – Register an API so external mods can push categorized stimuli or request wellbeing summaries without leaking internals.
- **Scalability audits** – Ship profile counters (entries ingested, flush duration, bytes per day) for spark profiler overlays, ensuring the system behaves on large SMP servers.
- **Scenario harnesses** – Scripted tests for neglect loops, rapid bonding, raid stress, and social recovery to validate metrics against narrative expectations before release.
