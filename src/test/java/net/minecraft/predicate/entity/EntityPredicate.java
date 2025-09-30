package net.minecraft.predicate.entity;

import com.mojang.serialization.Codec;

/** Minimal stub of EntityPredicate for unit testing. */
public final class EntityPredicate {
    public static final Codec<LootContextPredicate> LOOT_CONTEXT_PREDICATE_CODEC = Codec.unit(new LootContextPredicate());

    private EntityPredicate() {
    }
}
