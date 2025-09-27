package woflo.petsplus.behavior.social;

import net.minecraft.entity.mob.MobEntity;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetSwarmIndex;

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

    public double squaredDistanceTo(PetSocialData other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}
