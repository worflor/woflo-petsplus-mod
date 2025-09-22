package woflo.petsplus.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

import java.util.Optional;

/**
 * Utilities for inspecting shoulder perch state using vanilla player data.
 */
public final class PetPerchUtil {
    private static final String PETSPLUS_DATA_KEY = "PetsPlusData";
    private static final String ROLE_KEY = "role";

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
        Optional<Boolean> leftMatch = matchesComponent(owner.getShoulderEntityLeft(), component, petUuid);
        Optional<Boolean> rightMatch = matchesComponent(owner.getShoulderEntityRight(), component, petUuid);

        boolean seenData = leftMatch.isPresent() || rightMatch.isPresent();
        boolean perched = leftMatch.orElse(false) || rightMatch.orElse(false);

        if (!seenData) {
            perched = ownerHasPerchedRole(owner, component.getRole());
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
    public static boolean ownerHasPerchedRole(@Nullable PlayerEntity owner, PetRole role) {
        if (owner == null) {
            return false;
        }

        return hasRoleOnShoulder(owner.getShoulderEntityLeft(), role)
            || hasRoleOnShoulder(owner.getShoulderEntityRight(), role);
    }

    private static boolean hasRoleOnShoulder(@Nullable NbtCompound data, PetRole role) {
        if (data == null || data.isEmpty() || !data.contains(PETSPLUS_DATA_KEY)) {
            return false;
        }

        return data.getCompound(PETSPLUS_DATA_KEY)
            .map(petsPlusData -> petsPlusData.getString(ROLE_KEY)
                .map(roleKey -> roleKey.equals(role.getKey()))
                .orElse(false))
            .orElse(false);
    }

    private static Optional<Boolean> matchesComponent(@Nullable NbtCompound data, PetComponent component, String petUuid) {
        if (data == null || data.isEmpty() || !data.contains(PETSPLUS_DATA_KEY)) {
            return Optional.empty();
        }

        return data.getCompound(PETSPLUS_DATA_KEY).flatMap(petsPlusData -> {
            Optional<Boolean> roleMatch = petsPlusData.getString(ROLE_KEY)
                .map(roleKey -> roleKey.equals(component.getRole().getKey()));
            if (roleMatch.isPresent() && !roleMatch.get()) {
                return Optional.of(false);
            }

            if (!petsPlusData.contains("petUuid")) {
                return Optional.empty();
            }

            return petsPlusData.getString("petUuid").map(petUuid::equals);
        });
    }
}

