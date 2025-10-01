package woflo.petsplus.state;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Manages advancement-related state for players.
 */
public class PlayerAdvancementState {
    private static final Map<PlayerEntity, PlayerAdvancementState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private UUID bestFriendForevererPetUuid = null;
    private long bestFriendForevererAwardedTs = 0;
    private boolean orNotAwarded = false;
    private int dreamEscapeCount = 0;
    private int petSacrificeCount = 0;
    private int uniqueAlliesHealed = 0;
    private long uniqueAlliesHealedDay = 0;
    private float guardianDamageRedirected = 0;
    private final Set<UUID> healedAlliesToday = new HashSet<>();

    public PlayerAdvancementState(PlayerEntity player) {
        // Player reference not needed - using WeakHashMap key for identity
    }

    public static PlayerAdvancementState getOrCreate(PlayerEntity player) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(player, PlayerAdvancementState::new);
        }
    }

    public static PlayerAdvancementState get(PlayerEntity player) {
        synchronized (STATES) {
            return STATES.get(player);
        }
    }

    public static void remove(PlayerEntity player) {
        synchronized (STATES) {
            STATES.remove(player);
        }
    }

    public UUID getBestFriendForevererPetUuid() {
        return bestFriendForevererPetUuid;
    }

    public void setBestFriendForevererPetUuid(UUID uuid) {
        if (this.bestFriendForevererPetUuid == null) {
            this.bestFriendForevererPetUuid = uuid;
            this.bestFriendForevererAwardedTs = System.currentTimeMillis();
        }
    }

    public long getBestFriendForevererAwardedTs() {
        return bestFriendForevererAwardedTs;
    }

    public boolean isOrNotAwarded() {
        return orNotAwarded;
    }

    public void setOrNotAwarded(boolean awarded) {
        this.orNotAwarded = awarded;
    }

    public int getDreamEscapeCount() {
        return dreamEscapeCount;
    }

    public void incrementDreamEscapeCount() {
        this.dreamEscapeCount++;
    }

    public int getPetSacrificeCount() {
        return petSacrificeCount;
    }

    public void incrementPetSacrificeCount() {
        this.petSacrificeCount++;
    }

    public int getUniqueAlliesHealed() {
        return uniqueAlliesHealed;
    }

    public void setUniqueAlliesHealed(int count) {
        this.uniqueAlliesHealed = Math.max(0, count);
        if (this.uniqueAlliesHealed == 0) {
            this.healedAlliesToday.clear();
        }
    }

    public long getUniqueAlliesHealedDay() {
        return uniqueAlliesHealedDay;
    }

    public void setUniqueAlliesHealedDay(long day) {
        if (this.uniqueAlliesHealedDay != day) {
            this.uniqueAlliesHealedDay = day;
            this.healedAlliesToday.clear();
            this.uniqueAlliesHealed = 0;
        }
    }

    public float getGuardianDamageRedirected() {
        return guardianDamageRedirected;
    }

    public void addGuardianDamageRedirected(float damage) {
        this.guardianDamageRedirected += damage;
    }

    public void resetGuardianDamageRedirected() {
        this.guardianDamageRedirected = 0;
    }


    /**
     * Track an ally healed during the current Minecraft day.
     * @return true if this ally counted as unique for the day
     */
    public boolean trackAllyHealed(UUID allyUuid, long currentDay) {
        if (allyUuid == null) {
            return false;
        }

        if (this.uniqueAlliesHealedDay != currentDay) {
            this.uniqueAlliesHealedDay = currentDay;
            this.uniqueAlliesHealed = 0;
            this.healedAlliesToday.clear();
        }

        if (this.healedAlliesToday.add(allyUuid)) {
            this.uniqueAlliesHealed++;
            return true;
        }

        return false;
    }


    /**
     * Serialize state to NBT for persistence.
     */
    public void writeToNbt(NbtCompound nbt) {
        if (bestFriendForevererPetUuid != null) {
            nbt.putString("best_friend_foreverer_pet_uuid", bestFriendForevererPetUuid.toString());
        }
        nbt.putLong("best_friend_foreverer_awarded_ts", bestFriendForevererAwardedTs);
        nbt.putBoolean("or_not_awarded", orNotAwarded);
        nbt.putInt("dream_escape_count", dreamEscapeCount);
        nbt.putInt("pet_sacrifice_count", petSacrificeCount);
        nbt.putInt("unique_allies_healed", uniqueAlliesHealed);
        nbt.putLong("unique_allies_healed_day", uniqueAlliesHealedDay);

        NbtList healedList = new NbtList();
        for (UUID allyUuid : healedAlliesToday) {
            healedList.add(NbtString.of(allyUuid.toString()));
        }
        nbt.put("healed_allies_today", healedList);

        nbt.putFloat("guardian_damage_redirected", guardianDamageRedirected);
    }

    /**
     * Deserialize state from NBT.
     */
    public void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("best_friend_foreverer_pet_uuid")) {
            nbt.getString("best_friend_foreverer_pet_uuid").ifPresent(uuidStr -> {
                try {
                    this.bestFriendForevererPetUuid = UUID.fromString(uuidStr);
                } catch (Exception e) {
                    // Invalid UUID, ignore
                }
            });
        }
        if (nbt.contains("best_friend_foreverer_awarded_ts")) {
            nbt.getLong("best_friend_foreverer_awarded_ts").ifPresent(value -> {
                // Validate timestamp - should not be in the future and should be reasonable
                long currentTime = System.currentTimeMillis();
                if (value > 0 && value <= currentTime) {
                    this.bestFriendForevererAwardedTs = value;
                } else {
                    this.bestFriendForevererAwardedTs = 0;
                }
            });
        }
        if (nbt.contains("or_not_awarded")) {
            nbt.getBoolean("or_not_awarded").ifPresent(value -> this.orNotAwarded = value);
        }
        if (nbt.contains("dream_escape_count")) {
            nbt.getInt("dream_escape_count").ifPresent(value -> {
                // Validate count - should be non-negative and reasonable
                this.dreamEscapeCount = Math.max(0, Math.min(value, 10000));
            });
        }
        if (nbt.contains("pet_sacrifice_count")) {
            nbt.getInt("pet_sacrifice_count").ifPresent(value -> {
                // Validate count - should be non-negative and reasonable
                this.petSacrificeCount = Math.max(0, Math.min(value, 10000));
            });
        }
        if (nbt.contains("unique_allies_healed")) {
            nbt.getInt("unique_allies_healed").ifPresent(value -> {
                // Validate count - should be non-negative and reasonable
                this.uniqueAlliesHealed = Math.max(0, Math.min(value, 1000));
            });
        }
        if (nbt.contains("unique_allies_healed_day")) {
            nbt.getLong("unique_allies_healed_day").ifPresent(value -> {
                // Validate day - should be positive and reasonable (Minecraft days since epoch)
                long currentDay = System.currentTimeMillis() / 24000; // Minecraft day length
                if (value > 0 && value <= currentDay + 1) { // Allow current day + 1 for time zone differences
                    this.uniqueAlliesHealedDay = value;
                } else {
                    this.uniqueAlliesHealedDay = 0;
                }
            });
        }

        this.healedAlliesToday.clear();
        if (nbt.contains("healed_allies_today")) {
            nbt.getList("healed_allies_today").ifPresent(list -> {
                // Validate list size - should be reasonable
                int maxAllies = Math.min(list.size(), 1000);
                for (int i = 0; i < maxAllies; i++) {
                    NbtElement element = list.get(i);
                    if (element instanceof NbtString) {
                        element.asString().ifPresent(uuidStr -> {
                            try {
                                UUID uuid = UUID.fromString(uuidStr);
                                this.healedAlliesToday.add(uuid);
                            } catch (IllegalArgumentException ignored) {
                                // Invalid UUID, ignore
                            }
                        });
                    }
                }
                // Fix data consistency: ensure uniqueAlliesHealed matches healedAlliesToday.size()
                this.uniqueAlliesHealed = this.healedAlliesToday.size();
            });
        }

        if (nbt.contains("guardian_damage_redirected")) {
            nbt.getFloat("guardian_damage_redirected").ifPresent(value -> {
                // Validate damage - should be non-negative and reasonable
                if (value >= 0 && value <= Float.MAX_VALUE) {
                    this.guardianDamageRedirected = value;
                } else {
                    this.guardianDamageRedirected = 0;
                }
            });
        }
    }
}