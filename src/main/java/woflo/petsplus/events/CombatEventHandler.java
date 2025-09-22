package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

/**
 * Handles combat-related events and triggers pet abilities accordingly.
 */
public class CombatEventHandler {
    
    public static void register() {
        // Register damage events
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatEventHandler::onDamageReceived);
        AttackEntityCallback.EVENT.register(CombatEventHandler::onPlayerAttack);
        
        Petsplus.LOGGER.info("Combat event handlers registered");
    }
    
    /**
     * Called when any living entity receives damage.
     */
    private static boolean onDamageReceived(LivingEntity entity, DamageSource damageSource, float amount) {
        // Check if the damaged entity is a player (owner)
        if (entity instanceof PlayerEntity player) {
            handleOwnerDamageReceived(player, damageSource, amount);
        }
        
        // Check if the damage was dealt by a player
        if (damageSource.getAttacker() instanceof PlayerEntity attacker) {
            handleOwnerDealtDamage(attacker, entity, amount);
        }
        
        // Check if damage was from a projectile shot by a player
        if (damageSource.getSource() instanceof PersistentProjectileEntity projectile) {
            if (projectile.getOwner() instanceof PlayerEntity shooter) {
                handleProjectileDamage(shooter, entity, amount, projectile);
            }
        }
        
        return true; // Allow damage
    }
    
    /**
     * Called when a player attacks an entity directly.
     */
    private static ActionResult onPlayerAttack(PlayerEntity player, World world, Hand hand, Entity target, EntityHitResult hitResult) {
        if (target instanceof LivingEntity livingTarget) {
            // Apply any next attack riders
            OwnerCombatState combatState = OwnerCombatState.get(player);
            if (combatState != null) {
                applyAttackRiders(player, livingTarget, combatState);
            }
        }
        
        return ActionResult.PASS;
    }
    
    private static void handleOwnerDamageReceived(PlayerEntity owner, DamageSource damageSource, float amount) {
        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        combatState.onHitTaken();
        
        // Trigger low health events if needed
        float healthAfter = owner.getHealth() - amount;
        float maxHealth = owner.getMaxHealth();
        double healthPct = healthAfter / maxHealth;
        
        if (healthPct <= 0.35) { // 35% health threshold
            triggerAbilitiesForOwner(owner, "on_owner_low_health");
        }
        
        // Check for pet proximity and trigger guardian abilities
        triggerNearbyPetAbilities(owner, "owner_damage_taken");
    }
    
    private static void handleOwnerDealtDamage(PlayerEntity owner, LivingEntity victim, float damage) {
        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        combatState.enterCombat();
        
        // Apply Striker execution fallback
        float modifiedDamage = woflo.petsplus.roles.striker.StrikerExecutionFallback.applyOwnerExecuteBonus(owner, victim, damage);
        
        // Apply Scout spotter fallback
        woflo.petsplus.roles.scout.ScoutBehaviors.applySpotterGlowing(owner, victim);
        
        // Calculate victim health percentage after damage
        float victimHealthAfter = victim.getHealth() - modifiedDamage;
        float victimMaxHealth = victim.getMaxHealth();
        double victimHealthPct = victimHealthAfter / victimMaxHealth;
        
        // Create trigger context
        TriggerContext context = new TriggerContext(
            (net.minecraft.server.world.ServerWorld) owner.getWorld(),
            null, // Pet will be set when triggering specific pet abilities
            owner,
            "owner_dealt_damage"
        ).withData("victim", victim)
         .withData("damage", (double) modifiedDamage)
         .withData("victim_hp_pct", victimHealthPct);
        
        // Trigger abilities for nearby pets
        triggerNearbyPetAbilities(owner, context);
        
        Petsplus.LOGGER.debug("Owner {} dealt {} damage to {}, victim at {}% health", 
            owner.getName().getString(), modifiedDamage, victim.getType().toString(), victimHealthPct * 100);
    }
    
    private static void handleProjectileDamage(PlayerEntity shooter, LivingEntity target, float damage, PersistentProjectileEntity projectile) {
        // Check if this was a critical hit
        boolean wasCrit = projectile.isCritical();
        
        if (wasCrit) {
            triggerAbilitiesForOwner(shooter, "owner_projectile_crit");
        }
        
        // Handle as regular damage too
        handleOwnerDealtDamage(shooter, target, damage);
    }
    
    private static void applyAttackRiders(PlayerEntity owner, LivingEntity victim, OwnerCombatState combatState) {
        // Apply on-hit effects using the new attack rider system
        woflo.petsplus.combat.OwnerAttackRider.applyOnHitEffects(owner, victim, 0, null);
        
        // Clear expired riders
        woflo.petsplus.combat.OwnerAttackRider.clearExpiredRiders(owner);
    }
    
    public static void triggerAbilitiesForOwner(PlayerEntity owner, String eventType) {
        TriggerContext context = new TriggerContext(
            (net.minecraft.server.world.ServerWorld) owner.getWorld(),
            null,
            owner,
            eventType
        );
        
        triggerNearbyPetAbilities(owner, context);
    }

    /**
     * Trigger abilities for an owner with additional context data.
     */
    public static void triggerAbilitiesForOwner(PlayerEntity owner, String eventType, java.util.Map<String, Object> data) {
        TriggerContext context = new TriggerContext(
            (net.minecraft.server.world.ServerWorld) owner.getWorld(),
            null,
            owner,
            eventType
        );
        if (data != null) {
            for (var e : data.entrySet()) {
                context.withData(e.getKey(), e.getValue());
            }
        }
        triggerNearbyPetAbilities(owner, context);
    }
    
    private static void triggerNearbyPetAbilities(PlayerEntity owner, String eventType) {
        TriggerContext context = new TriggerContext(
            (net.minecraft.server.world.ServerWorld) owner.getWorld(),
            null,
            owner,
            eventType
        );
        
        triggerNearbyPetAbilities(owner, context);
    }
    
    private static void triggerNearbyPetAbilities(PlayerEntity owner, TriggerContext context) {
        // Find nearby pets that belong to this owner
        owner.getWorld().getEntitiesByClass(MobEntity.class, 
            owner.getBoundingBox().expand(32), // 32 block radius
            mob -> {
                PetComponent petComponent = PetComponent.get(mob);
                return petComponent != null && isPetOwnedBy(mob, owner);
            }
        ).forEach(pet -> {
            if (pet == null) {
                return; // Skip null pets
            }
            try {
                AbilityManager.triggerAbilities(pet, context);
            } catch (Exception e) {
                Petsplus.LOGGER.error("Error triggering abilities for pet {}", pet.getType(), e);
            }
        });
    }
    
    /**
     * Check if a mob is owned by the given player.
     */
    private static boolean isPetOwnedBy(MobEntity pet, PlayerEntity owner) {
        PetComponent component = PetComponent.get(pet);
        if (component != null) {
            return component.isOwnedBy(owner);
        }
        
        // Fall back to checking tameable entities
        if (pet instanceof net.minecraft.entity.passive.TameableEntity tameable) {
            return tameable.getOwner() == owner;
        }
        
        return false;
    }
}