package woflo.petsplus.advancement.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PlayerAdvancementState;

import java.util.Optional;
import java.util.Set;

/**
 * Advancement criterion that triggers when a player experiences a variety of different mood types.
 * Tracks the diversity of emotional states experienced with pets.
 */
public class PetMoodVarietyCriterion extends AbstractCriterion<PetMoodVarietyCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_mood_variety");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger this criterion when a player experiences a new mood type.
     * @param player The pet owner
     * @param moodTypes The set of mood types experienced so far
     * @param moodLevel The current mood level (optional, for level-specific requirements)
     */
    public void trigger(ServerPlayerEntity player, Set<String> moodTypes, Integer moodLevel) {
        this.trigger(player, conditions -> conditions.matches(moodTypes, moodLevel));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<Integer> minVariety,
        Optional<Integer> minLevel,
        Optional<Set<String>> requiredMoods
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.INT.optionalFieldOf("min_variety").forGetter(Conditions::minVariety),
                Codec.INT.optionalFieldOf("min_level").forGetter(Conditions::minLevel),
                Codec.STRING.listOf().optionalFieldOf("required_moods").xmap(
                    list -> list.map(Set::copyOf),
                    set -> set.map(java.util.ArrayList::new)
                ).forGetter(Conditions::requiredMoods)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(Set<String> experiencedMoods, Integer currentMoodLevel) {
            // Check minimum variety requirement
            if (minVariety.isPresent() && experiencedMoods.size() < minVariety.get()) {
                return false;
            }

            // Check minimum level requirement if specified
            if (minLevel.isPresent() && (currentMoodLevel == null || currentMoodLevel < minLevel.get())) {
                return false;
            }

            // Check specific mood requirements if specified
            if (requiredMoods.isPresent() && !experiencedMoods.containsAll(requiredMoods.get())) {
                return false;
            }

            return true;
        }
    }
}