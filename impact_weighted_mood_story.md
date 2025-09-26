# Impact-Weighted Mood Story

## Concise Algorithm Snapshot
1. **Screen active emotions** using freshness and signal gates so only recently stimulated feelings participate.
2. **Synthesize a weight** for each candidate by multiplying intensity punch, persistence credit, simmer duration, regulation bias, social mirroring, and appraisal trust—each capped by telemetry-derived bounds.
3. **Apply opponent transfers and hysteresis** to bleed weight between antagonistic emotions and respect momentum so moods do not flicker.
4. **Normalise and label** the resulting vector, surfacing the dominant emotion cluster as the current mood while exposing the full blend for analytics and UI copy.

This keeps the selection purposeful: spikes get immediate influence, long-lived emotions accrue leverage, and the final mood emerges from raw emotional data without behaviour-state scaffolding.

## Planned Algorithm & Goal Alignment
- **Derive, don’t define:** Every stage consumes the emotion record’s self-learned metrics (intensity traces, cadence EMA, impact budgets) so the mood forms directly from the data pets already emit. No bespoke behaviour presets or activity flags are needed; existing gameplay events automatically feed the pipeline.
- **Purposeful weighting:** Screening isolates the emotions that are both fresh and materially present, ensuring the blend focuses on what the creature is demonstrably feeling *now*. The multiplicative weight synthesis then lets simmering feelings and rekindled spikes stack in a transparent way—each factor explains *why* a mood contributor matters (e.g., “Fear stayed elevated for 90s, so its persistence credit is high”).
- **Balanced interpretation:** Opponent transfers and momentum guards keep the system from thrashing when opposing emotions fire together or when successive updates are similar. This stabilises the chosen mood while still letting new spikes break through if their evidence is strong.
- **Actionable output:** Normalisation produces a full weight vector that UI, analytics, or narrative systems can read. Because the values are grounded in the same multipliers used during evaluation, designers can backtrack which levers (duration, appraisal confidence, contagion) elevated a mood and adjust the underlying signals rather than hand-tuning arbitrary states.
- **Live palette:** Each core emotion now owns authored base/accent colours. The interpretation layer exports the top weighted stops as a palette so UI can breathe gradients that reflect the real emotional blend instead of static swatches.

Collectively, the algorithm achieves the goal of a pet-agnostic, low-overhead mood selector by letting intensity spikes, simmering durations, and contextual trust all speak through one derived weight without per-tick loops or hard-coded behavioural lenses.

## Purpose & Philosophy
Design a pet-agnostic mood selector that stays grounded in observed emotion data while remaining light on runtime cost. The system should:
- Reflect **how intense** each emotion feels and **how long** it has been active.
- Let impactful spikes and slow burns naturally influence the mood blend.
- Avoid per-tick bookkeeping; rely on lazy decay and event-driven updates.
- Tie every lever to established behavioural science, keeping the math derived from actual signals rather than manual presets.
- Reuse only **internal telemetry** (emotion enums, owner commands, relationship meters) that already lives in memory—no world scripts or bespoke state machines.

- **Integration & Data Sources**
  - **Event inputs:** Hook into the same emotion events, appraisal tags, and relationship telemetry already emitted by combat, bonding, and exploration systems. The algorithm consumes these payloads without requesting additional world queries.
  - **Stored metrics:** Duration, cadence, and percentile stats reuse the timestamps, EMAs, and impact budgets cached on each emotion record. No new global counters or map scans are required.
  - **Bond & care ledger:** Reuse `bondStrength`, rolling pet-counts, and last-petting timestamps from the pet component to derive a lightweight **relationship guard**. A bounded z-score (`bondZ`) and a decayed "recent care" multiplier (`carePulse`) inject long-term trust without authoring role-specific constants; both values clamp inside `[0.75, 1.25]` before influencing any weights.
  - **Owner combat snapshot:** Sample the existing combat diary—ticks since last hit, under-fire streaks, mount status—to compute a **danger gating factor**. The gate only activates when the moving average of danger telemetry exceeds historical medians, and it fades using the same cadence EMA already tracked per emotion so no extra polling is required.
  - **Optional context:** When narrative or UI layers need extra colour, they read the normalized mood vector and existing metadata (valence/arousal, owner proximity flags) rather than toggling scene state. Designer nudges adjust appraisal inputs; they never patch world objects.
  - **Persistence:** Everything runs inside the emotion subsystem’s data structures, so saving/loading keeps behaviour consistent without touching level scripting or pet-specific behaviour graphs.

