package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.tag.FluidTags;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.abilities.AbilityTriggerResult;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.emotions.PetMoodEngine;
import woflo.petsplus.tags.PetsplusEntityTypeTags;
import woflo.petsplus.roles.guardian.GuardianBulwark;
import woflo.petsplus.roles.striker.StrikerExecution;
import woflo.petsplus.roles.striker.StrikerExecution.ExecutionKillSummary;
import woflo.petsplus.roles.striker.StrikerHuntManager;
import woflo.petsplus.ui.UIFeedbackManager;
import woflo.petsplus.util.BehaviorSeedUtil;
import woflo.petsplus.util.TriggerConditions;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Handles combat-related events and triggers pet abilities accordingly.
 */
public class CombatEventHandler {

    private static final Map<EntityType<?>, Boolean> FLYER_TYPE_CACHE = new ConcurrentHashMap<>();
    
    // Cache for expensive pet swarm operations to improve performance
    private static final Map<java.util.UUID, List<PetSwarmIndex.SwarmEntry>> PET_SWARM_CACHE = new ConcurrentHashMap<>();
    private static final Map<java.util.UUID, Long> PET_SWARM_CACHE_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final long PET_SWARM_CACHE_TTL = 100; // 5 seconds cache TTL (100 ticks)
    
    // Coordinated attack tracking for Pack Spirit (enemy UUID -> set of pet UUIDs that damaged it with timestamp)
    private static final Map<java.util.UUID, Map<java.util.UUID, Long>> COORDINATED_ATTACKS = new ConcurrentHashMap<>();
    
    // Ultra-rare mood constants
    private static final double ULTRA_RARE_TRIGGER_RADIUS = 32.0;
    private static final long PACK_COORDINATION_WINDOW = 200L; // 10 seconds in ticks
    private static final long COORDINATED_ATTACK_CLEANUP_AGE = 1200L; // 1 minute in ticks
    private static final long DEEP_DARK_VETERAN_THRESHOLD = 12000L; // 10 minutes in ticks
    private static final int PACK_SPIRIT_MIN_PETS = 3;
    private static final int PACK_SPIRIT_ROLE_DIVERSITY_MIN = 3;
    
