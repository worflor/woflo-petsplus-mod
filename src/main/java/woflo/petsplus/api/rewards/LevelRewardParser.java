package woflo.petsplus.api.rewards;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.LevelReward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses level reward definitions from JSON into concrete reward objects.
 * 
 * <h2>Expected JSON Structure:</h2>
 * <pre>{@code
 * "level_rewards": {
 *   "3": [
 *     { "type": "stat_boost", "stat": "health", "amount": 5.0 },
 *     { "type": "ability_unlock", "ability": "petsplus:shield_bash" }
 *   ],
 *   "10": [
 *     { "type": "tribute_required", "item": "minecraft:gold_ingot" }
 *   ]
 * }
 * }</pre>
 * 
 * <h2>Supported Reward Types:</h2>
 * <ul>
 *   <li><b>ability_unlock</b> - Requires "ability" field (Identifier)</li>
 *   <li><b>stat_boost</b> - Requires "stat" (String) and "amount" (float) fields</li>
 *   <li><b>tribute_required</b> - Requires "item" field (Identifier)</li>
 * </ul>
 * 
 * <h2>Validation Rules:</h2>
 * <ul>
 *   <li>All level numbers must be valid integers</li>
 *   <li>Each level must have an array of reward objects</li>
 *   <li>Each reward must have a "type" field</li>
 *   <li>Type-specific required fields must be present</li>
 *   <li>Invalid entries are logged and skipped (non-fatal)</li>
 * </ul>
 * 
 * @see LevelReward
 * @see AbilityUnlockReward
 * @see StatBoostReward
 * @see TributeRequiredReward
 */
public final class LevelRewardParser {
    
    /**
     * Parses the "level_rewards" object from a role JSON file.
     * 
     * @param levelRewardsJson The "level_rewards" JSON object (keys are level numbers)
     * @param source Description of the source for error reporting
     * @return Map of level → list of rewards
     */
    public static Map<Integer, List<LevelReward>> parseRewards(JsonObject levelRewardsJson, String source) {
        Map<Integer, List<LevelReward>> rewards = new HashMap<>();
        
        if (levelRewardsJson == null) {
            return rewards;
        }
        
        // Track tribute milestones to detect duplicates
        Map<Integer, Identifier> tributeMilestones = new HashMap<>();
        
        for (String levelStr : levelRewardsJson.keySet()) {
            int level;
            try {
                level = Integer.parseInt(levelStr);
            } catch (NumberFormatException e) {
                Petsplus.LOGGER.error("Invalid level '{}' in level_rewards for {}", levelStr, source);
                continue;
            }
            
            JsonElement rewardsElement = levelRewardsJson.get(levelStr);
            if (!rewardsElement.isJsonArray()) {
                Petsplus.LOGGER.error("Level rewards for level {} must be an array in {}", level, source);
                continue;
            }
            
            JsonArray rewardsArray = rewardsElement.getAsJsonArray();
            List<LevelReward> levelRewards = new ArrayList<>();
            
            for (int i = 0; i < rewardsArray.size(); i++) {
                JsonElement rewardElement = rewardsArray.get(i);
                if (!rewardElement.isJsonObject()) {
                    Petsplus.LOGGER.error("Reward at index {} for level {} must be an object in {}", i, level, source);
                    continue;
                }
                
                JsonObject rewardJson = rewardElement.getAsJsonObject();
                LevelReward reward = parseReward(rewardJson, level, source);
                
                if (reward != null) {
                    levelRewards.add(reward);
                    
                    // Check for duplicate tribute milestones
                    if (reward instanceof TributeRequiredReward tributeReward) {
                        if (tributeMilestones.containsKey(level)) {
                            Petsplus.LOGGER.warn("Duplicate tribute_required reward at level {} in {}. " +
                                "Previous: {}, Current: {}. Only the last one will be used.",
                                level, source, tributeMilestones.get(level), tributeReward.getItemId());
                        }
                        tributeMilestones.put(level, tributeReward.getItemId());
                    }
                }
            }
            
            if (!levelRewards.isEmpty()) {
                rewards.put(level, levelRewards);
            }
        }
        
        if (!rewards.isEmpty()) {
            Petsplus.LOGGER.info("Loaded {} level reward configurations for {}", rewards.size(), source);
        }
        
        return rewards;
    }
    
    /**
     * Parses a single reward object.
     */
    private static LevelReward parseReward(JsonObject rewardJson, int level, String source) {
        if (!rewardJson.has("type")) {
            Petsplus.LOGGER.error("Reward at level {} missing 'type' field in {}", level, source);
            return null;
        }
        
        String type = rewardJson.get("type").getAsString();
        
        try {
            return switch (type) {
                case "ability_unlock" -> parseAbilityUnlock(rewardJson, level, source);
                case "stat_boost" -> parseStatBoost(rewardJson, level, source);
                case "tribute_required" -> parseTributeRequired(rewardJson, level, source);
                default -> {
                    Petsplus.LOGGER.error("Unknown reward type '{}' at level {} in {}", type, level, source);
                    yield null;
                }
            };
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to parse {} reward at level {} in {}: {}", type, level, source, e.getMessage());
            return null;
        }
    }
    
    private static AbilityUnlockReward parseAbilityUnlock(JsonObject json, int level, String source) {
        if (!json.has("ability")) {
            Petsplus.LOGGER.error("ability_unlock reward at level {} missing 'ability' field in {}", level, source);
            return null;
        }
        
        String abilityStr = json.get("ability").getAsString();
        Identifier abilityId = Identifier.tryParse(abilityStr);
        
        if (abilityId == null) {
            Petsplus.LOGGER.error("Invalid ability identifier '{}' at level {} in {}", abilityStr, level, source);
            return null;
        }
        
        return new AbilityUnlockReward(abilityId);
    }
    
    private static StatBoostReward parseStatBoost(JsonObject json, int level, String source) {
        if (!json.has("stat")) {
            Petsplus.LOGGER.error("stat_boost reward at level {} missing 'stat' field in {}", level, source);
            return null;
        }
        
        if (!json.has("amount")) {
            Petsplus.LOGGER.error("stat_boost reward at level {} missing 'amount' field in {}", level, source);
            return null;
        }
        
        String stat = json.get("stat").getAsString();
        float amount = json.get("amount").getAsFloat();
        
        // Validate stat name
        if (!StatNames.isValid(stat)) {
            Petsplus.LOGGER.warn("Unknown stat '{}' at level {} in {} (will be stored but may not apply)", stat, level, source);
        }
        
        // Validate amount is positive
        if (amount < 0) {
            Petsplus.LOGGER.error("stat_boost reward at level {} has negative amount ({}) in {}. " +
                "Negative amounts are not supported.", level, amount, source);
            return null;
        }
        
        return new StatBoostReward(stat, amount);
    }
    
    private static TributeRequiredReward parseTributeRequired(JsonObject json, int level, String source) {
        if (!json.has("item")) {
            Petsplus.LOGGER.error("tribute_required reward at level {} missing 'item' field in {}", level, source);
            return null;
        }
        
        String itemStr = json.get("item").getAsString();
        Identifier itemId = Identifier.tryParse(itemStr);
        
        if (itemId == null) {
            Petsplus.LOGGER.error("Invalid item identifier '{}' at level {} in {}", itemStr, level, source);
            return null;
        }
        
        return new TributeRequiredReward(itemId);
    }
}
