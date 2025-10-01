package woflo.petsplus.advancement.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.util.Optional;

/**
 * Advancement criterion that triggers when a pet of a specific role reaches a certain level.
 * Used for role-specific progression tracking.
 */
public class PetRoleLevelCriterion extends AbstractCriterion<PetRoleLevelCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_role_level");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger this criterion when a pet levels up.
     * @param player The pet owner
     * @param roleId The pet's role identifier
     * @param level The current level
     */
    public void trigger(ServerPlayerEntity player, String roleId, int level) {
        this.trigger(player, conditions -> conditions.matches(roleId, level));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<String> role,
        Optional<Integer> minLevel,
        Optional<Integer> maxLevel
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.optionalFieldOf("role").forGetter(Conditions::role),
                Codec.INT.optionalFieldOf("min_level").forGetter(Conditions::minLevel),
                Codec.INT.optionalFieldOf("max_level").forGetter(Conditions::maxLevel)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(String petRoleId, int petLevel) {
            // Check role if specified
            if (role.isPresent()) {
                if (petRoleId == null) {
                    return false;
                }
                if (!role.get().equalsIgnoreCase(petRoleId)) {
                    return false;
                }
            }

            // Check level range
            if (minLevel.isPresent() && petLevel < minLevel.get()) {
                return false;
            }
            if (maxLevel.isPresent() && petLevel > maxLevel.get()) {
                return false;
            }

            return true;
        }
    }
}