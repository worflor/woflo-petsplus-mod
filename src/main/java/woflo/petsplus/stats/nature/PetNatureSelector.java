package woflo.petsplus.stats.nature;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import woflo.petsplus.api.event.PetBreedEvent;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Evaluates environmental rules to determine which nature, if any, should be
 * assigned to a pet during breeding or taming. The rules mirror the nature
 * roster described in {@code natures_system_story.md} while still supporting
 * datapack extensions through simple predicate registration.
 */
public final class PetNatureSelector {
    private static final List<NatureRule<PetBreedEvent.BirthContext>> BIRTH_RULES = new ArrayList<>();
    private static final List<NatureRule<TameContext>> TAME_RULES = new ArrayList<>();
    private static final Set<Identifier> REGISTERED_NATURES = new LinkedHashSet<>();

    private static final Identifier OVERWORLD_DIMENSION = Identifier.of("minecraft", "overworld");
    private static final Identifier NETHER_DIMENSION = Identifier.of("minecraft", "the_nether");
    private static final TagKey<net.minecraft.block.Block> ORE_BLOCKS = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft", "ores"));

    private PetNatureSelector() {
    }

    static {
        registerNature("radiant", PetNatureSelector::matchesRadiant);
        registerNature("nocturne", PetNatureSelector::matchesNocturne);
        registerNature("hearth", PetNatureSelector::matchesHearth);
        registerNature("tempest", PetNatureSelector::matchesTempest);
        registerNature("solace", PetNatureSelector::matchesSolace);
        registerNature("festival", PetNatureSelector::matchesFestival);
        registerNature("otherworldly", ctx -> !isDimension(ctx, OVERWORLD_DIMENSION) && !isDimension(ctx, NETHER_DIMENSION));
        registerNature("infernal", ctx -> isDimension(ctx, NETHER_DIMENSION));
        registerNature("echoed", ctx -> ctx.environment().isDeepDarkBiome());
        registerNature("mycelial", ctx -> ctx.environment().isMushroomFieldsBiome());
        registerNature("gilded", ctx -> ctx.environment().hasValuableOres());
        registerNature("gloom", ctx -> ctx.isIndoors()
            && ctx.environment().getSkyLightLevel() <= 2
            && !ctx.environment().hasValuableOres());
        registerNature("verdant", ctx -> ctx.environment().hasLushFoliage());
        registerNature("summit", ctx -> ctx.environment().getHeight() >= 100 && ctx.environment().hasOpenSky());
        registerNature("tidal", ctx -> ctx.environment().isFullySubmerged() && ctx.environment().isOceanBiome());
        registerNature("molten", ctx -> ctx.environment().isNearLavaOrMagma());
        registerNature("frosty", PetNatureSelector::matchesFrosty);
        registerNature("mire", ctx -> ctx.environment().isNearMudOrMangrove() || ctx.environment().isSwampBiome());
        registerNature("relic", ctx -> ctx.environment().isNearMajorStructure());
        registerNature("unnatural", ctx -> !ctx.primaryOwned() && !ctx.partnerOwned(), ctx -> false);
    }

    /**
     * Returns a defensive copy of the registered nature identifiers for command
     * suggestions or UI.
     */
    public static Set<Identifier> getRegisteredNatureIds() {
        return Collections.unmodifiableSet(REGISTERED_NATURES);
    }

    private static void registerNature(String path, Predicate<NatureContext> predicate) {
        registerNature(path, predicate, predicate);
    }

    private static void registerNature(String path, Predicate<NatureContext> birthPredicate,
                                       Predicate<NatureContext> tamePredicate) {
        Identifier id = Identifier.of("petsplus", path);
        REGISTERED_NATURES.add(id);
        BIRTH_RULES.add(new NatureRule<>(id, context -> context != null && birthPredicate.test(new BirthNatureContext(context))));
        TAME_RULES.add(new NatureRule<>(id, context -> context != null && tamePredicate.test(context)));
    }

    /**
     * Returns the identifier of the first matching nature. If several natures
     * qualify the selection is made uniformly at random using the pet's RNG so
     * a tie between two candidates is effectively a coin flip. This scales
     * naturally to any number of overlapping rules.
     */
    public static Identifier selectNature(MobEntity child, PetBreedEvent.BirthContext context) {
        return pickNature(child, context, BIRTH_RULES);
    }

