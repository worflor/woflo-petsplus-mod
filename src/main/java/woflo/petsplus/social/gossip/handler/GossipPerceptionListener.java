package woflo.petsplus.social.gossip.handler;

import woflo.petsplus.ai.context.perception.PerceptionListener;
import woflo.petsplus.ai.context.perception.PerceptionStimulus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.gossip.RumorEntry;

import java.util.List;

/**
 * Listens to the perception bus and routes stimuli to registered gossip handlers.
 * <p>
 * Integrates the new handler system with the existing perception infrastructure,
 * allowing pets to generate gossip from any perception event (emotions, combat,
 * owner activity, crowd changes, environmental events, etc.).
 * <p>
 * Handlers are evaluated in priority order, with cooldown management and
 * suppression support for exclusive handlers.
 */
public final class GossipPerceptionListener implements PerceptionListener {
    
    private final PetComponent pet;
    
    /**
     * Creates a new gossip perception listener for a specific pet.
     * Should be registered to the pet's perception bus during initialization.
     */
    public GossipPerceptionListener(PetComponent pet) {
        this.pet = pet;
    }
    
    @Override
    public void onStimulus(PerceptionStimulus stimulus) {
        if (pet == null || stimulus == null) {
            return;
        }
        
        // Skip if pet has opted out of gossip
        long currentTick = stimulus.tick();
        if (pet.isGossipOptedOut(currentTick)) {
            return;
        }
        
        // Get applicable handlers for this stimulus type
        List<GossipStimulusHandler> handlers = GossipHandlerRegistry.getHandlersFor(stimulus);
        if (handlers.isEmpty()) {
            return;
        }
        
        // Process handlers in priority order
        for (GossipStimulusHandler handler : handlers) {
            // Skip if handler is on cooldown
            if (GossipHandlerRegistry.isOnCooldown(handler, currentTick)) {
                continue;
            }
            
            // Check if handler should process this stimulus
            if (!handler.shouldHandle(pet, stimulus)) {
                continue;
            }
            
            // Generate rumors from handler
            List<RumorEntry> rumors = handler.generateRumors(pet, stimulus);
            if (rumors.isEmpty()) {
                continue;
            }
            
            // Record rumors in pet's gossip ledger
            for (RumorEntry rumor : rumors) {
                pet.recordRumor(
                    rumor.topicId(),
                    rumor.intensity(),
                    rumor.confidence(),
                    currentTick,
                    rumor.sourceUuid(), // preserve source UUID from rumor
                    rumor.paraphrased(), // use the narrative template from JSON
                    rumor.lastWitnessTick() > 0 // witnessed flag
                );
            }
            
            // Mark handler as activated (start cooldown)
            GossipHandlerRegistry.markActivated(handler, currentTick);
            
            // If handler is suppressive, stop processing further handlers
            if (handler.isSuppressive()) {
                break;
            }
        }
    }
}
