package woflo.petsplus.tags;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

public class ModBlockTags {
    public static final TagKey<Block> DRYING_BLOCKS = createTag("drying_blocks");

    private static TagKey<Block> createTag(String name) {
        return TagKey.of(RegistryKeys.BLOCK, new Identifier(Petsplus.MOD_ID, name));
    }
}
