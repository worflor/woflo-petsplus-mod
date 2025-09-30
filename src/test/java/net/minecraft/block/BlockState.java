package net.minecraft.block;

import net.minecraft.registry.tag.TagKey;

/** Minimal block state stub for tests. */
public class BlockState {
    public boolean isOf(Block block) {
        return false;
    }

    public boolean isIn(TagKey<Block> tag) {
        return false;
    }

    public Block getBlock() {
        return null;
    }
}
