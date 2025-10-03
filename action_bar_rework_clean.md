# Action Bar Message Filtering System - Implementation Spec

## Goal

Reduce action bar message spam during combat by 40-60% while ensuring zero critical messages are lost.

## Context: Existing Architecture

**Files to modify:**
- `src/main/java/woflo/petsplus/ui/ActionBarCueManager.java` - Core queue system
- `src/main/java/woflo/petsplus/ui/UIFeedbackManager.java` - ~50 message send calls
- `src/main/java/woflo/petsplus/config/PetsPlusConfig.java` - Add config schema

**Key existing systems:**
- `ActionBarCueManager`: Tick-based queue with priority (HIGH/NORMAL/LOW), TTL, deduplication
- `OwnerCombatState`: Tracks combat (player/pet damage events, exits after 100 ticks/5s)
- `PetInspectionManager.onPlayerLookedAtPet()`: Determines focused pet via line-of-sight
- Distance filtering: 12-block 3D sphere already implemented

**No backwards compatibility needed** - mod is unreleased.

## Phase 1: Add Category Enum (1 day)

### Step 1.1: Add CueCategory enum to ActionBarCueManager

**Location:** Inside `ActionBarCueManager` class (after existing enums)

```java
public enum CueCategory {
    CRITICAL,  // Pet death, fortress fade, mark lost
    COMBAT,    // Execution, hunt focus, bloodlust  
    STATUS,    // Cooldowns, ability triggers
    SOCIAL,    // Emotions, pack interactions
    DEBUG      // Test/verbose messages
}
```

### Step 1.2: Extend ActionBarCue inner class

**Add field:** `private CueCategory category = CueCategory.STATUS;` (default STATUS)

**Add builder method:**
```java
public ActionBarCue withCategory(CueCategory category) {
    if (category != null) {
        this.category = category;
    }
    return this;
}
```

**Add getter:** `CueCategory category() { return category; }`

### Step 1.3: Classify all messages in UIFeedbackManager

**Search pattern:** `ActionBarCue.of(` in UIFeedbackManager.java

**Classification guide:**
- `sendGuardian*`, `sendStriker*` methods → Check message type:
  - Death/fade/lost → `CRITICAL`
  - Execution/hunt/bloodlust → `COMBAT`
  - Everything else → `STATUS`
- Add `.withCategory(CueCategory.XXX)` to each `ActionBarCue.of()` call

**Example change:**
```java
// Before:
ActionBarCue.of("petsplus.striker.execution", threshold, stacks)

// After:  
ActionBarCue.of("petsplus.striker.execution", threshold, stacks)
    .withCategory(CueCategory.COMBAT)
```

**Test:** Build project, ensure no compilation errors. All messages should still display (no behavior change yet).

## Phase 2: Implement Combat-Aware Cooldowns (1.5 days)

### Step 2.1: Add combat multipliers

**Location:** ActionBarCueManager class-level constants (near top with other statics)

```java
private static final Map<CueCategory, Float> COMBAT_COOLDOWN_MULTIPLIERS = Map.of(
    CueCategory.CRITICAL, 1.0f,   // Never throttle
    CueCategory.COMBAT,   1.5f,   // Slightly longer
    CueCategory.STATUS,   2.5f,   // Much longer  
    CueCategory.SOCIAL,   4.0f,   // Heavy suppress
    CueCategory.DEBUG,    10.0f   // Nearly mute
);
```

### Step 2.2: Modify queueCue() method to apply multipliers

**Find:** `queueCue(ServerPlayerEntity player, ActionBarCue cue)` method

**Add before creating QueuedCue:**
```java
// Apply combat cooldown multiplier
int baseCooldown = cue.repeatCooldownTicks();
int adjustedCooldown = baseCooldown;

OwnerCombatState combatState = OwnerCombatState.get(player);
if (combatState != null && combatState.isInCombat()) {
    float multiplier = COMBAT_COOLDOWN_MULTIPLIERS.getOrDefault(
        cue.category(), 1.0f
    );
    adjustedCooldown = Math.round(baseCooldown * multiplier);
}
```

**Change QueuedCue creation to use `adjustedCooldown` instead of `cue.repeatCooldownTicks()`**

