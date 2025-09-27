package woflo.petsplus.tags;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

/**
 * Shared block tags used by PetsPlus at runtime.
 */
public final class PetsplusBlockTags {
    public static final TagKey<Block> SNIFF_COMFORTS = tag("sniff/comforts");
    public static final TagKey<Block> SNIFF_CURIOSITIES = tag("sniff/curiosities");
    public static final TagKey<Block> SNIFF_WARDING = tag("sniff/warding");
    public static final TagKey<Block> SNIFF_STORAGE = tag("sniff/storage");

    public static final TagKey<Block> EXPLORATION_WORKSTATIONS = tag("exploration/workstations");
    public static final TagKey<Block> EXPLORATION_CURIOSITIES = tag("exploration/curiosities");

    public static final TagKey<Block> SAFETY_WARMTH = tag("safety/warmth");
    public static final TagKey<Block> SAFETY_BEACONS = tag("safety/beacons");

    public static final TagKey<Block> SLIPPERY_SURFACES = tag("movement/slippery_surfaces");

    private PetsplusBlockTags() {
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.of(RegistryKeys.BLOCK, Identifier.of(Petsplus.MOD_ID, path));
    }
}

