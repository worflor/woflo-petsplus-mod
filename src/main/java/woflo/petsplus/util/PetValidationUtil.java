package woflo.petsplus.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Common helpers for validating pets, ownership checks, and nearby lookups.
 * Centralizes patterns that were previously duplicated across commands and utilities.
 */
public final class PetValidationUtil {

    private static final double DEFAULT_NEARBY_RADIUS = 10.0;

    private PetValidationUtil() {
        // Utility class
    }

    /**
     * Returns the pet component associated with the mob, or null if the mob is not a managed pet.
     */
    @Nullable
    public static PetComponent getComponent(@Nullable MobEntity pet) {
        return pet == null ? null : PetComponent.get(pet);
    }

    /**
     * Checks whether the given mob is recognized as a PetsPlus pet.
     */
    public static boolean isPet(@Nullable MobEntity pet) {
        return getComponent(pet) != null;
    }

    /**
     * Checks whether the given mob is owned by the specified player.
     */
    public static boolean isOwnedBy(@Nullable MobEntity pet, @Nullable PlayerEntity player) {
        if (pet == null || player == null) {
            return false;
        }
        PetComponent component = PetComponent.get(pet);
        return component != null && component.isOwnedBy(player);
    }

    public static List<MobEntity> findOwnedPets(ServerPlayerEntity owner) {
        return findOwnedPets(owner, DEFAULT_NEARBY_RADIUS);
    }

    /**
     * Finds all owned pets within a custom radius around the owner.
     */
    public static List<MobEntity> findOwnedPets(ServerPlayerEntity owner, double radius) {
        ServerWorld world = owner.getCommandSource().getWorld();
        Box searchArea = owner.getBoundingBox().expand(radius);
        return world.getEntitiesByClass(MobEntity.class, searchArea, entity -> {
            PetComponent component = PetComponent.get(entity);
            return component != null && component.isOwnedBy(owner) && entity.isAlive();
        });
    }

    @Nullable
    public static MobEntity findOwnedPetByName(ServerPlayerEntity owner, String name) {
        return findOwnedPetByName(owner, name, DEFAULT_NEARBY_RADIUS);
    }

    @Nullable
    public static MobEntity findOwnedPetByName(ServerPlayerEntity owner, String name, double radius) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return findOwnedPets(owner, radius).stream()
            .filter(pet -> matchesDisplayName(pet, name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns a display-ready name for the pet, preferring the custom name when present.
     */
    public static String getDisplayName(MobEntity pet) {
        if (pet.hasCustomName()) {
            return pet.getCustomName().getString();
        }
        return pet.getType().getName().getString();
    }

    private static boolean matchesDisplayName(MobEntity pet, String candidate) {
        return getDisplayName(pet).equalsIgnoreCase(candidate);
    }
}
