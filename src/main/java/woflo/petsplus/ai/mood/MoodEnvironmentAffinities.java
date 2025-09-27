package woflo.petsplus.ai.mood;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.registry.tag.BlockTags;

/**
 * Centralized registry of block tag affinities for mood-driven goals.
 */
public final class MoodEnvironmentAffinities {
    public static final TagKey<Block> FOCUSED_CURATED = create("focused_study_points");
    public static final TagKey<Block> PLAYFUL_TOYS = create("playful_toys");
    public static final TagKey<Block> PASSIONATE_SPARK_TRIGGERS = create("passionate_spark_triggers");
    public static final TagKey<Block> PASSIONATE_RESONATORS = create("passionate_resonators");

    private MoodEnvironmentAffinities() {}

    private static TagKey<Block> create(String path) {
        return TagKey.of(RegistryKeys.BLOCK, Identifier.of("woflo", path));
    }

    public static boolean isFocusedStudyBlock(BlockState state) {
        return state.isIn(FOCUSED_CURATED)
            || state.isIn(BlockTags.ENCHANTMENT_POWER_PROVIDER)
            || state.isIn(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)
            || state.isOf(Blocks.LECTERN)
            || state.isOf(Blocks.ENCHANTING_TABLE)
            || state.isOf(Blocks.CARTOGRAPHY_TABLE)
            || state.isOf(Blocks.FLETCHING_TABLE)
            || state.isOf(Blocks.SMITHING_TABLE)
            || state.isOf(Blocks.LOOM)
            || state.isOf(Blocks.BREWING_STAND)
            || state.isOf(Blocks.DECORATED_POT)
            || state.isOf(Blocks.CHISELED_BOOKSHELF);
    }

    public static boolean isPlayfulToy(BlockState state) {
        return state.isIn(PLAYFUL_TOYS)
            || state.isIn(BlockTags.WOOL)
            || state.isIn(BlockTags.WOOL_CARPETS)
            || state.isIn(BlockTags.BEDS)
            || state.isIn(BlockTags.CANDLES)
            || state.isOf(Blocks.TARGET)
            || state.isOf(Blocks.NOTE_BLOCK)
            || state.isOf(Blocks.JUKEBOX)
            || state.isOf(Blocks.SLIME_BLOCK)
            || state.isOf(Blocks.HONEY_BLOCK)
            || state.isOf(Blocks.BAMBOO_PLANKS)
            || state.isOf(Blocks.BAMBOO_MOSAIC)
            || state.isOf(Blocks.COPPER_BLOCK)
            || state.isOf(Blocks.DRIPSTONE_BLOCK)
            || state.isOf(Blocks.DECORATED_POT);
    }

    public static boolean isPassionateSpark(BlockState state) {
        return state.isIn(PASSIONATE_SPARK_TRIGGERS)
            || state.isIn(BlockTags.CAMPFIRES)
            || state.isOf(Blocks.FIRE)
            || state.isOf(Blocks.SOUL_FIRE)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.BLAST_FURNACE)
            || state.isOf(Blocks.SMITHING_TABLE)
            || state.isOf(Blocks.NETHER_BRICKS)
            || state.isOf(Blocks.REDSTONE_BLOCK);
    }

    public static boolean isPassionateResonator(BlockState state) {
        return state.isIn(PASSIONATE_RESONATORS)
            || state.isIn(BlockTags.WOOL)
            || state.isOf(Blocks.AMETHYST_BLOCK)
            || state.isOf(Blocks.COPPER_BLOCK)
            || state.isOf(Blocks.BELL)
            || state.isOf(Blocks.SCULK_SENSOR)
            || state.isOf(Blocks.CALIBRATED_SCULK_SENSOR)
            || state.isOf(Blocks.CHISELED_BOOKSHELF);
    }
}
