package woflo.petsplus.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.Optional;

/**
 * Utilities for inspecting shoulder perch state using vanilla player data.
 */
public final class PetPerchUtil {
    private static final String PETSPLUS_DATA_KEY = "PetsPlusData";
    private static final String ROLE_KEY = "role";
    private static final String PET_UUID_KEY = "petUuid";

    private PetPerchUtil() {
    }

    /**
     * Check if the supplied pet component is currently perched on its owner.
     */
    public static boolean isPetPerched(@Nullable PetComponent component) {
        if (component == null) {
            return false;
        }
        PlayerEntity owner = component.getOwner();
        if (owner == null) {
            component.setPerched(false);
            return false;
        }

        String petUuid = component.getPet().getUuidAsString();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            component.setPerched(false);
            return false;
        }

        Optional<Boolean> leftMatch = matchesComponent(serverOwner.getLeftShoulderNbt(), component, petUuid);
        Optional<Boolean> rightMatch = matchesComponent(serverOwner.getRightShoulderNbt(), component, petUuid);

        boolean seenData = leftMatch.isPresent() || rightMatch.isPresent();
        boolean perched = leftMatch.orElse(false) || rightMatch.orElse(false);

        if (!seenData) {
            perched = ownerHasPerchedRole(owner, component.getRoleId());
        }

        component.setPerched(perched);
        return perched;
    }

    /**
     * Check if a specific pet entity is perched on the supplied owner.
     */
    public static boolean isPetPerched(@Nullable MobEntity pet, @Nullable PlayerEntity owner) {
        if (pet == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return false;
        }

        return isPetPerched(component);
    }

    /**
     * Check if the owner currently has a perched pet with the given role.
     */
    public static boolean ownerHasPerchedRole(@Nullable PlayerEntity owner, @Nullable Identifier roleId) {
        if (owner == null || roleId == null) {
            return false;
        }

        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        return hasRoleOnShoulder(serverOwner.getLeftShoulderNbt(), roleId)
            || hasRoleOnShoulder(serverOwner.getRightShoulderNbt(), roleId);
    }

    public static boolean ownerHasPerchedRole(@Nullable PlayerEntity owner, @Nullable PetRoleType roleType) {
        return roleType != null && ownerHasPerchedRole(owner, roleType.id());
    }

    private static boolean hasRoleOnShoulder(@Nullable NbtCompound data, Identifier roleId) {
        if (data == null || data.isEmpty() || !data.contains(PETSPLUS_DATA_KEY)) {
            return false;
        }

        Optional<NbtCompound> petsPlusData = data.getCompound(PETSPLUS_DATA_KEY);
        if (petsPlusData.isEmpty()) {
            return false;
        }

        Optional<String> roleKey = petsPlusData.get().getString(ROLE_KEY);
        if (roleKey.isEmpty()) {
            return false;
        }

        String stored = roleKey.get();
        return stored.equals(roleId.toString()) || stored.equals(roleId.getPath());
    }

    private static Optional<Boolean> matchesComponent(@Nullable NbtCompound data, PetComponent component, String petUuid) {
        if (data == null || data.isEmpty() || !data.contains(PETSPLUS_DATA_KEY)) {
            return Optional.empty();
        }

        Optional<NbtCompound> petsPlusData = data.getCompound(PETSPLUS_DATA_KEY);
        if (petsPlusData.isEmpty()) {
            return Optional.empty();
        }

        NbtCompound compound = petsPlusData.get();

        Optional<String> storedRole = compound.getString(ROLE_KEY);
        if (storedRole.isPresent()) {
            String roleKey = storedRole.get();
            if (!roleKey.isEmpty()) {
                Identifier currentRole = component.getRoleId();
                if (!roleKey.equals(currentRole.toString()) && !roleKey.equals(currentRole.getPath())) {
                    return Optional.of(false);
                }
            }
        }

        Optional<String> storedUuid = compound.getString(PET_UUID_KEY);
        if (storedUuid.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(storedUuid.get().equals(petUuid));
    }
}

