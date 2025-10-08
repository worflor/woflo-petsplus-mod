package woflo.petsplus.roles.guardian;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.tracking.PlayerTickDispatcher;
import woflo.petsplus.state.tracking.PlayerTickListener;
import woflo.petsplus.ui.UIFeedbackManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the temporary fortress bond barrier shared between a Guardian and their owner.
 * The bond applies a flat damage reduction to both owner and pet while active and
 * automatically expires when the duo separates or the guardian falls.
 */
public final class GuardianFortressBondManager {
    private static final double MAX_BOND_RANGE = 12.0;
    private static final double MAX_BOND_RANGE_SQUARED = MAX_BOND_RANGE * MAX_BOND_RANGE;
    private static final long CHECK_INTERVAL_TICKS = 5L;

    private static final Map<UUID, BondState> OWNER_BONDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BondState> PET_BONDS = new ConcurrentHashMap<>();
    private static final GuardianBondTicker BOND_TICKER = new GuardianBondTicker();

    private GuardianFortressBondManager() {}

    /**
     * Activate or refresh the fortress bond for the given owner/pet pair.
     *
     * @param pet           The guardian pet establishing the bond.
     * @param owner         The owner protected by the bond.
     * @param reductionPct  Fractional damage reduction to apply (0-1).
     * @param durationTicks Duration in ticks before the bond expires.
     * @return True if the bond was activated.
     */
    public static boolean activateBond(MobEntity pet, ServerPlayerEntity owner, double reductionPct, int durationTicks) {
        if (pet == null || owner == null) {
            return false;
        }
        if (reductionPct <= 0.0) {
            return false;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld ownerWorld)) {
            return false;
        }
        if (!(pet.getEntityWorld() instanceof ServerWorld petWorld)) {
            return false;
        }
        if (ownerWorld != petWorld) {
            return false;
        }

