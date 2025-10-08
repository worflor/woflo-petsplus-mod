package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.PetComponent;

/**
 * Effect that moves the Eclipsed void-save mechanic into the ability system.
 */
public class EclipsedVoidRescueEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eclipsed_void_rescue");

    private final int minimumLevel;
    private final int voidYThreshold;
    private final int cooldownTicks;
    private final double baseRescueRange;
    private final double rangePerLevel;

    public EclipsedVoidRescueEffect(JsonObject json) {
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 4));
        this.voidYThreshold = RegistryJsonHelper.getInt(json, "void_y_threshold", -64);
        this.cooldownTicks = Math.max(0, RegistryJsonHelper.getInt(json, "cooldown_ticks", 6000));
        this.baseRescueRange = Math.max(1.0D, RegistryJsonHelper.getDouble(json, "base_range", 128.0D));
        this.rangePerLevel = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "range_per_level", 32.0D));
    }

    public EclipsedVoidRescueEffect() {
        this.minimumLevel = 4;
        this.voidYThreshold = -64;
        this.cooldownTicks = 6000;
        this.baseRescueRange = 128.0D;
        this.rangePerLevel = 32.0D;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        DamageInterceptionResult interception = context.getDamageResult();
        if (interception == null || interception.isCancelled()) {
            return false;
        }
        if (!context.isLethalDamage() || !context.hasDamageContext()) {
            return false;
        }

        ServerWorld world = context.getEntityWorld();
        MobEntity pet = context.getPet();
        if (world == null || pet == null) {
            return false;
        }

        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }

        DamageSource damageSource = context.getIncomingDamageSource();
        if (damageSource == null || !isVoidDamage(damageSource)) {
            return false;
        }

        if (owner.getY() > voidYThreshold) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.isOwnedBy(owner) || !component.hasRole(PetRoleType.ECLIPSED)) {
            return false;
        }

        int petLevel = component.getLevel();
        if (petLevel < minimumLevel) {
            return false;
        }

        double rescueRange = computeRescueRange(petLevel);
        if (owner.squaredDistanceTo(pet) > rescueRange * rescueRange) {
            return false;
        }

        if (isOnCooldown(owner)) {
            return false;
        }

        List<MobEntity> candidates = collectRescueCandidates(owner, world, rescueRange);
        MobEntity bestCandidate = selectHighestLevelCandidate(candidates);
        if (bestCandidate == null || bestCandidate != pet) {
            return false;
        }

        try {
            if (!performVoidRescue(world, owner, pet, component)) {
                return false;
            }
        } catch (Exception exception) {
            Petsplus.LOGGER.error("Eclipsed void rescue failed for {}", owner.getName().getString(), exception);
            return false;
        }

        interception.cancel();
        return true;
    }

    private double computeRescueRange(int petLevel) {
        return baseRescueRange + (petLevel * rangePerLevel);
    }

    private static boolean isVoidDamage(DamageSource damageSource) {
        String name = damageSource.getName();
        return "outOfWorld".equals(name) || "fell.out.of.world".equals(name) || "generic.kill".equals(name);
    }

    private boolean isOnCooldown(ServerPlayerEntity owner) {
        if (!owner.hasStatusEffect(StatusEffects.WEAKNESS)) {
            return false;
        }
        StatusEffectInstance instance = owner.getStatusEffect(StatusEffects.WEAKNESS);
        return instance != null && instance.getDuration() > cooldownTicks - 200;
    }

    private List<MobEntity> collectRescueCandidates(ServerPlayerEntity owner, ServerWorld world, double range) {
        return world.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(range),
            entity -> {
                PetComponent comp = PetComponent.get(entity);
                return comp != null &&
                    comp.hasRole(PetRoleType.ECLIPSED) &&
                    comp.isOwnedBy(owner) &&
                    entity.isAlive() &&
                    comp.getLevel() >= minimumLevel;
            }
        );
    }

    private MobEntity selectHighestLevelCandidate(List<MobEntity> candidates) {
        MobEntity best = null;
        int highestLevel = Integer.MIN_VALUE;

        for (MobEntity candidate : candidates) {
            PetComponent comp = PetComponent.get(candidate);
            if (comp == null) {
                continue;
            }

            int level = comp.getLevel();
            if (level > highestLevel) {
                best = candidate;
                highestLevel = level;
            }
        }

        return best;
    }

    private boolean performVoidRescue(ServerWorld world, ServerPlayerEntity owner, MobEntity pet, PetComponent component) {
        BlockPos safeLocation = findSafeTeleportLocation(world, component.getLevel());
        if (safeLocation == null) {
            return false;
        }

        teleportToSafety(owner, world, safeLocation);
        applyVoidSaveEffects(owner, component.getLevel());
        applyVoidSaveCooldown(owner);
        applyPetExhaustion(pet, component);
        playVoidSaveFeedback(owner, pet, safeLocation);
        return true;
    }

    private BlockPos findSafeTeleportLocation(ServerWorld world, int petLevel) {
        BlockPos worldSpawn = world.getSpawnPoint().getPos();
        if (isSafeLocation(world, worldSpawn)) {
            return worldSpawn;
        }

        int searchRadius = Math.min(50, 10 + (petLevel * 5));
        for (int attempts = 0; attempts < 20; attempts++) {
            int x = worldSpawn.getX() + (world.getRandom().nextInt(searchRadius * 2) - searchRadius);
            int z = worldSpawn.getZ() + (world.getRandom().nextInt(searchRadius * 2) - searchRadius);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);

            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeLocation(world, candidate)) {
                return candidate;
            }
        }

        return worldSpawn;
    }

    private boolean isSafeLocation(ServerWorld world, BlockPos pos) {
        if (pos.getY() < world.getBottomY()) {
            return false;
        }

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (pos.getY() > topY) {
            return false;
        }

        BlockPos groundPos = pos.down();
        BlockPos airPos1 = pos;
        BlockPos airPos2 = pos.up();

        return world.getBlockState(groundPos).isSolidBlock(world, groundPos)
            && world.getBlockState(airPos1).isAir()
            && world.getBlockState(airPos2).isAir();
    }

    private void teleportToSafety(ServerPlayerEntity owner, ServerWorld world, BlockPos safeLocation) {
        owner.teleport(world,
            safeLocation.getX() + 0.5D,
            safeLocation.getY(),
            safeLocation.getZ() + 0.5D,
            Set.of(),
            owner.getYaw(),
            owner.getPitch(),
            false);

        owner.setVelocity(Vec3d.ZERO);
        owner.fallDistance = 0.0F;
    }

    private void applyVoidSaveEffects(ServerPlayerEntity owner, int petLevel) {
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0));
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 0));
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0));
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));

        if (petLevel >= 6) {
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 300, 0));
        }
    }

    private void applyVoidSaveCooldown(ServerPlayerEntity owner) {
        if (cooldownTicks <= 0) {
            return;
        }

        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, cooldownTicks, 0));
        owner.sendMessage(Text.of("§8You cannot be void-saved again for 5 minutes."), false);
    }

    private void applyPetExhaustion(MobEntity pet, PetComponent component) {
        int exhaustionLevel = Math.max(0, 7 - component.getLevel());

        if (exhaustionLevel > 0) {
            pet.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1200, exhaustionLevel - 1));
            pet.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 800, 0));
        }

        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0));
    }

    private void playVoidSaveFeedback(ServerPlayerEntity owner, MobEntity pet, BlockPos safeLocation) {
        String petName = pet.hasCustomName() ?
            pet.getCustomName().getString() :
            pet.getType().getName().getString();

        owner.getEntityWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.ENTITY_SHULKER_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.6F);

        owner.sendMessage(Text.of("§8" + petName + " §5pulled you from the void through shadow magic!"), false);
        owner.sendMessage(Text.of("§8✦ §5Saved from the void §8✦"), true);
        owner.sendMessage(Text.of("§8Teleported to safety at: §7" + safeLocation.getX() + ", " + safeLocation.getY() + ", " + safeLocation.getZ()), false);
    }
}


