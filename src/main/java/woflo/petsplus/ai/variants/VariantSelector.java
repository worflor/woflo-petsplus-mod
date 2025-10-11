package woflo.petsplus.ai.variants;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class VariantSelector {

    public Optional<BehaviorVariant> select(GoalDefinition goal, PetContext context) {
        return BehaviorVariantRegistry.all().stream()
            .filter(variant -> variant.matches(goal, context))
            .min(Comparator.comparing(v -> v.id().toString()));
    }

    public Optional<BehaviorVariant> select(GoalDefinition goal, PetContext context, List<Identifier> preferred) {
        if (preferred != null && !preferred.isEmpty()) {
            for (Identifier id : preferred) {
                Optional<BehaviorVariant> direct = BehaviorVariantRegistry.get(id);
                if (direct.isPresent() && direct.get().matches(goal, context)) {
                    return direct;
                }
            }
        }
        return select(goal, context);
    }

    public Optional<BehaviorVariant> get(Identifier id) {
        return BehaviorVariantRegistry.get(id);
    }
}

