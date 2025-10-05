package woflo.petsplus.state.modules;

import java.util.Map;

public interface CooldownModule extends DataBackedModule<CooldownModule.Data> {
    boolean isOnCooldown(String key, long currentTick);
    void setCooldown(String key, long durationTicks);
    void clearCooldown(String key);
    long getRemainingTicks(String key, long currentTick);
    Map<String, Long> snapshot();
    long getLastAttackTick();
    void recordAttack(long tick);
    void applyExpirations(Iterable<String> keys, long currentTick);

    record Data(Map<String, Long> cooldowns, long lastAttackTick) {}
}
