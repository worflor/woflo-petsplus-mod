package net.minecraft.entity;

import net.minecraft.world.World;

/** Minimal living entity stub. */
public class LivingEntity extends Entity {
    private float health = 20.0f;
    private float maxHealth = 20.0f;

    public LivingEntity(World world) {
        super(world);
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
    }

    public net.minecraft.entity.EntityType<?> getType() {
        return null;
    }
}
