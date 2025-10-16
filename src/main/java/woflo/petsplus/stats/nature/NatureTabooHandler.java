package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tracks nature-specific taboo reactions. Taboo stimuli are event-driven and
 * reuse the global emotion stimulus bus so we never rely on per-tick polling.
 *
 * <p>Each taboo definition contains the accumulation window, warning and
 * escalation thresholds, cooldown handling, emotion slot scaling, and cue
 * metadata required to surface behaviour to the owner.</p>
 */
public final class NatureTabooHandler {

    /**
     * Taboo triggers represent broad stimulus families. A nature maps to a
     * single taboo trigger; repeated stimuli within the configured accumulation
     * window escalate from a warning to a full panic reaction.
     */
    public enum TabooTrigger {
        LEASH_STASIS,
        TIGHT_SPACES,
        DISARMAMENT,
        PROLONGED_DARKNESS,
        LONELY_SILENCE,
        DOUSED_FLAME,
        LEYLINE_CLUTTER,
        OWNER_ABSENCE,
        CALM_WEATHER,
        LOUD_QUARREL,
        ECHOLESS_VOID,
        SOIL_STERILIZATION,
        JUNK_GLUT,
        SUDDEN_GLARE,
        MASS_DEFORESTATION,
        DEEP_UNDERGROUND,
        EXTREME_DROUGHT,
        BITTER_COLD_WATER,
        SCORCHING_HEAT,
        RUSHED_MARCH,
        ARTIFACT_ABUSE,
        VIOLENT_VIBRATION,
        DESYNCED_REDSTONE,
        RAW_SUNLIGHT,
        HOME_DISARRAY,
        STORM_BARRAGE,
        SLEEPING_DANGER,
        STAGNANT_ROUTINE
    }

    /** Stages of taboo escalation. */
    private enum Stage {
        CALM(0),
        WARNING(1),
        PANIC(2);

        private final int code;

        Stage(int code) {
            this.code = code;
        }

        static Stage fromCode(int code) {
            return switch (code) {
                case 1 -> WARNING;
                case 2 -> PANIC;
                default -> CALM;
            };
        }

        int code() {
            return code;
        }
    }

    private record TabooDefinition(
        TabooTrigger trigger,
        NatureFlavorHandler.Slot warningSlot,
        float warningScale,
        NatureFlavorHandler.Slot panicSlot,
        float panicScale,
        int warningThreshold,
        int panicThreshold,
        long accumulationWindow,
        long cooldownTicks,
        @Nullable String warningCue,
        @Nullable String panicCue
    ) {
    }

    private static final Map<Identifier, TabooDefinition> DEFAULT_TABOOS = new HashMap<>();
    private static final Map<Identifier, TabooDefinition> TABOOS = new HashMap<>();

