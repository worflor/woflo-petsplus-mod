package net.minecraft.entity.attribute;

import java.util.UUID;

/** Minimal attribute modifier stub. */
public class EntityAttributeModifier {
    public enum Operation {
        ADD_VALUE,
        ADD_MULTIPLIED_BASE,
        ADD_MULTIPLIED_TOTAL
    }

    private final UUID id;
    private final String name;
    private final double value;
    private final Operation operation;

    public EntityAttributeModifier(UUID id, String name, double value, Operation operation) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.operation = operation;
    }

    public EntityAttributeModifier(net.minecraft.util.Identifier id, double value, Operation operation) {
        this(UUID.randomUUID(), id.toString(), value, operation);
    }

    public double value() {
        return value;
    }

    public Operation operation() {
        return operation;
    }
}
