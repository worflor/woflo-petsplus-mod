package woflo.petsplus.social.gossip.handler;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.perception.PerceptionStimulus;
import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Handles perception stimuli to generate gossip rumors for pets.
 * <p>
 * Extensible through JSON datapacks - modpacks and mods can define custom handlers
 * that observe specific perception events and generate contextual gossip.
 * <p>
 * Each handler:
 * - Watches for specific stimulus types (emotion, combat, owner activity, etc.)
 * - Evaluates context conditions using StateKeys
 * - Generates emergent topic IDs based on the situation
 * - Creates narrative templates with dynamic substitution
 * - Calculates intensity/confidence from formulas
 * <p>
 * Handlers are registered via JSON in {@code data/<namespace>/gossip_handlers/}.
 */
public interface GossipStimulusHandler {
    
    /**
     * Unique identifier for this handler.
     * Used for datapack override detection and debugging.
     */
    Identifier getId();
    
    /**
     * The stimulus type this handler observes.
     * Examples: EMOTION_SAMPLE, COMBAT_START, OWNER_ACTIVITY, CROWD_SUMMARY
     */
    String getStimulusType();
    
    /**
     * Priority for handler execution when multiple handlers match the same stimulus.
     * Higher priority handlers execute first.
     * Default handlers use priority 0-100, allowing datapacks to override with 100+.
     */
    int getPriority();
    
    /**
     * Evaluates whether this handler should generate gossip for the given stimulus.
     * <p>
     * Uses context checks against StateKeys to filter appropriate situations:
     * - COMBAT_ENGAGED: only generate combat gossip when actually fighting
     * - OWNER_LAST_HURT_TICK: gossip about recent owner damage
     * - ARCANE_CACHED_AMBIENT_ENERGY: react to magical environments
     * 
     * @param pet The pet evaluating the stimulus
     * @param stimulus The perception event
     * @return true if this handler should generate gossip
     */
    boolean shouldHandle(PetComponent pet, PerceptionStimulus stimulus);
    
    /**
     * Generates gossip rumors from the stimulus.
     * <p>
     * May return multiple rumors if the situation warrants it (e.g., complex emotional
     * snapshots, multi-entity combat scenarios).
     * <p>
     * Topic IDs are generated via {@link GossipTopicBuilder} using context paths,
     * ensuring emergent topics without hardcoding.
     * 
     * @param pet The pet generating gossip
     * @param stimulus The perception event
     * @return List of rumors to add to the pet's ledger (empty if none generated)
     */
    List<RumorEntry> generateRumors(PetComponent pet, PerceptionStimulus stimulus);
    
    /**
     * Optional cooldown between activations of this specific handler (in ticks).
     * Prevents spam from high-frequency stimuli.
     * Default: 0 (no handler-specific cooldown, relies on ledger share cooldowns)
     */
    default long getCooldownTicks() {
        return 0L;
    }
    
    /**
     * Whether this handler should suppress lower-priority handlers when it activates.
     * Used for exclusive handlers that fully describe a situation.
     * Default: false (allows multiple handlers to contribute)
     */
    default boolean isSuppressive() {
        return false;
    }
}
