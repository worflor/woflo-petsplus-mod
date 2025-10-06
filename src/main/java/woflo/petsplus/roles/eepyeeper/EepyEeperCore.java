package woflo.petsplus.roles.eepyeeper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.ChanceValidationUtil;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
/**

 * Core Eepy Eeper mechanics implementation following the story requirements.

 */

public class EepyEeperCore {

    private static final Map<UUID, Integer> sleepCyclesRemaining = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastSleepTime = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> lastSleepEventIds = new ConcurrentHashMap<>();

    private static final EepyEeperCore INSTANCE = new EepyEeperCore();

    private EepyEeperCore() {}

    public static EepyEeperCore getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
        // Intentionally left blank; legacy damage interception now handled by abilities.
    }

    public static boolean beginSleepEvent(ServerPlayerEntity player, @Nullable UUID eventId, long worldTime) {
        if (player == null) {
            return false;
        }
        UUID ownerId = player.getUuid();
        if (eventId != null) {
            UUID previous = lastSleepEventIds.put(ownerId, eventId);
            if (eventId.equals(previous)) {
                return false;
            }
        } else {
            Long previous = lastSleepTime.get(ownerId);
            if (previous != null && previous == worldTime) {
                return false;
            }
        }
        lastSleepTime.put(ownerId, worldTime);
        return true;
    }

    public static void processSleepRecovery(ServerPlayerEntity player, ServerWorld world) {
        if (player == null || world == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = sleepCyclesRemaining.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue();
            if (remaining <= 0) {
                iterator.remove();
                continue;
            }
            remaining--;
            if (remaining <= 0) {
                iterator.remove();
                player.sendMessage(Text.of("A6Your knocked out Eepy Eeper stirs... they're ready to protect you again!"), false);
                Entity entity = world.getEntity(entry.getKey());
                if (entity instanceof MobEntity recoveredPet) {
                    playPetRecoveryCue(world, recoveredPet);
                }
            } else {
                entry.setValue(remaining);
                player.sendMessage(Text.translatable("petsplus.eepyeeper.recovery_progress", remaining), true);
            }
        }
    }

    public static void emitSleepLinkParticles(ServerPlayerEntity player, MobEntity pet, boolean empowered) {

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

    public static void playSleepShareSound(ServerWorld world, ServerPlayerEntity player, boolean restfulDreamsActive) {

        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FOX_SLEEP, SoundCategory.PLAYERS, 0.3f, 0.9f);

        if (restfulDreamsActive) {

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.PLAYERS, 0.4f, 0.85f + world.random.nextFloat() * 0.1f);

        }

    }

    public static void playPetRecoveryCue(ServerWorld world, MobEntity pet) {

        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.6f, 1.2f);

        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,

            pet.getX(), pet.getY() + 0.8, pet.getZ(),

            6, 0.3, 0.25, 0.3, 0.02);

    }

    /**

     * Apply baseline Eepy Eeper effects (10% slower movement)

     */

    public static void applyBaselineEffects(MobEntity pet, PetComponent petComp) {

        if (!petComp.hasRole(PetRoleType.EEPY_EEPER)) return;

        // Apply 10% speed reduction

        // Note: Speed reduction would be applied through the attribute system

        // Implementation depends on your existing attribute framework

    }

    /**

     * Find nearby Eepy Eeper pets owned by player

     */

    public static List<MobEntity> findNearbyEepyEepers(PlayerEntity owner, double radius) {

        if (!(owner.getWorld() instanceof ServerWorld world)) {

            return List.of();

        }

        return world.getEntitiesByClass(

            MobEntity.class,

            owner.getBoundingBox().expand(radius),

            entity -> {

                PetComponent petComp = PetComponent.get(entity);

                return petComp != null &&

                       petComp.hasRole(PetRoleType.EEPY_EEPER) &&

                       petComp.isOwnedBy(owner) &&

                       entity.isAlive();

            }

        );

    }

    /**

     * Check if player can use Dream's Escape (not on cooldown)

     */

    public static boolean canUseDreamEscape(ServerPlayerEntity player) {

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

     * Check if a pet is knocked out

     */

    public static boolean isPetKnockedOut(UUID petUuid) {

        return sleepCyclesRemaining.containsKey(petUuid) && sleepCyclesRemaining.get(petUuid) > 0;

    }

    public static void clearKnockout(UUID petUuid) {

        sleepCyclesRemaining.remove(petUuid);

    }

    /**

     * Get the number of sleep cycles remaining for a knocked out pet

     */

    public static int getSleepCyclesRemaining(UUID petUuid) {

        return sleepCyclesRemaining.getOrDefault(petUuid, 0);

    }

    /**
     * Handle sleep-based level up for Eepy Eeper pets.
     * This gives them a chance to gain a level while their owner sleeps,
     * helping to balance their slower XP learning rate.
     */
    public static boolean handleSleepLevelUp(PetComponent petComp, ServerPlayerEntity player, MobEntity pet, ServerWorld world) {
        int currentLevel = petComp.getLevel();
        int targetLevel = currentLevel + 1;
        
        // Set XP to exactly what's needed for the next level
        int requiredXp = petComp.getXpRequiredForLevel(targetLevel);
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