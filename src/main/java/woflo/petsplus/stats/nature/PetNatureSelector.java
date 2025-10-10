package woflo.petsplus.stats.nature;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.event.PetBreedEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;
import woflo.petsplus.stats.nature.astrology.AstrologySignDefinition;

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
    private static final TagKey<Block> ORE_BLOCKS = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft", "ores"));

    public static final TagKey<Block> NATURE_ARCHAEOLOGY_BLOCKS = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "natures/archaeology_sites"));
    public static final TagKey<Block> NATURE_TRIAL_BLOCKS = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "natures/trial_chamber_features"));
    public static final TagKey<Block> NATURE_CHERRY_BLOOM_BLOCKS = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "natures/cherry_bloom"));
    public static final TagKey<Block> NATURE_REDSTONE_COMPONENTS = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "natures/redstone_components"));
    public static final TagKey<Block> NATURE_REDSTONE_POWER_SOURCES = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "natures/redstone_power_sources"));
    public static final TagKey<Block> NATURE_HOMESTEAD_BLOCKS = TagKey.of(RegistryKeys.BLOCK,
        Identifier.of("petsplus", "natures/homestead_markers"));

    private PetNatureSelector() {
    }

    static {
        registerNature("frisky", ctx -> false, ctx -> ctx.environment().getBiomeTemperature() <= 0.3f);
        registerNature("fierce", ctx -> false, ctx -> ctx.environment().getBiomeTemperature() >= 1.0f);
        registerNature("feral", ctx -> false, ctx -> {
            float temp = ctx.environment().getBiomeTemperature();
            return temp > 0.3f && temp < 1.0f;
        });
        
        // === BORN NATURES (Bred pets only) ===
        registerNature("radiant", PetNatureSelector::matchesRadiant);
        registerNature("lunaris", PetNatureSelector::matchesLunaris);
        registerNature("homestead", PetNatureSelector::matchesHomestead);
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
        registerNature("ceramic", PetNatureSelector::matchesCeramic);
        registerNature("blossom", PetNatureSelector::matchesBlossom);
        registerNature("clockwork", PetNatureSelector::matchesClockwork);
        registerNature("sentinel", PetNatureSelector::matchesSentinel);
        registerNature("scrappy", PetNatureSelector::matchesScrappy);
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
        if (child == null) {
            return null;
        }
        return selectNature(child.getRandom(), context);
    }

    public static Identifier selectTameNature(MobEntity pet, TameContext context) {
        if (pet == null) {
            return null;
        }
        return pickNature(pet.getRandom(), context, TAME_RULES);
    }

    static Identifier selectNature(Random random, PetBreedEvent.BirthContext context) {
        return pickNature(random, context, BIRTH_RULES);
    }

    public static TameContext captureTameContext(ServerWorld world, MobEntity pet) {
        Identifier dimensionId = world.getRegistryKey().getValue();
        BlockPos pos = pet.getBlockPos();
        boolean indoors = !world.isSkyVisible(pos);
        boolean daytime = world.isDay();
        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();
        boolean fullMoon = world.getMoonPhase() == 0;
        long timeOfDay = world.getTimeOfDay() % 24000L;

        double witnessRadius = 12.0D;
        double witnessRadiusSq = witnessRadius * witnessRadius;
        int nearbyPlayers = world.getPlayers(player -> !player.isSpectator() && player.squaredDistanceTo(pet) <= witnessRadiusSq).size();
        int nearbyPets = countNearbyPets(world, pet, witnessRadius);

        PetBreedEvent.BirthContext.Environment environment = captureEnvironment(world, pet);

        return new TameContext(dimensionId, indoors, daytime, raining, thundering, fullMoon, nearbyPlayers, nearbyPets, timeOfDay, environment);
    }

    public static AstrologyRegistry.PetNatureSelectorContext toAstrologyContext(TameContext context, long worldTime) {
        PetBreedEvent.BirthContext.Environment environment = context.environment();
        boolean openSky = environment != null && environment.hasOpenSky();
        return new AstrologyRegistry.PetNatureSelectorContext(
            context.dimensionId(),
            worldTime,
            context.getTimeOfDay(),
            context.isIndoors(),
            context.isDaytime(),
            context.isRaining(),
            context.isThundering(),
            openSky,
            context.nearbyPlayers(),
            context.nearbyPets(),
            environment
        );
    }

    public static PetBreedEvent.BirthContext.Environment captureEnvironment(ServerWorld world, MobEntity entity) {
        return captureEnvironment(world, entity, null, null);
    }
    
    public static PetBreedEvent.BirthContext.Environment captureEnvironment(ServerWorld world, MobEntity entity,
                                                                             @org.jetbrains.annotations.Nullable PetComponent primaryParent,
                                                                             @org.jetbrains.annotations.Nullable PetComponent partnerParent) {
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
        scanNearbyStorage(world, pos, sample);
        
        // Check if parents have recent combat (within last 5 minutes = 6000 ticks)
        long currentTime = world.getTime();
        boolean hasRecentCombat = checkParentRecentCombat(primaryParent, currentTime) 
                               || checkParentRecentCombat(partnerParent, currentTime);
        sample.hasRecentCombat = hasRecentCombat;
        
        sample.finalizeAfterScan();
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
            sample.nearMajorStructure(),
            sample.hasArchaeologySite(),
            sample.nearTrialChamber(),
            sample.hasCherryBloom(),
            sample.hasActiveRedstone(),
            sample.hasHomesteadBlocks(),
            sample.hasRecentCombat(),
            sample.getNearbyChestCount(),
            sample.getTotalStorageItems(),
            sample.getUniqueItemTypes(),
            sample.getCombatRelevantItems()
        );
    }

    private static <T> Identifier pickNature(Random random, T context, List<NatureRule<T>> rules) {
        if (random == null || rules.isEmpty()) {
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

            if (random.nextInt(matchCount) == 0) {
                chosen = rule.id;
            }
        }

        return chosen;
    }

    private static int countNearbyPets(ServerWorld world, MobEntity source, double radius) {
        if (world == null || source == null || radius < 0.0D) {
            return 0;
        }
        PetSwarmIndex swarmIndex = StateManager.forWorld(world).getSwarmIndex();
        Vec3d center = source.getEntityPos();
        final int[] count = {0};
        swarmIndex.forEachPetInRange(center, radius, entry -> {
            MobEntity other = entry.pet();
            if (other == null || other == source || !other.isAlive()) {
                return;
            }
            count[0]++;
        });
        return count[0];
    }

    private static boolean matchesRadiant(NatureContext context) {
        return context.isDaytime()
            && context.environment().hasOpenSky()
            && !context.isRaining()
            && !context.isThundering();
    }

    private static boolean matchesLunaris(NatureContext context) {
        if (!isDimension(context, OVERWORLD_DIMENSION)) {
            return false;
        }
        PetBreedEvent.BirthContext.Environment env = context.environment();
        if (env == null || !env.hasOpenSky()) {
            return false;
        }
        if (context.isIndoors()) {
            return false;
        }
        long timeOfDay = context.getTimeOfDay() % 24000L;
        AstrologySignDefinition.NightPeriod nightPeriod = AstrologySignDefinition.classifyNightPeriod(timeOfDay);
        return nightPeriod != null || context.isThundering();
    }

    private static boolean matchesHomestead(NatureContext context) {
        return context.environment().hasHomesteadBlocks();
    }
    
    private static boolean matchesSentinel(NatureContext context) {
        PetBreedEvent.BirthContext.Environment env = context.environment();
        if (env == null || !env.hasRecentCombat()) return false;
        
        // Requires post-combat + organized, well-stocked storage
        float orgRatio = env.getOrganizationRatio();
        return env.getNearbyChestCount() >= 3
            && orgRatio < 0.4f  // Organized (low unique-to-total ratio)
            && env.getCombatRelevantItems() >= 20  // Well-equipped
            && env.getTotalStorageItems() >= 20;  // Has reserves
    }
    
    private static boolean matchesScrappy(NatureContext context) {
        PetBreedEvent.BirthContext.Environment env = context.environment();
        if (env == null || !env.hasRecentCombat()) return false;
        
        // Requires post-combat + disorganized OR sparse storage
        float orgRatio = env.getOrganizationRatio();
        boolean disorganized = env.getNearbyChestCount() > 0 && orgRatio > 0.6f;
        boolean sparse = env.getTotalStorageItems() < 15 || env.getCombatRelevantItems() < 10;
        
        return disorganized || sparse;
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

    private static boolean matchesCeramic(NatureContext context) {
        PetBreedEvent.BirthContext.Environment env = context.environment();
        if (env == null || !env.hasArchaeologySite()) {
            return false;
        }
        Identifier biomeId = env.getBiomeId();
        boolean aridBiome = biomeId.equals(BiomeKeys.DESERT.getValue())
            || biomeId.equals(BiomeKeys.BADLANDS.getValue())
            || biomeId.equals(BiomeKeys.ERODED_BADLANDS.getValue())
            || biomeId.equals(BiomeKeys.WOODED_BADLANDS.getValue());
        if (env.isNearTrialChamber()) {
            return true;
        }
        boolean digFriendlyWeather = env.hasOpenSky() && !context.isRaining() && !context.isThundering();
        if (aridBiome && digFriendlyWeather) {
            return true;
        }
        if (env.isNearMajorStructure() && (context.isIndoors() || env.hasCozyBlocks())) {
            return true;
        }
        return false;
    }

    private static boolean matchesBlossom(NatureContext context) {
        PetBreedEvent.BirthContext.Environment env = context.environment();
        if (env == null || !env.hasCherryBloom()) {
            return false;
        }
        Identifier biomeId = env.getBiomeId();
        boolean cherryGroves = biomeId.equals(BiomeKeys.CHERRY_GROVE.getValue());
        boolean meadow = biomeId.equals(BiomeKeys.MEADOW.getValue());
        if (!env.hasOpenSky()) {
            return false;
        }
        if (context.isRaining() || context.isThundering()) {
            return false;
        }
        return cherryGroves || meadow || env.hasLushFoliage();
    }

    private static boolean matchesClockwork(NatureContext context) {
        PetBreedEvent.BirthContext.Environment env = context.environment();
        if (env == null || !env.hasActiveRedstone()) {
            return false;
        }
        if (isDimension(context, NETHER_DIMENSION)) {
            return false;
        }
        if (!(context.isIndoors() || env.hasCozyBlocks() || env.isNearMajorStructure())) {
            return false;
        }
        return env.hasActiveRedstone();
    }

    private static boolean matchesFrosty(NatureContext context) {
        return (context.environment().hasSnowyPrecipitation() && context.environment().hasOpenSky())
            || context.environment().isNearPowderSnow()
            || (context.environment().getBiomeTemperature() <= 0.2f && (context.isRaining() || context.isThundering()));
    }

    private static boolean isDimension(NatureContext context, Identifier dimension) {
        return Objects.equals(context.dimensionId(), dimension);
    }

    public static boolean isBlockEmittingRedstone(ServerWorld world, BlockPos pos, BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.contains(Properties.POWERED) && Boolean.TRUE.equals(state.get(Properties.POWERED))) {
            return true;
        }
        if (state.contains(Properties.POWER) && state.get(Properties.POWER) > 0) {
            return true;
        }
        if (state.contains(Properties.LIT) && Boolean.TRUE.equals(state.get(Properties.LIT))) {
            return true;
        }
        if (state.emitsRedstonePower()) {
            for (Direction direction : Direction.values()) {
                if (state.getWeakRedstonePower(world, pos, direction) > 0) {
                    return true;
                }
            }
        }
        return false;
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
                    if (state.isIn(NATURE_TRIAL_BLOCKS)) {
                        sample.markTrialBlock();
                    }
                    if (state.isIn(NATURE_ARCHAEOLOGY_BLOCKS)) {
                        sample.recordArchaeologyBlock(state);
                    }
                    if (state.isIn(NATURE_CHERRY_BLOOM_BLOCKS)) {
                        sample.recordCherryBlock(state);
                    }
                    boolean redstoneComponent = state.isIn(NATURE_REDSTONE_COMPONENTS);
                    boolean redstoneSource = state.isIn(NATURE_REDSTONE_POWER_SOURCES);
                    if (redstoneComponent || redstoneSource) {
                        boolean powered = isBlockEmittingRedstone(world, mutable, state);
                        sample.recordRedstone(redstoneComponent, redstoneSource, powered);
                    }
                    if (state.isIn(NATURE_HOMESTEAD_BLOCKS) && dx <= 5 && dz <= 5 && dy >= -2 && dy <= 2) {
                        sample.recordHomesteadBlock(state);
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

    private static boolean checkParentRecentCombat(@org.jetbrains.annotations.Nullable PetComponent parent, long currentTime) {
        if (parent == null) return false;
        
        Long lastDanger = parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class);
        if (lastDanger == null) return false;
        
        // Combat within last 5 minutes (6000 ticks)
        long timeSinceCombat = currentTime - lastDanger;
        return timeSinceCombat < 6000L && timeSinceCombat >= 0;
    }
    
    private static void scanNearbyStorage(ServerWorld world, BlockPos center, EnvironmentSample sample) {
        // Guard: Only scan max 8 chests for performance
        final int MAX_CHESTS_TO_SCAN = 8;
        final int SCAN_RADIUS = 8;
        
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int chestsScanned = 0;
        java.util.Set<net.minecraft.item.Item> uniqueItems = new java.util.HashSet<>();
        
        // Scan in smaller radius for performance
        for (int x = center.getX() - SCAN_RADIUS; x <= center.getX() + SCAN_RADIUS && chestsScanned < MAX_CHESTS_TO_SCAN; x++) {
            for (int y = center.getY() - 3; y <= center.getY() + 3 && chestsScanned < MAX_CHESTS_TO_SCAN; y++) {
                for (int z = center.getZ() - SCAN_RADIUS; z <= center.getZ() + SCAN_RADIUS && chestsScanned < MAX_CHESTS_TO_SCAN; z++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    
                    // Guard: Only check chest-like blocks
                    if (!state.isOf(Blocks.CHEST) && !state.isOf(Blocks.BARREL) 
                        && !state.isOf(Blocks.TRAPPED_CHEST) && !state.isOf(Blocks.ENDER_CHEST)) {
                        continue;
                    }
                    
                    BlockEntity blockEntity = world.getBlockEntity(mutable);
                    if (!(blockEntity instanceof Inventory inventory)) {
                        continue;
                    }
                    
                    chestsScanned++;
                    
                    // Guard: Limit item scanning per chest
                    int sizeLimit = Math.min(inventory.size(), 27);
                    for (int i = 0; i < sizeLimit; i++) {
                        ItemStack stack = inventory.getStack(i);
                        if (stack.isEmpty()) continue;
                        
                        sample.totalStorageItems++;
                        uniqueItems.add(stack.getItem());
                        
                        // Track combat-relevant items
                        if (isCombatRelevantItem(stack)) {
                            sample.combatRelevantItems++;
                        }
                    }
                }
            }
        }
        
        sample.nearbyChestCount = chestsScanned;
        sample.uniqueItemTypes = uniqueItems.size();
    }
    
    private static boolean isCombatRelevantItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        Item item = stack.getItem();
        
        // Use ItemTags for weapons (swords, axes, etc.)
        if (stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS) || 
            stack.isIn(net.minecraft.registry.tag.ItemTags.AXES)) {
            return true;
        }
        
        // Check for armor using EQUIPPABLE component (works with all armor types including modded)
        net.minecraft.component.type.EquippableComponent equippable = 
            stack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            // Check if it's armor (not just cosmetic equippable like carved pumpkins)
            net.minecraft.entity.EquipmentSlot slot = equippable.slot();
            if (slot == net.minecraft.entity.EquipmentSlot.HEAD || 
                slot == net.minecraft.entity.EquipmentSlot.CHEST || 
                slot == net.minecraft.entity.EquipmentSlot.LEGS || 
                slot == net.minecraft.entity.EquipmentSlot.FEET) {
                return true;
            }
        }
        
        // Check for ranged weapons and special combat items
        if (item == Items.MACE || item == Items.TRIDENT || 
            item == Items.BOW || item == Items.CROSSBOW || item == Items.SHIELD) {
            return true;
        }
        
        // Arrows and projectiles
        if (item == Items.ARROW || item == Items.SPECTRAL_ARROW || item == Items.TIPPED_ARROW) {
            return true;
        }
        
        // Combat food (high-value healing items)
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            return true;
        }
        
        // Check for food with significant healing value using FOOD component
        net.minecraft.component.type.FoodComponent foodComponent = 
            stack.get(net.minecraft.component.DataComponentTypes.FOOD);
        if (foodComponent != null && foodComponent.nutrition() >= 6) {
            return true;
        }
        
        // Potions (check for beneficial effects via POTION_CONTENTS component)
        if (stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS) != null) {
            return true;
        }
        
        return false;
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

        long getTimeOfDay();

        PetBreedEvent.BirthContext.Environment environment();

        boolean primaryOwned();

        boolean partnerOwned();

        @Nullable Identifier primaryParentNatureId();

        @Nullable Identifier partnerParentNatureId();
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
        public long getTimeOfDay() {
            return context.getTimeOfDay();
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

        @Override
        public @Nullable Identifier primaryParentNatureId() {
            return context.getPrimaryParentNatureId();
        }

        @Override
        public @Nullable Identifier partnerParentNatureId() {
            return context.getPartnerParentNatureId();
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
        private final long timeOfDay;
        private final PetBreedEvent.BirthContext.Environment environment;

        private TameContext(Identifier dimensionId, boolean indoors, boolean daytime, boolean raining,
                             boolean thundering, boolean fullMoon, int nearbyPlayerCount, int nearbyPetCount,
                             long timeOfDay, PetBreedEvent.BirthContext.Environment environment) {
            this.dimensionId = dimensionId;
            this.indoors = indoors;
            this.daytime = daytime;
            this.raining = raining;
            this.thundering = thundering;
            this.fullMoon = fullMoon;
            this.nearbyPlayerCount = nearbyPlayerCount;
            this.nearbyPetCount = nearbyPetCount;
            this.timeOfDay = timeOfDay;
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
        public long getTimeOfDay() {
            return timeOfDay;
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

        @Override
        public @Nullable Identifier primaryParentNatureId() {
            return null;
        }

        @Override
        public @Nullable Identifier partnerParentNatureId() {
            return null;
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
        private boolean hasArchaeologySite;
        private boolean trialBlocksPresent;
        private boolean hasCherryBloom;
        private boolean hasActiveRedstone;
        private boolean hasHomesteadBlocks;
        private int homesteadFarmingBlocks;
        private int homesteadCropBlocks;
        private int homesteadStorageBlocks;
        private int homesteadShelterBlocks;
        private int suspiciousDigBlocks;
        private int decoratedPotBlocks;
        private int cherryPetalBlocks;
        private int cherryCanopyBlocks;
        private int cherryPollinatorBlocks;
        private final RedstoneNetworkTracker redstoneTracker = new RedstoneNetworkTracker();
        private boolean hasRecentCombat;
        private int nearbyChestCount;
        private int totalStorageItems;
        private int uniqueItemTypes;
        private int combatRelevantItems;

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

        boolean hasArchaeologySite() {
            return hasArchaeologySite;
        }

        boolean nearTrialChamber() {
            return trialBlocksPresent;
        }

        boolean hasCherryBloom() {
            return hasCherryBloom;
        }

        boolean hasActiveRedstone() {
            return hasActiveRedstone;
        }

        boolean hasHomesteadBlocks() {
            return hasHomesteadBlocks;
        }
        
        boolean hasRecentCombat() {
            return hasRecentCombat;
        }
        
        int getNearbyChestCount() {
            return nearbyChestCount;
        }
        
        int getTotalStorageItems() {
            return totalStorageItems;
        }
        
        int getUniqueItemTypes() {
            return uniqueItemTypes;
        }
        
        int getCombatRelevantItems() {
            return combatRelevantItems;
        }

        void recordHomesteadBlock(BlockState state) {
            // Farming: farmland, composter, hay bale
            if (state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.COMPOSTER) || state.isOf(Blocks.HAY_BLOCK)) {
                homesteadFarmingBlocks++;
            }
            // Crops: actively growing plants
            else if (state.isIn(BlockTags.CROPS) || state.isOf(Blocks.PUMPKIN) || state.isOf(Blocks.MELON) 
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.SUGAR_CANE)) {
                homesteadCropBlocks++;
            }
            // Storage/Crafting: chests, barrels, crafting, furnaces
            else if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.BARREL) || state.isOf(Blocks.CRAFTING_TABLE)
                || state.isOf(Blocks.FURNACE) || state.isOf(Blocks.SMOKER) || state.isOf(Blocks.BLAST_FURNACE)
                || state.isOf(Blocks.ENDER_CHEST) || state.isOf(Blocks.TRAPPED_CHEST)) {
                homesteadStorageBlocks++;
            }
            // Shelter: beds, doors
            else if (state.isIn(BlockTags.BEDS) || state.isIn(BlockTags.DOORS)) {
                homesteadShelterBlocks++;
            }
            updateHomesteadSignal();
        }

        private void updateHomesteadSignal() {
            if (hasHomesteadBlocks) {
                return;
            }
            // Build confidence score based on categories and counts
            int categoriesPresent = 0;
            int totalScore = 0;

            if (homesteadFarmingBlocks > 0) {
                categoriesPresent++;
                totalScore += Math.min(homesteadFarmingBlocks * 2, 8); // Cap contribution
            }
            if (homesteadCropBlocks > 0) {
                categoriesPresent++;
                totalScore += Math.min(homesteadCropBlocks * 2, 8);
            }
            if (homesteadStorageBlocks > 0) {
                categoriesPresent++;
                totalScore += Math.min(homesteadStorageBlocks * 3, 9); // Storage slightly more valuable
            }
            if (homesteadShelterBlocks > 0) {
                categoriesPresent++;
                totalScore += Math.min(homesteadShelterBlocks * 3, 9); // Shelter slightly more valuable
            }

            // Need at least 2 categories AND a score of 10+
            // OR 3+ categories with score of 8+
            if ((categoriesPresent >= 2 && totalScore >= 10) || (categoriesPresent >= 3 && totalScore >= 8)) {
                hasHomesteadBlocks = true;
            }
        }

        void recordArchaeologyBlock(BlockState state) {
            if (state.isOf(Blocks.SUSPICIOUS_SAND) || state.isOf(Blocks.SUSPICIOUS_GRAVEL)) {
                suspiciousDigBlocks++;
            } else if (state.isOf(Blocks.DECORATED_POT)) {
                decoratedPotBlocks++;
            }
            updateArchaeologySignal();
        }

        void markTrialBlock() {
            if (!trialBlocksPresent) {
                trialBlocksPresent = true;
                updateArchaeologySignal();
            }
        }

        void recordCherryBlock(BlockState state) {
            if (state.isOf(Blocks.PINK_PETALS)) {
                cherryPetalBlocks++;
            } else if (state.isOf(Blocks.CHERRY_LEAVES)
                || state.isOf(Blocks.CHERRY_LOG)
                || state.isOf(Blocks.STRIPPED_CHERRY_LOG)
                || state.isOf(Blocks.CHERRY_WOOD)
                || state.isOf(Blocks.STRIPPED_CHERRY_WOOD)
                || state.isOf(Blocks.CHERRY_PLANKS)
                || state.isOf(Blocks.CHERRY_SLAB)
                || state.isOf(Blocks.CHERRY_STAIRS)
                || state.isOf(Blocks.CHERRY_SAPLING)) {
                cherryCanopyBlocks++;
            } else if (state.isOf(Blocks.FLOWERING_AZALEA)
                || state.isOf(Blocks.AZALEA)
                || state.isOf(Blocks.BEE_NEST)
                || state.isOf(Blocks.BEEHIVE)) {
                cherryPollinatorBlocks++;
            }
            updateCherrySignal();
        }

        void recordRedstone(boolean component, boolean powerSource, boolean powered) {
            redstoneTracker.record(component, powerSource, powered);
            if (!hasActiveRedstone && redstoneTracker.isActive()) {
                hasActiveRedstone = true;
            }
        }

        void finalizeAfterScan() {
            updateArchaeologySignal();
            updateCherrySignal();
            updateHomesteadSignal();
            if (!hasActiveRedstone && redstoneTracker.isActive()) {
                hasActiveRedstone = true;
            }
        }

        private void updateArchaeologySignal() {
            if (hasArchaeologySite) {
                return;
            }
            if (trialBlocksPresent) {
                hasArchaeologySite = true;
                return;
            }
            if (suspiciousDigBlocks >= 2) {
                hasArchaeologySite = true;
                return;
            }
            if (decoratedPotBlocks >= 3) {
                hasArchaeologySite = true;
                return;
            }
            if (decoratedPotBlocks >= 2 && suspiciousDigBlocks >= 1) {
                hasArchaeologySite = true;
            }
        }

        private void updateCherrySignal() {
            if (hasCherryBloom) {
                return;
            }
            if (cherryCanopyBlocks >= 3 && cherryPetalBlocks >= 2) {
                hasCherryBloom = true;
                return;
            }
            if (cherryCanopyBlocks >= 2 && cherryPetalBlocks >= 1 && cherryPollinatorBlocks >= 1) {
                hasCherryBloom = true;
                return;
            }
            if (cherryCanopyBlocks >= 4 && cherryPollinatorBlocks >= 1) {
                hasCherryBloom = true;
            }
        }

        boolean isComplete() {
            return hasCozyBlocks
                && hasValuableOres
                && hasLushFoliage
                && nearLavaOrMagma
                && nearPowderSnow
                && nearMudOrMangrove
                && nearMajorStructure
                && hasArchaeologySite
                && hasCherryBloom
                && hasActiveRedstone
                && hasHomesteadBlocks;
        }
    }

    public static final class RedstoneNetworkTracker {
        private int componentCount;
        private int powerSourceCount;
        private int dualRoleCount;
        private int poweredComponentCount;
        private int poweredPowerSourceCount;
        private int poweredDualRoleCount;

        public void record(boolean component, boolean powerSource, boolean powered) {
            if (!component && !powerSource) {
                return;
            }
            if (component) {
                componentCount++;
                if (powered) {
                    poweredComponentCount++;
                }
            }
            if (powerSource) {
                powerSourceCount++;
                if (powered) {
                    poweredPowerSourceCount++;
                }
            }
            if (component && powerSource) {
                dualRoleCount++;
                if (powered) {
                    poweredDualRoleCount++;
                }
            }
        }

        private int totalNodeCount() {
            return componentCount + powerSourceCount - dualRoleCount;
        }

        private int poweredNodeCount() {
            return poweredComponentCount + poweredPowerSourceCount - poweredDualRoleCount;
        }

        public boolean isActive() {
            if (powerSourceCount == 0 || componentCount == 0) {
                return false;
            }
            if (componentCount < 2) {
                return false;
            }
            int nodes = totalNodeCount();
            if (nodes < 3) {
                return false;
            }
            int poweredNodes = poweredNodeCount();
            if (nodes >= 5) {
                return poweredNodes >= 3 && poweredComponentCount >= 2;
            }
            if (nodes == 4) {
                return poweredNodes >= 3 && poweredComponentCount >= 2;
            }
            return poweredNodes == 3 && poweredComponentCount >= 2;
        }
    }

    private record NatureRule<T>(Identifier id, Predicate<T> predicate) {
    }
}