    static {
        register("frisky", new TabooDefinition(
            TabooTrigger.LEASH_STASIS,
            NatureFlavorHandler.Slot.QUIRK, -0.35f,
            NatureFlavorHandler.Slot.MAJOR, -0.55f,
            3, 5, 200, 400,
            "petsplus.emotion_cue.taboo.frisky.warn",
            "petsplus.emotion_cue.taboo.frisky.panic"));

        register("feral", new TabooDefinition(
            TabooTrigger.TIGHT_SPACES,
            NatureFlavorHandler.Slot.MINOR, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.50f,
            4, 6, 220, 420,
            "petsplus.emotion_cue.taboo.feral.warn",
            "petsplus.emotion_cue.taboo.feral.panic"));

        register("fierce", new TabooDefinition(
            TabooTrigger.DISARMAMENT,
            NatureFlavorHandler.Slot.MINOR, -0.28f,
            NatureFlavorHandler.Slot.MAJOR, -0.52f,
            2, 4, 180, 360,
            "petsplus.emotion_cue.taboo.fierce.warn",
            "petsplus.emotion_cue.taboo.fierce.panic"));

        register("radiant", new TabooDefinition(
            TabooTrigger.PROLONGED_DARKNESS,
            NatureFlavorHandler.Slot.MINOR, -0.32f,
            NatureFlavorHandler.Slot.MAJOR, -0.58f,
            3, 6, 240, 480,
            "petsplus.emotion_cue.taboo.radiant.warn",
            "petsplus.emotion_cue.taboo.radiant.panic"));

        register("lunaris", new TabooDefinition(
            TabooTrigger.SUDDEN_GLARE,
            NatureFlavorHandler.Slot.MINOR, -0.26f,
            NatureFlavorHandler.Slot.MAJOR, -0.48f,
            3, 5, 220, 440,
            "petsplus.emotion_cue.taboo.lunaris.warn",
            "petsplus.emotion_cue.taboo.lunaris.panic"));

        register("festival", new TabooDefinition(
            TabooTrigger.LONELY_SILENCE,
            NatureFlavorHandler.Slot.MINOR, -0.27f,
            NatureFlavorHandler.Slot.MAJOR, -0.50f,
            4, 7, 260, 520,
            "petsplus.emotion_cue.taboo.festival.warn",
            "petsplus.emotion_cue.taboo.festival.panic"));

        register("infernal", new TabooDefinition(
            TabooTrigger.DOUSED_FLAME,
            NatureFlavorHandler.Slot.MINOR, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.56f,
            2, 4, 160, 420,
            "petsplus.emotion_cue.taboo.infernal.warn",
            "petsplus.emotion_cue.taboo.infernal.panic"));

        register("otherworldly", new TabooDefinition(
            TabooTrigger.LEYLINE_CLUTTER,
            NatureFlavorHandler.Slot.QUIRK, -0.33f,
            NatureFlavorHandler.Slot.MAJOR, -0.54f,
            3, 6, 240, 480,
            "petsplus.emotion_cue.taboo.otherworldly.warn",
            "petsplus.emotion_cue.taboo.otherworldly.panic"));

        register("hearth", new TabooDefinition(
            TabooTrigger.OWNER_ABSENCE,
            NatureFlavorHandler.Slot.MINOR, -0.29f,
            NatureFlavorHandler.Slot.MAJOR, -0.49f,
            2, 4, 400, 600,
            "petsplus.emotion_cue.taboo.hearth.warn",
            "petsplus.emotion_cue.taboo.hearth.panic"));

        register("tempest", new TabooDefinition(
            TabooTrigger.CALM_WEATHER,
            NatureFlavorHandler.Slot.QUIRK, -0.31f,
            NatureFlavorHandler.Slot.MAJOR, -0.55f,
            3, 6, 200, 420,
            "petsplus.emotion_cue.taboo.tempest.warn",
            "petsplus.emotion_cue.taboo.tempest.panic"));

        register("solace", new TabooDefinition(
            TabooTrigger.LOUD_QUARREL,
            NatureFlavorHandler.Slot.MINOR, -0.28f,
            NatureFlavorHandler.Slot.MAJOR, -0.50f,
            3, 5, 200, 480,
            "petsplus.emotion_cue.taboo.solace.warn",
            "petsplus.emotion_cue.taboo.solace.panic"));

        register("echoed", new TabooDefinition(
            TabooTrigger.ECHOLESS_VOID,
            NatureFlavorHandler.Slot.QUIRK, -0.33f,
            NatureFlavorHandler.Slot.MAJOR, -0.57f,
            4, 6, 240, 500,
            "petsplus.emotion_cue.taboo.echoed.warn",
            "petsplus.emotion_cue.taboo.echoed.panic"));

        register("mycelial", new TabooDefinition(
            TabooTrigger.SOIL_STERILIZATION,
            NatureFlavorHandler.Slot.MINOR, -0.29f,
            NatureFlavorHandler.Slot.MAJOR, -0.52f,
            3, 6, 260, 520,
            "petsplus.emotion_cue.taboo.mycelial.warn",
            "petsplus.emotion_cue.taboo.mycelial.panic"));

        register("gilded", new TabooDefinition(
            TabooTrigger.JUNK_GLUT,
            NatureFlavorHandler.Slot.QUIRK, -0.32f,
            NatureFlavorHandler.Slot.MAJOR, -0.54f,
            3, 5, 220, 520,
            "petsplus.emotion_cue.taboo.gilded.warn",
            "petsplus.emotion_cue.taboo.gilded.panic"));

        register("gloom", new TabooDefinition(
            TabooTrigger.SUDDEN_GLARE,
            NatureFlavorHandler.Slot.MINOR, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.53f,
            2, 4, 200, 480,
            "petsplus.emotion_cue.taboo.gloom.warn",
            "petsplus.emotion_cue.taboo.gloom.panic"));

        register("verdant", new TabooDefinition(
            TabooTrigger.MASS_DEFORESTATION,
            NatureFlavorHandler.Slot.MAJOR, -0.52f,
            NatureFlavorHandler.Slot.MAJOR, -0.70f,
            2, 4, 200, 440,
            "petsplus.emotion_cue.taboo.verdant.warn",
            "petsplus.emotion_cue.taboo.verdant.panic"));

        register("summit", new TabooDefinition(
            TabooTrigger.DEEP_UNDERGROUND,
            NatureFlavorHandler.Slot.MINOR, -0.31f,
            NatureFlavorHandler.Slot.MAJOR, -0.55f,
            3, 5, 240, 520,
            "petsplus.emotion_cue.taboo.summit.warn",
            "petsplus.emotion_cue.taboo.summit.panic"));

        register("tidal", new TabooDefinition(
            TabooTrigger.EXTREME_DROUGHT,
            NatureFlavorHandler.Slot.MINOR, -0.28f,
            NatureFlavorHandler.Slot.MAJOR, -0.53f,
            3, 5, 240, 480,
            "petsplus.emotion_cue.taboo.tidal.warn",
            "petsplus.emotion_cue.taboo.tidal.panic"));

        register("molten", new TabooDefinition(
            TabooTrigger.BITTER_COLD_WATER,
            NatureFlavorHandler.Slot.MINOR, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.56f,
            2, 4, 160, 420,
            "petsplus.emotion_cue.taboo.molten.warn",
            "petsplus.emotion_cue.taboo.molten.panic"));

        register("frosty", new TabooDefinition(
            TabooTrigger.SCORCHING_HEAT,
            NatureFlavorHandler.Slot.MINOR, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.55f,
            3, 5, 220, 480,
            "petsplus.emotion_cue.taboo.frosty.warn",
            "petsplus.emotion_cue.taboo.frosty.panic"));

        register("mire", new TabooDefinition(
            TabooTrigger.RUSHED_MARCH,
            NatureFlavorHandler.Slot.MINOR, -0.29f,
            NatureFlavorHandler.Slot.MAJOR, -0.50f,
            4, 7, 260, 520,
            "petsplus.emotion_cue.taboo.mire.warn",
            "petsplus.emotion_cue.taboo.mire.panic"));

        register("relic", new TabooDefinition(
            TabooTrigger.ARTIFACT_ABUSE,
            NatureFlavorHandler.Slot.MINOR, -0.32f,
            NatureFlavorHandler.Slot.MAJOR, -0.58f,
            2, 4, 200, 480,
            "petsplus.emotion_cue.taboo.relic.warn",
            "petsplus.emotion_cue.taboo.relic.panic"));

        register("ceramic", new TabooDefinition(
            TabooTrigger.VIOLENT_VIBRATION,
            NatureFlavorHandler.Slot.MINOR, -0.32f,
            NatureFlavorHandler.Slot.MAJOR, -0.55f,
            3, 5, 220, 500,
            "petsplus.emotion_cue.taboo.ceramic.warn",
            "petsplus.emotion_cue.taboo.ceramic.panic"));

        register("clockwork", new TabooDefinition(
            TabooTrigger.DESYNCED_REDSTONE,
            NatureFlavorHandler.Slot.QUIRK, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.52f,
            3, 6, 200, 400,
            "petsplus.emotion_cue.taboo.clockwork.warn",
            "petsplus.emotion_cue.taboo.clockwork.panic"));

        register("unnatural", new TabooDefinition(
            TabooTrigger.RAW_SUNLIGHT,
            NatureFlavorHandler.Slot.MINOR, -0.32f,
            NatureFlavorHandler.Slot.MAJOR, -0.57f,
            3, 5, 220, 520,
            "petsplus.emotion_cue.taboo.unnatural.warn",
            "petsplus.emotion_cue.taboo.unnatural.panic"));

        register("homestead", new TabooDefinition(
            TabooTrigger.HOME_DISARRAY,
            NatureFlavorHandler.Slot.MINOR, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.52f,
            3, 6, 260, 520,
            "petsplus.emotion_cue.taboo.homestead.warn",
            "petsplus.emotion_cue.taboo.homestead.panic"));

        register("blossom", new TabooDefinition(
            TabooTrigger.STORM_BARRAGE,
            NatureFlavorHandler.Slot.MINOR, -0.28f,
            NatureFlavorHandler.Slot.MAJOR, -0.50f,
            3, 5, 200, 420,
            "petsplus.emotion_cue.taboo.blossom.warn",
            "petsplus.emotion_cue.taboo.blossom.panic"));

        register("sentinel", new TabooDefinition(
            TabooTrigger.SLEEPING_DANGER,
            NatureFlavorHandler.Slot.MINOR, -0.32f,
            NatureFlavorHandler.Slot.MAJOR, -0.56f,
            2, 4, 200, 420,
            "petsplus.emotion_cue.taboo.sentinel.warn",
            "petsplus.emotion_cue.taboo.sentinel.panic"));

        register("scrappy", new TabooDefinition(
            TabooTrigger.STAGNANT_ROUTINE,
            NatureFlavorHandler.Slot.QUIRK, -0.30f,
            NatureFlavorHandler.Slot.MAJOR, -0.48f,
            3, 5, 220, 420,
            "petsplus.emotion_cue.taboo.scrappy.warn",
            "petsplus.emotion_cue.taboo.scrappy.panic"));
    }

