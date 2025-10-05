package woflo.petsplus.state.modules.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.history.HistoryEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.HistoryModule;

public final class DefaultHistoryModule implements HistoryModule {
    private static final int MAX_HISTORY_SIZE = 50;

    private final List<HistoryEvent> events = new ArrayList<>();
    private PetComponent parent;

    @Override
    public void onAttach(PetComponent parent) {
        this.parent = parent;
    }

    @Override
    public void onDetach() {
        events.clear();
        parent = null;
    }

    @Override
    public void recordEvent(HistoryEvent event) {
        if (event == null) {
            return;
        }
        events.add(event);
        if (events.size() > MAX_HISTORY_SIZE) {
            events.remove(0);
        }
    }

    @Override
    public List<HistoryEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    @Override
    public List<HistoryEvent> getEventsForOwner(@Nullable UUID ownerUuid) {
        if (ownerUuid == null) {
            return List.of();
        }
        return events.stream()
            .filter(event -> event.isWithOwner(ownerUuid))
            .collect(Collectors.toList());
    }

    @Override
    public long countEvents(String eventType, @Nullable UUID ownerUuid) {
        if (eventType == null) {
            return 0;
        }
        return events.stream()
            .filter(event -> event.isType(eventType))
            .filter(event -> ownerUuid == null || event.isWithOwner(ownerUuid))
            .count();
    }

    @Override
    public Data toData() {
        return new Data(List.copyOf(events));
    }

    @Override
    public void fromData(Data data) {
        events.clear();
        if (data == null || data.events() == null || data.events().isEmpty()) {
            return;
        }
        if (data.events().size() <= MAX_HISTORY_SIZE) {
            events.addAll(data.events());
        } else {
            events.addAll(data.events().subList(data.events().size() - MAX_HISTORY_SIZE, data.events().size()));
        }
    }

    public PetComponent parent() {
        return parent;
    }
}
