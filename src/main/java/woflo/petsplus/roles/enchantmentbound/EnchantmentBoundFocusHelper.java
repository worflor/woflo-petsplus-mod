package woflo.petsplus.roles.enchantmentbound;

import java.util.Locale;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Shared helpers for managing Enchantment-Bound Arcane Focus charges across
 * the mining, combat, and swim buckets. This mirrors the old handler logic but
 * exposes a reusable entry point for ability effects.
 */
public final class EnchantmentBoundFocusHelper {

    public enum Bucket {
        MINING,
        COMBAT,
        SWIM;

        private final String keySuffix;

        Bucket() {
            this.keySuffix = name().toLowerCase(Locale.ROOT);
        }

        String keySuffix() {
            return keySuffix;
        }
    }

    private static final String KEY_LAST_USE = "eb_focus_last";
    private static final String KEY_USED = "eb_focus_used";
    private static final String KEY_PREFIX = "eb_focus_";

    private EnchantmentBoundFocusHelper() {
    }

    public static boolean isFocusActive(ServerPlayerEntity owner, Bucket bucket) {
        if (owner == null || bucket == null) {
            return false;
        }
        long now = owner.getEntityWorld().getTime();
        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        long until = combatState.getTempState(KEY_PREFIX + bucket.keySuffix());
        return until > now;
    }

    public static boolean tryActivate(ServerPlayerEntity owner,
                                      Bucket bucket,
                                      int petLevel,
                                      int durationTicks,
                                      int cooldownTicks,
                                      int chargesAtThirty,
                                      boolean playSound) {
        if (owner == null || bucket == null) {
            return false;
        }
        if (petLevel < 20) {
            return false;
        }
        if (durationTicks <= 0) {
            return false;
        }
        int cooldown = Math.max(1, cooldownTicks);
        int maxCharges = Math.max(1, petLevel >= 30 ? chargesAtThirty : 1);

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        long now = owner.getEntityWorld().getTime();

        long activeUntil = combatState.getTempState(KEY_PREFIX + bucket.keySuffix());
        if (activeUntil > now) {
            return false;
        }

        long last = combatState.getTempState(KEY_LAST_USE);
        int used = (int) combatState.getTempState(KEY_USED);
        if (last != 0L) {
            long elapsed = now - last;
            if (elapsed >= cooldown) {
                used = 0;
            } else if (used >= maxCharges) {
                return false;
            }
        } else if (used >= maxCharges) {
            used = maxCharges;
        }

        if (used >= maxCharges) {
            return false;
        }

        combatState.setTempState(KEY_USED, used + 1L);
        combatState.setTempState(KEY_LAST_USE, now);
        combatState.setTempState(KEY_PREFIX + bucket.keySuffix(), now + durationTicks);

        if (playSound) {
            owner.getEntityWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 1.3f);
        }
        return true;
    }
}

