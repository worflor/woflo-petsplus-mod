package woflo.petsplus.roles.guardian;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the Guardian "Aegis Protocol" redirect stacking system.
 */
public final class GuardianAegisProtocolManager {
    private static final Map<UUID, StackState> OWNER_STACKS = new ConcurrentHashMap<>();

    private GuardianAegisProtocolManager() {}

    /**
     * Increment the active stack count for the owner/guardian pair.
     *
     * @param guardian       Guardian pet that triggered the stack.
     * @param owner          The owner receiving protection.
     * @param maxStacks      Maximum stack count allowed.
     * @param durationTicks  Duration before stacks expire.
     * @return The new stack count, or 0 if the stack could not be applied.
     */
    public static int incrementStacks(MobEntity guardian, ServerPlayerEntity owner, int maxStacks, int durationTicks) {
        if (guardian == null || owner == null) {
            return 0;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld ownerWorld)) {
            return 0;
        }
        if (!(guardian.getEntityWorld() instanceof ServerWorld guardianWorld)) {
            return 0;
        }
        if (ownerWorld != guardianWorld) {
            return 0;
        }

        UUID ownerId = owner.getUuid();
        int safeDuration = Math.max(1, durationTicks);
        long worldTime = ownerWorld.getTime();
        StackState previous = OWNER_STACKS.get(ownerId);
        int stacks = 1;
        if (previous != null && previous.matches(ownerWorld) && !previous.isExpired(worldTime)) {
            stacks = Math.min(maxStacks, previous.stacks() + 1);
        }

        long expiryTick = worldTime + safeDuration;
        StackState updated = new StackState(ownerId, guardian.getUuid(), ownerWorld.getRegistryKey(), stacks, expiryTick);
        OWNER_STACKS.put(ownerId, updated);

        applyEffects(owner, guardian, stacks, safeDuration);
        storeComponentState(guardian, stacks, expiryTick);
        return stacks;
    }

    /**
     * Get the current stack count for an owner if still valid.
     */
    public static int getActiveStacks(ServerPlayerEntity owner) {
        StackState state = resolveStacks(owner);
        return state != null ? state.stacks() : 0;
    }

    /**
     * Clear stacks when the owner disconnects or the guardian is removed.
     */
    public static void clearStacks(ServerPlayerEntity owner) {
        if (owner == null) {
            return;
        }
        StackState state = OWNER_STACKS.remove(owner.getUuid());
        if (state != null && owner.getEntityWorld() instanceof ServerWorld ownerWorld && state.matches(ownerWorld)) {
            MobEntity guardian = ownerWorld.getEntity(state.guardianId()) instanceof MobEntity mob ? mob : null;
            if (guardian != null) {
                PetComponent component = PetComponent.get(guardian);
                if (component != null) {
                    component.clearStateData("guardian_aegis_stacks");
                    component.clearStateData("guardian_aegis_expiry");
                }
            }
        }
    }

    private static void applyEffects(ServerPlayerEntity owner, MobEntity guardian, int stacks, int durationTicks) {
        int safeDuration = Math.max(1, durationTicks);
        int ownerResistance = Math.max(0, stacks - 1);
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, safeDuration, ownerResistance, false, true, true));

        if (stacks >= 2) {
            int absorptionLevel = stacks >= 3 ? 1 : 0;
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, safeDuration, absorptionLevel, false, true, true));
        }

        if (guardian != null && guardian.isAlive()) {
            int guardianResistance = Math.max(0, stacks - 2);
            guardian.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, safeDuration, guardianResistance, false, true, true));
        }

        LivingEntity mount = owner.getVehicle() instanceof LivingEntity living ? living : null;
        if (mount != null) {
            int mountResistance = Math.max(0, stacks - 2);
            int mountDuration = Math.max(60, safeDuration / 2);
            mount.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, mountDuration, mountResistance, false, true, true));
        }
    }

    private static void storeComponentState(MobEntity guardian, int stacks, long expiryTick) {
        PetComponent component = PetComponent.get(guardian);
        if (component == null) {
            return;
        }
        component.setStateData("guardian_aegis_stacks", stacks);
        component.setStateData("guardian_aegis_expiry", expiryTick);
    }

    @Nullable
    private static StackState resolveStacks(ServerPlayerEntity owner) {
        StackState state = OWNER_STACKS.get(owner.getUuid());
        if (state == null) {
            return null;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld ownerWorld)) {
            OWNER_STACKS.remove(owner.getUuid());
            return null;
        }
        if (!state.matches(ownerWorld) || state.isExpired(ownerWorld.getTime())) {
            OWNER_STACKS.remove(owner.getUuid());
            return null;
        }
        return state;
    }

    private record StackState(UUID ownerId, UUID guardianId, RegistryKey<World> worldKey, int stacks, long expiryTick) {
        boolean matches(ServerWorld world) {
            return world.getRegistryKey().equals(worldKey);
        }

        boolean isExpired(long worldTime) {
            return worldTime >= expiryTick;
        }
    }
}

