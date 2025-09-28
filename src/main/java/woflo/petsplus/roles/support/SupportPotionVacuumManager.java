package woflo.petsplus.roles.support;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Caches potion item entities by chunk section so support pets can reuse
 * nearby potion lookups instead of issuing fresh world scans each tick.
 */
public final class SupportPotionVacuumManager {

    private static final SupportPotionVacuumManager INSTANCE = new SupportPotionVacuumManager();

    private final Map<ServerWorld, WorldCache> worldCaches = new WeakHashMap<>();

    private SupportPotionVacuumManager() {
    }

    public static SupportPotionVacuumManager getInstance() {
        return INSTANCE;
    }

    public void trackOrUpdate(ItemEntity entity) {
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!isPotionStack(entity)) {
            remove(entity);
            return;
        }
        WorldCache cache = worldCaches.computeIfAbsent(serverWorld, w -> new WorldCache());
        cache.update(entity);
    }

    public void handleStackChanged(ItemEntity entity) {
        if (entity.getWorld().isClient()) {
            return;
        }
        if (entity.isRemoved() || entity.getStack().isEmpty()) {
            remove(entity);
        } else {
            trackOrUpdate(entity);
        }
    }

    public void remove(ItemEntity entity) {
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        WorldCache cache = worldCaches.get(serverWorld);
        if (cache != null) {
            cache.remove(entity);
            if (cache.isEmpty()) {
                worldCaches.remove(serverWorld);
            }
        }
    }

    public List<ItemEntity> collectPotionsNearby(MobEntity pet, double radius) {
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return Collections.emptyList();
        }
        return collectPotionsNearby(serverWorld, pet.getPos(), radius);
    }

    public List<ItemEntity> collectPotionsNearby(ServerWorld world, Vec3d center, double radius) {
        if (world == null || radius <= 0) {
            return Collections.emptyList();
        }
        WorldCache cache = worldCaches.get(world);
        if (cache == null) {
            return Collections.emptyList();
        }
        return cache.collect(center, radius);
    }

    private static boolean isPotionStack(ItemEntity entity) {
        return !entity.getStack().isEmpty() && SupportPotionUtils.isPotionItem(entity.getStack().getItem());
    }

    private static final class WorldCache {
        private final Map<Long, Set<ItemEntity>> itemsBySection = new HashMap<>();
        private final Map<ItemEntity, TrackedEntry> entries = new IdentityHashMap<>();

        void update(ItemEntity entity) {
            long sectionKey = sectionKey(entity.getPos());
            TrackedEntry entry = entries.get(entity);
            if (entry == null) {
                entry = new TrackedEntry(entity, sectionKey);
                entries.put(entity, entry);
                itemsBySection.computeIfAbsent(sectionKey, k -> new HashSet<>()).add(entity);
            } else if (entry.sectionKey != sectionKey) {
                Set<ItemEntity> current = itemsBySection.get(entry.sectionKey);
                if (current != null) {
                    current.remove(entity);
                    if (current.isEmpty()) {
                        itemsBySection.remove(entry.sectionKey);
                    }
                }
                entry.sectionKey = sectionKey;
                itemsBySection.computeIfAbsent(sectionKey, k -> new HashSet<>()).add(entity);
            }
        }

        void remove(ItemEntity entity) {
            TrackedEntry entry = entries.remove(entity);
            if (entry != null) {
                Set<ItemEntity> set = itemsBySection.get(entry.sectionKey);
                if (set != null) {
                    set.remove(entity);
                    if (set.isEmpty()) {
                        itemsBySection.remove(entry.sectionKey);
                    }
                }
            }
        }

        List<ItemEntity> collect(Vec3d center, double radius) {
            double radiusSq = radius * radius;
            int sectionRadius = MathHelper.ceil(radius / 16.0);
            int baseX = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.x));
            int baseY = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.y));
            int baseZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.z));

            List<ItemEntity> result = new ArrayList<>();
            for (int sx = baseX - sectionRadius; sx <= baseX + sectionRadius; sx++) {
                for (int sy = baseY - sectionRadius; sy <= baseY + sectionRadius; sy++) {
                    for (int sz = baseZ - sectionRadius; sz <= baseZ + sectionRadius; sz++) {
                        Set<ItemEntity> sectionItems = itemsBySection.get(ChunkSectionPos.asLong(sx, sy, sz));
                        if (sectionItems == null || sectionItems.isEmpty()) {
                            continue;
                        }
                        for (ItemEntity entity : new ArrayList<>(sectionItems)) {
                            if (entity.isRemoved() || entity.getStack().isEmpty()) {
                                remove(entity);
                                continue;
                            }
                            if (!SupportPotionUtils.isPotionItem(entity.getStack().getItem())) {
                                remove(entity);
                                continue;
                            }
                            double dx = entity.getX() - center.x;
                            double dy = entity.getY() - center.y;
                            double dz = entity.getZ() - center.z;
                            if ((dx * dx) + (dy * dy) + (dz * dz) <= radiusSq) {
                                result.add(entity);
                            }
                        }
                    }
                }
            }
            return result;
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        private static long sectionKey(Vec3d pos) {
            int secX = ChunkSectionPos.getSectionCoord(MathHelper.floor(pos.x));
            int secY = ChunkSectionPos.getSectionCoord(MathHelper.floor(pos.y));
            int secZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(pos.z));
            return ChunkSectionPos.asLong(secX, secY, secZ);
        }
    }

    private static final class TrackedEntry {
        private long sectionKey;

        private TrackedEntry(ItemEntity entity, long sectionKey) {
            this.sectionKey = sectionKey;
        }
    }
}
