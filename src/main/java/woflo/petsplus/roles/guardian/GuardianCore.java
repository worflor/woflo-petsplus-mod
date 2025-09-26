package woflo.petsplus.roles.guardian;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.FeedbackManager;
import woflo.petsplus.ui.UIFeedbackManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Core Guardian role systems for coordinating Bulwark redirection and blessing management.
 */
public final class GuardianCore {
    private static final double GUARDIAN_SEARCH_RANGE = 16.0;
    private static final int BULWARK_COOLDOWN_TICKS = 200;
    private static final int PRIMED_WINDOW_TICKS = 80;
    private static final int OWNER_STRENGTH_TICKS = 60;
    private static final int MOUNT_RESIST_TICKS = 60;
    private static final int WEAKNESS_TICKS = 50; // 2.5 seconds
    private static final String PRIMED_STATE_KEY = "guardian_bulwark_primed_until";
    private static final Comparator<GuardianCandidate> INTERCEPT_ORDER = Comparator
        .comparing(GuardianCandidate::spareHealth, Comparator.reverseOrder())
        .thenComparing(Comparator.comparingInt(GuardianCandidate::level).reversed())
        .thenComparing(GuardianCandidate::healthFraction, Comparator.reverseOrder());

    private static final Map<UUID, TimedEntry> guardianCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, GuardianPrimeState> primedOwners = new ConcurrentHashMap<>();

