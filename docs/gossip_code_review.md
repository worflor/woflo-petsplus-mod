# Gossip System Code Review

## Summary
This document captures the key issues identified while reviewing the new gossip-system implementation. The intent is to make sure follow-up work keeps the feature reliable, scalable, and immersive.

## Blocking issues

### 1. Abstract fallback topics never unlock
`PetGossipLedger.hasAbstractTopicsReady` seeds `lastShared` with `Long.MIN_VALUE` when a topic has never been emitted. The subsequent subtraction (`currentTick - lastShared`) underflows to a large negative number, so the cooldown check fails and abstract topics are treated as perpetually on cooldown. As a result, `hasShareableRumors` returns `false` whenever the pet only has abstract chatter available, and the social routines never emit the intended small-talk backfill.【F:src/main/java/woflo/petsplus/state/gossip/PetGossipLedger.java†L163-L175】

**Suggested fix:** Treat an unshared topic as immediately available (for example, branch when `lastShared == Long.MIN_VALUE` or initialize with a non-problematic sentinel such as `Long.MIN_VALUE / 2`).

### 2. Witnesses are punished for hearing their own stories
`shareOwnerRumor` pushes every nearby pet's ledger through `PetComponent.recordRumor`, which immediately marks the rumor as "heard". When the next gossip circle forms, those witnesses trip the duplicate guard in `GossipCircleRoutine.shareWithCluster`, take frustration, and opt out before the story ever reaches uninformed pets. The same happens for whispers. This undermines the immersive loop by scolding the participants of the original event and keeps rumors from propagating outward.【F:src/main/java/woflo/petsplus/events/EmotionsEventHandler.java†L1196-L1221】【F:src/main/java/woflo/petsplus/behavior/social/GossipCircleRoutine.java†L223-L256】

**Suggested fix:** Distinguish between witnesses and fresh listeners—either defer seeding the rumor until the circle runs, or track a "witness" flag so the first social exchange skips duplicate penalties for that cohort.

## Next steps
Resolving the blockers above will restore the abstract small-talk rotation and keep rumor sharing positive for pets that lived through the event, preserving both scalability and the intended in-world feel.
