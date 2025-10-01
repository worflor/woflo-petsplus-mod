package woflo.petsplus.advancement.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.entity.EntityType;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.util.Optional;

/**
 * Advancement criterion for pet trading operations.
 * Tracks when players give or receive pets through the leash trading system.
 */
public class PetTradingCriterion extends AbstractCriterion<PetTradingCriterion.Conditions> {

    public static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "pet_trading");

    public static final String ROLE_INITIATOR = "initiator";
    public static final String ROLE_RECIPIENT = "recipient";

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    /**
     * Trigger when a player initiates a pet trade (gives away a pet).
     * @param player The player giving away the pet
     * @param petType The type of pet being traded
     * @param tradeCount The total number of pets this player has given away
     */
    public void triggerInitiator(ServerPlayerEntity player, EntityType<?> petType, int tradeCount) {
        this.trigger(player, conditions -> conditions.matches(ROLE_INITIATOR, petType, tradeCount));
    }

    /**
     * Trigger when a player receives a pet through trading.
     * @param player The player receiving the pet
     * @param petType The type of pet being received
     * @param receiveCount The total number of pets this player has received
     */
    public void triggerRecipient(ServerPlayerEntity player, EntityType<?> petType, int receiveCount) {
        this.trigger(player, conditions -> conditions.matches(ROLE_RECIPIENT, petType, receiveCount));
    }

    public record Conditions(
        Optional<LootContextPredicate> player,
        Optional<String> role,
        Optional<String> entityType,
        Optional<Integer> minCount,
        Optional<Integer> maxCount,
        Optional<Integer> exactCount
    ) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.optionalFieldOf("role").forGetter(Conditions::role),
                Codec.STRING.optionalFieldOf("entity_type").forGetter(Conditions::entityType),
                Codec.INT.optionalFieldOf("min_count").forGetter(Conditions::minCount),
                Codec.INT.optionalFieldOf("max_count").forGetter(Conditions::maxCount),
                Codec.INT.optionalFieldOf("exact_count").forGetter(Conditions::exactCount)
            ).apply(instance, Conditions::new)
        );

        /**
         * Check if the conditions match the trading event.
         */
        public boolean matches(String role, EntityType<?> petType, int count) {
            // Check role if specified
            if (this.role.isPresent() && !this.role.get().equals(role)) {
                return false;
            }

            // Check entity type if specified
            if (this.entityType.isPresent()) {
                String expectedType = this.entityType.get();
                String actualType = EntityType.getId(petType).toString();
                if (!expectedType.equals(actualType)) {
                    return false;
                }
            }

            // Check count constraints
            if (this.exactCount.isPresent() && count != this.exactCount.get()) {
                return false;
            }

            if (this.minCount.isPresent() && count < this.minCount.get()) {
                return false;
            }

            if (this.maxCount.isPresent() && count > this.maxCount.get()) {
                return false;
            }

            return true;
        }
    }
}