## Psychological Grounding
Each mechanic maps to well-studied affect models so designers can reason about outcomes:
- **Core Affect Circumplex (Russell)** anchors emotions on valence/arousal axes so they can blend without behaviour states.
- **Appraisal Theory (Lazarus, Scherer)** uses goal relevance, controllability, novelty, and social proof to gate spike size.
- **Affective Chronometry (Davidson)** inspires the latency, rise, and recovery tracking baked into cadence-aware decay.
- **Peak–End Rule (Kahneman)** ensures sharp spikes and recent samples get non-linear emphasis.
- **Opponent-Process Theory (Solomon)** motivates weight transfers between antagonistic emotions.
- **Hedonic Adaptation & Homeostasis** guide slow baseline drift (`homeostasisBias`).
- **Habituation & Sensitisation** inform capped impact budgets and rekindle boosts.
- **Broaden-and-Build (Fredrickson)** justifies letting sustained positive affect bias future appraisals upward.
- **Emotional Contagion & Social Baseline Theory** explain the optional mirrored weight from bonded allies.

## Emotion Record
Active emotions are stored as lightweight records so the selection pass can stay O(emotions):

| Field | Description |
| --- | --- |
| `id` | Emotion identifier aligned with existing enums. |
| `intensity` | Latest sampled strength (0–1). |
| `startTime`, `lastEventTime` | Bound duration and freshness without polling. |
| `impactBudget` | Accumulated credit from spikes/rekindles, clamped to a rolling percentile cap. |
| `cadenceEMA`, `volatilityEMA`, `peakEMA` | Self-tuning estimates of event pacing, turbulence, and high-percentile intensity. |
| `habituationSlope` | Tracks how quickly the emotion decays back to baseline (hedonic adaptation). |
| `sensitisationGain` | Captures how much repeated spikes grow stronger over short windows. |
| `homeostasisBias` | Slow drift toward/away from baseline affect, bounded by observed variance. |
| `contagionShare` | Optional mirrored influence from allies, capped by relationship data and affiliation strength. |
| `relationshipGuard` | Cached `[0.75, 1.25]` multiplier derived from bond z-score and recent care pulse; decays automatically. |
| `dangerWindow` | Rolling danger gate sourced from owner combat diary (under-fire streak, mounted retreat). |
| `appraisalConfidence` | Cached trust in recent appraisals, 0–1. |
| `valence`, `arousal` | Static metadata for labelling, no runtime cost. |

Inactive emotions are discarded; derived caps (`impactCap`, `biasCap`, `contagionCap`) refresh via rolling statistics so the system scales with lived experience instead of fixed tables.

### Stability & Boundedness
- **Shared cap budget:** `impactCap`, `biasCap`, and contagion caps draw from a joint rolling window so the sum of long-tail modifiers cannot exceed twice the 95th percentile of historical total weight.
- **Guard rails:** Relationship and danger multipliers only activate after passing rolling z-score gates (`|z| > 0.75`) and cool down via the same half-life math as emotions, preventing constant recomputation while keeping them responsive.
- **Order of updates:** Apply decay → appraisal multiplier → contagion mirroring → rekindle boosts → adaptation/sensitisation adjustments in that order to avoid double-counting.
- **Clamp blending:** Whenever multiple slow-moving fields (homeostasis, habituation, sensitisation) combine, compute a weighted average using normalized confidences rather than raw addition.

