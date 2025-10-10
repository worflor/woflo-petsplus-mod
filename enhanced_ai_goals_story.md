# Enhanced AI Goals Story

## Vision
Create an endlessly extensible pet behaviour system by decomposing scoring, context capture, goal definition, and animation execution into modular, data-driven registries. This keeps the runtime deterministic while making it easy to add new influences, behaviours, and presentation variants without touching core control flow.

## Existing Entry Points to Respect
- **`AdaptiveAIManager.initializeAdaptiveAI`** already wires `GoalType` entries onto the vanilla `GoalSelector`. The new director and signal marketplace wrap this existing hook rather than replacing it, so mobs still enter the system through the current capability analysis pipeline.
- **`MobCapabilities.analyze`** remains the authoritative capability probe. Behaviour packs use its profiles to gate goals instead of inventing new capability flags, ensuring the data-driven catalogue maps cleanly onto today’s compatibility checks.
- **`PetContext.capture`** is the snapshot factory used by `GoalSuggester`. The event-driven context work extends this method so it reuses component snapshots (`PetComponent`, `PetMoodEngine`, `PetSwarmIndex` data) instead of creating parallel storage.
- **`GoalSuggester.calculateDesirability` / `calculateFeasibility`** become the initial sources of truth for the signal registry extraction, keeping the same modifiers (nature, mood, environment, variety, memory, momentum) but moving their math into plug-ins.
- **`AdaptiveGoal` subclasses and their `onStartGoal` / `onTickGoal` hooks** continue to drive look, movement, and pose updates. The planner feeds them fragment outputs, but the tick-level integration stays inside these existing classes so animation code paths remain untouched.

## Pillars

### 1. Signal Marketplace
- Refactor `calculateDesirability` and `calculateFeasibility` into registries of `DesirabilitySignal` and `FeasibilitySignal` plug-ins.
- Feed each signal from cached context slices that are marked dirty by the perception bus; recompute lazily when the owning stimuli or relationships change instead of on every tick.
- Share immutable signal outputs across pets that resolve to the same species/archetype context hash (e.g., same biome band, mood bucket, stimulus digest) so large packs reuse one cached result rather than duplicating work.
- Maintain deterministic ordering through keyed registration while allowing open-ended contributions (seasonal events, biome rarities, designer overrides).
- Extend suggestion outputs with per-signal traces for debugging, tuning, and offline analysis using the existing telemetry channel so no new logging system is required.
- Clamp and normalize aggregate multipliers to documented bounds before they reach the director to prevent runaway scores from third-party packs.

### 2. Enriched PetContext Snapshot
- Capture derived aggregates (nearby hostiles, friendly counts, closest entity distance) alongside existing owner proximity and entity lists.
- Maintain the snapshot incrementally: perception events update owner posture, crowd aggregates, and climate caches in-place, while idle ticks simply reuse the previous snapshot.
- Route dirty-flag updates through archetype caches first so matching pets can continue referencing a shared snapshot until their individual state diverges.
- Populate `activeEmotions` from the emotion sampler so emotion-sensitive signals become meaningful.
- Preserve behavioural history (recent goals, momentum, quirk counters) in the context instead of zeroing them during capture.
- Attach deterministic decay curves so cached data ages predictably between captures, amortizing work across event updates rather than rerunning reducers every frame.
- Prefer tapping existing component snapshots, vanilla data watchers, and previously-captured telemetry before introducing new storage to avoid duplication and keep cache coherence simple.

### 3. Unified Perception & Stimulus Bus
- Operate a single perception bus that ingests raw world callbacks (player posture, sounds, block changes) and emits typed stimulus samples; “stimulus” is just the payload carried by this bus, not a second system.
- Describe stimulus mappings in data so new sensors can be added without code while ensuring every mapping resolves to an event published on the same bus.
- Forward events from the existing `StateManager` owner-event frames and async batching pipelines so the bus piggybacks on infrastructure we already trust for mood, nature, and swarm updates.
- Mark dependent context slices and signal caches dirty as bus events arrive, allowing the runtime to coalesce updates and keep idle frames free of work.
- Feed a time-decayed `StimulusSnapshot` from the bus into `PetContext`, giving every signal and planner visibility into recent events without bespoke hooks or duplicate caches.

