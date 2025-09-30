package woflo.petsplus.advancement.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;

import java.util.Optional;

/**
 * Advancement criterion that triggers when a pet reaches a specific mood level.
 * Event-driven - fires only when mood changes.
 */
public class PetMoodLevelCriterion extends AbstractCriterion<PetMoodLevelCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_mood_level");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger this criterion when a pet's mood changes.
     * @param player The pet owner
     * @param mood The mood type
     * @param level The mood intensity level
     */
    public void trigger(ServerPlayerEntity player, PetComponent.Mood mood, int level) {
        this.trigger(player, conditions -> conditions.matches(mood, level));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<String> mood,
        Optional<Integer> minLevel,
        Optional<Integer> maxLevel
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.optionalFieldOf("mood").forGetter(Conditions::mood),
                Codec.INT.optionalFieldOf("min_level").forGetter(Conditions::minLevel),
                Codec.INT.optionalFieldOf("max_level").forGetter(Conditions::maxLevel)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(PetComponent.Mood petMood, int moodLevel) {
            // Check mood type if specified
            if (mood.isPresent() && petMood != null) {
                if (!mood.get().equalsIgnoreCase(petMood.name())) {
                    return false;
                }
            }

            // Check level range
            if (minLevel.isPresent() && moodLevel < minLevel.get()) {
                return false;
            }
            if (maxLevel.isPresent() && moodLevel > maxLevel.get()) {
                return false;
            }

            return true;
        }
    }
}