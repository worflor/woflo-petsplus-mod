package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

/**
 * Handles combat-related events and triggers pet abilities accordingly.
 */
public class CombatEventHandler {

    private static final String CHIP_DAMAGE_ACCUM_KEY = "restless_chip_accum";
    private static final String CHIP_DAMAGE_LAST_TICK_KEY = "restless_chip_last_tick";
    private static final String CHIP_DAMAGE_OWNER_ACCUM_KEY = "restless_owner_chip_accum";
    private static final String CHIP_DAMAGE_OWNER_LAST_TICK_KEY = "restless_owner_chip_last_tick";
    private static final float CHIP_DAMAGE_RATIO_CEILING = 0.30f; // ≤30% max health counts as chip damage
    private static final float CHIP_DAMAGE_THRESHOLD = 0.45f;      // Accumulated chip ratio before surging restlessness
    private static final int CHIP_DAMAGE_DECAY_TICKS = 80;         // ~4 seconds for decay calculations
    private static final float CHIP_DAMAGE_DECAY_STEP = 0.18f;     // Amount removed per decay interval
    private static final float CHIP_DAMAGE_BASE_STARTLE = 0.03f;
    private static final float CHIP_DAMAGE_STARTLE_SCALE = 0.50f;
    
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
        float maxHealth = Math.max(1f, owner.getMaxHealth());
        double healthPct = healthAfter / maxHealth;
        
        if (healthPct <= 0.35) { // 35% health threshold
            triggerAbilitiesForOwner(owner, "on_owner_low_health");
        }
        
        // Check for pet proximity and trigger guardian abilities
        triggerNearbyPetAbilities(owner, "owner_damage_taken");

        float damageIntensity = damageIntensity(amount, maxHealth);
        float ownerDangerFactor = missingHealthFactor((float) healthPct, 0.6f);

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

            float closeness = 0.35f + 0.65f * proximityFactor(owner, pet, 32.0);
            float angstWeight = scaledAmount(0.16f, 0.50f, damageIntensity) * closeness;
            float protectivenessWeight = scaledAmount(0.20f + (0.18f * ownerDangerFactor), 0.55f, damageIntensity) * closeness;
            float startleWeight = scaledAmount(0.10f, 0.40f, damageIntensity);
            float frustrationWeight = scaledAmount(0.04f, 0.30f, damageIntensity) * closeness;
            float forebodingWeight = ownerDangerFactor > 0f
                ? scaledAmount(0.05f, 0.40f, Math.max(ownerDangerFactor, damageIntensity)) * closeness
                : 0f;

            if (angstWeight > 0f) {
                pc.pushEmotion(PetComponent.Emotion.ANGST, angstWeight);
            }
            if (protectivenessWeight > 0f) {
                pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protectivenessWeight);
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
        float victimMaxHealth = Math.max(1f, victim.getMaxHealth());
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
        float intensity = damageIntensity(modifiedDamage, victimMaxHealth);
        float finishFactor = missingHealthFactor((float) victimHealthPct, 0.35f);
        boolean victimIsHostile = victim instanceof HostileEntity;

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

            float closeness = 0.35f + 0.65f * proximityFactor(owner, pet, 32.0);
            float protectivenessWeight = scaledAmount(0.18f, 0.45f, intensity) * closeness;
            float hopefulWeight = victimIsHostile
                ? scaledAmount(0.12f, 0.32f, Math.max(intensity, finishFactor)) * (0.75f + 0.25f * closeness)
                : 0f;
            float stoicWeight = finishFactor > 0f
                ? scaledAmount(0.05f, 0.30f, finishFactor) * (0.65f + 0.35f * closeness)
                : 0f;

