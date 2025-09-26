# Refresh pet species caches after loading state data

## Overview
This pull request strengthens how Pets+ derives and caches pet metadata so flight capability and species-dependent behaviour stay in sync with saved state. It also introduces smarter owner-assist combat coordination and the data plumbing needed to reason about flying mobs.

## Key changes
- **Species state lifecycle** – `PetComponent` now centralises cache invalidation through helper methods, allowing bulk state restoration/deserialization without repeatedly recomputing descriptors while still forcing a single refresh afterward.【F:src/main/java/woflo/petsplus/state/PetComponent.java†L592-L633】【F:src/main/java/woflo/petsplus/state/PetComponent.java†L1105-L1130】
- **Flight capability caching** – Flight checks reuse cached evaluations during combat lookups and avoid redundant tag scans by memoising flyer tag membership per entity type.【F:src/main/java/woflo/petsplus/events/CombatEventHandler.java†L39-L52】【F:src/main/java/woflo/petsplus/events/CombatEventHandler.java†L218-L245】
- **Owner assist AI** – Nearby ally selection now sorts by squared distance instead of maintaining a manual nearest-neighbour buffer, improving clarity and scalability for larger pet packs.【F:src/main/java/woflo/petsplus/ai/goals/OwnerAssistAttackGoal.java†L571-L592】
- **Flight metadata plumbing** – The FLYERS entity-type tag and supporting data providers provide a single source of truth for airborne species across runtime and data generation flows.【F:src/main/java/woflo/petsplus/tags/PetsplusEntityTypeTags.java†L1-L19】【F:src/main/resources/data/petsplus/tags/entity_types/flyers.json†L1-L23】
- **State restoration hooks** – Restoring preserved pet state now rebuilds caches only once and reapplies attribute modifiers after state resets, ensuring a consistent baseline for mood, combat, and AI subsystems.【F:src/main/java/woflo/petsplus/state/PetComponent.java†L1088-L1131】

## Testing
- `./gradlew check`