### Step 2.3: Test combat scenarios

**Test cases:**
1. Trigger STATUS message outside combat → 10s cooldown
2. Enter combat (take damage) → Same STATUS message → 25s cooldown (2.5x)
3. Exit combat (wait 5s) → Back to normal cooldowns
4. Trigger CRITICAL message during combat → Still 1.0x (no throttle)

**Verify:** Use `/petsplus cue stats` (implement in Phase 4) or add temporary logging

## Phase 3: Add User Configuration (1 day)

### Step 3.1: Add config section to PetsPlusConfig

**Location:** `PetsPlusConfig.java` - add new section

```java
// Add to config class
public static class ActionBarConfig {
    public String verbosityLevel = "NORMAL";  // MINIMAL, NORMAL, VERBOSE
    public boolean focusOnlyDuringCombat = false;
    public Map<String, Boolean> categoryEnabled = Map.of(
        "CRITICAL", true,
        "COMBAT", true,
        "STATUS", true,
        "SOCIAL", false,  // Default OFF to reduce spam
        "DEBUG", false
    );
}

public ActionBarConfig actionBar = new ActionBarConfig();
```

**Update config file schema version if you track versions**

### Step 3.2: Implement category toggle filtering

**Location:** ActionBarCueManager.queueCue() method (before adding to queue)

**Add check:**
```java
// Check if category is enabled in config
PetsPlusConfig config = PetsPlusConfig.getInstance();
String categoryName = cue.category().name();
if (!config.actionBar.categoryEnabled.getOrDefault(categoryName, true)) {
    return;  // Skip this cue entirely
}
```

### Step 3.3: Implement focusOnlyDuringCombat

**Location:** Same method, after category check

**Add:**
```java
// During combat, only show focused pet messages if enabled
if (config.actionBar.focusOnlyDuringCombat) {
    OwnerCombatState combatState = OwnerCombatState.get(player);
    if (combatState != null && combatState.isInCombat()) {
        ActionBarCueSource source = cue.source();
        // Allow if broadcast or CRITICAL, otherwise check focus
        if (source != null && source.petId() != null 
            && cue.category() != CueCategory.CRITICAL) {
            
            UUID focusedPetId = state.getCurrentFocusedPetId();  
            if (!source.petId().equals(focusedPetId)) {
                return;  // Skip unfocused pet during combat
            }
        }
    }
}
```

**Note:** You'll need to add `getCurrentFocusedPetId()` helper to PlayerCueState that returns the most recent focused pet UUID from the `recentPets` list.

### Step 3.4: Implement verbosity levels

**Add to config loading/validation:**
```java
public float getVerbosityMultiplier() {
    return switch (actionBar.verbosityLevel.toUpperCase()) {
        case "MINIMAL" -> 2.0f;   // Double all cooldowns
        case "VERBOSE" -> 0.5f;   // Halve all cooldowns
        default -> 1.0f;          // NORMAL = no change
    };
}
```

**Apply in queueCue():**
```java
adjustedCooldown = Math.round(adjustedCooldown * config.getVerbosityMultiplier());
```

**For MINIMAL - also increase distance threshold:**
```java
if ("MINIMAL".equals(config.actionBar.verbosityLevel)) {
    // Only show focused pet + nearby (reduce radius to 6 blocks)
    // Modify isEligible() to check this
}
```

## Phase 4: Testing & Debug Tools (0.5 days)

### Step 4.1: Add debug command `/petsplus cue stats`

**Location:** Create new command or add to existing PetsCommand.java

```java
private static int showCueStats(CommandContext<ServerCommandSource> context) {
    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
    PlayerCueState state = ActionBarCueManager.getPlayerState(player);
    
    if (state == null) {
        player.sendMessage(Text.literal("No active cue state"), false);
        return 0;
    }
    
    // Show queue size, cooldowns, suppressions by category
    player.sendMessage(Text.literal("=== Action Bar Stats ==="), false);
    player.sendMessage(Text.literal("Queued messages: " + state.queueSize()), false);
    player.sendMessage(Text.literal("Active cooldowns: " + state.cooldownCount()), false);
    // Add category-specific stats if you track them
    return 1;
}
```

