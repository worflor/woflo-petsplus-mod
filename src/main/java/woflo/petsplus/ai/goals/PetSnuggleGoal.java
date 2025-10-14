package woflo.petsplus.ai.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.events.EmotionContextCues;
import net.minecraft.entity.ai.goal.Goal;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passive, cozy "snuggle" channel that very slowly regenerates a pair of pets that settle in
 * together out of combat. Balanced by strict preconditions, pair locking and cooldowns so it feels
 * like a camp-side comfort, not a combat heal.
 */
public class PetSnuggleGoal extends Goal {
    // Tuning
    private static final double MAX_DISTANCE_SQ = 2.5 * 2.5; // must be very close once paired
    private static final double SEARCH_RADIUS = 6.0; // look for snuggle friends nearby
    private static final long MIN_SAFE_SINCE_COMBAT_TICKS = 100; // ~5s
    private static final int HEAL_PULSE_INTERVAL_TICKS = 80; // 4s between pulses
    private static final int SESSION_MAX_DURATION_TICKS = 20 * 20; // cap channel to ~20s per session

    private static final int SESSION_COOLDOWN_TICKS = 2400; // 2 minutes per pet after a session

    private static final float PET_HEAL_LVL_LT15 = 1.0f; // 0.5 heart
    private static final float PET_HEAL_LVL_GE15 = 2.0f; // 1 heart
    private static final float MIN_HEALTH_NEED = 0.6f;

    private static final float RELATIONSHIP_BASE_SCALE = 0.9f;
    private static final float RELATIONSHIP_URGENCY_SCALE = 0.45f;

    // Shared cooldown registries
    private static final Map<String, Long> PAIR_NEXT_HEAL_TICK = new ConcurrentHashMap<>(); // petA|petB -> tick
    private static final Map<UUID, UUID> ACTIVE_PAIRINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ACTIVE_PAIR_EXPIRY = new ConcurrentHashMap<>();

    private final MobEntity pet;
    private final PetComponent component;

    @Nullable private MobEntity partner;
    @Nullable private PetComponent partnerComponent;
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

        long now = world.getTime();
        cleanupExpiredPairings(now);

        if (component.isInCombat()) {
            return false;
        }

        if (isPaired(pet.getUuid(), now)) {
            return false;
        }

        Long cooldownUntil = component.getStateData(PetComponent.StateKeys.SNUGGLE_COOLDOWN_UNTIL_TICK, Long.class);
        if (cooldownUntil != null && now < cooldownUntil) {
            return false;
        }

        // Require calm state: no recent attacks
        if ((now - Math.max(0L, component.getLastAttackTick())) < MIN_SAFE_SINCE_COMBAT_TICKS) {
            return false;
        }

        Candidate candidate = findBestPartner(world, now);
        if (candidate == null) {
            return false;
        }

        if (pet.getUuid().compareTo(candidate.partner().getUuid()) > 0) {
            return false;
        }

