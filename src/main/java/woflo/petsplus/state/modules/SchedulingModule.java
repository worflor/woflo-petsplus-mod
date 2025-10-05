package woflo.petsplus.state.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import woflo.petsplus.state.coordination.PetWorkScheduler;

public interface SchedulingModule extends DataBackedModule<SchedulingModule.Data> {
    void schedule(PetWorkScheduler.TaskType type, long tick);
    void unschedule(PetWorkScheduler.TaskType type);
    boolean hasScheduledWork();
    boolean hasDueWork(long currentTick);
    long getEarliestScheduledTick();
    
    // New methods for direct delegation
    void scheduleTask(PetWorkScheduler.TaskType type, long tick);
    void unscheduleTask(PetWorkScheduler.TaskType type);
    boolean isInitialized();
    void markInitialized();
    void reset();

    // Cooldown management
    boolean isOnCooldown(String key, long currentTime);
    void setCooldown(String key, long endTime);
    void clearCooldown(String key);
    java.util.Map<String, Long> getAllCooldowns();

    record Data(Map<PetWorkScheduler.TaskType, Long> scheduledTicks, long earliestTick, boolean initialized, Map<String, Long> cooldowns) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .xmap(
                        stringMap -> {
                            Map<PetWorkScheduler.TaskType, Long> result = new HashMap<>();
                            stringMap.forEach((k, v) -> result.put(PetWorkScheduler.TaskType.valueOf(k), v));
                            return result;
                        },
                        typeMap -> {
                            Map<String, Long> result = new HashMap<>();
                            typeMap.forEach((k, v) -> result.put(k.name(), v));
                            return result;
                        }
                    )
                    .optionalFieldOf("scheduledTicks", new HashMap<>())
                    .forGetter(Data::scheduledTicks),
                Codec.LONG.optionalFieldOf("earliestTick", Long.MAX_VALUE).forGetter(Data::earliestTick),
                Codec.BOOL.optionalFieldOf("initialized", false).forGetter(Data::initialized),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", new HashMap<>()).forGetter(Data::cooldowns)
            ).apply(instance, Data::new)
        );
    }
}
