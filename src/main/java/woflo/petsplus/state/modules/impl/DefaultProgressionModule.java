package woflo.petsplus.state.modules.impl;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.registry.PetRoleType;
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
    private final Map<String, Float> permanentStatBoosts = new HashMap<>();
    private final Map<Integer, Identifier> tributeMilestones = new HashMap<>();
    private final List<Consumer<LevelUpEvent>> levelUpListeners = new ArrayList<>();
    private int maxAbilitySlots = 0;
    private int occupiedAbilitySlots = 0;

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
        this.level = Math.max(1, Math.min(level, getMaxConfiguredLevel()));
    }

    @Override
    public long getExperience() {
        return experience;
    }

    @Override
    public void setExperience(long experience) {
        long sanitized = Math.max(0, experience);
        long xpForNext = getXpForNextLevel();
        if (xpForNext <= 0) {
            this.experience = 0;
        } else {
            this.experience = Math.min(sanitized, xpForNext);
        }
    }

    @Override
    public void addExperience(long amount, ServerWorld world, long currentTick) {
        if (amount < 0) return;

        this.experience = Math.max(0, this.experience + amount);
        
        // Check for level ups
        int oldLevel = this.level;
        int maxLevel = getMaxConfiguredLevel();
        int maxIterations = 100; // Prevent infinite loops
        int iterations = 0;
        long xpForNext = getXpForNextLevel();
        while (xpForNext > 0 && this.experience >= xpForNext && this.level < maxLevel && iterations < maxIterations) {
            this.experience -= xpForNext;
            this.level++;
            iterations++;
            xpForNext = getXpForNextLevel();
        }

        // Cap experience at max level
        if (this.level >= maxLevel) {
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
        PetRoleType.XpCurve curve = parent != null ? parent.getRoleType().xpCurve() : null;
        if (curve != null) {
            if (level >= curve.maxLevel()) {
                return 0L;
            }
            return Math.max(1L, parent.getXpRequiredForLevel(level + 1));
        }
        return Math.max(1L, 100L * level);
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
    public boolean unlockAbility(Identifier abilityId) {
        if (abilityId == null) {
            return false;
        }
        if (unlockedAbilities.containsKey(abilityId)) {
            return false;
        }
        if (occupiedAbilitySlots >= maxAbilitySlots && maxAbilitySlots > 0) {
            return false;
        }
        unlockedAbilities.put(abilityId, true);
        occupiedAbilitySlots = Math.min(maxAbilitySlots, unlockedAbilities.size());
        return true;
    }

    @Override
    public Set<Identifier> getUnlockedAbilities() {
        return Collections.unmodifiableSet(unlockedAbilities.keySet());
    }

    @Override
    public int getMaxAbilitySlots() {
        return maxAbilitySlots;
    }

    @Override
    public int getOccupiedAbilitySlots() {
        return Math.min(occupiedAbilitySlots, maxAbilitySlots);
    }

    @Override
    public void setMaxAbilitySlots(int slots) {
        maxAbilitySlots = Math.max(0, slots);
        occupiedAbilitySlots = Math.min(maxAbilitySlots, unlockedAbilities.size());
    }

    @Override
    public Map<String, Float> getPermanentStatBoosts() {
        return Collections.unmodifiableMap(permanentStatBoosts);
    }

    @Override
    public void addPermanentStatBoost(String statName, float amount) {
        permanentStatBoosts.merge(statName, amount, Float::sum);
    }

    @Override
    public float getPermanentStatBoost(String statName) {
        return permanentStatBoosts.getOrDefault(statName, 0f);
    }

    @Override
    public void clearProgressionUnlocks() {
        unlockedMilestones.clear();
        unlockedAbilities.clear();
        permanentStatBoosts.clear();
        tributeMilestones.clear();
        occupiedAbilitySlots = 0;
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

    public void addStatBoost(String statKey, float amount) {
        permanentStatBoosts.merge(statKey, amount, Float::sum);
    }

    public void setStatBoost(String statKey, float value) {
        if (value == 0f) {
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
            maxAbilitySlots,
            getOccupiedAbilitySlots(),
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
        this.maxAbilitySlots = Math.max(0, data.maxAbilitySlots());
        if (this.maxAbilitySlots < 1 && !this.unlockedAbilities.isEmpty()) {
            this.maxAbilitySlots = this.unlockedAbilities.size();
        }
        if (this.maxAbilitySlots < 1) {
            this.maxAbilitySlots = 1;
        }
        this.occupiedAbilitySlots = Math.min(Math.max(0, data.occupiedAbilitySlots()), this.unlockedAbilities.size());
        this.occupiedAbilitySlots = Math.min(this.occupiedAbilitySlots, this.maxAbilitySlots);

        this.permanentStatBoosts.clear();
        this.permanentStatBoosts.putAll(data.permanentStatBoosts());

        this.tributeMilestones.clear();
        this.tributeMilestones.putAll(data.tributeMilestones());
    }

    private int getMaxConfiguredLevel() {
        PetRoleType.XpCurve curve = parent != null ? parent.getRoleType().xpCurve() : null;
        return curve != null ? curve.maxLevel() : 100;
    }
}
