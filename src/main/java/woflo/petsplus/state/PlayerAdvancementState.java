package woflo.petsplus.state;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtString;


import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Manages advancement-related state for players.
 */
public class PlayerAdvancementState {
    private static final Map<PlayerEntity, PlayerAdvancementState> STATES = new WeakHashMap<>();

    private UUID bestFriendForevererPetUuid = null;
    private long bestFriendForevererAwardedTs = 0;
    private boolean orNotAwarded = false;
    private int dreamEscapeCount = 0;
    private int uniqueAlliesHealed = 0;
    private long uniqueAlliesHealedDay = 0;
    private float guardianDamageRedirected = 0;
    private final Set<UUID> healedAlliesToday = new HashSet<>();

    public PlayerAdvancementState(PlayerEntity player) {
        // Player reference not needed - using WeakHashMap key for identity
    }

    public static PlayerAdvancementState getOrCreate(PlayerEntity player) {
        return STATES.computeIfAbsent(player, PlayerAdvancementState::new);
    }

    public static PlayerAdvancementState get(PlayerEntity player) {
        return STATES.get(player);
    }

    public static void remove(PlayerEntity player) {
        STATES.remove(player);
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
            nbt.getLong("best_friend_foreverer_awarded_ts").ifPresent(value -> this.bestFriendForevererAwardedTs = value);
        }
        if (nbt.contains("or_not_awarded")) {
            nbt.getBoolean("or_not_awarded").ifPresent(value -> this.orNotAwarded = value);
        }
        if (nbt.contains("dream_escape_count")) {
            nbt.getInt("dream_escape_count").ifPresent(value -> this.dreamEscapeCount = value);
        }
        if (nbt.contains("unique_allies_healed")) {
            nbt.getInt("unique_allies_healed").ifPresent(value -> this.uniqueAlliesHealed = value);
        }
        if (nbt.contains("unique_allies_healed_day")) {
            nbt.getLong("unique_allies_healed_day").ifPresent(value -> this.uniqueAlliesHealedDay = value);
        }

        this.healedAlliesToday.clear();
        if (nbt.contains("healed_allies_today")) {
            nbt.getList("healed_allies_today").ifPresent(list -> {
                for (int i = 0; i < list.size(); i++) {
                    NbtElement element = list.get(i);
                    if (element instanceof NbtString) {
                        element.asString().ifPresent(uuidStr -> {
                            try {
                                this.healedAlliesToday.add(UUID.fromString(uuidStr));
                            } catch (IllegalArgumentException ignored) {
                            }
                        });
                    }
                }
                this.uniqueAlliesHealed = Math.max(this.uniqueAlliesHealed, this.healedAlliesToday.size());
            });
        }

        if (nbt.contains("guardian_damage_redirected")) {
            nbt.getFloat("guardian_damage_redirected").ifPresent(value -> this.guardianDamageRedirected = value);
        }
    }
}