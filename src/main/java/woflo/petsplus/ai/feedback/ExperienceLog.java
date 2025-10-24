package woflo.petsplus.ai.feedback;

import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class ExperienceLog {

    private static final int MAX_ENTRIES = 32;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public void record(Identifier goalId, float satisfaction, long tick) {
        entries.addFirst(new Entry(goalId, satisfaction, tick));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public record Entry(Identifier goalId, float satisfaction, long tick) {}
}

