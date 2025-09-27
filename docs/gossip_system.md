# Gossip System Design

## Overview
- Extend each pet's social memory with a structured gossip ledger that stores rich rumor entries instead of only timestamps.
- Rumors influence emotions and surface contextual cues so the player experiences flavorful chatter that feels grounded in pet interactions.
- Same-species exchanges transmit vivid, high-confidence stories, while cross-species encounters mostly convey mood and partial information.

## Data Model
- Introduce `PetGossipLedger` to live alongside existing `PetComponent` state data.
- Ledger holds `RumorEntry` records with fields such as:
  - `topicId`
  - `intensity`
  - `confidence`
  - `lastHeardTick`
  - `sourceEntityId`
  - Optional `understoodAs` paraphrase for cross-species misinterpretations
- Provide helpers on `PetComponent`:
  - `getGossipLedger()`
  - `recordRumor(topic, strength, currentTick, source)`
  - `streamRumors()`/`hasRumor(topic)` for quick queries
  - `decayRumors(long currentTick)` to fade stale entries
- Persist ledger via dedicated NBT compound (`"gossipLedger"`) inside `writeToNbt`/`readFromNbt`.

## Gossip Triggers & Events
### Socialization Circles (Pack Gossip)
1. Use existing neighbor analytics in `applySocialEffects(...)` to detect when pets form a small or large pack.
2. Fire a short-lived `GOSSIP_CIRCLE` task on each participant when the pack branch activates.
3. Choose a storyteller via bond strength or age cached in `PetSocialData`.
4. While active, the leader pushes fresh rumors into nearby ledgers, emits contextual cues, then lets pets drift back to normal tasks.

### Ritualized Gossip Gatherings
- **Nightly Campfire Circle**
  1. Detection: scan for shared points of interest (POI) tagged as `campfire_hub` or for an active campfire block within the pack radius during dusk/night ticks.
  2. Behavior: enqueue a `GOSSIP_CIRCLE_CAMPFIRE` variant that keeps pets seated/loitering for a few seconds while the storyteller retells high-trust rumors with lingering warmth.
  3. Player cues: trigger `EmotionContextCues.sendCue(petsplus.emotion_cue.social.gossip.cozy_fire)` paired with relaxed/confident emotions so players read it as bedtime chatter.

- **Communal Feeding Banter**
  1. Detection: whenever multiple pets share a feeding trough, bowl, or owner-thrown food item tagged with a shared POI, mark the moment as a feeding ritual.
  2. Behavior: spawn a `GOSSIP_FEEDING` task that piggybacks on eating animations while swapping recent practical rumors (e.g., danger, resource finds) and prioritizing high-intensity entries.
  3. Player cues: surface lively/alert emotion mixes—pride for the teller, curiosity for listeners—so the scene reads as animated table talk.

- **Grooming & Rest Huddle**
  1. Detection: watch for scheduled rest periods or simultaneous use of grooming interactions (existing calm emotes) inside dens marked with a `resting_spot` POI.
  2. Behavior: kick off a `GOSSIP_GROOMING` task that emphasizes low-intensity, high-confidence whispers while pets idle, reinforcing bonds and slowly reinforcing rumor confidence.
  3. Player cues: emit soft, affectionate emotions (contentment, trust) with a cue like `petsplus.emotion_cue.social.gossip.grooming` to signal gentle, intimate sharing.

Each ritualized variant inherits the base circle mechanics but swaps contextual cues, emotion blends, and rumor weighting to match the activity so the player intuitively understands why pets gathered.

### Walk-Up Whisper (One-on-One Gossip)
1. Hook into `processAdvancedSocialDynamics` reunion/first-meeting logic that already runs every 120 ticks.
2. On meeting, schedule a `WHISPER` exchange: merge relevant rumor entries, apply emotional reactions, reset `social_memory_*` data, and allow pets to continue their preexisting goals.

### Rumor Creation Hooks
- Define canonical gossip topics (enum/registry) for notable events such as owner health, biome discoveries, or first-time meetings.
- In systems like `EmotionsEventHandler.addSocialAwarenessTriggers` and other notable triggers, record new rumors via the ledger helpers.

## Species-Based Comprehension
- Maintain a `GossipDialect` map keyed by `EntityType` that scores comprehension per topic.
- Same-species pets receive full clarity and intensity.
- Closely related species get dampened details; unrelated entities mostly feel the emotional tone.
- When propagating cross-species rumors, store dampened/paraphrased variants while still applying mood contagion for emotional bleed-through.

## Feedback & Decay
- Model a rumor lifecycle with explicit stages:
  - **Spark:** rumor is first recorded; intensity spikes while confidence starts low. Trigger excited/surprised emotions and cues like `...gossip.new`.
  - **Circulate:** repeated exchanges increase confidence when corroborated, slightly dropping intensity to reflect normalization. Pets show curious/animated moods.
  - **Confirm/Dispel:** ledger comparisons during exchanges push confidence to 1.0 when multiple sources agree (rewarding consensus with celebratory cues), or damp intensity and confidence sharply when contradictions arise (triggering doubtful/skeptical emotions).
- During gossip exchange helpers, compare overlapping rumor entries from both ledgers to automatically bump confidence when stories align or flag `contradicted` status when they clash.
- When contradictions surface, emit debunk cues (e.g., `petsplus.emotion_cue.social.gossip.debunked`) and apply confusion/embarrassment moods so players notice the rumor collapsing.
- When confirmation occurs, push pride/reassurance emotions and celebratory cues (e.g., `...gossip.confirmed`) so validation feels rewarding.
- During player tick handling (or a scheduled task), call `decayRumors` to naturally expire old gossip and throttle repeated chatter, resetting rumors to idle/expired state once confidence and intensity fall below thresholds.

## Debugging & Tuning
- Consider a `/petsplus gossip <pet>` debug command to dump active rumors for balancing and QA.
- Watch ledger sizes and decay timing to keep performance stable and avoid runaway rumor proliferation.
