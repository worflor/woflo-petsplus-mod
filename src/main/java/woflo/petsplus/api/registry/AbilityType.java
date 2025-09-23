package woflo.petsplus.api.registry;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.Ability;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Describes a single ability definition that can be produced on demand.
 *
 * <p>The registry stores the metadata that downstream systems need to
 * instantiate abilities (role ownership, lazy factory, etc.) without
 * duplicating JSON payloads or parsing logic.</p>
 */
public final class AbilityType {
    private final Identifier id;
    private volatile Supplier<Ability> factory;
    private volatile String description;

    public AbilityType(Identifier id, Supplier<Ability> factory, @Nullable String description) {
        this.id = Objects.requireNonNull(id, "id");
        update(factory, description);
    }

    public synchronized void update(Supplier<Ability> factory, @Nullable String description) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.description = description;
    }

    public Identifier id() {
        return id;
    }

    /**
     * Creates a new ability instance using the registered factory.
     */
    public Ability createAbility() {
        return factory.get();
    }

    /**
     * Optional human readable description used in tooling/docs.
     */
    @Nullable
    public String description() {
        return description;
    }
}
