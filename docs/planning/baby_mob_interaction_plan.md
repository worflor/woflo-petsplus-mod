# Pet Interaction Differentiation Plan: Baby vs. Adult Mobs

## Goals
- Detect nearby mob age states through the existing `PetContext` capture so adaptive goals can react without bespoke scans.
- Layer age-aware modifiers into the adaptive goal framework without rewriting individual behaviors from scratch.
- Ensure interaction policies stay extensible for modded entities while providing sensible fallbacks when age data is missing.
- Maintain the current lightweight tick cost by reusing existing caches and avoiding extra world queries.

## Codebase Touchpoints
1. **Context Capture (`woflo.petsplus.ai.context` package)**
   - Extend `PetContextCrowdSummary` to track separate counts/distances for baby versus adult entities, keyed by temperament (friendly/passive vs hostile vs neutral).
     - Implementation detail: update `PetContextCrowdSummary.fromEntities` to branch on `entity instanceof PassiveEntity`, `Monster`, and `LivingEntity.isBaby()` while retaining current distance caching and avoiding any extra entity scans.
   - Add a lightweight `NearbyMobAgeProfile` record (in the same package) that aggregates only the values we actually read from goals:
     - `int babyFriendly`, `int babyHostile`, `int babyNeutral` counts.
     - `double nearestBabyDistance` (default to `Double.POSITIVE_INFINITY` when none found).
     - `UUID nearestBabyId` (mirrors the existing crowd summary pattern for cheap lookups without keeping entity references).
   - Populate the profile inside `PetContext.captureFresh` using the same entity pass already performed for `PetContextCrowdSummary`. Because we compute the values inside that loop we avoid any extra allocations per tick.
   - Update `PetComponent` cached snapshots to store the new profile alongside the existing crowd summary, reusing the same invalidation paths so we do not introduce new timers or cache fields.

2. **Adaptive Goal Utilities (`woflo.petsplus.ai.goals` package)**
   - Add helper methods on `AdaptiveGoal` that simply forward to the cached profile, ensuring goals never trigger additional proximity scans:
     - `protected boolean isBabyMobNearby(Predicate<Entity> filter, double maxDistance)` consults the `NearbyMobAgeProfile` and, only when the profile is missing, falls back to the existing `getContext().nearbyEntities()` collection.
     - `protected double nearestBabyMobDistance()` returning the precomputed distance or `Double.POSITIVE_INFINITY`.
   - Reuse the current stance/intent enumerations (e.g., role-based tendencies already in `PetAIEnhancements`) instead of introducing a brand-new enum, keeping the surface area tight.

3. **Interaction Intent Mapping (`PetAIEnhancements` minimal hook)**
   - Avoid a heavy new service layer; extend the existing role tuning inside `PetAIEnhancements` to compute a compact `PetMobInteractionProfile` once per initialization tick:
     - Method signature sketch: `PetMobInteractionProfile createMobInteractionProfile(MobEntity pet, PetComponent component, PetContext context)`.
     - The profile remains a simple record of booleans/distances already used by the goals (`shouldApproachBabies`, `maintainBuffer`, `softenAnimations`), keeping allocation count minimal.
     - Defaults reuse the role-based checks already in `applyRoleSpecificAI`, so we only add a couple of conditionals rather than an entirely new type hierarchy.
   - Store the profile on `PetComponent` (e.g., `setMobInteractionProfile`) right after context capture and reuse it until the cached context invalidates, matching current cache cadence.

4. **Goal-Level Refinements (initial pass)**
   - `ParallelPlayGoal`: leverage `nearestBabyMobDistance()` to narrow play arcs when a baby mob is within 2 blocks and reuse existing emote toggles so no new animation paths are required.
   - `LeanAgainstOwnerGoal`: before entering the lean state, query `isBabyMobNearby(entity -> entity instanceof PassiveEntity, 1.5D)` to avoid squashing baby mobs between pet and owner. If the check fails, delay goal execution via the existing cooldown field rather than introducing new timers.
   - `PerchOnShoulderGoal`: read the cached `PetMobInteractionProfile` flag; when `maintainBuffer` is true, reuse the current hesitation timer multiplier instead of adding new counters.
   - Future optional goals: `CrouchApproachResponseGoal`, `OrbitSwimGoal` can adopt the same helpers without duplicating detection, keeping the footprint consistent.

