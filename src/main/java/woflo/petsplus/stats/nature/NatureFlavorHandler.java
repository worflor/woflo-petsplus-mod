package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.state.PetComponent;

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
        REDSTONE_INTERACTION
    }

    private enum Slot {
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
    }

    private static final Map<Identifier, NatureFlavor> FLAVORS = new HashMap<>();

    static {
        register("radiant", builder -> builder
            .hook(Trigger.OWNER_SLEEP, Slot.MAJOR, 0.75f, 200)
            .hook(Trigger.DAYBREAK, Slot.MINOR, 0.6f, 200)
            .hook(Trigger.WEATHER_CLEAR, Slot.QUIRK, 0.45f, 240));

        register("nocturne", builder -> builder
            .hook(Trigger.NIGHTFALL, Slot.MAJOR, 0.75f, 200)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.MINOR, 0.55f, 200)
            .hook(Trigger.OWNER_SLEEP, Slot.QUIRK, 0.35f, 260));

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

        register("verdant", builder -> builder
            .hook(Trigger.PLACE_SAPLING, Slot.MAJOR, 0.75f, 160)
            .hook(Trigger.WEATHER_CLEAR, Slot.MINOR, 0.5f, 240)
            .hook(Trigger.BREAK_MUSHROOM, Slot.QUIRK, 0.3f, 240));

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
            .hook(Trigger.INVENTORY_RELIC, Slot.MAJOR, 0.8f, 220)
            .hook(Trigger.JUKEBOX_PLAY, Slot.MINOR, 0.5f, 260)
            .hook(Trigger.INVENTORY_VALUABLE, Slot.QUIRK, 0.3f, 260));

        register("unnatural", builder -> builder
            .hook(Trigger.REDSTONE_INTERACTION, Slot.MAJOR, 0.8f, 140)
            .hook(Trigger.USE_ENDER_ARTIFACT, Slot.MINOR, 0.55f, 220)
            .hook(Trigger.WEATHER_THUNDER_START, Slot.QUIRK, 0.35f, 240));
    }

    private NatureFlavorHandler() {
    }

    public static void triggerForOwner(ServerPlayerEntity owner, double radius, Trigger trigger) {
        if (owner == null) {
            return;
        }
        if (!(owner.getWorld() instanceof ServerWorld world)) {
            return;
        }
        List<MobEntity> nearby = world.getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(radius), mob -> true);
        if (nearby.isEmpty()) {
            return;
        }

        MoodService moodService = MoodService.getInstance();
        long time = world.getTime();
        for (MobEntity pet : nearby) {
            PetComponent component = PetComponent.get(pet);
            if (component == null || !component.isOwnedBy(owner)) {
                continue;
            }
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

        NatureFlavor flavor = FLAVORS.get(natureId);
        if (flavor == null) {
            return;
        }

        PetComponent.NatureEmotionProfile profile = component.getNatureEmotionProfile();
        if (profile == null || profile.isEmpty()) {
            return;
        }

        List<AmbientHook> hooks = flavor.hooksFor(trigger);
        if (hooks.isEmpty()) {
            return;
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
            float amount = strength * hook.scale();
            if (Math.abs(amount) <= 0.001f) {
                continue;
            }
            component.pushEmotion(emotion, amount);
            applied = true;
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
        FLAVORS.put(Identifier.of("petsplus", path), builder.build());
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
}
