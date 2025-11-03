<p align="center">
  <img src="src/main/resources/assets/petsplus/icon.png"
       alt="Pets+ icon"
       width="128">
</p>

<h1 align="center">woflo's Pets+</h1>
<h3 align="center">dev-0.97-Virtues-and-Vices</h3>
<h4 align="center">next: bug fixing</h4>

---

<h4 align="center">Recent Changes</h4>
<ul>
  <li>Rework 0.95 [ai] - Following the same path as the stimulus pipeline, AI uses pet context, mood engine snapshots, and more to allow dynamic emergent behaviour (adaptive goal).</li>
  <li>Added 0.96 [ai] - AIState exists.</li>
  <li>Update 0.96 [ai] - Pet Adaptive Goals now react *properly* with relationships, bonds, and owner vs non owner pets.</li>
	<li>Rework 0.96 [ai] - Pet Director system now integrates with Adaptive Goals' 'action goals' and 'insight goals'.</li>
  <li>Added 0.96 [ai] - Swarm Snapshots flow through AIState so Adaptive Goals CAN view nearby entities if a nearby scan has occured recently. Some goal's request a fresh scan, some don't need fresh information.</li>
	<li>Added 0.96 [ai] - Bedtime Companion Goal.</li>
  <li>Added 0.96 [ai] - Nurse Baby Goal.</li>
  <li>Added 0.96 [ai] - Hide and Seek Goals; utilizing Pet Director and Group Coordinator.</li>
  <li>Added 0.96 [ai] - Cuddle Heal Goal.</li>
	<li>Broken 0.96 [my sanity] - my sanity hurts.</li>
  <li>Added 0.97 [nature] - Fenn and Falsei (tamed natures; making 5) exist now :]</li>
  <li>Added 0.97 [nature] - Abstract pets (bred nature) are formed in tight conformed spaces with absolutely no escape.</li>
  <li>Added 0.97 [mood] - New Ultra Rare Mood: Mining Reverie. Getting lost in the mining sauce.</li>
  <li>Added 0.97 [mood] - New Ultra Rare Mood: Malevolent Eclipse. A buildup of evil.</li>
  <li>Added 0.97 [morality] - Virtue and Vices. Morality is a 'drifting' layer on top of the core pet. Each nature has a baseline for each virtue and custom 'reintegration' value. These values drift with gameplay and lead to behaviour changes.</li>
  <li>Rework 0.97 [Pet Compendium] - Much prettier, very concise. So needed.</li>
  <li>Rework 0.97 [Gossip] - Follows the same stimulus/perception pipeline as the Mood Engine, allowing "gossip triggers" to hook onto the stimulus bus. ;) this is still being fine tuned, some functionality was lost, some was gained.</li>
</ul>

---

<p align="center">
  "I wish my block game contained a real-time emotion engine😔" - Someone, somewhere, at some point. probably.
  <br>
  <strong>Mod is NOT officially released.</strong>
</p>

---

### What you'll notice
- Realtime fluctuations in your pet's mood; shifting through the stimulus its exposed to throughout its environment and interactions.
- Adaptive Goal's allow your pet's current objective to fluctuate based on the surrounding environment and their internal state.
- Natures act like baseline *personalities*, adding quirks to your pets moods and stats.
- Levels unlocks grow as you adventure, explore, and keep pets close.
- Roles allow your pet to fulfill... roles!
- Memories and stories are tracked.

Curious how each layer fits together? The [feature docs](docs/features/_readme.md) might be your friend.

### Play It!
1. Download the latest jar file. 
2. Drop it into your `mods/` folder along with the Fabric API.
3. Launch and enjoy.

### Tweak It!
- All of the personalities, moods, roles, and triggers live in datapacks and configs, ready to remix.
- Ship tweaks with your pack or server so everyone shares the same vibe.

### Build It!
note: i prefer **idea suggestions** over *code contributions*. If your idea is well thought out, credit(s) will be attributed. rejection of ideas/code for any reason is fine; i'm picky. the better the documentation/reasoning, the higher liklihood of integration.

```bash
git clone https://github.com/woflo-dev/petsplus.git
cd petsplus
./gradlew build
```

#### Compatibility Notes
- Minecraft 1.21.10
- Fabric | Java 21
- Needs Fabric API

### Cred-It!
- Mod, systems, code: woflo
- Icon: GuriCreates — <https://guricreates.com>
- License: [GPL-3.0](LICENSE)

### Manual validation
- Spawned breeze and bogged mobs next to pets in a sandbox world and observed that only defensive/owner-guarding behaviors (owner orbit, lean against owner, patrol) retained the proximity multiplier while idle quirks and play actions remained unchanged.
- Placed trial spawner feature blocks near pets and confirmed that only wander/exploration goals (casual wander, purposeful patrol, trial scout) gained the exploration boost while social/idle goals stayed neutral.

Stay kind to your pets. They remember. Stay kind to me. I remember 🥺
