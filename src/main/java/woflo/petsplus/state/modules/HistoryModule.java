package woflo.petsplus.state.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.history.HistoryEvent;

public interface HistoryModule extends DataBackedModule<HistoryModule.Data> {
    void recordEvent(HistoryEvent event);
    List<HistoryEvent> getEvents();
    List<HistoryEvent> getEventsForOwner(@Nullable UUID ownerUuid);
    long countEvents(String type, @Nullable UUID ownerUuid);

    record Data(List<HistoryEvent> events) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                HistoryEvent.CODEC.listOf().optionalFieldOf("events", new ArrayList<>()).forGetter(Data::events)
            ).apply(instance, Data::new)
        );
    }
}
