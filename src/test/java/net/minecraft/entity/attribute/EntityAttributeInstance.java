package net.minecraft.entity.attribute;

import java.util.ArrayList;
import java.util.List;

/** Minimal attribute instance stub storing modifiers in memory. */
public class EntityAttributeInstance {
    private double baseValue;
    private final List<EntityAttributeModifier> modifiers = new ArrayList<>();

    public double getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(double baseValue) {
        this.baseValue = baseValue;
    }

    public void addPersistentModifier(EntityAttributeModifier modifier) {
        modifiers.add(modifier);
    }

    public List<EntityAttributeModifier> getModifiers() {
        return modifiers;
    }
}