    public static Identifier selectTameNature(MobEntity pet, TameContext context) {
        return pickNature(pet, context, TAME_RULES);
    }

    public static TameContext captureTameContext(ServerWorld world, MobEntity pet) {
        Identifier dimensionId = world.getRegistryKey().getValue();
        BlockPos pos = pet.getBlockPos();
        boolean indoors = !world.isSkyVisible(pos);
        boolean daytime = world.isDay();
        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();
        boolean fullMoon = world.getMoonPhase() == 0;

        double witnessRadius = 12.0D;
        double witnessRadiusSq = witnessRadius * witnessRadius;
        int nearbyPlayers = world.getPlayers(player -> !player.isSpectator() && player.squaredDistanceTo(pet) <= witnessRadiusSq).size();
        int nearbyPets = world.getEntitiesByClass(MobEntity.class, pet.getBoundingBox().expand(witnessRadius),
            entity -> entity != pet && PetComponent.get(entity) != null).size();

        PetBreedEvent.BirthContext.Environment environment = captureEnvironment(world, pet);

        return new TameContext(dimensionId, indoors, daytime, raining, thundering, fullMoon, nearbyPlayers, nearbyPets, environment);
    }

    public static PetBreedEvent.BirthContext.Environment captureEnvironment(ServerWorld world, MobEntity entity) {
        BlockPos pos = entity.getBlockPos();
        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        Identifier biomeId = biomeEntry.getKey().map(RegistryKey::getValue).orElse(Identifier.of("minecraft", "unknown"));
        float biomeTemperature = biomeEntry.value().getTemperature();
        boolean deepDark = biomeEntry.matchesKey(BiomeKeys.DEEP_DARK);
        boolean mushroomFields = biomeEntry.matchesKey(BiomeKeys.MUSHROOM_FIELDS);
        boolean ocean = biomeEntry.isIn(BiomeTags.IS_OCEAN);
        boolean swamp = biomeEntry.matchesKey(BiomeKeys.SWAMP) || biomeEntry.matchesKey(BiomeKeys.MANGROVE_SWAMP);
        boolean hasPrecipitation = biomeEntry.value().hasPrecipitation();
        boolean snowyPrecipitation = hasPrecipitation && biomeTemperature <= 0.15f;

        int skyLight = world.getLightLevel(LightType.SKY, pos);
        boolean hasOpenSky = world.isSkyVisible(pos);
        int height = pos.getY();

        EnvironmentSample sample = sampleEnvironment(world, pos);
        boolean fullySubmerged = entity.isSubmergedIn(FluidTags.WATER);

        return new PetBreedEvent.BirthContext.Environment(
            pos.toImmutable(),
            biomeId,
            biomeTemperature,
            deepDark,
            mushroomFields,
            ocean,
            swamp,
            snowyPrecipitation,
            skyLight,
            height,
            hasOpenSky,
            sample.hasCozyBlocks(),
            sample.hasValuableOres(),
            sample.hasLushFoliage(),
            fullySubmerged,
            sample.nearLavaOrMagma(),
            sample.nearPowderSnow(),
            sample.nearMudOrMangrove(),
            sample.nearMajorStructure()
        );
    }

    private static <T> Identifier pickNature(MobEntity entity, T context, List<NatureRule<T>> rules) {
        if (entity == null || rules.isEmpty()) {
            return null;
        }

        Identifier chosen = null;
        int matchCount = 0;

        for (NatureRule<T> rule : rules) {
            if (!rule.predicate.test(context)) {
                continue;
            }

            matchCount++;
            if (matchCount == 1) {
                chosen = rule.id;
                continue;
            }

            if (entity.getRandom().nextInt(matchCount) == 0) {
                chosen = rule.id;
            }
        }

        return chosen;
    }

    private static boolean matchesRadiant(NatureContext context) {
        return context.isDaytime()
            && context.environment().hasOpenSky()
            && !context.isRaining()
            && !context.isThundering();
    }

    private static boolean matchesNocturne(NatureContext context) {
        return !context.isDaytime()
            && context.isFullMoon()
            && context.environment().hasOpenSky();
    }

    private static boolean matchesHearth(NatureContext context) {
        return context.isIndoors() && context.environment().hasCozyBlocks();
    }

    private static boolean matchesTempest(NatureContext context) {
        return context.isRaining() || context.isThundering();
    }