## Event-Driven Update Flow
Hook into existing gameplay events—damage, praise, discovery, bonding—so there is no per-tick loop:
1. **Decay on demand** using cadence-aware half-life:
   ```
   cadenceEMA   = updateEMA(cadenceEMA, deltaTime, cadenceAlpha)
   adaptiveHalf = clamp(cadenceEMA * halfLifeMultiplier, minHalfLife, maxHalfLife)
   decayRate    = ln(2) / adaptiveHalf
   impactBudget = clamp(impactBudget * exp(-decayRate * deltaTime), 0, impactCap)
   ```
   Homeostasis and bias caps update with similar rolling-stat clamps so runaway values stay bounded.
2. **Derive appraisal multiplier** from gameplay context (goal relevance, controllability, novelty, social proof). Combine signals into `[-1,1]`, pass through `1 + 0.5 * tanh(score)` to stay within `[0.5,1.5]` without authored constants. Suggested signal sources:

   | Dimension | Gameplay Signals | Notes |
   | --- | --- | --- |
   | Goal relevance | Quest objectives, owner commands, survival checks | High relevance if success/failure directly affects core loop. |
   | Controllability | Recent success rate of mitigation actions | Use moving win/loss ratio to keep values derived. |
   | Novelty | First-time vs repeated events, discovery logs | Down-weights habituated stimuli automatically. |
   | Social proof | Ally reactions, crowd sentiment telemetry | Tie into existing companion emotion broadcasts. |

3. **Blend incoming sample** with contagion and rekindle effects:
   ```
   relationshipGuard = clamp(lerp(relationshipGuard, 1 + bondZ * guardScale, guardAlpha) * carePulse, 0.75, 1.25)
   dangerWindow      = clamp(lerp(dangerWindow, 1 + dangerZ * dangerScale, dangerAlpha), 0.75, 1.35)
   contagionFactor = clamp(1 + contagionShare, 0, 1 + contagionCap)
   adjustedSample  = clamp01(newSample * appraisalMultiplier * contagionFactor * relationshipGuard * dangerWindow)
   intensity       = clamp01(lerp(intensity, adjustedSample, spikeBias))
   impactBudget    = clamp(impactBudget * rekindleGain + spikeImpulse + homeostasisBias * regulationGain, 0, impactCap)
   habituationSlope = clamp(lerp(habituationSlope, adaptiveHalf, habituationAlpha), minHalfLife, maxHalfLife)
   sensitisationGain = clamp(lerp(sensitisationGain, 1 + volatilityEMA, sensitisationAlpha), 1, sensitisationCap)
   ```
   - `guardScale`, `dangerScale`, and lerp alphas pull from the 80th percentile of historical bond/danger deltas, so the guard rails self-tune per pet without authoring tables.
   - `spikeBias`, `spikeImpulse`, and `rekindleGain` derive from cadence, volatility, and percentile history so simmering emotions reignite proportionally to how they usually behave.
4. **Update duration helpers** only when requested; no work occurs between events. When a consumer queries the mood after a long idle period, run a **catch-up decay** that applies the stored `habituationSlope` to ensure weights ease down smoothly instead of dropping suddenly.

## Mood Interpretation Layer
Run this sequence whenever a consumer asks for the mood (UI refresh, analytics sample, slow heartbeat). The backend emotion accrual stays untouched—the interpretation layer simply reads the data that subsystem already tracks.

### Interpretation Goals
- **Frequent & strong** emotions should cut through and claim weight quickly.
- **Frequent & quiet** emotions should be dampened so background chatter stays supportive instead of dominant.
- **Old & strong** emotions should gracefully cool unless fresh evidence arrives.
- **New** emotions should get an immediate pulse, scaled by their spike strength, so meaningful surprises register without letting low-impact noise hijack the blend.

