package woflo.petsplus.state.modules.impl;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetWorkScheduler;
import woflo.petsplus.state.modules.SchedulingModule;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of SchedulingModule that tracks scheduled task ticks
 * and maintains the earliest scheduled tick for fast queries.
 */
public class DefaultSchedulingModule implements SchedulingModule {
    private PetComponent parent;
    private final EnumMap<PetWorkScheduler.TaskType, Long> scheduledTicks =
        new EnumMap<>(PetWorkScheduler.TaskType.class);
    private long earliestTick = Long.MAX_VALUE;
    private boolean initialized = false;
    private final Map<String, Long> cooldowns = new HashMap<>();

    @Override
    public void onAttach(PetComponent parent) {
        this.parent = parent;
    }

    @Override
    public void schedule(PetWorkScheduler.TaskType type, long tick) {
        long sanitizedTick = (tick == Long.MAX_VALUE) ? Long.MAX_VALUE : Math.max(0L, tick);
        scheduledTicks.put(type, sanitizedTick);
        recomputeEarliestTick();
    }
    
    @Override
    public void scheduleTask(PetWorkScheduler.TaskType type, long tick) {
        schedule(type, tick);
    }

    @Override
    public void unschedule(PetWorkScheduler.TaskType type) {
        scheduledTicks.remove(type);
        recomputeEarliestTick();
    }
    
    @Override
    public void unscheduleTask(PetWorkScheduler.TaskType type) {
        unschedule(type);
    }

    @Override
    public boolean hasScheduledWork() {
        return !scheduledTicks.isEmpty();
    }

    @Override
    public boolean hasDueWork(long currentTick) {
        return earliestTick != Long.MAX_VALUE && currentTick >= earliestTick;
    }

    @Override
    public long getEarliestScheduledTick() {
        return earliestTick;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void markInitialized() {
        this.initialized = true;
    }
    
    @Override
    public void reset() {
        markUninitialized();
    }

    public void markUninitialized() {
        this.initialized = false;
        this.scheduledTicks.clear();
        this.earliestTick = Long.MAX_VALUE;
    }

    public Map<PetWorkScheduler.TaskType, Long> getScheduledTicks() {
        return Collections.unmodifiableMap(scheduledTicks);
    }

    public Long getScheduledTick(PetWorkScheduler.TaskType type) {
        return scheduledTicks.get(type);
    }

    @Override
    public boolean isOnCooldown(String key, long currentTime) {
        Long cooldownEnd = cooldowns.get(key);
        return cooldownEnd != null && currentTime < cooldownEnd;
    }

    @Override
    public void setCooldown(String key, long endTime) {
        cooldowns.put(key, endTime);
    }

    @Override
    public void clearCooldown(String key) {
        cooldowns.remove(key);
    }

    @Override
    public Map<String, Long> getAllCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    private void recomputeEarliestTick() {
        if (scheduledTicks.isEmpty()) {
            earliestTick = Long.MAX_VALUE;
            return;
        }
        
        long min = Long.MAX_VALUE;
        for (Long tick : scheduledTicks.values()) {
            if (tick != null && tick < min) {
                min = tick;
            }
        }
        earliestTick = min;
    }

    @Override
    public Data toData() {
        // Scheduling data is transient - reinitialized on load
        // We mark as uninitialized so the system will reschedule tasks
        return new Data(
            new EnumMap<>(scheduledTicks),
            earliestTick,
            false, // Always mark as uninitialized on save
            new HashMap<>(cooldowns) // Save cooldowns
        );
    }

    @Override
    public void fromData(Data data) {
        // Clear existing state
        this.scheduledTicks.clear();
        this.earliestTick = Long.MAX_VALUE;
        this.initialized = false;
        this.cooldowns.clear();
        
        // Restore cooldowns
        if (data.cooldowns() != null) {
            this.cooldowns.putAll(data.cooldowns());
        }
        
        // Scheduling is transient - do not restore tasks
        // The system will reschedule as needed
    }
}
