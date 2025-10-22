package woflo.petsplus.ai.capability;

import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.pathing.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.inventory.InventoryChangedListener;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import woflo.petsplus.mixin.MobEntityAccessor;

/**
 * Detects what a mob CAN do without hardcoding entity types.
 * Works with vanilla and modded mobs automatically through capability detection.
 * This is the foundation of the pet-agnostic AI system.
 */
public class MobCapabilities {
    
    // === MOVEMENT CAPABILITIES ===
    
    /**
     * Can the mob navigate/pathfind on land?
     */
    public static boolean canWander(MobEntity mob) {
        EntityNavigation nav = mob.getNavigation();
        return nav != null && !(nav instanceof BirdNavigation);
    }
    
    /**
     * Can the mob fly?
     */
    public static boolean canFly(MobEntity mob) {
        return mob.getNavigation() instanceof BirdNavigation;
    }
    
    /**
     * Can the mob swim effectively?
     */
    public static boolean canSwim(MobEntity mob) {
        EntityNavigation nav = mob.getNavigation();
        return usesWaterNavigation(nav);
    }

    private static boolean usesWaterNavigation(EntityNavigation navigation) {
        return navigation instanceof SwimNavigation || navigation instanceof AmphibiousPathNavigation;
    }
    
    /**
     * Can the mob jump?
     */
    public static boolean canJump(MobEntity mob) {
        return mob.getJumpControl() != null;
    }
    
    // === INTERACTION CAPABILITIES ===
    
    /**
     * Does the mob have an owner?
     */
    public static boolean hasOwner(MobEntity mob) {
        UUID storedOwner = null;
        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            storedOwner = component.getOwnerUuid();
        }

        if (mob instanceof PetsplusTameable petsplus) {
            if (!petsplus.petsplus$isTamed()) {
                return false;
            }

            UUID capabilityOwner = petsplus.petsplus$getOwnerUuid();
            if (capabilityOwner != null) {
                return true;
            }

            return storedOwner != null;
        }

        if (storedOwner != null) {
            return true;
        }

        if (mob instanceof TameableEntity tameable) {
            return tameable.isTamed();
        }

