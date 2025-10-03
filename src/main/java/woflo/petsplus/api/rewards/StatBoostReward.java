package woflo.petsplus.api.rewards;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.LevelReward;
import woflo.petsplus.state.PetComponent;

/**
 * Grants a permanent flat stat boost to the pet.
 * 
 * <h2>JSON Format:</h2>
 * <pre>{@code
 * {
 *   "type": "stat_boost",
 *   "stat": "health",
 *   "amount": 5.0
 * }
 * }</pre>
 * 
 * <h2>Supported Stats:</h2>
 * <ul>
 *   <li><b>health</b> - Max health (flat addition, e.g., 5.0 = +5 hearts)</li>
 *   <li><b>speed</b> - Movement speed (flat addition, e.g., 0.02 = +2% base speed)</li>
 *   <li><b>attack</b> - Attack damage (flat addition, e.g., 2.0 = +2 damage)</li>
 *   <li><b>defense</b> - Armor points (flat addition, e.g., 3.0 = +3 armor)</li>
 * </ul>
 * 
 * <h2>Behavior:</h2>
 * <ul>
 *   <li>Uses {@link net.minecraft.entity.attribute.EntityAttributeModifier.Operation#ADD_VALUE ADD_VALUE} operation</li>
 *   <li>Boosts stack cumulatively (level 5 grants +5, level 10 grants +3 → total +8)</li>
 *   <li>Applied automatically after attribute recalculation</li>
 *   <li>Persistent via {@link PetComponent#getPermanentStatBoost(String)}</li>
 * </ul>
 * 
 * @see PetComponent#addPermanentStatBoost(String, float)
 * @see PetComponent#getPermanentStatBoost(String)
 * @see woflo.petsplus.stats.PetAttributeManager#applyPermanentStatBoosts
 */
public final class StatBoostReward implements LevelReward {
    private final String stat;
    private final float amount;
    
    public StatBoostReward(String stat, float amount) {
        this.stat = stat;
        this.amount = amount;
    }
    
    @Override
    public void apply(MobEntity pet, PetComponent comp, ServerPlayerEntity owner, int level) {
        // Validate stat name at runtime to prevent invalid stats from bypassing parser
        if (!StatNames.isValid(stat)) {
            Petsplus.LOGGER.warn("Applying unknown stat '{}' at level {} for pet {} (may not have effect)",
                stat, level, pet.getDisplayName().getString());
        }
        
        // Validate amount is positive (negative amounts could cause unexpected behavior)
        if (amount < 0) {
            Petsplus.LOGGER.error("Attempted to apply negative stat boost ({} {}) to pet {} at level {}. " +
                "Negative stat boosts are not supported.",
                amount, stat, pet.getDisplayName().getString(), level);
            return;
        }
        
        // Add permanent stat boost (caller handles attribute recalculation)
        comp.addPermanentStatBoost(stat, amount);
    }
    
    @Override
    public String getType() {
        return "stat_boost";
    }
    
    @Override
    public String getDescription() {
        return "+" + amount + " " + stat;
    }
    
    public String getStat() {
        return stat;
    }
    
    public float getAmount() {
        return amount;
    }
}
