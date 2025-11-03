package woflo.petsplus.social.gossip.handler;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.context.perception.PerceptionStimulus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for gossip stimulus handlers.
 * <p>
 * Manages handler registration, priority ordering, and dispatch to appropriate
 * handlers when perception stimuli arrive. Supports datapack-driven extensibility.
 * <p>
 * Handlers are registered from:
 * - Built-in mod handlers (emotion, combat, owner activity)
 * - Datapack JSON definitions in {@code data/<namespace>/gossip_handlers/}
 * - Other mods via programmatic registration
 */
public final class GossipHandlerRegistry {
    
    private static final Map<Identifier, GossipStimulusHandler> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<String, List<GossipStimulusHandler>> BY_STIMULUS_TYPE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Long> HANDLER_COOLDOWNS = new ConcurrentHashMap<>();
    
    private GossipHandlerRegistry() {}
    
    /**
     * Registers a gossip handler.
     * Handlers with the same ID replace previous registrations (datapack override pattern).
     */
    public static void register(GossipStimulusHandler handler) {
        Identifier id = handler.getId();
        
        // Remove old handler from stimulus type index if replacing
        GossipStimulusHandler old = HANDLERS.put(id, handler);
        if (old != null) {
            String oldType = old.getStimulusType();
            BY_STIMULUS_TYPE.computeIfPresent(oldType, (k, list) -> {
                list.remove(old);
                return list.isEmpty() ? null : list;
            });
            Petsplus.LOGGER.debug("Replaced gossip handler {} (was {}, now {})", 
                id, old.getClass().getSimpleName(), handler.getClass().getSimpleName());
        }
        
        // Add to stimulus type index
        String type = handler.getStimulusType();
        BY_STIMULUS_TYPE.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
        
        // Sort by priority (highest first)
        BY_STIMULUS_TYPE.get(type).sort(Comparator.comparingInt(GossipStimulusHandler::getPriority).reversed());
        
        Petsplus.LOGGER.debug("Registered gossip handler {} for stimulus type {}", id, type);
    }
    
    /**
     * Unregisters a gossip handler by ID.
     */
    public static void unregister(Identifier id) {
        GossipStimulusHandler removed = HANDLERS.remove(id);
        if (removed != null) {
            String type = removed.getStimulusType();
            BY_STIMULUS_TYPE.computeIfPresent(type, (k, list) -> {
                list.remove(removed);
                return list.isEmpty() ? null : list;
            });
            HANDLER_COOLDOWNS.remove(id);
            Petsplus.LOGGER.debug("Unregistered gossip handler {}", id);
        }
    }
    
    /**
     * Gets all handlers for a specific stimulus type, ordered by priority.
     */
    public static List<GossipStimulusHandler> getHandlersForType(String stimulusType) {
        return BY_STIMULUS_TYPE.getOrDefault(stimulusType, Collections.emptyList());
    }
    
    /**
     * Gets all handlers for a stimulus, including wildcard handlers.
     * @param stimulus The perception stimulus
     * @return List of applicable handlers, ordered by priority
     */
    public static List<GossipStimulusHandler> getHandlersFor(PerceptionStimulus stimulus) {
        List<GossipStimulusHandler> handlers = new ArrayList<>();
        
        // Add exact type matches
        handlers.addAll(getHandlersForType(stimulus.type().toString()));
        
        // Add wildcard handlers (stimulus type = "*")
        handlers.addAll(getHandlersForType("*"));
        
        // Sort by priority
        handlers.sort(Comparator.comparingInt(GossipStimulusHandler::getPriority).reversed());
        
        return handlers;
    }
    
    /**
     * Checks if a handler is currently on cooldown.
     */
    public static boolean isOnCooldown(GossipStimulusHandler handler, long currentTick) {
        if (handler.getCooldownTicks() <= 0) {
            return false;
        }
        
        Long lastActivation = HANDLER_COOLDOWNS.get(handler.getId());
        if (lastActivation == null) {
            return false;
        }
        
        return (currentTick - lastActivation) < handler.getCooldownTicks();
    }
    
    /**
     * Marks a handler as activated, starting its cooldown.
     */
    public static void markActivated(GossipStimulusHandler handler, long currentTick) {
        if (handler.getCooldownTicks() > 0) {
            HANDLER_COOLDOWNS.put(handler.getId(), currentTick);
        }
    }
    
    /**
     * Gets a handler by ID.
     */
    public static Optional<GossipStimulusHandler> getHandler(Identifier id) {
        return Optional.ofNullable(HANDLERS.get(id));
    }
    
    /**
     * Gets all registered handlers.
     */
    public static Collection<GossipStimulusHandler> getAllHandlers() {
        return Collections.unmodifiableCollection(HANDLERS.values());
    }
    
    /**
     * Clears all handlers (used for datapack reload).
     */
    public static void clear() {
        HANDLERS.clear();
        BY_STIMULUS_TYPE.clear();
        HANDLER_COOLDOWNS.clear();
        Petsplus.LOGGER.debug("Cleared gossip handler registry");
    }
    
    /**
     * Gets registry statistics for debugging.
     */
    public static String getStats() {
        return String.format("GossipHandlerRegistry: %d handlers, %d stimulus types",
            HANDLERS.size(), BY_STIMULUS_TYPE.size());
    }
}
