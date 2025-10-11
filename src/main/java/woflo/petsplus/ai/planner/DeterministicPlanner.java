package woflo.petsplus.ai.planner;

import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.group.GroupCoordinator;
import woflo.petsplus.ai.group.GroupContext;
import woflo.petsplus.ai.variants.BehaviorVariant;
import woflo.petsplus.ai.variants.VariantBootstrap;
import woflo.petsplus.ai.variants.VariantSelector;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic planner that resolves plans, caches them by context signature,
 * and performs variant/group selection so downstream systems can execute the
 * resulting behaviour without recomputation each tick.
 */
public final class DeterministicPlanner {

    private static final int MAX_CACHE_SIZE = 64;

    private final VariantSelector variantSelector = new VariantSelector();
    private final GroupCoordinator groupCoordinator = new GroupCoordinator();
    private final java.util.Map<String, PlanResolution> cache = new java.util.LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, PlanResolution> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    public Optional<ActionPlan> resolvePlan(GoalDefinition goal, PetContext context) {
        return PlanRegistry.getPlanForGoal(goal.id());
    }

    public Optional<PlanResolution> resolvePlanWithContext(GoalDefinition goal, PetContext context) {
        Optional<ActionPlan> planOpt = PlanRegistry.getPlanForGoal(goal.id());
        if (planOpt.isEmpty()) {
            return Optional.empty();
        }

        VariantBootstrap.ensureInitialized();
        ActionPlan plan = planOpt.get();
        String signature = createSignature(goal, context, plan);

        PlanResolution cached = cache.get(signature);
        if (cached != null) {
            return Optional.of(cached);
        }

        GroupContext group = resolveGroup(plan, context);
        List<PlanResolution.ResolvedStep> resolvedSteps = resolveSteps(goal, context, plan);
        PlanResolution resolution = new PlanResolution(plan, resolvedSteps, group, signature);
        cache.put(signature, resolution);
        return Optional.of(resolution);
    }

    private GroupContext resolveGroup(ActionPlan plan, PetContext context) {
        if (!plan.requiresOwnerGroup()) {
            return null;
        }
        PetComponent component = context.component();
        if (component == null) {
            return null;
        }

        List<PetComponent> candidates = new ArrayList<>();
        candidates.add(component);
        for (var entity : context.nearbyEntities()) {
            if (entity instanceof net.minecraft.entity.mob.MobEntity mobEntity) {
                PetComponent other = PetComponent.get(mobEntity);
                if (other != null && other != component) {
                    candidates.add(other);
                }
            }
        }

        return groupCoordinator.formOwnerGroup(candidates).orElse(null);
    }

    private List<PlanResolution.ResolvedStep> resolveSteps(GoalDefinition goal, PetContext context, ActionPlan plan) {
        List<PlanResolution.ResolvedStep> resolved = new ArrayList<>();
        for (ActionPlan.Step step : plan.steps()) {
            BehaviorVariant variant = null;
            if (!step.variantPool().isEmpty()) {
                variant = variantSelector.select(goal, context, step.variantPool()).orElse(null);
            }
            if (variant == null) {
                variant = variantSelector.select(goal, context).orElse(null);
            }
            resolved.add(new PlanResolution.ResolvedStep(step, variant));
        }
        return List.copyOf(resolved);
    }

    private String createSignature(GoalDefinition goal, PetContext context, ActionPlan plan) {
        StringBuilder builder = new StringBuilder(goal.id().toString());
        builder.append('|').append(plan.id());
        builder.append('|').append(context.moodLevel());
        builder.append('|').append(Math.round(context.behavioralMomentum() * 10f));
        builder.append('|').append(context.activeEmotions().hashCode());
        builder.append('|').append(context.crowdSummary().friendlyCount());
        builder.append('|').append(context.ownerNearby());
        if (plan.requiresOwnerGroup()) {
            builder.append('|').append(context.component() != null ? context.component().getOwnerUuid() : "");
        }
        return builder.toString();
    }
}