    private GuardianCore() {
    }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(GuardianCore::handleAfterDamage);
    }

    /**
     * Locate Guardian pets around the supplied owner that can be considered for Bulwark interception.
     */
    public static List<MobEntity> findNearbyGuardianPets(ServerPlayerEntity owner) {
        ServerWorld world = (ServerWorld) owner.getWorld();
        return world.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(GUARDIAN_SEARCH_RANGE),
            pet -> {
                PetComponent component = PetComponent.get(pet);
                if (component == null) {
                    return false;
                }
                if (!component.hasRole(PetRoleType.GUARDIAN)) {
                    return false;
                }
                if (!component.isOwnedBy(owner)) {
                    return false;
                }
                if (!pet.isAlive()) {
                    return false;
                }
                return true;
            }
        );
    }

    /**
     * Select the best Guardian candidate for intercepting incoming damage.
     */
    public static Optional<GuardianCandidate> selectGuardianForIntercept(ServerPlayerEntity owner) {
        List<MobEntity> guardians = findNearbyGuardianPets(owner);
        if (guardians.isEmpty()) {
            return Optional.empty();
        }

        return findBestGuardianForIntercept(guardians);
    }

    /**
     * Collect all Guardians that are ready to intercept the incoming damage, ranked by priority.
     */
    public static List<GuardianCandidate> collectGuardiansForIntercept(ServerPlayerEntity owner) {
        List<MobEntity> guardians = findNearbyGuardianPets(owner);
        if (guardians.isEmpty()) {
            return List.of();
        }

        return streamGuardianCandidates(guardians).toList();
    }

    /**
     * Evaluate a list of Guardian pets and rank them by readiness to intercept.
     */
    public static Optional<GuardianCandidate> findBestGuardianForIntercept(List<MobEntity> guardianPets) {
        return streamGuardianCandidates(guardianPets).findFirst();
    }

    private static Stream<GuardianCandidate> streamGuardianCandidates(List<MobEntity> guardianPets) {
        return guardianPets.stream()
            .map(GuardianCore::buildGuardianCandidate)
            .flatMap(optional -> optional.isPresent() ? Stream.of(optional.get()) : Stream.empty())
            .filter(candidate -> !candidate.onCooldown())
            .sorted(INTERCEPT_ORDER);
    }

    private static Optional<GuardianCandidate> buildGuardianCandidate(MobEntity pet) {
        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return Optional.empty();
        }

        float reserveFraction = computeBulwarkReserveFraction(component.getLevel());
        float reserveHealth = pet.getMaxHealth() * reserveFraction;
        float spareHealth = Math.max(0.0f, pet.getHealth() - reserveHealth);
        if (spareHealth <= 0.0f) {
            return Optional.empty();
        }

        float healthFraction = MathHelper.clamp(pet.getHealth() / pet.getMaxHealth(), 0.0f, 1.0f);
        boolean onCooldown = isGuardianOnCooldown(pet);

        return Optional.of(new GuardianCandidate(pet, component, reserveFraction, spareHealth, healthFraction, onCooldown));
    }

    /**
     * Compute the health fraction that must be preserved as a reserve for a Guardian pet.
     */
    public static float computeBulwarkReserveFraction(int level) {
        float fraction = 0.14f - Math.max(0, level - 1) * 0.01f;
        return MathHelper.clamp(fraction, 0.10f, 0.14f);
    }

    /**
     * Calculate the maximum redirect ratio for a Guardian before health scaling.
     */
    public static float computeRedirectRatio(int level) {
        float ratio = 0.28f + level * 0.018f;
        return Math.min(0.58f, ratio);
    }

    /**
     * Check if the Guardian has enough health remaining above the reserve to redirect safely.
     */
    public static boolean canGuardianSafelyRedirect(MobEntity guardian, float reserveFraction) {
        float reserveHealth = guardian.getMaxHealth() * reserveFraction;
        return guardian.getHealth() > reserveHealth;
    }

    /**
     * Determine if the Guardian is currently on Bulwark cooldown.
     */
    public static boolean isGuardianOnCooldown(MobEntity guardian) {
        TimedEntry entry = guardianCooldowns.get(guardian.getUuid());
        if (entry == null) {
            return false;
        }
        if (!(guardian.getWorld() instanceof ServerWorld world)) {
            return true;
        }
        if (!entry.matches(world)) {
            guardianCooldowns.remove(guardian.getUuid());
            return false;
        }
        if (entry.isExpired(world)) {
            guardianCooldowns.remove(guardian.getUuid());
            return false;
        }
        return true;
    }

    /**
     * Record a successful Bulwark redirect and prime the owner's blessing window.
     */
    public static void recordSuccessfulRedirect(ServerPlayerEntity owner, MobEntity guardian, PetComponent component,
                                                float originalDamage, float redirectedAmount, float reserveFraction,
                                                boolean hitReserveLimit) {
        ServerWorld world = (ServerWorld) owner.getWorld();
        long currentTick = world.getTime();

        guardianCooldowns.put(guardian.getUuid(), new TimedEntry(world.getRegistryKey(), currentTick + BULWARK_COOLDOWN_TICKS));
        component.setCooldown("guardian_bulwark_cd", BULWARK_COOLDOWN_TICKS);
        component.setStateData("guardian_bulwark_hit_reserve_limit", hitReserveLimit);
        component.setStateData("guardian_bulwark_reserve_fraction", (double) reserveFraction);

        GuardianPrimeState primeState = new GuardianPrimeState(guardian.getUuid(), world.getRegistryKey(), currentTick + PRIMED_WINDOW_TICKS);
        primedOwners.put(owner.getUuid(), primeState);

        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(owner);
        ownerState.setTempState(PRIMED_STATE_KEY, currentTick + PRIMED_WINDOW_TICKS);
        ownerState.onHitTaken();

        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, OWNER_STRENGTH_TICKS, 0));
        if (owner.getVehicle() instanceof LivingEntity mount) {
            mount.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, MOUNT_RESIST_TICKS, 0));
        }

        FeedbackManager.emitGuardianDamageAbsorbed(guardian, world);
        UIFeedbackManager.sendGuardianBulwarkMessage(owner, getGuardianName(guardian));

        TriggerContext context = new TriggerContext(world, guardian, owner, "after_pet_redirect")
            .withData("original_damage", (double) originalDamage)
            .withData("redirected_damage", (double) redirectedAmount)
            .withData("reserve_fraction", (double) reserveFraction)
            .withData("hit_reserve_limit", hitReserveLimit);
        AbilityManager.triggerAbilities(guardian, context);

        Petsplus.LOGGER.debug("Guardian {} absorbed {} of {} damage for {}", guardian.getName().getString(),
            redirectedAmount, originalDamage, owner.getName().getString());
    }

    /**
     * Consume the primed blessing immediately before a melee swing.
     */
    public static void handlePrimedPreAttack(PlayerEntity player, LivingEntity target) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        consumePrimedState(serverPlayer, target);
    }

    private static void handleAfterDamage(LivingEntity entity, DamageSource source, float baseAmount, float damageTaken, boolean blocked) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }
        consumePrimedState(attacker, entity);
    }

    private static void consumePrimedState(ServerPlayerEntity owner, @Nullable LivingEntity victim) {
        GuardianPrimeState primeState = primedOwners.get(owner.getUuid());
        if (primeState == null) {
            return;
        }

        ServerWorld world = (ServerWorld) owner.getWorld();
        if (!primeState.isActive(world)) {
            primedOwners.remove(owner.getUuid());
            clearOwnerPrimedState(owner);
            return;
        }

        primedOwners.remove(owner.getUuid());
        applyPrimedEffects(owner, victim);
        clearOwnerPrimedState(owner);
    }

    private static void applyPrimedEffects(ServerPlayerEntity owner, @Nullable LivingEntity victim) {
        ServerWorld world = (ServerWorld) owner.getWorld();
        if (victim != null && victim.isAlive()) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, WEAKNESS_TICKS, 0));
        }
        FeedbackManager.emitFeedback("guardian_shield_bash", victim != null ? victim : owner, world);
    }

    private static void clearOwnerPrimedState(ServerPlayerEntity owner) {
        OwnerCombatState state = OwnerCombatState.get(owner);
        if (state != null) {
            state.clearTempState(PRIMED_STATE_KEY);
        }
    }

    public static void handlePlayerTick(ServerPlayerEntity player) {
        GuardianPrimeState primeState = primedOwners.get(player.getUuid());
        if (primeState == null) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        if (!primeState.matches(world) || primeState.isExpired(world)) {
            primedOwners.remove(player.getUuid());
            clearOwnerPrimedState(player);
        }
    }

    public static boolean hasActiveGuardianProtection(ServerPlayerEntity player) {
        return !collectGuardiansForIntercept(player).isEmpty();
    }

    public static float getGuardianDamageReduction(ServerPlayerEntity player) {
        float total = 0.0f;
        for (GuardianCandidate candidate : collectGuardiansForIntercept(player)) {
            float scaledRatio = computeRedirectRatio(candidate.component().getLevel()) * candidate.healthFraction();
            total += scaledRatio;
        }
        return MathHelper.clamp(total, 0.0f, 1.0f);
    }

    private static String getGuardianName(MobEntity guardian) {
        return guardian.hasCustomName()
            ? guardian.getCustomName().getString()
            : guardian.getType().getName().getString();
    }

    public record GuardianCandidate(MobEntity pet, PetComponent component, float reserveFraction,
                                    float spareHealth, float healthFraction, boolean onCooldown) {
        public float reserveHealth() {
            return pet.getMaxHealth() * reserveFraction;
        }

        public int level() {
            return component.getLevel();
        }
    }

    private record TimedEntry(RegistryKey<World> worldKey, long expiryTick) {
        boolean matches(ServerWorld world) {
            return world.getRegistryKey().equals(worldKey);
        }

        boolean isExpired(ServerWorld world) {
            return matches(world) && world.getTime() >= expiryTick;
        }
    }

    private static final class GuardianPrimeState {
        private final UUID guardianId;
        private final TimedEntry window;

        GuardianPrimeState(UUID guardianId, RegistryKey<World> worldKey, long expiryTick) {
            this.guardianId = guardianId;
            this.window = new TimedEntry(worldKey, expiryTick);
        }

        boolean matches(ServerWorld world) {
            return window.matches(world);
        }

        boolean isActive(ServerWorld world) {
            return matches(world) && world.getTime() <= window.expiryTick;
        }

        boolean isExpired(ServerWorld world) {
            return window.isExpired(world);
        }

        @SuppressWarnings("unused")
        public UUID guardianId() {
            return guardianId;
        }
    }
}
