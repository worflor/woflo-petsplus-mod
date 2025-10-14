package woflo.petsplus.ai.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.events.EmotionContextCues;
import net.minecraft.entity.ai.goal.Goal;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passive, cozy "snuggle" channel that very slowly regenerates the pet (and optionally the owner)
 * while the crouch cuddle proximity channel is active. Balanced by strict preconditions and
 * cooldowns so it feels like a camp-side comfort, not a combat heal.
 */
public class PetSnuggleGoal extends Goal {
    // Tuning
    private static final double MAX_DISTANCE_SQ = 2.5 * 2.5; // must be very close
    private static final double PACK_REQUIRED_RADIUS = 6.0; // require another pet within 6 blocks
    private static final long MIN_SAFE_SINCE_COMBAT_TICKS = 100; // ~5s
    private static final int HEAL_PULSE_INTERVAL_TICKS = 80; // 4s between pulses
    private static final int SESSION_MAX_DURATION_TICKS = 20 * 20; // cap channel to ~20s per session

    private static final int OWNER_HEAL_COOLDOWN_TICKS = 1200; // 60s per owner (shared across pets)
    private static final int SESSION_COOLDOWN_TICKS = 2400; // 2 minutes per pet after a session

    private static final float PET_HEAL_LVL_LT15 = 1.0f; // 0.5 heart
    private static final float PET_HEAL_LVL_GE15 = 2.0f; // 1 heart
    private static final float OWNER_HEAL_AMOUNT = 1.0f; // 0.5 heart for owner

    // Shared cooldown registries
    private static final Map<UUID, Long> OWNER_NEXT_HEAL_TICK = new ConcurrentHashMap<>();
    private static final Map<String, Long> PAIR_NEXT_HEAL_TICK = new ConcurrentHashMap<>(); // owner|pet -> tick

    private final MobEntity pet;
    private final PetComponent component;

    @Nullable private ServerPlayerEntity owner;
    private long lastPulseTick;
    private long startTick;

    public PetSnuggleGoal(MobEntity pet, PetComponent component) {
        this.pet = pet;
        this.component = component;
        // Intentionally do not claim any controls so this goal can run alongside the hover goal
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }
        ServerPlayerEntity resolved = resolveActiveOwner(world);
        if (resolved == null) {
            return false;
        }

        long now = world.getTime();
        Long cooldownUntil = component.getStateData(PetComponent.StateKeys.SNUGGLE_COOLDOWN_UNTIL_TICK, Long.class);
        if (cooldownUntil != null && now < cooldownUntil) {
            return false;
        }

        // Require calm state: no recent attacks
        if ((now - Math.max(0L, component.getLastAttackTick())) < MIN_SAFE_SINCE_COMBAT_TICKS) {
            return false;
        }

        // Require pet to benefit (below max) or owner to benefit, but prefer pet HP gate
        if (pet.getHealth() >= pet.getMaxHealth() * 0.98f) {
            // Allow starting only if owner is missing a bit of health and owner cooldown allows soon
            if (resolved.getHealth() >= resolved.getMaxHealth() * 0.98f) {
                return false;
            }
        }

        // Require at least one other owned pet nearby (low-key more pets requirement)
        if (!hasNearbyPackMate(world, resolved)) {
            return false;
        }

        // Range check (tight cuddle distance)
        if (pet.squaredDistanceTo(resolved) > MAX_DISTANCE_SQ) {
            return false;
        }

