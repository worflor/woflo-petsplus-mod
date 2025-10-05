package woflo.petsplus.state.modules.impl;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.ProgressionModule;

import java.util.*;
import java.util.function.Consumer;

/**
 * Default implementation of ProgressionModule that manages pet leveling,
 * experience, milestones, abilities, and stat boosts.
 */
public class DefaultProgressionModule implements ProgressionModule {
    private PetComponent parent;
    private int level = 1;
    private long experience = 0;
    private final Map<Integer, Boolean> unlockedMilestones = new HashMap<>();
    private final Map<Identifier, Boolean> unlockedAbilities = new HashMap<>();
    private final Map<String, Integer> permanentStatBoosts = new HashMap<>();
    private final Map<Integer, Identifier> tributeMilestones = new HashMap<>();
    private final List<Consumer<LevelUpEvent>> levelUpListeners = new ArrayList<>();

    @Override
    public void onAttach(PetComponent parent) {
        this.parent = parent;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    @Override
    public long getExperience() {
        return experience;
    }

    @Override
    public void addExperience(long amount, ServerWorld world, long currentTick) {
        if (amount <= 0) return;
        
        this.experience = Math.max(0, this.experience + amount);
        
        // Check for level ups
        int oldLevel = this.level;
        int maxIterations = 100; // Prevent infinite loops
        int iterations = 0;
        while (this.experience >= getXpForNextLevel() && this.level < 100 && iterations < maxIterations) {
            this.experience -= getXpForNextLevel();
            this.level++;
            iterations++;
        }
        
        // Cap experience at max level
        if (this.level >= 100) {
            this.experience = 0;
        }
        
        // Fire level up events
        if (this.level > oldLevel) {
            LevelUpEvent event = new LevelUpEvent(this.level, currentTick, world);
            for (Consumer<LevelUpEvent> listener : levelUpListeners) {
                listener.accept(event);
            }
        }
    }

    @Override
    public long getXpForNextLevel() {
        // TODO: Implement role-based XP curve from registry (Phase 4)
        // For now, use linear progression: 100 XP per level
        // Parent is reserved for future role-based progression
        @SuppressWarnings("unused")
        PetComponent unused = parent;
        return 100L * level;
    }

    @Override
    public boolean hasMilestone(int id) {
        return unlockedMilestones.getOrDefault(id, false);
    }

    @Override
    public void unlockMilestone(int id) {
        unlockedMilestones.put(id, true);
    }

    @Override
    public Set<Integer> getUnlockedMilestones() {
        return Collections.unmodifiableSet(unlockedMilestones.keySet());
    }

    @Override
    public boolean hasAbility(Identifier abilityId) {
        return unlockedAbilities.getOrDefault(abilityId, false);
    }

    @Override
    public void unlockAbility(Identifier abilityId) {
        unlockedAbilities.put(abilityId, true);
    }

    @Override
    public Set<Identifier> getUnlockedAbilities() {
        return Collections.unmodifiableSet(unlockedAbilities.keySet());
    }

    @Override
    public Map<String, Integer> getPermanentStatBoosts() {
        return Collections.unmodifiableMap(permanentStatBoosts);
    }

    @Override
    public void addPermanentStatBoost(String statName, float amount) {
        permanentStatBoosts.merge(statName, (int) amount, Integer::sum);
    }

    @Override
    public float getPermanentStatBoost(String statName) {
        return permanentStatBoosts.getOrDefault(statName, 0);
    }

    @Override
    public void setTributeMilestone(int level, Identifier itemId) {
        tributeMilestones.put(level, itemId);
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public Identifier getTributeMilestone(int level) {
        return tributeMilestones.get(level);
    }

    @Override
    public boolean hasTributeMilestone(int level) {
        return tributeMilestones.containsKey(level);
    }

    public void addStatBoost(String statKey, int amount) {
        permanentStatBoosts.merge(statKey, amount, Integer::sum);
    }

    public void setStatBoost(String statKey, int value) {
        if (value == 0) {
            permanentStatBoosts.remove(statKey);
        } else {
            permanentStatBoosts.put(statKey, value);
        }
    }

    @Override
    public void registerLevelUpListener(Consumer<LevelUpEvent> listener) {
        levelUpListeners.add(listener);
    }

    @Override
    public Data toData() {
        return new Data(
            level,
            experience,
            new HashMap<>(unlockedMilestones),
            new HashMap<>(unlockedAbilities),
            new HashMap<>(permanentStatBoosts),
            new HashMap<>(tributeMilestones)
        );
    }

    @Override
    public void fromData(Data data) {
        this.level = Math.max(1, data.level());
        this.experience = Math.max(0, data.experience());
        
        this.unlockedMilestones.clear();
        this.unlockedMilestones.putAll(data.unlockedMilestones());
        
        this.unlockedAbilities.clear();
        this.unlockedAbilities.putAll(data.unlockedAbilities());
        
        this.permanentStatBoosts.clear();
        this.permanentStatBoosts.putAll(data.permanentStatBoosts());
        
        this.tributeMilestones.clear();
        this.tributeMilestones.putAll(data.tributeMilestones());
    }
}
