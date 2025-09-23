package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.stats.PetCharacteristics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Component that tracks pet-specific state including role and cooldowns.
 */
public class PetComponent {
    private static final Map<MobEntity, PetComponent> COMPONENTS = new WeakHashMap<>();
    private static final Identifier DEFAULT_ROLE_ID = PetRoleType.GUARDIAN_ID;

    private final MobEntity pet;
    private Identifier roleId;
    private PlayerEntity owner;
    private final Map<String, Long> cooldowns;
    private final Map<String, Object> stateData;
    private long lastAttackTick;
    private boolean isPerched;
    private int level;
    private int experience;
    private final Map<Integer, Boolean> unlockedMilestones;
    private PetCharacteristics characteristics;
    
    public PetComponent(MobEntity pet) {
        this.pet = pet;
        this.roleId = DEFAULT_ROLE_ID;
        this.cooldowns = new HashMap<>();
        this.stateData = new HashMap<>();
        this.lastAttackTick = 0;
        this.isPerched = false;
        this.level = 1; // Start at level 1
        this.experience = 0;
        this.unlockedMilestones = new HashMap<>();
        this.characteristics = null; // Will be generated when pet is first tamed
    }
    
    public static PetComponent getOrCreate(MobEntity pet) {
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            return StateManager.forWorld(serverWorld).getPetComponent(pet);
        }
        return COMPONENTS.computeIfAbsent(pet, PetComponent::new);
    }
    
    @Nullable
    public static PetComponent get(MobEntity pet) {
        if (pet == null) {
            return null;
        }
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager manager = StateManager.forWorld(serverWorld);
            return manager.getAllPetComponents().get(pet);
        }
        return COMPONENTS.get(pet);
    }
    
    public static void set(MobEntity pet, PetComponent component) {
        COMPONENTS.put(pet, component);
    }
    
    public static void remove(MobEntity pet) {
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager.forWorld(serverWorld).removePet(pet);
        }
        COMPONENTS.remove(pet);
    }
    
    public MobEntity getPet() {
        return pet;
    }
    
    public Identifier getRoleId() {
        return roleId != null ? roleId : DEFAULT_ROLE_ID;
    }

    public boolean hasRole(@Nullable Identifier id) {
        if (id == null) {
            return false;
        }
        return getRoleId().equals(id);
    }

    public boolean hasRole(@Nullable PetRoleType type) {
        return type != null && hasRole(type.id());
    }

    public boolean hasRole(@Nullable RegistryEntry<PetRoleType> entry) {
        return entry != null && hasRole(entry.value());
    }

    public void setRoleId(@Nullable Identifier id) {
        Identifier newId = id != null ? id : DEFAULT_ROLE_ID;
        this.roleId = newId;

        // Apply attribute modifiers when role changes
        woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
    }

    public void setRoleType(@Nullable PetRoleType type) {
        setRoleId(type != null ? type.id() : DEFAULT_ROLE_ID);
    }

    public void setRoleEntry(@Nullable RegistryEntry<PetRoleType> entry) {
        if (entry == null) {
            setRoleId(DEFAULT_ROLE_ID);
            return;
        }

        setRoleType(entry.value());
    }

    @Nullable
    public RegistryEntry<PetRoleType> getRoleEntry() {
        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        PetRoleType type = registry.get(getRoleId());
        if (type != null) {
            return registry.getEntry(type);
        }

        PetRoleType fallback = registry.get(DEFAULT_ROLE_ID);
        return fallback != null ? registry.getEntry(fallback) : null;
    }

    public PetRoleType getRoleType() {
        return getRoleType(true);
    }

    public PetRoleType getRoleType(boolean logMissing) {
        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        Identifier roleId = getRoleId();
        PetRoleType type = registry.get(roleId);
        if (type != null) {
            return type;
        }

        if (logMissing) {
            Petsplus.LOGGER.warn("Pet {} references missing role {}; defaulting to {}", pet.getUuid(), roleId, DEFAULT_ROLE_ID);
        }

        PetRoleType fallback = registry.get(DEFAULT_ROLE_ID);
        return fallback != null ? fallback : PetRoleType.GUARDIAN;
    }

    @Nullable
    public PlayerEntity getOwner() {
        return owner;
    }
    
    public void setOwner(@Nullable PlayerEntity owner) {
        this.owner = owner;
    }
    
    public boolean isOwnedBy(@Nullable PlayerEntity player) {
        return owner != null && owner.equals(player);
    }
    
    public boolean isOnCooldown(String key) {
        Long cooldownEnd = cooldowns.get(key);
        return cooldownEnd != null && pet.getWorld().getTime() < cooldownEnd;
    }

    public void setCooldown(String key, int ticks) {
        cooldowns.put(key, pet.getWorld().getTime() + ticks);
    }

    public void clearCooldown(String key) {
        cooldowns.remove(key);
    }

    /**
     * Updates cooldowns and triggers particle effects when they expire
     * Should be called periodically to check for cooldown refreshes
     */
    public void updateCooldowns() {
        if (!(pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return;
        }
        
        long currentTime = pet.getWorld().getTime();
        boolean anyExpired = false;
        
        // Check for expired cooldowns
        Iterator<Map.Entry<String, Long>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime >= entry.getValue()) {
                // Cooldown has expired
                anyExpired = true;
                iterator.remove(); // Remove expired cooldown
            }
        }
        
        // Trigger particle effect if any cooldown expired
        if (anyExpired) {
            woflo.petsplus.ui.CooldownParticleManager.triggerCooldownRefresh(serverWorld, pet);
        }
    }

    public long getRemainingCooldown(String key) {
        Long cooldownEnd = cooldowns.get(key);
        if (cooldownEnd == null) return 0;
        return Math.max(0, cooldownEnd - pet.getWorld().getTime());
    }
    
    public void setStateData(String key, Object value) {
        stateData.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getStateData(String key, Class<T> type) {
        Object value = stateData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Get typed state data with a default value when missing or wrong type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getStateData(String key, Class<T> type, T defaultValue) {
        Object value = stateData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }
    
    public long getLastAttackTick() {
        return lastAttackTick;
    }
    
    public void setLastAttackTick(long tick) {
        this.lastAttackTick = tick;
    }
    
    public boolean isPerched() {
        return isPerched;
    }
    
    public void setPerched(boolean perched) {
        this.isPerched = perched;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }
    
    public int getExperience() {
        return experience;
    }
    
    public void setExperience(int experience) {
        this.experience = Math.max(0, experience);
    }
    
    /**
     * Get the pet's unique characteristics.
     */
    @Nullable
    public PetCharacteristics getCharacteristics() {
        return characteristics;
    }
    
    /**
     * Set the pet's characteristics (usually generated once when first tamed).
     */
    public void setCharacteristics(@Nullable PetCharacteristics characteristics) {
        this.characteristics = characteristics;
    }
    
    /**
     * Generate and set characteristics for this pet if they don't exist.
     * Should be called when a pet is first tamed.
     */
    public void ensureCharacteristics() {
        if (characteristics == null) {
            long tameTime = pet.getWorld().getTime();
            characteristics = PetCharacteristics.generateForNewPet(pet, tameTime);
            
            // Apply attribute modifiers with the new characteristics
            woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
        }
    }
    
    /**
     * Calculate XP required for a specific level.
     * Feature levels are: 3, 7, 12, 17, 23, 27
     * Uses exponential scaling similar to Minecraft player XP but much more gentle.
     */
    public int getXpRequiredForLevel(int level) {
        if (level <= 1) {
            return 0;
        }

        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        int baseXp = (level - 1) * curve.baseLinearPerLevel()
            + (level - 1) * (level - 1) * curve.quadraticFactor();

        if (curve.isFeatureLevel(level)) {
            baseXp = Math.round(baseXp * curve.featureLevelBonusMultiplier());
        }

        return Math.max(1, baseXp);
    }

    /**
     * Get total XP required from level 1 to reach target level.
     */
    public int getTotalXpForLevel(int level) {
        int total = 0;
        for (int i = 2; i <= level; i++) {
            total += getXpRequiredForLevel(i);
        }
        return total;
    }
    
    /**
     * Add experience to the pet and handle level ups.
     * @param xpGained Amount of XP to add
     * @return true if the pet leveled up
     */
    public boolean addExperience(int xpGained) {
        if (xpGained <= 0) return false;
        
        int oldLevel = this.level;
        this.experience += xpGained;
        
        // Check for level ups
        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        while (this.experience >= getTotalXpForLevel(this.level + 1) && this.level < curve.maxLevel()) {
            this.level++;
        }

        return this.level > oldLevel;
    }
    
    /**
     * Get XP progress toward next level as a percentage (0.0 to 1.0).
     */
    public float getXpProgress() {
        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        if (level >= curve.maxLevel()) return 1.0f;

        int currentLevelTotalXp = getTotalXpForLevel(level);
        int nextLevelTotalXp = getTotalXpForLevel(level + 1);
        int xpForThisLevel = Math.max(1, nextLevelTotalXp - currentLevelTotalXp);
        int currentXpInLevel = experience - currentLevelTotalXp;

        return Math.max(0f, Math.min(1f, (float)currentXpInLevel / xpForThisLevel));
    }
    
    /**
     * Check if pet has reached a feature level (3, 7, 12, 17, 23, 27).
     */
    public boolean isFeatureLevel() {
        return getRoleType().xpCurve().isFeatureLevel(level);
    }
    
    /**
     * Check if pet is at least the required level for an ability.
     */
    public boolean hasLevel(int requiredLevel) {
        return level >= requiredLevel;
    }

    /**
     * Check if a milestone level has been unlocked with tribute.
     */
    public boolean isMilestoneUnlocked(int milestoneLevel) {
        return unlockedMilestones.getOrDefault(milestoneLevel, false);
    }

    /**
     * Unlock a milestone level (tribute paid).
     */
    public void unlockMilestone(int milestoneLevel) {
        unlockedMilestones.put(milestoneLevel, true);
    }

    /**
     * Check if pet is waiting for tribute at current level.
     */
    public boolean isWaitingForTribute() {
        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        for (int milestone : curve.tributeMilestones()) {
            if (level == milestone && !isMilestoneUnlocked(milestone)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Set pet metadata from an item component (for metadata tag items).
     * This updates the pet's display name, bond strength, and custom tags.
     */
    public void setPetMetadata(woflo.petsplus.component.PetsplusComponents.PetMetadata metadata) {
        if (metadata == null) return;
        
        // Update display name if provided
        if (metadata.customDisplayName().isPresent()) {
            String customName = metadata.customDisplayName().get();
            pet.setCustomName(net.minecraft.text.Text.literal(customName));
            pet.setCustomNameVisible(true);
        }
        
        // Add bond strength
        addBondStrength(metadata.bondStrength());
        
        // Store custom tags in state data
        for (Map.Entry<String, String> tag : metadata.customTags().entrySet()) {
            setStateData("tag_" + tag.getKey(), tag.getValue());
        }
        
        // Mark as special if indicated
        if (metadata.isSpecial()) {
            setStateData("isSpecial", true);
        }
    }
    
    /**
     * Add bond strength to the pet, which can provide stat bonuses.
     * Bond strength is stored as state data and influences combat effectiveness.
     */
    public void addBondStrength(long strengthToAdd) {
        if (strengthToAdd <= 0) return;
        
        long currentBond = getStateData("bondStrength", Long.class, 0L);
        long newBond = Math.min(10000L, currentBond + strengthToAdd); // Cap at 10,000
        setStateData("bondStrength", newBond);
        
        // Apply attribute bonuses based on bond strength
        // Every 1000 bond strength = +5% health and damage
        if (newBond >= 1000 && currentBond < 1000) {
            woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
        }
    }
    
    /**
     * Get current bond strength level.
     */
    public long getBondStrength() {
        return getStateData("bondStrength", Long.class, 0L);
    }
    
    /**
     * Reset all pet abilities and return to base state.
     * Used by respec tokens to allow re-allocation of progression.
     */
    public void resetAbilities() {
        // Clear all cooldowns
        cooldowns.clear();
        
        // Reset level and experience but keep at least level 1
        this.level = 1;
        this.experience = 0;
        
        // Clear unlocked milestones
        unlockedMilestones.clear();
        
        // Reset state data related to abilities but preserve bond strength and metadata
        Map<String, Object> preservedData = new HashMap<>();
        preservedData.put("bondStrength", getStateData("bondStrength", Long.class, 0L));
        preservedData.put("isSpecial", getStateData("isSpecial", Boolean.class, false));
        
        // Preserve custom tags
        for (String key : stateData.keySet()) {
            if (key.startsWith("tag_")) {
                preservedData.put(key, stateData.get(key));
            }
        }
        
        // Clear all state data and restore preserved items
        stateData.clear();
        stateData.putAll(preservedData);
        
        // Reset attributes to base values
        woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
    }
    
    public void writeToNbt(NbtCompound nbt) {
        nbt.putString("role", getRoleId().toString());
        nbt.putString("petUuid", pet.getUuidAsString());
        nbt.putLong("lastAttackTick", lastAttackTick);
        nbt.putBoolean("isPerched", isPerched);
        nbt.putInt("level", level);
        nbt.putInt("experience", experience);

        // Save unlocked milestones
        NbtCompound milestonesNbt = new NbtCompound();
        for (Map.Entry<Integer, Boolean> entry : unlockedMilestones.entrySet()) {
            milestonesNbt.putBoolean(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("milestones", milestonesNbt);
        
        // Save cooldowns
        NbtCompound cooldownsNbt = new NbtCompound();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            cooldownsNbt.putLong(entry.getKey(), entry.getValue());
        }
        nbt.put("cooldowns", cooldownsNbt);
        
        // Save state data
        if (!stateData.isEmpty()) {
            NbtCompound stateNbt = new NbtCompound();
            for (Map.Entry<String, Object> entry : stateData.entrySet()) {
                Object value = entry.getValue();
                String key = entry.getKey();
                
                // Save based on value type
                if (value instanceof String) {
                    stateNbt.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    stateNbt.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    stateNbt.putLong(key, (Long) value);
                } else if (value instanceof Boolean) {
                    stateNbt.putBoolean(key, (Boolean) value);
                } else if (value instanceof Float) {
                    stateNbt.putFloat(key, (Float) value);
                } else if (value instanceof Double) {
                    stateNbt.putDouble(key, (Double) value);
                } else if (value instanceof java.util.List) {
                    // Handle lists by converting to string representation
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) value;
                    NbtCompound listNbt = new NbtCompound();
                    for (int i = 0; i < list.size(); i++) {
                        listNbt.putString(String.valueOf(i), list.get(i).toString());
                    }
                    listNbt.putInt("size", list.size());
                    stateNbt.put(key, listNbt);
                }
                // Add more type handling as needed
            }
            nbt.put("stateData", stateNbt);
        }
        
        // Save characteristics
        if (characteristics != null) {
            NbtCompound characteristicsNbt = new NbtCompound();
            characteristics.writeToNbt(characteristicsNbt);
            nbt.put("characteristics", characteristicsNbt);
        }
    }
    
    public void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("role")) {
            nbt.getString("role").ifPresent(roleKey -> {
                Identifier parsed = Identifier.tryParse(roleKey);
                if (parsed == null && !roleKey.contains(":")) {
                    parsed = PetRoleType.normalizeId(roleKey);
                }

                if (parsed != null) {
                    setRoleId(parsed);
                }
            });
        }
        
        if (nbt.contains("lastAttackTick")) {
            nbt.getLong("lastAttackTick").ifPresent(tick -> this.lastAttackTick = tick);
        }
        if (nbt.contains("isPerched")) {
            nbt.getBoolean("isPerched").ifPresent(perched -> this.isPerched = perched);
        }
        if (nbt.contains("level")) {
            nbt.getInt("level").ifPresent(level -> this.level = Math.max(1, level));
        }
        if (nbt.contains("experience")) {
            nbt.getInt("experience").ifPresent(xp -> this.experience = Math.max(0, xp));
        }

        if (nbt.contains("milestones")) {
            nbt.getCompound("milestones").ifPresent(milestonesNbt -> {
                unlockedMilestones.clear();
                for (String key : milestonesNbt.getKeys()) {
                    try {
                        int level = Integer.parseInt(key);
                        milestonesNbt.getBoolean(key).ifPresent(unlocked ->
                            unlockedMilestones.put(level, unlocked));
                    } catch (NumberFormatException ignored) {
                        // Skip invalid milestone keys
                    }
                }
            });
        }

        if (nbt.contains("cooldowns")) {
            nbt.getCompound("cooldowns").ifPresent(cooldownsNbt -> {
                cooldowns.clear();
                for (String key : cooldownsNbt.getKeys()) {
                    cooldownsNbt.getLong(key).ifPresent(value -> cooldowns.put(key, value));
                }
            });
        }
        
        // Load stateData lists
        if (nbt.contains("stateData")) {
            nbt.getCompound("stateData").ifPresent(stateDataNbt -> {
                stateData.clear();
                for (String key : stateDataNbt.getKeys()) {
                    var listOpt = stateDataNbt.getCompound(key);
                    if (listOpt.isPresent()) {
                        var listNbt = listOpt.get();
                        listNbt.getInt("size").ifPresent(size -> {
                            java.util.List<String> list = new java.util.ArrayList<>();
                            for (int i = 0; i < size; i++) {
                                listNbt.getString(String.valueOf(i)).ifPresent(list::add);
                            }
                            stateData.put(key, list);
                        });
                        continue;
                    }

                    stateDataNbt.getString(key).ifPresent(value -> stateData.put(key, value));
                    stateDataNbt.getInt(key).ifPresent(value -> stateData.put(key, value));
                    stateDataNbt.getLong(key).ifPresent(value -> stateData.put(key, value));
                    stateDataNbt.getDouble(key).ifPresent(value -> stateData.put(key, value));
                    stateDataNbt.getFloat(key).ifPresent(value -> stateData.put(key, value));
                    stateDataNbt.getBoolean(key).ifPresent(value -> stateData.put(key, value));
                }
            });
        }
        
        // Load characteristics
        if (nbt.contains("characteristics")) {
            nbt.getCompound("characteristics").ifPresent(characteristicsNbt -> {
                this.characteristics = PetCharacteristics.readFromNbt(characteristicsNbt);
            });
        }
    }
    
    /**
     * Save component data to entity using component system.
     */
    public void saveToEntity() {
        PetsplusComponents.PetData data = PetsplusComponents.PetData.empty()
            .withRole(getRoleId());

        data = data.withLastAttackTick(lastAttackTick)
                  .withPerched(isPerched);
            
        // Add cooldowns
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            data = data.withCooldown(entry.getKey(), entry.getValue());
        }
        
        // Store in entity component (this would require entity component implementation)
        // For now, we'll use NBT persistence as that's more straightforward
    }
    
    /**
     * Load component data from entity using component system.
     */
    public void loadFromEntity() {
        // Load from entity component (this would require entity component implementation)
        // For now, we'll use NBT persistence as that's more straightforward
    }
}