**Add public getters to PlayerCueState:** `queueSize()`, `cooldownCount()`

### Step 4.2: Add test command `/petsplus cue test <category>`

```java
private static int testCueSpam(CommandContext<ServerCommandSource> context) {
    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
    String category = StringArgumentType.getString(context, "category");
    
    CueCategory cat = CueCategory.valueOf(category.toUpperCase());
    
    // Spam 5 test messages
    for (int i = 0; i < 5; i++) {
        ActionBarCueManager.queueCue(player, 
            ActionBarCue.of("petsplus.test.message", i)
                .withCategory(cat)
        );
    }
    return 1;
}
```

### Step 4.3: Manual test scenarios

**Test 1: Combat spam reduction**
1. Spawn 10 wolves, assign Striker role
2. Attack single mob
3. Count action bar messages before/after implementation
4. **Expected:** 40-60% reduction in non-CRITICAL messages

**Test 2: CRITICAL priority**
1. Enter combat
2. Kill pet (or trigger fortress fade)
3. **Expected:** CRITICAL message appears immediately despite combat throttling

**Test 3: Config changes**
1. Set `"SOCIAL": false` in config
2. Reload config (or restart)
3. Trigger emotion cue
4. **Expected:** No message shown

**Test 4: Focus-only mode**
1. Set `focusOnlyDuringCombat: true`
2. Enter combat with 3+ pets
3. Look at one pet
4. **Expected:** Only that pet's messages shown during combat

### Step 4.4: Performance check

Run with 10 players, 5 pets each:
- Profile tick time for `ActionBarCueManager.run()`
- **Target:** <0.1ms per player per tick
- Use `/debug start` (Minecraft profiler) or add timing logs

## Total Timeline: 3-4 days

## Design Constraints (DO NOT CHANGE)

1. **Category per message:** Single category only, no multi-category logic
2. **Combat detection:** Use existing `OwnerCombatState.isInCombat()` - do NOT create new system
3. **Focused pet:** Use existing `PetInspectionManager` - do NOT modify
4. **Distance filter:** Keep existing 12-block 3D sphere - do NOT change
5. **Priority handling:** Keep "newest wins" for same priority - do NOT modify
6. **Batching:** DEFERRED - implement only if spam persists after Phase 2
7. **EmotionContextCues:** Do NOT modify (separate system, integrate later)

## Implementation Checklist

### Phase 1: Categories
- [ ] Add `CueCategory` enum to ActionBarCueManager
- [ ] Add `category` field + `withCategory()` builder to ActionBarCue
- [ ] Classify all ~50 messages in UIFeedbackManager
- [ ] Test: Build succeeds, messages still display

### Phase 2: Combat Cooldowns  
- [ ] Add `COMBAT_COOLDOWN_MULTIPLIERS` map
- [ ] Modify `queueCue()` to apply multipliers based on combat state
- [ ] Test: STATUS 10s → 25s during combat, CRITICAL unchanged

### Phase 3: Configuration
- [ ] Add `ActionBarConfig` to PetsPlusConfig
- [ ] Implement category toggle filtering in `queueCue()`
- [ ] Implement `focusOnlyDuringCombat` logic
- [ ] Implement verbosity multipliers
- [ ] Test: Toggling SOCIAL off works, focus-only works

### Phase 4: Testing
- [ ] Add `/petsplus cue stats` command
- [ ] Add `/petsplus cue test <category>` command  
- [ ] Run 10-pet combat spam test
- [ ] Run CRITICAL priority test
- [ ] Profile performance (<0.1ms target)

## Success Criteria

✅ **40-60% spam reduction** in combat scenarios  
✅ **Zero CRITICAL messages lost** (priority always respected)  
✅ **<0.1ms per player per tick** performance overhead  
✅ **Config defaults work** without user changes

## Common Pitfalls to Avoid

❌ Don't modify `EmotionContextCues` - separate system  
❌ Don't change existing priority/TTL/dedup logic - only add categories  
❌ Don't implement batching yet - defer to later if needed  
❌ Don't add complexity - keep it simple and test each phase
