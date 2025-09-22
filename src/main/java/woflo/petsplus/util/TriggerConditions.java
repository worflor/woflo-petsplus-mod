package woflo.petsplus.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.entity.mob.HostileEntity;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.StateManager;

/**
 * Simple utility methods for trigger conditions.
 * Provides require_perched, require_mounted_owner, and mount state checks.
 */
public class TriggerConditions {
    
    /**
     * Information about an entity's armor/decoration.
     */
    public record ArmorInfo(boolean hasArmor, ItemStack stack) {
        public static final ArmorInfo EMPTY = new ArmorInfo(false, ItemStack.EMPTY);
    }
    
    /**
     * Check if player has any entity perched on shoulders (require_perched condition).
     * @param player The player to check
     * @return true if player has something perched
     */
    public static boolean isPerched(PlayerEntity player) {
        return !player.getShoulderEntityLeft().isEmpty() || 
               !player.getShoulderEntityRight().isEmpty();
    }
    
    /**
     * Check if player is mounted (require_mounted_owner condition).
     * @param player The player to check
     * @return true if player is riding something
     */
    public static boolean isMounted(PlayerEntity player) {
        return player.hasVehicle();
    }
    
    /**
     * Get the player's mount entity for mount-targeted effects.
     * @param player The player
     * @return The mount entity, or null if not mounted
     */
    @Nullable
    public static Entity getMount(PlayerEntity player) {
        return player.getVehicle();
    }
    
    /**
     * Get the player's mount as a LivingEntity if possible.
     * @param player The player
     * @return The mount as LivingEntity, or null if not mounted or not living
     */
    @Nullable
    public static LivingEntity getLivingMount(PlayerEntity player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof LivingEntity le ? le : null;
    }
    
    /**
     * Check if an entity is saddled (safe fallback implementation).
     * @param entity The entity to check
     * @return true if the entity is saddled
     */
    public static boolean isSaddled(Entity entity) {
        // Try reflection-based approach for Saddleable interface
        try {
            // Check if entity implements Saddleable (pigs, striders, camels)
            Class<?> saddleableClass = Class.forName("net.minecraft.entity.Saddleable");
            if (saddleableClass.isInstance(entity)) {
                Object saddleable = saddleableClass.cast(entity);
                var method = saddleableClass.getMethod("isSaddled");
                return (Boolean) method.invoke(saddleable);
            }
        } catch (Exception ignored) {
            // Saddleable interface not available or failed
        }
        
        // For horses, check via entity type and data tracker if needed
        if (entity instanceof AbstractHorseEntity) {
            // Safe fallback - assume not saddled if we can't determine
            // You could add a HorseInvAccessor mixin here if needed
            return false;
        }
        
        return false;
    }
    
    /**
     * Get armor/decoration information for an entity (safe fallback).
     * @param entity The entity to check
     * @return ArmorInfo with armor state and stack
     */
    public static ArmorInfo getBodyArmor(Entity entity) {
        // Try reflection for ArmorAnimal interface (horses, wolves)
        try {
            Class<?> armorAnimalClass = Class.forName("net.minecraft.entity.ArmorAnimal");
            if (armorAnimalClass.isInstance(entity)) {
                Object armorAnimal = armorAnimalClass.cast(entity);
                var method = armorAnimalClass.getMethod("getBodyArmor");
                ItemStack armor = (ItemStack) method.invoke(armorAnimal);
                return new ArmorInfo(!armor.isEmpty(), armor.copy());
            }
        } catch (Exception ignored) {
            // ArmorAnimal interface not available or failed
        }
        
        // For llamas, try to get decor if available
        if (entity instanceof LlamaEntity) {
            // Safe fallback - no decor detection for now
            // You could add an accessor mixin if needed
            return ArmorInfo.EMPTY;
        }
        
        return ArmorInfo.EMPTY;
    }
    
    /**
     * Check if an entity has a chest (safe fallback).
     * @param entity The entity to check
     * @return true if the entity has a chest
     */
    public static boolean hasChest(Entity entity) {
        // Try reflection for ChestedHorseEntity
        try {
            Class<?> chestedClass = Class.forName("net.minecraft.entity.passive.ChestedHorseEntity");
            if (chestedClass.isInstance(entity)) {
                Object chested = chestedClass.cast(entity);
                var method = chestedClass.getMethod("hasChest");
                return (Boolean) method.invoke(chested);
            }
        } catch (Exception ignored) {
            // ChestedHorseEntity not available or failed
        }
        
        return false;
    }
    
