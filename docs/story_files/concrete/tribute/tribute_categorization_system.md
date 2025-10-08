# Tribute Categorization and Rarity System

## Overview

This document details the tribute categorization and rarity system that forms the foundation of the boost-based tribute framework. The system automatically categorizes items and determines their rarity to calculate appropriate boost effects.

## Design Goals

1. **Intuitive Classification** - Items should fall into categories that make sense to players
2. **Automatic Detection** - System should automatically categorize vanilla and modded items
3. **Flexible Override** - Configuration should allow custom categorization for special cases
4. **Balanced Progression** - Rarity tiers should provide meaningful but balanced power scaling

## Category System

### Primary Categories

#### 1. Material Category
**Description**: Raw and refined resources, building blocks, and crafting components
**Primary Effects**: Attribute boosts (health, defense, attack)
**Examples**:
- Common: Stone, dirt, wood planks, coal, iron ingot
- Uncommon: Gold ingot, redstone, lapis lazuli, copper
- Rare: Diamond, emerald, nether quartz
- Epic: Netherite scrap, ancient debris
- Legendary: Dragon egg, elytra, nether star

**Automatic Detection Rules**:
- Items in `minecraft:blocks` tag
- Items with `ingot`, `nugget`, `gem`, `crystal` in name
- Ores and mineral blocks
- Building materials

#### 2. Food Category
**Description**: Edible items and treats that pets would enjoy
**Primary Effects**: Mood boosts, bond enhancement, temporary buffs
**Examples**:
- Common: Bread, apple, carrot, potato
- Uncommon: Cooked meat, pumpkin pie, cake
- Rare: Golden carrot, golden apple, enchanted golden apple
- Epic: Suspicious stew with special effects
- Legendary: Special event foods, modded superfoods

**Automatic Detection Rules**:
- Items with food components
- Items in `minecraft:foods` tag
- Items with `edible` property
- Special treats (cake, cookies)

#### 3. Magical Category
**Description**: Items with mystical properties, enchantments, or magical applications
**Primary Effects**: Ability enhancements, special powers, cooldown reduction
**Examples**:
- Common: Redstone dust, glowstone dust
- Uncommon: Ender pearl, blaze powder, ghast tear
- Rare: Enchanted books, potion ingredients, eyes of ender
- Epic: Enchanted golden apple, chorus fruit
- Legendary: Totem of undying, heart of the sea, nether star

**Automatic Detection Rules**:
- Enchanted items
- Brewing ingredients
- End-related items
- Items with magical properties

#### 4. Special Category
**Description**: Unique items, artifacts, and modded items that don't fit other categories
**Primary Effects**: Unique boosts, cross-mod integration, special abilities
**Examples**:
- Common: Name tags, leads, saddles
- Uncommon: Music discs, banners, maps
- Rare: Spawn eggs, armor stands
- Epic: Elytra, shulker boxes
- Legendary: Dragon head, modded artifacts

**Automatic Detection Rules**:
- Items with unique functionality
- Modded items (detected by namespace)
- Items with special properties
- Fallback for uncategorized items

## Rarity System

### Rarity Tiers

#### Common (Tier 1)
**Multiplier**: 0.5x
**Visual Indicator**: Gray particles
**Sound Pitch**: Low
**Description**: Everyday items that provide modest benefits

#### Uncommon (Tier 2)
**Multiplier**: 1.0x
**Visual Indicator**: Green particles
**Sound Pitch**: Normal
**Description**: Refined items that provide standard benefits

#### Rare (Tier 3)
**Multiplier**: 2.0x
**Visual Indicator**: Blue particles
**Sound Pitch**: High
**Description**: Precious items that provide significant benefits

#### Epic (Tier 4)
**Multiplier**: 3.0x
**Visual Indicator**: Purple particles
**Sound Pitch**: Very high
**Description**: Exceptional items that provide major benefits

#### Legendary (Tier 5)
**Multiplier**: 5.0x
**Visual Indicator**: Golden particles with special effects
**Sound Pitch**: Highest with special sound
**Description**: Unique artifacts that provide extraordinary benefits

