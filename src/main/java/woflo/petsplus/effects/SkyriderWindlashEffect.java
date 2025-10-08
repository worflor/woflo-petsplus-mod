package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.skyrider.SkyriderWinds;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Applies the Windlash Rider jump surge and attack rider when the owner begins a qualifying fall.
 */
public class SkyriderWindlashEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "skyrider_windlash");

    private final double radiusSq;
    private final double minFallDistance;
    private final int minLevel;
    private final int jumpDurationTicks;
    private final int jumpAmplifier;
    private final double bonusDamagePct;
    private final int bonusExpireTicks;
    private final double knockupStrength;
    private final int ownerSlowfallTicks;
    private final boolean sendMessage;
    private final boolean swingOwner;
    private final long duplicateGateTicks;

    public SkyriderWindlashEffect(JsonObject json) {
        double radius = RegistryJsonHelper.getDouble(json, "radius", 16.0D);
        this.radiusSq = radius <= 0 ? 0 : radius * radius;
        this.minFallDistance = RegistryJsonHelper.getDouble(json, "min_fall_distance", SkyriderWinds.getWindlashMinFallBlocks());
        this.minLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 7));
        this.jumpDurationTicks = Math.max(1, RegistryJsonHelper.getInt(json, "jump_boost_duration_ticks", 40));
        this.jumpAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "jump_boost_amplifier", 0));
        this.bonusDamagePct = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "bonus_damage_pct", 0.15D));
        this.bonusExpireTicks = Math.max(1, RegistryJsonHelper.getInt(json, "bonus_expire_ticks", 100));
        this.knockupStrength = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "knockup_strength", 0.35D));
        this.ownerSlowfallTicks = Math.max(0, RegistryJsonHelper.getInt(json, "owner_slowfall_ticks", 60));
        this.sendMessage = RegistryJsonHelper.getBoolean(json, "send_message", true);
        this.swingOwner = RegistryJsonHelper.getBoolean(json, "swing_owner", false);
        this.duplicateGateTicks = Math.max(1L, RegistryJsonHelper.getInt(json, "duplicate_gate_ticks", 5));
    }

    public SkyriderWindlashEffect() {
        this.radiusSq = 16.0D * 16.0D;
        this.minFallDistance = SkyriderWinds.getWindlashMinFallBlocks();
        this.minLevel = 7;
        this.jumpDurationTicks = 40;
        this.jumpAmplifier = 0;
        this.bonusDamagePct = 0.15D;
        this.bonusExpireTicks = 100;
        this.knockupStrength = 0.35D;
        this.ownerSlowfallTicks = 60;
        this.sendMessage = true;
        this.swingOwner = false;
        this.duplicateGateTicks = 5L;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        ServerWorld world = context.getEntityWorld();
        MobEntity pet = context.getPet();
        PlayerEntity owner = context.getOwner();

        if (world == null || pet == null || !(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.SKYRIDER) || component.getLevel() < minLevel) {
            return false;
        }

        if (radiusSq > 0.0D && serverOwner.squaredDistanceTo(pet) > radiusSq) {
            return false;
        }

        double fallDistance = Math.max(context.getTriggerContext().getFallDistance(), owner.fallDistance);
        if (fallDistance < minFallDistance) {
            return false;
        }

        OwnerCombatState ownerState = OwnerCombatState.getOrCreate(owner);
        long currentTick = world.getTime();
        long lastTick = ownerState.getTempState(SkyriderWinds.WINDLASH_LAST_TRIGGER_KEY);
        if (lastTick != 0 && currentTick - lastTick < duplicateGateTicks) {
            return false;
        }

        StatusEffectInstance jump = new StatusEffectInstance(StatusEffects.JUMP_BOOST, jumpDurationTicks, jumpAmplifier, false, true, true);
        serverOwner.addStatusEffect(jump);

        if (ownerSlowfallTicks > 0) {
            serverOwner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, ownerSlowfallTicks, 0, false, true, true));
        }

        if (bonusDamagePct > 0.0D || knockupStrength > 0.0D) {
            Effect knockupEffect = knockupStrength > 0.0D ? new KnockupEffect(knockupStrength, "victim") : null;
            Effect rider = new OwnerNextAttackBonusEffect(bonusDamagePct, null, knockupEffect, bonusExpireTicks);
            rider.execute(context);
        }

        if (swingOwner) {
            serverOwner.swingHand(Hand.MAIN_HAND, true);
        }

        // Removed windlash spam - will add particle/sound in ability JSON

        ownerState.setTempState(SkyriderWinds.WINDLASH_LAST_TRIGGER_KEY, currentTick);
        return true;
    }
}


