package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.effects.PetsplusEffectManager;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.roles.support.SupportPotionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Manages state for all pets and owners in the game using components for persistence.
 */
public class StateManager {
    private static final Map<ServerWorld, StateManager> WORLD_MANAGERS = new WeakHashMap<>();
    
    private final ServerWorld world;
    private final Map<MobEntity, PetComponent> petComponents = new WeakHashMap<>();
    private final Map<PlayerEntity, OwnerCombatState> ownerStates = new WeakHashMap<>();
    private long lastAuraCleanupTick;
    
    private StateManager(ServerWorld world) {
        this.world = world;
    }
    
    public static StateManager forWorld(ServerWorld world) {
        return WORLD_MANAGERS.computeIfAbsent(world, StateManager::new);
    }
    
    public PetComponent getPetComponent(MobEntity pet) {
        PetComponent component = petComponents.computeIfAbsent(pet, entity -> {
            // Try to get existing component from entity
            PetComponent existing = PetComponent.get(entity);
            if (existing != null) {
                return existing;
            }

            // Create new component and attach to entity
            PetComponent created = new PetComponent(entity);
            PetComponent.set(entity, created);
            return created;
        });

        component.ensureSpeciesDescriptorInitialized();
        return component;
    }
    
    public OwnerCombatState getOwnerState(PlayerEntity owner) {
        return ownerStates.computeIfAbsent(owner, entity -> {
            // Try to get existing component from entity
            OwnerCombatState existing = OwnerCombatState.get(entity);
            if (existing != null) {
                return existing;
            }
            
            // Create new component and attach to entity
            OwnerCombatState state = new OwnerCombatState(entity);
            OwnerCombatState.set(entity, state);
            return state;
        });
    }
    
    /**
     * Assign a role to a pet for testing purposes.
     */
    public boolean assignRole(MobEntity pet, String roleName) {
        if (pet == null || roleName == null) {
            return false;
        }

        Identifier roleId = PetRoleType.normalizeId(roleName);
        if (roleId == null) {
            Petsplus.LOGGER.warn("Attempted to assign invalid role '{}' to pet {}", roleName, pet.getUuid());
            return false;
        }

        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        if (roleType == null) {
            Petsplus.LOGGER.warn("Attempted to assign unknown role '{}' to pet {}", roleId, pet.getUuid());
            return false;
        }

        PetComponent component = getPetComponent(pet);
        component.setRoleId(roleId);
        component.ensureCharacteristics();

        Petsplus.LOGGER.debug("Assigned role {} ({}) to pet {}", roleId, roleType.translationKey(), pet.getUuid());
        return true;
    }
    
    /**
     * Get the count of active owner states.
     */
    public int getOwnerStateCount() {
        return ownerStates.size();
    }
    
    /**
     * Cleanup invalid states and return count.
     */
    public int cleanupInvalidStates() {
        int cleaned = 0;
        
        // Remove invalid pet components
        var petIterator = petComponents.entrySet().iterator();
        while (petIterator.hasNext()) {
            var entry = petIterator.next();
            if (entry.getKey() == null || entry.getKey().isRemoved()) {
                petIterator.remove();
                cleaned++;
            }
        }
        
        // Remove invalid owner states
        var ownerIterator = ownerStates.entrySet().iterator();
        while (ownerIterator.hasNext()) {
            var entry = ownerIterator.next();
            if (entry.getKey() == null || entry.getKey().isRemoved()) {
                ownerIterator.remove();
                cleaned++;
            }
        }
        
        return cleaned;
    }
    
    public void removePet(MobEntity pet) {
        petComponents.remove(pet);
    }
    
    public void removeOwner(PlayerEntity owner) {
        ownerStates.remove(owner);
    }
    
    /**
     * Tick all managed states for cleanup and updates.
     */
    public void tick() {
        // Tick all owner combat states
        ownerStates.values().forEach(OwnerCombatState::tick);
        
        // Clean up expired tags and effects
        woflo.petsplus.effects.TagTargetEffect.cleanupExpiredTags(world.getTime());
        woflo.petsplus.effects.ProjectileDrForOwnerEffect.cleanupExpired(world.getTime());
        
        // Clean up expired particle effect tracking
        woflo.petsplus.ui.CooldownParticleManager.cleanup(world.getTime());

        long time = world.getTime();

        // Pet-local periodic triggers and lightweight support mechanics
        if (!petComponents.isEmpty()) {
            for (Map.Entry<MobEntity, PetComponent> entry : petComponents.entrySet()) {
                MobEntity pet = entry.getKey();
                PetComponent comp = entry.getValue();
                if (pet == null || !pet.isAlive()) continue;
                PlayerEntity owner = comp.getOwner();
                if (owner == null || owner.isRemoved()) continue;

                // Update cooldowns and trigger particle effects on refresh
                comp.updateCooldowns();

                // Emit interval trigger every 20 ticks (approx 1s) and also faster at 40 if needed
                if (time % 20 == 0) {
                    woflo.petsplus.api.TriggerContext ctx = new woflo.petsplus.api.TriggerContext(
                        world, pet, owner, "interval_tick");
                    woflo.petsplus.abilities.AbilityManager.triggerAbilities(pet, ctx);
                }

                try {
                    PetsplusEffectManager.applyRoleAuraEffects(world, pet, comp, owner);
                } catch (Exception e) {
                    Petsplus.LOGGER.warn("Failed to apply aura effects for pet {}", pet.getUuid(), e);
                }

                PetRoleType roleType = comp.getRoleType(false);
                if (roleType != null) {
                    PetRoleType.SupportPotionBehavior behavior = roleType.supportPotionBehavior();
                    if (behavior != null) {
                        pickupNearbyPotionsForSupport(pet, owner, behavior);
                    }
                }

                // Emit role-specific particle effects
                if (woflo.petsplus.ui.ParticleEffectManager.shouldEmitParticles(pet, world)) {
                    woflo.petsplus.ui.ParticleEffectManager.emitRoleParticles(pet, world, time);
                }

                // Feed cheap emotion providers (rate-limited internally)
                try {
                    woflo.petsplus.api.mood.MoodAPI.get().processPet(world, pet, comp, time);
                } catch (Exception e) {
                    Petsplus.LOGGER.warn("Emotion provider processing failed for pet {}", pet.getUuid(), e);
                }
            }
        }

        if (time - lastAuraCleanupTick >= 6000) {
            PetsplusEffectManager.cleanup();
            lastAuraCleanupTick = time;
        }
    }
    
