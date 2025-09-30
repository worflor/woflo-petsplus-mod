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
 * Advancement criterion that triggers when a pet reaches a specific level.
 * More efficient than tick-based checks - only fires on actual level-up events.
 */
public class PetLevelCriterion extends AbstractCriterion<PetLevelCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_level");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger this criterion when a pet levels up.
     * @param player The pet owner
     * @param petLevel The new level of the pet
     * @param roleId The role ID of the pet (nullable)
     */
    public void trigger(ServerPlayerEntity player, int petLevel, String roleId) {
        this.trigger(player, conditions -> conditions.matches(petLevel, roleId));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<Integer> minLevel,
        Optional<Integer> maxLevel,
        Optional<String> role
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.INT.optionalFieldOf("min_level").forGetter(Conditions::minLevel),
                Codec.INT.optionalFieldOf("max_level").forGetter(Conditions::maxLevel),
                Codec.STRING.optionalFieldOf("role").forGetter(Conditions::role)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(int petLevel, String petRoleId) {
            // Check level range
            if (minLevel.isPresent() && petLevel < minLevel.get()) {
                return false;
            }
            if (maxLevel.isPresent() && petLevel > maxLevel.get()) {
                return false;
            }

            // Check role if specified
            if (role.isPresent()) {
                if (petRoleId == null) {
                    return false;
                }
                return role.get().equals(petRoleId);
            }

            return true;
        }
    }
}