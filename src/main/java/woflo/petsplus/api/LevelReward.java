package woflo.petsplus.api;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.state.PetComponent;

/**
 * Represents a reward granted when a pet reaches a specific level.
 * Rewards are defined in role JSON files under the "level_rewards" object.
 * 
 * <h2>JSON Structure:</h2>
 * <pre>{@code
 * "level_rewards": {
 *   "5": [
 *     { "type": "ability_unlock", "ability": "petsplus:shield_bash" },
 *     { "type": "stat_boost", "stat": "health", "amount": 5.0 }
 *   ],
 *   "10": [
 *     { "type": "tribute_required", "item": "minecraft:gold_ingot" }
 *   ]
 * }
 * }</pre>
 * 
 * <h2>Available Reward Types:</h2>
 * <ul>
 *   <li><b>ability_unlock</b> - Unlocks an ability for the pet</li>
 *   <li><b>stat_boost</b> - Grants permanent stat increase (health, speed, attack, defense)</li>
 *   <li><b>tribute_required</b> - Marks level as requiring tribute payment</li>
 * </ul>
 * 
 * <h2>Implementation Notes:</h2>
 * <p>
 * Rewards are applied automatically when a pet levels up via {@link woflo.petsplus.events.XpEventHandler}.
 * All reward data is persisted in {@link PetComponent} via NBT serialization.
 * </p>
 * 
 * @see woflo.petsplus.api.rewards.AbilityUnlockReward
 * @see woflo.petsplus.api.rewards.StatBoostReward
 * @see woflo.petsplus.api.rewards.TributeRequiredReward
 * @see woflo.petsplus.api.rewards.LevelRewardParser
 */
public interface LevelReward {
    /**
     * Applies this reward to the pet.
     * 
     * @param pet The pet entity receiving the reward
     * @param comp The pet's component data
     * @param owner The pet's owner (may be null)
     * @param level The level at which this reward is being applied (prevents race conditions)
     */
    void apply(MobEntity pet, PetComponent comp, ServerPlayerEntity owner, int level);
    
    /**
     * Returns the type identifier for this reward (matches JSON "type" field).
     */
    String getType();
    
    /**
     * Returns a human-readable description of this reward for logging/debugging.
     */
    String getDescription();
}
