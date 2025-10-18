package woflo.petsplus.ai.goals;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * When a pet enters the crouch cuddle handshake it slows near the owner and
 * hovers in a tight radius so proximity channels feel intentional and cozy.
 */
public class CrouchCuddleGoal extends AdaptiveGoal {
    private static final double APPROACH_SPEED = 0.5;
    private static final double HOVER_SPEED = 0.35;
    private static final double CLOSE_DISTANCE_SQ = 2.25; // ~1.5 blocks
    private static final double SNUFFLE_DISTANCE_SQ = 0.25; // ~0.5 blocks

    private final MobEntity pet;
    private final PetComponent component;
    @Nullable
    private ServerPlayerEntity cuddleOwner;

    public CrouchCuddleGoal(MobEntity pet) {
        super(pet, GoalRegistry.require(GoalIds.CROUCH_CUDDLE), EnumSet.of(Control.MOVE, Control.LOOK));
        this.pet = pet;
        this.component = PetComponent.get(pet);
    }

    @Override
    
    protected boolean canStartGoal() {
        if (component == null || pet.getEntityWorld().isClient()) {
            return false;
        }

        return refreshCuddleOwner();
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (component == null || pet.getEntityWorld().isClient()) {
            return false;
        }

        return refreshCuddleOwner();
    }

    @Override

    protected void onStartGoal() {
        refreshCuddleOwner();
    }

    @Override

    protected void onStopGoal() {
        pet.getNavigation().stop();
        cuddleOwner = null;
    }

    @Override
    
    protected void onTickGoal() {
        ServerPlayerEntity owner = this.cuddleOwner;
        if (owner == null) {
            return;
        }

        double distanceSq = pet.squaredDistanceTo(owner);
        pet.getLookControl().lookAt(owner, 30.0f, 30.0f);

        if (distanceSq > CLOSE_DISTANCE_SQ) {
            pet.getNavigation().startMovingTo(owner, APPROACH_SPEED);
            return;
        }

        pet.getNavigation().stop();

        if (distanceSq > SNUFFLE_DISTANCE_SQ) {
            Vec3d ownerPos = owner.getEntityPos();
            pet.getMoveControl().moveTo(ownerPos.x, ownerPos.y, ownerPos.z, HOVER_SPEED);
            pet.setVelocity(pet.getVelocity().multiply(0.5, 1.0, 0.5));
        } else {
            pet.setVelocity(pet.getVelocity().multiply(0.1, 1.0, 0.1));
        }
    }

    @Override
    protected float calculateEngagement() {
        return 0.45f;
    }

    private boolean refreshCuddleOwner() {
        ServerPlayerEntity owner = resolveActiveOwner();
        if (owner == null) {
            this.cuddleOwner = null;
            return false;
        }

        this.cuddleOwner = owner;
        return true;
    }

    @Nullable
    private ServerPlayerEntity resolveActiveOwner() {
        if (!(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }

        UUID ownerId = component.getCrouchCuddleOwnerId();
        if (ownerId == null) {
            return null;
        }

        ServerPlayerEntity owner = serverWorld.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.isSpectator()) {
            return null;
        }

        if (owner.getEntityWorld() != serverWorld || !owner.isSneaking()) {
            return null;
        }

        long currentTick = serverWorld.getTime();
        if (!component.isCrouchCuddleActiveWith(owner, currentTick)) {
            return null;
        }

        return owner;
    }
}



