package woflo.petsplus.state.processing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import woflo.petsplus.state.PetWorkScheduler;

/**
 * Simple synchronized pool for temporary task lists used during owner batch
 * assembly. Reusing the {@link java.util.ArrayList} instances significantly
 * reduces garbage generated when large swarms fan in many scheduled tasks.
 */
final class PooledTaskLists {
    private static final int MAX_POOL_SIZE = 512;
    private static final ArrayDeque<List<PetWorkScheduler.ScheduledTask>> POOL = new ArrayDeque<>();

    private PooledTaskLists() {
    }

    static List<PetWorkScheduler.ScheduledTask> borrow() {
        List<PetWorkScheduler.ScheduledTask> list;
        synchronized (POOL) {
            list = POOL.pollFirst();
        }
        if (list == null) {
            list = new ArrayList<>(4);
        } else {
            list.clear();
        }
        return list;
    }

    static void recycle(List<PetWorkScheduler.ScheduledTask> list) {
        if (list == null) {
            return;
        }
        list.clear();
        synchronized (POOL) {
            if (POOL.size() < MAX_POOL_SIZE) {
                POOL.addFirst(list);
            }
        }
    }
}
