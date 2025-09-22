package woflo.petsplus.roles.guardian;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.FeedbackManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements Guardian role mechanics: damage interception, protective buffs, and Bulwark ability.
 *
 * Core Features:
 * - Baseline: +Knockback Resistance scalar, modest Max HP scalar (handled in PetAttributeManager)
 * - Bulwark (passive, cooldown): Redirects portion of damage from owner to pet, primes owner's next attack
 * - Feature levels unlock protective abilities and enhance damage reduction
 *
 * Design Philosophy:
 * - Tank/Protection archetype focused on keeping the owner alive
 * - Reactive abilities that trigger on owner taking damage
 * - Damage redirection with strategic buffs and debuffs
 */
public class GuardianCore {

    // Bulwark cooldown tracking per pet
    private static final Map<UUID, Long> bulwarkCooldowns = new ConcurrentHashMap<>();

    // Owner attack priming tracking (from successful Bulwark redirects)
    private static final Map<UUID, PrimedBulwarkData> primedAttacks = new ConcurrentHashMap<>();

    // Configuration constants (could be moved to config later)
    private static final int BULWARK_COOLDOWN_TICKS = 200; // 10 seconds
    private static final int ATTACK_PRIMING_DURATION_TICKS = 80; // 4 seconds
    private static final float BASE_DAMAGE_REDIRECT_RATIO = 0.28f; // 28% base redirect
    private static final float DAMAGE_REDIRECT_PER_LEVEL = 0.018f; // +1.8% per level after 1
    private static final float DAMAGE_REDIRECT_RATIO_CAP = 0.58f; // Soft cap before health scaling
    private static final float DAMAGE_REDIRECT_HEALTH_FLOOR = 0.65f; // Minimum redirect share when barely healthy
    private static final float DAMAGE_REDIRECT_HEALTH_BONUS = 0.35f; // Additional share unlocked at full health
    private static final double GUARDIAN_PROXIMITY_RANGE = 16.0; // Range to find Guardian pets
    private static final String GUARDIAN_PRIMED_STATE_KEY = "guardian_bulwark_active_end";
    private static final float BULWARK_RESERVE_FRACTION_MAX = 0.14f; // Rookie guardians hold extra buffer
    private static final float BULWARK_RESERVE_FRACTION_MIN = 0.10f; // Never drop below 10% health floor
    private static final float BULWARK_RESERVE_FRACTION_PER_LEVEL = 0.01f; // 1% shaved per level beyond 1

    private record GuardianCandidate(MobEntity guardian, PetComponent component) {}

    private static final class PrimedBulwarkData {
        private final long expiryTick;

        private PrimedBulwarkData(long expiryTick) {
            this.expiryTick = expiryTick;
        }

        private boolean isExpired(long currentTick) {
            return currentTick > expiryTick;
        }
    }

    public static void initialize() {
        // Register post-damage handler to consume primed attacks
        ServerLivingEntityEvents.AFTER_DAMAGE.register(GuardianCore::onEntityAfterDamage);

        // Register world tick for cooldown cleanup and attack processing
        ServerTickEvents.END_WORLD_TICK.register(GuardianCore::onWorldTick);
    }

