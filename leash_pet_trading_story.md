# Leash-Based Pet Trading System Plan

## Feasibility overview
- Ownership information already lives in `PetComponent`, which keeps both the live `PlayerEntity` reference and UUID synchronized whenever `setOwner` or `setOwnerUuid` are called, so updating that component is enough to move all downstream systems to the new owner.
- Every tameable mob in the mod implements `PetsplusTameable`, letting you call `petsplus$setOwner` / `petsplus$setOwnerUuid` in a uniform way across vanilla tameables (via `TameableEntityMixin`) and custom companions (via `ComponentBackedTameableBridge`).
- The project already listens for sneaking interactions at the packet layer (`ServerPlayNetworkHandlerMixin`), so introducing another interaction hook (for leashes) fits the existing architecture.

## Implementation sketch

### 1. Capture the sneaking lead hand-off with a dedicated mixin
Intercept `LeadItem#useOnEntity` when a sneaking player uses a lead on another player, but keep the vanilla behavior untouched whenever no eligible pets are found. Use a thin mixin whose sole job is to delegate into a purpose-built handler so the interaction logic stays testable outside of injection code. Split guard rails aggressively: exit immediately on client worlds, non-lead stacks, or when the player is not sneaking before allocating helper objects or scanning entities. When the interaction qualifies, compute a tight search box (e.g., inflate the recipient’s bounding box by 10 blocks horizontally and 3 vertically) to constrain the pet lookup.

:::task-stub{title="Handle sneaking lead hand-off between players"}
- Create `src/main/java/woflo/petsplus/mixin/item/LeadItemMixin.java` targeting `net.minecraft.item.LeadItem#useOnEntity` with an `@Inject(at = @At("HEAD"), cancellable = true)`.
- Bail out unless `!world.isClient`, the user is sneaking, the stack is a lead, and the hit entity is a `PlayerEntity` that can receive pets (not spectator, alive, within trading distance).
- Delegate to a new `PetTradingHandler.trySwapLeash(ServerPlayerEntity owner, ServerPlayerEntity recipient, ItemStack lead, List<MobEntity> candidates)` that receives a prefetched list of nearby, owner-leashed mobs so the handler remains pure and testable without reaching into mixin state.
- Package the owner, recipient, lead stack, and filtered candidate list into a lightweight context object passed to the handler to keep logic self-contained and mockable.
- Inside the handler, sort the candidates by squared distance to the recipient, filter to `PetsplusTameable` + `PetComponent` pairs still owned by the initiator, ensure the leash holder remains the initiator, and return `ActionResult.PASS` when no candidates survive so vanilla lead attachment still works.
- Expose an overload such as `trySwapLeash(ServerPlayerEntity owner, ServerPlayerEntity recipient, ItemStack lead, MobEntity pet)` so other features (admin commands, datapack hooks) can reuse the validation + transfer flow without rebuilding the proximity search.
- When a pet is selected and the swap succeeds, cancel the mixin with `ActionResult.SUCCESS` to suppress vanilla logic and consume any lead handling performed by the helper.
:::

### 2. Centralize ownership swaps in a reusable helper
Keep all state transitions inside a dedicated helper so mixins, commands, and future automation can rely on one codepath. This helper should detach the leash, hand off ownership, and reattach to the recipient while keeping both component and tameable layers in sync.

:::task-stub{title="Implement reusable pet ownership transfer helper"}
- Introduce `woflo.petsplus.pet.PetOwnershipTransfers` with a static `transfer(ServerPlayerEntity owner, ServerPlayerEntity recipient, MobEntity pet, ItemStack leadStack)` method returning a `TransferResult` enum (`SUCCESS`, `NOT_OWNED`, `NOT_LEASHED`, `INCOMPATIBLE`, etc.) that includes contextual data (pet display name, failure reason) for localization.
- Validate that the pet implements `PetsplusTameable`, remains leashed to the owner, and that `PetComponent.get(pet).isOwnedBy(owner)` still holds; short-circuit when validation fails and surface the corresponding `TransferResult`.
- Confirm the leash holder is still the initiator before any detach to avoid mid-tick desyncs, then call `pet.detachLeash(true, false)` so physics and pathfinding reset cleanly; invoke `tameable.petsplus$setOwner(recipient)` / `tameable.petsplus$setOwnerUuid(recipient.getUuid())` to propagate through existing mixin bridges.
- Immediately reattach the leash via `pet.attachLeash(recipient, true)` and capture any dropped lead items so they can be added to the recipient’s inventory (with a drop fallback) to reinforce the “swap the lead” fantasy.
- Bubble meaningful failure states back to the caller through the enum so user-facing messaging can explain whether the pet was out of range, unowned, un-leashed, or otherwise disqualified.
:::

### 3. Refresh component state and nature data post-trade
After ownership transitions, force component refreshes so downstream systems (roles, nature, scheduling) behave as if the new owner just tamed the pet. This keeps the trade pathway aligned with taming and avoids stale metadata.

:::task-stub{title="Re-sync component state after ownership transfer"}
- From `PetOwnershipTransfers.transfer`, call `PetComponent.ensureCharacteristics()` once the new owner is set to guarantee roles/natures exist and make the routine idempotent so repeat trades or restarts cannot double-assign content.
- If the pet lacked a nature, reroute through the existing `PetDetectionHandler` (or an extracted helper) that assigns natures when pets are first discovered, ensuring reused logic.
- Queue any onboarding prompts the new owner should see (e.g., role selection screens) when the pet did not previously have that data, mirroring the taming flow and preventing registration gaps.
- Reset or reschedule any background tasks tied to the former owner so the new owner picks them up, leveraging whatever scheduling hooks already listen for ownership changes; ensure these operations tolerate retries to remain idempotent.
:::

### 4. Message both players and enforce guardrails
Surface clear, localized feedback for both the initiator and the recipient so trades feel intentional. Use the validation errors exposed by the helper to drive error text and ensure the feature respects world rules.

:::task-stub{title="Add messaging and validation for pet trades"}
- Convert helper results into translation-backed chat messages (e.g., `text.petsplus.pet_trade.success`, `text.petsplus.pet_trade.not_owned`). Announce success to both players and explain failures to the initiator by mapping `TransferResult` states into localized text.
- Block trades when the recipient cannot accept pets (spectator mode, dead, world-rule conflicts) before calling into the helper, mirroring the guard list used in the mixin entry point, and surface these reasons via the new translations.
- When the swap consumes a lead, attempt to insert a replacement into the recipient’s inventory with a drop fallback so the physical “lead swap” fantasy matches expectations.
- Emit advancement or stat hooks if desired, but ensure the mixin exits early with `ActionResult.PASS` on failures so vanilla behavior remains consistent. Only cancel with `ActionResult.SUCCESS` after dispatching messaging to avoid vanilla leash logic double-triggering.
:::

## Testing
⚠️ Not run (static analysis only).
