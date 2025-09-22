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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.OwnerCombatState;
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
    private static final Map<UUID, Long> attackPrimingExpiries = new ConcurrentHashMap<>();
    
    // Configuration constants (could be moved to config later)
    private static final int BULWARK_COOLDOWN_TICKS = 200; // 10 seconds
    private static final int ATTACK_PRIMING_DURATION_TICKS = 100; // 5 seconds
    private static final float BASE_DAMAGE_REDIRECT_RATIO = 0.3f; // 30% base redirect
    private static final double GUARDIAN_PROXIMITY_RANGE = 16.0; // Range to find Guardian pets
    private static final String GUARDIAN_PRIMED_STATE_KEY = "guardian_bulwark_active_end";

    public static void initialize() {
        // Register damage event handler for Bulwark damage redirection
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GuardianCore::onEntityDamage);

        // Register post-damage handler to consume primed attacks
        ServerLivingEntityEvents.AFTER_DAMAGE.register(GuardianCore::onEntityAfterDamage);

        // Register world tick for cooldown cleanup and attack processing
        ServerTickEvents.END_WORLD_TICK.register(GuardianCore::onWorldTick);
    }
    
    /**
     * Handle damage events for Bulwark damage redirection.
     */
    private static boolean onEntityDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        // Only process damage to players
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true; // Allow damage
        }
        
        // Don't process damage from pets (avoid redirect loops)
        if (damageSource.getAttacker() instanceof MobEntity attacker) {
            PetComponent attackerComp = PetComponent.get(attacker);
            if (attackerComp != null) {
                return true; // Allow damage from pets
            }
        }
        
        // Find nearby Guardian pets
        List<MobEntity> guardianPets = findNearbyGuardianPets(player);
        if (guardianPets.isEmpty()) {
            return true; // No guardians available
        }
        
        // Find the best guardian to intercept damage
        MobEntity bestGuardian = findBestGuardianForIntercept(guardianPets);
        if (bestGuardian == null) {
            return true; // No suitable guardian
        }
        
        // Attempt damage redirection
        attemptBulwarkRedirect(player, bestGuardian, damageSource, damageAmount);
        
        return true; // Always allow original damage (we handle redirection manually)
    }
    
    /**
     * Find nearby Guardian pets that can intercept damage.
     */
    private static List<MobEntity> findNearbyGuardianPets(ServerPlayerEntity player) {
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
                       !petComp.isPerched(); // Use PetComponent's perched state instead
            }
        );
    }
    
    /**
     * Find the best Guardian pet for damage interception.
     * Prioritizes: not on cooldown > higher level > closer distance > higher health
     */
    private static MobEntity findBestGuardianForIntercept(List<MobEntity> guardianPets) {
        long currentTime = guardianPets.get(0).getWorld().getTime();
        
        return guardianPets.stream()
            .filter(pet -> {
                // Must have health to absorb damage
                return pet.getHealth() > 2.0f;
            })
            .sorted((pet1, pet2) -> {
                // Priority 1: Not on cooldown
                boolean pet1OnCooldown = isOnBulwarkCooldown(pet1, currentTime);
                boolean pet2OnCooldown = isOnBulwarkCooldown(pet2, currentTime);
                
                if (pet1OnCooldown != pet2OnCooldown) {
                    return Boolean.compare(pet1OnCooldown, pet2OnCooldown);
                }
                
                // Priority 2: Higher level
                PetComponent comp1 = PetComponent.get(pet1);
                PetComponent comp2 = PetComponent.get(pet2);
                int levelDiff = Integer.compare(comp2.getLevel(), comp1.getLevel());
                if (levelDiff != 0) return levelDiff;
                
                // Priority 3: Higher health percentage
                float health1Pct = pet1.getHealth() / pet1.getMaxHealth();
                float health2Pct = pet2.getHealth() / pet2.getMaxHealth();
                return Float.compare(health2Pct, health1Pct);
            })
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Attempt to redirect damage using Bulwark ability.
     */
    private static boolean attemptBulwarkRedirect(ServerPlayerEntity player, MobEntity guardian, DamageSource damageSource, float damageAmount) {
        PetComponent petComp = PetComponent.get(guardian);
        if (petComp == null) return false;
        
        long currentTime = player.getWorld().getTime();
        
        // Check cooldown
        if (isOnBulwarkCooldown(guardian, currentTime)) {
            return false;
        }
        
        // Calculate redirect ratio based on level
        float redirectRatio = calculateRedirectRatio(petComp.getLevel());
        float redirectedDamage = damageAmount * redirectRatio;
        
        // Don't redirect if it would kill the guardian
        if (guardian.getHealth() <= redirectedDamage) {
            redirectedDamage = Math.max(0, guardian.getHealth() - 1.0f);
            redirectRatio = redirectedDamage / damageAmount;
        }
        
        if (redirectedDamage <= 0) {
            return false; // Can't redirect any damage
        }
        
        // Apply redirected damage to guardian
        if (player.getWorld() instanceof ServerWorld world) {
            guardian.damage(world, world.getDamageSources().magic(), redirectedDamage);
        }
        
        // Reduce player damage
        float reducedPlayerDamage = damageAmount - redirectedDamage;
        if (reducedPlayerDamage > 0) {
            // We can't easily modify the original damage, so apply healing
            // This is a limitation of the current system
            player.heal(redirectedDamage);
        }
        
        // Set cooldown
        bulwarkCooldowns.put(guardian.getUuid(), currentTime + BULWARK_COOLDOWN_TICKS);
        
        // Prime owner's next attack
        attackPrimingExpiries.put(player.getUuid(), currentTime + ATTACK_PRIMING_DURATION_TICKS);
        
        // Visual and audio feedback
        playBulwarkFeedback(player, guardian, redirectedDamage, redirectRatio);
        
        // Update owner combat state
        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(player);
        ownerState.setTempState(GUARDIAN_PRIMED_STATE_KEY, currentTime + ATTACK_PRIMING_DURATION_TICKS);

        return true;
    }

    /**
     * Consume primed Guardian attacks once the owner damages a target.
     */
    private static void onEntityAfterDamage(LivingEntity entity, DamageSource damageSource, float baseDamageAmount, float damageTaken, boolean blocked) {
        if (!(damageSource.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }

        UUID attackerId = attacker.getUuid();
        Long expiryTick = attackPrimingExpiries.get(attackerId);
        if (expiryTick == null) {
            return;
        }

        long currentTime = attacker.getWorld().getTime();
        OwnerCombatState ownerState = OwnerCombatState.get(attacker);
        if (ownerState == null) {
            attackPrimingExpiries.remove(attackerId);
            return;
        }

        long stateExpiry = ownerState.getTempState(GUARDIAN_PRIMED_STATE_KEY);
        if (stateExpiry <= currentTime || currentTime > expiryTick) {
            consumePrimedAttack(attacker, ownerState);
            return;
        }

        applyPrimedAttackEffects(attacker, entity, damageSource, baseDamageAmount);
        consumePrimedAttack(attacker, ownerState);
    }
    
    /**
     * Calculate damage redirect ratio based on Guardian level.
     */
    private static float calculateRedirectRatio(int level) {
        // Base 30% redirect, +2% per level after 3, max 70%
        float ratio = BASE_DAMAGE_REDIRECT_RATIO;
        if (level > 3) {
            ratio += (level - 3) * 0.02f;
        }
        return Math.min(0.7f, ratio);
    }
    
    /**
     * Check if a Guardian pet is on Bulwark cooldown.
     */
    private static boolean isOnBulwarkCooldown(MobEntity guardian, long currentTime) {
        Long cooldownExpiry = bulwarkCooldowns.get(guardian.getUuid());
        return cooldownExpiry != null && currentTime < cooldownExpiry;
    }
    
    /**
     * Apply effects for primed attacks from successful Bulwark redirects.
     */
    private static void applyPrimedAttackEffects(ServerPlayerEntity attacker, LivingEntity target, DamageSource damageSource, float originalDamage) {
        // Apply Weakness to target (Guardian's protective curse)
        if (target instanceof LivingEntity livingTarget) {
            livingTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0)); // 3 seconds
        }
        
        // Apply brief Strength to attacker (Guardian's blessing)
        attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 0)); // 2 seconds
        
        // Visual feedback
        if (attacker.getWorld() instanceof ServerWorld world) {
            FeedbackManager.emitFeedback("guardian_primed_attack", attacker, world);
        }
        
        // Notify player
        attacker.sendMessage(
            Text.literal("§6⚔ §eGuardian's Blessing: ").append(
                Text.literal("Your next strikes are empowered!").formatted(Formatting.YELLOW)
            ), 
            true // Action bar
        );
    }
    
    /**
     * Play visual and audio feedback for Bulwark damage redirection.
     */
    private static void playBulwarkFeedback(ServerPlayerEntity player, MobEntity guardian, float redirectedDamage, float redirectRatio) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        
        // Sound effect
        world.playSound(
            null,
            guardian.getX(), guardian.getY(), guardian.getZ(),
            SoundEvents.ITEM_SHIELD_BLOCK,
            SoundCategory.NEUTRAL,
            0.8f,
            1.2f
        );
        
        // Visual feedback
        FeedbackManager.emitFeedback("guardian_bulwark", guardian, world);
        
        // Notify player
        int redirectPercent = Math.round(redirectRatio * 100);
        String guardianName = guardian.hasCustomName() ? 
            guardian.getCustomName().getString() : 
            guardian.getType().getName().getString();
            
        player.sendMessage(
            Text.literal("§9🛡 §b").append(Text.literal(guardianName)).append(
                Text.literal(" absorbed " + redirectPercent + "% damage!").formatted(Formatting.AQUA)
            ), 
            true // Action bar
        );
    }
    
    /**
     * World tick handler for cooldown cleanup and passive effects.
     */
    private static void onWorldTick(ServerWorld world) {
        long currentTime = world.getTime();

        // Clean up expired cooldowns
        bulwarkCooldowns.entrySet().removeIf(entry -> currentTime > entry.getValue());
        attackPrimingExpiries.entrySet().removeIf(entry -> {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                return true; // Player logged out; clear entry
            }

            if (player.getWorld() != world) {
                return false; // Let the correct world handle expiry timing
            }

            if (player.getWorld().getTime() > entry.getValue()) {
                OwnerCombatState ownerState = OwnerCombatState.get(player);
                if (ownerState != null) {
                    ownerState.clearTempState(GUARDIAN_PRIMED_STATE_KEY);
                }
                return true;
            }

            return false;
        });
    }

    private static void consumePrimedAttack(ServerPlayerEntity attacker, OwnerCombatState ownerState) {
        attackPrimingExpiries.remove(attacker.getUuid());
        ownerState.clearTempState(GUARDIAN_PRIMED_STATE_KEY);
    }
    
    /**
     * Check if a player has an active Guardian providing protection.
     */
    public static boolean hasActiveGuardianProtection(ServerPlayerEntity player) {
        List<MobEntity> guardians = findNearbyGuardianPets(player);
        return !guardians.isEmpty() && guardians.stream().anyMatch(guardian -> 
            !isOnBulwarkCooldown(guardian, player.getWorld().getTime()) && guardian.getHealth() > 2.0f
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
        
        return calculateRedirectRatio(petComp.getLevel());
    }
}