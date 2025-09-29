package woflo.petsplus.state.processing;

/**
 * Priority buckets recognised by {@link AsyncWorkCoordinator}. Jobs submitted
 * to the coordinator can use these priorities to influence execution ordering
 * without bypassing the existing load-shedding and concurrency guards.
 */
public enum AsyncJobPriority {
    CRITICAL(3),
    HIGH(2),
    NORMAL(1),
    LOW(0);

    private final int weight;

    AsyncJobPriority(int weight) {
        this.weight = weight;
    }

    int weight() {
        return weight;
    }
}
