package woflo.petsplus.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

import java.util.concurrent.CompletableFuture;

/**
 * Generates entity type tags for PetsPlus mod.
 */
public class PetsplusEntityTagProvider extends FabricTagProvider.EntityTypeTagProvider {
    
    // Define our custom tags
    public static final TagKey<EntityType<?>> CC_RESISTANT = TagKey.of(RegistryKeys.ENTITY_TYPE, 
        Identifier.of(Petsplus.MOD_ID, "cc_resistant"));
    public static final TagKey<EntityType<?>> BOSS_ENTITIES = TagKey.of(RegistryKeys.ENTITY_TYPE, 
        Identifier.of(Petsplus.MOD_ID, "boss_entities"));
    public static final TagKey<EntityType<?>> TRIBUTE_IMMUNE = TagKey.of(RegistryKeys.ENTITY_TYPE, 
        Identifier.of(Petsplus.MOD_ID, "tribute_immune"));

    public PetsplusEntityTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        // CC Resistant entities (immune to crowd control effects)
        valueLookupBuilder(CC_RESISTANT)
            .add(EntityType.ENDER_DRAGON)
            .add(EntityType.WITHER)
            .add(EntityType.ELDER_GUARDIAN)
            .add(EntityType.WARDEN);

        // Boss entities
        valueLookupBuilder(BOSS_ENTITIES)
            .add(EntityType.ENDER_DRAGON)
            .add(EntityType.WITHER)
            .add(EntityType.ELDER_GUARDIAN)
            .add(EntityType.WARDEN);

        // Entities that qualify as natural flyers for assist heuristics
        valueLookupBuilder(PetsplusEntityTypeTags.FLYERS)
            .add(EntityType.PARROT)
            .add(EntityType.PHANTOM)
            .add(EntityType.ALLAY)
            .add(EntityType.VEX)
            .add(EntityType.GHAST)
            .add(EntityType.BEE)
            .add(EntityType.BAT)
            .add(EntityType.BLAZE);

        // Tribute immune entities (cannot be used for tribute payments)
        valueLookupBuilder(TRIBUTE_IMMUNE)
            .add(EntityType.VILLAGER)
            .add(EntityType.WANDERING_TRADER)
            .add(EntityType.PLAYER);

    }
}
