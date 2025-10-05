package woflo.petsplus.state.modules;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface StateDataModule extends DataBackedModule<StateDataModule.Data> {
    <T> Optional<T> getCustom(String key, Codec<T> codec);
    <T> void setCustom(String key, T value, Codec<T> codec);
    void removeCustom(String key);
    Set<String> getKeys();

    record Data(Map<String, JsonElement> customData) {}
}
