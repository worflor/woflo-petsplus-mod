package woflo.petsplus.mood.storm;

import net.minecraft.server.command.CommandManager;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.mood.MoodStormEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.List;

/**
 * Registers the default behaviour for mood storms including ambient feedback and
 * datapack hook execution.
 */
public final class MoodStormHooks {
    private static boolean initialized;

    private MoodStormHooks() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        MoodStormRegistry.initialize();
        MoodStormEvent.EVENT.register(MoodStormHooks::handleMoodStorm);
    }

    private static void handleMoodStorm(MoodStormEvent.Context context) {
        ServerWorld world = context.world();
        if (world == null) {
            return;
        }
        Vec3d center = context.center();
        MoodStormDefinition definition = context.definition();
        PetComponent.Mood mood = context.mood();
        PetComponent.Emotion emotion = definition != null && definition.emotionOverride() != null
            ? definition.emotionOverride()
            : defaultEmotion(mood);

        spawnParticles(world, center, context.particleRadius(), context.particleCount(), context.particleSpeed(), definition, mood);
        playAmbientSound(world, center, definition, mood);

        if (emotion != null && context.pushAmount() > 0f) {
            for (PetSwarmIndex.SwarmEntry entry : context.pushTargets()) {
                if (entry == null) {
                    continue;
                }
                var pet = entry.pet();
                if (pet != null) {
                    MoodService.getInstance().pushEmotion(pet, emotion, context.pushAmount());
                }
            }
        }

        if (definition != null) {
            runFunctions(world, context.owner(), center, definition.rewardFunctions());
            runFunctions(world, context.owner(), center, definition.penaltyFunctions());
        }
    }

    private static void spawnParticles(ServerWorld world,
                                       Vec3d center,
                                       double radius,
                                       int count,
                                       double speed,
                                       @Nullable MoodStormDefinition definition,
                                       PetComponent.Mood mood) {
        ParticleEffect effect = resolveParticle(definition, mood);
        if (effect == null || count <= 0) {
            return;
        }
        double spread = Math.max(0.25D, radius);
        world.spawnParticles(effect, center.x, center.y, center.z, count, spread, spread * 0.5D, spread, speed);
    }

    private static ParticleEffect resolveParticle(@Nullable MoodStormDefinition definition, PetComponent.Mood mood) {
        if (definition != null && definition.particleId() != null) {
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(definition.particleId());
            if (type instanceof ParticleEffect effect) {
                return effect;
            }
            if (type instanceof SimpleParticleType defaultType) {
                return defaultType;
            }
            Petsplus.LOGGER.warn("Mood storm particle {} requires parameters; falling back to default for mood {}",
                definition.particleId(), mood);
        }
        return createMoodParticle(mood);
    }

    private static ParticleEffect createMoodParticle(PetComponent.Mood mood) {
        Formatting formatting = mood.primaryFormatting;
        Integer colorValue = formatting != null ? formatting.getColorValue() : null;
        int color = colorValue != null ? colorValue : 0xFFFFFF;
        return new DustParticleEffect(color, 1.0f);
    }

    private static void playAmbientSound(ServerWorld world,
                                          Vec3d center,
                                          @Nullable MoodStormDefinition definition,
                                          PetComponent.Mood mood) {
        SoundEvent sound = resolveSound(definition, mood);
        if (sound == null) {
            return;
        }
        world.playSound(null, center.x, center.y, center.z, sound, SoundCategory.AMBIENT, 1.5f, 1.0f);
    }

    @Nullable
    private static SoundEvent resolveSound(@Nullable MoodStormDefinition definition, PetComponent.Mood mood) {
        if (definition != null && definition.ambientSoundId() != null) {
            SoundEvent sound = Registries.SOUND_EVENT.get(definition.ambientSoundId());
            if (sound != null) {
                return sound;
            }
            Petsplus.LOGGER.warn("Unknown ambient sound {} configured for mood storm mood {}",
                definition.ambientSoundId(), mood);
        }
        return defaultSound(mood);
    }

    @Nullable
    private static SoundEvent defaultSound(PetComponent.Mood mood) {
        return switch (mood) {
            case HAPPY, PLAYFUL -> sound("block.amethyst_block.chime");
            case CURIOUS -> sound("entity.fox.sniff");
            case BONDED -> sound("entity.villager.celebrate");
            case CALM -> sound("ambient.warped_forest.mood");
            case PASSIONATE -> sound("music_disc.cat");
            case YUGEN -> sound("ambient.soul_sand_valley.mood");
            case FOCUSED -> sound("block.enchantment_table.use");
            case SISU -> sound("item.shield.block");
            case SAUDADE -> sound("music_disc.far");
            case PROTECTIVE -> sound("item.shield.block");
            case RESTLESS -> sound("entity.phantom.ambient");
            case AFRAID -> sound("entity.cat.hiss");
            case ANGRY -> sound("entity.blaze.shoot");
            case ECHOED_RESONANCE -> sound("ambient.cave");
            case ARCANE_OVERFLOW -> sound("block.beacon.ambient");
            case PACK_SPIRIT -> sound("entity.warden.roar");
        };
    }

    private static SoundEvent sound(String path) {
        return Registries.SOUND_EVENT.get(Identifier.of("minecraft", path));
    }

    @Nullable
    private static PetComponent.Emotion defaultEmotion(PetComponent.Mood mood) {
        return switch (mood) {
            case HAPPY -> PetComponent.Emotion.CHEERFUL;
            case PLAYFUL -> PetComponent.Emotion.GLEE;
            case CURIOUS -> PetComponent.Emotion.CURIOUS;
            case BONDED -> PetComponent.Emotion.UBUNTU;
            case CALM -> PetComponent.Emotion.LAGOM;
            case PASSIONATE -> PetComponent.Emotion.KEFI;
            case YUGEN -> PetComponent.Emotion.YUGEN;
            case FOCUSED -> PetComponent.Emotion.FOCUSED;
            case SISU -> PetComponent.Emotion.SISU;
            case SAUDADE -> PetComponent.Emotion.SAUDADE;
            case PROTECTIVE -> PetComponent.Emotion.PROTECTIVE;
            case RESTLESS -> PetComponent.Emotion.RESTLESS;
            case AFRAID -> PetComponent.Emotion.STARTLE;
            case ANGRY -> PetComponent.Emotion.FRUSTRATION;
            case ECHOED_RESONANCE -> PetComponent.Emotion.ECHOED_RESONANCE;
            case ARCANE_OVERFLOW -> PetComponent.Emotion.ARCANE_OVERFLOW;
            case PACK_SPIRIT -> PetComponent.Emotion.PACK_SPIRIT;
        };
    }

    private static void runFunctions(ServerWorld world,
                                     @Nullable ServerPlayerEntity owner,
                                     Vec3d center,
                                     List<Identifier> functions) {
        if (functions == null || functions.isEmpty()) {
            return;
        }
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }
        ServerCommandSource source = owner != null
            ? owner.getCommandSource().withWorld(world).withPosition(center).withSilent()
            : server.getCommandSource().withWorld(world).withPosition(center).withSilent();
        CommandManager manager = server.getCommandManager();
        for (Identifier functionId : functions) {
            try {
                manager.executeWithPrefix(source, "function " + functionId);
            } catch (Exception e) {
                Petsplus.LOGGER.error("Failed to execute mood storm function {}", functionId, e);
            }
        }
    }
}