### 1. Candidate Screening
- **Freshness gate:** `freshness = exp(-lastEventAge / cadenceEMA)`; discard emotions with `freshness < 0.05` so stale highs stop steering the mood.
- **Frequency score:** `freq = clamp(cadenceRef / cadenceEMA, 0, freqCap)` where `cadenceRef` is the rolling median cadence of all tracked emotions. Frequent entrants > 1, sporadic ones fall toward 0.
- **Effective signal:** `signal = intensity * (0.35 + 0.65 * freshness) + 0.3 * sqrt(freq * impactBudget)` rewards emotions that are both active and repeatedly reinforced. Slow, quiet murmurs rarely pass the cut unless their simmering impactBudget proves they still matter.
- **Threshold:** retain emotions whose signal ≥ 60 % of the median (fallback to the maximum if that cull removes everything). This keeps the candidate set purposeful without hard-coding behaviour states.

### 2. Weight Synthesis
For each survivor, compute multiplicative factors that explicitly encode the four interpretation goals, then clamp the result to a telemetry-learned `weightCap` (≈95th percentile of historical weights):

| Factor | Formula | Purpose |
| --- | --- | --- |
| **Intensity punch** | `P = pow(intensity, gamma)` with `gamma = lerp(1.3, 2.4, intensity)` | Strong signals surge; frequent-but-quiet ones flatten toward 0. |
| **Frequency lift** | `F = 1 + freqBoost * smoothstep(freqMedian, freqHigh, freq)` with `freqBoost = 0.8 * sqrt(intensity)` | Frequent spikes gain leverage; quiet repetitions barely rise above 1. |
| **Quiet dampener** | `Q = smoothstep(quietFloor, quietCeil, intensity)^2` | Repeated low-level emotions stay soft unless they meaningfully exceed the quiet floor. |
| **Recency fade** | `R = exp(-max(0, lastEventAge - graceWindow) / recencyScale)` | Old emotions, even if once intense, fade without new corroboration. |
| **Persistence credit** | `C = 1 + clamp(impactBudget / impactCap, 0, 1)` | Sustained simmering still counts, counterbalancing the quiet dampener when the emotion genuinely stayed impactful. |
| **Adaptation balance** | `H = clamp(exp(-elapsed / habituationSlope) * sensitisationGain, 0.55, 1.45)` | Keeps long-term biases bounded while respecting sensitisation from repeated spikes. |
| **Context guards** | `G = relationshipGuard * dangerWindow * (0.75 + 0.5 * appraisalConfidence)` | Reuses bond, combat, and appraisal telemetry without inventing new behaviour variables. |

To ensure new emotions speak up immediately without overpowering the blend, add a novelty pulse derived from the same cadence stats:
```python
noveltyGate  = exp(-lastEventAge / noveltyHalfLife)
noveltyPulse = lerp(minNovelty, maxNovelty, intensity) * noveltyGate
rawWeight    = min((P * F * Q * R * C * H * G) + noveltyPulse, weightCap)
```
`noveltyHalfLife` reuses the cadence-derived half-life; `minNovelty`/`maxNovelty` draw from the 20th/90th percentile of historical first spikes so low-impact newcomers barely register while meaningful spikes add real weight immediately.

`freqMedian`, `freqHigh`, `quietFloor`, `quietCeil`, and `recencyScale` all come from the same rolling distribution trackers the backend already maintains (median cadence, intensity percentiles, and catch-up decay). No new tuning tables are required; designers adjust behaviour by shaping the underlying emotion emissions.

### 3. Opponent Transfer & Momentum Guard
1. **Opponent bleed:** For each antagonistic pair `(i, j)`, transfer `rawWeight[i] * O[i][j]` (≤35%) into the opponent and rebound a small share (`reboundGain`) based on telemetry. Process pairs in sorted order by combined weight so the largest conflicts resolve first and prevent oscillations.
2. **Momentum guard:** Compare the top candidate with the previous mood. If the difference is below `momentumBand = max(0.08, stddev(previousDominantWeights))`, keep the prior mood to avoid flicker.
3. **Modifier precedence:** Apply designer nudges *after* opponent bleed but *before* normalization so overrides remain transparent and bounded.

