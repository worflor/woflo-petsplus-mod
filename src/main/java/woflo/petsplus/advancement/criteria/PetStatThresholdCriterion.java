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
 * Advancement criterion for accumulated statistics reaching thresholds.
 * Examples: guardian damage redirected ≥ 1000, unique allies healed ≥ 5, dream escapes ≥ 3
 */
public class PetStatThresholdCriterion extends AbstractCriterion<PetStatThresholdCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_stat_threshold");

    public static final String STAT_GUARDIAN_DAMAGE = "guardian_damage_redirected";
    public static final String STAT_ALLIES_HEALED = "unique_allies_healed";
    public static final String STAT_DREAM_ESCAPES = "dream_escapes";
    public static final String STAT_PET_COUNT = "pet_count";

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger when a stat reaches a threshold.
     * @param player The pet owner
     * @param statType The stat being tracked
     * @param value The current value
     */
    public void trigger(ServerPlayerEntity player, String statType, float value) {
        this.trigger(player, conditions -> conditions.matches(statType, value));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<String> statType,
        Optional<Float> minValue,
        Optional<Float> maxValue
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.optionalFieldOf("stat_type").forGetter(Conditions::statType),
                Codec.FLOAT.optionalFieldOf("min_value").forGetter(Conditions::minValue),
                Codec.FLOAT.optionalFieldOf("max_value").forGetter(Conditions::maxValue)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(String stat, float val) {
            // Check stat type
            if (statType.isPresent() && !statType.get().equals(stat)) {
                return false;
            }

            // Check value range
            if (minValue.isPresent() && val < minValue.get()) {
                return false;
            }
            if (maxValue.isPresent() && val > maxValue.get()) {
                return false;
            }

            return true;
        }
    }
}
