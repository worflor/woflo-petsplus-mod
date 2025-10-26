package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dispatches lightweight, event-driven emotion pulses that give each nature a
 * distinct ambient personality. Triggers are registered against authored
 * nature IDs and leverage the existing emotion stimulus bus so no per-tick
 * processing is required.
 */
public final class NatureFlavorHandler {

    public enum Trigger {
        OWNER_SLEEP,
        OWNER_RESPAWN,
        DAYBREAK,
        NIGHTFALL,
        WEATHER_RAIN_START,
        WEATHER_THUNDER_START,
        WEATHER_CLEAR,
        CAMPFIRE_INTERACTION,
        BED_INTERACTION,
        JUKEBOX_PLAY,
        VILLAGER_TRADE,
        USE_FIREWORK,
        USE_ENDER_ARTIFACT,
        USE_LAVA_BUCKET,
        USE_FLINT_AND_STEEL,
        BUCKET_FISH,
        PLACE_SAPLING,
        BREAK_MUSHROOM,
        BREAK_MUD,
        BREAK_SNOW,
        INVENTORY_VALUABLE,
        INVENTORY_RELIC,
        REDSTONE_INTERACTION,
        ARCHAEOLOGY_BRUSH,
        USE_TRIAL_KEY,
        CHERRY_BLOSSOM_BLOOM,
        DECORATED_POT_PLACED,
        CHERRY_PETAL_HARVEST,
        REDSTONE_PULSE
    }

    public enum Slot {
        MAJOR,
        MINOR,
        QUIRK
    }

    private record AmbientHook(Trigger trigger, Slot slot, float scale, long cooldownTicks) {
    }

    private record NatureFlavor(Map<Trigger, List<AmbientHook>> hooks) {
        List<AmbientHook> hooksFor(Trigger trigger) {
            return hooks.getOrDefault(trigger, List.of());
        }

        void forEach(java.util.function.BiConsumer<Trigger, List<AmbientHook>> consumer) {
            hooks.forEach((trigger, list) -> consumer.accept(trigger, list));
        }
    }

    private static final Map<Identifier, NatureFlavor> DEFAULT_FLAVORS = new HashMap<>();
    private static final Map<Identifier, NatureFlavor> FLAVORS = new HashMap<>();
    private static final Map<Identifier, NatureFlavor> ASTROLOGY_FLAVORS = new HashMap<>();

    static {
        register("radiant", builder -> builder
            .hook(Trigger.OWNER_SLEEP, Slot.MAJOR, 0.75f, 200)
            .hook(Trigger.DAYBREAK, Slot.MINOR, 0.6f, 200)
            .hook(Trigger.WEATHER_CLEAR, Slot.QUIRK, 0.45f, 240));

        register("lunaris", builder -> builder
            .hook(Trigger.NIGHTFALL, Slot.MAJOR, 0.7f, 200)
            .hook(Trigger.DAYBREAK, Slot.MINOR, 0.6f, 220)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.MINOR, 0.5f, 240)
            .hook(Trigger.OWNER_SLEEP, Slot.QUIRK, 0.35f, 240));

        register("homestead", builder -> builder
            .hook(Trigger.PLACE_SAPLING, Slot.MAJOR, 0.7f, 180)
            .hook(Trigger.VILLAGER_TRADE, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.BED_INTERACTION, Slot.MINOR, 0.5f, 220)
            .hook(Trigger.CAMPFIRE_INTERACTION, Slot.QUIRK, 0.4f, 220)
            .hook(Trigger.DAYBREAK, Slot.QUIRK, 0.3f, 240));

        register("hearth", builder -> builder
            .hook(Trigger.CAMPFIRE_INTERACTION, Slot.MAJOR, 0.65f, 160)
            .hook(Trigger.BED_INTERACTION, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.VILLAGER_TRADE, Slot.QUIRK, 0.35f, 220));

        register("tempest", builder -> builder
            .hook(Trigger.WEATHER_THUNDER_START, Slot.MAJOR, 0.8f, 160)
            .hook(Trigger.USE_FIREWORK, Slot.MINOR, 0.6f, 140)
            .hook(Trigger.WEATHER_RAIN_START, Slot.QUIRK, 0.35f, 200));

