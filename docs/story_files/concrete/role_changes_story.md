# Role Changes Story

## Planning Document for Pet Role Ability/Mechanic Changes

This document outlines potential changes, improvements, and new mechanics for existing pet roles in Pets+.

---

## Scout
**Current State**: Threat detection, loot attraction, movement speed bonus
**Potential Changes**:
- **NEW**: Double Crouch: "Pulse Scan" - Reveals all mobs within 32 blocks through walls for 5 seconds

## Eepy Eeper
**Current State**: Sleep-based healing auras, rest mechanics
**Potential Changes**:

## Eclipsed
**Current State**: Shadow protection, night vision, eclipse energy, darkness bonuses
**Potential Changes**:
- **NEW**: Shift Right-Click: "Void Storage" - Opens an ender chest interface for remote storage access
- **NEW**: Double Crouch: "Shadow Step" - Creates a destroyable afterimage at your current location. Retriggering the ability teleports you back to the afterimage. Cooldown and duration scale with distance teleported and cross-dimensional travel

---

## Cross-Role Mechanics
**Ideas for systems that affect multiple roles**:

### New Activation Triggers System
**Goal**: Add active player-triggered abilities alongside passive mechanics to make the mod feel more interactive.

**Core Trigger Types**:
1. **Double Crouch Signal** (while looking at sitting pet with boss bar visible)
   - Fast double crouch within ~0.6s window
   - Instant activation, no buildup
   - Good for quick utility/buff abilities or pet commands

2. **Shift Right-Click Interaction** (direct contact with sitting pet)
   - Direct physical interaction
   - Instant activation
   - Good for mode toggles, item exchanges, or targeted abilities

3. **Proximity Channel** (crouch-hold while near sitting pet, 1.5s buildup)
   - Range configurable per role (typically 1.5-3 blocks)
   - Visual/audio buildup with particle effects
   - More powerful abilities requiring commitment and vulnerability

**Additional Trigger Ideas**:
4. **Item Offering** (drop specific items near sitting pet)
   - Pet "consumes" the item for temporary abilities
   - Different items = different effects per role
   - Resource cost adds strategic decision-making

**Design Principles**:
- Triggers should feel like natural pet communication/bonding
- No overlap with existing Minecraft interactions
- Clear visual/audio feedback prevents accidental activation
- Each trigger type suits different ability categories (quick vs. powerful vs. contextual)

**Implementation Notes**:
- Each role can assign different abilities to these triggers
- Triggers only work when pet is sitting (ensures intentional activation)
- **Does NOT work for perched animals or when mounted** - but works normally after unperching/unmounting
- Boss bar visibility requirement for double crouch adds targeting precision
- Cooldowns per trigger type to prevent spam
- Clear visual/audio feedback for each trigger type
- This creates three distinct interaction modes: sitting (active triggers), perched (passive bonuses), mounted (mount-specific abilities)

## New Ability Concepts
**Fresh mechanics not tied to existing roles**:
-

## Balance Considerations
**Notes on power levels and role synergy**:
-

## Algorithmic Ability Variation System

### Smart Modular Approach
Extend existing `PetCharacteristics.java` seeding system to vary ability parameters without hardcoding.

**Core Design Principles**:
- Use existing `characteristicSeed` infrastructure
- Generic methods that work for any ability parameter
- String-based ability keys prevent hardcoded enums
- Configurable variation ranges per parameter type
- Power budget balancing for trade-off parameters

**Proposed API**:
```java
// Generic parameter variation
public float getAbilityModifier(String abilityKey, float baseValue, float variationRange)

// Multi-parameter trade-offs (e.g., potency vs duration)
public VariationResult getBalancedVariation(String abilityKey, ParameterBalance balance)

// Power budget balancing
public class ParameterBalance {
    addParameter("potency", baseValue, weight)
    addParameter("duration", baseValue, weight)
    addParameter("cooldown", baseValue, weight, true) // inverse relationship
}
```

**Usage Examples**:
```java
// Simple variation
int cooldown = (int) characteristics.getAbilityModifier("guardian_bulwark_cd", 200, 0.2f);

// Balanced trade-offs
ParameterBalance balance = new ParameterBalance()
    .addParameter("potency", 3.0f, 1.0f)      // Slowness level
    .addParameter("duration", 10.0f, 1.0f);   // Duration in seconds
VariationResult result = characteristics.getBalancedVariation("eepy_drowsy", balance);
int slownesLevel = (int) result.get("potency");
int durationTicks = (int) (result.get("duration") * 20);
```

**Benefits**:
- Zero hardcoding - any ability can use this system
- Deterministic from pet UUID/tame time (existing seed)
- Power budget prevents OP combinations
- String keys allow dynamic ability registration
- Backwards compatible with existing code
- Config-driven variation ranges if needed