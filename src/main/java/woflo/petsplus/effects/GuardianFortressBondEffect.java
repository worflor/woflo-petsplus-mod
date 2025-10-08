package woflo.petsplus.effects;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.roles.guardian.GuardianFortressBondManager;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Effect that activates the Guardian fortress bond protective barrier.
 */
public class GuardianFortressBondEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "guardian_fortress_bond");

    private final double reductionPct;
    private final int durationTicks;

    public GuardianFortressBondEffect(double reductionPct, int durationTicks) {
        this.reductionPct = reductionPct;
        this.durationTicks = Math.max(20, durationTicks);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }
        MobEntity pet = context.getPet();
        if (pet == null || !pet.isAlive()) {
            return false;
        }
        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }
        if (owner.getEntityWorld() != world) {
            return false;
        }

        boolean activated = GuardianFortressBondManager.activateBond(pet, owner, reductionPct, durationTicks);
        if (!activated) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component != null) {
            component.setStateData("guardian_fortress_bond_expiry", world.getTime() + durationTicks);
        }

        UIFeedbackManager.sendGuardianFortressMessage(owner, pet);
        return true;
    }

    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
}


