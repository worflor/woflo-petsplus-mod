# Ability Token Overhaul Story

## Snapshot
- Roles currently inject abilities via `PetRoleType.defaultAbilities()` caches, so ability execution is bound to role IDs and reload order (`src/main/java/woflo/petsplus/abilities/AbilityManager.java`).
- Tribute level-ups award powers implicitly through roles; slot counts are not tracked in `DefaultProgressionModule`, leaving no guardrail for token application (`src/main/java/woflo/petsplus/state/modules/impl/DefaultProgressionModule.java`).
- Role JSON mixes stat curves with ability loadouts (`src/main/java/woflo/petsplus/data/PetRoleDataLoader.java`), complicating datapack compatibility once tokens replace roles.

## Pillars
1. **Tokenize abilities** – Every `AbilityType` must surface a matching `AbilityTokenItem` instance that can be granted, looted, or crafted.
2. **Gate by slots** – Tribute milestones expand a pet's `maxAbilitySlots`, enforcing capacity checks before `unlockAbility` persists new tokens.
3. **Decouple execution** – Ability triggers read unlocked ability IDs directly instead of deriving batches from roles.
4. **Excise roles** – Delete role registries, loaders, and data dependencies so progression stands on token-driven profiles only.

## Implementation Tracks
### 1. Token Item & Distribution
- Author `AbilityTokenItem` (`src/main/java/woflo/petsplus/items/AbilityTokenItem.java`) that serializes an ability `Identifier` via NBT/components and exposes tooltips drawn from `AbilityType` text.
- Regenerate creative tab entries and loot table helpers inside `PetsplusItems` after each `AbilityDataLoader` reload, creating one stack per registered ability (`src/main/java/woflo/petsplus/items/PetsplusItems.java`).
- Provide a static factory (e.g., `AbilityTokenItem.create(AbilityType)`) so commands, loot tables, and progression rewards mint consistent stacks.

### 2. Applying Tokens In-Game
- Register a crouch right-click handler next to the petting callback (`src/main/java/woflo/petsplus/events/PetsplusEntityInteractions.java`) to detect token usage on owned pets.
- Guard the interaction by verifying ownership, `occupiedSlots < maxSlots`, and absence of duplicate ability IDs before consuming the token and calling `ProgressionModule.unlockAbility`.
- Emit failure feedback (chat + particles) for full slots or duplicates to keep UX clear.

### 3. Tribute-Driven Slot Progression
- Extend `ProgressionModule.Data` with `maxAbilitySlots` and `occupiedAbilitySlots`, persisting them through `PetComponent` NBT and exposing quick lookups for UI (`src/main/java/woflo/petsplus/state/PetComponent.java`).
- Update tribute processing paths (`DefaultProgressionModule.handleTribute` and level-up listeners) so every configured level interval unlocks one additional slot once the tribute succeeds.
- Surface slot status through `/pets` command output to inform players about remaining tributes and capacity (`src/main/java/woflo/petsplus/commands/PetsCommand.java`).

### 4. Ability Execution Refactor
- Replace role-keyed caches in `AbilityManager` with `Identifier` keyed structures populated from `AbilityType` definitions during `reloadFromRegistry`.
- Collect unlocked ability IDs from `ProgressionModule.getUnlockedAbilities()` for each pet when dispatching triggers (`triggerAbilities`, `prepareOwnerExecutionPlan`, `applyOwnerExecutionPlan`).
- Remove role dependencies from reload order by letting `AbilityDataLoader` own cache rebuilds and deleting `PetRoleType` lookups.

### 5. Purge Role Definitions
- Remove `PetRoleType` registry, `PetRoleDataLoader`, and related JSON schema so datapacks no longer define role-driven loadouts.
- Introduce a lightweight `PetProgressionProfile` (or equivalent) that carries stat curves, emotion hooks, and presentation data without any role identifiers.
- Update all command, UI, and configuration surfaces to reference progression profiles or unlocked abilities rather than roles, ensuring no dead references remain.

## Validation Focus
- **Unit**: token interaction handler (slot limits, duplicates) and cache rebuild behavior.
- **Integration**: simulated tribute level-ups unlocking slots and ability triggering across multiple pets sharing tokens.
- **Manual**: debug command or log output to inspect unlocked tokens, slot counts, and ability cache membership on demand.

## Open Questions
- Should tribute unlocks deliver a specific token automatically or prompt player choice when multiple abilities are available?
- Do we need new ability metadata (rarity, category) to drive token drop rates once roles disappear?
- How far can slot counts scale per pet type, and should configuration cap growth?
