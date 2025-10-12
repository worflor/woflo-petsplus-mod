# Fetch Item Goal – Owner Proximity Smoke

Quick reasoning checks to confirm the fetch goal now respects owner proximity limits.

## Setup
1. Spawn or select a tame pet that supports the Fetch Item adaptive goal.
2. Ensure the owner is in Survival/Adventure so items can be tossed.
3. Keep creative flight or spectator nearby to reposition the owner quickly.

## Checks
1. **Owner nearby and within 16 blocks**
   - Drop an item roughly 6–8 blocks from the pet while standing beside it.
   - Expect the goal to start, the pet to grab the item, and return it to the owner.
2. **Owner further than 16 blocks**
   - Walk the owner >16 blocks away from the pet and drop another item near the pet.
   - Observe via logs (`[FetchItemGoal] ... owner_out_of_range`) that the goal is skipped and the pet ignores the item.
3. **Owner moves away mid-fetch**
   - Start a fetch (owner nearby), then sprint the owner beyond 16 blocks before the pet returns.
   - Verify the goal aborts, dropping any carried item safely at the pet’s location.

## Notes
- The new range gate mirrors the `PetContext#ownerNearby` heuristic and keeps fetch loops anchored to the owner’s vicinity.
- Debug logs help correlate skipped attempts when validating datapack tweaks or custom roles.