### 4. Social Relationship Graph
- Maintain a shared `SocialGraph` that tracks affinity, trust, and shared history between pets, owners, and nearby entities.
- Update relationship edges using perception stimuli processed through data-defined rules.
- Seed relationship updates with the existing `PetSwarmIndex` neighbor snapshots so crowd and owner-cohort knowledge flows straight into the graph without duplicating spatial queries.
- Expose lightweight queries (`affinityTo`, `sharedExperiencesWith`) inside `PetContext` so decision logic can adapt to changing relationships naturally.

### 5. Data-Driven Goal Catalogue
- Externalize goal metadata (category, cooldowns, energy bands, capability predicates) into data assets.
- Validate data packs against versioned JSON schemas during load; reject or quarantine malformed entries so they cannot corrupt runtime state.
- Generate or register `GoalType` entries and goal factories at load time, allowing designers to ship new behaviour packs via data.
- Let factories select `BehaviorVariant` pools based on context tags (biome, emotion, season).
- Allow transient situation detectors to mint temporary goal templates when emergent stimuli are observed.

### 6. Runtime Director
- Introduce a per-mob director that caches ranked suggestions and arbitrates with the vanilla `GoalSelector`.
- Expose simple brokerage hooks like `shouldActivate(GoalType)` that respect capability, priority, cooldown, and safety checks owned by `AdaptiveGoal`.
- Favor permissive fallbacks so stale caches degrade gracefully rather than blocking behaviours.
- Coordinate with group services to negotiate cooperative roles before activation.
- Resolve contention deterministically by always consulting the highest composite desirability score first; only data-authored weights influence ordering, keeping RNG optional and explicit when desired.

### 7. Generative Action Planning
- Replace one-class-per-behaviour execution with a deterministic planner that composes atomic `ActionFragment` primitives based on the active goal and context snapshot.
- Author fragments, preconditions, and effects in data so pets can synthesize novel sequences at runtime while remaining deterministic.
- Reuse the existing telemetry sinks (e.g., the async processing telemetry snapshots) to capture plan traces, execution outcomes, and signal contributions for offline tuning—no new logging infrastructure required.
- Cache and surface plan traces for debugging and iterative tuning.
- Memoize generated plans by context signatures (goal id, archetype hash, salient stimuli) with deterministic eviction so repeated scenarios pull from the plan cache before invoking the planner again.

### 8. Reusable Behaviour Variants
- Treat `BehaviorVariant` implementations as "motor primitives" with data-defined parameters (target focus, pose curves, pacing).
- Allow a single adaptive goal to spawn many visual outcomes by composing variants and tuning via data.
- Tag variants with taxonomy (pose family, locomotion type) so variety signals can balance selections without bespoke code.
- Drive variant choice from planner steps and social context to keep group scenes coherent.

### 9. Cooperative Group Coordination
- Cluster nearby pets into ad-hoc groups with shared `GroupContext` snapshots (membership, roles, shared targets).
- Broker cooperative goals defined in data so packs can negotiate hunts, play sessions, or protective formations without bespoke scripts, leaning on `PetSwarmIndex` cohorts for initial membership hints.
- Feed group outcomes back into the social graph to reinforce or dampen future collaboration.

### 10. Deterministic Feedback Loop & Tuning Pipeline
- Complete TODO hooks that record experiences and emotion feedback when behaviours finish.
- Store memory counters in `PetContext` so variety and recency signals reference real history.
- Capture scoring and execution telemetry through the existing analytics channel for offline tuning and emit deterministic weight tables that ship alongside data packs.
- Define transparent decay curves to keep learning effects predictable and reproducible while still allowing long-term preference shifts.
- Publish guardrails for telemetry sampling rates and data retention so tuning remains lightweight and privacy-safe.

### 11. Performance & Safety Governance
- Budget per-frame work with soft caps for perception dispatch, signal recomputation, and planning; instrument the runtime with counters that reuse the existing telemetry export path.
- Schedule heavy recomputations (planner rebuilds, group negotiations) on throttled queues triggered by events instead of fixed ticks, and expose profiling hooks to validate the system remains effectively free when idle.
- Document multiplier clamps, timeout thresholds, and sandboxing rules for plug-in code so third-party packs cannot introduce unbounded loops or destabilize the director.
- Respect Minecraft's existing activation range / inactivity timer (as other large mods do) by gating perception, planner dispatch, and group coordination behind LOD tiers: dormant entities outside the activation radius stay subscribed but never wake caches until vanilla marks them active.
- Provide a lightweight fallback tier for distant-yet-active pets (e.g., owner-mounted) that consumes precomputed archetype caches without running full planners, mirroring the level-of-detail patterns popular in Fabric/Forge creature mods.

