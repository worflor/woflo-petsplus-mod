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
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.striker.StrikerExecution;
import woflo.petsplus.roles.striker.StrikerHuntManager;
import woflo.petsplus.ui.FeedbackManager;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Applies the Striker bloodlust surge buffs based on recent execution momentum.
 */
public class StrikerBloodlustSurgeEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "striker_bloodlust_surge");

    private final int baseDurationTicks;
    private final int perStackDurationTicks;
    private final double ownerStrengthPerStack;
    private final double ownerSpeedPerStack;
    private final double petStrengthPerStack;
    private final double petSpeedPerStack;
    private final int maxAmplifier;
    private final boolean buffPet;
    private final boolean swingOwner;
    private final boolean playFeedback;

    public StrikerBloodlustSurgeEffect(JsonObject json) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier strikerId = PetRoleType.STRIKER.id();

        this.baseDurationTicks = Math.max(20, RegistryJsonHelper.getInt(json, "base_duration_ticks",
            config.getRoleInt(strikerId, "bloodlustBaseDurationTicks", 100)));
        this.perStackDurationTicks = Math.max(0, RegistryJsonHelper.getInt(json, "per_stack_duration_ticks",
            config.getRoleInt(strikerId, "bloodlustStackDurationTicks", 40)));
        this.ownerStrengthPerStack = Math.max(0.0, RegistryJsonHelper.getDouble(json, "owner_strength_per_stack",
            config.getRoleDouble(strikerId, "bloodlustOwnerStrengthPerStack", 0.7)));
        this.ownerSpeedPerStack = Math.max(0.0, RegistryJsonHelper.getDouble(json, "owner_speed_per_stack",
            config.getRoleDouble(strikerId, "bloodlustOwnerSpeedPerStack", 0.5)));
        this.petStrengthPerStack = Math.max(0.0, RegistryJsonHelper.getDouble(json, "pet_strength_per_stack",
            config.getRoleDouble(strikerId, "bloodlustPetStrengthPerStack", 0.45)));
        this.petSpeedPerStack = Math.max(0.0, RegistryJsonHelper.getDouble(json, "pet_speed_per_stack",
            config.getRoleDouble(strikerId, "bloodlustPetSpeedPerStack", 0.35)));
        this.maxAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "max_amplifier",
            config.getRoleInt(strikerId, "bloodlustMaxAmplifier", 3)));
        this.buffPet = RegistryJsonHelper.getBoolean(json, "buff_pet", true);
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

        int stacks = 0;
        if (context.getTriggerContext() != null) {
            stacks = context.getTriggerContext().getExecutionMomentumStacks();
            if (stacks <= 0 && context.getTriggerContext().wasExecutionKill()) {
                stacks = 1;
            }
        }
        if (stacks <= 0) {
            stacks = Math.max(0, StrikerExecution.getActiveMomentumStacks(owner));
        }
        if (stacks <= 0) {
            return false;
        }

        int duration = Math.max(20, baseDurationTicks + Math.max(0, stacks - 1) * perStackDurationTicks);
        applyStatusEffect(serverOwner, StatusEffects.STRENGTH, computeAmplifier(ownerStrengthPerStack, stacks), duration);
        applyStatusEffect(serverOwner, StatusEffects.SPEED, computeAmplifier(ownerSpeedPerStack, stacks), duration);

        MobEntity pet = context.getPet();
        if (buffPet && pet != null && pet.getWorld() instanceof ServerWorld) {
            applyStatusEffect(pet, StatusEffects.STRENGTH, computeAmplifier(petStrengthPerStack, stacks), duration);
            applyStatusEffect(pet, StatusEffects.SPEED, computeAmplifier(petSpeedPerStack, stacks), duration);
        }

        if (swingOwner) {
            owner.swingHand(Hand.MAIN_HAND, true);
        }

        if (playFeedback && context.getWorld() instanceof ServerWorld serverWorld) {
            FeedbackManager.emitRoleAbility(PetRoleType.STRIKER.id(), "bloodlust", owner, serverWorld);
        }

        // Removed bloodlust spam - will add particle/sound in ability JSON
        StrikerHuntManager.getInstance().onBloodlustTriggered(serverOwner, stacks, duration);
        return true;
    }

    private void applyStatusEffect(PlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier, int duration) {
        if (amplifier < 0) {
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, true, true, true));
    }

    private void applyStatusEffect(MobEntity entity, RegistryEntry<StatusEffect> effect, int amplifier, int duration) {
        if (amplifier < 0) {
            return;
        }
        entity.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, true, true, true));
    }

    private int computeAmplifier(double perStack, int stacks) {
        if (perStack <= 0 || stacks <= 0) {
            return 0;
        }
        double value = stacks * perStack;
        int amplifier = MathHelper.floor(value);
        return MathHelper.clamp(amplifier, 0, maxAmplifier);
    }
}