        return false;
    }
    
    /**
     * Can the mob pick up items?
     */
    public static boolean canPickUpItems(MobEntity mob) {
        return mob.canPickUpLoot();
    }
    
    /**
     * Does the mob have an inventory?
     */
    public static boolean hasInventory(MobEntity mob) {
        return mob instanceof InventoryChangedListener;
    }
    
    // === ANIMATION CAPABILITIES ===
    
    /**
     * Can the mob sit?
     */
    public static boolean canSit(MobEntity mob) {
        if (mob instanceof TameableEntity || mob instanceof PetsplusTameable) {
            return true;
        }

        // Fall back to detecting a dedicated sit goal so custom mobs are recognized.
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            return accessor.getGoalSelector().getGoals().stream()
                .map(entry -> entry.getGoal())
                .anyMatch(goal -> goal instanceof SitGoal ||
                    (goal != null && goal.getClass().getSimpleName().toLowerCase().contains("sit")));
        } catch (ClassCastException ignored) {
            return false;
        }
    }
    
    /**
     * Can the mob make sounds?
     */
    public static boolean canMakeSound(MobEntity mob) {
        return !mob.isSilent();
    }
    
    // === ENVIRONMENT PREFERENCES ===
    
    /**
     * Does this mob prefer land over water/air?
     */
    public static boolean prefersLand(MobEntity mob) {
        EntityNavigation nav = mob.getNavigation();
        return nav instanceof MobNavigation;
    }
    
    /**
     * Does this mob prefer water?
     */
    public static boolean prefersWater(MobEntity mob) {
        return canSwim(mob) && (mob.getAir() > 0 || mob.canBreatheInWater());
    }
    
    /**
     * Does this mob prefer air/flying?
     */
    public static boolean prefersAir(MobEntity mob) {
        return canFly(mob);
    }
    
    /**
     * Is the mob small enough for certain behaviors (shoulder perching, etc.)?
     */
    public static boolean isSmallSize(MobEntity mob) {
        return mob.getWidth() < 0.8f && mob.getHeight() < 1.0f;
    }
    
    /**
     * Get a complete capability profile for goal filtering.
     */
    public static CapabilityProfile analyze(MobEntity mob) {
        return new CapabilityProfile(
            canWander(mob),
            canFly(mob),
            canSwim(mob),
            canJump(mob),
            hasOwner(mob),
            canPickUpItems(mob),
            hasInventory(mob),
            canSit(mob),
            canMakeSound(mob),
            prefersLand(mob),
            prefersWater(mob),
            prefersAir(mob),
            isSmallSize(mob)
        );
    }
    
    /**
     * Immutable capability profile for a mob.
     */
    public record CapabilityProfile(
        boolean canWander,
        boolean canFly,
        boolean canSwim,
        boolean canJump,
        boolean hasOwner,
        boolean canPickUpItems,
        boolean hasInventory,
        boolean canSit,
        boolean canMakeSound,
        boolean prefersLand,
        boolean prefersWater,
        boolean prefersAir,
        boolean isSmallSize
    ) {
        /**
         * Check if this mob meets the capability requirements.
         */
        public boolean meetsRequirements(CapabilityRequirement requirement) {
            return requirement.test(this);
        }
    }
    
    /**
     * Functional interface for checking if capabilities meet requirements.
     */
    @FunctionalInterface
    public interface CapabilityRequirement {
        boolean test(CapabilityProfile profile);

        // Common requirements
        static CapabilityRequirement any() {
            return profile -> true;
        }
        
        static CapabilityRequirement land() {
            return profile -> profile.canWander && profile.prefersLand;
        }
        
        static CapabilityRequirement flying() {
            return profile -> profile.canFly;
        }
        
        static CapabilityRequirement aquatic() {
            return profile -> profile.canSwim && profile.prefersWater;
        }
        
        static CapabilityRequirement tamed() {
            return profile -> profile.hasOwner;
        }
        
        static CapabilityRequirement itemHandler() {
            return profile -> profile.canPickUpItems;
        }

        static CapabilityRequirement fromToken(String token) {
            String normalized = token.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "any" -> any();
                case "land" -> land();
                case "flying" -> flying();
                case "aquatic" -> aquatic();
                case "tamed" -> tamed();
                case "item_handler" -> itemHandler();
                case "can_wander" -> profile -> profile.canWander();
                case "can_fly" -> profile -> profile.canFly();
                case "can_swim" -> profile -> profile.canSwim();
                case "can_jump" -> profile -> profile.canJump();
                case "has_owner" -> profile -> profile.hasOwner();
                case "can_pick_up_items" -> profile -> profile.canPickUpItems();
                case "has_inventory" -> profile -> profile.hasInventory();
                case "can_sit" -> profile -> profile.canSit();
                case "can_make_sound" -> profile -> profile.canMakeSound();
                case "prefers_land" -> profile -> profile.prefersLand();
                case "prefers_water" -> profile -> profile.prefersWater();
                case "prefers_air" -> profile -> profile.prefersAir();
                case "is_small_size" -> profile -> profile.isSmallSize();
                default -> throw new IllegalArgumentException("Unknown capability requirement '" + token + "'");
            };
        }

        static CapabilityRequirement allOf(List<CapabilityRequirement> requirements) {
            List<CapabilityRequirement> copy = List.copyOf(requirements);
            return profile -> {
                for (CapabilityRequirement requirement : copy) {
                    if (!requirement.test(profile)) {
                        return false;
                    }
                }
                return true;
            };
        }

        static CapabilityRequirement anyOf(List<CapabilityRequirement> requirements) {
            List<CapabilityRequirement> copy = List.copyOf(requirements);
            return profile -> {
                for (CapabilityRequirement requirement : copy) {
                    if (requirement.test(profile)) {
                        return true;
                    }
                }
                return false;
            };
        }

        static CapabilityRequirement not(CapabilityRequirement requirement) {
            return profile -> !requirement.test(profile);
        }
    }
}
