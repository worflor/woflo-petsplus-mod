package woflo.petsplus.ai.variants;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;

import java.util.List;

/**
 * Registers a handful of built-in behaviour variants so planner decisions can
 * resolve variant IDs referenced by data packs. The bootstrap keeps the
 * registry deterministic and avoids requiring data for simple default poses.
 */
public final class VariantBootstrap {

    private static volatile boolean initialized;

    private VariantBootstrap() {
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (VariantBootstrap.class) {
            if (initialized) {
                return;
            }
            registerDefaults();
            initialized = true;
        }
    }

    private static void registerDefaults() {
        register("trotting", List.of("play", "wander"), "pose.trotting");
        register("searching", List.of("play", "special"), "pose.search");
        register("return_proud", List.of("play", "social"), "pose.return_proud");
    }

    private static void register(String path, List<String> supportedCategories, String pose) {
        Identifier id = Identifier.of("petsplus", path);
        BehaviorVariantRegistry.register(new SimpleVariant(id, supportedCategories, pose));
    }

    private record SimpleVariant(Identifier id, List<String> categories, String poseTag)
        implements BehaviorVariant {

        @Override
        public boolean matches(GoalDefinition goal, PetContext context) {
            if (goal == null) {
                return false;
            }
            String category = goal.category().name().toLowerCase();
            return categories.contains(category);
        }
    }
}
