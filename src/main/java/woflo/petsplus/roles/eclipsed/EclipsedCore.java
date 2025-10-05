package woflo.petsplus.roles.eclipsed;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.tracking.PlayerTickListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements Eclipsed role mechanics: shadow manipulation and eclipse powers.
 *
 * Core Features:
 * - Baseline: Shadow stealth, darkness vision, eclipse energy
 * - L7 Umbral Mastery: Eclipse field, shadow stepping, void damage
 * - Darkness and shadow-based abilities
 *
 * Design Philosophy:
 * - Shadow manipulation archetype
 * - Provides stealth and darkness-based advantages
 * - Eclipse-themed abilities with day/night cycles
 */
public class EclipsedCore implements PlayerTickListener {

    private static final double NEARBY_RADIUS = 16.0;
    private static final long FIELD_INTERVAL_TICKS = 5L;
    private static final long IDLE_RECHECK_TICKS = 40L;
    private static final Map<UUID, Long> NEXT_FIELD_TICK = new ConcurrentHashMap<>();

    private static final EclipsedCore INSTANCE = new EclipsedCore();

    private EclipsedCore() {}

    public static EclipsedCore getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }
        return NEXT_FIELD_TICK.getOrDefault(player.getUuid(), 0L);
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null || player.isRemoved() || player.isSpectator()) {
            if (player != null) {
                NEXT_FIELD_TICK.remove(player.getUuid());
            }
            return;
        }

        UUID playerId = player.getUuid();
        NEXT_FIELD_TICK.put(playerId, currentTick + IDLE_RECHECK_TICKS);

        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        List<MobEntity> eclipsedPets = getNearbyEclipsedPets(player, NEARBY_RADIUS);
        if (eclipsedPets.isEmpty()) {
            return;
        }

        NEXT_FIELD_TICK.put(playerId, currentTick + FIELD_INTERVAL_TICKS);
        processEclipseEffects(player, eclipsedPets);
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        if (player != null) {
            NEXT_FIELD_TICK.remove(player.getUuid());
        }
    }
    
    private static void processEclipseEffects(ServerPlayerEntity player, List<MobEntity> eclipsedPets) {
        for (MobEntity eclipsedPet : eclipsedPets) {
            PetComponent petComp = PetComponent.get(eclipsedPet);
            if (petComp == null || !(eclipsedPet instanceof PetsplusTameable)) {
                continue;
            }

            EclipsedVoid.onServerTick(eclipsedPet, player);
        }
    }
    
    /**
     * Check if player has a nearby Eclipsed pet.
     */
    private static boolean hasNearbyEclipsed(ServerPlayerEntity player) {
        return !getNearbyEclipsedPets(player, NEARBY_RADIUS).isEmpty();
    }

    private static List<MobEntity> getNearbyEclipsedPets(ServerPlayerEntity player, double radius) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return java.util.Collections.emptyList();
        }

        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(radius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= radius * radius;
            }
        );
    }
    
    /**
     * Check if entity is in darkness (low light level).
     */
    private static boolean isInDarkness(LivingEntity entity) {
        return entity.getWorld().getLightLevel(entity.getBlockPos()) <= 7;
    }
    
    /**
     * Check if it's currently an eclipse (night time).
     */
    private static boolean isEclipseTime(ServerWorld world) {
        long timeOfDay = world.getTimeOfDay() % 24000;
        return timeOfDay >= 13000 && timeOfDay <= 23000; // Night time
    }
    
    /**
     * Check if player has active Eclipse Field (L7+ Eclipsed).
     */
    public static boolean hasActiveEclipseField(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        
        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       component.getLevel() >= 7 && // L7+ for Eclipse Field
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).size() > 0;
    }
    
    /**
     * Get shadow damage bonus for nearby Eclipsed pets.
     */
    public static float getShadowDamageBonus(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player)) {
            return 0.0f;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return 0.0f;
        }
        
        // Bonus is higher in darkness and during eclipse
        float baseBonus = 1.0f;
        if (isInDarkness(player)) {
            baseBonus *= 1.5f; // 50% more in darkness
        }
        if (isEclipseTime(world)) {
            baseBonus *= 2.0f; // Double during eclipse
        }
        
        return baseBonus;
    }
    
    /**
     * Apply shadow stealth effects to player.
     */
    public static void applyShadowStealth(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player) || !isInDarkness(player)) {
            return;
        }
        
        // Apply night vision and stealth effects
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            StatusEffects.NIGHT_VISION, 200, 0, true, false)); // 10 seconds
        
        // Additional invisibility during eclipse
        if (player.getWorld() instanceof ServerWorld world && isEclipseTime(world)) {
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                StatusEffects.INVISIBILITY, 100, 0, true, false)); // 5 seconds
        }
    }
    
    /**
     * Trigger eclipse abilities when entering darkness.
     */
    public static void onEnterDarkness(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player)) {
            return;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        
        // Find nearby Eclipsed pets and trigger shadow abilities
        world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).forEach(eclipsedPet -> {
            PetComponent petComp = PetComponent.get(eclipsedPet);
            if (petComp != null && eclipsedPet instanceof PetsplusTameable) {
                // Trigger void abilities when entering darkness
                EclipsedVoid.onServerTick(eclipsedPet, player);
                
            }
        });
    }
    
    /**
     * Check if player has shadow protection from nearby Eclipsed pets.
     */
    public static boolean hasShadowProtection(ServerPlayerEntity player) {
        return hasNearbyEclipsed(player) && isInDarkness(player);
    }
    
    /**
     * Get eclipse energy level for player based on nearby Eclipsed pets.
     */
    public static int getEclipseEnergy(ServerPlayerEntity player) {
        if (!hasNearbyEclipsed(player)) {
            return 0;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return 0;
        }
        
        // Calculate energy based on highest level Eclipsed pet
        int maxLevel = world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.hasRole(PetRoleType.ECLIPSED) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).stream()
        .mapToInt(entity -> {
            PetComponent component = PetComponent.get(entity);
            return component != null ? component.getLevel() : 0;
        })
        .max()
        .orElse(0);
        
        // Base energy scaling with level and conditions
        int baseEnergy = maxLevel * 10;
        if (isInDarkness(player)) {
            baseEnergy *= 2; // Double in darkness
        }
        if (isEclipseTime(world)) {
            baseEnergy *= 3; // Triple during eclipse
        }
        
        return Math.min(baseEnergy, 300); // Max 300 eclipse energy
    }
}