            if (victimIsHostile) {
                float fervorWeight = scaledAmount(0.08f, 0.30f, intensity) * closeness;
                float frustrationWeight = scaledAmount(0.05f, 0.26f, Math.max(intensity, finishFactor)) * closeness;
                if (fervorWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.KEFI, fervorWeight);
                }
                if (frustrationWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.FRUSTRATION, frustrationWeight);
                }
            } else {
                float regretWeight = scaledAmount(0.08f, 0.30f, intensity);
                float wistfulWeight = scaledAmount(0.04f, 0.22f, Math.max(intensity, finishFactor)) * (0.6f + 0.4f * closeness);
                if (regretWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.REGRET, regretWeight);
                }
                if (wistfulWeight > 0f) {
                    pc.pushEmotion(PetComponent.Emotion.HIRAETH, wistfulWeight);
                }
            }

            if (protectivenessWeight > 0f) {
                pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protectivenessWeight);
            }
            if (hopefulWeight > 0f) {
                pc.pushEmotion(PetComponent.Emotion.HOPEFUL, hopefulWeight);
            }
            if (stoicWeight > 0f) {
                pc.pushEmotion(PetComponent.Emotion.STOIC, stoicWeight);
            }
        });

        Petsplus.LOGGER.debug("Owner {} dealt {} damage to {}, victim at {}% health",
            owner.getName().getString(), modifiedDamage, victim.getType().toString(), victimHealthPct * 100);
    }
    
    private static void handleProjectileDamage(PlayerEntity shooter, LivingEntity target, float damage, PersistentProjectileEntity projectile) {
        // Check if this was a critical hit
        boolean wasCrit = projectile.isCritical();
        
        if (wasCrit) {
            triggerAbilitiesForOwner(shooter, "owner_projectile_crit");

            float intensity = damageIntensity(damage, target.getMaxHealth());
            long now = shooter.getWorld().getTime();

            shooter.getWorld().getEntitiesByClass(MobEntity.class,
                shooter.getBoundingBox().expand(32),
                mob -> {
                    PetComponent pc = PetComponent.get(mob);
                    return pc != null && pc.isOwnedBy(shooter);
                }
            ).forEach(pet -> {
                PetComponent pc = PetComponent.get(pet);
                if (pc != null) {
                    pc.setLastAttackTick(now);
                    float closeness = 0.40f + 0.60f * proximityFactor(shooter, pet, 32.0);
                    float hopefulWeight = scaledAmount(0.16f, 0.45f, intensity) * closeness;
                    float cheerfulWeight = scaledAmount(0.08f, 0.25f, intensity);
                    float protectivenessWeight = scaledAmount(0.10f, 0.30f, intensity) * closeness;

                    pc.pushEmotion(PetComponent.Emotion.HOPEFUL, hopefulWeight);
                    pc.pushEmotion(PetComponent.Emotion.CHEERFUL, cheerfulWeight);
                    pc.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protectivenessWeight);
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

        float maxHealth = pet.getMaxHealth();
        float damageIntensity = damageIntensity(amount, maxHealth);
        float healthAfter = pet.getHealth() - amount;
        float healthAfterPercent = maxHealth > 0f ? MathHelper.clamp(healthAfter / maxHealth, 0f, 1f) : 0f;
        float lowHealthFactor = missingHealthFactor(healthAfterPercent, 0.5f);
        float criticalFactor = missingHealthFactor(healthAfterPercent, 0.25f);

        Entity attacker = damageSource.getAttacker();
        PlayerEntity owner = petComponent.getOwner();
        boolean petIsCursed = petComponent.hasRole(PetRoleType.CURSED_ONE);

        float startleWeight = scaledAmount(0.10f, 0.55f, damageIntensity);
        if (startleWeight > 0f) {
            petComponent.pushEmotion(PetComponent.Emotion.STARTLE, startleWeight);
        }

        if (attacker instanceof PlayerEntity playerAttacker) {
            if (owner != null && playerAttacker.equals(owner)) {
                if (petIsCursed) {
                    float roughhouse = scaledAmount(0.08f, 0.35f, damageIntensity);
                    petComponent.pushEmotion(PetComponent.Emotion.KEFI, roughhouse);
                    petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, roughhouse * 0.45f);
                } else {
                    float heartbreak = scaledAmount(0.24f, 0.60f, damageIntensity);
                    float longing = scaledAmount(0.12f, 0.35f, damageIntensity + (lowHealthFactor * 0.4f));
                    float worry = scaledAmount(0.10f, 0.32f, damageIntensity + (criticalFactor * 0.6f));
                    float fear = scaledAmount(0.14f, 0.40f, damageIntensity + (lowHealthFactor * 0.5f));
                    petComponent.pushEmotion(PetComponent.Emotion.HIRAETH, heartbreak);
                    petComponent.pushEmotion(PetComponent.Emotion.SAUDADE, longing);
                    petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, worry);
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, fear);
                }
                Petsplus.LOGGER.debug("Pet {} hurt by owner, pushed owner-related emotions", pet.getName().getString());
            } else {
                float panic = scaledAmount(0.18f, 0.55f, damageIntensity + (lowHealthFactor * 0.4f));
                float defiance = scaledAmount(0.12f, 0.45f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, panic);
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, defiance);
                if (owner != null) {
                    float closeness = 0.40f + 0.60f * proximityFactor(owner, pet, 16.0);
                    float protective = scaledAmount(0.16f, 0.45f, damageIntensity + (lowHealthFactor * 0.3f)) * closeness;
                    petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protective);
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
            } else {
                float dread = scaledAmount(0.15f, 0.45f, damageIntensity + (lowHealthFactor * 0.3f));
                float caution = scaledAmount(0.10f, 0.40f, Math.max(damageIntensity, lowHealthFactor));
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, dread);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, caution);
                if (mobAttacker.getMaxHealth() > pet.getMaxHealth() * 1.5f) {
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.08f, 0.30f, damageIntensity));
                    petComponent.pushEmotion(PetComponent.Emotion.STOIC, scaledAmount(0.05f, 0.25f, damageIntensity));
                }
                if (owner != null) {
                    float closeness = 0.30f + 0.70f * proximityFactor(owner, pet, 16.0);
                    float protective = scaledAmount(0.14f, 0.40f, damageIntensity) * closeness;
                    petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protective);
                }
                Petsplus.LOGGER.debug("Pet {} hurt by hostile mob, pushed fear emotions", pet.getName().getString());
            }
        } else {
            if (damageSource.isOf(net.minecraft.entity.damage.DamageTypes.FALL)) {
                float stumble = scaledAmount(0.12f, 0.35f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, scaledAmount(0.07f, 0.28f, damageIntensity));
                petComponent.pushEmotion(PetComponent.Emotion.GAMAN, scaledAmount(0.05f, 0.25f, Math.max(damageIntensity, stumble)));
            } else if (damageSource.isOf(net.minecraft.entity.damage.DamageTypes.IN_FIRE) ||
                      damageSource.isOf(net.minecraft.entity.damage.DamageTypes.ON_FIRE)) {
                float panic = scaledAmount(0.18f, 0.55f, damageIntensity + (lowHealthFactor * 0.3f));
                float dread = scaledAmount(0.12f, 0.45f, Math.max(damageIntensity, lowHealthFactor));
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, panic);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, dread);
            } else {
                float caution = scaledAmount(0.08f, 0.35f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, caution);
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, scaledAmount(0.05f, 0.25f, damageIntensity));
                petComponent.pushEmotion(PetComponent.Emotion.GAMAN, scaledAmount(0.04f, 0.20f, Math.max(damageIntensity, lowHealthFactor)));
            }
            Petsplus.LOGGER.debug("Pet {} hurt by environment, pushed cautious emotions", pet.getName().getString());
        }

        // Chip damage accumulation → restlessness buildup
        applyChipDamageRestlessness(pet, petComponent, amount, damageIntensity, attacker, owner);

        if (lowHealthFactor > 0f) {
            float panic = scaledAmount(0.12f, 0.45f, lowHealthFactor);
            float dread = scaledAmount(0.08f, 0.35f, lowHealthFactor);
            petComponent.pushEmotion(PetComponent.Emotion.ANGST, panic);
            petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, dread);
        }

        if (criticalFactor > 0f) {
            float desperation = scaledAmount(0.10f, 0.30f, criticalFactor);
            petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, desperation * 0.7f);
            petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, desperation);
        }
    }

    /**
     * Handle when a pet deals damage - triggers aggressive/triumphant emotions based on context
     */
    private static void handlePetDealtDamage(MobEntity pet, PetComponent petComponent, LivingEntity victim, float damage) {
        long now = pet.getWorld().getTime();
        petComponent.setLastAttackTick(now);

        float victimMaxHealth = Math.max(1f, victim.getMaxHealth());
        float damageIntensity = damageIntensity(damage, victimMaxHealth);
        PlayerEntity owner = petComponent.getOwner();

        float petHealthPercent = pet.getMaxHealth() > 0f ? MathHelper.clamp(pet.getHealth() / pet.getMaxHealth(), 0f, 1f) : 1f;
        float lowHealthFactor = missingHealthFactor(petHealthPercent, 0.5f);
        float criticalFactor = missingHealthFactor(petHealthPercent, 0.25f);

        if (victim instanceof PlayerEntity playerVictim) {
            if (owner != null && playerVictim.equals(owner)) {
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

            float closeness = owner != null ? 0.40f + 0.60f * proximityFactor(owner, pet, 16.0) : 0.75f;
            float protectiveness = scaledAmount(0.18f, 0.50f, damageIntensity) * closeness;
            float hopeful = scaledAmount(0.12f, 0.40f, damageIntensity) * (1f - (0.5f * lowHealthFactor));
            float frustration = scaledAmount(0.10f, 0.35f, damageIntensity + (lowHealthFactor * 0.3f));
            petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protectiveness);
            petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, hopeful);
            petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, frustration);

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
                float guilt = scaledAmount(0.26f, 0.45f, damageIntensity);
                float worry = scaledAmount(0.16f, 0.35f, damageIntensity + (lowHealthFactor * 0.3f));
                petComponent.pushEmotion(PetComponent.Emotion.REGRET, guilt);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, worry);
                Petsplus.LOGGER.debug("Pet {} fighting friendly pet, pushed distress emotions", pet.getName().getString());
            } else {
                float hopeful = scaledAmount(0.14f, 0.45f, damageIntensity) * (1f - (0.4f * lowHealthFactor));
                float frustration = scaledAmount(0.08f, 0.35f, damageIntensity + (lowHealthFactor * 0.3f));
                float protective = owner != null
                    ? scaledAmount(0.12f, 0.35f, damageIntensity) * (0.5f + 0.5f * proximityFactor(owner, pet, 16.0))
                    : scaledAmount(0.10f, 0.30f, damageIntensity);
                petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, hopeful);
                petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protective);

                if (criticalFactor > 0f) {
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.16f, 0.40f, criticalFactor));
                } else if (lowHealthFactor > 0f) {
                    petComponent.pushEmotion(PetComponent.Emotion.ANGST, scaledAmount(0.10f, 0.35f, lowHealthFactor));
                }

                if (mobVictim.getMaxHealth() > pet.getMaxHealth()) {
                    petComponent.pushEmotion(PetComponent.Emotion.STOIC, scaledAmount(0.06f, 0.30f, damageIntensity));
                }

                if (mobVictim instanceof HostileEntity) {
                    float hostilityPush = scaledAmount(0.05f, 0.24f, damageIntensity);
                    frustration = MathHelper.clamp(frustration + hostilityPush, 0f, 1f);
                }

                if (mobVictim instanceof net.minecraft.entity.passive.AnimalEntity) {
                    if (owner == null || owner.squaredDistanceTo(pet) > 8 * 8) {
                        petComponent.pushEmotion(PetComponent.Emotion.REGRET, scaledAmount(0.08f, 0.25f, damageIntensity));
                    }
                }
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, frustration);
                Petsplus.LOGGER.debug("Pet {} fighting hostile mob, pushed combat emotions", pet.getName().getString());
            }
        }

        if (damage > pet.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE) * 1.5f) {
            float celebration = scaledAmount(0.10f, 0.25f, damageIntensity);
            petComponent.pushEmotion(PetComponent.Emotion.CHEERFUL, celebration);
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
                petComponent.pushEmotion(PetComponent.Emotion.REGRET, 0.9f);
                petComponent.pushEmotion(PetComponent.Emotion.HIRAETH, 0.8f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.9f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, 0.7f);
                Petsplus.LOGGER.debug("Pet {} killed owner - extreme trauma emotions", pet.getName().getString());
                return;
            }

            float closeness = owner != null ? 0.40f + 0.60f * proximityFactor(owner, pet, 16.0) : 0.7f;
            float triumphScale = MathHelper.clamp(closeness, 0f, 1f);
            petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, scaledAmount(0.26f, 0.45f, triumphScale));
            petComponent.pushEmotion(PetComponent.Emotion.STOIC, scaledAmount(0.20f, 0.40f, triumphScale));
            petComponent.pushEmotion(PetComponent.Emotion.CHEERFUL, scaledAmount(0.14f, 0.30f, triumphScale));

            if (owner != null && closeness > 0.5f) {
                petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, scaledAmount(0.10f, 0.25f, triumphScale));
            }
            Petsplus.LOGGER.debug("Pet {} killed hostile player - protective triumph", pet.getName().getString());
        } else if (victim instanceof MobEntity mobVictim) {
            PetComponent victimPetComponent = PetComponent.get(mobVictim);
            if (victimPetComponent != null && owner != null && victimPetComponent.isOwnedBy(owner)) {
                petComponent.pushEmotion(PetComponent.Emotion.REGRET, 0.85f);
                petComponent.pushEmotion(PetComponent.Emotion.HIRAETH, 0.7f);
                petComponent.pushEmotion(PetComponent.Emotion.ANGST, 0.7f);
                petComponent.pushEmotion(PetComponent.Emotion.FOREBODING, 0.55f);
                Petsplus.LOGGER.debug("Pet {} killed friendly pet - guilt and trauma", pet.getName().getString());
                return;
            }

            float relativeStrength = mobVictim.getMaxHealth() / Math.max(1f, pet.getMaxHealth());
            float triumphScale = MathHelper.clamp((relativeStrength - 0.25f) / 1.75f, 0f, 1f);
            float protectiveBase = scaledAmount(0.18f, 0.40f, triumphScale);
            float stoicBase = scaledAmount(0.16f, 0.35f, triumphScale);
            float cheerfulBase = scaledAmount(0.14f, 0.30f, triumphScale);

            petComponent.pushEmotion(PetComponent.Emotion.CHEERFUL, cheerfulBase);
            petComponent.pushEmotion(PetComponent.Emotion.STOIC, stoicBase);
            petComponent.pushEmotion(PetComponent.Emotion.PROTECTIVENESS, protectiveBase);

            if (mobVictim instanceof HostileEntity) {
                petComponent.pushEmotion(PetComponent.Emotion.HOPEFUL, scaledAmount(0.12f, 0.30f, triumphScale));
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
    }

    private static float scaledAmount(float base, float scale, float intensity) {
        return MathHelper.clamp(base + (scale * MathHelper.clamp(intensity, 0f, 1f)), 0f, 1f);
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

    private static void applyChipDamageRestlessness(
        MobEntity pet,
        PetComponent petComponent,
        float amount,
        float damageRatio,
        Entity attacker,
        PlayerEntity owner
    ) {
        if (amount <= 0.0f) {
            return;
        }

        // Ignore obvious burst damage - that is handled by the fear system
        if (damageRatio > CHIP_DAMAGE_RATIO_CEILING) {
            return;
        }

        long now = pet.getWorld().getTime();
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

        accum += damageRatio;
        petComponent.setStateData(lastTickKey, now);

        float startle = scaledAmount(CHIP_DAMAGE_BASE_STARTLE, CHIP_DAMAGE_STARTLE_SCALE, damageRatio);
        if (treatAsOwnerSadness) {
            startle *= 0.55f;
        } else if (isOwnerAttack) {
            startle *= 1.15f; // Cursed One enjoys the roughhousing, others treat owner attacks as harsher than mobs
        } else if (attacker instanceof PlayerEntity) {
            startle *= 1.05f;
        }
        petComponent.pushEmotion(PetComponent.Emotion.STARTLE, startle);

        if (treatAsOwnerSadness) {
            petComponent.pushEmotion(PetComponent.Emotion.ENNUI, scaledAmount(0.16f, 0.35f, damageRatio));
            petComponent.pushEmotion(PetComponent.Emotion.SAUDADE, scaledAmount(0.06f, 0.22f, damageRatio));
        } else {
            float frustration = scaledAmount(0.04f, 0.28f, damageRatio);
            petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, frustration);
        }

        while (accum >= CHIP_DAMAGE_THRESHOLD) {
            if (treatAsOwnerSadness) {
                petComponent.pushEmotion(PetComponent.Emotion.ENNUI, 0.24f);
                petComponent.pushEmotion(PetComponent.Emotion.SAUDADE, 0.12f);
                petComponent.pushEmotion(PetComponent.Emotion.STARTLE, 0.08f);
            } else {
                petComponent.pushEmotion(PetComponent.Emotion.STARTLE, 0.12f);
                petComponent.pushEmotion(PetComponent.Emotion.ENNUI, 0.10f);
                petComponent.pushEmotion(PetComponent.Emotion.FRUSTRATION, 0.12f);
            }
            accum -= CHIP_DAMAGE_THRESHOLD * 0.6f;
        }

        if (accum < 0.001f) {
            petComponent.setStateData(accumKey, 0f);
        } else {
            petComponent.setStateData(accumKey, accum);
        }
    }
}
