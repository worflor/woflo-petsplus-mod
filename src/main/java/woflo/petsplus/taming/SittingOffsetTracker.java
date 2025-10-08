package woflo.petsplus.taming;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;

/**
 * Tracks whether a mixin-backed pet has already had its synthetic sitting
 * offset applied and provides helpers to keep the pose/offset in sync in a
 * single, entity-agnostic place.
 */
public interface SittingOffsetTracker {

    double petsplus$getSittingOffset();

    default EntityPose petsplus$getSittingPose() {
        return EntityPose.SLEEPING;
    }

    void petsplus$setSittingOffsetApplied(boolean applied);

    boolean petsplus$hasSittingOffsetApplied();

    default void petsplus$syncGroundedSitting(boolean sitting) {
        MobEntity mob = this.petsplus$asMobEntity();

        EntityPose targetPose = sitting ? this.petsplus$getSittingPose() : EntityPose.STANDING;
        boolean canTransform = mob.isAlive() && !mob.isRemoved();

        if (canTransform && mob.getPose() != targetPose) {
            mob.setPose(targetPose);
            mob.calculateDimensions();
        }

        if (sitting) {
            if (!this.petsplus$hasSittingOffsetApplied()) {
                if (canTransform) {
                    this.petsplus$shiftY(-this.petsplus$getSittingOffset());
                }
                this.petsplus$storeSittingOffset(true);
            }
        } else if (this.petsplus$hasSittingOffsetApplied()) {
            if (canTransform) {
                this.petsplus$shiftY(this.petsplus$getSittingOffset());
            }
            this.petsplus$storeSittingOffset(false);
        }
    }

    default void petsplus$resetGroundedSitting() {
        MobEntity mob = this.petsplus$asMobEntity();
        boolean canTransform = mob.isAlive() && !mob.isRemoved();

        if (this.petsplus$hasSittingOffsetApplied()) {
            if (canTransform) {
                this.petsplus$shiftY(this.petsplus$getSittingOffset());
            }
            this.petsplus$storeSittingOffset(false);
        }

        if (canTransform && mob.getPose() != EntityPose.STANDING) {
            mob.setPose(EntityPose.STANDING);
            mob.calculateDimensions();
        }
    }

    private MobEntity petsplus$asMobEntity() {
        return (MobEntity) this;
    }

    private void petsplus$shiftY(double delta) {
        MobEntity mob = this.petsplus$asMobEntity();
        mob.setPosition(mob.getX(), mob.getY() + delta, mob.getZ());
    }

    private void petsplus$storeSittingOffset(boolean applied) {
        if (this.petsplus$hasSittingOffsetApplied() == applied) {
            return;
        }

        this.petsplus$setSittingOffsetApplied(applied);

        MobEntity mob = this.petsplus$asMobEntity();
        if (!mob.getEntityWorld().isClient()) {
            PetComponent.getOrCreate(mob).setStateData("petsplus:sitting_offset", applied);
        }
    }
}


