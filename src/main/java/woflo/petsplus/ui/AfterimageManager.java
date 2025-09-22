package woflo.petsplus.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages configurable afterimage/encasement visuals for server-driven sequences.
 *
 * The system reads encasement styles from the config and exposes helpers for
 * starting, ticking, and finishing per-entity glass-like shells that can be
 * reused by multiple mechanics.
 */
public final class AfterimageManager {

    private static final Map<String, EncasementStyle> STYLES = new HashMap<>();
    private static final Map<UUID, EncasementInstance> ACTIVE_INSTANCES = new ConcurrentHashMap<>();
    private static EncasementStyle fallbackStyle = EncasementStyle.createFallback();
    private static boolean tickRegistered;

    private AfterimageManager() {
    }

    /**
     * Load encasement styles from configuration.
     */
    public static void initialize() {
        reloadStyles();
        registerTickHandler();
    }

    /**
     * Reload styles from the petsplus.json configuration.
     */
    public static void reloadStyles() {
        STYLES.clear();
        fallbackStyle = EncasementStyle.createFallback();
        STYLES.put("default", fallbackStyle);

        JsonObject visualsConfig = PetsPlusConfig.getInstance().getRoleConfig("visuals");
        if (!visualsConfig.has("afterimage_styles")) {
            return;
        }

        JsonElement element = visualsConfig.get("afterimage_styles");
        if (!(element instanceof JsonObject stylesObject)) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : stylesObject.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            String key = entry.getKey();
            JsonObject styleObj = entry.getValue().getAsJsonObject();
            EncasementStyle style = EncasementStyle.fromJson(key, styleObj, fallbackStyle);
            if (style != null) {
                STYLES.put(key, style);
                if ("default".equals(key)) {
                    fallbackStyle = style;
                }
            }
        }
    }

    /**
     * Begin an encasement effect around the supplied entity.
     */
    public static void startEncasement(MobEntity entity, String styleKey, int durationTicks) {
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        EncasementStyle style = getStyle(styleKey);
        EncasementInstance existing = ACTIVE_INSTANCES.remove(entity.getUuid());
        if (existing != null) {
            existing.finish(serverWorld, entity, false);
        }

        EncasementInstance instance = new EncasementInstance(entity, style, serverWorld.getTime(), durationTicks);
        ACTIVE_INSTANCES.put(entity.getUuid(), instance);
        instance.playInitial(serverWorld, entity);
    }

    /**
     * Finish the encasement effect, optionally bursting outwards.
     */
    public static void finishEncasement(MobEntity entity, boolean burst) {
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            ACTIVE_INSTANCES.remove(entity.getUuid());
            return;
        }

        EncasementInstance instance = ACTIVE_INSTANCES.remove(entity.getUuid());
        if (instance != null) {
            instance.finish(serverWorld, entity, burst);
        }
    }

    private static void registerTickHandler() {
        if (tickRegistered) {
            return;
        }

        ServerTickEvents.END_WORLD_TICK.register(AfterimageManager::tickWorld);
        tickRegistered = true;
    }

    /**
     * Tick active encasements for the supplied world.
     */
    private static void tickWorld(ServerWorld world) {
        if (ACTIVE_INSTANCES.isEmpty()) {
            return;
        }

        long worldTime = world.getTime();
        ACTIVE_INSTANCES.entrySet().removeIf(entry -> {
            EncasementInstance instance = entry.getValue();
            if (!instance.isInWorld(world)) {
                return false;
            }
            return instance.tick(world, worldTime);
        });
    }

    private static EncasementStyle getStyle(String key) {
        return STYLES.getOrDefault(key, fallbackStyle);
    }

    /**
     * Configured encasement styling information.
     */
    private static final class EncasementStyle {
        private final Identifier blockId;
        private final int encaseTicks;
        private final int columnCount;
        private final int verticalSlices;
        private final int initialShardCount;
        private final int shimmerCount;
        private final int shimmerInterval;
        private final int burstParticleCount;
        private final double burstSpeed;
        private final double shellOffset;
        private final double shimmerSpeed;
        private final double encaseShardSpeed;

        private Block cachedBlock;

        private EncasementStyle(Identifier blockId, int encaseTicks, int columnCount,
                                int verticalSlices, int initialShardCount, int shimmerCount,
                                int shimmerInterval, int burstParticleCount, double burstSpeed,
                                double shellOffset, double shimmerSpeed, double encaseShardSpeed) {
            this.blockId = blockId;
            this.encaseTicks = Math.max(5, encaseTicks);
            this.columnCount = Math.max(3, columnCount);
            this.verticalSlices = Math.max(2, verticalSlices);
            this.initialShardCount = Math.max(6, initialShardCount);
            this.shimmerCount = Math.max(4, shimmerCount);
            this.shimmerInterval = Math.max(1, shimmerInterval);
            this.burstParticleCount = Math.max(8, burstParticleCount);
            this.burstSpeed = Math.max(0.05, burstSpeed);
            this.shellOffset = Math.max(0.05, shellOffset);
            this.shimmerSpeed = Math.max(0.005, shimmerSpeed);
            this.encaseShardSpeed = Math.max(0.005, encaseShardSpeed);
        }

        private static EncasementStyle createFallback() {
            return new EncasementStyle(
                Identifier.of("minecraft", "black_stained_glass"),
                25,
                6,
                5,
                16,
                8,
                5,
                28,
                0.32,
                0.35,
                0.02,
                0.05
            );
        }

        private static EncasementStyle fromJson(String key, JsonObject json, EncasementStyle fallback) {
            try {
                Identifier blockId = null;
                if (json.has("block_id")) {
                    blockId = Identifier.tryParse(json.get("block_id").getAsString());
                }
                if (blockId == null) {
                    blockId = fallback.blockId;
                }

                int encaseTicks = json.has("encase_ticks") ? json.get("encase_ticks").getAsInt() : fallback.encaseTicks;
                int columnCount = json.has("column_count") ? json.get("column_count").getAsInt() : fallback.columnCount;
                int verticalSlices = json.has("vertical_slices") ? json.get("vertical_slices").getAsInt() : fallback.verticalSlices;
                int initialShardCount = json.has("initial_shard_count") ? json.get("initial_shard_count").getAsInt() : fallback.initialShardCount;
                int shimmerCount = json.has("shimmer_count") ? json.get("shimmer_count").getAsInt() : fallback.shimmerCount;
                int shimmerInterval = json.has("shimmer_interval") ? json.get("shimmer_interval").getAsInt() : fallback.shimmerInterval;
                int burstParticleCount = json.has("burst_particle_count") ? json.get("burst_particle_count").getAsInt() : fallback.burstParticleCount;
                double burstSpeed = json.has("burst_speed") ? json.get("burst_speed").getAsDouble() : fallback.burstSpeed;
                double shellOffset = json.has("shell_offset") ? json.get("shell_offset").getAsDouble() : fallback.shellOffset;
                double shimmerSpeed = json.has("shimmer_speed") ? json.get("shimmer_speed").getAsDouble() : fallback.shimmerSpeed;
                double encaseShardSpeed = json.has("encase_shard_speed") ? json.get("encase_shard_speed").getAsDouble() : fallback.encaseShardSpeed;

                return new EncasementStyle(blockId, encaseTicks, columnCount, verticalSlices,
                    initialShardCount, shimmerCount, shimmerInterval, burstParticleCount,
                    burstSpeed, shellOffset, shimmerSpeed, encaseShardSpeed);
            } catch (Exception e) {
                Petsplus.LOGGER.warn("Failed to parse afterimage style {}: {}", key, e.getMessage());
                return null;
            }
        }

        private Block resolveBlock() {
            if (cachedBlock != null) {
                return cachedBlock;
            }

            Block block = Registries.BLOCK.containsId(blockId)
                ? Registries.BLOCK.get(blockId)
                : Blocks.BLACK_STAINED_GLASS;
            if (block == Blocks.AIR) {
                block = Blocks.BLACK_STAINED_GLASS;
            }
            cachedBlock = block;
            return cachedBlock;
        }
    }

    /**
     * Runtime encasement instance data.
     */
    private static final class EncasementInstance {
        private final UUID entityId;
        private final Identifier worldId;
        private final EncasementStyle style;
        private final long startTick;
        private final long durationTicks;
        private final BlockStateParticleEffect shardEffect;
        private final BlockStateParticleEffect dustEffect;

        private Vec3d lastCenter = Vec3d.ZERO;
        private double lastBaseY;
        private double lastHeight;
        private double lastRadiusX;
        private double lastRadiusZ;

        private EncasementInstance(MobEntity entity, EncasementStyle style, long startTick, long durationTicks) {
            this.entityId = entity.getUuid();
            this.worldId = entity.getWorld().getRegistryKey().getValue();
            this.style = style;
            this.startTick = startTick;
            this.durationTicks = Math.max(durationTicks, style.encaseTicks + 20);
            Block block = style.resolveBlock();
            this.shardEffect = new BlockStateParticleEffect(ParticleTypes.BLOCK, block.getDefaultState());
            this.dustEffect = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, block.getDefaultState());
        }

        private boolean isInWorld(ServerWorld world) {
            return world.getRegistryKey().getValue().equals(worldId);
        }

        private void playInitial(ServerWorld world, MobEntity mob) {
            updateCachedGeometry(mob);

            double baseY = lastBaseY;
            double radiusX = lastRadiusX;
            double radiusZ = lastRadiusZ;
            double centerX = lastCenter.x;
            double centerZ = lastCenter.z;
            double height = lastHeight;

            for (int i = 0; i < style.initialShardCount; i++) {
                double angle = (Math.PI * 2 * i) / style.initialShardCount;
                double x = centerX + Math.cos(angle) * radiusX;
                double z = centerZ + Math.sin(angle) * radiusZ;
                double y = baseY + world.random.nextDouble() * Math.min(0.8, height * 0.3);
                world.spawnParticles(shardEffect, x, y, z, 1, 0.04, 0.05, 0.04, style.encaseShardSpeed * 1.4);
            }

            world.playSound(null, centerX, baseY + height * 0.25, centerZ,
                SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 0.6f,
                0.7f + world.random.nextFloat() * 0.2f);
        }

        private boolean tick(ServerWorld world, long worldTime) {
            MobEntity mob = (MobEntity) world.getEntity(entityId);
            if (mob == null || !mob.isAlive()) {
                if (lastHeight > 0.0) {
                    spawnDissipate(world);
                }
                return true;
            }

            updateCachedGeometry(mob);

            long elapsed = worldTime - startTick;
            float encaseProgress = MathHelper.clamp((float) elapsed / style.encaseTicks, 0.0f, 1.0f);
            float lifetimeProgress = MathHelper.clamp((float) elapsed / durationTicks, 0.0f, 1.0f);

            if (elapsed <= style.encaseTicks) {
                spawnEncasingPhase(world, encaseProgress, worldTime);
            } else {
                spawnSustainPhase(world, lifetimeProgress, worldTime);
            }

            if (lifetimeProgress > 0.85f) {
                spawnCracklePhase(world, lifetimeProgress, worldTime);
            }

            return elapsed > durationTicks + style.encaseTicks + 40;
        }

        private void spawnEncasingPhase(ServerWorld world, float progress, long worldTime) {
            double centerX = lastCenter.x;
            double centerZ = lastCenter.z;
            double baseY = lastBaseY;
            double height = lastHeight;
            double radiusX = lastRadiusX;
            double radiusZ = lastRadiusZ;

            int columns = style.columnCount;
            int slices = style.verticalSlices;
            int activeSlices = Math.max(1, Math.min(slices, (int) Math.ceil(progress * slices)));

            for (int column = 0; column < columns; column++) {
                double angle = (Math.PI * 2 * column / columns) + (worldTime * 0.02);
                double x = centerX + Math.cos(angle) * radiusX;
                double z = centerZ + Math.sin(angle) * radiusZ;

                for (int slice = 0; slice < activeSlices; slice++) {
                    double sliceRatio = (double) (slice + world.random.nextDouble() * 0.5) / slices;
                    sliceRatio = Math.min(sliceRatio, progress);
                    double y = baseY + height * sliceRatio;
                    world.spawnParticles(shardEffect, x, y, z, 1, 0.025, 0.045, 0.025, style.encaseShardSpeed);
                }
            }
        }

        private void spawnSustainPhase(ServerWorld world, float lifetimeProgress, long worldTime) {
            double centerX = lastCenter.x;
            double centerZ = lastCenter.z;
            double baseY = lastBaseY;
            double height = lastHeight;
            double radiusX = lastRadiusX;
            double radiusZ = lastRadiusZ;

            if ((worldTime - startTick) % style.shimmerInterval == 0) {
                for (int i = 0; i < style.shimmerCount; i++) {
                    double angle = world.random.nextDouble() * Math.PI * 2;
                    double x = centerX + Math.cos(angle) * radiusX;
                    double z = centerZ + Math.sin(angle) * radiusZ;
                    double y = baseY + world.random.nextDouble() * height;
                    world.spawnParticles(dustEffect, x, y, z, 1, 0.05, 0.03, 0.05, style.shimmerSpeed);
                }

                world.spawnParticles(dustEffect, centerX, baseY + height * 0.5, centerZ,
                    2, radiusX * 0.2, height * 0.3, radiusZ * 0.2, style.shimmerSpeed * 0.6);
            }

            if (worldTime % 20 == 0) {
                // Gentle outline pulses
                for (int column = 0; column < style.columnCount; column += 2) {
                    double angle = (Math.PI * 2 * column / style.columnCount) + (lifetimeProgress * Math.PI);
                    double x = centerX + Math.cos(angle) * radiusX;
                    double z = centerZ + Math.sin(angle) * radiusZ;
                    double yTop = baseY + height * (0.4 + lifetimeProgress * 0.4);
                    world.spawnParticles(shardEffect, x, yTop, z, 1, 0.02, 0.06, 0.02, style.shimmerSpeed * 0.5);
                }
            }
        }

        private void spawnCracklePhase(ServerWorld world, float lifetimeProgress, long worldTime) {
            double centerX = lastCenter.x;
            double centerZ = lastCenter.z;
            double baseY = lastBaseY;
            double height = lastHeight;
            double radiusX = lastRadiusX;
            double radiusZ = lastRadiusZ;

            if (worldTime % 4 != 0) {
                return;
            }

            float intensity = MathHelper.clamp((lifetimeProgress - 0.85f) / 0.15f, 0.0f, 1.0f);
            int shards = Math.max(4, (int) (style.shimmerCount * intensity));

            for (int i = 0; i < shards; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double x = centerX + Math.cos(angle) * radiusX * (0.9 + world.random.nextDouble() * 0.2);
                double z = centerZ + Math.sin(angle) * radiusZ * (0.9 + world.random.nextDouble() * 0.2);
                double y = baseY + world.random.nextDouble() * height;
                world.spawnParticles(shardEffect, x, y, z, 1, 0.05, 0.05, 0.05, style.shimmerSpeed + intensity * 0.1);
            }
        }

        private void finish(ServerWorld world, MobEntity mob, boolean burst) {
            updateCachedGeometry(mob);

            if (burst) {
                spawnBurst(world);
            } else {
                spawnDissipate(world);
            }
        }

        private void spawnBurst(ServerWorld world) {
            double centerX = lastCenter.x;
            double centerY = lastCenter.y;
            double centerZ = lastCenter.z;
            double radiusX = lastRadiusX;
            double radiusZ = lastRadiusZ;
            double radiusY = Math.max(0.6, lastHeight * 0.5);

            world.spawnParticles(shardEffect, centerX, centerY, centerZ, style.burstParticleCount,
                radiusX, radiusY, radiusZ, style.burstSpeed);
            world.spawnParticles(dustEffect, centerX, centerY, centerZ, Math.max(6, style.burstParticleCount / 2),
                radiusX * 0.7, radiusY * 0.6, radiusZ * 0.7, style.burstSpeed * 0.7);

            world.playSound(null, centerX, centerY, centerZ,
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 0.8f,
                0.65f + world.random.nextFloat() * 0.2f);
        }

        private void spawnDissipate(ServerWorld world) {
            double centerX = lastCenter.x;
            double centerY = lastCenter.y;
            double centerZ = lastCenter.z;

            world.spawnParticles(dustEffect, centerX, centerY, centerZ,
                Math.max(4, style.shimmerCount), lastRadiusX * 0.6,
                Math.max(0.3, lastHeight * 0.4), lastRadiusZ * 0.6, style.shimmerSpeed * 0.6);
        }

        private void updateCachedGeometry(MobEntity mob) {
            Box box = mob.getBoundingBox();
            double centerX = box.getCenter().x;
            double centerZ = box.getCenter().z;
            double baseY = box.minY;
            double height = Math.max(0.6, box.maxY - box.minY);
            double widthX = box.maxX - box.minX;
            double widthZ = box.maxZ - box.minZ;
            double radiusX = Math.max(0.35, widthX / 2.0 + style.shellOffset);
            double radiusZ = Math.max(0.35, widthZ / 2.0 + style.shellOffset);

            this.lastCenter = new Vec3d(centerX, baseY + height / 2.0, centerZ);
            this.lastBaseY = baseY;
            this.lastHeight = height;
            this.lastRadiusX = radiusX;
            this.lastRadiusZ = radiusZ;
        }
    }
}
