package woflo.petsplus.state.processing;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks a global pool of asynchronous worker slots that can be shared across
 * multiple worlds. Coordinators register for a slice of the pool and claim
 * individual slots as jobs begin execution to avoid oversubscribing the
 * server's CPUs.
 */
public final class AsyncWorkerBudget {
    private static final AsyncWorkerBudget GLOBAL = new AsyncWorkerBudget();

    private final int ceiling;
    private final Set<Registration> registrations = ConcurrentHashMap.newKeySet();
    private final AtomicInteger claimed = new AtomicInteger();

    private AsyncWorkerBudget() {
        int processors = Runtime.getRuntime().availableProcessors();
        this.ceiling = Math.max(1, processors - 1);
    }

    public static AsyncWorkerBudget global() {
        return GLOBAL;
    }

    public Registration registerCoordinator() {
        Registration registration = new Registration(this);
        registrations.add(registration);
        return registration;
    }

    void unregister(Registration registration) {
        if (registration == null) {
            return;
        }
        if (registrations.remove(registration)) {
            int drained = registration.drainClaims();
            if (drained > 0) {
                claimed.addAndGet(-drained);
            }
        }
    }

    int permittedSlots(Registration registration) {
        if (registration == null || registration.isClosed()) {
            return 0;
        }
        int coordinatorCount = Math.max(1, registrations.size());
        int baseShare = ceiling / coordinatorCount;
        if (ceiling % coordinatorCount != 0) {
            baseShare++;
        }
        if (baseShare <= 0 && ceiling > 0) {
            baseShare = 1;
        }
        return Math.min(ceiling, baseShare);
    }

    boolean tryClaimSlot(Registration registration) {
        if (registration == null || registration.isClosed()) {
            return false;
        }
        if (ceiling <= 0) {
            return false;
        }
        while (true) {
            int current = claimed.get();
            if (current >= ceiling) {
                return false;
            }
            if (claimed.compareAndSet(current, current + 1)) {
                registration.activeClaims.incrementAndGet();
                return true;
            }
        }
    }

    void releaseSlot(Registration registration) {
        if (registration == null) {
            return;
        }
        while (true) {
            int current = registration.activeClaims.get();
            if (current <= 0) {
                return;
            }
            if (registration.activeClaims.compareAndSet(current, current - 1)) {
                claimed.decrementAndGet();
                return;
            }
        }
    }

    /**
     * Tracks the state for an individual coordinator's share of the global
     * budget. Registrations are idempotent and can be closed to release any
     * claimed slots.
     */
    public static final class Registration implements AutoCloseable {
        private final AsyncWorkerBudget budget;
        private final AtomicInteger activeClaims = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Registration(AsyncWorkerBudget budget) {
            this.budget = budget;
        }

        public int permittedSlots() {
            return budget.permittedSlots(this);
        }

        public boolean tryClaimSlot() {
            return budget.tryClaimSlot(this);
        }

        public void releaseSlot() {
            budget.releaseSlot(this);
        }

        private int drainClaims() {
            return activeClaims.getAndSet(0);
        }

        boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                budget.unregister(this);
            }
        }
    }
}
