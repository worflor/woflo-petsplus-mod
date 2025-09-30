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
 * Advancement criterion for pet interactions like petting or healing.
 * Tracks interaction counts and types.
 */
public class PetInteractionCriterion extends AbstractCriterion<PetInteractionCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_interaction");

    public static final String INTERACTION_PETTING = "petting";
    public static final String INTERACTION_HEALING = "healing";
    public static final String INTERACTION_STARGAZING = "stargazing";

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger when a player interacts with their pet.
     * @param player The pet owner
     * @param interactionType The type of interaction
     * @param count The total count of this interaction type
     */
    public void trigger(ServerPlayerEntity player, String interactionType, int count) {
        this.trigger(player, conditions -> conditions.matches(interactionType, count));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<String> interactionType,
        Optional<Integer> minCount,
        Optional<Integer> maxCount,
        Optional<Integer> exactCount
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.optionalFieldOf("interaction_type").forGetter(Conditions::interactionType),
                Codec.INT.optionalFieldOf("min_count").forGetter(Conditions::minCount),
                Codec.INT.optionalFieldOf("max_count").forGetter(Conditions::maxCount),
                Codec.INT.optionalFieldOf("exact_count").forGetter(Conditions::exactCount)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(String type, int cnt) {
            // Check interaction type
            if (interactionType.isPresent() && !interactionType.get().equals(type)) {
                return false;
            }

            // Check exact count if specified
            if (exactCount.isPresent()) {
                return cnt == exactCount.get();
            }

            // Check count range
            if (minCount.isPresent() && cnt < minCount.get()) {
                return false;
            }
            if (maxCount.isPresent() && cnt > maxCount.get()) {
                return false;
            }

            return true;
        }
    }
}