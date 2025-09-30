package net.minecraft.registry.tag;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class BlockTags {
    public static final TagKey<Block> DIAMOND_ORES = create("diamond_ores");
    public static final TagKey<Block> EMERALD_ORES = create("emerald_ores");
    public static final TagKey<Block> GOLD_ORES = create("gold_ores");
    public static final TagKey<Block> LAPIS_ORES = create("lapis_ores");
    public static final TagKey<Block> REDSTONE_ORES = create("redstone_ores");
    public static final TagKey<Block> COAL_ORES = create("coal_ores");
    public static final TagKey<Block> COPPER_ORES = create("copper_ores");
    public static final TagKey<Block> IRON_ORES = create("iron_ores");
    public static final TagKey<Block> BEACON_BASE_BLOCKS = create("beacon_base_blocks");

    private BlockTags() {}

    private static TagKey<Block> create(String path) {
        return TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft", path));
    }
}
