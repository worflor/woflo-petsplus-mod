package woflo.petsplus.api.registry;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Placeholder registry entry for level rewards. Future tasks can attach
 * serializers or factories without having to change registration code.
 */
public final class LevelRewardType {
    private final Identifier id;
    private final String description;

    public LevelRewardType(Identifier id, @Nullable String description) {
        this.id = Objects.requireNonNull(id, "id");
        this.description = description;
    }

    public Identifier id() {
        return id;
    }

    @Nullable
    public String description() {
        return description;
    }
}
