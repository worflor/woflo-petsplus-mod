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
 * Advancement criterion for complex mood transitions within time windows.
 * Examples: RESTLESS→HAPPY within 100 seconds, ANGRY→CALM within 20 seconds
 */
public class PetMoodTransitionCriterion extends AbstractCriterion<PetMoodTransitionCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_mood_transition");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger when a mood transition occurs.
     * @param player The pet owner
     * @param fromMood Starting mood
     * @param fromLevel Starting mood level
     * @param toMood Ending mood
     * @param toLevel Ending mood level
     * @param ticksElapsed Ticks between moods (for time window validation)
     */
    public void trigger(ServerPlayerEntity player, PetComponent.Mood fromMood, int fromLevel,
                       PetComponent.Mood toMood, int toLevel, long ticksElapsed) {
        this.trigger(player, conditions ->
            conditions.matches(fromMood, fromLevel, toMood, toLevel, ticksElapsed));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<String> fromMood,
        Optional<Integer> minFromLevel,
        Optional<String> toMood,
        Optional<Integer> minToLevel,
        Optional<Long> maxTicksElapsed
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.optionalFieldOf("from_mood").forGetter(Conditions::fromMood),
                Codec.INT.optionalFieldOf("min_from_level").forGetter(Conditions::minFromLevel),
                Codec.STRING.optionalFieldOf("to_mood").forGetter(Conditions::toMood),
                Codec.INT.optionalFieldOf("min_to_level").forGetter(Conditions::minToLevel),
                Codec.LONG.optionalFieldOf("max_ticks_elapsed").forGetter(Conditions::maxTicksElapsed)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(PetComponent.Mood from, int fromLvl,
                              PetComponent.Mood to, int toLvl, long ticks) {
            // Check from mood
            if (fromMood.isPresent() && from != null) {
                if (!fromMood.get().equalsIgnoreCase(from.name())) {
                    return false;
                }
            }

            // Check from level
            if (minFromLevel.isPresent() && fromLvl < minFromLevel.get()) {
                return false;
            }

            // Check to mood
            if (toMood.isPresent() && to != null) {
                if (!toMood.get().equalsIgnoreCase(to.name())) {
                    return false;
                }
            }

            // Check to level
            if (minToLevel.isPresent() && toLvl < minToLevel.get()) {
                return false;
            }

            // Check time window
            if (maxTicksElapsed.isPresent() && ticks > maxTicksElapsed.get()) {
                return false;
            }

            return true;
        }
    }
}