    private static final ThreadLocal<Boolean> SUPPRESS_INTERCEPTION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String CHIP_DAMAGE_ACCUM_KEY = "restless_chip_accum";
    private static final String CHIP_DAMAGE_LAST_TICK_KEY = "restless_chip_last_tick";
    private static final String CHIP_DAMAGE_OWNER_ACCUM_KEY = "restless_owner_chip_accum";
    private static final String CHIP_DAMAGE_OWNER_LAST_TICK_KEY = "restless_owner_chip_last_tick";
    private static final float CHIP_DAMAGE_RATIO_CEILING = 0.30f; // ≤30% max health counts as chip damage
    private static final float CHIP_DAMAGE_THRESHOLD = 0.45f;      // Accumulated chip ratio before surging restlessness
    private static final int CHIP_DAMAGE_DECAY_TICKS = 80;         // ~4 seconds for decay calculations
    private static final float CHIP_DAMAGE_DECAY_STEP = 0.18f;     // Amount removed per decay interval
    private static final int OWNER_ASSIST_TARGET_TTL = 160;
    private static final int OWNER_ASSIST_CHAIN_LIMIT = 3;
    private static final double OWNER_ASSIST_BROADCAST_RADIUS = 24.0;
    private static final double OWNER_ASSIST_CHAIN_RADIUS = 5.0;
    private static final int OWNER_INTERCEPT_COOLDOWN_TICKS = 40;
    private static final TagKey<EntityType<?>> VANILLA_CREEPER_SOOTHER_TAG = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("minecraft", "scares_creepers"));
    private static final float OWNER_LOW_HEALTH_THRESHOLD = 0.45f;
    private static final String STATE_COMBAT_START_TICK = "combat_encounter_start";
    private static final String STATE_COMBAT_RELIEF_TICK = "combat_relief_last";

    public static void register() {
        // Register damage events
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatEventHandler::onDamageReceived);
        AttackEntityCallback.EVENT.register(CombatEventHandler::onPlayerAttack);
        ServerLivingEntityEvents.AFTER_DEATH.register(CombatEventHandler::onEntityDeath);

        Petsplus.LOGGER.info("Combat event handlers registered");
    }
    
    /**
     * Invalidate cached pet swarm data for a specific owner
     * Call this when pets are added/removed or when cache needs to be refreshed
     */
    public static void invalidatePetSwarmCache(java.util.UUID ownerUuid) {
        PET_SWARM_CACHE.remove(ownerUuid);
        PET_SWARM_CACHE_TIMESTAMPS.remove(ownerUuid);
    }
    
    /**
     * Cleanup stale coordinated attack entries to prevent memory leak.
     * Call this periodically (e.g., every 1 minute) to remove old attack tracking data.
     */
    private static void cleanupStaleCoordinatedAttacks(long currentTime) {
        final int[] removedVictims = {0};
        final int[] removedAttacks = {0};
        
        COORDINATED_ATTACKS.entrySet().removeIf(victimEntry -> {
            // Remove individual attack entries older than threshold
            int beforeSize = victimEntry.getValue().size();
            victimEntry.getValue().entrySet().removeIf(attackEntry -> 
                currentTime - attackEntry.getValue() > COORDINATED_ATTACK_CLEANUP_AGE
            );
            removedAttacks[0] += (beforeSize - victimEntry.getValue().size());
            
            // Remove victim entry if no attacks remain
            boolean shouldRemove = victimEntry.getValue().isEmpty();
            if (shouldRemove) {
                removedVictims[0]++;
            }
            return shouldRemove;
        });
        
        if (removedVictims[0] > 0 || removedAttacks[0] > 0) {
            Petsplus.LOGGER.debug("Cleaned up {} stale attack entries across {} victims from coordinated attack tracker",
                removedAttacks[0], removedVictims[0]);
        }
    }
    
    /**
     * Clean up expired cache entries to prevent memory leaks
     * This should be called periodically (e.g., in a server tick event)
     */
    public static void cleanupExpiredCacheEntries(long currentTime) {
        PET_SWARM_CACHE_TIMESTAMPS.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > PET_SWARM_CACHE_TTL) {
                PET_SWARM_CACHE.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Called when any living entity receives damage.
     */
    private static boolean onDamageReceived(LivingEntity entity, DamageSource damageSource, float amount) {
        if (Boolean.TRUE.equals(SUPPRESS_INTERCEPTION.get())) {
            return true;
        }
        if (amount <= 0.0F) {
            return true;
        }

        boolean allowDamage = true;
        float appliedDamage = amount;
        boolean targetManual = false;
        boolean targetCancelled = false;
        PlayerEntity attackingOwner = damageSource.getAttacker() instanceof PlayerEntity
            ? (PlayerEntity) damageSource.getAttacker()
            : null;

        if (attackingOwner != null) {
            DamageProcessingOutcome outgoingOutcome = processOwnerOutgoingDamage(attackingOwner, entity, damageSource, appliedDamage);
            if (outgoingOutcome.cancelled()) {
                targetCancelled = true;
                appliedDamage = 0.0F;
            } else {
                appliedDamage = outgoingOutcome.damage();
                targetManual |= outgoingOutcome.manual();
            }
        }

        if (entity instanceof PlayerEntity player) {
            DamageProcessingOutcome ownerOutcome = processOwnerDamage(player, damageSource, appliedDamage);
            if (ownerOutcome.cancelled()) {
                allowDamage = false;
            } else {
                float appliedAmount = ownerOutcome.damage();
                boolean damageApplied = ownerOutcome.manual();
                if (damageApplied) {
                    applyManualDamage(player, damageSource, appliedAmount);
                    appliedDamage = appliedAmount;
                    targetManual = false;
                    allowDamage = false;
                }
                if (appliedAmount > 0.0F) {
                    handleOwnerDamageReceived(player, damageSource, appliedAmount, damageApplied);
                }
            }
        }

        // Check if the damage was dealt by a player
        if (attackingOwner instanceof ServerPlayerEntity serverOwner
            && !targetCancelled && appliedDamage > 0.0F
            && damageSource.isOf(DamageTypes.PLAYER_ATTACK)
            && damageSource.getSource() == attackingOwner
            && isCriticalMeleeHit(serverOwner)) {
            broadcastOwnerCriticalHit(serverOwner, entity, appliedDamage);
        }

        if (attackingOwner != null && !targetCancelled && appliedDamage > 0.0F) {
            handleOwnerDealtDamage(attackingOwner, entity, appliedDamage);
        }

        // Check if damage was from a projectile shot by a player
        if (damageSource.getSource() instanceof PersistentProjectileEntity projectile) {
            if (projectile.getOwner() instanceof PlayerEntity shooter && !targetCancelled) {
                handleProjectileDamage(shooter, entity, appliedDamage, projectile);
            }
        }

        if (entity instanceof MobEntity mobEntity) {
            PetComponent petComponent = PetComponent.get(mobEntity);
            if (petComponent != null) {
                DamageProcessingOutcome petOutcome = processPetDamage(mobEntity, petComponent, damageSource, appliedDamage);
                if (petOutcome.cancelled()) {
                    targetCancelled = true;
                    appliedDamage = 0.0F;
                } else {
                    appliedDamage = petOutcome.damage();
                    targetManual |= petOutcome.manual();
                    if (appliedDamage > 0.0F) {
                        handlePetDamageReceived(mobEntity, petComponent, damageSource, appliedDamage);
                    }
                }
            }
        }

        // Check if a pet dealt damage - process outgoing damage modification first
        if (damageSource.getAttacker() instanceof MobEntity attackerMob) {
            PetComponent petComponent = PetComponent.get(attackerMob);
            if (petComponent != null && entity instanceof LivingEntity victim) {
                // Process pet outgoing damage for modification before damage is applied
                DamageProcessingOutcome petOutgoingOutcome = processPetOutgoingDamage(attackerMob, petComponent, victim, damageSource, appliedDamage);
                if (petOutgoingOutcome.cancelled()) {
                    targetCancelled = true;
                    appliedDamage = 0.0F;
                } else {
                    appliedDamage = petOutgoingOutcome.damage();
                    targetManual |= petOutgoingOutcome.manual();
                }
            }
        }

        // Fire post-damage trigger for pets after damage calculation is complete
        if (damageSource.getAttacker() instanceof MobEntity attackerMob && !targetCancelled && appliedDamage > 0.0F) {
            PetComponent petComponent = PetComponent.get(attackerMob);
            if (petComponent != null && entity instanceof LivingEntity victim) {
                handlePetDealtDamage(attackerMob, petComponent, victim, appliedDamage);
            }
        }

        if (targetCancelled) {
            allowDamage = false;
        } else if (targetManual) {
            applyManualDamage(entity, damageSource, appliedDamage);
            allowDamage = false;
        }

        return allowDamage;
    }

    private static DamageProcessingOutcome processOwnerDamage(PlayerEntity owner,
                                                              DamageSource damageSource,
                                                              float amount) {
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return DamageProcessingOutcome.allowOutcome(amount);
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return DamageProcessingOutcome.allowOutcome(amount);
        }
        if (amount <= 0.0F) {
            return DamageProcessingOutcome.allowOutcome(0.0F);
        }

        boolean lethalPreCheck = owner.getHealth() - amount <= 0.0F;

        Map<String, Object> payload = new HashMap<>();
        payload.put("damage", (double) amount);
        payload.put("damage_source", damageSource);
        payload.put("intercept_damage", true);
        if (damageSource.isOf(DamageTypes.FALL)) {
            payload.put("fall_distance", (double) owner.fallDistance);
        }
        if (lethalPreCheck) {
            payload.put("lethal_damage", true);
        }

        if (!payload.containsKey(GuardianBulwark.STATE_DATA_KEY)) {
            payload.put(GuardianBulwark.STATE_DATA_KEY, new GuardianBulwark.SharedState());
        }

        DamageInterceptionResult interception = new DamageInterceptionResult(amount);
        payload.put("damage_result", interception);

        StateManager manager = StateManager.forWorld(serverWorld);
        AbilityTriggerResult incoming = manager.dispatchAbilityTrigger(serverOwner, "owner_incoming_damage", payload);
        DamageInterceptionResult result = incoming.damageResult();
        if (result == null) {
            result = interception;
        }

        if (result.isCancelled() || result.getRemainingDamageAmount() <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        double remaining = result.getRemainingDamageAmount();
        if (remaining <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        if (remaining >= owner.getHealth()) {
            Map<String, Object> lethalPayload = new HashMap<>();
            lethalPayload.put("damage", remaining);
            lethalPayload.put("damage_source", damageSource);
            lethalPayload.put("lethal_damage", true);
            lethalPayload.put("intercept_damage", true);
            lethalPayload.put("damage_result", result);

            AbilityTriggerResult lethalResult = manager.dispatchAbilityTrigger(serverOwner, "owner_lethal_damage", lethalPayload);
            DamageInterceptionResult lethalInterception = lethalResult.damageResult();
            if (lethalInterception != null) {
                result = lethalInterception;
            }
            if (result.isCancelled() || result.getRemainingDamageAmount() <= 0.0D) {
                return DamageProcessingOutcome.cancelledOutcome();
            }
            remaining = result.getRemainingDamageAmount();
        }

        float finalAmount = (float) remaining;
        if (finalAmount <= 0.0F) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        if (result.isModified()) {
            return DamageProcessingOutcome.manualOutcome(finalAmount);
        }
        return DamageProcessingOutcome.allowOutcome(finalAmount);
    }

    private static DamageProcessingOutcome processOwnerOutgoingDamage(PlayerEntity attacker,
                                                                      LivingEntity victim,
                                                                      DamageSource damageSource,
                                                                      float amount) {
        if (!(attacker instanceof ServerPlayerEntity serverAttacker)) {
            return DamageProcessingOutcome.allowOutcome(amount);
        }
        if (!(attacker.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return DamageProcessingOutcome.allowOutcome(amount);
        }
        if (amount <= 0.0F) {
            return DamageProcessingOutcome.allowOutcome(0.0F);
        }

        boolean lethalPreCheck = victim.getHealth() - amount <= 0.0F;

        Map<String, Object> payload = new HashMap<>();
        payload.put("damage", (double) amount);
        payload.put("damage_source", damageSource);
        payload.put("intercept_damage", true);
        payload.put("victim", victim);
        double victimHpPct = Math.max(0.0F, victim.getHealth()) / Math.max(1.0F, victim.getMaxHealth());
        payload.put("victim_hp_pct", victimHpPct);
        if (lethalPreCheck) {
            payload.put("lethal_damage", true);
        }

        DamageInterceptionResult interception = new DamageInterceptionResult(amount);
        payload.put("damage_result", interception);

        StateManager manager = StateManager.forWorld(serverWorld);
        AbilityTriggerResult outgoing = manager.dispatchAbilityTrigger(serverAttacker, "owner_outgoing_damage", payload);
        DamageInterceptionResult result = outgoing.damageResult();
        if (result == null) {
            result = interception;
        }

        if (result.isCancelled() || result.getRemainingDamageAmount() <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        double remaining = result.getRemainingDamageAmount();
        if (remaining <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        float finalAmount = (float) remaining;
        if (finalAmount <= 0.0F) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        if (result.isModified()) {
            return DamageProcessingOutcome.manualOutcome(finalAmount);
        }
        return DamageProcessingOutcome.allowOutcome(finalAmount);
    }

    private static DamageProcessingOutcome processPetOutgoingDamage(MobEntity pet,
                                                                     PetComponent component,
                                                                     LivingEntity victim,
                                                                     DamageSource damageSource,
                                                                     float amount) {
        if (!(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return DamageProcessingOutcome.allowOutcome(amount);
        }
        if (amount <= 0.0F) {
            return DamageProcessingOutcome.allowOutcome(0.0F);
        }

        PlayerEntity owner = component.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return DamageProcessingOutcome.allowOutcome(amount);
        }

        // Friendly-fire guard: prevent damaging pets owned by the same owner (custom or vanilla tamed)
        if (victim instanceof MobEntity victimMob) {
            boolean sameOwnerPet = false;
            PetComponent victimComp = PetComponent.get(victimMob);
            if (victimComp != null && owner != null && victimComp.isOwnedBy(owner)) {
                sameOwnerPet = true;
            } else if (victimMob instanceof TameableEntity tame) {
                LivingEntity tameOwner = tame.getOwner();
                sameOwnerPet = tameOwner != null && tameOwner.equals(owner);
            }
            if (sameOwnerPet) {
                return DamageProcessingOutcome.cancelledOutcome();
            }
        }

        boolean lethalPreCheck = victim.getHealth() - amount <= 0.0F;
        double victimHpPct = Math.max(0.0F, victim.getHealth()) / Math.max(1.0F, victim.getMaxHealth());

        // Build trigger context with comprehensive data
        TriggerContext ctx = new TriggerContext(serverWorld, pet, serverOwner, "pet_outgoing_damage")
            .withData("damage", (double) amount)
            .withData("damage_source", damageSource)
            .withData("intercept_damage", true)
            .withData("victim", victim)
            .withData("victim_hp_pct", victimHpPct)
            .withData("pet_health_pct", (double) (pet.getHealth() / pet.getMaxHealth()));

        if (lethalPreCheck) {
            ctx.withData("lethal_damage", true);
        }

        // Add light level data for darkness-based abilities
        int lightLevel = serverWorld.getLightLevel(pet.getBlockPos());
        ctx.withData("light_level", lightLevel)
           .withData("in_darkness", lightLevel <= 7);

        // Create damage interception result
        DamageInterceptionResult interception = new DamageInterceptionResult(amount);
        ctx.withData("damage_result", interception);

        // Trigger abilities
        AbilityTriggerResult triggerResult = AbilityManager.triggerAbilities(pet, ctx);
        DamageInterceptionResult result = triggerResult.damageResult();
        if (result == null) {
            result = interception;
        }

        // Handle cancellation
        if (result.isCancelled() || result.getRemainingDamageAmount() <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        // Get final damage amount
        double remaining = result.getRemainingDamageAmount();
        if (remaining <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        float finalAmount = (float) remaining;
        if (finalAmount <= 0.0F) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        // Return modified or unmodified outcome
        if (result.isModified()) {
            return DamageProcessingOutcome.manualOutcome(finalAmount);
        }
        return DamageProcessingOutcome.allowOutcome(finalAmount);
    }

    private static DamageProcessingOutcome processPetDamage(MobEntity pet,
                                                            PetComponent component,
                                                            DamageSource damageSource,
                                                            float amount) {
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return DamageProcessingOutcome.allowOutcome(amount);
        }
        if (amount <= 0.0F) {
            return DamageProcessingOutcome.allowOutcome(0.0F);
        }

        PlayerEntity owner = component.getOwner();
        ServerPlayerEntity serverOwner = owner instanceof ServerPlayerEntity ? (ServerPlayerEntity) owner : null;
        boolean lethalPreCheck = pet.getHealth() - amount <= 0.0F;

        DamageInterceptionResult interception = new DamageInterceptionResult(amount);

        TriggerContext incomingContext = new TriggerContext(world, pet, serverOwner, "pet_incoming_damage")
            .withData("damage", (double) amount)
            .withData("damage_source", damageSource)
            .withData("lethal_damage", lethalPreCheck)
            .withData("intercept_damage", true)
            .withDamageContext(damageSource, amount, lethalPreCheck, interception);
        if (damageSource.isOf(DamageTypes.FALL)) {
            incomingContext.withData("fall_distance", (double) pet.fallDistance);
        }

        AbilityTriggerResult incomingResult = AbilityManager.triggerAbilities(pet, incomingContext);
        DamageInterceptionResult result = incomingResult.damageResult();
        if (result == null) {
            result = interception;
        }

        if (result.isCancelled() || result.getRemainingDamageAmount() <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        double remaining = result.getRemainingDamageAmount();
        if (remaining <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        if (remaining >= pet.getHealth()) {
            TriggerContext lethalContext = new TriggerContext(world, pet, serverOwner, "pet_lethal_damage")
                .withData("damage", remaining)
                .withData("damage_source", damageSource)
                .withData("lethal_damage", true)
                .withData("intercept_damage", true)
                .withDamageContext(damageSource, remaining, true, result);

            AbilityTriggerResult lethalResult = AbilityManager.triggerAbilities(pet, lethalContext);
            DamageInterceptionResult lethalInterception = lethalResult.damageResult();
            if (lethalInterception != null) {
                result = lethalInterception;
            }
        if (result.isCancelled() || result.getRemainingDamageAmount() <= 0.0D) {
            return DamageProcessingOutcome.cancelledOutcome();
        }
            remaining = result.getRemainingDamageAmount();
        }

        float finalAmount = (float) remaining;
        if (finalAmount <= 0.0F) {
            return DamageProcessingOutcome.cancelledOutcome();
        }

        if (result.isModified()) {
            return DamageProcessingOutcome.manualOutcome(finalAmount);
        }
        return DamageProcessingOutcome.allowOutcome(finalAmount);
    }

    private static void applyManualDamage(LivingEntity entity,
                                          DamageSource damageSource,
                                          float amount) {
        if (amount <= 0.0F || entity == null) {
            return;
        }
        if (Boolean.TRUE.equals(SUPPRESS_INTERCEPTION.get())) {
            float newHealth = Math.max(0.0F, entity.getHealth() - amount);
            entity.setHealth(newHealth);
            if (newHealth <= 0.0F && !entity.isDead()) {
                entity.onDeath(damageSource);
            }
            return;
        }

        boolean previous = SUPPRESS_INTERCEPTION.get();
        SUPPRESS_INTERCEPTION.set(true);
        try {
            boolean applied = false;
            if (entity.getEntityWorld() instanceof ServerWorld serverWorld) {
                applied = entity.damage(serverWorld, damageSource, amount);
            }
            if (!applied) {
                float newHealth = Math.max(0.0F, entity.getHealth() - amount);
                entity.setHealth(newHealth);
                if (newHealth <= 0.0F && !entity.isDead()) {
                    entity.onDeath(damageSource);
                }
            }
        } finally {
            SUPPRESS_INTERCEPTION.set(previous);
        }
    }

    private record DamageProcessingOutcome(boolean cancelled, boolean manual, float damage) {
        static DamageProcessingOutcome cancelledOutcome() {
            return new DamageProcessingOutcome(true, false, 0.0F);
        }

        static DamageProcessingOutcome manualOutcome(float damage) {
            return new DamageProcessingOutcome(false, true, Math.max(0.0F, damage));
        }

        static DamageProcessingOutcome allowOutcome(float damage) {
            return new DamageProcessingOutcome(false, false, Math.max(0.0F, damage));
        }
    }

    /**
     * Called when a player attacks an entity directly.
     */
    private static ActionResult onPlayerAttack(PlayerEntity player, World world, Hand hand, Entity target, EntityHitResult hitResult) {
        // Ensure this only runs on the server side to prevent client-side execution
        if (world.isClient()) {
            return ActionResult.PASS;
        }
        
        // Additional server validation - ensure player is valid and in proper state
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResult.PASS;
        }
        
        if (target instanceof LivingEntity livingTarget) {
            // Apply any next attack riders
            OwnerCombatState combatState = OwnerCombatState.get(player);
            if (combatState != null) {
                applyAttackRiders(player, livingTarget, combatState);
            }
        }
        
        return ActionResult.PASS;
    }
    
    private static void handleOwnerDamageReceived(PlayerEntity owner,
                                                  DamageSource damageSource,
                                                  float amount,
                                                  boolean damageAlreadyApplied) {
        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        combatState.onHitTaken();
        long now = owner.getEntityWorld().getTime();
        if (!combatState.hasTempState(STATE_COMBAT_START_TICK)) {
            combatState.setTempState(STATE_COMBAT_START_TICK, now);
        }

        if (isFallDamage(damageSource)) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("damage", (double) amount);
            double fallDistance = owner.fallDistance;
            if (fallDistance > 0d) {
                payload.put("fall_distance", fallDistance);
            }
            triggerAbilitiesForOwner(owner, "owner_took_fall_damage", payload);
        }

        // Trigger low health events if needed
        float currentHealth = Math.max(0f, owner.getHealth());
        float healthAfter = damageAlreadyApplied ? currentHealth : Math.max(0f, currentHealth - amount);
        float maxHealth = Math.max(1f, owner.getMaxHealth());
        double healthPct = healthAfter / maxHealth;
        boolean lightHit = amount < maxHealth * 0.10f;

        if (healthPct <= 0.35) { // 35% health threshold
            triggerAbilitiesForOwner(owner, "on_owner_low_health");
        }
        
        // Check for pet proximity and trigger guardian abilities
        triggerNearbyPetAbilities(owner, "owner_damage_taken");

        float damageIntensity = damageIntensity(amount, maxHealth);
        float ownerDangerFactor = missingHealthFactor((float) healthPct, 0.6f);

        if (owner instanceof ServerPlayerEntity) {
            List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
            if (!swarm.isEmpty()) {
                Vec3d ownerPos = owner.getEntityPos();
                double radiusSq = 32.0 * 32.0;
                for (PetSwarmIndex.SwarmEntry entry : swarm) {
                    MobEntity pet = entry.pet();
                    PetComponent pc = entry.component();
                    if (pet == null || pc == null || !pet.isAlive()) {
                        continue;
                    }
                    if (!withinRadius(entry, ownerPos, radiusSq)) {
                        continue;
                    }

                    pc.setLastAttackTick(now);

                    float severity = MathHelper.clamp((damageIntensity * 0.6f) + (ownerDangerFactor * 0.4f), 0f, 1f);
                    pc.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_TICK, now);
                    pc.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_SEVERITY, severity);
                    pc.setStateData(PetComponent.StateKeys.OWNER_LAST_HEALTH_RATIO,
                        MathHelper.clamp((float) healthPct, 0f, 1f));
                    if (healthPct <= OWNER_LOW_HEALTH_THRESHOLD) {
                        pc.setStateData(PetComponent.StateKeys.OWNER_LAST_LOW_HEALTH_TICK, now);
                    }

                    float hazardSeverity = PetMoodEngine.computeStatusHazardSeverity(owner);
                    if (hazardSeverity > 0f) {
                    pc.setStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK, now);
                    pc.setStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_SEVERITY, hazardSeverity);
                }

                float closeness = 0.35f + 0.65f * proximityFactor(owner, pet, 32.0);
                float angstWeight = scaledAmount(0.16f, 0.50f, damageIntensity) * closeness;
                float vigilWeight = scaledAmount(0.20f + (0.18f * ownerDangerFactor), 0.55f, damageIntensity) * closeness;
                float startleWeight = scaledAmount(0.10f, 0.40f, damageIntensity);
                float frustrationWeight = scaledAmount(0.04f, 0.30f, damageIntensity) * closeness;
                float forebodingWeight = ownerDangerFactor > 0f
                    ? scaledAmount(0.05f, 0.40f, Math.max(ownerDangerFactor, damageIntensity)) * closeness
                    : 0f;
                float protectiveWeight = scaledAmount(0.14f + (0.16f * ownerDangerFactor), 0.52f,
                    Math.max(ownerDangerFactor, damageIntensity)) * closeness;

                if (angstWeight > 0f) {
                    if (lightHit) {
                        pc.pushEmotion(PetComponent.Emotion.WORRIED, angstWeight);
                    } else {
                        pc.pushEmotion(PetComponent.Emotion.ANGST, angstWeight);
                    }
                }
                if (vigilWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, vigilWeight);
                }
                if (startleWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.STARTLE, startleWeight);
                }
                if (frustrationWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.FRUSTRATION, frustrationWeight);
                }
                if (forebodingWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.FOREBODING, forebodingWeight);
                }
                if (protectiveWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.PROTECTIVE, protectiveWeight);
                }
            }
        }
        }

        maybeCommandIntercept(owner, combatState, damageSource, now);
    }
    
    private static void handleOwnerDealtDamage(PlayerEntity owner, LivingEntity victim, float damage) {
        if (damage > 0.0F && owner instanceof ServerPlayerEntity serverOwner
            && owner.getEntityWorld() instanceof ServerWorld) {
            XpEventHandler.trackPlayerCombat(serverOwner);
        }
        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        combatState.enterCombat();
        long now = owner.getEntityWorld().getTime();
        combatState.markOwnerInterference(now);
        if (!combatState.hasTempState(STATE_COMBAT_START_TICK)) {
            combatState.setTempState(STATE_COMBAT_START_TICK, now);
        }

        float modifiedDamage = damage;

        StrikerExecution.noteOwnerDamage(victim, owner);
        if (StrikerExecution.hasFinisherMark(victim)) {
            StrikerExecution.onAttackFinisherMark(owner, victim, modifiedDamage);
        }
        
        // Track pet damage for Pack Spirit coordinated attacks (O(1) operations)
        if (victim instanceof HostileEntity && victim.isAlive() && owner instanceof ServerPlayerEntity) {
            List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
            for (PetSwarmIndex.SwarmEntry entry : swarm) {
                MobEntity pet = entry.pet();
                if (pet != null && pet.isAlive() && pet.getEntityPos().distanceTo(victim.getEntityPos()) < ULTRA_RARE_TRIGGER_RADIUS) {
                    // Track which pets are engaged with this enemy
                    COORDINATED_ATTACKS.computeIfAbsent(victim.getUuid(), k -> new ConcurrentHashMap<>())
                        .put(pet.getUuid(), now);
                }
            }
            
            // Periodic cleanup to prevent memory leak (every ~1 minute based on combat frequency)
            if (now % 1200L == 0) {
                cleanupStaleCoordinatedAttacks(now);
            }
        }
        
        // Calculate victim health percentage after damage
        float victimHealthAfter = victim.getHealth() - modifiedDamage;
        float victimMaxHealth = Math.max(1f, victim.getMaxHealth());
        double victimHealthPct = victimHealthAfter / victimMaxHealth;
        float intensity = damageIntensity(modifiedDamage, victimMaxHealth);
        float finishFactor = missingHealthFactor((float) victimHealthPct, 0.35f);
        boolean victimIsHostile = victim instanceof HostileEntity;
        float ownerHealthRatio = owner.getMaxHealth() <= 0f ? 1f : owner.getHealth() / owner.getMaxHealth();
        float ownerPeril = MathHelper.clamp(1f - ownerHealthRatio, 0f, 1f);
        boolean ownerRecentlyHit = combatState.recentlyDamaged(now, 80);
        float aggressionSignal = MathHelper.clamp(0.38f + 0.45f * intensity + 0.20f * (victimIsHostile ? 1f : 0f) + 0.15f * finishFactor, 0f, 1f);
        float urgencySignal = MathHelper.clamp((victimIsHostile ? 0.25f : 0.10f) + 0.30f * finishFactor + 0.35f * ownerPeril + (ownerRecentlyHit ? 0.20f : 0f), 0f, 1f);

        // Create trigger context
        TriggerContext context = new TriggerContext(
            (net.minecraft.server.world.ServerWorld) owner.getEntityWorld(),
            null, // Pet will be set when triggering specific pet abilities
            owner,
            "owner_dealt_damage"
        ).withData("victim", victim)
         .withData("damage", (double) modifiedDamage)
         .withData("victim_hp_pct", victimHealthPct);

        StrikerExecution.ExecutionResult preview = StrikerExecution.previewExecution(owner, victim, modifiedDamage);
        if (preview.strikerLevel() > 0) {
            context.withData("striker_level", preview.strikerLevel())
                   .withData("striker_preview_threshold_pct", (double) preview.appliedThresholdPct())
                   .withData("striker_preview_momentum_stacks", preview.momentumStacks())
                   .withData("striker_preview_momentum_fill", (double) preview.momentumFill())
                   .withData("striker_preview_ready", preview.triggered());
        }

        // Trigger abilities for nearby pets
        triggerNearbyPetAbilities(owner, context);

        if (owner.getEntityWorld() instanceof ServerWorld) {
            if (victim instanceof MobEntity victimMob) {
                PetComponent victimComponent = PetComponent.get(victimMob);
                boolean sameOwnerPet = victimComponent != null && victimComponent.isOwnedBy(owner);
                // Also guard vanilla tamed entities owned by this owner
                if (!sameOwnerPet && victimMob instanceof TameableEntity tame) {
                    LivingEntity tameOwner = tame.getOwner();
                    sameOwnerPet = tameOwner != null && tameOwner.equals(owner);
                }

                if (sameOwnerPet) {
                    // Do not set aggro/notify against our own pets
                    combatState.clearAggroTarget();
                } else {
                    combatState.rememberAggroTarget(victim, now, OWNER_ASSIST_TARGET_TTL, true, aggressionSignal, urgencySignal, victimIsHostile);
                    notifyPetsOfOwnerTarget(owner, victim, now);
                }
            } else {
                combatState.rememberAggroTarget(victim, now, OWNER_ASSIST_TARGET_TTL, true, aggressionSignal, urgencySignal, victimIsHostile);
                notifyPetsOfOwnerTarget(owner, victim, now);
            }
        }

        // Emotions: combat engagement for nearby owned pets
        

        // Check if victim is one of the owner's pets
        final boolean victimIsOwnersPet = victim instanceof MobEntity victimMob &&
            PetComponent.get(victimMob) != null &&
            PetComponent.get(victimMob).isOwnedBy(owner);

        if (owner instanceof ServerPlayerEntity) {
            List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
            if (!swarm.isEmpty()) {
                Vec3d ownerPos = owner.getEntityPos();
                double radiusSq = 32.0 * 32.0;
                for (PetSwarmIndex.SwarmEntry entry : swarm) {
                    MobEntity pet = entry.pet();
                    PetComponent pc = entry.component();
                    if (pet == null || pc == null || !pet.isAlive() || pet.equals(victim)) {
                        continue;
                    }
                    if (!withinRadius(entry, ownerPos, radiusSq)) {
                        continue;
                    }

                    pc.setLastAttackTick(now);

                    float closeness = 0.35f + 0.65f * proximityFactor(owner, pet, 32.0);

                    if (victimIsOwnersPet) {
                        float distressWeight = scaledAmount(0.12f, 0.30f, intensity) * closeness;
                        float uneasinessWeight = scaledAmount(0.08f, 0.25f, intensity) * closeness;
                        float forebodingWeight = scaledAmount(0.06f, 0.18f, intensity) * closeness;

                        pc.pushEmotion(PetComponent.Emotion.ANGST, distressWeight);
                        pc.pushEmotion(PetComponent.Emotion.ENNUI, uneasinessWeight);
                        pc.pushEmotion(PetComponent.Emotion.FOREBODING, forebodingWeight);
                    } else {
                        float vigilWeight = scaledAmount(0.18f, 0.45f, intensity) * closeness;
                        float hopefulWeight = 0f;
                        float protectiveWeight = 0f;
                        if (victimIsHostile) {
                            float petHealthRatio = pet.getHealth() / pet.getMaxHealth();
                            if (petHealthRatio > 0.75f) {
                                hopefulWeight = scaledAmount(0.06f, 0.20f, Math.max(intensity, finishFactor)) * (0.75f + 0.25f * closeness) * petHealthRatio;
                            }
                            protectiveWeight = scaledAmount(0.14f, 0.38f, Math.max(intensity, ownerPeril)) * closeness;
                        }
                        float stoicWeight = finishFactor > 0f
                            ? scaledAmount(0.05f, 0.30f, finishFactor) * (0.65f + 0.35f * closeness)
                            : 0f;

                        if (victimIsHostile) {
                            float fervorWeight = scaledAmount(0.08f, 0.30f, intensity) * closeness;
                            if (fervorWeight > 0f) {
                                pc.pushEmotion(PetComponent.Emotion.KEFI, fervorWeight);
                            }
                        } else {
                            float regretWeight = scaledAmount(0.08f, 0.30f, intensity);
                            float wistfulWeight = scaledAmount(0.04f, 0.22f, Math.max(intensity, finishFactor)) * (0.6f + 0.4f * closeness);
                            float melancholyWeight = scaledAmount(0.06f, 0.24f, Math.max(intensity, finishFactor)) * (0.7f + 0.3f * closeness);
                            if (regretWeight > 0f) {
                                pc.pushEmotion(PetComponent.Emotion.REGRET, regretWeight);
                            }
                            if (wistfulWeight > 0f) {
                                pc.pushEmotion(PetComponent.Emotion.HIRAETH, wistfulWeight);
                            }
                            if (melancholyWeight > 0f) {
                                pc.pushEmotion(PetComponent.Emotion.MELANCHOLY, melancholyWeight);
                            }
                        }

                        if (vigilWeight > 0f) {
                            pc.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, vigilWeight);
                        }
                        if (protectiveWeight > 0f) {
                            pc.pushEmotion(PetComponent.Emotion.PROTECTIVE, protectiveWeight);
                        }
                        if (hopefulWeight > 0f) {
                            pc.pushEmotion(PetComponent.Emotion.HOPEFUL, hopefulWeight);
                        }
                        if (stoicWeight > 0f) {
                            pc.pushEmotion(PetComponent.Emotion.STOIC, stoicWeight);
                        }

                        if (victimIsHostile && victim instanceof LivingEntity livingVictim) {
                            boolean petEngaged = pet.getTarget() == livingVictim || livingVictim.getAttacker() == pet;
                            if (petEngaged && livingVictim.getMaxHealth() >= pet.getMaxHealth() * 2.0f) {
                                float sisuWeight = scaledAmount(0.10f, 0.32f,
                                    Math.max(intensity, finishFactor)) * (0.75f + 0.25f * closeness);
                                if (sisuWeight > 0f) {
                                    pc.pushEmotion(PetComponent.Emotion.SISU, sisuWeight);
                                }
                            }
                        }
                    }
                }
            }
        }

        EntityType<?> victimType = victim.getType();
        String victimTypeName = victimType != null ? victimType.toString() : "unknown";
        Petsplus.LOGGER.debug("Owner {} dealt {} damage to {}, victim at {}% health",
            owner.getName().getString(), modifiedDamage, victimTypeName, victimHealthPct * 100);
    }
    
    private static void handleProjectileDamage(PlayerEntity shooter, LivingEntity target, float damage, PersistentProjectileEntity projectile) {
        Map<String, Object> payload = new HashMap<>();
        Identifier projectileId = null;
        String rawType = null;
        EntityType<?> projectileType = projectile.getType();
        if (projectileType != null) {
            projectileId = Registries.ENTITY_TYPE.getId(projectileType);
            rawType = projectileType.toString();
        }
        populateProjectileMetadata(payload, projectileId, rawType);
        payload.put("projectile_entity", projectile);
        payload.put("victim", target);
        payload.put("damage", (double) damage);
        boolean wasCrit = projectile.isCritical();
        payload.put("projectile_critical", wasCrit);

        triggerAbilitiesForOwner(shooter, "owner_shot_projectile", payload);

        if (wasCrit) {
            triggerAbilitiesForOwner(shooter, "owner_projectile_crit");

            if (shooter instanceof ServerPlayerEntity serverOwner) {
                broadcastOwnerCriticalHit(serverOwner, target, damage);
            }
        }

        // Handle as regular damage too
        handleOwnerDealtDamage(shooter, target, damage);
    }

    private static void broadcastOwnerCriticalHit(ServerPlayerEntity owner, LivingEntity target, float damage) {
        if (owner == null || target == null || owner.getEntityWorld() == null) {
            return;
        }

        List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
        if (swarm.isEmpty()) {
            return;
        }

        Vec3d ownerPos = owner.getEntityPos();
        double radiusSq = 32.0 * 32.0;
        float targetMaxHealth = Math.max(1f, target.getMaxHealth());
        float intensity = damageIntensity(damage, targetMaxHealth);
        boolean bossVictim = TriggerConditions.isBossEntity(target);
        long now = owner.getEntityWorld().getTime();

        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pet == null || pc == null || !pet.isAlive()) {
                continue;
            }
            if (!withinRadius(entry, ownerPos, radiusSq)) {
                continue;
            }

            pc.setLastAttackTick(now);
            float closeness = 0.40f + 0.60f * proximityFactor(owner, pet, 32.0);
            float cheerfulWeight = scaledAmount(0.08f, 0.25f, intensity);
            float vigilWeight = scaledAmount(0.10f, 0.30f, intensity) * closeness;

            float petMaxHealth = Math.max(1f, pet.getMaxHealth());
            float petHealthRatio = MathHelper.clamp(pet.getHealth() / petMaxHealth, 0f, 1f);
            if (petHealthRatio > 0.8f) {
                float hopefulWeight = scaledAmount(0.08f, 0.25f, intensity) * closeness * petHealthRatio;
                if (hopefulWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.HOPEFUL, hopefulWeight);
                }
            }

            boolean formidable = bossVictim || targetMaxHealth >= petMaxHealth * 1.5f;
            if (formidable) {
                float prideWeight = scaledAmount(0.08f, 0.26f, intensity) * (0.7f + 0.3f * closeness);
                if (prideWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.PRIDE, prideWeight);
                }
            }

            if (cheerfulWeight > 0f) {
                pc.pushEmotion(PetComponent.Emotion.CHEERFUL, cheerfulWeight);
            }
            if (vigilWeight > 0f) {
                pc.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, vigilWeight);
            }
        }
    }

    private static boolean isCriticalMeleeHit(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (player.getAttackCooldownProgress(0.5f) < 1.0f) {
            return false;
        }
        if (player.fallDistance <= 0.0f) {
            return false;
        }
        if (player.isOnGround() || player.isClimbing()) {
            return false;
        }
        if (player.isTouchingWater() || player.isInLava()) {
            return false;
        }
        if (player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return false;
        }
        if (player.hasVehicle() || player.isSprinting()) {
            return false;
        }
        return true;
    }

    static void populateProjectileMetadata(Map<String, Object> payload, Identifier projectileId, @Nullable String rawType) {
        String projectileTypeString = "unknown";
        if (projectileId != null) {
            projectileTypeString = projectileId.toString();
            payload.put("projectile_identifier", projectileId);
            payload.put("projectile_type_no_namespace", projectileId.getPath());
        } else if (rawType != null && !rawType.isEmpty()) {
            String sanitized = sanitizeProjectileTypeString(rawType);
            if (!sanitized.isEmpty()) {
                projectileTypeString = sanitized;
                Identifier parsed = Identifier.tryParse(sanitized);
                if (parsed != null) {
                    payload.put("projectile_identifier", parsed);
                    payload.put("projectile_type_no_namespace", parsed.getPath());
                } else {
                    int colonIndex = sanitized.indexOf(':');
                    if (colonIndex >= 0 && colonIndex + 1 < sanitized.length()) {
                        payload.put("projectile_type_no_namespace", sanitized.substring(colonIndex + 1).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        String normalized = projectileTypeString.toLowerCase(Locale.ROOT);
        payload.put("projectile_type", normalized);
        payload.put("projectile_type_id", normalized);
        payload.put("projectile_id", normalized);
        if (!payload.containsKey("projectile_type_no_namespace")) {
            int colonIndex = normalized.indexOf(':');
            if (colonIndex >= 0 && colonIndex + 1 < normalized.length()) {
                payload.put("projectile_type_no_namespace", normalized.substring(colonIndex + 1));
            } else if (!"unknown".equals(normalized)) {
                payload.put("projectile_type_no_namespace", normalized);
            }
        }
    }

    private static String sanitizeProjectileTypeString(String rawType) {
        String trimmed = rawType.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex <= 0) {
            return trimmed;
        }

        int start = colonIndex - 1;
        while (start >= 0 && isIdentifierChar(trimmed.charAt(start))) {
            start--;
        }
        start++;

        int end = colonIndex + 1;
        while (end < trimmed.length() && isIdentifierChar(trimmed.charAt(end))) {
            end++;
        }

        if (start < end) {
            return trimmed.substring(start, end);
        }
        return trimmed;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/';
    }

    private static boolean isFallDamage(DamageSource damageSource) {
        return damageSource != null && damageSource.isOf(DamageTypes.FALL);
    }
    
    private static void applyAttackRiders(PlayerEntity owner, LivingEntity victim, OwnerCombatState combatState) {
        // Apply on-hit effects using the new attack rider system
        woflo.petsplus.combat.OwnerAttackRider.applyOnHitEffects(owner, victim, 0, null);
        
        // Clear expired riders
        woflo.petsplus.combat.OwnerAttackRider.clearExpiredRiders(owner);
    }
    
    public static void triggerAbilitiesForOwner(PlayerEntity owner, String eventType) {
        triggerNearbyPetAbilities(owner, eventType, null);
    }

    /**
     * Trigger abilities for an owner with additional context data.
     */
    public static void triggerAbilitiesForOwner(PlayerEntity owner, String eventType, java.util.Map<String, Object> data) {
        triggerNearbyPetAbilities(owner, eventType, data);
    }

    private static void triggerNearbyPetAbilities(PlayerEntity owner, String eventType) {
        triggerNearbyPetAbilities(owner, eventType, null);
    }

    private static void triggerNearbyPetAbilities(PlayerEntity owner, TriggerContext context) {
        if (context == null) {
            return;
        }
        Map<String, Object> data = context.getEventData().isEmpty()
            ? null
            : new HashMap<>(context.getEventData());
        triggerNearbyPetAbilities(owner, context.getEventType(), data);
    }

    private static void triggerNearbyPetAbilities(PlayerEntity owner,
                                                  String eventType,
                                                  @Nullable Map<String, Object> data) {
        // Server-side validation to prevent client-side execution
        if (owner == null) {
            return;
        }

        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (eventType == null || eventType.isEmpty()) {
            return;
        }
        
        // Additional validation: ensure the player is still valid and in the world
        if (!serverOwner.isAlive() || serverOwner.isRemoved()) {
            return;
        }
        
        Map<String, Object> payload = (data == null || data.isEmpty())
            ? null
            : new HashMap<>(data);
        StateManager.forWorld(serverWorld).fireAbilityTrigger(serverOwner, eventType, payload);
    }

    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        Entity attacker = damageSource.getAttacker();
        PlayerEntity owner = null;

        if (attacker instanceof PlayerEntity player) {
            owner = player;
        } else if (attacker instanceof MobEntity mobAttacker) {
            PetComponent petComponent = PetComponent.get(mobAttacker);
            if (petComponent != null) {
                owner = petComponent.getOwner();
            }
        }

        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return;
        }
        if (!(entity.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("victim", entity);
        payload.put("victim_max_health", (double) Math.max(0f, entity.getMaxHealth()));
        payload.put("victim_was_hostile", entity instanceof HostileEntity);

        ExecutionKillSummary executionSummary = StrikerExecution.consumeExecutionKillData(owner, entity);
        StrikerHuntManager huntManager = StrikerHuntManager.getInstance();
        if (executionSummary != null) {
            payload.put("execution_kill", true);
            payload.put("execution_threshold_pct", (double) executionSummary.thresholdPct());
            payload.put("execution_momentum_stacks", executionSummary.momentumStacks());
            payload.put("execution_momentum_fill", (double) executionSummary.momentumFill());
            payload.put("striker_level", executionSummary.strikerLevel());
        } else {
            payload.put("execution_kill", false);
        }

        boolean finisherConsumed = StrikerExecution.consumeFinisherKillFlag(owner, entity);
        payload.put("finisher_mark_consumed", finisherConsumed);

        if (executionSummary != null) {
            huntManager.onExecutionKill(serverOwner, entity, executionSummary, finisherConsumed);
        } else if (finisherConsumed) {
            huntManager.onFinisherSpent(serverOwner, entity);
        } else {
            huntManager.onOwnerKill(serverOwner);
        }

        // Check if entity is wild animal - nearby pets learn from observation
        if (entity instanceof MobEntity mobEntity) {
            PetComponent victimPc = PetComponent.get(mobEntity);
            if (victimPc == null) {
                // Wild animal - notify nearby pets to learn
                List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(serverOwner);
                Vec3d entityPos = entity.getEntityPos();
                double observeRadiusSq = 16.0 * 16.0; // Pets within 16 blocks observe
                
                for (PetSwarmIndex.SwarmEntry entry : swarm) {
                    MobEntity pet = entry.pet();
                    if (pet != null && pet.isAlive() && pet.squaredDistanceTo(entityPos) <= observeRadiusSq) {
                        RelationshipEventHandler.onPetObservedOwnerHunt(pet, entity);
                    }
                }
            }
        }
        
        TriggerContext context = new TriggerContext(serverWorld, null, serverOwner, "owner_killed_entity");
        payload.forEach(context::withData);

        triggerNearbyPetAbilities(serverOwner, context);
        
        // Ultra-rare mood triggers
        triggerUltraRareMoods(serverOwner, entity, serverWorld);
    }
    
    /**
     * Trigger ultra-rare moods based on exceptional combat events
     */
    private static void triggerUltraRareMoods(ServerPlayerEntity owner, LivingEntity victim, ServerWorld world) {
        // 1. ECHOED RESONANCE - Warden kill + Deep Dark exposure
        if (woflo.petsplus.util.TriggerConditions.isBossEntity(victim) && 
            victim.getType() == net.minecraft.entity.EntityType.WARDEN) {
            
            List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
            if (!swarm.isEmpty()) {
                Vec3d ownerPos = owner.getEntityPos();
                double radiusSq = ULTRA_RARE_TRIGGER_RADIUS * ULTRA_RARE_TRIGGER_RADIUS;
                int triggeredCount = 0;
                
                // Check if owner has Darkness effect (Warden scream synergy)
                boolean ownerHasDarkness = owner.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS);
                
                for (PetSwarmIndex.SwarmEntry entry : swarm) {
                    MobEntity pet = entry.pet();
                    PetComponent pc = entry.component();
                    if (pet == null || pc == null || !pet.isAlive()) {
                        continue;
                    }
                    if (!withinRadius(entry, ownerPos, radiusSq)) {
                        continue;
                    }
                    
                    // Base Warden kill intensity
                    float baseIntensity = 0.85f;
                    
                    // +30% if owner is afflicted by Darkness (shared sensory deprivation)
                    if (ownerHasDarkness) {
                        baseIntensity *= 1.3f;
                    }
                    
                    // Check Deep Dark exposure duration (O(1) state lookup)
                    long deepDarkTime = pc.getStateData("deep_dark_duration", Long.class, 0L);
                    if (deepDarkTime > DEEP_DARK_VETERAN_THRESHOLD) {
                        baseIntensity *= 1.2f; // +20% for veteran of the depths
                    }
                    
                    // Check sculk exposure buildup (O(1) state lookup)
                    int sculkExposure = pc.getStateData("sculk_exposure_level", Integer.class, 0);
                    float sculkBonus = Math.min(0.4f, sculkExposure * 0.05f); // Max +40% at 8 exposures
                    
                    pc.pushEmotion(PetComponent.Emotion.ECHOED_RESONANCE, Math.min(1.0f, baseIntensity + sculkBonus));
                    pc.pushEmotion(PetComponent.Emotion.SISU, 0.40f + (ownerHasDarkness ? 0.20f : 0f));
                    pc.pushEmotion(PetComponent.Emotion.PRIDE, 0.35f + (deepDarkTime > DEEP_DARK_VETERAN_THRESHOLD ? 0.25f : 0f));
                    pc.pushEmotion(PetComponent.Emotion.RELIEF, 0.50f + (ownerHasDarkness ? 0.30f : 0f));
                    
                    // Clear sculk buildup after cathartic Warden victory
                    pc.setStateData("sculk_exposure_level", 0);
                    
                    triggeredCount++;
                }
                
                if (triggeredCount > 0) {
                    Petsplus.LOGGER.debug("Echoed Resonance triggered for {} pets after Warden kill by {} (darkness: {}, deep exposure boost active)", 
                        triggeredCount, owner.getName().getString(), ownerHasDarkness);
                }
            }
        }
        
        // 2. PACK SPIRIT - Synchronized combat participation
        if (victim instanceof HostileEntity && victim.getMaxHealth() >= 20.0f) {
            // Retrieve coordinated attack data with atomic read (fixes race condition)
            Map<java.util.UUID, Long> attackers = COORDINATED_ATTACKS.get(victim.getUuid());
            if (attackers == null) {
                return; // No tracked attacks for this victim
            }
            
            // Filter to recent participants (within coordination window)
            long currentTime = world.getTime();
            List<java.util.UUID> recentAttackers = attackers.entrySet().stream()
                .filter(e -> currentTime - e.getValue() < PACK_COORDINATION_WINDOW)
                .map(Map.Entry::getKey)
                .toList();
            
            if (recentAttackers.size() >= PACK_SPIRIT_MIN_PETS) {
                // Find actual pet entities that participated
                List<MobEntity> participatingPets = world.getEntitiesByClass(MobEntity.class,
                    owner.getBoundingBox().expand(ULTRA_RARE_TRIGGER_RADIUS),
                    mob -> {
                        PetComponent pc = PetComponent.get(mob);
                        if (pc == null || !pc.isOwnedBy(owner)) return false;
                        return recentAttackers.contains(mob.getUuid());
                    });
                
                if (participatingPets.size() >= PACK_SPIRIT_MIN_PETS) {
                    // True coordinated attack - multiple pets damaged same target
                    float packSize = Math.min(participatingPets.size(), 6);
                    float intensityScale = 0.6f + (packSize / 10.0f); // 0.6 at 3, 1.2 at 6
                    
                    // Check for role diversity bonus (O(1) per pet)
                    java.util.Set<Identifier> uniqueRoles = new java.util.HashSet<>();
                    for (MobEntity pet : participatingPets) {
                        PetComponent pc = PetComponent.get(pet);
                        if (pc != null) {
                            Identifier roleId = pc.getRoleId();
                            if (roleId != null) {
                            uniqueRoles.add(roleId);
                        }
                    }
                }
                float diversityBonus = uniqueRoles.size() >= PACK_SPIRIT_ROLE_DIVERSITY_MIN ? 0.25f : 0f;                    for (MobEntity pet : participatingPets) {
                        PetComponent pc = PetComponent.get(pet);
                        if (pc != null) {
                            float finalIntensity = intensityScale + diversityBonus;
                            pc.pushEmotion(PetComponent.Emotion.PACK_SPIRIT, 0.75f * finalIntensity);
                            pc.pushEmotion(PetComponent.Emotion.UBUNTU, 0.50f * finalIntensity);
                            pc.pushEmotion(PetComponent.Emotion.PRIDE, 0.45f * finalIntensity);
                            pc.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, 0.35f * finalIntensity);
                            
                            // Increment pack cohesion counter (long-term bond)
                            int cohesion = pc.getStateData("pack_cohesion_victories", Integer.class, 0);
                            pc.setStateData("pack_cohesion_victories", cohesion + 1);
                        }
                    }
                    
                    Petsplus.LOGGER.debug("Pack Spirit triggered for {} coordinated pets after defeating {} (roles: {}, health: {})",
                        participatingPets.size(), victim.getType().getName().getString(), uniqueRoles.size(), victim.getMaxHealth());
                }
            }
            
            // Clean up attack tracking with monitoring
            Map<java.util.UUID, Long> removed = COORDINATED_ATTACKS.remove(victim.getUuid());
            if (removed != null && removed.size() > 10) {
                Petsplus.LOGGER.debug("Cleaned {} attack entries for defeated {}", removed.size(), victim.getType().getName().getString());
            }
        }
    }

    /**
     * Handle when a pet takes damage - triggers fear/anger emotions based on context
     */
    private static void handlePetDamageReceived(MobEntity pet, PetComponent petComponent, DamageSource damageSource, float amount) {
        // Ensure thread-safe access to pet state
        synchronized (pet) {
            long now = pet.getEntityWorld().getTime();
            petComponent.setLastAttackTick(now);

        float maxHealth = pet.getMaxHealth();
        float damageIntensity = damageIntensity(amount, maxHealth);
        float healthAfter = pet.getHealth() - amount;
        float healthAfterPercent = maxHealth > 0f ? MathHelper.clamp(healthAfter / maxHealth, 0f, 1f) : 0f;
        float lowHealthFactor = missingHealthFactor(healthAfterPercent, 0.5f);
        float criticalFactor = missingHealthFactor(healthAfterPercent, 0.25f);

        Entity attacker = damageSource.getAttacker();
        PlayerEntity owner = petComponent.getOwner();
        boolean petIsCursed = petComponent.hasRole(PetRoleType.CURSED_ONE);

        float startleWeight = scaledAmount(0.02f, 0.11f, damageIntensity);
        if (startleWeight > 0f) {
            petComponent.pushEmotion(PetComponent.Emotion.STARTLE, startleWeight);
        }

        if (attacker instanceof PlayerEntity playerAttacker) {
            if (owner != null && playerAttacker.equals(owner)) {
                // Relationship tracking - owner attacked their own pet
                RelationshipEventHandler.onOwnerAttackedPet(pet, playerAttacker, amount);
                
                // Check cooldown to prevent emotion spam from rapid punching
                // Use atomic check-and-set to prevent race condition in emotion processing
                long lastEmotionTime = petComponent.getLastAttackTick();
                if (now - lastEmotionTime < 40) { // 2 second cooldown (40 ticks)
                    return; // Skip emotion processing if too recent
                }
                // Update the last attack tick atomically to prevent race conditions
                petComponent.setLastAttackTick(now);
                // Realistic emotional responses to being hit by owner
                if (damageIntensity < 0.15f && petIsCursed) {
                    // Only very light damage on cursed pets counts as "rough housing"
                    float playfulness = scaledAmount(0.02f, 0.08f, damageIntensity);
                    petComponent.pushEmotion(PetComponent.Emotion.KEFI, playfulness);
                } else {
                    // Being hit by owner is traumatic and confusing
                    float confusion = scaledAmount(0.08f, 0.20f, damageIntensity);
                    float distress = scaledAmount(0.06f, 0.16f, damageIntensity + (lowHealthFactor * 0.4f));
                    float worry = scaledAmount(0.04f, 0.12f, damageIntensity + (criticalFactor * 0.6f));
                    float fear = scaledAmount(0.05f, 0.14f, damageIntensity + (lowHealthFactor * 0.5f));

                    // Extra distress when pet is at low health
                    float healthPercent = pet.getHealth() / pet.getMaxHealth();
                    if (healthPercent <= 0.5f) {
                        float despair = scaledAmount(0.05f, 0.12f, (0.5f - healthPercent) * 2.0f);
                        petComponent.pushEmotion(PetComponent.Emotion.ENNUI, despair);
                        petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, despair * 0.8f);
                    }

                    petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, confusion);
                    petComponent.pushEmotion(PetComponent.Emotion.ENNUI, distress);
                    petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, worry);
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, fear);
                }
                Petsplus.LOGGER.debug("Pet {} hurt by owner, pushed owner-related emotions", pet.getName().getString());
            } else {
                // Relationship tracking - non-owner player attacked pet
                RelationshipEventHandler.onPetAttackedByOther(pet, playerAttacker, amount);
                
                float panic = scaledAmount(0.04f, 0.11f, damageIntensity + (lowHealthFactor * 0.4f));
                float defiance = scaledAmount(0.03f, 0.09f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, panic);
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, defiance);
                if (owner != null) {
                    float closeness = 0.40f + 0.60f * proximityFactor(owner, pet, 16.0);
                    float protective = scaledAmount(0.12f, 0.30f, damageIntensity + (lowHealthFactor * 0.3f)) * closeness;
                    petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, protective);
                }
                Petsplus.LOGGER.debug("Pet {} hurt by hostile player, pushed defensive emotions", pet.getName().getString());
            }
        } else if (attacker instanceof MobEntity mobAttacker) {
            PetComponent attackerPetComponent = PetComponent.get(mobAttacker);
            if (attackerPetComponent != null && owner != null && attackerPetComponent.isOwnedBy(owner)) {
                float confusion = scaledAmount(0.12f, 0.35f, damageIntensity);
                float resentment = scaledAmount(0.06f, 0.25f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, confusion);
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, resentment);
                Petsplus.LOGGER.debug("Pet {} hurt by friendly pet, pushed unease", pet.getName().getString());
            } else if (attackerPetComponent != null) {
                // Cross-owner pet attack - track relationship between two different owners' pets
                RelationshipEventHandler.onPetAttackedByRivalPet(pet, mobAttacker, amount);
                
                float fear = scaledAmount(0.08f, 0.22f, damageIntensity + (lowHealthFactor * 0.3f));
                float caution = scaledAmount(0.10f, 0.28f, Math.max(damageIntensity, lowHealthFactor));
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, fear);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, caution);
                if (owner != null) {
                    float closeness = 0.35f + 0.65f * proximityFactor(owner, pet, 16.0);
                    float protective = scaledAmount(0.12f, 0.28f, damageIntensity) * closeness;
                    petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, protective);
                }
                Petsplus.LOGGER.debug("Pet {} hurt by rival pet, pushed defensive emotions", pet.getName().getString());
            } else {
                // Check if this is a wild animal (no pet component, no owner)
                boolean isWildAnimal = !mobAttacker.isPersistent() || 
                    (mobAttacker instanceof net.minecraft.entity.passive.AnimalEntity);
                
                if (isWildAnimal && damageIntensity > 0.15f) {
                    // Significant damage from wild animal - track species memory
                    RelationshipEventHandler.onPetAttackedByWildAnimal(pet, mobAttacker, amount);
                }
                
                // Hostile mob damage -> fear and caution but more balanced
                float dread = scaledAmount(0.10f, 0.28f, damageIntensity + (lowHealthFactor * 0.3f));
                float caution = scaledAmount(0.08f, 0.25f, Math.max(damageIntensity, lowHealthFactor));
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, dread);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, caution);
                if (mobAttacker.getMaxHealth() > pet.getMaxHealth() * 1.5f) {
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.08f, 0.30f, damageIntensity));
                    petComponent.pushEmotion(PetComponent.Emotion.STOIC, scaledAmount(0.05f, 0.25f, damageIntensity));
                }
                if (owner != null) {
                    float closeness = 0.30f + 0.70f * proximityFactor(owner, pet, 16.0);
                    float protective = scaledAmount(0.10f, 0.26f, damageIntensity) * closeness;
                    petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, protective);
                }
                Petsplus.LOGGER.debug("Pet {} hurt by hostile mob, pushed fear emotions", pet.getName().getString());
            }
        } else {
            if (damageSource.isOf(net.minecraft.entity.damage.DamageTypes.FALL)) {
                // Fall damage -> pain and momentary disorientation
                petComponent.pushEmotion(PetComponent.Emotion.STARTLE, scaledAmount(0.08f, 0.18f, damageIntensity));
                petComponent.pushEmotion(PetComponent.Emotion.GAMAN, scaledAmount(0.04f, 0.12f, damageIntensity));
            } else if (damageSource.isOf(net.minecraft.entity.damage.DamageTypes.IN_FIRE) ||
                      damageSource.isOf(net.minecraft.entity.damage.DamageTypes.ON_FIRE)) {
                // Fire damage -> panic and fear, but more realistic levels
                float panic = scaledAmount(0.12f, 0.30f, damageIntensity + (lowHealthFactor * 0.3f));
                float dread = scaledAmount(0.08f, 0.25f, Math.max(damageIntensity, lowHealthFactor));
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, panic);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, dread);
            } else {
                // Generic environmental damage -> caution and endurance
                float caution = scaledAmount(0.06f, 0.18f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, caution);
                petComponent.pushEmotion(PetComponent.Emotion.GAMAN, scaledAmount(0.04f, 0.15f, Math.max(damageIntensity, lowHealthFactor)));
            }
            Petsplus.LOGGER.debug("Pet {} hurt by environment, pushed cautious emotions", pet.getName().getString());
        }

        // Chip damage accumulation → restlessness buildup
        applyChipDamageRestlessness(pet, petComponent, amount, damageIntensity, attacker, owner, damageSource);

        if (lowHealthFactor > 0f) {
            float panic = scaledAmount(0.12f, 0.45f, lowHealthFactor);
            float dread = scaledAmount(0.08f, 0.35f, lowHealthFactor);
            petComponent.pushEmotion(PetComponent.Emotion.ANGST, panic);
            petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, dread);
            
            // Track that pet entered low health state for potential recovery emotions
            petComponent.setStateData(PetComponent.StateKeys.HEALTH_LAST_LOW_TICK, now);
        }

        if (criticalFactor > 0f) {
            float desperation = scaledAmount(0.10f, 0.30f, criticalFactor);
            petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, desperation * 0.7f);
            petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, desperation);
        }
        } // End synchronized block for pet state access
    }

    /**
     * Handle when a pet deals damage - triggers aggressive/triumphant emotions based on context
     */
    private static void handlePetDealtDamage(MobEntity pet, PetComponent petComponent, LivingEntity victim, float damage) {
        if (damage > 0.0F && pet.getEntityWorld() instanceof ServerWorld) {
            XpEventHandler.trackPetCombat(pet);
        }
        float victimMaxHealth = Math.max(1f, victim.getMaxHealth());
        float damageIntensity = damageIntensity(damage, victimMaxHealth);
        PlayerEntity owner = petComponent.getOwner();

        // Ensure thread-safe access to pet state
        synchronized (pet) {
            long now = pet.getEntityWorld().getTime();
            petComponent.setLastAttackTick(now);

        float petHealthPercent = pet.getMaxHealth() > 0f ? MathHelper.clamp(pet.getHealth() / pet.getMaxHealth(), 0f, 1f) : 1f;
        float lowHealthFactor = missingHealthFactor(petHealthPercent, 0.5f);
        float criticalFactor = missingHealthFactor(petHealthPercent, 0.25f);

        // Check for health recovery: if pet was low and has recovered, apply stoic/endurance
        checkHealthRecovery(pet, petComponent, petHealthPercent, now);

        if (victim instanceof PlayerEntity playerVictim) {
            if (owner != null && playerVictim.equals(owner)) {
                // Relationship tracking - pet attacked owner (accidental or otherwise)
                RelationshipEventHandler.onPetAttackedOwner(pet, playerVictim, damage);
                
                float guilt = scaledAmount(0.32f, 0.50f, damageIntensity);
                float panic = scaledAmount(0.20f, 0.45f, damageIntensity + criticalFactor);
                float dread = scaledAmount(0.16f, 0.35f, damageIntensity);
                float remorse = scaledAmount(0.18f, 0.35f, damageIntensity + (lowHealthFactor * 0.4f));
                petComponent.pushEmotion(PetComponent.Emotion.REGRET, guilt);
                petComponent.pushEmotion(PetComponent.Emotion.HIRAETH, remorse);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, panic);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, dread);
                if (victim.getHealth() - damage <= 0) {
                    handlePetKill(pet, petComponent, victim);
                }
                Petsplus.LOGGER.debug("Pet {} accidentally hurt owner, pushed guilt emotions", pet.getName().getString());
                return;
            }
            
            // Check if pet is helping another player defend against this hostile player
            // Look for nearby non-owner players who might be the beneficiary
            if (owner != null && pet.getEntityWorld() instanceof ServerWorld serverWorld) {
                List<ServerPlayerEntity> nearbyPlayersRaw = serverWorld.getPlayers(p -> 
                    p != owner && 
                    p != playerVictim && 
                    !p.isSpectator() && 
                    p.squaredDistanceTo(pet) <= 16.0 * 16.0
                );
                List<PlayerEntity> nearbyPlayers = new java.util.ArrayList<>(nearbyPlayersRaw);
                if (!nearbyPlayers.isEmpty()) {
                    // Pet is fighting alongside another player - track relationship with them
                    for (PlayerEntity ally : nearbyPlayers) {
                        RelationshipEventHandler.onPetCombatAlly(pet, ally, playerVictim);
                    }
                }
            }

            float closeness = owner != null ? 0.40f + 0.60f * proximityFactor(owner, pet, 16.0) : 0.75f;
            float guardianResolve = scaledAmount(0.18f, 0.50f, damageIntensity) * closeness;
            petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, guardianResolve);
            
            // Health-based emotions: hopeful at high health, determined/stoic when hurt
            float healthRatio = pet.getHealth() / pet.getMaxHealth();
            if (healthRatio > 0.75f) {
                float hopeful = scaledAmount(0.08f, 0.25f, damageIntensity) * closeness * healthRatio;
                petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, hopeful);
            } else if (healthRatio < 0.5f) {
                float determined = scaledAmount(0.10f, 0.30f, damageIntensity) * closeness * (1f - healthRatio);
                petComponent.pushEmotion(PetComponent.Emotion.STOIC, determined);
            }
            
            // Defending against hostile players should build protective fervor, not frustration
            float fervor = scaledAmount(0.08f, 0.25f, damageIntensity + (lowHealthFactor * 0.2f));
            petComponent.pushEmotion(PetComponent.Emotion.KEFI, fervor);

            if (criticalFactor > 0f) {
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.18f, 0.40f, criticalFactor));
            } else if (lowHealthFactor > 0f) {
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.12f, 0.35f, lowHealthFactor));
            }

            if (owner != null && owner.getHealth() / owner.getMaxHealth() < 0.5f) {
                float stoic = scaledAmount(0.06f, 0.30f, damageIntensity) * closeness;
                petComponent.pushEmotion(PetComponent.Emotion.STOIC, stoic);
            }
            Petsplus.LOGGER.debug("Pet {} defending against hostile player, health-adjusted emotions", pet.getName().getString());
        } else if (victim instanceof MobEntity mobVictim) {
            PetComponent victimPetComponent = PetComponent.get(mobVictim);
            if (victimPetComponent != null && owner != null && victimPetComponent.isOwnedBy(owner)) {
                // Relationship tracking - pet attacked ally pet
                RelationshipEventHandler.onPetAttackedAlly(pet, mobVictim);
                
                float guilt = scaledAmount(0.26f, 0.45f, damageIntensity);
                float worry = scaledAmount(0.16f, 0.35f, damageIntensity + (lowHealthFactor * 0.3f));
                petComponent.pushEmotion(PetComponent.Emotion.REGRET, guilt);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, worry);
                Petsplus.LOGGER.debug("Pet {} fighting friendly pet, pushed distress emotions", pet.getName().getString());
            } else if (victimPetComponent != null) {
                // Cross-owner pet combat - track rivalry/dominance
                RelationshipEventHandler.onPetAttackedRivalPet(pet, mobVictim, damage);
                
                float confidence = scaledAmount(0.10f, 0.25f, damageIntensity);
                float rivalry = scaledAmount(0.08f, 0.22f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.STOIC, confidence);
                petComponent.pushEmotion(PetComponent.Emotion.KEFI, rivalry);
                if (owner != null) {
                    float closeness = 0.4f + 0.6f * proximityFactor(owner, pet, 16.0);
                    float protective = scaledAmount(0.12f, 0.30f, damageIntensity) * closeness;
                    petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, protective);
                }
                Petsplus.LOGGER.debug("Pet {} fighting rival pet, pushed competitive emotions", pet.getName().getString());
            } else {
                float protective = owner != null
                    ? scaledAmount(0.12f, 0.35f, damageIntensity) * (0.5f + 0.5f * proximityFactor(owner, pet, 16.0))
                    : scaledAmount(0.10f, 0.30f, damageIntensity);
                
                // Health-based emotions when attacking mobs
                float healthRatio = pet.getHealth() / pet.getMaxHealth();
                if (healthRatio > 0.8f) {
                    float hopeful = scaledAmount(0.06f, 0.20f, damageIntensity) * healthRatio;
                    petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, hopeful);
                } else if (healthRatio < 0.4f) {
                    float grim = scaledAmount(0.08f, 0.25f, damageIntensity) * (1f - healthRatio);
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, grim);
                }
                
                petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, protective);

                if (criticalFactor > 0f) {
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.16f, 0.40f, criticalFactor));
                } else if (lowHealthFactor > 0f) {
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.10f, 0.35f, lowHealthFactor));
                }

                if (mobVictim.getMaxHealth() > pet.getMaxHealth()) {
                    petComponent.pushEmotion(PetComponent.Emotion.STOIC, scaledAmount(0.06f, 0.30f, damageIntensity));
                }

                if (mobVictim instanceof HostileEntity) {
                    // Relationship tracking - combat ally against hostiles
                    if (owner != null) {
                        RelationshipEventHandler.onPetCombatAlly(pet, owner, mobVictim);
                    }
                    
                    // Fighting hostiles should build fervor/triumph, not frustration
                    float fervor = scaledAmount(0.08f, 0.25f, damageIntensity);
                    petComponent.pushEmotion(PetComponent.Emotion.KEFI, fervor);
                }

                if (mobVictim instanceof net.minecraft.entity.passive.AnimalEntity) {
                    if (owner == null || owner.squaredDistanceTo(pet) > 8 * 8) {
                        petComponent.pushEmotion(PetComponent.Emotion.REGRET, scaledAmount(0.08f, 0.25f, damageIntensity));
                    }
                    } // End synchronized block for pet state access
                }
                Petsplus.LOGGER.debug("Pet {} fighting hostile mob, pushed combat emotions", pet.getName().getString());
            }
        }

        if (damage > pet.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE) * 1.5f) {
            float celebration = scaledAmount(0.10f, 0.25f, damageIntensity);
            petComponent.pushEmotion(PetComponent.Emotion.CHEERFUL, celebration);
        }

        // Fire pet_dealt_damage trigger for abilities
        if (owner instanceof ServerPlayerEntity serverOwner && pet.getEntityWorld() instanceof ServerWorld serverWorld) {
            TriggerContext ctx = new TriggerContext(serverWorld, pet, serverOwner, "pet_dealt_damage")
                .withData("victim_hp_pct", (double) (victim.getHealth() / victimMaxHealth))
                .withData("damage", (double) damage)
                .withData("victim", victim);
            
            // Add Guardian-specific data if available (for abilities that trigger after redirect)
            Boolean hitReserveLimit = petComponent.getStateData("guardian_bulwark_hit_reserve_limit", Boolean.class);
            if (hitReserveLimit != null) {
                ctx.withData("guardian_recently_redirected", true)
                   .withData("guardian_hit_reserve_limit", hitReserveLimit);
            }
            
            AbilityManager.triggerAbilities(pet, ctx);
        }

        if (victim.getHealth() - damage <= 0) {
            handlePetKill(pet, petComponent, victim);
        }
    }

    /**
     * Handle when a pet kills an enemy - triggers triumphant emotions based on context
     */
    private static void handlePetKill(MobEntity pet, PetComponent petComponent, LivingEntity victim) {
        PlayerEntity owner = petComponent.getOwner();

        if (victim instanceof PlayerEntity playerVictim) {
            if (owner != null && playerVictim.equals(owner)) {
                petComponent.pushEmotion(PetComponent.Emotion.REGRET, 0.45f);
                petComponent.pushEmotion(PetComponent.Emotion.HIRAETH, 0.40f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.45f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, 0.35f);
                Petsplus.LOGGER.debug("Pet {} killed owner - extreme trauma emotions", pet.getName().getString());
                return;
            }

            float closeness = owner != null ? 0.40f + 0.60f * proximityFactor(owner, pet, 16.0) : 0.7f;
            float triumphScale = MathHelper.clamp(closeness, 0f, 1f);
            petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, scaledAmount(0.26f, 0.45f, triumphScale));
            petComponent.pushEmotion(PetComponent.Emotion.STOIC, scaledAmount(0.20f, 0.40f, triumphScale));
            petComponent.pushEmotion(PetComponent.Emotion.CHEERFUL, scaledAmount(0.14f, 0.30f, triumphScale));

            if (owner != null && closeness > 0.5f) {
                float healthRatio = pet.getHealth() / pet.getMaxHealth();
                if (healthRatio > 0.7f) {
                    petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, scaledAmount(0.08f, 0.20f, triumphScale) * healthRatio);
                }
            }
            Petsplus.LOGGER.debug("Pet {} killed hostile player - protective triumph", pet.getName().getString());
        } else if (victim instanceof MobEntity mobVictim) {
            PetComponent victimPetComponent = PetComponent.get(mobVictim);
            if (victimPetComponent != null && owner != null && victimPetComponent.isOwnedBy(owner)) {
                petComponent.pushEmotion(PetComponent.Emotion.REGRET, 0.40f);
                petComponent.pushEmotion(PetComponent.Emotion.HIRAETH, 0.35f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.35f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, 0.25f);
                Petsplus.LOGGER.debug("Pet {} killed friendly pet - guilt and trauma", pet.getName().getString());
                return;
            }
            
            // Check if victim is wild animal (no pet component)
            if (victimPetComponent == null) {
                // Wild animal kill - track species memory
                RelationshipEventHandler.onPetKilledWildAnimal(pet, mobVictim);
            }

            float relativeStrength = mobVictim.getMaxHealth() / Math.max(1f, pet.getMaxHealth());
            float triumphScale = MathHelper.clamp((relativeStrength - 0.25f) / 1.75f, 0f, 1f);
            float protectiveBase = scaledAmount(0.18f, 0.40f, triumphScale);
            float stoicBase = scaledAmount(0.16f, 0.35f, triumphScale);
            float cheerfulBase = scaledAmount(0.14f, 0.30f, triumphScale);
            boolean bossKill = TriggerConditions.isBossEntity(mobVictim);
            if (bossKill || mobVictim.getMaxHealth() >= pet.getMaxHealth() * 1.5f) {
                float prideScale = MathHelper.clamp(relativeStrength / 2.0f, 0f, 1f);
                petComponent.pushEmotion(PetComponent.Emotion.PRIDE,
                    scaledAmount(0.22f, 0.48f, Math.max(prideScale, triumphScale)));
            }

            petComponent.pushEmotion(PetComponent.Emotion.CHEERFUL, cheerfulBase);
            petComponent.pushEmotion(PetComponent.Emotion.STOIC, stoicBase);
            petComponent.pushEmotion(PetComponent.Emotion.GUARDIAN_VIGIL, protectiveBase);

            if (mobVictim instanceof HostileEntity) {
                float healthRatio = pet.getHealth() / pet.getMaxHealth();
                if (healthRatio > 0.75f) {
                    petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, scaledAmount(0.06f, 0.18f, triumphScale) * healthRatio);
                } else if (healthRatio < 0.5f) {
                    petComponent.pushEmotion(PetComponent.Emotion.STOIC, scaledAmount(0.08f, 0.22f, triumphScale) * (1f - healthRatio));
                }
            } else if (mobVictim instanceof net.minecraft.entity.passive.AnimalEntity) {
                if (owner == null || owner.squaredDistanceTo(pet) > 8 * 8) {
                    petComponent.pushEmotion(PetComponent.Emotion.REGRET, scaledAmount(0.10f, 0.25f, 1f - triumphScale));
                }
            }

            Integer killCount = petComponent.getStateData("kill_count", Integer.class);
            if (killCount == null || killCount == 0) {
                petComponent.pushEmotion(PetComponent.Emotion.CHEERFUL, scaledAmount(0.10f, 0.20f, Math.max(triumphScale, 0.5f)));
                petComponent.setStateData("kill_count", 1);
            } else {
                petComponent.setStateData("kill_count", killCount + 1);
            }

            Petsplus.LOGGER.debug("Pet {} killed {}, pushed triumphant emotions",
                pet.getName().getString(), victim.getType().toString());
        }

        attemptOwnerAssistChain(pet, petComponent, victim);
    }

    private static void notifyPetsOfOwnerTarget(PlayerEntity owner, LivingEntity target, long now) {
        if (!(owner.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        // If the target is one of our own pets (custom or vanilla), do not broadcast
        if (target instanceof MobEntity targetMob) {
            PetComponent targetComponent = PetComponent.get(targetMob);
            if (targetComponent != null && targetComponent.isOwnedBy(owner)) {
                return;
            }
            if (targetMob instanceof TameableEntity tame) {
                LivingEntity tameOwner = tame.getOwner();
                if (tameOwner != null && tameOwner.equals(owner)) {
                    return;
                }
            }
        }

        List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
        if (swarm.isEmpty()) {
            return;
        }
        Vec3d ownerPos = owner.getEntityPos();
        double radiusSq = OWNER_ASSIST_BROADCAST_RADIUS * OWNER_ASSIST_BROADCAST_RADIUS;
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pet == null || pc == null || pet.equals(target) || pet.isRemoved() || !pet.isAlive()) {
                continue;
            }
            if (!withinRadius(entry, ownerPos, radiusSq)) {
                continue;
            }
            if (pet instanceof TameableEntity tameable && tameable.isSitting()) {
                continue;
            }

            if (OwnerAssistAttackGoal.isPetHesitating(pc, now) || OwnerAssistAttackGoal.isPetRegrouping(pc, now)) {
                OwnerAssistAttackGoal.clearAssistHesitation(pc);
                OwnerAssistAttackGoal.clearAssistRegroup(pc);
            }
            if (pc.getMoodStrength(PetComponent.Mood.AFRAID) > 0.96f) {
                OwnerAssistAttackGoal.markAssistHesitation(pc, now);
                continue;
            }
            pc.setLastAttackTick(now);

            // Extra safety: ensure this pet can target the entity (and skip friendly-fire)
            if (target instanceof MobEntity tMob) {
                PetComponent tComp = PetComponent.get(tMob);
                if (tComp != null && tComp.isOwnedBy(owner)) {
                    continue;
                }
                if (tMob instanceof TameableEntity tTame) {
                    LivingEntity tOwner = tTame.getOwner();
                    if (tOwner != null && tOwner.equals(owner)) {
                        continue;
                    }
                }
            }

            if (!pet.canTarget(target)) {
                continue;
            }

            if (pet.getTarget() != target) {
                pet.setTarget(target);
            }

            double distanceSq = pet.squaredDistanceTo(target);
            if (distanceSq > 9.0d || !pet.getNavigation().isFollowingPath()) {
                OwnerAssistAttackGoal.primeNavigationForAssist(pet, target);
            }
        }
    }

    private static void attemptOwnerAssistChain(MobEntity pet, PetComponent petComponent, LivingEntity defeatedTarget) {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null || !(owner.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return;
        }

        long now = serverWorld.getTime();

        if (!combatState.isAggroTarget(defeatedTarget.getUuid())) {
            // The pet finished off something other than the active owner target; ignore and keep the snapshot.
            return;
        }

        if (!combatState.canChainAssist(OWNER_ASSIST_CHAIN_LIMIT)) {
            handleAssistChainFinished(pet, petComponent, owner, combatState, now, true, true);
            return;
        }

        if (OwnerAssistAttackGoal.isPetRegrouping(petComponent, now)) {
            handleAssistChainFinished(pet, petComponent, owner, combatState, now, false, true);
            return;
        }

        if (pet instanceof net.minecraft.entity.passive.TameableEntity tameable && tameable.isSitting()) {
            handleAssistChainFinished(pet, petComponent, owner, combatState, now, false, false);
            return;
        }

        boolean petCanFly = canPetEngageAirborneTarget(pet, petComponent);
        double attackAttribute = pet.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        double petAttackDamage = Double.isNaN(attackAttribute) ? 0.0 : Math.max(0.0, attackAttribute);

        float fear = petComponent.getMoodStrength(PetComponent.Mood.AFRAID);
        float protective = petComponent.getMoodStrength(PetComponent.Mood.PROTECTIVE);
        float angry = petComponent.getMoodStrength(PetComponent.Mood.ANGRY);
        float restless = petComponent.getMoodStrength(PetComponent.Mood.RESTLESS);
        float passionate = petComponent.getMoodStrength(PetComponent.Mood.PASSIONATE);
        float focused = petComponent.getMoodStrength(PetComponent.Mood.FOCUSED);
        float sisu = petComponent.getMoodStrength(PetComponent.Mood.SISU);
        float calm = petComponent.getMoodStrength(PetComponent.Mood.CALM);
        float saudade = petComponent.getMoodStrength(PetComponent.Mood.SAUDADE);
        float yugen = petComponent.getMoodStrength(PetComponent.Mood.YUGEN);
        float bonded = petComponent.getMoodStrength(PetComponent.Mood.BONDED);
        if (fear > 0.90f) {
            OwnerAssistAttackGoal.markAssistHesitation(petComponent, now);
            handleAssistChainFinished(pet, petComponent, owner, combatState, now, false, true);
            if (owner != null) {
                pet.getNavigation().startMovingTo(owner, 1.05d);
            }
            return;
        }

        float ownerAggro = combatState.getActiveAggroAggression();
        float ownerUrgency = combatState.getActiveAggroUrgency();
        float positive = 0.45f * protective + 0.40f * angry + 0.30f * restless + 0.25f * passionate + 0.20f * sisu + 0.15f * focused + 0.15f * bonded;
        float negative = 0.55f * fear + 0.35f * calm + 0.25f * saudade + 0.20f * yugen;
        float baseDesire = MathHelper.clamp(positive + 0.35f * ownerAggro + 0.25f * ownerUrgency - negative + 0.12f, 0f, 1f);
        if (baseDesire < 0.35f) {
            handleAssistChainFinished(pet, petComponent, owner, combatState, now, false, false);
            return;
        }

        double range = Math.max(OWNER_ASSIST_CHAIN_RADIUS, pet.getWidth() + 2.0);
        var candidates = serverWorld.getEntitiesByClass(MobEntity.class,
            defeatedTarget.getBoundingBox().expand(range),
            mob -> {
                if (mob == null || mob.equals(pet) || mob.equals(defeatedTarget) || !mob.isAlive() || mob.isRemoved()) {
                    return false;
                }
                PetComponent candidateComponent = PetComponent.get(mob);
                if (candidateComponent != null && candidateComponent.isOwnedBy(owner)) {
                    return false;
                }
                return mob.isAttackable()
                    && isChainTargetViable(pet, mob, petComponent, owner, petCanFly, petAttackDamage);
            }
        );

        if (candidates.isEmpty()) {
            handleAssistChainFinished(pet, petComponent, owner, combatState, now, false, false);
            return;
        }

        MobEntity nextTarget = candidates.stream()
            .min(Comparator.comparingDouble(mob -> mob.squaredDistanceTo(defeatedTarget)))
            .orElse(null);

        if (nextTarget == null) {
            handleAssistChainFinished(pet, petComponent, owner, combatState, now, false, false);
            return;
        }

        boolean nextHostile = nextTarget instanceof HostileEntity;
        float aggression = MathHelper.clamp(baseDesire * 0.45f + ownerAggro * 0.55f + (nextHostile ? 0.10f : -0.08f), 0f, 1f);
        float urgency = MathHelper.clamp(ownerUrgency * 0.65f + baseDesire * 0.30f + (nextHostile ? 0.12f : 0.05f), 0f, 1f);
        combatState.rememberAggroTarget(nextTarget, now, OWNER_ASSIST_TARGET_TTL, false, aggression, urgency, nextHostile);
        combatState.incrementAssistChain(now);
        pet.setTarget(nextTarget);
        notifyPetsOfOwnerTarget(owner, nextTarget, now);
    }

    private static void handleAssistChainFinished(MobEntity pet, PetComponent petComponent, PlayerEntity owner,
        OwnerCombatState combatState, long now, boolean reachedLimit, boolean applyRegroup) {
        if (combatState != null) {
            combatState.clearAggroTarget();
            if (reachedLimit) {
                combatState.resetAssistChain(now);
            }
            if (combatState.getTimeSinceLastHit() > 60) {
                combatState.clearTempState(STATE_COMBAT_START_TICK);
                combatState.clearTempState(STATE_COMBAT_RELIEF_TICK);
            }
        }

        if (petComponent != null) {
            float closeness = owner != null ? 0.35f + 0.65f * proximityFactor(owner, pet, 16.0) : 0.7f;
            float reliefBase = scaledAmount(0.08f, 0.24f, closeness);
            long encounterStart = combatState != null ? combatState.getTempState(STATE_COMBAT_START_TICK) : 0L;
            long lastReliefWave = combatState != null ? combatState.getTempState(STATE_COMBAT_RELIEF_TICK) : 0L;
            if (encounterStart > 0 && now - encounterStart >= 600L
                && (combatState == null || now - lastReliefWave >= 200L)) {
                reliefBase += 0.18f;
                if (combatState != null) {
                    combatState.setTempState(STATE_COMBAT_RELIEF_TICK, now);
                }
            }
            petComponent.pushEmotion(PetComponent.Emotion.RELIEF, reliefBase);
            petComponent.pushEmotion(PetComponent.Emotion.LOYALTY, scaledAmount(0.10f, 0.26f, closeness));
            if (applyRegroup) {
                OwnerAssistAttackGoal.markAssistRegroup(petComponent, now);
            } else {
                OwnerAssistAttackGoal.clearAssistRegroup(petComponent);
                OwnerAssistAttackGoal.clearAssistHesitation(petComponent);
            }
        }

        if (owner != null && pet.isAlive()) {
            double distSq = pet.squaredDistanceTo(owner);
            if (distSq > 3.5d) {
                pet.getNavigation().startMovingTo(owner, 1.1d);
            }
        }
    }

    private static void maybeCommandIntercept(PlayerEntity owner, OwnerCombatState combatState, DamageSource damageSource, long now) {
        if (!(owner.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        LivingEntity attacker = null;
        Entity source = damageSource.getSource();
        Entity directAttacker = damageSource.getAttacker();
        boolean rangedThreat = false;

        if (source instanceof PersistentProjectileEntity projectile) {
            rangedThreat = true;
            if (projectile.getOwner() instanceof LivingEntity shooter) {
                attacker = shooter;
            }
        }

        if (attacker == null && directAttacker instanceof LivingEntity living) {
            attacker = living;
            rangedThreat = rangedThreat || owner.squaredDistanceTo(living) > 36.0d;
        }

        if (attacker == null || attacker.equals(owner)) {
            return;
        }

        double attackerDistanceSq = owner.squaredDistanceTo(attacker);
        if (!rangedThreat) {
            rangedThreat = attackerDistanceSq > 36.0d;
        }

        if (owner.isTeammate(attacker)) {
            return;
        }

        boolean attackerHostile = attacker instanceof HostileEntity;

        if (attacker instanceof MobEntity mobAttacker) {
            PetComponent attackerComponent = PetComponent.get(mobAttacker);
            if (attackerComponent != null && attackerComponent.isOwnedBy(owner)) {
                return;
            }

            if (mobAttacker instanceof TameableEntity tameable) {
                LivingEntity tameOwner = tameable.getOwner();
                if (tameOwner != null && tameOwner.equals(owner)) {
                    return;
                }
            }

            LivingEntity mobTarget = mobAttacker.getTarget();
            if (!rangedThreat && mobTarget != null && mobTarget.equals(owner)) {
                attackerHostile = true;
            }
        }

        if (!rangedThreat && !attackerHostile) {
            if (attacker instanceof PlayerEntity player) {
                if (player.isSpectator() || owner.isSpectator()) {
                    return;
                }
            } else if (attacker instanceof MobEntity mob) {
                LivingEntity mobTarget = mob.getTarget();
                if (mobTarget == null || !mobTarget.equals(owner)) {
                    return;
                }
            } else {
                return;
            }
        }

        LivingEntity finalAttacker = attacker;
        final boolean interceptAttackerHostile = attackerHostile;
        List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
        if (swarm.isEmpty()) {
            return;
        }
        Vec3d ownerPos = owner.getEntityPos();
        double radiusSq = 16.0 * 16.0;
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity pet = entry.pet();
            PetComponent pc = entry.component();
            if (pet == null || pc == null || pet.equals(finalAttacker) || !pet.isAlive() || pet.isRemoved()) {
                continue;
            }
            if (!withinRadius(entry, ownerPos, radiusSq)) {
                continue;
            }
            if (pet instanceof TameableEntity tameable && tameable.isSitting()) {
                continue;
            }
            long cooldownUntil = pc.getStateData("assist_intercept_cooldown", Long.class, 0L);
            if (cooldownUntil > now) {
                continue;
            }

            float protective = pc.getMoodStrength(PetComponent.Mood.PROTECTIVE);
            float focused = pc.getMoodStrength(PetComponent.Mood.FOCUSED);
            float afraid = pc.getMoodStrength(PetComponent.Mood.AFRAID);
            if (afraid > 0.7f) {
                continue;
            }
            if (protective < 0.2f && focused < 0.2f) {
                continue;
            }

            Vec3d intercept = computeInterceptPoint(owner, finalAttacker);
            double speed = MathHelper.clamp(1.0d + 0.3d * (protective + focused), 1.0d, 1.8d);
            pet.getNavigation().startMovingTo(intercept.x, intercept.y, intercept.z, speed);

            pc.setStateData("assist_intercept_cooldown", now + OWNER_INTERCEPT_COOLDOWN_TICKS);
            pc.pushEmotion(PetComponent.Emotion.FOCUSED, scaledAmount(0.06f, 0.24f, protective + focused));
            pc.pushEmotion(PetComponent.Emotion.VIGILANT, scaledAmount(0.05f, 0.22f, Math.max(0f, 1f - afraid)));

            if (combatState != null) {
                float aggression = MathHelper.clamp(0.48f + 0.25f * protective, 0f, 1f);
                float urgency = MathHelper.clamp(0.35f + 0.20f * focused + (interceptAttackerHostile ? 0.12f : 0f), 0f, 1f);
                combatState.rememberAggroTarget(finalAttacker, now, OWNER_ASSIST_TARGET_TTL, false, aggression, urgency, interceptAttackerHostile);
            }
        }
    }

    private static Vec3d computeInterceptPoint(PlayerEntity owner, LivingEntity attacker) {
        Vec3d ownerPos = owner.getEntityPos();
        Vec3d attackerPos = attacker.getEntityPos();
        Vec3d direction = attackerPos.subtract(ownerPos);
        double distance = direction.length();
        if (distance < 1.0E-3) {
            return ownerPos;
        }
        double step = MathHelper.clamp(distance * 0.5d, 1.5d, 3.5d);
        return ownerPos.add(direction.normalize().multiply(step));
    }

    private static boolean isChainTargetViable(MobEntity pet, MobEntity candidate, PetComponent petComponent,
        @Nullable PlayerEntity owner, boolean petCanFly, double petAttackDamage) {
        if (candidate instanceof CreeperEntity creeper) {
            if (!canPetSafelyChainToCreeper(creeper, petAttackDamage)
                && !isPetOrPackCreeperProof(pet, petComponent, owner)) {
                return false;
            }
        }

        if (!petCanFly && isLikelyAirborne(candidate)) {
            return false;
        }

        return true;
    }

    private static boolean isPetOrPackCreeperProof(MobEntity pet, PetComponent petComponent, @Nullable PlayerEntity owner) {
        if (isCatContext(pet, petComponent)) {
            return true;
        }

        return hasNearbyCreeperProofAlly(pet, owner);
    }

    private static boolean hasNearbyCreeperProofAlly(MobEntity pet, @Nullable PlayerEntity owner) {
        if (!(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        double radius = Math.max(4.0, pet.getWidth() + 3.0);
        double radiusSq = radius * radius;
        Vec3d petPos = pet.getEntityPos();

        boolean found = false;
        if (owner instanceof ServerPlayerEntity) {
            List<PetSwarmIndex.SwarmEntry> swarm = snapshotOwnedPets(owner);
            if (!swarm.isEmpty()) {
                for (PetSwarmIndex.SwarmEntry entry : swarm) {
                    MobEntity other = entry.pet();
                    PetComponent otherComponent = entry.component();
                    if (other == null || other.equals(pet) || other.isRemoved() || !other.isAlive()) {
                        continue;
                    }
                    if (!withinRadius(entry, petPos, radiusSq)) {
                        continue;
                    }
                    if (otherComponent != null && otherComponent.isOwnedBy(owner) && isCatContext(other, otherComponent)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (found) {
            return true;
        }

        Box searchBox = pet.getBoundingBox().expand(radius);
        return !serverWorld.getEntitiesByClass(MobEntity.class, searchBox, other -> {
            if (other == null || other.equals(pet) || !other.isAlive() || other.isRemoved()) {
                return false;
            }

            PetComponent otherComponent = PetComponent.get(other);
            if (otherComponent != null) {
                if (!otherComponent.isOwnedBy(owner)) {
                    return false;
                }
                return isCatContext(other, otherComponent);
            }

            return isCatEntity(other);
        }).isEmpty();
    }

    private static boolean isCatContext(MobEntity entity, @Nullable PetComponent component) {
        if (isCatEntity(entity)) {
            return true;
        }

        if (component != null) {
            if (component.hasSpeciesTag(VANILLA_CREEPER_SOOTHER_TAG)) {
                return true;
            }

            if (component.matchesSpeciesKeyword("cat", "ocelot", "feline")) {
                return true;
            }
        }

        return false;
    }

    private static boolean isCatEntity(MobEntity entity) {
        EntityType<?> type = entity.getType();
        if (type.isIn(VANILLA_CREEPER_SOOTHER_TAG) || type == EntityType.OCELOT) {
            return true;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(type);
        if (typeId != null) {
            String path = typeId.getPath();
            if (path != null) {
                String lowered = path.toLowerCase(Locale.ROOT);
                if (lowered.contains("cat") || lowered.contains("ocelot") || lowered.contains("feline")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<PetSwarmIndex.SwarmEntry> snapshotOwnedPets(PlayerEntity owner) {
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return List.of();
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return List.of();
        }
        
        java.util.UUID ownerUuid = serverOwner.getUuid();
        long now = serverWorld.getTime();
        
        // Check cache first to improve performance
        Long cacheTimestamp = PET_SWARM_CACHE_TIMESTAMPS.get(ownerUuid);
        if (cacheTimestamp != null && (now - cacheTimestamp) < PET_SWARM_CACHE_TTL) {
            List<PetSwarmIndex.SwarmEntry> cachedSwarm = PET_SWARM_CACHE.get(ownerUuid);
            if (cachedSwarm != null) {
                return cachedSwarm;
            }
        }
        
        // Cache miss or expired - fetch fresh data
        List<PetSwarmIndex.SwarmEntry> swarm;
        synchronized (serverOwner) {
            StateManager stateManager = StateManager.forWorld(serverWorld);
            if (stateManager != null) {
                swarm = stateManager.getSwarmIndex().snapshotOwner(ownerUuid);
            } else {
                swarm = List.of();
            }
        }
        
        // Update cache
        PET_SWARM_CACHE.put(ownerUuid, swarm);
        PET_SWARM_CACHE_TIMESTAMPS.put(ownerUuid, now);
        
        return swarm;
    }

    private static boolean withinRadius(PetSwarmIndex.SwarmEntry entry, Vec3d center, double radiusSq) {
        double dx = entry.x() - center.x;
        double dy = entry.y() - center.y;
        double dz = entry.z() - center.z;
        return (dx * dx) + (dy * dy) + (dz * dz) <= radiusSq;
    }

    private static boolean canPetSafelyChainToCreeper(CreeperEntity creeper, double petAttackDamage) {
        if (petAttackDamage <= 0.0d) {
            return false;
        }

        float creeperHealth = creeper.getHealth();
        if (creeperHealth <= 0f) {
            creeperHealth = creeper.getMaxHealth();
        }

        return petAttackDamage + 0.5d >= creeperHealth;
    }

    private static boolean canPetEngageAirborneTarget(MobEntity pet, PetComponent petComponent) {
        if (petComponent != null) {
            PetComponent.FlightCapability capability = petComponent.getFlightCapability();
            if (capability.canFly()) {
                if (capability.source().isMetadataDerived()) {
                    Petsplus.LOGGER.debug(
                        "Treating pet {} as flight-capable via {} context metadata",
                        pet.getName().getString(),
                        capability.source()
                    );
                }
                return true;
            }
        }

        if (pet.hasNoGravity()) {
            return true;
        }

        String navName = pet.getNavigation().getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return navName.contains("bird") || navName.contains("fly");
    }

    private static boolean isLikelyAirborne(LivingEntity entity) {
        PetComponent component = entity instanceof MobEntity mob ? PetComponent.get(mob) : null;
        if (component != null && component.isFlightCapable()) {
            return true;
        }

        if (isTaggedFlyer(entity.getType())) {
            return true;
        }

        if (entity instanceof GhastEntity || entity instanceof PhantomEntity || entity instanceof VexEntity || entity instanceof BlazeEntity) {
            return true;
        }
        if (entity.hasNoGravity()) {
            return true;
        }
        if (entity instanceof MobEntity mob) {
            String navName = mob.getNavigation().getClass().getSimpleName().toLowerCase(Locale.ROOT);
            return navName.contains("bird") || navName.contains("fly");
        }
        return false;
    }

    private static boolean isTaggedFlyer(EntityType<?> type) {
        if (type == null) {
            return false;
        }
        return FLYER_TYPE_CACHE.computeIfAbsent(type, key -> key.isIn(PetsplusEntityTypeTags.FLYERS));
    }

    private static float scaledAmount(float base, float scale, float intensity) {
        return MathHelper.clamp(base + (scale * MathHelper.clamp(intensity, 0f, 1f)), 0f, 1f);
    }

    private static float hashedNoise(long seed) {
        seed ^= (seed >>> 33);
        seed *= 0xff51afd7ed558ccdL;
        seed ^= (seed >>> 33);
        seed *= 0xc4ceb9fe1a85ec53L;
        seed ^= (seed >>> 33);
        return (float) (Double.longBitsToDouble((seed & 0x000fffffffffffffL) | 0x3ff0000000000000L) - 1.0d);
    }

    private static float damageIntensity(float amount, float maxHealth) {
        if (maxHealth <= 0f) {
            return 0f;
        }
        return MathHelper.clamp(amount / maxHealth, 0f, 1f);
    }

    private static float missingHealthFactor(float healthPercent, float threshold) {
        if (threshold <= 0f) {
            return 0f;
        }
        return MathHelper.clamp((threshold - healthPercent) / threshold, 0f, 1f);
    }

    private static float proximityFactor(PlayerEntity owner, MobEntity pet, double radius) {
        if (owner == null || pet == null || radius <= 0d) {
            return 0f;
        }
        double distSq = owner.squaredDistanceTo(pet);
        double maxSq = radius * radius;
        if (maxSq <= 0d) {
            return 0f;
        }
        return MathHelper.clamp(1f - (float) (distSq / maxSq), 0f, 1f);
    }

    private record CombatVariance(
        long baseSeed,
        float nuanceNoise,
        float lateralBias,
        float retreatTempo,
        float spinBias,
        float moodAnchor,
        float urgencyBias
    ) {
        float nuanceCentered() {
            return (nuanceNoise * 2f) - 1f;
        }
    }

    private static CombatVariance computeCombatVariance(
        MobEntity pet,
        @Nullable PetComponent petComponent,
        @Nullable DamageSource damageSource,
        float damageRatio,
        long now,
        boolean treatAsOwnerSadness
    ) {
        long baseSalt = 0xB0C10AA3D15C4A27L;
        long uuidSalt = pet.getUuid().getLeastSignificantBits() ^ pet.getUuid().getMostSignificantBits();

        long typeSalt = 0L;
        if (damageSource != null) {
            String typeName = damageSource.getName();
            if (typeName != null) {
                long typeHash = typeName.hashCode();
                typeSalt = (typeHash << 32) ^ typeHash;
            }
        }

        long timeBucket = now / 7L; // Re-roll variance a few times per second for pacing
        long ratioBits = Float.floatToIntBits(MathHelper.clamp(damageRatio, 0f, 1f));

        float protective = 0f;
        float restless = 0f;
        float playful = 0f;
        float focused = 0f;
        if (petComponent != null) {
            PetMoodEngine moodEngine = petComponent.getMoodEngine();
            if (moodEngine != null) {
                protective = MathHelper.clamp(moodEngine.getMoodStrength(PetComponent.Mood.PROTECTIVE), 0f, 1f);
                restless = MathHelper.clamp(moodEngine.getMoodStrength(PetComponent.Mood.RESTLESS), 0f, 1f);
                playful = MathHelper.clamp(moodEngine.getMoodStrength(PetComponent.Mood.PLAYFUL), 0f, 1f);
                focused = MathHelper.clamp(moodEngine.getMoodStrength(PetComponent.Mood.FOCUSED), 0f, 1f);
            }
        }

        long moodSalt = ((long) (protective * 997f) << 48)
            ^ ((long) (restless * 997f) << 32)
            ^ ((long) (playful * 997f) << 16)
            ^ (long) (focused * 997f);

        long sadnessSalt = treatAsOwnerSadness ? 0x5F2D3AB19E3779B9L : 0L;

        long combinedSalt = baseSalt ^ uuidSalt ^ typeSalt ^ sadnessSalt;
        combinedSalt ^= (timeBucket * 0x9E3779B97F4A7C15L);
        combinedSalt ^= ((long) ratioBits << 1);
        combinedSalt ^= moodSalt;

        long mixedSeed;
        if (petComponent != null) {
            mixedSeed = petComponent.mixStableSeed(combinedSalt);
        } else {
            mixedSeed = BehaviorSeedUtil.mixBehaviorSeed(uuidSalt, combinedSalt);
        }

        float nuanceNoise = hashedNoise(mixedSeed);
        float lateralBias = MathHelper.clamp((hashedNoise(mixedSeed ^ 0x6A09E667F3BCC909L) * 2f) - 1f, -1f, 1f);
        float retreatTempo = MathHelper.clamp(
            0.85f
                + ((hashedNoise(mixedSeed ^ 0xBB67AE8584CAA73BL) * 2f) - 1f) * 0.25f
                + protective * 0.20f
                + focused * 0.18f
                - restless * 0.12f
                - (treatAsOwnerSadness ? 0.10f : 0f),
            0.6f,
            1.4f
        );

        float spinBias = MathHelper.clamp((hashedNoise(mixedSeed ^ 0x3C6EF372FE94F82BL) * 2f) - 1f, -1f, 1f);
        float moodAnchor = MathHelper.clamp(
            0.88f + protective * 0.32f - restless * 0.22f + playful * 0.18f,
            0.6f,
            1.5f
        );
        float urgencyBias = MathHelper.clamp(
            0.8f + restless * 0.25f + protective * 0.18f + playful * 0.12f,
            0.55f,
            1.5f
        );

        return new CombatVariance(mixedSeed, nuanceNoise, lateralBias, retreatTempo, spinBias, moodAnchor, urgencyBias);
    }

    @Nullable
    private static Vec3d computeHazardRetreatVector(MobEntity pet, @Nullable DamageSource damageSource) {
        if (damageSource != null) {
            Vec3d hazardCenter = damageSource.getPosition();
            if (hazardCenter != null) {
                Vec3d away = new Vec3d(pet.getX() - hazardCenter.x, 0.0d, pet.getZ() - hazardCenter.z);
                if (away.lengthSquared() > 1.0E-4d) {
                    return away.normalize();
                }
            }
        }

        World world = pet.getEntityWorld();
        BlockPos petPos = pet.getBlockPos();
        Vec3d closestHazard = null;
        double closestDistanceSq = Double.MAX_VALUE;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    mutable.set(petPos.getX() + dx, petPos.getY() + dy, petPos.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    boolean isHazard = isHazardBlock(state) || world.getFluidState(mutable).isIn(FluidTags.LAVA);
                    if (!isHazard) {
                        continue;
                    }
                    Vec3d center = Vec3d.ofCenter(mutable);
                    double distanceSq = center.squaredDistanceTo(pet.getX(), pet.getY(), pet.getZ());
                    if (distanceSq < closestDistanceSq) {
                        closestDistanceSq = distanceSq;
                        closestHazard = center;
                    }
                }
            }
        }

        if (closestHazard != null) {
            Vec3d away = new Vec3d(pet.getX() - closestHazard.x, 0.0d, pet.getZ() - closestHazard.z);
            if (away.lengthSquared() > 1.0E-4d) {
                return away.normalize();
            }
        }

        return null;
    }

    private static boolean isHazardBlock(BlockState state) {
        if (state.isOf(Blocks.CACTUS)
            || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.FIRE)
            || state.isOf(Blocks.SOUL_FIRE)) {
            return true;
        }

        if (state.getBlock() instanceof CampfireBlock) {
            return state.contains(CampfireBlock.LIT) && state.get(CampfireBlock.LIT);
        }

        return false;
    }

    private static void applyChipDamageRestlessness(
        MobEntity pet,
        PetComponent petComponent,
        float amount,
        float damageRatio,
        Entity attacker,
        PlayerEntity owner,
        DamageSource damageSource
    ) {
        if (amount <= 0.0f) {
            return;
        }

        // Ignore obvious burst damage - that is handled by the fear system
        if (damageRatio > CHIP_DAMAGE_RATIO_CEILING) {
            return;
        }

        long now = pet.getEntityWorld().getTime();
        boolean isOwnerAttack = attacker instanceof PlayerEntity playerAttacker && owner != null && playerAttacker.equals(owner);
        boolean petIsCursed = petComponent.hasRole(PetRoleType.CURSED_ONE);
        boolean treatAsOwnerSadness = isOwnerAttack && !petIsCursed;

        String accumKey = treatAsOwnerSadness ? CHIP_DAMAGE_OWNER_ACCUM_KEY : CHIP_DAMAGE_ACCUM_KEY;
        String lastTickKey = treatAsOwnerSadness ? CHIP_DAMAGE_OWNER_LAST_TICK_KEY : CHIP_DAMAGE_LAST_TICK_KEY;

        float accum = petComponent.getStateData(accumKey, Float.class, 0f);
        long lastTick = petComponent.getStateData(lastTickKey, Long.class, 0L);
        if (lastTick > 0L) {
            long delta = now - lastTick;
            if (delta > 0L) {
                float decay = (delta / (float) CHIP_DAMAGE_DECAY_TICKS) * CHIP_DAMAGE_DECAY_STEP;
                if (decay > 0f) {
                    accum = Math.max(0f, accum - decay);
                }
            }
        }

        float severity = MathHelper.clamp(damageRatio / CHIP_DAMAGE_RATIO_CEILING, 0f, 1f);
        float severityCurve = severity * severity;
        float accumContribution = damageRatio * MathHelper.lerp(severity, 0.45f, 1.0f);
        if (treatAsOwnerSadness) {
            accumContribution *= 0.75f;
        }
        accum += accumContribution;
        petComponent.setStateData(lastTickKey, now);

        float accumRatio = MathHelper.clamp(accum / CHIP_DAMAGE_THRESHOLD, 0f, 1.35f);
        float severityEnvelope = MathHelper.clamp(0.42f * severity + 0.38f * severityCurve + 0.20f * accumRatio, 0f, 1f);
        CombatVariance variance = computeCombatVariance(pet, petComponent, damageSource, damageRatio, now, treatAsOwnerSadness);
        float nuanceNoise = variance.nuanceNoise();
        float nuanceCentered = variance.nuanceCentered();
        float accentSeverity = MathHelper.clamp(
            severityEnvelope + nuanceCentered * MathHelper.lerp(severityEnvelope, 0.07f, 0.24f),
            0f,
            1f
        );
        accentSeverity = MathHelper.clamp(accentSeverity * MathHelper.lerp(variance.moodAnchor(), 0.85f, 1.18f), 0f, 1f);
        float jitteredSeverity = MathHelper.clamp(
            MathHelper.lerp(0.65f, severityEnvelope, accentSeverity) * MathHelper.lerp(variance.urgencyBias(), 0.9f, 1.18f),
            0f,
            1f
        );

        float startle = scaledAmount(0.0065f, treatAsOwnerSadness ? 0.18f : 0.26f, jitteredSeverity);
        startle *= MathHelper.lerp(Math.abs(nuanceCentered), 0.9f, 1.18f);
        startle *= MathHelper.lerp(variance.urgencyBias(), 0.82f, 1.16f);
        if (treatAsOwnerSadness) {
            startle *= 0.55f;
        } else if (isOwnerAttack) {
            startle *= 1.15f; // Cursed One enjoys the roughhousing, others treat owner attacks as harsher than mobs
        } else if (attacker instanceof PlayerEntity) {
            startle *= 1.05f;
        }
        petComponent.pushEmotion(PetComponent.Emotion.STARTLE, startle);

        if (treatAsOwnerSadness) {
            float ennuiBlend = MathHelper.clamp(MathHelper.lerp(0.55f, severityEnvelope, accentSeverity), 0f, 1f);
            float saudadeBlend = MathHelper.clamp(MathHelper.lerp(0.45f, severityCurve, accentSeverity), 0f, 1f);
            petComponent.pushEmotion(PetComponent.Emotion.ENNUI, scaledAmount(0.07f, 0.36f, ennuiBlend));
            petComponent.pushEmotion(PetComponent.Emotion.SAUDADE, scaledAmount(0.025f, 0.27f, saudadeBlend));
        } else {
            float frustrationBlend = MathHelper.clamp(0.35f * severityEnvelope + 0.40f * accentSeverity + 0.25f * accumRatio, 0f, 1f);
            float ennuiTrace = MathHelper.clamp(MathHelper.lerp(0.35f, severityEnvelope, accentSeverity), 0f, 1f);
            float frustration = scaledAmount(0.0105f, 0.21f, frustrationBlend);
            frustration *= MathHelper.lerp(nuanceNoise, 0.92f, 1.22f);
            frustration *= MathHelper.lerp(variance.moodAnchor(), 0.85f, 1.12f);
            petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, frustration);
            petComponent.pushEmotion(PetComponent.Emotion.ENNUI, scaledAmount(0.0055f, 0.14f, ennuiTrace));
        }

        boolean emittedBurst = false;
        while (accum >= CHIP_DAMAGE_THRESHOLD) {
            float overshoot = MathHelper.clamp((accum - CHIP_DAMAGE_THRESHOLD) / (CHIP_DAMAGE_THRESHOLD * 0.9f), 0f, 1f);
            float burstSeed = MathHelper.clamp(0.55f * overshoot + 0.45f * accentSeverity, 0f, 1f);
            long burstSalt = variance.baseSeed() ^ (emittedBurst ? 0x9E3779B97F4A7C15L : 0xC6A4A7935BD1E995L);
            burstSalt ^= (long) (overshoot * 6553f);
            float burstNoise = (hashedNoise(burstSalt) * 2f) - 1f;
            float burstSeverity = MathHelper.clamp(
                (burstSeed * MathHelper.lerp(variance.urgencyBias(), 0.9f, 1.15f))
                    + burstNoise * MathHelper.lerp(burstSeed, 0.06f, 0.2f),
                0f,
                1f
            );
            if (!emittedBurst && burstSeverity < 0.3f) {
                break;
            }

            float burstCurve = MathHelper.clamp(burstSeverity * burstSeverity * MathHelper.lerp(variance.moodAnchor(), 0.9f, 1.18f), 0f, 1f);
            if (treatAsOwnerSadness) {
                petComponent.pushEmotion(PetComponent.Emotion.ENNUI, scaledAmount(0.12f, 0.42f, burstCurve));
                petComponent.pushEmotion(PetComponent.Emotion.SAUDADE, scaledAmount(0.05f, 0.30f, burstCurve));
                petComponent.pushEmotion(PetComponent.Emotion.STARTLE, scaledAmount(0.06f, 0.24f, burstCurve));
            } else {
                petComponent.pushEmotion(PetComponent.Emotion.STARTLE, scaledAmount(0.05f, 0.28f, burstCurve));
                petComponent.pushEmotion(PetComponent.Emotion.ENNUI, scaledAmount(0.02f, 0.18f, burstCurve));
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, scaledAmount(0.04f, 0.24f, burstCurve));
            }

            float drainBias = MathHelper.clamp(Math.max(burstSeverity, accentSeverity) * MathHelper.lerp(variance.moodAnchor(), 0.9f, 1.18f), 0f, 1f);
            float drain = MathHelper.lerp(
                drainBias,
                CHIP_DAMAGE_THRESHOLD * (0.45f + 0.12f * severityEnvelope),
                CHIP_DAMAGE_THRESHOLD * 0.95f
            );
            drain *= MathHelper.lerp(variance.retreatTempo(), 0.92f, 1.12f);
            accum = Math.max(0f, accum - drain);
            emittedBurst = true;
        }

        float retreatEnergy = MathHelper.clamp(0.5f * accentSeverity + 0.5f * severityCurve, 0f, 1f);
        retreatEnergy *= MathHelper.lerp(variance.moodAnchor(), 0.78f, 1.28f);
        if (treatAsOwnerSadness) {
            retreatEnergy *= 0.6f;
        } else {
            retreatEnergy *= MathHelper.lerp(variance.urgencyBias(), 0.92f, 1.1f);
        }

        float retreatVariance = MathHelper.clamp(
            retreatEnergy + variance.nuanceCentered() * MathHelper.lerp(retreatEnergy, 0.05f, 0.18f),
            0f,
            1f
        );
        retreatVariance = MathHelper.clamp(retreatVariance * variance.retreatTempo(), 0f, 1f);
        boolean canHop = pet.isOnGround() || pet.isTouchingWater() || pet.isClimbing();
        if (canHop && retreatVariance > 0.05f) {
            Vec3d retreatVector;
            if (attacker instanceof LivingEntity attackerLiving && !attackerLiving.equals(pet)) {
                retreatVector = new Vec3d(pet.getX() - attackerLiving.getX(), 0.0d, pet.getZ() - attackerLiving.getZ());
            } else if (attacker != null && !attacker.equals(pet)) {
                retreatVector = new Vec3d(pet.getX() - attacker.getX(), 0.0d, pet.getZ() - attacker.getZ());
            } else {
                Vec3d hazardRetreat = computeHazardRetreatVector(pet, damageSource);
                if (hazardRetreat != null) {
                    retreatVector = hazardRetreat;
                } else {
                    double yaw = MathHelper.lerp((variance.spinBias() + 1f) * 0.5d, -Math.PI, Math.PI);
                    retreatVector = new Vec3d(Math.cos(yaw), 0.0d, Math.sin(yaw));
                }
            }

            double lengthSq = retreatVector.lengthSquared();
            if (lengthSq < 1.0E-4d) {
                retreatVector = new Vec3d(1.0d, 0.0d, 0.0d);
            }

            Vec3d normalized = retreatVector.normalize();
            if (Math.abs(variance.lateralBias()) > 0.01f) {
                Vec3d lateral = new Vec3d(-normalized.z, 0.0d, normalized.x);
                double lateralMix = variance.lateralBias() * MathHelper.lerp(retreatVariance, 0.05f, 0.4f);
                normalized = normalized.multiply(1.0d - Math.abs(lateralMix)).add(lateral.multiply(lateralMix)).normalize();
            }

            double hopStrength = MathHelper.lerp(retreatVariance, 0.05d, 0.38d) * MathHelper.lerp(variance.retreatTempo(), 0.85d, 1.25d);
            double liftStrength = MathHelper.lerp(retreatVariance, 0.03d, 0.22d) * MathHelper.lerp(variance.moodAnchor(), 0.85d, 1.25d);
            pet.takeKnockback(hopStrength, normalized.x, normalized.z);
            pet.addVelocity(0.0d, liftStrength, 0.0d);
            pet.velocityModified = true;

            if (Math.abs(variance.spinBias()) > 0.05f) {
                float spinAmount = variance.spinBias() * MathHelper.lerp(retreatVariance, treatAsOwnerSadness ? 8f : 12f, treatAsOwnerSadness ? 24f : 32f);
                pet.setYaw(pet.getYaw() + spinAmount);
                pet.bodyYaw = pet.getYaw();
                pet.headYaw = MathHelper.clamp(pet.headYaw + spinAmount * 0.65f, pet.getYaw() - 45f, pet.getYaw() + 45f);
            }

            if (retreatVariance > 0.18f) {
                double travel = MathHelper.lerp(retreatVariance, treatAsOwnerSadness ? 0.8d : 1.4d, 4.0d)
                    * MathHelper.lerp(variance.moodAnchor(), 0.9d, 1.3d);
                Vec3d retreatOrigin = new Vec3d(pet.getX(), pet.getY(), pet.getZ());
                Vec3d retreatTarget = retreatOrigin.add(normalized.multiply(travel));
                double speed = MathHelper.lerp(retreatVariance, 0.72d, 1.32d)
                    * MathHelper.lerp(variance.retreatTempo(), 0.85d, 1.35d);
                pet.getNavigation().startMovingTo(retreatTarget.x, retreatTarget.y, retreatTarget.z, speed);
            }
        }

        if (accum < 0.001f) {
            petComponent.setStateData(accumKey, 0f);
        } else {
            petComponent.setStateData(accumKey, accum);
        }
    }

    /**
     * Check if pet has recovered from low health and apply stoic/endurance emotions
     */
    private static void checkHealthRecovery(MobEntity pet, PetComponent petComponent, float currentHealthPercent, long now) {
        // Check if pet was previously in low health
        long lastLowHealthTick = petComponent.getStateData(PetComponent.StateKeys.HEALTH_LAST_LOW_TICK, Long.class, 0L);
        if (lastLowHealthTick == 0L) {
            return; // No previous low health state
        }

        // Check cooldown to prevent spamming recovery emotions
        long lastRecoveryCooldown = petComponent.getStateData(PetComponent.StateKeys.HEALTH_RECOVERY_COOLDOWN, Long.class, 0L);
        if (now - lastRecoveryCooldown < 1200) { // 60 second cooldown
            return;
        }

        // Check if pet has recovered (health now above 60%)
        if (currentHealthPercent >= 0.6f) {
            // Calculate recovery intensity based on how low they were and time since low health
            long timeSinceLowHealth = now - lastLowHealthTick;
            float recoveryIntensity = MathHelper.clamp(timeSinceLowHealth / 2400f, 0.1f, 1.0f); // 2 minutes max for full intensity

            // Apply stoic or endurance based on recovery context
            float stoicWeight = scaledAmount(0.15f, 0.40f, recoveryIntensity);
            float gamanWeight = scaledAmount(0.10f, 0.30f, recoveryIntensity);

            petComponent.pushEmotion(PetComponent.Emotion.STOIC, stoicWeight);
            petComponent.pushEmotion(PetComponent.Emotion.GAMAN, gamanWeight);

            // Set cooldown and clear the low health tracking
            petComponent.setStateData(PetComponent.StateKeys.HEALTH_RECOVERY_COOLDOWN, now);
            petComponent.setStateData(PetComponent.StateKeys.HEALTH_LAST_LOW_TICK, 0L);

            Petsplus.LOGGER.debug("Pet {} recovered from low health, applied stoic/gaman emotions", pet.getName().getString());
        }
    }
}



