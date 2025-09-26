package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.effects.AuraTargetResolver;
import woflo.petsplus.effects.PetsplusEffectManager;
import woflo.petsplus.effects.ProjectileDrForOwnerEffect;
import woflo.petsplus.effects.TagTargetEffect;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.roles.support.SupportPotionUtils;
import woflo.petsplus.roles.support.SupportPotionVacuumManager;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.ui.CooldownParticleManager;
import woflo.petsplus.util.EntityTagUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Manages state for all pets and owners in the game using components for persistence.
 */
public class StateManager {
    private static final Map<ServerWorld, StateManager> WORLD_MANAGERS = new WeakHashMap<>();
    private static final long INTERVAL_TICK_SPACING = 20L;
    private static final long SUPPORT_POTION_ACTIVE_RECHECK = 5L;
    private static final long SUPPORT_POTION_IDLE_RECHECK = 40L;
    private static final long PARTICLE_RECHECK_INTERVAL = 20L;
    private static final long DEFAULT_AURA_RECHECK = 40L;
    
    private final ServerWorld world;
    private final Map<MobEntity, PetComponent> petComponents = new WeakHashMap<>();
    private final Map<PlayerEntity, OwnerCombatState> ownerStates = new WeakHashMap<>();
    private long nextMaintenanceTick;
    private final PetSwarmIndex swarmIndex = new PetSwarmIndex();
    private final AuraTargetResolver auraTargetResolver = new AuraTargetResolver(swarmIndex);
    
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

        if (world instanceof ServerWorld serverWorld) {
            MoodService.getInstance().trackPet(serverWorld, pet);
        }

        component.ensureSpeciesDescriptorInitialized();
        swarmIndex.trackPet(pet, component);
        return component;
    }

    @Nullable
    public PetComponent peekPetComponent(MobEntity pet) {
        return petComponents.get(pet);
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
        if (world instanceof ServerWorld) {
            MoodService.getInstance().untrackPet(pet);
        }
        swarmIndex.untrackPet(pet);
    }

    public void removeOwner(PlayerEntity owner) {
        ownerStates.remove(owner);
        UUID ownerId = owner.getUuid();
        swarmIndex.removeOwner(ownerId);
        auraTargetResolver.handleOwnerRemoval(ownerId);
    }

    public void handleOwnerTick(ServerPlayerEntity player) {
        auraTargetResolver.handleOwnerTick(player);
        OwnerCombatState state = ownerStates.get(player);
        if (state != null) {
            state.tick();
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        PetsplusEffectManager.maybeCleanup(server);

        long currentServerTick = server.getTicks();
        if (nextMaintenanceTick == 0L) {
            nextMaintenanceTick = currentServerTick + 20;
        }

        if (currentServerTick >= nextMaintenanceTick) {
            long worldTime = world.getTime();

            TagTargetEffect.cleanupExpiredTags(worldTime);
            EntityTagUtil.cleanupExpiredTags(worldTime);
            ProjectileDrForOwnerEffect.cleanupExpired(worldTime);
            CooldownParticleManager.maybeCleanup(worldTime);

            petComponents.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isRemoved());
            ownerStates.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isRemoved());

            nextMaintenanceTick = currentServerTick + 200;
        }
    }

    public void handlePetTick(MobEntity pet) {
        PetComponent component = petComponents.get(pet);
        if (component == null) {
            return;
        }
        if (!pet.isAlive()) {
            return;
        }

        PlayerEntity owner = component.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner) || owner.isRemoved()) {
            return;
        }

        long time = world.getTime();
        component.ensureTickSchedulingInitialized(time);

        swarmIndex.updatePet(pet, component);
        component.updateCooldowns();

        if (component.isIntervalTickDue(time)) {
            TriggerContext ctx = new TriggerContext(world, pet, serverOwner, "interval_tick");
            woflo.petsplus.abilities.AbilityManager.triggerAbilities(pet, ctx);
            component.scheduleNextIntervalTick(time + INTERVAL_TICK_SPACING);
        }

        PetRoleType roleType = component.getRoleType(false);
        PetRoleType.SupportPotionBehavior supportBehavior = null;
        boolean hasAuraContent = false;
        if (roleType != null) {
            supportBehavior = roleType.supportPotionBehavior();
            hasAuraContent = !roleType.passiveAuras().isEmpty() || supportBehavior != null;
        }

        if (hasAuraContent && component.isAuraCheckDue(time)) {
            try {
                long nextTick = PetsplusEffectManager.applyRoleAuraEffects(
                    world,
                    pet,
                    component,
                    serverOwner,
                    auraTargetResolver,
                    time
                );
                if (nextTick == Long.MAX_VALUE) {
                    component.scheduleNextAuraCheck(time + DEFAULT_AURA_RECHECK);
                } else {
                    component.scheduleNextAuraCheck(Math.max(time + 1, nextTick));
                }
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Failed to apply aura effects for pet {}", pet.getUuid(), e);
                component.scheduleNextAuraCheck(time + DEFAULT_AURA_RECHECK);
            }
        } else if (!hasAuraContent) {
            component.scheduleNextAuraCheck(Long.MAX_VALUE);
        }

        if (supportBehavior != null && component.isSupportPotionScanDue(time)) {
            boolean processed = pickupNearbyPotionsForSupport(pet, serverOwner, component, supportBehavior);
            long delay = processed ? SUPPORT_POTION_ACTIVE_RECHECK : SUPPORT_POTION_IDLE_RECHECK;
            component.scheduleNextSupportPotionScan(time + delay);
        }

        if (component.isParticleCheckDue(time)) {
            boolean emitted = false;
            if (woflo.petsplus.ui.ParticleEffectManager.shouldEmitParticles(pet, world)) {
                woflo.petsplus.ui.ParticleEffectManager.emitRoleParticles(pet, world, time);
                emitted = true;
            }
            long delay = emitted ? PARTICLE_RECHECK_INTERVAL : PARTICLE_RECHECK_INTERVAL * 2;
            component.scheduleNextParticleCheck(time + delay);
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

    public PetSwarmIndex getSwarmIndex() {
        return swarmIndex;
    }

    public AuraTargetResolver getAuraTargetResolver() {
        return auraTargetResolver;
    }

    private boolean pickupNearbyPotionsForSupport(MobEntity pet, PlayerEntity owner, PetComponent component,
                                                  PetRoleType.SupportPotionBehavior behavior) {
        if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) return false;
        if (component.getLevel() < behavior.minLevel()) return false;

        double pickupRadius = SupportPotionUtils.getAutoPickupRadius(component, behavior);
        List<net.minecraft.entity.ItemEntity> items = SupportPotionVacuumManager.getInstance()
            .collectPotionsNearby(pet, pickupRadius);
        if (items.isEmpty()) return false;

        var currentState = SupportPotionUtils.getStoredState(component);

        boolean acceptedAny = false;

        for (net.minecraft.entity.ItemEntity picked : items) {
            var stack = picked.getStack();
            var incoming = SupportPotionUtils.createStateFromStack(stack, component);
            if (!incoming.isValid()) continue;

            var outcome = SupportPotionUtils.mergePotionStates(
                component,
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

            SupportPotionUtils.writeStoredState(component, outcome.result());
            currentState = outcome.result();
            acceptedAny = true;

            stack.decrement(1);
            SupportPotionVacuumManager.getInstance().handleStackChanged(picked);
            if (stack.isEmpty()) {
                SupportPotionVacuumManager.getInstance().remove(picked);
                picked.discard();
            }

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
        return acceptedAny;
    }

}
