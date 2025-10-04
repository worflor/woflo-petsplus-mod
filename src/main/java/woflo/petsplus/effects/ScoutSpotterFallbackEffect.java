package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.scout.ScoutBehaviors;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ui.UIFeedbackManager;
import woflo.petsplus.util.EffectConfigHelper;

/**
 * Applies the Scout spotter fallback glowing pulse when the owner has been without pet assists
 * for a configured duration.
 */
public class ScoutSpotterFallbackEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "scout_spotter_fallback");
    private static final String STATE_LAST_TRIGGER = "scout_spotter_last";
    private static final String STATE_GATE_TICK = "scout_spotter_gate";

    private final double radiusSq;
    private final int minimumLevel;
    private final int idleTicks;
    private final int cooldownTicks;
    private final int glowDurationTicks;
    private final boolean sendMessage;

    public ScoutSpotterFallbackEffect(JsonObject json) {
        double radius = RegistryJsonHelper.getDouble(json, "radius", 16.0D);
        this.radiusSq = radius <= 0 ? Double.POSITIVE_INFINITY : radius * radius;
        this.minimumLevel = EffectConfigHelper.parseMinLevel(json, "min_level", 10);
        this.idleTicks = EffectConfigHelper.parseDuration(json, "idle_ticks", 60);
        this.cooldownTicks = EffectConfigHelper.parseDuration(json, "cooldown_ticks", 300);
        this.glowDurationTicks = EffectConfigHelper.parseDuration(json, "glow_duration_ticks", 20);
        this.sendMessage = RegistryJsonHelper.getBoolean(json, "send_message", true);
    }

    public ScoutSpotterFallbackEffect() {
        this.radiusSq = 16.0D * 16.0D;
        this.minimumLevel = 10;
        this.idleTicks = 60;
        this.cooldownTicks = 300;
        this.glowDurationTicks = 20;
        this.sendMessage = true;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        ServerWorld world = context.getWorld();
        MobEntity pet = context.getPet();
        PlayerEntity owner = context.getOwner();
        if (world == null || !(owner instanceof ServerPlayerEntity serverOwner) || pet == null) {
            return false;
        }

        if (!"owner_dealt_damage".equals(context.getTriggerContext().getEventType())) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.SCOUT) || component.getLevel() < minimumLevel) {
            return false;
        }
        if (!component.isOwnedBy(owner) || !pet.isAlive()) {
            return false;
        }

        if (radiusSq != Double.POSITIVE_INFINITY && pet.squaredDistanceTo(owner) > radiusSq) {
            return false;
        }

        LivingEntity victim = context.getTriggerContext().getVictim() instanceof LivingEntity living
            ? living
            : null;
        if (victim == null) {
            return false;
        }

        StateManager manager = StateManager.forWorld(world);
        if (manager == null) {
            return false;
        }

        List<PetSwarmIndex.SwarmEntry> scoutEntries = ScoutBehaviors.collectScoutEntries(manager, serverOwner, radiusSq);
        if (scoutEntries.isEmpty()) {
            return false;
        }

        long now = world.getTime();
        long mostRecentAttack = Long.MIN_VALUE;
        boolean currentPetIncluded = false;
        for (PetSwarmIndex.SwarmEntry entry : scoutEntries) {
            PetComponent entryComponent = entry.component();
            if (entryComponent == null || entryComponent.getLevel() < minimumLevel) {
                continue;
            }
            MobEntity entryPet = entry.pet();
            if (entryPet == null || entryPet.isRemoved()) {
                continue;
            }
            if (radiusSq != Double.POSITIVE_INFINITY && entryPet.squaredDistanceTo(serverOwner) > radiusSq) {
                continue;
            }
            if (entryPet == pet) {
                currentPetIncluded = true;
            }
            long lastAttack = entryComponent.getLastAttackTick();
            if (lastAttack > mostRecentAttack) {
                mostRecentAttack = lastAttack;
            }
        }

        if (!currentPetIncluded) {
            return false;
        }

        if (mostRecentAttack > 0 && now - mostRecentAttack < idleTicks) {
            return false;
        }

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        long lastTrigger = combatState.getTempState(STATE_LAST_TRIGGER);
        if (lastTrigger > 0 && now - lastTrigger < cooldownTicks) {
            return false;
        }
        if (combatState.getTempState(STATE_GATE_TICK) == now) {
            return false;
        }

        StatusEffectInstance glowing = new StatusEffectInstance(StatusEffects.GLOWING, glowDurationTicks, 0, false, true, true);
        boolean applied = victim.addStatusEffect(glowing);
        if (!applied) {
            return false;
        }

        combatState.setTempState(STATE_LAST_TRIGGER, now);
        combatState.setTempState(STATE_GATE_TICK, now);

        // Removed scout spotter spam - visual feedback via glowing effect

        return true;
    }
}
