# Pet Nature System Story

## Goals
- Layer evocative, lore-friendly personalities on top of the existing breeding metadata without fighting vanilla breeding loops.
- Reward attentive breeders with contextual stat nudges and upbeat behavioral quirks instead of raw abilities or gameplay debuffs.
- Keep the system data-driven so designers can extend or retune natures without Java patches.

## Birth context telemetry
Every newborn pet already records a rich snapshot when the breeding handler fires:
- The handler resolves parents, establishes ownership, and captures a `BirthContext` payload containing world time, time of day, dimension key, indoor status, weather flags, and nearby witness counts before storing it on the child component.【F:src/main/java/woflo/petsplus/events/PetBreedingHandler.java†L45-L111】
- That snapshot is persisted under dedicated `PetComponent.StateKeys`, alongside genealogy fields such as parent UUIDs, inherited role markers, and stat inheritance flags, so other systems can query or display the same data without re-running world checks.【F:src/main/java/woflo/petsplus/state/PetComponent.java†L103-L135】

These hooks give the nature picker deterministic access to when, where, and how a pet entered the world.

## Nature selection flow
1. **Evaluate candidates:** Each registered nature supplies a predicate over the stored birth context (and any lightweight extensions such as moon phase or local block tags). During the newborn’s post-birth tick, the handler evaluates natures in priority order until one claims the child.
2. **Apply stat lean:** Each claimed nature supplies a major (≈6%) and minor (≈2–3%) multiplier within the existing ±15% characteristic envelope so that long-term breeding still respects vanilla balance while letting dedicated players bias a lineage.
3. **Stamp flavor cues:** The chosen nature sets a quirk tag for the mood engine, enabling cosmetic emotes, sound barks, or idle preferences that reinforce the personality without imposing penalties or active abilities.
4. **Persist & broadcast:** The selected nature id is written to the child’s component, surfaced through the breed event payload, and available for UI, lore books, or advancement triggers.

## Nature roster (20 one-word archetypes)
| Nature | Trigger concept | Major buff | Minor buff | Quirk |
| --- | --- | --- | --- | --- |
| **Radiant** | Born outdoors during clear daylight | +6% movement speed | +3% vitality | Basks in warm beams and breaks into breezy sun-trot emotes whenever skies clear. |
| **Nocturne** | Night birth under a full moon phase | +5% agility | +2% focus | Glides through moonlit patrols and radiates Quiet mood beats after dusk. |
| **Hearth** | Indoors with a nearby bed or campfire | +6% defense | +3% loyalty | Nests beside cozy blocks while resting and spreads a Gentle warmth to stablemates. |
| **Tempest** | Rain or thunder active at birth | +6% attack | +3% vitality | Shakes off raindrops with delighted chirps whenever storms roll overhead. |
| **Solace** | No players or pets within the witness radius | +5% vitality | +2% defense | Hums a low tune when left alone, keeping nearby pets settled instead of spooked. |
| **Festival** | At least 4 players or 6 pets witnessing | +5% loyalty | +3% speed | Kicks up celebratory sparks during group meetups and nudges Jubilant mood pulses. |
| **Otherworldly** | Dimension is neither Overworld nor Nether | +5% vitality | +2% agility | Carries a faint portal shimmer and drifts toward Yūgen mood surges near gateways. |
| **Infernal** | Dimension key is the Nether | +6% health | +3% attack | Trails tiny embers when excited and kindles Warmth mood spikes around basalt and lava. |
| **Echoed** | Deep Dark biome at birth | +6% defense | +3% focus | Listens for sculk vibrations and sustains Vigilant mood tones in darkness. |
| **Mycelial** | Mushroom Fields biome cradle | +6% health | +2% vitality | Releases gentle spore puffs while resting, seeding Cheerful moods for companions. |
| **Gilded** | Valuable ore blocks detected beneath the nest | +5% focus | +3% agility | Taps its paws after mining hauls and heightens Curious mood echoes. |
| **Gloom** | Indoors with skylight ≤2 and no valuables nearby | +5% agility | +2% defense | Moves quietly through tunnels and keeps Stealthy mood cues active underground. |
| **Verdant** | Surrounded by lush foliage or crops | +5% vitality | +3% health | Rustles leaves in playful circles and coaxes Verdant mood blooms from nearby pets. |
| **Summit** | Birth height ≥ Y=100 with open sky | +6% speed | +3% agility | Performs short skyline hops and radiates Exultant moods atop high perches. |
| **Tidal** | Fully submerged ocean-biome birth | +6% swim speed | +3% health | Trails bubble rings when content and inspires Buoyant mood ripples underwater. |
| **Molten** | Lava or magma blocks within range | +6% attack | +2% defense | Shakes off sparks and keeps Tempered mood embers glowing near magma. |
| **Frost** | Snowing biome or powder snow contact | +6% defense | +3% speed | Exhales frosty breath plumes and stirs Crisp mood breezes in wintry zones. |
| **Mire** | Mud, mangrove, or swamp tags nearby | +5% health | +3% vitality | Splashes through muck with delight, spreading Mirthful mood ripples across wetlands. |
| **Relic** | Generated over a major structure (e.g., Stronghold) | +5% focus | +2% defense | Chimes softly near carved stonework and kindles Insightful mood sparks when secrets lurk. |
| **Untamed** | Neither parent had an owner at birth | +6% speed | +3% agility | Ranges wide before circling back and shares Adventurous mood surges during long treks. |

### Tuning guidelines
- Keep multipliers modest and positive; natures should feel like flavorful lean-ins rather than mandatory power picks.
- Favor predicates that piggyback on existing birth context data to minimize extra block scans. Only specialized archetypes (e.g., ore checks, moon phase) should extend the snapshot, and even then restrict to tight radii to stay server-friendly.
- Provide at least one approachable path in every climate or progression tier so players in any biome can chase unique personalities.
- When adding new entries, document the trigger and quirk in datapack JSON so community packs can localize or replace behavior cleanly.

## Integration beats
- **UI & lore:** Surface the stored nature id in genealogy screens or chat blurbs so players understand how the context affected their pet.
- **Advancements:** Hook specific natures into achievement-style goals (e.g., earn "Echo Whisperer" by breeding an Echoed companion) to spotlight biome exploration.
- **Future extensions:** If gene dominance or heritage trees are introduced later, let natures feed into those systems as tie-breakers rather than redundant modifiers.

By leaning on the telemetry we already capture, this nature system enriches breeding depth while remaining performant, discoverable, and aligned with the mod’s vanilla-plus ethos.