        register("solace", builder -> builder
            .hook(Trigger.OWNER_RESPAWN, Slot.MAJOR, 0.75f, 220)
            .hook(Trigger.OWNER_SLEEP, Slot.MINOR, 0.55f, 220)
            .hook(Trigger.WEATHER_CLEAR, Slot.QUIRK, 0.4f, 240));

        register("festival", builder -> builder
            .hook(Trigger.VILLAGER_TRADE, Slot.MAJOR, 0.7f, 160)
            .hook(Trigger.USE_FIREWORK, Slot.MINOR, 0.55f, 160)
            .hook(Trigger.DAYBREAK, Slot.QUIRK, 0.35f, 220));

        register("otherworldly", builder -> builder
            .hook(Trigger.USE_ENDER_ARTIFACT, Slot.MAJOR, 0.75f, 160)
            .hook(Trigger.INVENTORY_RELIC, Slot.MINOR, 0.5f, 240)
            .hook(Trigger.WEATHER_CLEAR, Slot.QUIRK, 0.35f, 240));

        register("infernal", builder -> builder
            .hook(Trigger.USE_LAVA_BUCKET, Slot.MAJOR, 0.75f, 200)
            .hook(Trigger.USE_FLINT_AND_STEEL, Slot.MINOR, 0.55f, 180)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.QUIRK, 0.35f, 240));

        register("echoed", builder -> builder
            .hook(Trigger.JUKEBOX_PLAY, Slot.MAJOR, 0.75f, 200)
            .hook(Trigger.WEATHER_RAIN_START, Slot.MINOR, 0.55f, 220)
            .hook(Trigger.NIGHTFALL, Slot.QUIRK, 0.35f, 240));

        register("mycelial", builder -> builder
            .hook(Trigger.BREAK_MUSHROOM, Slot.MAJOR, 0.75f, 180)
            .hook(Trigger.BREAK_MUD, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.WEATHER_RAIN_START, Slot.QUIRK, 0.35f, 220));

        register("gilded", builder -> builder
            .hook(Trigger.INVENTORY_VALUABLE, Slot.MAJOR, 0.7f, 200)
            .hook(Trigger.VILLAGER_TRADE, Slot.MINOR, 0.5f, 220)
            .hook(Trigger.USE_FIREWORK, Slot.QUIRK, 0.35f, 240));

        register("gloom", builder -> builder
            .hook(Trigger.WEATHER_RAIN_START, Slot.MAJOR, 0.75f, 220)
            .hook(Trigger.NIGHTFALL, Slot.MINOR, 0.55f, 220)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.QUIRK, -0.4f, 260));

        register("summit", builder -> builder
            .hook(Trigger.USE_FIREWORK, Slot.MAJOR, 0.8f, 140)
            .hook(Trigger.DAYBREAK, Slot.MINOR, 0.5f, 220)
            .hook(Trigger.WEATHER_CLEAR, Slot.QUIRK, 0.35f, 240));

        register("tidal", builder -> builder
            .hook(Trigger.BUCKET_FISH, Slot.MAJOR, 0.8f, 180)
            .hook(Trigger.WEATHER_RAIN_START, Slot.MINOR, 0.55f, 220)
            .hook(Trigger.WEATHER_CLEAR, Slot.QUIRK, 0.35f, 260));

        register("molten", builder -> builder
            .hook(Trigger.USE_FLINT_AND_STEEL, Slot.MAJOR, 0.75f, 180)
            .hook(Trigger.USE_LAVA_BUCKET, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.QUIRK, 0.35f, 240));

        register("frosty", builder -> builder
            .hook(Trigger.BREAK_SNOW, Slot.MAJOR, 0.75f, 200)
            .hook(Trigger.WEATHER_CLEAR, Slot.MINOR, 0.5f, 240)
            .hook(Trigger.NIGHTFALL, Slot.QUIRK, 0.35f, 240));

        register("mire", builder -> builder
            .hook(Trigger.BREAK_MUD, Slot.MAJOR, 0.75f, 200)
            .hook(Trigger.WEATHER_RAIN_START, Slot.MINOR, 0.5f, 220)
            .hook(Trigger.OWNER_RESPAWN, Slot.QUIRK, 0.3f, 260));

        register("relic", builder -> builder
            .hook(Trigger.ARCHAEOLOGY_BRUSH, Slot.MAJOR, 0.8f, 180)
            .hook(Trigger.INVENTORY_RELIC, Slot.MAJOR, 0.8f, 220)
            .hook(Trigger.JUKEBOX_PLAY, Slot.MINOR, 0.5f, 260)
            .hook(Trigger.INVENTORY_VALUABLE, Slot.QUIRK, 0.3f, 260));

        register("ceramic", builder -> builder
            .hook(Trigger.ARCHAEOLOGY_BRUSH, Slot.MAJOR, 0.75f, 160)
            .hook(Trigger.DECORATED_POT_PLACED, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.USE_TRIAL_KEY, Slot.QUIRK, 0.45f, 220));

        register("blossom", builder -> builder
            .hook(Trigger.CHERRY_BLOSSOM_BLOOM, Slot.MAJOR, 0.75f, 180)
            .hook(Trigger.CHERRY_PETAL_HARVEST, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.PLACE_SAPLING, Slot.QUIRK, 0.35f, 220));

        register("clockwork", builder -> builder
            .hook(Trigger.REDSTONE_PULSE, Slot.MAJOR, 0.8f, 160)
            .hook(Trigger.REDSTONE_INTERACTION, Slot.MINOR, 0.55f, 180)
            .hook(Trigger.USE_FIREWORK, Slot.QUIRK, 0.35f, 220));

        register("unnatural", builder -> builder
            .hook(Trigger.REDSTONE_INTERACTION, Slot.MAJOR, 0.8f, 140)
            .hook(Trigger.USE_ENDER_ARTIFACT, Slot.MINOR, 0.55f, 220)
            .hook(Trigger.USE_TRIAL_KEY, Slot.MINOR, 0.6f, 200)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.QUIRK, 0.35f, 240));

        register("abstract", builder -> builder
            .hook(Trigger.OWNER_RESPAWN, Slot.MAJOR, 0.78f, 220)
            .hook(Trigger.USE_ENDER_ARTIFACT, Slot.MINOR, 0.52f, 200)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.MINOR, 0.36f, 260)
            .hook(Trigger.NIGHTFALL, Slot.QUIRK, 0.44f, 240)
            .hook(Trigger.DAYBREAK, Slot.QUIRK, -0.28f, 260));

        register("verdant", builder -> builder
            .hook(Trigger.PLACE_SAPLING, Slot.MAJOR, 0.75f, 160)
            .hook(Trigger.CHERRY_BLOSSOM_BLOOM, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.WEATHER_CLEAR, Slot.MINOR, 0.5f, 240)
            .hook(Trigger.BREAK_MUSHROOM, Slot.QUIRK, 0.3f, 240));
        
        register("sentinel", builder -> builder
            .hook(Trigger.WEATHER_THUNDER_START, Slot.MAJOR, 0.70f, 180)
            .hook(Trigger.NIGHTFALL, Slot.MINOR, 0.50f, 220)
            .hook(Trigger.OWNER_SLEEP, Slot.MINOR, 0.45f, 200)
            .hook(Trigger.DAYBREAK, Slot.QUIRK, 0.30f, 240));
        
        register("scrappy", builder -> builder
            .hook(Trigger.USE_FIREWORK, Slot.MAJOR, 0.75f, 160)
            .hook(Trigger.VILLAGER_TRADE, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.OWNER_RESPAWN, Slot.MINOR, 0.50f, 220)
            .hook(Trigger.WEATHER_CLEAR, Slot.QUIRK, 0.35f, 240)
            .hook(Trigger.CHERRY_BLOSSOM_BLOOM, Slot.MINOR, 0.6f, 220)
            .hook(Trigger.CHERRY_PETAL_HARVEST, Slot.QUIRK, 0.4f, 220));

        register("fenn", builder -> builder
            .hook(Trigger.WEATHER_RAIN_START, Slot.MAJOR, 0.75f, 180)
            .hook(Trigger.NIGHTFALL, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.OWNER_SLEEP, Slot.QUIRK, 0.35f, 200));

        register("falsi", builder -> builder
            .hook(Trigger.JUKEBOX_PLAY, Slot.MAJOR, 0.75f, 160)
            .hook(Trigger.VILLAGER_TRADE, Slot.MINOR, 0.55f, 180)
            .hook(Trigger.CHERRY_BLOSSOM_BLOOM, Slot.MINOR, 0.45f, 220)
            .hook(Trigger.PLACE_SAPLING, Slot.QUIRK, 0.40f, 200));
    }

    private NatureFlavorHandler() {
    }

    public static void triggerForOwner(ServerPlayerEntity owner, double radius, Trigger trigger) {
        if (owner == null) {
            return;
        }
        if (!(owner.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        StateManager stateManager = StateManager.forWorld(world);
        PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
        List<PetSwarmIndex.SwarmEntry> swarm = swarmIndex.snapshotOwner(owner.getUuid());
        if (swarm.isEmpty()) {
            return;
        }

        MoodService moodService = MoodService.getInstance();
        long time = world.getTime();
        Vec3d center = owner.getEntityPos();
        double normalizedRadius = Math.max(0.0D, radius);
        double squaredRadius = normalizedRadius * normalizedRadius;
        boolean limitByRadius = normalizedRadius > 0.0D;
        Box ownerBounds = owner.getBoundingBox();

        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity pet = entry.pet();
            if (pet == null || !pet.isAlive()) {
                continue;
            }
            if (limitByRadius) {
                if (pet.squaredDistanceTo(center) > squaredRadius) {
                    continue;
                }
            } else if (!ownerBounds.intersects(pet.getBoundingBox())) {
                continue;
            }
            PetComponent component = entry.component();
            moodService.getStimulusBus().queueStimulus(pet,
                comp -> applyAmbientFlavor(pet, comp, world, owner, trigger, time));
        }
    }

    public static void triggerForPet(MobEntity pet, PetComponent component, @Nullable ServerWorld world,
                                     @Nullable ServerPlayerEntity owner, Trigger trigger, long time) {
        if (component == null) {
            return;
        }
        applyAmbientFlavor(pet, component, world, owner, trigger, time);
    }

    private static void applyAmbientFlavor(MobEntity pet, PetComponent component, @Nullable ServerWorld world,
                                           @Nullable ServerPlayerEntity owner, Trigger trigger, long time) {
        if (component == null) {
            return;
        }
        Identifier natureId = component.getNatureId();
        if (natureId == null) {
            return;
        }

        PetComponent.NatureEmotionProfile profile = component.getNatureEmotionProfile();
        if (profile == null || profile.isEmpty()) {
            return;
        }

        boolean applied = applyFlavor(component, trigger, time, profile, FLAVORS.get(natureId));

        if (natureId.equals(AstrologyRegistry.LUNARIS_NATURE_ID)) {
            Identifier signId = component.getAstrologySignId();
            if (signId != null) {
                applied |= applyFlavor(component, trigger, time, profile, ASTROLOGY_FLAVORS.get(signId));
            }
        }

        if (applied) {
            markTriggered(component, trigger, time);
        }
    }

    private static boolean canTrigger(PetComponent component, Trigger trigger, long cooldown, long now) {
        String key = cooldownKey(trigger);
        Long last = component.getStateData(key, Long.class, 0L);
        return now - last >= cooldown;
    }

    private static boolean applyFlavor(PetComponent component,
                                       Trigger trigger,
                                       long time,
                                       PetComponent.NatureEmotionProfile profile,
                                       @Nullable NatureFlavor flavor) {
        if (flavor == null) {
            return false;
        }
        List<AmbientHook> hooks = flavor.hooksFor(trigger);
        if (hooks.isEmpty()) {
            return false;
        }

        boolean applied = false;
        for (AmbientHook hook : hooks) {
            if (!canTrigger(component, trigger, hook.cooldownTicks(), time)) {
                continue;
            }
            PetComponent.Emotion emotion = emotionForSlot(profile, hook.slot());
            float strength = strengthForSlot(profile, hook.slot());
            if (emotion == null || strength <= 0f) {
                continue;
            }
            float amount = strength * hook.scale() * component.getHarmonyMoodMultiplier();
            if (Math.abs(amount) <= 0.001f) {
                continue;
            }
            component.pushEmotion(emotion, amount);
            applied = true;
        }
        return applied;
    }

    private static void markTriggered(PetComponent component, Trigger trigger, long time) {
        component.setStateData(cooldownKey(trigger), time);
    }

    private static String cooldownKey(Trigger trigger) {
        return "nature_flavor_last_" + trigger.name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static PetComponent.Emotion emotionForSlot(PetComponent.NatureEmotionProfile profile, Slot slot) {
        return switch (slot) {
            case MAJOR -> profile.majorEmotion();
            case MINOR -> profile.minorEmotion();
            case QUIRK -> profile.quirkEmotion();
        };
    }

    private static float strengthForSlot(PetComponent.NatureEmotionProfile profile, Slot slot) {
        return switch (slot) {
            case MAJOR -> profile.majorStrength();
            case MINOR -> profile.minorStrength();
            case QUIRK -> profile.quirkStrength();
        };
    }

    private static void register(String path, java.util.function.Consumer<NatureFlavorBuilder> consumer) {
        NatureFlavorBuilder builder = new NatureFlavorBuilder();
        consumer.accept(builder);
        Identifier id = Identifier.of("petsplus", path);
        NatureFlavor flavor = builder.build();
        DEFAULT_FLAVORS.put(id, flavor);
        FLAVORS.put(id, flavor);
    }

    private static final class NatureFlavorBuilder {
        private final EnumMap<Trigger, List<AmbientHook>> hooks = new EnumMap<>(Trigger.class);

        NatureFlavorBuilder hook(Trigger trigger, Slot slot, float scale, long cooldownTicks) {
            hooks.computeIfAbsent(trigger, ignored -> new ArrayList<>())
                .add(new AmbientHook(trigger, slot, scale, cooldownTicks));
            return this;
        }

        NatureFlavor build() {
            EnumMap<Trigger, List<AmbientHook>> map = new EnumMap<>(Trigger.class);
            hooks.forEach((trigger, list) -> map.put(trigger, List.copyOf(list)));
            return new NatureFlavor(map);
        }
    }

    public record HookConfig(Trigger trigger, Slot slot, float scale, long cooldownTicks, boolean append) {
    }

    public record NatureFlavorOverride(boolean replace, List<HookConfig> hooks) {
    }

    public static synchronized void reloadFromDatapack(Map<Identifier, NatureFlavorOverride> overrides) {
        FLAVORS.clear();
        FLAVORS.putAll(DEFAULT_FLAVORS);
        if (overrides == null || overrides.isEmpty()) {
            return;
        }

        overrides.forEach((natureId, override) -> {
            if (override == null) {
                return;
            }

            NatureFlavor existing = override.replace() ? null : FLAVORS.get(natureId);
            NatureFlavor merged = mergeOverride(existing, override);
            if (merged == null) {
                if (override.replace()) {
                    FLAVORS.remove(natureId);
                }
                return;
            }
            FLAVORS.put(natureId, merged);
        });
    }

    public static synchronized void reloadAstrologyOverrides(Map<Identifier, NatureFlavorOverride> overrides) {
        ASTROLOGY_FLAVORS.clear();
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        overrides.forEach((signId, override) -> {
            if (override == null) {
                return;
            }
            NatureFlavor existing = override.replace() ? null : ASTROLOGY_FLAVORS.get(signId);
            NatureFlavor merged = mergeOverride(existing, override);
            if (merged == null) {
                if (override.replace()) {
                    ASTROLOGY_FLAVORS.remove(signId);
                }
                return;
            }
            ASTROLOGY_FLAVORS.put(signId, merged);
        });
    }

    private static @Nullable NatureFlavor mergeOverride(@Nullable NatureFlavor existing, NatureFlavorOverride override) {
        EnumMap<Trigger, List<AmbientHook>> mutable = new EnumMap<>(Trigger.class);
        if (existing != null) {
            existing.forEach((trigger, list) -> mutable.put(trigger, new ArrayList<>(list)));
        }

        List<HookConfig> hooks = override.hooks();
        if (hooks != null) {
            for (HookConfig config : hooks) {
                if (config == null || config.trigger() == null || config.slot() == null) {
                    continue;
                }
                if (!config.append()) {
                    mutable.remove(config.trigger());
                }
            }
            for (HookConfig config : hooks) {
                if (config == null || config.trigger() == null || config.slot() == null) {
                    continue;
                }
                mutable.computeIfAbsent(config.trigger(), ignored -> new ArrayList<>())
                    .add(new AmbientHook(config.trigger(), config.slot(), config.scale(), config.cooldownTicks()));
            }
        }

        if (mutable.isEmpty()) {
            return null;
        }

        NatureFlavorBuilder builder = new NatureFlavorBuilder();
        mutable.forEach((trigger, list) -> list.forEach(hook ->
            builder.hook(hook.trigger(), hook.slot(), hook.scale(), hook.cooldownTicks())));
        return builder.build();
    }
}
