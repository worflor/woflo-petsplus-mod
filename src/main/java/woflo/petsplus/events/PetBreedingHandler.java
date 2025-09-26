package woflo.petsplus.events;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.event.PetBreedEvent;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetAttributeManager;
import woflo.petsplus.stats.PetCharacteristics;
import woflo.petsplus.stats.nature.PetNatureSelector;

import java.util.Objects;
import java.util.UUID;

/**
 * Hooks into vanilla animal breeding to seed inherited characteristics and
 * metadata for the newborn while keeping the base breeding flow untouched.
 */
public final class PetBreedingHandler {
    private static final float EMOTION_PULSE_PRIMARY = 0.35f;
    private static final float EMOTION_PULSE_SECONDARY = 0.25f;
    private static final float EMOTION_PULSE_CHILD = 0.40f;

    private PetBreedingHandler() {
    }

    public static void register() {
        Petsplus.LOGGER.info("Pet breeding handler registered");
    }

    public static void onPetBred(AnimalEntity primaryParent, AnimalEntity partner, PassiveEntity childEntity) {
        if (!(childEntity instanceof MobEntity mobChild)) {
            return;
        }
        if (!(mobChild.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        PetComponent primaryComponent = PetComponent.get(primaryParent);
        PetComponent partnerComponent = PetComponent.get(partner);

        PlayerEntity primaryOwner = resolveOwner(primaryParent, primaryComponent);
        PlayerEntity partnerOwner = resolveOwner(partner, partnerComponent);

        PlayerEntity resolvedOwner = chooseOwner(primaryOwner, partnerOwner);
        boolean ownersDiffer = primaryOwner != null && partnerOwner != null
            && !Objects.equals(primaryOwner.getUuid(), partnerOwner.getUuid());

        PetComponent childComponent = null;
        boolean inheritedRole = false;
        boolean inheritedStats = false;
        PetBreedEvent.BirthContext birthContext = null;
        Identifier assignedNature = null;

        if (!ownersDiffer && (resolvedOwner != null || primaryComponent != null || partnerComponent != null)) {
            childComponent = PetComponent.getOrCreate(mobChild);

            birthContext = captureBirthContext(serverWorld, mobChild);
            long now = birthContext.getWorldTime();
            childComponent.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_TICK, now);
            childComponent.setStateData(PetComponent.StateKeys.BREEDING_PARENT_A_UUID, primaryParent.getUuidAsString());
            childComponent.setStateData(PetComponent.StateKeys.BREEDING_PARENT_B_UUID, partner.getUuidAsString());
            childComponent.setStateData(PetComponent.StateKeys.BREEDING_SOURCE, "vanilla");

            if (childComponent.getStateData(PetComponent.StateKeys.TAMED_TICK, Long.class) == null) {
                childComponent.setStateData(PetComponent.StateKeys.TAMED_TICK, now);
            }

            recordBirthContext(childComponent, birthContext);
            assignedNature = PetNatureSelector.selectNature(mobChild, birthContext);
            if (assignedNature != null) {
                childComponent.setStateData(PetComponent.StateKeys.BREEDING_ASSIGNED_NATURE, assignedNature.toString());
            }

            if (resolvedOwner != null) {
                childComponent.setOwner(resolvedOwner);
                childComponent.setStateData(PetComponent.StateKeys.BREEDING_OWNER_UUID, resolvedOwner.getUuid().toString());
            }

            Identifier primaryRole = primaryComponent != null ? primaryComponent.getRoleId() : null;
            Identifier partnerRole = partnerComponent != null ? partnerComponent.getRoleId() : null;

            if (primaryRole != null) {
                childComponent.setStateData(PetComponent.StateKeys.BREEDING_PRIMARY_ROLE, primaryRole.toString());
            }
            if (partnerRole != null) {
                childComponent.setStateData(PetComponent.StateKeys.BREEDING_PARTNER_ROLE, partnerRole.toString());
            }

            Identifier inherited = determineInheritedRole(primaryRole, partnerRole);
            if (inherited != null) {
                PetRoleType type = PetsPlusRegistries.petRoleTypeRegistry().get(inherited);
                if (type != null) {
                    childComponent.setRoleId(inherited);
                    childComponent.setStateData(PetComponent.StateKeys.BREEDING_INHERITED_ROLE, inherited.toString());
                    inheritedRole = true;
                }
            }

            PetCharacteristics primaryCharacteristics = primaryComponent != null
                ? primaryComponent.getCharacteristics()
                : null;
            PetCharacteristics partnerCharacteristics = partnerComponent != null
                ? partnerComponent.getCharacteristics()
                : null;

            PetCharacteristics blended = PetCharacteristics.blendFromParents(
                mobChild,
                now,
                primaryCharacteristics,
                partnerCharacteristics
            );
            if (blended != null) {
                childComponent.setCharacteristics(blended);
                PetAttributeManager.applyAttributeModifiers(mobChild, childComponent);
                boolean usedParentStats = primaryCharacteristics != null || partnerCharacteristics != null;
                childComponent.setStateData(PetComponent.StateKeys.BREEDING_INHERITED_STATS, usedParentStats);
                inheritedStats = usedParentStats;
            }

            pulseParentEmotions(primaryComponent);
            pulseParentEmotions(partnerComponent);
            pulseChildEmotions(childComponent);
        }

        PetBreedEvent.fire(new PetBreedEvent.Context(
            primaryParent,
            partner,
            childEntity,
            primaryComponent,
            partnerComponent,
            childComponent,
            inheritedRole,
            inheritedStats,
            birthContext,
            assignedNature
        ));
    }

    private static PetBreedEvent.BirthContext captureBirthContext(ServerWorld world, MobEntity child) {
        BlockPos pos = child.getBlockPos();
        boolean indoors = !world.isSkyVisible(pos);
        boolean daytime = world.isDay();
        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();
        long worldTime = world.getTime();
        long timeOfDay = world.getTimeOfDay();
        Identifier dimensionId = world.getRegistryKey().getValue();

        double witnessRadius = 12.0D;
        double witnessRadiusSq = witnessRadius * witnessRadius;
        int nearbyPlayers = world.getPlayers(player -> !player.isSpectator() && player.squaredDistanceTo(child) <= witnessRadiusSq).size();
        int nearbyPets = world.getEntitiesByClass(MobEntity.class, child.getBoundingBox().expand(witnessRadius),
            entity -> entity != child && PetComponent.get(entity) != null).size();

        return new PetBreedEvent.BirthContext(
            worldTime,
            timeOfDay,
            dimensionId,
            indoors,
            daytime,
            raining,
            thundering,
            nearbyPlayers,
            nearbyPets
        );
    }

    private static void recordBirthContext(PetComponent component, PetBreedEvent.BirthContext context) {
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_TIME_OF_DAY, context.getTimeOfDay());
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_IS_DAYTIME, context.isDaytime());
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_IS_INDOORS, context.isIndoors());
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_IS_RAINING, context.isRaining());
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_IS_THUNDERING, context.isThundering());
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_NEARBY_PLAYER_COUNT, context.getNearbyPlayerCount());
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_NEARBY_PET_COUNT, context.getNearbyPetCount());
        component.setStateData(PetComponent.StateKeys.BREEDING_BIRTH_DIMENSION, context.getDimensionId().toString());
    }

    private static void pulseParentEmotions(@Nullable PetComponent component) {
        if (component == null) {
            return;
        }
        component.pushEmotion(PetComponent.Emotion.LOYALTY, EMOTION_PULSE_PRIMARY);
        component.pushEmotion(PetComponent.Emotion.PLAYFULNESS, EMOTION_PULSE_SECONDARY);
    }

    private static void pulseChildEmotions(@Nullable PetComponent component) {
        if (component == null) {
            return;
        }
        component.pushEmotion(PetComponent.Emotion.CHEERFUL, EMOTION_PULSE_CHILD);
        component.pushEmotion(PetComponent.Emotion.LOYALTY, EMOTION_PULSE_PRIMARY);
    }

    private static PlayerEntity resolveOwner(AnimalEntity entity, @Nullable PetComponent component) {
        PlayerEntity owner = null;
        if (entity instanceof TameableEntity tameable && tameable.isTamed()) {
            if (tameable.getOwner() instanceof PlayerEntity player) {
                owner = player;
            }
        }
        if (owner == null && component != null) {
            owner = component.getOwner();
        }
        return owner;
    }

    private static PlayerEntity chooseOwner(@Nullable PlayerEntity a, @Nullable PlayerEntity b) {
        if (a != null && b != null) {
            UUID uuidA = a.getUuid();
            if (uuidA.equals(b.getUuid())) {
                return a;
            }
            return null;
        }
        return a != null ? a : b;
    }

    private static Identifier determineInheritedRole(@Nullable Identifier primary, @Nullable Identifier partner) {
        if (primary != null && partner != null && primary.equals(partner)) {
            return primary;
        }
        if (primary != null && partner == null) {
            return primary;
        }
        if (partner != null && primary == null) {
            return partner;
        }
        return null;
    }
}
