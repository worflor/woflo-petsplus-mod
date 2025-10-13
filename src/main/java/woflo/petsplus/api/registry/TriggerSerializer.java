package woflo.petsplus.api.registry;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.Trigger;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Serializer contract for turning datapack JSON payloads into concrete {@link Trigger}
 * implementations. Serializers are registered in {@link PetsPlusRegistries} so datapack
 * abilities and custom mods can reference them by identifier.
 *
 * @param <C> configuration model parsed from JSON
 */
public interface TriggerSerializer<C> {
    /**
     * Identifier that downstream datapacks use to reference this serializer.
     */
    Identifier id();

    /**
     * Codec used to decode the configuration from JSON.
     */
    Codec<C> codec();

    /**
     * Optional human readable description for docs/tooling.
     */
    @Nullable
    default String description() {
        return null;
    }

    /**
     * Produces a trigger using the decoded configuration.
     */
    DataResult<Trigger> create(Identifier abilityId, C config);

    /**
     * Decodes configuration data from JSON using the provided codec.
     */
    default DataResult<C> decode(JsonObject json) {
        return codec().parse(JsonOps.INSTANCE, json);
    }

    /**
     * Convenience method that decodes configuration and instantiates the trigger.
     */
    default DataResult<Trigger> read(Identifier abilityId, JsonObject json) {
        return decode(json).flatMap(config -> create(abilityId, config));
    }

    /**
     * Creates a new builder when the construction may fail.
     */
    static <C> Builder<C> builder(Identifier id, Codec<C> codec, TriggerResultFactory<C> factory) {
        return new Builder<>(id, codec, factory);
    }

    /**
     * Convenience helper for serializers that always succeed.
     */
    static <C> Builder<C> simple(Identifier id, Codec<C> codec, TriggerFactory<C> factory) {
        return builder(id, codec, (abilityId, config) -> DataResult.success(factory.create(abilityId, config)));
    }

    @FunctionalInterface
    interface TriggerFactory<C> {
        Trigger create(Identifier abilityId, C config);
    }

    @FunctionalInterface
    interface TriggerResultFactory<C> {
        DataResult<Trigger> create(Identifier abilityId, C config);
    }

    /**
     * Phase A scaffold: Light wrapper to peek top-level schemaVersion without enforcement.
     */
    static OptionalInt peekSchemaVersion(JsonObject obj) {
        return RegistryJsonHelper.getSchemaVersionFromObject(obj);
    }

    /**
     * Fluent builder used by registration helpers.
     */
    final class Builder<C> {
        private final Identifier id;
        private final Codec<C> codec;
        private final TriggerResultFactory<C> factory;
        private String description;

        private Builder(Identifier id, Codec<C> codec, TriggerResultFactory<C> factory) {
            this.id = Objects.requireNonNull(id, "id");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        public Builder<C> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public TriggerSerializer<C> build() {
            String desc = this.description;
            return new TriggerSerializer<>() {
                @Override
                public Identifier id() {
                    return id;
                }

                @Override
                public Codec<C> codec() {
                    return codec;
                }

                @Override
                public String description() {
                    return desc;
                }

                @Override
                public DataResult<Trigger> create(Identifier abilityId, C config) {
                    return factory.create(abilityId, config);
                }
            };
        }
    }
}