### Automatic Rarity Detection

#### Vanilla Item Rarity Mapping
```json
{
  "minecraft:stone": "common",
  "minecraft:iron_ingot": "common",
  "minecraft:gold_ingot": "uncommon",
  "minecraft:diamond": "rare",
  "minecraft:netherite_scrap": "epic",
  "minecraft:dragon_egg": "legendary",
  "minecraft:cake": "uncommon",
  "minecraft:golden_carrot": "rare",
  "minecraft:enchanted_golden_apple": "epic",
  "minecraft:totem_of_undying": "legendary"
}
```

#### Rarity Calculation Rules

1. **Base Value Assessment**
   - Mining difficulty (tool tier required)
   - Crafting complexity (number of steps)
   - Resource scarcity (generation rate)
   - Functional power (in-game utility)

2. **Dynamic Rarity Factors**
   - Enchantment level (adds +1 tier per high-level enchantment)
   - Item stack size (smaller stacks = higher rarity)
   - Recipe exclusivity (single-use recipes = higher rarity)
   - Dimension availability (higher dimensions = higher rarity)

3. **Modded Item Handling**
   - Default to uncommon for unknown modded items
   - Increase rarity based on material composition
   - Respect mod-provided rarity tags if available
   - Configuration overrides for special cases

## Category-Specific Boost Patterns

### Material Category Boosts
```
Health Boost: Base Value × Rarity × 0.8
Defense Boost: Base Value × Rarity × 1.0
Attack Boost: Base Value × Rarity × 0.6
```

### Food Category Boosts
```
Bond Strength: Base Value × Rarity × 1.2
Mood Boost: Base Value × Rarity × 1.0
Temporary Buff: Base Value × Rarity × 0.5
```

### Magical Category Boosts
```
Ability Potency: Base Value × Rarity × 1.0
Cooldown Reduction: Base Value × Rarity × 0.8
Special Power: Base Value × Rarity × 1.5
```

### Special Category Boosts
```
Unique Effect: Base Value × Rarity × 2.0
Cross-Mod Integration: Base Value × Rarity × 1.0
Special Ability: Base Value × Rarity × 1.3
```

## Configuration System

### Category Configuration
```json
{
  "tribute_categories": {
    "material": {
      "enabled": true,
      "base_multiplier": 1.0,
      "primary_effects": ["health", "defense", "attack"],
      "effect_weights": {
        "health": 0.4,
        "defense": 0.4,
        "attack": 0.2
      },
      "auto_detect": {
        "tags": ["minecraft:blocks", "minecraft:ores"],
        "name_patterns": ["ingot", "nugget", "gem", "ore"],
        "properties": ["solid", "hard"]
      }
    },
    "food": {
      "enabled": true,
      "base_multiplier": 0.8,
      "primary_effects": ["mood", "bond", "temporary"],
      "effect_weights": {
        "mood": 0.3,
        "bond": 0.5,
        "temporary": 0.2
      },
      "auto_detect": {
        "tags": ["minecraft:foods"],
        "properties": ["edible"],
        "components": ["food"]
      },
      "special_treats": {
        "minecraft:cake": {
          "effects": ["joy_boost", "bond_strength"],
          "value_multiplier": 1.5,
          "flavor_text": "A special treat that brings pure joy"
        }
      }
    }
  }
}
```

### Rarity Configuration
```json
{
  "rarity_tiers": {
    "common": {
      "multiplier": 0.5,
      "particle_color": "#808080",
      "sound_pitch": 0.8,
      "max_tributes_per_tier": 10
    },
    "uncommon": {
      "multiplier": 1.0,
      "particle_color": "#00ff00",
      "sound_pitch": 1.0,
      "max_tributes_per_tier": 8
    },
    "rare": {
      "multiplier": 2.0,
      "particle_color": "#0080ff",
      "sound_pitch": 1.2,
      "max_tributes_per_tier": 6
    },
    "epic": {
      "multiplier": 3.0,
      "particle_color": "#8000ff",
      "sound_pitch": 1.4,
      "max_tributes_per_tier": 4
    },
    "legendary": {
      "multiplier": 5.0,
      "particle_color": "#ffd700",
      "sound_pitch": 1.6,
      "max_tributes_per_tier": 2,
      "special_effects": true
    }
  }
}
```

