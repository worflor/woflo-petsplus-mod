package woflo.petsplus.history;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;

import java.util.UUID;

/**
 * Utility class for recording pet history events.
 * Provides a clean API for recording different types of events without requiring
 * direct knowledge of the internal history storage implementation.
 */
public class HistoryManager {
    
    private static final int MAX_HISTORY_SIZE = 50; // Configurable limit
    
    /**
     * Records a trade event when a pet is transferred between owners.
     * 
     * @param pet The pet being traded
     * @param fromOwner The player giving away the pet
     * @param toOwner The player receiving the pet
     * @param method The trading method used (e.g., "leash", "command")
     */
    public static void recordTradeEvent(MobEntity pet, ServerPlayerEntity fromOwner, 
                                       ServerPlayerEntity toOwner, String method) {
        if (pet == null || fromOwner == null || toOwner == null || method == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.trade(
                    timestamp,
                    fromOwner.getUuid(),
                    fromOwner.getName().getString(),
                    toOwner.getUuid(),
                    toOwner.getName().getString(),
                    method
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded trade event for pet {}: {} -> {} via {}", 
                    pet.getUuidAsString(), fromOwner.getName().getString(), 
                    toOwner.getName().getString(), method);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record trade event for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a level up event when a pet gains a level.
     * 
     * @param pet The pet that leveled up
     * @param newLevel The new level achieved
     * @param source The source of the XP (e.g., "combat", "experience")
     */
    public static void recordLevelUpEvent(MobEntity pet, int newLevel, String source) {
        if (pet == null || source == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null && component.getOwner() instanceof ServerPlayerEntity owner) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.levelUp(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    newLevel,
                    source
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded level up event for pet {}: level {} via {}", 
                    pet.getUuidAsString(), newLevel, source);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record level up event for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a combat event when a pet participates in combat.
     * 
     * @param pet The pet involved in combat
     * @param result The combat result ("victory" or "defeat")
     * @param opponentType The type of opponent (e.g., "zombie", "skeleton")
     */
    public static void recordCombatEvent(MobEntity pet, String result, String opponentType) {
        if (pet == null || result == null || opponentType == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null && component.getOwner() instanceof ServerPlayerEntity owner) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.combat(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    result,
                    opponentType
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded combat event for pet {}: {} vs {}", 
                    pet.getUuidAsString(), result, opponentType);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record combat event for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a mood milestone event when a pet achieves a significant mood state.
     * 
     * @param pet The pet with the mood milestone
     * @param mood The mood type (e.g., "happy", "excited")
     * @param intensity The mood intensity (0.0 to 1.0)
     */
    public static void recordMoodMilestoneEvent(MobEntity pet, String mood, float intensity) {
        if (pet == null || mood == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null && component.getOwner() instanceof ServerPlayerEntity owner) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.moodMilestone(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    mood,
                    intensity
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded mood milestone event for pet {}: {} ({})", 
                    pet.getUuidAsString(), mood, intensity);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record mood milestone event for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records an ownership start event when a pet first gets an owner.
     * 
     * @param pet The pet being owned
     * @param owner The new owner
     * @param method The ownership method (e.g., "tame", "spawn", "trade")
     */
    public static void recordOwnershipStartEvent(MobEntity pet, ServerPlayerEntity owner, String method) {
        if (pet == null || owner == null || method == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.ownershipStart(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    method
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded ownership start event for pet {}: {} via {}", 
                    pet.getUuidAsString(), owner.getName().getString(), method);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record ownership start event for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a role change event when a pet's role is changed.
     * 
     * @param pet The pet with the role change
     * @param fromRole The previous role
     * @param toRole The new role
     */
    public static void recordRoleChangeEvent(MobEntity pet, String fromRole, String toRole) {
        if (pet == null || fromRole == null || toRole == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null && component.getOwner() instanceof ServerPlayerEntity owner) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.roleChange(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    fromRole,
                    toRole
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded role change event for pet {}: {} -> {}", 
                    pet.getUuidAsString(), fromRole, toRole);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record role change event for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a dream escape achievement (Eepy Eeper role).
     * This is part of the modular achievement system that ties into owner history.
     * 
     * @param pet The Eepy Eeper pet that escaped death
     * @param owner The owner at the time of the dream escape
     */
    public static void recordDreamEscape(MobEntity pet, PlayerEntity owner) {
        if (pet == null || owner == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.dreamEscape(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString()
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded dream escape achievement for pet {}: owner {}", 
                    pet.getUuidAsString(), owner.getName().getString());
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record dream escape achievement for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a pet sacrifice achievement (Cursed One role).
     * This is part of the modular achievement system that ties into owner history.
     * 
     * @param pet The Cursed One pet that sacrificed itself
     * @param owner The owner that was resurrected
     * @param resurrectionHealth The health amount restored
     */
    public static void recordPetSacrifice(MobEntity pet, PlayerEntity owner, float resurrectionHealth) {
        if (pet == null || owner == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.petSacrifice(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    resurrectionHealth
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded pet sacrifice achievement for pet {}: owner {} restored to {} health", 
                    pet.getUuidAsString(), owner.getName().getString(), resurrectionHealth);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record pet sacrifice achievement for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a guardian protection achievement (Guardian role).
     * This is part of the modular achievement system that ties into owner history.
     * 
     * @param pet The Guardian pet that redirected damage
     * @param owner The owner being protected
     * @param damageRedirected The amount of damage redirected
     */
    public static void recordGuardianProtection(MobEntity pet, PlayerEntity owner, float damageRedirected) {
        if (pet == null || owner == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.guardianProtection(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    damageRedirected
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded guardian protection achievement for pet {}: {} damage redirected for {}", 
                    pet.getUuidAsString(), damageRedirected, owner.getName().getString());
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record guardian protection achievement for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records an ally healed achievement (Support role).
     * This is part of the modular achievement system that ties into owner history.
     * 
     * @param pet The Support pet that healed an ally
     * @param owner The owner of the support pet
     * @param allyUuid The UUID of the healed ally
     * @param day The Minecraft day (for unique daily tracking)
     */
    public static void recordAllyHealed(MobEntity pet, PlayerEntity owner, UUID allyUuid, long day) {
        if (pet == null || owner == null || allyUuid == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.allyHealed(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString(),
                    allyUuid,
                    day
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded ally healed achievement for pet {}: owner {}, ally {}", 
                    pet.getUuidAsString(), owner.getName().getString(), allyUuid.toString());
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record ally healed achievement for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records a best friend foreverer achievement.
     * This is part of the modular achievement system that ties into owner history.
     * 
     * @param pet The pet that reached max friendship
     * @param owner The owner who achieved max friendship
     */
    public static void recordBestFriendForeverer(MobEntity pet, PlayerEntity owner) {
        if (pet == null || owner == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.bestFriendForeverer(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString()
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded best friend foreverer achievement for pet {}: owner {}", 
                    pet.getUuidAsString(), owner.getName().getString());
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record best friend foreverer achievement for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Records an "or not" achievement (first permanent pet death).
     * This is part of the modular achievement system that ties into owner history.
     * 
     * @param pet The pet that died permanently
     * @param owner The owner at the time of death
     */
    public static void recordOrNot(MobEntity pet, PlayerEntity owner) {
        if (pet == null || owner == null) {
            return;
        }
        
        try {
            PetComponent component = PetComponent.get(pet);
            if (component != null) {
                long timestamp = pet.getWorld().getTime();
                HistoryEvent event = HistoryEvent.orNot(
                    timestamp,
                    owner.getUuid(),
                    owner.getName().getString()
                );
                component.addHistoryEvent(event);
                
                Petsplus.LOGGER.debug("Recorded 'or not' achievement for pet {}: owner {}", 
                    pet.getUuidAsString(), owner.getName().getString());
            }
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to record 'or not' achievement for pet " + pet.getUuidAsString(), e);
        }
    }
    
    /**
     * Gets the maximum history size limit.
     */
    public static int getMaxHistorySize() {
        return MAX_HISTORY_SIZE;
    }
}