    private static boolean matchesSolace(NatureContext context) {
        return context.nearbyPlayers() == 0 && context.nearbyPets() == 0;
    }

    private static boolean matchesFestival(NatureContext context) {
        return context.nearbyPlayers() >= 3 || context.nearbyPets() >= 3;
    }

    private static boolean matchesFrosty(NatureContext context) {
        return (context.environment().hasSnowyPrecipitation() && context.environment().hasOpenSky())
            || context.environment().isNearPowderSnow()
            || (context.environment().getBiomeTemperature() <= 0.2f && (context.isRaining() || context.isThundering()));
    }

    private static boolean isDimension(NatureContext context, Identifier dimension) {
        return Objects.equals(context.dimensionId(), dimension);
    }

    private static EnvironmentSample sampleEnvironment(ServerWorld world, BlockPos center) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();

        int minY = Math.max(world.getBottomY(), centerY - 6);
        int maxSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, centerX, centerZ);
        int maxY = Math.min(maxSurfaceY, centerY + 3);

        EnvironmentSample sample = new EnvironmentSample();

        outer:
        for (int x = centerX - 6; x <= centerX + 6; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = centerZ - 6; z <= centerZ + 6; z++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }

                    int dx = Math.abs(x - centerX);
                    int dy = y - centerY;
                    int dz = Math.abs(z - centerZ);

                    if (!sample.hasCozyBlocks && dx <= 4 && dz <= 4 && dy >= -2 && dy <= 2 && isCozyBlock(state)) {
                        sample.hasCozyBlocks = true;
                    }
                    if (!sample.hasValuableOres && dx <= 2 && dz <= 2 && dy >= -6 && dy <= 1 && isValuableOre(state)) {
                        sample.hasValuableOres = true;
                    }
                    if (!sample.hasLushFoliage && dx <= 3 && dz <= 3 && dy >= -1 && dy <= 2 && isLushBlock(state)) {
                        sample.hasLushFoliage = true;
                    }
                    if (!sample.nearLavaOrMagma && dx <= 4 && dz <= 4 && dy >= -2 && dy <= 2 && isLavaOrMagmaBlock(state)) {
                        sample.nearLavaOrMagma = true;
                    }
                    if (!sample.nearPowderSnow && dx <= 3 && dz <= 3 && dy >= -2 && dy <= 2 && state.isOf(Blocks.POWDER_SNOW)) {
                        sample.nearPowderSnow = true;
                    }
                    if (!sample.nearMudOrMangrove && dx <= 4 && dz <= 4 && dy >= -2 && dy <= 2 && isMudOrMangroveBlock(state)) {
                        sample.nearMudOrMangrove = true;
                    }
                    if (!sample.nearMajorStructure && dx <= 6 && dz <= 6 && dy >= -3 && dy <= 3 && isRelicBlock(state)) {
                        sample.nearMajorStructure = true;
                    }

                    if (sample.isComplete()) {
                        break outer;
                    }
                }
            }
        }

        return sample;
    }

    private static boolean isCozyBlock(BlockState state) {
        return state.isIn(BlockTags.BEDS)
            || state.isIn(BlockTags.WOOL_CARPETS)
            || state.isIn(BlockTags.WOOL)
            || state.isIn(BlockTags.CANDLES)
            || state.isOf(Blocks.CAMPFIRE)
            || state.isOf(Blocks.SOUL_CAMPFIRE)
            || state.isOf(Blocks.FURNACE)
            || state.isOf(Blocks.SMOKER)
            || state.isOf(Blocks.BLAST_FURNACE)
            || state.isOf(Blocks.LANTERN)
            || state.isOf(Blocks.SOUL_LANTERN)
            || state.isOf(Blocks.TORCH)
            || state.isOf(Blocks.SOUL_TORCH)
            || state.isOf(Blocks.CANDLE)
            || state.isOf(Blocks.CANDLE_CAKE)
            || state.isOf(Blocks.BOOKSHELF)
            || state.isOf(Blocks.CHISELED_BOOKSHELF)
            || state.isOf(Blocks.BARREL);
    }

    private static boolean isValuableOre(BlockState state) {
        Block block = state.getBlock();
        return state.isIn(ORE_BLOCKS)
            || block == Blocks.ANCIENT_DEBRIS
            || block == Blocks.RAW_GOLD_BLOCK
            || block == Blocks.RAW_COPPER_BLOCK
            || block == Blocks.RAW_IRON_BLOCK
            || block == Blocks.GILDED_BLACKSTONE;
    }

    private static boolean isLushBlock(BlockState state) {
        return state.isIn(BlockTags.LEAVES)
            || state.isIn(BlockTags.FLOWERS)
            || state.isIn(BlockTags.CROPS)
            || state.isIn(BlockTags.SAPLINGS)
            || blockIsMushroom(state.getBlock())
            || state.isOf(Blocks.SHORT_GRASS)
            || state.isOf(Blocks.GRASS_BLOCK)
            || state.isOf(Blocks.TALL_GRASS)
            || state.isOf(Blocks.FERN)
            || state.isOf(Blocks.LARGE_FERN)
            || state.isOf(Blocks.MOSS_BLOCK)
            || state.isOf(Blocks.MOSS_CARPET)
            || state.isOf(Blocks.CAVE_VINES)
            || state.isOf(Blocks.CAVE_VINES_PLANT)
            || state.isOf(Blocks.HANGING_ROOTS)
            || state.isOf(Blocks.VINE)
            || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.BIG_DRIPLEAF)
            || state.isOf(Blocks.SMALL_DRIPLEAF)
            || state.isOf(Blocks.BAMBOO)
            || state.isOf(Blocks.SPORE_BLOSSOM)
            || state.isOf(Blocks.FLOWERING_AZALEA)
            || state.isOf(Blocks.AZALEA);
    }

    private static boolean blockIsMushroom(Block block) {
        return block == Blocks.RED_MUSHROOM
            || block == Blocks.BROWN_MUSHROOM
            || block == Blocks.CRIMSON_FUNGUS
            || block == Blocks.WARPED_FUNGUS;
    }

    private static boolean isLavaOrMagmaBlock(BlockState state) {
        FluidState fluidState = state.getFluidState();
        return fluidState.isIn(FluidTags.LAVA)
            || state.isOf(Blocks.LAVA)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.FIRE)
            || state.isOf(Blocks.SOUL_FIRE);
    }

    private static boolean isMudOrMangroveBlock(BlockState state) {
        return state.isOf(Blocks.MUD)
            || state.isOf(Blocks.PACKED_MUD)
            || state.isOf(Blocks.MUD_BRICKS)
            || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
            || state.isOf(Blocks.MANGROVE_ROOTS)
            || state.isIn(BlockTags.MANGROVE_LOGS)
            || state.isOf(Blocks.MANGROVE_LEAVES)
            || state.isOf(Blocks.MANGROVE_PROPAGULE)
            || state.isOf(Blocks.CLAY);
    }

    private static boolean isRelicBlock(BlockState state) {
        return state.isOf(Blocks.CHISELED_STONE_BRICKS)
            || state.isOf(Blocks.CRACKED_STONE_BRICKS)
            || state.isOf(Blocks.MOSSY_STONE_BRICKS)
            || state.isOf(Blocks.REINFORCED_DEEPSLATE)
            || state.isOf(Blocks.DEEPSLATE_BRICKS)
            || state.isOf(Blocks.DEEPSLATE_TILES)
            || state.isOf(Blocks.CRACKED_DEEPSLATE_BRICKS)
            || state.isOf(Blocks.CRACKED_DEEPSLATE_TILES)
            || state.isOf(Blocks.POLISHED_BLACKSTONE_BRICKS)
            || state.isOf(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
            || state.isOf(Blocks.NETHER_BRICKS)
            || state.isOf(Blocks.RED_NETHER_BRICKS)
            || state.isOf(Blocks.GILDED_BLACKSTONE)
            || state.isOf(Blocks.END_STONE_BRICKS)
            || state.isOf(Blocks.QUARTZ_BRICKS)
            || state.isOf(Blocks.CHISELED_QUARTZ_BLOCK)
            || state.isOf(Blocks.SEA_LANTERN)
            || state.isOf(Blocks.CHISELED_DEEPSLATE)
            || state.isOf(Blocks.CRACKED_NETHER_BRICKS)
            || state.isOf(Blocks.CHISELED_POLISHED_BLACKSTONE);
    }

    private interface NatureContext {
        Identifier dimensionId();

        boolean isIndoors();

        boolean isDaytime();

        boolean isRaining();

        boolean isThundering();

        boolean isFullMoon();

        int nearbyPlayers();

        int nearbyPets();

        PetBreedEvent.BirthContext.Environment environment();

        boolean primaryOwned();

        boolean partnerOwned();
    }

    private static final class BirthNatureContext implements NatureContext {
        private final PetBreedEvent.BirthContext context;

        private BirthNatureContext(PetBreedEvent.BirthContext context) {
            this.context = context;
        }

        @Override
        public Identifier dimensionId() {
            return context.getDimensionId();
        }

        @Override
        public boolean isIndoors() {
            return context.isIndoors();
        }

        @Override
        public boolean isDaytime() {
            return context.isDaytime();
        }

        @Override
        public boolean isRaining() {
            return context.isRaining();
        }

        @Override
        public boolean isThundering() {
            return context.isThundering();
        }

        @Override
        public boolean isFullMoon() {
            return context.isFullMoon();
        }

        @Override
        public int nearbyPlayers() {
            return context.getNearbyPlayerCount();
        }

        @Override
        public int nearbyPets() {
            return context.getNearbyPetCount();
        }

        @Override
        public PetBreedEvent.BirthContext.Environment environment() {
            return context.getEnvironment();
        }

        @Override
        public boolean primaryOwned() {
            return context.isPrimaryParentOwned();
        }

        @Override
        public boolean partnerOwned() {
            return context.isPartnerParentOwned();
        }
    }

    public static final class TameContext implements NatureContext {
        private final Identifier dimensionId;
        private final boolean indoors;
        private final boolean daytime;
        private final boolean raining;
        private final boolean thundering;
        private final boolean fullMoon;
        private final int nearbyPlayerCount;
        private final int nearbyPetCount;
        private final PetBreedEvent.BirthContext.Environment environment;

        private TameContext(Identifier dimensionId, boolean indoors, boolean daytime, boolean raining,
                             boolean thundering, boolean fullMoon, int nearbyPlayerCount, int nearbyPetCount,
                             PetBreedEvent.BirthContext.Environment environment) {
            this.dimensionId = dimensionId;
            this.indoors = indoors;
            this.daytime = daytime;
            this.raining = raining;
            this.thundering = thundering;
            this.fullMoon = fullMoon;
            this.nearbyPlayerCount = nearbyPlayerCount;
            this.nearbyPetCount = nearbyPetCount;
            this.environment = environment;
        }

        @Override
        public Identifier dimensionId() {
            return dimensionId;
        }

        @Override
        public boolean isIndoors() {
            return indoors;
        }

        @Override
        public boolean isDaytime() {
            return daytime;
        }

        @Override
        public boolean isRaining() {
            return raining;
        }

        @Override
        public boolean isThundering() {
            return thundering;
        }

        @Override
        public boolean isFullMoon() {
            return fullMoon;
        }

        @Override
        public int nearbyPlayers() {
            return nearbyPlayerCount;
        }

        @Override
        public int nearbyPets() {
            return nearbyPetCount;
        }

        @Override
        public PetBreedEvent.BirthContext.Environment environment() {
            return environment;
        }

        @Override
        public boolean primaryOwned() {
            return false;
        }

        @Override
        public boolean partnerOwned() {
            return false;
        }
    }

    private static final class EnvironmentSample {
        private boolean hasCozyBlocks;
        private boolean hasValuableOres;
        private boolean hasLushFoliage;
        private boolean nearLavaOrMagma;
        private boolean nearPowderSnow;
        private boolean nearMudOrMangrove;
        private boolean nearMajorStructure;

        boolean hasCozyBlocks() {
            return hasCozyBlocks;
        }

        boolean hasValuableOres() {
            return hasValuableOres;
        }

        boolean hasLushFoliage() {
            return hasLushFoliage;
        }

        boolean nearLavaOrMagma() {
            return nearLavaOrMagma;
        }

        boolean nearPowderSnow() {
            return nearPowderSnow;
        }

        boolean nearMudOrMangrove() {
            return nearMudOrMangrove;
        }

        boolean nearMajorStructure() {
            return nearMajorStructure;
        }

        boolean isComplete() {
            return hasCozyBlocks
                && hasValuableOres
                && hasLushFoliage
                && nearLavaOrMagma
                && nearPowderSnow
                && nearMudOrMangrove
                && nearMajorStructure;
        }
    }

    private record NatureRule<T>(Identifier id, Predicate<T> predicate) {
    }
}
