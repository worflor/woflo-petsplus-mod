package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.ParticleTypes;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.ui.FeedbackManager;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Launches the owner upward in a controlled gust, optionally lifting their mount and pet
 * while layering slow-fall protection so the escape feels smooth and reliable.
 */
public class SkyriderGustUpwardsEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "skyrider_gust_upwards");

    private final double verticalBoost;
    private final double forwardPush;
    private final double petLiftScale;
    private final int ownerSlowFallTicks;
    private final int petSlowFallTicks;
    private final boolean includePet;
    private final boolean includeMount;
    private final boolean swingOwner;
    private final boolean playFeedback;

    public SkyriderGustUpwardsEffect(JsonObject json) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier skyriderId = PetRoleType.SKYRIDER.id();

        this.verticalBoost = Math.max(0.2, RegistryJsonHelper.getDouble(json, "vertical_boost",
            config.getRoleDouble(skyriderId, "gustVerticalBoost", 1.0)));
        this.forwardPush = Math.max(0.0, RegistryJsonHelper.getDouble(json, "forward_push",
            config.getRoleDouble(skyriderId, "gustForwardPush", 0.25)));
        this.petLiftScale = MathHelper.clamp(RegistryJsonHelper.getDouble(json, "pet_lift_scale",
            config.getRoleDouble(skyriderId, "gustPetLiftScale", 0.65)), 0.0, 1.5);
        this.ownerSlowFallTicks = Math.max(0, RegistryJsonHelper.getInt(json, "owner_slowfall_ticks",
            config.getRoleInt(skyriderId, "gustOwnerSlowfallTicks", 100)));
        this.petSlowFallTicks = Math.max(0, RegistryJsonHelper.getInt(json, "pet_slowfall_ticks",
            config.getRoleInt(skyriderId, "gustPetSlowfallTicks", 80)));
        this.includePet = RegistryJsonHelper.getBoolean(json, "lift_pet", true);
        this.includeMount = RegistryJsonHelper.getBoolean(json, "mount_inherit", true);
        this.swingOwner = RegistryJsonHelper.getBoolean(json, "swing_owner", true);
        this.playFeedback = RegistryJsonHelper.getBoolean(json, "play_feedback", true);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        ServerWorld world = context.getWorld();
        MobEntity pet = context.getPet();

        boolean triggered = applyLift(serverOwner, verticalBoost, forwardPush, ownerSlowFallTicks, true);

        if (includeMount) {
            triggered |= applyMountLift(owner.getVehicle(), serverOwner, verticalBoost * 0.85,
                forwardPush * 0.8, ownerSlowFallTicks);
        }

        if (includePet && pet != null && pet.getWorld() == world) {
            triggered |= applyLift(pet, Math.max(0.0, verticalBoost * petLiftScale),
                forwardPush * 0.5, petSlowFallTicks, true);
        }

        if (!triggered) {
            return false;
        }

        if (playFeedback) {
            FeedbackManager.emitRoleAbility(PetRoleType.SKYRIDER.id(), "gust", owner, world);
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_PARROT_FLY, SoundCategory.PLAYERS, 0.8f, 1.4f);
        }

        if (swingOwner) {
            owner.swingHand(Hand.MAIN_HAND, true);
        }

        UIFeedbackManager.sendSkyriderGustMessage(serverOwner, pet, ownerSlowFallTicks / 20);
        return true;
    }

    private boolean applyLift(LivingEntity living, double vertical, double forward, int slowFallTicks, boolean applySlowFall) {
        if (!living.isAlive()) {
            return false;
        }

        Vec3d baseVelocity = living.getVelocity();
        boolean hasSpace = hasLaunchSpace(living, vertical);

        Vec3d horizontal = computeHorizontalImpulse(living, forward, !hasSpace);

        double desiredVertical = Math.max(0.0, vertical);
        if (!hasSpace) {
            desiredVertical *= 0.45;
        }

        double boostedY = Math.max(baseVelocity.y + desiredVertical, desiredVertical * 0.85);
        Vec3d finalVelocity = new Vec3d(
            baseVelocity.x + horizontal.x,
            boostedY,
            baseVelocity.z + horizontal.z
        );

        living.setVelocity(finalVelocity);
        living.velocityModified = true;
        living.fallDistance = 0.0f;

        if (applySlowFall && slowFallTicks > 0) {
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,
                slowFallTicks, hasSpace ? 0 : 1, true, true, true));
        }

        spawnGustParticles(living, hasSpace);
        return true;
    }

    private boolean applyMountLift(@Nullable Entity vehicle, LivingEntity rider, double vertical, double forward, int slowFallTicks) {
        if (vehicle == null) {
            return false;
        }

        if (vehicle instanceof LivingEntity livingVehicle) {
            return applyLift(livingVehicle, vertical, forward, slowFallTicks, true);
        }

        if (vehicle instanceof BoatEntity || vehicle instanceof AbstractMinecartEntity) {
            Vec3d velocity = vehicle.getVelocity();
            Vec3d horizontal = computeHorizontalImpulse(rider, forward, true);
            double boostedY = Math.max(velocity.y + Math.max(0.0, vertical), Math.max(0.0, vertical) * 0.85);
            vehicle.setVelocity(velocity.x + horizontal.x, boostedY, velocity.z + horizontal.z);
            vehicle.velocityModified = true;
            spawnGustParticles(vehicle, true);
            return true;
        }

        return false;
    }

    private Vec3d computeHorizontalImpulse(@Nullable LivingEntity basis, double forward, boolean addExtraDrift) {
        double magnitude = Math.max(0.0, forward);
        if (basis == null || magnitude <= 0.0) {
            return Vec3d.ZERO;
        }

        Vec3d facing = basis.getRotationVector();
        Vec3d horizontal = new Vec3d(facing.x, 0.0, facing.z);

        if (horizontal.lengthSquared() < 1.0E-4) {
            Vec3d movement = basis.getVelocity().multiply(1.0, 0.0, 1.0);
            if (movement.lengthSquared() > 1.0E-4) {
                horizontal = movement;
            } else {
                Direction fallback = basis.getHorizontalFacing();
                horizontal = Vec3d.of(fallback.getVector());
            }
        }

        double lengthSq = horizontal.lengthSquared();
        if (lengthSq < 1.0E-4) {
            return Vec3d.ZERO;
        }

        horizontal = horizontal.normalize();
        if (addExtraDrift) {
            magnitude *= 1.2;
        }

        return horizontal.multiply(magnitude);
    }

    private boolean hasLaunchSpace(LivingEntity living, double vertical) {
        double height = MathHelper.clamp(vertical + living.getHeight() * 0.5, 0.5, 4.0);
        Box box = living.getBoundingBox().stretch(0.25, height, 0.25).offset(0.0, 0.05, 0.0);
        return living.getWorld().isSpaceEmpty(living, box);
    }

    private void spawnGustParticles(Entity entity, boolean verticalLaunch) {
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Vec3d pos = entity.getPos();
        double baseY = pos.y + entity.getStandingEyeHeight() * 0.4;
        double swirlRadius = 0.4 + entity.getWidth() * 0.3;

        for (int i = 0; i < 8; i++) {
            double angle = entity.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = swirlRadius * (0.8 + entity.getRandom().nextDouble() * 0.4);
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            serverWorld.spawnParticles(ParticleTypes.END_ROD,
                pos.x + offsetX,
                baseY + entity.getRandom().nextDouble() * 0.6,
                pos.z + offsetZ,
                1, 0.0, verticalLaunch ? 0.1 : 0.0, 0.0, 0.02);
        }

        int cloudCount = verticalLaunch ? 10 : 6;
        serverWorld.spawnParticles(ParticleTypes.CLOUD,
            pos.x, pos.y + 0.1, pos.z,
            cloudCount, 0.35, 0.2, 0.35, 0.03);
    }

}