        double clampedReduction = Math.min(1.0, Math.max(0.0, reductionPct));
        int safeDuration = Math.max(1, durationTicks);
        long expiryTick = ownerWorld.getTime() + safeDuration;
        BondState state = new BondState(owner.getUuid(), pet.getUuid(), ownerWorld.getRegistryKey(), clampedReduction, expiryTick);
        OWNER_BONDS.put(owner.getUuid(), state);
        PET_BONDS.put(pet.getUuid(), state);
        BOND_TICKER.schedule(owner, ownerWorld.getServer().getTicks());
        return true;
    }

    /**
     * Apply fortress bond reduction to the owner after redirection has finished.
     */
    public static float modifyOwnerDamage(ServerPlayerEntity owner, float amount) {
        if (amount <= 0.0f) {
            return amount;
        }
        BondState state = resolveOwnerBond(owner);
        if (state == null) {
            return amount;
        }

        return applyReduction(amount, state.reductionPct());
    }

    /**
     * Computes the redirected damage amount after fortress bond reduction but
     * without mutating any shared state.
     */
    public static float computeRedirectedDamage(ServerPlayerEntity owner, MobEntity pet, float amount) {
        if (amount <= 0.0f || pet == null || owner == null) {
            return amount;
        }
        BondState state = resolveBond(owner, pet);
        if (state == null) {
            return amount;
        }

        return applyReduction(amount, state.reductionPct());
    }

    /**
     * Applies fortress bond reduction to incoming pet damage using the shared
     * interception result.
     */
    public static boolean applyPetDamageReduction(ServerPlayerEntity owner,
                                                  MobEntity pet,
                                                  DamageInterceptionResult result) {
        if (pet == null || result == null) {
            return false;
        }
        if (!(pet.getEntityWorld() instanceof ServerWorld petWorld)) {
            return false;
        }

        BondState state = owner != null ? resolveBond(owner, pet) : null;
        if (state == null) {
            state = resolvePetBond(pet, petWorld);
            if (state == null) {
                return false;
            }
        }

        double remaining = result.getRemainingDamageAmount();
        float reducedAmount = applyReduction((float) remaining, state.reductionPct());
        if (reducedAmount >= remaining) {
            return false;
        }

        result.setRemainingDamageAmount(reducedAmount);
        return true;
    }

    private static float applyReduction(float amount, double reductionPct) {
        double multiplier = 1.0 - Math.min(1.0, Math.max(0.0, reductionPct));
        return (float) Math.max(0.0, amount * multiplier);
    }

    @Nullable
    private static BondState resolveOwnerBond(ServerPlayerEntity owner) {
        BondState state = OWNER_BONDS.get(owner.getUuid());
        if (state == null) {
            return null;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld ownerWorld)) {
            endBond(owner, null, state, BondEndReason.CLEARED);
            return null;
        }
        return validateBond(owner, state, ownerWorld, true);
    }

    @Nullable
    private static BondState resolvePetBond(MobEntity pet, ServerWorld world) {
        BondState state = PET_BONDS.get(pet.getUuid());
        if (state == null) {
            return null;
        }
        PetComponent component = PetComponent.get(pet);
        ServerPlayerEntity owner = component != null && component.getOwner() instanceof ServerPlayerEntity serverOwner
            ? serverOwner
            : null;
        if (owner == null) {
            endBond(null, pet, state, BondEndReason.CLEARED);
            return null;
        }
        return validateBond(owner, state, world, true);
    }

    @Nullable
    private static BondState resolveBond(ServerPlayerEntity owner, MobEntity pet) {
        if (!(owner.getEntityWorld() instanceof ServerWorld ownerWorld) || !(pet.getEntityWorld() instanceof ServerWorld petWorld)) {
            return null;
        }
        BondState state = OWNER_BONDS.get(owner.getUuid());
        if (state == null || !state.petId().equals(pet.getUuid())) {
            return null;
        }
        if (!state.matches(ownerWorld) || !state.matches(petWorld)) {
            endBond(owner, pet, state, BondEndReason.CLEARED);
            return null;
        }
        if (state.isExpired(ownerWorld.getTime())) {
            endBond(owner, pet, state, BondEndReason.EXPIRED);
            return null;
        }
        if (!pet.isAlive()) {
            endBond(owner, pet, state, BondEndReason.PET_LOST);
            return null;
        }
        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.isOwnedBy(owner)) {
            endBond(owner, pet, state, BondEndReason.CLEARED);
            return null;
        }
        return validateDistance(owner, pet, state, true);
    }

    @Nullable
    private static BondState validateBond(ServerPlayerEntity owner, BondState state, ServerWorld world, boolean notify) {
        if (!state.matches(world)) {
            endBond(owner, null, state, notify ? BondEndReason.DIMENSION : BondEndReason.CLEARED);
            return null;
        }
        if (state.isExpired(world.getTime())) {
            endBond(owner, null, state, notify ? BondEndReason.EXPIRED : BondEndReason.CLEARED);
            return null;
        }

        MobEntity pet = state.resolvePet(world);
        if (pet == null || !pet.isAlive()) {
            endBond(owner, pet, state, notify ? BondEndReason.PET_LOST : BondEndReason.CLEARED);
            return null;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.isOwnedBy(owner)) {
            endBond(owner, pet, state, BondEndReason.CLEARED);
            return null;
        }

        return validateDistance(owner, pet, state, notify);
    }

    @Nullable
    private static BondState validateDistance(ServerPlayerEntity owner, MobEntity pet, BondState state, boolean notify) {
        if (owner.squaredDistanceTo(pet) > MAX_BOND_RANGE_SQUARED) {
            endBond(owner, pet, state, notify ? BondEndReason.OUT_OF_RANGE : BondEndReason.CLEARED);
            return null;
        }
        return state;
    }

    private static void endBond(@Nullable ServerPlayerEntity owner, @Nullable MobEntity pet, BondState state,
                                 BondEndReason reason) {
        OWNER_BONDS.remove(state.ownerId());
        PET_BONDS.remove(state.petId());

        if (pet == null && owner != null && owner.getEntityWorld() instanceof ServerWorld serverWorld && state.matches(serverWorld)) {
            pet = state.resolvePet(serverWorld);
        }

        if (pet != null) {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                component.clearStateData("guardian_fortress_bond_expiry");
            }
        }

        if (owner != null) {
            switch (reason) {
                case EXPIRED -> UIFeedbackManager.sendGuardianFortressFadeMessage(owner);
                case OUT_OF_RANGE -> UIFeedbackManager.sendGuardianFortressOutOfRangeMessage(owner, pet);
                case PET_LOST -> UIFeedbackManager.sendGuardianFortressPetDownMessage(owner, pet);
                case DIMENSION -> UIFeedbackManager.sendGuardianFortressDimensionMessage(owner);
                default -> {
                }
            }
        }
    }

    public static void clearBond(ServerPlayerEntity owner) {
        BondState state = OWNER_BONDS.remove(owner.getUuid());
        if (state != null) {
            PET_BONDS.remove(state.petId());
            if (owner.getEntityWorld() instanceof ServerWorld ownerWorld && state.matches(ownerWorld)) {
                MobEntity pet = ownerWorld.getEntity(state.petId()) instanceof MobEntity mob ? mob : null;
                if (pet != null) {
                    PetComponent component = PetComponent.get(pet);
                    if (component != null) {
                        component.clearStateData("guardian_fortress_bond_expiry");
                    }
                }
            }
        }
    }

    public static PlayerTickListener ticker() {
        return BOND_TICKER;
    }

    static boolean tickOwner(ServerPlayerEntity owner, long currentTick) {
        BondState state = OWNER_BONDS.get(owner.getUuid());
        if (state == null) {
            return false;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld ownerWorld)) {
            endBond(owner, null, state, BondEndReason.CLEARED);
            return false;
        }

        BondState validated = validateBond(owner, state, ownerWorld, true);
        if (validated == null) {
            return false;
        }

        MobEntity pet = validated.resolvePet(ownerWorld);
        if (pet != null) {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                component.setStateData("guardian_fortress_bond_expiry", validated.expiryTick());
            }
        }

        return ownerWorld.getTime() < validated.expiryTick();
    }

    private enum BondEndReason {
        EXPIRED,
        OUT_OF_RANGE,
        PET_LOST,
        DIMENSION,
        CLEARED
    }

    private record BondState(UUID ownerId, UUID petId, RegistryKey<World> worldKey, double reductionPct, long expiryTick) {
        boolean matches(ServerWorld world) {
            return world.getRegistryKey().equals(worldKey);
        }

        boolean isExpired(long worldTime) {
            return worldTime >= expiryTick;
        }

        @Nullable
        MobEntity resolvePet(ServerWorld world) {
            if (!matches(world)) {
                return null;
            }
            return world.getEntity(this.petId()) instanceof MobEntity mob ? mob : null;
        }
    }

    private static final class GuardianBondTicker implements PlayerTickListener {
        private final Map<UUID, Long> nextRunTicks = new ConcurrentHashMap<>();

        @Override
        public long nextRunTick(ServerPlayerEntity player) {
            if (player == null) {
                return Long.MAX_VALUE;
            }
            return nextRunTicks.getOrDefault(player.getUuid(), Long.MAX_VALUE);
        }

        @Override
        public void run(ServerPlayerEntity player, long currentTick) {
            if (player == null) {
                return;
            }

            if (GuardianFortressBondManager.tickOwner(player, currentTick)) {
                nextRunTicks.put(player.getUuid(), currentTick + CHECK_INTERVAL_TICKS);
            } else {
                nextRunTicks.remove(player.getUuid());
            }
        }

        @Override
        public void onPlayerRemoved(ServerPlayerEntity player) {
            if (player == null) {
                return;
            }
            nextRunTicks.remove(player.getUuid());
            GuardianFortressBondManager.clearBond(player);
        }

        void schedule(ServerPlayerEntity owner, long tick) {
            if (owner == null) {
                return;
            }
            nextRunTicks.put(owner.getUuid(), Math.max(0L, tick));
            PlayerTickDispatcher.requestImmediateRun(owner, this);
        }
    }
}

