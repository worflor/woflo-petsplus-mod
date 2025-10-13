package woflo.petsplus.api.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.Effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Serializer contract for converting datapack JSON payloads into {@link Effect}
 * implementations. Effect serializers can request nested deserialisation through the
 * supplied {@link Context} to support composite definitions.
 *
 * @param <C> configuration model parsed from JSON
 */
public interface EffectSerializer<C> {
    Identifier id();

    Codec<C> codec();

    @Nullable
    default String description() {
        return null;
    }

    DataResult<Effect> create(Identifier abilityId, C config, Context context);

    default DataResult<C> decode(JsonObject json) {
        return codec().parse(JsonOps.INSTANCE, json);
    }

    default DataResult<Effect> read(Identifier abilityId, JsonObject json, Context context) {
        return decode(json).flatMap(config -> create(abilityId, config, context));
    }

    static <C> Builder<C> builder(Identifier id, Codec<C> codec, EffectResultFactory<C> factory) {
        return new Builder<>(id, codec, factory);
    }

    static <C> Builder<C> simple(Identifier id, Codec<C> codec, EffectFactory<C> factory) {
        return builder(id, codec, (abilityId, config, context) ->
            DataResult.success(factory.create(abilityId, config, context)));
    }

    @FunctionalInterface
    interface EffectFactory<C> {
        Effect create(Identifier abilityId, C config, Context context);
    }

    @FunctionalInterface
    interface EffectResultFactory<C> {
        DataResult<Effect> create(Identifier abilityId, C config, Context context);
    }

    interface Context {
        default DataResult<Effect> deserialize(JsonObject json) {
            return deserialize(json, "");
        }

        DataResult<Effect> deserialize(JsonObject json, String pointer);

        default DataResult<List<Effect>> deserializeList(JsonArray array) {
            return deserializeList(array, "");
        }

        default DataResult<List<Effect>> deserializeList(JsonArray array, String pointer) {
            List<Effect> effects = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) {
                    errors.add(pointerWithIndex(pointer, i) + " is not a JSON object");
                    continue;
                }
                DataResult<Effect> result = deserialize(element.getAsJsonObject(), pointerWithIndex(pointer, i));
                result.resultOrPartial(errors::add).ifPresent(effects::add);
            }

            if (!errors.isEmpty()) {
                String message = String.join("; ", errors);
                return DataResult.error(() -> message);
            }

            return DataResult.success(effects);
        }

        private static String pointerWithIndex(String pointer, int index) {
            String suffix = "[" + index + "]";
            if (pointer == null || pointer.isEmpty()) {
                return suffix;
            }
            return pointer + suffix;
        }
    }

    /** Reads the top-level schema version, if present, without enforcing it. */
    static OptionalInt peekSchemaVersion(JsonObject obj) {
        return RegistryJsonHelper.getSchemaVersionFromObject(obj);
    }

    final class Builder<C> {
        private final Identifier id;
        private final Codec<C> codec;
        private final EffectResultFactory<C> factory;
        private String description;

        private Builder(Identifier id, Codec<C> codec, EffectResultFactory<C> factory) {
            this.id = Objects.requireNonNull(id, "id");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        public Builder<C> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public EffectSerializer<C> build() {
            String desc = this.description;
            return new EffectSerializer<>() {
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
                public DataResult<Effect> create(Identifier abilityId, C config, Context context) {
                    return factory.create(abilityId, config, context);
                }
            };
        }
    }
}
