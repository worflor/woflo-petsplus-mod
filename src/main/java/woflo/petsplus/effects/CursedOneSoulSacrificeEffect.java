package woflo.petsplus.effects;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.roles.cursedone.CursedOneSoulSacrificeManager;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIFeedbackManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the Cursed One Soul Sacrifice channel.
 */
public class CursedOneSoulSacrificeEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "cursed_one_soul_sacrifice");

    private final int xpCostLevels;
    private final int durationTicks;
    private final double reanimationMultiplier;
    private final double healPercent;
    private final List<StatusEffectInstance> ownerEffects;

    public CursedOneSoulSacrificeEffect(int xpCostLevels, int durationTicks, double reanimationMultiplier,
                                        double healPercent, List<StatusEffectInstance> ownerEffects) {
        this.xpCostLevels = Math.max(0, xpCostLevels);
        this.durationTicks = Math.max(20, durationTicks);
        this.reanimationMultiplier = Math.max(1.0, reanimationMultiplier);
        this.healPercent = Math.max(0.0, healPercent);
        this.ownerEffects = new ArrayList<>(ownerEffects);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getEntityWorld() instanceof ServerWorld)) {
            return false;
        }

        MobEntity pet = context.getPet();
        if (pet == null || pet.isRemoved()) {
            return false;
        }

        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return false;
        }

        if (xpCostLevels > 0 && owner.experienceLevel < xpCostLevels) {
            UIFeedbackManager.sendCursedSoulSacrificeInsufficientXp(owner, xpCostLevels);
            return false;
        }

        if (!CursedOneSoulSacrificeManager.markActivation(pet, durationTicks, reanimationMultiplier)) {
            return false;
        }

        if (xpCostLevels > 0) {
            owner.addExperienceLevels(-xpCostLevels);
        }

        if (healPercent > 0.0f) {
            float healAmount = (float) (owner.getMaxHealth() * MathHelper.clamp(healPercent, 0.0, 1.0));
            owner.heal(healAmount);
        }

        for (StatusEffectInstance effect : ownerEffects) {
            if (effect == null) {
                continue;
            }
            owner.addStatusEffect(new StatusEffectInstance(effect));
        }

        CursedOneSoulSacrificeManager.emitActivationParticles(pet, owner);
        UIFeedbackManager.sendCursedSoulSacrificeActivated(owner, pet, xpCostLevels, durationTicks / 20);
        return true;
    }

    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
}