        this.partner = candidate.partner();
        this.partnerComponent = candidate.partnerComponent();
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }
        MobEntity partner = this.partner;
        if (partner == null || partner.isRemoved() || !partner.isAlive() || partner.getEntityWorld() != world) {
            return false;
        }

        if (pet.getUuid().compareTo(partner.getUuid()) > 0) {
            return false;
        }

        long now = world.getTime();
        if (component.isInCombat()) {
            return false;
        }

        // Session cap to avoid indefinite channels
        if (now - startTick > SESSION_MAX_DURATION_TICKS) {
            return false;
        }

        if (pet.isRemoved() || !pet.isAlive()) {
            return false;
        }
        if (partner.squaredDistanceTo(pet) > MAX_DISTANCE_SQ) {
            return false;
        }

        // Still calm
        if ((now - Math.max(0L, component.getLastAttackTick())) < MIN_SAFE_SINCE_COMBAT_TICKS) {
            return false;
        }

        PetComponent partnerComponent = this.partnerComponent;
        if (partnerComponent == null) {
            return false;
        }

        if (partnerComponent.isInCombat()) {
            return false;
        }

        if ((now - Math.max(0L, partnerComponent.getLastAttackTick())) < MIN_SAFE_SINCE_COMBAT_TICKS) {
            return false;
        }

        if (!isPairedWith(pet.getUuid(), partner.getUuid(), now)) {
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

        MobEntity partner = this.partner;
        PetComponent partnerComponent = this.partnerComponent;
        if (partner != null && partnerComponent != null) {
            partnerComponent.setStateData(PetComponent.StateKeys.SNUGGLE_LAST_START_TICK, startTick);
            long expiry = startTick + SESSION_MAX_DURATION_TICKS + 40L;
            ACTIVE_PAIRINGS.put(pet.getUuid(), partner.getUuid());
            ACTIVE_PAIRINGS.put(partner.getUuid(), pet.getUuid());
            ACTIVE_PAIR_EXPIRY.put(pet.getUuid(), expiry);
            ACTIVE_PAIR_EXPIRY.put(partner.getUuid(), expiry);

            // Gentle cue for both pets' owners (if online)
            var ownerA = component.getOwner();
            if (ownerA instanceof net.minecraft.server.network.ServerPlayerEntity serverOwnerA) {
                EmotionContextCues.sendCue(
                    serverOwnerA,
                    "snuggle.start." + pet.getUuidAsString(),
                    pet,
                    net.minecraft.text.Text.translatable("petsplus.snuggle.start", pet.getDisplayName()),
                    200);
            }
            var ownerB = partnerComponent.getOwner();
            if (ownerB instanceof net.minecraft.server.network.ServerPlayerEntity serverOwnerB) {
                EmotionContextCues.sendCue(
                    serverOwnerB,
                    "snuggle.start." + partner.getUuidAsString(),
                    partner,
                    net.minecraft.text.Text.translatable("petsplus.snuggle.start", partner.getDisplayName()),
                    200);
            }
        }
    }

    @Override
    public void stop() {
        if (pet.getEntityWorld() instanceof ServerWorld world) {
            long now = world.getTime();
            long until = now + SESSION_COOLDOWN_TICKS;
            component.setStateData(PetComponent.StateKeys.SNUGGLE_COOLDOWN_UNTIL_TICK, until);
            PetComponent partnerComponent = this.partnerComponent;
            if (partnerComponent != null) {
                partnerComponent.setStateData(PetComponent.StateKeys.SNUGGLE_COOLDOWN_UNTIL_TICK, until);
            }
        }
        MobEntity partner = this.partner;
        if (partner != null) {
            PAIR_NEXT_HEAL_TICK.remove(pairKey(pet.getUuid(), partner.getUuid()));
        }
        clearPairing();
        partner = null;
        partnerComponent = null;
    }

    @Override
    public void tick() {
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        long now = world.getTime();
        if ((now - lastPulseTick) < HEAL_PULSE_INTERVAL_TICKS) {
            return;
        }
        lastPulseTick = now;

        MobEntity partner = this.partner;
        PetComponent partnerComponent = this.partnerComponent;
        if (partner == null || partnerComponent == null) {
            return;
        }

        String pairKey = pairKey(pet.getUuid(), partner.getUuid());
        Long next = PAIR_NEXT_HEAL_TICK.get(pairKey);
        if (next != null && now < next) {
            return;
        }
        PAIR_NEXT_HEAL_TICK.put(pairKey, now + HEAL_PULSE_INTERVAL_TICKS);

        // Healing cadence: both pets pulse heal based on their level
        float petHeal = component.getLevel() >= 15 ? PET_HEAL_LVL_GE15 : PET_HEAL_LVL_LT15;
        applySafeHeal(pet, petHeal);

        float partnerHeal = partnerComponent.getLevel() >= 15 ? PET_HEAL_LVL_GE15 : PET_HEAL_LVL_LT15;
        applySafeHeal(partner, partnerHeal);

        // Emotions – cozy, bonded
        float urgency = snuggleUrgency(pet, partner);
        component.pushEmotion(PetComponent.Emotion.CONTENT, 0.05f + 0.04f * urgency);
        component.pushEmotion(PetComponent.Emotion.LOYALTY, 0.03f + 0.03f * urgency);
        component.pushEmotion(PetComponent.Emotion.UBUNTU, 0.04f + 0.03f * urgency);
        partnerComponent.pushEmotion(PetComponent.Emotion.CONTENT, 0.05f + 0.04f * urgency);
        partnerComponent.pushEmotion(PetComponent.Emotion.LOYALTY, 0.03f + 0.03f * urgency);
        partnerComponent.pushEmotion(PetComponent.Emotion.UBUNTU, 0.04f + 0.03f * urgency);

        // Relationship bonding – treat as restorative proximity scaled by need & kinship
        applyRelationshipPulse(partner, partnerComponent, urgency);

        // Soft ambient feedback
        emitAmbient(world, pet.getEntityPos().add(partner.getPos()).multiply(0.5));
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

    private void applyRelationshipPulse(MobEntity partner, PetComponent partnerComponent, float urgency) {
        float kinship = kinshipAffinity(partner, partnerComponent);
        float trustScale = RELATIONSHIP_BASE_SCALE + RELATIONSHIP_URGENCY_SCALE * urgency;
        float affectionScale = RELATIONSHIP_BASE_SCALE + kinship;
        float respectScale = RELATIONSHIP_BASE_SCALE + kinship * 0.35f;

        component.recordEntityInteraction(
            partner.getUuid(),
            InteractionType.PROXIMITY,
            trustScale,
            affectionScale,
            respectScale
        );
        partnerComponent.recordEntityInteraction(
            pet.getUuid(),
            InteractionType.PROXIMITY,
            trustScale,
            affectionScale,
            respectScale
        );
    }

    private float kinshipAffinity(MobEntity partner, PetComponent partnerComponent) {
        float affinity = 0.0f;
        if (pet.getType() == partner.getType()) {
            affinity += 0.35f;
        }
        if (sharesOwner(partnerComponent)) {
            affinity += 0.25f;
        } else {
            RelationshipType relation = component.getRelationshipType(partner.getUuid());
            if (relation == RelationshipType.COMPANION) {
                affinity += 0.3f;
            } else if (relation == RelationshipType.FRIEND) {
                affinity += 0.2f;
            } else if (relation == RelationshipType.FUN_ACQUAINTANCE) {
                affinity += 0.1f;
            }
        }
        return MathHelper.clamp(affinity, 0.0f, 0.5f);
    }

    private Candidate findBestPartner(ServerWorld world, long now) {
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) {
            return null;
        }
        PetSwarmIndex index = manager.getSwarmIndex();
        Vec3d center = pet.getPos();
        final Candidate[] best = {null};
        index.forEachPetInRange(center, SEARCH_RADIUS, entry -> {
            if (entry == null) {
                return;
            }
            MobEntity other = entry.pet();
            if (other == null || other == pet || other.isRemoved() || !other.isAlive()) {
                return;
            }
            if (!(other.getEntityWorld() instanceof ServerWorld) || other.getEntityWorld() != world) {
                return;
            }
            PetComponent otherComponent = entry.component();
            if (otherComponent == null) {
                return;
            }
            if (!canPairWith(other, otherComponent, now)) {
                return;
            }

            double distanceSq = pet.squaredDistanceTo(other);
            double score = scoreCandidate(other, otherComponent, distanceSq);
            if (score <= 0.0) {
                return;
            }

            Candidate current = best[0];
            if (current == null || score > current.score()) {
                best[0] = new Candidate(other, otherComponent, score);
            }
        });

        return best[0];
    }

    private boolean canPairWith(MobEntity other, PetComponent otherComponent, long now) {
        if (isPaired(other.getUuid(), now)) {
            return false;
        }
        if (otherComponent == component) {
            return false;
        }
        Long cooldownUntil = otherComponent.getStateData(PetComponent.StateKeys.SNUGGLE_COOLDOWN_UNTIL_TICK, Long.class);
        if (cooldownUntil != null && now < cooldownUntil) {
            return false;
        }
        if (otherComponent.isInCombat()) {
            return false;
        }

        if ((now - Math.max(0L, otherComponent.getLastAttackTick())) < MIN_SAFE_SINCE_COMBAT_TICKS) {
            return false;
        }

        float selfMissing = missingHealth(pet);
        float otherMissing = missingHealth(other);
        if ((selfMissing + otherMissing) < MIN_HEALTH_NEED) { // require at least some need
            return false;
        }

        if (!sharesOwner(otherComponent)) {
            RelationshipType relation = component.getRelationshipType(other.getUuid());
            RelationshipType reverse = otherComponent.getRelationshipType(pet.getUuid());
            if (!allowsSnuggle(relation) || !allowsSnuggle(reverse)) {
                return false;
            }
        }

        return true;
    }

    private double scoreCandidate(MobEntity other, PetComponent otherComponent, double distanceSq) {
        double distance = Math.sqrt(Math.max(0.0001, distanceSq));
        double normalizedDistance = MathHelper.clamp(1.0 - (distance / SEARCH_RADIUS), 0.0, 1.0);

        double selfMissing = Math.min(10.0, missingHealth(pet));
        double otherMissing = Math.min(10.0, missingHealth(other));
        double healthScore = (selfMissing * 1.2) + (otherMissing * 1.0);

        double affinity = 0.0;
        if (pet.getType() == other.getType()) {
            affinity += 2.0;
        }
        if (sharesOwner(otherComponent)) {
            affinity += 1.0;
        } else {
            RelationshipType relation = component.getRelationshipType(other.getUuid());
            if (relation == RelationshipType.COMPANION) {
                affinity += 1.5;
            } else if (relation == RelationshipType.FRIEND) {
                affinity += 1.0;
            } else if (relation == RelationshipType.FUN_ACQUAINTANCE) {
                affinity += 0.5;
            }
        }

        double urgency = Math.max(selfMissing, otherMissing);
        double score = (healthScore + affinity * 1.2 + urgency * 1.5) * (0.5 + normalizedDistance * 0.5);
        return score;
    }

    private float missingHealth(LivingEntity entity) {
        return Math.max(0.0f, entity.getMaxHealth() - entity.getHealth());
    }

    private static boolean allowsSnuggle(RelationshipType type) {
        if (type == null) {
            return false;
        }
        return type == RelationshipType.COMPANION || type == RelationshipType.FRIEND || type == RelationshipType.FUN_ACQUAINTANCE;
    }

    private static float snuggleUrgency(LivingEntity a, LivingEntity b) {
        float missing = Math.max(Math.max(0.0f, a.getMaxHealth() - a.getHealth()), Math.max(0.0f, b.getMaxHealth() - b.getHealth()));
        return MathHelper.clamp(missing / 6.0f, 0.0f, 1.2f);
    }

    private boolean sharesOwner(PetComponent otherComponent) {
        UUID ownerA = component.getOwnerUuid();
        UUID ownerB = otherComponent.getOwnerUuid();
        return ownerA != null && ownerA.equals(ownerB);
    }

    private void cleanupExpiredPairings(long now) {
        ACTIVE_PAIR_EXPIRY.entrySet().removeIf(entry -> {
            if (entry.getValue() == null || entry.getValue() < now) {
                UUID id = entry.getKey();
                UUID partner = ACTIVE_PAIRINGS.remove(id);
                if (partner != null) {
                    PAIR_NEXT_HEAL_TICK.remove(pairKey(id, partner));
                    ACTIVE_PAIRINGS.remove(partner, id);
                    ACTIVE_PAIR_EXPIRY.remove(partner);
                }
                return true;
            }
            return false;
        });
    }

    private boolean isPaired(UUID id, long now) {
        cleanupExpiredPairings(now);
        return ACTIVE_PAIRINGS.containsKey(id);
    }

    private boolean isPairedWith(UUID id, UUID partnerId, long now) {
        cleanupExpiredPairings(now);
        UUID partner = ACTIVE_PAIRINGS.get(id);
        return partner != null && partner.equals(partnerId);
    }

    private void clearPairing() {
        MobEntity partner = this.partner;
        if (partner == null) {
            ACTIVE_PAIRINGS.remove(pet.getUuid());
            ACTIVE_PAIR_EXPIRY.remove(pet.getUuid());
            return;
        }
        UUID selfId = pet.getUuid();
        UUID partnerId = partner.getUuid();
        ACTIVE_PAIRINGS.remove(selfId);
        ACTIVE_PAIRINGS.remove(partnerId, selfId);
        ACTIVE_PAIRINGS.remove(selfId, partnerId);
        ACTIVE_PAIR_EXPIRY.remove(selfId);
        ACTIVE_PAIR_EXPIRY.remove(partnerId);
        PAIR_NEXT_HEAL_TICK.remove(pairKey(selfId, partnerId));
    }

    private static String pairKey(UUID first, UUID second) {
        if (first.compareTo(second) <= 0) {
            return first + "|" + second;
        }
        return second + "|" + first;
    }

    private record Candidate(MobEntity partner, PetComponent partnerComponent, double score) {}
}