5. **Signals and Planner Integration**
   - Update `SocialProximityFeasibilitySignal` to short-circuit when baby hostility risk is high (e.g., hostile babies from other mods) using the new profile data.
   - Add a desirability tweak in `AgeDesirabilitySignal` so young pets prioritize baby-friendly interactions while mature pets remain cautious.
   - Ensure `DeterministicPlanner` signatures include an age-aware hash component so plan caching respects baby proximity shifts.

## Interaction Matrix (Revised)
| Mob Temperament \\ Age | Baby Mob Behavior | Adult Mob Behavior |
|-------------------------|-------------------|--------------------|
| Friendly / Passive (e.g., wolves, villagers) | Playful emotes at distance ≤ 2, defer to `PLAYFUL` stance. Guardians guard perimeter but no pushing. | Maintain existing friendly behaviors. |
| Neutral (e.g., llamas) | Observe quietly, only approach if `PetMobInteractionProfile.shouldApproachBabies` is true. | Use existing caution thresholds. |
| Hostile (e.g., zombies) | `PROTECTIVE` stance: pets position between owner and baby mobs, avoid first strike unless owner harmed. | Follow current defensive/assist combat logic. |
| Tiny companions (e.g., allays) | Mirror friendly play with reduced speed; rely on `SOFT` animation tone. | Use adaptive play goals as-is. |

## Implementation Steps
1. **Data Model Updates**
   - Modify `PetContextCrowdSummary` and add `NearbyMobAgeProfile`. Write unit tests verifying counts/distances for mixed baby/adult mobs using mock entities or minimal test harnesses.
   - Extend `PetComponent` caching to persist the new profile and invalidate when crowd caches refresh.
   - Run a quick micro-benchmark (existing tick profiler harness) to confirm the additional bookkeeping does not introduce measurable overhead.

2. **Profile Hook-Up**
   - Implement the compact `PetMobInteractionProfile` record and the helper factory inside `PetAIEnhancements`.
   - Update `PetAIEnhancements.enhancePetAI` to instantiate and cache the interaction profile prior to adaptive goal initialization, reusing existing component setters.

3. **Goal Adjustments**
   - Update `ParallelPlayGoal`, `LeanAgainstOwnerGoal`, and `PerchOnShoulderGoal` to consume helper methods and the new interaction profile fields. Ensure we respect existing cooldown and state machines when deferring actions.
   - Add regression tests (where feasible) for each goal's `canStart`/`shouldContinue` methods using fabricated contexts to validate baby-aware gating.

4. **Planner/Signal Tweaks**
   - Modify `SocialProximityFeasibilitySignal` and `AgeDesirabilitySignal` to consider baby counts. Guard for null profiles to avoid NPEs.
   - Update `DeterministicPlanner#createSignature` to include a normalized snapshot of `NearbyMobAgeProfile` to prevent stale plan reuse when baby mobs enter/exit range, but keep the hash inputs trimmed to the three count fields plus nearest distance.

5. **Validation & Tooling**
   - Introduce a debug command or log toggle within the new profile factory to dump the computed stance for nearby mobs (limited to dev builds) without persisting extra state.
   - Perform manual playtests with baby villagers, baby zombies, and baby passive mobs to confirm stance transitions and ensure no AI stalls occur.

## Risks & Considerations
- `LivingEntity.isBaby()` is only defined on `AgeableEntity`; for mods that do not expose age, the helper must fall back gracefully and treat the mob as adult.
- Additional context fields will increase the footprint of `PetContext`; caching strategy must remain light to prevent tick stutters.
- Ensure the new profile helpers respect server/client separation—logic should live server-side, with client synchronization limited to cosmetic cues if needed.

## Next Steps
- Finalize the data model changes and land unit tests.
- Implement the profile factory hook and wire it through `PetAIEnhancements`.
- Incrementally update targeted goals, validating each in isolation before expanding to the rest of the social suite.
- Fold planner/signals changes into the same review so we can measure cache churn alongside goal tweaks.

## Completeness Checklist
- [x] **Data capture:** Context snapshots now account for baby/ adult splits without extra scans.
- [x] **Caching:** New records (`NearbyMobAgeProfile`, `PetMobInteractionProfile`) reuse existing invalidation paths.
- [x] **Goal integration:** Priority social goals consume the helpers without introducing new timers.
- [x] **Planner & signals:** Deterministic planner and desirability/feasibility signals react to the age-aware profile.
- [x] **Validation path:** Unit tests, profiler runs, and manual playtests are planned so we can confirm no regressions.
