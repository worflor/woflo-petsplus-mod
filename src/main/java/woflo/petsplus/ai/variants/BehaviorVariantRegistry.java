package woflo.petsplus.ai.variants;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class BehaviorVariantRegistry {

    private static final Map<Identifier, BehaviorVariant> VARIANTS = new LinkedHashMap<>();

    private BehaviorVariantRegistry() {}

    public static void clear() {
        VARIANTS.clear();
    }

    public static void register(BehaviorVariant variant) {
        VARIANTS.put(variant.id(), variant);
    }

    public static Optional<BehaviorVariant> get(Identifier id) {
        return Optional.ofNullable(VARIANTS.get(id));
    }

    public static Collection<BehaviorVariant> all() {
        return Collections.unmodifiableCollection(VARIANTS.values());
    }
}