### 4. Normalise & Label
- Divide by the total weight (epsilon guarded) so the blend sums to 1.
- Map the dominant cluster to a mood label using static content metadata (e.g., Joy+Playful → “Bubbly”, Fear+Protectiveness → “On Guard”). Maintain the mapping in data files that include expected contributor ratios, ensuring new content validates against historical blends before shipping.
- Expose top-three contributors, raw versus adjusted weights, and factor breakdown for analytics and UI.

## Performance & Tooling Notes
- All heavy math (EMA, percentile tracking, decay) occurs only on emotion events.
- Evaluation is O(candidate emotions) with a handful of multiplies, logs, and exponentials.
- Relationship and danger guards piggyback on the same EMA/cap infrastructure, so we avoid extra per-tick loops while still tapping into bond history and owner combat context.
- Designers bias behaviour by emitting events or adjusting appraisal inputs—no hard-coded mood states.
- Logging the normalized weight distribution helps validate that spikes, simmering feelings, and rekindles balance as expected.
- Provide a lightweight telemetry dashboard that tracks the composite multipliers (`P`, `F`, `Q`, `R`, `C`, `H`, `G`, novelty pulse) so designers can spot conflicts early.

## Behavioural Enhancements
- **Mood momentum:** Blend the previous normalized vector with the new one using the hysteresis band as a smoothing factor.
- **Anticipatory spikes:** When telegraphed events loom, emit low-intensity, short-lived spikes derived from existing forecast systems to reflect prospect theory without polling.
- **Recovery windows:** Temporarily reduce spike responsiveness after regulation pushes an emotion below its ember threshold, mirroring affective refractoriness. The reduction factor comes from historical recovery duration per emotion and the `habituationSlope` term.
- **Narrative annotations:** Optional labels watch for weight patterns (“Alert” when fear + protectiveness > 0.6) without touching the base math.

The result is a single, impact-weighted algorithm that derives mood directly from emotional intensity and duration, honours validated behavioural science, and stays lightweight enough for live gameplay.

## Effectiveness at Separating Dominant Signals from Background Calm

The weighting steps intentionally differentiate between urgent, high-energy emotions and steady-but-gentle backdrops so the pet reacts to the right drivers without erasing supportive context:

1. **Freshness gate prioritises active sparks.** A serene backdrop may hold a high raw intensity because the owner is near, but if another emotion fires in rapid succession the cadence-aware freshness multiplier plus the frequency score keep those active signals above the median threshold while older, unchanging samples fade toward the cut line. The background feeling still remains if its effective signal exceeds the median, yet its relative weight is tempered by the same freshness math that elevated the spike.
2. **Frequency lift and quiet dampener spotlight volatile signals.** Each reinforced surge pushes its `freq` score upward, so `F` and `Q` compound alongside the persistence credit when the emotion continues to pulse. Passive emotions that lack spike impulses stay near the quiet floor unless their simmering impact budget proves otherwise, meaning they provide a stable floor rather than the dominant peak.
3. **Opponent transfers resolve conflicts deterministically.** Antagonistic pairs derive from valence/arousal metadata, not hand-authored emotion lists, so whichever high-arousal alert emotion is present bleeds a capped portion of weight away from whichever low-arousal counterpart co-occurs. The calmer feeling still exists—its residual weight is visible in the blend—but the current mood tilts toward the high-impact cluster because that pattern wins the normalized sum.
4. **Momentum guard preserves situational awareness.** If combat persists, the previous mood already being defensive keeps the hysteresis band tight. Calm would need a meaningful rise in relative weight to reclaim dominance, mirroring how pets stay keyed-up until the environment stops generating high-impact spikes.

In practice, this means background serenity continues to influence the blended vector (useful for UI copy like “Resolute yet reassured”), but the decision logic recognises that whichever emotion currently demonstrates the strongest evidence—via intensity, cadence, and rekindle credit—surfaces as the leading mood driver. The same gates and dampeners apply to any low-impact backdrop, regardless of label, so the algorithm measures whether a feeling behaves like background noise by watching its telemetry rather than by hard-coding specific emotions.