    /**
     * Check if player is mounted on a saddled entity.
     * @param player The player to check
     * @return true if mounted on a saddled entity
     */
    public static boolean isMountedOnSaddled(PlayerEntity player) {
        Entity mount = getMount(player);
        return mount != null && isSaddled(mount);
    }
    
    /**
     * Check if player is mounted on an armored entity.
     * @param player The player to check
     * @return true if mounted on an entity with armor/decoration
     */
    public static boolean isMountedOnArmored(PlayerEntity player) {
        Entity mount = getMount(player);
        return mount != null && getBodyArmor(mount).hasArmor();
    }
    
    /**
     * Check if player is mounted on an entity with storage (chest).
     * @param player The player to check
     * @return true if mounted on an entity with a chest
     */
    public static boolean isMountedOnChested(PlayerEntity player) {
        Entity mount = getMount(player);
        return mount != null && hasChest(mount);
    }
    
    /**
     * Get comprehensive mount state for complex trigger conditions.
     * @param player The player to check
     * @return MountState record with all mount information
     */
    public static MountState getMountState(PlayerEntity player) {
        Entity mount = getMount(player);
        if (mount == null) {
            return new MountState(false, false, false, false, null, ArmorInfo.EMPTY);
        }
        
        boolean saddled = isSaddled(mount);
        ArmorInfo armor = getBodyArmor(mount);
        boolean chested = hasChest(mount);
        
        return new MountState(true, saddled, armor.hasArmor(), chested, mount, armor);
    }
    
    /**
     * Comprehensive mount state information.
     */
    public record MountState(
        boolean isMounted,
        boolean isSaddled, 
        boolean isArmored,
        boolean hasChest,
        @Nullable Entity mount,
        ArmorInfo armorInfo
    ) {}
    
    /**
     * Check if player is currently in combat state.
     * @param player The player to check
     * @return true if player is in combat
     */
    public static boolean isInCombat(PlayerEntity player) {
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            StateManager stateManager = StateManager.forWorld(serverWorld);
            OwnerCombatState combatState = stateManager.getOwnerState(player);
            return combatState.isInCombat();
        }
        return false;
    }
    
    /**
     * Set player's combat state.
     * @param player The player
     * @param inCombat Whether they should be in combat
     */
    public static void setCombatState(PlayerEntity player, boolean inCombat) {
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            StateManager stateManager = StateManager.forWorld(serverWorld);
            OwnerCombatState combatState = stateManager.getOwnerState(player);
            if (inCombat) {
                combatState.enterCombat();
            } else {
                // Combat will end naturally via tick() when timeout expires
                // We don't have a direct exitCombat() method, so we let it time out
                combatState.updateCombatTimer();
            }
        }
    }
    
    /**
     * Find the nearest hostile entity within a radius.
     * @param player The player center point
     * @param radius Search radius
     * @return Nearest hostile entity, or null if none found
     */
    @Nullable
    public static HostileEntity findNearestHostile(PlayerEntity player, double radius) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        
        Box searchBox = Box.of(player.getPos(), radius * 2, radius * 2, radius * 2);
        return world.getEntitiesByClass(HostileEntity.class, searchBox, entity -> 
            entity.isAlive() && entity.squaredDistanceTo(player) <= radius * radius
        ).stream().min((a, b) -> 
            Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player))
        ).orElse(null);
    }
    
    /**
     * Check if mount is a "boss" type entity that should be protected from certain effects.
     * @param mount The mount entity to check
     * @return true if mount should be protected from boss-unsafe effects
     */
    public static boolean isBossMount(Entity mount) {
        return mount instanceof LivingEntity livingMount && BossSafetyUtil.isBossEntity(livingMount);
    }
    
    /**
     * Get mount with additional boss safety and type checks.
     * @param player The player
     * @return Extended mount information
     */
    public static ExtendedMountState getExtendedMountState(PlayerEntity player) {
        Entity mount = getMount(player);
        if (mount == null) {
            return new ExtendedMountState(false, false, false, false, false, null, ArmorInfo.EMPTY);
        }
        
        boolean saddled = isSaddled(mount);
        ArmorInfo armor = getBodyArmor(mount);
        boolean chested = hasChest(mount);
        boolean isBoss = isBossMount(mount);
        
        return new ExtendedMountState(true, saddled, armor.hasArmor(), chested, isBoss, mount, armor);
    }
    
    /**
     * Extended mount state with boss safety information.
     */
    public record ExtendedMountState(
        boolean isMounted,
        boolean isSaddled,
        boolean isArmored,
        boolean hasChest,
        boolean isBoss,
        @Nullable Entity mount,
        ArmorInfo armorInfo
    ) {}
}