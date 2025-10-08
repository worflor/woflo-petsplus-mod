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
 * Advancement criterion that triggers when a pet dies.
 * Distinguishes between permanent death and other death types.
 */
public class PetDeathCriterion extends AbstractCriterion<PetDeathCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_death");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger when a pet dies.
     * @param player The pet owner
     * @param petLevel The level of the pet that died
     * @param permanent Whether this was a permanent death
     */
    public void trigger(ServerPlayerEntity player, int petLevel, boolean permanent) {
        this.trigger(player, conditions -> conditions.matches(petLevel, permanent));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<Integer> minLevel,
        Optional<Boolean> permanentDeath
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.INT.optionalFieldOf("min_level").forGetter(Conditions::minLevel),
                Codec.BOOL.optionalFieldOf("permanent_death").forGetter(Conditions::permanentDeath)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(int level, boolean isPermanent) {
            // Check level requirement
            if (minLevel.isPresent() && level < minLevel.get()) {
                return false;
            }

            // Check permanent death flag
            if (permanentDeath.isPresent() && permanentDeath.get() != isPermanent) {
                return false;
            }

            return true;
        }
    }
}
