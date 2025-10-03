# Action Bar Spam Reduction - Simple Plan

## Goal

Stop spamming the action bar. Period. Keep it for:

- **Critical alerts ONLY** - pet death, fortress breaking (things you NEED to know)
- **Major milestones** - level ups, role unlocks (rare, meaningful events)
- **Rare/iconic emotions** - Maybe 1-2 special ones (first bond, heroic moment)

Everything else → **subtle in-world feedback**:
- Particles (brief, not spammy)
- Sounds (quiet, atmospheric)
- Maybe pet animations/behaviors eventually

**Philosophy**: Immersive Minecraft gaming = see/hear feedback in the world, not read spam text

## What You Already Have ✅

- `ParticleEffect.java` + `SoundEffect.java` - Full working system
- JSON ability files - Can add `particle_effect` and `sound_effect` to any ability
- `UIFeedbackManager.java` - All the action bar spam comes from here
- `PetsPlusConfig.java` - Can add simple toggles

## The Actual Work (2-3 hours max)

### Step 1: Add Config Toggle (15 min)

```java
public static class ActionBarSettings {
    public boolean showCombatMessages = false;     // Default OFF - no spam
    public boolean showAbilityProcs = false;       // Default OFF - use particles/sounds
    public boolean showEmotions = false;           // Default OFF - maybe 1-2 iconic ones only
    public boolean showMilestones = true;          // Level ups, role unlocks
    // Critical alerts always show (fortress breaking, pet death) - no toggle needed
}

public ActionBarSettings actionBar = new ActionBarSettings();
```

### Step 2: Audit UIFeedbackManager (30 min)

Go through each `sendGuardian*` and `sendStriker*` method and categorize:

**KEEP (Critical - Always Show):**

- `sendGuardianFortressFadeMessage` - Fortress about to break (you can die!)
- `sendGuardianFortressOutOfRangeMessage` - Fortress broke (immediate danger)
- `sendGuardianFortressPetDownMessage` - Fortress broke (pet died)
- `sendGuardianFortressDimensionMessage` - Fortress broke (changed dimension)
- Any pet death messages (if they exist)

**KEEP (Milestones - showMilestones config):**

- Level up messages
- Role unlock messages  
- Bond tier reached messages
- First time achievements (maybe?)

**REMOVE (All Combat/Ability Spam):**

- `sendStrikerExecutionMessage` → Particle: blood/crit, Sound: impact
- `sendStrikerHuntFocusMessage` → Remove entirely (unnecessary)
- `sendStrikerBloodlustMessage` → Particle: red sparkles on pet, Sound: growl
- `sendStrikerMarkLostMessage` → Particle: fading mark, Sound: whoosh
- `sendGuardianAegisMessage` → Particle: shield shimmer, Sound: quiet ding
- `sendGuardianBulwarkMessage` → Particle: barrier effect, Sound: shield
- `sendGuardianProjectileDRMessage` → Particle: deflect, Sound: ricochet

**EMOTIONS (Default OFF, maybe 1-2 special):**

- 99% of emotions → Just particle/sound (tail wag = hearts, scared = sweat drops)
- 1% iconic → Maybe action bar (first bond formed, heroic save, pack loyalty moment)
- Keep it **rare and meaningful**, not every emotion ping

### Step 3: Remove Spam Messages (30 min)

For each "REMOVE" message, either:

1. **Delete the method call entirely** if the ability already has particles/sounds in JSON
2. **Comment it out** if you want to verify it's not needed first

Example - striker execution already has effects in the ability JSON, so just delete:

```java
// BEFORE in some striker ability code:
UIFeedbackManager.sendStrikerExecutionMessage(player, threshold, stacks);

// AFTER:
// Removed - ability has particle/sound effects in JSON
```

### Step 4: Add Particles/Sounds to Abilities Missing Them (1-2 hours)

For abilities that had action bar messages but NO particles/sounds, add **subtle** effects to JSON:

**Design philosophy for feedback**:
- **Particles**: Low count (3-8), brief, on-theme
- **Sounds**: Quiet (0.2-0.4 volume), atmospheric, non-annoying
- **Target**: Usually "pet" or "victim", not always "owner"
- **Goal**: "I noticed that" not "OMG LOOK AT THIS"

**Example**: Striker bloodlust - red combat energy:

```json
{
  "id": "petsplus:striker_bloodlust",
  "effects": [
    {
      "type": "striker_bloodlust",
      "stacks": 3
    },
    {
      "type": "particle_effect",
      "particle": "damage_indicator",  // Subtle red particles
      "target": "pet",
      "count": 5,
      "offset_x": 0.3,
      "offset_y": 0.4,
      "offset_z": 0.3,
      "speed": 0.05,
      "height_offset": 0.8
    },
    {
      "type": "sound_effect",
      "sound": "entity_wolf_growl",  // Quiet growl
      "target": "pet",
      "volume": 0.3,  // QUIET - not annoying
      "pitch": 0.9,
      "category": "neutral"
    }
  ]
}
```

**Example**: Guardian aegis shield - protective shimmer:

```json
{
  "type": "particle_effect",
  "particle": "enchant",  // Subtle sparkle
  "target": "pet",
  "count": 4,
  "offset_x": 0.4,
  "offset_y": 0.5,
  "offset_z": 0.4,
  "speed": 0.02,
  "height_offset": 1.0
},
{
  "type": "sound_effect",
  "sound": "block_enchantment_table_use",
  "target": "pet",
  "volume": 0.2,  // Very quiet
  "pitch": 1.4,
  "category": "neutral"
}
```

That's it! No code changes needed - JSON only. Keep it subtle and immersive.

### Step 5: Gate Emotion Messages (15 min)

Most emotions → Delete action bar entirely, add particles/sounds to emotion system

For 1-2 iconic emotions only, gate behind config:

```java
public static void sendEmotionMessage(ServerPlayerEntity player, MobEntity pet, String emotionKey) {
    // Only show if it's an iconic emotion AND config enabled
    if (PetsPlusConfig.getInstance().actionBar.showEmotions && isIconicEmotion(emotionKey)) {
        sendActionBarMessage(player, pet, emotionKey);
    }
    // Particles/sounds always play regardless (handled by emotion system)
}

private static boolean isIconicEmotion(String emotionKey) {
    // Maybe: first_bond, heroic_save, pack_loyalty_moment
    return emotionKey.contains("first_bond") || emotionKey.contains("heroic");
}
```

Everything else: **Just particles and sounds, no text**

### Step 6: Test (30 min)

1. Spawn some pets with different roles
2. Fight mobs
3. Verify you only see critical messages + gossip
4. Check particles/sounds show up for removed messages
5. Toggle config options to confirm gates work

## Implementation Checklist

- [ ] Add `ActionBarSettings` to PetsPlusConfig
- [ ] Audit all ~30 message methods in UIFeedbackManager
- [ ] Remove combat spam methods (or comment out to test)
- [ ] Add particles/sounds to abilities missing them via JSON
- [ ] Gate optional messages behind config
- [ ] Test with pets in combat
- [ ] Verify critical messages still show
- [ ] Check gossip/emotions still work

## What Actually Shows on Action Bar (Minimal!)

**Critical Alerts** (Always show - no config):

- Fortress fade warning (about to lose protection)
- Fortress broken - out of range
- Fortress broken - pet downed
- Fortress broken - dimension change
- Pet death (if not already handled)

**Milestones** (showMilestones = true by default):

- Pet leveled up
- New role unlocked
- Bond tier increased
- Major achievements

**Iconic Emotions** (showEmotions = false by default):

- First bond formed (once ever)
- Heroic save moment (pet saved you from death)
- Pack loyalty event (rare, meaningful)
- **That's it** - everything else is particles/sounds only

## Everything That Gets Removed (Replace with Subtle Feedback)

**All Combat Abilities** (particles + quiet sounds):

- Execution proc → Blood particles on victim, impact sound
- Hunt focus update → Remove entirely (unnecessary)
- Bloodlust stacks → Red shimmer on pet, quiet growl
- Aegis stacks → Shield sparkle on pet, soft ding
- Bulwark proc → Barrier particles, shield sound
- Projectile DR → Deflect particles, ricochet sound
- Mark lost → Fading particles on target, whoosh
- Finisher ready → Glow on pet, anticipation sound

**All Regular Emotions** (default behavior - no text):

- Happy/excited → Heart particles, happy sound
- Scared/anxious → Sweat particles, whimper
- Playful → Note particles, bark
- Tired → Zzz particles, yawn sound
- Hungry → Food particles, stomach rumble
- Loyal/bonded → Sparkle particles, warm sound
- **99% of emotions** → Just world feedback, NO action bar spam

**Ability Status Updates** (remove entirely or particles only):

- Cooldown ready messages → Small glow on pet (if you want feedback at all)
- Ability proc messages → Handled by ability's own particles/sounds
- Stack counters → Remove (players can see buffs if needed)

## Why This Works

- You **already have** particle/sound effect system working perfectly
- Abilities **already use it** via JSON (scout_mark.json, event_horizon.json, etc.)
- You just need to **delete 90% of action bar calls** and **add subtle JSON effects**
- Action bar becomes **actually important** - you only see it for critical info
- **Immersive gameplay** - you see/hear feedback in the world, not reading text spam
- No complex systems, no hardcoding, no "templates" or "managers"

## Design Principles

**Subtle is better**:
- Low particle counts (3-8, not 20+)
- Quiet sounds (0.2-0.4 volume, not 1.0)
- Brief effects (not lingering)
- Themed to the ability (bloodlust = red, shield = barrier, etc.)

**World-based, not text-based**:
- If you can show it with particles → do that
- If you can convey it with sound → do that
- Action bar is for **critical information only**

**Player agency**:
- Default = minimal (most players want this)
- Config = can enable more if they want numbers/status
- Critical alerts = always show (no choice, it's important)

## Timeline: Half a day tops

- Config: 15 min
- Audit: 30 min  
- Delete spam: 30 min
- Add JSON effects: 1-2 hours (depending on how many abilities)
- Gate optional: 15 min
- Test: 30 min

**Total: 3-4 hours of actual work**

Not weeks. Not days. A few hours of deleting spam and editing JSON files.
