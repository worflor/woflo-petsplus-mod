# Social Proximity Feasibility Smoke Check

Use this quick JShell script to exercise the signal's new owner-versus-pack logic with synthetic
contexts. The script constructs lightweight stand-ins for the `PetContext` interface so the logic
can be verified without standing up the game runtime.

```
/env -class-path build/classes/java/main

import java.util.Map;
import java.util.Set;
import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.ai.suggester.signal.feasibility.SocialProximityFeasibilitySignal;
import woflo.petsplus.ai.capability.MobCapabilities;
import net.minecraft.util.math.Vec2f;

record StubContext(boolean ownerNearby, float ownerDistance, PetContextCrowdSummary summary) implements PetContext {
    @Override public boolean ownerNearby() { return ownerNearby; }
    @Override public float distanceToOwner() { return ownerDistance; }
    @Override public PetContextCrowdSummary crowdSummary() { return summary; }
}

MobCapabilities.CapabilityRequirement none = MobCapabilities.CapabilityRequirement.any();
GoalDefinition ownerGoal = new GoalDefinition(Identifier.of("petsplus", "lean_against_owner"), GoalDefinition.Category.SOCIAL, 1, 0, 0, none, new Vec2f(0, 1), GoalDefinition.IdleStaminaBias.CENTERED, false, mob -> null);
GoalDefinition packGoal = new GoalDefinition(Identifier.of("petsplus", "pack_play"), GoalDefinition.Category.SOCIAL, 1, 0, 0, none, new Vec2f(0, 1), GoalDefinition.IdleStaminaBias.CENTERED, false, mob -> null);

var signal = new SocialProximityFeasibilitySignal();
var emptySummary = PetContextCrowdSummary.empty();
var packSummary = new PetContextCrowdSummary(2, 0, 0, 4.0, Double.POSITIVE_INFINITY);

SignalResult ownerAbsent = signal.evaluate(ownerGoal, new StubContext(false, 10f, emptySummary));
SignalResult packmatesOnly = signal.evaluate(packGoal, new StubContext(false, 10f, packSummary));
System.out.println(ownerAbsent);
System.out.println(packmatesOnly);
```

The second `SignalResult` prints a partial multiplier (>0) whenever packmates are present, proving
the pack fallback path is exercised.
```
/exit
```