    /**
     * Called when the world is being saved or unloaded to ensure all pet data is persisted.
     * The actual persistence happens through the MobEntityDataMixin, but this method
     * can be used for any additional cleanup or state synchronization.
     */
    public void onWorldSave() {
        // Currently, the MobEntityDataMixin handles automatic persistence
        // This method is available for future enhancements or explicit saves
        int petCount = petComponents.size();
        int ownerCount = ownerStates.size();
        
        if (petCount > 0 || ownerCount > 0) {
            woflo.petsplus.Petsplus.LOGGER.debug("PetsPlus: Preparing to save world {} with {} pets and {} owners", 
                world.getRegistryKey().getValue(), petCount, ownerCount);
        }
    }
    
    /**
     * Get all pet components for debugging/admin purposes.
     */
    public Map<MobEntity, PetComponent> getAllPetComponents() {
        return new HashMap<>(petComponents);
    }
    
    /**
     * Get all owner states for debugging/admin purposes.
     */
    public Map<PlayerEntity, OwnerCombatState> getAllOwnerStates() {
        return new HashMap<>(ownerStates);
    }

    private void pickupNearbyPotionsForSupport(MobEntity pet, PlayerEntity owner, PetRoleType.SupportPotionBehavior behavior) {
        // Allow giving potions: if player Q-drops or throws a potion item near the pet, "store" it by tagging comp state
        // This is a simple pickup: find ItemEntity with potion item in small radius and consume one
        if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
        PetComponent comp = petComponents.get(pet);
        if (comp == null || comp.getLevel() < behavior.minLevel()) return;

        double pickupRadius = SupportPotionUtils.getAutoPickupRadius(comp, behavior);
        var box = pet.getBoundingBox().expand(pickupRadius);
        var items = serverWorld.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, box,
            ie -> !ie.getStack().isEmpty() && (ie.getStack().isOf(net.minecraft.item.Items.POTION)
                || ie.getStack().isOf(net.minecraft.item.Items.SPLASH_POTION)
                || ie.getStack().isOf(net.minecraft.item.Items.LINGERING_POTION)));
        if (items.isEmpty()) return;

        var currentState = SupportPotionUtils.getStoredState(comp);

        for (net.minecraft.entity.ItemEntity picked : items) {
            var stack = picked.getStack();
            var incoming = SupportPotionUtils.createStateFromStack(stack, comp);
            if (!incoming.isValid()) continue;

            var outcome = SupportPotionUtils.mergePotionStates(
                comp,
                currentState,
                incoming,
                false
            );
            if (!outcome.accepted()) {
                if (owner instanceof ServerPlayerEntity serverOwner) {
                    SupportPotionUtils.RejectionReason reason = outcome.rejectionReason();
                    String cueId = "support.potion.reject." + reason.name().toLowerCase() + "." + pet.getUuidAsString();
                    net.minecraft.text.Text message = switch (reason) {
                        case INCOMPATIBLE -> net.minecraft.text.Text.translatable(
                            "petsplus.emotion_cue.support.potion_incompatible",
                            pet.getDisplayName()
                        );
                        case TOO_FULL, INVALID, NONE -> net.minecraft.text.Text.translatable(
                            "petsplus.emotion_cue.support.potion_full",
                            pet.getDisplayName()
                        );
                    };
                    EmotionContextCues.sendCue(serverOwner, cueId, message, 160);
                }
                continue;
            }

            SupportPotionUtils.writeStoredState(comp, outcome.result());
            currentState = outcome.result();

            stack.decrement(1);
            if (stack.isEmpty()) picked.discard();

            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                pet.getX(), pet.getY() + pet.getHeight() * 0.5, pet.getZ(),
                7, 0.25, 0.25, 0.25, 0.02);

            if (owner instanceof ServerPlayerEntity serverOwner) {
                net.minecraft.text.Text message = net.minecraft.text.Text.translatable(
                    "petsplus.emotion_cue.support.potion_stored",
                    pet.getDisplayName()
                );
                String cueId = "support.potion.stored." + pet.getUuidAsString();
                if (outcome.toppedUp()) {
                    message = net.minecraft.text.Text.translatable(
                        "petsplus.emotion_cue.support.potion_topped_up",
                        pet.getDisplayName()
                    );
                    cueId = "support.potion.topped." + pet.getUuidAsString();
                }
                EmotionContextCues.sendCue(serverOwner, cueId, message, 160);
            }

            serverWorld.playSound(null, pet.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_BREWING_STAND_BREW,
                net.minecraft.sound.SoundCategory.NEUTRAL,
                0.4f,
                1.6f);

            break;
        }
    }

}