#### Threading & Scheduling Guidelines
- Keep any interaction with `ServerWorld`, entity mutation, or vanilla `GoalSelector` APIs strictly on the main thread; wrap results produced off-thread in immutable payloads handed back to the main loop for application.
- Offload pure data processing—stimulus aggregation, signal recomputation, planner searches, and telemetry summarisation—to the existing async task framework (`AsyncWorkCoordinator`) where available, or to lightweight worker executors that never touch world state directly.
- Gate every async producer behind dirty-flag snapshots so workers reuse cached context slices and only emit new results when inputs change, ensuring the main thread pays O(1) to swap in fresh data.
- Leverage the mod’s existing telemetry batching (rather than new async loggers) for reporting perf counters and plan traces so background work stays consolidated.

## Quick Wins
- Cache owner intent signals (sprinting, sneaking, held item) in the context to unlock social and guard behaviours immediately, mirroring data from `OwnerAbilitySignalTracker` and similar event listeners so updates only fire when relevant events arrive.
- Summarize crowd pressure with lightweight aggregates maintained by event deltas, supporting plug-ins like "crowd discomfort" or "pack playfulness" without per-tick scans.
- Expose signal traces in the suggestion payload via the existing telemetry structures to accelerate balancing sessions and feed the tuning pipeline.
- Cache weather and time-of-day flags once per capture and mark them dirty only when the perception bus observes a sky-state change.
- Start routing minimal perception events through the bus so future detectors already have data to consume while proving the event-driven cache invalidation path.

## Implementation Phases
1. **Scoring Extraction**: Build signal interfaces, migrate existing multipliers, and add trace outputs.
2. **Event-Driven Context Backbone**: Stand up the perception bus, dirty-flag graph, and cache invalidation logic so context and signals update only when stimuli fire; reuse `StateManager` owner-event dispatch and `PetSwarmIndex` movement updates as the primary feeders; prove idle frames remain free.
3. **Context & Perception Expansion**: Broaden `PetContext`, populate social/emotion snapshots, hook the incremental updates into relationship and climate caches, and wire LOD signals from the existing activation-range hooks to keep dormant pets cold.
4. **Goal & Fragment Data Externalization**: Author schemas, loaders, and registration paths for goal metadata, action fragments, and situation detectors with validation tooling.
5. **Director & Planner Integration**: Insert the director between scoring and `GoalSelector`, wire brokerage hooks, route activations through the deterministic planner with throttled scheduling, and introduce deterministic plan caches so repeated scenarios avoid recomputation.
6. **Variant & Group Composition**: Parameterize variants, connect them to planner steps, and introduce group coordination services governed by safety clamps, sourcing membership hints from `PetSwarmIndex` cohorts.
7. **Feedback, Governance & Tuning Completion**: Finalize experience recording, decay logic, social reinforcement, telemetry reuse, and the offline tuning pipeline backed by schema validation and performance watchdogs.

## Success Metrics
- Adding a new influence, stimulus, or goal requires only data or plug-in registration—no core loop edits.
- Behaviour variety and cooperative interactions increase measurably (e.g., unique plan fragments executed per hour, group plans completed per session) without sacrificing deterministic playback.
- Designers can package behaviour packs that load cleanly, pass schema validation, and automatically participate in scoring, planning, and execution.
- Logged telemetry (reusing the existing analytics export) supports iterative tuning with reproducible before/after comparisons while staying within the defined performance budgets.
- Idle scenarios show near-zero CPU impact in profiling captures, confirming the event-driven backbone.
- Plan-cache hit rates remain high for repeated scenarios, and archetype cache reuse scales linearly with pack size without increasing per-entity cost.

## Testing Strategy
- Unit tests for individual signals to ensure deterministic multipliers given a fixed `PetContext` and to verify clamped output ranges.
- Property tests for the perception bus and social graph to confirm deterministic stimulus aggregation, incremental updates, and idle stability.
- Snapshot tests for director and planner decisions to verify stable activation orderings and plan assembly under mocked contexts without unintended recomputation.
- Integration tests that load behaviour, fragment, and group plan data packs to validate automatic registration, cooperative execution, telemetry reuse, and schema enforcement.
- Performance harnesses that replay bursty perception traces to ensure the event-driven caches coalesce work and respect frame-time budgets.
- Regression captures that toggle vanilla activation ranges and chunk visibility to confirm LOD gates suppress work for dormant pets while still waking caches deterministically when entities re-enter scope.
- Planner cache tests that simulate repeated contexts to verify memoized plans are reused and invalidated correctly when dirty stimuli arrive.

