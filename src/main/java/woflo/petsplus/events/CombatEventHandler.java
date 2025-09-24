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

        // Check if a pet took damage
        if (entity instanceof MobEntity mobEntity) {
            PetComponent petComponent = PetComponent.get(mobEntity);
            if (petComponent != null) {
                handlePetDamageReceived(mobEntity, petComponent, damageSource, amount);
            }
        }

        // Check if a pet dealt damage
        if (damageSource.getAttacker() instanceof MobEntity attackerMob) {
            PetComponent petComponent = PetComponent.get(attackerMob);
            if (petComponent != null) {
                handlePetDealtDamage(attackerMob, petComponent, entity, amount);
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

        // Push emotions to nearby owned pets: threat/aversion and protectiveness
        long now = owner.getWorld().getTime();
        owner.getWorld().getEntitiesByClass(MobEntity.class,
            owner.getBoundingBox().expand(32),
            mob -> {
                PetComponent pc = PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(owner);
            }
        ).forEach(pet -> {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;
            pc.setLastAttackTick(now);
            pc.pushEmotion(PetComponent.Emotion.ANGST, Math.min(1f, amount * 0.1f));
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.35f);
        });
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

        // Emotions: combat engagement for nearby owned pets
        long now = owner.getWorld().getTime();
        owner.getWorld().getEntitiesByClass(MobEntity.class,
            owner.getBoundingBox().expand(32),
            mob -> {
                PetComponent pc = PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(owner);
            }
        ).forEach(pet -> {
            PetComponent pc = PetComponent.get(pet);
            if (pc == null) return;
            pc.setLastAttackTick(now);
            pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.3f);
            pc.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.1f);
        });
        
        Petsplus.LOGGER.debug("Owner {} dealt {} damage to {}, victim at {}% health", 
            owner.getName().getString(), modifiedDamage, victim.getType().toString(), victimHealthPct * 100);
    }
    
    private static void handleProjectileDamage(PlayerEntity shooter, LivingEntity target, float damage, PersistentProjectileEntity projectile) {
        // Check if this was a critical hit
        boolean wasCrit = projectile.isCritical();
        
        if (wasCrit) {
            triggerAbilitiesForOwner(shooter, "owner_projectile_crit");
            // Zealous drive on big crits
            shooter.getWorld().getEntitiesByClass(MobEntity.class,
                shooter.getBoundingBox().expand(32),
                mob -> {
                    PetComponent pc = PetComponent.get(mob);
                    return pc != null && pc.isOwnedBy(shooter);
                }
            ).forEach(pet -> {
                PetComponent pc = PetComponent.get(pet);
                if (pc != null) {
                    pc.pushEmotion(PetComponent.Emotion.AMAL, 0.25f);
                }
            });
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

    /**
     * Handle when a pet takes damage - triggers fear/anger emotions based on context
     */
    private static void handlePetDamageReceived(MobEntity pet, PetComponent petComponent, DamageSource damageSource, float amount) {
        long now = pet.getWorld().getTime();
        petComponent.setLastAttackTick(now);

        // Scale emotion intensity with damage relative to pet's max health
        float maxHealth = pet.getMaxHealth();
        float damageRatio = Math.min(1.0f, amount / maxHealth);

        Entity attacker = damageSource.getAttacker();
        PlayerEntity owner = petComponent.getOwner();

        // Context-based emotional responses
        if (attacker instanceof PlayerEntity playerAttacker) {
            if (owner != null && playerAttacker.equals(owner)) {
                // Owner accidentally hurt pet - confusion and sadness
                petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, damageRatio * 0.8f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, damageRatio * 0.4f);
                Petsplus.LOGGER.debug("Pet {} hurt by owner, pushed confusion emotions", pet.getName().getString());
            } else {
                // Hostile player - fear and anger
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, damageRatio * 0.6f);
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, damageRatio * 0.5f);
                if (owner != null) {
                    // Extra protectiveness when owner is nearby
                    if (owner.squaredDistanceTo(pet) < 16 * 16) {
                        petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.7f);
                    }
                }
                Petsplus.LOGGER.debug("Pet {} hurt by hostile player, pushed defensive emotions", pet.getName().getString());
            }
        } else if (attacker instanceof MobEntity mobAttacker) {
            PetComponent attackerPetComponent = PetComponent.get(mobAttacker);
            if (attackerPetComponent != null && owner != null && attackerPetComponent.isOwnedBy(owner)) {
                // Friendly fire from another pet - mild confusion
                petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, damageRatio * 0.3f);
                Petsplus.LOGGER.debug("Pet {} hurt by friendly pet, pushed mild confusion", pet.getName().getString());
            } else {
                // Wild/hostile mob - appropriate combat response
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, damageRatio * 0.4f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, damageRatio * 0.3f);

                // Bigger reaction to dangerous mobs
                if (mobAttacker.getMaxHealth() > pet.getMaxHealth() * 1.5f) {
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, damageRatio * 0.3f); // Extra fear
                }
                Petsplus.LOGGER.debug("Pet {} hurt by hostile mob, pushed fear emotions", pet.getName().getString());
            }
        } else {
            // Environmental damage (fall, fire, etc) - frustration and caution
            if (damageSource.isOf(net.minecraft.entity.damage.DamageTypes.FALL)) {
                petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, damageRatio * 0.4f);
            } else if (damageSource.isOf(net.minecraft.entity.damage.DamageTypes.IN_FIRE) ||
                      damageSource.isOf(net.minecraft.entity.damage.DamageTypes.ON_FIRE)) {
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, damageRatio * 0.6f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, damageRatio * 0.4f);
            } else {
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, damageRatio * 0.3f);
            }
            Petsplus.LOGGER.debug("Pet {} hurt by environment, pushed cautious emotions", pet.getName().getString());
        }

        // Health-based fear reactions - pets should feel increasingly fearful as health drops
        float healthAfter = pet.getHealth() - amount;
        float healthAfterPercent = healthAfter / maxHealth;

        if (healthAfterPercent <= 0.25f) {
            // Critical health - extreme fear and panic
            petComponent.pushEmotion(PetComponent.Emotion.ANGST, 1.0f);
            petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, 0.6f);
        } else if (healthAfterPercent <= 0.5f) {
            // Low health - significant fear and concern
            petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.6f);
            petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, 0.4f);
        }
    }

    /**
     * Handle when a pet deals damage - triggers aggressive/triumphant emotions based on context
     */
    private static void handlePetDealtDamage(MobEntity pet, PetComponent petComponent, LivingEntity victim, float damage) {
        long now = pet.getWorld().getTime();
        petComponent.setLastAttackTick(now);

        // Scale emotion with damage dealt
        float damageRatio = Math.min(1.0f, damage / 20.0f); // Scale against typical mob health
        PlayerEntity owner = petComponent.getOwner();

        // Check pet's own health state to adjust emotional response
        float petHealthPercent = pet.getHealth() / pet.getMaxHealth();
        boolean petIsLowHealth = petHealthPercent <= 0.5f;
        boolean petIsCritical = petHealthPercent <= 0.25f;

        // Context-based responses for different victim types
        if (victim instanceof PlayerEntity playerVictim) {
            if (owner != null && playerVictim.equals(owner)) {
                // Pet accidentally hurt owner - extreme guilt and confusion
                petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, 1.0f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.6f);
                Petsplus.LOGGER.debug("Pet {} accidentally hurt owner, pushed guilt emotions", pet.getName().getString());
            } else {
                // Pet defending against hostile player - emotions vary by pet's health state
                if (petIsCritical) {
                    // Critical health - mostly fear, desperate fighting
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.8f);
                    petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, damageRatio * 0.7f);
                    petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.3f); // Much reduced protectiveness
                } else if (petIsLowHealth) {
                    // Low health - mixed fear and diminished protectiveness
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.5f);
                    petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.5f); // Reduced protectiveness
                    petComponent.pushEmotion(PetComponent.Emotion.AMAL, damageRatio * 0.4f);
                } else {
                    // Healthy - strong protective instincts
                    petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.8f);
                    petComponent.pushEmotion(PetComponent.Emotion.AMAL, damageRatio * 0.6f);
                }

                // Extra emotions if owner is nearby and in danger (but not if pet is critical)
                if (!petIsCritical && owner != null && owner.squaredDistanceTo(pet) < 16 * 16 && owner.getHealth() / owner.getMaxHealth() < 0.5f) {
                    petComponent.pushEmotion(PetComponent.Emotion.SISU, 0.5f); // Determined to protect
                }
                Petsplus.LOGGER.debug("Pet {} defending against hostile player, health-adjusted emotions", pet.getName().getString());
            }
        } else if (victim instanceof MobEntity mobVictim) {
            PetComponent victimPetComponent = PetComponent.get(mobVictim);
            if (victimPetComponent != null && owner != null && victimPetComponent.isOwnedBy(owner)) {
                // Fighting another friendly pet - confusion and distress
                petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, damageRatio * 0.8f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, damageRatio * 0.4f);
                Petsplus.LOGGER.debug("Pet {} fighting friendly pet, pushed distress emotions", pet.getName().getString());
            } else {
                // Fighting wild/hostile mob - emotions vary by pet's health state
                if (petIsCritical) {
                    // Critical health - desperate survival, less protectiveness
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.6f);
                    petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, damageRatio * 0.6f);
                    petComponent.pushEmotion(PetComponent.Emotion.AMAL, damageRatio * 0.2f); // Reduced zealousness
                } else if (petIsLowHealth) {
                    // Low health - mixed fear with fighting spirit
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.3f);
                    petComponent.pushEmotion(PetComponent.Emotion.AMAL, damageRatio * 0.3f);
                    petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.2f); // Reduced protectiveness
                } else {
                    // Healthy - normal combat emotions
                    petComponent.pushEmotion(PetComponent.Emotion.AMAL, damageRatio * 0.4f);
                    petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.3f);
                }

                // Extra satisfaction against dangerous enemies (but not if pet is critical)
                if (!petIsCritical && mobVictim.getMaxHealth() > pet.getMaxHealth()) {
                    petComponent.pushEmotion(PetComponent.Emotion.SISU, damageRatio * 0.3f); // Pride in facing stronger foe
                }

                // Different emotions for different mob types
                if (mobVictim instanceof net.minecraft.entity.mob.HostileEntity) {
                    // Fighting monsters - righteous anger
                    petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, damageRatio * 0.2f);
                } else if (mobVictim instanceof net.minecraft.entity.passive.AnimalEntity) {
                    // Attacking peaceful animals - mild guilt (unless protecting owner)
                    if (owner != null && owner.squaredDistanceTo(pet) > 8 * 8) {
                        petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, damageRatio * 0.2f);
                    }
                }
                Petsplus.LOGGER.debug("Pet {} fighting hostile mob, pushed combat emotions", pet.getName().getString());
            }
        }

        // Bonus emotions for critical hits or high damage
        if (damage > pet.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE) * 1.5) {
            petComponent.pushEmotion(PetComponent.Emotion.FROHLICH, 0.2f); // Joy from powerful strike
        }

        // Check if this killed the victim
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
                // Pet killed owner - extreme trauma and confusion
                petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, 1.0f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, 1.0f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, 0.8f);
                Petsplus.LOGGER.debug("Pet {} killed owner - extreme trauma emotions", pet.getName().getString());
                return; // Don't add positive emotions
            } else {
                // Pet killed hostile player - strong protective satisfaction
                petComponent.pushEmotion(PetComponent.Emotion.SISU, 0.8f);
                petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.7f);
                petComponent.pushEmotion(PetComponent.Emotion.FROHLICH, 0.4f);

                // Extra pride if protecting owner
                if (owner != null && owner.squaredDistanceTo(pet) < 16 * 16) {
                    petComponent.pushEmotion(PetComponent.Emotion.AMAL, 0.3f);
                }
                Petsplus.LOGGER.debug("Pet {} killed hostile player - protective triumph", pet.getName().getString());
            }
        } else if (victim instanceof MobEntity mobVictim) {
            PetComponent victimPetComponent = PetComponent.get(mobVictim);
            if (victimPetComponent != null && owner != null && victimPetComponent.isOwnedBy(owner)) {
                // Pet killed friendly pet - severe guilt and trauma
                petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, 0.9f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.7f);
                Petsplus.LOGGER.debug("Pet {} killed friendly pet - guilt and trauma", pet.getName().getString());
                return; // Don't add positive emotions
            } else {
                // Killed wild/hostile mob - appropriate triumph
                petComponent.pushEmotion(PetComponent.Emotion.FROHLICH, 0.6f);
                petComponent.pushEmotion(PetComponent.Emotion.SISU, 0.5f);
                petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, 0.4f);

                // Extra emotions based on victim type
                if (mobVictim instanceof net.minecraft.entity.mob.HostileEntity) {
                    // Killed monster - righteous satisfaction
                    petComponent.pushEmotion(PetComponent.Emotion.AMAL, 0.3f);

                    // Boss monsters give extra pride
                    if (mobVictim.getMaxHealth() > 100) {
                        petComponent.pushEmotion(PetComponent.Emotion.SISU, 0.4f);
                    }
                } else if (mobVictim instanceof net.minecraft.entity.passive.AnimalEntity) {
                    // Killed peaceful animal - some guilt unless protecting owner
                    if (owner == null || owner.squaredDistanceTo(pet) > 8 * 8) {
                        petComponent.pushEmotion(PetComponent.Emotion.WELTSCHMERZ, 0.3f);
                    }
                }

                // First kill bonus - extra excitement
                Integer killCount = petComponent.getStateData("kill_count", Integer.class);
                if (killCount == null || killCount == 0) {
                    petComponent.pushEmotion(PetComponent.Emotion.FROHLICH, 0.3f);
                    petComponent.setStateData("kill_count", 1);
                } else {
                    petComponent.setStateData("kill_count", killCount + 1);
                }

                Petsplus.LOGGER.debug("Pet {} killed {}, pushed triumphant emotions",
                    pet.getName().getString(), victim.getType().toString());
            }
        }
    }
}