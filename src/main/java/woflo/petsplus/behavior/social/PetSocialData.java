package woflo.petsplus.behavior.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;

/**
 * Lightweight cache of neighbour traits used during social behaviour passes.
 */
public final class PetSocialData {

    private final MobEntity pet;
    private final PetComponent component;
    private final long age;
    private final float bondStrength;
    private final double x;
    private final double y;
    private final double z;
    private final PetComponent.Mood currentMood;
    private final Vec3d velocity;
    private final float bodyYaw;
    private final float headYaw;
    private final int jitterSeed;
    private final long lastPetTick;
    private final long lastCrouchCuddleTick;
    private final long lastSocialInteractionTick;
    private final long lastThreatRecoveryTick;

    public PetSocialData(PetSwarmIndex.SwarmEntry entry, long currentTick) {
        this(entry.pet(), entry.component(), currentTick, entry.x(), entry.y(), entry.z());
    }

    public PetSocialData(MobEntity pet, PetComponent component, long currentTick) {
        this(pet, component, currentTick, pet.getX(), pet.getY(), pet.getZ());
    }

    private PetSocialData(MobEntity pet, PetComponent component, long currentTick,
                          double x, double y, double z) {
        this.pet = pet;
        this.component = component;
        this.age = Math.max(0, currentTick - component.getTamedTick());
        this.bondStrength = component.getBondStrength();
        this.x = x;
        this.y = y;
        this.z = z;
        this.currentMood = component.getCurrentMood();
        this.velocity = pet.getVelocity();
        this.bodyYaw = pet.bodyYaw;
        this.headYaw = pet.headYaw;
        this.jitterSeed = component.getOrCreateSocialJitterSeed();
        this.lastPetTick = component.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class, 0L);
        this.lastCrouchCuddleTick = component.getStateData(PetComponent.StateKeys.LAST_CROUCH_CUDDLE_TICK, Long.class, 0L);
        this.lastSocialInteractionTick = component.getStateData(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, Long.class, 0L);
        this.lastThreatRecoveryTick = component.getStateData(PetComponent.StateKeys.THREAT_LAST_RECOVERY_TICK, Long.class, 0L);
    }

    public MobEntity pet() {
        return pet;
    }

    public PetComponent component() {
        return component;
    }

    public long age() {
        return age;
    }

    public float bondStrength() {
        return bondStrength;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public PetComponent.Mood currentMood() {
        return currentMood;
    }

    public Vec3d velocity() {
        return velocity;
    }

    public double speed() {
        return velocity.length();
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    public float headYaw() {
        return headYaw;
    }

    public int jitterSeed() {
        return jitterSeed;
    }

    public long lastPetTick() {
        return lastPetTick;
    }

    public long lastCrouchCuddleTick() {
        return lastCrouchCuddleTick;
    }

    public long lastSocialInteractionTick() {
        return lastSocialInteractionTick;
    }

    public long lastThreatRecoveryTick() {
        return lastThreatRecoveryTick;
    }

    public double squaredDistanceTo(PetSocialData other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    public double relativeSpeedTo(PetSocialData other) {
        if (other == null) {
            return speed();
        }
        return this.velocity.subtract(other.velocity()).length();
    }

    public boolean isFacingToward(PetSocialData target, double toleranceDegrees) {
        if (target == null) {
            return false;
        }
        double dx = target.x() - this.x;
        double dz = target.z() - this.z;
        double horizontalSq = (dx * dx) + (dz * dz);
        if (horizontalSq < 1.0E-6) {
            return true;
        }
        double desiredYaw = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        double difference = Math.abs(MathHelper.wrapDegrees(this.headYaw - desiredYaw));
        return difference <= toleranceDegrees;
    }

    public double headingAlignmentWith(PetSocialData other) {
        if (other == null) {
            return 0.0;
        }
        double difference = Math.abs(MathHelper.wrapDegrees(this.bodyYaw - other.bodyYaw()));
        return 1.0 - (difference / 180.0);
    }
}