    /**
     * Find nearby Guardian pets that can intercept damage.
     */
    public static List<MobEntity> findNearbyGuardianPets(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return List.of();
        }

        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(GUARDIAN_PROXIMITY_RANGE),
            pet -> {
                PetComponent petComp = PetComponent.get(pet);
                return petComp != null &&
                       petComp.getRole() == PetRole.GUARDIAN &&
                       petComp.isOwnedBy(player) &&
                       pet.isAlive() &&
                       !petComp.isPerched();
            }
        );
    }

    /**
     * Compute the minimum health a Guardian must keep in reserve when life-sharing.
     */
    public static float getBulwarkReserveHealth(MobEntity guardian, PetComponent component) {
        int level = component != null ? component.getLevel() : 1;
        float reserveFraction = computeBulwarkReserveFraction(level);
        return guardian.getMaxHealth() * reserveFraction;
    }

    /**
     * Convenience overload when a component lookup is required internally.
     */
    public static float getBulwarkReserveHealth(MobEntity guardian) {
        return getBulwarkReserveHealth(guardian, PetComponent.get(guardian));
    }

    /**
     * Determine whether a Guardian has enough health above its reserve to intercept.
     */
    public static boolean canGuardianSafelyRedirect(MobEntity guardian, PetComponent component) {
        if (guardian == null || component == null) {
            return false;
        }

        return guardian.getHealth() > getBulwarkReserveHealth(guardian, component);
    }

    public static boolean canGuardianSafelyRedirect(MobEntity guardian) {
        return canGuardianSafelyRedirect(guardian, PetComponent.get(guardian));
    }

    private static float computeBulwarkReserveFraction(int level) {
        float fraction = BULWARK_RESERVE_FRACTION_MAX - Math.max(0, level - 1) * BULWARK_RESERVE_FRACTION_PER_LEVEL;
        return MathHelper.clamp(fraction, BULWARK_RESERVE_FRACTION_MIN, BULWARK_RESERVE_FRACTION_MAX);
    }

    /**
     * Find the best Guardian pet for damage interception.
     * Prioritizes: not on cooldown > higher level > higher health percentage
     */
    public static MobEntity findBestGuardianForIntercept(List<MobEntity> guardianPets) {
        if (guardianPets.isEmpty()) {
            return null;
        }

        long currentTime = guardianPets.get(0).getWorld().getTime();

        return guardianPets.stream()
            .map(pet -> new GuardianCandidate(pet, PetComponent.get(pet)))
            .filter(candidate -> candidate.component() != null &&
                                 canGuardianSafelyRedirect(candidate.guardian(), candidate.component()))
            .sorted((candidate1, candidate2) -> {
                MobEntity pet1 = candidate1.guardian();
                MobEntity pet2 = candidate2.guardian();

                boolean pet1OnCooldown = isOnBulwarkCooldown(pet1, currentTime);
                boolean pet2OnCooldown = isOnBulwarkCooldown(pet2, currentTime);

                if (pet1OnCooldown != pet2OnCooldown) {
                    return Boolean.compare(pet1OnCooldown, pet2OnCooldown);
                }

                float pet1Reserve = getBulwarkReserveHealth(pet1, candidate1.component());
                float pet2Reserve = getBulwarkReserveHealth(pet2, candidate2.component());
                float pet1Spare = pet1.getHealth() - pet1Reserve;
                float pet2Spare = pet2.getHealth() - pet2Reserve;

                int spareDiff = Float.compare(pet2Spare, pet1Spare);
                if (spareDiff != 0) {
                    return spareDiff;
                }

                int levelDiff = Integer.compare(
                    candidate2.component().getLevel(),
                    candidate1.component().getLevel()
                );
                if (levelDiff != 0) {
                    return levelDiff;
                }

                float health1Pct = pet1.getHealth() / pet1.getMaxHealth();
                float health2Pct = pet2.getHealth() / pet2.getMaxHealth();
                return Float.compare(health2Pct, health1Pct);
            })
            .map(GuardianCandidate::guardian)
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if a Guardian pet is on Bulwark cooldown.
     */
    public static boolean isOnBulwarkCooldown(MobEntity guardian, long currentTime) {
        Long cooldownExpiry = bulwarkCooldowns.get(guardian.getUuid());
        return cooldownExpiry != null && currentTime < cooldownExpiry;
    }

    /**
     * Calculate damage redirect ratio based on Guardian level.
     */
    public static float calculateRedirectRatio(int level) {
        return calculateRedirectRatio(level, 1.0f);
    }

    public static float calculateRedirectRatio(int level, float guardianHealthFraction) {
        float ratio = BASE_DAMAGE_REDIRECT_RATIO;
        if (level > 1) {
            ratio += (level - 1) * DAMAGE_REDIRECT_PER_LEVEL;
        }

        ratio = MathHelper.clamp(ratio, BASE_DAMAGE_REDIRECT_RATIO, DAMAGE_REDIRECT_RATIO_CAP);

        float healthScale = MathHelper.clamp(
            DAMAGE_REDIRECT_HEALTH_FLOOR + guardianHealthFraction * DAMAGE_REDIRECT_HEALTH_BONUS,
            DAMAGE_REDIRECT_HEALTH_FLOOR,
            1.0f
        );

        return ratio * healthScale;
    }

    /**
     * Record a successful Bulwark redirect, priming the owner for their next strike and firing triggers.
     */
    public static void recordSuccessfulRedirect(ServerPlayerEntity owner, MobEntity guardian,
                                                DamageSource damageSource, float originalDamage,
                                                float redirectedDamage, float reserveHealth,
                                                boolean hitReserveLimit) {
        if (!(owner.getWorld() instanceof ServerWorld world)) {
            return;
        }

        long currentTime = world.getTime();
        bulwarkCooldowns.put(guardian.getUuid(), currentTime + BULWARK_COOLDOWN_TICKS);

        long primedExpiry = currentTime + ATTACK_PRIMING_DURATION_TICKS;
        primedAttacks.put(owner.getUuid(), new PrimedBulwarkData(primedExpiry));

        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(owner);
        ownerState.setTempState(GUARDIAN_PRIMED_STATE_KEY, primedExpiry);

        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0));
        if (owner.getVehicle() instanceof LivingEntity mount) {
            applyMountStabilityBonus(mount);
        }

        playBulwarkFeedback(owner, guardian, redirectedDamage, originalDamage, reserveHealth, hitReserveLimit);

        owner.sendMessage(
            Text.literal("§6⚔ §eGuardian's Blessing: ").append(
                Text.literal("Your next strikes are empowered!").formatted(Formatting.YELLOW)
            ),
            true
        );

        TriggerContext context = new TriggerContext(world, guardian, owner, "after_pet_redirect")
            .withData("original_damage", (double) redirectedDamage)
            .withData("damage_source", damageSource)
            .withData("guardian_reserve_health", (double) reserveHealth)
            .withData("guardian_reserve_limit_hit", hitReserveLimit);
        AbilityManager.triggerAbilities(guardian, context);
    }

    /**
     * Apply primed Guardian attack effects when the owner begins a melee strike.
     */
    public static void handlePrimedPreAttack(ServerPlayerEntity attacker, LivingEntity target) {
        PrimedBulwarkData data = primedAttacks.get(attacker.getUuid());
        if (data == null) {
            return;
        }

        long currentTime = attacker.getWorld().getTime();
        if (data.isExpired(currentTime)) {
            clearPrimedState(attacker);
            return;
        }

        applyWeaknessEffect(target);
        emitPrimedAttackFeedback(attacker);
        clearPrimedState(attacker);
    }

    /**
     * Consume primed Guardian attacks once the owner damages a target (projectile fallback).
     */
    private static void onEntityAfterDamage(LivingEntity entity, DamageSource damageSource, float baseDamageAmount,
                                            float damageTaken, boolean blocked) {
        if (!(damageSource.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }

        PrimedBulwarkData data = primedAttacks.get(attacker.getUuid());
        if (data == null) {
            return;
        }

        long currentTime = attacker.getWorld().getTime();
        if (data.isExpired(currentTime)) {
            clearPrimedState(attacker);
            return;
        }

        applyWeaknessEffect(entity);
        emitPrimedAttackFeedback(attacker);
        clearPrimedState(attacker);
    }

    private static void emitPrimedAttackFeedback(ServerPlayerEntity attacker) {
        if (attacker.getWorld() instanceof ServerWorld world) {
            FeedbackManager.emitFeedback("guardian_primed_attack", attacker, world);
        }
    }

    private static void applyWeaknessEffect(LivingEntity target) {
        if (target != null && target.isAlive()) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 50, 0));
        }
    }

    /**
     * Play visual and audio feedback for Bulwark damage redirection.
     */
    private static void playBulwarkFeedback(ServerPlayerEntity player, MobEntity guardian, float redirectedDamage,
                                            float originalDamage, float reserveHealth, boolean hitReserveLimit) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        float redirectRatio = originalDamage <= 0 ? 0.0f : redirectedDamage / originalDamage;

        world.playSound(
            null,
            guardian.getX(), guardian.getY(), guardian.getZ(),
            SoundEvents.ITEM_SHIELD_BLOCK,
            SoundCategory.NEUTRAL,
            0.8f,
            1.2f
        );

        FeedbackManager.emitFeedback("guardian_bulwark", guardian, world);

        int redirectPercent = Math.round(redirectRatio * 100);
        String guardianName = guardian.hasCustomName() ?
            guardian.getCustomName().getString() :
            guardian.getType().getName().getString();

        MutableText baseMessage = Text.literal("§9🛡 §b").append(Text.literal(guardianName)).append(
            Text.literal(" absorbed " + redirectPercent + "% damage!").formatted(Formatting.AQUA)
        );

        if (hitReserveLimit) {
            int reservePercent = Math.round((reserveHealth / guardian.getMaxHealth()) * 100);
            baseMessage = baseMessage.append(
                Text.literal(" (reserve at " + reservePercent + "%)").formatted(Formatting.RED)
            );
        }

        player.sendMessage(baseMessage, true);
    }

    /**
     * World tick handler for cooldown cleanup and passive effects.
     */
    private static void onWorldTick(ServerWorld world) {
        long currentTime = world.getTime();

        bulwarkCooldowns.entrySet().removeIf(entry -> currentTime > entry.getValue());

        primedAttacks.entrySet().removeIf(entry -> {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }

            if (player.getWorld() != world) {
                return false;
            }

            PrimedBulwarkData data = entry.getValue();
            if (data == null) {
                return true;
            }

            if (data.isExpired(player.getWorld().getTime())) {
                OwnerCombatState ownerState = OwnerCombatState.get(player);
                if (ownerState != null) {
                    ownerState.clearTempState(GUARDIAN_PRIMED_STATE_KEY);
                }
                return true;
            }

            return false;
        });
    }

    private static void clearPrimedState(ServerPlayerEntity attacker) {
        primedAttacks.remove(attacker.getUuid());
        OwnerCombatState ownerState = OwnerCombatState.get(attacker);
        if (ownerState != null) {
            ownerState.clearTempState(GUARDIAN_PRIMED_STATE_KEY);
        }
    }

    private static void applyMountStabilityBonus(LivingEntity mount) {
        mount.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0));
    }

    /**
     * Check if a player has an active Guardian providing protection.
     */
    public static boolean hasActiveGuardianProtection(ServerPlayerEntity player) {
        List<MobEntity> guardians = findNearbyGuardianPets(player);
        long currentTime = player.getWorld().getTime();
        return guardians.stream().anyMatch(guardian ->
            !isOnBulwarkCooldown(guardian, currentTime) && canGuardianSafelyRedirect(guardian)
        );
    }

    /**
     * Get the damage reduction factor from active Guardian pets.
     */
    public static float getGuardianDamageReduction(ServerPlayerEntity player) {
        List<MobEntity> guardians = findNearbyGuardianPets(player);
        if (guardians.isEmpty()) return 0.0f;

        MobEntity bestGuardian = findBestGuardianForIntercept(guardians);
        if (bestGuardian == null) return 0.0f;

        PetComponent petComp = PetComponent.get(bestGuardian);
        if (petComp == null) return 0.0f;

        if (!canGuardianSafelyRedirect(bestGuardian, petComp)) {
            return 0.0f;
        }

        float healthFraction = bestGuardian.getMaxHealth() <= 0.0f
            ? 0.0f
            : bestGuardian.getHealth() / bestGuardian.getMaxHealth();
        return calculateRedirectRatio(petComp.getLevel(), healthFraction);
    }
}
