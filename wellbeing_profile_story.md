# Pet Wellbeing Profile Story

## Vision
Craft a lightweight background system that charts each pet's emotional journey over time. The profile should emphasize high-intensity emotions, spotlight long-running moods (especially prolonged unhappiness or neglect), and provide derived insights that can later power sentimental "proof of existence" presentations.

## Guiding Principles
- **Event-driven, low overhead**: Reuse existing emotion cue dispatch; no new global polls.
- **Derived-first data**: Persist compact summaries instead of raw logs.
- **Consistency tracking**: Highlight sustained emotional states and neglect streaks.
- **Future-facing sentimentality**: Organize data so later UX layers can tell meaningful stories.

## Existing Systems to Leverage
- `PetMoodEngine` for blended emotions, dominant mood, and weights.
- `PetComponent` for lifecycle context (role, milestones, bond level).
- `EmotionContextCues` stimulus summaries with timestamps and categories.
- Emotion catalog of 30 defined feelings (positive, negative, neutral groupings).

## Data Flow Overview
1. **Stimulus Capture Layer**
   - Hook into `EmotionContextCues.sendCue` (or equivalent) to intercept each `StimulusSummary`.
   - Maintain a per-pet **daily scratchpad** recording entries that exceed an intensity threshold or match flagged categories (threat, bonding, exploration, etc.).
   - Each entry stores: timestamp, dominant emotion, signed delta, category tags, rebound markers (links to subsequent positive deltas that resolve a negative spike), and optional health/hunger snapshot for richer neglect heuristics.
   - Deduplicate repeated cues within a short cooldown window (e.g., 5 in-game minutes) so spammy behaviors do not drown out meaningful spikes.

2. **Daily Rollup Pipeline**
   - On world-day rollover (or when scratchpad hits N entries) flush into a durable `WellbeingDay` record:
     - Date/tick window and streak counters (e.g., consecutive low-health days, dominant emotion streak length).
     - Top positive and negative emotions (N best weighted deltas) with totals and contributing cue categories.
     - Aggregate stats: total positive/negative delta, variance, net mood direction, rebound latency averages, health baseline delta.
     - Context snapshot from `PetComponent` (role, level, milestones, bond tier, health percentile).
     - Optional slot for **notable moments**: a single sentence generated from template rules ("Felt anxious for 6h until fed") for downstream UX reuse.
   - Store last 30 (configurable) days in a circular buffer serialized with the pet. Apply delta compression (e.g., byte-packed ints) so NBT stays compact.
   - Wipe the scratchpad after rollup; recompute derived metrics lazily. If a rollup is missed (chunk unloaded), merge multiple day spans on next tick while marking the record as aggregated.

3. **Neglect & Consistency Tracking**
   - Maintain running streak counters:
     - **Low-health streak** when pet health stays under configurable threshold across daily snapshots.
     - **Unresolved negative streak** when days finish with dominant negative emotion and no recorded rebound.
     - **Stable mood streak** for days where dominant emotion category stays consistent (e.g., calm/happy) to celebrate reliable care.
   - Tag `WellbeingDay` records with these streak states for future UI callouts.

4. **Derived Metrics (Rolling 7–30 Day Windows)**
   - **Bond Stability**: Ratio of bonding/calm deltas vs. threat/ennui deltas.
   - **Consistency Score**: Standard deviation of daily dominant emotions; lower = predictable, higher = volatile.
   - **Exploration Appetite**: Frequency of curiosity/focused spikes tied to exploration-tagged cues.
   - **Care Responsiveness**: Average time between negative spikes and positive rebounds within the same day.
   - **Owner Reliability**: Count of days where negative deltas dominate without rebound; feed into alerts or memory journal.
   - **Neglect Severity Index**: Weighted combo of low-health streak length, hunger neglect, and unresolved negative streaks, capped to prevent runaway values.
   - **Emotional Diversity**: Shannon-style entropy on dominant emotions to reveal pets stuck in narrow mood loops.
   - Recompute on demand from stored `WellbeingDay` data so metrics remain reproducible. Cache results with version stamps so schema tweaks can invalidate gracefully.

## Sentimental & Future UX Hooks
- **Daily Digest**: Provide yesterday's dominant emotion, summary line, streak updates for future journals or chat quips.
- **Timeline Archive**: Allow `Proof of Existence` features to replay peaks, troughs, and neglect recoveries.
- **Behavior Hooks**: Expose metric getters for AI behaviors (e.g., high exploration score unlocks scouting routines).
- **Reminder System**: If neglect streak crosses threshold, trigger soft notifications or quest prompts.
- **Shared Memories**: When multiple pets experience linked emotions (pack hunt, group fear), cross-reference records to craft group narratives later.

## Implementation Notes
- Create `PetWellbeingTracker` attached to each `PetComponent`.
- Provide listener method (e.g., `onStimulusCaptured(StimulusSummary summary)`) to populate scratchpads.
- Flush tracker via daily tick hook (world time % 24000 == 0) or when pet unloads.
- Serialize `PetWellbeingTracker` alongside mood data (NBT friendly, small footprint).
- Expose API: `getRecentWellbeingDays()`, `getMetric(PetWellbeingMetric metric)`, `getNeglectState()`.
- Add config entries for intensity thresholds, retention length, streak definitions.
- Provide migration helpers so tracker can populate from legacy saves by replaying recent mood history if available.
- Add defensive guards (e.g., max entries per day, fallback defaults) so corrupted data can't crash serialization or UI consumers.

## Efficiency Considerations
- Scratchpad entries capped per day; prune low-intensity noise.
- Derived metrics cached with dirty flags; recompute only when underlying days mutate.
- Utilize immutable snapshots to avoid recalculating streaks every tick.
- Keep storage numeric/enums; no heavy strings beyond emotion/category identifiers.
- Batch save operations when multiple pets flush in same tick to minimize disk churn.
- Provide server rule toggles to disable high-cost metrics for large modpacks without code edits.

## Integration Path
1. Implement tracker backend (no UI yet).
2. Instrument cue dispatch and daily rollup hooks.
3. Add debug commands/logging to inspect wellbeing data during testing.
4. Iterate on thresholds/metrics based on playtesting.
5. Later, connect to `Proof of Existence` UI, journals, and sentimental systems.
6. Ship telemetry hooks (logbook, datapack export) for creators to validate assumptions before full release.

## Edge Case & Robustness Considerations
- **Offline & Chunk Unload Gaps**: Track last processed day and reconcile gaps when pet reloads, creating "offline days" with decay weighting so streaks degrade gradually rather than snapping.
- **Pet Transfers**: When ownership changes, split the streak history but keep long-term metrics so sentimental systems can highlight prior caretakers.
- **Death & Revival**: Mark terminal days with closure tags and lock streak counters; if pet revives, restart counters but keep prior archive for memorialization.
- **Cross-Mod Compatibility**: Allow other mods to inject custom cue categories/emotions via registry events without changing core logic.
- **Performance Guardrails**: Expose profiler counters (entries processed, rollup duration) so we can monitor background cost.
- **Testing Strategy**: Create scripted scenarios (neglect, adventure day, healing arc) to verify metrics match narrative expectations before release.

