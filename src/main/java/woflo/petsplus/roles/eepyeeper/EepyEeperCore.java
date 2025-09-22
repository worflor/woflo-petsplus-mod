package woflo.petsplus.roles.eepyeeper;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.ParticleTypes;
import woflo.petsplus.advancement.AdvancementManager;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
/**

 * Core Eepy Eeper mechanics implementation following the story requirements.

 */

public class EepyEeperCore {

    private static final Map<UUID, Integer> sleepCyclesRemaining = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> lastSleepTime = new ConcurrentHashMap<>();

    public static void initialize() {

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EepyEeperCore::onEntityDamage);

        ServerTickEvents.END_WORLD_TICK.register(EepyEeperCore::onWorldTick);

    }

    /**

     * Handle Eepy Eeper damage interception and Dream's Escape

     */

    private static boolean onEntityDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {

        // Handle Dream's Escape for lethal damage

        if (entity instanceof ServerPlayerEntity player) {

            return handleDreamEscape(player, damageSource, damageAmount);

        }

        // Handle Phantom immunity

        if (entity instanceof PlayerEntity player && damageSource.getAttacker() instanceof PhantomEntity) {

            return handlePhantomImmunity(player);

        }

        return true; // Allow damage

    }

    /**

     * Handle Dream's Escape mechanic - L30 ability

     */

    private static boolean handleDreamEscape(ServerPlayerEntity player, DamageSource damageSource, float damageAmount) {

        // Check if this would be lethal damage

        if (player.getHealth() - damageAmount > 0) {

            return true; // Not lethal, allow damage

        }

        // Find nearby Eepy Eeper pets at L30

        List<MobEntity> eepyPets = findNearbyEepyEepers(player, 8.0);

        MobEntity dreamEscapePet = null;

        for (MobEntity pet : eepyPets) {

            PetComponent petComp = PetComponent.get(pet);

            if (petComp != null && petComp.getLevel() >= 30) {

                // Check if pet is not knocked out

                if (!isPetKnockedOut(pet.getUuid())) {

                    dreamEscapePet = pet;

                    break;

                }

            }

        }

        if (dreamEscapePet == null) {

            return true; // No suitable pet, allow death

        }

        // Check cooldown (once per 3 sleep cycles)

        if (!canUseDreamEscape(player)) {

            return true; // On cooldown, allow death

        }

        // Perform Dream's Escape

        return performDreamEscape(player, dreamEscapePet, damageSource, damageAmount);

    }

    /**

     * Perform the Dream's Escape rescue

     */

    private static boolean performDreamEscape(ServerPlayerEntity player, MobEntity pet, DamageSource damageSource, float damageAmount) {

        try {

            // Cancel death and heal to minimal health

            player.setHealth(1.0f);

            // Apply Blindness I (30s) and remove all XP

            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 600, 0));

            player.totalExperience = 0;

            player.experienceLevel = 0;

            player.experienceProgress = 0.0f;

            // Teleport to respawn point

            net.minecraft.server.network.ServerPlayerEntity.Respawn respawn = player.getRespawn();

            ServerWorld serverWorld = player.getWorld();

            if (respawn != null) {

                // Use respawn position

                BlockPos respawnPos = respawn.pos();

                player.teleport(serverWorld,

                    respawnPos.getX() + 0.5, respawnPos.getY() + 1, respawnPos.getZ() + 0.5,

                    java.util.Set.of(), respawn.angle(), 0.0f, false);

            } else {

                // Teleport to world spawn if no respawn set

                BlockPos worldSpawn = serverWorld.getSpawnPos();

                player.teleport(serverWorld,

                    worldSpawn.getX() + 0.5, worldSpawn.getY() + 1, worldSpawn.getZ() + 0.5,

                    java.util.Set.of(), player.getYaw(), player.getPitch(), false);

            }

            // Trigger advancement

            AdvancementManager.triggerDreamEscape(player);

            // Create dramatic Dream's Escape visual/audio effects

            createDreamEscapeEffects(player, pet, serverWorld);

            playPetKnockoutEffect(serverWorld, pet);

            sacrificeDreamEscapePet(serverWorld, player, pet);

            return false; // Prevent death

        } catch (Exception e) {

            woflo.petsplus.Petsplus.LOGGER.error("Dream's Escape failed", e);

            return true; // Allow death if escape fails

        }

    }

    private static void sacrificeDreamEscapePet(ServerWorld world, ServerPlayerEntity player, MobEntity pet) {

        if (!pet.isAlive()) {

            sleepCyclesRemaining.remove(pet.getUuid());

            return;

        }

        sleepCyclesRemaining.remove(pet.getUuid());

        String petName = pet.hasCustomName()
            ? pet.getCustomName().getString()
            : pet.getType().getName().getString();

        pet.damage(world, pet.getDamageSources().magic(), Float.MAX_VALUE);

        player.sendMessage(Text.of("Ac" + petName + " A6vanished so you could wake up..."), false);

    }

    /**

     * Handle phantom immunity for players with living Eepy Eeper pets

     */

    private static boolean handlePhantomImmunity(PlayerEntity player) {

        List<MobEntity> eepyPets = findNearbyEepyEepers(player, Double.MAX_VALUE); // Check all loaded eepy pets

        for (MobEntity pet : eepyPets) {

            PetComponent petComp = PetComponent.get(pet);

            if (petComp != null && pet.isAlive()) {

                // Player has living Eepy Eeper, immune to phantoms

                createPhantomProtectionEffect(player, pet, (ServerWorld) player.getWorld());

                player.sendMessage(Text.of("§9✦ Your sleepy companion wards off nightmares... ✦"), true);

                return false; // Prevent phantom damage

            }

        }

        return true; // No living eepy pets, allow phantom damage

    }

    // ---- Visual/audio helpers (stubs) -------------------------------------------------

    private static void createDreamEscapeEffects(ServerPlayerEntity player, MobEntity pet, ServerWorld world) {

        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CHORUS_FLOWER_GROW, SoundCategory.PLAYERS, 0.6f, 1.2f);

        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 0.4f, 1.4f);

        for (int i = 0; i < 18; i++) {

            double ox = (world.random.nextDouble() - 0.5) * 1.6;

            double oy = world.random.nextDouble() * 1.2;

            double oz = (world.random.nextDouble() - 0.5) * 1.6;

            world.spawnParticles(ParticleTypes.GLOW, player.getX() + ox, player.getY() + 0.8 + oy, player.getZ() + oz, 1, 0.0, 0.0, 0.0, 0.0);

        }

        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,

            pet.getX(), pet.getY() + 0.7, pet.getZ(),

            8, 0.4, 0.25, 0.4, 0.012);

    }

    private static void createPhantomProtectionEffect(PlayerEntity player, MobEntity pet, ServerWorld world) {

        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.PLAYERS, 0.3f, 1.5f);

        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_SPORE_BLOSSOM_STEP, SoundCategory.PLAYERS, 0.2f, 1.6f);

        Vec3d p = pet.getPos();

        world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y + 1.2, p.z, 10, 0.4, 0.3, 0.4, 0.02);

        world.spawnParticles(ParticleTypes.GLOW,

            player.getX(), player.getY() + 1.2, player.getZ(),

            6, 0.35, 0.45, 0.35, 0.01);

    }

    /**

     * Handle sleep events for Eepy Eeper mechanics

     */

    public static void onPlayerSleep(ServerPlayerEntity player) {

        List<MobEntity> eepyPets = findNearbyEepyEepers(player, 16.0);

        if (!(player.getWorld() instanceof ServerWorld world)) {

            return;

        }

        boolean sharedAny = false;

        boolean restfulDreamsActive = false;

        for (MobEntity pet : eepyPets) {

            PetComponent petComp = PetComponent.get(pet);

            if (petComp == null) continue;

            // Heal pet to full and restore owner hunger (baseline)

            pet.setHealth(pet.getMaxHealth());

            player.getHungerManager().setFoodLevel(20);

            player.getHungerManager().setSaturationLevel(20.0f);

            // Special Eepy Eeper sleep bonus: configurable chance to gain 1 level (balances slower learning rate)
            // Only applies if not at tribute gate and not max level
            float sleepLevelUpChance = (float) PetsPlusConfig.getInstance().getDouble("eepy_eeper", "sleepLevelUpChance", 0.5);
            if (petComp.getLevel() < 30 && !petComp.isWaitingForTribute() && world.random.nextFloat() < sleepLevelUpChance) {
                boolean leveled = handleSleepLevelUp(petComp, player, pet, world);
                if (leveled) {
                    String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
                    player.sendMessage(Text.of("§6✨ " + petName + " §egained a level while dreaming! Sweet dreams grant wisdom. §6✨"), false);
                    
                    // Play special sleep level-up sound
                    world.playSound(null, pet.getX(), pet.getY(), pet.getZ(), 
                        net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 
                        net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1.5f);
                    
                    // Simple particle effect for sleep level-up
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                        pet.getX(), pet.getY() + pet.getHeight() * 0.8, pet.getZ(),
                        8, 0.5, 0.3, 0.5, 0.05);
                }
            }

            boolean empowered = petComp.getLevel() >= 20;

            // L20+ Restful Dreams bonuses

            if (empowered) {

                // Grant Regeneration I (10s) and Saturation II (10s) to owner

                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));

                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 200, 1));

                // Grant Resistance I (10s) to pet

                pet.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));

                // Grant bonus Pet XP

                int bonusXP = PetsPlusConfig.getInstance().getInt("eepy_eeper", "bonusPetXpPerSleep", 25);

                petComp.addExperience(bonusXP);

                player.sendMessage(Text.of("A6Restful Dreams: Your Eepy Eeper shares peaceful slumber benefits!"), true);

                restfulDreamsActive = true;

            }

            emitSleepLinkParticles(player, pet, empowered);

            sharedAny = true;

        }

        if (sharedAny) {

            playSleepShareSound(world, player, restfulDreamsActive);

        }

        // Handle knocked out pets - reduce sleep cycles needed

        for (UUID petUuid : sleepCyclesRemaining.keySet()) {

            int remaining = sleepCyclesRemaining.get(petUuid);

            if (remaining > 0) {

                remaining--;

                if (remaining <= 0) {

                    sleepCyclesRemaining.remove(petUuid);

                    player.sendMessage(Text.of("A6Your knocked out Eepy Eeper stirs... they're ready to protect you again!"), false);

                    Entity entity = world.getEntity(petUuid);

                    if (entity instanceof MobEntity recoveredPet) {

                        playPetRecoveryCue(world, recoveredPet);

                    }

                } else {

                    sleepCyclesRemaining.put(petUuid, remaining);

                    player.sendMessage(Text.of("A7Your Eepy Eeper needs " + remaining + " more sleep cycle(s) to recover..."), true);

                }

            }

        }

        // Update last sleep time for cooldown tracking

        lastSleepTime.put(player.getUuid(), world.getTime());

    }

    /**

     * World tick handler for passive effects

     */

    private static void onWorldTick(ServerWorld world) {

        // Handle Nap Time aura every 5 seconds (100 ticks)

        if (world.getTime() % 100 == 0) {

            handleNapTimeAura(world);

        }

    }

    /**

     * Handle L10 Nap Time - sitting pet radiates Regeneration I in 4-block radius

     */

    private static void handleNapTimeAura(ServerWorld world) {

        for (ServerPlayerEntity player : world.getPlayers()) {

            List<MobEntity> eepyPets = findNearbyEepyEepers(player, 16.0);

            for (MobEntity pet : eepyPets) {

                PetComponent petComp = PetComponent.get(pet);

                if (petComp == null || petComp.getLevel() < 10) continue;

                // Check if pet is sitting

                boolean isSitting = false;

                if (pet instanceof net.minecraft.entity.passive.TameableEntity tameable) {

                    isSitting = tameable.isSitting();

                }

                if (isSitting) {

                    // Apply Regeneration I to allies and pets within 4 blocks

                    double radius = PetsPlusConfig.getInstance().getDouble("eepy_eeper", "napRegenRadius", 4.0);

                    List<LivingEntity> nearbyEntities = world.getEntitiesByClass(

                        LivingEntity.class,

                        pet.getBoundingBox().expand(radius),

                        entity -> entity != pet &&

                                 (entity instanceof PlayerEntity ||

                                  (entity instanceof MobEntity mob && PetComponent.get(mob) != null))

                    );

                    for (LivingEntity entity : nearbyEntities) {

                        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 0)); // 6s

                    }

                    // Visual feedback

                    if (world.getTime() % 200 == 0) { // Every 10 seconds

                        player.sendMessage(Text.of("§aYour Eepy Eeper's cozy presence expands"), true);

                    }

                    // Particle effects (Z particles for sleep)

                    if (world.getTime() % 20 == 0) {

                        Vec3d petPos = pet.getPos();

                        world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,

                            petPos.x, petPos.y + 1, petPos.z,

                            3, 0.5, 0.3, 0.5, 0.02);

                    }

                }

            }

        }

    }

    private static void emitSleepLinkParticles(ServerPlayerEntity player, MobEntity pet, boolean empowered) {

        ServerWorld world = player.getWorld();

        if (!pet.isAlive()) return;

        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,

            pet.getX(), pet.getY() + 0.6, pet.getZ(),

            5, 0.35, 0.2, 0.35, 0.008);

        if (empowered) {

            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,

                player.getX(), player.getY() + 0.8, player.getZ(),

                6, 0.4, 0.3, 0.4, 0.01);

        }

    }

    private static void playSleepShareSound(ServerWorld world, ServerPlayerEntity player, boolean restfulDreamsActive) {

        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FOX_SLEEP, SoundCategory.PLAYERS, 0.3f, 0.9f);

        if (restfulDreamsActive) {

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.PLAYERS, 0.4f, 0.85f + world.random.nextFloat() * 0.1f);

        }

    }

    private static void playNapTimeAmbient(ServerWorld world, MobEntity pet) {

        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.25f, 0.75f + world.random.nextFloat() * 0.1f);

        Vec3d petPos = pet.getPos();

        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,

            petPos.x, petPos.y + 0.9, petPos.z,

            6, 0.45, 0.25, 0.45, 0.01);

        world.spawnParticles(ParticleTypes.END_ROD,

            petPos.x, petPos.y + 1.1, petPos.z,

            3, 0.35, 0.2, 0.35, 0.006);

    }

    private static void playPetKnockoutEffect(ServerWorld world, MobEntity pet) {

        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.ENTITY_FOX_SLEEP, SoundCategory.NEUTRAL, 0.6f, 0.9f);

        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,

            pet.getX(), pet.getY() + 0.4, pet.getZ(),

            10, 0.35, 0.3, 0.35, 0.01);

        world.spawnParticles(ParticleTypes.SOUL,

            pet.getX(), pet.getY() + 0.7, pet.getZ(),

            4, 0.25, 0.2, 0.25, 0.005);

    }

    private static void playPetRecoveryCue(ServerWorld world, MobEntity pet) {

        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.6f, 1.2f);

        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,

            pet.getX(), pet.getY() + 0.8, pet.getZ(),

            6, 0.3, 0.25, 0.3, 0.02);

    }

    /**

     * Apply baseline Eepy Eeper effects (10% slower movement)

     */

    public static void applyBaselineEffects(MobEntity pet, PetComponent petComp) {

        if (petComp.getRole() != PetRole.EEPY_EEPER) return;

        // Apply 10% speed reduction

        // Note: Speed reduction would be applied through the attribute system

        // Implementation depends on your existing attribute framework

    }

    /**

     * Find nearby Eepy Eeper pets owned by player

     */

    private static List<MobEntity> findNearbyEepyEepers(PlayerEntity owner, double radius) {

        if (!(owner.getWorld() instanceof ServerWorld world)) {

            return List.of();

        }

        return world.getEntitiesByClass(

            MobEntity.class,

            owner.getBoundingBox().expand(radius),

            entity -> {

                PetComponent petComp = PetComponent.get(entity);

                return petComp != null &&

                       petComp.getRole() == PetRole.EEPY_EEPER &&

                       petComp.isOwnedBy(owner) &&

                       entity.isAlive();

            }

        );

    }

    /**

     * Check if player can use Dream's Escape (not on cooldown)

     */

    private static boolean canUseDreamEscape(ServerPlayerEntity player) {

        // Check if enough sleep cycles have passed since last use

        Long lastSleep = lastSleepTime.get(player.getUuid());

        if (lastSleep == null) return true; // Never used before

        // Simple implementation: 3 MC days cooldown

        long currentTime = player.getWorld().getTime();

        long timeSinceLastSleep = currentTime - lastSleep;

        long threeDays = 72000; // 3 MC days in ticks

        return timeSinceLastSleep >= threeDays;

    }

    /**

     * Knock out a pet for a certain number of sleep cycles

     */

    private static void knockOutPet(UUID petUuid, int sleepCycles) {

        sleepCyclesRemaining.put(petUuid, sleepCycles);

    }

    /**

     * Check if a pet is knocked out

     */

    private static boolean isPetKnockedOut(UUID petUuid) {

        return sleepCyclesRemaining.containsKey(petUuid) && sleepCyclesRemaining.get(petUuid) > 0;

    }

    /**

     * Get the number of sleep cycles remaining for a knocked out pet

     */

    public static int getSleepCyclesRemaining(UUID petUuid) {

        return sleepCyclesRemaining.getOrDefault(petUuid, 0);

    }

    /**

     * Public method to trigger sleep events from other systems

     */

    public static void triggerSleepEvent(ServerPlayerEntity player) {

        onPlayerSleep(player);

    }
    
    /**
     * Handle sleep-based level up for Eepy Eeper pets.
     * This gives them a chance to gain a level while their owner sleeps, 
     * helping to balance their slower XP learning rate.
     */
    private static boolean handleSleepLevelUp(PetComponent petComp, ServerPlayerEntity player, MobEntity pet, ServerWorld world) {
        int currentLevel = petComp.getLevel();
        int targetLevel = currentLevel + 1;
        
        // Set XP to exactly what's needed for the next level
        int requiredXp = PetComponent.getTotalXpForLevel(targetLevel);
        petComp.setExperience(requiredXp);
        
        // Force level calculation update
        boolean actuallyLeveled = petComp.addExperience(0); // This will trigger level up logic
        
        if (actuallyLeveled) {
            // Apply attribute modifiers for new level
            woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(pet, petComp);
            
            // Check if this is a feature level and trigger appropriate handlers
            if (petComp.isFeatureLevel()) {
                // Play extra celebratory sound for feature levels
                world.playSound(null, pet.getX(), pet.getY(), pet.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_CELEBRATE,
                    net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1.0f);
            }
        }
        
        return actuallyLeveled;
    }

}