    private NatureTabooHandler() {
    }

    private static void register(String path, TabooDefinition definition) {
        Identifier id = Identifier.of("petsplus", path);
        DEFAULT_TABOOS.put(id, definition);
        TABOOS.put(id, definition);
    }

    public static synchronized void reloadFromDatapack(Map<Identifier, TabooDefinition> overrides) {
        TABOOS.clear();
        TABOOS.putAll(DEFAULT_TABOOS);
        if (overrides != null && !overrides.isEmpty()) {
            TABOOS.putAll(overrides);
        }
    }

    public static void triggerForOwner(ServerPlayerEntity owner,
                                       double radius,
                                       TabooTrigger trigger,
                                       float intensity) {
        if (owner == null || !(owner.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        MoodService moodService = MoodService.getInstance();
        var stateManager = woflo.petsplus.state.StateManager.forWorld(world);
        var swarmIndex = stateManager.getSwarmIndex();

        double normalizedRadius = Math.max(0.0d, radius);
        boolean limit = normalizedRadius > 0.0d;
        Vec3d ownerPos = new Vec3d(owner.getX(), owner.getY(), owner.getZ());
        var ownerBox = owner.getBoundingBox();
        long now = world.getTime();
        boolean[] sawAny = new boolean[1];

        Consumer<PetSwarmIndex.SwarmEntry> dispatcher = entry -> {
            MobEntity pet = entry.pet();
            if (pet == null || !pet.isAlive()) {
                return;
            }
            if (!limit && !ownerBox.intersects(pet.getBoundingBox())) {
                return;
            }
            PetComponent component = entry.component();
            moodService.getStimulusBus().queueStimulus(pet, collector ->
                applyTabooStimulus(pet, component, world, owner, trigger, intensity, now));
            sawAny[0] = true;
        };

        if (limit) {
            swarmIndex.forEachPetInRange(owner, ownerPos, normalizedRadius, dispatcher);
            if (!sawAny[0]) {
                return;
            }
        } else {
            var snapshot = swarmIndex.snapshotOwner(owner.getUuid());
            if (snapshot.isEmpty()) {
                return;
            }
            for (var entry : snapshot) {
                dispatcher.accept(entry);
            }
            if (!sawAny[0]) {
                return;
            }
        }
    }

    public static void triggerForPet(MobEntity pet,
                                     PetComponent component,
                                     @Nullable ServerWorld world,
                                     @Nullable ServerPlayerEntity owner,
                                     TabooTrigger trigger,
                                     float intensity) {
        if (component == null) {
            return;
        }
        long now;
        if (world != null) {
            now = world.getTime();
        } else if (pet != null && pet.getEntityWorld() instanceof ServerWorld serverWorld) {
            now = serverWorld.getTime();
            world = serverWorld;
        } else {
            now = component.getStateData("__taboo_now", Long.class, 0L);
        }
        applyTabooStimulus(pet, component, world, owner, trigger, intensity, now);
    }

    private static void applyTabooStimulus(@Nullable MobEntity pet,
                                           PetComponent component,
                                           @Nullable ServerWorld world,
                                           @Nullable ServerPlayerEntity owner,
                                           TabooTrigger trigger,
                                           float intensity,
                                           long now) {
        if (component == null) {
            return;
        }
        Identifier natureId = component.getNatureId();
        if (natureId == null) {
            return;
        }

        TabooDefinition definition = resolveDefinition(component, natureId);
        if (definition == null || definition.trigger != trigger) {
            return;
        }

        if (intensity <= 0.0f) {
            intensity = 1.0f;
        }

        long lastTick = component.getStateData(lastTickKey(trigger), Long.class, Long.MIN_VALUE);
        int count = component.getStateData(countKey(trigger), Integer.class, 0);
        Stage stage = Stage.fromCode(component.getStateData(stageKey(trigger), Integer.class, 0));
        long cooldownEnd = component.getStateData(cooldownKey(trigger), Long.class, Long.MIN_VALUE);

        if (cooldownEnd != Long.MIN_VALUE && now < cooldownEnd && stage == Stage.PANIC) {
            // Still cooling down – just refresh timestamps and reschedule review
            component.setStateData(lastTickKey(trigger), now);
            component.setStateData(cooldownKey(trigger), cooldownEnd);
            scheduleReview(component, world, now, definition, trigger);
            return;
        }

        if (lastTick == Long.MIN_VALUE || now - lastTick > definition.accumulationWindow) {
            count = 0;
            stage = Stage.CALM;
        }

        count += Math.max(1, Math.round(intensity));
        component.setStateData(countKey(trigger), count);
        component.setStateData(lastTickKey(trigger), now);

        Stage newStage = stage;
        if (stage == Stage.CALM && count >= definition.warningThreshold) {
            newStage = Stage.WARNING;
            issueStageFeedback(pet, component, owner, definition.warningSlot, definition.warningScale * intensity,
                definition.warningCue, trigger, Stage.WARNING);
        }
        if (count >= definition.panicThreshold) {
            if (newStage != Stage.PANIC) {
                newStage = Stage.PANIC;
                issueStageFeedback(pet, component, owner, definition.panicSlot, definition.panicScale * intensity,
                    definition.panicCue, trigger, Stage.PANIC);
                long end = now + definition.cooldownTicks;
                component.setStateData(cooldownKey(trigger), end);
            } else {
                long end = now + definition.cooldownTicks;
                component.setStateData(cooldownKey(trigger), end);
            }
        }

        if (newStage != stage) {
            component.setStateData(stageKey(trigger), newStage.code());
        }

        scheduleReview(component, world, now, definition, trigger);
    }

    private static void issueStageFeedback(@Nullable MobEntity pet,
                                           PetComponent component,
                                           @Nullable ServerPlayerEntity owner,
                                           NatureFlavorHandler.Slot slot,
                                           float scale,
                                           @Nullable String cueKey,
                                           TabooTrigger trigger,
                                           Stage stage) {
        if (component == null) {
            return;
        }

        PetComponent.NatureEmotionProfile profile = component.getNatureEmotionProfile();
        if (profile != null) {
            PetComponent.Emotion emotion = emotionForSlot(profile, slot);
            float strength = strengthForSlot(profile, slot);
            if (emotion != null && strength > 0f && Math.abs(scale) > 0.001f) {
                component.pushEmotion(emotion, strength * scale);
            }
        }

        if (owner != null && cueKey != null && pet != null) {
            String cueId = "taboo." + trigger.name().toLowerCase(Locale.ROOT) + "." + pet.getUuidAsString() + "." + stage.name().toLowerCase(Locale.ROOT);
            EmotionContextCues.sendCue(owner, cueId, pet, Text.translatable(cueKey), 200);
        }

        String quirkKey = quirkKey(trigger);
        switch (stage) {
            case WARNING -> component.setQuirkCounter(quirkKey, 1);
            case PANIC -> {
                component.setQuirkCounter(quirkKey, 3);
                component.setPanicking(true);
            }
            default -> component.setQuirkCounter(quirkKey, 0);
        }
    }

    private static void scheduleReview(PetComponent component,
                                       @Nullable ServerWorld world,
                                       long now,
                                       TabooDefinition definition,
                                       TabooTrigger trigger) {
        long due = now + definition.accumulationWindow;
        if (world != null) {
            component.scheduleTabooReview(due);
        } else {
            component.scheduleTabooReview(due);
        }
    }

    public static void runScheduledReview(PetComponent component, ServerWorld world, long now) {
        if (component == null) {
            return;
        }
        Identifier natureId = component.getNatureId();
        if (natureId == null) {
            return;
        }
        TabooDefinition definition = resolveDefinition(component, natureId);
        if (definition == null) {
            return;
        }

        TabooTrigger trigger = definition.trigger;
        long lastTick = component.getStateData(lastTickKey(trigger), Long.class, Long.MIN_VALUE);
        int count = component.getStateData(countKey(trigger), Integer.class, 0);
        Stage stage = Stage.fromCode(component.getStateData(stageKey(trigger), Integer.class, 0));
        long cooldownEnd = component.getStateData(cooldownKey(trigger), Long.class, Long.MIN_VALUE);

        if (stage == Stage.CALM && count <= 0) {
            clearTabooState(component, trigger);
            return;
        }

        if (stage == Stage.PANIC && cooldownEnd != Long.MIN_VALUE && now < cooldownEnd) {
            component.scheduleTabooReview(cooldownEnd);
            return;
        }

        if (lastTick != Long.MIN_VALUE && now - lastTick < definition.accumulationWindow) {
            component.scheduleTabooReview(lastTick + definition.accumulationWindow);
            return;
        }

        if (stage == Stage.PANIC) {
            component.setStateData(stageKey(trigger), Stage.WARNING.code());
            component.setQuirkCounter(quirkKey(trigger), 1);
            component.setPanicking(false);
            component.scheduleTabooReview(now + definition.accumulationWindow);
            return;
        }

        clearTabooState(component, trigger);
    }

    private static void clearTabooState(PetComponent component, TabooTrigger trigger) {
        component.clearStateData(countKey(trigger));
        component.clearStateData(lastTickKey(trigger));
        component.clearStateData(stageKey(trigger));
        component.clearStateData(cooldownKey(trigger));
        component.setQuirkCounter(quirkKey(trigger), 0);
        component.setPanicking(false);
    }

    private static String countKey(TabooTrigger trigger) {
        return "nature_taboo_" + trigger.name().toLowerCase(Locale.ROOT) + "_count";
    }

    private static String lastTickKey(TabooTrigger trigger) {
        return "nature_taboo_" + trigger.name().toLowerCase(Locale.ROOT) + "_last";
    }

    private static String stageKey(TabooTrigger trigger) {
        return "nature_taboo_" + trigger.name().toLowerCase(Locale.ROOT) + "_stage";
    }

    private static String cooldownKey(TabooTrigger trigger) {
        return "nature_taboo_" + trigger.name().toLowerCase(Locale.ROOT) + "_cooldown";
    }

    private static String quirkKey(TabooTrigger trigger) {
        return "taboo:" + trigger.name().toLowerCase(Locale.ROOT);
    }

    private static @Nullable TabooDefinition resolveDefinition(PetComponent component, Identifier natureId) {
        TabooDefinition definition = TABOOS.get(natureId);
        if (definition != null) {
            return definition;
        }

        if (natureId.equals(AstrologyRegistry.LUNARIS_NATURE_ID)) {
            Identifier sign = component.getAstrologySignId();
            if (sign != null) {
                definition = TABOOS.get(sign);
                if (definition != null) {
                    return definition;
                }
            }
        }
        return null;
    }

    private static PetComponent.Emotion emotionForSlot(PetComponent.NatureEmotionProfile profile,
                                                       NatureFlavorHandler.Slot slot) {
        return switch (slot) {
            case MAJOR -> profile.majorEmotion();
            case MINOR -> profile.minorEmotion();
            case QUIRK -> profile.quirkEmotion();
        };
    }

    private static float strengthForSlot(PetComponent.NatureEmotionProfile profile,
                                         NatureFlavorHandler.Slot slot) {
        return switch (slot) {
            case MAJOR -> profile.majorStrength();
            case MINOR -> profile.minorStrength();
            case QUIRK -> profile.quirkStrength();
        };
    }

    public record TabooOverride(TabooTrigger trigger,
                                 NatureFlavorHandler.Slot warningSlot,
                                 float warningScale,
                                 NatureFlavorHandler.Slot panicSlot,
                                 float panicScale,
                                 int warningThreshold,
                                 int panicThreshold,
                                 long accumulationWindow,
                                 long cooldownTicks,
                                 @Nullable String warningCue,
                                 @Nullable String panicCue) {

        TabooDefinition toDefinition() {
            return new TabooDefinition(trigger, warningSlot, warningScale, panicSlot, panicScale,
                warningThreshold, panicThreshold, accumulationWindow, cooldownTicks,
                warningCue, panicCue);
        }
    }

    public static synchronized void reloadOverrides(Map<Identifier, TabooOverride> overrides) {
        TABOOS.clear();
        TABOOS.putAll(DEFAULT_TABOOS);
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        for (Map.Entry<Identifier, TabooOverride> entry : overrides.entrySet()) {
            Identifier id = entry.getKey();
            TabooOverride override = entry.getValue();
            if (id == null || override == null || override.trigger() == null) {
                continue;
            }
            TABOOS.put(id, override.toDefinition());
        }
    }
}