        this.owner = resolved;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }
        ServerPlayerEntity o = owner;
        if (o == null || o.isRemoved() || !o.isAlive() || o.getEntityWorld() != world) {
            return false;
        }

        long now = world.getTime();
        // Session cap to avoid indefinite channels
        if (now - startTick > SESSION_MAX_DURATION_TICKS) {
            return false;
        }

        if (pet.isRemoved() || !pet.isAlive()) {
            return false;
        }
        // Must still be in crouch cuddle window and close
        if (!component.isCrouchCuddleActiveWith(o, now)) {
            return false;
        }
        if (pet.squaredDistanceTo(o) > MAX_DISTANCE_SQ) {
            return false;
        }

        // Still calm
        if ((now - Math.max(0L, component.getLastAttackTick())) < MIN_SAFE_SINCE_COMBAT_TICKS) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        startTick = world.getTime();
        lastPulseTick = startTick; // first heal pulse comes after interval
        component.setStateData(PetComponent.StateKeys.SNUGGLE_LAST_START_TICK, startTick);

        // Subtle cue
        ServerPlayerEntity o = owner;
        if (o != null) {
            EmotionContextCues.sendCue(o,
                "snuggle.start." + pet.getUuidAsString(),
                pet,
                net.minecraft.text.Text.translatable("petsplus.snuggle.start", pet.getDisplayName()),
                200);
        }
    }

    @Override
    public void stop() {
        if (pet.getEntityWorld() instanceof ServerWorld world) {
            long now = world.getTime();
            long until = now + SESSION_COOLDOWN_TICKS;
            component.setStateData(PetComponent.StateKeys.SNUGGLE_COOLDOWN_UNTIL_TICK, until);
        }
        owner = null;
    }

    @Override
    public void tick() {
        if (!(pet.getEntityWorld() instanceof ServerWorld world) || owner == null) {
            return;
        }
        long now = world.getTime();
        if ((now - lastPulseTick) < HEAL_PULSE_INTERVAL_TICKS) {
            return;
        }
        lastPulseTick = now;

        ServerPlayerEntity o = owner;
        if (o == null) return;

        // Healing cadence: pet always, owner only if eligible
        float petHeal = component.getLevel() >= 15 ? PET_HEAL_LVL_GE15 : PET_HEAL_LVL_LT15;
        applySafeHeal(pet, petHeal);

        // Owner shared cooldown across all pets
        if (canOwnerReceiveHeal(o, now)) {
            applySafeHeal(o, OWNER_HEAL_AMOUNT);
            OWNER_NEXT_HEAL_TICK.put(o.getUuid(), now + OWNER_HEAL_COOLDOWN_TICKS);
        }

        // Pair anti-spam: ensure pair interval between pulses
        String pairKey = pairKey(o.getUuid(), pet.getUuid());
        Long next = PAIR_NEXT_HEAL_TICK.get(pairKey);
        if (next != null && now < next) {
            return;
        }
        PAIR_NEXT_HEAL_TICK.put(pairKey, now + HEAL_PULSE_INTERVAL_TICKS);

        // Emotions – cozy, bonded
        float packFactor = 1.0f + 0.25f * MathHelper.clamp(nearbyAllyCount(world, o) - 1, 0, 3);
        component.pushEmotion(PetComponent.Emotion.CONTENT, 0.06f * packFactor);
        component.pushEmotion(PetComponent.Emotion.UBUNTU, 0.05f * packFactor);

        // Soft ambient feedback
        emitAmbient(world, pet.getEntityPos());
    }

    private boolean hasNearbyPackMate(ServerWorld world, ServerPlayerEntity owner) {
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) return false;
        PetSwarmIndex index = manager.getSwarmIndex();
        final Vec3d center = owner.getEntityPos();
        final int[] count = {0};
        index.forEachPetInRange(owner, center, PACK_REQUIRED_RADIUS, entry -> {
            if (entry == null) return;
            MobEntity other = entry.pet();
            if (other == null || other == pet || other.isRemoved() || !other.isAlive()) return;
            PetComponent c = entry.component();
            if (c != null && c.isOwnedBy(owner)) {
                count[0]++;
            }
        });
        return count[0] > 0;
    }

    private int nearbyAllyCount(ServerWorld world, ServerPlayerEntity owner) {
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) return 1;
        PetSwarmIndex index = manager.getSwarmIndex();
        final Vec3d center = owner.getEntityPos();
        final int[] count = {1}; // include self
        index.forEachPetInRange(owner, center, PACK_REQUIRED_RADIUS, entry -> {
            if (entry == null) return;
            MobEntity other = entry.pet();
            if (other == null || other.isRemoved() || !other.isAlive()) return;
            PetComponent c = entry.component();
            if (c != null && c.isOwnedBy(owner)) {
                count[0]++;
            }
        });
        return count[0];
    }

    private boolean canOwnerReceiveHeal(ServerPlayerEntity owner, long now) {
        Long gate = OWNER_NEXT_HEAL_TICK.get(owner.getUuid());
        if (gate != null && now < gate) return false;
        return owner.getHealth() < owner.getMaxHealth();
    }

    private static void applySafeHeal(LivingEntity entity, float amount) {
        if (entity == null || amount <= 0) return;
        try {
            entity.heal(amount);
        } catch (Throwable t) {
            // Fallback to clamped setHealth if something blocked heal()
            float newHp = Math.min(entity.getMaxHealth(), entity.getHealth() + amount);
            entity.setHealth(newHp);
        }
    }

    private static void emitAmbient(ServerWorld world, Vec3d pos) {
        try {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.15f, 0.9f + world.random.nextFloat() * 0.05f);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART, pos.x, pos.y + 0.6, pos.z, 1, 0.2, 0.2, 0.2, 0.0);
        } catch (Throwable ignored) {}
    }

    @Nullable
    private ServerPlayerEntity resolveActiveOwner(ServerWorld world) {
        @Nullable java.util.UUID ownerId = component.getOwnerUuid();
        if (ownerId == null) return null;
        ServerPlayerEntity maybe = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (maybe == null || maybe.isRemoved() || !maybe.isAlive() || maybe.isSpectator()) return null;

        long now = world.getTime();
        if (!component.isCrouchCuddleActiveWith(maybe, now)) return null;
        if (!maybe.isSneaking()) return null;
        return maybe;
    }

    private static String pairKey(UUID owner, UUID pet) {
        return owner.toString() + "|" + pet.toString();
    }
}

