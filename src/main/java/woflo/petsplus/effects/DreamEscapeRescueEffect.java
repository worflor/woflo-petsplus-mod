package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity.Respawn;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.advancement.criteria.PetInteractionCriterion;
import woflo.petsplus.history.HistoryEvent;
import woflo.petsplus.history.HistoryManager;
import woflo.petsplus.roles.eepyeeper.EepyEeperCore;
import woflo.petsplus.state.PetComponent;

/**
 * Effect that ports Dream's Escape into the ability system. When a qualifying
 * Eepy Eeper pet intercepts lethal owner damage, the effect teleports the owner
 * to safety, wipes their XP, and sacrifices the pet with dramatic feedback.
 */
public class DreamEscapeRescueEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eepy_dream_escape_rescue");

    private final double activationRadiusSq;
    private final int blindnessDurationTicks;

    public DreamEscapeRescueEffect(JsonObject json) {
        double radius = RegistryJsonHelper.getDouble(json, "activation_radius", 8.0D);
        this.activationRadiusSq = Math.max(1.0D, radius) * Math.max(1.0D, radius);
        this.blindnessDurationTicks = Math.max(0, RegistryJsonHelper.getInt(json, "blindness_duration_ticks", 600));
    }

    public DreamEscapeRescueEffect() {
        this.activationRadiusSq = 64.0D;
        this.blindnessDurationTicks = 600;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        DamageInterceptionResult interception = context.getDamageResult();
        if (interception == null) {
            return false;
        }
        if (!context.isLethalDamage() || !context.hasDamageContext()) {
            return false;
        }

        ServerWorld world = context.getWorld();
        MobEntity pet = context.getPet();
        if (!(context.getOwner() instanceof ServerPlayerEntity owner) || world == null || pet == null) {
            return false;
        }

        if (pet.isRemoved() || !pet.isAlive()) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.isOwnedBy(owner)) {
            return false;
        }

        if (component.getLevel() < 30) {
            return false;
        }

        if (EepyEeperCore.isPetKnockedOut(pet.getUuid())) {
            return false;
        }

        if (!EepyEeperCore.canUseDreamEscape(owner)) {
            return false;
        }

        if (owner.squaredDistanceTo(pet) > activationRadiusSq) {
            return false;
        }

        try {
            performRescue(world, owner, pet, interception);
            return true;
        } catch (Exception exception) {
            Petsplus.LOGGER.error("Dream's Escape effect failed for {}", owner.getName().getString(), exception);
            return false;
        }
    }

    private void performRescue(ServerWorld world,
                                ServerPlayerEntity owner,
                                MobEntity pet,
                                DamageInterceptionResult interception) {
        interception.cancel();

        owner.setHealth(1.0F);
        if (blindnessDurationTicks > 0) {
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, blindnessDurationTicks, 0));
        }
        owner.totalExperience = 0;
        owner.experienceLevel = 0;
        owner.experienceProgress = 0.0F;

        teleportOwner(world, owner);
        recordHistory(owner, pet);
        createRescueFeedback(world, owner, pet);
        sacrificePet(world, owner, pet);
    }

    private static void teleportOwner(ServerWorld world, ServerPlayerEntity owner) {
        Respawn respawn = owner.getRespawn();
        if (respawn != null) {
            BlockPos pos = respawn.pos();
            owner.teleport(world, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                Set.of(), respawn.angle(), 0.0F, false);
            return;
        }
        BlockPos spawn = world.getSpawnPos();
        owner.teleport(world, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
            Set.of(), owner.getYaw(), owner.getPitch(), false);
    }

    private static void recordHistory(ServerPlayerEntity owner, MobEntity pet) {
        HistoryManager.recordDreamEscape(pet, owner);
        HistoryManager.recordPetSacrifice(pet, owner, 1.0F);

        PetComponent component = PetComponent.get(pet);
        if (component != null) {
            long dreamEscapes = component.getAchievementCount(HistoryEvent.AchievementType.DREAM_ESCAPE, owner.getUuid());
            long sacrifices = component.getAchievementCount(HistoryEvent.AchievementType.PET_SACRIFICE, owner.getUuid());
        AdvancementCriteriaRegistry.PET_INTERACTION.trigger(
            owner,
            PetInteractionCriterion.INTERACTION_DREAM_ESCAPE,
            (int) dreamEscapes
        );
        AdvancementCriteriaRegistry.PET_INTERACTION.trigger(
            owner,
            PetInteractionCriterion.INTERACTION_PET_SACRIFICE,
            (int) sacrifices
        );
        }
    }

    private static void createRescueFeedback(ServerWorld world, ServerPlayerEntity owner, MobEntity pet) {
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.BLOCK_CHORUS_FLOWER_GROW, SoundCategory.PLAYERS, 0.6F, 1.2F);
        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 0.4F, 1.4F);

        for (int i = 0; i < 18; i++) {
            double ox = (world.random.nextDouble() - 0.5D) * 1.6D;
            double oy = world.random.nextDouble() * 1.2D;
            double oz = (world.random.nextDouble() - 0.5D) * 1.6D;
            world.spawnParticles(ParticleTypes.GLOW,
                owner.getX() + ox,
                owner.getY() + 0.8D + oy,
                owner.getZ() + oz,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
        }

        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            pet.getX(), pet.getY() + 0.7D, pet.getZ(),
            8, 0.4D, 0.25D, 0.4D, 0.012D);
    }

    private static void sacrificePet(ServerWorld world, ServerPlayerEntity owner, MobEntity pet) {
        EepyEeperCore.clearKnockout(pet.getUuid());

        String petName = pet.hasCustomName()
            ? pet.getCustomName().getString()
            : pet.getType().getName().getString();

        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(),
            SoundEvents.ENTITY_PHANTOM_BITE, SoundCategory.NEUTRAL, 0.6F, 0.8F);
        world.playSound(null, pet.getX(), pet.getY(), pet.getZ(),
            SoundEvents.ENTITY_ALLAY_DEATH, SoundCategory.NEUTRAL, 0.7F, 0.9F);

        pet.damage(world, pet.getDamageSources().magic(), Float.MAX_VALUE);
        owner.sendMessage(Text.translatable("petsplus.eepyeeper.dream_sacrifice", petName), false);
    }
}