### Item Override Configuration
```json
{
  "item_overrides": {
    "minecraft:diamond": {
      "category": "material",
      "rarity": "rare",
      "custom_effects": ["health_boost", "bond_strength"],
      "value_multiplier": 1.2,
      "flavor_text": "A symbol of enduring strength and loyalty"
    },
    "minecraft:cake": {
      "category": "food",
      "rarity": "uncommon",
      "custom_effects": ["joy_boost", "bond_strength"],
      "value_multiplier": 1.5,
      "special_interaction": "celebration"
    },
    "modid:artifact": {
      "category": "special",
      "rarity": "legendary",
      "custom_effects": ["unique_power"],
      "value_multiplier": 2.0,
      "mod_integration": true
    }
  }
}
```

## Role Affinity System

### Role-Based Category Preferences
```json
{
  "role_affinities": {
    "petsplus:guardian": {
      "preferred_categories": ["material"],
      "category_multipliers": {
        "material": 1.2,
        "food": 0.8,
        "magical": 1.0,
        "special": 1.0
      },
      "preferred_effects": ["health", "defense"]
    },
    "petsplus:support": {
      "preferred_categories": ["food", "magical"],
      "category_multipliers": {
        "material": 0.8,
        "food": 1.2,
        "magical": 1.2,
        "special": 1.0
      },
      "preferred_effects": ["bond", "mood", "ability_potency"]
    },
    "petsplus:scout": {
      "preferred_categories": ["magical", "special"],
      "category_multipliers": {
        "material": 0.8,
        "food": 1.0,
        "magical": 1.3,
        "special": 1.2
      },
      "preferred_effects": ["speed", "cooldown_reduction"]
    }
  }
}
```

## Integration with Existing Systems

### Item Tag Integration
The system respects existing Minecraft item tags:
- `minecraft:ores` for ore detection
- `minecraft:foods` for food detection
- `minecraft:blocks` for material detection
- Custom mod tags for special items

### Registry Integration
The system integrates with existing registries:
- Item registry for basic item properties
- Enchantment registry for magical item detection
- Recipe registry for complexity assessment
- Biome registry for availability assessment

## Advanced Features

### Tribute Synergy System
Certain tribute combinations create synergistic effects:
- Material + Magical = Enhanced durability
- Food + Special = Extended buff duration
- Multiple same-category tributes = Diminishing returns
- Cross-category tributes = Balanced enhancement

### Seasonal Variations
Tribute values can vary based on:
- Game time (day/night cycles)
- Season (if season mod is present)
- Moon phase (for magical tributes)
- Weather conditions
- Current biome

### Player Reputation System
Repeated tribute types build reputation:
- Material tributes = "Provider" reputation
- Food tributes = "Feeder" reputation
- Magical tributes = "Mystic" reputation
- Special tributes = "Collector" reputation

Reputation affects:
- Tribute effectiveness
- Special dialogue options
- Unique visual effects
- Access to special tribute options

## Balance Considerations

### Power Scaling
- Common tributes provide meaningful but modest benefits
- Legendary tributes provide significant but not game-breaking advantages
- Multiple tributes stack with diminishing returns
- Role affinities encourage specialization without forcing it

### Accessibility
- Every item has some value as tribute
- No tribute is completely worthless
- Clear feedback helps players understand value
- Configuration allows for server-specific balance

### Progression Integration
- Early game tributes focus on basic improvements
- Mid-game tributes enable specialization
- Late-game tributes provide unique advantages
- Tribute history creates pet biography

## Conclusion

The tribute categorization and rarity system provides a flexible, intuitive framework for automatically determining tribute values while allowing for extensive customization. It creates natural opportunities for player expression and emergent gameplay while maintaining balance and accessibility.

The system's automatic detection capabilities ensure compatibility with modded content, while the configuration system allows server administrators to fine-tune the experience to their specific needs.