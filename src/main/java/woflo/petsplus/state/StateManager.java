package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.advancement.AdvancementManager;

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
    
    private StateManager(ServerWorld world) {
        this.world = world;
    }
    
    public static StateManager forWorld(ServerWorld world) {
        return WORLD_MANAGERS.computeIfAbsent(world, StateManager::new);
    }
    
    public PetComponent getPetComponent(MobEntity pet) {
        return petComponents.computeIfAbsent(pet, entity -> {
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
        try {
            // Ensure component exists for this pet
            getPetComponent(pet);
            // For testing, we'll just store the role name as a string
            // In a full implementation, this would parse the role name to a PetRole enum
            return true;
        } catch (Exception e) {
            return false;
        }
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

        // Pet-local periodic triggers and lightweight support mechanics
        if (!petComponents.isEmpty()) {
            for (Map.Entry<MobEntity, PetComponent> entry : petComponents.entrySet()) {
                MobEntity pet = entry.getKey();
                PetComponent comp = entry.getValue();
                if (pet == null || !pet.isAlive()) continue;
                PlayerEntity owner = comp.getOwner();
                if (owner == null || owner.isRemoved()) continue;

                // Emit interval trigger every 20 ticks (approx 1s) and also faster at 40 if needed
                long time = world.getTime();
                if (time % 20 == 0) {
                    woflo.petsplus.api.TriggerContext ctx = new woflo.petsplus.api.TriggerContext(
                        world, pet, owner, "interval_tick");
                    woflo.petsplus.abilities.AbilityManager.triggerAbilities(pet, ctx);
                }

                // Support role extras: apply sitting aura if not moving and collect owner-thrown potions
                if (comp.getRole() == woflo.petsplus.api.PetRole.SUPPORT) {
                    applySupportSittingAuraIfEligible(pet, owner);
                    // Pulse AoE from stored potion every second
                    if (world.getTime() % 20 == 0) {
                        applyStoredPotionAura(pet, owner, comp);
                    }
                    pickupNearbyPotionsForSupport(pet, owner);
                }

                // Emit role-specific particle effects
                if (woflo.petsplus.ui.ParticleEffectManager.shouldEmitParticles(pet, world)) {
                    woflo.petsplus.ui.ParticleEffectManager.emitRoleParticles(pet, world, time);
                }
            }
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

    // --- Support role helpers (lightweight, pet-local) ---
    private void applySupportSittingAuraIfEligible(MobEntity pet, PlayerEntity owner) {
        // If pet is tame and is sitting or effectively stationary, pulse a small regen to owner
        boolean sitting = (pet instanceof net.minecraft.entity.passive.TameableEntity t) && t.isSitting();
        boolean stationary = pet.getVelocity().lengthSquared() < 1.0E-4;
        if (!(sitting || stationary)) return;

        // Small radius around pet to affect owner (owner must be close)
        if (pet.squaredDistanceTo(owner) > 6.0 * 6.0) return;

        // Apply a short regeneration tick; rely on StatusEffect merge to keep light
        owner.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            net.minecraft.entity.effect.StatusEffects.REGENERATION,
            40, // 2s, refreshed by pulses
            0,
            false,
            false,
            true
        ));

        // Emit feedback for sitting regen (only occasionally to avoid spam)
        if (world.getTime() % 60 == 0) { // Every 3 seconds
            woflo.petsplus.ui.FeedbackManager.emitSupportRegenArea(pet, (net.minecraft.server.world.ServerWorld) world);
        }
    }

    private void pickupNearbyPotionsForSupport(MobEntity pet, PlayerEntity owner) {
        // Allow giving potions: if player Q-drops or throws a potion item near the pet, "store" it by tagging comp state
        // This is a simple pickup: find ItemEntity with potion item in small radius and consume one
        if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
        var box = pet.getBoundingBox().expand(1.5);
        var items = serverWorld.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, box,
            ie -> !ie.getStack().isEmpty() && (ie.getStack().isOf(net.minecraft.item.Items.POTION)
                || ie.getStack().isOf(net.minecraft.item.Items.SPLASH_POTION)
                || ie.getStack().isOf(net.minecraft.item.Items.LINGERING_POTION)));
        if (items.isEmpty()) return;
        PetComponent comp = petComponents.get(pet);
        if (comp == null) return;
        // Enforce single-slot storage
        if (Boolean.TRUE.equals(comp.getStateData("support_potion_present", Boolean.class))) return;

        // Remove one from the world stack
        net.minecraft.entity.ItemEntity picked = items.get(0);
        var stack = picked.getStack();
        // Get effects using pet level for duration calculation
        var effects = woflo.petsplus.roles.support.SupportPotionUtils.getAuraEffects(stack, comp.getLevel());
        if (effects.isEmpty()) return;

        // Calculate appropriate aura pulse duration based on original potion and pet level
        int basePotionDuration = effects.isEmpty() ? 3600 : effects.get(0).getDuration(); // Use first effect or default
        int auraPulseDuration = woflo.petsplus.roles.support.SupportPotionUtils.getAuraPulseDuration(basePotionDuration, comp.getLevel());

        // Serialize to strings
        java.util.List<String> serialized = new java.util.ArrayList<>();
        for (var e : effects) {
            var id = net.minecraft.registry.Registries.STATUS_EFFECT.getId(e.getEffectType().value());
            if (id != null) serialized.add(id.toString() + "|" + Math.max(0, e.getAmplifier()));
        }
        comp.setStateData("support_potion_effects", serialized);
        comp.setStateData("support_potion_present", true);
        comp.setStateData("support_potion_aura_duration", auraPulseDuration);

        stack.decrement(1);
        if (stack.isEmpty()) picked.discard();

        // Small feedback: brief particles around pet
        serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
            pet.getX(), pet.getY() + pet.getHeight() * 0.5, pet.getZ(),
            5, 0.2, 0.2, 0.2, 0.01);
    }

    private void applyStoredPotionAura(MobEntity pet, PlayerEntity owner, PetComponent comp) {
        if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
        if (!Boolean.TRUE.equals(comp.getStateData("support_potion_present", Boolean.class))) return;
        @SuppressWarnings("unchecked")
        java.util.List<String> serialized = comp.getStateData("support_potion_effects", java.util.List.class);
        if (serialized == null || serialized.isEmpty()) return;
        int auraDuration = 80;
        Integer storedDur = comp.getStateData("support_potion_aura_duration", Integer.class);
        if (storedDur != null) auraDuration = Math.max(20, storedDur);

        // Build effects
        java.util.List<net.minecraft.entity.effect.StatusEffectInstance> effects = new java.util.ArrayList<>();
        for (String s : serialized) {
            String[] parts = s.split("\\|");
            if (parts.length != 2) continue;
            var effectId = net.minecraft.util.Identifier.tryParse(parts[0]);
            int amp;
            try { amp = Integer.parseInt(parts[1]); } catch (Exception e) { continue; }
            var entry = net.minecraft.registry.Registries.STATUS_EFFECT.getEntry(effectId);
            if (entry.isEmpty()) continue;
            effects.add(new net.minecraft.entity.effect.StatusEffectInstance(entry.get(), auraDuration, amp, false, true, true));
        }
        if (effects.isEmpty()) return;

        // Apply to allies around pet (owner + friendly players within small radius)
        double radius = 6.0;
        var box = pet.getBoundingBox().expand(radius);
        java.util.List<net.minecraft.entity.player.PlayerEntity> allies = new java.util.ArrayList<>();
        allies.add(owner);
        allies.addAll(serverWorld.getEntitiesByClass(net.minecraft.entity.player.PlayerEntity.class, box,
            p -> p != owner && p.isAlive() && p.squaredDistanceTo(pet) <= radius * radius));

        for (var p : allies) {
            boolean appliedEffect = false;
            for (var e : effects) {
                // Refresh only if nearly expired to avoid heavy packets
                var existing = p.getStatusEffect(e.getEffectType());
                if (existing != null && existing.getDuration() > 20) continue;
                p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(e));
                appliedEffect = true;
            }

            if (appliedEffect && owner instanceof ServerPlayerEntity serverOwner && p instanceof ServerPlayerEntity serverAlly) {
                AdvancementManager.triggerSupportHealAllies(serverOwner, serverAlly);
            }
        }

        // Emit feedback for potion pulse (less frequent)
        if (world.getTime() % 100 == 0) { // Every 5 seconds
            woflo.petsplus.ui.FeedbackManager.emitFeedback("support_potion_pulse", pet, serverWorld);
        }
    }
}