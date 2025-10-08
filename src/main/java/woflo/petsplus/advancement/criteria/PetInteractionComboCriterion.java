package woflo.petsplus.advancement.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.util.Map;
import java.util.Optional;

/**
 * Advancement criterion that triggers when a player completes combinations of different interactions.
 * Used for tracking diverse interaction patterns with pets.
 */
public class PetInteractionComboCriterion extends AbstractCriterion<PetInteractionComboCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_interaction_combo");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger this criterion when a player completes an interaction.
     * @param player The pet owner
     * @param interactionCounts Map of interaction types to their counts
     */
    public void trigger(ServerPlayerEntity player, Map<String, Integer> interactionCounts) {
        this.trigger(player, conditions -> conditions.matches(interactionCounts));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<Map<String, Integer>> requiredInteractions,
        Optional<Integer> totalInteractions,
        Optional<Integer> uniqueInteractionTypes
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("required_interactions").forGetter(Conditions::requiredInteractions),
                Codec.INT.optionalFieldOf("total_interactions").forGetter(Conditions::totalInteractions),
                Codec.INT.optionalFieldOf("unique_interaction_types").forGetter(Conditions::uniqueInteractionTypes)
            ).apply(instance, Conditions::new)
        );

        public boolean matches(Map<String, Integer> playerInteractionCounts) {
            // Check specific interaction requirements
            if (requiredInteractions.isPresent()) {
                Map<String, Integer> required = requiredInteractions.get();
                for (Map.Entry<String, Integer> requirement : required.entrySet()) {
                    String interactionType = requirement.getKey();
                    int requiredCount = requirement.getValue();
                    
                    int playerCount = playerInteractionCounts.getOrDefault(interactionType, 0);
                    if (playerCount < requiredCount) {
                        return false;
                    }
                }
            }

            // Check total interaction count
            if (totalInteractions.isPresent()) {
                int total = playerInteractionCounts.values().stream().mapToInt(Integer::intValue).sum();
                if (total < totalInteractions.get()) {
                    return false;
                }
            }

            // Check unique interaction types
            if (uniqueInteractionTypes.isPresent()) {
                if (playerInteractionCounts.size() < uniqueInteractionTypes.get()) {
                    return false;
                }
            }

            return true;
        }
    }
}
